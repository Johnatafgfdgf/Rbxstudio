# RBX Studio Android — Fase 13: MeshPart e identidade de edição

**Branch experimental:** `work/ai-autonomous-read-v1`; continuidade da Fase 12, sem refazer a central de IA ou tocar na `main`.

## Alterações implementadas

- A ferramenta **já existente** `propose_part_style_update` aceita objetos das classes **Part** e **MeshPart** em `Workspace.Nome` ou `Workspace.Model.Nome`, sendo `Model` filho direto do Workspace. Não suporta UnionOperation, Script, Model como alvo ou hierarquias mais profundas.
- As únicas propriedades modificáveis continuam sendo `Material`, `Transparency`, `Anchored` e `CanCollide`. O agente retorna apenas JSON de proposta, nunca código Luau arbitrário.
- `verifiedStyleTarget` confere caminho exato, último segmento do nome, classe na lista fechada, propriedades reais e ausência de resposta ambígua `matches`.
- A proposta guarda **classe original** e o `uniqueId` devolvido pelo MCP **se existir**. Antes da escrita e após o read-back, a classe deve coincidir; se o ID original era não vazio, ele também deve coincidir. Se o MCP não fornecer `uniqueId`, a classe, caminho e propriedades ainda são conferidos, mas **não é possível garantir identidade estável** contra substituições homônimas.
- O código Luau de escrita é um **template fixo** do aplicativo, que valida caminho, classe exata (`p.ClassName`), Model pai e propriedades antigas antes de modificar a peça.
- O Android continua solicitando autorização individual e mostrando uma prévia real. Sem recibo e read-back confirmado, o plano pausa sem reexecutar a escrita automaticamente.

## Verificação

`scripts/test_ai_part_style_java.py` compila/executa os helpers de produção: templates de Part e MeshPart, escopo aninhado, tipos não permitidos, identidades trocadas e valores de propriedades. `scripts/check_ai_hub.py` foi ampliado para assegurar o fluxo de leitura, identidade, classe, aprovação e ausência de despacho arbitrário de código.

O workflow `RBX Studio AI Hub native Java verification` referente ao commit `9681901468` **passou**: https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/44022058470 . Isso é verificação de código/compilação Android Java SDK 35, não execução real do editor.

## Limites e próximos passos

**Não comprovado:** funcionamento ponta a ponta no Android físico, materiais renderizados no mapa, física em Play, Undo do motor nativo, estabilidade real dos IDs do MCP em todas as cenas, e compilação APK completa desta revisão. Ainda são necessárias verificações com duas peças homônimas e substituição do alvo durante a confirmação.

Próximo incremento: testes Play com observações reais, relatório de Output e prévia do viewport, sempre respeitando permissões e sem simular resultados.
