# Agente do RBX Studio Android — R8

Esta atualização continua a R7 entregue. As alterações operam o DOM real do editor, o compilador Luau, os serviços de arquivos, a Toolbox e o runtime local existentes.

## Diagnóstico da R7

- sendMessage interceptava palavras sobre chuva e chamava proposeWeather antes de consultar o provedor. O método inseria sempre StudioAiWeather.SOURCE, no destino fixo RbxWeatherRain. O atalho e essa classe foram removidos.
- O fluxo de project_edit retornava depois de preparar um lote. A confirmação e a gravação aconteciam na UI e encerravam a consulta atual. O novo executor mantém o ciclo de ferramentas após cada lote.
- O limite de três leituras restringia a investigação. O modo autônomo agora permite configurar 8, 16, 24 ou 32 chamadas, com prazo de 15 minutos e limite de correções consecutivas. As chamadas extras de fallback entram no orçamento.
- O MCP de Play chamava a ação da barra do editor, que no Android inicia o fluxo externo. Os testes MCP passam a chamar especificamente play_start_android_local. A publicação externa continua no fluxo próprio.
- O contador de mutações do DOM podia voltar ao restaurar um histórico. A prévia agora combina revisão do histórico e contador do DOM para recusar gravações preparadas antes de outras edições.
- As aprovações de operações sensíveis não possuíam escopos de tarefa, projeto e categoria. Esses escopos foram adicionados sem ampliar automaticamente a autorização de edição para operações externas.
- Detalhes técnicos ocupavam o corpo principal da conversa. Os novos recibos ficam em uma seção recolhível de ações.

## Operações conectadas

O editor informa o estado real, seleção, serviços, revisão e capacidades. O agente consulta propriedades e enums por classe. Os IDs retornados pelo Explorer identificam objetos dentro da sessão atual. Ao reabrir o mapa, o agente consulta novamente o projeto.

A API de lotes mantém criação, propriedades, movimento, renomeação, duplicação, exclusão e terrain da R7. A R8 acrescenta atributos tipados, tags e substituição integral de scripts com comparação do código anterior. O lote é validado em uma cópia e instalado como um passo real de Desfazer após todas as operações passarem.

A leitura de scripts é paginada. A verificação de sintaxe usa o compilador Luau e retorna caminhos e diagnósticos. O scan é limitado a 32 scripts, 8.192 objetos visitados e aproximadamente 256 KiB de código. Campos complete e scope mostram a cobertura da leitura.

O agente pode salvar o arquivo do mapa aberto, selecionar objetos no Explorer, obter capturas reais de Edit e realizar um teste breve de inicialização e Output do runtime local quando as respectivas permissões estão habilitadas. Esse teste não prova todas as mecânicas de um jogo.

A Toolbox utiliza busca real. O modo de importação exige autorização própria e remove Script, LocalScript, ModuleScript, Plugin, VideoFrame e PackageLink da cópia baixada antes de inseri-la. A inspeção tem limites de tamanho e profundidade. O destino da importação precisa ser único e existente. Recursos que dependam de scripts importados exigem adaptação posterior. Essa inspeção não certifica a segurança de um asset.

publish_animation foi conectado ao chat. A publicação pede autorização específica com caminho e proprietário, usa a implementação Open Cloud existente e aceita somente um ID retornado pela API. A publicação e o carregamento do ID dependem das permissões e da resposta real do Roblox.

## Provedores e permissões

OpenAI Responses, Gemini, Claude e OpenRouter têm adaptadores de chamadas estruturadas e resultados de ferramentas. O adaptador preserva as assinaturas opacas de chamadas Gemini quando presentes. Se o provedor recusar o formato com HTTP 400, a tarefa pode continuar pelo protocolo JSON validado. As credenciais permanecem no Android Keystore e nunca são argumentos do modelo.

Os formatos dos quatro provedores foram testados com fixtures de protocolo. A conectividade e a geração de soluções por contas reais exigem chaves de API e testes de integração.

As permissões gerais continuam salvas por projeto e provedor. Capturas, importação e Play possuem opções próprias. Exclusões e publicações podem ser autorizadas uma vez, durante a tarefa, no projeto ou por categoria. Uma ação dedicada revoga as autorizações sensíveis.

Checkpoints criptografados guardam o objetivo e os recibos de operações. A retomada usa leituras novas. Uma conexão perdida durante uma gravação gera estado inconclusivo e interrompe a repetição automática. Pausar mantém as operações já confirmadas. O menu oferece Desfazer e Refazer com verificação de revisão.

## Evidências automatizadas

- scripts/test_ai_agent_java.py executa o ciclo de produção em testes unitários de transporte. Verifica a preservação de diferentes pedidos, múltiplos lotes, correção de prévia recusada, ausência de repetição após perda de resposta e quatro formatos de ferramentas. As respostas de teste não são evidência de geração real por um provedor.
- scripts/test_ai_project_native.py usa as funções de produção de edição, reflexão, compilação, atributos, importação e serialização. Os testes modificam árvores Roblox reais e fazem roundtrip pelo formato binário.
- tests/native_agent_contracts.rs verifica inventário entre serviços, objetos existentes por ID, atributos Vector3, partículas com parâmetros diferentes, rejeição de código Python, atomicidade do projeto original e remoção de componentes executáveis de assets.
- A preparação compara os patches com fontes limpas dos commits fixados dos submódulos.
- Os testes existentes de planos, Lighting e recibos MCP continuam disponíveis.

## Cenários solicitados

| Cenário | Cobertura nesta entrega |
| --- | --- |
| A — três tipos de chuva | Atalho removido. Preservação dos pedidos e parâmetros testada. Geração e resultado visual por API real pendentes |
| B — objeto existente | Edição por ID e caminho, propriedades e atributos testados no DOM |
| C — múltiplos serviços | GUI, RemoteEvent, módulos e scripts cliente/servidor testados, incluindo roundtrip de arquivo |
| D — permissões | Escopos implementados. Reinício físico do Android e telas de autorização pendentes |
| E — recuperação de erros | Prévia recusada e erro de sintaxe testados. Correção de erro de runtime pelo modelo pendente |
| F — troca de modelo | Adaptadores de quatro provedores testados. Troca com contas reais pendente |
| G — persistência | Serialização e reabertura de mapa testadas. Ciclo de fechar e abrir o app no celular pendente |
| H — interface | Código Android compilado. Toque, teclado, orientação e desempenho no dispositivo pendentes |

## Limitações observáveis

A captura de Play depende do host. Em Android, a captura do viewport em Edit está conectada, enquanto capturar a janela do jogo local pode retornar indisponibilidade. Capturas e logs não substituem um teste completo de comportamento.

A API cobre as classes e propriedades que a reflexão e o editor suportam. Isso não implica paridade total com todos os recursos do Roblox Studio oficial. Geração remota de meshes, moderação de assets, disponibilidade de sons e permissões de publicação são dependências externas.

Sem aparelho Android conectado e sem credenciais de teste, os testes A–H de ponta a ponta permanecem parcialmente verificados. O APK é uma build para teste.

## Arquivos principais

- android/src/com/rbxnative/androidport/StudioAiAgent.java
- android/src/com/rbxnative/androidport/StudioAiToolProtocol.java
- android/src/com/rbxnative/androidport/StudioAiHub.java
- android/src/com/rbxnative/androidport/StudioAiProjectEdit.java
- android/src/com/rbxnative/androidport/StudioAiConversation.java
- android/StudioManifest.xml
- patches/rbx-native--android-port.patch
- scripts/check_ai_hub.py
- scripts/test_ai_agent_java.py
- scripts/test_ai_project_native.py
- tests/StudioAiAgentTest.java
- tests/native_agent_contracts.rs

O patch inclui shell/mcp.rs, shell/mcp/project_edit.rs, shell/mcp/jobs.rs, mcp/tools/tree.rs, mcp/tools/assets/insert.rs, mcp/tools/assets/animations.rs e mcp/skills/mobile-studio-agent.md.

## Documentação usada

- [Function calling da OpenAI](https://developers.openai.com/api/docs/guides/function-calling)
- [Function calling do Gemini](https://ai.google.dev/gemini-api/docs/function-calling)
- [Uso de ferramentas do Claude](https://platform.claude.com/docs/en/agents-and-tools/tool-use/overview)
- [Segurança cliente-servidor Roblox](https://create.roblox.com/docs/scripting/security/client-server-boundary)

Os guias locais e o leitor de documentação oficial são consultáveis pelo agente. A adição desses materiais fornece referências recuperáveis e não altera os pesos dos modelos.

## Instalação desta build

O reinício do ambiente tornou indisponível a chave de assinatura de desenvolvimento usada na R7. A R8 usa uma nova assinatura e o identificador com.rbxnative.androidport.experimental.aichat.r8, para permitir instalação separada e preservar os dados da R7. Mapas e credenciais de outra instalação precisam ser abertos ou configurados na R8. A chave de assinatura é um artefato privado de compilação e fica fora do código-fonte publicado.
