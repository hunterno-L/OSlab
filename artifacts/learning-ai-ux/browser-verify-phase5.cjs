/* 阶段 5：断网时草稿保存失败/恢复重试；390px 移动布局。 */
const fs = require("fs");
const path = require("path");
const puppeteer = require("puppeteer-core");

const EVIDENCE = path.join(__dirname, "evidence");
const SHOTS = path.join(__dirname, "screenshots");
const BASE = "http://localhost:8081";
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const save = (name, value) =>
  fs.writeFileSync(path.join(EVIDENCE, name), JSON.stringify(value, null, 2), "utf8");
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);

async function login(page) {
  await page.goto(`${BASE}/user/login`, { waitUntil: "domcontentloaded", timeout: 60000 });
  await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll("button")).find(
      (b) => b.textContent.trim() === "登录 / 注册"
    );
    if (btn) btn.click();
  });
  await page.waitForSelector("form input[name=userAccount]", { timeout: 20000 });
  await page.evaluate(() => {
    const set = (el, v) => {
      const d = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
      d.set.call(el, v);
      el.dispatchEvent(new Event("input", { bubbles: true }));
      el.dispatchEvent(new Event("change", { bubbles: true }));
    };
    set(document.querySelector("form input[name=userAccount]"), "qa_b_nov11");
    set(document.querySelector("form input[name=userPassword]"), "qaPass12345");
    const form = document.querySelector("form");
    Array.from(form.querySelectorAll("button")).find((b) => b.textContent.trim() === "登录").click();
  });
  await page.waitForFunction(() => Boolean(localStorage.getItem("userId")), { timeout: 20000 });
  await sleep(800);
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: true,
    args: ["--no-sandbox", "--disable-gpu"],
    defaultViewport: { width: 1440, height: 900 },
  });
  const evidence = {};
  try {
    const page = await browser.newPage();
    await login(page);
    const start = await page.evaluate(async () => {
      const r = await fetch("/api/learning/assessment/start", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          courseCode: "course.database-systems",
          count: 2,
          mode: "SELF_TEST",
          studyState: "ALL",
        }),
        credentials: "include",
      });
      return r.json();
    });
    evidence.start = start;
    const runId = start.data.runId;
    await page.goto(`${BASE}/assessments/run/${runId}`, {
      waitUntil: "domcontentloaded",
      timeout: 60000,
    });
    await page.waitForSelector("article.self-test-card", { timeout: 30000 });
    await sleep(1200);

    // 断网后作答：保存失败、输入保留
    await page.setOfflineMode(true);
    const option = await page.$("article.self-test-card .stc-option");
    if (option) await option.click();
    let saveErrorSeen = false;
    try {
      await page.waitForFunction(
        () => /草稿保存失败/.test(document.body.innerText),
        { timeout: 15000 }
      );
      saveErrorSeen = true;
    } catch {
      saveErrorSeen = false;
    }
    evidence.offline = await page.evaluate(() => ({
      saveErrorSeen: /草稿保存失败/.test(document.body.innerText),
      checked: document.querySelectorAll("article.self-test-card .arco-radio-checked").length,
      text: document.body.innerText.slice(0, 900),
    }));
    evidence.offline.saveErrorSeen = saveErrorSeen;
    await page.screenshot({ path: path.join(SHOTS, "b06-offline-save-failure.png") });

    // 恢复网络并重试保存
    await page.setOfflineMode(false);
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll("button")).find((b) =>
        b.textContent.includes("重试保存")
      );
      if (btn) btn.click();
    });
    let recovered = false;
    try {
      await page.waitForFunction(
        () => /已保存/.test(document.querySelector(".save-state")?.textContent || ""),
        { timeout: 15000 }
      );
      recovered = true;
    } catch {
      recovered = false;
    }
    const after = await page.evaluate(async (id) => {
      const r = await fetch(`/api/learning/assessment/run?runId=${id}&t=${Date.now()}`, {
        credentials: "include",
        cache: "no-store",
      });
      return r.json();
    }, runId);
    evidence.recovered = {
      recovered,
      draft: after?.data?.questions?.[0]?.draftAnswer ?? null,
      saveState: await page.evaluate(() =>
        document.querySelector(".save-state")?.textContent.trim()
      ),
    };
    log("offline retry captured");

    // 390px 移动布局
    await page.setViewport({ width: 390, height: 844, isMobile: true, hasTouch: true });
    await page.reload({ waitUntil: "domcontentloaded", timeout: 60000 });
    await page.waitForSelector("article.self-test-card", { timeout: 30000 });
    await sleep(1500);
    evidence.mobile = await page.evaluate(() => ({
      width: window.innerWidth,
      scrollWidth: document.documentElement.scrollWidth,
      noHorizontalOverflow: document.documentElement.scrollWidth <= window.innerWidth + 1,
      buttons: Array.from(document.querySelectorAll("button"))
        .map((b) => b.textContent.trim())
        .filter(Boolean)
        .slice(0, 20),
      cardVisible: Boolean(document.querySelector("article.self-test-card")),
      noteVisible: /交卷后统一判分/.test(document.body.innerText),
    }));
    await page.screenshot({ path: path.join(SHOTS, "b04-mobile-self-test.png") });
    save("phase5.json", evidence);
    log("PHASE5 DONE");
  } catch (e) {
    save("phase5-error.json", { message: String(e), stack: String(e.stack) });
    console.error("PHASE5 FAILED", e);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
