# Play 0.3 — contrato atual

Play agora chama `rook_engine::session::Session` original: um servidor e um cliente locais, com suas VMs e DOMs separados, replicação e entrega de remotes originais. `rbx_lua` permanece no editor para o command bar; não executa os scripts do Play. Internet multiplayer não está exposto.

O editor retém DOM, seleção e estado de salvamento anteriores. O cliente Rook fornece uma projeção para o renderer original `rbx_viewer`. Parar restaura o documento exato, inclusive propriedades desconhecidas. A projeção temporária nunca substitui o arquivo salvo.

| Área | Integração |
|---|---|
| Scripts/LocalScripts/ModuleScripts | Hosts, scheduler, API e critérios de execução originais do Rook |
| Replicação local e RemoteEvent | ServerHost/ClientHost/Session originais; sem transporte de Internet na UI |
| R6, StarterCharacter, física, respawn | Sistemas originais do Rook; aparência externa depende dos recursos disponíveis |
| Cabeça | A malha real tem prioridade; na ausência dela, usa a geometria arredondada original de `rook-render/src/mesh/head.rs` |
| Frames, contatos e Humanoid | Gerados pelo engine original; diagnóstico lê o estado real do controlador |
| Entrada Android | Direcional/pulo, WASD/espaço e toque convertido em clique; não equivale ao conjunto completo de multitouch/input do Roblox |
| Câmera | Terceira pessoa com raycast original e CameraType Scriptable do cliente |
| GUI | Layout/input originais Rook, projeção para o renderer rbx-native; cobertura visual parcial |
| Animação | KeyframeSequences importadas usam Animator/Rig originais rbx-native e Motor6D físicos Rook; não inclui animações proprietárias de caminhada |
| Recursos | Instalação Roblox Android legível ou APK/ZIP escolhido no seletor; os mesmos bytes alimentam o resolver de runtime |

R15 completo, roupas/acessórios de conta, CoreScripts, áudio de saída Android, entrada completa por IME em TextBox do jogo, luau-lsp, login/publicação, plugins e todas as ferramentas de modelagem desktop continuam sem integração completa. Compilar um módulo não prova compatibilidade de todas as APIs. A validação está no relatório.
