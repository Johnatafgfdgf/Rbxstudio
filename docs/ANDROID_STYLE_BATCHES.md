# Revisão visual do RBX-Mobile

A contagem usada nos lotes é de **27 superfícies principais**. São 21 painéis
do registro `Panel::ALL`, quatro entradas principais da interface e duas
janelas de configuração. Submenus, popovers, barras compartilhadas e editores
especializados fazem parte da revisão como componentes das superfícies.

| Lote | Superfícies | Implementação |
| --- | --- | --- |
| 1 | Início, Menu (File, Edit, Model, View), Ferramentas, Painéis, Explorer | Estilo Android aplicado e prévia interativa exercitada no navegador. |
| 2 | Properties, Toolbox, Output, Viewport, Terrain Editor | Pendente. |
| 3 | Input Actions, Layers, Playtests, Security Audit, Collaborators | Pendente. |
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
