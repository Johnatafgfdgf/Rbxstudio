# RBX Studio Android — IA: verificação real após gravação autorizada

**Branch experimental:** `work/ai-autonomous-read-v1` em `Johnatafgfdgf/Rbxstudio`. Sem mudanças na `main`, no repositório original ou no motor gráfico. Continuação incremental da fase que adicionou recibos estritos de `execute_luau` e diagnóstico MCP local.

## O que foi alterado

- `StudioAiHub.java`: após o usuário aprovar a criação de uma `Part`, o cliente lê o objeto por `inspect_instance` no MCP local autenticado e confere caminho, nome e classe.
- Modelo composto: exige confirmação por leitura de `Model` e de **cada** `Part` planejada, antes de exibir sucesso verificado.
- `BillboardGui` e `ScreenGui`: após o recibo estrito, a interface tenta conferir o objeto criado na hierarquia real.
- Kit `Receber item`: verifica o registro dos quatro objetos principais: `ScreenGui`, `RemoteEvent`, `Script` e `Tool`, em seus respectivos serviços. **A leitura não prova que a GUI e o inventário funcionam em Play.**
- `multi_edit`: um novo script é verificado por classe/caminho; para editar um script existente, uma segunda leitura `script_read` precisa diferir da anterior e incluir o texto substituto. Se o retorno estiver truncado ou diferente de sua representação, o resultado fica **não confirmado**, mesmo que a edição tenha ocorrido.
- Falha de leitura não repete a gravação: evita duplicar objetos/scripts após timeouts, recusas ou respostas ambíguas. A interface avisa para conferir o Explorer antes de tentar de novo.

**Limites:** um recibo exato + presença no Explorer não provam comportamento em Play, fidelidade visual, propriedades de cada objeto, publicação ou Undo. A alteração de posição/cor de uma `Part` ainda não possui comparação pós-escrita de cada propriedade. Segurança de conteúdos externos/Toolbox permanece pendente.

## Verificações adicionadas

- `scripts/check_ai_hub.py` agora verifica que os novos caminhos de leitura estão conectados aos pontos de escrita, incluindo a exigência de não repetir automaticamente uma gravação ambígua.
- A workflow `ai-hub-android-java.yml` já executa o script de contratos, o teste Java de recibos e compila os fontes Android (Java 11/SDK 35) após pushes nesta branch.
- Os workflows existentes de ARM64, compatibilidade do motor e exportação de fontes também recebem alterações na branch, sem substituir o aplicativo.

## Estado de validação

| Item | Estado |
| --- | --- |
| Código das leituras pós-gravação | Implementado na branch experimental |
| Contratos estáticos | Adicionados; conferir resultado do GitHub Actions |
| Compilação Java / APK ARM64 da revisão | Aguardar conclusão efetiva dos workflows para marcar como aprovado |
| MCP com lugar aberto em Android físico | Não testado nesta alteração |
| Comportamento de Tool, GUI, Play, publicação e Undo | Não validado por esta alteração |

## Próximas etapas

1. Corrigir qualquer regressão apontada pelo build Java e pelo teste ARM64.
2. Testar operações com MCP real e confirmar pós-leitura/Undo no Android.
3. Adicionar leitura pós-escrita de propriedades transformadas, testes do modo Play e registro seguro por projeto.
4. Evoluir para ferramentas adicionais com permissões, checkpoints e execução autônoma gradual, sem permitir que a IA execute Luau arbitrário sem aprovação.
