# Auditoria inicial do RBX-Mobile 0.6.7

O inventário usa o APK ARM64 distribuído, SHA-256 `b424034bc873acc6b2f0014eb26027de7f4af2ffc69b57ad98f2dae93db446a4`, e o ZIP correspondente, SHA-256 `57cbee0af4b09a3dbcd6652ca8bfea317ad51c5c25b6d0c33338ca86e0a381fb`. O editor aberto pelo manifesto é `GpuiActivity`. A execução usa `RookPlayerActivity` no processo `:rook`. A atividade `MainActivity` do protótipo Java tem outra interface e ficou fora do inventário visual do editor distribuído.

## Evidência antes das alterações

- **Código 0.6.7.** Os caminhos abaixo ligam controles reais às operações do editor. Uma rota encontrada no código indica onde testar.
- **Gravação do usuário.** [1000255628.mp4](https://chatgpt.com/library?file_id=libfile_839f2489ba0c8191b1d0cb522130544c&account_id=personal), 1920 × 864, 31,2 s. Mostra Play, menu do jogo, retorno ao editor, seleção no Explorer e código Luau. A versão da gravação é desconhecida. Entre 21 e 27 s, a faixa de ferramentas e o Explorer concorrem com o código. Recolher a faixa aumenta a área visível. O vídeo mostra a abertura do código e a rolagem. Digitação, persistência e acessibilidade exigem teste separado.
- **Emulação tentada.** Google APIs Android 11 x86_64, 432 × 960 a 160 dpi, Lavapipe e CPU por software. O emulador concluiu o boot e anunciou `arm64-v8a` por `libndk_translation.so`. A transferência do APK de 67.216.459 bytes terminou. O Package Manager respondeu `Can't find service: package`. A primeira tentativa ficou bloqueada no Android antes de abrir o app.
- **Regressão de referência.** O ZIP contém evidência dos 55 testes aprovados, 3 ignorados e 8 cenários de diagnóstico da 0.6.7. Esses resultados cobrem o motor e a lógica do projeto. O teste de toque do APK constitui uma verificação distinta.

## Inventário e percurso de verificação

| Área | Entradas e operações identificadas na 0.6.7 | Ações de verificação |
|---|---|---|
| Inicialização e Home | Onboarding, entrada opcional no Roblox, Baseplate/templates, recentes, abrir arquivo e preferências | Instalar limpo, pular configuração, criar, reabrir e voltar da Home |
| Arquivos | File, abas de lugares, seletor SAF, RBXL/RBXLX e importação de modelos | Criar um arquivo de teste, salvar, reabrir e verificar conteúdo |
| Menus | File, Edit, Model, View, popovers de painéis, Command Palette e ribbon de sete categorias | Abrir, rolar até o fim, cancelar, tocar no item e confirmar o efeito |
| Explorer | Selecionar, pesquisar, expandir, inserir filho, menu de contexto, copiar/colar/excluir e histórico | Criar Part, Folder e Script, selecionar, renomear, copiar, excluir e desfazer |
| Properties | Campos por classe, valor escalar, enum, cor, atributos, tags e referências | Alternar Anchored e editar Name/Size; persistir no arquivo |
| Cena 3D | Seletor, câmera por arrasto, pinça, analógico, gizmos, foco e snap | Selecionar a base, girar, mover, ampliar, mover objeto e desfazer |
| Ferramentas avançadas | Rig, Animation, terreno, GUI, plugins, layers, configurações e diagnóstico | Encontrar cada painel, conferir abertura e retorno; testar ações com fixture apropriada |
| Luau | Abertura por dois toques, abas, modo Code/Graph, highlight, busca, histórico, IME e LSP opcional | Abrir Script, tocar no texto, digitar por IME, rolar e confirmar a gravação |
| Toolbox | Store, Inventory, Recent, busca, categorias, miniaturas, ID e arquivo local | Buscar e rolar, abrir detalhes, importar fixture RBXM e cancelar/revisar scripts |
| Segurança de modelos | Revisão de Script/LocalScript/ModuleScript antes de inserir, leitura e remoção de scripts | Contar as três classes em um modelo local e testar cancelar e inserir sem scripts |
| Play | Ribbon Test, instância Android separada, cliente/servidor, joystick, câmera, interação, hotbar, menu e saída | Abrir mapa, ver personagem e cena, andar, saltar, usar item e voltar ao mesmo editor |
| Diagnóstico e Output | Ação File, relatório de cenários e logs | Rodar em projeto de teste e conferir resultados e estado ao voltar |
| Publicação | File, autenticação autorizada, link do lugar, Save/Publish via Open Cloud e histórico | Conferir entrada e cancelamento. Ensaio de publicação requer um lugar de teste e credencial da conta |
| Acessibilidade e navegação | Foco GPUI, roles/labels na camada de renderização, botão de teclado do Android e elementos nativos de Play/SAF | Capturar árvore Android, verificar rótulo, foco, área de toque, teclado, Back e ações pelos nós |
| Layout e dispositivo | Paisagem, escala lógica do GPUI, docks, menus, rolagem e safe areas | Comparar geometrias 360 e 432 dp de altura, teclado aberto e tela larga |

## Problemas e hipóteses registrados

| ID | Prioridade | Evidência e impacto | Causa provável no código | Correção a validar |
|---|---|---|---|---|
| UI-01 | Alta | Vídeo, 21–27 s. A cena e o código dividem a altura com várias faixas; o comando mostra instrução de teclado físico | `shell.rs` monta menu, abas, ribbon, workspace e Command Bar em sequência. `layout.rs` mostra a Command Bar por padrão | Priorizar área útil em altura compacta e manter acesso às ferramentas e ao comando por um controle visível |
| UI-02 | Alta | Código. Play fica em Test, dentro da faixa que começa recolhida no Android. Reabrir painéis passa por View ou Home | `shell/chrome.rs`, `shell/ribbon.rs` e `menu_bar.rs` | Atalhos claros de toque para Play e painéis principais, ligados aos handlers existentes |
| UI-03 | Alta | Código e vídeo, 18–23 s. Abrir código depende de acertar dois toques na mesma linha | `shell/reparent.rs` encaminha ao editor em `click_count() >= 2` | Ação explícita para editar o script selecionado, usando `Shell::open_script` |
| UI-04 | Alta | Código. A coluna lateral pode encolher a 28% da janela, e 6 alvos de 28 px dividem uma faixa horizontal | `shell/workspace.rs` limita docks; a faixa `explorer_touch_actions` usa dimensões fixas | Painel com largura útil em telefone, ações prioritárias e atalhos contextuais de toque |
| UI-05 | Média | Código. Dropdown principal rola, mas a maioria das opções usa linha compacta; títulos e o botão de expansão têm alvos estreitos | `menu_bar/bar.rs` e `popup.rs` reaproveitam a densidade de desktop | Aumentar o alvo de ações no Android, limitar pelo espaço disponível e preservar a rolagem |
| UI-06 | Média | Código. As categorias de ribbon somam larguras fixas; somente a faixa dos comandos rola | `shell/chrome.rs` define espaçamento horizontal de 22 px e sem rolagem para os títulos | Fazer a navegação de categorias rolar em telas estreitas, com controle de fechar sempre alcançável |
| UI-07 | Média | Código. Toolbox usa ícones de 30 px sem rótulo visível para coleções e a opção de arquivo local fica atrás de um ícone | `shell/toolbox.rs` renderiza coleções como ícones e esconde as opções em `options_open` | Identificar a coleção ativa e tornar a importação local fácil de encontrar, preservando a revisão |
| UI-08 | Alta | Código. Os controles do editor são desenhados no GPUI e os métodos da ponte Android de árvore acessível usam o padrão vazio | `gpui-pre/src/platform.rs` e `gpui-mobile/src/android/window.rs` | Investigar adaptador AccessKit Android compatível e testar a árvore com UI Automator. Coordenadas ficam restritas a geometrias registradas |
| UI-09 | Alta | Código. IME depende de um View de 1 px e do tamanho da superfície após abrir o teclado. O vídeo só demonstra código aberto e rolagem | `GpuiImeView.java`, `upstream/gpui-mobile/src/android/ime.rs` e composição da janela | Medir área de texto e provar inserção/edição e persistência por toque em Android |
| UI-10 | Média | Código. Rook encerra a Activity quando recebe Back | `RookPlayerActivity.onBackPressed`; menu de pausa segue outra rota | Testar Back, saída e restauração. Validar a intenção antes de alterar o comportamento de Play |
| UI-11 | Média | Código. Muitos caminhos avançados vêm de um editor desktop e os binários auxiliares para Android são parciais | `rbx_studio/build.rs` e ferramentas de painel | Classificar cada entrada por disponibilidade, com amostras de uso e mensagens reais |
| UI-12 | Alta | A instalação ficou bloqueada pelo sistema do AVD Android 11 | Boot em TCG sem KVM, com serviço Android reiniciando. Causa do reinício ainda aberta | Repetir com Android 15 Google APIs e quatro vCPUs. Manter log de instalação separado do log da Activity |
| UI-13 | Alta | Código. Tocar Play logo depois de digitar pode executar uma cópia anterior do script | `scripts.rs` agrupa as alterações por 400 ms. `play/android.rs` clona o DOM antes de forçar a escrita pendente | Usar a mesma descarga de alterações que o salvamento antes de capturar o mapa |
| UI-14 | Média | Android 15 emulado, captura da Home 960 × 432. O cartão de Open Cloud fica entre o título e os templates; só o título New aparece no fim da tela. A opção de projeto local no onboarding tem peso visual baixo | `launcher/home_window/view/pages.rs` prioriza um banner de desktop com ilustração de 380 px. `wizard/frame.rs` usa o botão Ghost para projeto local | Mostrar templates primeiro em altura compacta, manter o caminho da chave e tornar o botão local visível |

## Referências consultadas

- A documentação Android recomenda alvos de toque de pelo menos 48 × 48 dp. Em paisagem, a altura disponível também orienta a composição da interface. [Acessibilidade de Views](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views) · [Classes de tamanho](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes).
- O Shapr3D agrupa ferramentas pela seleção e mantém comandos adicionais num menu. O Nomad separa os gestos que movem a câmera dos que manipulam o objeto. [Shapr3D](https://support.shapr3d.com/hc/en-us/articles/7378907587484-Accessing-tools) · [Nomad](https://nomadsculpt.com/manual/pt/interface).
- Maestro é open source. Os seletores de texto, ID e descrição dependem da árvore de acessibilidade. UI Automator também permite observar atividades e diálogos do sistema. [Maestro](https://github.com/mobile-dev-inc/maestro) · [UI Automator](https://developer.android.com/training/testing/other-components/ui-automator).
- AccessKit Android fornece um adaptador para árvores virtuais. A versão 0.7.5 usa AccessKit 0.24.1, que corresponde ao lockfile do GPUI nesta base. A integração precisa tratar a troca de janelas do editor, posição e escala dos nós, foco, ações e IME. [AccessKit](https://github.com/AccessKit/accesskit/releases) · [Views customizadas](https://developer.android.com/guide/topics/ui/accessibility/views/custom-views).
- Ramus oferece emulador em nuvem com captura, toque e logcat. Exige uma sessão autenticada e declara que o APK deve rodar em x86_64. A tradução ARM64 e Vulkan precisam ser verificadas na imagem do provedor. [Ramus Quickstart](https://ramus.dev/docs/quickstart) · [MCP do Ramus](https://ramus.dev/docs/mcp).
- O emulador oficial permite CPU por software com `ANDROID_I_WANT_MY_TCG=yes`; o próprio Google registra o custo e o suporte limitado. Imagens Google APIs específicas oferecem tradução ARM. [Linha de comando do emulador](https://developer.android.com/studio/run/emulator-commandline) · [Tradução ARM](https://android-developers.googleblog.com/2020/03/run-arm-apps-on-android-emulator.html).
