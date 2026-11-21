# Auditoria do RBX Studio Android 0.6.11 — 16/11/2026

**Alvo:** `Johnatafgfdgf/Rbxstudio`, branch `verify/android-menus`. Não altera o repositório original. A versão examinada é um aplicativo próprio, **não o Roblox Studio oficial**, construído com componentes de código aberto de terceiros. Identidade de produto e proveniência do código são coisas diferentes.

## Cobertura e evidência

- Pipeline de auditoria [43895724746](https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/43895724746): **aprovado**.
- O script `scripts/audit_repository.py` leu **247 arquivos rastreados do produto** e **4.457 arquivos rastreados dos três submódulos**, totalizando **95.607.487 bytes**. Incluiu **194 arquivos binários** no inventário. O relatório JSON contém caminho, tamanho, hash e localização dos achados.
- Foram detectadas **128 ocorrências textuais** compatíveis com o nome legado no produto; várias são caminhos internos, testes, diffs, histórico e licença. **Oito** foram marcadas automaticamente como possíveis textos de interface, incluindo falsos positivos de testes/scripts e a Activity histórica.
- As capturas nativas da edição `d45622f`, execução [43848418099](https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/43848418099), confirmaram Home, editor 3D, Menu, Ferramentas, Painéis e gaveta MCP, mas não comprovam todas as ações do programa.
- **Limitação do método:** leitura integral e busca estática automatizada não provam ausência de bugs de execução. Os submódulos foram inventariados integralmente, não reescritos nem submetidos a auditoria manual linha a linha.

## Problemas encontrados e ordem de tratamento

| Prioridade | Local | Evidência / impacto | Estado |
| --- | --- | --- | --- |
| Alta | `scripts/package_apk.py` | A chave de assinatura debug é gerada quando ausente. No runner descartável do GitHub isso pode impedir instalar uma nova versão por cima da anterior. Desinstalar antes de exportar projetos pode perder os dados. | **Em aberto**: precisa de chave persistente mantida em segredo; nunca publicá-la no repositório |
| Alta para release | `android/StudioManifest.xml` | `android:debuggable="true"` é padrão. Adequado ao APK de teste, inadequado para distribuição de produção. | Em aberto: usar build de release com `PORT_DEBUGGABLE=0` e chave de assinatura apropriada |
| Migração | `scripts/package_apk.py` / manifesto | O ID Android experimental ainda é `com.rbxnative.androidport.experimental`. Renomeá-lo cria outra instalação e não migra automaticamente dados ou credenciais. | Em aberto: definir novo ID, exportar/importar projetos e verificar instalação sem perda |
| Migração | `StudioServices.java`, `RookPlayerActivity.java` | Aliases internos do Keystore e do serviço de chave Open Cloud contêm o nome legado. São **identificadores internos**, não prova de requisição externa ao domínio citado. | Preservados até implementar leitura do alias antigo e regravação segura no novo |
| Alta para funcionalidade | `docs/ANDROID_AI_AGENT.md` | O painel MCP existe, mas não há LLM/chat integrado, executor autônomo geral nem validação real de `publish_animation`. | Em aberto; não anunciar como completo |
| Alta para qualidade | `scripts/studio_smoke.py`, UI GPUI | Navegação principal tem teste no emulador; scripts, importação da Toolbox, publicação, teclado e Play real ainda exigem testes Android mais amplos, inclusive telefone. | Em aberto |
| Média | `android/src/com/rbxnative/androidport/MainActivity.java` | Interface Java 0.3 continuava compilada no DEX do APK GPUI 0.6.11, embora não seja a Activity inicial. Contém títulos de diagnóstico antigos. | Alterado o empacotamento para não incluí-la no Studio |
| Média | `RbxGeometry.java` | O nome sugere legado, mas as classes atuais PlayLoadingView, StudioPlayer, RbxBrand e RookPlayerActivity **dependem** de constantes e vetores de geometria. Excluí-la quebra `javac`. | Mantida no APK até migração de símbolos; regressão localizada pelo CI e corrigida |
| Baixa | `native/src/android.rs`, `core/src/editor.rs`, `core/src/play.rs` | Mensagens/status/logs usavam identidade antiga. | Textos e tag de log corrigidos |
| Baixa | `patches/rbx-native--android-port.patch` | Nome/autor do tema, descrição do MCP `save_place` e campo `source` citavam produto antigo. | Strings visíveis corrigidas preservando nomes e contratos de ferramentas |
| Baixa | Nome do arquivo de saída | O APK do Studio era gerado com prefixo `rbx-native-android`. | Script renomeado para `rbx-studio-android` somente no aplicativo Studio; testes e workflows relevantes atualizados |
| Baixa | `README.md` | Instruções de empacotamento apontavam para 0.6.9 enquanto a versão atual é 0.6.11. | Corrigidas e acrescentados limites do MCP e aviso de assinatura |

## Alterações nesta auditoria

- `native/src/android.rs`: tag de log e estado de inicialização `RbxStudio`.
- `core/src/editor.rs`: estado `Rbx Studio · editor Rust`.
- `core/src/play.rs`: estado `Play local · servidor e cliente`.
- `patches/rbx-native--android-port.patch`: `Rbx Studio Dark` e metadados de ferramentas MCP com identidade do aplicativo.
- `scripts/package_apk.py`: exclui classes históricas não usadas da compilação GPUI; mantém `RbxGeometry`, necessária ao layout/Play; renomeia o APK do Studio e o nome da nova certificação debug.
- `scripts/studio_smoke.py`, workflows de build e `README.md`: usam o novo nome de saída.
- `scripts/check_studio_branding.py`: teste estático de regressão antes do build.
- `scripts/audit_repository.py` e `.github/workflows/audit-repository.yml`: inventário completo automatizado, atualizado em novos commits.

**Atenção:** uma execução intermediária de reempacotamento falhou ao excluir `RbxGeometry`. O erro `cannot find symbol RbxGeometry` mostrou que a classe era necessária; `f70736e8` removeu essa exclusão. O novo APK e as capturas posteriores precisam passar novamente pelo CI antes de serem distribuídos como atualização validada.

## O que significa “sem envolvimento do rbx-native”

É possível ter **identidade pública independente**, interface própria, nome de APK, ícone e ferramentas próprios sem usar a marca antiga em telas. Contudo, este projeto continua usando `upstream/rbx-native` (editor, DOM, renderer e codecs) e `upstream/rook` (Play/física), conforme `.gitmodules` e `Cargo.toml`. Remover toda dependência técnica significa substituir esses módulos com implementações equivalentes, testar formatos, renderização, física, scripts e publicação. Isso seria uma migração arquitetural extensa, não um simples “buscar/substituir”.

Atribuições de copyright e licenças MIT/Apache precisam ser mantidas nos arquivos legais e no APK conforme as condições aplicáveis. Não excluir nem falsificar a origem do código. Também **não** renomear IDs de aplicativo, caminhos de dados ou aliases de credenciais existentes antes de estabelecer migração de dados testada.

## Próximos testes necessários

1. Novo build ARM64: verificar compilação, APK assinado, ABI, hash, pacote e novo nome de arquivo.
2. Emulador x86: validar captura real, criação de projeto, Explorer/Properties, Toolbox, IA e gestos sem regressão.
3. Celular real: editor Luau, teclado, rolagem, importação, salvar/reabrir, Play, publicação em experiência de teste e recuperação de dados.
4. Planejar migração do ID Android e credenciais antigas antes de qualquer limpeza profunda de nomes internos.
5. Definir chave de assinatura estável e segura para atualizações, sem publicá-la em código ou artefatos públicos.

A auditoria não certifica funcionalidade integral nem total independência técnica do código original. Seu resultado é um inventário reproduzível, alterações cirúrgicas e pendências claramente delimitadas.

## Auditoria binária e continuação da identidade visual (16/11)

A inspeção do APK ARM64 real da execução [43895849504](https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/43895849504) verificou o arquivo `classes.dex`, as bibliotecas `.so` e as licenças empacotadas. O DEX não inclui `MainActivity`, `NativeSmokeTest`, `PlayerPad` nem `StudioIcon` legados; inclui a geometria necessária ao player e preserva as atribuições legais. Apenas duas strings `rbx-native` foram encontradas no DEX: o alias privado de credenciais e o identificador de serviço da chave Open Cloud, ainda necessários para compatibilidade.

A biblioteca Rust compilada, entretanto, carregava diversas strings herdadas, inclusive os temas `rbx-native Light` / `rbx-native High contrast`, o `serverInfo.name` do MCP e mensagens públicas de Loren, UGC, geração de assets e Team Presence. O inventário inicial de fontes não identificava essas strings porque estão no submódulo de terceiros. Foram adicionadas substituições explícitas ao patch Android consolidado: `57c36b23` (temas e protocolo MCP com expectativas de testes) e `df840391` (mensagens de UI e erros). Os protocolos, APIs, nomes Luau internos e prefixos de replicação não foram renomeados para evitar quebrar compatibilidade.

O autoupdater encontrado em buscas ao código público **não está presente no commit fixado** do submódulo deste APK (`eb85a1088a69f4159598b4fed0ae26956573c175`); não há evidência de que seja executado por esta versão. Não misturar achados do ramo mais novo do upstream com falhas comprovadas na versão empacotada.

A assinatura de atualização agora aceita um keystore privado persistente através de segredos do GitHub (`072cbe6d` e workflows relacionados). Sem um segredo configurado, a compilação de teste ainda gera chave temporária e **não garante atualização sobre APK anterior**. Foi acrescentado `scripts/check_studio_apk.py` para fiscalizar o binário real, inclusive as classes antigas e os textos de interface, além de `scripts/check_studio_branding.py` reforçado. A configuração de um keystore privado consistente e a migração do ID Android/credenciais seguem pendentes. Não guardar chaves no repositório.

**Validação:** a auditoria estática da revisão com esses patches passou em [43900399477](https://github.com/Johnatafgfdgf/Rbxstudio/actions/runs/43900399477). Os builds nativos e a captura Android ainda estavam em execução quando esta seção foi escrita; não considerar as strings reempacotadas comprovadas em um novo APK sem revisar os artefatos dessas execuções.
