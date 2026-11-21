# RBX Studio Android — Fase 15 (incremento): Tools e GUIs no Play local

Branch de trabalho: `work/ai-autonomous-read-v1`. A `main` permanece inalterada.

## O que foi acrescentado

Na central de IA, junto ao **Teste rápido Play local**, há a opção adicional desmarcada por padrão:

**Cenário opcional: estados reais de Tool e GUI.**

Ao autorizar o teste rápido de Play local e marcar essa opção, o app:

1. Inicia **somente** o Play local pelo MCP após confirmar estado `Edit` e autorização específica. O fluxo antigo de Play que publica/abre o Roblox não é substituído.
2. Identifica um jogador no Client e confirma a presença de `Backpack`, `PlayerGui` e `Humanoid` pela análise já existente. Todos precisam pertencer ao mesmo Player para o cenário de spawn estar completo.
3. Consulta via `search_game_tree` **Client** (nunca Edit ou Server) duas subárvores com limites rígidos:
   - `Players.<jogador>.Backpack`: `max_depth=2`, `head_limit=48`.
   - `Players.<jogador>.PlayerGui`: `max_depth=4`, `head_limit=64`.
4. Escolhe uma **Tool** que seja filha direta do Backpack e um **TextButton** ou **ImageButton** realmente aninhado sob um `ScreenGui` desse PlayerGui. Não depende do nome do item e ignora outros Players. Se o primeiro ScreenGui for decorativo ou não possuir botão, verifica os próximos.
5. Usa `inspect_instance` somente sobre os caminhos exatos previamente encontrados, verificando novamente classe, caminho, presença de propriedades e ausência de múltiplas correspondências:
   - **Tool.Enabled**, **Tool.RequiresHandle**, existência de **Handle** como filho direto com classe de peça compatível.
   - **ScreenGui.Enabled**, **GuiButton.Visible** e **GuiButton.Active**.
6. Mostra os caminhos, valores reais `sim`, `não` ou `sem confirmação` e a avaliação conservadora **configuração pronta** / **não confirmada** no relatório local. Depois consulta Output, solicita fim do Play e verifica retorno a Edit.

O app só considera a verificação adicional completa quando uma Tool e um botão de GUI foram encontrados na amostra e os valores observáveis indicam configuração compatível. Uma propriedade ausente, uma ferramenta sem Handle obrigatório, uma árvore truncada, um botão desativado ou falta de correspondência **não** são convertidos em sucesso.

## Limites e segurança

- Isto verifica **configuração e presença no Client durante Play**, **não** equipagem, evento `Equipped`, `Activated`, clique real, funcionamento de scripts, física, renderização, permissões de rede ou resultado visual.
- Nenhum controle é clicado ou acionado. Nenhum Luau é enviado nem executado; nenhum modelo, script ou asset é criado ou alterado. A Tool e o botão são apenas observados.
- Uma Tool equipada e ausente do Backpack pode não ser amostrada. A interface não é obrigada a conter ScreenGui/GuiButton em todo jogo. Jogos com nomenclatura ou hierarquia fora do formato suportado podem produzir diagnóstico inconclusivo sem indicar defeito.
- O cenário usa um jogador candidato do MCP, mas não comprova que ele é o jogador local quando há mais de um jogador.
- O relatório permanece somente no dispositivo. Não é enviado à API de IA.
- O encerramento de Play continua uma tentativa verificada pelo MCP. Se o canal falhar, o usuário precisa confirmar manualmente que Play foi interrompido.

## Cobertura automatizada

- `scripts/test_ai_tool_gui_java.py` compila e executa os **métodos Java reais de produção** que selecionam Tool/GuiButton, checam o Handle e classificam booleanos, incluindo casos adversos de nomes, classes, jogadores, pais e propriedades inválidos.
- `scripts/test_ai_play_smoke_java.py` exercita os estados de relatório quando o cenário está pronto, incompleto ou não autorizado.
- `scripts/check_ai_hub.py` garante que as consultas sejam limitadas a Client, caminhos conhecidos e consentimento explícito, sem nenhum comando de interação, código Luau ou escrita.
- `.github/workflows/ai-hub-android-java.yml` executa os testes e compila a Activity no SDK 35.

**Ainda não houve verificação em aparelho físico.** Um resultado positivo de CI prova apenas as verificações de código e compilação, não as interações reais num jogo Roblox no celular.

## Verificação da revisão

A revisão de código com nomes de Tool e GUI contendo espaços e hífens foi validada pela CI em **`b141846f730b`**: https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/44026900300 . Todos os testes de regressão Java da central de IA, incluindo os testes do cenário de Player e de Tool/GuiButton, e a compilação Java da Activity usando Android SDK 35 foram concluídos com sucesso.

A exportação de código-fonte completa da mesma revisão de produção (código da Activity até `98820b181ab8`, antes da alteração isolada do teste adicional) está em https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/44026878871 . A revisão final `b141846f730b` acrescenta apenas casos de teste para nomes com espaços.

**Um build Java verde NÃO comprova que o APK ARM64, o motor MCP Play ou os eventos de input Android funcionam no aparelho.** Esses testes práticos continuam pendentes.

## Próximos incrementos da Fase 15

Implementar testes interativos reais apenas após identificar e validar uma via de input compatível com Android, com ação do usuário e confirmação observável de evento/efeito. Não assumir que o backend de mouse/teclado desktop (XTest/SendInput) funciona no Android.
