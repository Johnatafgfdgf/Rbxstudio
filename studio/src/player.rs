//! Handoff of an immutable editor snapshot to the original Rook process.
use std::{io::{Read, Seek, SeekFrom}, path::{Path, PathBuf}, sync::{Mutex, atomic::{AtomicU64, Ordering}}, time::{Duration, Instant}};
use rbx_studio::android_play::{Host, Phase, Update};

struct Active { id: u64, directory: PathBuf, started: Instant, offset: u64 }
struct Player { files: PathBuf, active: Mutex<Option<Active>>, next: AtomicU64, diagnostic_gate: Mutex<()> }

pub fn install(files: &Path) {
    let _ = rbx_studio::android_play::install(Box::new(Player {
        files: files.to_owned(), active: Mutex::new(None), next: AtomicU64::new(1), diagnostic_gate: Mutex::new(()),
    }));
}

impl Host for Player {
    fn diagnostics(&self) -> Result<String, String> {
        let _guard = self.diagnostic_gate.try_lock().map_err(|_| "Diagnóstico já está em execução")?;
        if self.active.lock().unwrap_or_else(|p| p.into_inner()).is_some() {
            return Err("Encerre o Play antes de executar o diagnóstico do motor".into());
        }
        let report = rbx_android_core::diagnostics::run();
        let report = serde_json::to_string_pretty(&report).map_err(|e| e.to_string())?;
        let folder = self.files.join("diagnostics");
        std::fs::create_dir_all(&folder).map_err(|e| e.to_string())?;
        std::fs::write(folder.join("latest.json"), &report).map_err(|e| e.to_string())?;
        Ok(report)
    }

    fn start(&self, bytes: Vec<u8>, title: String, accent: u32) -> Result<u64, String> {
        let _guard = self.diagnostic_gate.try_lock().map_err(|_| "Aguarde a conclusão do diagnóstico")?;
        rbx_android_core::resources::shared().ok_or("Recursos do Roblox indisponíveis")?.play_options()?;
        let mut active = self.active.lock().unwrap_or_else(|p| p.into_inner());
        if active.is_some() { return Err("Uma sessão de Play já está aberta".into()); }
        let id = self.next.fetch_add(1, Ordering::Relaxed);
        let stamp = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH)
            .map_err(|e| e.to_string())?.as_nanos();
        let directory = self.files.join("local-play").join(format!("{stamp}-{id}"));
        std::fs::create_dir_all(&directory).map_err(|e| e.to_string())?;
        let launch = (|| -> Result<(), String> {
            std::fs::write(directory.join("place.rbxl"), bytes).map_err(|e| e.to_string())?;
            let metadata = serde_json::json!({"title":title, "accent":accent});
            std::fs::write(directory.join("appearance.json"), metadata.to_string()).map_err(|e| e.to_string())?;
            gpui_mobile::android::player_control("start", &directory).map_err(|e| e.to_string())?;
            Ok(())
        })();
        if let Err(error) = launch {
            let _ = std::fs::remove_dir_all(&directory);
            return Err(error);
        }
        *active = Some(Active { id, directory, started: Instant::now(), offset: 0 });
        Ok(id)
    }

    fn poll(&self, id: u64) -> Update {
        let mut active = self.active.lock().unwrap_or_else(|p| p.into_inner());
        let Some(session) = active.as_mut().filter(|s| s.id == id) else {
            return Update { phase: Phase::Stopped, output: Vec::new() };
        };
        let mut output = Vec::new();
        if let Ok(mut file) = std::fs::File::open(session.directory.join("output.log")) {
            let mut bytes = Vec::new();
            if file.seek(SeekFrom::Start(session.offset)).is_ok() && file.take(64 * 1024).read_to_end(&mut bytes).is_ok() {
                if let Some(end) = bytes.iter().rposition(|&b| b == b'\n') {
                    output.extend(String::from_utf8_lossy(&bytes[..end]).lines().map(str::to_owned));
                    session.offset += (end + 1) as u64;
                }
            }
        }
        let state = std::fs::read(session.directory.join("state.json")).ok()
            .and_then(|b| serde_json::from_slice::<serde_json::Value>(&b).ok());
        let alive = gpui_mobile::android::player_control("alive", &session.directory);
        let phase = match state.as_ref().and_then(|s| s["status"].as_str()) {
            Some("failed") => Phase::Failed(state.as_ref().and_then(|s| s["error"].as_str()).unwrap_or("O Play local falhou").to_owned()),
            Some("stopped") => Phase::Stopped,
            _ if alive.as_ref().is_err() => Phase::Failed("Não foi possível verificar o processo do player".into()),
            _ if !alive.unwrap_or(false) && session.started.elapsed() > Duration::from_secs(10) =>
                Phase::Failed("O processo do player encerrou antes de concluir a sessão".into()),
            Some("running") => Phase::Running,
            _ => Phase::Starting,
        };
        Update { phase, output }
    }

    fn stop(&self, id: u64) {
        let mut active = self.active.lock().unwrap_or_else(|p| p.into_inner());
        if active.as_ref().is_some_and(|s| s.id == id) {
            if let Some(session) = active.take() {
                if let Err(error) = gpui_mobile::android::player_control("stop", &session.directory) {
                    log::warn!("{error}");
                } else {
                    let _ = std::fs::remove_dir_all(session.directory);
                }
            }
        }
    }
}
