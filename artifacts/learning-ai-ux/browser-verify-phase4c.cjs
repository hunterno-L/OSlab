/* 阶段 4c：打开未完成阅读活动完成它，验证 ACTIVITY_DONE；验证“返回题目练习”回到带单元范围的原页。 */
const fs = require("fs");
const path = require("path");
const puppeteer = require("puppeteer-core");

const EVIDENCE = path.join(__dirname, "evidence");
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
    await page.evaluateOnNewDocument(() => {
      window.__bLearningEvents = [];
      window.addEventListener("oslab:learning-updated", (e) =>
        window.__bLearningEvents.push(e.detail)
      );
    });
    await login(page);

    // 打开第二条阅读活动并完成
    await page.goto(`${BASE}/courses/course.database-systems/units/unit.db.fd-keys`, {
      waitUntil: "domcontentloaded",
      timeout: 60000,
    });
    await sleep(2500);
    const opened = await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll("button")).find((b) =>
        /超键与候选键/.test(b.textContent) && !/下一项/.test(b.textContent)
      );
      if (!btn) return false;
      btn.click();
      return true;
    });
    await sleep(2500);
    evidence.activityOpened = opened;
    evidence.buttonsAfterOpen = await page.evaluate(() =>
      Array.from(document.querySelectorAll("button"))
        .map((b) => b.textContent.trim())
        .filter(Boolean)
        .slice(0, 40)
    );
    const completed = await page.evaluate(async () => {
      const btn = Array.from(document.querySelectorAll("button")).find((b) =>
        /确认阅读完成|完成小结/.test(b.textContent) && !b.disabled
      );
      if (!btn) return { clicked: false };
      btn.click();
      await new Promise((r) => setTimeout(r, 3000));
      return {
        clicked: true,
        events: window.__bLearningEvents || [],
        text: document.body.innerText.slice(0, 500),
      };
    });
    evidence.unitDone = completed;
    log("unit completion captured");

    // 结果页返回按钮：应回到带单元范围的题目练习页
    await page.goto(`${BASE}/assessments/run/2120324708202946562`, {
      waitUntil: "domcontentloaded",
      timeout: 60000,
    });
    await sleep(2500);
    const returned = await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll("button")).find((b) =>
        /返回原单元|返回题目练习|继续练习/.test(b.textContent)
      );
      const label = btn ? btn.textContent.trim() : null;
      if (btn) btn.click();
      return { label, clicked: Boolean(btn) };
    });
    await sleep(1800);
    evidence.returned = {
      ...returned,
      url: await page.evaluate(() => location.href),
    };
    save("phase4c.json", evidence);
    log("PHASE4C DONE");
  } catch (e) {
    save("phase4c-error.json", { message: String(e), stack: String(e.stack) });
    console.error("PHASE4C FAILED", e);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
