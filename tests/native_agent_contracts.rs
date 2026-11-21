#[cfg(test)]
mod tests {
    use rbx_dom::{Variant, WeakDom};
    use rbx_reflection::ReflectionDatabase;
    use serde_json::json;
    use crate::batch::apply_operations;

    #[test]
    fn inventory_cross_service_roundtrip_and_existing_object_ids() {
        let db=ReflectionDatabase::shared();
        let mut dom=WeakDom::new();
        let world=dom.new_instance("Workspace","Workspace",None);
        let existing=dom.new_instance("Part","Existing.Part",Some(world));
        let ops=json!({"allow_scripts":true,"operations":[
            {"op":"set","path":{"instance_id":existing.value()},"properties":{"Anchored":true,"Size":"4, 12, 4","CFrame":"0, 6, 0"}},
            {"op":"attribute","path":["Workspace","Existing.Part"],"name":"Wind","type":"Vector3","value":"1, 2, 3"},
            {"op":"ensure_service","class":"CollectionService"},
            {"op":"tag","path":["Workspace","Existing.Part"],"name":"WindAffected","enabled":true},
            {"op":"ensure_service","class":"StarterGui"},
            {"op":"ensure_service","class":"ReplicatedStorage"},
            {"op":"ensure_service","class":"ServerScriptService"},
            {"op":"ensure_service","class":"StarterPlayerScripts"},
            {"op":"create","class":"ScreenGui","parent":"StarterGui","name":"Inventory"},
            {"op":"create","class":"TextLabel","parent":"StarterGui.Inventory","name":"Title","properties":{"Text":"Inventário","Size":"1, 0, 0, 44"}},
            {"op":"create","class":"RemoteEvent","parent":"ReplicatedStorage","name":"InventoryRequest"},
            {"op":"create","class":"ModuleScript","parent":"ReplicatedStorage","name":"Items","source":"return {Sword = {Price = 100}}"},
            {"op":"create","class":"Script","parent":"ServerScriptService","name":"InventoryServer","source":"local remote = game:GetService('ReplicatedStorage'):WaitForChild('InventoryRequest')
remote.OnServerEvent:Connect(function(player) print(player.Name) end)"},
            {"op":"create","class":"LocalScript","parent":"StarterPlayer.StarterPlayerScripts","name":"InventoryClient","source":"local label: string = 'Inventory'
print(label)"},
            {"op":"edit_script","path":"StarterPlayer.StarterPlayerScripts.InventoryClient","old":"print(label)","new":"print(label .. '!')"}
        ]});
        let (report,review)=apply_operations(&mut dom,db,&ops).unwrap();
        assert_eq!(report.len(),15);
        assert!(!review);
        let bytes=rbx_binary::serialize_with_defaults(&dom,|class,key| db.stored_default(class,key).cloned()).unwrap();
        let reopened=rbx_binary::deserialize(&bytes).unwrap();
        let world=reopened.root_refs().iter().filter_map(|r|reopened.get(*r)).find(|i|i.class()=="Workspace").unwrap();
        let part=reopened.get(world.children()[0]).unwrap();
        assert_eq!(part.name(),"Existing.Part");
        assert!(part.tags().contains(&"WindAffected"));
        assert_eq!(part.properties().get("Anchored"),Some(&Variant::Bool(true)));
        assert!(matches!(part.attributes().get("Wind"),Some(Variant::Vector3(v)) if v.y==2.0));
    }

    #[test]
    fn contextual_effect_arguments_are_preserved() {
        let db=ReflectionDatabase::shared();
        let mut dom=WeakDom::new();
        let world=dom.new_instance("Workspace","Workspace",None);
        dom.new_instance("Part","Roof",Some(world));
        for (name,rate) in [("LightRain",12),("CinematicRain",120),("RoofRain",45)] {
            let ops=json!({"operations":[{"op":"create","class":"ParticleEmitter","parent":"Workspace.Roof","name":name,"properties":{"Rate":rate}}]});
            apply_operations(&mut dom,db,&ops).unwrap();
        }
        let roof=dom.get(dom.get(world).unwrap().children()[0]).unwrap();
        let rates:Vec<_>=roof.children().iter().map(|r| dom.get(*r).unwrap().properties()["Rate"].clone()).collect();
        assert_eq!(rates,vec![Variant::Float32(12.0),Variant::Float32(120.0),Variant::Float32(45.0)]);
    }

    #[test]
    fn animation_and_terrain_edits_are_persisted() {
        let db=ReflectionDatabase::shared();
        let mut dom=WeakDom::new();
        let world=dom.new_instance("Workspace","Workspace",None);
        dom.new_instance("Terrain","Terrain",Some(world));
        let (_,review)=apply_operations(&mut dom,db,&json!({"operations":[
            {"op":"create","class":"KeyframeSequence","parent":"Workspace","name":"Wave","properties":{"Loop":true}},
            {"op":"create","class":"Keyframe","parent":"Workspace.Wave","name":"Step","properties":{"Time":0.5}},
            {"op":"create","class":"Pose","parent":"Workspace.Wave.Step","name":"UpperTorso","properties":{"CFrame":"0, 1, 0"}},
            {"op":"terrain","center":[8,8,8],"size":[16,16,16],"material":"Grass"}
        ]})).unwrap();
        assert!(!review);
        let bytes=rbx_binary::serialize_with_defaults(&dom,|class,key| db.stored_default(class,key).cloned()).unwrap();
        let reopened=rbx_binary::deserialize(&bytes).unwrap();
        let terrain=crate::terrain::find_terrain(&reopened).unwrap();
        let grid=rbx_terrain::smooth_grid::decode(crate::terrain::grid_bytes(&reopened,terrain)).unwrap();
        assert_eq!(grid.get([2,2,2]).material,rbx_terrain::Material::Grass);
        let world=reopened.root_refs().iter().filter_map(|r|reopened.get(*r)).find(|i|i.class()=="Workspace").unwrap();
        let animation=world.children().iter().filter_map(|r|reopened.get(*r)).find(|i|i.name()=="Wave").unwrap();
        assert_eq!(animation.class(),"KeyframeSequence");
        assert_eq!(reopened.get(animation.children()[0]).unwrap().properties().get("Time"),Some(&Variant::Float32(0.5)));
    }

    #[test]
    fn failed_script_batch_is_atomic_before_install() {
        let db=ReflectionDatabase::shared();
        let mut original=WeakDom::new();
        let world=original.new_instance("Workspace","Workspace",None);
        original.new_instance("Folder","Keep",Some(world));
        let revision=original.mutations();
        let mut staged=original.clone();
        let result=apply_operations(&mut staged,db,&json!({"allow_scripts":true,"operations":[
            {"op":"rename","path":"Workspace.Keep","name":"Staged"},
            {"op":"create","class":"Script","parent":"Workspace","name":"Bad","source":"import pygame
def rain():
    pass"}
        ]}));
        assert!(result.unwrap_err().contains("Script Luau"));
        assert_eq!(original.mutations(),revision);
        assert_eq!(original.get(original.get(world).unwrap().children()[0]).unwrap().name(),"Keep");
        let (_,review)=apply_operations(&mut original.clone(),db,&json!({"operations":[{"op":"delete","path":"Workspace.Keep"}]})).unwrap();
        assert!(review);
    }

    #[test]
    fn untrusted_asset_is_inspected_before_grafting() {
        let db=ReflectionDatabase::shared();
        let mut asset=WeakDom::new();
        let model=asset.new_instance("Model","Downloaded",None);
        let part=asset.new_instance("Part","RetainedPart",Some(model));
        let code=asset.new_instance("ModuleScript","UntrustedSource",Some(model));
        let package=asset.new_instance("PackageLink","Updates",Some(model));
        assert_eq!(crate::batch::remove_asset_sources(&mut asset,db).unwrap(),2);
        assert!(asset.get(part).is_some());
        assert!(asset.get(code).is_none() && asset.get(package).is_none());
    }
}

#[cfg(test)]
mod generated_regressions {
    use rbx_dom::WeakDom;
    use rbx_reflection::ReflectionDatabase;
    use serde_json::json;
    fn count(dom:&WeakDom)->usize { let mut pending=dom.root_refs().to_vec();let mut n=0;while let Some(r)=pending.pop(){if let Some(i)=dom.get(r){n+=1;pending.extend_from_slice(i.children());}}n }
    fn generated(name:&str){
        let fixtures:serde_json::Value=serde_json::from_slice(&std::fs::read(std::env::var("RBX_GENERATOR_FIXTURES").expect("Generate Java fixtures first")).unwrap()).unwrap();
        let fixture=fixtures.as_array().unwrap().iter().find(|f|f["generator"]==name).unwrap();
        let db=ReflectionDatabase::shared();let mut dom=WeakDom::new();let world=dom.new_instance("Workspace","Workspace",None);dom.new_instance("Terrain","Terrain",Some(world));
        crate::batch::apply_operations(&mut dom,db,&json!({"operations":[{"op":"ensure_service","class":"StarterGui"},{"op":"ensure_service","class":"StarterPlayerScripts"}]})).unwrap();
        let ops=fixture["operations"].as_array().unwrap();let mut index=0;
        while index<ops.len(){
            let mut end=(index+32).min(ops.len());
            if ops[index]["op"]=="terrain"{end=index+1;}else if let Some(offset)=ops[index..end].iter().position(|op|op["op"]=="terrain"){end=index+offset;}
            let mut staged=dom.clone();let(report,review)=crate::batch::apply_operations(&mut staged,db,&json!({"allow_scripts":true,"operations":&ops[index..end]})).unwrap_or_else(|e|panic!("{name}: {e}"));
            assert!(!review);assert_eq!(report.len(),end-index);dom=staged;index=end;
        }
        let bytes=rbx_binary::serialize_with_defaults(&dom,|c,k|db.stored_default(c,k).cloned()).unwrap();let reopened=rbx_binary::deserialize(&bytes).unwrap();assert_eq!(count(&reopened),count(&dom));
        if name=="terrain"{let terrain=crate::terrain::find_terrain(&reopened).unwrap();assert!(!crate::terrain::grid_bytes(&reopened,terrain).is_empty());}
        eprintln!("{name}: {} real operations, {} persisted objects, {} bytes",ops.len(),count(&reopened),bytes.len());
    }
    #[test]fn terrain(){generated("terrain");}
    #[test]fn building(){generated("building");}
    #[test]fn scatter(){generated("scatter");}
    #[test]fn layered_vfx(){generated("vfx");}
    #[test]fn ui_with_callback(){generated("ui");}
    #[test]fn editable_visual_builder(){generated("visual_ui");}
    #[test]fn animation(){generated("animation");}
    #[test]fn camera(){generated("camera");}
    #[test]fn spatial_audio(){generated("audio");}
    #[test]
    fn gui_literals_source_property_and_empty_source_replacement_are_real() {
        use crate::batch::apply_operations;
        let db=ReflectionDatabase::shared();let mut dom=WeakDom::new();
        apply_operations(&mut dom,db,&json!({"allow_scripts":true,"operations":[
          {"op":"ensure_service","class":"StarterGui"},
          {"op":"create","class":"ScreenGui","name":"Menu","parent":"StarterGui"},
          {"op":"create","class":"Frame","name":"Panel","parent":"StarterGui.Menu","properties":{"Size":"UDim2.new(0.85,0,0.8,0)","BackgroundColor3":"Color3.fromRGB(17,30,48)"}},
          {"op":"create","class":"LocalScript","name":"Logic","parent":"StarterGui.Menu","properties":{"Source":"script.Parent.Enabled = false"}}
        ]})).unwrap();
        let path=json!("StarterGui.Menu.Logic");let target=crate::batch::lookup(&dom,&path).unwrap();
        assert_eq!(crate::script_source::read(&dom,target).unwrap(),"script.Parent.Enabled = false");
        assert!(apply_operations(&mut dom.clone(),db,&json!({"operations":[{"op":"set","path":path,"properties":{"Source":"print(1)"}}]})).is_err());
        assert!(apply_operations(&mut dom.clone(),db,&json!({"allow_scripts":true,"operations":[{"op":"set","path":path,"properties":{"Source":"local = bad"}}]})).is_err());
        assert!(apply_operations(&mut dom.clone(),db,&json!({"operations":[{"op":"set","path":"StarterGui.Menu.Panel","properties":{"Size":"UDim2.new(os.execute(),0,1,0)"}}]})).is_err());
        let bytes=rbx_binary::serialize_with_defaults(&dom,|c,k|db.stored_default(c,k).cloned()).unwrap();
        let reopened=rbx_binary::deserialize(&bytes).unwrap();let r=crate::batch::lookup(&reopened,&path).unwrap();
        assert_eq!(crate::script_source::read(&reopened,r).unwrap(),"script.Parent.Enabled = false");
        apply_operations(&mut dom,db,&json!({"allow_scripts":true,"operations":[{"op":"set","path":path,"properties":{"Source":""}},{"op":"script_replace","path":path,"old":"","source":"print('filled')"}]})).unwrap();
        assert_eq!(crate::script_source::read(&dom,target).unwrap(),"print('filled')");
    }

    #[test]fn task_snapshot_reopens_and_refreshes_the_same_dom() {
        let db=ReflectionDatabase::shared();let mut dom=WeakDom::new();
        let workspace=dom.new_instance("Workspace","Workspace",None);dom.new_instance("Part","Existing",Some(workspace));
        let folder=std::env::temp_dir().join(format!("rbx-snapshot-{}",std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        std::fs::create_dir_all(&folder).unwrap();let project=folder.join("Test.rbxl");let id="12345678-1234-1234-1234-123456789abc";
        let path=crate::batch::snapshot_path(&project,id).unwrap();
        assert!(crate::batch::snapshot_path(&project,"../../bad").is_err());
        crate::batch::write_snapshot(&dom,db,&path).unwrap();
        dom.new_instance("Part","Generated",Some(workspace));
        let restored=crate::batch::read_snapshot(&path).unwrap();
        let changes=crate::batch::full_refresh_changes(&dom,&restored);
        dom.mirror(restored.snapshot(&changes));
        assert!(crate::batch::lookup(&dom,&json!("Workspace.Generated")).is_err());
        assert!(crate::batch::lookup(&dom,&json!("Workspace.Existing")).is_ok());
        assert!(crate::batch::write_snapshot(&dom,db,&path).is_err());
        std::fs::remove_dir_all(folder).unwrap();
    }

    #[test]fn legacy_gui_aliases_write_the_actual_serialized_properties() {
        use crate::batch::apply_operations;
        let db=ReflectionDatabase::shared();let mut dom=WeakDom::new();
        apply_operations(&mut dom,db,&json!({"operations":[
            {"op":"ensure_service","class":"StarterGui"},
            {"op":"create","parent":"StarterGui","class":"ScreenGui","name":"Shop","properties":{"IgnoreGuiInset":true}},
            {"op":"create","parent":"StarterGui.Shop","class":"TextLabel","name":"Title","properties":{"Font":"Antique","Text":"Shop"}},
            {"op":"create","parent":"StarterGui.Shop.Title","class":"UICorner","name":"Round","properties":{"CornerRadius":"0,12"}}
        ]})).unwrap();
        let bytes=rbx_binary::serialize_with_defaults(&dom,|c,k|db.stored_default(c,k).cloned()).unwrap();let saved=rbx_binary::deserialize(&bytes).unwrap();
        let title=crate::batch::lookup(&saved,&json!("StarterGui.Shop.Title")).unwrap();
        assert!(matches!(saved.get(title).unwrap().properties().get("FontFace"),Some(rbx_dom::Variant::Font(_))));
        let corner=crate::batch::lookup(&saved,&json!("StarterGui.Shop.Title.Round")).unwrap();
        for key in ["TopLeftRadius","TopRightRadius","BottomLeftRadius","BottomRightRadius"]{assert!(matches!(saved.get(corner).unwrap().properties().get(key),Some(rbx_dom::Variant::UDim(value)) if value.offset==12));}
    }

}
