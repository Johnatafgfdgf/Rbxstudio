//! Original Roblox Studio packages and CorePatch, shared by both native engines.
//! Package discovery, manifests, integrity checks and extraction belong to Rook.
use std::{path::{Component, Path, PathBuf}, sync::{Arc, Mutex, OnceLock}};
use rook_content::{resolver::{ApiKey, Config, HostContent, packages::Packages}, Resolver};
use rook_engine::core_scripts::{CorePatch, PATCH};

#[derive(Clone, Debug)]
pub enum Preparation { Idle, Loading, Ready, Failed(String) }

pub struct StudioResources {
    root: PathBuf,
    local: Arc<rbx_assets::AndroidContent>,
    config: Config,
    packages: Mutex<Arc<Packages>>,
    patch: Mutex<Option<Arc<CorePatch>>>,
    state: Mutex<Preparation>,
}

impl StudioResources {
    pub fn new(root: PathBuf, archives: Vec<PathBuf>, network: bool) -> Self {
        let local = Arc::new(rbx_assets::AndroidContent::new(archives));
        let mut config = Config::empty();
        let reader = local.clone();
        config.host_content = Some(HostContent::new(move |path| reader.read_local(path).ok().flatten()));
        config.network = network;
        config.cache_dir = Some(root.join("asset-cache"));
        config.cache_max = Some(256 << 20);
        config.kept_dir = Some(root.join("kept-assets"));
        if config.endpoints.is_roblox("assetdelivery") {
            config.api_key = Some(ApiKey::new(|| {
                rbx_cloud::ApiKey::from_env_or_config().map(|key| key.expose_secret().to_owned())
            }));
        }
        let packages = Arc::new(Packages::new(root.join("studio-packages"), &config.endpoints));
        Self { root, local, config, packages: Mutex::new(packages),
            patch: Mutex::new(None), state: Mutex::new(Preparation::Idle) }
    }

    /// Fresh failure memo for a new Play, retaining downloaded packages and assets.
    pub fn resolver(&self) -> Arc<Resolver> {
        Arc::new(Resolver::new(self.resolver_config()))
    }

    fn resolver_config(&self) -> Config {
        let mut config = self.config.clone();
        config.packages = Some(self.packages.lock().unwrap().clone());
        config
    }

    pub fn core_patch(&self) -> Option<Arc<CorePatch>> { self.patch.lock().unwrap().clone() }
    pub fn state(&self) -> Preparation { self.state.lock().unwrap().clone() }

    /// Do not silently start a reduced client while its official UI is still
    /// loading. A new Play uses the original engine's execution/heap budgets.
    pub fn play_options(&self) -> Result<rook_engine::Options, String> {
        self.play_options_with_config(self.resolver_config())
    }

    #[cfg(target_os = "android")]
    pub fn play_options_with_brand(&self, icon: Vec<u8>) -> Result<rook_engine::Options, String> {
        let mut config = self.resolver_config();
        if !icon.is_empty() {
            let reader = self.local.clone();
            config.host_content = Some(HostContent::new(move |path| {
                // Replace only the menu's image at its original location.
                // Input, hit targets, chat and the menu are still the player's own.
                if path.eq_ignore_ascii_case("textures/ui/TopBar/coloredlogo.png") {
                    Some(icon.clone())
                } else {
                    reader.read_local(path).ok().flatten()
                }
            }));
        }
        self.play_options_with_config(config)
    }

    fn play_options_with_config(&self, config: Config) -> Result<rook_engine::Options, String> {
        let patch = match self.state() {
            Preparation::Ready => self.core_patch().ok_or("Os CoreScripts ainda não estão disponíveis")?,
            Preparation::Failed(error) => return Err(format!("Não foi possível preparar os recursos do Roblox: {error}")),
            _ => return Err("Os recursos oficiais do Roblox ainda estão sendo preparados. Aguarde a conclusão do download".into()),
        };
        Ok(rook_engine::Options {
            content: Some(Arc::new(Resolver::new(config))), core_scripts: Some(patch),
            wait_for_sounds: false, ..Default::default()
        })
    }

    pub fn diagnostics(&self) -> serde_json::Value {
        let (state, error) = match self.state() {
            Preparation::Idle => ("idle", None), Preparation::Loading => ("loading", None),
            Preparation::Ready => ("ready", None), Preparation::Failed(e) => ("failed", Some(e)),
        };
        serde_json::json!({"core_scripts": state, "error": error,
            "packages": "original-rook-on-demand", "network": self.config.network,
            "version": self.packages.lock().unwrap().version()})
    }

    /// For rbx-native's resource reader: preserve local APK priority and use
    /// the same original package implementation as the Rook resolver.
    pub fn read_package(&self, path: &str) -> Option<Vec<u8>> {
        let normalized = path.replace('\\', "/");
        if normalized.contains('\0') || Path::new(&normalized).components()
            .any(|p| !matches!(p, Component::Normal(_) | Component::CurDir)) { return None; }
        let packages = self.packages.lock().unwrap().clone();
        packages.find(&normalized, self.config.network).and_then(|file| std::fs::read(file).ok())
    }

    /// Editor assets share Rook's original legacy-path catalogue and asset
    /// resolver after local/install packages. This includes the public classic
    /// SpawnLocation image instead of rbx-native's private modern substitute.
    /// Called only by the editor's asset workers; never by the UI thread.
    pub fn read_resource(&self, path: &str) -> Option<Vec<u8>> {
        crate::platform::ensure_platform();
        let path = path.replace('\\', "/");
        self.local.read_local(&path).ok().flatten()
            .or_else(|| self.read_package(&path)).or_else(|| {
            let id = rook_content::resolver::defaults::legacy_asset(&path)?;
            self.resolver().resolve(&rook_content::ContentRef::Asset { id, version: None })
                .ok().map(|fetched| fetched.bytes.to_vec())
        })
    }

    /// May block on disk and CDN; only the preparation worker calls this.
    fn load_patch(&self) -> Result<Arc<CorePatch>, String> {
        let local = self.local.read_local(PATCH).map_err(|e| e.to_string())?;
        let path = if let Some(bytes) = local {
            std::fs::create_dir_all(&self.root).map_err(|e| e.to_string())?;
            let staged = self.root.join("DataModelPatch.rbxm.new");
            let target = self.root.join("DataModelPatch.rbxm");
            std::fs::write(&staged, bytes).map_err(|e| e.to_string())?;
            // Parse before replacing the last usable local patch.
            CorePatch::load(&staged, None)?;
            std::fs::rename(staged, &target).map_err(|e| e.to_string())?;
            target
        } else {
            let packages = self.packages.lock().unwrap().clone();
            packages.find(PATCH, self.config.network)
                .ok_or_else(|| "Pacotes oficiais indisponíveis e nenhum CorePatch local encontrado".to_owned())?
        };
        CorePatch::load(&path, None).map(Arc::new)
    }

    /// A retry gets a new package failure memo; successful downloads stay on disk.
    pub fn prepare(self: &Arc<Self>, retry: bool) -> bool {
        crate::platform::ensure_platform();
        {
            let mut state = self.state.lock().unwrap();
            if matches!(*state, Preparation::Loading) || (!retry && matches!(*state, Preparation::Ready)) { return false; }
            *state = Preparation::Loading;
        }
        if retry {
            *self.packages.lock().unwrap() = Arc::new(Packages::new(self.root.join("studio-packages"), &self.config.endpoints));
        }
        let resources = self.clone();
        let worker = std::thread::Builder::new().name("roblox-studio-resources".into()).spawn(move || {
            let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| resources.load_patch()))
                .unwrap_or_else(|_| Err("Falha interna ao preparar os pacotes oficiais".into()));
            match result {
                Ok(patch) => { *resources.patch.lock().unwrap() = Some(patch); *resources.state.lock().unwrap() = Preparation::Ready; }
                Err(error) => { log::warn!("Preparação dos CoreScripts: {error}"); *resources.state.lock().unwrap() = Preparation::Failed(error); }
            }
        });
        if let Err(error) = worker { *self.state.lock().unwrap() = Preparation::Failed(error.to_string()); return false; }
        true
    }
}

static SHARED: OnceLock<Option<Arc<StudioResources>>> = OnceLock::new();
pub fn shared() -> Option<&'static Arc<StudioResources>> {
    SHARED.get_or_init(|| {
        let root = std::env::var_os("RBX_NATIVE_ROBLOX_RESOURCES")?;
        let paths = std::env::var_os("RBX_NATIVE_ANDROID_APKS")
            .map(|p| std::env::split_paths(&p).collect()).unwrap_or_default();
        let resources = Arc::new(StudioResources::new(root.into(), paths, true));
        let weak = Arc::downgrade(&resources);
        rbx_assets::set_android_resource_fallback(move |path| weak.upgrade()?.read_resource(path));
        Some(resources)
    }).as_ref()
}

pub fn begin_background_preparation() -> bool {
    crate::platform::ensure_platform();
    shared().is_some_and(|resources| resources.prepare(false))
}

#[cfg(target_os = "android")]
pub fn install_asset_key(secret: String) {
    if !secret.trim().is_empty() {
        rbx_cloud::ApiKey::install(Some(rbx_cloud::ApiKey::new(secret)));
    }
}
