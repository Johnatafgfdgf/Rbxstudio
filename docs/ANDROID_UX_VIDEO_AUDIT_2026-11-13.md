# RBX-Mobile: auditoria do vídeo de UX (13/11/2026)

**Fonte:** gravação fornecida pelo usuário, 1920 × 864, cerca de 4min41s.  
**Código investigado:** branch `work/rbx-mobile-0.6.9`. A versão exata do APK que aparece no vídeo não foi confirmada.  
**Critério de evidência:** `V` = visível na gravação; `C` = localizado no código; `T` = depende de teste Android real.

## Inventário com tempos e evidências

| Momento | Constatação | Evidência | Impacto / decisão |
|---|---|---|---|
| 00:00–00:11 | Toolbox aberta junto ao viewport; imagens de ativos e botões de inserção sobre miniaturas. | V | Separar área tocável de inserir da prévia, validar hitboxes e confirmação de inserção. |
| 00:12–00:35 | Alternância para Explorer e entrada na busca; teclado ocupa grande parte da altura e **barra de status cobre comandos da barra superior** perto de 00:24. | V+C | Prioridade crítica: manter IME redimensionável sem exibir status por cima da UI. |
| 00:36–00:47 | Properties com linhas extensas e valor de BrickColor abreviado. | V | Testar edição de campos longos, seletores e foco com IME. Abreviação existe; não prova impossibilidade de editar. |
| 00:48–00:59 | Toolbox/Criações: mensagem de login e área vazia, sem CTA evidente no corpo do painel. | V | Oferecer caminho contextual claro para entrar na conta, sem simular catálogo. |
| 01:00–01:20 | Ferramentas revela segunda faixa de manipulação que repete funções já presentes na barra superior; fundo do viewport fica escurecido. | V+C | Manter um só atalho primário e, se houver ribbon, reservar comandos contextuais avançados; preservar fechamento por toque fora. |
| 01:24–01:42 | Terrain Editor é alto/denso, com opções Create/Edit, pincel, sliders e mais conteúdo além da altura. | V | Usar modo de tarefa dedicado com controles prioritários fixos, rolando configurações; testar scroll e retorno para cena. |
| 01:48–02:06 | Game Settings usa lista lateral e áreas com bastante texto; páginas mudam de assunto e idioma. | V | Compactar navegação, manter salvar/cancelar sempre visíveis e verificar formulários em 360–432 dp de altura. |
| 02:12–02:18 | Seletor de jogo ao tentar vincular/publicar. | V | Validar escolha, cancelamento, autenticação, salvamento e publicação com destino de teste; vídeo não prova sucesso remoto. |
| 02:24–02:36 | Faixa de ferramentas horizontal exige deslocamento para funções como Output/Viewport/Settings. | V | Considerar agrupamento por tarefa e indicador de rolagem; manter ações centrais permanentemente acessíveis. |
| 02:42–03:00 | Viewport e Input Action expõem toggles, estatísticas e estado vazio. | V | Melhorar estados vazios e rótulos, não afirmar que falta implementação. |
| 03:06–03:12 | Security Audit mostra estado vazio sem ação visível para executar auditoria. | V | Distinguir 'nenhum problema' de 'análise ainda não executada'. |
| 03:24–03:30 | Painel Loren usa texto e caminhos de navegação voltados a desktop em inglês. | V | Adaptar orientação ao fluxo Android e padronizar idioma. |
| 03:36–03:48 | Menus File/Edit/View abrem submenus densos com comandos pequenos. | V | Menus de toque com linha mínima validada de 48dp, scroll e área segura; verificar se comando cabe e executa. |
| 04:00–04:30 | Seleção, gizmos 3D e valores numéricos de manipulação aparecem, com mudanças de ferramenta/seleção. | V | Os gizmos estão presentes e há reação visual, mas não é possível comprovar todos os caminhos de mover/rotacionar/escalar/cancelar. |
| 04:30–04:41 | Câmera enquadra a cena de outra posição até focar em área de céu/terreno. | V | Verificar separação de gestos da câmera, seleção e panorâmica sem concluir por vídeo que houve bug. |

## Mapeamento técnico confirmado no repositório

- `patches/rbx-native--android-port.patch`, arquivo adicionado `crates/rbx_studio/src/shell/mobile.rs`: `mobile_action_bar`, `mobile_workspace`, `mobile_drawer`; barra mantém Salvar e Play separados do scroll principal e gaveta ocupa ~42% da largura quando >=640 unidades lógicas.
- `crates/rbx_studio/src/shell/workspace.rs` (no mesmo patch): `explorer_touch_actions` e painéis.
- `crates/rbx_studio/src/shell/toolbox.rs` (no patch): busca, categorias, revisão de scripts e importação.
- `GpuiImeView.java` + `StudioWindow.java` + `GpuiActivity.java`: transição fullscreen/IME, barras de sistema e foco.
- `scripts/android_ui_audit.py`: captura, execução ADB e verificação de logs; exige APK executável em dispositivo.

## Organização proposta por prioridade

1. **Persistente no topo:** Menu, Ferramentas e Painéis à esquerda; ferramenta 3D ativa próxima; Salvar e Play fixos à direita. A barra deve preservar a cena, sem duplicar edição e sem esconder Salvar/Play num scroll.
2. **Sob demanda:** Ribbon contextual com inserção, script, terreno e categorias específicas; retirar comandos primários repetidos que não agregam contexto, após testes de vínculos e atalhos.
3. **Gaveta lateral única:** Explorer, Properties e Toolbox por abas. Manter fechar/expandir acessíveis, busca visível e barra contextual da seleção com scroll suave. Painel expandido quando propriedades complexas exigirem espaço.
4. **Documento de código:** modo de foco, IME e retorno à Cena sempre claros. A abertura da IME nunca pode colocar status bar sobre o topo.
5. **Modo 3D:** seleção, mover, girar, escalar, snap, eixos e câmera devem usar uma única fonte de estado. Soltar, cancelar, trocar ferramenta/painel ou perder foco encerra o gesto sem deixar eixo preso.

**Não alterar arbitrariamente:** percentuais da gaveta, sensibilidade, limiares de gizmo, motor, publicação, estrutura dos painéis e fluxo de salvamento sem medição/testes e comparação com os casos de regressão.

## Alteração efetivamente aplicada nesta branch

- `android/src/com/rbxnative/androidport/StudioWindow.java`: `configureWhileTyping` remove `FLAG_FULLSCREEN` (não bloquear `adjustResize`), mas oculta somente a status bar durante a digitação.
- `android/src/dev/gpui/mobile/GpuiImeView.java`: usa o novo helper ao abrir o teclado.
- `android/src/dev/gpui/mobile/GpuiActivity.java`: reaplica a configuração correta quando a Activity retoma o foco, distinguindo IME ativa do restante da sessão.

A mudança é intencionalmente restrita às barras de sistema/IME; **não altera** salvamento, publicação, renderização 3D, geometria do gizmo ou tratamento de modelos.

## Matriz de verificação obrigatória

- [ ] No POCO X6 5G ou Android ARM64 equivalente, abrir Explorer → busca → IME: status não deve encobrir Menu/Play/Salvar.
- [ ] Teclado aberto: editar busca, Property Name e script; campo ativo visível, sem perda de foco.
- [ ] Fechar IME, retornar app após multitarefa e abrir outra vez: tela cheia e foco corretos.
- [ ] Alternar menus, gaveta, ferramenta 3D e câmera com um e dois dedos sem captura cruzada.
- [ ] Exercitar objetos com pivôs, escala, rotação e cancelamento; comparar transform e histórico.
- [ ] Executar testes de salvamento/reabertura e publicação em lugar privado dedicado.
- [ ] Executar UI Automator / Maestro com screenshots em 960×432, 904×407 e POCO real.
- [ ] Medir fps, travamentos, tempo dos toques e renderização com painel aberto/fechado.

## Resultado verificável até aqui

- Arquivos Java editados e commits publicados em branch isolada: **sim**.
- Inspeção do vídeo e mapeamento de caminhos de código: **sim**.
- GitHub Actions de compilação Java e APK ARM64: workflow criado; execução inicial registrada como **falha antes de disponibilizar passos/logs**; motivo ainda não demonstrado.
- APK novo desta branch: **não verificado / não disponibilizado**.
- Emulador físico, Play, catálogo e testes de toque end-to-end: **não realizados nesta auditoria**.

Não declarar gestos, menus, rendimento, publicação ou APK como aprovados sem evidência executável.
