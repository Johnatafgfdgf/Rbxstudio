# RBX Studio Android — Fase 14: diagnóstico local do Play e Output via MCP

**Branch:** `work/ai-autonomous-read-v1`. Continuação da Fase 13, mantendo a `main` intacta.

## Implementado, sem simulações

A Activity Android `StudioAiHub.java` inclui uma área **Diagnóstico Play local • leitura real MCP** com:
- autorização explícita e desligada por padrão: `Autorizo ler LOCALMENTE estado e Output nesta sessão`;
- ação `Verificar Play/Output do editor • somente leitura`;
- chamadas reais para **`get_studio_state`** e **`get_console_output`** pelo mesmo MCP local autenticado;
- resultado local mostrando o estado observado: `Play`, `Edit`, `Starting` ou inconclusivo, com critério estrito para afirmar `Play`;
- relatório com total de linhas de alerta/erro detectadas por *heurística*, até oito trechos destacados e até 1.800 caracteres do Output;
- cancelamento, revogação de autorização (interrompe conexão e limpa a tela), falha conservadora se alguma leitura não estiver disponível.

`get_studio_state` expõe as linhas `Current Studio Mode`, `Available DataModels` e `Focused DataModel in the viewport` no protocolo MCP nativo. Para declarar `Play`, o app exige exatamente modo `Play`, foco `Client` e DataModels `Client, Server` ou `Edit, Client, Server`. O relatório não é enviado ao provedor de IA e **não exige API Key**.

## O que esta etapa NÃO faz

- **Não inicia, controla ou encerra** o jogo. O usuário abre Play local pelos controles existentes, quando disponíveis.
- **Não declara testes de jogabilidade aprovados** por ausência de linhas contendo `error`. A classificação de logs é apenas por palavras e pode ter falsos negativos e positivos.
- Não fornece leitura de frames, imagens, capturas, controles de personagem, execução automática de cenários ou correções automáticas.
- Não presume que o Play que abre o aplicativo oficial do Roblox em outra atividade esteja disponível como `Client/Server` no MCP do editor; nesse caso a resposta fica `Edit`/inconclusiva.
- Não executa `start_stop_play`, `execute_luau`, `multi_edit` nem publica ou salva conteúdo.

## Testes e verificação

`scripts/test_ai_play_probe_java.py` compila e executa os **métodos reais de produção** `checkedLocalPlayMode` e `buildLocalPlayReport`, com casos de Play, Edit, Starting, modos incoerentes, saída vazia, contagem de erros/avisos e truncamento.

`scripts/check_ai_hub.py` exige autorização, leitura somente das duas ferramentas permitidas e ausência de caminhos de gravação. O workflow `.github/workflows/ai-hub-android-java.yml` foi ampliado para executar os novos testes e compilar a Activity no Android SDK 35.

**CI confirmada:** os contratos estáticos, os testes Java reais (incluindo Play/Output) e a compilação da Activity Android SDK 35 passaram no commit `954948cd331d`, workflow https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/44022773090 . **Pendente:** inspeção MCP/Output real no Android físico, testes do Play local Rook em aparelho, Play externo no app Roblox, interface e controles. A compilação Java não equivale a APK completo nem a Play funcional; nenhum APK desta fase é considerado entregue até confirmar o respectivo artefato.

## Continuação

Adicionar, quando o motor e permissões permitirem, cenários de teste locais verificáveis com início/fim autorizados, coleta de Output por cenário, seleção segura de DataModel, captura visual genuína e relatórios reprodutíveis. A integração não pode alterar o comportamento do Play atual nem atribuir ao agente operações não realizadas.
