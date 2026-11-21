# RBX Studio Android — Fase 15: eventos reais Equipped e Activated

**Branch experimental:** `work/ai-autonomous-read-v1`, sem alteração da `main`.

## Implementação incremental

O teste local de Play mantém o fluxo anterior: autorização explícita, checagem de `Edit`, início por `start_stop_play`, leitura de árvores `Client/Server`, amostragem do Output, tentativa de parar e confirmação de retorno a `Edit`.

### 1. Observar `Tool.Equipped`

A opção de equipagem, **desmarcada por padrão**, agora cria um listener temporário `found.Equipped:Connect` **antes** da única chamada fixa `Humanoid:EquipTool(found)`. O código espera no máximo um pequeno intervalo de observação e executa `Disconnect()`. O Client devolve um dos recibos exatos `EQUIPPED_EVENT` (a Tool foi equipada e o evento apareceu) ou `EQUIPPED_NO_EVENT` (mudou de pai, mas evento não observado). Ambos ainda exigem **leitura MCP independente** da árvore `Workspace.<Player>`; somente `EQUIPPED_EVENT` + read-back contam como verificação completa do cenário.

Nenhuma confirmação será inferida apenas de `Tool.Parent`, de sucesso de transporte RPC, ou de texto não reconhecido.

### 2. Testar `Tool.Activated` pela API (não por toque)

Foi adicionada outra caixa, também **desmarcada por padrão**:

**Avançado: testar Tool.Activated por API (NÃO é toque no Android)**

Selecioná-la exige selecionar também a opção anterior de equipagem. Antes de qualquer operação, o aplicativo exibe um modal descrevendo as possíveis consequências: `Tool:Activate()` pode disparar scripts já presentes no mapa. Use somente projetos de teste confiáveis e descartáveis.

Depois de confirmado `Equipped` e do read-back, uma segunda chamada fixa de `execute_luau` **Client** valida o `LocalPlayer`, encontra exatamente uma Tool com a classe/nome previstos no `Character`, exige `Tool.Enabled`, escuta temporariamente `found.Activated`, chama uma vez `found:Activate()`, faz `found:Deactivate()` como limpeza e desconecta o listener. A resposta exata deve ser `ACTIVATED_EVENT`. Outros resultados — até respostas que contêm esse texto junto com erros — são tratados como inconclusivos.

**Essa operação é uma ativação programática. Não é uma interação de tela, não usa `user_mouse_input` e não pode ser apresentada como teste de toque, `GuiButton.Activated` ou `MouseButton1Click`.** Também não valida efeitos visuais, física, nem a lógica particular dos scripts do jogo.

### 3. Veredito detalhado

O relatório local separa:

- Recibo da equipagem;
- Presença da Tool no Character por leitura MCP independente;
- Evento **Tool.Equipped** observado / não confirmado;
- Quando selecionado, evento **Tool.Activated via API** observado / não confirmado;
- Output e retorno ao `Edit`.

O cenário de ativação opcional é considerado positivo apenas quando os recibos e os eventos solicitados foram realmente observados e o Play retornou ao Edit. Ausência de qualquer evidência = **inconclusivo**, não sucesso simulado.

## Restrições e segurança

- Somente duas execuções Luau fixas e autorizadas podem ocorrer no cenário mais avançado, ambas no DataModel `Client`: equipar e ativar uma Tool identificada no Client. O provedor de IA não escolhe, edita ou executa o código.
- Nenhum fluxo muda código-fonte em `Edit`, publica experiências ou injeta eventos de toque no sistema Android.
- A Tool pode executar seus próprios scripts pré-existentes, inclusive de equipagem/ativação. Não testar em projeto de produção ou com modelos não confiáveis.
- A aplicação não pode desfazer scripts que já foram disparados no Play se o usuário cancelar ou o canal MCP falhar. Se o stop não for confirmado, confira e encerre a sessão manualmente.
- Falhas ou atrasos de callback, incompatibilidade do runtime Rook/Fiu no Android, ausência de Player/Tool e limites de tempo podem impedir recibos. O relatório declara a limitação.
- Os testes de desenvolvimento com respostas de MCP simuladas servem **apenas** para validar o código Java. Não comprovam que eventos Roblox dispararam num celular real.

## Testes do repositório

- `scripts/test_ai_play_equip_java.py`: compila os métodos Java reais de equipagem, verifica geração do listener e recibos `EQUIPPED_EVENT`/`EQUIPPED_NO_EVENT`/rejeitados, além do read-back.
- `scripts/test_ai_play_activation_java.py`: compila o builder e verificador Java da ativação, testa paths inválidos, presença dos listeners e recusa de recibos incompletos.
- `scripts/test_ai_play_smoke_java.py`: verifica o relatório, incluindo a exigência de evento observado quando o respectivo cenário foi solicitado.
- `scripts/check_ai_hub.py`: exige consentimento separado e verificações estáticas de modo Client, dois comandos limitados e ausência de publicação.
- `.github/workflows/ai-hub-android-java.yml`: executa os testes e compila a Activity com Android SDK 35.

O APK ARM64 continua sendo um build separado; verifique o artifact antes de qualquer afirmação de versão instalável. **A Fase 15 ainda não concluiu os testes de toques reais em GUI.**

## Resultado verificado do incremento

- **CI Java aprovada** para o código das opções `Equipped` e `Activated` no commit `a0824e02b0be`: https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/44029549232 . A suíte validou os recibos, rejeições, limites, leitura independente e compilou `StudioAiHub.java` junto com `StudioServices.java` com Android SDK 35.
- **ZIP completo de fontes** exportado com sucesso para esse mesmo commit, artifact `rbxstudio-ai-0611-complete-source`: https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/44029549210 .
- **Build APK ARM64 experimental** em workflow separado: https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/44029549176 . Na última checagem estava em andamento, sem artifact APK verificado.

Os testes Java executam os métodos reais de geração/validação, mas os recibos MCP são fornecidos por fixtures. Eles **não** demonstram que `Tool.Equipped` ou `Tool.Activated` já foram observados em uma sessão Play no Android. Sem executar o APK no aparelho e registrar a evidência do Client não se pode marcar a compatibilidade física como aprovada.

## Próximo obstáculo concreto

Uma verificação de **GuiButton.Activated por toque físico no Android** exige um canal de input Android associado à janela do Play e uma observação confiável do evento. O driver `user_mouse_input` nativo verificado utiliza backends desktop, portanto não será utilizado para alegar toques no celular. Essa integração precisa ser implementada/testada separadamente, com consentimento explícito e execução em Android real.
