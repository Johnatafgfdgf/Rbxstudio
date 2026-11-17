# RBX Studio Android — Fase 10: controle seguro de Lighting via agente de IA

**Branch de desenvolvimento:** `work/ai-autonomous-read-v1` — alterações somente experimentais, sem substituir a interface existente nem alterar `main`.

## Novo recurso real

A central de IA consegue **propor** alterar a iluminação global de um jogo Roblox usando uma ação tipada `propose_lighting_update`. O fluxo reutiliza o MCP local e a arquitetura de consentimento já utilizada pelo agente para Parts, GUIs e scripts.

**Fluxo da ação:**

1. O usuário habilita o consentimento separado **Permitir propostas para alterar horário e brilho de Lighting**, desligado por padrão, e autoriza o contexto do Explorer. O agente precisa estar ligado e a API do provedor conectada.
2. O agente precisa primeiro executar uma leitura MCP real `inspect_instance` no caminho literal `Lighting`. O app rejeita resposta ambígua, outra classe, outro caminho e propriedades ilegíveis.
3. O modelo só pode propor um JSON com pelo menos um campo numérico opcional: `clock_time` no intervalo fechado **0..24**, `brightness` no intervalo fechado **0..10**, e um `reason` textual curto. Campos arbitrários não são traduzidos em operações de escrita.
4. O app cria um objeto tipado `LightingProposal` com os valores anteriores lidos de `properties.ClockTime` e `properties.Brightness`, apresenta uma prévia antes/depois e solicita **aprovação específica**.
5. Após a confirmação, o app reinspeciona Lighting e compara ambos os valores anteriores para evitar alterações em um estado desatualizado. O código Luau que pode ser executado é **um template fixo dentro do Android**, formado apenas por números validados; nenhum Luau produzido pela IA é executado.
6. A chamada MCP `execute_luau` usa o mesmo caminho confirmado de escrita do app, exigindo o recibo exato `UPDATED_LIGHTING:Lighting`. O próprio comando revalida o estado anterior e pode devolver `STALE_LIGHTING`.
7. Em seguida o Android consulta `inspect_instance` novamente para comparar **ClockTime e Brightness**, inclusive o campo que não foi alterado. Resultado incerto não é relatado como gravação validada. Se existir plano guiado em andamento, ele pausa e não avança automaticamente diante de erro.

**Planos:** `LIGHTING` é uma categoria admitida no planejamento de 2 a 5 etapas. A categoria exige a permissão correspondente, uma leitura MCP atual, prévia e consentimento individual de escrita. A etapa só avança após sucesso confirmado por leitura do editor.

## Limitações honestas

- A ação modifica **somente** `ClockTime` e `Brightness`. Não altera `Ambient`, `OutdoorAmbient`, `FogEnd`, atmosferas, pós-processamento ou texturas de céu; esses recursos precisam de ferramentas tipadas adicionais.
- O resultado numérico não garante que a cena ficará visualmente boa no celular ou que o modo Play mostrará o mesmo ambiente. Ainda é necessário testar com mapa real e renderização no Play.
- O MCP exige sua infraestrutura local válida e autorizações do editor. A comunicação com o provedor de IA é por API Key; o modelo não recebe permissão genérica para escrever.
- O indicador `read-back` prova apenas os valores lidos depois da edição. Não garante persistência após salvar/publicar o projeto nem qualidade do mapa.
- A alteração de Lighting é global para o jogo; por isso exige aprovação separada e não é executada ao planejar ou simplesmente receber a resposta da IA.

## Verificação de código

- `scripts/test_ai_lighting_java.py`: compila o `LightingProposal`, o template **Java real** `fixedLightingLuau` e o validador real `checkedEditReceipt` usando Java 11. Testa valores, propriedades não selecionadas, recibos exatos, rejeição de erro e separação de justificativa textual de código executável.
- `scripts/test_ai_plan_guards.py`: cobre autorização cruzada e rejeição para a categoria `LIGHTING`.
- `scripts/check_ai_hub.py`: verifica estaticamente estado, consentimento, seleção do destino real, limites, template fixo e caminhos de pós-verificação.
- `.github/workflows/ai-hub-android-java.yml`: executa as suítes e compila `StudioAiHub.java` com o Android SDK 35.

A confirmação de CI/APK precisa ser feita sobre o **commit final**. Compilação Java e testes unitários não substituem testar a gravação MCP e a iluminação em Android real.

## Continuação do trabalho

Prioridade seguinte: materiais, transparência, colisão, ancoragem e propriedades físicas de Parts já existentes; então testes automáticos de Play e verificação visual do mapa. A interface e o código já existentes devem continuar intactos.
