# Continuação RBX Studio Android — 16/11/2026

## Escopo protegido
- Repositório experimental: `Johnatafgfdgf/Rbxstudio`, branch `verify/android-menus`.
- Não aplicar estas alterações ao original `Johnatafgfdgf/Roblox-Studio-for-Android`.
- Usar o rbx-native + Rook + GPUI existente; não retirar botões, menus ou handlers para simplificar layout.
- APK experimental deve ter ID de pacote separado `com.rbxnative.androidport.experimental`.

## Falhas reais observadas
O teste Android de screenshots do commit `d9585ec` (run 43807343742) compilou o APK nativo x86_64 e iniciou o aplicativo, mas falhou em `Home did not finish opening the original editor within its startup budget`. O arquivo `after-skip.png` incluído no artefato prova que a interface ainda estava no assistente de boas-vindas: o script clicava (430,535), coordenada de um cartão informativo, não da saída. A interface tinha X em (1235,40) na geometria controlada de 1280 x 576. Além disso, o fechamento do assistente na versão Android acionava `cx.quit()` quando `on_finish` era `None`, encerrando o app em vez de abrir Home.

A compilação ARM64 `d9585ec` (run 43807343705) passou, gerou `rbx-native-android-0.6.11-arm64-v8a-experimental-debug.apk` e verificou o pacote independente, mas nenhum APK foi anexado: o glob de upload terminava em `arm64-v8a-debug.apk` e não incluía `-experimental-`. Portanto, sucesso de compilação não equivalia a arquivo disponibilizado.

## Correções commitadas
1. `57e24ed` e `3942bce`: X do onboarding Android chama `skip_key`, que pode levar à Home e preservar o fluxo existente de chave; a atualização do tamanho do hunk consolidado do patch foi necessária.
2. `aafb82f`: `scripts/studio_smoke.py` usa o X realmente observado, em vez de coordenada desatualizada.
3. `d45622f`: capturas `before-skip.png` e `after-skip.png` e comparação dos pixels do topo; falha rapidamente se onboarding não sair.
4. `2afd397`: upload do APK usando `dist/*arm64-v8a*debug.apk` e falha explícita se nenhum arquivo casar.

**Cuidado com evidência:** o build intermediário `57e24ed` apresentou erro Rust de delimitador não fechado, causado pelo hunk ainda com número original de linhas. O hunk foi ajustado em `3942bce`. Não considerar os commits posteriores validados até o CI concluir; observar o log do `rbx_studio` se persistir erro.

## Execuções principais para verificar
- APK ARM64 final (inclui correção do glob): https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/43848533749
- Emulador, screenshots reais, verificação da navegação: https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/43848418099

## Critérios para aprovação
1. `scripts/check_preparation.py` passa, inclusive paridade dos patches contra fonte limpa.
2. Compilação Rust de `rbx_studio`, Player e empacotamento ARM64 passam; artefato zip contém APK real e .sha256.
3. Captura nativa confirma X -> Home sem matar PID e o botão Novo jogo abre o editor.
4. Menu, Ferramentas, Painéis e painel IA abrem com toques no APK real. Explorer/Properties devem coexistir e arrastar a cena sem capturar o gesto.
5. Validar em POCO X6 5G com captura real, não apenas prévia HTML: botões/scroll, árvore Explorer e pastas, Play oficial, criação de experiência de teste, Toolbox, scripts Luau e teclado, movimentação 3D.
6. Integração IA atual possui painel MCP e política de autorização; **não** alegar chat interno autônomo ou publicação de animações testada sem validação real.

Próxima ação técnica após o CI: abrir `after-skip.png`, `new-place.png`, `mobile-*.png` e `mobile-menu-touches.json` dos artefatos; corrigir somente defeitos visíveis e reproduzíveis, sem substituir handlers existentes.