# OSLab C+B 工作台与图谱实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 按用户资源偏好 inline 顺序执行，不并行派发重型子代理。

**Goal:** 在已交付的 C+B 全站基线上，把「课程学习图谱」「学习单元工作台」「节点工作台」「实验界面」四块推进到与 2026-10-24 设计稿一致的完成度；图谱严格对齐用户确认的截图，实验页采用 B 的实验手册气质。

**Architecture:** 先扩展共享 token 与图谱组件的可配置能力，再改图谱页的数据映射、工具栏、注记与详情结构，最后按页面重排三个工作台。所有改动限于表现层与纯展示 prop；不改 API 契约、路由行为、store、答题/判题/学习记录逻辑。实验页的 B 主题通过页面级 token 作用域实现，全局壳层、右下角 AI 浮球与黄色 `OS` 品牌块保持不变。

**Tech Stack:** Vue 3.2 + TypeScript 4.5 + Arco Design Vue 2.56 + ECharts 5.6 + Monaco + Vue CLI 5；测试为 Node 静态/渲染契约脚本（`node tests/*.js`），构建为 `npm run build`。

**Spec:** `docs/design/2026-10-24-cb-learning-atlas.md`

**Visual references:**
- 图谱基准：`design/directions/oslab-design-directions.html` 的 `C+B · 学习图册`，以及用户 2026-10-24 提供的图谱截图（课程学习图谱 + 右侧 NODE / INSPECTOR）。
- 三个工作台基准：`design/directions/oslab-workspaces-cb.html`（学习单元 / 节点工作台 / 实验界面 · B）。

**Current baseline:** 2026-10-24 全站 C+B 改造已交付，记录见 `docs/design/2026-10-24-cb-delivery.md`；当前 `KnowledgeGraph.vue` 已有索引卡节点、`annotations` prop、编号批注与 `AnnotationNote`。

**Execution amendment (2026-10-24, later):** 用户要求严格对齐设计稿，恢复小图（≤12 可见节点）全量编号（选中固定 ③、其余顺序 ①②④…），并新增确定性放射布局与长度档位卡宽；大图仍回退为选中 ③ + 两个最近关系 ①②。`buildVisibleAnnotations` 按此规则实现。

---

## Global Constraints

- **纯表现层**：不改 API、路由、store、权限、题目/实验/任务逻辑；除 `annotations`/`edgeTone` 等纯展示 prop 外不新增数据请求。
- **不新增依赖**：不引入 Tailwind、UI 框架、外链字体或图标库；使用现有 Arco、系统字体栈与既有组件。
- **Token 单一来源**：颜色、字体、间距、圆角、阴影只在 `src/styles/learning-theme.css` 定义；图谱 `GRAPH_TOKENS` 必须逐项镜像并由测试守护。页面禁止新增裸色值。
- **C+B 基线**：页面标题、当前选中项、进度、选中图谱节点使用荧光笔；编号只在有真实映射或小图全量编号时出现；每个详情面板最多一个批注块。
- **图谱编号修订（有意为之）**：用户要求严格对齐截图中的 ①–⑪ 编号。小图（当前可见节点 ≤ 12）按画布顺序给每个可见节点编号；大图（> 12）回退为“选中节点 3 + 最多两个最近关系 1/2”。此条修订写入交付记录。
- **实验页 B 的边界**：B 主题只作用于 `#experimentWorkbench` 页面内容；全局 Header、实验中心侧栏、右下角 AI 浮球与黄色 `OS` 品牌块保持现状，不整体切换站点主题。
- **内容不伪造**：节点工作台的教材图示/正文由教师内容驱动，不在任意 Markdown 中注入编号；编号用于壳层章节、真实关系与真实推荐。没有数据的统计、批注、示例一律不新增。
- **可访问性底线**：正文对比度 ≥ 4.5:1；状态必须有文字；`:focus-visible` 2px；桌面点击目标 ≥ 32px；`prefers-reduced-motion` 下关闭动效。
- **资源纪律**：测试、构建、lint 串行执行；Node 显式 `--max-old-space-size`（测试 2048，构建 4096）；不并行启动多个重型进程。
- **提交纪律**：本计划默认不自动 `git commit`；每个任务以测试、构建或浏览器检查为检查点。只有用户明确要求时才提交。
- **验证环境**：前端 dev server 预期运行在 `http://localhost:8081`；若未运行，用 `NODE_OPTIONS=--max-old-space-size=4096 npm run serve` 启动一次，不要重复启动。

## Baseline

改动前先确认现有契约全绿：

```bash
cd 前端代码/osLab-frontend
for f in tests/*.js; do echo "=== $f"; NODE_OPTIONS=--max-old-space-size=2048 node "$f" || break; done
```

Expected: 全部通过（`knowledge-graph-render.js` 29 项、`design-system-contracts.js`、`course-semantics.js`、`learning-integration-contracts.js` 等）。

---

### Task 1: 工作台与图谱契约、B 主题 token

**Files:**
- Create: `前端代码/osLab-frontend/tests/workbench-layout-contracts.js`
- Modify: `前端代码/osLab-frontend/tests/design-system-contracts.js`
- Modify: `前端代码/osLab-frontend/src/styles/learning-theme.css:27-143`

**Interfaces:**
- Consumes: 现有 C+B token（`--oslab-canvas` … `--oslab-graph-relation-fallback`）。
- Produces: `--oslab-font-tech`、`--oslab-graph-edge-muted`、`--oslab-graph-highlight`、`.oslab-lab-b` 作用域 token；供 Task 2–6 使用。

- [ ] **Step 1: 新建工作台契约测试**

创建 `前端代码/osLab-frontend/tests/workbench-layout-contracts.js`：

```js
// Run with: node tests/workbench-layout-contracts.js
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const read = (file) =>
  fs.readFileSync(path.join(root, "src", file), "utf8");

const graph = read("views/knowledgeGraph/KnowledgeGraphView.vue");
assert.ok(
  graph.includes("VISIBLE_ANNOTATION_LIMIT"),
  "图谱页必须声明小图全量编号上限"
);
assert.ok(
  graph.includes("buildVisibleAnnotations"),
  "图谱页必须用纯函数生成小图编号"
);
assert.ok(
  graph.includes("graph-annotation-chip"),
  "图谱画布必须有 ③ 注记条"
);
assert.ok(
  graph.includes("NODE / INSPECTOR"),
  "图谱详情必须使用 NODE / INSPECTOR 眉题"
);
assert.ok(
  graph.includes("detail-definition"),
  "节点定义必须使用高亮定义块"
);
assert.ok(
  graph.includes('edge-tone="muted"'),
  "课程学习图谱必须启用灰度边"
);

const unit = read("views/learning/CourseUnitView.vue");
assert.ok(unit.includes("activity-strip"), "单元页必须有活动卡片带");
assert.ok(unit.includes("unit-ledger"), "单元页必须有右侧学习地图");
assert.ok(unit.includes("activityGroups"), "单元页必须按活动类型汇总");
assert.ok(unit.includes("oslab-highlight-text"), "单元页标题必须使用荧光笔");

const node = read("views/learning/NodeLearningWorkspaceView.vue");
assert.ok(node.includes("node-hero"), "节点页必须有 hero 卡");
assert.ok(node.includes("figure-source"), "节点页教材来源必须用等宽行");
assert.ok(node.includes("tutor-focus-note"), "导师卡必须有 ③ 焦点批注");
assert.ok(node.includes("AnnotationNote"), "节点页必须使用批注组件");

const lab = read("views/question/ViewQuestionView.vue");
assert.ok(lab.includes("oslab-lab-b"), "实验页必须挂 B 主题作用域");
assert.ok(lab.includes("lab-fig"), "实验页必须有 FIG 标签");
assert.ok(lab.includes("lab-spec"), "实验页必须有等宽规格行");
assert.ok(lab.includes("lab-margin-note"), "实验说明必须有一条页边注记");

console.log("Workbench layout contracts passed: graph, unit, node, lab.");
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/workbench-layout-contracts.js
```

Expected: FAIL，第一条提示 `图谱页必须声明小图全量编号上限`。

- [ ] **Step 3: 扩展 token**

在 `learning-theme.css` 的 `:root` 中追加：

```css
  --oslab-font-tech: "Bahnschrift SemiCondensed", "Arial Narrow",
    "Noto Sans SC", "Microsoft YaHei", sans-serif;
  --oslab-graph-edge-muted: #c9ccbe;
  --oslab-graph-highlight: #f2d64b;
```

在文件末尾追加实验页 B 主题作用域（值来自 C+B 设计稿的 B 方向）：

```css
/* 实验页 B：仅作用于页面内容，不改变全局壳层与 AI 浮球 */
.oslab-lab-b {
  --oslab-lab-bg: #e7ecef;
  --oslab-lab-panel: #f8fafb;
  --oslab-lab-panel-soft: #f1f5f7;
  --oslab-lab-ink: #142b3b;
  --oslab-lab-ink-soft: #3f5667;
  --oslab-lab-muted: #5f7688;
  --oslab-lab-line: #b7c7d3;
  --oslab-lab-line-strong: #8da5b6;
  --oslab-lab-blue: #1f5fbf;
  --oslab-lab-red: #b6483a;
  --oslab-lab-red-soft: #f7ddd6;

  --oslab-canvas: var(--oslab-lab-bg);
  --oslab-panel: var(--oslab-lab-panel);
  --oslab-panel-soft: var(--oslab-lab-panel-soft);
  --oslab-ink: var(--oslab-lab-ink);
  --oslab-ink-soft: var(--oslab-lab-ink-soft);
  --oslab-muted-strong: var(--oslab-lab-muted);
  --oslab-line: var(--oslab-lab-line);
  --oslab-line-strong: var(--oslab-lab-line-strong);
  --oslab-primary: var(--oslab-lab-blue);
  --oslab-primary-hover: #174a96;
  --oslab-annotation: var(--oslab-lab-red);
  --oslab-annotation-soft: var(--oslab-lab-red-soft);
  --oslab-highlight: var(--oslab-lab-red);
  --oslab-highlight-soft: var(--oslab-lab-red-soft);

  --oslab-font-display: var(--oslab-font-tech);
  --oslab-radius-sm: 0px;
  --oslab-radius-md: 0px;
  --oslab-radius-lg: 0px;
  --oslab-radius-container: 0px;
  --oslab-radius-control: 0px;
  --oslab-shadow-card: none;

  --arcoblue-5: 23, 74, 150;
  --arcoblue-6: 31, 95, 191;
  --arcoblue-7: 18, 58, 120;
  --red-6: 182, 72, 58;
}
```

- [ ] **Step 4: 扩展设计系统契约**

在 `tests/design-system-contracts.js` 末尾追加：

```js
assert.ok(
  css.includes("--oslab-graph-edge-muted: #c9ccbe"),
  "图谱必须提供灰度边 token"
);
assert.ok(
  css.includes("--oslab-graph-highlight: #f2d64b"),
  "图谱选中节点必须有荧光笔条 token"
);
assert.ok(
  css.includes(".oslab-lab-b"),
  "实验页必须提供 B 主题作用域"
);
assert.ok(
  css.includes("--oslab-font-tech"),
  "B 主题必须提供 tech 字体栈"
);
```

- [ ] **Step 5: 运行契约测试**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/design-system-contracts.js
NODE_OPTIONS=--max-old-space-size=2048 node tests/workbench-layout-contracts.js
```

Expected: `design-system-contracts` PASS；`workbench-layout-contracts` 仍 FAIL（页面尚未改），继续 Task 2–6。

- [ ] **Step 6: 检查点**

```bash
git diff --stat -- 前端代码/osLab-frontend/src/styles/learning-theme.css 前端代码/osLab-frontend/tests
```

不自动提交。

---

### Task 2: 图谱组件——灰度边、选中荧光条、批注色调

**Files:**
- Modify: `前端代码/osLab-frontend/src/components/KnowledgeGraph.vue:43-64, 95-142, 534-654, 953-992`
- Modify: `前端代码/osLab-frontend/src/components/learning/AnnotationNote.vue`
- Test: `前端代码/osLab-frontend/tests/knowledge-graph-render.js`

**Interfaces:**
- Consumes: Task 1 的 `--oslab-graph-edge-muted`、`--oslab-graph-highlight`。
- Produces: `KnowledgeGraph` 新 prop `edgeTone: "semantic" | "muted"`；选中节点 `markPoint` 荧光条；`AnnotationNote` 新 prop `tone: "annotation" | "primary"`。

- [ ] **Step 1: 增加失败测试**

在 `tests/knowledge-graph-render.js` 的 annotation 测试之后追加：

```js
test("muted edge tone greys non-adjacent relations and keeps selection path cobalt", () => {
  const input = makeGraph(3);
  input.edgeTone = "muted";
  input.selectedId = "0";
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    const adjacent = edgeStyle(harness, 0);
    const distant = edgeStyle(harness, 1);
    assert.equal(adjacent.color, harness.GRAPH_TOKENS.pathEdge);
    assert.equal(distant.color, harness.GRAPH_TOKENS.mutedEdge);
    assert.notEqual(adjacent.opacity, distant.opacity);
  } finally {
    harness.graph.dispose();
  }
});

test("selected node renders a highlighter tape markPoint", () => {
  const harness = createHarness(makeGraph(3));
  try {
    harness.initChart();
    harness.flush();
    const series = harness.graph.getOption().series[0];
    assert.ok(series.markPoint, "选中有节点时必须输出 markPoint");
    assert.equal(series.markPoint.data.length, 1);
    assert.equal(
      series.markPoint.itemStyle.color,
      harness.GRAPH_TOKENS.highlight
    );
  } finally {
    harness.graph.dispose();
  }
});
```

在 `graph palette mirrors learning-theme tokens` 中追加：

```js
assertToken("oslab-graph-edge-muted", GRAPH_TOKENS.mutedEdge);
assertToken("oslab-graph-highlight", GRAPH_TOKENS.highlight);
```

把 `tests/knowledge-graph-render.js:1073` 的小图尺寸断言改为自适应宽度（截图里的“用户态”与中心节点宽度不同）：

```js
test("small graphs fit card width to the label within 64-196", () => {
  const input = makeGraph(5);
  input.selectedId = "";
  input.nodes[0].name = "短";
  input.nodes[1].displayName =
    "一个非常非常长需要截断并且无法在两行内完整显示的知识点名称示例";
  input.nodes[4].category = "未定义类别";
  const harness = createHarness(input);
  try {
    const { GRAPH_TOKENS } = harness;
    harness.initChart();
    harness.flush();
    const option = harness.graph.getOption().series[0];
    const sizes = new Set();
    for (const node of option.data) {
      assert.equal(node.symbol, "rect");
      assert.equal(node.itemStyle.borderRadius, 8);
      assert.ok(
        node.symbolSize[0] >= 64 &&
          node.symbolSize[0] <= 196 &&
          node.symbolSize[1] >= 38 &&
          node.symbolSize[1] <= 42,
        `small-graph card width must fit the label within bounds: ${node.symbolSize}`
      );
      sizes.add(`${node.symbolSize[0]}x${node.symbolSize[1]}`);
      const nameLines = String(node.label.formatter)
        .split("\n")
        .filter((line) => line.includes("{name|"));
      assert.ok(nameLines.length <= 2, "names must clamp to two lines");
    }
    assert.ok(sizes.size >= 3, "varied names must produce varied card widths");
    assert.equal(option.data[0].itemStyle.color, GRAPH_TOKENS.nodeFill.concept);
  } finally {
    harness.graph.dispose();
  }
});
```

保留 150 节点大图的紧凑档测试（`symbolSize` 112–128）不变。

- [ ] **Step 2: 运行测试确认失败**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/knowledge-graph-render.js
```

Expected: FAIL，`GRAPH_TOKENS.mutedEdge` / `markPoint` 不存在。

- [ ] **Step 3: 增加 prop 与 token**

在 `KnowledgeGraph.vue` 的 `annotations` prop 之后加：

```js
  // 边色调：semantic = 按关系类型着色（其他页面默认）；
  // muted = 课程学习图谱的灰度边，只有选中邻接/路径使用钴蓝。
  edgeTone: { type: String, default: "semantic" },
```

在 `GRAPH_TOKENS` 的 `highlightSoft` 后加：

```js
  highlight: "#f2d64b",
  mutedEdge: "#c9ccbe",
```

- [ ] **Step 3b: 小图节点宽度随标题自适应**

把 `nodeSizeOf` 改为：

```js
const NODE_AUTO_WIDTH_LIMIT = 40;

const nodeSizeOf = (node) => {
  const fixed =
    ((props.nodes?.length ?? 0) <= NODE_AUTO_WIDTH_LIMIT
      ? NODE_TIERS_ROOMY
      : NODE_TIERS)[nodeTierOf(node)];
  if ((props.nodes?.length ?? 0) > NODE_AUTO_WIDTH_LIMIT) return fixed;
  const label = String(node.displayName || node.name || "").replace(/\s+/g, " ");
  // 44 = 左右内边距 + 状态圆点占位；编号额外在 buildNodeStyle 里加 20/32。
  const width = Math.min(196, Math.max(64, textWidth(label) + 44));
  return { width, height: fixed.height };
};
```

同时把 `buildLabel` 的小图标签内缩从 32 改为 24，避免自适应窄卡把“用户态”截成“用…”：

```js
const labelWidth =
  ctx.width - ((props.nodes?.length ?? 0) <= NODE_AUTO_WIDTH_LIMIT ? 24 : 16);
```

同时在 `buildNodeStyle` 里把编号加宽改成“全图编号时只加 20px”，避免 11 个节点全部被 +32 撑开：

```js
const annotationCount = Object.keys(props.annotations || {}).length;
const allNumbered =
  annotationCount > 0 && annotationCount === (props.nodes?.length ?? 0);
const annotationBump = allNumbered ? 20 : 32;
const size = annotationNumber
  ? {
      width: Math.min(196, baseSize.width + annotationBump),
      height: baseSize.height,
    }
  : baseSize;
```

- [ ] **Step 4: 在 `buildLink` 中应用灰度边**

在 `if (pathEmphasis) { ... }` 之前插入：

```js
  if (props.edgeTone === "muted") {
    if (pathEmphasis || adjacent) {
      color = GRAPH_TOKENS.pathEdge;
      width = pathEmphasis ? 2 : 1.6;
      lineType = "dashed";
    } else {
      color = GRAPH_TOKENS.mutedEdge;
      width = 1.2;
      lineType = type === "related" ? "dotted" : "dashed";
    }
  }
```

- [ ] **Step 5: 输出选中荧光条 `markPoint`**

在 `KnowledgeGraph.vue` 的 series 返回对象中，`data` 之后加：

```js
    markPoint: (() => {
      const selected = data.find((node) => node.id === ctx.selectedId);
      if (!selected) return { data: [] };
      return {
        silent: true,
        symbol: "rect",
        symbolSize: [28, 5],
        itemStyle: {
          color: GRAPH_TOKENS.highlight,
          borderRadius: 1,
        },
        data: [
          {
            coord: [selected.x, selected.y],
            symbolOffset: [0, -24],
          },
        ],
      };
    })(),
```

- [ ] **Step 6: 给 `AnnotationNote` 增加色调**

修改 `AnnotationNote.vue`：

```vue
<template>
  <aside class="annotation-note" :class="tone === 'primary' ? 'is-primary' : 'is-annotation'" :aria-label="title || `批注 ${index}`">
    <span class="annotation-note__index" aria-hidden="true">{{ index }}</span>
    <div class="annotation-note__body">
      <p v-if="title" class="annotation-note__title">{{ title }}</p>
      <p class="annotation-note__text"><slot /></p>
    </div>
  </aside>
</template>

<script setup lang="ts">
// eslint-disable-next-line no-undef
defineProps<{
  index: number | string;
  title?: string;
  tone?: "annotation" | "primary";
}>();
</script>
```

在 style 中：

```css
.annotation-note__index { background: var(--oslab-annotation); }
.annotation-note.is-primary .annotation-note__index { background: var(--oslab-primary); }
.annotation-note.is-primary { border-color: var(--oslab-primary); background: var(--oslab-highlight-soft); }
```

- [ ] **Step 7: 运行图谱测试**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/knowledge-graph-render.js
```

Expected: 全部 PASS。若 `markPoint` 的 SSR 输出导致既有断言失败，先确认失败点是“新增 markPoint”而不是标签/边回归；不要删除原有断言。

- [ ] **Step 8: 检查点**

```bash
git diff --stat -- 前端代码/osLab-frontend/src/components/KnowledgeGraph.vue 前端代码/osLab-frontend/src/components/learning/AnnotationNote.vue 前端代码/osLab-frontend/tests/knowledge-graph-render.js
```

不自动提交。

---

### Task 3: 课程学习图谱页——编号、注记条、工具栏与详情

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/knowledgeGraph/KnowledgeGraphView.vue:85-285, 403-630, 1679-1725, 2634-3320`
- Test: `前端代码/osLab-frontend/tests/workbench-layout-contracts.js`（Task 1 已创建）
- Test: `前端代码/osLab-frontend/tests/knowledge-graph-render.js`（新增纯函数测试）

**Interfaces:**
- Consumes: Task 2 的 `edgeTone="muted"`、`markPoint`、`AnnotationNote tone="primary"`。
- Produces: `buildVisibleAnnotations(nodes, selectedKey, relations, limit)`；小图全量编号；`graph-annotation-chip`；`detail-definition`；`NODE / INSPECTOR`。

- [ ] **Step 1: 增加纯函数失败测试**

在 `tests/knowledge-graph-render.js` 的 view 测试区追加（使用 `createViewHarness` 暴露的导出）：

```js
test("small graphs number every visible node and large graphs fall back to focus", () => {
  const nodes = Array.from({ length: 11 }, (_, index) => ({
    id: String(index),
    name: `node-${index}`,
  }));
  const small = createViewHarness();
  const numbered = small.buildVisibleAnnotations(nodes, "3", [], 12);
  assert.equal(Object.keys(numbered).length, 11);
  assert.equal(numbered["0"], 1);
  assert.equal(numbered["3"], 4);
  assert.equal(numbered["10"], 11);

  const largeNodes = Array.from({ length: 150 }, (_, index) => ({
    id: String(index),
    name: `node-${index}`,
  }));
  const fallback = small.buildVisibleAnnotations(
    largeNodes,
    "3",
    [{ nodeKey: "7" }, { nodeKey: "9" }],
    12
  );
  assert.deepEqual(fallback, { "3": 3, "7": 1, "9": 2 });
});
```

在 view 编译导出串中加入 `buildVisibleAnnotations, graphAnnotations, selectedAnnotationNumber, selectedRelationCount`。

- [ ] **Step 2: 运行测试确认失败**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/knowledge-graph-render.js
```

Expected: FAIL，`buildVisibleAnnotations is not a function`。

- [ ] **Step 3: 实现编号纯函数并替换 `graphAnnotations`**

在 `KnowledgeGraphView.vue` 的 `relationGroups` 之后加入：

```ts
const VISIBLE_ANNOTATION_LIMIT = 12;

const buildVisibleAnnotations = (
  visibleNodes: Array<{ id?: unknown; nodeCode?: string; stableId?: string }>,
  selectedKey: string,
  relationItems: Array<{ nodeKey: string }>,
  limit = VISIBLE_ANNOTATION_LIMIT
) => {
  const result: Record<string, number> = {};
  if (visibleNodes.length > 0 && visibleNodes.length <= limit) {
    visibleNodes.forEach((node, index) => {
      result[nodeKeyOf(node)] = index + 1;
    });
    return result;
  }
  if (!selectedKey) return result;
  result[selectedKey] = 3;
  const seen = new Set([selectedKey]);
  relationItems
    .filter((item) => {
      if (!item.nodeKey || seen.has(item.nodeKey)) return false;
      seen.add(item.nodeKey);
      return true;
    })
    .slice(0, 2)
    .forEach((item, index) => {
      result[item.nodeKey] = index + 1;
    });
  return result;
};

const graphAnnotations = computed<Record<string, number>>(() => {
  const relations = [
    ...relationGroups.value.prerequisites,
    ...relationGroups.value.successors,
    ...relationGroups.value.related,
  ];
  return buildVisibleAnnotations(
    canvasGraph.value.nodes,
    selectedNodeKey.value,
    relations
  );
});

const selectedAnnotationNumber = computed(
  () => graphAnnotations.value[selectedNodeKey.value] || 3
);
const selectedRelationCount = computed(
  () =>
    relationGroups.value.prerequisites.length +
    relationGroups.value.successors.length +
    relationGroups.value.related.length
);
const selectedTypeLabel = computed(() =>
  selectedNode.value ? nodeTypeLabel(selectedNode.value) : ""
);
```

删除旧的 `neighborAnnotationCount`，并把 `selectedAnnotationText` 改为：

```ts
const selectedAnnotationText = computed(() => {
  if (!selectedNode.value) return "";
  return `${selectedAnnotationNumber.value} 当前节点；${selectedTypeLabel.value} · ${selectedCrumb.value} · ${selectedRelationCount.value} 条关系。先读定义，再进入节点工作台。`;
});
```

- [ ] **Step 4: 重构图谱卡片头部与筛选行**

把页面级 `<div class="toolbar">…</div>`（当前 107–131 行）整体删除，改为图谱卡片内部结构：

```vue
<div class="graph-card-head">
  <div class="graph-card-heading">
    <span class="kicker">KNOWLEDGE GRAPH</span>
    <h2 class="card-title">知识图谱</h2>
  </div>
  <div class="graph-card-tools">
    <label class="label-toggle">
      <span>关系标签</span>
      <a-switch v-model="showRelationLabels" size="small" />
    </label>
    <a-tooltip v-if="!isTeaching" content="只保留当前选中节点的补救路径与一跳邻接">
      <a-button
        size="mini"
        :type="pathFocus ? 'primary' : 'outline'"
        @click="pathFocus = !pathFocus"
      >只看学习路径</a-button>
    </a-tooltip>
    <a-tooltip content="复位视野：图谱居中并恢复默认缩放">
      <a-button size="mini" @click="resetViewCanvas">重置视野</a-button>
    </a-tooltip>
  </div>
</div>
<div class="graph-scope">
  <a-input-search
    v-model="searchText"
    placeholder="搜索范围内的知识点"
    allow-clear
    class="graph-search"
    @search="searchFocus"
    @clear="searchText = ''"
  />
  <a-select
    v-model="statusFilter"
    placeholder="全部状态"
    allow-clear
    class="graph-status"
  >
    <a-option value="GREEN">已掌握</a-option>
    <a-option value="YELLOW">待巩固</a-option>
    <a-option value="RED">薄弱</a-option>
    <a-option value="GRAY">未学习</a-option>
  </a-select>
  <div class="graph-stats">
    课程共 {{ graphMeta.totalNodes }} 个节点 ·
    {{ graphMeta.totalLinks }} 条关系 · 可见
    <b>{{ canvasGraph.nodes.length }}</b> 个节点
  </div>
</div>
```

- [ ] **Step 5: 传灰度边并加 ③ 注记条**

在 `<KnowledgeGraph …>` 上加：

```vue
edge-tone="muted"
```

在 `.canvas-wrap` 内、`<KnowledgeGraph>` 之前加：

```vue
<div v-if="selectedNode" class="graph-annotation-chip" aria-hidden="true">
  <span class="graph-annotation-index">{{ selectedAnnotationNumber }}</span>
  <span class="graph-annotation-copy">
    <b>{{ selectedTypeLabel }}</b> · {{ selectedCrumb }} ·
    {{ selectedRelationCount }} 条关系
  </span>
</div>
```

- [ ] **Step 6: 详情面板改为截图结构**

在知识点详情分支（`v-else-if="selectedNode"`）中：

1. 把眉题改为：

```vue
<span class="card-title">NODE / INSPECTOR</span>
```

2. 给标题加荧光笔：

```vue
<h3 class="node-name oslab-highlight-text">
  {{ selectedNode.displayName || selectedNode.name }}
</h3>
```

3. 把 `selectedNode.description` 的普通段落替换为定义块：

```vue
<blockquote v-if="selectedNode.description" class="detail-definition">
  <span class="detail-definition-label">定义</span>
  <span>{{ selectedNode.description }}</span>
</blockquote>
<p v-else class="detail-definition detail-definition--empty">
  <span class="detail-definition-label">定义</span>
  <span>暂无节点描述</span>
</p>
```

4. 批注改为：

```vue
<AnnotationNote
  :index="selectedAnnotationNumber"
  title="批注 / NOTES"
  tone="primary"
>
  {{ selectedAnnotationText }}
</AnnotationNote>
```

5. 在“掌握证据”标题之后加真实题量统计：

```vue
<div v-if="selectedQuestionStats" class="evidence-stats">
  <div><b>{{ selectedQuestionStats.total }}</b><span>本节点题目</span></div>
  <div><b>{{ selectedQuestionStats.attempted }}</b><span>已作答</span></div>
</div>
```

并在 script 中加：

```ts
const selectedQuestionStats = computed(
  () => selectedWorkbench.value?.questionStats || null
);
```

6. 在“关联节点”标题之后、原有分组之前加真实关系 chips：

```vue
<div v-if="allRelatedRelations.length" class="relation-chips">
  <button
    v-for="item in allRelatedRelations.slice(0, 3)"
    :key="`chip-${item.nodeKey}`"
    type="button"
    class="relation-chip"
    @click="selectAndFocus(item.nodeKey)"
  >
    <i class="dot" :class="dotClass(item.status)"></i>
    {{ item.displayName }}
  </button>
</div>
```

加 computed：

```ts
const allRelatedRelations = computed(() => [
  ...relationGroups.value.prerequisites,
  ...relationGroups.value.successors,
  ...relationGroups.value.related,
]);
```

7. 把“查看该节点的关联题目”文本链接改为次按钮：

```vue
<button type="button" class="cta-secondary" @click="openNodeQuestions">
  查看关联题目
</button>
```

- [ ] **Step 7: 图谱页 CSS**

在 `KnowledgeGraphView.vue` 的 scoped style 末尾追加：

```css
.graph-card-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.graph-card-heading .kicker {
  display: block;
  margin-bottom: 2px;
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--oslab-muted-strong);
}
.graph-card-heading .card-title {
  margin: 0;
  font-family: var(--oslab-font-display);
  font-size: 20px;
}
.graph-card-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.graph-scope {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 0 12px;
  border-bottom: 1px solid var(--oslab-line-soft);
}
.graph-search { width: 260px; }
.graph-search :deep(.arco-input-wrapper) { border-radius: var(--oslab-radius-control); }
.graph-status { width: 126px; }
.graph-stats {
  margin-left: auto;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
}
.graph-stats b { color: var(--oslab-ink-soft); }
.canvas-wrap { position: relative; }
.graph-annotation-chip {
  position: absolute;
  left: 14px;
  top: 12px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 7px;
  max-width: calc(100% - 28px);
  padding: 5px 10px 5px 6px;
  border: 1px solid var(--oslab-primary);
  border-radius: var(--oslab-radius-pill);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--oslab-shadow-card);
  pointer-events: none;
}
.graph-annotation-index {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--oslab-primary);
  color: #fff;
  font-family: var(--oslab-font-mono);
  font-size: 9px;
}
.graph-annotation-copy {
  overflow: hidden;
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
  font-size: 11px;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.graph-annotation-copy b { color: var(--oslab-ink); font-weight: 600; }
.detail-definition {
  margin: 2px 0 10px;
  padding: 10px 12px;
  border-left: 2px solid var(--oslab-primary);
  border-radius: 0 var(--oslab-radius-md) var(--oslab-radius-md) 0;
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink-soft);
  font-size: 13px;
  line-height: 1.7;
}
.detail-definition-label {
  display: block;
  margin-bottom: 3px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}
.detail-definition--empty { color: var(--oslab-muted-strong); }
.detail-card :deep(.annotation-note) {
  margin: 8px 0 12px;
}
.evidence-stats {
  display: flex;
  gap: 22px;
  padding: 4px 0 10px;
}
.evidence-stats div { display: flex; align-items: baseline; gap: 5px; }
.evidence-stats b {
  color: var(--oslab-ink);
  font-family: var(--oslab-font-mono);
  font-size: 20px;
}
.evidence-stats span { color: var(--oslab-muted-strong); font-size: 11px; }
.relation-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.relation-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-soft);
  color: var(--oslab-ink-soft);
  font-size: 11px;
}
.relation-chip:hover { border-color: var(--oslab-primary); }
.cta-secondary {
  width: 100%;
  min-height: 36px;
  margin-top: 8px;
  border: 1px solid var(--oslab-line-strong);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font-size: 13px;
  font-weight: 500;
}
.cta-secondary:hover { border-color: var(--oslab-primary); color: var(--oslab-primary); }
```

并把图例关系线改为灰度底、保留虚线语义：

```css
.rel-dependent,
.rel-contain,
.rel-related,
.rel-implements {
  border-top-color: var(--oslab-graph-node-border);
}
.rel-path { border-top-color: var(--oslab-graph-path-edge); }
```

- [ ] **Step 8: 运行契约与图谱测试**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/knowledge-graph-render.js
NODE_OPTIONS=--max-old-space-size=2048 node tests/workbench-layout-contracts.js
```

Expected: 图谱测试 PASS；工作台契约在 Task 4–6 完成前仍会失败（unit/node/lab 部分）。

- [ ] **Step 9: 浏览器结构检查**

打开 `/graph`（1440 宽）：

1. 选中节点后，左侧出现 ③ 注记条，编号与节点徽标一致。
2. 小图（≤12 节点）每个可见节点有 1..N 编号；大图仅选中节点 + 两个关系编号。
3. 非邻接边为灰度虚线；选中邻接/路径为钴蓝虚线。
4. 右侧详情顺序为：NODE / INSPECTOR → 高亮标题 → 定义块 → 批注 / NOTES → 掌握状态 → 掌握证据（两数字）→ 关联节点 chips → 两个按钮。
5. 1024/768 下注记条不遮挡节点，详情下沉。

---

### Task 4: 学习单元工作台

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/learning/CourseUnitView.vue:1-202, 566-754`
- Test: `前端代码/osLab-frontend/tests/workbench-layout-contracts.js`

**Interfaces:**
- Consumes: 现有 `unit.activities`、`unit.progress`、`unit.objectives`、`unit.nodeMappings`；`CourseCatalogSidebar`；`MdViewer`；`QuestionAnswerCard`。
- Produces: `activityGroups`、`activity-strip`、`unit-ledger`、高亮标题与活动卡片；不新增数据请求。

- [ ] **Step 1: 在 script 中加入活动分组**

在 `CourseUnitView.vue` 的 `activity` / `nextActivity` computed 之后加入：

```ts
const activityGroups = computed(() => {
  const list = unit.value?.activities || [];
  const groupOf = (types: string[]) =>
    list.filter((item) => types.includes(item.activityType));
  const groups = [
    {
      key: "read",
      index: 1,
      title: "阅读与演示",
      hint: "按顺序读，完成阅读记录",
      items: groupOf(["READING", "WORKED_EXAMPLE", "DEMO", "SUMMARY"]),
    },
    {
      key: "practice",
      index: 2,
      title: "练习与测评",
      hint: "作答后才形成掌握证据",
      items: groupOf(["PRACTICE", "ASSESSMENT"]),
    },
    {
      key: "lab",
      index: 3,
      title: "实践",
      hint: "实验与演示同步进度",
      items: groupOf(["EXPERIMENT"]),
    },
  ];
  return groups
    .filter((group) => group.items.length > 0)
    .map((group) => ({
      ...group,
      done: group.items.filter((item) => item.completed).length,
    }));
});

const isPracticeActivity = computed(() =>
  ["PRACTICE", "ASSESSMENT"].includes(activity.value?.activityType || "")
);
const activeActivityNumber = computed(() =>
  isPracticeActivity.value ? "②" : "①"
);
```

- [ ] **Step 2: 重排模板**

把 `<div class="unit-main">` 内 `data` 之后的结构改为：

```vue
<div v-if="unit" class="unit-main-inner">
  <header class="unit-hero">
    <div>
      <div class="eyebrow">UNIT / {{ unit.unitCode }}</div>
      <h1 class="oslab-highlight-text">{{ unit.title }}</h1>
      <div class="unit-meta">
        <span v-if="unit.estimatedMinutes">建议学习时间 {{ unit.estimatedMinutes }} 分钟</span>
        <span>{{ unit.objectives?.length || 0 }} 个学习目标</span>
        <span>{{ unit.activities?.length || 0 }} 项学习活动</span>
      </div>
    </div>
    <div class="unit-progress-big">
      <b>{{ unit.progress.percent || 0 }}<small>%</small></b>
      <span>{{ unit.progress.completedCount }}/{{ unit.progress.requiredCount }} 必需活动</span>
      <a-progress
        :percent="(unit.progress.percent || 0) / 100"
        :show-text="false"
        stroke-color="var(--oslab-highlight)"
        size="small"
      />
    </div>
  </header>

  <div class="unit-content-grid">
    <div class="unit-content">
      <section class="objectives-card">
        <div class="section-kicker">OBJECTIVES</div>
        <h2>这一单元要解决什么</h2>
        <ul>
          <li v-for="(objective, index) in unit.objectives" :key="objective">
            <b>{{ index + 1 }}</b><span>{{ objective }}</span>
          </li>
        </ul>
      </section>

      <section class="activity-strip-card">
        <div class="section-head">
          <div><div class="section-kicker">ACTIVITIES</div><h2>学习活动</h2></div>
          <span class="section-count">{{ unit.activities?.filter((item) => item.completed).length || 0 }} / {{ unit.activities?.length || 0 }} 已完成</span>
        </div>
        <nav class="activity-strip" aria-label="学习活动">
          <button
            v-for="(item, index) in unit.activities"
            :key="item.activityCode"
            type="button"
            class="activity-step-card"
            :class="{ active: activeCode === item.activityCode, done: item.completed }"
            :aria-current="activeCode === item.activityCode ? 'step' : undefined"
            @click="selectActivity(item.activityCode)"
          >
            <span class="step-number">{{ item.completed ? "✓" : String(index + 1).padStart(2, "0") }}</span>
            <span class="step-type">{{ activityLabel(item.activityType) }}{{ item.required ? "" : " · 选学" }}</span>
            <strong>{{ item.title }}</strong>
            <span class="step-status">{{ item.completed ? "已完成" : activeCode === item.activityCode ? "进行中" : "未开始" }}</span>
          </button>
        </nav>
        <p v-if="activityGroups.length" class="strip-note">
          <b>①</b> 先按顺序完成阅读与演示，再进入练习；作答结果才会形成掌握证据。
        </p>
      </section>

      <section v-if="activity" class="activity-card" :class="{ 'is-checkpoint': isPracticeActivity }">
        <div class="activity-card-head">
          <span class="activity-card-index">{{ activeActivityNumber }}</span>
          <p class="eyebrow">
            {{ activityLabel(activity.activityType) }}{{ activity.required ? "" : " · 选学" }}
            <span v-if="activity.completed">· 已完成</span>
          </p>
        </div>
        <h2>{{ activity.title }}</h2>
        <!-- 现有 activity.content / isQuestion / DEMO/EXPERIMENT / 操作按钮模板原样保留 -->
      </section>

      <aside v-if="unit.nodeMappings?.length" class="nodes">
        <div class="section-kicker">RELATED KNOWLEDGE</div>
        <h2>相关知识与人画像</h2>
        <p>概念与实践证据可在节点工作台中查看；无实践观测时显示未评估。</p>
        <div class="node-chips">
          <router-link v-for="node in unit.nodeMappings" :key="node.nodeCode" :to="...">...</router-link>
        </div>
      </aside>

      <footer>…现有上一/下一教学条目…</footer>
    </div>

    <aside class="unit-ledger" aria-label="本单元学习地图">
      <div class="section-kicker">STUDY LEDGER</div>
      <h2>本单元地图</h2>
      <div class="ledger-progress">
        <b>{{ unit.progress.percent || 0 }}<small>%</small></b>
        <span>{{ unit.progress.completedCount }}/{{ unit.progress.requiredCount }}</span>
      </div>
      <span class="meter"><i :style="{ width: `${unit.progress.percent || 0}%` }"></i></span>
      <div class="ledger-steps">
        <div v-for="group in activityGroups" :key="group.key" class="ledger-step" :class="{ current: group.items.some((item) => item.activityCode === activeCode) }">
          <span class="step-num">{{ group.index }}</span>
          <div><b>{{ group.title }}</b><small>{{ group.hint }}</small></div>
          <em>{{ group.done }} / {{ group.items.length }}</em>
        </div>
      </div>
      <p class="ledger-note">先完成阅读，再作答。答错时回到对应阅读段，不直接跳到下一题。</p>
      <div class="ledger-foot">
        <span>建议 {{ unit.estimatedMinutes || "—" }} 分钟</span>
        <span>当前：{{ activity ? activityLabel(activity.activityType) : "—" }}</span>
      </div>
    </aside>
  </div>
</div>
```

> 现有 `activity.content`、`QuestionAnswerCard`、`DEMO/EXPERIMENT` 入口、保存错误与操作按钮不要改写；删除原 `<p class="eyebrow">` 与 `<h2>`（已由新的 `activity-card-head` + `<h2>` 取代），保留其后的内容模板。`router-link` 的 `:to` 查询参数保持原样。

- [ ] **Step 3: 单元页 CSS**

替换/追加：

```css
.unit-page { display: flex; align-items: flex-start; gap: 16px; padding: 4px 0 40px; }
.unit-main { flex: 1; min-width: 0; max-width: none; }
.unit-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin: 8px 0 16px;
}
.unit-hero h1 {
  display: inline;
  margin: 0;
  padding: 0 4px;
  font-family: var(--oslab-font-display);
  font-size: 34px;
  line-height: 1.2;
  font-weight: 700;
}
.unit-meta { display: flex; flex-wrap: wrap; gap: 6px 14px; margin-top: 6px; color: var(--oslab-muted-strong); font-size: 11px; }
.unit-progress-big { flex: 0 0 auto; min-width: 170px; text-align: right; }
.unit-progress-big b { color: var(--oslab-ink); font-family: var(--oslab-font-mono); font-size: 30px; }
.unit-progress-big b small { font-size: 12px; color: var(--oslab-muted-strong); }
.unit-progress-big span { display: block; margin: 2px 0 6px; color: var(--oslab-muted-strong); font-size: 10.5px; }
.unit-content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 258px;
  gap: 16px;
  align-items: start;
}
.unit-content { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.section-kicker { color: var(--oslab-muted-strong); font-family: var(--oslab-font-mono); font-size: 10px; letter-spacing: 0.14em; }
.objectives-card,
.activity-strip-card,
.activity-card,
.nodes,
.unit-ledger {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.objectives-card { padding: 14px 16px; border-left: 3px solid var(--oslab-primary); }
.objectives-card h2 { margin: 2px 0 8px; font-size: 15px; }
.objectives-card ul { display: grid; gap: 4px; margin: 0; padding: 0; list-style: none; }
.objectives-card li { display: flex; gap: 8px; color: var(--oslab-ink-soft); font-size: 12px; line-height: 1.6; }
.objectives-card li b { color: var(--oslab-primary); font-family: var(--oslab-font-mono); font-size: 10px; }
.activity-strip-card { padding: 14px 16px; }
.section-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; }
.section-head h2 { margin: 2px 0 0; font-size: 16px; }
.section-count { color: var(--oslab-muted-strong); font-family: var(--oslab-font-mono); font-size: 10px; }
.activity-strip { display: flex; gap: 8px; margin-top: 10px; padding: 2px 2px 6px; overflow-x: auto; scrollbar-width: thin; }
.activity-step-card {
  position: relative;
  flex: 0 0 168px;
  min-height: 122px;
  padding: 10px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
  text-align: left;
}
.activity-step-card:hover { border-color: var(--oslab-line-strong); background: var(--oslab-panel); }
.activity-step-card.active { border-color: var(--oslab-primary); background: var(--oslab-panel); box-shadow: 0 4px 14px rgba(36, 71, 216, 0.10); }
.activity-step-card.active::before { content: ""; position: absolute; left: 50%; top: -4px; transform: translateX(-50%); width: 28px; height: 5px; border-radius: 1px; background: var(--oslab-highlight); }
.step-number { display: block; color: var(--oslab-muted-strong); font-family: var(--oslab-font-mono); font-size: 10px; }
.step-type { display: block; margin-top: 5px; color: var(--oslab-primary); font-size: 9.5px; letter-spacing: 0.06em; }
.activity-step-card strong { display: block; margin-top: 3px; color: var(--oslab-ink); font-size: 12px; line-height: 1.5; }
.step-status { position: absolute; left: 10px; bottom: 8px; color: var(--oslab-muted-strong); font-size: 9.5px; }
.activity-step-card.active .step-status { color: var(--oslab-primary); }
.activity-step-card.done .step-number { color: var(--oslab-mastered); }
.strip-note { display: flex; gap: 7px; margin: 8px 0 0; padding: 5px 8px; border-left: 2px solid var(--oslab-primary); background: var(--oslab-highlight-soft); color: var(--oslab-ink-soft); font-family: var(--oslab-font-display); font-size: 11px; line-height: 1.5; }
.strip-note b { color: var(--oslab-primary); font-family: var(--oslab-font-mono); font-size: 9px; }
.activity-card { padding: 18px 20px; }
.activity-card.is-checkpoint { border-left: 3px solid var(--oslab-annotation); }
.activity-card-head { display: flex; align-items: center; gap: 8px; }
.activity-card-index { display: grid; place-items: center; width: 20px; height: 20px; border-radius: 50%; background: var(--oslab-primary); color: #fff; font-family: var(--oslab-font-mono); font-size: 10px; }
.activity-card .eyebrow { margin: 0; }
.activity-card h2 { margin: 6px 0 12px; font-family: var(--oslab-font-display); font-size: 22px; }
.nodes { padding: 14px 16px; }
.nodes h2 { margin: 2px 0 4px; font-size: 15px; }
.nodes > p { margin: 0 0 8px; color: var(--oslab-muted-strong); font-size: 11px; }
.node-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.node-chips a { padding: 3px 9px; border-radius: 999px; background: var(--oslab-panel-soft); color: var(--oslab-ink-soft); font-size: 11px; }
.node-chips a:hover { background: var(--oslab-highlight-soft); color: var(--oslab-ink); }
.unit-ledger { position: sticky; top: 12px; padding: 14px; }
.unit-ledger h2 { margin: 2px 0 8px; font-family: var(--oslab-font-display); font-size: 18px; }
.ledger-progress { display: flex; align-items: baseline; gap: 4px; color: var(--oslab-muted-strong); font-family: var(--oslab-font-mono); }
.ledger-progress b { color: var(--oslab-ink); font-size: 30px; line-height: 1; }
.ledger-progress b small { font-size: 11px; }
.ledger-progress span { margin-left: auto; font-size: 10px; }
.meter { display: block; height: 8px; margin: 8px 0 10px; border-radius: 999px; background: #e6e7dc; overflow: hidden; }
.meter i { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--oslab-highlight), var(--oslab-highlight-strong)); }
.ledger-steps { display: flex; flex-direction: column; gap: 2px; }
.ledger-step { display: flex; align-items: flex-start; gap: 8px; padding: 8px 0; border-top: 1px solid var(--oslab-line); }
.step-num { display: grid; place-items: center; width: 18px; height: 18px; flex: 0 0 auto; border: 1px solid var(--oslab-line-strong); border-radius: 50%; color: var(--oslab-muted-strong); font-family: var(--oslab-font-mono); font-size: 8.5px; }
.ledger-step.current .step-num { border-color: var(--oslab-primary); background: var(--oslab-primary); color: #fff; }
.ledger-step b { display: block; color: var(--oslab-ink); font-size: 11.5px; }
.ledger-step small { display: block; color: var(--oslab-muted-strong); font-size: 9.5px; }
.ledger-step em { margin-left: auto; color: var(--oslab-muted-strong); font-family: var(--oslab-font-mono); font-style: normal; font-size: 9px; }
.ledger-note { margin: 12px 0; padding: 9px 10px; border-left: 2px solid var(--oslab-primary); background: var(--oslab-highlight-soft); color: var(--oslab-ink-soft); font-family: var(--oslab-font-display); font-size: 11px; line-height: 1.6; }
.ledger-foot { display: flex; justify-content: space-between; padding-top: 8px; border-top: 1px solid var(--oslab-line); color: var(--oslab-muted-strong); font-size: 9.5px; }
```

- [ ] **Step 4: 响应式**

```css
@media (max-width: 1024px) {
  .unit-content-grid { grid-template-columns: 1fr; }
  .unit-ledger { position: static; }
  .unit-page { display: block; }
}
@media (max-width: 760px) {
  .unit-hero { flex-direction: column; align-items: flex-start; }
  .unit-hero h1 { font-size: 26px; }
  .activity-step-card { flex-basis: 156px; }
}
```

- [ ] **Step 5: 运行工作台契约**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/workbench-layout-contracts.js
```

Expected: 单元页 4 条断言 PASS；node/lab 断言仍失败。

- [ ] **Step 6: 浏览器检查**

打开任一单元路由（如 `/courses/course.operating-systems/units/unit.1.2`）：

1. 左目录 264、中间内容、右侧 258 学习地图；1440 宽无横向滚动。
2. 活动卡片带横向滚动；当前活动高亮；完成项显示 ✓。
3. 活动为练习/测评时卡片左侧出现 ② 与红边。
4. 学习地图的 3 组计数来自真实活动，不是硬编码。
5. 1024 宽学习地图下沉，760 宽卡片单列可横滚。

---

### Task 5: 节点工作台

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/learning/NodeLearningWorkspaceView.vue:1-120, 134-192, 570-725, 1569-1800, 2120-2260`
- Test: `前端代码/osLab-frontend/tests/workbench-layout-contracts.js`

**Interfaces:**
- Consumes: 现有 `workbench`、`focusSummary`、`definitionLine`、`primaryRecommendation`、`tutor` 会话、`textbookChunks`、`learningUnits`、`prerequisites/successors/relatedNodes`。
- Produces: `node-hero` hero 卡、`figure-source`、`chunk-index`、`tutor-focus-note` ③、`AnnotationNote`；不注入教师内容。

- [ ] **Step 1: 头部改为 hero 卡并加高亮标题/节点码**

在 `<header class="node-header">` 上加 `node-hero` 类，在标题行之前加：

```vue
<div class="node-kicker">NODE / CONCEPT</div>
```

在标题行内、`<h1>` 之后加：

```vue
<span class="node-code">{{ currentNodeCode || workbench?.mastery?.nodeCode }}</span>
```

给 `<h1>` 加类：

```vue
<h1 class="node-title oslab-highlight-text">
```

在 `definitionLine` 段落上保留 `.node-definition`，但改为定义块语义：

```vue
<p v-if="definitionLine" class="node-definition">
  <span class="node-definition-label">定义</span>
  {{ definitionLine }}
</p>
```

- [ ] **Step 2: 教材摘要加编号与等宽来源行**

找到 `v-for="chunk in workbench.textbookChunks"`，改为：

```vue
<article
  v-for="(chunk, chunkIndex) in workbench.textbookChunks"
  :key="chunk.chunkCode"
  :id="`chunk-${chunk.chunkCode}`"
  class="content-section textbook-chunk"
>
  <div class="chunk-head">
    <span class="chunk-index">{{ String(chunkIndex + 1).padStart(2, "0") }}</span>
    <h3>{{ chunk.title }}</h3>
  </div>
  <p class="figure-source">
    {{ chunk.sourceSectionTitle }} · 版本 {{ chunk.revision || 1 }}
    <span v-if="chunk.sourceRef">
      · {{ Object.values(chunk.sourceRef).filter((value) => typeof value !== "object").join(" · ") }}
    </span>
  </p>
  <MdViewer :value="chunk.contentMd" />
  <AnnotationNote
    v-if="chunkIndex === 0 && (focusSummary || primaryRecommendation)"
    :index="3"
    title="当前节点"
  >
    {{ focusSummary || primaryRecommendation?.reason }}
  </AnnotationNote>
</article>
```

批注必须留在 `v-for` 的 `article` 内部（`chunkIndex` 只在循环作用域内有效），不要放到循环之后。

导入：

```ts
import AnnotationNote from "@/components/learning/AnnotationNote.vue";
```

- [ ] **Step 3: 导师卡加 ③ 焦点与紧凑统计**

在 `<aside class="tutor-panel">` 顶部加：

```vue
<div class="tutor-kicker">NODE MENTOR</div>
```

把 `.tutor-focus` 改为：

```vue
<div class="tutor-focus tutor-focus-note">
  <span class="tutor-focus-index">③</span>
  <div>
    <span class="context-label">当前学习焦点</span>
    <p>{{ focusSummary }}</p>
  </div>
</div>
```

证据卡的 `.evidence-line` 保持真实数据；把 `evidence-card` 的类加 `tutor-evidence`，便于统一视觉。

- [ ] **Step 4: CSS**

```css
.node-hero {
  padding: 14px 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.node-kicker {
  margin-bottom: 2px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
}
.node-title {
  display: inline;
  padding: 0 4px;
  font-family: var(--oslab-font-display);
  font-size: 32px;
}
.node-code {
  padding: 2px 7px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 9.5px;
}
.node-definition {
  padding: 9px 11px;
  border-left: 2px solid var(--oslab-primary);
  border-radius: 0 var(--oslab-radius-md) var(--oslab-radius-md) 0;
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink-soft);
  font-size: 13px;
  line-height: 1.7;
}
.node-definition-label {
  margin-right: 6px;
  color: var(--oslab-primary);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}
.workspace-grid { grid-template-columns: minmax(0, 1fr) 300px; gap: 16px; }
.workspace-main {
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
  padding: 14px 20px 6px;
}
.text-tab.active {
  background: var(--oslab-highlight-soft);
  border-radius: var(--oslab-radius-sm) var(--oslab-radius-sm) 0 0;
}
.text-tab.active::after { background: var(--oslab-primary); }
.content-section { border-bottom-color: var(--oslab-line); }
.textbook-chunk { padding-top: 10px; }
.chunk-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.chunk-index {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--oslab-primary);
  color: #fff;
  font-family: var(--oslab-font-mono);
  font-size: 9px;
}
.chunk-head h3 { margin: 0; font-size: 15px; }
.figure-source {
  margin: 0 0 8px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 8.5px;
  line-height: 1.6;
}
.workspace-main :deep(.annotation-note) { margin: 10px 0 12px; }
.tutor-panel {
  padding: 14px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.tutor-kicker {
  margin-bottom: 3px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 9.5px;
  letter-spacing: 0.14em;
}
.tutor-focus {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 10px;
  border: 1px solid var(--oslab-primary);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-highlight-soft);
}
.tutor-focus-index {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--oslab-primary);
  color: #fff;
  font-family: var(--oslab-font-mono);
  font-size: 9px;
}
.tutor-focus .context-label {
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}
.tutor-focus p {
  margin: 2px 0 0;
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
  font-size: 11px;
  line-height: 1.55;
}
.tutor-evidence { margin-top: 10px; }
```

- [ ] **Step 5: 运行契约**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/workbench-layout-contracts.js
```

Expected: node 页面 4 条断言 PASS。

- [ ] **Step 6: 浏览器检查**

1. 节点页头部是白卡，标题有荧光笔；节点码显示。
2. 概念摘要每个教材块有 01/02 编号与等宽来源行；第一块后有真实推荐批注 ③。
3. 导师卡有 NODE MENTOR 眉题、③ 焦点、真实掌握/证据/最近失败；聊天与发送仍可用。
4. 992/1024 宽导师卡折叠到内容底部；无横向溢出。
5. 教师内容图示不被改写；只改容器和标题样式。

---

### Task 6: 实验界面 B 主题

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/question/ViewQuestionView.vue:1-294, 934-1192`
- Modify: `前端代码/osLab-frontend/src/styles/learning-theme.css`（Task 1 已加 `.oslab-lab-b`）
- Test: `前端代码/osLab-frontend/tests/workbench-layout-contracts.js`

**Interfaces:**
- Consumes: `.oslab-lab-b` token 作用域、现有 `question`、`form.language`、`chapter`、`question.tags`、`a-tabs`、`CodeEditor`、评测状态。
- Produces: B 主题页面；`lab-fig`、`lab-spec`、`lab-margin-note`、light editor chrome；不改 Monaco、提交、轮询、导师逻辑。

- [ ] **Step 1: 根节点挂 B 主题**

```vue
<div id="experimentWorkbench" class="experiment-workbench oslab-lab-b">
```

- [ ] **Step 2: 增加 FIG 与规格行计算**

在 script 的 `chapter` / `question` 计算附近加入：

```ts
const labFigure = computed(() => {
  const title = question.value?.title || "";
  const match = /实验\s*([\d.]+)/.exec(title);
  return match ? `FIG. ${match[1]}` : "LAB";
});

const labSpec = computed(() => {
  const language = String(form.value.language || "c").toUpperCase();
  const tags = (question.value?.tags || []).slice(0, 2).join(" / ");
  return tags ? `${language} · ${tags}` : `${language} · 判题由后端执行`;
});
```

- [ ] **Step 3: 头部与面板标签**

把 `.workbench-title` 改为：

```vue
<div class="workbench-title">
  <div class="lab-title-line">
    <span class="lab-fig">{{ labFigure }}</span>
    <h2 class="title">{{ question?.title || "编程实验" }}</h2>
  </div>
  <div class="meta">
    <a-tag v-if="chapter" class="node-tag">章节：{{ chapter }}</a-tag>
    <a-tag v-for="(tag, index) of question?.tags || []" :key="index" class="node-tag" @click="goNode(tag)">
      {{ tag }}
    </a-tag>
  </div>
  <p class="lab-spec">{{ labSpec }}</p>
</div>
```

把编辑器头改为：

```vue
<div class="editor-heading">
  <span class="panel-kicker">CODE / EDITOR</span>
  <span class="hint">草稿自动保存</span>
</div>
```

把结果卡标题改为：

```vue
<span class="panel-kicker">EVALUATION</span>
<h3>评测结果</h3>
```

在实验说明页签的 `MdViewer` 之后加：

```vue
<aside class="lab-margin-note">
  草稿自动保存；提交后可在右侧查看判题进度与结果。
</aside>
```

- [ ] **Step 4: B 主题 CSS**

在 `ViewQuestionView.vue` 的 style 末尾追加：

```css
.oslab-lab-b.experiment-workbench {
  padding: 14px;
  background:
    linear-gradient(rgba(31, 95, 191, 0.055) 1px, transparent 1px) 0 0 / 24px 24px,
    linear-gradient(90deg, rgba(31, 95, 191, 0.055) 1px, transparent 1px) 0 0 / 24px 24px,
    linear-gradient(rgba(31, 95, 191, 0.10) 1px, transparent 1px) 0 0 / 120px 120px,
    linear-gradient(90deg, rgba(31, 95, 191, 0.10) 1px, transparent 1px) 0 0 / 120px 120px,
    var(--oslab-lab-bg);
}
.oslab-lab-b .workbench-crumb {
  padding-bottom: 4px;
  border-bottom: 1px solid var(--oslab-lab-line-strong);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
  letter-spacing: 0.06em;
}
.oslab-lab-b .workbench-header {
  padding: 10px 0 8px;
  border-bottom: 1px solid var(--oslab-lab-ink);
}
.oslab-lab-b .lab-title-line { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.oslab-lab-b .lab-fig {
  padding: 1px 5px;
  border: 1px solid var(--oslab-lab-red);
  color: var(--oslab-lab-red);
  font-family: var(--oslab-font-mono);
  font-size: 8.5px;
  letter-spacing: 0.08em;
}
.oslab-lab-b .workbench-title .title {
  margin: 0;
  font-family: var(--oslab-font-tech);
  font-size: 26px;
  letter-spacing: 0.04em;
}
.oslab-lab-b .lab-spec {
  margin: 4px 0 0;
  color: var(--oslab-lab-muted);
  font-family: var(--oslab-font-mono);
  font-size: 8.5px;
  letter-spacing: 0.06em;
}
.oslab-lab-b .panel {
  border-color: var(--oslab-lab-line-strong);
  border-radius: 0;
  background: var(--oslab-lab-panel);
  box-shadow: none;
}
.oslab-lab-b .panel-editor {
  border-color: var(--oslab-lab-ink);
  background: #10141a;
}
.oslab-lab-b .panel-heading,
.oslab-lab-b .editor-heading,
.oslab-lab-b .status-card h3 {
  font-family: var(--oslab-font-tech);
}
.oslab-lab-b .editor-heading,
.oslab-lab-b .status-card {
  background: var(--oslab-lab-panel);
}
.oslab-lab-b .editor-heading {
  border-bottom: 1px solid var(--oslab-lab-ink);
  color: var(--oslab-lab-ink);
}
.oslab-lab-b .panel-kicker {
  color: var(--oslab-lab-red);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}
.oslab-lab-b .status-card {
  border-bottom: 1px solid var(--oslab-lab-line-strong);
  padding: 14px;
}
.oslab-lab-b .status-card h3 { margin-bottom: 10px; color: var(--oslab-lab-ink); }
.oslab-lab-b .lab-margin-note {
  margin: 12px 0 0 24px;
  padding-left: 9px;
  border-left: 2px solid var(--oslab-lab-red);
  color: var(--oslab-lab-red);
  font-family: var(--oslab-font-display);
  font-size: 11px;
  line-height: 1.55;
  transform: rotate(-1deg);
}
.oslab-lab-b :deep(.arco-tabs-tab-active .arco-tabs-tab-title) { color: var(--oslab-lab-red); }
.oslab-lab-b :deep(.arco-tabs-ink-bar) { background: var(--oslab-lab-red); }
.oslab-lab-b :deep(.markdown-body h2) {
  padding-left: 8px;
  border-left: 2px solid var(--oslab-lab-red);
  font-family: var(--oslab-font-tech);
  font-size: 14px;
}
.oslab-lab-b :deep(.markdown-body h3) { font-family: var(--oslab-font-tech); }
.oslab-lab-b :deep(.arco-btn-primary) {
  border-radius: 0;
  background: var(--oslab-lab-ink);
}
.oslab-lab-b :deep(.arco-select-view) {
  border-radius: 0;
  background: var(--oslab-lab-panel);
}
.oslab-lab-b .node-tag {
  border: 1px solid var(--oslab-lab-line);
  border-radius: 0;
  background: transparent;
  color: var(--oslab-lab-ink-soft);
}
.oslab-lab-b .status-card,
.oslab-lab-b .remediation { border-radius: 0; }
.oslab-lab-b .status-card :deep(.arco-empty) { color: var(--oslab-lab-muted); }
```

- [ ] **Step 5: 响应式**

现有 1024 断点已把三栏变单列；保持。补：

```css
@media (max-width: 1024px) {
  .oslab-lab-b.experiment-workbench { padding: 10px; }
  .oslab-lab-b .workbench-title .title { font-size: 22px; }
}
```

- [ ] **Step 6: 运行契约**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=2048 node tests/workbench-layout-contracts.js
```

Expected: lab 4 条断言 PASS；整个文件 PASS（Task 3–6 已完成）。

- [ ] **Step 7: 浏览器检查**

1. 实验页内容为冷蓝蓝图底、方角墨线面板；全局 Header 与右下角 AI 保持 C+B。
2. 标题前有红色 `FIG. 2.1`（按真实标题提取），下方有 `C · 标签` 规格行。
3. 实验说明保留 Arco tabs；激活页签为红色下划线；Markdown h2 有红色左线。
4. 编辑器头/脚为浅色，代码区深色；提交按钮为墨色方角；Monaco 交互不变。
5. 评测结果空态为方角；提交/轮询/导师对话逻辑不变。
6. 1024 宽三栏变单列，顺序为说明 → 编辑器 → 结果；无横向溢出。

---

### Task 7: 响应式、可访问性与视觉校对

**Files:**
- Modify: `src/views/knowledgeGraph/KnowledgeGraphView.vue`
- Modify: `src/views/learning/CourseUnitView.vue`
- Modify: `src/views/learning/NodeLearningWorkspaceView.vue`
- Modify: `src/views/question/ViewQuestionView.vue`
- Modify: `src/styles/learning-theme.css`

**Interfaces:**
- Consumes: Task 2–6。
- Produces: 1440/1024/768 三档可用；焦点、对比度、reduced-motion 达标。

- [ ] **Step 1: 断点检查与修正**

在浏览器分别以 1440、1024、768 宽检查：

| 路由 | 期望 |
| --- | --- |
| `/graph` | 1440 三栏；1024 详情下沉、侧栏隐藏；768 单列、图谱最小高 420、注记条不遮节点 |
| 单元页 | 1440 左目录 + 内容 + 258 学习地图；1024 学习地图下沉；768 活动带横滚 |
| 节点页 | 1440 1fr + 300 导师；1024 导师折叠到内容底部；768 单列 |
| 实验页 | 1440 说明 + 编辑器/结果；1024 单列；768 代码区横向滚动 |

修正任何横向溢出：给 `min-width: 0`、`overflow-x: auto` 或调整列宽，不允许隐藏关键列。

- [ ] **Step 2: 可访问性**

- 注记条、图谱节点编号、活动编号不能是唯一状态信号；保留状态文字。
- 新增按钮/切换补 `aria-pressed` 或 `aria-current`；关系 chips 用 `<button type="button">`。
- 检查 `:focus-visible` 在蓝图底、荧光底、深色代码区可见；必要时在 `.oslab-lab-b` 内补 2px 红色焦点。
- `prefers-reduced-motion` 下关闭图谱/选中荧光条动画（已有规则，确认覆盖新元素）。

- [ ] **Step 3: 视觉校对**

对照 `design/directions/oslab-workspaces-cb.html` 与用户图谱截图，测量：

```js
// 在浏览器控制台或 evaluate_script 中
const rect = (selector) => {
  const el = document.querySelector(selector);
  return el ? el.getBoundingClientRect().toJSON() : null;
};
rect(".unit-content-grid");
rect(".unit-ledger");
rect(".workspace-grid");
rect(".tutor-panel");
rect(".content-row");
rect(".detail-card");
rect(".workbench-body");
```

重点：列宽、12/16px 间距、卡片内边距、标题字号、详情栏宽度。不得出现无解释的空白列。

---

### Task 8: 全量验收与交付记录

**Files:**
- Verify: 所有修改文件
- Modify: `docs/design/2026-10-24-cb-delivery.md`
- Create (optional): `docs/design/2026-10-24-cb-workbenches-graph-delivery.md`

**Interfaces:**
- Consumes: Task 1–7。
- Produces: 测试/构建/浏览器证据与已知限制。

- [ ] **Step 1: 全量 Node 测试**

```bash
cd 前端代码/osLab-frontend
for f in tests/*.js; do echo "=== $f"; NODE_OPTIONS=--max-old-space-size=2048 node "$f" || break; done
```

Expected: 全部通过（含新增 `workbench-layout-contracts.js`）。

- [ ] **Step 2: 串行构建**

```bash
cd 前端代码/osLab-frontend
NODE_OPTIONS=--max-old-space-size=4096 npm run build
```

Expected: `Build complete`，无 error；记录 warning 数量。

- [ ] **Step 3: Lint**

```bash
cd 前端代码/osLab-frontend
npx vue-cli-service lint --no-fix
```

Expected: 无新增 error；既有 warning 记录在交付文档。

- [ ] **Step 4: 浏览器验收**

在 `http://localhost:8081` 逐路由检查并截图（若内置浏览器可视表面不可用，记录 DOM/尺寸/computed style 结果与限制）：

- `/graph`：小图 11 节点全量编号；注记条；灰度边；详情结构；两按钮。
- 单元页：活动带、学习地图、真实计数。
- 节点页：hero、教材编号、导师 ③、聊天可用。
- 实验页：B 主题、FIG/规格行、编辑器浅头深体、评测/提交正常。
- 三档宽度无横向溢出；控制台无新增 error。

- [ ] **Step 5: 写交付记录**

在 `docs/design/2026-10-24-cb-delivery.md` 追加“第三轮：工作台与图谱”：

```markdown
## 第三轮：工作台与图谱（2026-10-24）

- 图谱：小图 ≤12 可见节点全量编号；大图回退选中 + 两关系；灰度边 + 钴蓝路径；选中荧光条；③ 注记条；NODE / INSPECTOR 详情。
- 单元页：活动卡片带 + 右侧学习地图；真实活动分组，不新增数据。
- 节点页：hero 卡、教材块编号与来源行、真实推荐批注、导师 ③ 焦点。
- 实验页：B 主题作用于页面内容；全局 C+B 壳层、AI 浮球、黄色 OS 标识不变。
- 测试：node tests/*.js 全绿；build 成功；lint 无新增 error。
- 已知限制：图谱 markPoint 荧光条在 SVG/Canvas 缩放下如出现偏移，以选中标签荧光底为主；教师教材图示不注入编号。
```

- [ ] **Step 6: 变更清单**

```bash
git status --short
git diff --stat
```

向用户汇报修改文件、测试/构建结果与未提交状态；等待用户决定是否提交。

---

## Self-Review

- **Spec coverage:** 图谱（Task 2–3）、单元（Task 4）、节点（Task 5）、实验（Task 6）、响应式/可访问性（Task 7）、验收/交付（Task 8）逐项覆盖。
- **Spec deviation recorded:** 小图全量编号 + 实验页 B 子主题均为用户 2026-10-24 的明确要求，写入交付记录；其余保持 C+B 规范。
- **Placeholder scan:** 未使用 TBD/TODO；页面任务给出实际类名、代码与断点；未写“适当处理”。
- **Type consistency:** `edgeTone` 在 Task 2 定义、Task 3 使用；`buildVisibleAnnotations` 在 Task 3 定义并测试；`activityGroups` 在 Task 4 定义并用于模板；`labFigure/labSpec` 在 Task 6 定义并使用；`AnnotationNote.tone` 在 Task 2 定义、Task 3 使用。
- **Fidelity:** 图谱节点尺寸/边/编号/注记条/详情顺序按用户截图；三个工作台的列宽、卡片内边距、编号与荧光笔按 `oslab-workspaces-cb.html`。
- **Resource constraint:** 所有测试/构建/lint 串行；heap cap 明确；不自动提交；不新增依赖。
