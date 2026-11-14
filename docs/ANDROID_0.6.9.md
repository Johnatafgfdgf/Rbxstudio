# RBX-Mobile 0.6.9

## Evidências da 0.6.8 e revisão de impacto

| Área | Evidência de partida | Gravidade e causa | Mudança na 0.6.9 |
|---|---|---|---|
| Cena e ferramentas | Captura em paisagem enviada pelo usuário. Com Ferramentas abertas, o viewport tinha cerca de 190 dos 690 px da altura da imagem. | Alta. Barra principal, abas, faixa de ferramentas e console ocupavam linhas permanentes. | Barra principal de 52 unidades lógicas. Ferramentas e menus abrem sobre a área de trabalho. Console recolhido por padrão. |
| Explorer, Properties e Toolbox | Captura em paisagem com os dois painéis. Explorer e Toolbox abertos juntos deixavam cerca de 43% da largura para a cena. | Alta. O layout em três colunas calculava os dois painéis independentemente. | Uma gaveta com abas e largura de 42% a partir de 640 unidades lógicas. Em telas estreitas a gaveta abre com largura total e pode ser fechada para retornar à cena. |
| Arraste e rotação | Usuário relata alça presa em um eixo após soltar e anéis que falham sob toque. | Alta. Fim/cancelamento do gesto podia deixar estado de arraste. O hit test de rotação dependia do cruzamento do raio com o plano, sensível à inclinação da câmera. | Finalização aplica a última amostra e restaura o gizmo. Troca de seleção/ferramenta, perda de foco e redimensionamento limpam a interação. Anéis projetados na tela têm faixa de toque e uma tangente de arraste para planos quase de perfil. |
| Rolagem | Usuário relata menus com deslocamento abrupto e conflito entre controles. | Alta. Fling Android usava o decaimento geral e a lista interna sempre consumia a rolagem. | Arrasto segue o deslocamento do dedo. Fling usa a curva Android disponível no GPUI. A rolagem pode chegar ao ancestral quando o conteúdo interno encontra um limite. O mapa trata gestos iniciados no viewport. |
| Play e publicação | O usuário confirmou que a publicação comum funciona. O Play Android anterior abria o Rook. | Alta. O teste local tinha cobertura incompleta de APIs Roblox. | Play reutiliza a publicação de lugar com destino privado separado. Play local ainda está no Test. A ação Publish to Roblox permanece disponível para o fluxo de publicação comum. |
| Configuração de Play | A página Conta tinha campo de destino em linha e navegação desktop. | Alta. Larguras fixas comprimiam o formulário e o menu vertical podia ultrapassar telas baixas. | Campo de destino em bloco, validação assíncrona, acesso ao Creator Dashboard, navegação horizontal em janelas estreitas. |

## Inventário por entrada

| Controles | Caminho Android | Estado da verificação |
|---|---|---|
| File, Edit, Model, View | Menu | Rotas inspecionadas. Abertura, rolagem e fechamento por toque pendentes. |
| Salvar, Play, Parar, Cena, Teclado | Barra superior, conforme o documento ou estado | Salvamento e regras de destino testados em Rust. Interações Android pendentes. |
| Home, Avatar, UI, Script, Model, Test, Plugins | Ferramentas | Rotas e estados inspecionados. Navegação por toque pendente. |
| Copy, Paste, Cut, Duplicate, Part, Script, GUI | Ferramentas → Home ou menus contextuais | Entradas inspecionadas. Operações via faixa e menu de toque pendentes. |
| Selecionar, Mover, Girar, Redimensionar, focar, eixos locais/globais, encaixe, analógico | Barra superior no documento de cena. A faixa pode rolar horizontalmente. | Manipulação de modelos agrupados, pivôs, eixos e projeção de alças testadas em Rust. Gestos no viewport pendentes. |
| Explorer, Propriedades, Toolbox, Output e painéis adicionais | Painéis ou Menu → View | Caminho da gaveta inspecionado. Busca, rolagem, edição de propriedades e importação via UI pendentes. |
| Editor de scripts e de interfaces, animação | Documento e ações do Explorer, Ferramentas e menus | Persistência de código testada em Rust. Digitação, modo de foco e retorno para Cena pendentes. |
| Criar, abrir, exportar, publicar, Configurações | Home, File e Play | Regressão de arquivos em Rust. Seletor Android e envio a um lugar real pendentes. |

O inventário inicial de caminhos e pendências está em [ANDROID_UI_BASELINE_0.6.7.md](ANDROID_UI_BASELINE_0.6.7.md). O [roteiro Android](../tests/android/README.md) registra as verificações a repetir por resolução.

## Configurar Play

1. Prepare uma experiência privada dedicada e copie o ID do lugar inicial. **Escolher ou criar** reutiliza o seletor e a criação de experiências já oferecidos pela publicação do RBX-Mobile. A criação depende do login Roblox usado pelo editor e da autorização da conta. Se o jogo usa vários lugares, assets restritos ou DataStores, verifique essas dependências da experiência de teste.
2. Em **Play → Configurar teste**, use uma chave Open Cloud com **Universe Read** e **universe-places Write** para o destino. Se você já usa a publicação pelo RBX-Mobile, adicione esses acessos na chave configurada. **Universe Write** permite solicitar a reinicialização de servidores antigos desse lugar.
3. Informe o ID ou link do lugar e toque em **Usar lugar**. No primeiro envio da sessão, confirme o destino exibido.
4. No Roblox, use uma conta com permissão para jogar a experiência privada. Volte pelo seletor de apps do Android para continuar editando.

O Play valida a visibilidade privada e o ID do lugar inicial antes do envio. O salvamento usa o formato aberto e inclui alterações pendentes no editor de scripts. Se o projeto tem vínculo Roblox, o universo e o lugar vinculados são recusados como destino. A confirmação autoriza enviar cópias ao destino escolhido durante a sessão de edição. O estado é limpo se o destino mudar. Um envio confirmado no Open Cloud fornece o número de versão. O lançamento no Android pode ser repetido sem novo upload. A entrada efetiva e a versão carregada no cliente dependem do Roblox.

O código usa a API v1 de versões que já atendia a publicação do RBX-Mobile. O guia atual também apresenta o fluxo assíncrono de Assets API. A documentação do Roblox marca o esquema de deep links legado como obsoleto e recomenda Share Links para uso público. O intent Android restringe o destino ao pacote oficial e verifica se a instalação oferece um handler compatível. [Esquema de publicação v1](https://github.com/Roblox/creator-docs/blob/main/content/en-us/reference/cloud/universes-api/v1.json) · [Guia de publicação](https://create.roblox.com/docs/cloud/guides/usage-place-publishing) · [Deep links](https://create.roblox.com/docs/production/promotion/deeplinks) · [Intents Android](https://developer.android.com/reference/android/content/Intent)

## Arquivos e dependências

- `android/StudioManifest.xml`, `android/src/com/rbxnative/androidport/StudioRoblox.java` e `scripts/package_apk.py` identificam e empacotam a versão e fazem a abertura no Android.
- `patches/rbx-native--android-port.patch` altera Play, Settings, layout e ferramentas do editor, eventos do viewport, projeção do gizmo, câmera e rolagem GPUI.
- `patches/gpui-mobile--android-port.patch` liga o intent Android e configura a inércia do gesto no backend de toque.
- As adaptações de transformação usam os pivôs, o histórico e a seleção do editor existente. As bibliotecas ImGuizmo e TransformControls foram referências de algoritmos. O código e as licenças dos componentes continuam no pacote.

## Resultados

| Verificação de código e pacote | Resultado |
|---|---|
| Regressão Rust do editor e Rook | 58 passaram, 0 falharam, 3 ignorados. Inclui modelos agrupados, pivô personalizado, rotação local e global, importação, salvamento, Luau e cenários do Play local. |
| Projeção das alças | 3 passaram. Faixa de toque, visão de perfil e recorte atrás da câmera. |
| Destino de Play | 3 passaram. Testa destino separado, IDs inválidos, visibilidade e lugar inicial. |
| Diagnóstico Rook | 8 cenários passaram. |
| Roteiro ADB | 5 testes unitários passaram. Testam os parsers e critérios do roteiro. |
| Preparação de fontes | 34 arquivos gerados idênticos entre execuções. As três bases preparadas conferem com os patches. |
| APK | Android 10 ou superior, ARM64, código 15. ZIP íntegro, assinatura compatível com 0.6.8 e alinhamento ELF mínimo de 16 KiB. |
| Play local | A biblioteca nativa Rook empacotada é byte a byte a mesma da 0.6.8. |

Os três testes ignorados requerem uma GPU de renderização ou recursos oficiais da Roblox em cache. O empacotamento preserva os avisos da 0.6.8. O inventário de dependências ainda registra 47 entradas sem texto de licença no pacote de origem.

### Execução no Android

| Ambiente | Resultado observado |
|---|---|
| Android 15, API 35, x86_64 com tradução ARM64, CPU sem KVM | O serviço de pacotes do Android parou durante a instalação. A tentativa terminou antes da inicialização do app. |
| Android 11, API 30, x86_64 com tradução ARM64, paisagem 960 × 432 a 160 dpi | O Android instalou a 0.6.9 ARM64, confirmou código 15 e iniciou `GpuiActivity`. |
| Android 11 com Lavapipe e GLES via ANGLE | O GPUI encontrou um adaptador GLES. A configuração da janela retornou `EGL_BAD_ALLOC` e `Invalid surface` em duas aberturas. A captura ficou preta. |
| Android 11 com SwiftShader e Vulkan desativado | O único adaptador encontrado anunciou limite 0 de grupos de computação. A configuração do GPUI pede 65535. A abertura terminou antes do primeiro quadro do editor. |
| Android 11 com ANGLE/SwiftShader e opção `-feature -Vulkan` | O Android continuou anunciando `ranchu` como driver Vulkan. A superfície GLES voltou a falhar com `EGL_BAD_ALLOC`. |
| 0.6.8 como controle, no mesmo Android 11 com ANGLE/SwiftShader | A versão anterior instalou e registrou `EGL_BAD_ALLOC` e `Invalid surface` no mesmo ponto. A captura também ficou preta. |
| Atualização da 0.6.8 para a 0.6.9 no Android 11 | `adb install -r` atualizou o pacote ARM64 para código 15 e preservou o horário da primeira instalação. |

O contexto GPUI registra a rejeição em `platform/gpui-wgpu/src/wgpu_context.rs`. Nos modos com ANGLE, o Android também registrou uma tentativa de conectar a superfície EGL que já estava conectada. O projeto instancia Vulkan e GLES para a mesma janela. A próxima investigação é isolar o backend usado na criação da superfície e verificar os requisitos de GLES do renderizador. [Discussão no wgpu](https://github.com/gfx-rs/wgpu/issues/2384)

O teste de instalação verificou um APK ARM64 com a mesma assinatura da 0.6.8. O ambiente usa GPU por software. O POCO X6 5G e seu driver Adreno 710 seguem pendentes de teste. A navegação, o teclado, os controles de toque e os fluxos autenticados de Toolbox, publicação e Play requerem validação de ponta a ponta. Os testes de destino conferem regras de seleção do lugar. A confirmação de entrada na experiência depende de uma sessão real no Roblox. [Trechos de log](evidence/0.6.9/android-startup.log)

SHA-256 do APK

```text
356e3ec62bb7d87d6bb38f454c1c1ef6bff320f1f70a9b174cf7658b696a43cb
```

## Ferramentas de auditoria

- UI Automator acompanha o seletor de documentos, o teclado e outras Views nativas. O roteiro de auditoria guarda a árvore e capturas. [UI Automator](https://developer.android.com/training/testing/other-components/ui-automator)
- Maestro suporta automação por seletores e coordenadas. A superfície GPUI ainda exige coordenadas verificadas para seus controles. [Maestro](https://github.com/mobile-dev-inc/maestro)
- Ramus oferece emulação em nuvem por upload de APK. Uma sessão com acesso e suporte à ABI ARM64 ou um APK x86_64 da mesma revisão são necessários para repetir a auditoria ali. [Ramus](https://ramus.dev/docs/quickstart)
- O layout segue os tamanhos de alvo e o uso de dimensões lógicas do Android. As opções contextuais usam o GPUI do projeto. [Alvos de toque](https://developer.android.com/guide/topics/ui/accessibility/apps#touch-targets) · [Layout adaptável](https://developer.android.com/develop/ui/views/layout/responsive-adaptive-design-with-views)
- ImGuizmo e TransformControls serviram para comparar a geometria de alças e os referenciais local e global. A implementação usa a projeção do renderizador rbx-native, com seu histórico e os pivôs Roblox. [ImGuizmo](https://github.com/CedricGuillemet/ImGuizmo) · [TransformControls](https://threejs.org/docs/#examples/en/controls/TransformControls)
