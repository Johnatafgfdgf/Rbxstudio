# Fase 16 — Prévia local de recursos, screenshots e texturas VFX

**Branch:** \`work/ai-autonomous-read-v1\`. Não substitui editor, Toolbox ou
a integração MCP atual. A \`main\` permanece intocada.

## O que foi implementado

A central de IA Android ganhou **Prévia real • VFX, imagens, sons e
screenshots**, com acesso opt-in pelo seletor oficial de documentos
(Android Storage Access Framework). O usuário escolhe um arquivo;
a Activity não varre pastas nem baixa conteúdo automaticamente.

O novo \`StudioAssetInspector.java\` é produção Java independente de Android
e verifica a assinatura real dos bytes, não a extensão indicada pelo
provedor. Aceita PNG/JPEG/WEBP/GIF (até 8 MiB) e
WAV/OGG/MP3/FLAC (até 16 MiB). Rejeita conteúdo desconhecido,
SVG/HTML/ZIP, executáveis e arquivos além do limite. Calcula SHA-256
e tamanho para identificação/deduplicação futura.

Para imagens reais, a Activity decodifica as dimensões via
\`BitmapFactory.Options.inJustDecodeBounds\`, recusa dimensões
acima de 8192 e apresenta miniatura com \`inSampleSize\`.
Um analisador técnico, sem IA, observa pixels efetivos, transparência,
predominância de escuro/brilho e variação de luminância local na
miniatura reduzida. Pixels transparentes não contam como "escuros".
É um passo concreto para a análise de **capturas visuais verdadeiras**
e **sprites VFX**. O relatório não classifica a beleza nem afirma
que o efeito funciona no motor.

O resultado permanece em memória e na interface, sem cópia persistente,
sem upload para uma IA, sem extração de scripts, sem implantação no Roblox,
sem solicitação de publicação ou simulação de Asset ID.
Também não há pesquisa autônoma em repositórios nem análise de som:
o áudio só é identificado tecnicamente e não é executado.

## Testes

\`scripts/test_ai_asset_inspector.py\` compila **a classe Java de produção**
com \`javac --release 11\` e testa PNG, WAV, hash, rejeição de SVG/ZIP,
limite de arquivo, amostras transparentes/claras/escuras e contagens inválidas.
\`.github/workflows/ai-hub-android-java.yml\` inclui teste e compilação da
nova classe no Android SDK 35. \`scripts/package_apk.py\` já inclui automaticamente
todos os arquivos Java do diretório Android na compilação do APK.

## Próximos trabalhos reais

- Captura do viewport/Play com permissão Android apropriada e prova de
  que os frames são realmente os da cena (a seleção de arquivo ainda é manual).
- Análise semântica opt-in pelo modelo com capacidade multimodal: pedir
  permissão explícita por imagem, exibir quais dados serão enviados, respeitar
  limites/custos e verificar se a API selecionada aceita imagens.
- Catálogo de recursos com procedência/licenças verificáveis e busca em
  repositórios autorizados (VFX, SFX, animações, modelos, câmeras).
- Importação e publicação Roblox Open Cloud somente após autorização, com
  verificação do ID retornado e teste real dos objetos/efeitos no Play.
- Relatórios de avaliação visual e tentativa de correção que comparem
  estado anterior e posterior; nenhuma alegação de beleza ou correção sem provas.

## Ajuste de memória e distribuição

Arquivos de áudio são agora identificados pelo cabeçalho e processados em
streaming até o limite; apenas o hash/tamanho ficam na estrutura de resultado.
As imagens ainda podem ser armazenadas temporariamente até 8 MiB para produzir
miniatura, sem enviar bytes à rede. O workflow de exportação foi ajustado para
incluir mudanças futuras neste inspetor e gerar um ZIP do commit exato.


## Continuação incremental — detalhes técnicos reais de WAV

A inspeção local agora usa até **64 KiB do cabeçalho**, enquanto calcula o
SHA-256 de todo o áudio por streaming (máximo original de 16 MiB).
Para arquivos RIFF/WAVE PCM ou IEEE float coerentes, lê de verdade os chunks
`fmt ` e `data`, validando limites RIFF, canal, frequência, taxa de bytes,
alinhamento e bits por amostra. Exibe canais, Hz, bits e **duração estimada**
a partir do tamanho declarado de `data` e da taxa de bytes. Dados
truncados, taxas inconsistentes e codecs desconhecidos **não** produzem
uma duração inventada; o relatório informa a limitação.

O caminho do seletor Android, a prévia de imagem, as permissões, a rede e o
Play permanecem intactos. O arquivo de áudio não é decodificado, reproduzido,
armazenado, publicado, nem transmitido para a IA.

Regressão em `scripts/test_ai_asset_inspector.py`: WAV PCM mono de um
segundo, leitura em blocos de um único byte, formato incompleto, RIFF
truncado e taxa de bytes inconsistente. O workflow Java existente executa
o teste e compila a classe de produção. Aprovação na CI **não substitui**
a inspeção em dispositivo Android real.
