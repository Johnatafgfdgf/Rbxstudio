# RBX Studio Android — Agente IA: etapa funcional de provedores (16/11/2026)

**Escopo:** `Johnatafgfdgf/Rbxstudio`, branch `verify/android-menus`. O projeto original não foi modificado. O aplicativo continua construído sobre GPUI e mantém o servidor MCP e suas permissões existentes.

## Entregue no código desta etapa

| Componente | Implementação real | Limite atual |
| --- | --- | --- |
| Tela de provedores | `StudioAiHub` é uma `Activity` Android privada, aberta pelo botão **Abrir provedores e chat** na gaveta GPUI **IA** | Ainda é uma tela complementar, não um chat sobreposto no viewport |
| OpenAI | `GET /v1/models`; `POST /v1/responses` | A API Key e acesso a modelos dependem da conta |
| Gemini | `GET /v1beta/models`; `POST /v1beta/models/{id}:generateContent` | A página de modelos e os limites dependem da API |
| Claude | `GET /v1/models`; `POST /v1/messages` | A disponibilidade de modelos varia por conta |
| OpenRouter | `GET /api/v1/models`; `POST /api/v1/chat/completions` | A lista pode ser grande; a UI limita a 400 entradas por consulta |
| Credenciais | `StudioServices.credential` usa Android Keystore AES-GCM; uma chave privada por provedor; botões **Salvar** e **Remover chave** | Não importar automaticamente tokens de outras contas ou provedores |
| Seleção e histórico | Escolha do modelo por provedor, histórico de mensagens na sessão; dados de uso quando a API informar | Preços, latência e comparações transversais não são afirmados sem fontes |
| Editor real | Botão **Inspecionar Explorer real via MCP** usa loopback autenticado, somente `search_game_tree`; `studio_id` e token ficam no processo | O MCP precisa estar ativado, e o chat não executa ferramentas de edição ainda |
| Envio de contexto | A árvore é mantida localmente; checkbox explícita, desligada por padrão, decide se ela será incluída no pedido ao modelo | Não lê scripts inteiros nem a cena visual automaticamente |
| Permissões de edição | Fila, aceitação/recusa e classificação por risco permanecem no editor MCP; ferramentas como `multi_edit`, `insert_asset`, `execute_luau` e publicação são protegidas pela política anterior | O novo chat ainda não é um executor autônomo dessas ferramentas |
| Conhecimento especializado | Novas skills acessíveis por `skill`: `mobile-toolbox-safety`, `mobile-ui-builder`, `mobile-gameplay-check`, além das existentes | São guias para clientes MCP; não instalam automaticamente software de terceiros |

## Segurança implementada e requisitos

- `StudioAiHub` não é exportada para outros aplicativos, e a ligação GPUI passa o endpoint MCP apenas num Intent interno explícito.
- Tokens da sessão MCP e API Keys não são enviados aos provedores no prompt, não são persistidos no projeto Roblox, e não são registrados nos logs.
- Requests vão para destinos HTTPS fixos dos quatro provedores; redirecionamentos HTTP são desativados antes de enviar autenticação. Timeout de conexão de 12 s e de leitura de 30 s.
- Respostas têm limites de tamanho; histórico em memória limitado a 12 turnos (mensagens).
- A inspeção MCP usa apenas o endereço IPv4 127.0.0.1 e a ferramenta de leitura `search_game_tree`. Textos de scripts, Toolbox, páginas externas e mensagens do modelo são conteúdo não confiável.
- A IA nunca recebe autorização implícita para executar Luau, inserir assets, excluir objetos, editar scripts, salvar projetos, publicar ou enviar arquivos. Uma futura execução de ferramentas deve respeitar a fila de aprovações do editor.

**Importante:** ler código-fonte e compilar não prova que os provedores aceitam as credenciais do usuário. Não usamos API Keys de usuário nem efetuamos chamadas faturáveis em CI. O teste real de conexão requer usuário autorizado configurar sua própria chave e observar uma resposta do serviço.

## Fluxo inicial no celular

1. Abra um lugar no RBX Studio Android e toque na barra **IA**.
2. Toque em **Abrir provedores e chat**. Para consultar o projeto, primeiro ative o servidor MCP na mesma gaveta.
3. Na tela de provedores, selecione OpenAI, Gemini, Claude ou OpenRouter; o botão de tutorial explica o tipo de chave necessário. Não compartilhe chaves por chat.
4. Salve a chave no armazenamento protegido do Android, consulte o catálogo oficial e selecione um modelo disponibilizado para a sua conta.
5. Digite uma pergunta e envie. O resultado, erros e contagem de tokens (se disponíveis) aparecem na tela.
6. Para analisar o mapa real, toque **Inspecionar Explorer real via MCP**. Marque o checkbox de contexto somente se autorizar enviar o resumo do projeto à IA.
7. Volte ao editor. A central não modifica o mapa automaticamente; clientes MCP autorizados continuam tendo o fluxo de ferramentas existente.

APIs podem ter custos ou requisitos de idade/conta; o aplicativo não compra créditos nem oferece atalhos para contornar condições de acesso. Nunca informe preços como se fossem universais ou em tempo real sem consultar a fonte.

## Pesquisa de bibliotecas e repositórios

Recursos existentes ou adequados para uso futuro sem instalar executáveis externos indiscriminadamente:

- **Roblox Creator Docs:** referência oficial de Luau, UI, segurança, animação, formatos e Open Cloud. https://create.roblox.com/docs
- **Luau-LSP** (MIT): assistência de código e diagnósticos estáticos; o editor já possui integração parcial com LSP, mas no Android ainda há limitações de distribuição do binário. https://github.com/JohnnyMorganz/luau-lsp
- **Rojo** (MPL-2.0): sincronização de scripts/arquivos e projetos de jogo com controle de versão. Não confundir `rojo sourcemap` com um servidor de sincronização ativo. https://github.com/rojo-rbx/rojo
- **Wally** (MPL-2.0): gerenciador de pacotes com manifests e lockfiles. Instalar pacotes de terceiros requer auditoria e autorização. https://github.com/UpliftGames/wally
- **Luau oficial:** gramática, verificação de tipos e execução; adaptar apenas após confirmar suporte no motor Android e licença. https://github.com/luau-lang/luau

Esses repositórios não são fontes de execução automática: consultar informações e incorporar cópias licenciadas é diferente de baixar e executar scripts não auditados no telefone.

## Não implementado nem aprovado como completo

- Agente autônomo que chama múltiplas ferramentas por objetivo, distribui tarefas por especialistas e se autocorrige até finalizar.
- Fluxos de execução de edições pelo chat e validação end-to-end de `multi_edit`, Toolbox, Luau, GUI, screenshots e Play sob comando do LLM.
- Histórico criptografado e memória persistente por projeto, checkpoints de operações, retomar tarefas após interrupção, desfazer mudanças externas.
- Seleção automática entre provedores por custo/desempenho, indicadores de preço/latência de todos os modelos, orçamento e limite de despesas.
- Chat flutuante sobre o viewport com arrastar/redimensionar, streaming em tempo real, animações complexas e prévias editáveis.
- Publicação de animações via API em conta real (a ferramenta MCP existe, mas o upload ainda não foi validado).
- Testes de funcionamento completo no POCO X6 5G real.

## Verificação e entregáveis

- `scripts/check_ai_hub.py`: guarda contratos de endpoints, Keystore, privacidade e nenhuma ferramenta destrutiva ligada ao chat.
- `scripts/check_studio_apk.py`: exige a classe real `StudioAiHub` no DEX do APK assinado.
- Workflows: `.github/workflows/experimental-arm64-apk.yml` (APK ARM64) e `.github/workflows/real-android-ui-preview.yml` (emulador x86 com capturas).
- Versão de pacote mantida em `0.6.11` experimental; o nome/identidade Android não mudou para evitar perda de projetos e credenciais. Não desinstale a versão anterior sem exportar os dados.

**Status de validação:** verificar o resultado do GitHub Actions da revisão mais recente antes de anunciar APK. Uma execução intermediária encontrou um delimitador não fechado no novo painel, corrigido em `7b472de9`; alterações posteriores exigem nova compilação. A integração de chat e o acesso real ao MCP precisam de ensaio no dispositivo e com credenciais da própria conta para serem declarados operacionais.
