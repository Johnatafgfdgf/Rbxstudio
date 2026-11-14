# Inventário dos dois projetos — 0.3

Este inventário percorre todos os manifests e arquivos Rust dos fontes preparados a partir dos commits fixados. Cada hash está em `module-inventory.json`. Contar, ler ou compilar arquivos não comprova compatibilidade nem compreensão de todos os comportamentos. A coluna descreve pontos realmente conectados ao aplicativo.

| Origem | Crate | Arquivos Rust | Linhas | Integração |
|---|---|---:|---:|---|
| rbx-native | rbx_animation | 83 | 18163 | Animator/Rig/KeyframeSequence called for imported clips; no default walk assets bundled |
| rbx-native | rbx_assets | 16 | 3195 | Original resource/cache APIs plus local Android seekable APK adapter |
| rbx-native | rbx_audit | 9 | 3310 | Not integrated in the Android application |
| rbx-native | rbx_autorig | 28 | 10067 | Not integrated in the Android application |
| rbx-native | rbx_binary | 46 | 7651 | Original editor file codec |
| rbx-native | rbx_cloud | 55 | 13031 | Renderer asset provider; login/publication UI not adapted |
| rbx-native | rbx_dom | 34 | 6981 | Authoritative edit/save document and temporary render projection |
| rbx-native | rbx_eyedropper | 5 | 526 | Not integrated in the Android application |
| rbx-native | rbx_figma | 11 | 4390 | Not integrated in the Android application |
| rbx-native | rbx_import | 9 | 1475 | Not integrated in the Android application |
| rbx-native | rbx_loren | 66 | 16741 | Not integrated in the Android application |
| rbx-native | rbx_lua | 118 | 25930 | Original edit command bar; Play scripts use Rook VMs |
| rbx-native | rbx_materials | 3 | 481 | Original renderer materials |
| rbx-native | rbx_mesh | 16 | 3496 | Original renderer mesh types/codecs |
| rbx-native | rbx_parser_cli | 20 | 2389 | Not integrated in the Android application |
| rbx-native | rbx_play | 73 | 15920 | Not integrated in the Android application |
| rbx-native | rbx_presence | 49 | 12305 | Not integrated in the Android application |
| rbx-native | rbx_reflection | 12 | 1806 | Original editor metadata |
| rbx-native | rbx_studio | 970 | 256232 | Domain files extracted with hashes by prepare.py; GPUI desktop shell excluded |
| rbx-native | rbx_terrain | 24 | 4855 | Original terrain render data; sculpting UI pending |
| rbx-native | rbx_ugc | 13 | 2618 | Not integrated in the Android application |
| rbx-native | rbx_viewer | 408 | 108302 | Single original renderer, GPU presentation, selection and gizmos |
| rbx-native | rbx_wasm | 5 | 955 | Not integrated in the Android application |
| rbx-native | rbx_xml | 35 | 3757 | Original editor file codec |
| rook | rook-audio | 21 | 3214 | Original engine audio graph/decoder linked; Android output is not connected |
| rook | rook-content | 35 | 6983 | Original resolver plus shared local Android resource callback |
| rook | rook-dom | 14 | 2135 | Original runtime server/client DOMs, never saved over the editor project |
| rook | rook-engine | 65 | 12749 | Original Session/server/client/host called during Play |
| rook | rook-formats | 8 | 2076 | Original loader/conversion; file_property adapter preserves original encodings |
| rook | rook-gui | 50 | 9365 | Original engine layout/input; visual and widget coverage partial |
| rook | rook-lua | 103 | 20943 | Original runtime, scheduler, APIs and signals in Play; upstream coverage remains partial |
| rook | rook-net | 5 | 935 | Original local replication/remotes through Session; Internet transport not exposed |
| rook | rook-physics | 45 | 10403 | Original Rapier physics, collision, Humanoid and joints |
| rook | rook-platform | 8 | 820 | Original portable files/threads/HTTP; desktop clipboard/keyring excluded |
| rook | rook-reflection | 11 | 1945 | Original runtime metadata |
| rook | rook-render | 60 | 14571 | Only unchanged procedural head extracted; the Rook renderer is excluded |
| rook | rook-terrain | 24 | 4173 | Original engine terrain/collision dependency; extensive worlds not validated |
| rook | rook-types | 20 | 3477 | Original runtime value/math types |
| rook | rook-view | 46 | 6060 | Not integrated in the Android application |
