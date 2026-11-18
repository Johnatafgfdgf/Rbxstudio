# RBX Studio Android — Fase 11: materiais, transparência, colisão e ancoragem de Parts

**Branch:** `work/ai-autonomous-read-v1`. Mudança incremental no `StudioAiHub.java`, sem refazer o editor ou alterar a `main`.

## Ação real do agente

Adicionada a ação JSON `propose_part_style_update`, destinada **somente** a uma `Part` única, filha direta de `Workspace`, identificada por caminho `Workspace.NomeDaPeca` (nome alfanumérico validado).

O agente precisa primeiro inspecionar o objeto real via MCP `inspect_instance`; então pode propor alterar uma ou mais propriedades opcionais:

| Campo da proposta | Tipo aceito | Limite |
| --- | --- | --- |
| `material` | texto de uma lista explícita de materiais Roblox | Plastic, SmoothPlastic, Wood, WoodPlanks, Concrete, Brick, Metal, Glass, Neon, Grass, Sand, Slate, Marble, Granite, Ice |
| `transparency` | número finito | 0–1 |
| `anchored` | booleano real | `true` ou `false` |
| `can_collide` | booleano real | `true` ou `false` |

Os outros campos não são escritos. No mínimo um precisa estar presente. Propostas de classes não suportadas, propriedades ausentes na inspeção, scripts em vez de Parts, materiais fora da lista, strings usadas no lugar de booleanos ou números fora do limite são rejeitados.

## Fluxo de autorização e verificação

1. É necessário conectar o MCP e autorizar **Explorer**, **Agente** e a permissão existente **propostas de peças/modelos 3D**, desativadas por padrão.
2. A IA faz `inspect_instance` de uma Part existente e propõe uma alteração; **não executa Luau de escrita**.
3. O Android apresenta uma prévia de material, transparência, ancoragem e colisão com valores anteriores e desejados; botão de confirmação e `AlertDialog` separado.
4. Após aprovação, o Android faz uma **nova inspeção MCP**. Se qualquer um dos quatro valores originais mudou desde a proposta, a gravação é recusada.
5. O aplicativo gera um **template fixo de Luau**, sem incluir `reason` nem trechos executáveis fornecidos pelo modelo. Ele confere novamente nome/classe/pai e os quatro valores anteriores no instante da escrita. A resposta precisa ser exatamente `UPDATED_STYLE:NomeDaPeca` — qualquer outro recibo não conta como sucesso.
6. A segunda leitura MCP do alvo compara **todas as quatro propriedades finais**, incluindo as não alteradas. Confirmação inconclusiva impede avanço de planos e exige inspeção antes de repetir.
7. Uma alteração verificada pode entrar na memória criptografada somente se o usuário já ativou essa opção. O planejamento guiado entende a nova operação como etapa `PART`, mas cada escrita exige autorização individual.

## Escopo explícito

- Funciona para **Parts existentes diretamente no Workspace**. Peças aninhadas em Models, MeshParts e UnionOperations ainda precisam de caminho/tipo e validação própria.
- Não inclui edição de `PhysicalProperties`, densidade, atrito, elasticidade, `CollisionGroup`, `CanTouch`, `CanQuery`, pivôs nem materiais personalizados.
- O brilho, a aparência dos materiais e a colisão em **Play no Android** ainda requerem teste real; leitura MCP de propriedades não é teste físico nem visual.
- Não promete que uma alteração foi salva, publicada no Roblox ou desfeita; esses são fluxos separados.
- Alterar `Anchored` ou `CanCollide` pode modificar significativamente a física do jogo. A confirmação explica isso ao usuário.

## Testes

- `scripts/test_ai_part_style_java.py`: compila e executa as **implementações Java reais** do tipo `PartStyleProposal`, da validação local de material, número/booleanos, das comparações finais e do template fixo de escrita. Cobre valores válidos, inválidos, rejeição de strings falsas, injeção de `reason`, campos não selecionados e readback.
- `scripts/test_ai_plan_guards.py`: valida a admissão da nova operação na categoria `PART` e a rejeição em outras categorias.
- `scripts/check_ai_hub.py`: atualiza o contrato de recibos e acrescenta invariantes da ferramenta tipada e consentida.
- `.github/workflows/ai-hub-android-java.yml` executa os testes e compila o código Android com o SDK.

**Importante:** verifique o CI do último commit antes de declarar concluído o APK. Mesmo CI verde não substitui testar o fluxo MCP real, o renderizador e o Play no celular.

## Próximos passos

Priors: integrar edição de propriedades em Parts dentro de modelos, trabalhar propriedades físicas mais completas e criar uma bateria de Play com relatórios reais do editor. Proteger a operação sempre com permissões e feedback verificável.
