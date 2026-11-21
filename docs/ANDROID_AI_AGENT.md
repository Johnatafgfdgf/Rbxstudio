# Assistente IA — RBX Studio Experimental (Android)

**Projeto:** somente a branch `verify/android-menus` de `Johnatafgfdgf/Rbxstudio`. Não modificar o repositório original nem remover ferramentas para criar espaço para a IA.

## Objetivo e estado real

Reutilizar o MCP já existente em `crates/rbx_studio/src/mcp` / `shell/mcp.rs`, que acessa o **DOM real do editor** e usa **o mesmo histórico de desfazer** para alterações locais. A nova entrada **IA** na barra Android abre uma área própria: conexão, permissões, solicitações pendentes, capacidades e atividade.

**Já implementado no código (validar compilações e APIs antes de anunciar como operacional):**
- Interface Android `mobile-ai-panel` com viewport visível e ações de toque.
- Ativar/desativar o servidor MCP do editor sem o ativar automaticamente em todos os APKs.
- Mostrar apenas ID de sessão e porta; segredo de conexão não aparece na interface.
- Copiar os dados de pareamento, **incluindo credencial privada**, apenas quando o usuário tocar no botão.
- Ferramentas de leitura permitidas após conexão MCP autorizada; edições locais exigem aprovação individual até o usuário optar por liberá-las **somente nesta sessão**; operações sensíveis exigem confirmação **sempre**, independentemente da liberação.
- Recusa explícita de chamadas rejeitadas; fila e registro simples de ações sem incluir código-fonte ou credenciais.
- Contrato `ai_permissions.rs` com testes de regressão.

**Parcialmente implementado:** central de provedores Android com chat de texto por API (OpenAI, Gemini, Claude e OpenRouter), lista real de modelos e inspeção opcional do Explorer via MCP local autenticado. **Ainda não implementado:** planejamento e execução totalmente autônoma de ações do modelo, conexão remota de MCP, detecção universal de erros visuais, validação real de todas as ferramentas em aparelho físico. Uma ferramenta MCP `publish_animation` foi adicionada para upload de KeyframeSequence e IDs devolvidos pela Open Cloud, mas a **validação real de upload ainda não ocorreu**; não anunciar como confirmada.

O servidor MCP já presente aceita clientes compatíveis com o protocolo. A nova Activity Android consegue ler somente a árvore de objetos por esse servidor, desde que ele esteja ativado, e o usuário pode escolher enviar esse resumo como contexto ao modelo. **O chat novo não executa ferramentas do editor.** A assinatura ChatGPT pessoal não é automaticamente uma chave de API. Cada provedor exige uma chave própria e pode cobrar pelo uso. Veja `docs/ANDROID_AI_PHASE1_2026-11-16.md` para os recursos implementados e seus limites.

## Experiência Android planejada

Em paisagem, a área IA é uma gaveta limitada à direita, fechável, sem substituir Explorer, viewport nem Script Editor. Em retrato, oferecer uma sheet quase em tela cheia com retorno visível. Uma futura versão deve oferecer:
1. **Chat:** texto, histórico por projeto, anexos de imagem autorizados, botão parar, contexto selecionado.
2. **Contexto:** lugar ativo, seleção atual, painel Explorer, abas e scripts, avisos, informações do renderizador.
3. **Plano:** ações ordenadas, escopo do projeto, progresso, erros e tentativas. No máximo uma etapa modificadora por transação.
4. **Aprovações:** visualizar o que vai mudar, origens da Toolbox, lista de arquivos e destino de publicação; permitir uma vez, recusar ou permitir somente edições locais até fechar.
5. **Inspeção:** captura real do viewport (não mock), relatório do Output, logs de script e comportamento em Play.
6. **Histórico:** miniatura e descrição de cada alteração, desfazer/restaurar via histórico existente; backups antes de publicação.
7. **Conexões:** cliente MCP ou provedor de IA consentido, sempre revogável; segredos no armazenamento seguro do sistema. A primeira versão Android já possui a tela complementar `StudioAiHub`, mas o chat flutuante completo permanece planejado.

Não usar botão que apenas simule um resultado. Funções sem backend devem mostrar `Indisponível nesta versão` ou não aparecer até estarem implementadas.

## Capacidades e planejamento do agente

| Classe | Ferramentas já presentes | Política Android |
|---|---|---|
| Inspeção | search_game_tree, inspect_instance, script_read, script_search, script_grep, get_studio_state, screen_capture, get_console_output | Leitura após conexão autorizada |
| Edição local | multi_edit | Confirmar por padrão; cada mudança entra no Undo |
| Testes | start_stop_play, user_mouse_input, user_keyboard_input, character_navigation | Confirmar ou liberar temporariamente |
| Importação | search_asset e insert_asset | Busca pode ser livre; **inserção exige confirmação**, pois modelos podem conter scripts |
| Código arbitrário | execute_luau | Confirmar individualmente sempre |
| Salvamento e rede | save_place, upload_image | Confirmar individualmente sempre |
| Geração de assets | APIs de geração oficiais do Roblox Studio | Não disponíveis neste editor fora do serviço correspondente |
| Animações e publicação | `publish_animation` (novo MCP) usa `Client::create_animation_asset` e devolve IDs reais em caso de sucesso; não foi validado com upload real | Confirmar proprietário, escopo, custos, IDs e destino antes de publicar |

Ciclo de trabalho pretendido para tarefas como “configure todas as animações do modelo”:
1. Ler a árvore, identificar rig R6/R15, animadores, scripts, referências de animação, dependências e owner do jogo.
2. Diagnosticar as animações existentes e validar quais arquivos ou IDs estão faltando.
3. Elaborar e mostrar um plano: arquivos de animação, nomes, local de upload, mudanças Luau e testes.
4. Com aprovação explícita, serializar os assets de animação no formato Roblox suportado (`.rbxm`/`.rbxmx`) e enviar pela Open Cloud Assets API com escopo autorizado.
5. Aguardar o processamento de cada upload; extrair **IDs retornados pela API**, nunca inventá-los, e verificar a autorização de uso pelo jogo.
6. Atualizar os scripts reais como **uma alteração reversível**, usando `rbxassetid://<id>` correspondente para cada animação.
7. Executar Play, verificar Output e capturar o estado do mapa; corrigir apenas os defeitos observados e permitir reverter.
8. Exibir relatório com IDs, assets, scripts alterados e testes aprovados/falhos.

**Limitação oficial:** animações exportadas fora do Studio podem falhar no upload ou não funcionar, mesmo em formato aceito. A ferramenta deve apresentar o erro real e não marcar como concluído. Fonte: https://create.roblox.com/docs/cloud/guides/usage-assets

## Riscos, permissões e limites

- **Não** entregar acesso irrestrito sem consentimento: um arquivo da Toolbox pode incluir prompts maliciosos, scripts, conteúdo externo e dados privados.
- Separar leitura de criação/edição, execução de scripts e publicação/ações externas.
- Executar as ferramentas via UI do editor, sem expor token Open Cloud, cookie de login ou outros segredos ao LLM.
- Validar a experiência aberta e o proprietário antes de publicar, sem deixar o agente enviar assets para outro projeto inadvertidamente.
- Alterações devem estar no Undo; operações externas precisam de confirmação e comprovante de operação, porque um Undo local não desfaz publicação.
- Não aceitar nomes de ferramentas desconhecidas como leitura: classificação desconhecida = ação sensível.
- Painel IA deve ter cancelamento, conexão revogável e fila de permissões visível.
- Adicionar testes que executem um fluxo seguro real: ler Explorer -> editar script permitido -> undo -> importar modelo autorizado -> tela capturada -> Play; e testes negativos para bloqueio sem permissão.

## Regras de consistência visual

Não importar uma segunda framework visual do GitHub sobre GPUI. Usar as estruturas existentes e aplicar boas práticas de Android:
- Alvos de toque >=48 dp, sem dependência de hover ou atalhos de PC.
- Layout com largura limitada, rolagem localizada e barras que não interceptem gestos da cena.
- Rodapé e botões de autorização sempre alcançáveis; no máximo dois botões principais por seção.
- Nenhum ícone, aba ou texto deve ficar cortado, e nenhuma ação duplicada sem motivo funcional.
- Usar tokens centralizados e esquema consistente para claro/escuro, contraste legível, foco e estados desabilitados.
- Gestos de câmera e rolagem de painel devem permanecer separados; validar toque com cenas e painéis simultâneos.
- Incluir verificação visual em pelo menos dois tamanhos de telefone e nos dois temas.

Referências:
- https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-2
- https://create.roblox.com/docs/studio/mcp
- https://create.roblox.com/docs/studio/mcp-open-cloud
- https://create.roblox.com/docs/assistant/guide

## Compatibilidade e entrega

Revisar o APK Android ARM64 em dispositivo real antes de promover qualquer mudança, mantendo as compilações x86_64 de emulador como teste complementar. Não misturar o APK experimental com o pacote da versão original. Não remover funções existentes do editor para adequar a UI.

A etapa presente entrega **infraestrutura de conexão MCP + painel Android + aprovações**, uma Activity de chat por API com quatro provedores e cadastro criptografado de chaves, consulta de modelos, contexto de Explorer somente leitura e catálogo ampliado de skills. A ferramenta `publish_animation` ainda não teve upload real validado. Um agente **autônomo executor** ainda precisa de planejador, ciclo de ferramentas, integração com a política de aprovação, registro de checkpoints, testes e recuperação. Os testes de integração com modelos exigem chaves próprias do usuário. **Não considerar pronto apenas porque a interface existe.**
