const puppeteer = require("puppeteer-core");
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
const BASE = "http://localhost:8081";
const runId = process.argv[2];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: true,
    args: ["--no-sandbox", "--disable-gpu"],
    defaultViewport: { width: 1440, height: 900 },
  });
  const page = await browser.newPage();
  page.on("request", (r) => {
    if (r.url().includes("/drafts") && r.method() === "PUT") console.log("PUT BODY", r.postData());
  });
  page.on("console", (m) => {
    if (m.type() === "error" || m.type() === "warn") console.log("CONSOLE", m.type(), m.text().slice(0, 200));
  });
  await page.goto(`${BASE}/user/login`, { waitUntil: "networkidle2" });
  await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll("button")).find((b) => b.textContent.trim() === "登录 / 注册");
    if (btn) btn.click();
  });
  await page.waitForSelector("form input[name=userAccount]", { timeout: 15000 });
  await page.evaluate(() => {
    const set = (el, v) => {
      const d = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
      d.set.call(el, v);
      el.dispatchEvent(new Event("input", { bubbles: true }));
    };
    set(document.querySelector("form input[name=userAccount]"), "qa_b_nov11");
    set(document.querySelector("form input[name=userPassword]"), "qaPass12345");
    const form = document.querySelector("form");
    Array.from(form.querySelectorAll("button")).find((b) => b.textContent.trim() === "登录").click();
  });
  await page.waitForFunction(() => Boolean(localStorage.getItem("userId")), { timeout: 20000 });
  await page.goto(`${BASE}/assessments/run/${runId}`, { waitUntil: "networkidle2" });
  await page.waitForSelector("article.self-test-card", { timeout: 30000 });
  await sleep(1200);
  const pre = await page.evaluate(() => {
    const card = document.querySelector("article.self-test-card");
    const opt = card.querySelector(".stc-option");
    const input = opt.querySelector("input[type=radio]");
    return {
      disabled: input.disabled,
      checked: input.checked,
      labelClass: opt.className,
      groupClass: card.querySelector(".stc-options")?.className,
      rect: opt.getBoundingClientRect().toJSON(),
      saveState: document.querySelector(".save-state")?.textContent.trim(),
      vueDraft: card.innerText.slice(0, 60),
    };
  });
  console.log("PRE", JSON.stringify(pre, null, 2));
  const handles = await page.$$("article.self-test-card .stc-option");
  console.log("HANDLES", handles.length);
  await handles[2].click();
  await sleep(3000);
  await page.evaluate(() => document.querySelector("article.self-test-card input[type=radio]").focus());
  await page.keyboard.press("Space");
  await sleep(3000);
  const post = await page.evaluate(() => {
    const card = document.querySelector("article.self-test-card");
    return {
      checked: Array.from(card.querySelectorAll("input[type=radio]")).map((r) => r.checked),
      labelClasses: Array.from(card.querySelectorAll(".stc-option")).map((n) => n.className),
      saveState: document.querySelector(".save-state")?.textContent.trim(),
    };
  });
  console.log("POST", JSON.stringify(post, null, 2));
  const run = await page.evaluate(async (id) => {
    const r = await fetch(`/api/learning/assessment/run?runId=${id}`, { credentials: "include" });
    return r.json();
  }, runId);
  console.log("DRAFTS", JSON.stringify(run.data.questions.map((q) => ({ id: q.id, draft: q.draftAnswer, unsure: q.markedUnsure }))));
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
