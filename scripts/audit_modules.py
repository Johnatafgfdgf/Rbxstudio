#!/usr/bin/env python3
"""Inventory every crate and source hash without equating presence with integration."""
import hashlib,json,pathlib,subprocess,tomllib,sys
root=pathlib.Path(__file__).resolve().parents[1]
full='--full' in sys.argv
active={
'rbx_animation':'Animator/Rig/KeyframeSequence called for imported clips; no default walk assets bundled',
'rbx_assets':'Original resource/cache APIs plus local Android seekable APK adapter',
'rbx_binary':'Original editor file codec','rbx_xml':'Original editor file codec',
'rbx_cloud':'Renderer asset provider; login/publication UI not adapted',
'rbx_dom':'Authoritative edit/save document and temporary render projection',
'rbx_lua':'Original edit command bar; Play scripts use Rook VMs',
'rbx_materials':'Original renderer materials','rbx_mesh':'Original renderer mesh types/codecs',
'rbx_reflection':'Original editor metadata','rbx_terrain':'Original terrain render data; sculpting UI pending',
'rbx_viewer':'Single original renderer, GPU presentation, selection and gizmos',
'rbx_studio':'Domain files extracted with hashes by prepare.py; GPUI desktop shell excluded',
'rook-engine':'Original Session/server/client/host called during Play',
'rook-lua':'Original runtime, scheduler, APIs and signals in Play; upstream coverage remains partial',
'rook-net':'Original local replication/remotes through Session; Internet transport not exposed',
'rook-physics':'Original Rapier physics, collision, Humanoid and joints',
'rook-dom':'Original runtime server/client DOMs, never saved over the editor project',
'rook-types':'Original runtime value/math types','rook-reflection':'Original runtime metadata',
'rook-formats':'Original loader/conversion; file_property adapter preserves original encodings',
'rook-content':'Original resolver plus shared local Android resource callback',
'rook-audio':'Original engine audio graph/decoder linked; Android output is not connected',
'rook-gui':'Original engine layout/input; visual and widget coverage partial',
'rook-platform':'Original portable files/threads/HTTP; desktop clipboard/keyring excluded',
'rook-terrain':'Original engine terrain/collision dependency; extensive worlds not validated',
'rook-render':'Only unchanged procedural head extracted; the Rook renderer is excluded',
}
if full:
 active.update({
 'rbx_studio':'Editor original compilado/linkado para ARM64/x86_64; Home e tela cheia capturados no Android 35; carregamento assíncrono e ciclo de superfície passam check nas duas ABIs; nova cena/retomada/Play visual em validação',
 'rbx_viewer':'Renderer original integrado ao GPUI; Vulkan perde o dispositivo ao abrir mapa no emulador, inclusive com qualidade inicial10; GL falha ao traduzir leitura de profundidade; apresentação 3D ainda não aprovada',
 'rbx_assets':'Leitor APK/ZIP local e fallback para os pacotes oficiais Rook, com prioridade local e modo offline separado',
 'rbx_cloud':'Views originais de conta/publicação incluídas; Keystore Android validado; fluxos Roblox ainda não validados no Android',
 'rbx_audit':'Dock original de auditoria incluído na aplicação GPUI; fluxo Android ainda não validado',
 'rbx_autorig':'Ferramentas originais de rig incluídas na aplicação GPUI; fluxo Android ainda não validado',
 'rbx_eyedropper':'Código original incluído; captura fora da própria janela exige adaptação Android',
 'rbx_figma':'Fluxo original incluído; login/importação Figma no Android ainda não validados',
 'rbx_import':'Importador original incluído; seletor/espelho SAF validado separadamente, importação 3D ainda não validada',
 'rbx_loren':'Ferramentas originais incluídas; sincronização completa de projetos SAF ainda precisa ser adaptada',
 'rbx_presence':'UI/protocolo originais incluídos; colaboração Android ainda não validada',
 'rbx_ugc':'Ferramentas originais incluídas; publicação autenticada Android ainda não validada',
 'rbx_wasm':'Módulo original compilado na aplicação; execução/importação Android ainda não validada',
 'rbx_terrain':'Ferramentas originais de terreno incluídas na UI; fluxo Android ainda não validado',
 'rbx_play':'Play Android ligado por snapshot ao player Rook original; passagem de sessão compilada, execução/depuração local ainda em validação; auxiliares ausentes',
 'rbx_parser_cli':'Executável CLI não portado ao host Android',
 'rook-engine':'Session, 25 CoreScripts oficiais móveis, direcional/pulo simultâneos e cancelamento validados no motor; Stop preserva o documento; Play GPUI ainda sem validação visual',
 'rook-lua':'VM/scheduler/APIs originais em uso; emotes com estado/Clone/sinais; GetTextBoundsAsync pelo shaper original validado com fontes oficiais, sem fallback de tamanho; cobertura upstream parcial',
 'rook-content':'Downloader oficial original ligado; manifesto/extração/CorePatch, fontes originais e reutilização offline validados com pacotes reais',
 'rook-platform':'No host GPUI, clipboard e credenciais conectados aos serviços Android; Keystore/SAF sobreviveram a force-stop nos testes',
 'rook-gui':'Layout/input originais usados pelo runtime; CoreScripts executam, fidelidade visual Android ainda não validada',
 'rook-render':'Renderer original ligado à superfície NativeActivity do player original e linkado nos APKs ARM64/x86_64; apresentação Android ainda sem evidência',
 'rook-view':'SceneCache/Installed ligados pelo frame loop do player original Android; execução ainda em validação',
 'rook-audio':'Output.sync ligado no player original e dispositivo liberado/restaurado no ciclo Android; reprodução no Android ainda não validada',
 'rook-player':'Host Android adicionado à aplicação original, preservando Game/servidor/renderer/áudio; bibliotecas reais ARM64/x86_64 compiladas/linkadas/empacotadas; execução visual ainda em validação',
 'rook-cli':'Aplicação CLI original não portada ao host Android; preservada no submódulo',
 })
rows=[]
for repo in ['rbx-native','rook']:
 base=root/'upstream'/repo;commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=base,text=True).strip()
 manifests=list((base/'crates').glob('*/Cargo.toml'))
 if full:manifests+=list((base/'apps').glob('*/Cargo.toml'))
 for manifest in sorted(manifests):
  data=tomllib.loads(manifest.read_text());pkg=data['package'];files=sorted(manifest.parent.rglob('*.rs'));name=pkg['name']
  rows.append({'repository':repo,'commit':commit,'crate':name,'rust_files':len(files),'source_lines':sum(len(p.read_text().splitlines()) for p in files),'integration':active.get(name,'Not integrated in the Android application'),'modules':[{'path':str(p.relative_to(base)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files]})
inventory='full-module-inventory.json' if full else 'module-inventory.json'
(root/'docs'/inventory).write_text(json.dumps(rows,indent=2)+'\n')
lines=['# Inventário dos dois projetos — '+('port integral em desenvolvimento' if full else '0.3'),'',f'Este inventário percorre todos os manifests e arquivos Rust dos fontes preparados a partir dos commits fixados. Cada hash está em `{inventory}`. Contar, ler ou compilar arquivos não comprova compatibilidade nem compreensão de todos os comportamentos. A coluna descreve pontos realmente conectados ao aplicativo.','','| Origem | Crate | Arquivos Rust | Linhas | Integração |','|---|---|---:|---:|---|']
for r in rows:lines.append(f'| {r["repository"]} | {r["crate"]} | {r["rust_files"]} | {r["source_lines"]} | {r["integration"]} |')
(root/'docs'/('FULL_PORT_AUDIT.md' if full else 'MODULE_AUDIT.md')).write_text('\n'.join(lines)+'\n')
print(json.dumps({'crates':len(rows),'rust_files':sum(r['rust_files'] for r in rows)}))
