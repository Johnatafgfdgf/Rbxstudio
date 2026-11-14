(() => {
  "use strict";
  const root = document.getElementById("rbx-mobile-review");
  const app = root.querySelector(".app");
  const data = JSON.parse(root.querySelector("script[type='application/json']").textContent);
  const byId = name => root.querySelector("#rbx-" + name);
  const homeBody = byId("home-body");
  const drawer = byId("drawer");
  const drawerBody = byId("drawer-body");
  const sheet = byId("sheet");
  const popup = byId("popup");
  const shade = byId("shade");
  const dialogShade = byId("dialog-shade");
  const homeScreen = root.querySelector(".home-screen");
  const editorScreen = root.querySelector(".editor-screen");
  const workspace = root.querySelector(".workspace");
  const animations = new WeakMap();
  const reduced = matchMedia("(prefers-reduced-motion: reduce)");
  const tools = data.tools;
  const panelNames = data.panels;
  const transforms = [
    ["Selecionar", "mouse-pointer-2", "select"],
    ["Mover", "move-3d", "move"],
    ["Girar", "rotate-3d", "rotate"],
    ["Escala", "scaling", "scale"],
  ];
  const state = {
    page: "home", homeTab: "home", project: "Baseplate", recovered: true, recent: [],
    sheet: null, popup: null, tab: "Home", panel: null, expanded: false, width: 0,
    tool: "select", selection: null, query: "", queryMode: false, allServices: false,
    lightIcons: false, commandBar: false, reduced: false, clipboard: null, undo: [], redo: [],
    expandedNodes: new Set(["Workspace"]), nodes: [], saved: false, localAxes: false, stick: false,
  };
  let toastTimer;
  let nextId = 0;
  let dialogReturn = null;
  let popupTrigger = null;
  const id = () => "node-" + (++nextId);

  function element(tag, className = "", text = "") {
    const node = document.createElement(tag);
    node.className = className;
    if (text) node.textContent = text;
    return node;
  }

  function glyph(name) {
    const icon = element("span", "glyph");
    icon.setAttribute("aria-hidden", "true");
    icon.style.setProperty("--glyph", "var(--icon-" + name + ")");
    return icon;
  }

  function button(label, action, icon, className = "control", options = {}) {
    const node = element("button", className);
    node.type = "button";
    node.dataset.action = action;
    node.setAttribute("aria-label", label);
    if (icon) node.append(glyph(icon));
    if (!options.iconOnly) node.append(element("span", "label", label));
    if (options.pressed !== undefined) node.setAttribute("aria-pressed", String(options.pressed));
    if (options.checked !== undefined) {
      node.setAttribute("role", "menuitemcheckbox");
      node.setAttribute("aria-checked", String(options.checked));
      if (options.checked) node.append(glyph("check"));
    }
    if (options.disabled) node.disabled = true;
    return node;
  }

  function hydrateButtons() {
    for (const node of root.querySelectorAll("button[data-icon]")) {
      const text = node.textContent.trim();
      node.type = "button";
      if (!node.hasAttribute("aria-label") && text) node.setAttribute("aria-label", text);
      node.replaceChildren(glyph(node.dataset.icon), ...(text ? [element("span", "label", text)] : []));
    }
  }

  function announce(message) {
    const toast = byId("status");
    clearTimeout(toastTimer);
    toast.textContent = message;
    toast.classList.add("visible");
    toastTimer = setTimeout(() => toast.classList.remove("visible"), 2400);
  }

  function motion(node, show, axis = "y", shift = -data.motion.sheetShift) {
    if ((!show && node.hidden) || (show && !node.hidden && !node.inert)) return;
    const previous = animations.get(node);
    const from = node.hidden
      ? { opacity: "0", transform: axis === "x" ? "translateX(" + shift + "px)" : "translateY(" + shift + "px)" }
      : { opacity: getComputedStyle(node).opacity, transform: getComputedStyle(node).transform };
    previous?.cancel();
    node.hidden = false;
    node.inert = !show;
    if (reduced.matches || state.reduced) {
      node.hidden = !show;
      return;
    }
    const end = axis === "x" ? "translateX(" + shift + "px)" : "translateY(" + shift + "px)";
    const animation = node.animate(
      [from, { opacity: show ? 1 : 0, transform: show ? "none" : end }],
      { duration: show ? data.motion.openMs : data.motion.closeMs, easing: "cubic-bezier(.333,1,.667,1)", fill: "both" },
    );
    animations.set(node, animation);
    animation.onfinish = () => {
      if (animations.get(node) !== animation) return;
      node.hidden = !show;
      animations.delete(node);
      animation.cancel();
    };
  }

  function animatePage(node) {
    if (reduced.matches || state.reduced) return;
    node.animate([{ opacity: 0, transform: "translateY(8px)" }, { opacity: 1, transform: "none" }],
      { duration: data.motion.openMs, easing: "cubic-bezier(.333,1,.667,1)" });
  }

  function homeCard(label, description, action, icon, image) {
    const card = button(label, action, null, "project-tile surface" + (action === "open-file" ? " file-tile" : ""));
    const thumb = image ? element("img", "thumbnail") : element("span", "thumbnail placeholder");
    if (image) {
      thumb.alt = "";
      thumb.src = image;
    } else {
      thumb.append(glyph(icon));
    }
    const copy = element("span", "project-label");
    copy.append(element("strong", "", label), element("small", "", description));
    card.replaceChildren(thumb, copy);
    return card;
  }

  function renderHome() {
    const title = { home: "Início", recent: "Recentes", games: "Meus jogos" }[state.homeTab];
    byId("page-title").textContent = title;
    for (const tab of root.querySelectorAll("[data-action^='home-tab:']")) {
      tab.setAttribute("aria-pressed", String(tab.dataset.action === "home-tab:" + state.homeTab));
    }
    homeBody.replaceChildren();
    homeBody.scrollTop = 0;
    if (state.homeTab === "home") {
      const hero = element("div", "hero surface");
      const copy = element("div", "hero-copy");
      const mark = element("span", "mark");
      mark.setAttribute("aria-hidden", "true");
      copy.append(element("h2", "", "Bem-vindo ao Rbx Studio!"), element("p", "", "Crie, edite e publique seus jogos no Roblox."));
      hero.append(copy, mark, button("Novo jogo", "new:Baseplate", "plus", "control primary"));
      const projects = element("div", "project-grid");
      for (const [index, template] of data.templates.entries()) {
        projects.append(homeCard(template[0], template[1], "new:" + template[0], null, data.images[index]));
      }
      projects.append(
        homeCard("Loren project", "Services & Controllers, by fabi", "new:Loren project", "flame"),
        homeCard("Abrir arquivo…", ".rbxl ou .rbxlx do seu dispositivo", "open-file", "folder-open"),
      );
      homeBody.append(hero, element("h2", "", "Novo"), projects);
      sizeLayout();
      if (state.recovered) {
        const recovery = element("div", "surface recovery");
        const label = element("span", "project-label");
        label.append(element("strong", "", "Baseplate 13"), element("small", "", "Recuperado automaticamente"));
        recovery.append(glyph("file"), label, button("Abrir", "recover", null, "control selected"), button("Descartar", "discard"));
        homeBody.append(element("h2", "", "Lugares recuperados"), recovery);
      }
    }
    if (state.homeTab === "recent" || (state.homeTab === "home" && state.recent.length)) {
      homeBody.append(element("h2", "", "Recentes"));
      if (state.recent.length) {
        for (const name of state.recent) {
          const recent = element("div", "surface recovery");
          recent.append(glyph("file"), element("span", "project-label", name), button("Abrir", "recent:" + name));
          homeBody.append(recent);
        }
      } else {
        homeBody.append(element("p", "", "Nenhum arquivo recente nesta prévia."));
      }
    }
    if (state.homeTab !== "recent") {
      const hint = element("div", "surface key-hint");
      hint.append(glyph("key-round"), element("p", "", "Arquivos locais funcionam sem conta. Conecte sua chave para publicar no Roblox."),
        button("Conectar", "account", null, "control selected"));
      homeBody.append(hint);
    }
    animatePage(homeBody);
  }

  function blankTree() {
    return [
      { id: "Workspace", label: "Workspace", class: "Workspace", children: [
        { id: "Terrain", label: "Terrain", class: "Terrain" },
        { id: "Camera", label: "Camera", class: "Camera" },
        { id: "Baseplate", label: "Baseplate", class: "Part" },
        { id: "SpawnLocation", label: "SpawnLocation", class: "SpawnLocation" },
      ] },
      ...["Players", "Lighting", "ReplicatedFirst", "ReplicatedStorage", "ServerScriptService", "ServerStorage",
        "StarterGui", "StarterPack", "StarterPlayer", "Teams", "SoundService", "TextChatService"]
        .map(name => ({ id: name, label: name, class: name, children: [] })),
    ];
  }

  function enterEditor(project) {
    state.page = "editor";
    state.project = project;
    state.nodes = blankTree();
    state.recent = [project, ...state.recent.filter(name => name !== project)];
    state.panel = null; state.expanded = false; state.sheet = null; state.popup = null;
    state.selection = null; state.clipboard = null; state.saved = false; state.query = "";
    state.undo = []; state.redo = []; state.expandedNodes = new Set(["Workspace"]);
    for (const layer of [drawer, sheet, popup, shade]) {
      animations.get(layer)?.cancel();
      layer.hidden = true; layer.inert = true;
    }
    homeScreen.hidden = true; editorScreen.hidden = false;
    app.dataset.page = "editor";
    renderControls();
    animatePage(editorScreen);
  }

  function leaveEditor() {
    closePopup(false);
    state.page = "home"; state.homeTab = "home";
    app.dataset.page = "home";
    homeScreen.hidden = false; editorScreen.hidden = true;
    renderHome();
    root.querySelector("[data-action='new:Baseplate']")?.focus({ preventScroll: true });
  }

  function renderControls() {
    const transformButtons = transforms.map(([label, icon, tool]) =>
      button(label, "tool:" + tool, icon, "control icon-only", { iconOnly: true, pressed: state.tool === tool }));
    root.querySelector(".transform-wide").replaceChildren(...transformButtons);
    const active = transforms.find((item) => item[2] === state.tool);
    root.querySelector("[data-action='active-tool']").replaceChildren(glyph(active?.[1] || "move-3d"));
    for (const action of ["menu", "tools", "panels"]) {
      const open = action === "panels" ? state.popup === "panels" : state.sheet === action;
      root.querySelector("[data-action='" + action + "']").setAttribute("aria-expanded", String(open));
    }
    const save = root.querySelector("[data-action='save']");
    save.replaceChildren(glyph(state.saved ? "check" : "save"), element("span", "label", state.saved ? "Salvo" : "Salvar"));
    save.setAttribute("aria-label", "Salvar");
  }

  function openSheet(type) {
    closePopup(false);
    state.sheet = state.sheet === type ? null : type;
    if (state.sheet) renderSheet();
    motion(sheet, Boolean(state.sheet));
    motion(shade, state.sheet === "menu", "y", 0);
    renderControls();
  }

  function renderSheet() {
    sheet.replaceChildren();
    if (state.sheet === "menu") {
      const categories = element("div", "menu-titles");
      for (const name of Object.keys(data.menus)) {
        categories.append(button(name, "main-menu:" + name, null, "control",
          { pressed: state.popup === "main:" + name }));
      }
      const documents = element("div", "document-strip");
      documents.append(button(state.project, "scene", "box", "control selected"),
        button("Script Editor", "script-editor", "file-code"),
        button("Novo ou abrir documento", "document-add", "plus", "control icon-only", { iconOnly: true }));
      sheet.append(categories, documents);
    } else if (state.sheet === "tools") {
      const tabs = element("div", "tool-tabs");
      for (const tab of data.tabs) {
        tabs.append(button(tab, "ribbon-tab:" + tab, null, "control flat", { pressed: state.tab === tab }));
      }
      const actions = element("div", "tool-items");
      for (const group of tools[state.tab]) {
        const row = element("div", "tool-group");
        for (const [label, icon, action, disabled] of group) {
          row.append(button(label, action ? "ribbon:" + action : "sample:" + label, icon,
            "control tile-action", { disabled }));
        }
        actions.append(row);
      }
      sheet.append(tabs, actions);
    }
  }

  function closeSheet() {
    if (!state.sheet) return;
    state.sheet = null;
    motion(sheet, false);
    motion(shade, false, "y", 0);
    renderControls();
  }

  function openPopup(kind, trigger, width, options, alignRight = false) {
    if (state.popup === kind) { closePopup(); return; }
    state.popup = kind;
    popupTrigger = trigger;
    const rect = workspace.getBoundingClientRect();
    const anchor = trigger.getBoundingClientRect();
    const px = Math.min(width, rect.width - 16);
    const left = Math.max(8, Math.min(alignRight ? anchor.right - rect.left - px : anchor.left - rect.left, rect.width - px - 8));
    const top = Math.max(6, Math.min(anchor.bottom - rect.top + 5, rect.height / 3));
    popup.style.setProperty("--popup-width", width + "px");
    popup.style.setProperty("--popup-left", left + "px");
    popup.style.setProperty("--popup-top", top + "px");
    popup.setAttribute("role", "group");
    popup.setAttribute("aria-label", trigger.getAttribute("aria-label") || trigger.textContent.trim());
    popup.replaceChildren(...options);
    popup.scrollTop = 0;
    motion(popup, true);
    trigger.setAttribute("aria-expanded", "true");
    popup.querySelector("button:not(:disabled)")?.focus({ preventScroll: true });
    renderControls();
  }

  function closePopup(restoreFocus = true) {
    if (!state.popup) return;
    state.popup = null;
    popupTrigger?.setAttribute("aria-expanded", "false");
    if (restoreFocus) popupTrigger?.focus({ preventScroll: true });
    popupTrigger = null;
    motion(popup, false);
    renderControls();
  }

  function menuItems(rows, prefix, icon, checked) {
    return rows.map(row => row === null ? element("div", "divider")
      : button(row, prefix + row, icon?.[row], "menu-row", { checked: checked?.[row] }));
  }

  function sizeLayout() {
    const width = root.clientWidth;
    drawer.style.setProperty("--drawer-width", Math.min(380, Math.max(260, Math.min(width * .4, state.width || width * .36))) + "px");
    const grid = homeBody.querySelector(".project-grid");
    if (grid) grid.style.setProperty("--columns", Math.max(1, Math.min(5, Math.floor((width - 24 + 16) / 191))));
  }

  function setPanel(panel) {
    closePopup(false);
    closeSheet();
    state.panel = panel === state.panel ? null : panel;
    if (state.panel) renderDrawer();
    motion(drawer, Boolean(state.panel), "x", data.motion.drawerShift);
  }

  function renderDrawer() {
    if (!state.panel) return;
    const expand = drawer.querySelector("[data-action='expand-panel']");
    drawer.dataset.expanded = String(state.expanded);
    drawer.dataset.bottom = String(state.panel === "Output");
    drawer.setAttribute("aria-label", state.panel);
    expand.replaceChildren(glyph(state.expanded ? "minimize-2" : "maximize-2"));
    expand.setAttribute("aria-label", state.expanded ? "Reduzir painel" : "Ampliar painel");
    const tabButtons = [["Explorer", "list-tree"], ["Properties", "sliders-horizontal"], ["Toolbox", "package"]]
      .map(([name, icon]) => {
        const selected = state.panel === name;
        const labeled = selected && name === "Explorer" && (state.expanded ? root.clientWidth : drawer.clientWidth) >= 325;
        return button(name, "drawer-tab:" + name, icon, "control" + (labeled ? "" : " icon-only"), { pressed: selected, iconOnly: !labeled });
      });
    if (!["Explorer", "Properties", "Toolbox"].includes(state.panel)) tabButtons.push(element("span", "drawer-notice", state.panel));
    drawer.querySelector(".drawer-tabs").replaceChildren(...tabButtons);
    drawerBody.replaceChildren();
    if (state.panel !== "Explorer") {
      drawerBody.append(element("p", "drawer-notice", "Conteúdo interno reservado para outro lote."));
      return;
    }
    const settings = element("div", "drawer-actions");
    settings.append(button("Opções do Explorer", "explorer-settings", "ellipsis", "control icon-only", { iconOnly: true }));
    const explorer = element("div", "explorer-body");
    const filter = element("div", "explorer-filter");
    const search = element("input");
    search.type = "search"; search.value = state.query;
    search.placeholder = state.queryMode ? "Part where Anchored = false" : "Search";
    search.setAttribute("aria-label", "Buscar no Explorer");
    search.dataset.explorerSearch = "";
    filter.append(search, button("Query mode", "query-mode", "code", "control icon-only", { iconOnly: true, pressed: state.queryMode }));
    const tree = element("div", "tree");
    tree.setAttribute("role", "group"); tree.setAttribute("aria-label", "Hierarquia do projeto");
    const actions = element("div", "explorer-actions");
    actions.setAttribute("aria-label", "Ações do Explorer");
    explorer.append(filter, tree, actions);
    drawerBody.append(settings, explorer);
    renderTree();
  }

  function locate(nodeId, nodes = state.nodes, parent = null) {
    for (const item of nodes) {
      if (item.id === nodeId) return { item, nodes, parent };
      const found = locate(nodeId, item.children || [], item);
      if (found) return found;
    }
    return null;
  }

  function renderTree() {
    const tree = drawerBody.querySelector(".tree");
    if (!tree) return;
    const scroll = tree.scrollTop;
    const query = state.query.trim().toLocaleLowerCase();
    const matches = item => (item.label + " " + item.class).toLocaleLowerCase().includes(query)
      || (item.children || []).some(matches);
    const visit = (item, depth) => {
      if (query && !matches(item)) return;
      const row = element("div", "tree-line" + (state.selection === item.id ? " selected" : ""));
      row.style.setProperty("--depth", depth);
      const branch = Boolean(item.children?.length);
      const expanded = state.expandedNodes.has(item.id);
      if (branch) {
        const arrow = button((expanded ? "Recolher " : "Expandir ") + item.label, "expand-node:" + item.id,
          expanded ? "chevron-down" : "chevron-right", "tree-arrow", { iconOnly: true });
        arrow.setAttribute("aria-expanded", String(expanded));
        row.append(arrow);
      } else {
        row.append(element("span", "tree-arrow"));
      }
      const select = button(item.label, "select-node:" + item.id, null, "tree-node",
        { pressed: state.selection === item.id });
      const icon = element("img", "class-glyph");
      icon.alt = "";
      icon.src = (state.lightIcons ? data.lightClassIcons : data.classIcons)[item.class] || data.classIcons.Part;
      select.prepend(icon);
      row.append(select);
      tree.append(row);
      if (branch && (expanded || query)) for (const child of item.children) visit(child, depth + 1);
    };
    tree.replaceChildren();
    for (const service of state.nodes) {
      if (!state.allServices && ["Teams", "TextChatService", "SoundService"].includes(service.id)) continue;
      visit(service, 0);
    }
    if (!tree.childElementCount) tree.append(element("p", "drawer-notice", "Nenhum resultado"));
    tree.scrollTop = scroll;
    renderExplorerActions();
  }

  function renderExplorerActions() {
    const holder = drawerBody.querySelector(".explorer-actions");
    const selected = locate(state.selection);
    const copyable = Boolean(selected?.parent);
    const controls = [
      ["Ações da seleção", "row-actions", "ellipsis", !selected],
      ["Desfazer", "undo", "undo-2", !state.undo.length],
      ["Refazer", "redo", "redo-2", !state.redo.length],
      ["Copiar", "copy", "copy", !copyable],
      ["Colar", "paste", "clipboard-paste", !state.clipboard],
      ["Excluir seleção", "delete", "trash", !copyable],
    ];
    if (selected && /Script$/.test(selected.item.class)) controls.unshift(["Editar script", "script-editor", "file-code", false]);
    const buttons = controls.map(([label, action, icon, disabled]) => button(label, action, icon,
      "control" + (action === "script-editor" ? "" : " icon-only"), { disabled, iconOnly: action !== "script-editor" }));
    holder.replaceChildren(...buttons);
  }

  function record() {
    state.undo.push({ nodes: structuredClone(state.nodes), selection: state.selection });
    state.redo.length = 0;
  }

  function history(from, to) {
    if (!from.length) return;
    to.push({ nodes: structuredClone(state.nodes), selection: state.selection });
    const snapshot = from.pop();
    state.nodes = snapshot.nodes;
    state.selection = snapshot.selection;
    renderTree();
  }

  function addObject(kind) {
    record();
    const parent = locate(state.selection)?.item || state.nodes[0];
    parent.children ||= [];
    const item = { id: id(), label: kind, class: kind, children: [] };
    parent.children.push(item);
    state.expandedNodes.add(parent.id);
    state.selection = item.id;
    renderTree();
  }

  function copyObject(cut = false) {
    const selected = locate(state.selection);
    if (!selected?.parent) return;
    state.clipboard = structuredClone(selected.item);
    if (cut) removeObject();
    renderTree();
  }

  function cloneNode(item) {
    return { ...item, id: id(), children: item.children?.map(cloneNode) || [] };
  }

  function pasteObject(into = false, duplicate = false) {
    const selected = locate(state.selection);
    const source = duplicate ? selected?.item : state.clipboard;
    if (!source) return;
    record();
    const parent = into ? selected?.item : selected?.parent;
    const target = parent || state.nodes[0];
    target.children ||= [];
    const copy = cloneNode(source);
    target.children.push(copy);
    state.expandedNodes.add(target.id);
    state.selection = copy.id;
    renderTree();
  }

  function removeObject() {
    const selected = locate(state.selection);
    if (!selected?.parent) return;
    record();
    selected.nodes.splice(selected.nodes.indexOf(selected.item), 1);
    state.selection = null;
    renderTree();
  }

  function renameObject() {
    const row = drawerBody.querySelector(".tree-line.selected .tree-node");
    const selected = locate(state.selection);
    if (!row || !selected?.parent) return;
    const input = element("input", "rename-input");
    input.value = selected.item.label;
    input.setAttribute("aria-label", "Renomear");
    row.replaceWith(input);
    input.focus(); input.select();
    input.addEventListener("keydown", event => {
      event.stopPropagation();
      if (event.key === "Enter" && input.value.trim()) {
        record();
        selected.item.label = input.value.trim();
        renderTree();
      } else if (event.key === "Escape") {
        renderTree();
      }
    });
    input.addEventListener("blur", () => renderTree(), { once: true });
  }

  function showDialog(title, message, buttons) {
    dialogReturn = document.activeElement;
    byId("dialog-title").textContent = title;
    byId("dialog-message").textContent = message;
    dialogShade.querySelector(".dialog-actions").replaceChildren(...buttons.map(([label, action, primary]) =>
      button(label, action, null, primary ? "control primary" : "control")));
    homeScreen.inert = true; editorScreen.inert = true;
    motion(dialogShade, true, "y", data.motion.sheetShift);
    dialogShade.querySelector("button")?.focus({ preventScroll: true });
  }

  function closeDialog() {
    motion(dialogShade, false, "y", data.motion.sheetShift);
    homeScreen.inert = false; editorScreen.inert = false;
    dialogReturn?.focus({ preventScroll: true });
    dialogReturn = null;
  }

  function fileCommand(command) {
    const same = {
      "New": () => enterEditor("Baseplate"),
      "New Place": () => enterEditor("Baseplate"),
      "Open…": openFile,
      "Close Place": leaveEditor,
      "Save to File": save,
      "Undo": () => history(state.undo, state.redo),
      "Redo": () => history(state.redo, state.undo),
      "Cut": () => copyObject(true),
      "Copy": () => copyObject(),
      "Paste": () => pasteObject(),
      "Paste Into": () => pasteObject(true),
      "Paste at Original Location": () => pasteObject(),
      "Paste into at Original Location": () => pasteObject(true),
      "Duplicate": () => pasteObject(false, true),
      "Delete": removeObject,
      "Reduce Motion": () => { state.reduced = !state.reduced; announce(state.reduced ? "Movimento reduzido" : "Movimento padrão"); },
      "Command Bar": () => {
        state.commandBar = !state.commandBar;
        const bar = editorScreen.querySelector(".command-preview") || element("input", "command-preview");
        bar.setAttribute("aria-label", "Command Bar");
        bar.placeholder = "Execute a command with Ctrl+Enter";
        bar.hidden = !state.commandBar;
        if (!bar.isConnected) editorScreen.append(bar);
      },
    };
    closePopup();
    if (same[command]) same[command]();
    else if (panelNames.includes(command)) setPanel(command);
    else if (["Insert Part", "Insert Folder", "Insert Script", "Insert LocalScript", "Insert ModuleScript", "Insert ModuleScript (Class)"].includes(command)) {
      addObject(command.replace("Insert ", "").replace(" (Class)", ""));
    } else {
      announce("Prévia: " + command);
    }
  }

  function openFile() {
    announce("Abertura de arquivos disponível no aplicativo Android.");
  }

  function save() {
    state.saved = true;
    renderControls();
    announce("Salvamento simulado da prévia.");
    setTimeout(() => { state.saved = false; renderControls(); }, 1800);
  }

  function dispatch(action, trigger) {
    const colon = action.indexOf(":");
    const kind = colon < 0 ? action : action.slice(0, colon);
    const value = colon < 0 ? "" : action.slice(colon + 1);
    switch (kind) {
      case "home-tab": state.homeTab = value; renderHome(); return;
      case "new": case "recent": enterEditor(value); return;
      case "recover": state.recovered = false; enterEditor("Baseplate 13"); return;
      case "discard": state.recovered = false; renderHome(); return;
      case "account": showDialog("Configurar conta", "A conexão com o Roblox é feita no aplicativo Android.", [["Cancelar", "close-dialog"]]); return;
      case "open-file": openFile(); return;
      case "menu": case "tools": openSheet(kind); return;
      case "scene": closePopup(false); closeSheet(); return;
      case "main-menu": openPopup("main:" + value, trigger, 320, menuItems(data.menus[value], "command:")); return;
      case "document-add": openPopup("documents", trigger, 220, menuItems(["New Place", "Open…"], "command:")); return;
      case "command": fileCommand(value); return;
      case "ribbon-tab": closePopup(false); state.tab = value; renderSheet(); return;
      case "ribbon": {
        const options = data.inserts[value];
        if (options) { openPopup("insert:" + value, trigger, 220,
          options.map(([label, icon, className]) => button(label, "insert:" + (className || label), icon, "menu-row", { disabled: className === false }))); return; }
        if (panelNames.includes(value)) { setPanel(value); return; }
        if (value === "Play") { dispatch("play", trigger); return; }
        announce("Prévia: " + value); return;
      }
      case "panels":
        if (state.popup !== "panels") closeSheet();
        openPopup("panels", trigger, 240, menuItems(panelNames, "panel:", {}, { [state.panel]: true }));
        return;
      case "panel": setPanel(value); return;
      case "drawer-tab": if (state.panel !== value) setPanel(value); return;
      case "close-panel": setPanel(state.panel); return;
      case "expand-panel": state.expanded = !state.expanded; renderDrawer(); return;
      case "explorer-settings": openPopup("explorer-settings", trigger, 250, [
        button("Fechar painel", "close-panel", null, "menu-row"),
        button("Show all services", "all-services", null, "menu-row", { checked: state.allServices }),
        button("Light Icons", "light-icons", null, "menu-row", { checked: state.lightIcons }),
      ], true); return;
      case "all-services": state.allServices = !state.allServices; closePopup(); renderTree(); return;
      case "light-icons": state.lightIcons = !state.lightIcons; closePopup(); renderTree(); return;
      case "query-mode": {
        state.queryMode = !state.queryMode;
        const input = drawerBody.querySelector("[data-explorer-search]");
        input.placeholder = state.queryMode ? "Part where Anchored = false" : "Search";
        trigger.setAttribute("aria-pressed", String(state.queryMode));
        input.focus(); return;
      }
      case "select-node": state.selection = value; renderTree(); return;
      case "expand-node":
        if (state.expandedNodes.has(value)) state.expandedNodes.delete(value);
        else state.expandedNodes.add(value);
        renderTree(); return;
      case "row-actions": {
        const selected = locate(state.selection);
        if (selected) openPopup("explorer-row", trigger, 280, data.explorerRows.map(([key, icon, label]) =>
          button(label, "row:" + key, icon, "menu-row", {
            disabled: (["cut", "copy", "duplicate", "rename", "delete", "change-class", "freeze-rotation"].includes(key) && !selected.parent)
              || (key.startsWith("paste") && !state.clipboard)
              || (key === "ungroup" && selected.item.class !== "Model")
              || (key === "play-animation" && selected.item.class !== "Animation"),
          })), true);
        return;
      }
      case "row": {
        closePopup(false);
        const actions = {
          cut: () => copyObject(true), copy: () => copyObject(), duplicate: () => pasteObject(false, true),
          "paste-into": () => pasteObject(true), "paste-into-original": () => pasteObject(true),
          rename: renameObject, delete: removeObject,
        };
        if (actions[value]) actions[value]();
        else if (value === "insert") {
          const anchor = drawerBody.querySelector("[data-action='row-actions']");
          const options = ["Folder", "Part", "Model", "Script", "LocalScript", "ModuleScript"];
          openPopup("insert-object", anchor, 240, menuItems(options, "insert:"), true);
        } else announce("Prévia: " + data.explorerRows.find(row => row[0] === value)?.[2]);
        return;
      }
      case "insert": closePopup(false); addObject(value); return;
      case "copy": copyObject(); return;
      case "paste": pasteObject(); return;
      case "delete": removeObject(); return;
      case "undo": history(state.undo, state.redo); return;
      case "redo": history(state.redo, state.undo); return;
      case "save": save(); return;
      case "play":
        closeSheet(); closePopup(false);
        showDialog("Configurar experiência de teste",
          "A prévia mostra a janela do Play. Criar, publicar e abrir Roblox requerem o aplicativo Android.",
          [["Voltar", "close-dialog"], ["Criar experiência de teste", "play-create", true],
            ["Utilizar experiência existente", "play-existing"], ["Creator Dashboard", "play-dashboard"]]);
        return;
      case "play-create": case "play-existing": case "play-dashboard": announce("Disponível no aplicativo Android."); return;
      case "close-dialog": closeDialog(); return;
      case "active-tool":
        openPopup("active-tool", trigger, 220, transforms.map(([label, icon, tool]) =>
          button(label, "tool:" + tool, icon, "menu-row", { checked: state.tool === tool })));
        return;
      case "tool": closePopup(false); closeSheet(); state.tool = value; renderControls(); return;
      case "transform-options":
        openPopup("transform-options", trigger, 290, [
          button("Focar seleção", "sample:Focar seleção", "crosshair", "menu-row"),
          button(state.localAxes ? "Eixos locais" : "Eixos globais", "axes", "axis-3d", "menu-row"),
          button("Analógico da câmera", "stick", "gamepad-2", "menu-row", { checked: state.stick }),
          button("Transform combinado", "tool:combined", "move-3d", "menu-row"),
          button("Align", "sample:Align", null, "menu-row"), button("1 stud", "sample:Snap de movimento", "magnet", "menu-row"),
          button("45°", "sample:Snap de rotação", "rotate-cw", "menu-row"),
        ], true);
        return;
      case "axes": state.localAxes = !state.localAxes; closePopup(); return;
      case "stick": state.stick = !state.stick; closePopup(); return;
      case "script-editor": announce("A edição de scripts fica no aplicativo Android."); return;
      case "sample": announce("Prévia: " + value); return;
    }
  }

  root.addEventListener("click", event => {
    const trigger = event.target.closest("button[data-action]");
    if (trigger && root.contains(trigger) && !trigger.disabled) {
      dispatch(trigger.dataset.action, trigger);
      return;
    }
    if (event.target === shade) { closePopup(); closeSheet(); return; }
    if (!popup.contains(event.target)) closePopup(false);
    if (state.sheet === "tools" && !sheet.contains(event.target)) closeSheet();
  });
  root.addEventListener("pointerdown", event => {
    if (state.popup && !popup.contains(event.target) && !event.target.closest("button[data-action]")) closePopup(false);
  });
  root.addEventListener("input", event => {
    if (event.target.matches("[data-explorer-search]")) {
      state.query = event.target.value; renderTree();
    }
  });
  root.addEventListener("keydown", event => {
    if (event.key === "Escape") {
      if (!dialogShade.hidden && !dialogShade.inert) closeDialog();
      else if (state.popup) closePopup();
      else if (state.sheet) closeSheet();
      else if (state.panel) setPanel(state.panel);
      else return;
      event.stopPropagation(); event.preventDefault();
    }
    if (state.popup && popup.contains(document.activeElement) && ["ArrowUp", "ArrowDown", "Home", "End"].includes(event.key)) {
      const rows = [...popup.querySelectorAll("button:not(:disabled)")];
      const index = rows.indexOf(document.activeElement);
      const next = event.key === "Home" ? 0 : event.key === "End" ? rows.length - 1
        : (index + rows.length + (event.key === "ArrowDown" ? 1 : -1)) % rows.length;
      rows[next]?.focus(); event.preventDefault();
    }
  });
  const grip = drawer.querySelector(".drawer-grip");
  let drag = null;
  grip.addEventListener("pointerdown", event => {
    if (root.clientWidth < 700 || state.expanded || state.panel === "Output") return;
    drag = { x: event.clientX, width: drawer.offsetWidth };
    grip.setPointerCapture(event.pointerId);
  });
  grip.addEventListener("pointermove", event => {
    if (!drag) return;
    state.width = drag.width + drag.x - event.clientX;
    sizeLayout();
  });
  for (const event of ["pointercancel", "lostpointercapture"]) grip.addEventListener(event, () => { drag = null; });
  new ResizeObserver(sizeLayout).observe(root);
  root.querySelector(".scene-image").src = data.images[0];
  hydrateButtons();
  renderHome();
  renderControls();
})();
