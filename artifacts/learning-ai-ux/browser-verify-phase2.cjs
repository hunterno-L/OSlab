/* 阶段 2：自测冲突、交卷幂等、结果页、综合题、绕过检查。
 * 依赖阶段 1 产出的 runId 与阶段 1 的草稿状态。
 */
const fs = require("fs");
const path = require("path");
const puppeteer = require("puppeteer-core");

const OUT = __dirname;
const EVIDENCE = path.join(OUT, "evidence");
const SHOTS = path.join(OUT, "screenshots");
const BASE = "http://localhost:8081";
const ACCOUNT = "qa_b_nov11";
const PASSWORD = "qaPass12345";
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const save = (name, value) =>
  fs.writeFileSync(path.join(EVIDENCE, name), JSON.stringify(value, null, 2), "utf8");
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);

async function login(page) {
  await page.goto(`${BASE}/home`, { waitUntil: "domcontentloaded", timeout: 60000 });
  await sleep(600);
  if (await page.evaluate(() => Boolean(localStorage.getItem("userId")))) return;
  await page.goto(`${BASE}/user/login`, { waitUntil: "domcontentloaded", timeout: 60000 });
  await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll("button")).find(
      (b) => b.textContent.trim() === "登录 / 注册"
    );
    if (btn) btn.click();
  });
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
      const form = document.querySelector("form");
      Array.from(form.querySelectorAll("button")).find((b) => b.textContent.trim() === "登录").click();
    },
    ACCOUNT,
    PASSWORD
  );
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

async function waitSaved(page) {
  await page.waitForFunction(
    () => /已保存/.test(document.querySelector(".save-state")?.textContent || ""),
    { timeout: 15000 }
  );
}

(async () => {
  const runId = JSON.parse(fs.readFileSync(path.join(EVIDENCE, "phase1-summary.json"), "utf8")).runId;
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: true,
    args: ["--no-sandbox", "--disable-gpu"],
    defaultViewport: { width: 1440, height: 900 },
  });
  const evidence = { runId };
  try {
    const page = await browser.newPage();
    await login(page);
    const runUrl = `${BASE}/assessments/run/${runId}?returnTo=/assessments?courseCode=course.operating-systems&courseCode=course.operating-systems`;
    await page.goto(runUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    await page.waitForSelector("article.self-test-card", { timeout: 30000 });
    await sleep(800);

    const run = await api(page, "GET", `/api/learning/assessment/run?runId=${runId}`);
    save("b04-active-run.json", run.json);
    const queue = run.json.data.questions.map((q) => ({ id: String(q.id), type: q.type, title: q.title }));
    evidence.queue = queue;
    evidence.hasAnalysisInActiveRun = run.json.data.questions.some(
      (q) => q.analysis !== undefined && q.analysis !== null
    );
    evidence.hasCorrectInActiveRun = run.json.data.questions.some(
      (q) => q.correct !== undefined || q.latestCorrect !== undefined && q.latestCorrect !== null
    );

    // ---- B05 已知解析/普通作答路径不能绕过 ----
    const qid = queue[0].id;
    evidence.b05Detail = await api(
      page,
      "GET",
      `/api/learning/questions/get?questionId=${qid}`
    );
    evidence.b05Answer = await api(page, "POST", "/api/learning/questions/answer", {
      questionId: qid,
      answer: "A",
      submissionKey: `qa-bypass-${runId}`,
    });
    evidence.b05ListPage = await api(page, "POST", "/api/learning/questions/list/page", {
      courseCode: "course.operating-systems",
      pageSize: 5,
      current: 1,
    });
    save("b05-bypass.json", {
      detail: evidence.b05Detail.json,
      answer: evidence.b05Answer.json,
      listHasAnalysis: (evidence.b05ListPage.json?.data?.records || []).some(
        (q) => q.analysis
      ),
    });

    // ---- B06 双标签页版本冲突：另一页先保存，旧版本页保存必须冲突 ----
    const pageB = await browser.newPage();
    await pageB.goto(runUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    await pageB.waitForSelector("article.self-test-card", { timeout: 30000 });
    await sleep(800);

    const beforeConflict = await api(page, "GET", `/api/learning/assessment/run?runId=${runId}`);
    const baseVersion = beforeConflict.json?.data?.rowVersion ?? 0;
    const baseDrafts = (beforeConflict.json?.data?.questions || []).map((q) => ({
      questionId: q.id,
      answer: q.draftAnswer ?? "",
      markedUnsure: Boolean(q.markedUnsure),
    }));
    const bump = await api(page, "PUT", `/api/learning/assessment/runs/${runId}/drafts`, {
      clientSaveId: `qa-other-tab-${Date.now()}`,
      rowVersion: baseVersion,
      drafts: baseDrafts,
    });
    evidence.b06 = { baseVersion, bump: bump.json };
    const pageBOptions = await pageB.$$("article.self-test-card .stc-option");
    if (pageBOptions.length > 2) await pageBOptions[2].click();
    let conflictSeen = false;
    try {
      await pageB.waitForFunction(
        () => document.body.innerText.includes("草稿版本冲突"),
        { timeout: 15000 }
      );
      conflictSeen = true;
    } catch {
      conflictSeen = false;
    }
    evidence.b06.conflictSeen = conflictSeen;
    evidence.b06.pageText = await pageB.evaluate(() =>
      document.body.innerText.slice(0, 800)
    );
    if (conflictSeen) {
      await pageB.evaluate(() => {
        const btn = Array.from(document.querySelectorAll("button")).find((b) =>
          b.textContent.includes("载入服务端草稿")
        );
        if (btn) btn.click();
      });
      await sleep(800);
      evidence.b06.afterApply = await pageB.evaluate(() =>
        document.body.innerText.includes("草稿版本冲突")
      );
    }
    save("b06-conflict.json", evidence.b06);
    await pageB.close();

    // 刷新第一页，拿到最新 rowVersion 与草稿再交卷
    await page.goto(runUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    await page.waitForSelector("article.self-test-card", { timeout: 30000 });
    await sleep(1500);

    // ---- B07 交卷 + 幂等 + 结果页 ----
    await page.evaluate(() => {
      window.__bLearningEvents = [];
      window.addEventListener("oslab:learning-updated", (e) =>
        window.__bLearningEvents.push(e.detail)
      );
    });
    await sleep(800);
    await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll(".head-actions button")).find(
        (b) => b.textContent.trim() === "交卷"
      );
      if (btn) btn.click();
    });
    await sleep(700);
    evidence.confirmText = await page.evaluate(() => document.body.innerText.slice(-500));
    await page.evaluate(() => {
      const modal = document.querySelector(".arco-modal");
      const btn = modal
        ? Array.from(modal.querySelectorAll("button")).find((b) => b.textContent.trim() === "交卷")
        : null;
      if (btn) btn.click();
    });
    await page.waitForFunction(
      () => document.body.innerText.includes("自测结果"),
      { timeout: 30000 }
    );
    await sleep(1500);
    evidence.finishEvents = await page.evaluate(() => window.__bLearningEvents || []);
    evidence.submissionKey = await page.evaluate((id) => {
      const keys = Object.keys(sessionStorage);
      const key = keys.find((k) => k.includes(`:${id}`));
      return key ? sessionStorage.getItem(key) : null;
    }, runId);
    const result1 = await api(page, "GET", `/api/learning/assessment/runs/${runId}/result`);
    save("b07-result.json", result1.json);
    evidence.resultSummary = result1.json?.data?.summaryText;
    evidence.resultStats = {
      total: result1.json?.data?.totalQuestions,
      valid: result1.json?.data?.validQuestions,
      answered: result1.json?.data?.answeredQuestions,
      unanswered: result1.json?.data?.unansweredQuestions,
      pending: result1.json?.data?.pendingSelfAssessmentCount,
      invalid: result1.json?.data?.invalidCount,
      accuracy: result1.json?.data?.accuracy,
    };
    await page.evaluate(() => window.scrollTo(0, 0));
    await sleep(300);
    await page.screenshot({ path: path.join(SHOTS, "b07-self-test-result.png") });

    const repeat = await api(page, "POST", "/api/learning/assessment/finish", {
      runId: Number(runId),
      submissionKey: evidence.submissionKey,
      rowVersion: 0,
    });
    const differentKey = await api(page, "POST", "/api/learning/assessment/finish", {
      runId: Number(runId),
      submissionKey: `qa-different-${Date.now()}`,
      rowVersion: 0,
    });
    evidence.repeatFinish = repeat.json;
    evidence.differentKeyFinish = differentKey.json;
    evidence.repeatEqualsStored =
      JSON.stringify(repeat.json?.data) === JSON.stringify(result1.json?.data)
        ? "result-json-differs-by-mode-field"
        : "different-shape";
    save("b07-idempotency.json", {
      repeat: repeat.json,
      differentKey: differentKey.json,
    });

    // 返回路径
    const returnInfo = await page.evaluate(() => {
      const btn = Array.from(document.querySelectorAll("button")).find((b) =>
        /返回原单元|继续练习|返回题目练习/.test(b.textContent)
      );
      const text = btn ? btn.textContent.trim() : null;
      if (btn) btn.click();
      return text;
    });
    await sleep(1800);
    evidence.returnButton = returnInfo;
    evidence.returnUrl = await page.evaluate(() => location.href);

    // ---- 并发交卷（新 run，1 题） ----
    const start2 = await api(page, "POST", "/api/learning/assessment/start", {
      courseCode: "course.operating-systems",
      count: 1,
      mode: "SELF_TEST",
      studyState: "ALL",
    });
    evidence.concurrentStart = start2.json;
    const runId2 = start2.json?.data?.runId;
    if (runId2) {
      const mk = (key) =>
        api(page, "POST", "/api/learning/assessment/finish", {
          runId: Number(runId2),
          submissionKey: key,
          rowVersion: 0,
        });
      const key = `qa-concurrent-${runId2}`;
      const both = await page.evaluate(
        async (id, k) => {
          const send = () =>
            fetch("/api/learning/assessment/finish", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ runId: id, submissionKey: k, rowVersion: 0 }),
              credentials: "include",
            }).then((r) => r.json());
          return Promise.all([send(), send()]);
        },
        Number(runId2),
        key
      );
      const other = await mk(`qa-other-${runId2}`);
      evidence.concurrent = {
        runId2,
        same: both,
        sameResultsEqual: JSON.stringify(both[0]?.data) === JSON.stringify(both[1]?.data),
        differentKey: other.json,
      };
      save("b07-concurrent.json", evidence.concurrent);
    }

    // ---- B08 综合题：定向节点自测 ----
    const start3 = await api(page, "POST", "/api/learning/assessment/start", {
      courseCode: "course.operating-systems",
      nodeCode: "os.process.pv_operation",
      count: 5,
      mode: "SELF_TEST",
      studyState: "ALL",
    });
    evidence.comprehensiveStart = start3.json;
    const runId3 = start3.json?.data?.runId;
    if (runId3) {
      const run3 = await api(page, "GET", `/api/learning/assessment/run?runId=${runId3}`);
      evidence.comprehensiveTypes = (run3.json?.data?.questions || []).map((q) => q.type);
      save("b08-comprehensive-run.json", run3.json);
      const finish3 = await api(page, "POST", "/api/learning/assessment/finish", {
        runId: Number(runId3),
        submissionKey: `qa-comprehensive-${runId3}`,
        rowVersion: run3.json?.data?.rowVersion ?? 0,
      });
      evidence.comprehensiveFinish = finish3.json;
      const result3 = await api(page, "GET", `/api/learning/assessment/runs/${runId3}/result`);
      save("b08-comprehensive-result.json", result3.json);
      evidence.comprehensiveStats = {
        total: result3.json?.data?.totalQuestions,
        valid: result3.json?.data?.validQuestions,
        answered: result3.json?.data?.answeredQuestions,
        unanswered: result3.json?.data?.unansweredQuestions,
        pending: result3.json?.data?.pendingSelfAssessmentCount,
        invalid: result3.json?.data?.invalidCount,
        statuses: (result3.json?.data?.questions || []).map((q) => ({
          type: q.type,
          status: q.status,
          correct: q.correct,
        })),
      };
      await page.goto(`${BASE}/assessments/run/${runId3}`, {
        waitUntil: "domcontentloaded",
        timeout: 60000,
      });
      await sleep(2000);
      evidence.comprehensiveResultText = await page.evaluate(() =>
        document.body.innerText.slice(0, 1500)
      );
    }

    save("phase2.json", evidence);
    log("PHASE2 DONE");
  } catch (e) {
    save("phase2-error.json", { message: String(e), stack: String(e.stack) });
    console.error("PHASE2 FAILED", e);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
