# Inventário dos dois projetos — port integral em desenvolvimento

Este inventário percorre todos os manifests e arquivos Rust dos fontes preparados a partir dos commits fixados. Cada hash está em `full-module-inventory.json`. Contar, ler ou compilar arquivos não comprova compatibilidade nem compreensão de todos os comportamentos. A coluna descreve pontos realmente conectados ao aplicativo.

| Origem | Crate | Arquivos Rust | Linhas | Integração |
|---|---|---:|---:|---|
| rbx-native | rbx_animation | 83 | 18163 | Animator/Rig/KeyframeSequence called for imported clips; no default walk assets bundled |
| rbx-native | rbx_assets | 16 | 3203 | Leitor APK/ZIP local e fallback para os pacotes oficiais Rook, com prioridade local e modo offline separado |
| rbx-native | rbx_audit | 9 | 3310 | Dock original de auditoria incluído na aplicação GPUI; fluxo Android ainda não validado |
| rbx-native | rbx_autorig | 28 | 10067 | Ferramentas originais de rig incluídas na aplicação GPUI; fluxo Android ainda não validado |
| rbx-native | rbx_binary | 46 | 7651 | Original editor file codec |
| rbx-native | rbx_cloud | 55 | 13031 | Views originais de conta/publicação incluídas; Keystore Android validado; fluxos Roblox ainda não validados no Android |
| rbx-native | rbx_dom | 34 | 6981 | Authoritative edit/save document and temporary render projection |
| rbx-native | rbx_eyedropper | 5 | 526 | Código original incluído; captura fora da própria janela exige adaptação Android |
| rbx-native | rbx_figma | 11 | 4390 | Fluxo original incluído; login/importação Figma no Android ainda não validados |
| rbx-native | rbx_import | 9 | 1475 | Importador original incluído; seletor/espelho SAF validado separadamente, importação 3D ainda não validada |
| rbx-native | rbx_loren | 66 | 16741 | Ferramentas originais incluídas; sincronização completa de projetos SAF ainda precisa ser adaptada |
| rbx-native | rbx_lua | 118 | 25930 | Original edit command bar; Play scripts use Rook VMs |
| rbx-native | rbx_materials | 3 | 481 | Original renderer materials |
| rbx-native | rbx_mesh | 16 | 3496 | Original renderer mesh types/codecs |
| rbx-native | rbx_parser_cli | 20 | 2389 | Executável CLI não portado ao host Android |
| rbx-native | rbx_play | 73 | 15920 | Play Android ligado por snapshot ao player Rook original; passagem de sessão compilada, execução/depuração local ainda em validação; auxiliares ausentes |
| rbx-native | rbx_presence | 49 | 12305 | UI/protocolo originais incluídos; colaboração Android ainda não validada |
| rbx-native | rbx_reflection | 12 | 1806 | Original editor metadata |
| rbx-native | rbx_studio | 973 | 256548 | Editor original compilado/linkado para ARM64/x86_64; Home e tela cheia capturados no Android 35; carregamento assíncrono e ciclo de superfície passam check nas duas ABIs; nova cena/retomada/Play visual em validação |
| rbx-native | rbx_terrain | 24 | 4855 | Ferramentas originais de terreno incluídas na UI; fluxo Android ainda não validado |
| rbx-native | rbx_ugc | 13 | 2618 | Ferramentas originais incluídas; publicação autenticada Android ainda não validada |
| rbx-native | rbx_viewer | 408 | 108333 | Renderer original integrado ao GPUI; Vulkan perde o dispositivo ao abrir mapa no emulador, inclusive com qualidade inicial10; GL falha ao traduzir leitura de profundidade; apresentação 3D ainda não aprovada |
| rbx-native | rbx_wasm | 5 | 955 | Módulo original compilado na aplicação; execução/importação Android ainda não validada |
| rbx-native | rbx_xml | 35 | 3757 | Original editor file codec |
| rook | rook-cli | 35 | 6275 | Aplicação CLI original não portada ao host Android; preservada no submódulo |
| rook | rook-player | 19 | 3446 | Host Android adicionado à aplicação original, preservando Game/servidor/renderer/áudio; bibliotecas reais ARM64/x86_64 compiladas/linkadas/empacotadas; execução visual ainda em validação |
| rook | rook-audio | 21 | 3214 | Output.sync ligado no player original e dispositivo liberado/restaurado no ciclo Android; reprodução no Android ainda não validada |
| rook | rook-content | 35 | 6983 | Downloader oficial original ligado; manifesto/extração/CorePatch, fontes originais e reutilização offline validados com pacotes reais |
| rook | rook-dom | 14 | 2147 | Original runtime server/client DOMs, never saved over the editor project |
| rook | rook-engine | 66 | 12966 | Session, 25 CoreScripts oficiais móveis, direcional/pulo simultâneos e cancelamento validados no motor; Stop preserva o documento; Play GPUI ainda sem validação visual |
| rook | rook-formats | 8 | 2076 | Original loader/conversion; file_property adapter preserves original encodings |
| rook | rook-gui | 50 | 9406 | Layout/input originais usados pelo runtime; CoreScripts executam, fidelidade visual Android ainda não validada |
| rook | rook-lua | 104 | 21116 | VM/scheduler/APIs originais em uso; emotes com estado/Clone/sinais; GetTextBoundsAsync pelo shaper original validado com fontes oficiais, sem fallback de tamanho; cobertura upstream parcial |
| rook | rook-net | 5 | 935 | Original local replication/remotes through Session; Internet transport not exposed |
| rook | rook-physics | 45 | 10403 | Original Rapier physics, collision, Humanoid and joints |
| rook | rook-platform | 8 | 820 | No host GPUI, clipboard e credenciais conectados aos serviços Android; Keystore/SAF sobreviveram a force-stop nos testes |
| rook | rook-reflection | 11 | 1945 | Original runtime metadata |
| rook | rook-render | 60 | 14571 | Renderer original ligado à superfície NativeActivity do player original e linkado nos APKs ARM64/x86_64; apresentação Android ainda sem evidência |
| rook | rook-terrain | 24 | 4173 | Original engine terrain/collision dependency; extensive worlds not validated |
| rook | rook-types | 20 | 3478 | Original runtime value/math types |
| rook | rook-view | 46 | 6060 | SceneCache/Installed ligados pelo frame loop do player original Android; execução ainda em validação |
