# RBX Studio — arquitetura de escrita de scripts (context-first)

Implementação experimental sobre `work/ai-agent-r10`, sem modificar a R10 original.

## Problema observado

Antes, o agente recebia automaticamente uma leitura superficial da árvore e a confundia com contexto suficiente para escrever qualquer Source. Assim podia gerar Luau a partir do objetivo textual e só depois descobrir em que serviço ou objeto inserir o código. Erros de API às vezes passavam pelo verificador porque o código de validação ignorava genericamente aliases espaciais, independentemente da classe. Regras específicas para `ParticleEmitter` não resolvem essa categoria de problema.

## Fluxo implementado

1. `project_context` e `search_game_tree`: leitura real do mapa.
2. `script_design(placements)`: o modelo declara, **sem Source**, cada script que pretende criar ou alterar: `path`, `class` (`Script`, `LocalScript` ou `ModuleScript`), `mode` e `purpose`.
3. O host verifica o caminho exato ou o ancestral existente no Explorer. Para `edit`, exige o script existente de classe compatível e lê seu código atual. O resultado estruturado é reenviado ao modelo.
4. Só após esse resultado o modelo escreve Luau e usa `project_edit`. Uma operação com Source sem planejamento correspondente não é aplicada.
5. O preflight estático usa a reflexão de propriedades da classe instanciada, em vez de uma exceção específica para chuva. Aliases de posição/rotação só passam quando existe um campo espacial refletido compatível. A validação nativa continua fazendo preview, compilação Luau e confirmação de escrita.
6. A revisão já existente consulta fontes persistidas, diagnósticos e — quando disponíveis — Play, Output e captura visual; a conclusão deve declarar os limites realmente observados.
7. `procedural_generate` expõe `script_targets` de todos os scripts gerados, inclusive após os oito primeiros itens da prévia. `procedural_apply` usa o mesmo preflight de planejamento e propriedades.

## A fronteira entre editor e Roblox oficial

A reflexão do DOM do RBX Studio **não é** uma cópia completa da API Luau do Roblox. O verificador atual confirma algumas operações estatísticas, não métodos/eventos dinâmicos, tipos inferidos por fluxo, semântica de todos os pais, permissões de assets ou rendering. A aplicação não deve usar `editor_schema` como única fonte de verdade para determinar se uma propriedade da API Roblox existe.

Uma solução completa de compatibilidade requer um catálogo versionado e verificável de classes, herança, membros, restrições de acesso e disponibilidade do runtime Roblox oficial (por exemplo, exportação oficial de Scripting API); um analisador Luau com tipos/fluxos; testes em Play no runtime próprio e no cliente Roblox quando possível; e evidências visuais e de áudio. Esses componentes **não foram implementados por esta mudança**. Não é correto declarar scripts universalmente funcionais nem gameplay verificado somente por compilação.

## Testes/limites

- Regressões Java verificam: recusa de escrita antes de `script_design`, planejamento com leitura do ancestral real, edição estruturada, revisão de escrita e propriedades genéricas inválidas.
- O workflow `ai-script-preflight.yml` executa o contrato Java em cada PR.
- Testes não equivalem a executar chuva, HUD ou modelos no aparelho. O procedimento de revisão permanece Play/Output e viewport reais sempre que disponíveis.
- Após mutações, o planejamento é invalidado: scripts novos não são colocados com contexto de revisão antigo.
- O checkpoint persistente **não** reutiliza evidência de arquitetura após reiniciar sessão; o modelo precisa revalidar o projeto.
