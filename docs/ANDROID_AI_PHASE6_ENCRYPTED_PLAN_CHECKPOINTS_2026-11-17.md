# RBX Studio Android — Fase 6: checkpoints de planos no Android Keystore

**Branch experimental:** `work/ai-autonomous-read-v1`. Mudanças incrementais no `StudioAiHub.java`, aproveitando a implementação existente do MCP e de `StudioServices.credential`. A branch `main` não foi alterada.

## Implementação

- Checkpoints de planos de 2 a 5 etapas podem ser guardados **apenas com consentimento**, pelo controle `Salvar checkpoints criptografados deste plano` (desligado inicialmente). Desmarcar interrompe futuras atualizações, mas **não apaga** o checkpoint anterior; há um botão separado para apagá-lo.
- O registro contém versão (1), nome exato do projeto, objetivo curto, categorias/títulos/instruções de cada etapa, número de etapas confirmadas e data de gravação. Limite de **4.600 bytes UTF-8**. Não guarda scripts completos, chaves de API, pairing MCP, mensagens completas, imagens ou credenciais. *As instruções curtas podem refletir informações do projeto, mas são criptografadas localmente.*
- Reutiliza o **AES-GCM do Android Keystore** através do serviço de credenciais já usado no app. Usa um alias derivado do nome do projeto, separado do diário de operações confirmado.
- A leitura valida versão, nome exato do projeto, estrutura de 2–5 etapas, tipos permitidos (`SCRIPT`, `PART`, `GUI`, `ITEM`) e contador de progresso entre 0 e o total. Dados inválidos não são restaurados.
- Restaurar exige ação no botão `Revisar e recuperar checkpoint salvo`, abre confirmação com as etapas visíveis e deixa o plano **pausado**. Nenhuma API ou comando MCP é enviado durante a recuperação. A sessão precisa de novo MCP, API Key e autorizações explícitas antes de gerar outra proposta. A continuação sempre solicita uma inspeção atual do editor.
- Ao aprovar um plano, pausar/retomar ou **confirmar uma gravação pelo MCP**, o checkpoint é atualizado se o consentimento de armazenamento ainda estiver ativo.
- Erros ou resultados não confirmados nas sete classes de escrita pausam o plano e não provocam repetição automática da gravação. Cancelar um plano apaga sua cópia persistente; apagar somente o checkpoint não apaga objetos do editor.
- O armazenamento é indexado pelo **nome exato** do projeto porque o editor ainda não expõe um identificador de arquivo/experiência estável nesta integração. **Dois projetos diferentes com o mesmo nome podem colidir**; uma confirmação explícita avisa isso e exige conferência do estado real do mapa.

## Testes e limitações

- `scripts/check_ai_hub.py` verifica os contratos de consentimento, limite de tamanho, separação do diário, validação da versão e do projeto, restauração pausada e nenhuma chamada de API/MCP no fluxo de recuperação.
- O workflow `ai-hub-android-java.yml` compila `StudioAiHub.java` e `StudioServices.java` contra o Android SDK 35, além de executar os testes de recibos MCP, operações de planos e cálculos de rotação.
- Verificar a conclusão da execução referente ao commit final antes de afirmar que o novo APK existe. **Compilação em CI não equivale a teste físico do Keystore, MCP ou Play.**
- A persistência **não salva uma cópia do mapa**, não executa desfazer, não revalida retroativamente as etapas concluídas e não oferece retomada automática. O contador significa apenas *gravações previamente confirmadas*, não conclusão funcional do jogo.
- Os controles de autorização de dados do Explorer/scripts e da API são específicos da sessão e não são persistidos. A aprovação de um plano não autoriza todas as gravações futuras.

## Próximos trabalhos

1. Identidade única de arquivo/experiência fornecida pelo motor nativo, para evitar colisão entre projetos homônimos.
2. Testes reais de morte de processo/reabertura, corrupção intencional do checkpoint, exclusão da chave do Keystore e retentativas.
3. Toolbox somente leitura com análise estática de modelos e dependências antes de importar, sempre com confirmação.
4. Testes reais de Play e revisão visual das GUIs/objetos pelo app, com relatórios fiéis ao estado do editor.
