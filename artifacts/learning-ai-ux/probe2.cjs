const puppeteer = require("puppeteer-core");
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
const BASE = "http://localhost:8081";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: true,
    args: ["--no-sandbox", "--disable-gpu"],
    defaultViewport: { width: 1440, height: 900 },
  });
  const page = await browser.newPage();
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
  await sleep(800);
  const start = await page.evaluate(async () => {
    const r = await fetch("/api/learning/assessment/start", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ courseCode: "course.operating-systems", count: 1, mode: "SELF_TEST", studyState: "ALL" }),
      credentials: "include",
    });
    return r.json();
  });
  console.log("start", JSON.stringify(start));
  await page.goto(`${BASE}/assessments/run/${start.data.runId}`, { waitUntil: "networkidle2" });
  await page.waitForSelector("article.self-test-card", { timeout: 30000 });
  await sleep(1000);
  const before = await page.evaluate(() => {
    const card = document.querySelector("article.self-test-card");
    const label = card.querySelector(".stc-option");
    return {
      cardHead: card.innerText.slice(0, 200),
      optionCount: card.querySelectorAll(".stc-option").length,
      optionOuter: label ? label.outerHTML.slice(0, 500) : null,
      radios: card.querySelectorAll("input[type=radio]").length,
    };
  });
  console.log("BEFORE", JSON.stringify(before, null, 2));
  await page.evaluate(() => {
    document.querySelector("article.self-test-card .stc-option").click();
  });
  await sleep(1500);
  const afterLabel = await page.evaluate(() => {
    const card = document.querySelector("article.self-test-card");
    return {
      checked: Array.from(card.querySelectorAll("input[type=radio]")).map((r) => r.checked),
      saveState: document.querySelector(".save-state")?.textContent.trim(),
      cardText: card.innerText.slice(-160),
    };
  });
  console.log("AFTER LABEL", JSON.stringify(afterLabel));
  if (!afterLabel.checked.some(Boolean)) {
    await page.evaluate(() => {
      const input = document.querySelector("article.self-test-card input[type=radio]");
      input.click();
      input.dispatchEvent(new Event("change", { bubbles: true }));
    });
    await sleep(1500);
    const afterInput = await page.evaluate(() => {
      const card = document.querySelector("article.self-test-card");
      return {
        checked: Array.from(card.querySelectorAll("input[type=radio]")).map((r) => r.checked),
        saveState: document.querySelector(".save-state")?.textContent.trim(),
      };
    });
    console.log("AFTER INPUT", JSON.stringify(afterInput));
  }
  const runNow = await page.evaluate(async (id) => {
    const r = await fetch(`/api/learning/assessment/run?runId=${id}`, { credentials: "include" });
    return r.json();
  }, start.data.runId);
  console.log("RUN", JSON.stringify(runNow.data.questions.map((q) => ({ id: q.id, draft: q.draftAnswer, unsure: q.markedUnsure }))));
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
