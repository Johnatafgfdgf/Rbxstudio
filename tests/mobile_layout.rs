#[path = "../upstream/rbx-native/crates/rbx_studio/src/shell/mobile_metrics.rs"]
mod metrics;
#[path = "../upstream/rbx-native/crates/rbx_studio/src/shell/revision_cache.rs"]
mod cache;
#[test]
fn landscape_drawer_keeps_scene_usable_even_after_resize() {
    for width in [700., 904., 960., 1280.] {
        for requested in [0., 100., 300., 9999.] {
            let drawer = metrics::drawer_width(width, requested, false);
            assert!(drawer >= 260. && drawer <= 380.);
            assert!(width - drawer >= width * 0.6 - 0.01);
        }
    }
}
#[test]
fn narrow_and_expanded_panels_use_an_overlay() {
    for width in [320., 360., 407., 432., 699.] {
        assert_eq!(metrics::drawer_width(width, 340., false), width);
        assert!(metrics::compact_tools(width));
    }
    assert!(!metrics::compact_tools(904.));
    assert_eq!(metrics::drawer_width(904., 340., true), 904.);
}
#[test]
fn output_clamps_after_ime_and_window_resize() {
    for height in [0., 40., 96., 180., 360., 407., 432.] {
        for preferred in [0., 150., 9999.] {
            let actual = metrics::output_height(height, preferred, false);
            assert!(actual >= 0. && actual <= height);
            if height >= 150. { assert!(actual <= height * 0.65); }
        }
        assert_eq!(metrics::output_height(height, 150., true), height);
    }
}
#[test]
fn camera_redraw_reuses_guides_but_expansion_invalidates() {
    let mut cache = cache::RevisionCache::default();
    let first = cache.get(0, || (0..10000).collect::<Vec<_>>());
    for _ in 0..1000 {
        let again = cache.get(0, || panic!("unchanged tree must not rebuild"));
        assert!(std::rc::Rc::ptr_eq(&first, &again));
    }
    let expanded = cache.get(1, || vec![42]);
    assert!(!std::rc::Rc::ptr_eq(&first, &expanded));
    assert_eq!(*expanded, vec![42]);
}
