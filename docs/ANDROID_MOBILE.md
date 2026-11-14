# Rbx Studio no Android

O aplicativo parte da 0.6.3. O documento de edição é do rbx-native, e Rook executa uma cópia com servidor e cliente locais. A gravação da versão anterior mostra uma publicação concluída pelo usuário e o Play com o mapa, o personagem e a skybox de montanhas.

## Compatibilidade acrescentada na 0.6.5

| Sistema | Cobertura | Pendente |
|---|---|---|
| ProximityPrompt | Toque padrão e interface Luau, tempo de espera, linha de visão, distância e sinais no cliente e no servidor | Indicadores à distância e seleção por gamepad |
| DragDetector | Parte ancorada com limite, sinais Luau com Ray e CFrame, arrasto físico de objetos soltos e liberação ao soltar o dedo | Trackball, funções Scriptable, políticas de permissão por callback e gamepad |
| Tool | Junta RightGrip, toque mantido, cancelamento, largar via script e recolher pelo Handle | Inventários particulares de jogos e testes físicos com os itens do usuário |
| Animator | KeyframeSequence pelo resolvedor de conteúdo, movimento de Motor6D, marcador e restauração da pose ao parar | Outros formatos de clipe, sincronização entre jogadores e rigs com nomes de juntas ambíguos |
| Editor | Excluir usa a seleção inteira, desfazer e refazer indicam disponibilidade, a Toolbox mostra Inserir sobre a miniatura | Validação de toque e de layout no POCO |

O botão de proximidade de reserva chama a API do próprio prompt quando o pacote de CoreScripts não inicia a interface original. O ZIP inclui os três arquivos em `rbx_studio/src/dragger/target` que faltaram na 0.6.4. Eles são fontes do manipulador de malhas do editor.

### Verificação da 0.6.5

- A suíte principal passou 47 testes no Linux. Os três cenários adicionais passaram com os recursos oficiais em cache e Vulkan por software. Eles cobrem o menu original, o botão de proximidade e partículas texturizadas com skybox.
- O cenário de ferramenta conferiu a pose física e a desenhada, o cancelamento do toque e a coleta de volta ao Backpack. O teste de animação usou um clipe em cache com um marcador e conferiu a pose do Motor6D ao tocar e parar.
- A instalação, a GPU Adreno, a edição pelo teclado Android e a publicação com a conta do usuário exigem verificação no aparelho.

## Base validada na 0.6.4

| Sistema | Integração no aplicativo | Evidência ou lacuna |
|---|---|---|
| Scripts, LocalScripts e ModuleScripts | VMs Luau separadas, serviços, sinais, agendamento e remotes locais via Rook | Testes exercitam RemoteEvent nos dois sentidos e a execução real de scripts. A cobertura dos serviços acompanha o runtime. |
| Saúde, morte e respawn | O servidor local valida o Humanoid do jogador que solicitou reset. O menu usa o ciclo de morte existente. | Testes separam Retomar, Reset e Sair. Scripts do mapa não ganham acesso a Shutdown. |
| Humanoid e física | Rapier com movimento, colisões, truss, escalada, salto, natação e estados | Cenário de obby com truss e salto segurado. A direção do impulso e o tempo para reagarrar são aproximações. Assentos e veículos complexos continuam parciais. |
| R6, R15 e animações | Clipes públicos de locomoção e leitura de KeyframeSequence. As poses de Motor6D entram no desenho do avatar. | O teste de locomoção usa o avatar padrão. Animator e AnimationTrack para scripts arbitrários continuam incompletos. |
| Entrada no celular | Cada dedo conserva seu InputObject. O runtime trata captura, rolagem, toque no mundo e cancelamento. | Testes cobrem toque, arrasto, cancelamento, dois dedos, GuiButton e ClickDetector. |
| Ferramentas | Backpack, equipar, desequipar e sinais replicados Activated e Deactivated. Tool:Activate e Tool:Deactivate usam os mesmos sinais. | Testado com ferramenta sem Handle. Encaixe do Handle, soltar objetos e ações contínuas exigem cobertura adicional. |
| Outras interações | ContextActionService, entrada e sinais da GUI podem ser usados pelos scripts | ProximityPrompt e DragDetector ainda não têm seus fluxos nativos completos. |
| Menus e gráficos | CoreScripts oficiais carregados em execução, interface ampliada onde cabe, configurações ligadas ao PlayerSettings | No Android a escala 3D é 2/3 no Automático e nível 1, 80% nos níveis 2 e 3 e integral a partir de 4. A seleção de saídas de áudio segue o sistema. |
| Chat e emotes | CoreScripts e coleção de emotes com estado e sinais | Chat e presença de servidores Roblox e execução geral de emotes personalizados continuam sem validação. |
| Partículas, Beam e Trail | Textura atual e legada. Transformações de Attachment aninhado são compartilhadas pela simulação e pelo desenho. | Cenário com emissão Luau e textura curva. Veja a investigação do modelo específico abaixo. |
| Skybox | Sky da Toolbox entra em Lighting. O antigo vai para Skyboxes anteriores. O Play solicita as seis faces, mesmo quando uma falha. | Cenário com seis faces e renderização fora da tela. A imagem original depende das permissões do asset. |
| Toolbox | Busca, miniaturas, recentes, inventário e modelos locais ou por ID | Uma revisão assíncrona conta scripts em qualquer nível, permite ler o código e pode remover scripts antes de inserir. A análise estática aponta indícios e fontes ausentes. |
| Editor e Explorer | Analógico, pinça, giro fora de manipuladores, seleção, duplicação, exclusão e histórico | Testes de importação com várias raízes, duplicação, histórico e restauração depois do Play. Os gestos no POCO ainda precisam de teste físico. |
| Home | Prévias oficiais baixadas sob demanda e armazenadas com até 192 px. O headshot usa o ID confirmado pelo Open Cloud. | URLs e tamanhos das três prévias conferidos. O headshot da conta real depende da integração ativa no celular. |
| Recursos e rede | Pacotes, cache, leitura offline, Asset Delivery e Open Cloud para conteúdo autorizado | O teste de SpawnLocation confere a prioridade do recurso local. O download que exige autenticação ficou sem teste. |
| Materiais, áudio, luz e terreno | Renderizadores existentes e mixer do Rook | Efeitos avançados, áudio e desempenho em mapas grandes precisam de comparação adicional no Android. |
| Publicação e persistência | Formatos de arquivo, histórico, link do projeto e Open Cloud nas rotas existentes | A publicação com a conta real não foi repetida. Os testes de arquivo verificam que o Play preserva o documento. |
| LSP, Rojo, Lune e colaboração | Pontos de integração do rbx-native preservados | Algumas ferramentas dependem de executáveis externos. O Play local usa o motor Rook para simulação. |
| Android | APK ARM64 para Android 10 ou superior | Compilação, assinatura e alinhamento conferidos. A GPU, o desempenho e o toque do POCO X6 5G precisam de validação física. |

## Resultados da 0.6.4

- A suíte Linux passou 42 testes. Ela cobre mapa e personagem, morte e respawn, Luau, histórico, persistência, revisão de scripts aninhados, escalada, salto, ferramentas e toque.
- O teste separado do menu carregou o DataModelPatch oficial 0.746.0.7461269 e tocou nos botões do próprio menu. Retomar manteve o personagem, Redefinir o trocou após confirmação, o controle gráfico aplicou e salvou o nível 6, e Sair só solicitou o encerramento após confirmação. Cancelar manteve o Play aberto.
- O ensaio com recursos oficiais em cache executou 25 scripts do cliente sem erros registrados. Dois contatos nos controles originais moveram o avatar R15 em 23,438 studs e o fizeram subir 6,684 studs no salto. O cancelamento interrompeu a caminhada e o fim da sessão restaurou o arquivo de edição.
- O teste de fonte atrasou a resposta HTTP em um servidor local. O jogo continuou por 45 quadros até a conclusão das quatro requisições da fonte. A leitura seguinte conservou as mesmas medidas de texto e reutilizou os dados carregados.
- O teste Vulkan com SwiftShader renderizou uma cena de 640 × 360 com seis faces de céu e uma partícula curva disparada por Luau. A leitura da imagem registrou 5.132 pixels da partícula e 224.764 pixels do céu. A textura desse teste é sintética.
- O APK contém as duas bibliotecas ARM64 compiladas, ambas com segmentos ELF alinhados a 16 KiB. O manifesto identifica a versão 0.6.4, código 10 e Android 10 como mínimo. O certificado é o mesmo da 0.6.3. A instalação e o desempenho no POCO X6 5G não foram medidos.

## Vento e céu

A gravação mostra três objetos distintos no Explorer, incluindo `Wind particles` e `SkyBox with Mountains`. O céu de montanhas aparece no Play. O modelo homônimo mais bem classificado na busca pública declara zero scripts, mas o vídeo não confirma seu ID. O download do candidato respondeu HTTP 401 sem uma conta autorizada. O cenário reproduzível cobre emissão Luau, textura curva sintética, Beam, Trail, Attachments aninhados e seis faces de Sky. O modelo do vídeo continua pendente de uma reprodução com seu RBXM ou RBXL.

## Executar os testes

- `cargo test --locked -p rbx_android_core --tests`
- O teste de renderização marcado como ignorado requer um adaptador Vulkan ou GL. `cargo test --locked -p rbx_android_core --test play_compat renders_textured_luau_particles_against_a_six_face_sky -- --ignored --nocapture`
- O teste do menu original requer `RBX_MOBILE_OFFICIAL_COREPATCH` com o caminho do DataModelPatch oficial e `RBX_MOBILE_OFFICIAL_CONTENT` com o diretório de recursos. `cargo test --locked -p rbx_android_core --test play_compat original_pause_menu -- --ignored --nocapture`
- O ensaio de controles e fontes pode reaproveitar um cache preenchido em `/pasta/cache/official-resources`. `cargo run --locked -p rbx_android_core --example studio_resources -- /pasta/cache --touch --offline-cache`
- O inventário dos módulos está em `FULL_PORT_AUDIT.md`. Os testes de versões anteriores estão em `TEST_REPORT.md`.

## Referências

- [Templates do Creator Hub](https://create.roblox.com/docs/resources/templates) e [miniaturas de avatar](https://create.roblox.com/docs/cloud/reference/features/avatars).
- [Riscos de assets de terceiros](https://create.roblox.com/docs/scripting/security/third-party-vulnerabilities).
- [Humanoid](https://create.roblox.com/docs/reference/engine/classes/Humanoid), [ferramentas](https://create.roblox.com/docs/players/tools) e [efeitos](https://create.roblox.com/docs/effects).
- [Prompts](https://create.roblox.com/docs/ui/proximity-prompts), [arrasto 3D](https://create.roblox.com/docs/ui/3D-drag-detectors) e [Animator](https://create.roblox.com/docs/reference/engine/classes/Animator).
- [Motor Rook](https://github.com/AsyncFinn/rook) e [roadmap](https://github.com/AsyncFinn/rook/tree/main/docs/roadmap).
