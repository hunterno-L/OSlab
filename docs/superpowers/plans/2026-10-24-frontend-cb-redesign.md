# OSLab 全站 C+B 视觉改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本次按用户资源偏好采用 inline 顺序执行，不并行派发重型子代理。

**Goal:** 把学生端学习工作区从现有的中性白底界面升级为 C+B「学习图册」：冷纸底、索引卡、荧光笔进度、编号批注，同时保持现有路由、数据、权限与行为不变。

**Architecture:** 先改 `learning-theme.css` 的 token 与 Arco 桥接，再改壳层和共享组件，最后逐页替换 scoped CSS。图谱是唯一需要同时改 CSS token 与 ECharts 实色的模块；`GRAPH_TOKENS` 必须逐项镜像 CSS，并由现有契约测试守护。页面只改视觉层，不新增 API 调用、不改 store、不改路由。

**Tech Stack:** Vue 3.2 + TypeScript 4.5 + Arco Design Vue 2.56 + ECharts 5.6 + Vue CLI 5；测试为 Node 脚本（`node tests/*.js`），构建为 `npm run build`。

**Spec:** `docs/design/2026-10-24-cb-learning-atlas.md`

**Visual reference:** `design/directions/oslab-design-directions.html`，切换到 `C+B · 学习图册`。

---

## Global Constraints

- **纯表现层改造**：不修改 API 契约、路由、store、权限、题目/实验/任务逻辑。除图谱 `annotations` 这种纯展示 prop 外，不新增数据请求。
- **不新增依赖**：不引入 Tailwind、UI 框架、外链字体或图标库。使用现有 Arco 组件与系统字体栈。
- **Token 单一来源**：颜色/字体/间距/圆角/阴影只在 `src/styles/learning-theme.css` 定义；页面禁止新增裸色值。图谱例外：`KnowledgeGraph.vue` 的 `GRAPH_TOKENS` 必须与 CSS 同名 token 同值，并由 `tests/knowledge-graph-render.js` 校验。
- **保留契约**：现有 class 名、`aria-*`、`data-*`、测试断言的模板字符串尽量保留；只加不改。图谱测试中的 legend 类名、`legend-caption` 文本、workbench CTA 绑定方式不得改动。
- **荧光笔纪律**：只用于页面标题、当前筛选/选中、学习进度、选中节点标签；不得用于正文、按钮、全部标题。
- **批注纪律**：编号只出现在 `annotations` 映射中的节点；每个详情面板最多一个批注块；没有数据支撑不编造批注。
- **可访问性底线**：正文对比度 ≥ 4.5:1；状态必须有文字；`:focus-visible` 2px；点击目标桌面 ≥ 32px；`prefers-reduced-motion` 下关闭动效。
- **资源纪律**：所有测试、构建、lint 串行执行；Node 显式加 `--max-old-space-size`；不并行启动多个重型代理。
- **提交纪律**：本计划默认不自动 `git commit`；每个任务以测试/构建/浏览器检查为检查点。只有用户明确要求时才提交。
- **验证环境**：前端 dev server 预期运行在 `http://localhost:8081`；若未运行，用 `NODE_OPTIONS=--max-old-space-size=4096 npm run serve` 启动一次，不要重复启动。

## Baseline

改动前已执行：

```bash
cd 前端代码/osLab-frontend
for f in tests/*.js; do NODE_OPTIONS=--max-old-space-size=2048 node "$f"; done
```

结果：除 `tests/learning-integration-contracts.js` 的 `播放器离页必须停止 timer` 外全部通过。该失败是测试仍断言旧的 `onBeforeUnmount(pause)`，而组件实际为 `onBeforeUnmount(clearTimer)`，不是运行时泄漏。Task 1 先修契约。

---

### Task 1: 修复过期的播放器清理契约

**Files:**
- Modify: `前端代码/osLab-frontend/tests/learning-integration-contracts.js:149`

**Interfaces:**
- Consumes: `src/components/learning/AlgorithmDemoPlayer.vue` 的 `clearTimer` / `window.clearInterval` 实现。
- Produces: 全绿基线；后续任务不再被这个失败干扰。

- [ ] **Step 1: 把断言改成当前真实契约**

```js
assert(
  player.includes("onBeforeUnmount(clearTimer)"),
  "播放器离页必须调用 clearTimer 停止 timer"
);
assert(
  /const clearTimer\s*=\s*\(\)\s*=>\s*\{[\s\S]*?window\.clearInterval\(timer\)/.test(
    player
  ),
  "clearTimer 必须清理 setInterval 句柄"
);
assert(player.includes("window.setInterval"), "播放器必须有自动播放");
```

- [ ] **Step 2: 运行契约测试**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/learning-integration-contracts.js
```

Expected: `Learning integration contracts passed...`

- [ ] **Step 3: 记录检查点**

```bash
git diff --stat -- 前端代码/osLab-frontend/tests/learning-integration-contracts.js
```

不自动提交。

---

### Task 2: 设计令牌、Arco 色阶与图谱 token 同步

**Files:**
- Modify: `前端代码/osLab-frontend/src/styles/learning-theme.css`（`:root` 与 `body` Arco 桥接段）
- Modify: `前端代码/osLab-frontend/src/components/KnowledgeGraph.vue:93-132`
- Modify: `前端代码/osLab-frontend/tests/knowledge-graph-render.js`（旧硬编码颜色与圆角断言）
- Create: `前端代码/osLab-frontend/tests/design-system-contracts.js`

**Interfaces:**
- Consumes: Spec §2.1–2.6、§3.1–3.2 的精确 token 值。
- Produces: `--oslab-canvas/highlight/annotation/...`、`--arcoblue-6: 36, 71, 216`、`GRAPH_TOKENS` 新值；供 Task 3–11 的 CSS 使用。

- [ ] **Step 1: 先写失败的 token 契约测试**

创建 `前端代码/osLab-frontend/tests/design-system-contracts.js`：

```js
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const css = fs.readFileSync(
  path.join(root, "src/styles/learning-theme.css"),
  "utf8"
);

const tokens = new Map();
for (const match of css.matchAll(/--([a-z0-9-]+)\s*:\s*([^;]+);/gi)) {
  if (!tokens.has(match[1])) tokens.set(match[1], match[2].trim().toLowerCase());
}

const expectToken = (name, value) => {
  assert.ok(tokens.has(name), `learning-theme.css 必须定义 --${name}`);
  assert.equal(tokens.get(name), value, `--${name} 值不符合 C+B 规范`);
};

expectToken("oslab-canvas", "#eef0e9");
expectToken("oslab-panel", "#ffffff");
expectToken("oslab-panel-soft", "#f7f8f2");
expectToken("oslab-ink", "#20242b");
expectToken("oslab-ink-soft", "#3a4150");
expectToken("oslab-muted-strong", "#6b7168");
expectToken("oslab-line", "#dddfd3");
expectToken("oslab-line-strong", "#c9ccbe");
expectToken("oslab-primary", "#2447d8");
expectToken("oslab-primary-hover", "#1a35a8");
expectToken("oslab-primary-soft", "#e9edff");
expectToken("oslab-highlight", "#f2d64b");
expectToken("oslab-highlight-soft", "#fff7c2");
expectToken("oslab-annotation", "#d7614a");
expectToken("oslab-mastered", "#3f8f6e");
expectToken("oslab-review", "#b8892e");
expectToken("oslab-weak", "#b8543f");
expectToken("oslab-unseen", "#6b7168");
expectToken("oslab-graph-surface", "transparent");
expectToken("oslab-graph-node-border", "#c9ccbe");
expectToken("oslab-graph-node-radius", "8px");
expectToken("oslab-graph-category-concept", "#e8eefc");
expectToken("oslab-graph-category-algorithm", "#f1e9fa");
expectToken("oslab-graph-category-function", "#e4f2ed");
expectToken("oslab-graph-category-default", "#eeefe7");
expectToken("oslab-graph-selection", "#2447d8");
expectToken("oslab-graph-selection-ring", "rgba(36, 71, 216, 0.18)");
expectToken("oslab-graph-path", "#8fa6c4");
expectToken("oslab-graph-path-strong", "#6b809f");
expectToken("oslab-graph-path-edge", "#3f66a5");
expectToken("oslab-graph-path-ring", "rgba(107, 128, 159, 0.28)");
expectToken("oslab-graph-review-ring", "rgba(184, 137, 46, 0.26)");
expectToken("oslab-graph-text-soft", "#6b7168");
expectToken("oslab-graph-label-surface", "#ffffff");
expectToken("oslab-graph-highlight-soft", "#fff7c2");
expectToken("oslab-graph-relation-dependent", "#4f72b8");
expectToken("oslab-graph-relation-contain", "#8b96a6");
expectToken("oslab-graph-relation-related", "#9a948b");
expectToken("oslab-graph-relation-implements", "#b8892e");
expectToken("oslab-graph-relation-fallback", "#b0b6bf");
expectToken("arcoblue-6", "36, 71, 216");
expectToken("red-6", "184, 84, 63");
expectToken("green-6", "63, 143, 110");
expectToken("orange-6", "184, 137, 46");
expectToken(
  "oslab-font-display",
  '"lxgw wenkai", "kaiti sc", "kaiti", "stkaiti", "noto serif sc", serif'
);
expectToken(
  "oslab-shadow-card",
  "0 8px 24px rgba(32, 36, 43, 0.06)"
);

const header = fs.readFileSync(
  path.join(root, "src/components/GlobalHeader.vue"),
  "utf8"
);
const layout = fs.readFileSync(
  path.join(root, "src/layouts/LearningLayout.vue"),
  "utf8"
);
assert.ok(
  header.includes("var(--oslab-primary") || header.includes("var(--oslab-"),
  "GlobalHeader 必须使用主题 token"
);
assert.ok(
  layout.includes("var(--oslab-highlight"),
  "LearningLayout 的页面标题必须使用荧光笔 token"
);

console.log("Design system contracts passed: C+B tokens and Arco bridge.");
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/design-system-contracts.js
```

Expected: FAIL，提示 `learning-theme.css 必须定义 --oslab-canvas` 或值不符。

- [ ] **Step 3: 替换 `learning-theme.css` 的根 token 块**

把 `:root` 中画布/文本/品牌/状态/graph 段替换为 Spec §2.1–2.4 的精确值。关键块如下，其余按 Spec 补全：

```css
:root {
  --oslab-canvas: #eef0e9;
  --oslab-panel: #ffffff;
  --oslab-panel-soft: #f7f8f2;
  --oslab-panel-muted: #f1f3ea;

  --oslab-ink: #20242b;
  --oslab-ink-soft: #3a4150;
  --oslab-muted-strong: #6b7168;
  --oslab-muted: #8b9187;

  --oslab-line: #dddfd3;
  --oslab-line-soft: #eceee6;
  --oslab-line-strong: #c9ccbe;

  --oslab-primary: #2447d8;
  --oslab-primary-hover: #1a35a8;
  --oslab-primary-active: #142a87;
  --oslab-primary-soft: #e9edff;
  --oslab-on-primary: #ffffff;

  --oslab-highlight: #f2d64b;
  --oslab-highlight-soft: #fff7c2;
  --oslab-highlight-strong: #ffe98a;

  --oslab-annotation: #d7614a;
  --oslab-annotation-soft: #f7ddd6;

  --oslab-mastered: #3f8f6e;
  --oslab-mastered-soft: #e6f2ec;
  --oslab-review: #b8892e;
  --oslab-review-soft: #f6edd9;
  --oslab-weak: #b8543f;
  --oslab-weak-soft: #f6e3de;
  --oslab-unseen: #6b7168;
  --oslab-unseen-soft: #eeefe9;
  --oslab-danger: #b8543f;

  --oslab-graph-surface: transparent;
  --oslab-graph-node-border: #c9ccbe;
  --oslab-graph-node-radius: 8px;
  --oslab-graph-category-concept: #e8eefc;
  --oslab-graph-category-algorithm: #f1e9fa;
  --oslab-graph-category-function: #e4f2ed;
  --oslab-graph-category-default: #eeefe7;
  --oslab-graph-selection: #2447d8;
  --oslab-graph-selection-ring: rgba(36, 71, 216, 0.18);
  --oslab-graph-path: #8fa6c4;
  --oslab-graph-path-strong: #6b809f;
  --oslab-graph-path-edge: #3f66a5;
  --oslab-graph-path-ring: rgba(107, 128, 159, 0.28);
  --oslab-graph-review-ring: rgba(184, 137, 46, 0.26);
  --oslab-graph-text-soft: #6b7168;
  --oslab-graph-label-surface: #ffffff;
  --oslab-graph-highlight-soft: #fff7c2;
  --oslab-graph-relation-dependent: #4f72b8;
  --oslab-graph-relation-contain: #8b96a6;
  --oslab-graph-relation-related: #9a948b;
  --oslab-graph-relation-implements: #b8892e;
  --oslab-graph-relation-fallback: #b0b6bf;

  --oslab-font-ui: -apple-system, BlinkMacSystemFont, "Segoe UI",
    "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  --oslab-font-display: "LXGW WenKai", "Kaiti SC", "KaiTi", "STKaiti",
    "Noto Serif SC", serif;
  --oslab-font-mono: "Cascadia Code", "JetBrains Mono", ui-monospace,
    SFMono-Regular, Menlo, Consolas, monospace;

  --oslab-text-display: 34px;
  --oslab-text-page: 34px;
  --oslab-text-section: 18px;
  --oslab-text-small: 12px;
  --oslab-font-body: 14px;
  --oslab-leading-body: 1.6;

  --oslab-space-1: 4px;
  --oslab-space-2: 8px;
  --oslab-space-3: 12px;
  --oslab-space-4: 16px;
  --oslab-space-5: 20px;
  --oslab-space-6: 24px;
  --oslab-space-8: 32px;
  --oslab-space-10: 40px;
  --oslab-space-12: 48px;

  --oslab-radius-sm: 6px;
  --oslab-radius-md: 9px;
  --oslab-radius-lg: 12px;
  --oslab-radius-container: 14px;
  --oslab-radius-control: 9px;
  --oslab-radius-pill: 999px;

  --oslab-shadow-card: 0 8px 24px rgba(32, 36, 43, 0.06);
  --oslab-shadow-overlay: 0 16px 40px rgba(32, 36, 43, 0.14);

  --oslab-ease: cubic-bezier(0.2, 0.7, 0.2, 1);
  --oslab-duration-fast: 160ms;
  --oslab-duration: 220ms;
}
```

- [ ] **Step 4: 更新 Arco 桥接**

在 `body` 的 Arco 段替换 `--arcoblue-*`、`--red-*`、`--green-*`、`--orange-*` 三元组，值为 Spec §3.1。至少保证：

```css
body {
  --arcoblue-5: 26, 53, 168;
  --arcoblue-6: 36, 71, 216;
  --arcoblue-7: 20, 42, 135;
  --red-6: 184, 84, 63;
  --green-6: 63, 143, 110;
  --orange-6: 184, 137, 46;
}
```

- [ ] **Step 5: 同步 `KnowledgeGraph.vue` 的 `GRAPH_TOKENS`**

把第 93–132 行整块替换为：

```js
const GRAPH_TOKENS = Object.freeze({
  surface: "transparent",
  nodeBorder: "#c9ccbe",
  nodeRadius: 8,
  category: Object.freeze({
    concept: "#e8eefc",
    algorithm: "#f1e9fa",
    function: "#e4f2ed",
    fallback: "#eeefe7",
  }),
  selection: "#2447d8",
  selectionRing: "rgba(36, 71, 216, 0.18)",
  path: "#8fa6c4",
  pathStrong: "#6b809f",
  pathEdge: "#3f66a5",
  pathRing: "rgba(107, 128, 159, 0.28)",
  reviewRing: "rgba(184, 137, 46, 0.26)",
  inkSoft: "#3a4150",
  textSoft: "#6b7168",
  labelSurface: "#ffffff",
  highlightSoft: "#fff7c2",
  relation: Object.freeze({
    dependent: "#4f72b8",
    contain: "#8b96a6",
    related: "#9a948b",
    implements: "#b8892e",
    fallback: "#b0b6bf",
  }),
  status: Object.freeze({
    GREEN: "#3f8f6e",
    YELLOW: "#b8892e",
    RED: "#b8543f",
    GRAY: "#6b7168",
  }),
  statusFill: Object.freeze({
    GREEN: "#e6f2ec",
    YELLOW: "#f6edd9",
    RED: "#f6e3de",
    GRAY: "#eeefe9",
  }),
});
```

- [ ] **Step 6: 更新图谱测试的旧硬编码值**

在 `tests/knowledge-graph-render.js` 中做以下精确替换：

| 旧值 | 新值 | 出现位置 |
| --- | --- | --- |
| `#2059c9` | `#2447d8` | 全部 |
| `rgba(29, 33, 41, 0.24)` | `#c9ccbe` | 全部 |
| `#37352f` | `#3a4150` | 全部 |
| `#a9695d` | `#b8543f` | 全部 |
| `#757069` | `#6b7168` | 全部 |
| `assert.equal(node.itemStyle.borderRadius, 2)` | `assert.equal(node.itemStyle.borderRadius, 8)` | `nodes use compact stable tiers...` |

并在 `graph palette mirrors learning-theme tokens` 中追加：

```js
assertToken("oslab-graph-highlight-soft", GRAPH_TOKENS.highlightSoft);
```

- [ ] **Step 7: 运行两个契约测试**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/design-system-contracts.js
NODE_OPTIONS=--max-old-space-size=2048 node tests/knowledge-graph-render.js
```

Expected: 两个都通过。若 `knowledge-graph-render` 因 `transparent` 背景或字符串格式失败，按实际输出修正测试期望，不要回退 token。

- [ ] **Step 8: 跑全量 node 测试**

```bash
for f in tests/*.js; do echo "=== $f"; NODE_OPTIONS=--max-old-space-size=2048 node "$f" || break; done
```

Expected: 全部通过（Task 1 已修复播放器契约）。

---

### Task 3: Arco 组件皮肤、共享状态与批注组件

**Files:**
- Modify: `前端代码/osLab-frontend/src/styles/learning-theme.css`（第 3/5 层）
- Create: `前端代码/osLab-frontend/src/components/learning/AnnotationNote.vue`
- Modify: `前端代码/osLab-frontend/tests/design-system-contracts.js`（追加组件契约）

**Interfaces:**
- Consumes: Task 2 的 token。
- Produces: 统一的按钮/输入/表格/标签/卡片/进度/空态样式；`AnnotationNote` 组件（`index`, `title` props）；后续页面直接使用。

- [ ] **Step 1: 在 `learning-theme.css` 追加 Arco 组件覆盖**

```css
body {
  --color-bg-1: #ffffff;
  --color-bg-2: #f7f8f2;
  --color-bg-3: #eef0e9;
  --color-bg-4: #e6e8dd;
  --color-bg-5: #dddfd3;
  --color-text-1: #20242b;
  --color-text-2: #3a4150;
  --color-text-3: #6b7168;
  --color-text-4: #8b9187;
  --color-border-1: #eceee6;
  --color-border-2: #dddfd3;
  --color-border-3: #c9ccbe;
  --color-fill-1: #f7f8f2;
  --color-fill-2: #eef0e9;
  --color-fill-3: #e6e8dd;
  --border-radius-small: 9px;
  --border-radius-medium: 12px;
  --border-radius-large: 14px;
}

body .arco-btn {
  border-radius: var(--oslab-radius-control);
  font-weight: 600;
  transition:
    background-color var(--oslab-duration-fast) var(--oslab-ease),
    border-color var(--oslab-duration-fast) var(--oslab-ease);
}
body .arco-btn-primary {
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
}
body .arco-btn-primary:hover {
  background: var(--oslab-primary-hover);
}
body .arco-btn-secondary,
body .arco-btn-outline {
  background: var(--oslab-panel);
  border-color: var(--oslab-line-strong);
  color: var(--oslab-ink-soft);
}
body .arco-input-wrapper,
body .arco-select-view,
body .arco-textarea-wrapper {
  background: var(--oslab-panel-soft);
  border-color: var(--oslab-line-soft);
  border-radius: var(--oslab-radius-control);
}
body .arco-input-wrapper:focus-within,
body .arco-select-view:focus-within {
  border-color: var(--oslab-primary);
  box-shadow: 0 0 0 2px var(--oslab-primary-soft);
}
body .arco-table-th {
  background: var(--oslab-panel-soft);
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 10.5px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
body .arco-table-td {
  border-color: var(--oslab-line-soft);
  color: var(--oslab-ink-soft);
}
body .arco-table-tr:hover .arco-table-td {
  background: var(--oslab-panel-soft);
}
body .arco-tag {
  border: 0;
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-muted);
  color: var(--oslab-ink-soft);
}
body .arco-card {
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
body .arco-pagination-item {
  border-radius: var(--oslab-radius-sm);
}
```

- [ ] **Step 2: 重定义共享面板与状态类**

```css
.section-card,
.graph-card,
.detail-card,
.node-header {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}

.oslab-highlight-text {
  display: inline;
  padding: 0 3px;
  background: linear-gradient(
    transparent 56%,
    var(--oslab-highlight) 56%,
    var(--oslab-highlight) 92%,
    transparent 92%
  );
}

.oslab-highlight-block {
  background: var(--oslab-highlight-soft);
  border-radius: var(--oslab-radius-sm);
}

.oslab-empty,
.oslab-loading,
.oslab-error {
  border: 1px dashed var(--oslab-line-strong);
  border-radius: var(--oslab-radius-container);
  background: var(--oslab-panel-soft);
  padding: var(--oslab-space-10) var(--oslab-space-6);
}
.oslab-state-title {
  font-family: var(--oslab-font-display);
  font-size: 18px;
  color: var(--oslab-ink);
}
.oslab-state-hint {
  color: var(--oslab-muted-strong);
  max-width: 520px;
}
.oslab-status-tag {
  border-radius: var(--oslab-radius-pill);
  padding: 2px 9px;
  font-size: 11px;
  line-height: 18px;
  font-weight: 500;
}
.oslab-page-title {
  font-family: var(--oslab-font-display);
  font-size: var(--oslab-text-page);
  font-weight: 700;
}
.oslab-section-title {
  font-size: var(--oslab-text-section);
  font-weight: 600;
}
.oslab-annotation-badge {
  display: inline-grid;
  place-items: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
  line-height: 1;
}
```

- [ ] **Step 3: 创建 `AnnotationNote.vue`**

```vue
<template>
  <aside class="annotation-note" :aria-label="title || `批注 ${index}`">
    <span class="annotation-note__index" aria-hidden="true">{{ index }}</span>
    <div class="annotation-note__body">
      <p v-if="title" class="annotation-note__title">{{ title }}</p>
      <p class="annotation-note__text"><slot /></p>
    </div>
  </aside>
</template>

<script setup lang="ts">
defineProps<{
  index: number | string;
  title?: string;
}>();
</script>

<style scoped>
.annotation-note {
  display: flex;
  gap: 9px;
  align-items: flex-start;
  margin: 10px 0;
  padding: 9px 11px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
}
.annotation-note__index {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--oslab-annotation);
  color: #fff;
  font-family: var(--oslab-font-mono);
  font-size: 9px;
}
.annotation-note__title {
  margin: 0 0 2px;
  color: var(--oslab-ink);
  font-size: 11.5px;
  font-weight: 600;
}
.annotation-note__text {
  margin: 0;
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
  font-size: 12px;
  line-height: 1.55;
}
</style>
```

- [ ] **Step 4: 追加静态契约**

在 `tests/design-system-contracts.js` 末尾追加：

```js
const annotation = fs.readFileSync(
  path.join(root, "src/components/learning/AnnotationNote.vue"),
  "utf8"
);
assert.ok(
  annotation.includes("var(--oslab-annotation)"),
  "AnnotationNote 必须使用批注 token"
);
assert.ok(
  css.includes(".oslab-highlight-text"),
  "必须提供荧光笔工具类"
);
assert.ok(
  css.includes(".arco-table-th"),
  "必须覆盖 Arco 表头"
);
```

- [ ] **Step 5: 运行契约测试**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/design-system-contracts.js
```

Expected: PASS。

---

### Task 4: 全局壳层（浮层头部、点阵画布、荧光笔页面标题）

**Files:**
- Modify: `前端代码/osLab-frontend/src/layouts/LearningLayout.vue`
- Modify: `前端代码/osLab-frontend/src/layouts/TeachingLayout.vue`
- Modify: `前端代码/osLab-frontend/src/components/GlobalHeader.vue`
- Modify: `前端代码/osLab-frontend/src/components/assistant/FloatingAiAssistant.vue`（仅颜色，若存在裸色）

**Interfaces:**
- Consumes: Task 2/3 的 token 与 Arco 皮肤。
- Produces: 全站统一壳层；所有页面不再各自处理 header 背景与页面标题样式。

- [ ] **Step 1: `LearningLayout.vue` 加眉题并重写壳层 CSS**

模板中：

```vue
<div v-if="pageTitle" class="page-title-row" :class="{ 'content-wide': isWide }">
  <p class="page-eyebrow" aria-hidden="true">OSLab / LEARNING</p>
  <h1 class="page-title">{{ pageTitle }}</h1>
</div>
```

样式替换：

```css
.themed-layout {
  min-height: 100vh;
  background:
    radial-gradient(rgba(32, 36, 43, 0.075) 1px, transparent 1px) 0 0 / 22px 22px,
    var(--oslab-canvas);
}
.themed-layout .header {
  height: auto;
  line-height: normal;
  padding: 12px 12px 0;
  background: transparent;
  box-shadow: none;
}
.themed-layout .content {
  background: transparent;
  padding: 20px 32px 32px;
  min-height: calc(100vh - 84px);
}
.page-title-row {
  margin: 0 auto 16px;
}
.page-eyebrow {
  margin: 0 0 2px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
.page-title-row .page-title {
  display: inline;
  padding: 0 4px;
  font-family: var(--oslab-font-display);
  font-size: var(--oslab-text-page);
  font-weight: 700;
  line-height: 1.2;
  background: linear-gradient(
    transparent 56%,
    var(--oslab-highlight) 56%,
    var(--oslab-highlight) 92%,
    transparent 92%
  );
}
```

移动端保持现有 1024 断点，标题改 28px、内容 padding 改 16px 12px 32px。

- [ ] **Step 2: `TeachingLayout.vue` 对齐同一壳层**

把 `.themed-layout .header` 改为与 LearningLayout 相同的透明/12px 外距；`.content` 改为透明 + 点阵底 + 20px 32px 32px。保留教师页现有宽度逻辑。

- [ ] **Step 3: `GlobalHeader.vue` 改成浮层卡片与胶囊导航**

```css
#globalHeader {
  min-height: 60px;
  padding: 0 16px;
  gap: 18px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  background: var(--oslab-panel);
  box-shadow: var(--oslab-shadow-card);
}
.main-navigation {
  gap: 4px;
}
.main-nav-link {
  min-height: 36px;
  padding: 0 13px;
  border: 0;
  border-radius: var(--oslab-radius-pill);
  color: var(--oslab-ink-soft);
  font-size: 13px;
  font-weight: 500;
}
.main-nav-link:hover,
.main-nav-link:focus-visible {
  background: var(--oslab-panel-soft);
  color: var(--oslab-ink);
}
.main-nav-link.active {
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
  font-weight: 600;
}
.title {
  font-family: var(--oslab-font-display);
  font-size: 20px;
}
```

在 900px 断点保留两行布局，导航胶囊高度 34px；480px 断点隐藏副标题。

- [ ] **Step 4: AI 浮球颜色**

在 `FloatingAiAssistant.vue` 中把主色/背景裸色换成 `var(--oslab-primary)` 与 `var(--oslab-on-primary)`；若为独立 CSS 文件，同样处理。不要改交互逻辑。

- [ ] **Step 5: 追加壳层契约**

在 `tests/design-system-contracts.js` 末尾追加：

```js
assert.ok(
  layout.includes("page-eyebrow"),
  "LearningLayout 必须渲染等宽眉题"
);
assert.ok(
  layout.includes("var(--oslab-highlight)"),
  "页面标题必须使用荧光笔 token"
);
assert.ok(
  header.includes("var(--oslab-radius-pill)"),
  "顶部导航必须使用胶囊圆角"
);
```

- [ ] **Step 6: 运行测试与构建检查**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/design-system-contracts.js
NODE_OPTIONS=--max-old-space-size=4096 npm run build
```

Expected: 契约 PASS；build 成功。若 dev server 在 8081，打开 `/home`、`/graph` 检查头部浮层、点阵底、标题荧光笔；1024 下检查不重叠。

---

### Task 5: 图谱组件——索引卡节点与编号批注

**Files:**
- Modify: `前端代码/osLab-frontend/src/components/KnowledgeGraph.vue:43-62, 344-379, 381-460`
- Modify: `前端代码/osLab-frontend/tests/knowledge-graph-render.js`

**Interfaces:**
- Consumes: Task 2 的 `GRAPH_TOKENS`；Task 3 的批注 token。
- Produces: `KnowledgeGraph` 新 prop `annotations: Record<string, number>`；节点标签形如 `{num|3}{dot|●}{name|…}`；选中标签底色荧光笔。

- [ ] **Step 1: 增加 prop**

在 `showRelationLabels` 之后加入：

```js
  // 编号批注：key = 节点稳定 key，value = 1..9 的显示编号。
  // 只给被批注引用的节点渲染编号，不给全图编号。
  annotations: { type: Object, default: () => ({}) },
```

- [ ] **Step 2: 在 `buildLabel` 中渲染编号与选中荧光底**

把 `buildLabel` 改为接收 `annotationNumber` 并计算可用宽度：

```js
const buildLabel = (ctx) => {
  const dotWidth = ctx.showStatusUI ? STATUS_DOT_SPACE : 0;
  const numWidth = ctx.annotationNumber ? 22 : 0;
  const nameWidth = ctx.width - 16 - 6 - dotWidth - numWidth;
  const displayName = truncateName(ctx.displayName, nameWidth);
  const rich = {
    num: {
      color: "#ffffff",
      backgroundColor: PRIMARY,
      fontSize: 9,
      lineHeight: 16,
      width: 16,
      height: 16,
      borderRadius: 8,
      align: "center",
      verticalAlign: "middle",
      padding: [2, 0, 0, 0],
    },
    dot: {
      color: ctx.statusColor,
      fontSize: 9,
      lineHeight: 15,
      width: STATUS_DOT_SPACE,
      align: "left",
    },
    name: {
      color: ctx.dimmed ? MUTED_STRONG : INK_SOFT,
      fontSize: 12.5,
      lineHeight: 15,
      align: "left",
      fontWeight: ctx.isSelected ? 600 : 500,
      backgroundColor: ctx.isSelected
        ? GRAPH_TOKENS.highlightSoft
        : "transparent",
      padding: ctx.isSelected ? [1, 3] : 0,
    },
  };
  const numPrefix = ctx.annotationNumber
    ? `{num|${ctx.annotationNumber}} `
    : "";
  const dot = ctx.showStatusUI ? "{dot|●}" : "";
  const text = `${numPrefix}${dot}{name|${displayName}}`;
  return {
    show: ctx.labelsVisible,
    position: "inside",
    align: "center",
    verticalAlign: "middle",
    width: ctx.width - 16,
    padding: [NODE_PADDING_Y, 3],
    formatter: text,
    rich,
  };
};
```

- [ ] **Step 3: 在 `buildNodeStyle` 传入编号并只给选中节点加阴影**

```js
    label: buildLabel({
      isSelected,
      dimmed,
      statusColor,
      showStatusUI: ctx.showStatusUI,
      labelsVisible: ctx.labelsVisible,
      displayName: node.displayName || node.name,
      width: size.width,
      annotationNumber: Number(props.annotations?.[key]) || 0,
    }),
```

`itemStyle.shadowBlur` 保持 `isSelected ? 8 : 0`；`shadowColor` 选中为 `selectionRing`，其余 `"transparent"`。不要给 150 个节点加阴影。

- [ ] **Step 4: 增加编号批注测试**

在 `tests/knowledge-graph-render.js` 的 `nodes use compact stable tiers...` 之后加入：

```js
test("annotation numbers render only on referenced nodes", () => {
  const input = makeGraph(3);
  input.annotations = { "0": 3, "1": 1 };
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    const data = harness.graph.getOption().series[0].data;
    assert.ok(String(data[0].label.formatter).includes("{num|3}"));
    assert.ok(String(data[1].label.formatter).includes("{num|1}"));
    assert.ok(!String(data[2].label.formatter).includes("{num|"));
  } finally {
    harness.graph.dispose();
  }
});
```

- [ ] **Step 5: 运行图谱测试**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/knowledge-graph-render.js
```

Expected: 全部 PASS。若 `assertLabelsAttached` 因编号/荧光底溢出失败，优先减小编号宽度或把名称可用宽度再减 4px，不要放宽标签内嵌断言。

---

### Task 6: 课程学习页（`/graph`）C+B 重构

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/knowledgeGraph/KnowledgeGraphView.vue`
- Modify: `前端代码/osLab-frontend/src/components/learning/CourseCatalogSidebar.vue`
- Modify: `前端代码/osLab-frontend/src/components/learning/CourseSidebar.vue`
- Test: `前端代码/osLab-frontend/tests/knowledge-graph-render.js`（模板契约）

**Interfaces:**
- Consumes: Task 5 的 `annotations` prop；Task 3 的 `AnnotationNote`。
- Produces: 课程学习页的 C+B 视觉；编号与详情批注联动。

- [ ] **Step 1: 计算批注映射**

在 `KnowledgeGraphView.vue` 的 `relationGroups` 之后加入：

```ts
const graphAnnotations = computed<Record<string, number>>(() => {
  const result: Record<string, number> = {};
  if (!selectedNodeKey.value) return result;
  result[selectedNodeKey.value] = 3;
  const seen = new Set<string>([selectedNodeKey.value]);
  const neighbors = [
    ...relationGroups.value.prerequisites,
    ...relationGroups.value.successors,
    ...relationGroups.value.related,
  ]
    .filter((item) => {
      if (seen.has(item.nodeKey)) return false;
      seen.add(item.nodeKey);
      return true;
    })
    .slice(0, 2);
  neighbors.forEach((item, index) => {
    result[item.nodeKey] = index + 1;
  });
  return result;
});

const selectedAnnotationText = computed(() => {
  if (!selectedNode.value) return "";
  const type = nodeTypeLabel(selectedNode.value);
  return `③ 当前节点；①② 为最近一跳关系。${type}节点 · ${selectedCrumb.value}。先读定义，再进入节点工作台。`;
});
```

- [ ] **Step 2: 传 prop 并插入批注**

模板中：

```vue
<KnowledgeGraph
  ...
  :annotations="graphAnnotations"
  ...
/>
```

在节点详情的 `selectedNode.description` 之后插入：

```vue
<AnnotationNote :index="3" title="当前节点">
  {{ selectedAnnotationText }}
</AnnotationNote>
```

并 import：

```ts
import AnnotationNote from "@/components/learning/AnnotationNote.vue";
```

- [ ] **Step 3: 替换视图内裸主色**

把 `KnowledgeGraphView.vue` 中所有 `stroke-color="#2059c9"` 替换为 `stroke-color="var(--oslab-primary)"`（3 处：章节进度、单元进度、掌握度）。不要改逻辑。

- [ ] **Step 4: 页面 CSS 覆盖**

在 scoped style 末尾追加（保留既有布局，只覆盖视觉）：

```css
.graph-page {
  gap: 16px;
  align-items: flex-start;
}
.course-side {
  width: 264px;
  padding: 14px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.graph-main {
  padding: 0;
}
.workspace-header {
  align-items: center;
  margin-bottom: 14px;
}
.page-title {
  font-family: var(--oslab-font-display);
  font-size: 30px;
}
.page-subtitle {
  color: var(--oslab-muted-strong);
}
.content-row {
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
}
.graph-card {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.canvas-wrap {
  border-radius: var(--oslab-radius-lg);
  background:
    radial-gradient(rgba(32, 36, 43, 0.075) 1px, transparent 1px) 0 0 / 22px 22px,
    var(--oslab-panel-soft);
  overflow: hidden;
}
.detail-card {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.node-name {
  font-family: var(--oslab-font-display);
  font-size: 24px;
  line-height: 1.3;
}
.section-title {
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--oslab-muted-strong);
}
.cta-main {
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-primary);
}
```

- [ ] **Step 5: 侧栏组件视觉**

在 `CourseCatalogSidebar.vue` / `CourseSidebar.vue` 的 scoped CSS 末尾把容器、当前项、数字重定义为：

```css
/* 两个组件按各自类名落地，语义相同 */
.sidebar-shell {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.catalog-item.active,
.side-nav-item.active {
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink);
  font-weight: 600;
}
.catalog-count,
.side-nav-count {
  font-family: var(--oslab-font-mono);
  color: var(--oslab-muted-strong);
}
```

执行时按文件现有根类名替换（`CourseCatalogSidebar` 用实际的 catalogue 类，`CourseSidebar` 用实际 nav 类），不新增无对应元素的类名。

- [ ] **Step 6: 浏览器验证**

打开 `http://localhost:8081/graph`：

1. 1440 宽：左侧 264、图谱自适应、右侧 340；无横向滚动。
2. 选中一个节点：右侧出现 1 个批注块；图谱仅该节点 + 最多两个邻居出现编号。
3. 1024 / 768：侧栏不遮挡主区；图谱最小高 ≥ 420；表格/工具栏可换行。
4. 键盘 Tab 能聚焦工具栏、图谱节点、详情按钮。

- [ ] **Step 7: 运行图谱测试**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/knowledge-graph-render.js
```

Expected: 全部 PASS（legend 类名、workbench 绑定未变）。

---

### Task 7: 实验中心与算法模拟

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/question/QuestionsView.vue`（style 390-518）
- Modify: `前端代码/osLab-frontend/src/views/learning/SimulationView.vue`（style 326-556）
- Modify: `前端代码/osLab-frontend/src/components/learning/AlgorithmDemoPlayer.vue`（style 1971-3151）

**Interfaces:**
- Consumes: Task 3 的表格/标签/卡片皮肤、`AnnotationNote`。
- Produces: 实验中心表格与模拟工作区的 C+B 视觉；模拟规则区使用批注样式。

- [ ] **Step 1: 实验中心**

在 `QuestionsView.vue` scoped style 末尾覆盖：

```css
.experiment-grid {
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
}
.chapter-nav {
  padding: 12px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.chapter-item {
  min-height: 36px;
  padding: 0 10px;
  border-radius: var(--oslab-radius-control);
  color: var(--oslab-ink-soft);
}
.chapter-item.active {
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink);
  font-weight: 600;
}
.experiment-section {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.section-heading h2 {
  font-family: var(--oslab-font-display);
  font-size: 24px;
}
.table-footer {
  border-top: 1px solid var(--oslab-line-soft);
}
```

表格本身由 Task 3 的 Arco 皮肤接管；只保留 `acceptedRate` 文本，不新增颜色逻辑。

- [ ] **Step 2: 模拟页壳层**

在 `SimulationView.vue` 覆盖：

```css
.simulation-body {
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
}
.algorithm-rail {
  padding: 14px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.algorithm-item {
  min-height: 44px;
  border-radius: var(--oslab-radius-control);
}
.algorithm-item.active {
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink);
}
.algorithm-workspace {
  min-width: 0;
}
.simulation-head h1 {
  display: inline;
  padding: 0 4px;
  background: linear-gradient(
    transparent 56%,
    var(--oslab-highlight) 56%,
    var(--oslab-highlight) 92%,
    transparent 92%
  );
}
```

- [ ] **Step 3: 播放器视觉**

在 `AlgorithmDemoPlayer.vue` scoped style 末尾覆盖关键块：

```css
.demo-player {
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  background: var(--oslab-panel);
  box-shadow: var(--oslab-shadow-card);
}
.demo-head {
  padding: 16px;
  border-bottom: 1px solid var(--oslab-line-soft);
}
.demo-title {
  font-family: var(--oslab-font-display);
  font-size: 24px;
}
.demo-input-card,
.analysis-panel,
.code-card {
  background: var(--oslab-panel-soft);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
}
.code-card {
  background: #20242b;
  color: #e8e6e1;
}
.demo-note {
  border-left: 2px solid var(--oslab-annotation);
  background: var(--oslab-annotation-soft);
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
  font-size: 12px;
  line-height: 1.55;
  border-radius: 0 var(--oslab-radius-sm) var(--oslab-radius-sm) 0;
}
.chip-row .arco-tag,
.demo-badges .arco-tag {
  border-radius: var(--oslab-radius-pill);
}
```

不改播放器逻辑、步骤模型、代码高亮行；只改容器与文字皮肤。

- [ ] **Step 4: 验证**

打开 `/experiments`、`/experiments/simulation`：

1. 1440：章节栏 220、表格自适应；模拟页目录 240、播放器自适应。
2. 1024 / 768：章节栏可折叠为上方横排或横向滚动；播放器代码区不溢出。
3. 运行一次演示：步骤推进、指标、代码行高亮正常；`demo-note` 显示规则边界。

- [ ] **Step 5: 运行模拟相关测试**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/simulation-algorithms.js
NODE_OPTIONS=--max-old-space-size=2048 node tests/simulation-player.js
NODE_OPTIONS=--max-old-space-size=2048 node tests/learning-integration-contracts.js
```

Expected: 全部通过。

---

### Task 8: 题目练习（`/assessments`）

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/learning/LearningQuestionsView.vue`（style 760-941）
- Modify: `前端代码/osLab-frontend/src/components/learning/QuestionAnswerCard.vue`（style 360-586）
- Modify: `前端代码/osLab-frontend/src/views/learning/AssessmentRunView.vue`（style 314-366）

**Interfaces:**
- Consumes: Task 3 的 chip/card/选项皮肤；Task 6 的侧栏。
- Produces: 题卡流与连续测评的 C+B 视觉。

- [ ] **Step 1: 页面筛选与题卡流**

在 `LearningQuestionsView.vue` 覆盖：

```css
.question-page {
  gap: 16px;
}
.question-main {
  min-width: 0;
}
.page-head {
  padding: 0 0 12px;
  border-bottom: 1px solid var(--oslab-line);
}
.filter-panel {
  padding: 12px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.filter-chip {
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-soft);
  color: var(--oslab-ink-soft);
}
.filter-chip.active {
  background: var(--oslab-highlight-soft);
  border-color: var(--oslab-highlight);
  color: var(--oslab-ink);
  font-weight: 600;
}
.chip-review {
  color: var(--oslab-annotation);
}
.question-stream {
  gap: 14px;
}
.stream-item {
  margin: 0;
}
```

- [ ] **Step 2: 题卡与选项**

在 `QuestionAnswerCard.vue` 覆盖：

```css
.qa-card {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.qa-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--oslab-ink);
}
.qa-tag {
  border: 0;
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-muted);
  color: var(--oslab-ink-soft);
}
.qa-option {
  display: flex;
  align-items: center;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
}
.qa-option:hover {
  border-color: var(--oslab-line-strong);
}
.qa-option.is-selected,
.qa-option:has(input:checked) {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
}
.qa-result.is-correct {
  border-left: 2px solid var(--oslab-mastered);
  background: var(--oslab-mastered-soft);
}
.qa-result.is-wrong {
  border-left: 2px solid var(--oslab-weak);
  background: var(--oslab-weak-soft);
}
.qa-feedback {
  border-radius: var(--oslab-radius-md);
}
.qa-feedback-title {
  font-weight: 600;
}
.qa-submit-row .arco-btn-primary {
  border-radius: var(--oslab-radius-pill);
}
```

如果现有选项没有 `is-selected` 类，用实际类名替换，不要改答题逻辑。

- [ ] **Step 3: 连续测评页**

在 `AssessmentRunView.vue` 覆盖容器卡、题干、选项、结果区，沿用上面的 `.qa-*` 视觉；保持键盘与选项逻辑不变。

- [ ] **Step 4: 验证**

打开 `/assessments`：

1. 选择题卡、未作答/已答对/已答错三种状态。
2. 切换节点 chip 与作答状态 chip，确认荧光笔只落在当前 chip。
3. 1024 / 768：侧栏与题卡不重叠；选项文字不溢出。

- [ ] **Step 5: 运行题目流契约**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/learning-integration-contracts.js
```

Expected: PASS。

---

### Task 9: 学习任务与学习记录

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/tasks/MyTasksView.vue`（style 284-373）
- Modify: `前端代码/osLab-frontend/src/views/learning/LearningRecordsView.vue`（style 1657-2199）
- Modify: `前端代码/osLab-frontend/src/views/tasks/TaskDetailView.vue`（style 1059-1291）

**Interfaces:**
- Consumes: Task 3 的表格/卡片/状态皮肤、`AnnotationNote`。
- Produces: 任务列表、记录时间线/待巩固、任务详情的 C+B 视觉。

- [ ] **Step 1: 学习任务**

在 `MyTasksView.vue` 覆盖：

```css
.task-summary {
  gap: 12px;
}
.summary-card {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
  text-align: left;
}
.summary-card.active {
  background: var(--oslab-highlight-soft);
  border-color: var(--oslab-highlight);
}
.summary-value {
  font-family: var(--oslab-font-mono);
  font-size: 30px;
  color: var(--oslab-ink);
}
.summary-label {
  color: var(--oslab-muted-strong);
}
.task-list-card {
  border-radius: var(--oslab-radius-container);
}
.task-title {
  color: var(--oslab-ink);
  font-weight: 600;
}
.task-desc {
  color: var(--oslab-muted-strong);
}
```

- [ ] **Step 2: 学习记录**

在 `LearningRecordsView.vue` 覆盖记录卡与时间线：

```css
.learning-records-view {
  gap: 16px;
}
.records-heading h1 {
  font-family: var(--oslab-font-display);
}
.filter-bar {
  padding: 12px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.record-timeline {
  border-left-color: var(--oslab-line);
}
.event-content {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.event-title {
  color: var(--oslab-ink);
  font-weight: 600;
}
.event-evidence,
.failure-line {
  font-family: var(--oslab-font-display);
  color: var(--oslab-ink-soft);
}
.status-pill {
  border-radius: var(--oslab-radius-pill);
}
.pager-row {
  border-top: 1px solid var(--oslab-line-soft);
}
```

若记录卡已有固定背景/边框，按实际类名替换；不要改筛选、分页、复习跳转逻辑。

- [ ] **Step 3: 任务详情**

在 `TaskDetailView.vue` 覆盖：

```css
.task-detail {
  background: transparent;
}
.task-head,
.field-block,
.question-answer,
.submission-item,
.code-wrap {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
}
.task-title {
  font-family: var(--oslab-font-display);
  font-size: 28px;
}
.action-bar {
  position: sticky;
  bottom: 12px;
  padding: 10px 12px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-overlay);
}
```

提交、批阅、文件逻辑不动。

- [ ] **Step 4: 验证**

打开 `/tasks`、`/records`、任一任务详情：

1. 空态显示新“空白索引卡”样式。
2. 表格/记录卡在 1440 和 1024 下不溢出。
3. 状态标签文字完整，不依赖颜色。
4. 任务详情底部操作栏不遮挡内容。

- [ ] **Step 5: 运行身份与任务相关测试**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/user-identity.js
NODE_OPTIONS=--max-old-space-size=2048 node tests/learning-integration-contracts.js
```

Expected: PASS。

---

### Task 10: 首页、个人信息、学习资源

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/HomeView.vue`（style 139-323）
- Modify: `前端代码/osLab-frontend/src/views/user/StudentProfileView.vue`（style 182-272）
- Modify: `前端代码/osLab-frontend/src/views/ResourcesView.vue`（style 235-373）

**Interfaces:**
- Consumes: Task 3/4 的 token、卡片、空态、壳层。
- Produces: 三个入口页的 C+B 视觉；首页作为“学习台”的第一印象。

- [ ] **Step 1: 首页**

覆盖：

```css
.home-view {
  max-width: 1440px;
}
.home-intro h1 {
  display: inline;
  padding: 0 4px;
  font-family: var(--oslab-font-display);
  font-size: 40px;
  background: linear-gradient(
    transparent 56%,
    var(--oslab-highlight) 56%,
    var(--oslab-highlight) 92%,
    transparent 92%
  );
}
.intro-copy {
  color: var(--oslab-muted-strong);
}
.task-stat {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
  text-align: left;
}
.task-stat strong {
  font-family: var(--oslab-font-mono);
  font-size: 30px;
  color: var(--oslab-ink);
}
.task-stat:hover {
  border-color: var(--oslab-highlight);
  background: var(--oslab-highlight-soft);
}
.entry-item {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
}
.entry-item:hover {
  border-color: var(--oslab-primary);
  box-shadow: var(--oslab-shadow-card);
}
.entry-icon {
  background: var(--oslab-highlight-soft);
  color: var(--oslab-primary);
  border-radius: var(--oslab-radius-md);
}
```

- [ ] **Step 2: 个人信息**

覆盖：

```css
.profile-header h1 {
  display: inline;
  padding: 0 4px;
  font-family: var(--oslab-font-display);
  font-size: 34px;
  background: linear-gradient(
    transparent 56%,
    var(--oslab-highlight) 56%,
    var(--oslab-highlight) 92%,
    transparent 92%
  );
}
.profile-panel {
  padding: 20px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.profile-summary {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--oslab-line-soft);
}
.profile-summary .arco-avatar {
  box-shadow: 0 0 0 3px var(--oslab-highlight-soft);
}
.profile-summary h2 {
  font-family: var(--oslab-font-display);
}
```

`a-descriptions` 由 Task 3 的 Arco 皮肤接管；不要改编辑资料逻辑。

- [ ] **Step 3: 学习资源**

覆盖：

```css
.page-intro h1 {
  display: inline;
  padding: 0 4px;
  font-family: var(--oslab-font-display);
  font-size: 34px;
  background: linear-gradient(
    transparent 56%,
    var(--oslab-highlight) 56%,
    var(--oslab-highlight) 92%,
    transparent 92%
  );
}
.filter-panel {
  padding: 12px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.resource-card {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.resource-kicker {
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--oslab-muted-strong);
}
.resource-card h2 {
  font-family: var(--oslab-font-display);
}
.resource-description {
  color: var(--oslab-ink-soft);
}
```

- [ ] **Step 4: 验证**

打开 `/home`、`/account`、`/resources`：

1. 首页四张任务卡、六个入口卡在 1440/1024/768 下排布正常。
2. 个人信息编辑态展开后不溢出。
3. 资源卡 hover、预览抽屉正常；空态显示新样式。

---

### Task 11: 实验工作区、节点工作台、任务详情等深层页面

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/question/ViewQuestionView.vue`（style 934-1164）
- Modify: `前端代码/osLab-frontend/src/views/learning/NodeLearningWorkspaceView.vue`（style 1568-2401）
- Modify: `前端代码/osLab-frontend/src/views/learning/CourseUnitView.vue`（style 566-730）
- Modify: `前端代码/osLab-frontend/src/views/learning/AssessmentRunView.vue`（已在 Task 8 覆盖，仅补深层状态）

**Interfaces:**
- Consumes: Task 3 的卡片/标签/空态、Task 5 的编号批注、Task 9 的任务详情皮肤。
- Produces: 深层页面与主站一致；代码编辑器保持深色，不强行套纸色。

- [ ] **Step 1: 实验工作区**

在 `ViewQuestionView.vue` 覆盖：

```css
.experiment-workbench {
  gap: 16px;
}
.workbench-header {
  padding: 0 0 12px;
  border-bottom: 1px solid var(--oslab-line);
}
.workbench-title {
  font-family: var(--oslab-font-display);
  font-size: 28px;
}
.workbench-body {
  gap: 16px;
}
.panel {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.panel-editor {
  background: #20242b;
  border-color: #3a4150;
}
.status-card,
.remediation {
  border-radius: var(--oslab-radius-md);
}
.node-tag {
  border-radius: var(--oslab-radius-pill);
}
```

保留 Monaco 深色主题与提交/评测逻辑；只改外层卡片、标题、状态区。

- [ ] **Step 2: 节点工作台**

在 `NodeLearningWorkspaceView.vue` 覆盖：

```css
.node-header,
.evidence-card,
.content-section,
.answer-panel {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
}
.node-title {
  font-family: var(--oslab-font-display);
  font-size: 30px;
}
.node-definition,
.concept-text {
  font-family: var(--oslab-font-display);
  font-size: 14px;
  line-height: 1.7;
}
.text-tab.active {
  color: var(--oslab-primary);
  border-bottom-color: var(--oslab-primary);
}
.node-metrics .metric {
  background: var(--oslab-panel-soft);
  border-radius: var(--oslab-radius-md);
}
.failure-hint {
  border-left: 2px solid var(--oslab-annotation);
  background: var(--oslab-annotation-soft);
  font-family: var(--oslab-font-display);
}
```

不改节点加载、缓存、活动跳转逻辑。

- [ ] **Step 3: 课程单元页**

在 `CourseUnitView.vue` 覆盖容器、标题、活动卡片；沿用 `section-card` / `oslab-section-title`。若已有 `detail-card` 等类，直接由 Task 3 接管，无需重复。

- [ ] **Step 4: 验证深层路由**

1. `/experiments` → 进入实验 2.1 → `/experiments/code/:id`：三栏不溢出，代码区深色，提交按钮钴蓝。
2. `/graph` → 双击节点 → `/knowledge/concept/:nodeCode`：标题、证据卡、失败提示样式正确。
3. `/graph` → 选择单元 → 单元页：卡片与活动列表一致。
4. 1024 / 768：代码区横向滚动、左右栏堆叠；不遮挡提交按钮。

---

### Task 12: 全量验收、构建与交付记录

**Files:**
- Verify: 所有修改文件
- Create: `docs/design/2026-10-24-cb-delivery.md`

**Interfaces:**
- Consumes: Task 1–11。
- Produces: 可交付的全站 C+B 视觉；验收证据与已知限制。

- [ ] **Step 1: 跑全量 node 测试**

```bash
cd 前端代码/osLab-frontend
for f in tests/*.js; do echo "=== $f"; NODE_OPTIONS=--max-old-space-size=2048 node "$f" || break; done
```

Expected: 全部通过。若失败，定位到具体契约；不要通过删除断言来“修复”。

- [ ] **Step 2: 串行构建**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=4096 npm run build
```

Expected: `Build complete`，无 error。记录 warning 数量与类型。

- [ ] **Step 3: Lint**

```bash
cd 前端代码/osLab-frontend
npx vue-cli-service lint --no-fix
```

Expected: 无新增 error。若只存在改动前已有的 warning，记录并在交付文档中说明。

- [ ] **Step 4: 浏览器验收**

在 `http://localhost:8081` 逐页检查：

| 路由 | 1440 | 1024 | 768 |
| --- | --- | --- | --- |
| `/home` | 入口网格 3 列 | 2 列 | 1 列 |
| `/graph` | 目录/图谱/详情三栏 | 详情下沉或收窄 | 单列 + 图谱 420 高 |
| `/assessments` | 目录 + 题卡流 | 目录收窄 | 单列 |
| `/experiments` | 章节 + 表格 | 表格横向滚动 | 单列 |
| `/experiments/simulation` | 目录 + 播放器 | 目录收窄 | 单列 |
| `/tasks` | 4 卡 + 表格 | 2×2 卡 | 单列 |
| `/records` | 侧栏 + 记录卡 | 收窄 | 单列 |
| `/account` | 居中资料卡 | 同左 | 全宽 |
| `/resources` | 筛选 + 卡片网格 | 2 列 | 1 列 |

检查项：
- 无横向溢出、无文字被裁切、无按钮被遮挡。
- 焦点环可见；Tab 顺序合理。
- 状态不只靠颜色。
- 图表/代码区在 reduced motion 下无动画残留。
- 浏览器控制台无新增 error。

- [ ] **Step 5: 写交付记录**

创建 `docs/design/2026-10-24-cb-delivery.md`，至少包含：

```markdown
# OSLab C+B 视觉改造交付记录

- 完成范围：学生端 9 个主路由 + 3 个深层页面
- 设计规范：docs/design/2026-10-24-cb-learning-atlas.md
- 视觉参考：design/directions/oslab-design-directions.html（C+B 模式）
- 测试：node tests/*.js 全绿
- 构建：npm run build 成功
- Lint：无新增 error
- 已知限制：教师端仅继承 token/壳层，未重排页面；图谱编号仅覆盖选中节点与最近两条关系
- 后续建议：教师端专项视觉计划；图谱批注从“最近两条关系”扩展为可配置批注源
```

- [ ] **Step 6: 变更清单**

```bash
git status --short
git diff --stat
```

向用户汇报修改文件、测试结果、构建结果与未提交状态；等待用户决定是否提交。

---

## Self-Review

- **Spec coverage:** 全站 token/Arco/壳层/组件/页面/响应式/验收均映射到 Task 2–12；图谱编号只覆盖有映射节点（Task 5/6）；教师端明确为非目标（Spec §5.2）。
- **Placeholder scan:** 未使用 TBD/TODO；页面任务均给出实际选择器与值；未写“适当处理”。
- **Type consistency:** `annotations: Record<string, number>` 在 Task 5 定义、Task 6 使用；`AnnotationNote` 的 `index/title` props 在 Task 3 定义、Task 6/9 使用；`oslab-annotation-badge` 类在 Task 3 定义。
- **Test consistency:** 现有图谱测试的旧硬编码值在 Task 2 一次性替换；新增 annotation 测试在 Task 5；播放器契约在 Task 1 修复。
- **Resource constraint:** 所有测试/构建串行，显式 heap cap；不自动提交；不新增依赖。
