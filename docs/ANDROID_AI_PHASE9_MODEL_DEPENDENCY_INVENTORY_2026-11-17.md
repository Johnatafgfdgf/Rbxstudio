# RBX Studio Android — Fase 9: inventário de dependências de modelos

**Branch experimental:** `work/ai-autonomous-read-v1`. Extensão da auditoria MCP de modelos já inseridos, sem substituir o editor, sem alteração da branch `main` e sem nova ferramenta de gravação.

## O que esta fase implementa

O relatório de auditoria de um `Model` em `Workspace` agora inclui uma seção **DEPENDÊNCIAS**, produzida a partir dos trechos Luau que já foram lidos do editor com autorização explícita:

- **Módulos locais encontrados:** identifica apenas referências simples da forma `require(script.Parent.Module)`, `require(script.Parent.Parent.Module)` e até mais um `Parent`, verificando se o caminho calculado já existe **na árvore do Model auditado** com classe `ModuleScript`. Não lê conteúdo além da janela original, não segue referências nem executa `require`.
- **Referências locais não resolvidas:** se um caminho simples não constar da árvore ou não for `ModuleScript`, o relatório marca a dependência como não resolvida.
- **Assets externos:** detecta `require(123456789)` e outros identificadores numéricos de cinco a dezoito dígitos. Apresenta IDs detectados, identificados como **não verificados**, sem consultar, baixar ou executar o asset.
- **`require` dinâmicos/desconhecidos:** expressões complexas, `require(variable)`, `require(game.ReplicatedStorage.X)` e estilos não suportados são contabilizados, **nunca presumidos seguros**.
- **Serviços externos:** mostra referências textuais a `ReplicatedStorage`, `ServerStorage`, `ServerScriptService`, `DataStoreService`, `MessagingService`, `TeleportService`, `HttpService` ou `InsertService` como **fora do escopo do Model**. Detectar um serviço, isoladamente, não significa comportamento malicioso.

## Limites, privacidade e resultado

A detecção é **heurística**, limitada a 24 mil caracteres por script já inspecionado e no máximo 40 expressões `require` percorridas por script. O inventário de cada categoria é limitado a 12 itens, e qualquer excesso é informado. Não há novo acesso a Internet, MCP, chamadas de API ou execução de Luau para interpretar as dependências.

Mesmo quando **todas as fontes locais forem lidas**, o relatório exibe **“FONTES LOCAIS LIDAS, MAS DEPENDÊNCIAS SEM VERIFICAÇÃO”** se existirem referências externas, `require` dinâmicos ou referências locais que não puderam ser resolvidas. Isso evita confundir leitura de código com auditoria integral de suas dependências. Se a árvore ou algum script já era incompleto, mantém o resultado **“INCOMPLETO”**.

Os caminhos e IDs no relatório podem revelar estrutura do projeto. O relatório permanece local por padrão e só entra na mensagem à IA com as autorizações separadas já existentes. **Nenhum código-fonte é enviado pelo recurso de relatório.**

### Limitações explícitas

- Regex não é um interpretador Luau; não elimina comentários ou strings antes de procurar padrões. Pode gerar falsos positivos e não cobre todos os padrões dinâmicos ou ofuscados.
- `require(script.Parent:WaitForChild("X"))`, módulos com nomes especiais, serviços referenciados indiretamente e dependências transitivas não são resolvidos nesta fase.
- A presença de `ModuleScript` no Model **não comprova seu comportamento seguro**. Não foi executada inspeção de Play, verificação por ID de assets ou publicação de conteúdo.
- A auditoria anterior só cobre um `Model` sob `Workspace`, até os limites de árvore e scripts já estabelecidos na fase 8. Se esses limites forem excedidos, o resultado continua incompleto.

## Testes

- `scripts/test_model_dependencies_java.py` extrai as funções Java reais `scanScriptDependencies` e `localModulePath`, compila com `javac --release 11`, e verifica módulos locais resolvíveis, dependências externas, duplicatas, expressão dinâmica, limites e rejeição de caminhos fora do Model.
- `scripts/test_toolbox_source_scanner.py` e `scripts/test_inserted_model_audit_java.py` continuam cobrindo a classificação de padrões e parsing das respostas MCP; foram atualizados para isolar corretamente o método Java de risco após o acréscimo das novas funções.
- `scripts/check_ai_hub.py` confere os contratos da auditoria, inclusive que o inventário de dependências não chama MCP, ferramentas de edição ou APIs de modelos.
- `.github/workflows/ai-hub-android-java.yml` chama as três suítes e compila a Activity com o Android SDK.

**Validação ainda necessária:** confirmar a conclusão dos CI do commit mais recente e reproduzir o relatório em dispositivo Android real, com modelos de scripts conhecidos e dependências externas. Compilação não substitui teste funcional.

## Próxima etapa

Planejar testes reais de Play com controle de acesso; ampliar a cobertura de referências sem executar código não confiável; identidade estável do projeto em vez de usar apenas seu título. Não adicionar inserção de assets sem autorização individual e validação do conteúdo importado.
