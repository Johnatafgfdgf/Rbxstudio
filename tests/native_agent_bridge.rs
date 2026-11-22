// Test transport using production DOM operations, reflection, compiler and serializer; no renderer or Android MCP socket.
pub fn run_bridge(){
 use std::io::{BufRead,Write};use rbx_dom::{WeakDom,Variant};use rbx_reflection::ReflectionDatabase;use serde_json::{json,Value};
 let db=ReflectionDatabase::shared();let mut dom=WeakDom::new();
 if let Ok(input)=std::env::var("RBX_LIVE_INPUT"){dom=rbx_binary::deserialize(&std::fs::read(input).unwrap()).unwrap();}
 else {let world=dom.new_instance("Workspace","Workspace",None);dom.new_instance("Terrain","Terrain",Some(world));dom.new_instance("Part","Baseplate",Some(world));}
 crate::batch::apply_operations(&mut dom,db,&json!({"operations":[{"op":"ensure_service","class":"StarterGui"},{"op":"ensure_service","class":"StarterPlayerScripts"},{"op":"ensure_service","class":"ServerScriptService"},{"op":"ensure_service","class":"ReplicatedStorage"}]})).unwrap();
 if std::env::var("RBX_LIVE_INJECT_ERROR").ok().as_deref()==Some("true"){
  let parent=crate::batch::lookup(&dom,&json!("ServerScriptService")).unwrap();let script=dom.new_instance("Script","BrokenBootstrap",Some(parent));
  dom.set_property(script,"Source",Variant::String("local value =\nprint(value)".into())).unwrap();
 }
 let file=std::env::var("RBX_LIVE_PLACE").expect("Set RBX_LIVE_PLACE");
 for line in std::io::stdin().lock().lines(){let request:Value=serde_json::from_str(&line.unwrap()).unwrap();let tool=request["tool"].as_str().unwrap();let args=&request["arguments"];
  let result=(||->Result<Value,String>{let revision=format!("0:{}",dom.mutations());
   if tool=="android_asset_capabilities"{return Ok(json!({"checked":false,"asset_write":false,"asset_strategy":"toolbox","reason":"No account key in headless tests"}));}
   if tool=="android_project_snapshot"{let path=crate::batch::snapshot_path(std::path::Path::new(&file),args["snapshot_id"].as_str().ok_or("Missing ID")?)?;let bytes=crate::batch::write_snapshot(&dom,db,&path)?;return Ok(json!({"created":true,"snapshot_id":args["snapshot_id"],"bytes":bytes}));}
   if tool=="android_project_context"{return Ok(json!({"revision":revision,"mode":"Edit","roots":dom.root_refs().iter().map(|r|{let i=dom.get(*r).unwrap();json!({"name":i.name(),"class":i.class(),"instance_id":r.value()})}).collect::<Vec<_>>(),"capabilities":{"snapshot":true},"scope":"Headless DOM integration"}));}
   if tool=="android_preview_project_edit"||tool=="android_apply_project_edit"{if tool=="android_apply_project_edit"&&args["revision"]!=revision{return Err("Project changed after preview".into());}let mut staged=dom.clone();let(report,review)=crate::batch::apply_operations(&mut staged,db,args)?;let changed=staged.mutations()!=dom.mutations();if tool=="android_apply_project_edit"{if review&&args["confirm_delete"]!=true{return Err("Deletion requires explicit confirmation".into());}dom=staged;}return Ok(json!({"revision":revision,"review":review,"operations":report,"applied":tool=="android_apply_project_edit","changed":changed}));}
   if tool=="android_project_save"{let bytes=rbx_binary::serialize_with_defaults(&dom,|c,k|db.stored_default(c,k).cloned()).map_err(|e|e.to_string())?;rbx_binary::deserialize(&bytes).map_err(|e|e.to_string())?;std::fs::write(&file,&bytes).map_err(|e|e.to_string())?;return Ok(json!({"saved":true,"bytes":bytes.len(),"revision":revision}));}
   if tool=="android_editor_schema"{return crate::batch::schema(db,args);}
   if tool=="get_studio_state"{return Ok(json!({"mode":"Edit","scope":"No runtime in headless test"}));}
   let mut all=Vec::new();let mut pending=dom.root_refs().to_vec();while let Some(r)=pending.pop(){if let Some(i)=dom.get(r){all.push(r);pending.extend_from_slice(i.children());}}
   if tool=="search_game_tree"||tool=="script_search"{let prefix=args["path"].as_str().unwrap_or("");let q=args["keywords"].as_str().unwrap_or("").to_lowercase();return Ok(json!(all.iter().filter_map(|r|{let i=dom.get(*r)?;let path=crate::batch::full_name(&dom,*r);(path.starts_with(prefix)&&i.name().to_lowercase().contains(&q)&&(tool!="script_search"||crate::script_source::is_script(&dom,db,*r))).then(||json!({"name":i.name(),"className":i.class(),"fullPath":path,"instance_id":r.value()}))}).take(100).collect::<Vec<_>>()));}
   if tool=="inspect_instance"{let r=crate::batch::lookup(&dom,&args["path"])?;let i=dom.get(r).unwrap();let properties:serde_json::Map<String,Value>=i.properties().iter().map(|(k,v)|{let v=match v{Variant::Bool(v)=>json!(v),Variant::Float32(v)=>json!(v),Variant::Float64(v)=>json!(v),Variant::String(v)=>json!(v),_=>json!(format!("{v:?}"))};(k.clone(),v)}).collect();return Ok(json!({"path":crate::batch::full_name(&dom,r),"className":i.class(),"instance_id":r.value(),"properties":properties,"children":i.children().iter().map(|r|crate::batch::full_name(&dom,*r)).collect::<Vec<_>>()}));}
   if tool=="script_read"{let r=crate::batch::lookup(&dom,&args["target_file"])?;let text=crate::script_source::read(&dom,r).ok_or("Script missing")?;let start=args["start_line_one_indexed"].as_u64().unwrap_or(1).max(1) as usize;return Ok(json!(text.lines().enumerate().skip(start-1).take(120).map(|(i,line)|format!("{:6}→{}",i+1,line)).collect::<Vec<_>>().join("\n")));}
   if tool=="android_project_diagnostics"{return Ok(json!({"errors":all.iter().filter_map(|r|{let code=crate::script_source::read(&dom,*r)?;let path=crate::batch::full_name(&dom,*r);rbx_lua::compile_check(&code,&path).err().map(|e|json!({"path":path,"error":e}))}).collect::<Vec<_>>(),"complete":true,"scope":"Syntax only; no Play"}));}
   Err(format!("Unavailable in headless test: {tool}"))
  })();println!("{}",match result{Ok(v)=>json!({"result":v}),Err(e)=>json!({"error":e})});std::io::stdout().flush().unwrap();
 }
}
