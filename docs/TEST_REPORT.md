# rbx-native Android 0.3 — entrega e validação

Data: 1 de novembro de 2026. Build debug ARM64 para Android 10/API 29 ou posterior; alvo API 35. Pacote `com.rbxnative.androidport`, versionCode 3, versão `0.3-native-runtime`.

## Resultado

A versão 0.3 compila para ARM64 e x86_64, passa os 26 testes do núcleo e completa a instrumentação Android 35. O Play agora usa a sessão original Rook, com servidor e cliente locais, suas VMs e replicação. O editor, o documento e o renderer continuam baseados no rbx-native original. Não se utilizou Stew.

O teste Android verificou GUI desenhada e clicável com resolução interna reduzida, movimento real do R6 por toque, Play/Stop, edição, Luau e salvar/reabrir. O POCO X6 5G físico ainda não foi testado por mim. Não há medição de FPS, memória ou temperatura no aparelho.

Fonte testada: [73c6433cd3340356514795ad0d90de9a10aec78d](https://github.com/Johnatafgfdgf/Roblox-Studio-for-Android/commit/73c6433cd3340356514795ad0d90de9a10aec78d). Execução aprovada: [GitHub Actions 41361416794](https://github.com/Johnatafgfdgf/Roblox-Studio-for-Android/actions/runs/41361416794). Documentação e continuidade: [branch de desenvolvimento](https://github.com/Johnatafgfdgf/Roblox-Studio-for-Android/tree/work/rbx-native-android-port).

## O que mudou

- Play usa `rook_engine::session::Session`, `ServerHost` e `ClientHost` originais, com Scripts/LocalScripts, ModuleScripts, remotes locais, respawn, física e Humanoid do Rook. A command bar de edição conserva o runtime rbx-native.
- A projeção do cliente fornece ao renderer os nomes, tipos e defaults corretos das propriedades. Alterações comuns são incrementais; mudanças estruturais podem exigir sincronização completa. Parar restaura exatamente o documento, a seleção e o estado dirty anteriores ao Play.
- PlayerGui do cliente aparece no renderer, com uma marca restrita à projeção. Os templates de StarterGui não aparecem duplicados. Essa marca não é gravada no projeto.
- GUI de jogo e entrada usam as mesmas dimensões internas. Os toques físicos são convertidos para esse espaço mesmo quando a cena é renderizada abaixo da resolução da Surface.
- A cabeça R6 arredondada usa a geometria original de `rook-render/src/mesh/head.rs`: 792 vértices, 2160 índices, normais e UVs conferidos. Uma malha real carregada tem prioridade; o fallback não sobrescreve assets nem cache.
- KeyframeSequences importadas usam Animator/Rig originais e alimentam os Motor6D físicos Rook. Ciclos proprietários de caminhada não foram incluídos.
- O leitor original de APK foi adaptado para instalações Roblox legíveis, APKs divididos e APK/ZIP escolhido pelo seletor. Recursos locais têm prioridade sobre cache antigo. A leitura reutiliza arquivos ZIP seekable; arquivos inválidos não substituem uma cópia válida.
- A importação de recursos roda em segundo plano e permite recarregar o renderer. As áreas de navegação, recortes e teclado de scripts usam WindowInsets Android. Painéis continuam sobrepostos, sem recriar o renderer ao abrir.
- O diagnóstico consulta o estado real do Humanoid e limita os logs. Foram consolidados patches reproduzíveis, o inventário dos 39 crates e os textos de licença das dependências.

## Evidências

| Verificação | Resultado e alcance |
|---|---|
| Recursos locais e geometria | 5 testes passaram: paths/content/ExtraContent, diferenças de caixa, travessia, múltiplos pacotes, cache, substituição e malha original |
| Edição e física | 14 testes passaram: template original, transformações, histórico, Luau, referências, save/reopen, importação, colisão, caminhada/pulo e restauração |
| Sessão de execução | 7 testes passaram: servidor/cliente/remotes, respawn, projeção incremental, câmera/tween, animação importada, interrupção de script, defaults e GUI/input |
| Total | 26 passaram localmente e na CI; 0 falhas |
| Preparação limpa | Duas execuções; os 34 arquivos adaptados conferidos byte a byte contra os patches |
| Android ARM64 e x86_64 | Compilação Rust/NDK, Java, D8 e empacotamento aprovados na mesma execução de CI |
| Android 35, x86_64, GPU SwiftShader | Instrumentação inteira aprovada; clique físico ativou Luau e o botão azul foi confirmado por amostra de pixels |
| Resolução do teste Android | 640 × 233 internos; o teste exige largura interna menor que a Surface física |
| Frames e painéis | 20 frames apresentados na captura de Play; 27 ao terminar; `surface_builds=1` nos dois estados |
| R6 no Android | Direcional por eventos de toque deslocou a raiz em pelo menos 2 studs; Stop retornou à edição |
| Renderer original no computador | Captura Vulkan/llvmpipe CPU 960 × 540, com R6 arredondado e GUI; sem texturas externas |
| Licenças | 397 pacotes no inventário Android; nenhum texto de licença faltando; NOTICE Rook preservado |
| APK final | Assinatura verificada; ZIP e segmentos ELF conferidos para alinhamento de 16 KiB; apenas a assinatura mudou após o empacotamento da CI |

Os logs e estados ficam em `docs/evidence/`. A instrumentação rejeita estado fatal, erro de teste, ausência de frames, janela externa sobre o aplicativo e recriação do renderer ao abrir painéis. A captura contém uma Part e um botão criados exclusivamente pelo teste; eles não foram acrescentados ao template padrão.

Comandos reproduzíveis do núcleo:

```bash
python3 scripts/prepare.py
CARGO_INCREMENTAL=0 CARGO_PROFILE_DEV_CODEGEN_UNITS=1 cargo test --locked -p rbx_android_core --tests
```

O encoder Intel do upstream possui kernels Android ARM64, mas não Android x86_64. Somente nesse segundo alvo, as imagens mantêm seus pixels RGBA originais sem recompressão BC. O encoder ARM64 e a decodificação BC originais continuam ativos. Essa diferença de build é registrada nos patches.

## Instalação e uso

**Exporte seus projetos na versão 0.2 antes de desinstalá-la.** A chave daquela entrega não foi recuperada; a 0.3 possui nova assinatura e o Android não permite atualizar diretamente uma instalação assinada com outra chave. Desinstalar remove os projetos privados do app. Após exportar, remova a instalação anterior, instale o APK 0.3 e abra os arquivos exportados pelo seletor.

O backup da nova chave debug foi preservado para manter a assinatura nas próximas entregas. Esta não é uma assinatura de produção.

Em Projeto, crie, abra, salve ou exporte. Em Inserir, adicione Parts. Use o viewport ou Explorer para selecionar, e Mover/Girar/Escalar ou Properties para editar. Toque longo no ícone mostra seu nome. Os painéis são recolhíveis e ajustáveis.

Em Scripts, edite, aplique e confira a sintaxe Luau. Testar inicia a sessão local; o direcional move, a seta pula, arrastar gira a câmera e a pinça ajusta a distância. Parar volta ao estado de edição anterior. O teste de pulo pertence ao núcleo; a instrumentação Android confirma o deslocamento pelo direcional.

Em Mais → Recursos locais, importe um APK/ZIP de recursos ao qual você tem acesso. A instalação Roblox legível é detectada automaticamente. Após importar, recarregue o renderer e pare/inicie Play para renovar os caches da sessão. Não foram redistribuídos recursos proprietários. Assets privados continuam dependendo de autorização pelos provedores originais; não há fluxo de login/API key adaptado nesta versão.

Mais → Gráficos e desempenho permite escolher resolução, qualidade e limite de atualização. O padrão é 1280 px no maior lado e qualidade 10; 960 px/qualidade 5 reduzem o custo. Os limites de 30/60 são configurações, não resultados de benchmark. O tempo de renderização exibido é de CPU, não uma medição isolada de GPU.

## Limites ainda abertos

| Área | Situação |
|---|---|
| POCO X6 5G / Adreno 710 | Teste físico, uso prolongado, FPS, memória e aquecimento pendentes |
| OpenGL/GLES | Captura local encontrou limitação no passe `fs_resolve`; compatibilidade não declarada corrigida |
| Aparência e animação | R15, roupas, acessórios e aparência de conta incompletos; sem caminhada padrão proprietária; KeyframeSequence importada não equivale a toda a API AnimationTrack |
| GUI e entrada | Casos cobertos funcionam; widgets, multitouch, IME de jogo e combinações avançadas permanecem parciais |
| Materiais/assets | Pipeline original preservado; acesso a todos os recursos externos não validado pelas capturas offline |
| Áudio | Graph/decode conectados ao engine; saída de áudio Android pendente |
| Multiplayer | Servidor/cliente/remotes locais; conexão multiplayer pela Internet não exposta |
| Editor completo | LSP, modelagem/CSG/autorig e ferramentas desktop completas pendentes |
| Ecossistema | Plugins, Rojo, Lune, login e publicação não integrados |

O inventário dos 39 crates descreve a origem e o estado de integração, não certifica todas as APIs. O projeto segue experimental e não é uma implementação completa do Roblox Studio ou compatibilidade com toda experiência Roblox.

## Identificação do APK entregue

- Arquivo: `rbx-native-android-0.3-arm64-v8a-debug.apk`
- Tamanho: 18,416,486 bytes
- SHA-256: `491583748dbfe2966b9f2348d787382c1f27378240d3453606da1993eca574df`
- Certificado SHA-256: `0b36c4e4587b40ecc16dbdca22fcaa20149432532567cc9acf44355c5b83711c`
- ABI: `arm64-v8a`; Android mínimo API 29; alvo API 35.
- rbx-native original: `eb85a1088a69f4159598b4fed0ae26956573c175`.
- Rook original: `8680da0fde3ded84731b7422c59de26db0e14135`.
