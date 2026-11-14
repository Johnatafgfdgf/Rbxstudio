//! Vertex-layout adapter for Rook's unchanged procedural head.
use std::sync::{Arc, OnceLock};
#[derive(Default)]
struct MeshData {
    vertices: Vec<Vertex>,
    indices: Vec<u32>,
}
struct Vertex {
    position: [f32; 3],
    normal: [f32; 3],
    uv: [f32; 2],
}
impl Vertex {
    fn plain(position: [f32; 3], normal: [f32; 3], uv: [f32; 2]) -> Self {
        Self {
            position,
            normal,
            uv,
        }
    }
}
#[path = "upstream/rook_head.rs"]
mod original;
pub fn head() -> Arc<rbx_mesh::SkinnedMesh> {
    static MESH: OnceLock<Arc<rbx_mesh::SkinnedMesh>> = OnceLock::new();
    MESH.get_or_init(|| {
        let m = original::head();
        let mut min = [f32::INFINITY; 3];
        let mut max = [f32::NEG_INFINITY; 3];
        let vertices = m
            .vertices
            .into_iter()
            .map(|v| {
                for a in 0..3 {
                    min[a] = min[a].min(v.position[a]);
                    max[a] = max[a].max(v.position[a]);
                }
                rbx_mesh::Vertex {
                    position: v.position,
                    normal: v.normal,
                    uv: v.uv,
                    color: [255; 4],
                }
            })
            .collect();
        Arc::new(
            rbx_mesh::Mesh {
                version: (0, 0),
                vertices,
                indices: m.indices,
                lods: Vec::new(),
                bounds: rbx_mesh::Aabb { min, max },
            }
            .into(),
        )
    })
    .clone()
}
