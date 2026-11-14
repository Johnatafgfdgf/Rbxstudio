# Integração real dos componentes — 0.3

O relatório de testes é a autoridade sobre resultados. Esta tabela descreve chamadas conectadas ao aplicativo; a presença ou compilação de uma dependência não prova cobertura de todas as APIs. Desempenho/estabilidade no POCO X6 5G ainda exigem teste físico.

| Recurso | Código utilizado | Integração e limite |
|---|---|---|
| Documento de edição, Instances, referências | rbx-native DOM/reflection | Autoritativo para edição e salvamento |
| RBXL/RBXLX e RBXM/RBXMX | Codecs e merge originais | Place e importação separados; desconhecidos preservados nos casos cobertos |
| Save/autosave/undo/redo | Studio history/save originais | Escrita atômica; um gesto por entrada no histórico |
| Properties, mover/girar/escalar | Módulos de domínio Studio, ScaleDragger original | Adaptados ao toque; ferramentas desktop adicionais pendentes |
| Explorer e seleção | Hierarquia nativa e pick original | Painéis sobrepostos; drag/reorder do Explorer pendente |
| Renderer, materiais e shaders | rbx_viewer/materials/mesh originais | Um renderer; sombras, normal maps, meshes e efeitos do upstream |
| Apresentação Android | SurfaceView/JNI/wgpu | Passe GPU sRGB; sem readback CPU por frame |
| Qualidade/desempenho | Níveis originais, caches, idle/tick | Resolução interna configurável, 30/60 Hz; sem FPS físico prometido |
| Recursos locais | Leitor APK original rbx-native adaptado | Instalação legível e APK/ZIP via seletor; content/ExtraContent/splits e reload |
| Recursos privados/remotos | Provedores originais | Acesso depende de autorização; login/API key não expostos pela UI |
| Cabeça R6 | Malha solicitada + head.rs original Rook | Fallback arredondado original; nunca gravado sobre cache/asset baixado |
| Command bar/editor de scripts | rbx_lua/compiler e IME Android | Editor lexical; LSP e UI GPUI completos pendentes |
| Play | rook-engine::Session original | Servidor e cliente locais, VMs e DOMs próprios |
| Scripts, LocalScripts, ModuleScripts | rook-lua/scheduler originais | Executados pelos hosts originais; compatibilidade segue o upstream |
| Replicação/remotes locais | rook-net, ServerHost/ClientHost | Entrega local original; multiplayer pela Internet não exposto |
| Física, contatos, Humanoid | Rapier/Rook originais | Caminhada, pulo, colisão, Touched e estados reais |
| R6, StarterCharacter, spawn/respawn | Sistemas originais Rook | Recursos externos/rigs avançados não são cobertura integral de avatares |
| Input do jogo | Ponte para entrada Rook | Direcional, pulo, WASD/espaço e toque como clique; multitouch completo pendente |
| Câmera | Ponte de terceira pessoa + câmera cliente | Raycast original e Scriptable; PlayerModule proprietário não incluído |
| GUI do jogo | Layout/input Rook e render rbx-native | Ponte de clique conectada; widgets/IME/cobertura visual parciais |
| Animação de rig | Animator/Rig/KeyframeSequence rbx-native | Sequências importadas aplicadas em Motor6D Rook; sem ciclos proprietários de caminhada |
| Stop | Snapshot original de edição | Restaura documento, seleção e dirty; Play não sobrescreve o projeto |
| Terreno | rbx_terrain + rook-terrain/physics | Render/dados/colisão; ferramentas móveis de sculpt e cenários extensos pendentes |
| Áudio | rook-audio pelo engine | Graph/decode ligados; saída de som Android pendente |
| R15/aparência de conta/roupas/acessórios | Recursos e sistemas adicionais | Sem integração completa ou validação física |
| Modelagem avançada/CSG/mesh/autorig | Fontes originais preservadas | Ferramentas Android completas não portadas |
| Autocomplete/diagnóstico semântico | luau-lsp não conectado | Pendente; checagem atual é sintática |
| Plugins, Rojo, Lune | Fora da aplicação | Não integrados |
| Login/publicação Roblox | Sem fluxo de conta adaptado | Não integrados |
| Safe area/recortes/teclado de scripts | WindowInsets Android | Ligados ao layout; validar em recortes e teclados reais |

`MODULE_AUDIT.md` e `module-inventory.json` inventariam os 39 crates. As adaptações reproduzíveis estão nos patches consolidados; os antigos são mantidos como histórico e ignorados quando existe o consolidado daquele repositório. O shell desktop GPUI, o renderer Rook inteiro e os módulos listados como pendentes não são apresentados como port concluído.
