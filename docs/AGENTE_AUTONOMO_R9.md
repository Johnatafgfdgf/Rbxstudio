# RBX Studio Android — agente R9

Data: 21/11/2026. Continuação incremental da R8 recuperada, sem alteração dos três binários nativos ARM64. A camada Java do aplicativo foi recompilada. Não é uma declaração de conclusão das 18 fases nem de paridade com o Roblox Studio oficial.

## Diagnóstico e correções

| Problema | Causa encontrada | Alteração |
|---|---|---|
| `Ferramenta indisponível ou sem permissão: studio_action` | Envelope genérico confundido com a ação interna, com argumentos pouco específicos | Uma função por ferramenta, schemas por operação e normalização limitada do envelope legado, rejeitando conflitos |
| Modelo confundindo classe/nome/argumentos de criação | Schema genérico não delimitava cada formato de operação | `create`, `set`, scripts, atributos, tags, terreno e demais operações têm campos próprios e obrigatórios |
| Menu `+` abaixo da tela | Popup ancorado abaixo do campo sem limitar posição e altura à janela disponível | Cálculo dentro da área visível, preferência acima do campo, atualização após redimensionamento e teclado |
| Opções finais do menu inacessíveis | Conteúdo do popup sem contêiner de rolagem vertical real | `ScrollView` nativo, altura limitada, sem rolagem horizontal |
| Campo de mensagem cortado em pouca altura | Cabeçalho, ativação e status consumiam a altura disponível com IME | Modo compacto: oculta subtítulo e faixa de ativação e reduz linhas do campo/status quando necessário |
| Memória técnica e checkpoint não recuperados | Leitura do campo vazio de senha em vez do documento guardado no campo de usuário da credencial criptografada; checkpoint R8 com uma codificação adicional | Decodificador compartilhado para documentos R9 e checkpoints legados R8 |
| Conclusão apesar de erros de sintaxe persistentes | Estado de verificação marcado como concluído antes de conferir os erros, especialmente no último passo | Erros reais impedem a conclusão normal e retornam ao ciclo de correção até o limite |
| 503 do provedor interrompendo imediatamente | Nenhuma nova tentativa para recusa temporária explícita | Uma tentativa adicional limitada para 502/503/504/529; conta no orçamento. Não repete gravações nem falhas de transporte ambíguas |
| Modelo inventando propriedades e IDs dentro de scripts | Compilação Luau verifica sintaxe, não a existência das APIs Roblox | Preflight consulta o schema para atribuições literais simples; bloqueia IDs não confirmados e exige categoria separada para carregamento remoto de código |

Não foi encontrado um roteador ativo `pediu chuva → inserir script de chuva` na base R8 recuperada. O ciclo novo não contém respostas de chuva nem identificadores fixos para esse pedido. A instrução específica sobre chuva foi substituída por orientação geral. Exemplos de operações continuam sendo exemplos declarados; não substituem solicitações silenciosamente. Os geradores recebem parâmetros estruturados escolhidos pelo modelo e suas prévias ficam disponíveis antes da aplicação.

A extração de blocos Luau, os cartões separados de código, as permissões persistentes e a inicialização local do MCP já existiam na R8 e foram preservados. Não são apresentados como implementações inéditas da R9.

## Infraestrutura conectada

`StudioAiAgent` coordena chamadas reais, inspeção, correção, salvamento e evidências. `StudioAiToolProtocol` adapta OpenAI Responses, Gemini, Claude e OpenRouter sem converter texto comum do chat em código executável. Preserva os identificadores e metadados necessários das chamadas, incluindo o conteúdo original e as assinaturas opacas do Gemini. Uma chamada por resposta mantém a execução sequencial previsível.

`StudioDeveloperEngine` conecta planejamento com dependências, registro de habilidades, prévias procedurais, execução pelo adaptador real do editor, inspeção estática, recuperação e memória. O trabalho é limitado por chamadas, duração e tamanho das operações. Papéis como World Builder, Animator e QA organizam critérios do plano; não representam conversas fictícias entre modelos. Ainda não existe distribuição automática completa de cada papel para provedores distintos.

`StudioDeveloperKnowledge` recupera seletivamente até três documentos por consulta: sete guias autorais iniciais e documentos externos limitados, com origem e licença. `StudioResourceHub` descobre repositórios públicos, consulta commit, licença e README sem executar código. `StudioAiScriptPolicy`, `StudioAiStoredDocument`, `StudioAiRetry` e `StudioAiPopupBounds` implementam validações, armazenamento compatível, recuperação limitada e geometria de popups.

Não houve treinamento dos pesos de nenhum modelo. Memória e documentação são contexto recuperável.

## Ferramentas efetivas

O catálogo tem 30 ferramentas estruturadas:

- Contexto, schema, seleção, busca de hierarquia e inspeção de instâncias.
- Edição de projeto em lotes reais: criar, garantir serviço/container, propriedades, renomear, mover, duplicar, excluir, substituir/editar scripts, atributos, tags e região de terreno.
- Leitura e pesquisa de scripts, diagnóstico, Output e estado do editor.
- Busca na Toolbox, importação revisada e busca de documentação, conforme permissões e disponibilidade do serviço.
- Captura real de viewport, testes locais e publicação de animação pelas rotas já existentes, quando disponíveis e autorizados.
- `skills_list`, `task_plan`, `procedural_generate`, `procedural_apply`, `quality_check`, `knowledge_search`, `resource_search`, `resource_inspect`, `rig_inspect` e finalização.

As edições passam pelas funções nativas já existentes de preview/aplicação, reflexão de tipos, revisão de exclusões e recibo real. Objetos podem ser referenciados por ID de sessão ou caminho. A interface existente recebe a atualização do DOM, histórico e marcação de alteração. Não existe uma ferramenta de execução arbitrária de texto do chat.

As permissões efetivas continuam sendo filtradas pela sessão, categoria e escopo concedidos. Leituras de scripts, imagens, importação, Play e operações externas dependem das respectivas opções. O catálogo fixo do protocolo não garante que toda ferramenta esteja disponível em toda sessão; recusas retornam informação para correção. Não há nova permissão ilimitada de conta.

## Geração procedural disponível

| Gerador | Resultado editável real | Limite ou cuidado |
|---|---|---|
| `terrain` | Regiões do Terrain com ruído fractal, suavização, materiais, rio e água parametrizados | Grade de 2 a 8 células por eixo; uma região por lote nativo; suavização não é erosão hidráulica |
| `building` | Modelo com piso, paredes, entrada aberta com verga e telhado | Estrutura modular simples; não é biblioteca de todos os estilos arquitetônicos |
| `scatter` | Árvores procedurais com espaçamento e áreas reservadas | Até 48 árvores; vínculo explícito ao terreno recém-aplicado amostra sua superfície e evita água |
| `vfx` | Camadas de emitters, luzes, beams e trails com attachments reais | Até oito camadas e orçamento de emissão; sem garantir qualidade visual ou textura publicada |
| `ui` | ScreenGui, painel, título, grade responsiva e scroll | Até 32 itens; botões com callback têm LocalScript; itens sem interação viram labels |
| `animation` | KeyframeSequence com árvore Pose, tempos e easing | Rig e nomes inspecionados; reprodução, blend, retargeting e qualidade de movimento ainda precisam de teste |
| `camera` | Timeline editável com CFrameValues e controlador local TweenService | Restaura a câmera ao terminar; coordenação cinematográfica audiovisual completa ainda parcial |
| `audio` | Sons espaciais com volume, looping, atenuação e velocidade | IDs confirmados; inicialmente parados; não gera áudio nem publica automaticamente |

Prévia: até 256 operações; até oito prévias por tarefa. Aplicação: lotes de até 32 operações, exceto terreno, que usa uma região por lote compatível com a R8. Um cursor de aplicação evita repetir lotes já confirmados dentro da tarefa. Se a resposta de uma gravação for perdida, o agente para de escrever e exige inspeção, sem repetir a operação automaticamente.

As verificações estáticas observam propriedades reais: emissão de partículas, SoundId ausente, attachments e texto pequeno. Não medem FPS, draw calls ou beleza. Não inferem que o efeito apareceu só porque uma instância existe.

## Recursos consultados e licenças

O conector real foi testado em 21/11/2026. Os resultados completos, incluindo commits exatos, estão em `docs/evidence/r9/sources.json`.

| Fonte | Licença retornada pela origem | Uso nesta entrega |
|---|---|---|
| https://github.com/Roblox/react-luau | MIT | Descoberta e inspeção de metadados/licença/README; pacote não instalado automaticamente |
| https://github.com/Roblox/testez | Apache-2.0; repositório arquivado | Referência de testes; não instalado como dependência |
| https://github.com/dphfox/Fusion | MIT no commit consultado | Referência de interfaces; confirmar novamente ao escolher outra revisão |
| https://github.com/JohnnyMorganz/luau-lsp | MIT | Referência de linguagem; não é um novo servidor LSP embarcado nesta entrega |

Os componentes nativos essenciais Luau, DOM, reflexão, serializer e terreno já faziam parte da base. Suas licenças e avisos existentes foram preservados. Nenhum pack externo de modelos, sons ou VFX foi redistribuído. Fontes novas podem ser pesquisadas pelo conector; README, comentários e metadados continuam sendo dados externos, sem autoridade para mudar permissões.

Referências técnicas verificadas: https://ai.google.dev/gemini-api/docs/function-calling e https://create.roblox.com/docs/reference/engine/classes/ParticleEmitter. Os guias embarcados são resumos autorais, com links para documentação oficial; não são cópias integrais desses sites.

## Testes e evidências

| Verificação | Resultado e alcance |
|---|---|
| Regressões determinísticas | 28/28 passaram: contratos Java, segurança, permissões, inspeção de modelos/assets, protocolos, Play e evidências como unidades de teste; não equivalem a executar tudo no Android |
| Edição nativa e persistência | 15/15 passaram usando funções reais do DOM, reflexão, parser de propriedades, compilador Luau e serializer Roblox; oito casos cobrem os geradores |
| Geometria dos menus | 10.000 combinações passaram, incluindo telas pequenas e área disponível reduzida pelo teclado |
| Interface Android 29 | `+` em paisagem e retrato, rolagem até a última opção e campo visível com IME passaram na Activity real em APK exclusivo de teste da interface. Houve uma falha do System UI na inicialização do emulador, resolvida com a opção Aguardar; não é evidência de desempenho no aparelho |
| Provedores | Contratos dos quatro protocolos passaram; chamadas online reais somente em dois modelos Gemini |
| Gemini 3.5 Flash Lite — cabana | 7 chamadas, 9 operações reais, arquivo `.rbxl` salvo e desserializado novamente; tarefa estrutural concluída |
| Gemini 3.1 Flash Lite — terreno e seis árvores | 8 chamadas, 35 operações reais, arquivo `.rbxl` salvo e desserializado novamente; tarefa estrutural concluída |
| Chuva — Gemini 3.5 | Incompleto: respostas 503 de alta demanda, inclusive após nova tentativa limitada; alterações confirmadas preservadas |
| Chuva — Gemini 3.1 | Incompleto: limite gratuito de requisições por minuto retornou 429 depois de três operações confirmadas; não foi declarado sucesso |
| Compilação Android | Todos os 49 arquivos Java de produção compilaram com SDK 35 e compatibilidade Java 11 |
| Build ARM64 | Java/manifest/resources novos com os três binários nativos R8 preservados byte a byte; assinatura e alinhamento verificados |

Os mapas e traces entregues como evidência foram gerados com a API real e o mesmo código de edição/reflexão/compilação/salvamento usado pelo aplicativo, através de um transporte de teste sem interface. Não são mocks dessas operações. **Esse teste não executa o socket MCP Android, GPUI, viewport, física, scripts em Play nem o app Roblox oficial.**

Foram capturados menus em um emulador Android 29 com a Activity Java real, usando um APK exclusivo de teste da interface, sem bibliotecas nativas, chaves de usuário ou viewport. As capturas finais demonstram `+` limitado à tela em paisagem e retrato, rolagem até a última opção e campo de envio inteiramente visível com IME. Uma captura imediatamente após a troca de orientação mostrou o launcher antes de a Activity reaparecer; o XML subsequente e a captura do popup em retrato confirmaram a interface. O teste foi ajustado para capturar depois da leitura do layout nas próximas execuções. Esses resultados não devem ser confundidos com um teste do APK ARM64 no POCO.

## Limitações e fases ainda parciais

| Fase solicitada | Estado desta entrega |
|---|---|
| 1 — Inteligência aumentada | Fundação conectada; planejamento, geradores, contexto, execução e recuperação. Os motores avançados não estão todos completos |
| 2 — Repositórios e ambientes | GitHub dinâmico e quatro fontes inspecionadas; terreno, distribuição e construção modular simples. Packs profissionais e estilos completos pendentes |
| 3 — VFX | Composição de camadas e referências reais; produção/publicação de texturas e avaliação artística ainda parciais |
| 4 — Animação | Inspeção de rig e sequências editáveis; retargeting, blend e resultado profissional não verificados |
| 5 — Game design | Ferramentas para sistemas em vários serviços; catálogo completo de componentes de gameplay não entregue |
| 6 — Áudio | Configuração espacial e origem confirmada; síntese/edição/publicação geral não concluídas |
| 7 — Cinematografia | Timeline de câmera editável e controlador; direção audiovisual completa ainda parcial |
| 8 — UI/UX | Grade responsiva com ações e scroll; validação de todos os layouts gerados em Play pendente |
| 9 — Memória | Documentos e recibos criptografados, inspeção nas configurações e exclusão. Transação durável de toda tarefa ainda ausente |
| 10 — Modelos menores | Schemas, geradores e correção limitada; compilador semântico universal de intenções não implementado |
| 11 — Equipe virtual | Papéis e dependências no plano; não há roteamento completo de especialistas entre vários modelos |
| 12 — Qualidade | Tipos, sintaxe, recibos e verificações estáticas reais; revisão visual completa não validada |
| 13 — Produção de assets | Rotas existentes preservadas; pipeline geral para todas as categorias ainda não suportado |
| 14 — Desempenho | Orçamentos de objetos, lotes e partículas; FPS, memória de GPU, LOD e draw calls não medidos |
| 15 — Segurança | Permissões, armazenamento e inspeção preservados; novos controles de IDs/código e origem. Não é certificação de recursos externos |
| 16 — Testes extremos | Duas tarefas online concluídas e testes nativos; a bateria completa de oito cenários não foi concluída |
| 17 — Experiência | Correções concretas dos popups, scroll, campo com IME e checkpoint; evidência visual separada do editor nativo |
| 18 — Implementação/build | Código conectado, Java recompilado e APK assinado; núcleo nativo R8 reutilizado, CI atualizado mas não disparado |

- Controle do DOM editável suportado não significa acesso a toda API ou ferramenta do Roblox Studio oficial. A disponibilidade depende da reflexão e dos recursos reais desta implementação.
- Não foram completados geradores de jogos inteiros, combate/inventário/save profissional, bibliotecas de todos os biomas/estilos, geração externa de imagens/sons nem pipeline geral de publicação de todos os tipos de assets.
- Toolboxes e uploads preservam as rotas existentes. Importação automatizada de modelos remove código executável e PackageLinks; adaptar scripts legítimos de terceiros ainda requer revisão específica. Nenhum asset foi publicado durante os testes.
- Modelos pequenos recebem ferramentas tipadas, documentação seletiva, limites e correção de argumentos. Isso melhora a execução; não garante qualidade profissional para qualquer solicitação.
- Preflight de scripts é limitado a padrões estáticos simples; não é um typechecker completo nem certificação de segurança. Luau válido pode falhar no runtime.
- Verificação visual depende de captura real, autorização e modelo com visão. Não houve confirmação de chuva, áudio, animação ou cinematografia funcionando no renderer/Play nesta entrega.
- Memória técnica é opcional e por projeto no perfil Android. Retomada após reiniciar consulta o projeto novamente; cursores procedurais não são uma transação durável entre processos. Respostas ambíguas bloqueiam repetição automática, mas uma retomada longa não foi validada ponta a ponta em aparelho físico.
- Parar pausa solicitações; alterações já confirmadas permanecem. Desfazer/refazer atua por etapa do histórico, não como reversão atômica de toda a tarefa.
- Permissões R8 persistidas foram preservadas e os testes de escopo passaram. A sequência completa de conceder/reiniciar/executar no dispositivo físico não foi testada nesta sessão.
- Outros provedores compatíveis com endpoint arbitrário, comparação automática completa de custo/capacidades e distribuição entre vários especialistas/modelos continuam parciais. Os quatro provedores existentes foram preservados.
- Desempenho no POCO X6 5G, primeira entrada no app, demais painéis e integração completa com teclado em aparelhos distintos ainda exigem validação no APK instalado.

## Reprodução e entrega

Reprodução dos testes: `scripts/run_ai_regressions.py`, `scripts/test_ai_project_native.py`, `scripts/check_android_java.py`, `scripts/test_chat_popup_android.py` e `scripts/test_ai_live.py`. Os dois últimos são opt-in: exigem SDK/AVD ou credencial local explicitamente fornecida. A chave usada nos testes não faz parte do fonte ou dos artefatos.

O workflow novo `developer-engine.yml` executa regressões, compilação Java e testes nativos, sem enviar chaves a CI. O workflow Java existente agora compila a lista real de fontes distribuídas. Os workflows foram atualizados no fonte, mas não foram disparados no GitHub nesta sessão.

APK: pacote `com.rbxnative.androidport.experimental.aichat.r8`, versionCode **25**, certificado preservado da R8. Deve atualizar essa instalação experimental sem desinstalar ou apagar seus dados. Isso não se aplica a APKs de outra assinatura/pacote. O código-fonte completo inclui os submódulos expandidos, patches e testes. A chave de assinatura privada não está no ZIP.

O APK desta entrega não é uma recompilação integral do Rust: recompila a camada Java modificada e reutiliza o núcleo R8 cujo SHA-256 foi conferido. Não há alterações nativas R9 pendentes fora do binário distribuído.

Commit local e hashes finais ficam em `SOURCE_BUILD_INFO.json` do ZIP e no arquivo de verificação da entrega. Nenhum commit foi publicado nem PR criado nesta sessão.
