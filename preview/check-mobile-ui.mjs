import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";
import { resolve } from "node:path";

const { chromium } = await import(process.env.RBX_PLAYWRIGHT_MODULE || "playwright");
const service = process.env.RBX_HEADLESS_CHROMIUM
  ? (await import(process.env.RBX_HEADLESS_CHROMIUM)).default
  : null;
const browser = await chromium.launch(service ? {
  executablePath: await service.executablePath(),
  args: service.args,
} : {});
const source = pathToFileURL(resolve(process.argv[2] || "preview/mobile-ui.html")).href;

try {
  const page = await browser.newPage({ viewport: { width: 904, height: 407 }, hasTouch: true });
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.goto(source);
  const action = name => page.locator('[data-action="' + name + '"]').first();
  const drawer = page.locator("#rbx-drawer");
  const windowShade = page.locator("#rbx-window-shade");
  const openPanel = async name => {
    await action("panels").tap();
    await action("panel:" + name).tap();
  };

  await action("new:Baseplate").tap();
  await action("menu").tap();
  for (const title of ["File", "Edit", "Model", "View"]) {
    await action("main-menu:" + title).tap();
    assert.ok(await page.locator("#rbx-popup .menu-row").count(), title);
    await page.keyboard.press("Escape");
  }
  await action("main-menu:File").tap();
  await action("command:Game Settings…").tap();
  await windowShade.waitFor({ state: "visible" });
  assert.equal(await page.locator(".settings-tabs button").count(), 8);
  assert.ok(await action("settings-save").isDisabled());
  await page.getByRole("textbox", { name: "Name", exact: true }).fill("Obby de exemplo");
  assert.ok(await action("settings-save").isEnabled());
  await action("settings-save").tap();
  assert.ok(await action("settings-save").isDisabled());
  await action("window-page:Places").tap();
  await page.keyboard.press("Escape");
  assert.equal(await windowShade.evaluate(el => el.inert), true);
  await windowShade.waitFor({ state: "hidden" });

  await action("menu").tap();
  await action("main-menu:File").tap();
  await action("command:Studio Settings…").tap();
  assert.equal(await page.locator(".settings-tabs button").count(), 10);
  await action("window-page:Accessibility").tap();
  await page.getByRole("checkbox", { name: "Reduce motion" }).check();
  await action("window-close").tap();
  assert.ok(await windowShade.isHidden());

  const panels = await page.locator("#rbx-mobile-review > script[type='application/json']")
    .evaluate(el => JSON.parse(el.textContent).panels);
  assert.equal(panels.length, 21);
  for (const name of panels) {
    await openPanel(name);
    assert.equal(await drawer.getAttribute("aria-label"), name);
    assert.ok(!(await drawer.innerText()).includes("reservado para outro lote"), name);
    await action("close-panel").tap();
    assert.ok(await drawer.isHidden());
  }

  await openPanel("Playtests");
  await action("sample-choice:Speed|2×").tap();
  assert.equal(await action("sample-choice:Speed|1×").getAttribute("aria-pressed"), "false");
  assert.equal(await action("sample-choice:Speed|2×").getAttribute("aria-pressed"), "true");
  await openPanel("Watch");
  const watch = page.getByRole("textbox", { name: "Add a watch expression" });
  await action("sample-choice:Watch|My Watches").tap();
  await watch.fill("workspace.Part");
  await watch.press("Enter");
  await page.getByRole("button", { name: "Remove watch" }).tap();
  assert.ok(!((await drawer.textContent()).includes("workspace.Part")));
  await openPanel("Localization");
  await action("sample-choice:Localization|Strings 0").tap();
  assert.ok(await page.getByRole("button", { name: "Nothing new to write" }).isDisabled());
  await action("close-panel").tap();

  for (const width of [320, 360, 430, 640, 740, 904, 960]) {
    await page.setViewportSize({ width, height: width === 960 ? 432 : 407 });
    const play = await action("play").boundingBox();
    const save = await action("save").boundingBox();
    assert.ok(play.width >= 48 && play.x + play.width <= width, `Play at ${width}`);
    assert.ok(save.width >= 48 && save.x + save.width <= width, `Save at ${width}`);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), width);

    await openPanel("Properties");
    assert.ok((await drawer.boundingBox()).width <= width);
    await action("close-panel").tap();
  }
  assert.deepEqual(errors, []);
  console.log("Preview passed: 4 menus, 21 drawers, 18 settings tabs, Watch, Playtests, Localization and 7 widths.");
} finally {
  await browser.close();
}
