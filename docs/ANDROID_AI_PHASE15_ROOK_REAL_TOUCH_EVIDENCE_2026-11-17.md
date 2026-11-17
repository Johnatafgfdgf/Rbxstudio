# Fase 15 — Segurança do Play MCP e evidência real de touchscreen no Rook Android

**Escopo:** alterações incrementais na branch `work/ai-autonomous-read-v1`. A `main` não foi alterada.

## Problema encontrado na auditoria da execução

A integração Android atual do motor possui **dois caminhos distintos de Play**:

- A rotina `Shell::play_start` direcionada para Android chama `play_start_external`: valida um destino Roblox privado, publica via Open Cloud mediante aprovação e abre o aplicativo oficial Roblox.
- A rotina `play_start_android_local` é **separada** e invoca o host Android do Rook, que abre a `RookPlayerActivity` (NativeActivity) em processo separado.
- O MCP nativo `start_stop_play` usa o primeiro caminho, via `mcp_play_start` → `Shell::play_start`. Não há evidência de que o MCP disponha de um DOM `Client/Server` do player Rook Android, separado, pois `Session.client`/`Session.server` são disponibilizados pela ligação interna da sessão de Play original, não pela `StudioPlayer.start` do Android.

**Risco corrigido:** as rotinas antigas do diagnóstico automático da IA não podem mais enviar `start_stop_play` pelo caminho externo. `StudioAiHub.requireVerifiedAndroidMcpPlay()` bloqueia tanto a janela de confirmação como a execução sem uma ponte local comprovada. `StudioTouchDiagnostics.hasVerifiedLocalMcpPlayBridge()` retorna `false` nesta versão e só deve ser habilitada após implementação e validação de uma ponte Rook Client/Server real. O botão e as opções antigas foram preservados, mas **não executam publicação inadvertida**. O relatório explica essa limitação claramente.

**Isso não desativa os fluxos existentes do editor** para Play local Rook ou Play externo oficialmente autorizado; protege apenas as tentativas automatizadas da IA de usar o endpoint MCP não comprovado.

## Novo diagnóstico de toques Android do Rook

Foi acrescentado o arquivo Java `StudioTouchDiagnostics.java` e uma instrumentação passiva em `RookPlayerActivity.java`, sem adicionar uma camada de UI sobre o jogo.

No painel da IA, a seção **Play Rook local • diagnóstico de toques Android** possui:

- uma autorização nova, desmarcada por padrão;
- **Preparar diagnóstico real de toque • próximo Play local**;
- **Ler resultado de toques do player Rook**;
- **Apagar autorização e dados locais de toque**.

Com consentimento explícito, uma solicitação de uso único, com validade máxima de **10 minutos**, é gravada no armazenamento privado do aplicativo. Quando o próximo player Rook abre, consome a solicitação e gera um relatório privado. Na `RookPlayerActivity`:

- a contagem só inicia depois que o serviço nativo comunica `ready`;
- o `dispatchTouchEvent` observa somente `MotionEvent.ACTION_DOWN`, `ACTION_UP` e `ACTION_CANCEL` cuja fonte seja `SOURCE_TOUCHSCREEN`;
- **sempre chama `super.dispatchTouchEvent(event)` e devolve o resultado inalterado**: não captura, não redireciona e não simula toques;
- grava apenas contagens agregadas limitadas e os estados `ready`/`stopped`. Não grava coordenadas, ponteiros, identidade do jogador, texto digitado, imagens, mapa, scripts, credenciais ou gravações de tela. Não envia os dados para a IA;
- produz relatório legível localmente no painel da IA quando o usuário retorna ao editor.

### O que pode e não pode ser concluído

**Evidência possível:** se o player informou `ready` e o Java realmente recebeu `ACTION_DOWN` e `ACTION_UP`, o relatório pode confirmar que eventos de touchscreen passaram por **essa rota Java da Activity** durante a sessão Play.

**Não comprovado:** que a fila nativa do Rook consumiu tais eventos, que o jogador se moveu, que um `GuiButton.Activated`/`MouseButton1Click` foi disparado, que `Tool.Activated` ocorreu, ou que um script/efeito visual funcionou.

**Limite decisivo:** `NativeActivity` pode entregar entradas à `AInputQueue` sem passar por `Activity.dispatchTouchEvent`. Assim, zero eventos Java é **inconclusivo**, não reprovação do toque Android. A próxima fase deve instrumentar a fila nativa e associá-la ao evento Luau do mesmo botão para validar clicabilidade real.

## Verificação de código

- `scripts/test_ai_android_touch_java.py` compila os métodos reais e puros de verificação de contagens e de formatação do relatório. Testa evidência, ausência de eventos, sessão não pronta, dados inconsistentes e ausência de afirmações falsas de clique.
- O teste também confere o código do Rook/Hub, autorização inicial desmarcada, repasse dos MotionEvents sem modificação, zero coleta de coordenadas e bloqueio do caminho externo MCP.
- `.github/workflows/ai-hub-android-java.yml` executa a nova verificação e compila `StudioAiHub.java`, `RookPlayerActivity.java`, `StudioTouchDiagnostics.java` e dependências com o SDK Android 35.
- O APK ARM64 é compilado em workflow distinto. Um teste Java verde **não prova** execução no aparelho ou compatibilidade do driver de toque.

## Guia para verificação no celular

1. Instalar uma versão experimental **apenas depois do build APK concluir com artifact confirmado**.
2. Na central de IA, marcar o consentimento para entrada touchscreen e tocar em **Preparar diagnóstico**. A solicitação expira em 10 minutos.
3. Voltar ao editor e abrir **Play Rook local**, não o Play externo por Open Cloud. Esperar carregar.
4. Tocar naturalmente na tela do jogo; sair do Play e voltar à central de IA.
5. Marcar novamente a permissão de leitura, se a interface for recriada, e abrir **Ler resultado**.
6. Conferir se o relatório identifica `ready`, `ACTION_DOWN`/`ACTION_UP` e encerramento. Caso não identifique, o problema pode estar na entrega à fila nativa; não inventar um clique de GUI.
7. Tocar em **Apagar autorização e dados locais de toque** ao terminar.

## Próximos incrementos

Instrumentar `AInputQueue`/winit no Rook e adicionar um contrato explícito com Luau para **observar `GuiButton.Activated` de um botão autorizado pelo usuário após um toque físico**. Só então será possível afirmar que uma interface recebeu e processou um toque em Android real.


## Continuação incremental — Instrumentação da fila nativa via winit

No host Rook Android, a rota `NativeActivity → AInputQueue → winit.WindowEvent::Touch`
é observada de maneira passiva, após a primeira apresentação de frame e somente
na sessão com consentimento de uso único. Os contadores `Started`, `Ended`
e `Cancelled` são enviados pelo bridge JNI local à Activity e armazenados
separadamente dos contadores `dispatchTouchEvent` Java. O evento original
continua encaminhado sem alteração ao `Launch.window_event` do Rook.

O diagnóstico não captura coordenadas, identificadores de ponteiro, imagens,
textos nem conteúdo do projeto. A gravação mantém evidências das duas rotas
quando elas coexistem e rejeita totais inconsistentes e identificadores de
sessões anteriores. A opção de diagnóstico continua desativada por padrão.

**Status e limites:** é uma implementação de observabilidade por código.
`winit.Touch` indica que o loop nativo recebeu o evento; não garante que
o motor tenha executado um callback Luau ou que `GuiButton.Activated` tenha
sido disparado. Não há reprodução/injeção de toques, nem confirmação de
funcionamento em aparelho físico. A correlação com um botão e um evento
Luau real permanece pendente.

O teste `scripts/test_ai_android_touch_java.py` verifica o formatter Java de
produção e os contratos de autorização e encaminhamento da rota Rust. A
compilação ARM64, instalação e teste manual do Play devem ser validados no
commit específico; CI Java verde isoladamente não é prova de runtime.

## Continuação — sinal GuiButton.Activated por toque nativo

O Rook original já possui `DataModel::gui_touch`; o patch Android preserva
seu tratamento de toque. A instrumentação agora observa **somente** o ponto
em que o próprio Rook emite `self.fire(button, "Activated", ...)` após um
toque real concluído em botão, e nunca o caminho de mouse ou execução arbitrária.

A nova checkbox **Autorizo registrar até 200 sinais de GUI e o caminho do
último botão** é adicional, desmarcada por padrão, e exige confirmação no
mesmo consentimento de sessão única. O Java verifica o `id` da sessão,
`guiConsent`, `ready`, `stopped` e limites. Armazena apenas contagem e
caminho do último botão no armazenamento privado local; apagá-los revoga o
consentimento. Não armazena coordenadas, ponteiros ou textos digitados,
nem envia conteúdo para API de IA. A observação é removida ao terminar Play.

**Precisão do resultado:** essa observação é o sinal `GuiButton.Activated`
emitido na ponte Rook→Luau após o toque, não a comprovação de que um
`LocalScript` específico tratou o evento ou produziu resultado visual.
Não há alteração no projeto, scripts novos, Play MCP externo ou
publicação automática. A evidência depende de um Play físico e uma GUI
real existente; testes estáticos/Java não garantem compatibilidade física.

A regressão estende `scripts/test_ai_android_touch_java.py` e
`ai-hub-android-java.yml`. O build ARM64 e o teste em aparelho físico
devem ser checados separadamente para o commit exato.

## Continuação: encaminhamento de callbacks do evento no Luau

Em `rook-lua`, foi acrescentada captura opcional **apenas ao redor**
da emissão real `GuiButton.Activated` por toque. Registra quantidade de
handlers conectados encaminhados pelo despachante, tentativas de chamar ou
retomar handlers durante o despacho, e erros de execução reportados nessa
janela. A captura funciona com `SignalBehavior` Immediate e Deferred;
identifica conexões por ID **somente em memória no fio executor** e não
persiste IDs, corpos de scripts, coordenadas nem outras entradas.

O relatório Android distingue agora: toque → sinal no Rook → handlers
encaminhados → tentativas de execução. **Tentativa não é conclusão de
callback**; um handler pode falhar, suspender ou não produzir efeito no
jogo. Sem observação de efeito/estado final, o diagnóstico não marca
o sistema como funcional. A regra de consentimento separado e o limite
de 200 ativações da sessão permanecem.

O teste de contrato Java usa os métodos de produção; o build Android
nativo precisa validar a nova ligação de `rook-lua`, e testes físicos
continuam obrigatórios.

## Continuação — término síncrono real de handlers

O Rook agora verifica a `mlua::Thread` **após a primeira retomada
síncrona** em `threads.rs`. Só considera "concluído sem erro" o handler
que termina com `ThreadStatus::Unresumable` sem novo erro registrado
naquele intervalo. Erros e resultados ainda não concluídos são
separados; interrupções e `task.wait` **não** contam como sucesso.

Handlers `SignalBehavior::Deferred` que rodam após sair da função
`GuiButton.Activated` ficam como **não acompanhados**, pois o relatório
é uma fotografia do instante do toque. Não se afirma que uma corrotina
suspensa terminou posteriormente ou que o efeito visual ocorreu.

Os contadores adicionais obedecem ao consentimento de GUI existente,
ao limite de 200 sinais, às validações Java (contadores não negativos,
soma de estados ≤ número de tentativas) e ao armazenamento privado
local. Não há publicação automática nem injeção de eventos.

## Ajuste de compatibilidade mlua

A versão real de `mlua` usada pelo Rook define o estado terminal da corrotina
como `ThreadStatus::Finished`, e não `Unresumable`. A instrumentação
só contabiliza `Finished` **sem novo erro**; `Resumable` continua sem
conclusão comprovada e `Error` não é contabilizado como sucesso.
 
## Continuação — alterações observadas no DOM durante Activated

Sem criar scripts artificiais nem injetar toques, o host Android passa a
consultar o contador absoluto do diário de mutações (`Dom::journal().end()`)
**imediatamente antes e depois** de emitir o `GuiButton.Activated` real.
Se o diário estiver habilitado, transmite a diferença numérica, limitada a
1.000 entradas por sinal, junto com os resultados do despachante. O Java
guarda, apenas na sessão opt-in atual e até 200 sinais:

- `journalChanges`: total limitado de registros de alterações do DOM.
- `guiWithJournalChanges`: quantidade de sinais com pelo menos um registro.

Nenhum objeto, propriedade, valor, script, coordenada ou screenshot é
persistido pelo diagnóstico. O recurso reutiliza consentimento específico
de GUI, token de uso único e armazenamento privado do Android.
Uma mudança registrada na mesma janela **não atribui causalidade a um
script específico**, não garante que a mudança persistiu e **não prova**
resultado visual. Zero alterações também não prova ausência de efeitos
(se o diário estava desativado, por exemplo). Para confirmar efeitos de
jogo é necessário observar o estado esperado em testes reais no aparelho.
