# RBX Studio Android — Fase 12: estilo de Parts dentro de Models

**Branch:** `work/ai-autonomous-read-v1`. Continuação incremental da Fase 11, sem substituir o editor, retirar controles ou modificar `main`.

## Novo escopo implementado

A ação existente `propose_part_style_update` suporta `Workspace.Part` e `Workspace.Model.Part` (**Part única dentro de Model filho direto do Workspace**). O agente altera apenas Material, Transparency, Anchored e CanCollide, com inspeção real prévia, prévia antes/depois, consentimento individual e nova leitura de confirmação.

Caminhos são estritamente validados: os nomes devem ser simples e há no máximo um Model intermediário. Serviços diferentes, caminhos com vários Models, caracteres de controle e trechos de código são rejeitados.

Para uma Part aninhada, o Android consulta o Model pai no MCP antes da escrita e novamente na confirmação. O template Luau fixo também exige que o pai seja Model diretamente em Workspace, que a Part pertença ao pai exato e que as quatro propriedades anteriores não tenham mudado. Nenhum Luau vindo do modelo é executado. Os recibos são específicos: `UPDATED_STYLE:Model.Part` ou `UPDATED_STYLE:Part`; resultado inconclusivo pausa o plano sem repetição automática.

## Validação no código

`scripts/test_ai_part_style_java.py` compila os helpers reais e inclui casos de nome/escopo malicioso, geração de Luau para Part direta e aninhada e verificações de propriedades. `scripts/check_ai_hub.py` verifica os guardas estáticos. O workflow `.github/workflows/ai-hub-android-java.yml` executa essas suítes e compila a Activity com SDK Android 35.

**Testes pendentes:** edição MCP com Model real no celular, Undo, materiais renderizados e colisão em Play. Não há suporte nesta fase a MeshPart, Model em múltiplos níveis, física avançada, execução automática de Play ou publicação de assets. A aprovação da proposta continua obrigatória.

**Próxima prioridade:** validar todo o fluxo no Android, identificar o objeto de maneira estável e ampliar os testes funcionais de Play.
