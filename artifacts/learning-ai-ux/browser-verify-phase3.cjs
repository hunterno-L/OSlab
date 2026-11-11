/* 阶段 3：首页闭环、第二课程、跨账号隔离、原功能冒烟。 */
const fs = require("fs");
const path = require("path");
const puppeteer = require("puppeteer-core");

const OUT = __dirname;
const EVIDENCE = path.join(OUT, "evidence");
const SHOTS = path.join(OUT, "screenshots");
const BASE = "http://localhost:8081";
const ACCOUNT = "qa_b_nov11";
const PASSWORD = "qaPass12345";
const OTHER = "qa_b_other";
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const save = (name, value) =>
  fs.writeFileSync(path.join(EVIDENCE, name), JSON.stringify(value, null, 2), "utf8");
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);

async function login(page, account = ACCOUNT, password = PASSWORD) {
  await page.goto(`${BASE}/home`, { waitUntil: "domcontentloaded", timeout: 60000 });
  await sleep(800);
  if (await page.evaluate(() => Boolean(localStorage.getItem("userId")))) return;
  await page.goto(`${BASE}/user/login`, { waitUntil: "domcontentloaded", timeout: 60000 });
  await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll("button")).find(
      (b) => b.textContent.trim() === "登录 / 注册"
    );
    if (btn) btn.click();
  });
  await page.waitForSelector("form input[name=userAccount]", { timeout: 20000 });
  await page.evaluate(
    (acc, pwd) => {
      const set = (el, v) => {
        const d = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
        d.set.call(el, v);
        el.dispatchEvent(new Event("input", { bubbles: true }));
        el.dispatchEvent(new Event("change", { bubbles: true }));
      };
      set(document.querySelector("form input[name=userAccount]"), acc);
      set(document.querySelector("form input[name=userPassword]"), pwd);
      const form = document.querySelector("form");
      Array.from(form.querySelectorAll("button")).find((b) => b.textContent.trim() === "登录").click();
    },
    account,
    password
  );
  await page.waitForFunction(() => Boolean(localStorage.getItem("userId")), { timeout: 20000 });
  await sleep(900);
}

async function api(page, method, url, body) {
  return page.evaluate(
    async (m, u, b) => {
      const res = await fetch(u, {
        method: m,
        headers: b ? { "Content-Type": "application/json" } : undefined,
        body: b ? JSON.stringify(b) : undefined,
        credentials: "include",
      });
      let json = null;
      try {
        json = await res.json();
      } catch {
        json = null;
      }
      return { status: res.status, json };
    },
    method,
    url,
    body || null
  );
}

(async () => {
  const mainRunId = JSON.parse(
    fs.readFileSync(path.join(EVIDENCE, "phase1-summary.json"), "utf8")
  ).runId;
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: true,
    args: ["--no-sandbox", "--disable-gpu"],
    defaultViewport: { width: 1440, height: 900 },
  });
  const evidence = { mainRunId };
  try {
    const page = await browser.newPage();
    await login(page);

    // ---- B01 首页 ----
    await page.goto(`${BASE}/home`, { waitUntil: "domcontentloaded", timeout: 60000 });
    await sleep(2500);
    evidence.b01 = await page.evaluate(() => ({
      title: document.title,
      url: location.href,
      text: document.body.innerText.slice(0, 1800),
      buttons: Array.from(document.querySelectorAll("main button, main a"))
        .map((n) => n.textContent.trim())
        .filter(Boolean)
        .slice(0, 30),
    }));
    await page.screenshot({ path: path.join(SHOTS, "b01-home.png") });
    log("B01 captured");

    // ---- C01 第二课程：数据库系统练习 + 提示 ----
    await page.goto(`${BASE}/assessments?courseCode=course.database-systems`, {
      waitUntil: "domcontentloaded",
      timeout: 60000,
    });
    await page.waitForFunction(() => document.querySelectorAll("article").length > 0, {
      timeout: 30000,
    });
    await sleep(1200);
    evidence.c01 = await page.evaluate(() => ({
      url: location.href,
      title: document.title,
      head: document.body.innerText.slice(0, 900),
      cards: document.querySelectorAll("article").length,
      osTerms: /缺页中断|页面置换/.test(document.body.innerText.slice(0, 1500)),
    }));
    const dbCards = await page.$$("article");
    if (dbCards.length) {
      const info = await page.evaluate(() => {
        const card = document.querySelector("article");
        return {
          text: card.innerText.slice(0, 500),
          radios: card.querySelectorAll("input[type=radio]").length,
          type: card.querySelector("input[type=radio]") ? "SINGLE" : "OTHER",
        };
      });
      evidence.c01.firstCard = info;
      if (info.radios >= 2) {
        const opts = await page.$$("article .qa-option");
        if (opts.length) await opts[0].click();
        await sleep(300);
        await page.evaluate(() => {
          const card = document.querySelector("article");
          const submit = Array.from(card.querySelectorAll("button")).find(
            (b) => b.textContent.trim() === "提交答案"
          );
          if (submit) submit.click();
        });
        await sleep(2500);
        evidence.c01.feedback = await page.evaluate(() =>
          document.querySelector("article")?.innerText.slice(-500)
        );
      }
      // 提示接线（帮助先落库，再请求导师）
      const hint = await page.evaluate(async () => {
        const cards = Array.from(document.querySelectorAll("article"));
        const card = cards.find((c) =>
          Array.from(c.querySelectorAll("button")).some((b) =>
            b.textContent.includes("我需要提示")
          )
        );
        if (!card) return { ok: false };
        Array.from(card.querySelectorAll("button"))
          .find((b) => b.textContent.includes("我需要提示"))
          .click();
        await new Promise((r) => setTimeout(r, 2500));
        return { ok: true, text: card.innerText.slice(-260) };
      });
      evidence.c01.hint = hint;
    }
    await page.screenshot({ path: path.join(SHOTS, "c01-second-course.png") });
    log("C01 captured");

    // ---- C02 跨账号隔离 ----
    const ctx2 = await browser.createBrowserContext();
    const page2 = await ctx2.newPage();
    await page2.goto(`${BASE}/user/login`, {
      waitUntil: "domcontentloaded",
      timeout: 60000,
    });
    const register = await page2.evaluate(async (acc, pwd) => {
      const r = await fetch("/api/user/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userAccount: acc, userPassword: pwd, checkPassword: pwd }),
        credentials: "include",
      });
      return r.json();
    }, OTHER, PASSWORD);
    evidence.c02Register = register;
    await login(page2, OTHER, PASSWORD);
    evidence.c02OtherUserId = await page2.evaluate(() => localStorage.getItem("userId"));
    const crossRun = await api(
      page2,
      "GET",
      `/api/learning/assessment/run?runId=${mainRunId}`
    );
    const crossResult = await api(
      page2,
      "GET",
      `/api/learning/assessment/runs/${mainRunId}/result`
    );
    const crossFinish = await api(page2, "POST", "/api/learning/assessment/finish", {
      runId: mainRunId,
      submissionKey: `qa-cross-${Date.now()}`,
      rowVersion: 0,
    });
    evidence.c02 = {
      run: crossRun.json,
      result: crossResult.json,
      finish: crossFinish.json,
      firstUserLeak: await page2.evaluate(
        (runId) => ({
          userId: localStorage.getItem("userId"),
          bodyHasMainRun: document.body.innerText.includes(String(runId)),
        }),
        mainRunId
      ),
    };
    save("c02-cross-account.json", evidence.c02);
    await ctx2.close();
    log("C02 captured");

    // ---- C03 原功能冒烟 ----
    const smoke = [];
    for (const route of ["/resources", "/experiments", "/settings/models"]) {
      await page.goto(`${BASE}${route}`, { waitUntil: "domcontentloaded", timeout: 60000 });
      await sleep(2200);
      smoke.push(
        await page.evaluate(() => ({
          url: location.href,
          title: document.title,
          head: document.body.innerText.slice(0, 300),
          hasError: /加载失败|页面不存在|出错了/.test(document.body.innerText.slice(0, 1200)),
        }))
      );
    }
    evidence.c03 = smoke;
    log("C03 captured");

    save("phase3.json", evidence);
    log("PHASE3 DONE");
  } catch (e) {
    save("phase3-error.json", { message: String(e), stack: String(e.stack) });
    console.error("PHASE3 FAILED", e);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
