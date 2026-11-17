# RBX Studio Android — Fase 8: auditoria MCP de modelos já inseridos

**Código:** `android/src/com/rbxnative/androidport/StudioAiHub.java` na branch experimental `work/ai-autonomous-read-v1`. A `main` permanece intocada.

## Entrega desta etapa

A seção **Auditoria de modelo JÁ INSERIDO (MCP local)** foi acrescentada à central de IA Android. O usuário informa um caminho exato no Explorer, por exemplo `Workspace.Ponte` ou `Workspace.Cidade.Casa`, ativa uma autorização específica desligada por padrão, e toca **Auditar modelo existente pelo MCP**.

O app realiza operações reais, **somente leitura**, no datamodel `Edit` por meio da infraestrutura MCP já implementada:

1. `inspect_instance`: confere que existe **um Model não ambíguo** no caminho informado e obtém `children.totalDescendants`, a contagem de descendentes do editor.
2. `search_game_tree`: pesquisa subárvores com `path`, `max_depth=4` e limite de **100 nós por resposta**. Visita em seguida ramos marcados por `childSummary`, comparando as instâncias distintas encontradas com a contagem original. Rejeita caminhos fora do modelo, repetidos na mesma resposta, não suportados, com classes inconsistentes ou árvores truncadas.
3. `script_read`: lê fontes de `Script`, `LocalScript` e `ModuleScript` com linhas numeradas, no máximo **16 scripts × 2 páginas × 120 linhas**. Confere numeração sequencial, tamanho e resposta. Uma fonte com final desconhecido, erro ou fragmento truncado torna o relatório **incompleto**.
4. Aplica localmente a função Java `sourceRiskMask` já testada na fase 7. Informa quantos scripts apresentam possíveis sinais de `loadstring`, `require(assetId)`, HTTP, ofuscação, `InsertService`, DataStore/Messaging/Teleport e mostra os caminhos (sem publicar fontes nem chaves).

O fluxo suporta no máximo **12 consultas de árvore** e é cancelável. Se o MCP responder em formato diferente, uma leitura for recusada ou a contagem do editor não bater, **não afirma que o modelo foi integralmente revisado**.

## Consentimento e privacidade

- `Autorizo leitura LOCAL da árvore e dos scripts deste modelo` inicia desativado. A análise local **não exige API Key**, não usa `requestModelWithFreeFallback`, não executa Luau e não chama escrita, importação, Toolbox externa ou publicação.
- Desativar essa permissão interrompe a inspeção atual, aborta o socket MCP e descarta o relatório disponibilizado para envio.
- O relatório fica apenas na tela por padrão. Há um controle separado, também **desativado**, para **Enviar somente o RELATÓRIO desta auditoria à IA (sem código-fonte)**. Esse envio só ocorre numa mensagem normal da IA quando **também** estiver marcada a permissão de envio do contexto do projeto.
- Desmarcar o envio do relatório limpa o histórico de mensagens para impedir que conteúdo autorizado anteriormente seja reenviado inadvertidamente.
- O relatório pode expor **nomes e caminhos dos objetos**; eles só são enviados ao provedor se o usuário marcar o compartilhamento. O código-fonte lido por esta auditoria não entra nesse relatório.
- O recurso não armazena uma cópia dos scripts no Keystore nem fornece permissões adicionais de edição ao agente. Qualquer edição posterior continua no fluxo anterior de **proposta, prévia, autorização e leitura de confirmação MCP**.

## Limitações técnicas (não ocultar na interface)

- A auditoria percorre somente um Model existente sob `Workspace`. Caminhos complexos com caracteres fora do alfabeto, números, espaços, hífen e sublinhado podem ser rejeitados; objetos homônimos tornam o caminho ambíguo.
- O MCP devolve uma resposta limitada a 12.000 caracteres e pesquisa no máximo 100 nós por vez. Modelos maiores ou muito aninhados podem ficar **incompletos** mesmo que contenham scripts analisáveis.
- Fontes com mais de 240 linhas, muito grandes ou com resposta malformada são **parcialmente inspecionadas**. As categorias detectadas se referem somente ao código efetivamente lido.
- **Não resolve dependências transitivas fora do Model** (p. ex., módulos em ReplicatedStorage ou assets acessados via require), não identifica todas as formas de ofuscação e não executa testes Play.
- Encontrar um padrão **não prova malware**; não encontrar padrões **não certifica segurança**. A contagem de objetos do Explorer pode mudar enquanto o usuário edita o mapa; discrepâncias fazem a auditoria falhar de maneira conservadora.

## Verificação

- `scripts/test_inserted_model_audit_java.py`: compila e executa **os métodos Java de produção** que validam caminhos de Model e decodificam linhas MCP (incluindo páginas não sequenciais ou truncadas), além de verificar que a análise de risco reconhece `require(ID)`.
- `scripts/test_toolbox_source_scanner.py` mantém o teste anterior de categorias de sinais; `scripts/check_ai_hub.py` garante estaticamente os limites, permissões explícitas e ausência de ferramentas de escrita na auditoria.
- O workflow `.github/workflows/ai-hub-android-java.yml` executa ambas as suítes e compila a Activity Android.
- **Ainda faltam testes com Android físico e Model real aberto no editor**, incluindo um Model com nomes duplicados, com ramos maiores que o limite, scripts vazios e cancelamento durante leitura. A aprovação de CI não comprova esse funcionamento completo.

## Próxima etapa

Com testes reais de auditoria concluídos, priorizar inspeção prévia de assets por ID, identidade única de projeto e testes Play efetivos. A importação deve permanecer uma operação separada, autorizada pelo usuário e seguida de nova inspeção do conteúdo inserido.
