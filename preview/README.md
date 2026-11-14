# Prévia interativa do Rbx Studio

Abra `preview/index.html` no navegador ou use um serviço de visualização de HTML hospedado no GitHub. Não há dependências externas ou necessidade de instalar APK.

## Objetivo
Validar rapidamente a organização da interface, o estilo visual e o uso em modo paisagem **antes de editar os componentes GPUI/Rust do aplicativo Android**. Esta é uma prévia funcional de navegação e aparência; **não é o renderizador real do aplicativo** e, portanto, não garante pixels idênticos ao APK.

## O que já pode ser testado
- Tela inicial, cartões e entrada no editor de demonstração.
- Menus File, Edit, Model e View com as ações identificadas no código do editor.
- Sete abas de ferramentas: Home, Avatar, UI, Script, Model, Test e Plugins.
- Seletor dos 21 painéis declarados no `Panel::ALL` da revisão copiada, com exemplos navegáveis de Explorer, Properties e Toolbox.
- Dock de ferramentas visuais e seleção demonstrativa de um objeto no Explorer.
- Ajustes imediatos de cor, arredondamento, espaçamento, tamanho dos alvos de toque e largura de painel em **Visual**.
- Exportação das preferências para `rbxstudio-design.json`, para orientar a futura implementação em GPUI/Rust.

## O que ainda não faz
- Não altera nenhuma função do aplicativo Android.
- Não executa Roblox, scripts, física, catálogo de assets, projeto real, publicação ou Play.
- Os painéis fora de Explorer, Properties e Toolbox ainda mostram o contêiner de navegação, sem seus controles internos.
- Os comandos de menu apresentam feedback de demonstração e não fazem alterações em arquivos.

## Referências do código
- Menus: submódulo `upstream/rbx-native`, arquivo `crates/rbx_studio/src/menu_bar.rs`, com alterações em `patches/rbx-native--android-port.patch`.
- Ferramentas: `crates/rbx_studio/src/shell/ribbon.rs`.
- Painéis: `crates/rbx_studio/src/shell/layout.rs`, com o painel adicional Toolbox no patch Android.
- Páginas iniciais: `crates/rbx_studio/src/launcher/home_window.rs`.
- Configurações: `crates/rbx_studio/src/shell/settings_window/nav.rs`.

## Fluxo de aprovação sugerido
1. Abrir a prévia no celular de preferência em paisagem.
2. Ajustar aparência em **Visual**.
3. Abrir **Menu**, **Ferramentas** e **Painéis**, conferindo espaçamento, scroll e alcance dos botões.
4. Exportar o JSON de design aprovado.
5. Só então implementar o padrão no GPUI real e conferir por captura do Android.

**Nota:** números e nomes de menus são derivados da base versionada, mas a inclusão completa dos controles específicos de cada painel deve ser auditada antes da migração real.
