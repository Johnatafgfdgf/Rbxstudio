//! Android host for the complete original rbx_studio GPUI application.
//! Editor modules and views remain in the pinned upstream source tree.
#[cfg(target_os = "android")]
mod platform;
#[cfg(target_os = "android")]
mod player;
#[cfg(target_os = "android")]
#[no_mangle]
fn android_main(app: android_activity::AndroidApp) {
    android_logger::init_once(android_logger::Config::default()
        .with_tag("RbxStudio").with_max_level(log::LevelFilter::Info));
    if let Some(files) = app.internal_data_path() {
        let config = files.join("config");
        let cache = files.join("cache");
        for directory in [&files, &config, &cache] {
            if let Err(error) = std::fs::create_dir_all(directory) {
                log::error!("{}: {error}", directory.display());
                return;
            }
        }
        std::env::set_var("XDG_DATA_HOME", &files);
        std::env::set_var("XDG_CONFIG_HOME", config);
        std::env::set_var("XDG_CACHE_HOME", &cache);
        std::env::set_var("TMPDIR", cache);
        std::env::set_var("RBX_NATIVE_ROBLOX_RESOURCES", files.join("roblox-resources"));
    }
    gpui_mobile::android::jni::init_platform(&app);
    platform::install();
    if let Some(files) = app.internal_data_path() { player::install(&files); }
    match gpui_mobile::android::resource_paths() {
        Ok(paths) => std::env::set_var("RBX_NATIVE_ANDROID_APKS", paths),
        Err(error) => log::warn!("{error}"),
    }
    rbx_android_core::resources::begin_background_preparation();
    rbx_studio::run_android();
}
