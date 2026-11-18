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

## Testes

scripts/test_ai_visual_review_java.py compila e executa a classe
StudioVisualReview.java de produção com stubs estruturais org.json.
Confere todos os quatro formatos multimodais, rejeição de modelos/
provedores inválidos e de JPEGs fora do limite, e guardas da Activity.
O workflow ai-hub-android-java.yml também compila a Activity e
a classe nova usando Android SDK 35. O APK ARM64 é compilado
separadamente na workflow experimental-arm64-apk.yml.
