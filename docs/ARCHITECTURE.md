# Arquitetura 0.3 e procedência

Os submódulos continuam fixados: rbx-native `eb85a1088a69f4159598b4fed0ae26956573c175` (MIT) e Rook `8680da0fde3ded84731b7422c59de26db0e14135` (Apache-2.0). A aplicação preserva o editor e o renderer rbx-native; a Activity/SurfaceView/JNI adapta a plataforma Android.

Em edição, o DOM e os codecs rbx-native são autoritativos. O command bar usa `rbx_lua`. Módulos de domínio do Studio são extraídos por `scripts/prepare.py`, com hashes registrados. O shell GPUI desktop não foi portado.

Em Play, o XML original alimenta o loader Rook; `Session` original executa servidor e cliente em VMs separadas. Os sistemas de scripts, física/Humanoid, respawn, replicação, remotes, GUI e resolução de conteúdo são os do engine original. O cliente fornece uma projeção visual ao mesmo renderer rbx-native. O journal é consumido antes de ser podado; mudanças comuns usam patches de propriedades. Alterações estruturais exigem conversão completa. A projeção nunca é salva; Parar restaura o snapshot original.

`patches/rbx-native--android-port.patch` reúne Surface GPU, limites de adaptador, MSAA de gizmos, APIs de host do command bar, recursos Android, reload e fallback da cabeça. `patches/rook--android-port.patch` reúne o host std sem dependências desktop, journal do renderer, conteúdo local e encoding original de propriedades. Os patches consolidados têm precedência sobre os patches antigos mantidos como histórico. O preparo foi conferido duas vezes em fontes limpas, com comparação byte a byte contra a cópia de trabalho.

`geometry` converte somente o layout de vértices da cabeça procedural original Rook; o algoritmo original é copiado sem alteração e recebe hash de procedência. Não há segundo renderer. `core/animation.rs` usa Animator/Rig/KeyframeSequence originais, aplicando Transform em Motor6D do Rook. Recursos de caminhada não são inventados nem redistribuídos.

A apresentação mantém a cena na GPU e usa uma textura sRGB intermediária quando a Surface Android não suporta views mutáveis. Não há readback CPU por frame. A resolução interna ajustável e os painéis sobrepostos da 0.2 permanecem. A interface respeita as barras de navegação, recortes e o teclado do editor.

`AndroidContent` estende o leitor original de APK do rbx-native para arquivos seekable e pacotes divididos. Recursos locais têm prioridade sobre cache antigo por caminho. A importação ocorre em segundo plano, valida o arquivo antes da troca e reinicia as falhas de carregamento do renderer. Assets privados continuam seguindo as regras de autenticação dos provedores originais.

Veja `PLAY_API.md`, `FEATURE_MATRIX.md` e `MODULE_AUDIT.md` para limites e `TEST_REPORT.md` para resultados. A presença de um arquivo ou crate não significa que todas as suas funções foram adaptadas, compreendidas ou testadas.

A resolução interna é o espaço lógico da GUI do jogo: os toques da Surface são convertidos para esse mesmo espaço, inclusive ao mudar a resolução. Os controles e painéis Android permanecem em pixels nativos.

A projeção marca apenas o PlayerGui de runtime para o planejador original e oculta o StarterGui de templates. Sem essa marca, o renderer de edição excluiria a GUI do jogador. A marca é descartada no Stop e nunca altera o projeto salvo.

O encoder ISPC original oferece binários Android ARM64, mas não x86_64. Somente nesse alvo de emulador, o método de compressão conserva os pixels e mips RGBA originais; a decodificação BC continua disponível. O encoder original ARM64 não é alterado. Isso remove a dependência de um kernel inexistente sem substituir imagens ou codecs.
