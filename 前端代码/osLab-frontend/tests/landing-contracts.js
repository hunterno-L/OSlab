// Run with: node tests/landing-contracts.js
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8");
const src = (relative) => read(path.join("src", relative));
const has = (source, needle, message) =>
  assert.ok(source.includes(needle), `${message}: 缺少 ${needle}`);
const lacks = (source, needle, message) =>
  assert.ok(!source.includes(needle), `${message}: 不应包含 ${needle}`);

const theater = src("components/theater/PagingTheater.vue");
has(theater, "IntersectionObserver", "剧场必须在离屏时暂停");
has(theater, "prefers-reduced-motion", "剧场必须支持减少动态");
has(theater, 'aria-live="polite"', "剧场必须播报当前节拍");
has(theater, 'aria-hidden="true"', "Canvas 本身必须对读屏隐藏");
has(theater, "Math.min(window.devicePixelRatio || 1, 2)", "DPR 必须封顶 2");
has(theater, "requestAnimationFrame", "剧场必须使用 requestAnimationFrame");
has(theater, "cancelAnimationFrame", "暂停时必须取消动画帧");
lacks(theater, "shadowBlur", "剧场不得使用大面积阴影模糊");

const routes = src("router/routes.ts");
has(routes, 'path: "/welcome"', "必须有公开落地页路由");
has(routes, '"/welcome"', "根路径无角色时必须回退到 /welcome");

const dialog = src("components/auth/AuthDialog.vue");
has(dialog, 'aria-modal="true"', "弹层必须有 aria-modal");
has(dialog, "Escape", "弹层必须支持 Esc 关闭");
has(dialog, 'router.replace("/welcome")', "关闭弹层必须回到落地页");
has(dialog, "data-auth-trigger", "关闭后必须把焦点归还触发按钮");

const layout = src("layouts/UserLayout.vue");
lacks(layout, "AuthFlowCanvas", "旧知识流动画必须移除");
lacks(layout, "os-main", "旧左右分栏必须移除");

const landing = src("views/landing/LandingView.vue");
for (const text of ["AUTH ENTRY", "2026.10", "01 / 06"]) {
  lacks(landing + dialog, text, "装饰性微文案必须删除");
}
has(landing, "ZhiCore", "落地页必须展示新品牌名");
has(landing, "Page&amp;Frame", "落地页必须使用简洁的英文主标题");
lacks(landing, "沿着章节，逐步掌握操作系统", "旧的长主标题必须删除");
lacks(landing, "查看平台功能", "平台功能辅助按钮必须删除");
lacks(landing, "landing-eyebrow", "蓝色眉题必须删除");
lacks(landing, "landing-kicker", "首屏装饰性小字必须删除");
has(landing, "landing-hero.png", "首屏必须使用定制背景图");
has(landing, 'id="features"', "首屏之后必须展示功能概览");
const heroMarkup = landing.split('id="features"')[0];
lacks(heroMarkup, "PagingTheater", "分页演示应放在功能介绍中而非首屏");
has(landing, "PagingTheater", "功能介绍必须展示分页演示");
has(landing, "IntersectionObserver", "落地页区块必须按滚动触发淡入");
has(
  landing,
  'classList.toggle("is-visible", entry.isIntersecting)',
  "区块离开视口时必须淡出"
);
has(landing, "prefers-reduced-motion", "落地页动画必须尊重减少动态偏好");

for (const anchor of ["mainline", "graph", "practice", "evidence"]) {
  has(landing, `id="${anchor}"`, `落地页必须有 #${anchor} 区块`);
}
has(landing, "MainlineDemo", "必须使用学习主线演示");
has(landing, "GraphProfileDemo", "必须使用图谱/画像演示");
has(landing, "PracticeTabsDemo", "必须使用三种练习演示");
has(landing, "EvidenceLedger", "必须使用证据说明");

const practice = src("components/landing/PracticeTabsDemo.vue");
has(practice, 'role="tablist"', "练习页签必须可访问");
has(practice, "aria-selected", "练习页签必须有选中态");
lacks(landing, "累计", "落地页不得出现虚构累计数据");
lacks(landing, "余万", "落地页不得出现虚构规模数据");

console.log("landing contracts passed");
