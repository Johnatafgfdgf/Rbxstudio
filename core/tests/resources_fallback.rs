use rbx_android_core::resources::{Preparation, StudioResources};
use std::{sync::{Arc, atomic::{AtomicUsize, Ordering}}, time::{Duration, Instant}};

#[test]
fn offline_core_patch_preparation_cannot_enter_the_global_online_fallback() {
    let calls = Arc::new(AtomicUsize::new(0));
    let counted = calls.clone();
    assert!(rbx_assets::set_android_resource_fallback(move |_| {
        counted.fetch_add(1, Ordering::SeqCst);
        Some(b"downloaded resource".to_vec())
    }));
    let local = rbx_assets::AndroidContent::new(vec![]);
    assert!(local.read_local("textures/probe.png").unwrap().is_none());
    assert_eq!(calls.load(Ordering::SeqCst), 0);
    assert_eq!(local.read("textures/probe.png").unwrap().unwrap(), b"downloaded resource");
    let before = calls.load(Ordering::SeqCst);
    let temp = tempfile::tempdir().unwrap();
    let resources = Arc::new(StudioResources::new(temp.path().into(), vec![], false));
    assert!(resources.prepare(false));
    let deadline = Instant::now() + Duration::from_secs(3);
    while matches!(resources.state(), Preparation::Loading) && Instant::now() < deadline {
        std::thread::sleep(Duration::from_millis(1));
    }
    assert!(matches!(resources.state(), Preparation::Failed(_)));
    assert_eq!(calls.load(Ordering::SeqCst), before, "An offline resolver reached the online fallback");
}
