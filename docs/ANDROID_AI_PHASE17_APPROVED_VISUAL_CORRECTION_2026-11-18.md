# Fase 17/24 — sugestão visual → proposta de edição → confirmação e leitura MCP

**Branch de trabalho:** `work/ai-autonomous-read-v1` de
`Johnatafgfdgf/Rbxstudio`. A `main` não é modificada.
A adaptação de design/ícones será revisada **depois** das fases
funcionais, conforme a prioridade atual do usuário.

## Primeiro fluxo real implementado nesta fase

A fase 16 já possuía: imagem PixelCopy real do Rook, revisão
visual autorizada por provedor de IA, Decal criado por MCP e
confirmação independente de sua classe, face e textura.

A fase 17 inicia a ligação dessas etapas a **uma correção real**,
com escopo mínimo e verificável: trocar **somente `Decal.Face`** de
um Decal já existente, após o usuário aprovar. Os demais recursos
da fase 17 e o executor geral de tarefas ainda permanecem em aberto.

### Dados não confiáveis da IA

`StudioDecalVisualReview` solicita uma recomendação opcional
`DECAL_FACE_SUGGESTION: Top | justificativa visual`, **apenas**
quando a captura mostrar evidência concreta de face errada.
`StudioDecalFaceCorrection.fromVisualReply` rejeita resposta
maior que 12.000 caracteres, múltiplas sugestões, faces fora das
seis enumerações `Front/Back/Top/Bottom/Left/Right`, face
igual à atual, justificativas curtas, URLs e sintaxes de execução.

O retorno textual da IA não é código de editor: a interface
exibe a sugestão para leitura, vinculada **ao Decal confirmado
e à captura atualmente selecionada**. Alterar a imagem ou
o estado do Decal torna a proposta obsoleta. O usuário pode
ignorar a sugestão sem que ocorra qualquer edição.

### Aprovação individual e revalidação

1. Botão **Inspecionar e revisar correção sugerida** só fica
   habilitado depois de uma sugestão válida e de uma conexão
   MCP autorizada, com a identidade do Decal ainda confirmada.
2. `inspect_instance` faz leitura real de classe `Decal`,
   caminho, nome, `uniqueId`, `Texture` e `Face`. Se algum
   campo estiver ausente, alterado ou ambíguo, a correção é
   recusada; nenhum código é executado.
3. Um diálogo apresenta caminho, Asset ID mantido,
   Face antiga e nova, justificativa **não verificada** da IA
   e o impacto da operação. **Cancelar não escreve no projeto**.
4. Depois de confirmação explícita, o app consulta
   `inspect_instance` **novamente** e compara a identidade,
   a mesma face e a mesma textura. Só então envia **um**
   comando Luau fixo gerado por Java via
   `executeApproved3dCode` com DataModel `Edit`.
5. O código permite editar **somente `decal.Face`** em
   uma `Part` existente sob `Workspace` ou
   `Workspace.Model` e retorna exatamente
   `UPDATED_DECAL:<nome>`. Não cria nem remove objetos,
   não usa texto Luau arbitrário da IA, não troca o Asset ID,
   não manipula scripts e não publica o jogo.
6. Uma nova consulta MCP independente exige o mesmo
   `uniqueId`, a mesma `Texture` e a Face autorizada.
   Sem esse read-back, o aplicativo diz **operação não
   confirmada** e pede inspeção do Explorer/Output antes
   de qualquer repetição.
7. A captura anterior é invalidada depois da escrita.
   O novo resultado visual requer um novo Play e outra
   captura autorizada. Não confundir sucesso de inspeção
   com prova de beleza ou renderização.

## Limitações honestas

- O primeiro tipo de correção é **somente Face de Decal**:
  um agente de edição geral ainda não está concluído.
- O modelo pode interpretar a cena de maneira incorreta;
  por isso a correção exige uma pessoa e duas inspeções MCP.
- A resposta MCP pode ser interrompida depois da escrita;
  o sistema **não repete nem desfaz automaticamente** a
  operação incerta. Testar manualmente o Undo do editor.
- A integração ainda precisa de teste com projeto, conta,
  decal publicado, MCP e Play reais em Android. Os testes
  Java garantem validação de entrada e geração do comando,
  **não** que o asset foi exibido ou que o Roblox o aceitou.
- A chave de IA e o token MCP não são passados ao parser de
  sugestão e não são serializados no Luau gerado.

## Implementação e validação

- Produção: `StudioDecalFaceCorrection.java`,
  `StudioDecalVisualReview.java`, `StudioAiHub.java`.
- Regressão: `scripts/test_ai_decal_face_correction_java.py`,
  executa os métodos Java reais com respostas válidas,
  múltiplas sugestões, comandos injetados, IDs inválidos,
  e valida o Luau gerado e os guardas da Activity.
- A workflow `.github/workflows/ai-hub-android-java.yml`
  compila as classes Android e executa o teste.
- Exportação do código completo atualizada em
  `.github/workflows/export-ai-full-source.yml`.

**Próxima parte da fase 17:** generalizar sugestões para
mudanças de material, cor e posicionamento **com propostas
estruturadas, pré-condições e rollback por histórico do editor**,
sem permitir que a IA escolha APIs MCP desconhecidas ou código
de publicação. Revisão visual e automação de várias etapas
só após read-back consistente e testes reais.
