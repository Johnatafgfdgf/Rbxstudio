# RBX Studio Android — Agente IA: memória por projeto e continuação guiada

**Branch de trabalho:** `work/ai-autonomous-read-v1` • continuação incremental da fase de read-back, sem refazer o aplicativo nem alterar a `main`.

## O que foi codificado

1. `StudioAiHub.java` reutiliza `StudioServices.credential` e o Android Keystore (AES-GCM) para salvar um diário local **opcional**, por nome exato do projeto. A chave do registro é um SHA-256 do título; o registro criptografado inclui versão, título e até 16 eventos.
2. Eventos contêm apenas **tipo de operação, caminho/nome do objeto e timestamp**. Não salvam código Luau, respostas do modelo, prompts do usuário, API Keys, capturas nem tokens MCP. Apenas uma gravação confirmada pela segunda leitura MCP pode entrar no diário. Respostas inconclusivas ficam só na sessão, com alerta para não repetir a operação automaticamente.
3. A interface inclui dois consentimentos independentes, inicialmente **desligados**: registrar modificações verificadas e enviar o diário à IA. Há ações para consultar e apagar o registro do projeto. Sem título de projeto apropriado, o controle é desabilitado.
4. `Continuar tarefa anterior • usa a API` aproveita o histórico da sessão, pede nova inspeção MCP antes de qualquer proposta e continua exigindo aprovação manual. Não executa `multi_edit`, `execute_luau` nem ações de publicação automaticamente.
5. Resultados de operações aprovadas são anexados à **última resposta do assistente** na sessão, preservando a alternância de papéis necessária em APIs como Gemini/Claude.
6. Revogar consentimento de código, projeto ou memória invalida leituras de IA em andamento, interrompe conexões locais/HTTP quando possível e limpa os turnos anteriores para que dados revogados não sejam reenviados. **Uma chamada já transmitida à API não pode ser recuperada.** Uma gravação MCP já aprovada não é cancelada à força, e seu resultado não volta ao histórico compartilhado se o consentimento do Explorer foi retirado.

## Limites honestos

- A identificação persistente usa **o nome exato do projeto**, não um ID único de lugar; **dois projetos diferentes com o mesmo título podem compartilhar o diário**. O app mostra esse aviso. A integração futura deve usar um ID estável do projeto/arquivo, quando disponível.
- O diário **não é backup, histórico completo de desfazer, memória semântica ou retomada automática após reiniciar**. Ele serve de contexto de alterações confirmadas e não prova que os objetos continuam presentes.
- O botão Continuar funciona somente com o histórico da sessão. Para usar o diário após reabrir, é necessário autorizar o envio e especificar a tarefa.
- Nenhum código Luau ou script da Toolbox é executado com base apenas em dados antigos; o agente deve inspecionar o estado atual do editor.
- A verificação por leitura confirma objetos e propriedades, mas não prova funcionamento completo em Play, qualidade da cena ou estabilidade no Android físico.

## Verificação

`scripts/check_ai_hub.py` valida os contratos de aprovação, ausência de execução silenciosa, Keystore, consentimento explícito, número máximo de eventos, higiene dos turnos e conexão do diário às sete classes de operações com read-back.

O workflow `ai-hub-android-java.yml` executa contratos estáticos e compila a Activity com o SDK Android 35. O workflow experimental ARM64 prepara o APK separadamente. **Não marcar como aprovado até consultar a conclusão da execução dessa revisão.**

## Próximos trabalhos

- Obter um identificador estável do projeto.
- Guardar planos e checkpoints seguros para continuar após fechar o app, de forma opt-in e sem registrar código/credenciais.
- Validar integração real entre memória, MCP, múltiplos provedores e Android físico.
- Construir testes em Play, Toolbox segura e relatórios de desempenho, mantendo autonomia graduada e permissões.
