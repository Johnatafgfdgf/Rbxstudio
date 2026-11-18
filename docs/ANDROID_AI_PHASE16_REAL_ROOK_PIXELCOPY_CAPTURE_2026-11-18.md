# Fase 16 — Captura de um frame real do Play Rook Android

Branch experimental: work/ai-autonomous-read-v1. A main não é alterada.

## Por que a prévia antiga não era prova do Play

A captura preview.png de StudioPlayer.start vinha da janela do EDITOR,
antes de abrir o player, com apenas 42x24 pixels. Ela não pode provar
o que o motor Rook exibiu durante o Play. Arquivos escolhidos na
galeria também não comprovariam sua origem.

## Caminho de captura novo

1. Na central IA: Autorizar 1 captura no próximo Play Rook. Exibe
   confirmação específica e separada da autorização de usar uma IA.
2. A autorização expira em 10 minutos e é consumida ao abrir o
   próximo RookPlayerActivity; não se reaplica a sessões seguintes.
3. O usuário inicia Play LOCAL pelos controles normais do editor.
4. O player aguarda o sinal nativo ready, dispensa a tela de loading
   existente, aguarda 900 ms e exige janela válida/focada.
5. Android PixelCopy.request(getWindow(), frame, ...) copia UMA vez a
   janela do player NativeActivity. Apenas PixelCopy.SUCCESS permite
   registrar a imagem. Não captura o app Roblox externo.
6. O JPEG pequeno é mantido no diretório PRIVADO do aplicativo junto
   de metadados de origem rook_native_window_pixelcopy, token aleatório
   de sessão, ready, status, timestamp, dimensões e hash SHA-256.
7. Na central IA: Importar captura REAL. A importação valida a origem,
   validade, resultado PixelCopy, hash, dimensões e bytes JPEG.
   O arquivo privado é consumido e apagado após a leitura.
8. A imagem importada aparece na prévia existente e pode ser usada
   no ANTES/DEPOIS ou enviada à IA APÓS uma segunda autorização.
9. Apagar captura e revogar autorização permite descartar arquivos
   temporários e interromper solicitações ainda não executadas.

## Limitações e segurança

- Um frame copiado da janela real não prova que um LocalScript, Tool,
  VFX ou asset funcionou corretamente, nem fornece FPS ou animações.
- Falhas, janela sem foco, PixelCopy sem dados e consentimento expirado
  jamais viram capturas simuladas ou relatórios de sucesso.
- Os arquivos não são públicos, não entram no projeto e não são
  enviados por rede. A exclusão ocorre na importação, revogação ou
  na próxima preparação; uma imagem expirou para importação e será
  descartada quando o app consultar/limpar os dados.
- O arquivo pode conter informações visíveis na tela. Uma análise
  por IA exige aprovação separada após inspeção da miniatura.
- O Play externo no aplicativo oficial Roblox NÃO está integrado a
  essa captura. Nenhuma captura de tela do sistema é solicitada.
- A infraestrutura ainda precisa ser validada em Android real com
  a atividade Nativa Rook, especialmente GPU, foco e loading.

## Testes adicionados

StudioPlayFrameRules é produção Java independente de Android. O teste
scripts/test_ai_play_frame_java.py compila e executa os guardas reais
de prazo, formato JPEG, SHA-256, sessão, PixelCopy, ready e fonte,
incluindo rejeições explícitas de prévia do editor e de arquivos
modificados. O workflow Java compila as classes Android pelo SDK 35.

## Vinculação independente da autorização

O recibo da imagem é agora vinculado a um registro **separado** de
autorização, criado no momento em que o usuário consente. A janela Rook
confere esse registro quando consome a solicitação e quando grava a
captura. A importação exige correspondência exata do ID e dos prazos
no registro original e no recibo. A revogação apaga também o registro,
impedindo a leitura posterior mesmo que uma operação entre processos
termine tarde. A checagem SHA-256 continua verificando integridade
dos bytes, não qualidade da cena.
