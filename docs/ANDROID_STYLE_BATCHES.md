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
| 4 | Script Analysis, Watch, Call Stack, Tests, Argon | Pendente. |
| 5 | Wally, Plugin Widgets, Loren, Avatar Settings, Localization | Pendente. |
| 6 | Studio Settings, Game Settings | Pendente. Inclui revisão das superfícies secundárias. |

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
