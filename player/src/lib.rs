//! A NativeActivity host for the pinned original Rook player, in its own
//! Android process. The GPUI editor keeps its document and GPU state separate.
#[cfg(target_os = "android")]
mod platform;

#[cfg(target_os = "android")]
#[no_mangle]
fn android_main(app: android_activity::AndroidApp) {
    use std::{io::Write, sync::{Arc, Mutex, atomic::{AtomicU32, Ordering}}, time::{Duration, Instant}};
    use serde_json::json;
    android_logger::init_once(android_logger::Config::default()
        .with_tag("RookPlayer").with_max_level(log::LevelFilter::Info));
    let result = (|| -> anyhow::Result<()> {
        platform::initialize(&app)?;
        let files = app.internal_data_path().ok_or_else(|| anyhow::anyhow!("Diretório privado indisponível"))?.canonicalize()?;
        for (name, value) in [
            ("XDG_DATA_HOME", files.clone()), ("XDG_CONFIG_HOME", files.join("config")),
            ("XDG_CACHE_HOME", files.join("cache")), ("TMPDIR", files.join("cache")),
            ("RBX_NATIVE_ROBLOX_RESOURCES", files.join("roblox-resources")),
        ] { std::env::set_var(name, value); }
        std::env::set_var("NO_COLOR", "1");
        std::env::set_var("RBX_NATIVE_ANDROID_APKS", platform::resource_paths()?);
        let session = platform::session()?.canonicalize()?;
        anyhow::ensure!(session.starts_with(files.join("local-play")), "Sessão de Play inválida");
        let place = session.join("place.rbxl");
        anyhow::ensure!(place.is_file(), "A cópia do documento não foi encontrada");
        let state = Arc::new(session.join("state.json"));
        let write_state = |status: &str, error: Option<String>| {
            let value = json!({"status": status, "error": error, "pid": std::process::id()});
            let temp = state.with_extension("native");
            if std::fs::write(&temp, value.to_string()).is_ok() { let _ = std::fs::rename(temp, state.as_ref()); }
        };
        write_state("starting", None);
        match platform::asset_key() {
            Ok(key) => rbx_android_core::resources::install_asset_key(key),
            Err(_) => log::warn!("O Play não conseguiu abrir a chave de assets no armazenamento seguro"),
        }
        let output = Arc::new(Mutex::new((std::fs::OpenOptions::new().create(true).append(true)
            .open(session.join("output.log"))?, 0usize)));
        rook_engine::console::set_output(Some(Box::new(move |line| {
            if let Ok(mut output) = output.lock() {
                if output.1 < 8 * 1024 * 1024 {
                    let _ = writeln!(output.0, "{line}");
                    output.1 += line.len() + 1;
                }
            }
        })));
        // This consent is consumed by the Activity for this single Play only.
        let observe_native_touch = platform::touch_probe_active();
        // Opt-in signal observation is independent of the aggregate input
        // counters. The default is disabled and all reports remain local.
        if observe_native_touch && platform::gui_probe_active() {
            let reports = Arc::new(AtomicU32::new(0));
            rook_engine::touch_probe::install(Some(Arc::new(move |path, queued, attempted, completed, unresolved, failed, errors, journal_changes, handler_dom_changes, handlers_with_changes| {
                if reports.fetch_add(1, Ordering::Relaxed) < 200 {
                    platform::report_touch_gui_activated(path, queued, attempted,
                        completed, unresolved, failed, errors, journal_changes,
                        handler_dom_changes, handlers_with_changes);
                }
            })));
        }
        let ready_state = state.clone();
        let brand_icon = std::fs::read(session.join("menu.png")).unwrap_or_default();
        let run = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            rook_player::android::run(app, place, move || {
                platform::loading_stage("Preparando os recursos do jogo…");
                rbx_android_core::resources::begin_background_preparation();
                let resources = rbx_android_core::resources::shared()
                    .ok_or_else(|| anyhow::anyhow!("Recursos do Roblox não configurados"))?;
                let deadline = Instant::now() + Duration::from_secs(120);
                loop {
                    use rbx_android_core::resources::Preparation;
                    match resources.state() {
                        Preparation::Ready => break,
                        Preparation::Failed(error) => anyhow::bail!(error),
                        _ if Instant::now() >= deadline => anyhow::bail!("O preparo dos recursos excedeu o tempo limite"),
                        _ => std::thread::sleep(Duration::from_millis(50)),
                    }
                }
                let options = resources.play_options_with_brand(brand_icon).map_err(anyhow::Error::msg)?;
                let resources = rook_player::android::Resources::new(options.content.unwrap(), options.core_scripts.unwrap());
                platform::loading_stage("Abrindo o mapa e criando o jogador…");
                Ok(resources)
            }, move || {
                let value = json!({"status":"running", "error":null, "pid":std::process::id()});
                let temp = ready_state.with_extension("native");
                if std::fs::write(&temp, value.to_string()).is_ok() { let _ = std::fs::rename(temp, ready_state.as_ref()); }
                let _ = platform::ready();
                log::info!("ROOK_FIRST_FRAME_PRESENTED");
            }, observe_native_touch, move |down, up, cancel| {
                platform::report_native_touch_counts(down, up, cancel);
            })
        })).unwrap_or_else(|panic| Err(anyhow::anyhow!("Falha no player: {}",
            panic.downcast_ref::<String>().map(String::as_str)
                .or_else(|| panic.downcast_ref::<&str>().copied()).unwrap_or("pânico nativo"))));
        rook_engine::touch_probe::install(None);
        rook_engine::console::flush();
        match &run {
            Ok(()) => write_state("stopped", None),
            Err(error) => write_state("failed", Some(format!("{error:#}"))),
        }
        run
    })();
    if let Err(error) = result { log::error!("{error:#}"); }
    let _ = platform::finish();
}
