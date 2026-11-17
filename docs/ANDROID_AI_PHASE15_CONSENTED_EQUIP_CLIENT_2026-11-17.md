# RBX Studio Android — Fase 15: equipagem controlada em Play local

**Branch:** `work/ai-autonomous-read-v1`. Nenhuma alteração na branch `main`.

## Implementado no código Android

O teste rápido de Play local recebe mais um checkbox, **desativado por padrão**:

**Avançado: testar EQUIPAR Tool durante Play (executa scripts existentes)**

Quando o usuário marca a opção, uma janela específica informa que haverá **uma execução fixa de Luau no Client**, podendo disparar os scripts já existentes da Tool (`Equipped`, animações etc). A execução só ocorre depois da confirmação da janela, nunca por comando livre de um modelo de IA.

O fluxo continua exigindo autorização para iniciar/encerrar Play e confirmação de `Edit` antes do início, evitando controlar Play previamente aberto. Após o estado `Play`, ele identifica `Client`, `Server`, `Players`, um Player do Client com `Backpack` e `Humanoid`, e uma Tool identificada pelo caminho real `Players.<Player>.Backpack.<Tool>`.

O aplicativo prepara um **template Luau imutável**, preenchido apenas por nomes que passaram na allowlist de caracteres:
- Exige `Players.LocalPlayer` igual ao Player identificado no MCP; não equipa Tool de outro jogador.
- Verifica o `Backpack`, `Character` e `Humanoid`.
- Rejeita objetos duplicados com mesmo nome, Tool ausente ou `Disabled`, e `Handle` inválido quando a Tool exige Handle.
- Executa **somente** `humanoid:EquipTool(found)`, aguarda brevemente e devolve um recibo exato `EQUIPPED` caso a Tool seja movida para `Character`.
- Após o recibo, faz outra chamada independente `search_game_tree` com `datamodel_type=Client`, em `Workspace.<Player>`, `max_depth=1`, `head_limit=48`. Confirma exatamente um objeto classe `Tool` com nome e caminho esperados sob esse Character.
- O relatório marca **recibo** e **read-back** separadamente. Só considera a verificação extra positiva quando ambos são confirmados, e o `Play` ainda precisa voltar a `Edit` para completar o teste.
- O fluxo anterior de leitura de `PlayerGui`, `Tool` e `Output` continua. Se o usuário escolheu apenas equipagem, **não é exigido** que exista um botão de GUI, porque nem toda Tool possui interface.

## Segurança e escopo

- **Efeito real em runtime**, **não somente análise estrutural**: o método `Humanoid:EquipTool` pode disparar scripts já existentes dentro do próprio jogo. É destinado apenas a **mapas de teste confiáveis**.
- A operação ocorre na sessão Play local; não altera os arquivos do projeto Edit, não faz `multi_edit`, não publica o jogo, não injeta toques OS e não envia dados para nenhuma IA remota.
- Requer opt-in específico, distinto da permissão genérica de ler árvore/Output, com segunda confirmação modal. Cancelamento e revogação são checados antes do pedido, mas não conseguem desfazer um comando já entregue ao runtime.
- **Não confundir com teste de clique em GUI**: este fluxo NÃO clica na tela nem em `GuiButton`; o backend atual de `user_mouse_input` usa XTest/SendInput no Windows/Linux e não foi validado no Android.
- Se a Tool tiver uma implementação de equipagem diferente do padrão, precisar de servidor específico, não estiver no Backpack, ou se o Client MCP não suportar o método, será registrado como **inconclusivo**.
- A confirmação da equipagem no Client **não prova** que o servidor replicou a ação, que `Tool.Activated` funciona ou que a animação tocou.
- Se a comunicação com MCP falhar durante o início ou parada, a sessão Play pode continuar ativa e o usuário deve encerrá-la manualmente.

## Testes de código

- `scripts/test_ai_play_equip_java.py` compila e executa os métodos Java reais que geram o template Luau, interpretam o recibo e validam o read-back. Casos negativos incluem path de outro jogador, caracteres indevidos, Tool de classe errada, duplicação de Tool, recibos não exatos e árvore incompleta.
- `scripts/test_ai_play_smoke_java.py` verifica os novos campos no relatório e que falhas de recibo, leitura ou encerramento nunca são marcadas como sucesso.
- `scripts/check_ai_hub.py` valida as invariantes de opt-in, Client e execução fixa.
- `.github/workflows/ai-hub-android-java.yml` valida a lógica e compila a `StudioAiHub.java` contra o SDK Android 35.

A CI **não realiza Play real no Android físico**. Essa verificação precisa de um APK instalável e de um mapa de teste confiável. Não afirmar que `Equipped` foi observado em dispositivo até haver essa evidência.

## Próxima etapa

Para **cliques reais**, será necessário um canal de injeção de eventos compatível com Android ou um teste manual instrumentado que observe o evento `Activated` e o resultado sem confundir chamadas programáticas com toques na tela. Até isso acontecer, deixar clique automático desativado, e não simular sucesso.
