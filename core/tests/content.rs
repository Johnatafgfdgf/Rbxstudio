//! Local fixtures only; no Roblox assets are redistributed.
use rbx_assets::{
    AndroidContent, AssetCache, AssetFetcher, AssetRef, AssetResolver, FetchError, NativeContent,
};
use std::{io::Write, path::Path};
fn archive(path: &Path, entries: &[(&str, &[u8])]) {
    let mut z = zip::ZipWriter::new(std::fs::File::create(path).unwrap());
    for (n, b) in entries {
        z.start_file(
            *n,
            zip::write::SimpleFileOptions::default()
                .compression_method(zip::CompressionMethod::Deflated),
        )
        .unwrap();
        z.write_all(b).unwrap();
    }
    z.finish().unwrap();
}
struct NeverNetwork;
impl AssetFetcher for NeverNetwork {
    fn fetch_id(&self, _: u64) -> Result<Vec<u8>, FetchError> {
        panic!("Local resources must not need an API key or asset delivery")
    }
}
#[test]
fn original_apk_reader_handles_content_extra_content_case_and_traversal() {
    let t = tempfile::tempdir().unwrap();
    let f = t.path().join("base.apk");
    archive(
        &f,
        &[
            ("assets/content/textures/SpawnLocation.png", b"spawn"),
            ("assets/ExtraContent/avatar/heads/head.mesh", b"head"),
        ],
    );
    let c = AndroidContent::new(vec![f]);
    assert_eq!(
        c.read("Textures/SpawnLocation.png").unwrap().unwrap(),
        b"spawn"
    );
    assert_eq!(c.read("avatar/heads/head.mesh").unwrap().unwrap(), b"head");
    assert!(c.read("../AndroidManifest.xml").unwrap().is_none());
}
#[test]
fn multiple_local_packages_and_atomic_replacement_are_observed_without_restart() {
    let t = tempfile::tempdir().unwrap();
    let a = t.path().join("textures.zip");
    archive(&a, &[("textures/face.png", b"old")]);
    archive(
        &t.path().join("avatar.zip"),
        &[("avatar/heads/head.mesh", b"head")],
    );
    let c = AndroidContent::new(vec![t.path().into()]);
    assert_eq!(c.read("textures/face.png").unwrap().unwrap(), b"old");
    assert_eq!(c.read("avatar/heads/head.mesh").unwrap().unwrap(), b"head");
    let b = t.path().join("incoming.tmp");
    archive(&b, &[("textures/face.png", b"updated bytes")]);
    std::fs::rename(b, a).unwrap();
    assert_eq!(
        c.read("textures/face.png").unwrap().unwrap(),
        b"updated bytes"
    );
}
#[test]
fn local_spawn_wins_over_old_cache_without_fetching_authenticated_substitute() {
    let t = tempfile::tempdir().unwrap();
    let a = t.path().join("base.apk");
    archive(
        &a,
        &[("assets/content/textures/SpawnLocation.png", b"local")],
    );
    let cache = AssetCache::new(Some(t.path().join("cache"))).unwrap();
    cache
        .put_native("textures/SpawnLocation.png", b"stale")
        .unwrap();
    let native = NativeContent::with_android_sources(cache.native_packages_dir(), vec![a]);
    let resolver = AssetResolver::new(cache, Box::new(NeverNetwork), native);
    assert_eq!(
        resolver
            .resolve(&AssetRef::Native("textures/SpawnLocation.png".into()))
            .unwrap()
            .bytes,
        b"local"
    );
}
#[test]
fn invalid_local_archive_does_not_destroy_a_working_cached_resource() {
    let t = tempfile::tempdir().unwrap();
    let a = t.path().join("invalid.apk");
    std::fs::write(&a, b"broken").unwrap();
    assert!(AndroidContent::validate(&a).is_err());
    let cache = AssetCache::new(Some(t.path().join("cache"))).unwrap();
    cache.put_native("textures/face.png", b"valid").unwrap();
    let native = NativeContent::with_android_sources(cache.native_packages_dir(), vec![a]);
    assert_eq!(
        AssetResolver::new(cache, Box::new(NeverNetwork), native)
            .resolve(&AssetRef::Native("textures/face.png".into()))
            .unwrap()
            .bytes,
        b"valid"
    );
}
#[test]
fn rook_head_is_the_original_rounded_mesh_with_valid_normals_and_uvs() {
    let m = rook_geometry_bridge::head();
    assert_eq!(m.vertices.len(), 792);
    assert_eq!(m.indices.len(), 2160);
    assert!((m.bounds.max[1] - 0.6012).abs() < 0.0001);
    for v in &m.vertices {
        assert!(v
            .position
            .iter()
            .chain(v.normal.iter())
            .chain(v.uv.iter())
            .all(|x| x.is_finite()));
        let n = v.normal.iter().map(|x| x * x).sum::<f32>();
        assert!((n - 1.).abs() < 0.001);
    }
    assert!(m.indices.iter().all(|&i| (i as usize) < m.vertices.len()));
    assert!(std::sync::Arc::ptr_eq(&m, &rook_geometry_bridge::head()));
}
