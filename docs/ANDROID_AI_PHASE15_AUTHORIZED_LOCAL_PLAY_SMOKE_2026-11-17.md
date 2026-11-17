# RBX Studio Android — Fase 15: primeiro teste real controlado de Play local

**Branch:** `work/ai-autonomous-read-v1`. Implementação incremental sobre a central de IA da Fase 14; a `main` não foi alterada.

## Entrega neste incremento

A Activity Android `StudioAiHub.java` recebe **Teste rápido Play local • iniciar → ler Output → parar**, com:
- uma opção de autorização adicional e **desmarcada por padrão**, diferente da permissão de leitura da Fase 14;
- uma caixa de confirmação para iniciar e encerrar **somente** Play local por meio de `start_stop_play` com `is_start=true/false`; o próprio aplicativo emite os parâmetros, nunca o provedor de IA;
- pré-condição rigorosa: `get_studio_state` deve confirmar `Edit`, para **não tomar controle de Play já aberto**;
- comparação exata dos recibos MCP `Game Started` e `Game Stopped`, sem aceitar textos aproximados;
- depois do início, segunda leitura `get_studio_state` exige `Play` com DataModels `Client/Server`;
- consulta **duas árvores reais de execução** com `search_game_tree`: `Server.Workspace` e `Client.Players`, sempre com caminho e DataModel fixos no código, profundidade 1 e no máximo 10 nós por resultado. A resposta é analisada como JSON e exige `fullPath`, `className` e `name` exatos para cada raiz; falhas não são tratadas como sucesso;
- pausa limitada de observação e leitura do `get_console_output` do editor;
- no bloco `finally`, o agente **tenta encerrar o Play iniciado** e verifica retorno a `Edit`. Em caso de perda do recibo de início, só envia stop se o MCP indicar Play em andamento, para reduzir o risco de encerrar uma sessão não iniciada pelo teste;
- resultado independente dos relatórios anteriores: recibo de início, disponibilidade de Client/Server, confirmação das árvores Server.Workspace e Client.Players, Output obtido, retorno a Edit e limitações;
- cancelamento e revogação de autorização, com aviso para confirmar manualmente o encerramento se o canal MCP for interrompido.

A implementação amplia o timeout de **apenas** `start_stop_play(is_start=true)` para 210 segundos, compatível com os até 200 segundos de startup do job do motor nativo. Os demais endpoints mantêm seu timeout anterior.

## Cenário adicional opcional: spawn e estruturas do jogador

Agora o teste rápido também oferece a caixa **Cenário opcional: Player, Backpack, PlayerGui e Humanoid**, desmarcada por padrão e separada da autorização de iniciar Play. Ao marcar, o app faz apenas duas leituras adicionais pelo MCP **Client**, depois que Play já foi confirmado:

- `search_game_tree(datamodel_type=Client, path=Players, max_depth=2, head_limit=48)` para identificar um `Player`, seu `Backpack` e seu `PlayerGui` realmente presentes sob o mesmo caminho;
- `search_game_tree(datamodel_type=Client, path=Workspace, instance_type=Humanoid, max_depth=3, head_limit=24)` para verificar um `Humanoid` associado ao mesmo nome de Player na árvore `Workspace.NomeDoJogador.Humanoid`.

O avaliador exige classe e caminho exatos, não junta objetos de jogadores diferentes, não envia nomes à IA e não aceita uma resposta MCP truncada como confirmação. O relatório discrimina **Player, Backpack, PlayerGui e Humanoid**, e só considera o cenário confirmado quando os quatro correspondem ao **mesmo Player**. Falha, personagem personalizado com hierarquia diferente, logs de outro modo ou ausência de um objeto ficam como **inconclusivos**, sem impedir a tentativa de encerrar o Play.

**Limite importante:** as verificações são estruturais. Elas não provam que GUI respondeu ao toque, que a Tool equipa, que Humanoid se move, nem que o mapa renderiza. Isso requer cenários instrumentados adicionais e validação física em aparelho.

### Regressão adicional

- `scripts/test_ai_play_scene_java.py` compila o **avaliador Java de produção** e testa correspondências corretas, classes/tipos incorretos, caminhos divergentes, múltiplos Players e casos em que os objetos pertencem a pessoas diferentes;
- `scripts/test_ai_play_smoke_java.py` valida que o cenário opcional precisa estar completo para o relatório marcar essa verificação como concluída;
- `scripts/check_ai_hub.py` exige autorização desligada por padrão, caminhos Client fixos, dois limites de leitura e ausência de execução arbitrária de scripts neste fluxo.

## Segurança e limites técnicos

- A única operação com efeito é iniciar/parar o **Play local**. Não executa Luau arbitrário, não publica jogos, não importa assets, não altera scripts nem move o personagem.
- O teste **não altera o fluxo Play do editor** que publica/abre um jogo no app Roblox externo.
- `Game Started` pode não ocorrer no Android se o motor Rook/MCP local não conseguir iniciar. A interface informa quando não foi possível confirmar a execução; **não simula sucesso**.
- Durante startup, o usuário pode cancelar e interromper o socket antes de receber o recibo. Nesse caso é possível que o Play tenha iniciado mesmo sem confirmação; a limpeza tenta consultar o estado real, mas, se MCP estiver indisponível, pode exigir parada manual. Um resultado sem retorno confirmado a Edit **não é sucesso**.
- O Output e as árvores do Client/Server são observados em um único momento e pode conter mensagens anteriores. Sem erros visíveis não prova scripts, Tool, GUI, física, rede nem animações corretos.
- O fluxo não salva o projeto, não recupera estado da cena e não garante Undo. O próprio usuário deve testar em projeto de teste/backup.
- Esse é **o primeiro smoke test de Play** da Fase 15, não a implementação integral de testes de gameplay automatizados.

## Verificação automatizada

- `scripts/test_ai_play_smoke_java.py` compila os helpers Java reais de recibos e interpretação de estados, exercitando recibos incorretos, relatórios incompletos e limites de Output;
- `scripts/check_ai_hub.py` valida consentimento, confirmação, pré-condição Edit, exatamente um início e um encerramento possíveis, leitura de estado/Output e ausência de comandos Luau ou publicação nesse fluxo;
- `.github/workflows/ai-hub-android-java.yml` passa a executar a nova suíte e compilar a Activity Android com SDK 35.

CI e APK devem ser verificados para o commit exato da fase. Mesmo CI aprovado **não comprova** execução em Android físico nem compatibilidade Play real no aparelho.

## Próximos testes físicos

1. Abrir um mapa descartável e ativar a integração MCP local;
2. conferir que Play já aberto impede o teste;
3. testar início com mapas simples e mapas com erro Luau real, comparando o Output;
4. verificar o retorno a Edit e cancelamento durante startup;
5. validar que a experiência privada aberta no app oficial Roblox não é afetada pelo controle local.
