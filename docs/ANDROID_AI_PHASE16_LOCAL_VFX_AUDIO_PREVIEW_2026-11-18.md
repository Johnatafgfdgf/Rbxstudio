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

## Continuação — catálogo pesquisável de recursos externos (Poly Haven)

O AI Hub oferece agora busca **real**, iniciada por toque, na API pública
da [Poly Haven](https://polyhaven.com/our-api). O usuário digita a descrição
e escolhe entre texturas, modelos 3D ou ambientes HDRI. A classe
`StudioPolyHavenCatalog.java` chama o endpoint oficial
`https://api.polyhaven.com/search` com a consulta codificada na URL, limite
de oito resultados, timeout de 7/9 segundos, máximo de 256 KiB de resposta,
sem redirects e com User-Agent identificável. Em caso de erro, não inventa
resultados. O aplicativo só recebe slugs da API e só permite abrir páginas
`https://polyhaven.com/a/<slug>` após toque; links externos recebidos da
rede não são executados. O usuário pode inspecionar formatos e licença na
página original e selecionar manualmente uma imagem/áudio na prévia existente.

Atribuição `Powered by Poly Haven` aparece no app, conforme os termos da
API. Os assets do catálogo Poly Haven são anunciados como CC0 pelo
fornecedor, mas o app **não** certifica a licença de arquivos de terceiros
escolhidos manualmente. Somente o texto digitado e o tipo selecionado são
enviados à API: nenhuma chave de IA, script, arquivo local, nome de projeto,
ou conteúdo do Explorer/MCP é transmitido. O sistema não baixa, insere,
executa ou publica assets nem declara modelos HDRI compatíveis com Roblox.

A automação não faz buscas invisíveis de fundo; o usuário precisa tocar
**Buscar agora na Poly Haven**. A busca não oferece SFX/animações e não
converte/instala formatos 3D, já que essa biblioteca trabalha com texturas,
modelos e HDRIs. Próximas integrações deverão verificar arquivos e formatos
com provas antes de inserir e publicar, sempre com consentimento.

Teste adicionado: `scripts/test_ai_polyhaven_catalog_java.py` compila
a classe Java de produção e exercita encoding UTF-8, categorias,
rejeição de caracteres de controle, slugs e prevenção de links arbitrários.
Também verifica a conexão do botão à busca real e a seleção segura do
resultado. A compilação Android Java do workflow agora inclui a nova
classe. Esses testes não equivalem a validar rede, layout e navegador
no celular físico.



## Continuação da fase 16 — ficha verificada de preparação de assets

Depois da busca por descrição na Poly Haven, cada item encontrado oferece
**Ver dados e formatos**. O usuário precisa tocar nesse botão. O aplicativo
consulta diretamente os endpoints oficiais, com slug validado:

- `GET /info/{slug}` — nome, descrição, categoria e publicação;
- `GET /files/{slug}` — formatos informados para um asset publicado.

**Correção de integração:** os caminhos `/v2/...` e o campo de resposta
`data` empregados na primeira versão eram incompatíveis com o contrato
oficial. A implementação foi corrigida para esses dois endpoints
e objetos JSON diretos (sem envelope `data`). Referência:
https://github.com/Poly-Haven/Public-API/blob/master/swagger.yml.

Os dois pedidos são HTTPS GET para `api.polyhaven.com`, com cabeçalhos
fixos, sem seguir redirecionamentos, timeout e máximo de 512 KiB cada.
URLs de arquivos contidas no JSON são tratadas como **dados**, não são
seguidas nem abertas. A tela mostra apenas formatos conhecidos que
apareçam em entradas de arquivo com metadados de URL e tamanho, limitando
a inspeção da árvore JSON. Se a lista de arquivos falha, a ficha deixa
isso explícito e preserva apenas os dados que a API confirmou.

O resultado informa quais passos de compatibilidade ainda seriam
necessários. PNG/JPG de texturas são **candidatos** para uma futura
importação, nunca prova de que um asset Roblox já existe. Modelos 3D
externos e HDRIs ainda carecem de conversão, validação no editor e
publicação autorizada. O botão não baixa bytes dos arquivos, não cria
instâncias, não transforma formatos, não envia dados ao modelo de IA e
não inventa Asset IDs. Nenhuma chave de API ou dado do projeto acompanha
a consulta.

**Validação automatizada:** o teste de produção
`scripts/test_ai_polyhaven_catalog_java.py` inclui URLs fixas de detalhes,
validação de slug, enumeração de formatos permitidos e contratos
estáticos da interface Android. O workflow
`ai-hub-android-java.yml` também compila a classe e a Activity Android
com o SDK 35. Aprovado no workflow
https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/44245178637
para o commit `3418285f9b38a9ee1fb602fbcad5eaab41c25786`.
Os testes não fazem requisições ao serviço em um celular real e não
comprovam download/importação/publicação/qualidade visual no jogo.

**Trabalho pendente:** validação no Android físico da busca e ficha com
internet real; escolha de arquivos individuais com hash/limite e
licenças; conversor de formato realmente compatível; importação em
cenário de teste, leitura de volta via MCP, revisão visual e, separadamente,
publicação mediante confirmação e ID real retornado.


## Continuação — comparação de bytes de arquivos selecionados pelo usuário

Para texturas encontradas na Poly Haven, depois de clicar em
**Ver dados e formatos**, a Activity lê a árvore real de `/files/{slug}`
sem seguir URLs. São extraídos apenas candidatos PNG/JPG de até
**8 MiB**, com URL de origem permitida, identificador válido, tamanho
positivo e hash MD5 publicado na ficha. O painel apresenta até seis
variantes, ordenadas por menor tamanho para reduzir impacto no celular.

Ao tocar em **Conferir localmente**, um diálogo explica o que será lido e
o usuário seleciona, usando o seletor de documentos oficial do Android,
um arquivo já salvo no aparelho. O código de produção
`StudioPolyHavenIntegrity.java` lê esse arquivo em streaming, sem
manter sua cópia. Ele confirma a assinatura inicial PNG/JPG,
o tamanho exato, compara o MD5 com o valor publicado no catálogo e
calcula o SHA-256 do arquivo local para identificação.
Falhas de tamanho, formato, hash ou arquivo ausente não são tratadas
como verificações bem-sucedidas.

**Limitação de confiança:** MD5 não é resistente a colisões, portanto
a correspondência é uma verificação de integridade simples contra
o catálogo recebido, e não prova criptográfica forte da autoria ou
ausência de conteúdo malicioso. Também não significa que a textura
seja bonita, decodificável no Roblox ou funcional no jogo. A
inspeção **não importa assets, não cria IDs, não publica, não envia
arquivos à IA e não altera o mapa**. Não há download automático.

O teste `scripts/test_ai_polyhaven_integrity_java.py` exercita
diretamente os métodos Java de produção com arquivos em memória,
leituras em segmentos de um byte, arquivos alterados, truncados,
excedentes, assinaturas incorretas e origens suspeitas.
O workflow de testes compila a nova classe com o AI Hub
no Android SDK 35. A verificação em Android físico com arquivos reais
ainda é pendente; nenhum resultado será anunciado sem evidências.

Próxima integração: autorização de importação de arquivo cuja
compatibilidade esteja comprovada, inserção real no projeto e
read-back do editor, com rollback local e publicação separada.


## Continuação — Roblox Open Cloud decal via consentimento separado

Novo cliente de produção `StudioRobloxImageUpload.java` implementa
o endpoint **POST https://apis.roblox.com/assets/v1/assets** da API
oficial Roblox Open Cloud, com `multipart/form-data`, campo `request`
para os metadados, campo `fileContent` para os bytes PNG/JPG,
`x-api-key` de autorização e `assetType=Decal`. O endpoint GET
`/assets/v1/operations/{id}` verifica posteriormente o status real.
Especificação:
https://create.roblox.com/docs/cloud/guides/usage-assets

O formulário opcional fica abaixo da verificação de um arquivo local
na tela já existente do AI Hub, **sem trocar a interface original**.
O usuário informa seu Roblox User ID, um nome simples para o decal
e uma chave Open Cloud com escopo autorizado. A chave de **Roblox**
não é a mesma chave de OpenAI/Gemini e **não é gravada** na memória
persistente ou no projeto. O usuário deve obter autorização legítima
para os recursos de upload da própria conta. O aplicativo não solicita
ou contorna verificações de idade, moderadores, quotas ou permissões.

Para liberar o envio, uma textura PNG/JPG precisa ter sido
**selecionada manualmente** pelo seletor Android e corresponder ao
tamanho, à assinatura e ao MD5 informado pela Poly Haven.
O app solicita então uma **segunda confirmação** de upload, distinta
da autorização de inspeção local. No momento exato da operação,
`readVerifiedBytes` relê todo o arquivo pelo URI e verifica novamente
tamanho, assinatura e checksum; também valida as dimensões decodificadas
como estritamente menores que 8000 × 8000, antes de uma única requisição
HTTPS de envio. Nenhum upload é disparado por modelos de IA,
Toolbox ou buscas automáticas.

Uma resposta de criação bem-sucedida retorna o caminho de uma
**operação**, não um Asset ID presumido. O usuário consulta
explicitamente o estado por um segundo botão. O aplicativo só mostra
`rbxassetid://<ID>` quando a resposta **real da Open Cloud** marca
a operação como concluída e confirma o tipo de asset `DECAL`,
exibindo também o estado de moderação. Se o pedido falhar ou perder
a resposta, o relatório diz que a criação ficou **incerta** e
recomenda consultar o Creator Dashboard antes de qualquer nova
tentativa, para evitar duplicações. Não há repetição automática.

Isso cria um **decal na conta Roblox** somente com chave válida,
permissão real e consentimento no celular. **Não publica o jogo
nem altera o mapa**: usar o asset ID em uma Part/Decal do Explorer
será um passo adicional, com validação do destino e recibo MCP,
sem associar falsamente IDs de assets a arquivos ou publicações.

Teste criado: `scripts/test_ai_roblox_decal_upload_java.py`.
Ele compila as classes Java de produção e valida o formato
da requisição, limites, ID e nome de criador, rejeição de valores
malformados, geração de URLs de operação restritas e revalidação
de bytes alterados. O workflow Java Android também compila
`StudioRobloxImageUpload.java` e o AI Hub completo.
CI validada em
https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/44248216224.
Esse teste **não faz um upload real nem prova a autenticação,
moderação ou a execução no Android físico**, que ainda precisam
ser verificados por quem tenha uma conta e chave autorizadas.


## Continuação — Decal confirmado aplicado a uma Part via MCP

Novo módulo de produção: StudioRobloxDecalPlacement.java.

O ID do decal não é digitado livremente para esta operação: o AI Hub
só aceita o assetId numérico obtido de uma operação da própria
Roblox Open Cloud após done=true, com resposta de tipo ASSET_TYPE_DECAL.
O usuário pode então preparar separadamente uma aplicação a uma
Part por MCP, sem publicar o mapa.

A interface móvel acrescenta um campo para um destino exato
Workspace.Parede ou Workspace.Casa.Parede, um nome de Decal simples,
o seletor de Face (Front, Back, Top, Bottom, Left, Right)
e dois botões distintos para inspecionar e aplicar.
Não usa scripts ou texto Luau da IA nem URLs escolhidas pelo modelo.

Fluxo técnico de segurança:

1. O usuário pressiona Inspecionar Part e preparar aplicação.
   Uma chamada real inspect_instance via MCP deve retornar uma única
   instância Part, no caminho correto e com lista completa dos filhos.
   A inspeção rejeita objeto homônimo e exige uniqueId não vazio.
2. O aplicativo mostra uma prévia textual do caminho, nome, Face e
   rbxassetid://ID, ainda sem escrever no editor.
3. Ao tocar Revisar e aplicar, o app confere que os dados
   continuam iguais aos previamente inspecionados e
   pede aprovação explícita num diálogo independente.
4. Após aprovação, o app lê inspect_instance novamente.
   Se a Part foi substituída (uniqueId diferente), ou já
   existe um objeto homônimo, recusa a operação.
5. Um gerador Java fixo cria o comando Luau de Instance.new("Decal"),
   define nome, imagem e face e o insere somente na Part aprovada.
   O comando passa pelo MCP local execute_luau com DataModel Edit;
   o retorno precisa ser exatamente CREATED_DECAL:Nome, sem
   aceitar sucesso genérico ou retorno de colisão como prova.
6. Outra consulta MCP, independente, só considera o resultado
   verificado quando o novo objeto corresponde ao caminho, nome,
   classe Decal, Face e URI da textura esperados. Caso a leitura
   falhe, o relatório informa que a alteração ficou incerta,
   orientando a verificar o Explorer/Output e evitando reenvio
   automático do comando.

Compatibilidade: a implementação inicial usa a propriedade legada
Decal.Texture, para compatibilidade com o editor existente.
O Roblox atualmente prioriza Decal.ColorMapContent, enquanto
Texture está depreciada:
https://create.roblox.com/docs/reference/engine/classes/Decal
Uma migração futura depende de confirmar como o MCP e o
engine local lêem a propriedade do tipo Content.

Limitações: a API remota pode confirmar um ID que ainda
não esteja disponível em todos os jogos, por permissão ou moderação.
A criação pelo MCP não prova que a imagem foi renderizada no Play.
Não há publicação do jogo, alteração de scripts, nem undo
automático. A opção Desfazer do editor continua relevante.

Teste novo scripts/test_ai_roblox_decal_placement_java.py compila
a classe Java de produção, exerce Face, caminhos, Asset IDs e
validação de nomes, confirma que só gera Luau de Decal
com escopo fechado e que a UI exige inspeção, confirmação
e leitura MCP pós-escrita. O workflow Java compila essa classe
junto do AI Hub Android e executa o teste. Testes no aparelho
físico com uma Part real e permissão MCP ainda pendem.


## Continuação — diagnóstico de carregamento do Decal no Play local

Foi acrescentada uma etapa opcional e exclusivamente **somente leitura**,
que aproveita o próprio MCP do editor para avaliar mensagens reais
relacionadas ao **Asset ID do Decal que acabou de ser aplicado**.
Classes de produção: `StudioDecalPlayEvidence.java` e `StudioAiHub.java`.

- O botão **Conferir erros do Decal no Play local** fica desativado
  até que a criação do Decal tenha uma confirmação MCP do objeto,
  classe, Face e URI de textura. É necessária uma autorização
  adicional: **Autorizo ler o estado/Output local para ESTE diagnóstico**,
  inicialmente desativada.
- O usuário abre o Play **manualmente** no editor. O aplicativo consulta
  `get_studio_state`. Somente se a sessão real **Client/Server**
  for identificada pelo contrato MCP, consulta `get_console_output`.
  Não inicia ou interrompe Play, não executa Luau, não chama
  qualquer IA, não captura imagens e não modifica o mapa.
- O analisador Java examina no máximo **12.000 caracteres** do Output,
  associa linhas apenas quando aparece o **Asset ID numérico exato**
  (evitando confusão com IDs que têm o mesmo prefixo/sufixo)
  e mostra no máximo quatro trechos de 180 caracteres.
  Mensagens como `Failed to load`, `permission denied` e
  `content blocked` são destacadas como **possíveis** falhas.
  Uma menção a *moderation approved* não deve disparar falso alerta.
- Sem Play confirmado, sem linhas para o ID ou sem resposta do MCP,
  o resultado é **inconclusivo**. Mesmo com ausência de erros,
  não há alegação de que a textura apareceu ou está correta.
  A imagem ainda deve ser conferida com a captura PixelCopy
  do **Play Rook local**, mediante autorização separada,
  e revisão visual real; PixelCopy sozinho também pode copiar
  um frame em carregamento. A detecção não confunde o
  editor local com o aplicativo Roblox externo.
- Se a autorização é revogada durante este diagnóstico,
  somente a leitura atual é cancelada; a revogação não cancela
  uploads ou edições independentes. Não se armazena o Output
  persistente nem se envia o Output ao provedor de IA.

**Teste:** `scripts/test_ai_decal_play_evidence_java.py` compila
e executa os métodos Java reais em cenários com Play/Edit,
falsos positivos por outros IDs, mensagens de carregamento,
moderação aprovada, limites de tamanho e resultado inconclusivo.
O workflow Java também compila as classes junto do AI Hub.
Ainda não foi feito um teste com um Decal real no aparelho:
o teste automatizado não comprova renderização, aparência
nem disponibilidade de assets publicados no Roblox.


## Continuação — revisão visual contextualizada do Decal após PixelCopy

Novo módulo Java de produção: `StudioDecalVisualReview.java`.

Após a criação de um Decal na Part (com recibo MCP e uma consulta
independente que confirmou classe, nome, caminho, textura e Face), o
aplicativo guarda **somente nesta Activity** a identidade da proposta
e o instante da confirmação. Ao importar uma captura REAL PixelCopy do
Play Rook, o app usa `capturedAt` obtido do recibo com integridade conferida por
SHA-256 e conferido por `StudioPlayFrameRules.validReceipt`
(sem confiar na hora de importação). A captura só é considerada
**candidata a revisão daquele Decal** quando foi registrada após a
confirmação MCP, em até duas horas, e o objeto, o ID e o pareamento MCP
continuam os mesmos. Importar qualquer imagem via seletor SAF
**desfaz** essa associação; imagens locais arbitrárias não podem
ser apresentadas como prova da captura Rook.

O usuário pode então tocar **Avaliar Decal na captura com IA • confirmar**.
A janela informa de forma explícita quais dados serão enviados
à **IA escolhida**: uma única miniatura JPEG de até **384 px /
64 KiB**, o nome/caminho da Part/Decal, o Asset ID e a face do
Decal. A imagem também pode conter nomes e outras informações
visíveis na cena. **Nenhum script, Output, arquivo original ou
histórico do projeto é anexado.** A chave de API é lida do
Keystore somente no instante de envio. Há **UMA solicitação** ao
provedor selecionado, sem fallback ou retries automáticos.

O pedido visual é escrito pelo próprio aplicativo, não pela IA,
por meio de `StudioDecalVisualReview.buildRequest`, que reaproveita
os contratos existentes de OpenAI, Gemini, Claude e OpenRouter.
O contexto restringe a análise a elementos observáveis:
posição/recorte/face/textura/legibilidade, com problemas localizados
e testes de correção verificáveis; também obriga o modelo a
**reconhecer quando não pode identificar a Part** no screenshot.
Não cria nem edita objetos e não dispara correções sem a
autorização específica do editor.

**Limites importantes:** a hora posterior de captura não comprova
que a câmera estava apontada para a Part, que o mesmo mapa estava
aberto ou que o asset ficou visível. A imagem é uma evidência
fotográfica real do Play Rook, não prova da renderização do Decal
específico. A avaliação da IA pode estar incorreta e a captura
única não comprova funcionamento de VFX em movimento, física, FPS,
áudio ou carregamento em todos os dispositivos. A seleção de região
da imagem, repetição de capturas e eventual automação de correção
exigem trabalhos separados e novos critérios de aprovação.

**Regressão:** `scripts/test_ai_decal_visual_review_java.py`
compila os métodos Java reais, rejeita capturas anteriores ou
não associadas ao Decal e confere os quatro formatos de payload
para imagens/descrição com prompt restrito. O workflow Java Android
compila as classes e a Activity. Sem um Play real em um aparelho
com um Decal publicado, a validação continua limitada ao contrato
de código, não à percepção visual efetiva.
