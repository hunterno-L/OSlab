/* 阶段 2b：全部以字符串 ID 重做交卷幂等/并发/综合题结果。 */
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
  const comprehensiveRunId = "2120323200883335170";
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

    // ---- 主 run：原交卷键重发返回同一结果；不同键被拒绝 ----
    const mainResult = await api(
      page,
      "GET",
      `/api/learning/assessment/runs/${mainRunId}/result`
    );
    const repeatSameKey = await api(page, "POST", "/api/learning/assessment/finish", {
      runId: mainRunId,
      submissionKey: mainKey,
      rowVersion: 0,
    });
    const repeatDifferentKey = await api(page, "POST", "/api/learning/assessment/finish", {
      runId: mainRunId,
      submissionKey: `qa-different-${Date.now()}`,
      rowVersion: 0,
    });
    evidence.main = {
      mainRunId,
      result: mainResult.json,
      repeatSameKey: repeatSameKey.json,
      sameResult:
        JSON.stringify(repeatSameKey.json?.data) ===
        JSON.stringify(mainResult.json?.data),
      repeatDifferentKey: repeatDifferentKey.json,
    };
    save("b07-idempotency.json", evidence.main);
    log("B07 idempotency recaptured");

    // ---- 并发交卷：同一新 run、同一键同时发两次 ----
    const startA = await api(page, "POST", "/api/learning/assessment/start", {
      courseCode: "course.operating-systems",
      count: 1,
      mode: "SELF_TEST",
      studyState: "ALL",
    });
    const runA = startA.json?.data?.runId;
    const keyA = `qa-concurrent-${runA}`;
    const both = await page.evaluate(
      async (id, key) => {
        const send = () =>
          fetch("/api/learning/assessment/finish", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ runId: id, submissionKey: key, rowVersion: 0 }),
            credentials: "include",
          }).then((r) => r.json());
        return Promise.all([send(), send()]);
      },
      runA,
      keyA
    );
    const differentKey = await api(page, "POST", "/api/learning/assessment/finish", {
      runId: runA,
      submissionKey: `qa-other-${runA}`,
      rowVersion: 0,
    });
    evidence.concurrent = {
      runId: runA,
      responses: both,
      sameResultsEqual: JSON.stringify(both[0]?.data) === JSON.stringify(both[1]?.data),
      differentKey: differentKey.json,
    };
    save("b07-concurrent.json", evidence.concurrent);
    log("B07 concurrency recaptured");

    // ---- 综合题定向 run：交卷并读取结果 ----
    const compRun = await api(
      page,
      "GET",
      `/api/learning/assessment/run?runId=${comprehensiveRunId}`
    );
    const compFinish = await api(page, "POST", "/api/learning/assessment/finish", {
      runId: comprehensiveRunId,
      submissionKey: `qa-comprehensive-${comprehensiveRunId}`,
      rowVersion: compRun.json?.data?.rowVersion ?? 0,
    });
    const compResult = await api(
      page,
      "GET",
      `/api/learning/assessment/runs/${comprehensiveRunId}/result`
    );
    evidence.comprehensive = {
      run: compRun.json,
      finish: compFinish.json,
      result: compResult.json,
    };
    save("b08-comprehensive-result.json", compResult.json);
    save("b08-comprehensive.json", evidence.comprehensive);
    log("B08 comprehensive recaptured");

    // 结果页截图（综合题显示待自评）
    await page.goto(`${BASE}/assessments/run/${comprehensiveRunId}`, {
      waitUntil: "domcontentloaded",
      timeout: 60000,
    });
    await sleep(2500);
    evidence.comprehensiveResultText = await page.evaluate(() =>
      document.body.innerText.slice(0, 1600)
    );
    await page.screenshot({ path: path.join(SHOTS, "b08-comprehensive-result.png") });

    save("phase2b.json", evidence);
    log("PHASE2B DONE");
  } catch (e) {
    save("phase2b-error.json", { message: String(e), stack: String(e.stack) });
    console.error("PHASE2B FAILED", e);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
