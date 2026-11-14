# RBX-Mobile 0.6.7 — integração do motor

Base desta etapa: `Rbx-Studio-0.6.6-Fonte.zip`, SHA-256 `53705e3c6b22126ff476b400e1b9fb2181bf5fed9b93fc25289413d1b5faf9c1`. A base continua sendo rbx-native com a sessão e o motor Rook. Os três upstreams preparados, as licenças e as adaptações anteriores acompanham o pacote de fontes. Não foi utilizado Stew Studio.

## O que mudou

- **Tool criada por LocalScript:** antes, a criação de RightGrip retornava imediatamente no cliente. Agora o objeto local ganha a junta da mão, segue Grip e perde a junta ao desequipar ou destruir. Quando uma Tool do servidor é equipada no cliente, a junta prevista é removida quando a junta do servidor chega. Isso evita duas juntas rígidas para o mesmo Handle.
- A hotbar lista as Tools do Backpack e do Character, acompanha entregas, remoções e respawns e equipa ou guarda por toque e pelas teclas 1–0. Os slots crescem com a resolução e podem ser acessados por rolagem horizontal. A interface de reserva fica no CoreGui. Ela respeita `StarterGui:SetCoreGuiEnabled(Backpack, false)` e se desativa quando encontra a interface original de inventário.
- **Respawn:** o jogador recebe StarterGear, e o Backpack recebe clones de StarterPack e StarterGear ao recriar o personagem.
- **Mira por toque:** Mouse.Hit e Mouse.Target são atualizados antes de Activated, tanto no toque curto quanto na utilização mantida.
- **RequiresHandle:** Equipped, Unequipped e Activated deixam de ocorrer quando a Tool exige Handle e não tem uma BasePart chamada Handle. Tools com RequiresHandle=false continuam funcionando sem peça física.
- **Movers modernos:** LinearVelocity, AngularVelocity, AlignPosition e AlignOrientation passam a aplicar forças/torques no solver físico, em vez de apenas existir na árvore. Incluem alvos e limites em execução, modos Vector/Line/Plane, eixos de referência, alinhamento com um ou dois Attachments e reações entre assemblies. A orientação usa arco de quaternion, inclusive a 180°. A integração usa as massas e inércias do Rapier; não move a peça por teleporte nem implementa um voo particular.
- **Diagnóstico:** `File → Diagnóstico do motor…` executa cenários isolados em segundo plano. Os resultados e falhas aparecem no Output; o relatório JSON mais recente também é guardado pelo aplicativo. A execução usa VMs e física reais, sem abrir, publicar ou alterar o projeto em edição. Uma segunda execução simultânea e o início de Play durante esse diagnóstico são recusados para limitar o consumo de memória.

## Testes reproduzíveis

`cargo test --locked -p rbx_android_core --tests`

`cargo run --locked -p rbx_android_core --example engine_diagnostics -- resultado.json`

Os cenários compartilhados com o celular estão em `core/src/diagnostics/`. Cada afirmação Luau usa objetos da sessão real. Erros Luau fazem o teste falhar; o fato de um script compilar ou uma classe existir não é considerado sucesso.

| Cenário | Consequências verificadas |
|---|---|
| tool_aim | Raycast e Mouse.Hit/Target atualizados antes de Activated por toque |
| tool_delivery | Toque em Receber item, RemoteEvent, clone no servidor, replicação ao Backpack, presença na hotbar, equipar por toque, uma RightGrip válida nos dois lados, Handle próximo da mão, Activated/Deactivated no Script servidor, guardar por toque e Unequipped |
| local_tool | Tool criada somente no cliente, junta física, Grip dinâmico, ativação, desequipar, Destroy, RequiresHandle e ausência do objeto no servidor |
| mover_constraints | Velocidade linear, sustentação contra gravidade, velocidade angular, convergência de posição/orientação e retorno à queda ao desativar |
| constraint_modes | Eixos livres em Line/Plane, força limitada por eixo, Attachment1, reação linear e angular, alvo em outro Attachment, alteração de Attachment durante Play e alinhamento de eixo primário |
| humanoid_forces | Constraint criada no cliente atua no assembly do Humanoid em Physics, movimento chega ao servidor e remover a constraint devolve a queda ao controlador |
| runtime_objects | require de ModuleScript, task.spawn/delay/wait, BindableEvent, Clone/Destroy, Tween e alterações visíveis no cliente |
| inventory_respawn | Dois respawns mantêm as ferramentas e clonam StarterGear sem acumular cópias. Os slots acompanham a resolução e somem quando o jogo desativa a hotbar |

O diagnóstico não equivale a uma certificação no Roblox oficial. Os limiares físicos verificam invariantes e cenários documentados; a precisão numérica do solver proprietário não foi comparada nesta execução.

## Matriz de compatibilidade real

| Sistema | Estado nesta etapa | Limite conhecido |
|---|---|---|
| Scripts/LocalScripts/ModuleScripts | Runtime e VMs existentes preservados; cenários reais exercitados | Não há garantia para todos os serviços e membros do Roblox |
| Instance, sinais, task, require, Tween | Criação, clone, destruição, eventos e mudança de propriedades exercitados | A matriz não certifica todos os métodos/sobrecargas |
| Cliente/servidor local | Entrega por RemoteEvent e pose do personagem verificadas | Não é uma conexão a servidores de jogos Roblox |
| Backpack/Tool/Grip | Ciclo integrado e objetos locais testados | Inventários próprios e pacotes arbitrários ainda dependem das APIs que utilizam |
| StarterPack/StarterGear | Reposição/clonagem em respawn testadas | Aquisições de itens por serviços comerciais não são simuladas |
| Hotbar | Reserva em CoreGui com rolagem e seleção por toque | Aparência da interface no POCO e miniaturas de cada Tool ainda exigem inspeção física; a reserva usa o nome do item |
| LinearVelocity | Vector/Line/Plane, RelativeTo, Enabled e limites/reação exercitados | Não reproduz o solver proprietário; combinações complexas de mecanismos exigem novos cenários |
| AngularVelocity | Velocidade, referência e reação exercitadas | Conservação geral em sistemas com eixos bloqueados precisa de cobertura adicional |
| AlignPosition | OneAttachment/TwoAttachment e convergência exercitadas | Força aplicada fora do centro de massa e mecanismos multijunta precisam de comparação adicional |
| AlignOrientation | AllAxes a 180° e PrimaryAxisParallel exercitados | Perpendicular, LookAt, propriedades de eixos, RigidityEnabled e diferentes limites têm implementação, mas não foram todos comparados ao Roblox |
| Forças e constraints anteriores | Implementações Rapier/Rook mantidas | Hinge, BallSocket, Spring e Rope não significam veículos completos; limites angulares avançados continuam parciais |
| Humanoid | Movimento anterior preservado; constraint em Physics exercitada | EvaluateStateMachine, assentos/veículos, detalhes de escadas, natação e R6/R15 continuam sem paridade integral |
| Animator/Motor6D | Integração anterior preservada | Formatos, prioridades/misturas e rigs arbitrários não foram todos validados |
| Entrada/câmera | Rotas anteriores de toque, GUI, analógico e câmera mantidas | Não houve ensaio no POCO X6 5G nesta execução |
| GUI | Botão real e inventário entram na suíte | Widgets avançados, safe areas e teclado dependem de cobertura Android adicional |
| Toolbox | Contagem/revisão de scripts e fluxo anterior mantidos | Análise estática não prova segurança; modelos podem depender de APIs ausentes |
| Vento, partículas, Beam/Trail | Renderização anterior preservada | O asset exato do vídeo continua sem reprodução verificável; não foi declarado corrigido nesta etapa |
| Assets/Spawn/skybox | Resolvedor e fallback anteriores mantidos | Assets privados ou restritos continuam exigindo acesso autorizado |
| Explorer/Properties/histórico | Rotas existentes preservadas | O conjunto integral de funções do Studio não foi revalidado por toque |
| Editor Luau/LSP/Rojo/Lune | Integrações existentes mantidas | Executáveis e serviços externos não viraram ferramentas Android completas nesta etapa |
| Publicação | Rotas Open Cloud e serialização preservadas | A publicação real com a conta do usuário não foi repetida; testes de arquivo não substituem esse ensaio |
| Desempenho/Adreno | Defaults econômicos e separação editor/player preservados | Nenhum FPS, uso térmico ou consumo de memória no POCO foi medido |

## Arquivos e reprodução

A suíte principal passou **55 testes, com 0 falhas e 3 ignorados**. O executável de diagnóstico passou **8 cenários, com 0 falhas**. A compilação Java verificou os 21 arquivos de fonte do host Android. A preparação limpa e a repetida reconstruíram os três upstreams com os patches e conservaram os 34 arquivos gerados.

Os três testes ignorados exigem recursos gráficos ou pacotes oficiais. A tentativa online de conferir recursos/controles oficiais foi bloqueada neste host: inicialmente conexão recusada e, usando o proxy do ambiente, `UnknownIssuer` na cadeia TLS. Isso não foi classificado como permissão negada de um asset. Não foi desativada a verificação de certificados. Os testes internos de `rook-physics` e `rook-engine` também não foram executados como pacotes isolados da workspace principal, porque Cargo rejeita os dev-dependencies de pacotes excluídos; não foram contados como aprovados.

A regressão também identificou e eliminou a interferência de uma hotbar em PlayerGui com scripts que buscam a primeira ScreenGui. O inventário de reserva fica no CoreGui. O workflow `engine-compatibility.yml` executa a mesma suíte e produz o JSON em pushes da linha `work/rbx-mobile-*`.

A primeira execução no GitHub Actions, `42929320081`, terminou com falha sem etapas nem logs disponíveis. A suíte aprovada foi executada no outro ambiente de nuvem usado para compilar o APK. O build registrou que o `luau-lsp` precisa de um executável para `android/aarch64` para que o editor use o servidor de linguagem nessa arquitetura.

O código principal do diagnóstico fica em `core/src/diagnostics.rs`, nos scripts `core/src/diagnostics/*.luau`, em `core/tests/engine_diagnostics.rs` e no exemplo `core/examples/engine_diagnostics.rs`. O host está em `studio/src/player.rs`.

Os arquivos do motor alterados nesta etapa incluem `rook-physics/src/joints/movers.rs` e `movers/modern.rs`, `rook-lua/src/api/methods/tools.rs`, `characters.rs`, `signal/expand.rs`, `rook-lua/src/lib.rs`, `rook-engine/src/frame.rs`, `session/host.rs` e `session/host/inventory_ui.luau`. O menu está em `rbx_studio/src/menu_bar.rs`, `menu_bar/actions.rs`, `android_play.rs` e `shell/play/android.rs`. Essas alterações são reproduzidas pelos patches consolidados em `patches/`.

O manifesto e o empacotamento identificam a versão 0.6.7. A assinatura usa a mesma chave da 0.6.6, certificado SHA-256 `0b36c4e4587b40ecc16dbdca22fcaa20149432532567cc9acf44355c5b83711c`.

## Referências públicas

- [Tool](https://create.roblox.com/docs/reference/engine/classes/Tool) e [Humanoid](https://create.roblox.com/docs/reference/engine/classes/Humanoid).
- [Movers](https://create.roblox.com/docs/physics/mover-constraints), [LinearVelocity](https://create.roblox.com/docs/reference/engine/classes/LinearVelocity), [AngularVelocity](https://create.roblox.com/docs/reference/engine/classes/AngularVelocity).
- [AlignPosition](https://create.roblox.com/docs/reference/engine/classes/AlignPosition), [AlignOrientation](https://create.roblox.com/docs/reference/engine/classes/AlignOrientation), [ActuatorRelativeTo](https://create.roblox.com/docs/reference/engine/enums/ActuatorRelativeTo) e [AlignType](https://create.roblox.com/docs/reference/engine/enums/AlignType).
- [StarterGear](https://create.roblox.com/docs/reference/engine/classes/StarterGear).

Não foi medido um percentual global de compatibilidade. Esta entrega não afirma equivalência de 99,9% ao Roblox Engine.
