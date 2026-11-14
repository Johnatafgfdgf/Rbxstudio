# Regressão de toque do RBX-Mobile

Use um emulador vazio ou um aparelho de teste com ADB autorizado. Um APK ARM64 requer Android com ARM64 nativo ou uma imagem Google APIs com tradução ARM64. Consulte `adb shell getprop ro.product.cpu.abilist`. A imagem x86_64 sem tradução requer um APK x86_64 da mesma revisão. As referências da auditoria são os APKs 0.6.7 e 0.6.8.

```sh
python3 scripts/android_ui_audit.py --apk /caminho/Rbx-Studio-0.6.7-arm64.apk --out /tmp/rbx-067
python3 scripts/android_ui_audit.py --apk /caminho/Rbx-Studio-0.6.8-arm64.apk --out /tmp/rbx-068
python3 scripts/android_ui_audit.py --apk /caminho/Rbx-Studio-0.6.9-arm64.apk --out /tmp/rbx-069
python3 -m unittest discover -s tests/android -p 'test_*.py'
```

Cada execução grava o hash do APK, ABI, densidade, captura, saída do Android e árvore do UI Automator quando disponível. `passed` corresponde à expectativa que o script verificou. `captured` indica que a etapa aguarda inspeção da captura. Use uma execução por diretório.

O roteiro também falha quando o Android registra um ANR do RBX-Mobile durante a sessão. O marcador de início no logcat delimita os eventos dessa execução. ANRs de componentes do Android ficam no log bruto para diagnóstico.

O plano `tests/android/picker-smoke-960x432.json` exercita **Use local projects**, **Open a file…** e o retorno pelo botão Voltar. Use a 0.6.8 num emulador de teste que mostre o assistente inicial, em paisagem a 960 × 432 px e 160 dpi. Confira as capturas da Home, do seletor e do retorno. A abertura do seletor por si só verifica a Activity em primeiro plano. A seleção de um RBXLX e a cena carregada exigem o ensaio de arquivos da tabela.

```sh
python3 scripts/android_ui_audit.py --apk /caminho/Rbx-Studio-0.6.8-arm64.apk \
  --plan tests/android/picker-smoke-960x432.json --out /tmp/rbx-068-picker
```

## Roteiro com resultados esperados

Use a mesma geometria nas duas versões. Repita em 432 e 360 dp de altura lógica, com teclado por software. Em um emulador de auditoria, `adb shell wm size 432x960` e `adb shell wm density 160` produzem 960 × 432 dp quando a Activity gira. Repita com `360x800` ou altere a densidade. Retorne às configurações do emulador com `wm size reset` e `wm density reset`.

| Etapa | Interação | Condição de aprovação |
|---|---|---|
| Home | Abra o APK, pule a entrada, crie Baseplate e volte após usar Home do Android | Editor original e mapa visíveis, mesma sessão depois de voltar e ausência de crash |
| Menu e rolagem | Abra File, Edit, Model e View. Role até o fim e volte ao topo | Últimos itens alcançáveis, nenhuma ação disparada pela rolagem e toque fora fecha a lista |
| Painéis | Na 0.6.9 toque em Painéis. Alterne Explorer, Propriedades e Toolbox. Expanda, recolha e feche | Uma única aba ocupa a gaveta. Estado real reflete a seleção e a cena recebe toque após fechar |
| Objetos | Insira uma Part no Model, altere Name/Anchored, duplique, exclua e desfaça | Árvore, Properties e cena refletem o mesmo objeto e o histórico reverte na ordem correta |
| Câmera | Arraste no chão, mova o analógico, faça pinça e arraste um manipulador | Câmera responde fora do manipulador; o gizmo muda a Part; nenhum movimento continua após soltar |
| Código | Insira Script, selecione e toque em Editar script. Abra Teclado e digite um marcador | Marcador aparece pelo IME, há área de edição utilizável, Painéis restaura controles e Cena mantém o script |
| Play imediato | Digite a última letra do marcador e toque em Play local em menos de 400 ms | A sessão lê o código recém-editado. A saída traz o marcador completo |
| Toolbox local | Copie a fixture `toolbox-review.rbxmx` para Downloads. Abra Toolbox → Opções → Arquivo local | Revisão mostra 1 Script, 1 LocalScript e 1 ModuleScript. Cancelar deixa o mapa igual. Inserir sem scripts conserva o Model/Folder |
| Toolbox online | Abra Loja, busque, role, alterne coleções, abra detalhes e cancele revisão | Seleção visível, erro de rede informado se ocorrer e cancelamento preserva o mapa |
| Play local | Inicie por Ferramentas → Test → Play local. Ande, salte, gire a câmera e toque em Parar | Activity Rook apresenta cena e avatar, aceita gestos e volta ao editor |
| Play oficial sem configuração | Toque em Play num perfil de teste novo. Abra Configurar teste e volte | A tela abre em Conta com o campo do lugar. Voltar retorna ao editor |
| Play oficial configurado | Em uma experiência privada descartável, autorize a chave, confira o ID na confirmação, publique e abra. Volte pelo seletor de apps | O histórico do lugar de teste mostra uma nova versão. O Roblox entra nesse lugar e o editor conserva o projeto salvo |
| Play oficial com erro | Verifique sem o Roblox instalado, com chave sem escopo, lugar público ou ID do universo vinculado | O app explica o impedimento, impede o envio e permite corrigir a configuração |
| Persistência | Salve no seletor Android, feche e reabra o RBXL | Part editada e código digitado reaparecem no novo carregamento |
| Publicação | Abra File → Publish to Roblox e cancele | A rota leva ao fluxo existente sem publicar. Ensaio real usa uma conta e um lugar de teste próprios |

Para preencher o modelo de importação no Android, use `adb push tests/android/toolbox-review.rbxmx /sdcard/Download/`. O fluxo da revisão permite observar a contagem sem rodar scripts de terceiros.

## Repetir interações

Passe `--plan caminho.json`. A lista `steps` aceita `tap`, `swipe`, `node`, `text` e `key`. Ações com coordenadas exigem `screenshot_size` conferido e o mesmo tamanho da captura mais recente. Use os pontos do seu APK após revisar as capturas. Para alvos Android, `node` aceita `text`, `description` ou `id`, exatamente um por alvo. `expect` aceita `activity`, `node` e `log`. O log precisa ter sido emitido após começar a etapa.

Em Play local, o botão nativo **Parar** pode ser procurado pela árvore Android. Exemplo de etapa depois que a Activity Rook já estiver em primeiro plano.

```json
{
  "steps": [
    {
      "name": "stop-play",
      "node": {"text": "Parar"},
      "expect": {"activity": "dev.gpui.mobile.GpuiActivity"}
    }
  ]
}
```

Use a etapa depois do toque que abriu Play local no mesmo plano. Confirme `ROOK_FIRST_FRAME_PRESENTED` e veja a captura do mapa. Para digitação real, toque nas teclas do IME. O campo `text` do plano injeta texto ASCII pelo Android e serve para exercitar o caminho de teclado físico. Faça pinça manualmente ou por injeção multiponteiro.

## Maestro, UI Automator e Ramus

- UI Automator já fornece o dump usado pelo roteiro. Use os seletores de View do sistema para SAF, teclado e superfícies nativas do player.
- Maestro usa a mesma hierarquia para seletores. O editor GPUI ainda precisa de uma ponte de acessibilidade Android para tornar os controles da superfície selecionáveis por rótulo. Use os passos por coordenadas com a geometria conferida enquanto essa ponte estiver pendente.
- A sessão Ramus requer autenticação. Use uma variante x86_64 ou confirme se a imagem da sessão anuncia ARM64. Instale o APK nessa sessão, abra o mesmo projeto de teste e compare as capturas. [Maestro Android](https://github.com/mobile-dev-inc/maestro-docs/blob/main/introduction/get-started/supported-platform/android/android-native.md) · [UI Automator](https://developer.android.com/training/testing/other-components/ui-automator) · [Ramus](https://ramus.dev/docs/quickstart).
