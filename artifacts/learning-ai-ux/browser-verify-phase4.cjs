/* 阶段 4：单元活动完成通知与测评返回原单元。 */
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
    await page.evaluate(() => {
      window.__bLearningEvents = [];
      window.addEventListener("oslab:learning-updated", (e) =>
        window.__bLearningEvents.push(e.detail)
      );
    });

    // ---- 单元活动完成：仅在后端成功后发送通知 ----
    const unitUrl = `${BASE}/courses/course.database-systems/units/unit.db.fd-keys`;
    await page.goto(unitUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    await sleep(3000);
    evidence.unitBefore = await page.evaluate(() => ({
      url: location.href,
      title: document.title,
      text: document.body.innerText.slice(0, 800),
      buttons: Array.from(document.querySelectorAll("button"))
        .map((b) => b.textContent.trim())
        .filter(Boolean)
        .slice(0, 30),
    }));
    const completed = await page.evaluate(async () => {
      const btn = Array.from(document.querySelectorAll("button")).find((b) =>
        /确认阅读完成|完成小结/.test(b.textContent)
      );
      if (!btn) return { clicked: false };
      btn.click();
      await new Promise((r) => setTimeout(r, 2500));
      return {
        clicked: true,
        text: document.body.innerText.slice(0, 900),
        events: window.__bLearningEvents || [],
      };
    });
    evidence.unitComplete = completed;
    await page.screenshot({ path: path.join(SHOTS, "b09-unit-complete.png") });
    log("B09 unit complete captured");

    // ---- 从单元范围自测，交卷后返回原单元 ----
    const scopeUrl = `${BASE}/assessments?courseCode=course.database-systems&sectionCode=sec.db.fd&unitCode=unit.db.fd-keys`;
    await page.goto(scopeUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    await page.waitForFunction(
      () =>
        Array.from(document.querySelectorAll("label, .arco-radio, .arco-radio-button")).some(
          (n) => n.textContent.trim() === "整组自测"
        ),
      { timeout: 30000 }
    );
    await page.evaluate(() => {
      const selfTest = Array.from(
        document.querySelectorAll("label, .arco-radio, .arco-radio-button")
      ).find((n) => n.textContent.trim() === "整组自测");
      selfTest.click();
      const input = selfTest.querySelector && selfTest.querySelector("input[type=radio]");
      if (input) input.click();
    });
    await sleep(700);
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll("button")).find(
        (b) => b.textContent.trim() === "开始整组自测"
      );
      if (btn) btn.click();
    });
    await page.waitForFunction(() => location.pathname.startsWith("/assessments/run/"), {
      timeout: 30000,
    });
    await page.waitForSelector("article.self-test-card", { timeout: 30000 });
    await sleep(1200);
    const runUrl = await page.evaluate(() => location.href);
    evidence.scopedRunUrl = runUrl;
    const options = await page.$$("article.self-test-card .stc-option");
    if (options.length) await options[0].click();
    await sleep(1600);
    await page.evaluate(() => {
      window.__bLearningEvents = [];
      const btn = Array.from(document.querySelectorAll(".head-actions button")).find(
        (b) => b.textContent.trim() === "交卷"
      );
      if (btn) btn.click();
    });
    await sleep(800);
    await page.evaluate(() => {
      const modal = document.querySelector(".arco-modal");
      const btn = modal
        ? Array.from(modal.querySelectorAll("button")).find((b) => b.textContent.trim() === "交卷")
        : null;
      if (btn) btn.click();
    });
    await page.waitForFunction(() => document.body.innerText.includes("自测结果"), {
      timeout: 30000,
    });
    await sleep(1500);
    evidence.scopedFinishEvents = await page.evaluate(() => window.__bLearningEvents || []);
    evidence.scopedResult = await page.evaluate(() => ({
      text: document.body.innerText.slice(0, 700),
      buttons: Array.from(document.querySelectorAll("button"))
        .map((b) => b.textContent.trim())
        .filter(Boolean)
        .slice(0, 20),
    }));
    const returned = await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll("button")).find((b) =>
        /返回原单元/.test(b.textContent)
      );
      if (!btn) return { clicked: false };
      btn.click();
      return { clicked: true };
    });
    await sleep(1800);
    evidence.returned = {
      ...returned,
      url: await page.evaluate(() => location.href),
      text: await page.evaluate(() => document.body.innerText.slice(0, 500)),
    };
    await page.screenshot({ path: path.join(SHOTS, "b09-return-unit.png") });
    save("phase4.json", evidence);
    log("PHASE4 DONE");
  } catch (e) {
    save("phase4-error.json", { message: String(e), stack: String(e.stack) });
    console.error("PHASE4 FAILED", e);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
