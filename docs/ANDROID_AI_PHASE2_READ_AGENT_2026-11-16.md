# RBX Studio Android — IA: leitura autônoma controlada (16/11/2026)

**Branch experimental:** `work/ai-autonomous-read-v1` do repositório `Johnatafgfdgf/Rbxstudio`.
Base: `verify/android-menus` no commit `dde238cfc2abaeec0354a1c5bda0ce2b53f5e5f4`. Não altera o projeto original nem o motor Rook.

## Alterações de código

- `StudioAiHub.java` oferece o checkbox **Agente: analisar objetos e scripts por nome**. Ele começa **desativado** e exige que o usuário ative o servidor MCP e marque a autorização de envio do contexto.
- O modelo pode escolher entre concluir ou usar quatro ferramentas MCP de leitura validadas contra os schemas do rbx-native: `search_game_tree` (árvore, profundidade 1–4, nome/caminho), `inspect_instance` (propriedades), `script_search` (localizar nomes de scripts) e `get_studio_state` (estado atual).
- Cada solicitação faz uma chamada **real** ao MCP local autenticado (`127.0.0.1`). Não há mock de leitura, nem ferramentas mutáveis acessíveis neste ciclo. Argumentos livres são limitados em tamanho e rejeitam controles.
- O agente recebe o resultado da árvore e pode utilizá-lo na próxima resposta. São no máximo duas leituras e três chamadas ao provedor por pedido; a conclusão informa o número de leituras realizadas.
- O modelo nunca recebe o token da conexão MCP ou a API Key. Os resultados de leitura são tratados como dados não confiáveis; os argumentos aceitos são delimitados pelo código, não pela resposta da IA.
- O botão **Cancelar análise** interrompe a continuidade e impede que resultados atrasados apareçam na tela. A chamada HTTP ativa pode terminar pelo timeout configurado; cancelar não promete reverter cobrança de chamada já iniciada.
- `inspectProject` reutiliza o mesmo leitor MCP, com limite de 80.000 caracteres para a resposta e 12.000 caracteres de árvore enviada ao chat.
- `scripts/check_ai_hub.py` verifica presença de consentimento, lista fechada, limites e ausência de execução automática de edição.
- Workflows adicionaram a branch experimental aos testes Java, build ARM64 e exportação completa dos fontes.

## Testes necessários

1. GitHub Actions `ai-hub-android-java.yml`: contratos estáticos + compilação com SDK Android 35.
2. `experimental-arm64-apk.yml`: Rust ARM64, patches, empacotamento e inspeção do DEX, APK assinado de teste.
3. `export-ai-full-source.yml`: arquivo de código-fonte com dependências e licenças.
4. Android físico: ler um projeto aberto com MCP ativado, negar consentimento e verificar bloqueio, cancelar durante execução, receber JSON válido e inválido, alternar provedores, testar teclado e fluxo após rotação, medir lag e bateria.
5. APIs: testar cada provedor somente com credencial própria e autorização de uso; verificar limites e custo de até três requisições na atividade de investigação.

**Não marcar nenhum teste como aprovado até o resultado existir.**

## Limites intencionais

A etapa **não** executa `multi_edit`, `execute_luau`, `insert_asset`, `save_place`, `publish_animation`, animações, Play ou publicações. Essas operações continuam sob a política MCP existente e exigem fluxo seguro de autorização e testes de ponta a ponta antes de receberem autonomia. O agente pode concluir sem consultar a ferramenta; nesse caso, a resposta identifica explicitamente zero consultas reais.

O chat ainda não é flutuante, não oferece especialistas independentes nem memória persistente. O objetivo desta entrega incremental é ligar **quatro ferramentas de leitura verificáveis** ao modelo com consentimento, orçamento de chamadas e cancelamento, sem degradar o editor.
