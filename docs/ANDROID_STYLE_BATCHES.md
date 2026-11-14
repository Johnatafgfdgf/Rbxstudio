# Revisão visual do RBX-Mobile

A contagem usada nos lotes é de **27 superfícies principais**. São 21 painéis
do registro `Panel::ALL`, quatro entradas principais da interface e duas
janelas de configuração. Submenus, popovers, barras compartilhadas e editores
especializados fazem parte da revisão como componentes das superfícies.

| Lote | Superfícies | Implementação |
| --- | --- | --- |
| 1 | Início, Menu (File, Edit, Model, View), Ferramentas, Painéis, Explorer | Estilo Android aplicado e prévia interativa exercitada no navegador. |
| 2 | Properties, Toolbox, Output, Viewport, Terrain Editor | Estilo Android aplicado. Prévia exercitada por toque, incluindo filtros, recolher Output e alternar a ferramenta de terreno. |
| 3 | Input Actions, Layers, Playtests, Security Audit, Collaborators | Estilo Android aplicado. |
| 4 | Script Analysis, Watch, Call Stack, Tests, Argon | Estilo Android aplicado. |
| 5 | Wally, Plugin Widgets, Loren, Avatar Settings, Localization | Estilo Android aplicado. Todas as 21 gavetas usam a mesma transição. |
| 6 | Studio Settings, Game Settings | Estilo Android aplicado. Revisão adicional de Data Stores, editores e menus compartilhados. |

## Lote 1

| Entrada | Local anterior e atual | Handler ou estado preservado |
| --- | --- | --- |
| Criar, abrir, recentes, conta e recuperação | Início, abas e cartões existentes com nova apresentação | `launcher/home_window/view/{pages,recovered}.rs` |
| Menu | Primeiro botão. File, Edit, Model e View no painel superior | `menu_bar.rs` mantém o registro de ações e atalhos. |
| Ferramentas | Segundo botão. Home, Avatar, UI, Script, Model, Test e Plugins | `shell/ribbon.rs`, `shell/ribbon/commands.rs`, `shell/plugins/ribbon.rs` |
| Painéis | Terceiro botão. Abre o seletor de painéis | `shell/mobile.rs` usa `set_panel_open`. |
| Selecionar, Mover, Girar, Escala, transformação | Barra do editor ou seletor da ferramenta ativa | `shell/mobile.rs` usa `transform_action`. |
| Salvar, Play | Extremo direito, fora da área rolável | `save_in_place` e `play_start`/`play_stop` |
| Busca, árvore e seleção | Gaveta Explorer | `shell/workspace.rs`, `shell/rows.rs`, `shell/query_select.rs` |
| Cut, Copy, Paste, Duplicate, Rename e demais ações da instância | Menu de contexto no Explorer. Ações globais do Edit permanecem acessíveis | `shell/explorer_edit/menu.rs`. Home já omitira a faixa duplicada no Android. |

Validação da prévia em Chromium com seis larguras e movimento reduzido.
Cobertura de navegação de Início, quatro menus, sete abas de Ferramentas,
21 entradas de Painéis e 19 ações de contexto do Explorer. O APK e os handlers
nativos exigem uma rodada própria de validação em Android.

## Lote 2

| Superfície | Controle e posição | Handler ou estado |
| --- | --- | --- |
| Properties | Busca no topo. Categorias recolhíveis e campos na coluna | `panels::properties`, `toggle_category`, `properties::edit` |
| Toolbox | Loja, Inventário, Recentes e Criações no topo. Opções na busca. Adicionar abaixo da miniatura | `Toolbox::{show_collection,search,import_asset}`; `finish_toolbox_review` |
| Output | Busca, filtros, contexto, Clear, opções e recolher no cabeçalho inferior rolável | `output_filter`, `output_context`, `output.clear`, `output_collapsed` |
| Viewport | Seções Rendering, Camera, Overlays, Dragging & Snapping e Performance | `viewport_toggles`, `set_dragger`, `set_perf_metric` |
| Terrain Editor | Guias Create e Edit. Ferramentas em grade e parâmetros existentes abaixo | `terrain.tab`, `use_terrain_tool`, `tool_sections` |

O seletor de categorias, a busca, a inserção e o desfazer da Toolbox foram
exercitados com dados locais da prévia. A revisão Luau e o importador reais
exigem Android. O Output usa o estado de recolhimento existente e deixa
Expandir, Recolher e Fechar disponíveis mesmo com o histórico recolhido.

## Lote 3

| Superfície | Controle e posição | Handler ou estado |
| --- | --- | --- |
| Input Actions | Check e New InputContext no topo. Tabela rolável e inspetor abaixo | `input_toolbar`, `input_table`, `input_inspector` |
| Layers | Criar, atribuir e fixar nota na barra rolável. Camada ativa destacada | `add_layer`, `assign_selection_to_active_layer`, `pin_note` |
| Playtests | Play da gravação, velocidade, mapas e taxa de amostragem | `playtests_toggle_play`, `speed`, `playtests_set_heat`, `playtests_set_rate` |
| Security Audit | Re-run junto do resumo. Resultados roláveis e links para o código | `queue_audit`, `reveal`, `select` |
| Collaborators | Compartilhamento e status, relay em Advanced | `set_team_presence_enabled`, `toggle_presence_advanced`, `save_presence_relay` |

O comando New InputContext da barra substitui sua duplicação no estado vazio
Android. O botão de reprodução em Playtests continua dedicado a gravações locais.

## Lote 4

| Superfície | Controle e posição | Handler ou estado |
| --- | --- | --- |
| Script Analysis | Retry junto do resumo quando há falha. Lista de diagnósticos rolável | `ensure_luau_lsp`, `restart_luau_lsp`, `reveal_problem` |
| Watch | Variables e My Watches no topo, campo de expressão no rodapé | `debug.tab`, `watch_input`, `request_watch_values` |
| Call Stack | Pilha e frame ativo no corpo da gaveta | `select_frame`, `stack_scroll` |
| Tests | Rerun on save e Run all no topo. Execução individual na linha | `run_all_tests`, `rerun_on_save`, `tests_tree` |
| Argon | Conexão e opções existentes, em coluna rolável | `argon_connect`, `argon_disconnect`, `argon_set` |

## Lote 5

| Superfície | Controle e posição | Handler ou estado |
| --- | --- | --- |
| Wally | Busca e abas Home, Installed e Updates na faixa superior | `wally_search_for`, `wally_set_page` |
| Plugin Widgets | Abas roláveis e fechar junto ao título | `plugins.widgets.current`, `Input::WidgetClosed` |
| Loren | Link do framework, listas Server e Client empilhadas. Estado vazio aponta para o modelo já disponível | `loren_body`, `loren_row_clicked` |
| Avatar Settings | Abas General, Body, Clothing, Accessories e Movement em faixa rolável | `set_avatar_tab`, `apply_avatar_writes`, `sidebar_hidden` |
| Localization | Modos Overflow e Strings, controles roláveis na mesma barra | `set_pseudo_locale`, `write_localization_table`, `localization.tab` |

## Lote 6

| Superfície | Controle e posição | Handler ou estado |
| --- | --- | --- |
| Studio Settings | Busca e dez páginas. No compacto, categorias e atalhos em faixa rolável | `settings_window::{nav,kit,search}`; as alterações continuam aplicadas pelos setters existentes. |
| Game Settings | Oito categorias na faixa superior do Android. Save e Cancel no rodapé | `GameSettingsWindow::{save,revert}`; os mesmos cálculos de permissões e campos inválidos comandam a disponibilidade. |
| Data Stores | Seletor de experiência e Refresh no cabeçalho. Guias Data Stores, Ordered Data Stores e Memory Store abaixo | `DataStoresWindow::{pick_experience,refresh,set_section}`; a lista padrão permite rolagem horizontal se as colunas não couberem. |
| Editor de Scripts | Abas e fundo padronizados, acesso pelo Explorer, foco e teclado existentes | `script_panel::script_editor`, `activate_script`, `focus_script` |
| Editor de UI e Animation Editor | Abas de documento e faixa de ferramentas com o mesmo tema | `set_ui_tab`, `animation_strip`, `anim.mode` |
| Menus do editor | As 55 famílias de `MenuId` passam pela apresentação móvel. Inclui seletores de inserção, material, dispositivo, campos de animação, Wally, Argon e abas de documento | `shell/menu.rs`, `gpui-base/src/{popover,popup}.rs`; as listas de comandos e seus handlers continuam vindo dos módulos existentes. |
| Outras janelas | Botões e diálogos que usam `launcher::ui`, menu de Recentes e componentes que usam o tema padrão do toolkit | `launcher/ui.rs`, `launcher/home_window/view/recent_menu.rs`, `theme/apply.rs` |

## Verificação

- Chromium exercitou os quatro menus principais, sete abas da faixa de ferramentas,
  abertura das 21 gavetas, 19 ações de contexto do Explorer e as 18 categorias
  de configurações. Foram usados sete tamanhos de 320 a 960 px, incluindo 904 × 407
  e 960 × 432, alternância de painel, redução de movimento e reabertura durante
  o fechamento. A prévia também exercitou a sequência de seleção, inserção,
  corte e desfazer com uma cópia do projeto de exemplo.
- Na verificação da prévia, a troca de categoria em Game Settings fazia Esc
  perder o alvo. O botão ativo recupera o foco após a troca e o teste de
  fechamento por Esc passa.
- O patch consolidado foi aplicado a uma cópia limpa do rbx-native fixado
  pelo submódulo e reproduziu os arquivos editados. A análise sintática dos
  arquivos Rust não encontrou erros novos em relação à base.
- Execução nativa, compilação, Android IME, gestos 3D, serviços externos,
  autenticação e publicação ficam para a validação com APK. Transições de
  fechamento dos diálogos gerenciados separadamente ainda dependem do
  fechamento da janela ou overlay existente. A prévia usa dados de exemplo.
