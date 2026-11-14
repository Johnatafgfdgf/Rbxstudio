#[path = "upstream/transform/frame.rs"]
pub(crate) mod frame;
#[path = "upstream/transform/targets.rs"]
pub(crate) mod targets;

pub(crate) fn parts(
    dom: &rbx_dom::WeakDom,
    db: &rbx_reflection::ReflectionDatabase,
    refs: &[rbx_dom::Ref],
) -> Vec<targets::Target> {
    targets::Targets::read(dom, db, refs).0
}
