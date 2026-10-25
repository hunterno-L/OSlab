// Run with: node tests/home-contracts.js
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const src = (relative) =>
  fs.readFileSync(path.join(root, "src", relative), "utf8");
const has = (source, needle, message) =>
  assert.ok(source.includes(needle), `${message}: 缺少 ${needle}`);
const lacks = (source, needle, message) =>
  assert.ok(!source.includes(needle), `${message}: 不应包含 ${needle}`);

const home = src("views/HomeView.vue");
for (const api of [
  "listCourses",
  "getCourseResume",
  "getCourseOutline",
  "getCourseUnit",
  "personalGraph",
  "getTaskSummary",
]) {
  has(home, api, `首页必须使用 ${api}`);
}
has(home, "unitState", "首页必须有当前单元加载态");
has(home, "profileState", "首页必须有画像加载态");
has(home, "重试", "首页必须提供区块级重试");
lacks(home, "<main", "首页不得嵌套 main 地标");

const entries = src("components/home/EntryLedger.vue");
for (const route of [
  "/assessments",
  "/experiments",
  "/experiments/simulation",
  "/resources",
]) {
  has(entries, route, `目录入口必须包含 ${route}`);
}

const routes = src("router/routes.ts");
const start = routes.indexOf('path: "/home"');
const end = routes.indexOf('path: "/assessments"');
const homeRoute = routes.slice(start, end);
lacks(homeRoute, "pageTitle", "首页路由不应设置 pageTitle");

console.log("home contracts passed");
