# RBX Studio — Fase 16: avaliação visual multimodal com autorização

Branch: work/ai-autonomous-read-v1. A main não foi modificada.

## Implementado de verdade nesta etapa

A Activity StudioAiHub ganhou um botão para analisar a miniatura selecionada
com a IA do provedor já configurado (OpenAI, Gemini, Claude ou OpenRouter).
O botão permanece desabilitado sem uma imagem local válida. O usuário confirma
**cada envio**, com aviso do provedor/modelo, possível cobrança e conteúdo
enviado: uma miniatura JPEG de até 384px sobre fundo escuro, máx. 64 KiB.
Áudio e arquivo original não são enviados. Nenhum dado do Explorer,
scripts, credenciais, Output ou histórico da conversa acompanha a imagem.

StudioVisualReview.java monta a requisição JSON multimodal por API:
- OpenAI Responses: blocos input_text + input_image (data URL, detail low).
- Gemini generateContent: parts text + inline_data (image/jpeg).
- Claude Messages: content text + image.source base64.
- OpenRouter Chat Completions: text + image_url (data URL).

Nenhum modelo é assumido compatível com visão só por aparecer no catálogo;
modelos que não aceitam imagem podem retornar erro. A chamada usa a URL do
provedor já selecionado, chave do Keystore e transporte HTTP existente,
sem redirecionamentos e com o limite anterior de 128 KiB por corpo.
**Não** utiliza troca de modelo de reserva nem tenta outro provedor
automaticamente. A chamada é única após a confirmação.

O retorno da API é exibido em painel separado, sem inserção em histórico
persistente, memória de projeto, MCP ou código Luau. Prompt fixo pede
observações ancoradas em regiões da imagem, prioridades concretas,
incertezas e correções sugeridas. A resposta é explicitamente identificada
como análise de IMAGEM ESTÁTICA, incapaz de provar som, animações,
colisões, desempenho ou execução no Play. Nenhuma correção é aplicada
automaticamente.

## Privacidade, cancelamento e limitações

As imagens ficam somente em memória enquanto a Activity está aberta.
A cópia usada pelo pedido é reciclada no Android ao terminar.
O pedido só é preparado depois de tocar "Enviar uma miniatura" no
diálogo modal. O controle "Cancelar análise" revoga a época local e
interrompe a conexão ativa, embora uma chamada já enviada possa ter
sido recebida/cobrada. O original não é publicado ou enviado à IA.

A implementação **não** captura automaticamente o jogo nem inspeciona
modelos de animação em execução. Ainda faltam testes no Android físico
com chave de API real e modelo de visão habilitado, verificação de
qualidade da imagem enviada, limites de custo efetivos e comparação
visual antes/depois de edições no mapa.

## Continuação — comparação antes/depois

A central de IA agora permite selecionar uma imagem, guardá-la como
**ANTES** apenas na memória do aplicativo e selecionar outra como
**DEPOIS**. Os dois arquivos são lidos pelo seletor Android, não são
extraídos automaticamente do jogo nem publicados.

O botão de comparação local mede diferenças reais após normalizar
as duas miniaturas para uma amostra RGBA de 160×160: percentual de
pixels diferentes (limiar de 30 pontos em algum canal), diferença RGB
média, variação de luminosidade e região com maior concentração de
mudanças. Proporções diferentes são rejeitadas para evitar comparar
capturas incompatíveis. Isso NÃO é uma avaliação de beleza ou de FPS,
apenas diferença técnica de imagem.

Uma segunda opção pede autorização explícita para enviar **somente
duas miniaturas reduzidas** ao modelo escolhido. Cada uma tem no máximo
40 KiB; as requisições de dois arquivos também têm limite total de
120 KiB, menor que o limite de 128 KiB do transporte HTTP existente.
O formato correto é montado para OpenAI Responses, Gemini, Claude
Messages ou OpenRouter e o prompt marca a primeira como ANTES e a
segunda como DEPOIS. O modelo recebe nenhuma informação do
Explorer, scripts, Output, credenciais ou histórico; não existe
troca automática de modelo, publicação nem alteração do jogo.

As cópias usadas no pedido são descartadas ao finalizar. A imagem
ANTES permanece em memória até ser substituída ou até o fechamento
da Activity. A comparação ainda não comprova que um efeito funciona
durante o jogo, nem registra o estado do mundo automaticamente.

## Testes

scripts/test_ai_visual_review_java.py compila e executa a classe
StudioVisualReview.java de produção com stubs estruturais org.json.
Confere todos os quatro formatos multimodais, rejeição de modelos/
provedores inválidos e de JPEGs fora do limite, e guardas da Activity.
O workflow ai-hub-android-java.yml também compila a Activity e
a classe nova usando Android SDK 35. O APK ARM64 é compilado
separadamente na workflow experimental-arm64-apk.yml.

## Mapa de diferenças visual (diagnóstico local)

A comparação local agora mostra, além de percentuais, um **mapa de diferenças
160×160 criado dos pixels reais das duas prévias**. O mapa conserva uma base
cinza para pixels que não cruzaram o limiar de 30/255 e marca em
amarelo/laranja/vermelho alterações de magnitude crescente. O mapa
não é uma terceira captura, não substitui as imagens reais e não mede
beleza, performance, execução de scripts, animações ou som.

Ao escolher um novo arquivo ou importar outra captura do Rook,
o mapa anterior é ocultado e o Bitmap antigo é liberado, evitando
que uma comparação antiga pareça pertencer à imagem atual. O botão
**Guardar imagem como ANTES** também descarta o mapa obsoleto.
Os testes Java de produção verificam regiões alteradas, cores do mapa,
regiões inalteradas, pixels transparentes e tamanhos inválidos.
