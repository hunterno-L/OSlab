const puppeteer = require("puppeteer-core");
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
const BASE = "http://localhost:8081";
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
  await page.goto(`${BASE}/assessments?courseCode=course.operating-systems`, { waitUntil: "networkidle2" });
  await new Promise((r) => setTimeout(r, 5000));
  const info = await page.evaluate(() => ({
    url: location.href,
    title: document.title,
    userId: localStorage.getItem("userId"),
    radios: Array.from(document.querySelectorAll(".arco-radio")).map((n) => n.textContent.trim()).slice(0, 10),
    allRadioInputs: document.querySelectorAll("input[type=radio]").length,
    bodyHead: document.body.innerText.slice(0, 900),
    counts: { articles: document.querySelectorAll("article").length, buttons: document.querySelectorAll("button").length },
  }));
  console.log(JSON.stringify(info, null, 2));
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
