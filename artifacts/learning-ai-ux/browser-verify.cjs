/* B 端真实浏览器回归：逐题反馈、自测草稿/刷新、网络载荷、交卷幂等、结果页。
 * 运行：node artifacts/learning-ai-ux/browser-verify.cjs
 * 依赖本机 Edge + 前端 8081 / 后端 8121；仅写入 artifacts/learning-ai-ux/。
 */
const fs = require("fs");
const path = require("path");
const puppeteer = require("puppeteer-core");

const OUT = __dirname;
const EVIDENCE = path.join(OUT, "evidence");
const SHOTS = path.join(OUT, "screenshots");
fs.mkdirSync(EVIDENCE, { recursive: true });
fs.mkdirSync(SHOTS, { recursive: true });

const BASE = "http://localhost:8081";
const ACCOUNT = "qa_b_nov11";
const PASSWORD = "qaPass12345";
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";

const log = (...args) => console.log(new Date().toISOString().slice(11, 19), ...args);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const save = (name, value) =>
  fs.writeFileSync(path.join(EVIDENCE, name), JSON.stringify(value, null, 2), "utf8");

async function clickText(page, text, selector = "button, label, a, .arco-radio, .arco-checkbox, .arco-tabs-tab") {
  const clicked = await page.evaluate(
    (t, sel) => {
      const nodes = Array.from(document.querySelectorAll(sel));
      const target = nodes.find((n) => n.textContent.trim() === t || n.textContent.trim().startsWith(t));
      if (!target) return false;
      target.click();
      return true;
    },
    text,
    selector
  );
  if (!clicked) throw new Error(`clickText failed: ${text}`);
  return clicked;
}

async function loginIfNeeded(page) {
  await page.goto(`${BASE}/home`, { waitUntil: "networkidle2", timeout: 60000 });
  await sleep(800);
  const logged = await page.evaluate(() => Boolean(localStorage.getItem("userId")));
  if (logged) return;
  await page.goto(`${BASE}/user/login`, { waitUntil: "networkidle2", timeout: 60000 });
  await sleep(500);
  const modalOpen = await page.evaluate(() => Boolean(document.querySelector("form input[name=userAccount]")));
  if (!modalOpen) await clickText(page, "登录 / 注册");
  await page.waitForSelector("form input[name=userAccount]", { timeout: 15000 });
  await page.evaluate(
    (account, password) => {
      const set = (el, v) => {
        const d = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
        d.set.call(el, v);
        el.dispatchEvent(new Event("input", { bubbles: true }));
        el.dispatchEvent(new Event("change", { bubbles: true }));
      };
      set(document.querySelector("form input[name=userAccount]"), account);
      set(document.querySelector("form input[name=userPassword]"), password);
    },
    ACCOUNT,
    PASSWORD
  );
  await page.evaluate(() => {
    const form = document.querySelector("form");
    const btn = Array.from(form.querySelectorAll("button")).find((b) => b.textContent.trim() === "登录");
    btn.click();
  });
  await page.waitForFunction(() => Boolean(localStorage.getItem("userId")), { timeout: 20000 });
  await sleep(1000);
}

function attachRecorder(page) {
  const records = [];
  page.on("response", async (response) => {
    const url = response.url();
    if (!/\/api\/learning\/(assessment|practice|questions)/.test(url)) return;
    const method = response.request().method();
    let body = null;
    try {
      const text = await response.text();
      body = text.length > 400000 ? text.slice(0, 400000) + "...TRUNCATED" : text;
    } catch {
      body = null;
    }
    records.push({
      url,
      method,
      status: response.status(),
      requestBody: response.request().postData() || null,
      body,
    });
  });
  return records;
}

async function waitSpinner(page) {
  await page.waitForFunction(
    () => !document.body.innerText.includes("正在加载题目"),
    { timeout: 20000 }
  );
  await sleep(500);
}

async function phase1(browser) {
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });
  const records = attachRecorder(page);
  await loginIfNeeded(page);

  // ---- B02 逐题反馈 ----
  await page.goto(`${BASE}/assessments?courseCode=course.operating-systems`, {
    waitUntil: "networkidle2",
    timeout: 60000,
  });
  await waitSpinner(page);
  await page.waitForFunction(() => document.querySelectorAll("article").length > 0, {
    timeout: 30000,
  });
  await page.evaluate(() => {
    const chip = Array.from(document.querySelectorAll("button")).find((b) =>
      b.textContent.trim().startsWith("缺页中断")
    );
    if (chip) chip.click();
  });
  await sleep(1800);
  const cards = await page.$$("article");
  if (!cards.length) throw new Error("no question cards");
  const type = await page.evaluate(() => {
    const card = document.querySelector("article");
    return {
      radios: card.querySelectorAll("input[type=radio]").length,
      texts: Array.from(card.querySelectorAll(".qa-option-key")).map((n) => n.textContent.trim()),
    };
  });
  if (type.radios >= 3) {
    await page.evaluate(() => {
      const card = document.querySelector("article");
      card.querySelectorAll("input[type=radio]")[2].click();
    });
    await sleep(200);
    await page.evaluate(() => {
      const card = document.querySelector("article");
      const submit = Array.from(card.querySelectorAll("button")).find(
        (b) => b.textContent.trim() === "提交答案"
      );
      submit.click();
    });
    await page.waitForFunction(
      () => /回答正确|回答错误|待评阅/.test(document.querySelector("article")?.innerText || ""),
      { timeout: 25000 }
    );
  }
  await sleep(500);
  const feedback = await page.evaluate(() => document.querySelector("article").innerText);
  save("b02-feedback.txt", feedback);
  await page.evaluate(() => {
    const card = document.querySelector("article");
    card.scrollIntoView({ block: "start", behavior: "instant" });
    window.scrollBy(0, -90);
  });
  await sleep(400);
  await page.screenshot({ path: path.join(SHOTS, "b02-per-question-feedback.png") });
  log("B02 feedback captured");

  // ---- B03 提示先记录（普通练习） ----
  const second = await page.evaluate(async () => {
    const cards = Array.from(document.querySelectorAll("article"));
    const card = cards.find((c) =>
      Array.from(c.querySelectorAll("button")).some((b) => b.textContent.includes("我需要提示"))
    );
    if (!card) return { ok: false, cards: cards.length };
    const help = Array.from(card.querySelectorAll("button")).find((b) =>
      b.textContent.includes("我需要提示")
    );
    if (!help) return { ok: false, cards: cards.length };
    help.click();
    await new Promise((r) => setTimeout(r, 3000));
    return { ok: true, text: card.innerText.slice(-300) };
  });
  save("b03-hint-ui.json", second);
  log("B03 hint clicked");

  // ---- B04 自测作答/草稿/刷新 ----
  await page.goto(`${BASE}/assessments?courseCode=course.operating-systems`, {
    waitUntil: "networkidle2",
    timeout: 60000,
  });
  await page.waitForFunction(
    () =>
      Array.from(document.querySelectorAll("label, .arco-radio, .arco-radio-button")).some(
        (n) => n.textContent.trim() === "整组自测"
      ),
    { timeout: 25000 }
  );
  await page.evaluate(() => {
    const selfTest =
      Array.from(document.querySelectorAll("label, .arco-radio, .arco-radio-button")).find(
        (n) => n.textContent.trim() === "整组自测"
      ) || document.querySelector('input[type=radio][value="SELF_TEST"]');
    selfTest.click();
    const input = selfTest.querySelector && selfTest.querySelector("input[type=radio]");
    if (input) input.click();
  });
  await sleep(800);
  await clickText(page, "开始整组自测");
  await page.waitForFunction(() => location.pathname.startsWith("/assessments/run/"), {
    timeout: 30000,
  });
  await sleep(2500);
  const runId = await page.evaluate(() => location.pathname.split("/").pop());
  log("self-test run", runId);

  await page.waitForSelector("article.self-test-card", { timeout: 30000 });
  await sleep(1200);

  const currentNavIndex = () =>
    page.evaluate(() =>
      Array.from(document.querySelectorAll(".self-nav-item")).findIndex((n) =>
        n.className.includes("current")
      )
    );
  const readRun = () =>
    page.evaluate(async (id) => {
      const r = await fetch(`/api/learning/assessment/run?runId=${id}&t=${Date.now()}`, {
        credentials: "include",
        cache: "no-store",
      });
      return r.json();
    }, runId);

  const pickOption = async () => {
    for (let attempt = 0; attempt < 3; attempt += 1) {
      const options = await page.$$("article.self-test-card .stc-option");
      if (options.length) {
        await options[options.length > 1 ? 1 : 0].click();
        try {
          await page.waitForFunction(
            () =>
              document.querySelectorAll("article.self-test-card .arco-radio-checked").length > 0,
            { timeout: 2500 }
          );
          return true;
        } catch {
          await sleep(400);
        }
      }
      const textarea = await page.$("article.self-test-card textarea");
      if (textarea) {
        await textarea.click();
        await textarea.type("自测验证草稿：先写出关键步骤，待交卷后对照解析。");
        return true;
      }
      await sleep(400);
    }
    return false;
  };

  const answerCurrent = async (markUnsure) => {
    const index = await currentNavIndex();
    const before = await readRun();
    const qid = before?.data?.questions?.[index]?.id;
    const picked = await pickOption();
    const uiState = await page.evaluate(() => ({
      navClass: document.querySelectorAll(".self-nav-item")[
        Array.from(document.querySelectorAll(".self-nav-item")).findIndex((n) =>
          n.className.includes("current")
        )
      ]?.className,
      checked: document.querySelectorAll(
        "article.self-test-card .arco-radio-checked"
      ).length,
      saveState: document.querySelector(".save-state")?.textContent.trim(),
    }));
    if (markUnsure) {
      const mark = await page.$("article.self-test-card .arco-checkbox");
      if (mark) await mark.click();
    }
    let draft = null;
    for (let attempt = 0; attempt < 10; attempt += 1) {
      await sleep(600);
      const after = await readRun();
      draft = after?.data?.questions?.find((q) => String(q.id) === String(qid));
      const value = draft?.draftAnswer;
      const answered = Array.isArray(value) ? value.length > 0 : value !== null && value !== undefined && String(value).trim() !== "";
      if (answered && (!markUnsure || draft?.markedUnsure)) break;
    }
    await page.evaluate(() => {
      const next = Array.from(document.querySelectorAll("button")).find(
        (b) => b.textContent.trim() === "下一题"
      );
      if (next) next.click();
    });
    await sleep(900);
    return {
      index,
      qid,
      picked,
      uiState,
      draftAnswer: draft?.draftAnswer ?? null,
      markedUnsure: draft?.markedUnsure ?? null,
    };
  };

  const step1 = await answerCurrent(false);
  const step2 = await answerCurrent(true);
  const step3 = await answerCurrent(false);
  save("b04-steps.json", { step1, step2, step3 });
  await page.waitForFunction(
    () => /已保存/.test(document.querySelector(".save-state")?.textContent || ""),
    { timeout: 15000 }
  );
  const beforeReload = await page.evaluate(() => ({
    url: location.href,
    saveState: document.querySelector(".save-state")?.textContent.trim(),
    nav: Array.from(document.querySelectorAll(".self-nav-item")).map((n) => n.textContent.trim()),
    cardText: document.querySelector("article.self-test-card")?.innerText.slice(0, 500),
    bodyHasAnalysis: /参考解析|正确答案|回答正确|回答错误/.test(document.body.innerText),
  }));
  save("b04-before-reload.json", beforeReload);
  await page.evaluate(() => {
    const card = document.querySelector("article.self-test-card");
    if (card) card.scrollIntoView({ block: "start", behavior: "instant" });
    window.scrollBy(0, -20);
  });
  await sleep(400);
  await page.screenshot({ path: path.join(SHOTS, "b04-self-test-answering.png") });

  await page.reload({ waitUntil: "networkidle2", timeout: 60000 });
  await sleep(2500);
  const afterReload = await page.evaluate(() => {
    const nav = Array.from(document.querySelectorAll(".self-nav-item"));
    const current = document.querySelector("article.self-test-card");
    return {
      url: location.href,
      saveState: document.querySelector(".save-state")?.textContent.trim(),
      nav: nav.map((n) => n.textContent.trim()),
      answeredMarks: nav.map((n) => n.className),
      cardText: current?.innerText.slice(0, 400),
      textValues: Array.from(current?.querySelectorAll("textarea, input.arco-input") || []).map((el) => el.value),
      checkedRadios: Array.from(current?.querySelectorAll("input[type=radio]") || []).map((r) => r.checked),
    };
  });
  save("b04-after-reload.json", afterReload);

  for (const record of records) {
    if (!record.body) continue;
    if (record.url.includes("/assessment/start") || record.url.includes("/assessment/run") || record.url.includes("/drafts") || record.url.includes("/practice/attempts")) {
      const name = record.url.replace(/[^a-zA-Z0-9]+/g, "_").replace(/^http_localhost_8081_api_/, "").slice(0, 90) + "_" + record.method + ".json";
      fs.writeFileSync(path.join(EVIDENCE, name), record.body, "utf8");
    }
  }
  save("b04-network-index.json", records.map((r) => ({ url: r.url, method: r.method, status: r.status, requestBody: r.requestBody })));
  return { runId, page, records };
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: true,
    args: ["--no-sandbox", "--disable-gpu", "--window-size=1440,900"],
    defaultViewport: { width: 1440, height: 900 },
  });
  try {
    const result = await phase1(browser);
    fs.writeFileSync(path.join(EVIDENCE, "phase1-summary.json"), JSON.stringify({ runId: result.runId }, null, 2));
    log("PHASE1 DONE runId=", result.runId);
  } catch (e) {
    console.error("PHASE1 FAILED", e);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
