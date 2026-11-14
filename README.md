# Rbx Studio · rbx-mobile

Editor nativo para Android com arquivos RBXL, RBXLX, RBXM e RBXMX. O Play salva o projeto, publica uma cópia numa experiência privada de teste e abre o Roblox oficial no Android. O Rook fica disponível em Ferramentas → Test → Play local. O APK ARM64 requer Android 10 ou superior.

## Versão 0.6.9

- Use **Painéis** para alternar entre Explorer, Propriedades e Toolbox. Abra os menus e a faixa de ferramentas quando precisar.
- Na cena, toque para selecionar, arraste fora das alças para olhar e use dois dedos para deslocar e aproximar a câmera.
- Configure **Play → Configurar teste** com o ID do lugar inicial de uma experiência privada e dedicada. A chave Open Cloud precisa de leitura do universo e escrita de versões para o destino de teste.
- O [relatório da versão](docs/ANDROID_0.6.9.md) reúne o inventário, as mudanças e o estado da verificação.

## Versão 0.6.8

- A barra Android oferece Play, Salvar, Explorer, Propriedades, Toolbox e acesso à faixa de ferramentas.
- Selecione um script no Explorer e toque em **Editar script**. Em janelas baixas o editor de código ganha a área dos painéis e permite reabri-los em **Painéis**.
- O Play inclui a última digitação do script mesmo quando o toque acontece antes do fim do agrupamento de alterações.
- O [inventário inicial da auditoria Android](docs/ANDROID_UI_BASELINE_0.6.7.md) relaciona os caminhos de toque com o código e as evidências.

## Versão 0.6.7

- Corrige a junta de Tools criadas por LocalScript e remove juntas previstas duplicadas após a replicação.
- Acrescenta hotbar de reserva em CoreGui, seleção por toque, reposição de StarterGear e regras de RequiresHandle.
- Conecta LinearVelocity, AngularVelocity, AlignPosition e AlignOrientation à simulação física, com alvos, limites e Attachments em execução.
- `File → Diagnóstico do motor…` executa oito cenários isolados e mostra resultados no Output.

A [matriz e o relatório desta etapa](docs/ENGINE_0.6.7.md) distinguem o que foi testado dos recursos parciais. Não há uma medição de paridade global com o Roblox Engine.

## Versão 0.6.6

- No Explorer, toque duas vezes no Script, LocalScript ou ModuleScript. Toque no código para abrir o teclado Android. As alterações entram no mesmo histórico e no mesmo fluxo de salvamento do editor.

## Versão 0.6.5

- O Play executa os eventos de `ProximityPrompt` com toque, tempo de espera, distância e linha de visão. A interface local aparece quando o pacote de CoreScripts não inicia o botão padrão.
- O toque arrasta partes e modelos por `DragDetector`. A resposta geométrica respeita limites e a física usa o motor Rook para objetos soltos.
- Ferramentas com `Handle` usam a junta da mão. O toque prolongado inicia o uso. Soltar ou cancelar encerra a ação. O item pode ser largado por um script e recolhido pelo personagem.
- `Animator:LoadAnimation` carrega clipes KeyframeSequence. O Play aplica as poses em Motor6D e executa os marcadores Luau.
- O Explorer exclui a seleção em uma etapa de histórico e sinaliza quando desfazer e refazer estão disponíveis. Os cartões da Toolbox ocupam menos altura no Android.

## Versão 0.6.4

- A Toolbox revisa Scripts, LocalScripts e ModuleScripts antes de inserir. A lista mostra o caminho, o código e os alertas. A opção Inserir sem scripts preserva os demais objetos.
- No Play, o personagem encara a escada e ganha impulso ao pular para trás. Toques acionam ClickDetector, ferramentas equipadas e botões Luau. Arrastar um ScrollingFrame move o conteúdo.
- O menu original do Play separa Retomar, Redefinir personagem e Sair. A qualidade gráfica escolhida nas Configurações fica salva para a próxima sessão.
- No editor, arrastar fora dos manipuladores gira a câmera mesmo com Mover, Girar ou Escalar selecionado. O analógico e a pinça continuam disponíveis.
- A Home baixa as imagens dos templates do Creator Hub e o avatar da conta validada. As miniaturas reduzidas dos templates ficam disponíveis offline.
- Beam e Trail leem as propriedades atuais de textura. Emissores, feixes e trilhas seguem Attachments aninhados durante o Play.
- Os modos Automático e nível 1 reduzem a resolução da cena 3D e desativam sombras no Play Android. A interface mantém a resolução da tela.

O [estado de validação](docs/ANDROID_MOBILE.md) detalha as integrações e os limites dos testes. Os inventários e relatórios das versões anteriores estão em `docs/`.

## Compilar no Linux

Use Rust 1.99.1, Python 3.12, Git, JDK 17 ou mais recente, SDK Android 35 e NDK r28c. O ZIP de código-fonte inclui os três repositórios preparados e os patches. Em um clone Git, inicialize os submódulos com `git submodule update --init --recursive`.

```bash
rustup toolchain install 1.99.1 --profile minimal
rustup target add --toolchain 1.99.1 aarch64-linux-android
export ANDROID_HOME=/caminho/para/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;28.2.13676358'

python3 scripts/prepare.py
PORT_ARCH=aarch64-linux-android python3 scripts/cargo_android.py build --locked -p rbx_android_studio -p rbx_android_player
PORT_APP=studio PORT_ARCH=aarch64-linux-android python3 scripts/package_apk.py
```

Use `PORT_DEBUGGABLE=0` junto a `PORT_APP=studio` para empacotar sem a opção de depuração do Android. O resultado está em `dist/rbx-native-android-0.6.9-arm64-v8a-debug.apk`. O empacotamento usa javac, D8, aapt, zipalign e apksigner. Para um emulador x86_64, instale o alvo Rust correspondente e use `PORT_ARCH=x86_64-linux-android`. A assinatura de atualização deve usar a mesma chave da instalação anterior.

```bash
cargo test --locked -p rbx_android_core --tests
cargo run --locked -p rbx_android_core --example studio_resources -- /pasta/temporaria --touch
```

Os pacotes de recursos externos são carregados em execução e reutilizados do cache. `brand/rbx-mark.json` é a fonte dos vetores da marca. Para editar os vetores, use `python3 scripts/generate_brand.py`.

## Créditos e licenças

- [rbx-native](https://github.com/chteau/rbx-native), `eb85a1088a69f4159598b4fed0ae26956573c175`, MIT.
- [Rook](https://github.com/AsyncFinn/rook), `8680da0fde3ded84731b7422c59de26db0e14135`, Apache-2.0.
- [gpui-mobile](https://github.com/longbridge/gpui-mobile), `0b882efdac7f524e0bb0b1d4c886b2aa752f9f20`, opção Apache-2.0.

As adaptações reproduzíveis estão nos patches em `patches/`. Os avisos das dependências estão no APK e as licenças originais acompanham os fontes.
