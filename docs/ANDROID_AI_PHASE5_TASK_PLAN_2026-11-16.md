# RBX Studio Android — Fase 5: planejamento de tarefas MCP verificáveis

**Branch:** `work/ai-autonomous-read-v1`. Continuação de `StudioAiHub.java` sem substituição do motor GPUI, Rook, Explorer ou implementações anteriores.

## Adicionado ao código

- Novo contrato JSON `propose_task_plan`. O agente precisa ter feito pelo menos uma leitura MCP real antes da proposta e retorna um plano com **2 a 5 etapas**, cada uma contendo `kind`, `title` e `instruction`.
- Categorias permitidas e seus executores já existentes: `SCRIPT` (criar/editar scripts), `PART` (criar/editar peças e modelos compostos), `GUI` (BillboardGui/ScreenGui) e `ITEM` (kit já suportado de Tool + GUI + RemoteEvent + Script). Uma etapa só é aceita se sua categoria já estiver habilitada pelo usuário.
- Tela Android mostra a prévia completa do plano e solicita aprovação explícita. **Aprovar o plano não executa nem autoriza nenhuma gravação**.
- Painel mostra as etapas, progresso, botão `Executar próxima etapa • usa API`, pausa/retomada e cancelamento. O acionamento da etapa exige MCP, provedor configurado, permissões vigentes e campo de texto livre.
- O modelo recebe uma solicitação específica para a etapa selecionada; faz nova leitura do estado do editor. Um seletor de categorias recusa propostas fora do tipo do passo, ferramentas desconhecidas, novas propostas de plano aninhado e execução arbitrária.
- Após a aprovação individual da escrita (continua no sistema anterior), `recordEditorOutcome` só avança o contador quando a nova leitura MCP confirma uma operação correspondente à categoria. Se a verificação falhar ou a categoria não corresponder, o plano pausa **sem repetir a gravação**.
- O cancelamento do plano descarta suas propostas pendentes; não exclui nem reverte objetos já criados. Revogar permissões cancela futuras consultas e pausa o plano. Uma mensagem comum não pode substituir uma etapa ainda aguardando sua aprovação.
- O plano é **temporário da sessão**. O histórico criptografado por projeto da fase 4 continua independente; a proposta de plano não é salva no Keystore.

## Testes de código

- `scripts/check_ai_hub.py`: contratos estáticos para limite de etapas, inspeção prévia, categorias, botões e ausência de caminho novo de gravação.
- `scripts/test_ai_plan_guards.py`: extrai os métodos Java reais `planStepMatches` e `actionMatchesPlannedKind`, compila e exercita 25 casos válidos e proibidos, inclusive bloqueios de `execute_luau` e `publish_animation` como etapas.
- `.github/workflows/ai-hub-android-java.yml`: executa teste Java de matriz de rotação, recibos, categorias e compilação da Activity com SDK 35.
- Validar resultados de CI da **última revisão** antes de afirmar que o APK compilou. Provas com API Key, MCP e Android físico continuam pendentes.

## Não suportado nesta fase

- Execução autônoma contínua sem interação humana; cada etapa usa API e requer solicitação explícita, prévia e autorização de gravação.
- Persistência/restauração de planos ao reabrir; gerenciamento de dependências entre projetos.
- Toolbox de terceiros, upload/publicação de assets, testes de Play automáticos e autocorreção contínua. Essas ações **não podem** ser assumidas pela aprovação de um plano.
- Garantir que a interface e os scripts funcionem em Play: confirmação por `inspect_instance` ou `script_read` não é teste funcional.
- Promessa de gratuidade ou de custo fixo da API, nem acesso a modelos sem as credenciais e as permissões do provedor.

## Próximos trabalhos

1. Identificação estável da experiência e checkpoints de execução por projeto, com consentimento.
2. Validar um plano de criação real e o fluxo MCP `ler → prévia → aprovar → escrever → conferir → próxima etapa` no Android.
3. Integração segura da Toolbox, inspeção estática, Play e relatórios de testes sem ações externas não autorizadas.
4. UI sobreposta ao viewport com ergonomia mobile, sem sacrificar desempenho ou funções existentes.
