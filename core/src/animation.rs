//! Original rbx-native Animator/Rig applied to Rook's physical joints.
use crate::projection::Projection;
use rbx_animation::{
    animator::{Animator, TrackId},
    Rig,
};
use rbx_dom::{Ref, WeakDom};
use rook_dom::InstanceId;
use rook_types::{CFrame, Value};
use std::collections::HashMap;
pub(crate) struct ClipPlayer {
    pub animator: Animator,
    _track: TrackId,
    joints: Vec<(String, InstanceId, CFrame)>,
    pub model: Ref,
}
impl ClipPlayer {
    pub fn new(
        native: &WeakDom,
        model: Ref,
        sequence: Ref,
        projection: &Projection,
        dm: &rook_engine::DataModel,
    ) -> Result<Self, String> {
        if native
            .get(sequence)
            .is_none_or(|i| i.class() != "KeyframeSequence")
        {
            return Err("Selecione uma KeyframeSequence importada".into());
        }
        let clip = rbx_animation::sequence::read(native, sequence);
        if clip.keyframes.is_empty() {
            return Err("A animação não possui keyframes".into());
        }
        let rig = Rig::read(native, model);
        let reverse: HashMap<_, _> = projection.map.iter().map(|(&a, &b)| (b, a)).collect();
        let w = dm.runtime().world();
        let joints = rig
            .joints
            .iter()
            .filter_map(|j| {
                let id = *reverse.get(&j.instance)?;
                let name = rig.names.get(&j.part1)?.clone();
                Some((
                    name,
                    id,
                    w.dom
                        .cframe_property(id, "Transform")
                        .unwrap_or(CFrame::IDENTITY),
                ))
            })
            .collect::<Vec<_>>();
        if joints.is_empty() {
            return Err("O personagem não possui juntas compatíveis".into());
        }
        if !clip.keyframes.iter().any(|k| {
            k.poses
                .keys()
                .any(|name| joints.iter().any(|j| &j.0 == name))
        }) {
            return Err("Os nomes das poses não correspondem a este rig".into());
        }
        let mut animator = Animator::new();
        let track = animator.load(clip);
        animator.play(track, 0.1, 1., 1.);
        Ok(Self {
            animator,
            _track: track,
            joints,
            model,
        })
    }
    pub fn step(&mut self, dt: f32, dm: &rook_engine::DataModel) {
        self.animator.step(dt);
        let pose = self.animator.pose();
        for (name, id, rest) in &self.joints {
            let cf = pose
                .get(name)
                .map(|f| {
                    let f = f.to_data();
                    CFrame {
                        position: glam::Vec3::new(f.position.x, f.position.y, f.position.z),
                        rotation: glam::Mat3::from_cols_array(&f.rotation).transpose(),
                    }
                })
                .unwrap_or(*rest);
            dm.set_property(*id, "Transform", Value::CFrame(cf));
        }
    }
    pub fn stop(self, dm: &rook_engine::DataModel) {
        for (_, id, rest) in self.joints {
            if !dm.runtime().world().dom.is_destroyed(id) {
                dm.set_property(id, "Transform", Value::CFrame(rest));
            }
        }
    }
}
