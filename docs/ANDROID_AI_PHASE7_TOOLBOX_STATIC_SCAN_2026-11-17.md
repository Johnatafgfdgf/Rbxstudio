# RBX Studio Android — Fase 7: análise estática local da Toolbox (.rbxmx)

**Branch:** `work/ai-autonomous-read-v1` • inclusão incremental na central de IA Android, sem recriar o editor ou alterar a branch `main`.

## Implementado

- Na central de IA há a seção **Segurança dos modelos da Toolbox**, com o botão **Selecionar e analisar modelo .rbxmx**.
- O Android abre o seletor de documentos para escolher manualmente um arquivo **.rbxmx (XML)**. O código lê o arquivo por `ContentResolver.openInputStream` e usa o `XmlPullParser` do Android, sem executar scripts, carregar módulos, inserir modelos no Workspace, enviar dados a um provedor de IA ou publicar assets.
- O relatório conta instâncias, `Script`, `LocalScript`, `ModuleScript`, código ausente/vazio/truncado e **categorias de sinais para revisão**. Marca padrões como `loadstring`, `require(assetId)`, HTTP/rede, `getfenv`/`setfenv`/`string.char`, `InsertService:LoadAsset` e serviços como DataStore/Messaging/Teleport.
- Indica até 16 nomes de scripts que receberam alertas, sem reproduzir o conteúdo do código. Nenhuma credencial, arquivo ou relatório é armazenado automaticamente no histórico de IA.
- Limites: até **2 MiB** de XML, **1.200 instâncias**, **100 scripts**, profundidade de 64 itens e **120 mil caracteres por fonte**. Ultrapassar limites recusa ou marca a inspeção incompleta, sem apresentar resultado como seguro.
- Documentos com `DOCTYPE` ou `ENTITY` são rejeitados. Entrada malformada ou de outro formato não é aprovada silenciosamente.
- O relatório diz explicitamente que **uma correspondência não comprova malware e a ausência de correspondências não comprova segurança**. Comentários podem causar falsos alarmes; código ofuscado, dependências dinâmicas e outros mecanismos não detectados podem passar sem alerta.

## Escopo e limitações

Esta é uma **análise antes da importação, exclusivamente para arquivos locais .rbxmx**. Não analisa automaticamente um ID da loja, não baixa assets da internet, não faz auditoria integral de `.rbxm` binário e não examina todos os scripts de um modelo já inserido no editor. Não garante comportamento em Play nem segurança futura após alterações do modelo.

O usuário ainda decide se vai importar o modelo pelo editor, independentemente do resultado. Nenhum novo comando MCP de escrita foi disponibilizado à IA.

## Testes

- `scripts/test_toolbox_source_scanner.py` extrai a função **Java real** `sourceRiskMask`, compila com `javac --release 11` e executa 15 testes de sinais positivos, negativos e combinados.
- `scripts/check_ai_hub.py` verifica o caminho somente leitura, limites, rejeição de DTD/entidades, botão mobile, ausência de execução Luau, importação de assets ou chamadas de IA.
- `.github/workflows/ai-hub-android-java.yml` executa os testes e compila a `StudioAiHub` com SDK Android 35.
- Testes do seletor Android e do parser XML com arquivos `.rbxmx` reais **em dispositivo físico** ainda são necessários. Build de APK não implica que o scanner foi verificado com modelos de terceiros.

## Próxima etapa

Integrar auditoria somente leitura de **modelos já inseridos**, consultando `search_game_tree` e `script_read` pelo MCP com autorização de leitura e cobertura declarada. Depois, possibilitar inspeção de dependências externas, aprovação de importação e validação de comportamento em Play, sem executar automaticamente conteúdo não confiável.
