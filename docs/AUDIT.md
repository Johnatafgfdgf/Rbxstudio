# Auditoria de compatibilidade

Inspeção de código-fonte real em 1 de novembro de 2026. Recursos listados como portados ainda dependem da validação indicada no relatório de testes.

| Projeto / componente | Constatação no fonte | Android / decisão |
|---|---|---|
| rbx-native / rbx_viewer | wgpu 30, winit 0.30; renderer separado do GPUI | Portar este renderer; SurfaceView/JNI; Vulkan/GLES no mesmo pipeline |
| rbx_studio | GPUI Kit, X11, keyring, diálogos e executáveis auxiliares desktop | Separar domínio original; adaptar apenas os serviços e UI Android |
| rbx_dom / binary / xml / reflection | Rust, árvore própria e preservação de variantes | Compilados como núcleo; codecs originais usados |
| assets / materials / mesh / terrain | CPU Rust e shaders WGSL, cache/HTTP assíncronos | Mantidos; nenhum pacote proprietário redistribuído |
| rbx_lua | mlua 0.12 Luau vendored, scheduler, signals e binding WeakDom | Integrado com NDK/C++; sandbox e orçamento do upstream |
| rbx_animation / autorig / import / ugc | Módulos reais, fluxos dependentes da Shell | Fontes preservados; ferramentas ainda sem adaptação Android |
| rbx_audit / loren / presence / cloud | Auditoria, framework, rede e APIs adicionais | Só cloud transitivo para resolver; fluxos de login/publicação não portados |
| rbx_wasm | Plugins via wasmtime, não o renderer web | Fora desta build; não necessário para portar viewport |
| rbx_play | Integração com player, canais e executáveis desktop | Não integrado; Play local usa runtime original + Rook |
| Rook / physics | Rapier 0.36, assemblies, joints, colliders, fixed step | Integrado por API original, sem renderer duplicado |
| Rook / dom, types, reflection, formats, terrain | Crates portáveis com journal, migração e codecs | Compilados e usados pelo adaptador de simulação |
| Rook / engine, lua, net, audio, gui, render, content | Dependências desktop; renderer concorrente; content usa ISPC, áudio cpal, keyring/clipboard | Crates completos não integrados; funções puras de personagens de rook-lua foram extraídas e usadas, sem sua VM; demais integrações precisam de adaptação por módulo |
| Luau oficial | C++ portátil; licença MIT; fonte vendorizado pelo mlua/luau0-src | Compilado pelo NDK por meio da dependência original rbx_lua |
| luau-lsp | MIT; servidor C++ e integração desktop no rbx_studio build.rs | Fonte auditado; sem binário/cliente LSP Android nesta build |
| rojo-rbx/rbx-dom | MIT; família distinta da árvore própria rbx-native | Usado transitivamente pelo rook-formats, sem substituir o DOM autoritativo |
| Rojo | MPL-2.0; sincronização de arquivos/servidor desktop | Não integrado; prioridade posterior |
| Lune | MPL-2.0; runtime/automação independente, APIs de host | Não integrado; evitar terceira VM nesta etapa |

Não se incorporou o Rook inteiro. O rbx-native não é um port do Roblox Studio oficial e esta aplicação não inclui o motor proprietário Roblox. Materiais/meshes/efeitos são as implementações reais do upstream, com suas aproximações e recursos ainda incompletos.

