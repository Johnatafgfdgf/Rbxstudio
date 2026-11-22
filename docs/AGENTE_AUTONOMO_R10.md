# RBX Studio Android — R10

Validação retomada em 22/11/2026. Repositório: https://github.com/Johnatafgfdgf/Rbxstudio, branch `work/ai-agent-r10`.

## Recuperação e preservação

A continuação usa a R9 realmente recuperada e o checkpoint R10, sem recriar o aplicativo. A referência R9 `403b3b841fccd3e018cb7bcdbdc3e7b7ff663795` veio do arquivo de origem; esse objeto Git original não foi encontrado. A branch pública R10 foi preservada e avançada; a principal não foi substituída. Os pins dos três submódulos permanecem originais e as mudanças nativas são patches reproduzíveis.

O ambiente reiniciou entre as sessões. O APK e algumas capturas/traces finais de 21/11 não foram recuperados. Não são apresentados como artefatos disponíveis. Os ajustes finais foram reaplicados, testados novamente e preservados em commits/checkpoints. A pasta `docs/evidence/r10/recovered-validation/` contém a nova execução. Falhas do próprio harness também estão registradas e não contam como falhas ou sucessos de tarefas do modelo.

## Bugs corrigidos e causas

| Falha | Causa e correção |
|---|---|
| Agente pede inspeção depois de cada mudança | A escrita invalidava a leitura. O ciclo agora consulta a árvore real automaticamente antes da próxima chamada do modelo. |
| `studio_action` indisponível | Envelope confundido com nome de ferramenta. Os adaptadores normalizam a ação estruturada e mantêm argumentos/resultados separados do chat. |
| Scripts vazios ou texto tratado como código | Source é um campo separado, compilado como Luau; scripts criados/alterados são rastreados e lidos antes da conclusão. Texto comum não é executado. |
| `source` ignorado, scripts vazios e `Script missing` | O modelo enviava `properties.source` enquanto o editor espera `Source`; o schema genérico também ocultava esse campo especial. O adaptador normaliza aliases sem alterar o JSON original, recusa códigos conflitantes, expõe Source para as três classes de scripts e reconhece fonte vazia somente após confirmar classe/propriedades reais. StringValue não é tratado como script executável. |
| IDs novos adivinhados e lotes recusados repetidamente | Os números de instâncias futuras não são previsíveis. A recuperação devolve árvore atual e caminhos derivados de pais reais; dependências dentro do lote usam caminhos. |
| Respostas MCP truncadas deixam JSON inválido | Removido o corte cego de 12 mil caracteres do transporte. A leitura de contexto pode ser limitada, mas recebe aviso explícito; não é usada como JSON completo. |
| Propriedades de GUI recusadas | O armazenamento usa FontFace, cantos separados e ScreenInsets. O adaptador traduz Font, CornerRadius e IgnoreGuiInset para os campos reais, preservando os valores. |
| `Part.Position` recusado | Position/Orientation são representados por CFrame no DOM. O adaptador aceita esses campos em BasePart/Attachment, preserva a outra metade da transformação e rejeita valores não finitos ou campos conflitantes. Teste com save/reopen real. |
| GUI salva com referências quebradas | O Visual Builder recusa modificadores UI fora de GuiObject. A revisão verifica referências locais literais de scripts contra instâncias reais. Não interpreta todo Luau nem prevê objetos criados em runtime. |
| Contagem falsa quando nada mudou | Recibos `changed:false` não incrementam operações nem lotes alterados. Lotes mistos contam operações confirmadas do lote, não propriedades individuais. |
| Gemini sem `parts` após chamada malformada | Alternativa limitada a JSON estruturado para MALFORMED_FUNCTION_CALL/UNEXPECTED_TOOL_CALL, mantendo validação. Bloqueios de segurança não são contornados. |
| 429 e falhas transitórias encerram imediatamente | Retry-After/RetryInfo, tentativas limitadas e reservas elegíveis compartilham o orçamento. Não há rotação de chaves para contornar cotas compartilhadas/desconhecidas. |
| Reserva pode gerar cobrança não aceita | Modelos de custo desconhecido/free tier exigem consentimento explícito para uso automático. Preço zero e free tier não são tratados como saldo garantido. |
| Chat recalcula layout continuamente | Texto e limite de linhas só mudam quando necessário. Popup fica na área disponível e menus usam ScrollView. Teste Android é separado do teste do viewport. |
| Composer cortado por navegação/teclado | Padding considera a interseção da área visível com o host já redimensionado, evitando desconto duplo do teclado. API 30+ usa WindowInsets/WindowMetrics; API 29 usa visibleDisplayFrame. |
| IA conclui após editar só layout, deixando lógica existente quebrada | A revisão agora inclui fontes e hierarquia reais mesmo quando o lote alterou apenas objetos. Uma lacuna estática de controles somente por teclado impede a conclusão também em scripts preexistentes inspecionados; há teste de regressão para essa evasão. A revisão continua limitada e não certifica gameplay. |

## Funcionalidades conectadas

- **Conexões:** várias chaves por OpenAI, Gemini, Claude e OpenRouter; nome, prioridade, ativação, edição, exclusão, teste, catálogo e modelo por conexão. Limite local de 32 conexões. Endpoint arbitrário para qualquer provedor ainda não foi adicionado.
- **Credenciais:** Android Keystore/AES-GCM existente; segredos separados de metadados, histórico e handoff. Revogação remove a cópia legada. Nenhuma chave foi incluída no código ou nos artefatos públicos.
- **Reservas e handoff:** estado persistente v2 com objetivo, complementos, plano, eventos, recibos, cursores de builds, scripts, revisão, snapshot e próxima ação. Troca reinicia o protocolo do provedor, não o projeto. Operação sem recibo exige reconciliação e não é repetida às cegas.
- **Uso:** barra sob o modelo, limites e renovação somente quando a API informa. Sem quota, mostra saldo não informado e consumo observado. Não inventa porcentagem Gemini.
- **Autonomia por projeto:** operações locais reversíveis autorizadas sem repetir aprovações por objeto. Publicação, código remoto, exclusões grandes e operações de conta continuam protegidos. Orçamento 8–128 chamadas; novo padrão 64, preservando escolhas anteriores; prazo de 30 minutos.
- **Execução:** contexto real, investigação, plano, lotes tipados, observação, diagnóstico, revisão e correção limitada. Complementos entram na tarefa ativa. Pausa preserva trabalho; cancelamento/undo usam histórico ou snapshot guardado por revisão.
- **Checkpoints:** arquivos Roblox reais, restauração no DOM e atualização de histórico/mirror/viewport. Não há retenção automática completa de snapshots nesta versão.
- **Contexto e conhecimento:** busca seletiva, schema dinâmico, compactação com estado estruturado e referências recuperadas automaticamente para o pedido. Documentos são dados, sem autoridade sobre permissões. Memória não é treinamento dos pesos do modelo.
- **Roblox:** consulta oficial de capacidades da chave instalada antes de upload; sem permissão de assets, o agente pode procurar recursos existentes na Toolbox. A consulta online de uma chave Roblox real continua não validada nesta sessão. IDs/publicações precisam de confirmação real.

## Ferramentas e limites

As edições usam o DOM, reflexão, Source, compilador Luau, histórico e serializer reais do editor. Há ferramentas para hierarquia/seleção, classes e propriedades suportadas, serviços, criar/mover/duplicar/excluir/renomear, atributos/tags, scripts, terreno, diagnóstico, Output, salvar, undo/redo, Toolbox, recursos/documentação e as rotas existentes de captura, Play e publicação quando disponíveis e autorizadas.

Isso não representa paridade com todas as APIs e comportamentos do Roblox oficial. IDs são estáveis na sessão; após reabrir, a retomada inspeciona o estado e utiliza caminhos/recibos, sem garantir identidade numérica universal entre sessões. Gravações ambíguas permanecem um bloqueio real de recuperação.

O Explorer recebeu seleção múltipla explícita por toque, contagem e ações da seleção real. O editor Luau recebeu selecionar/copiar/recortar/colar/desfazer/refazer/início/fim, encaminhamento do menu Android e correções de seleção/rolagem do IME. Foram compilados e exercitados por contratos; uso completo dessas telas no POCO ainda exige validação física.

Na auditoria do ciclo autônomo não foi encontrada uma rota `pedido de chuva → script fixo`, nem um cache de respostas comum aos modelos. Os geradores procedurais são ferramentas explícitas parametrizadas; utilitários antigos continuam existindo como recursos próprios, sem substituir silenciosamente o pedido do chat. Os traces mostram soluções e alterações diferentes, mas isso não garante diversidade estética nem elimina repetições causadas pelo próprio modelo. O teste de chuva localizada inicialmente falhou por tipos/IDs, o que motivou correções reais de argumentos, não a inserção de uma resposta pronta.

## AI Visual Builder e especialidades

Árvores personalizadas de Frames, textos, imagens/botões, layouts, scroll, constraints, cantos/strokes/gradientes, temas e callbacks compilam para operações editáveis do projeto. O schema descreve filhos explicitamente. O verificador calcula limites, interseções de botões, contraste declarado e alvos de toque em três tamanhos de tela; não renderiza, mede fontes ou certifica beleza.

Os oito geradores existentes foram preservados: terrain, building, scatter, vfx, ui, animation, camera e audio. Terreno com ruído, construção modular, distribuição, camadas de VFX, keyframes, timeline de câmera e sons têm saídas reais, com os limites descritos na R9. Papéis especializados organizam plano e critérios; não há distribuição completa e automática de todos os especialistas entre modelos.

Inspeção do renderer confirmou que o fallback ParticleEmitter usa `sparkles_main.dds`. O conhecimento recuperável explica que esse recurso parece brilho/estrelas e não deve ser apresentado como gota realista. Isso é informação técnica, não um script de chuva predefinido. Texturas reais exigem recurso confirmado ou alternativa compatível.

Ainda não há equivalência com ForgeGUI, geração externa completa de imagens/áudio, biblioteca profissional de animações nem inspeção visual automática de toda GUI. Capturas/comparações existentes só autorizam afirmações visuais quando uma imagem real foi obtida.

## Fontes e licenças

O Resource Hub existente pesquisa/inspeciona metadados, commit, licença e README sem executar código externo. React Luau (MIT), Fusion (MIT no commit consultado na R9), Luau-LSP (MIT) e TestEZ (Apache-2.0, arquivado) continuam referências recuperáveis. Evidências em `docs/evidence/r9/sources.json`; não foram reinstalados automaticamente esses pacotes nem redistribuídos packs externos. Avisos/licenças nativos foram preservados. Não houve novo treinamento de modelos.

Referências oficiais: Creator Hub (API keys, TextLabel, UICorner, ScreenGui, partículas), documentação de rate limits de Gemini/OpenAI/Claude. Os detalhes de implementação estão nos respectivos fontes e nos relatórios anteriores.

Revalidação em 22/11: [limites Gemini](https://ai.google.dev/gemini-api/docs/rate-limits), [chaves Roblox e introspecção](https://create.roblox.com/docs/cloud/auth/api-keys) e [Assets API](https://create.roblox.com/docs/cloud/guides/usage-assets). A quota Gemini é por projeto, portanto cadastrar várias chaves não implica somar limites. Upload Roblox depende das permissões oficiais; o app não amplia o acesso de uma chave.

## Testes e estado da entrega

Os resultados atuais e seus arquivos estão em `docs/evidence/r10/`. Um `completed:true` é a decisão do agente naquela execução, não uma certificação independente de gameplay.

| Verificação | Alcance |
|---|---|
| Regressões | 29/29 suítes passaram novamente em 22/11: contratos Java, protocolos, permissões, assets, geradores e limites. Não são 29 testes funcionais Android. |
| Nativo | 20/20 casos passaram: DOM, reflexão, Luau, snapshots, aliases GUI/espaciais, persistência binária e geradores. Transporte sem renderer/JNI/socket Android. |
| Java | Todos os 53 arquivos embarcados compilaram com SDK 35/Java 11. |
| API | Catálogo oficial Gemini HTTP 200. Novas tarefas em Gemini 3.1 e 3.5 Flash Lite; outros provedores têm contratos, não testes online com chaves reais nesta sessão. |
| GUI | Nova configuração futurista: 13 operações; edição posterior: 8 operações, preservando ScreenGui e adaptando Source. Salvas e reabertas; não testadas em Play/renderização. |
| Recuperação de erro | Um erro Luau controlado foi lido/corrigido: 1 operação e diagnóstico sem erro de sintaxe. Não comprova lógica de jogo. |
| Chuva localizada | 16 chamadas Gemini 3.1, 6 operações confirmadas, mapa salvo. Script/efeitos estruturais criados; aparência, som e gameplay não verificados. Tentativas anteriores que falharam estão preservadas. |
| Retomada | Gemini 3.5 aplicou e salvou 19 operações; interrupção controlada após escrita confirmada. Gemini 3.1 retomou o mapa e Task State persistidos, concluiu sem novo lote e sem duplicar operações. Mesma chave/provedor; não é teste online de failover entre contas. |
| Android Keystore | Conexões múltiplas, seleção/modelo, alteração/exclusão e Task State passaram em Android 29 após force-stop/reabertura real. Segredos sintéticos, backend AES-GCM/Keystore real e inspeção de ausência de texto claro. Não houve chamada a provedores nesse teste. |
| Android | Capturas/reporte da execução atual, quando disponíveis, em `android-ui/`; Activity Java em APK de teste sem viewport nativo. Não é certificação do APK ARM64 completo. |
| Build ARM64 | Resultado, hashes e assinatura em `apk-inspection.json` quando produzido. Editor nativo recompilado; player local preservado da R9. |
| Dispositivo físico | POCO X6 5G indisponível: sem medição de FPS, bateria, memória ou estabilidade real nesse aparelho. |

O teste de inventário gerou fontes reais e salvou o mapa, mas a leitura independente encontrou falhas funcionais: controles I/F sem toque, coleta fora do contêiner percorrido, Tool sem Handle confirmado e ausência da entrega/equipagem pedida. O retorno `completed:true` dessas tentativas NÃO é aprovação do sistema. A barreira mobile foi ampliada após essa evidência; o resultado final está no resumo de validação. A revisão estática de remotes/referências é limitada e não substitui segurança e testes de gameplay. Builds que avisam sobre ausência de cloudflared/Luau-LSP Android não incluem esses binários automaticamente.

## Pendências

1. Exercitar o APK completo no POCO: JNI/MCP, Explorer, teclado, Toolbox, viewport, Play e publicação de teste no Roblox oficial.
2. Testar permissões após reinício físico e failover online entre várias chaves e provedores distintos; Keystore e Task State já passaram após reinício de processo no emulador.
3. Renderizar e interagir com GUIs/VFX gerados, verificar sons, física, animações e integração cliente-servidor; salvar/compilar não prova isso.
4. Completar geração externa de assets, publicação por categoria, direção profissional de animação/cinematografia e distribuição dos especialistas.
5. Ampliar reconciliação de gravações ambíguas e QA de dependências, segurança e desempenho.

Não é possível declarar zero bugs, autonomia ilimitada ou capacidade profissional para qualquer solicitação com a validação disponível.

## Arquivos e reprodução

Principais arquivos: StudioAiAgent, StudioAiConnections, StudioAiUsage, StudioAiHub, StudioAiToolProtocol, StudioAiProjectEdit, StudioAiScriptPolicy, StudioAiVisualBuilder, StudioDeveloperEngine/Knowledge, StudioRobloxCapabilities, GpuiImeView e patches nativos de MCP/Explorer/script editor/capacidades. A lista completa é gerada em `changed-files.txt`.

Use `scripts/prepare.py`, `check_preparation.py`, `run_ai_regressions.py`, `test_ai_project_native.py`, `check_android_java.py` e `cargo_android.py`. `run_ai_live_batch.py` exige chave explícita e nunca é disparado automaticamente em CI. `test_chat_popup_android.py` exige SDK/AVD. O workflow de contratos inclui a branch R10; seu resultado remoto deve ser consultado separadamente dos resultados locais.
