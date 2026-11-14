use std::path::{Path, PathBuf};
use std::time::Duration;

use glam::{Mat3, Vec3};
use rbx_dom::{Ref, Variant, WeakDom};
use rbx_reflection::ReflectionDatabase;
use serde_json::{json, Value};

use crate::history::History;
use crate::properties::{self, Properties};
use crate::save::{self, Format};
use crate::script_editor::source;
use crate::template::Template;
use crate::transform::{frame, targets::Targets};

/// One authoritative rbx-native DOM. Android widgets only dispatch commands.
pub struct Editor {
    pub dom: WeakDom,
    pub selection: Vec<Ref>,
    pub path: PathBuf,
    pub logs: Vec<String>,
    pub status: String,
    pub tool: String,
    pub local: bool,
    pub snap: f32,
    pub dirty: bool,
    pub revision: u64,
    props: Properties,
    history: History,
    gesture: bool,
    gesture_recorded: bool,
}

impl Editor {
    pub fn open_or_create(path: PathBuf) -> Result<Self, String> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let dom = if path.exists() {
            rbx_viewer::read_place(&path)?
        } else {
            Template::Baseplate.dom()
        };
        let mut editor = Self {
            dom,
            path,
            selection: Vec::new(),
            logs: Vec::new(),
            status: "rbx-native · núcleo Rust".into(),
            tool: "select".into(),
            local: false,
            snap: 1.0,
            dirty: false,
            revision: 0,
            props: Properties::new(ReflectionDatabase::embedded()),
            history: History::new(20),
            gesture: false,
            gesture_recorded: false,
        };
        editor.dom.take_changes();
        if !editor.path.exists() {
            editor.save()?;
        }
        Ok(editor)
    }

    pub fn database(&self) -> &ReflectionDatabase {
        self.props.db()
    }

    fn before(&mut self) {
        if self.gesture && self.gesture_recorded {
            return;
        }
        // The renderer may not have consumed earlier commands in this frame.
        // Clear the snapshot's journal, never the live document's journal.
        let mut snapshot = self.dom.clone();
        snapshot.take_changes();
        self.history.push(snapshot);
        self.gesture_recorded = self.gesture;
    }

    pub fn begin_gesture(&mut self) {
        self.gesture = true;
        self.gesture_recorded = false;
    }
    pub fn end_gesture(&mut self) {
        self.gesture = false;
        self.gesture_recorded = false;
    }

    pub fn changed(&mut self) {
        self.props.dom_changed(self.dom.changes());
        self.history.record_changes(self.dom.changes().to_vec());
        self.dirty = true;
        self.revision += 1;
        self.selection.retain(|id| self.dom.get(*id).is_some());
    }

    pub fn save(&mut self) -> Result<(), String> {
        save::save(&self.dom, Format::of_path(&self.path), &self.path)?;
        self.dirty = false;
        self.status = format!(
            "Salvo · {}",
            self.path.file_name().unwrap_or_default().to_string_lossy()
        );
        Ok(())
    }

    pub fn load(&mut self, path: &Path) -> Result<(), String> {
        // Parse completely before replacing the live document.
        let dom = rbx_viewer::read_place(path)?;
        self.before();
        self.dom = dom;
        self.selection.clear();
        self.changed();
        self.status = format!("Importado · {}", path.display());
        Ok(())
    }

    /// Original three-way apply with an empty source is also a lossless
    /// multi-root import: it remaps ALL references and Instance attributes.
    pub fn import_model(&mut self, path: &Path) -> Result<(), String> {
        let mut incoming = rbx_viewer::read_place(path)?;
        let roots = incoming.root_refs().to_vec();
        if roots
            .iter()
            .any(|r| incoming.get(*r).is_some_and(|i| i.class() == "Workspace"))
        {
            return Err("Esse arquivo contém um place; use Abrir projeto".into());
        }
        if roots.is_empty() {
            return Err("O arquivo de modelo não contém Instances".into());
        }
        let name = path.file_stem().unwrap_or_default().to_string_lossy();
        let group = incoming.new_instance("Model", &name, None);
        for r in roots {
            incoming.set_parent(r, Some(group));
        }
        self.before();
        let existing = self.dom.root_refs().to_vec();
        rbx_dom::merge::apply(&mut self.dom, &WeakDom::new(), &incoming);
        let imported = self
            .dom
            .root_refs()
            .iter()
            .copied()
            .find(|r| !existing.contains(r))
            .ok_or("O importador não inseriu o modelo")?;
        let workspace = self
            .dom
            .root_refs()
            .iter()
            .copied()
            .find(|r| self.dom.get(*r).is_some_and(|i| i.class() == "Workspace"))
            .unwrap_or_else(|| self.dom.new_instance("Workspace", "Workspace", None));
        self.dom.set_parent(imported, Some(workspace));
        self.selection = vec![imported];
        self.changed();
        self.status = format!("Modelo importado · {name}");
        Ok(())
    }

    pub fn run(&mut self, code: &str) -> Result<(), String> {
        rbx_lua::compile_check(code, "AndroidCommand")?;
        self.before();
        let owned = std::mem::take(&mut self.dom);
        let backup = owned.clone();
        let mut runtime = match rbx_lua::Runtime::new(owned, ReflectionDatabase::embedded()) {
            Ok(runtime) => runtime,
            Err(error) => {
                self.dom = backup;
                return Err(error.to_string());
            }
        };
        runtime.set_resume_budget(Duration::from_millis(100));
        let result = runtime.run_for(code, Duration::from_secs(2));
        self.dom = runtime.into_dom();
        self.changed();
        match result {
            Ok(output) => {
                self.logs.extend(output.lines().iter().cloned());
                Ok(())
            }
            Err(error) => {
                self.logs.extend(error.output.lines().iter().cloned());
                Err(error.error.to_string())
            }
        }
    }

    /// Uses the original Luau Instance.new / defaults / DOM binding.
    pub fn insert(&mut self, class: &str) -> Result<Ref, String> {
        if ![
            "Part",
            "WedgePart",
            "CornerWedgePart",
            "Model",
            "Folder",
            "Script",
            "LocalScript",
            "ModuleScript",
            "PointLight",
            "SpotLight",
        ]
        .contains(&class)
        {
            return Err("Classe não oferecida por este port".into());
        }
        let next = self.dom.next_ref();
        let parent = if ["Script", "ModuleScript"].contains(&class) {
            "game:GetService(\"ServerScriptService\")".to_owned()
        } else if class.ends_with("Light") {
            self.selection
                .first()
                .copied()
                .map(|id| self.lua_ref(id))
                .transpose()?
                .ok_or("Selecione uma Part para inserir a luz")?
        } else {
            "workspace".into()
        };
        let mut code = format!("local p = Instance.new(\"{class}\")\np.Parent = {parent}\n");
        if self.database().is_subclass_of(class, "BasePart") {
            code.push_str("p.Position = Vector3.new(0, 4, 0)\np.Anchored = true\n");
        }
        if class.ends_with("Script") {
            code.push_str(if class == "ModuleScript" {
                "p.Source = 'local module = {}\\nreturn module\\n'\n"
            } else {
                "p.Source = 'print(\"Hello, world!\")\\n'\n"
            });
        }
        self.run(&code)?;
        let made = Ref::new(next);
        if self.dom.get(made).is_none() {
            return Err("Instance.new não criou a instância".into());
        }
        self.selection = vec![made];
        Ok(made)
    }

    /// Exact child indices avoid ambiguous names when scripting Clone/Group.
    pub fn lua_ref(&self, id: Ref) -> Result<String, String> {
        let parent = self.dom.parent(id);
        let siblings = match parent {
            Some(p) => self.dom.get(p).ok_or("Pai ausente")?.children(),
            None => self.dom.root_refs(),
        };
        let index = siblings
            .iter()
            .position(|r| *r == id)
            .ok_or("Referência ausente")?
            + 1;
        let base = match parent {
            Some(p) => self.lua_ref(p)?,
            None => "game".into(),
        };
        Ok(format!("{base}:GetChildren()[{index}]"))
    }

    pub fn edit(&mut self, id: Ref, name: &str, text: &str) -> Result<(), String> {
        self.before();
        if name == "Source" {
            if !source::is_script(&self.dom, self.database(), id) {
                return Err("Objeto não é script".into());
            }
            source::write(&mut self.dom, id, text);
        } else {
            properties::edit::commit(&mut self.dom, self.props.db(), id, name, text)?;
        }
        self.changed();
        Ok(())
    }

    pub fn transform(&mut self, kind: &str, xyz: [f32; 3]) -> Result<(), String> {
        if !xyz.iter().all(|x| x.is_finite()) {
            return Err("Transformação inválida".into());
        }
        let targets = crate::transform::parts(&self.dom, self.database(), &self.selection);
        if targets.is_empty() {
            return Err("Selecione uma Part ou Model".into());
        }
        self.before();
        let vector = Vec3::from_array(xyz);
        for target in targets {
            let placed = match kind {
                "move" => target.moved_to(
                    target.position()
                        + if self.local {
                            target.orientation() * vector
                        } else {
                            vector
                        },
                ),
                "scale" => target.resized_to(
                    (target.size() + vector).clamp(Vec3::splat(0.001), Vec3::splat(2048.0)),
                    target.position(),
                ),
                "rotate" => {
                    let [x, y, z] = xyz.map(f32::to_radians);
                    let turn = Mat3::from_rotation_y(y)
                        * Mat3::from_rotation_x(x)
                        * Mat3::from_rotation_z(z);
                    target.rotated_to(if self.local {
                        target.orientation() * turn
                    } else {
                        turn * target.orientation()
                    })
                }
                _ => return Err("Ferramenta desconhecida".into()),
            };
            if kind == "rotate" {
                let cf = frame::cframe(glam::Mat4::from_cols(
                    placed.orientation().x_axis.extend(0.),
                    placed.orientation().y_axis.extend(0.),
                    placed.orientation().z_axis.extend(0.),
                    placed.position().extend(1.),
                ));
                self.dom
                    .set_property(target.referent, "CFrame", Variant::CFrame(cf))
                    .map_err(|e| e.to_string())?;
            } else {
                // Original Properties commit preserves every stored rotation bit
                // when only position or size is changed, including unusual files.
                let p = placed.position();
                properties::edit::commit(
                    &mut self.dom,
                    self.props.db(),
                    target.referent,
                    "CFrame",
                    &format!("{}, {}, {}", p.x, p.y, p.z),
                )?;
            }
            if kind == "scale" {
                let s = placed.size();
                self.dom
                    .set_property(
                        target.referent,
                        "size",
                        Variant::Vector3(rbx_dom::Vector3Data {
                            x: s.x,
                            y: s.y,
                            z: s.z,
                        }),
                    )
                    .map_err(|e| e.to_string())?;
            }
        }
        self.changed();
        Ok(())
    }

    pub(crate) fn resize_absolute(
        &mut self,
        id: Ref,
        size: Vec3,
        position: Vec3,
    ) -> Result<(), String> {
        self.before();
        properties::edit::commit(
            &mut self.dom,
            self.props.db(),
            id,
            "size",
            &format!("{}, {}, {}", size.x, size.y, size.z),
        )?;
        properties::edit::commit(
            &mut self.dom,
            self.props.db(),
            id,
            "CFrame",
            &format!("{}, {}, {}", position.x, position.y, position.z),
        )?;
        self.changed();
        Ok(())
    }

    /// JSON is only the Android command boundary, never the project format.
    pub fn command(&mut self, command: &Value) -> Result<(), String> {
        let op = command["op"].as_str().ok_or("Comando sem op")?;
        let text = |key: &str| command[key].as_str().unwrap_or("");
        match op {
            "select" => {
                let id = Ref::new(command["id"].as_u64().unwrap_or(0) as u32);
                self.selection = if self.dom.get(id).is_some() {
                    vec![id]
                } else {
                    vec![]
                };
            }
            "insert" => {
                self.insert(text("class"))?;
            }
            "edit" => {
                let id = command["id"]
                    .as_u64()
                    .map(|n| Ref::new(n as u32))
                    .or_else(|| self.selection.first().copied())
                    .ok_or("Nenhum objeto selecionado")?;
                self.edit(id, text("name"), text("text"))?;
            }
            "tool" => {
                self.tool = text("tool").to_owned();
            }
            "local" => {
                self.local = !self.local;
            }
            "snap" => {
                self.snap = command["value"].as_f64().unwrap_or(1.0).clamp(0.0, 128.0) as f32;
            }
            "transform" => {
                let v = ["x", "y", "z"].map(|k| command[k].as_f64().unwrap_or(0.0) as f32);
                self.transform(text("kind"), v)?;
            }
            "delete" => {
                let refs = self.selection.clone();
                if refs.iter().any(|id| self.dom.parent(*id).is_none()) {
                    return Err("Serviços não podem ser excluídos".into());
                }
                self.before();
                for id in refs {
                    self.dom.remove(id);
                }
                self.changed();
            }
            "duplicate" => {
                let id = *self.selection.first().ok_or("Selecione um objeto")?;
                let next = self.dom.next_ref();
                self.run(&format!(
                    "local p = {}; local c = p:Clone(); c.Parent = p.Parent",
                    self.lua_ref(id)?
                ))?;
                self.selection = vec![Ref::new(next)];
            }
            "group" => {
                let refs = self.selection.clone();
                let parent = refs
                    .first()
                    .and_then(|id| self.dom.parent(*id))
                    .ok_or("Selecione um objeto no Workspace")?;
                self.before();
                let model = self.dom.new_instance("Model", "Model", Some(parent));
                for id in refs {
                    self.dom.set_parent(id, Some(model));
                }
                self.selection = vec![model];
                self.changed();
            }
            "undo" | "redo" => {
                let current = self.dom.clone();
                let step = if op == "undo" {
                    self.history.undo(current)
                } else {
                    self.history.redo(current)
                };
                if let Some((dom, _)) = step {
                    self.dom = dom;
                    self.changed();
                }
            }
            "new" => {
                self.before();
                self.dom = if text("template") == "classic" {
                    Template::ClassicBaseplate.dom()
                } else {
                    Template::Baseplate.dom()
                };
                self.selection.clear();
                self.changed();
            }
            "project_new" => {
                let path = PathBuf::from(text("path"));
                if path.exists() {
                    return Err("Já existe um projeto com esse nome".into());
                }
                self.save()?;
                self.before();
                self.dom = Template::Baseplate.dom();
                self.path = path;
                self.selection.clear();
                self.changed();
                self.save()?;
            }
            "project_open" => {
                let path = PathBuf::from(text("path"));
                let dom = rbx_viewer::read_place(&path)?;
                self.save()?;
                self.before();
                self.dom = dom;
                self.path = path;
                self.selection.clear();
                self.changed();
                self.dirty = false;
            }
            "open" => {
                self.load(Path::new(text("path")))?;
            }
            "import_model" => self.import_model(Path::new(text("path")))?,
            "save" => {
                self.save()?;
            }
            "export" => {
                save::save(
                    &self.dom,
                    Format::of_path(Path::new(text("path"))),
                    Path::new(text("path")),
                )?;
            }
            "run" => {
                self.run(text("source"))?;
            }
            "check" => {
                rbx_lua::compile_check(text("source"), "Script")?;
                self.logs.push("Luau: compilação concluída".into());
            }
            _ => return Err(format!("Comando desconhecido: {op}")),
        }
        if self.logs.len() > 300 {
            self.logs.drain(..self.logs.len() - 300);
        }
        Ok(())
    }

    pub fn snapshot(&self) -> Value {
        fn walk(editor: &Editor, id: Ref, depth: usize, items: &mut Vec<Value>) {
            if let Some(i) = editor.dom.get(id) {
                items.push(json!({"id":id.value(),"name":i.name(),"class":i.class(),"depth":depth,
                    "selected":editor.selection.contains(&id),"script":source::is_script_class(editor.database(),i.class())}));
                for &child in i.children() {
                    walk(editor, child, depth + 1, items);
                }
            }
        }
        let mut items = Vec::new();
        for &root in self.dom.root_refs() {
            walk(self, root, 0, &mut items);
        }
        let rows = self
            .props
            .rows_matching(&self.dom, &self.selection, "", None);
        let properties: Vec<Value> = rows.iter().map(|row| {
            let editable = self.selection.first().and_then(|id| self.dom.get(*id)).and_then(|i| {
                if row.name == "Name" { Some(i.name().to_owned()) }
                else { i.properties().get(&row.name).and_then(properties::edit::edit_text) }
            }).or_else(|| row.edit.as_ref().map(|_| row.value.clone()));
            json!({"name":row.name,"value":row.value,"category":row.category,"text":editable,"editable":row.edit.is_some() && row.name!=properties::edit::FOLDER_COLOR_PROPERTY})
        }).collect();
        let selected = self.selection.first().copied();
        json!({"tree":items,"properties":properties,"selection":selected.map(|r|r.value()),
            "source":selected.and_then(|id|source::read(&self.dom,id)), "revision":self.revision,
            "dirty":self.dirty,"status":self.status,"logs":self.logs,"tool":self.tool,"local":self.local,"snap":self.snap})
    }
}
