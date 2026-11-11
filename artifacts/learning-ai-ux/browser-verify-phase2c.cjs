/* 阶段 2c：综合题待自评路径 + 同键交卷两次完全一致。 */
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
  const mainKey = fs
    .readFileSync(path.join(EVIDENCE, "main-submission-key.txt"), "utf8")
    .trim();
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

    // ---- 主 run：同一交卷键连续两次返回完全一致 ----
    const first = await api(page, "POST", "/api/learning/assessment/finish", {
      runId: mainRunId,
      submissionKey: mainKey,
      rowVersion: 0,
    });
    const second = await api(page, "POST", "/api/learning/assessment/finish", {
      runId: mainRunId,
      submissionKey: mainKey,
      rowVersion: 0,
    });
    evidence.mainRepeat = {
      first: first.json,
      second: second.json,
      identical: JSON.stringify(first.json) === JSON.stringify(second.json),
    };
    save("b07-repeat-same-key.json", evidence.mainRepeat);
    log("B07 repeat verified");

    // ---- 综合题：写入草稿后交卷，应为待自评 ----
    const start = await api(page, "POST", "/api/learning/assessment/start", {
      courseCode: "course.operating-systems",
      nodeCode: "os.process.pv_operation",
      count: 5,
      mode: "SELF_TEST",
      studyState: "ALL",
    });
    const runId = start.json?.data?.runId;
    const run = await api(page, "GET", `/api/learning/assessment/run?runId=${runId}`);
    const comp = (run.json?.data?.questions || []).find((q) => q.type === "COMPREHENSIVE");
    const drafts = (run.json?.data?.questions || []).map((q) => ({
      questionId: q.id,
      answer: q.id === comp?.id ? "我的自测作答：先列出信号量初值与同步关系，再写出 P/V 顺序。" : q.draftAnswer ?? "",
      markedUnsure: false,
    }));
    const saveDraft = await api(page, "PUT", `/api/learning/assessment/runs/${runId}/drafts`, {
      clientSaveId: `qa-comp-${Date.now()}`,
      rowVersion: run.json?.data?.rowVersion ?? 0,
      drafts,
    });
    const finish = await api(page, "POST", "/api/learning/assessment/finish", {
      runId,
      submissionKey: `qa-comp-pending-${runId}`,
      rowVersion: saveDraft.json?.data?.rowVersion ?? 0,
    });
    const result = await api(page, "GET", `/api/learning/assessment/runs/${runId}/result`);
    evidence.comprehensive = {
      runId,
      compQuestionId: comp?.id,
      saveDraft: saveDraft.json,
      finish: finish.json,
      result: result.json,
    };
    save("b08-comprehensive-pending.json", evidence.comprehensive);
    log("B08 pending recaptured");

    await page.goto(`${BASE}/assessments/run/${runId}`, {
      waitUntil: "domcontentloaded",
      timeout: 60000,
    });
    await sleep(2500);
    evidence.resultText = await page.evaluate(() =>
      document.body.innerText.slice(0, 1600)
    );
    await page.screenshot({ path: path.join(SHOTS, "b08-comprehensive-result.png") });

    save("phase2c.json", evidence);
    log("PHASE2C DONE runId=", runId);
  } catch (e) {
    save("phase2c-error.json", { message: String(e), stack: String(e.stack) });
    console.error("PHASE2C FAILED", e);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
