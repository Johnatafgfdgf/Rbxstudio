use rbx_android_core::resources::{Preparation, StudioResources};
use rook_content::ContentRef;
use std::{fs, sync::Arc, time::{Duration, Instant}};

#[test]
fn both_engines_read_the_same_cached_original_studio_packages_offline() {
    let temp = tempfile::tempdir().unwrap();
    let texture = temp.path().join("studio-packages/textures/probe.png");
    fs::create_dir_all(texture.parent().unwrap()).unwrap();
    fs::write(&texture, b"package-texture").unwrap();
    let resources = StudioResources::new(temp.path().into(), vec![], false);
    assert!(resources.play_options().is_err(), "Play must not silently omit pending CoreScripts");
    assert_eq!(resources.read_package("textures/probe.png").unwrap(), b"package-texture");
    let content = resources.resolver().resolve(&ContentRef::File("textures/probe.png".into())).unwrap();
    assert_eq!(&*content.bytes, b"package-texture");
    assert!(resources.read_package("textures/../../secret").is_none());
    assert!(resources.read_package("/textures/probe.png").is_none());
}

#[test]
fn invalid_core_patch_is_reported_and_retry_keeps_other_cached_resources() {
    let temp = tempfile::tempdir().unwrap();
    let model = temp.path().join("studio-packages/ExtraContent/models/DataModelPatch/DataModelPatch.rbxm");
    fs::create_dir_all(model.parent().unwrap()).unwrap();
    fs::write(&model, b"not a model").unwrap();
    let resources = Arc::new(StudioResources::new(temp.path().into(), vec![], false));
    for retry in [false, true] {
        assert!(resources.prepare(retry));
        let due = Instant::now() + Duration::from_secs(3);
        while matches!(resources.state(), Preparation::Loading) && Instant::now() < due {
            std::thread::sleep(Duration::from_millis(1));
        }
        assert!(matches!(resources.state(), Preparation::Failed(_)));
        assert!(resources.core_patch().is_none());
        assert!(resources.play_options().is_err(), "Failed CoreScripts must not produce a reduced Play");
        assert!(model.is_file());
    }
}
