//! Small, reproducible compatibility scenarios using the actual Play VMs and
//! physics. No credentials, publication or current editor document are used.
use rook_engine::{DataModel, InputEvent, Options, TouchPhase, session::Session};
use rook_lua::MessageType;
use serde_json::{Value, json};

fn source(dm: &DataModel, code: &str) -> Result<(), String> {
    dm.run_extra_script(code)?;
    errors(dm)
}

fn errors(dm: &DataModel) -> Result<(), String> {
    let errors: Vec<_> = dm
        .take_log()
        .into_iter()
        .filter(|entry| entry.kind == MessageType::Error)
        .map(|entry| entry.message)
        .collect();
    if errors.is_empty() {
        Ok(())
    } else {
        Err(errors.join("\n"))
    }
}

fn step(session: &mut Session, seconds: f64) -> Result<(), String> {
    session.run_for(seconds);
    errors(session.server())?;
    errors(session.client(0).ok_or("Cliente ausente")?)
}

fn create(code: &str) -> Result<Session, String> {
    let options = Options {
        touch_input: true,
        initial_viewport: Some((960, 480)),
        memory_limit: 128 * 1024 * 1024,
        ..Options::default()
    };
    let mut server = DataModel::new(&options).map_err(|e| e.to_string())?;
    source(&server, code)?;
    server.start();
    let mut session = Session::new(server, options);
    session
        .join("Compatibilidade", 1)
        .map_err(|e| e.to_string())?;
    step(&mut session, 0.2)?;
    Ok(session)
}

pub const CASES: &[(&str, &str)] = &[
    (
        "tool_aim",
        "Mouse.Hit e Target da Tool usam a posição atual do toque",
    ),
    (
        "tool_delivery",
        "GUI → RemoteEvent → Backpack → equipar → usar → guardar",
    ),
    (
        "local_tool",
        "Tool criada no cliente: encaixe, Grip, eventos e remoção",
    ),
    (
        "mover_constraints",
        "LinearVelocity, AngularVelocity e alinhamentos movem assemblies",
    ),
    (
        "runtime_objects",
        "ModuleScript, task, eventos, Tween e objetos criados durante Play",
    ),
    (
        "constraint_modes",
        "Eixos livres, limites, Attachment1, reação e Attachments dinâmicos",
    ),
    (
        "humanoid_forces",
        "Constraints no assembly do Humanoid, estado Physics e retorno à queda",
    ),
    (
        "inventory_respawn",
        "StarterPack e StarterGear repõem inventário a cada respawn",
    ),
];

pub fn run_case(id: &str) -> Result<Vec<String>, String> {
    let mut stages = Vec::new();
    let result = (|| -> Result<(), String> {
        match id {
            "tool_aim" => {
                let mut s = create(
                    "local p=Instance.new('Part',workspace); p.Name='AimTarget'; p.Anchored=true; p.Size=Vector3.new(5,5,2); p.Position=Vector3.new(0,6,-8)",
                )?;
                source(
                    s.client(0).unwrap(),
                    include_str!("diagnostics/tool_aim.luau"),
                )?;
                step(&mut s, 0.1)?;
                for phase in [TouchPhase::Begin, TouchPhase::End] {
                    s.input(
                        0,
                        InputEvent::Touch {
                            id: 905,
                            phase,
                            x: 480.,
                            y: 240.,
                        },
                    );
                }
                step(&mut s, 0.1)?;
                source(
                    s.client(0).unwrap(),
                    "assert(workspace:GetAttribute('AimUses')==1, 'Activated não recebeu Mouse.Hit e Target atualizados')",
                )?;
                stages.push("Raycast do Mouse atualizado antes de Tool.Activated: passou".into());
            }
            "tool_delivery" => {
                let mut s = create(include_str!("diagnostics/tool_server.luau"))?;
                source(
                    s.client(0).unwrap(),
                    include_str!("diagnostics/tool_client.luau"),
                )?;
                step(&mut s, 0.1)?;
                for phase in [TouchPhase::Begin, TouchPhase::End] {
                    s.input(
                        0,
                        InputEvent::Touch {
                            id: 901,
                            phase,
                            x: 60.,
                            y: 45.,
                        },
                    );
                }
                step(&mut s, 0.2)?;
                source(
                    s.client(0).unwrap(),
                    "assert(game.Players.LocalPlayer.Backpack:FindFirstChild('DiagnosticTool'), 'O botão não entregou a Tool ao Backpack')",
                )?;
                stages.push("Entrega pelo botão e replicação: passou".into());
                source(
                    s.client(0).unwrap(),
                    "assert(game:GetService('CoreGui').RbxMobileInventory.Hotbar:FindFirstChild('Slot1'), 'Tool não apareceu no inventário'); assert(not game.Players.LocalPlayer.PlayerGui:FindFirstChild('RbxMobileInventory'), 'Inventário alterou a hierarquia de interfaces do jogo')",
                )?;
                for phase in [TouchPhase::Begin, TouchPhase::End] {
                    s.input(
                        0,
                        InputEvent::Touch {
                            id: 903,
                            phase,
                            x: 240.,
                            y: 425.,
                        },
                    );
                }
                step(&mut s, 0.2)?;
                source(s.server(), include_str!("diagnostics/tool_held.luau"))?;
                source(
                    s.client(0).unwrap(),
                    include_str!("diagnostics/tool_held.luau"),
                )?;
                stages.push("Equipar e junta RightGrip nos dois lados: passou".into());
                for phase in [TouchPhase::Begin, TouchPhase::End] {
                    s.input(
                        0,
                        InputEvent::Touch {
                            id: 902,
                            phase,
                            x: 740.,
                            y: 200.,
                        },
                    );
                }
                step(&mut s, 0.2)?;
                source(
                    s.server(),
                    "assert(workspace:GetAttribute('ToolUses')==1, 'Activated não chegou ao Script servidor'); assert(workspace:GetAttribute('ToolReleases')==1, 'Deactivated não chegou ao servidor')",
                )?;
                stages.push("Uso por toque, Activated e Deactivated no servidor: passou".into());
                for phase in [TouchPhase::Begin, TouchPhase::End] {
                    s.input(
                        0,
                        InputEvent::Touch {
                            id: 904,
                            phase,
                            x: 240.,
                            y: 425.,
                        },
                    );
                }
                step(&mut s, 0.2)?;
                source(
                    s.server(),
                    "local p=game.Players:GetPlayers()[1]; assert(p.Backpack:FindFirstChild('DiagnosticTool'), 'A Tool não voltou ao Backpack'); assert(workspace:GetAttribute('ToolUnequips')==1, 'Unequipped ausente')",
                )?;
                stages.push("Guardar e Unequipped: passou".into());
                source(
                    s.client(0).unwrap(),
                    "local p=game.Players.LocalPlayer; p.Character.Humanoid:EquipTool(p.Backpack.DiagnosticTool)",
                )?;
                step(&mut s, 0.2)?;
                source(
                    s.client(0).unwrap(),
                    "local c=game.Players.LocalPlayer.Character; local hand=c:FindFirstChild('RightHand') or c:FindFirstChild('Right Arm'); c.DiagnosticTool:Destroy(); assert(not hand:FindFirstChild('RightGrip'), 'Destroy local da Tool replicada deixou junta fantasma')",
                )?;
                stages.push("Destruição local da Tool replicada remove a junta: passou".into());
            }
            "local_tool" => {
                let mut s = create("")?;
                source(
                    s.client(0).unwrap(),
                    include_str!("diagnostics/local_tool.luau"),
                )?;
                step(&mut s, 0.25)?;
                source(
                    s.client(0).unwrap(),
                    include_str!("diagnostics/local_tool_check.luau"),
                )?;
                stages.push("Tool local, Grip alterado em execução, uso e limpeza: passou".into());
                source(
                    s.server(),
                    "local p=game.Players:GetPlayers()[1]; assert(not p.Backpack:FindFirstChild('ClientOnly')); assert(not p.Character:FindFirstChild('ClientOnly'))",
                )?;
                stages.push("Isolamento cliente/servidor preservado: passou".into());
            }
            "mover_constraints" => {
                let mut s = create(include_str!("diagnostics/movers.luau"))?;
                step(&mut s, 1.0)?;
                source(s.server(), include_str!("diagnostics/movers_check.luau"))?;
                stages.push("Velocidade linear, angular, posição e orientação: passou".into());
                source(
                    s.server(),
                    "workspace.MoverVelocity.LinearVelocity.Enabled=false; workspace.MoverVelocity.AssemblyLinearVelocity=Vector3.zero",
                )?;
                step(&mut s, 0.1)?;
                source(
                    s.server(),
                    "assert(workspace.MoverVelocity.AssemblyLinearVelocity.Y < -5, 'Desativar constraint não devolveu a gravidade')",
                )?;
                stages.push("Desativação e retorno à física livre: passou".into());
            }
            "runtime_objects" => {
                let mut s = create(include_str!("diagnostics/runtime.luau"))?;
                step(&mut s, 0.6)?;
                source(
                    s.server(),
                    "assert(workspace:GetAttribute('RuntimeDone')==true, 'task/Tween/eventos não concluíram')",
                )?;
                source(
                    s.client(0).unwrap(),
                    "local p=workspace:FindFirstChild('RuntimePart'); assert(p and p.Color==Color3.new(1,0,0) and math.abs(p.Transparency-0.5)<0.01, 'Propriedades não chegaram ao cliente')",
                )?;
                stages.push("require, task, Clone/Destroy, Tween e replicação: passou".into());
            }
            "constraint_modes" => {
                let mut s = create(include_str!("diagnostics/constraint_modes.luau"))?;
                step(&mut s, 1.0)?;
                source(
                    s.server(),
                    include_str!("diagnostics/constraint_modes_check.luau"),
                )?;
                stages.push("Line/Plane, força limitada, referência móvel, reação e alteração do Attachment: passou".into());
            }
            "humanoid_forces" => {
                let mut s = create("")?;
                source(
                    s.client(0).unwrap(),
                    include_str!("diagnostics/humanoid_forces.luau"),
                )?;
                step(&mut s, 0.5)?;
                source(
                    s.client(0).unwrap(),
                    "local r=game.Players.LocalPlayer.Character.HumanoidRootPart; assert(r.Position.Y > r:GetAttribute('StartY')+5, 'Constraint não moveu o Humanoid'); assert(math.abs(r.AssemblyLinearVelocity.Y-14)<0.3, 'Velocidade do Humanoid incompatível')",
                )?;
                source(
                    s.server(),
                    "assert(game.Players:GetPlayers()[1].Character.HumanoidRootPart.Position.Y > 16, 'Movimento do personagem não chegou ao servidor')",
                )?;
                stages.push("Constraint move o Humanoid e a pose chega ao servidor: passou".into());
                source(
                    s.client(0).unwrap(),
                    "local c=game.Players.LocalPlayer.Character; c.HumanoidRootPart.DiagnosticLinearVelocity:Destroy(); c.Humanoid.AutoRotate=true; c.Humanoid:ChangeState(Enum.HumanoidStateType.Freefall)",
                )?;
                step(&mut s, 0.2)?;
                source(
                    s.client(0).unwrap(),
                    "assert(game.Players.LocalPlayer.Character.HumanoidRootPart.AssemblyLinearVelocity.Y < 0, 'Remover constraint não devolveu a queda ao Humanoid')",
                )?;
                stages.push("Remoção da constraint e retorno ao controlador: passou".into());
            }
            "inventory_respawn" => {
                let mut s = create(
                    "local t=Instance.new('Tool', game:GetService('StarterPack')); t.Name='PackTool'; t.RequiresHandle=false",
                )?;
                source(
                    s.server(),
                    "local p=game.Players:GetPlayers()[1]; assert(p:FindFirstChildOfClass('StarterGear'), 'StarterGear ausente'); local t=Instance.new('Tool',p.StarterGear); t.Name='PersonalTool'; t.RequiresHandle=false; p:LoadCharacter()",
                )?;
                for _ in 0..2 {
                    step(&mut s, 0.3)?;
                    for dm in [s.server(), s.client(0).unwrap()] {
                        source(
                            dm,
                            "local p=game.Players.LocalPlayer or game.Players:GetPlayers()[1]; assert(p.Backpack:FindFirstChild('PackTool'), 'StarterPack não foi copiado'); assert(p.Backpack:FindFirstChild('PersonalTool'), 'StarterGear não foi copiado'); assert(#p.Backpack:GetChildren()==2, 'Inventário duplicado ou incompleto'); assert(p.StarterGear.PersonalTool~=p.Backpack.PersonalTool, 'StarterGear foi movido em vez de clonado')",
                        )?;
                    }
                    source(s.server(), "game.Players:GetPlayers()[1]:LoadCharacter()")?;
                }
                s.client_mut(0).unwrap().set_viewport(1920, 960);
                step(&mut s, 0.4)?;
                source(s.client(0).unwrap(), "local gui=game:GetService('CoreGui').RbxMobileInventory; assert(gui.Hotbar.Slot1.AbsoluteSize.X>=115 and gui.Hotbar.Slot1.AbsoluteSize.Y>=119, 'Hotbar não acompanhou a resolução de toque'); game:GetService('StarterGui'):SetCoreGuiEnabled(Enum.CoreGuiType.Backpack,false)")?;
                step(&mut s, 0.3)?;
                source(s.client(0).unwrap(), "assert(not game:GetService('CoreGui').RbxMobileInventory.Enabled, 'Inventário ignorou CoreGuiEnabled')")?;
                stages.push("Respawns, clones, replicação, escala de toque e inventário desativado pelo jogo: passou".into());
            }
            _ => return Err("Teste desconhecido".into()),
        }
        Ok(())
    })();
    result
        .map(|()| stages.clone())
        .map_err(|error| format!("{}\nFalha: {error}", stages.join("\n")))
}

pub fn run() -> Value {
    let started = std::time::Instant::now();
    let cases: Vec<_> = CASES.iter().map(|(id, name)| {
        let result = std::panic::catch_unwind(|| run_case(id));
        match result {
            Ok(Ok(stages)) => json!({"id":id,"name":name,"passed":true,"stages":stages}),
            Ok(Err(error)) => json!({"id":id,"name":name,"passed":false,"error":error}),
            Err(_) => json!({"id":id,"name":name,"passed":false,"error":"Falha nativa durante o cenário"}),
        }
    }).collect();
    let passed = cases.iter().filter(|c| c["passed"] == true).count();
    json!({"version":"0.6.7", "scope":"VMs e física locais; não mede paridade integral nem a GPU do aparelho",
        "passed":passed,"failed":cases.len()-passed,"elapsed_ms":started.elapsed().as_millis(),"cases":cases})
}
