//! Android services for the original player process; no desktop stubs.
use std::{io, path::PathBuf, sync::OnceLock};
use jni::{JavaVM, JNIEnv, objects::{GlobalRef, JObject, JString, JValue}};

static VM: OnceLock<JavaVM> = OnceLock::new();
static ACTIVITY: OnceLock<GlobalRef> = OnceLock::new();

pub fn initialize(app: &android_activity::AndroidApp) -> anyhow::Result<()> {
    let vm = unsafe { JavaVM::from_raw(app.vm_as_ptr().cast()) }?;
    {
        let env = vm.attach_current_thread()?;
        let activity = unsafe { JObject::from_raw(app.activity_as_ptr().cast()) };
        let _ = ACTIVITY.set(env.new_global_ref(activity)?);
    }
    let _ = VM.set(vm);
    rbx_android_core::install_platform(Box::new(Clipboard), Box::new(Credentials));
    Ok(())
}

fn with_activity<T>(f: impl FnOnce(&mut JNIEnv, &JObject) -> anyhow::Result<T>) -> anyhow::Result<T> {
    let mut env = VM.get().ok_or_else(|| anyhow::anyhow!("VM indisponível"))?.attach_current_thread()?;
    let activity = ACTIVITY.get().ok_or_else(|| anyhow::anyhow!("Activity indisponível"))?.as_obj();
    let result = f(&mut env, activity);
    if result.is_err() { let _ = env.exception_clear(); }
    result
}

fn call(operation: &str, value: &str) -> anyhow::Result<String> {
    with_activity(|env, activity| {
        let operation = env.new_string(operation)?;
        let value = env.new_string(value)?;
        let result = env.call_method(activity, "service", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            &[JValue::Object(&operation), JValue::Object(&value)])?.l()?;
        if result.is_null() { return Ok(String::new()); }
        Ok(env.get_string(&JString::from(result))?.to_string_lossy().into_owned())
    })
}

pub fn resource_paths() -> anyhow::Result<String> { call("resources", "") }
pub fn session() -> anyhow::Result<PathBuf> { call("session", "").map(PathBuf::from) }
pub fn asset_key() -> anyhow::Result<String> { call("asset-key", "") }
pub fn loading_stage(stage: &str) { let _ = call("loading-stage", stage); }
pub fn ready() -> anyhow::Result<()> { call("ready", "").map(|_| ()) }

// Only the one-use Activity authorization enables passive native phase totals.
// The host receives aggregate counters, never pointer IDs or coordinates.
pub fn touch_probe_active() -> bool {
    matches!(call("touch-probe-enabled", ""), Ok(value) if value == "1")
}
pub fn gui_probe_active() -> bool {
    matches!(call("touch-gui-enabled", ""), Ok(value) if value == "1")
}
pub fn report_touch_gui_activated(path: &str, queued: u32, attempted: u32, errors: u32) {
    let data = serde_json::json!({"path":path,"queued":queued,"attempted":attempted,"errors":errors});
    let _ = call("native-gui-activated", &data.to_string());
}
pub fn report_native_touch_counts(down: u32, up: u32, cancel: u32) {
    let report = serde_json::json!({"down":down,"up":up,"cancel":cancel});
    let _ = call("native-touch-counts", &report.to_string());
}
pub fn finish() -> anyhow::Result<()> { call("finish", "").map(|_| ()) }

#[derive(Debug)]
struct Clipboard;
impl rook_platform::clipboard::Clipboard for Clipboard {
    fn text(&self) -> Option<String> { call("clipboard-read", "").ok().filter(|s| !s.is_empty()) }
    fn set_text(&self, value: &str) { if call("clipboard-write", value).is_err() { log::warn!("Falha ao copiar texto"); } }
}

#[derive(Debug)]
struct Credentials;
fn credential(operation: &str, name: &str, secret: &str) -> io::Result<Option<String>> {
    call("credential", &serde_json::json!({"operation":operation,"name":name,"secret":secret}).to_string())
        .map(|s| (!s.is_empty()).then_some(s)).map_err(|_| io::Error::other("Falha no armazenamento seguro do Android"))
}
impl rook_platform::credentials::Credentials for Credentials {
    fn get(&self, name: &str) -> io::Result<Option<String>> { credential("read", name, "") }
    fn set(&self, name: &str, secret: &str) -> io::Result<()> { credential("write", name, secret).map(|_| ()) }
    fn clear(&self, name: &str) -> io::Result<bool> {
        let present = self.get(name)?.is_some();
        credential("delete", name, "")?;
        Ok(present)
    }
}
