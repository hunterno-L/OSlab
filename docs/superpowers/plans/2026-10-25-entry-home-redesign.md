# OSLab 入口页与首页重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 OSLab 的认证入口改成平台落地页 + 登录弹层，并把登录后首页升级为"当前单元 + 学习画像 + 演算台 + 任务账本 + 目录入口"。

**Architecture:** 入口页使用 `UserLayout` 作为落地页外壳，`LandingView` 承担内容，`AuthDialog` 通过路由子视图承载登录/注册。动效由一个纯函数场景模型 `pagingScene.ts` 驱动单个 `PagingTheater.vue` Canvas，落地页用 ambient 模式、首页用 guided 模式。首页数据全部来自现有课程、画像、任务接口，每个区块独立加载与重试。

**Tech Stack:** Vue 3 + TypeScript + Vue Router 4 + Vuex 4 + Canvas 2D + 原生 CSS + Node 静态契约测试

**Spec:** `docs/superpowers/specs/2026-10-25-entry-home-redesign-design.md`

## Global Constraints

- 不新增任何 npm 依赖。
- 颜色、字体、圆角、动效只能使用 `learning-theme.css` 的 `--oslab-*` 令牌；组件内不写裸色值。
- 只使用 `--oslab-font-display` / `--oslab-font-ui` / `--oslab-font-mono`；等宽只用于 `nodeCode`、地址、页号、判题结果、真实计数。
- 不出现装饰性微文案：`OPERATING SYSTEMS`、`AUTH ENTRY`、`2026.10`、`01 / 06`、账号用途说明。
- 不复制参考站的视觉、文案、素材或代码；只复刻落地页结构与"实时演算 + 分节推进"的节奏。
- 不修改后端接口；学生登录后的默认首页仍是 `/graph`。
- 全局 Header 与右下角 AI 助手位置不变。
- 所有动效支持 `prefers-reduced-motion`。
- 不执行 `git commit`；每个任务的检查点只运行测试与 `git diff --stat`。只有用户明确要求时才提交。
- 控制资源占用：测试/lint/构建串行执行，不并行启动 dev server 与构建；验证完成后停止 dev server；剧场统一 24/30fps 上限。

---

### Task 1: 动效令牌与分页场景模型

**Files:**
- Create: `前端代码/osLab-frontend/src/components/theater/pagingScene.ts`
- Modify: `前端代码/osLab-frontend/src/styles/learning-theme.css:27-120`（在 `:root` 内追加）
- Modify: `前端代码/osLab-frontend/tests/design-system-contracts.js`
- Test: `前端代码/osLab-frontend/tests/paging-scene.js`

**Interfaces:**
- Produces:
  - `buildPagingScene({ sequence?: number[], frames?: number }): { sequence: number[], frames: number, accesses: Access[], faults: number, hits: number, totalWeight: number }`
  - `phaseAt(scene, weight: number): { access: Access, phase: Phase }`
  - `BEATS: { id: 1|2|3|4|5|6, label: string }[]`
  - `Access = { page: number, index: number, hit: boolean, evicted: number|null, frameIndex: number, framesBefore: (number|null)[], framesAfter: (number|null)[], phases: Phase[] }`
  - `Phase = { beat: 1|2|3|4|5|6|"HIT", label: string, weight: number }`

- [ ] **Step 1: 写失败测试**

Create `前端代码/osLab-frontend/tests/paging-scene.js`:

```js
// Run with: node tests/paging-scene.js
const assert = require("assert").strict;

(async () => {
  const { buildPagingScene, phaseAt, BEATS } = await import(
    "../src/components/theater/pagingScene.ts"
  );

  const scene = buildPagingScene();
  assert.equal(scene.frames, 3, "默认 3 个页框");
  assert.deepEqual(scene.sequence, [1, 2, 3, 1, 4], "默认访问序列");
  assert.equal(scene.faults, 4, "1,2,3,1,4 应有 4 次缺页");
  assert.equal(scene.hits, 1, "第二次访问 1 应命中");
  assert.equal(scene.accesses.length, 5, "应有 5 次访问");

  assert.deepEqual(scene.accesses[0].framesAfter, [1, null, null]);
  assert.deepEqual(scene.accesses[4].framesAfter, [1, 4, 3]);
  assert.equal(scene.accesses[4].evicted, 2, "第 5 次访问应淘汰第 2 页");
  assert.equal(scene.accesses[3].hit, true);
  assert.ok(
    !scene.accesses[3].phases.some((phase) => phase.beat === 4),
    "命中访问不能出现缺页中断拍"
  );

  assert.equal(BEATS.length, 6, "六拍定义必须完整");

  const again = buildPagingScene();
  assert.deepEqual(again, scene, "相同输入必须得到完全相同的场景");

  const last = phaseAt(scene, scene.totalWeight - 1);
  assert.equal(last.access.index, 4, "末尾权重应落在最后一次访问");
  assert.equal(last.phase.beat, 6, "末尾拍应为恢复执行");

  console.log("paging scene passed");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `node tests/paging-scene.js`
Expected: FAIL，提示找不到 `pagingScene.ts`。

- [ ] **Step 3: 实现场景模型**

Create `前端代码/osLab-frontend/src/components/theater/pagingScene.ts`:

```js
export const BEATS = [
  { id: 1, label: "请求页" },
  { id: 2, label: "TLB 未命中" },
  { id: 3, label: "页表遍历" },
  { id: 4, label: "缺页中断" },
  { id: 5, label: "换入 / 置换" },
  { id: 6, label: "恢复执行" },
];

const DEFAULT_SEQUENCE = [1, 2, 3, 1, 4];
const DEFAULT_FRAMES = 3;

export function buildPagingScene(options = {}) {
  const sequence = options.sequence ?? DEFAULT_SEQUENCE;
  const frames = options.frames ?? DEFAULT_FRAMES;

  if (!Array.isArray(sequence) || sequence.length === 0) {
    throw new Error("sequence must be a non-empty array");
  }
  if (!Number.isInteger(frames) || frames < 1) {
    throw new Error("frames must be a positive integer");
  }

  const slots = Array(frames).fill(null);
  const lastUsed = new Map();
  const accesses = [];
  let tick = 0;
  let faults = 0;
  let hits = 0;

  for (let index = 0; index < sequence.length; index += 1) {
    const page = sequence[index];
    tick += 1;
    const framesBefore = slots.slice();
    const frameIndex = slots.indexOf(page);
    const hit = frameIndex !== -1;
    const phases = [{ beat: 1, label: `请求第 ${page} 页`, weight: 1 }];

    if (hit) {
      phases.push({ beat: "HIT", label: "TLB 命中", weight: 1 });
      phases.push({ beat: 6, label: "恢复执行", weight: 1 });
      lastUsed.set(page, tick);
      hits += 1;
      accesses.push({
        page,
        index,
        hit: true,
        evicted: null,
        frameIndex,
        framesBefore,
        framesAfter: slots.slice(),
        phases,
      });
      continue;
    }

    phases.push({ beat: 2, label: "TLB 未命中", weight: 1 });
    phases.push({ beat: 3, label: "遍历页表", weight: 1 });

    let targetIndex = slots.indexOf(null);
    let evicted = null;

    if (targetIndex === -1) {
      targetIndex = 0;
      let oldest = Infinity;
      for (let i = 0; i < slots.length; i += 1) {
        const used = lastUsed.has(slots[i]) ? lastUsed.get(slots[i]) : -1;
        if (used < oldest) {
          oldest = used;
          targetIndex = i;
        }
      }
      evicted = slots[targetIndex];
      phases.push({ beat: 4, label: "缺页中断", weight: 1 });
      phases.push({
        beat: 5,
        label: `换出第 ${evicted} 页，换入第 ${page} 页`,
        weight: 2,
      });
    } else {
      phases.push({ beat: 4, label: "缺页中断", weight: 1 });
      phases.push({ beat: 5, label: `从磁盘换入第 ${page} 页`, weight: 2 });
    }

    slots[targetIndex] = page;
    lastUsed.set(page, tick);
    faults += 1;
    phases.push({ beat: 6, label: "恢复执行", weight: 1 });

    accesses.push({
      page,
      index,
      hit: false,
      evicted,
      frameIndex: targetIndex,
      framesBefore,
      framesAfter: slots.slice(),
      phases,
    });
  }

  const totalWeight = accesses.reduce(
    (sum, access) =>
      sum + access.phases.reduce((inner, phase) => inner + phase.weight, 0),
    0
  );

  return {
    sequence: sequence.slice(),
    frames,
    accesses,
    faults,
    hits,
    totalWeight,
  };
}

export function phaseAt(scene, weight) {
  const total = scene.totalWeight;
  let remaining = ((weight % total) + total) % total;

  for (const access of scene.accesses) {
    for (const phase of access.phases) {
      if (remaining < phase.weight) return { access, phase };
      remaining -= phase.weight;
    }
  }

  const access = scene.accesses[scene.accesses.length - 1];
  return { access, phase: access.phases[access.phases.length - 1] };
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `node tests/paging-scene.js`
Expected: PASS，输出 `paging scene passed`。

- [ ] **Step 5: 追加动效令牌与令牌测试**

在 `src/styles/learning-theme.css` 的 `:root` 内 `--oslab-shadow-overlay` 之后追加：

```css
  /* 入场与进度生长使用慢速；界面过渡沿用既有 fast/base */
  --oslab-duration-slow: 420ms;
```

在 `tests/design-system-contracts.js` 的令牌断言末尾追加：

```js
expectToken("oslab-ease", "cubic-bezier(0.2, 0.7, 0.2, 1)");
expectToken("oslab-duration-fast", "160ms");
expectToken("oslab-duration", "220ms");
expectToken("oslab-duration-slow", "420ms");
```

- [ ] **Step 6: 运行令牌测试**

Run: `node tests/design-system-contracts.js`
Expected: PASS。

- [ ] **Step 7: 检查点**

Run: `git diff --stat`
Expected: 仅 `learning-theme.css`、`tests/design-system-contracts.js`、`tests/paging-scene.js`、`pagingScene.ts` 四个文件。不要提交。

---

### Task 2: PagingTheater Canvas 组件

**Files:**
- Create: `前端代码/osLab-frontend/src/components/theater/PagingTheater.vue`
- Test: `前端代码/osLab-frontend/tests/landing-contracts.js`（先写剧场部分）

**Interfaces:**
- Consumes: `buildPagingScene`, `phaseAt`, `BEATS` from Task 1。
- Produces: `<PagingTheater mode="ambient|guided" :playing="boolean" :show-controls="boolean" label="页面置换 · LRU" @beat="(label: string) => void" />`
- CSS class contract: `.paging-theater`, `.paging-theater-canvas`, `.paging-theater-controls`, `.paging-theater-legend`, `.paging-theater-steps`。

- [ ] **Step 1: 写剧场契约测试**

Create `前端代码/osLab-frontend/tests/landing-contracts.js`，先只包含剧场与基础文件检查：

```js
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
has(theater, "aria-live=\"polite\"", "剧场必须播报当前节拍");
has(theater, "aria-hidden=\"true\"", "Canvas 本身必须对读屏隐藏");
has(theater, "Math.min(window.devicePixelRatio || 1, 2)", "DPR 必须封顶 2");
has(theater, "requestAnimationFrame", "剧场必须使用 requestAnimationFrame");
has(theater, "cancelAnimationFrame", "暂停时必须取消动画帧");
lacks(theater, "shadowBlur", "剧场不得使用大面积阴影模糊");

console.log("landing contracts passed");
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `node tests/landing-contracts.js`
Expected: FAIL，找不到 `PagingTheater.vue`。

- [ ] **Step 3: 实现组件状态与控制**

Create `前端代码/osLab-frontend/src/components/theater/PagingTheater.vue`。模板：

```vue
<template>
  <figure ref="hostRef" class="paging-theater" :class="[`is-${mode}`, { 'is-compact': compact }]">
    <div
      ref="stageRef"
      class="paging-theater-stage"
      role="img"
      :aria-label="`${label}：三个页框，访问序列 1、2、3、1、4`"
    >
      <canvas ref="canvasRef" class="paging-theater-canvas" aria-hidden="true" />
    </div>

    <div class="paging-theater-legend" aria-hidden="true">
      <span
        v-for="beat in BEATS"
        :key="beat.id"
        :class="{ 'is-active': activeBeat === beat.id }"
      >
        <b>{{ beat.id }}</b>{{ beat.label }}
      </span>
    </div>

    <p class="sr-only" aria-live="polite">{{ liveText }}</p>

    <div v-if="showControls" class="paging-theater-controls">
      <button type="button" :aria-label="isPlaying ? '暂停演示' : '播放演示'" @click="togglePlay">
        {{ isPlaying ? "暂停" : "播放" }}
      </button>
      <button type="button" aria-label="上一步" @click="step(-1)">上一步</button>
      <button type="button" aria-label="下一步" @click="step(1)">下一步</button>
      <button type="button" aria-label="重播演示" @click="replay">重播</button>
    </div>

    <ol v-if="reducedMotion" class="paging-theater-steps">
      <li v-for="access in scene.accesses" :key="access.index">
        第 {{ access.index + 1 }} 次请求第 {{ access.page }} 页：
        {{ access.hit ? "TLB 命中" : `缺页${access.evicted ? `，淘汰第 ${access.evicted} 页` : ""}` }}
      </li>
    </ol>
  </figure>
</template>
```

脚本骨架（完整实现播放、可见性、降级与绘制入口）：

```ts
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { BEATS, buildPagingScene, phaseAt } from "./pagingScene.ts";

const props = withDefaults(
  defineProps<{
    mode?: "ambient" | "guided";
    playing?: boolean;
    showControls?: boolean;
    label?: string;
  }>(),
  { mode: "ambient", playing: true, showControls: true, label: "页面置换 · LRU" }
);

const emit = defineEmits<{ beat: [label: string] }>();

const scene = buildPagingScene();
const weight = ref(0);
const isPlaying = ref(props.playing);
const inView = ref(false);
const compact = ref(false);
const reducedMotion = ref(false);
const liveText = ref("");
const hostRef = ref<HTMLElement | null>(null);
const stageRef = ref<HTMLElement | null>(null);
const canvasRef = ref<HTMLCanvasElement | null>(null);

const WEIGHT_MS = computed(() => (props.mode === "ambient" ? 560 : 460));
const frameInterval = computed(() => (props.mode === "ambient" ? 1000 / 24 : 1000 / 30));
const current = computed(() => phaseAt(scene, weight.value));
const activeBeat = computed(() =>
  current.value.phase.beat === "HIT" ? 2 : current.value.phase.beat
);
const liveLabel = computed(() => {
  const { access, phase } = current.value;
  return `第 ${access.index + 1} 次访问第 ${access.page} 页：${phase.label}`;
});

let raf = 0;
let lastTime = 0;
let lastFrame = 0;
let lastAnnounce = 0;
let resizeObserver: ResizeObserver | null = null;
let intersectionObserver: IntersectionObserver | null = null;

function canPlay() {
  return isPlaying.value && inView.value && !reducedMotion.value;
}

function start() {
  if (raf || !canPlay()) return;
  lastTime = performance.now();
  lastFrame = 0;
  raf = requestAnimationFrame(tick);
}

function stop() {
  if (!raf) return;
  cancelAnimationFrame(raf);
  raf = 0;
}

function tick(now: number) {
  if (!canPlay()) {
    raf = 0;
    return;
  }
  raf = requestAnimationFrame(tick);
  if (now - lastFrame < frameInterval.value) return;

  const delta = Math.min(now - lastTime, 100);
  lastTime = now;
  lastFrame = now;
  weight.value += delta / WEIGHT_MS.value;

  if (weight.value >= scene.totalWeight) {
    if (props.mode === "ambient") {
      weight.value %= scene.totalWeight;
    } else {
      weight.value = scene.totalWeight - 0.001;
      isPlaying.value = false;
    }
  }

  draw();
  if (!canPlay()) stop();
}

function togglePlay() {
  isPlaying.value = !isPlaying.value;
}

function step(direction: 1 | -1) {
  isPlaying.value = false;
  const next = weight.value + direction;
  weight.value = Math.min(Math.max(next, 0), scene.totalWeight - 0.001);
  draw();
}

function replay() {
  weight.value = 0;
  isPlaying.value = true;
}
```

在同一文件中补全：

- `draw()`：读取画布与 CSS 令牌，调用 `drawScene(ctx, current.value)`。
- `resize()`：用 `stageRef.value.clientWidth` 作为 CSS 宽度，按 `compact ? 640 : 1000` 的逻辑宽度计算高度，设置 canvas 像素尺寸；DPR 用 `Math.min(window.devicePixelRatio || 1, 2)`；容器宽度 <640 时用 1.5，并切换 `compact`。
- `drawScene` 的几何固定为 1000×420：

| 区域 | 坐标 |
| --- | --- |
| CPU | x=40, y=40, w=130, h=90 |
| TLB | x=40, y=160, w=130, h=34 |
| 页表 | x=210, y=40, w=250, h=300，6 行 |
| 页框 | x=520, y=40，3 张 140×100，间距 12 |
| 磁盘 | x=520, y=330, w=440, h=60 |
| 印章 | 中心 640, 250，文字"缺页中断"，-4° |

窄屏（容器 <640px）切换到 640×420 紧凑布局：

| 区域 | 坐标 |
| --- | --- |
| CPU | x=24, y=24, w=120, h=80 |
| TLB | x=24, y=120, w=120, h=30 |
| 页框 | x=190, y=24，3 张 120×90，间距 10 |
| 磁盘 | x=190, y=320, w=360, h=60 |
| 页表 | 隐藏 |
| 印章 | 中心 340, 210 |

绘制入口使用固定签名，避免模板里堆绘图逻辑：

```ts
type Phase = { beat: number | "HIT"; label: string; weight: number };
type Access = { page: number; hit: boolean; evicted: number | null; framesAfter: (number | null)[] };

function drawScene(ctx: CanvasRenderingContext2D, width: number, height: number, access: Access, phase: Phase) {
  ctx.clearRect(0, 0, width, height);
  drawGrid(ctx, width, height);
  drawCpu(ctx, phase);
  drawTlb(ctx, access, phase);
  if (!compact.value) drawPageTable(ctx, access, phase);
  drawFrames(ctx, access);
  drawDisk(ctx, access, phase);
  drawAddressLine(ctx, access, phase);
  if (phase.beat === 4) drawStamp(ctx);
}
```

`compact` 由 `resize()` 根据容器宽度 <640 得出；紧凑模式使用 640×420 逻辑尺寸，否则 1000×420。

- 颜色只从 CSS 变量读取：`--oslab-ink`、`--oslab-line`、`--oslab-primary`、`--oslab-highlight`、`--oslab-annotation`、`--oslab-panel`。
- 地址线使用 90° 折线：CPU → TLB → 页表 → 页框；命中走 CPU → TLB → 页框；`lineDashOffset` 按当前拍推进。
- 页表行显示页码、帧号、有效位；当前页行用荧光笔底。
- 页框卡片显示页码；当前访问目标页用 2px 钴蓝边线；空闲框用虚线。
- 磁盘抽屉显示 1–5 页卡片；换入/换出时对应卡片高亮。
- 缺页时画珊瑚色描边印章；不要每帧重绘阴影。

在 `onMounted` 中设置：

```ts
reducedMotion.value = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
resizeObserver = new ResizeObserver(() => resize());
resizeObserver.observe(hostRef.value!);
intersectionObserver = new IntersectionObserver(
  ([entry]) => {
    inView.value = entry.isIntersecting;
    canPlay() ? start() : stop();
  },
  { threshold: 0.15 }
);
intersectionObserver.observe(hostRef.value!);
document.addEventListener("visibilitychange", syncVisibility);
syncVisibility();
if (reducedMotion.value) {
  isPlaying.value = false;
  drawFinalFrame();
} else {
  start();
}
```

`syncVisibility()`：`document.hidden` 时 `stop()`，否则 `canPlay()` 时 `start()`。
`drawFinalFrame()`：`weight.value = scene.totalWeight - 0.001; draw();`。
`watch([isPlaying, reducedMotion], () => (canPlay() ? start() : stop()))`。
`watch(liveLabel, (label) => { const now = performance.now(); if (now - lastAnnounce < 800) return; lastAnnounce = now; liveText.value = label; emit("beat", label); })`。
`onBeforeUnmount`：`stop()`、断开两个 observer、移除 `visibilitychange`。

- [ ] **Step 4: 补组件样式**

在同一个 SFC 的 `<style scoped>` 中至少定义：

```css
.paging-theater {
  position: relative;
  margin: 0;
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container, 14px);
  box-shadow: var(--oslab-shadow-card);
}
.paging-theater-canvas { display: block; width: 100%; height: auto; aspect-ratio: 1000 / 420; }
.paging-theater.is-compact .paging-theater-canvas { aspect-ratio: 640 / 420; }
.paging-theater-legend { display: flex; flex-wrap: wrap; gap: 8px 16px; margin-top: 12px; color: var(--oslab-muted-strong); font-size: 12px; }
.paging-theater-legend b { margin-right: 4px; font-family: var(--oslab-font-mono); font-weight: 600; color: var(--oslab-primary); }
.paging-theater-legend .is-active { color: var(--oslab-ink); background: var(--oslab-highlight-soft); }
.paging-theater-controls { display: flex; gap: 8px; margin-top: 12px; }
.paging-theater-controls button { min-height: 32px; padding: 0 12px; border: 1px solid var(--oslab-line); border-radius: var(--oslab-radius-control, 9px); background: var(--oslab-panel); color: var(--oslab-ink); font: inherit; font-size: 13px; }
.paging-theater-controls button:hover { border-color: var(--oslab-primary); }
.paging-theater-controls button:focus-visible { outline: 2px solid var(--oslab-primary); outline-offset: 2px; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 640px) {
  .paging-theater { padding: 10px; }
  .paging-theater-legend { font-size: 11px; gap: 6px 10px; }
}
@media (prefers-reduced-motion: reduce) {
  .paging-theater * { transition: none !important; animation: none !important; }
}
```

- [ ] **Step 5: 运行契约测试**

Run: `node tests/landing-contracts.js`
Expected: PASS，输出 `landing contracts passed`。

- [ ] **Step 6: 类型与构建检查**

Run: `npx vue-cli-service lint --no-fix`
Expected: 无新增 error。

- [ ] **Step 7: 检查点**

Run: `git diff --stat`
Expected: 新增 `PagingTheater.vue` 与 `tests/landing-contracts.js`。不要提交。

---

### Task 3: 路由、认证弹层与落地页首屏

**Files:**
- Modify: `前端代码/osLab-frontend/src/router/routes.ts:48-58`、`前端代码/osLab-frontend/src/router/routes.ts:59-73`
- Modify: `前端代码/osLab-frontend/src/layouts/UserLayout.vue`（整体重写）
- Create: `前端代码/osLab-frontend/src/components/auth/AuthDialog.vue`
- Create: `前端代码/osLab-frontend/src/views/landing/LandingView.vue`
- Create: `前端代码/osLab-frontend/src/styles/auth-entry.css`
- Modify: `前端代码/osLab-frontend/src/views/user/UserLoginView.vue:8-9,117,143-146,217-220`
- Modify: `前端代码/osLab-frontend/src/views/user/UserRegisterView.vue:9,110,136`
- Modify: `前端代码/osLab-frontend/tests/ui-guideline-contracts.js:149-159`
- Delete: `前端代码/osLab-frontend/src/components/auth/AuthFlowCanvas.vue`
- Test: `前端代码/osLab-frontend/tests/landing-contracts.js`

**Interfaces:**
- Consumes: `PagingTheater` from Task 2。
- Produces:
  - 路由 `/welcome`（公开）。
  - `AuthDialog` 通过 `<router-view />` 承载 `UserLoginView` / `UserRegisterView`。
  - `LandingView` 暴露 header、hero、footer；Task 4 在 hero 与 footer 之间插入四个 section。
  - Header CTA 带 `data-auth-trigger`，关闭弹层后焦点回到它。

- [ ] **Step 1: 扩展落地页契约测试**

在 `tests/landing-contracts.js` 追加：

```js
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
for (const text of ["OPERATING SYSTEMS", "AUTH ENTRY", "2026.10", "01 / 06"]) {
  lacks(landing + dialog, text, "装饰性微文案必须删除");
}
has(landing, "沿着教材，把操作系统学明白", "落地页必须使用确认后的主标题");
has(landing, "PagingTheater", "落地页首屏必须使用分页剧场");
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `node tests/landing-contracts.js`
Expected: FAIL，找不到 `/welcome` 或 `LandingView.vue`。

- [ ] **Step 3: 修改路由**

在 `routes.ts` 的 `/user` 路由之前插入：

```ts
  {
    path: "/welcome",
    name: "平台介绍",
    component: UserLayout,
    meta: { hideInMenu: true },
  },
```

把 `/` 的 redirect 改为：

```ts
        redirect: () => {
          const role = store.state.user.loginUser?.userRole;
          return ROLE_HOME[role] || "/welcome";
        },
```

- [ ] **Step 4: 创建认证弹层**

Create `src/components/auth/AuthDialog.vue`。面板切换用 `route.path` 作 key 直接切换，不使用 `out-in` 过渡：隐藏标签页里过渡可能不结束，导致路由已变但面板不换。

```vue
<template>
  <Transition name="os-dialog" appear>
    <div class="os-auth-overlay" @click.self="close">
      <div
        ref="panelRef"
        class="os-auth-dialog"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="isRegister ? 'os-register-tab' : 'os-login-tab'"
        @keydown.tab="trapFocus"
      >
        <div class="os-auth-dialog-head">
          <div class="os-tabs" role="tablist" aria-label="账号入口">
            <button
              id="os-login-tab"
              type="button"
              class="os-tab"
              role="tab"
              aria-controls="os-auth-panel"
              :aria-selected="!isRegister"
              :tabindex="!isRegister ? 0 : -1"
              @click="switchMode('login')"
              @keydown.right.prevent="focusMode('register')"
              @keydown.left.prevent="focusMode('login')"
            >登录</button>
            <button
              id="os-register-tab"
              type="button"
              class="os-tab"
              role="tab"
              aria-controls="os-auth-panel"
              :aria-selected="isRegister"
              :tabindex="isRegister ? 0 : -1"
              @click="switchMode('register')"
              @keydown.right.prevent="focusMode('register')"
              @keydown.left.prevent="focusMode('login')"
            >注册</button>
            <span class="os-tab-rule" :class="{ 'is-register': isRegister }" aria-hidden="true" />
          </div>
          <button
            type="button"
            class="os-auth-close"
            :disabled="authBusy"
            aria-label="关闭登录窗口"
            @click="close"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.path" />
        </router-view>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import type { Ref } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const isRegister = computed(() => route.path === "/user/register");
const authBusy = inject<Ref<boolean>>("oslabAuthBusy", ref(false));
const panelRef = ref<HTMLElement | null>(null);
const previousOverflow = document.body.style.overflow;

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

function focusables(): HTMLElement[] {
  return panelRef.value ? Array.from(panelRef.value.querySelectorAll<HTMLElement>(FOCUSABLE)) : [];
}

function focusInitial() {
  const account = document.getElementById("oslab-account-input");
  if (account instanceof HTMLElement) {
    account.focus();
    return;
  }
  focusables()[0]?.focus();
}

function trapFocus(event: KeyboardEvent) {
  const list = focusables();
  if (!list.length) return;
  const first = list[0];
  const last = list[list.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

async function close() {
  if (authBusy.value) return;
  await router.replace("/welcome");
  await nextTick();
  document.querySelector<HTMLElement>("[data-auth-trigger]")?.focus();
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") {
    event.preventDefault();
    close();
  }
}

async function focusMode(mode: "login" | "register") {
  if (authBusy.value) return;
  switchMode(mode);
  await nextTick();
  document.getElementById(mode === "login" ? "os-login-tab" : "os-register-tab")?.focus();
}

function switchMode(mode: "login" | "register") {
  if (authBusy.value) return;
  const target = mode === "register" ? "/user/register" : "/user/login";
  if (route.path === target) return;
  const query: Record<string, string> = {};
  if (typeof route.query.redirect === "string" && route.query.redirect) {
    query.redirect = route.query.redirect;
  }
  router.push({ path: target, query });
}

onMounted(() => {
  document.body.style.overflow = "hidden";
  document.addEventListener("keydown", onKeydown);
  nextTick(focusInitial);
});

onBeforeUnmount(() => {
  document.body.style.overflow = previousOverflow;
  document.removeEventListener("keydown", onKeydown);
});
</script>

<style scoped>
.os-auth-overlay {
  position: fixed;
  inset: 0;
  z-index: 900;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(32, 36, 43, 0.32);
}
.os-auth-dialog {
  width: min(420px, 100%);
  max-height: 90vh;
  overflow-y: auto;
  padding: 24px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container, 14px);
  box-shadow: var(--oslab-shadow-overlay);
}
.os-auth-dialog-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.os-auth-close {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 0;
  background: none;
  color: var(--oslab-muted-strong);
  cursor: pointer;
}
.os-auth-close svg { width: 18px; height: 18px; }
.os-auth-close:focus-visible { outline: 2px solid var(--oslab-primary); outline-offset: 2px; }
.os-dialog-enter-active, .os-dialog-leave-active { transition: opacity var(--oslab-duration-fast) var(--oslab-ease); }
.os-dialog-enter-from, .os-dialog-leave-to { opacity: 0; }
@media (max-width: 640px) {
  .os-auth-overlay { align-items: end; padding: 0; }
  .os-auth-dialog {
    width: 100%;
    max-height: 92vh;
    border-radius: 14px 14px 0 0;
    padding: 20px 20px calc(20px + env(safe-area-inset-bottom));
  }
}
@media (prefers-reduced-motion: reduce) {
  .os-dialog-enter-active, .os-dialog-leave-active { transition: none; }
}
</style>
```

- [ ] **Step 5: 重写 UserLayout 外壳**

Replace `src/layouts/UserLayout.vue` with:

```vue
<template>
  <div id="userLayout">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <main id="main-content">
      <LandingView />
    </main>
    <AuthDialog v-if="isAuthRoute" />
  </div>
</template>

<script setup lang="ts">
import { computed, provide, ref } from "vue";
import { useRoute } from "vue-router";
import LandingView from "@/views/landing/LandingView.vue";
import AuthDialog from "@/components/auth/AuthDialog.vue";
import "@/styles/auth-entry.css";

const route = useRoute();
const isAuthRoute = computed(
  () => route.path === "/user/login" || route.path === "/user/register"
);

const sharedAccount = ref("");
provide("oslabSharedAccount", sharedAccount);

const authBusy = ref(false);
provide("oslabAuthBusy", authBusy);
</script>

<style>
#userLayout {
  min-height: 100vh;
  background: var(--oslab-canvas);
  color: var(--oslab-ink);
  font-family: var(--oslab-font-ui);
}
#userLayout,
#userLayout *,
#userLayout *::before,
#userLayout *::after {
  box-sizing: border-box;
}
</style>
```

- [ ] **Step 6: 创建落地页首屏**

Create `src/views/landing/LandingView.vue`：

```vue
<template>
  <div class="landing">
    <header class="landing-header">
      <a class="landing-brand" href="/welcome" @click.prevent="goWelcome">
        <img
          src="@/assets/oj-logo.svg"
          alt=""
          width="31"
          height="31"
          class="landing-brand-mark"
        />
        <span class="landing-brand-name">OSLab</span>
      </a>
      <button type="button" class="landing-cta" data-auth-trigger @click="onCta">
        <span>{{ ctaLabel }}</span>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path d="M5 12h14" /><path d="m13 6 6 6-6 6" />
        </svg>
      </button>
    </header>

    <div class="landing-body">
      <section class="landing-hero" aria-labelledby="landing-hero-title">
        <div class="landing-hero-copy">
          <h1 id="landing-hero-title">沿着教材，把操作系统学明白</h1>
          <p>OSLab 用教材式学习单元组织五个章节，用知识图谱和学习画像记录你真正掌握了什么。</p>
          <div class="landing-hero-actions">
            <button type="button" class="landing-primary" data-auth-trigger @click="onCta">
              {{ loggedIn ? "进入学习" : "登录开始学习" }}
            </button>
            <a class="landing-secondary" href="#mainline">查看学习方式</a>
          </div>
        </div>
        <PagingTheater class="landing-hero-theater" mode="ambient" label="页面置换 · LRU" />
      </section>
    </div>

    <footer class="landing-footer">
      <span>OSLab 操作系统学习平台</span>
      <nav aria-label="页脚">
        <a href="/welcome" @click.prevent="goWelcome">平台简介</a>
        <a :href="loggedIn ? '/graph' : '/user/login'">课程入口</a>
        <a :href="loggedIn ? '/resources' : '/user/login'">学习资源</a>
        <a href="/user/login">登录</a>
      </nav>
      <span>© 2026 OSLab</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";
import PagingTheater from "@/components/theater/PagingTheater.vue";
import { ROLE_HOME } from "@/navigation/appNavigation";
import ACCESS_ENUM from "@/access/accessEnum";

const router = useRouter();
const store = useStore();
const loggedIn = computed(() => {
  const role = store.state.user?.loginUser?.userRole;
  return Boolean(role && role !== ACCESS_ENUM.NOT_LOGIN);
});
const ctaLabel = computed(() => (loggedIn.value ? "进入学习" : "登录 / 注册"));

function onCta() {
  if (loggedIn.value) {
    const role = store.state.user.loginUser?.userRole;
    router.push(ROLE_HOME[role] || "/graph");
    return;
  }
  router.push("/user/login");
}

function goWelcome() {
  if (router.currentRoute.value.path !== "/welcome") {
    router.push("/welcome");
    return;
  }
  const behavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ? "auto"
    : "smooth";
  window.scrollTo({ top: 0, behavior });
}
</script>

<style scoped>
.landing {
  --landing-max: 1200px;
  min-height: 100vh;
  background: var(--oslab-canvas);
  color: var(--oslab-ink);
}
.landing-header {
  width: min(var(--landing-max), calc(100% - 96px));
  margin: 0 auto;
  padding: 24px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.landing-brand { display: inline-flex; align-items: center; gap: 12px; color: inherit; text-decoration: none; }
.landing-brand-mark { width: 31px; height: 31px; display: block; }
.landing-brand-name { font-family: var(--oslab-font-display); font-size: 22px; font-weight: 700; }
.landing-cta {
  display: inline-flex; align-items: center; gap: 10px;
  min-height: 40px; padding: 0 18px;
  border: 1px solid var(--oslab-ink); border-radius: var(--oslab-radius-control, 9px);
  background: var(--oslab-ink); color: var(--oslab-on-primary);
  font: inherit; font-size: 14px; cursor: pointer;
}
.landing-cta svg { width: 16px; height: 16px; }
.landing-cta:focus-visible, .landing-primary:focus-visible, .landing-secondary:focus-visible {
  outline: 2px solid var(--oslab-primary); outline-offset: 2px;
}
.landing-body { width: min(var(--landing-max), calc(100% - 96px)); margin: 0 auto; }
.landing-hero { padding: 72px 0 96px; }
.landing-hero-copy { max-width: 780px; margin: 0 auto; text-align: center; }
.landing-hero h1 {
  margin: 0;
  font-family: var(--oslab-font-display);
  font-size: clamp(44px, 5.2vw, 72px);
  font-weight: 700;
  line-height: 1.08;
  background: linear-gradient(transparent 62%, var(--oslab-highlight) 62%, var(--oslab-highlight) 94%, transparent 94%);
}
.landing-hero p { max-width: 640px; margin: 24px auto 0; font-size: 16px; line-height: 1.8; color: var(--oslab-ink-soft); }
.landing-hero-actions { display: flex; justify-content: center; gap: 12px; margin-top: 32px; }
.landing-primary {
  min-height: 44px; padding: 0 22px; border: 0; border-radius: var(--oslab-radius-control, 9px);
  background: var(--oslab-primary); color: var(--oslab-on-primary); font: inherit; font-size: 15px; cursor: pointer;
}
.landing-secondary {
  min-height: 44px; display: inline-flex; align-items: center; padding: 0 18px;
  border: 1px solid var(--oslab-line-strong); border-radius: var(--oslab-radius-control, 9px);
  color: var(--oslab-ink); text-decoration: none; font-size: 15px;
}
.landing-hero-theater { margin-top: 56px; }
.landing-footer {
  width: min(var(--landing-max), calc(100% - 96px));
  margin: 0 auto;
  padding: 28px 0 36px;
  display: flex; align-items: center; justify-content: space-between; gap: 24px;
  border-top: 1px solid var(--oslab-line);
  color: var(--oslab-muted-strong); font-size: 13px;
}
.landing-footer nav { display: flex; gap: 20px; }
.landing-footer a { color: inherit; text-decoration: none; }
.landing-footer a:hover { color: var(--oslab-primary); }
@media (max-width: 768px) {
  .landing-header, .landing-body, .landing-footer { width: calc(100% - 40px); }
  .landing-hero { padding: 40px 0 64px; }
  .landing-hero-actions { flex-direction: column; align-items: stretch; }
  .landing-primary, .landing-secondary { justify-content: center; }
  .landing-hero-theater { margin-top: 32px; }
  .landing-footer { flex-direction: column; align-items: flex-start; }
}
</style>
```

- [ ] **Step 7: 创建认证表单样式**

Create `src/styles/auth-entry.css`，把旧 `UserLayout` 中 `#userLayout .os-tabs`、`.os-tab`、`.os-tab-rule`、`.os-auth-panel`、`.os-field`、`.os-submit`、`.os-eye`、`.os-form-extra`、`.os-remember`、`.os-field-error`、`.os-error`、`.os-confirm-row` 的规则迁移过来，并删除已移除的类。

至少覆盖：

```css
#userLayout .os-tabs { position: relative; display: flex; gap: 40px; width: 128px; }
#userLayout .os-tab { border: 0; background: none; padding: 0 0 12px; font: inherit; font-size: 14px; color: var(--oslab-muted-strong); cursor: pointer; }
#userLayout .os-tab[aria-selected="true"] { color: var(--oslab-ink); font-weight: 600; }
#userLayout .os-tab-rule { position: absolute; bottom: 0; left: 0; width: 28px; height: 2px; background: var(--oslab-primary); transition: transform var(--oslab-duration-fast) var(--oslab-ease); }
#userLayout .os-tab-rule.is-register { transform: translateX(69px); }
#userLayout .os-auth-panel h2 { margin: 20px 0 0; font-size: 26px; line-height: 1.4; font-weight: 500; }
#userLayout .os-field { display: block; margin: 22px 0 0; }
#userLayout .os-field-label { display: block; margin-bottom: 10px; font-size: 12px; color: var(--oslab-ink-soft); }
#userLayout input[type="text"],
#userLayout input[type="password"] {
  width: 100%; height: 48px; padding: 0 15px;
  border: 1px solid var(--oslab-line); border-radius: var(--oslab-radius-control, 9px);
  background: var(--oslab-panel); color: var(--oslab-ink); font: inherit; font-size: 14px;
}
#userLayout input:focus-visible { outline: 2px solid var(--oslab-primary); outline-offset: 1px; border-color: var(--oslab-primary); }
#userLayout .os-input-wrap { position: relative; display: block; }
#userLayout .os-password { padding-right: 46px; }
#userLayout .os-eye { position: absolute; right: 5px; top: 5px; width: 38px; height: 38px; display: grid; place-items: center; border: 0; background: none; color: var(--oslab-muted-strong); cursor: pointer; }
#userLayout .os-eye svg { width: 17px; height: 17px; }
#userLayout .os-form-extra { margin: 14px 0 18px; font-size: 12px; }
#userLayout .os-remember { display: flex; align-items: center; gap: 8px; }
#userLayout .os-remember input { width: 14px; height: 14px; accent-color: var(--oslab-primary); }
#userLayout .os-submit {
  width: 100%; height: 50px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 20px; border: 0; border-radius: var(--oslab-radius-control, 9px);
  background: var(--oslab-ink); color: var(--oslab-on-primary); font: inherit; font-size: 14px; cursor: pointer;
}
#userLayout .os-submit:hover { background: var(--oslab-ink-soft); }
#userLayout .os-submit:disabled { cursor: wait; opacity: 0.72; }
#userLayout .os-submit svg { width: 17px; height: 17px; }
#userLayout .os-field-error, #userLayout .os-error { display: block; margin-top: 6px; color: var(--oslab-weak); font-size: 12px; line-height: 1.5; }
#userLayout .os-confirm-row { display: grid; grid-template-rows: 0fr; opacity: 0; transition: grid-template-rows var(--oslab-duration-fast) var(--oslab-ease), opacity var(--oslab-duration-fast) var(--oslab-ease); }
#userLayout .os-confirm-row.is-expanded { grid-template-rows: 1fr; opacity: 1; }
#userLayout .os-confirm-inner { overflow: hidden; min-height: 0; }
#userLayout .os-register-hint { display: block; margin-top: 8px; color: var(--oslab-muted-strong); font-size: 12px; line-height: 1.6; }
@media (max-width: 640px) {
  #userLayout input[type="text"], #userLayout input[type="password"] { font-size: 16px; }
}
```

- [ ] **Step 8: 清理登录/注册视图**

`UserLoginView.vue`：
- 删除 `<p class="os-form-subtitle">…</p>`（第 9 行）。
- 删除 `<p class="os-account-note">…</p>`（第 117 行）。
- 删除 `inject("oslabAdvanceFlow")` 与 `advanceFlow` 常量（第 143-146 行）。
- 删除成功分支中的 `advanceFlow();` 与 `await new Promise((resolve) => setTimeout(resolve, 160));`（第 218-219 行）。

`UserRegisterView.vue`：
- 删除 `<p class="os-form-subtitle">…</p>`（第 9 行）。
- 删除 `<p class="os-account-note">…</p>`（第 136 行）。
- 保留 `<span class="os-register-hint">账号至少 4 位，密码至少 8 位。</span>`。

- [ ] **Step 9: 删除旧动画组件并更新既有 UI 契约**

Run: `rm 前端代码/osLab-frontend/src/components/auth/AuthFlowCanvas.vue`
Expected: 无引用残留；`grep -r "AuthFlowCanvas" src` 无输出。

修改 `tests/ui-guideline-contracts.js` 的"Tab 键盘导航"段（原第 149-159 行）：页签已迁移到 `AuthDialog.vue`，logo 已迁移到 `LandingView.vue`。

```js
const authDialog = readSrc("components/auth/AuthDialog.vue");
for (const needle of [
  "@keydown.right.prevent",
  "@keydown.left.prevent",
  ':tabindex="!isRegister ? 0 : -1"',
  "focusMode",
]) {
  expectIncludes(authDialog, needle, "登录/注册页签键盘导航");
}
const landingView = readSrc("views/landing/LandingView.vue");
expectIncludes(landingView, 'width="31"', "登录页 logo 必须有宽高");
```

- [ ] **Step 10: 运行契约测试与 lint**

Run:
```bash
node tests/landing-contracts.js
node tests/ui-guideline-contracts.js
npx vue-cli-service lint --no-fix
```
Expected: 测试 PASS，lint 无新增 error。

- [ ] **Step 11: 手工验证认证流**

Run:
```bash
npm run serve
```

逐项验证：
1. `/` → `/welcome`，首屏显示标题和剧场。
2. 点击登录 → 弹层；`Esc` / 遮罩 / 关闭按钮均回到 `/welcome`。
3. 直接访问 `/graph` → 跳 `/user/login?redirect=/graph`，弹层打开；登录后回到 `/graph`。
4. 登录/注册页签切换保留账号、清空密码；`Tab` 不逃出弹层。
5. 窄屏 390px：弹层为底部抽屉，输入框字号 16px。

- [ ] **Step 12: 检查点**

Run: `git diff --stat`
Expected: 只包含 Task 3 列出的文件。不要提交。

---

### Task 4: 落地页四个介绍区块

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/landing/LandingView.vue`
- Create: `前端代码/osLab-frontend/src/components/landing/MainlineDemo.vue`
- Create: `前端代码/osLab-frontend/src/components/landing/GraphProfileDemo.vue`
- Create: `前端代码/osLab-frontend/src/components/landing/PracticeTabsDemo.vue`
- Create: `前端代码/osLab-frontend/src/components/landing/EvidenceLedger.vue`
- Modify: `前端代码/osLab-frontend/tests/landing-contracts.js`

**Interfaces:**
- Consumes: `LandingView` 的 hero/footer 布局。
- Produces: 四个 section，id 分别为 `mainline`、`graph`、`practice`、`evidence`；header 锚点导航指向它们。

- [ ] **Step 1: 扩展契约测试**

在 `tests/landing-contracts.js` 追加：

```js
for (const anchor of ["mainline", "graph", "practice", "evidence"]) {
  has(landing, `id="${anchor}"`, `落地页必须有 #${anchor} 区块`);
}
has(landing, "MainlineDemo", "必须使用学习主线演示");
has(landing, "GraphProfileDemo", "必须使用图谱/画像演示");
has(landing, "PracticeTabsDemo", "必须使用三种练习演示");
has(landing, "EvidenceLedger", "必须使用证据说明");
const practice = src("components/landing/PracticeTabsDemo.vue");
has(practice, 'role="tablist"', "练习页签必须可访问");
has(practice, 'aria-selected', "练习页签必须有选中态");
lacks(landing, "累计", "落地页不得出现虚构累计数据");
lacks(landing, "余万", "落地页不得出现虚构规模数据");
```

- [ ] **Step 2: 实现 MainlineDemo**

Create `src/components/landing/MainlineDemo.vue`，核心数据与结构：

```vue
<template>
  <div class="demo-panel">
    <div class="demo-switch" role="group" aria-label="主线与补救演示">
      <button type="button" :aria-pressed="mode === 'main'" @click="mode = 'main'">主线</button>
      <button type="button" :aria-pressed="mode === 'review'" @click="mode = 'review'">补救</button>
    </div>
    <ol class="chapter-rail">
      <li v-for="(chapter, index) in chapters" :key="chapter" :class="{ 'is-current': index === 0 }">
        <span class="chapter-index">{{ index + 1 }}</span>
        <span>{{ chapter }}</span>
      </li>
    </ol>
    <div class="unit-card">
      <p class="unit-path">第一章 · 计算机系统概述</p>
      <h3>当前学习单元</h3>
      <p class="unit-goal">目标：完成本章第一个学习单元。</p>
      <p>从第一个已发布单元开始，依次完成阅读、例子、演示与测评。</p>
      <span class="unit-cta">继续学习 →</span>
    </div>
    <div v-if="mode === 'review'" class="remedy-card">
      <strong>补充单元</strong>
      <p>发现薄弱点：页面置换。完成补充内容后回到主线位置。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

const mode = ref<"main" | "review">("main");
const chapters = [
  "计算机系统概述",
  "进程与线程",
  "内存管理",
  "文件管理",
  "输入/输出（I/O）管理",
];
</script>
```

样式重点：`chapter-rail` 为纵向细线列表；`is-current` 使用荧光笔底；`remedy-card` 使用珊瑚色左边线与浅底；不显示虚构完成率。

- [ ] **Step 3: 实现 GraphProfileDemo**

Create `src/components/landing/GraphProfileDemo.vue`，节点与关系使用真实名称：

```vue
<template>
  <div class="demo-panel">
    <span class="demo-badge">示例</span>
    <div class="demo-switch" role="group" aria-label="图谱视图">
      <button type="button" :aria-pressed="mode === 'public'" @click="mode = 'public'">公共图谱</button>
      <button type="button" :aria-pressed="mode === 'profile'" @click="mode = 'profile'">我的画像</button>
    </div>
    <svg viewBox="0 0 480 300" role="img" aria-label="知识图谱与个人学习画像示意">
      <g v-for="edge in edges" :key="edge.id">
        <line
          :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
          class="edge"
        />
        <text :x="edge.lx" :y="edge.ly" text-anchor="middle" class="edge-label">{{ edge.label }}</text>
      </g>
      <g
        v-for="node in nodes"
        :key="node.id"
        class="node"
        :class="[`is-${node.type}`, mode === 'profile' ? `is-${node.status}` : '']"
      >
        <rect :x="node.x" :y="node.y" width="104" height="40" rx="8" />
        <text :x="node.x + 52" :y="node.y + 18" text-anchor="middle">{{ node.label }}</text>
        <text v-if="mode === 'profile'" :x="node.x + 52" :y="node.y + 33" text-anchor="middle" class="node-status">
          {{ statusLabel[node.status] }}
        </text>
      </g>
    </svg>
    <p v-if="mode === 'profile'" class="demo-note">
      「页表」暂无实践证据，显示为未学习，不按薄弱处理。
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

type Status = "mastered" | "review" | "weak" | "unseen";
const mode = ref<"public" | "profile">("public");
const statusLabel: Record<Status, string> = {
  mastered: "已掌握",
  review: "待巩固",
  weak: "薄弱",
  unseen: "未学习",
};
const nodes = [
  { id: "lru", label: "LRU", type: "algorithm", x: 188, y: 56, status: "mastered" as Status },
  { id: "fifo", label: "FIFO", type: "algorithm", x: 40, y: 140, status: "review" as Status },
  { id: "opt", label: "OPT", type: "algorithm", x: 336, y: 140, status: "weak" as Status },
  { id: "fault", label: "缺页中断", type: "mechanism", x: 188, y: 140, status: "mastered" as Status },
  { id: "locality", label: "局部性原理", type: "concept", x: 188, y: 224, status: "mastered" as Status },
  { id: "ptable", label: "页表", type: "mechanism", x: 336, y: 224, status: "unseen" as Status },
];
const edges = [
  { id: "1", x1: 240, y1: 96, x2: 240, y2: 140, label: "包含", lx: 252, ly: 122 },
  { id: "2", x1: 240, y1: 180, x2: 240, y2: 224, label: "依据", lx: 252, ly: 206 },
  { id: "3", x1: 144, y1: 76, x2: 188, y2: 76, label: "对比", lx: 166, ly: 66 },
  { id: "4", x1: 292, y1: 76, x2: 336, y2: 160, label: "对比", lx: 326, ly: 112 },
  { id: "5", x1: 292, y1: 244, x2: 336, y2: 244, label: "相关", lx: 314, ly: 234 },
];
</script>
```

样式重点：节点默认白底、1px `--oslab-line`；公共态按 `node.type` 使用现有图谱类别浅底（concept→`--oslab-graph-category-concept`，algorithm→`--oslab-graph-category-algorithm`，mechanism→`--oslab-graph-category-function`）；画像态在类别底上改状态色边线并在节点内显示文字；`unseen` 用虚线边；边线 1px `--oslab-line-strong`；不使用粒子。`.demo-badge` 用 12px UI 字体、`--oslab-panel-soft` 底、`--oslab-line` 边线，放在面板左上角。

- [ ] **Step 4: 实现 PracticeTabsDemo**

Create `src/components/landing/PracticeTabsDemo.vue`，三个页签：

```vue
<template>
  <div class="demo-panel">
    <div class="practice-tabs" role="tablist" aria-label="练习入口">
      <button
        v-for="(tab, index) in tabs"
        :id="`practice-tab-${tab.id}`"
        :key="tab.id"
        type="button"
        role="tab"
        :aria-selected="active === tab.id"
        :tabindex="active === tab.id ? 0 : -1"
        :aria-controls="`practice-panel-${tab.id}`"
        @click="active = tab.id"
        @keydown.right.prevent="focusTab(index + 1)"
        @keydown.left.prevent="focusTab(index - 1)"
      >{{ tab.label }}</button>
    </div>

    <div v-if="active === 'question'" id="practice-panel-question" role="tabpanel" aria-labelledby="practice-tab-question" class="practice-panel">
      <p class="practice-question">缺页中断发生时，负责选择淘汰页面并完成调入的是谁？</p>
      <button
        v-for="option in options"
        :key="option"
        type="button"
        class="practice-option"
        :class="{ 'is-selected': selected === option }"
        @click="selected = option"
      >{{ option }}</button>
    </div>

    <div v-else-if="active === 'code'" id="practice-panel-code" role="tabpanel" aria-labelledby="practice-tab-code" class="practice-panel">
      <pre class="practice-code"><code>int lru_hit(int page) {
  for (int i = 0; i &lt; FRAMES; i++)
    if (frames[i] == page) return i;
  return -1;
}</code></pre>
      <ul class="practice-result">
        <li>编译通过</li>
        <li>样例通过</li>
        <li>等待提交</li>
      </ul>
    </div>

    <div v-else ref="simRef" id="practice-panel-sim" role="tabpanel" aria-labelledby="practice-tab-sim" class="practice-panel">
      <div :key="simKey" class="gantt" :class="{ 'is-playing': simPlaying }" role="img" aria-label="CPU 调度甘特图示意">
        <span class="gantt-block p1" style="--span: 3">P1</span>
        <span class="gantt-block p2" style="--span: 2">P2</span>
        <span class="gantt-block p3" style="--span: 4">P3</span>
      </div>
      <button type="button" class="gantt-replay" @click="replaySim">重播</button>
      <p class="demo-note">FCFS / SJF / RR / MLFQ 等调度算法可直接运行。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from "vue";

const tabs = [
  { id: "question", label: "题目练习" },
  { id: "code", label: "编程实验" },
  { id: "sim", label: "算法模拟" },
] as const;
const active = ref<(typeof tabs)[number]["id"]>("question");
const selected = ref("");
const options = ["进程调度程序", "页表硬件", "操作系统缺页处理程序", "编译程序"];

const simRef = ref<HTMLElement | null>(null);
const simPlaying = ref(false);
const simKey = ref(0);
let observer: IntersectionObserver | null = null;

async function watchSimVisibility() {
  await nextTick();
  if (!simRef.value) return;
  observer?.disconnect();
  observer = new IntersectionObserver(
    ([entry]) => {
      if (!entry.isIntersecting) return;
      simPlaying.value = true;
      observer?.disconnect();
      observer = null;
    },
    { threshold: 0.4 }
  );
  observer.observe(simRef.value);
}

function replaySim() {
  simPlaying.value = false;
  simKey.value += 1;
  requestAnimationFrame(() => {
    simPlaying.value = true;
  });
}

async function focusTab(index: number) {
  const next = tabs[(index + tabs.length) % tabs.length];
  active.value = next.id;
  await nextTick();
  document.getElementById(`practice-tab-${next.id}`)?.focus();
}

watch(active, (tab) => {
  if (tab === "sim") void watchSimVisibility();
});

onBeforeUnmount(() => observer?.disconnect());
</script>
```

样式重点：页签下划线 2px 钴蓝；面板最小高度 260px；代码块使用 `--oslab-panel-soft`、等宽字体、1px 边线；甘特块使用钴蓝/荧光笔/珊瑚三色，仅在 `.gantt.is-playing` 时执行一次 `gantt-grow` 宽度动画（三个块分别延迟 0/120/240ms）；`prefers-reduced-motion` 下取消动画。

- [ ] **Step 5: 实现 EvidenceLedger**

Create `src/components/landing/EvidenceLedger.vue`：

```vue
<template>
  <ul class="evidence-ledger">
    <li v-for="row in rows" :key="row.name">
      <strong>{{ row.name }}</strong>
      <span>{{ row.meaning }}</span>
      <em>{{ row.source }}</em>
    </li>
  </ul>
</template>

<script setup lang="ts">
const rows = [
  { name: "活动进度", meaning: "是否阅读、运行、提交或完成指定活动", source: "真实活动行为" },
  { name: "单元进度", meaning: "必需活动完成比例与当前位置", source: "活动进度聚合" },
  { name: "达标情况", meaning: "单元检查点是否通过、建议复习什么", source: "服务端测评或判题" },
  { name: "节点掌握", meaning: "累积的概念与实践证据", source: "学习事件与 BKT" },
];
</script>
```

样式重点：四行用 1px 横线分隔；三列网格 `minmax(120px, 0.6fr) 1.4fr 0.8fr`；移动端改为单列；不显示任何数字。

- [ ] **Step 6: 组合进 LandingView**

在 `LandingView.vue` 的 header 中、CTA 之前加入：

```vue
      <nav class="landing-nav" aria-label="平台介绍">
        <a href="#mainline">学习主线</a>
        <a href="#graph">知识图谱</a>
        <a href="#practice">三种练习</a>
        <a href="#evidence">学习证据</a>
      </nav>
```

在 `</section>`（hero 结束）之后、`</div>`（landing-body 结束）之前加入：

```vue
      <section id="mainline" class="landing-section">
        <div class="landing-section-copy">
          <h2>不是一张知识列表，而是一条学习主线</h2>
          <p>五个章节按教材顺序编排，每章拆成有目标的学习单元。第一次学习从首个已发布单元开始；回来时接着上次的位置；遇到薄弱点，系统插入补充单元，完成后回到主线。</p>
        </div>
        <MainlineDemo />
      </section>

      <section id="graph" class="landing-section">
        <div class="landing-section-copy">
          <h2>同一张图谱，看见自己的掌握</h2>
          <p>公共图谱保存概念、算法、机制与实现及其关系；每个学生在此基础上叠加概念与实践证据、最近变化和下一步建议。没有证据的节点显示为未学习，不会被误判为薄弱。</p>
        </div>
        <GraphProfileDemo />
      </section>

      <section id="practice" class="landing-section">
        <div class="landing-section-copy">
          <h2>理解之后，随时练</h2>
          <p>题目练习、算法模拟和编程实验都能独立进入，也能被学习单元引用。演示覆盖 CPU 调度、内存分配、页面置换和磁盘调度。</p>
        </div>
        <PracticeTabsDemo />
      </section>

      <section id="evidence" class="landing-section">
        <div class="landing-section-copy">
          <h2>进度不是打勾，是证据</h2>
          <p>活动进度、单元进度、达标情况和节点掌握分开记录；一次真实作答只计一次证据，历史变化可以回放。</p>
        </div>
        <EvidenceLedger />
      </section>
```

并 import 四个组件。

补充样式：

```css
.landing-nav { display: flex; gap: 24px; font-size: 14px; }
.landing-nav a { color: var(--oslab-ink-soft); text-decoration: none; }
.landing-nav a:hover { color: var(--oslab-primary); }
.landing-section {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  gap: 64px;
  align-items: start;
  padding: 96px 0;
  border-top: 1px solid var(--oslab-line);
}
.landing-section-copy h2 {
  margin: 0;
  font-family: var(--oslab-font-display);
  font-size: clamp(30px, 3vw, 40px);
  line-height: 1.2;
}
.landing-section-copy p { margin: 20px 0 0; font-size: 16px; line-height: 1.8; color: var(--oslab-ink-soft); }
.demo-panel {
  padding: 20px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container, 14px);
  box-shadow: var(--oslab-shadow-card);
}
.demo-switch, .practice-tabs { display: flex; gap: 8px; }
.demo-switch button, .practice-tabs button {
  min-height: 32px; padding: 0 12px; border: 1px solid var(--oslab-line);
  border-radius: 999px; background: var(--oslab-panel); color: var(--oslab-muted-strong);
  font: inherit; font-size: 13px; cursor: pointer;
}
.demo-switch button[aria-pressed="true"], .practice-tabs button[aria-selected="true"] {
  background: var(--oslab-highlight-soft); border-color: var(--oslab-highlight); color: var(--oslab-ink);
}
.demo-note { margin: 12px 0 0; color: var(--oslab-muted-strong); font-size: 13px; line-height: 1.6; }
@media (max-width: 900px) {
  .landing-nav { display: none; }
}
@media (max-width: 900px) {
  .landing-section { grid-template-columns: 1fr; gap: 32px; padding: 64px 0; }
}
@media (prefers-reduced-motion: reduce) {
  .demo-panel * { animation: none !important; transition: none !important; }
}
```

- [ ] **Step 7: 运行测试与 lint**

Run:
```bash
node tests/landing-contracts.js
npx vue-cli-service lint --no-fix
```
Expected: PASS，无新增 error。

- [ ] **Step 8: 手工检查**

Run: `npm run serve`，访问 `/welcome`：
- 四个锚点都能滚动到对应区块。
- 三个练习页签可用键盘切换。
- 900px 以下隐藏顶部锚点，区块单列。
- 页面无横向溢出。
- 减少动态时甘特图不动、剧场显示静态帧。

- [ ] **Step 9: 检查点**

Run: `git diff --stat`
Expected: 五个落地页组件/视图 + 契约测试。不要提交。

---

### Task 5: 登录后首页

**Files:**
- Modify: `前端代码/osLab-frontend/src/views/HomeView.vue`（整体重写）
- Create: `前端代码/osLab-frontend/src/components/home/ProfileCard.vue`
- Create: `前端代码/osLab-frontend/src/components/home/UnitTimeline.vue`
- Create: `前端代码/osLab-frontend/src/components/home/EntryLedger.vue`
- Create: `前端代码/osLab-frontend/tests/home-contracts.js`

**Interfaces:**
- Consumes: `PagingTheater`（guided）、`personalGraph`、`masteryRecommend`、`listCourses`、`getCourseResume`、`getCourseOutline`、`getCourseUnit`、`unitPath`、`getTaskSummary`。
- Produces: 首页四个数据区块 + 演算台 + 任务账本 + 目录入口。

- [ ] **Step 1: 写首页契约测试**

Create `前端代码/osLab-frontend/tests/home-contracts.js`：

```js
// Run with: node tests/home-contracts.js
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const src = (relative) => fs.readFileSync(path.join(root, "src", relative), "utf8");
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
for (const route of ["/assessments", "/experiments", "/experiments/simulation", "/resources"]) {
  has(entries, route, `目录入口必须包含 ${route}`);
}

const routes = src("router/routes.ts");
const start = routes.indexOf('path: "/home"');
const end = routes.indexOf('path: "/assessments"');
const homeRoute = routes.slice(start, end);
lacks(homeRoute, "pageTitle", "首页路由不应设置 pageTitle");

console.log("home contracts passed");
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `node tests/home-contracts.js`
Expected: FAIL。

- [ ] **Step 3: 实现 ProfileCard**

Create `src/components/home/ProfileCard.vue`。计算与模板骨架：

```ts
import { computed } from "vue";

const props = defineProps<{
  profile: {
    statusCounts?: Record<"GREEN" | "YELLOW" | "RED" | "GRAY", number>;
    evidenceCount?: number;
    recommendedNodeName?: string | null;
  } | null;
  recommend: string | null;
  state: "loading" | "ready" | "empty" | "error";
}>();
const emit = defineEmits<{ retry: [] }>();

const rows = computed(() => {
  const counts = props.profile?.statusCounts;
  return [
    { key: "GREEN", label: "已掌握", value: counts?.GREEN ?? 0, color: "var(--oslab-mastered)" },
    { key: "YELLOW", label: "待巩固", value: counts?.YELLOW ?? 0, color: "var(--oslab-review)" },
    { key: "RED", label: "薄弱", value: counts?.RED ?? 0, color: "var(--oslab-weak)" },
    { key: "GRAY", label: "未学习", value: counts?.GRAY ?? 0, color: "var(--oslab-unseen)" },
  ];
});
const total = computed(() => rows.value.reduce((sum, row) => sum + row.value, 0));
const hasRemedy = computed(() =>
  rows.value.some((row) => (row.key === "YELLOW" || row.key === "RED") && row.value > 0)
);
const nodeHref = computed(() =>
  props.recommend ? `/knowledge/node/${encodeURIComponent(props.recommend)}` : ""
);
```

```vue
<section class="profile-card" aria-labelledby="profile-title">
  <h2 id="profile-title">我的学习画像</h2>
  <div v-if="state === 'loading'" class="profile-skeleton" aria-busy="true">正在加载学习画像…</div>
  <div v-else-if="state === 'error'" class="profile-error">
    暂时无法加载
    <button type="button" @click="emit('retry')">重试</button>
  </div>
  <template v-else>
    <div v-if="total > 0" class="profile-bar" aria-hidden="true">
      <span
        v-for="row in rows"
        :key="row.key"
        :style="{ width: `${(row.value / total) * 100}%`, background: row.color }"
      ></span>
    </div>
    <p v-else class="profile-empty">暂无学习证据</p>
    <ul class="profile-counts">
      <li v-for="row in rows" :key="row.key">
        <i :style="{ background: row.color }" aria-hidden="true"></i>
        {{ row.label }}<b>{{ row.value }}</b>
      </li>
    </ul>
    <p v-if="(profile?.evidenceCount ?? 0) > 0" class="profile-evidence">
      有效证据 {{ profile?.evidenceCount }} 条
    </p>
    <router-link v-if="recommend" class="profile-next" :to="nodeHref">
      下一步：{{ recommend }}
    </router-link>
    <div class="profile-links">
      <router-link to="/records">查看学习记录</router-link>
      <router-link v-if="hasRemedy" to="/records?view=review">去巩固</router-link>
    </div>
  </template>
</section>
```

样式重点：卡片白底、1px 边线、14px 圆角、卡片阴影；分布条高 8px、圆角 999、无动画（首次可 600ms 生长，减少动态下取消）；四类计数 2×2 网格，数字用等宽 `tabular-nums`；`i` 为 8px 状态圆点。

- [ ] **Step 4: 实现 UnitTimeline**

Create `src/components/home/UnitTimeline.vue`。计算与模板骨架：

```ts
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { unitPath } from "@/api/course";

const props = defineProps<{
  unit: {
    unitCode: string;
    courseCode: string;
    currentActivityCode?: string;
    activities: Array<{
      activityCode: string;
      title: string;
      activityType: string;
      completed?: boolean;
      sortOrder?: number;
    }>;
  } | null;
  courseCode: string;
}>();

const LABELS: Record<string, string> = {
  READING: "阅读",
  WORKED_EXAMPLE: "跟随例子",
  DEMO: "互动演示",
  PRACTICE: "练习",
  ASSESSMENT: "测评",
  EXPERIMENT: "编程实验",
  SUMMARY: "小结",
};

const narrow = ref(false);
let media: MediaQueryList | null = null;
function syncNarrow() {
  narrow.value = media?.matches ?? false;
}
onMounted(() => {
  media = window.matchMedia("(max-width: 640px)");
  syncNarrow();
  media.addEventListener("change", syncNarrow);
});
onBeforeUnmount(() => media?.removeEventListener("change", syncNarrow));

const activities = computed(() =>
  [...(props.unit?.activities ?? [])].sort(
    (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
  )
);
const currentCode = computed(
  () =>
    props.unit?.currentActivityCode ||
    activities.value.find((activity) => !activity.completed)?.activityCode ||
    ""
);
const visible = computed(() => {
  if (!narrow.value) return activities.value;
  const index = activities.value.findIndex(
    (activity) => activity.activityCode === currentCode.value
  );
  if (index < 0) return activities.value.slice(0, 3);
  return activities.value.slice(
    Math.max(0, index - 1),
    Math.min(activities.value.length, index + 2)
  );
});
function href(activityCode: string) {
  if (!props.unit) return "/graph";
  return `${unitPath(props.courseCode, props.unit.unitCode)}?activity=${encodeURIComponent(activityCode)}`;
}
```

```vue
<section class="unit-timeline" aria-labelledby="unit-timeline-title">
  <h2 id="unit-timeline-title">本单元进度</h2>
  <ol class="timeline-track">
    <li
      v-for="activity in visible"
      :key="activity.activityCode"
      :class="{
        'is-current': activity.activityCode === currentCode,
        'is-done': activity.completed,
      }"
    >
      <router-link :to="href(activity.activityCode)">
        <span class="activity-dot" aria-hidden="true"></span>
        <span class="activity-label">{{ LABELS[activity.activityType] || activity.title }}</span>
      </router-link>
    </li>
  </ol>
  <router-link
    v-if="narrow && activities.length > visible.length"
    class="timeline-all"
    :to="unit?.unitCode ? unitPath(courseCode, unit.unitCode) : '/graph'"
  >查看全部活动</router-link>
</section>
```

样式重点：横向节点 + 1px 连接线；当前节点荧光笔底、钴蓝点；已完成节点显示勾；窄屏只渲染三个节点。

- [ ] **Step 5: 实现 EntryLedger**

Create `src/components/home/EntryLedger.vue`：

```ts
const entries = [
  { label: "课程学习", description: "图谱、单元与当前进度", path: "/graph" },
  { label: "学习任务", description: "待办、批阅反馈与提交记录", path: "/tasks", studentOnly: true },
  { label: "题目练习", description: "按知识点筛选、作答与复习", path: "/assessments" },
  { label: "编程实验", description: "写代码、提交判题、查看记录", path: "/experiments" },
  { label: "算法模拟", description: "CPU 调度、内存分配、页面置换、磁盘调度", path: "/experiments/simulation" },
  { label: "学习资源", description: "课程资料与参考链接", path: "/resources" },
];
```

- 根据 `isStudent` 过滤 `studentOnly`。
- 每行 `<router-link>`，1px 横线，hover 背景 `--oslab-panel-soft`，右侧箭头。
- 不显示图标、序号或假数据。

- [ ] **Step 6: 重写 HomeView**

保留问候与角色判断，加入以下逻辑（实际代码）：

```ts
import { computed, onMounted, ref } from "vue";
import { useStore } from "vuex";
import { getTaskSummary } from "@/api/tasks";
import { personalGraph, masteryRecommend } from "@/api/learning";
import type { PersonalGraphVO } from "@/api/learning";
import {
  listCourses,
  getCourseResume,
  getCourseOutline,
  getCourseUnit,
  unitPath,
} from "@/api/course";
import type { CourseSummary, CourseOutline, CourseUnit } from "@/api/course";
import PagingTheater from "@/components/theater/PagingTheater.vue";
import ProfileCard from "@/components/home/ProfileCard.vue";
import UnitTimeline from "@/components/home/UnitTimeline.vue";
import EntryLedger from "@/components/home/EntryLedger.vue";

type LoadState = "loading" | "ready" | "empty" | "error";
type Resume = { unitCode?: string; activityCode?: string; reason: string };

const store = useStore();
const displayName = computed(() => store.state.user?.loginUser?.userName || "同学");
const isStudent = computed(() => store.state.user?.loginUser?.userRole === "user");
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) return "早上好";
  if (hour < 18) return "下午好";
  return "晚上好";
});

const course = ref<CourseSummary | null>(null);
const outline = ref<CourseOutline | null>(null);
const resume = ref<Resume | null>(null);
const unit = ref<CourseUnit | null>(null);
const unitState = ref<LoadState>("loading");

const profile = ref<PersonalGraphVO | null>(null);
const recommend = ref<string | null>(null);
const profileState = ref<LoadState>("loading");

const tasks = ref<Record<string, number>>({});
const tasksState = ref<LoadState>("loading");

function firstUnitCode(data: CourseOutline | null): string | null {
  if (!data) return null;
  for (const section of data.sections) {
    if (section.units?.length) return section.units[0].unitCode;
    const nested = firstUnitCode({ ...data, sections: section.children || [] });
    if (nested) return nested;
  }
  return null;
}

function findSectionTitle(data: CourseOutline | null, sectionCode?: string): string {
  if (!data || !sectionCode) return "";
  for (const section of data.sections) {
    if (section.sectionCode === sectionCode) {
      return [section.textbookNumber, section.title].filter(Boolean).join(" ");
    }
    const nested = findSectionTitle({ ...data, sections: section.children || [] }, sectionCode);
    if (nested) return nested;
  }
  return "";
}

async function loadUnit() {
  unitState.value = "loading";
  try {
    const courses = await listCourses();
    course.value = courses[0] || null;
    if (!course.value) {
      unitState.value = "empty";
      return;
    }
    outline.value = await getCourseOutline(course.value.courseCode);
    resume.value = await getCourseResume(course.value.courseCode);
    const code = resume.value.unitCode || firstUnitCode(outline.value);
    if (!code) {
      unitState.value = "empty";
      return;
    }
    unit.value = await getCourseUnit(code);
    unitState.value = "ready";
  } catch {
    unitState.value = "error";
  }
}

async function loadProfile() {
  profileState.value = "loading";
  try {
    const res = await personalGraph();
    if (res.data.code !== 0) throw new Error(res.data.message);
    profile.value = res.data.data;
    if (profile.value?.recommendedNodeName) {
      recommend.value = profile.value.recommendedNodeName;
    } else {
      const rec = await masteryRecommend();
      recommend.value = rec.data.code === 0 ? rec.data.data?.[0]?.nodeName || null : null;
    }
    profileState.value = "ready";
  } catch {
    profileState.value = "error";
  }
}

async function loadTasks() {
  if (!isStudent.value) return;
  tasksState.value = "loading";
  try {
    const res = await getTaskSummary();
    if (res.data.code !== 0) throw new Error(res.data.message);
    tasks.value = res.data.data || {};
    tasksState.value = "ready";
  } catch {
    tasksState.value = "error";
  }
}

const sectionTitle = computed(() =>
  findSectionTitle(outline.value, unit.value?.sectionCode)
);

const currentActivityCode = computed(() => {
  if (resume.value?.activityCode) return resume.value.activityCode;
  if (unit.value?.currentActivityCode) return unit.value.currentActivityCode;
  return unit.value?.activities.find((activity) => !activity.completed)?.activityCode;
});

const continueHref = computed(() => {
  if (!course.value || !unit.value) return "/graph";
  if (resume.value?.reason === "COURSE_COMPLETED") return "/graph";
  const base = unitPath(course.value.courseCode, unit.value.unitCode);
  return currentActivityCode.value
    ? `${base}?activity=${encodeURIComponent(currentActivityCode.value)}`
    : base;
});

const courseCompleted = computed(() => resume.value?.reason === "COURSE_COMPLETED");

onMounted(() => {
  void loadUnit();
  void loadProfile();
  void loadTasks();
});
```

模板结构：

```vue
<template>
  <div class="home-view">
    <section class="home-hero" aria-labelledby="home-greeting">
      <div class="home-current">
        <p class="home-greeting">{{ greeting }}，{{ displayName }}</p>
        <h1 id="home-greeting">
          {{ course?.title || "操作系统" }}
          <span v-if="sectionTitle" class="home-section">{{ sectionTitle }}</span>
        </h1>

        <div v-if="unitState === 'loading'" class="home-placeholder" aria-busy="true">正在加载当前单元…</div>
        <div v-else-if="unitState === 'error'" class="home-placeholder">
          暂时无法加载当前单元
          <button type="button" @click="loadUnit">重试</button>
        </div>
        <div v-else-if="unitState === 'empty'" class="home-placeholder">
          暂时没有可继续的学习单元
          <router-link to="/graph">进入课程学习</router-link>
        </div>
        <template v-else-if="unit">
          <h2 class="home-unit-title">{{ unit?.title }}</h2>
          <p v-if="unit?.objectives?.[0]" class="home-unit-goal">目标：{{ unit?.objectives?.[0] }}</p>
          <div class="home-progress" role="progressbar" :aria-valuenow="unit?.progress?.percent ?? 0" aria-valuemin="0" aria-valuemax="100">
            <span :style="{ width: `${unit?.progress?.percent ?? 0}%` }"></span>
          </div>
          <p class="home-progress-label">{{ unit?.progress?.completedCount ?? 0 }} / {{ unit?.progress?.requiredCount ?? 0 }} 个必做活动</p>
          <div class="home-actions">
            <router-link class="home-primary" :to="continueHref">
              {{ courseCompleted ? "复习课程" : "继续学习" }}
            </router-link>
            <router-link class="home-secondary" to="/graph">查看课程图谱</router-link>
          </div>
        </template>
      </div>

      <ProfileCard
        :profile="profile"
        :recommend="recommend"
        :state="profileState"
        @retry="loadProfile"
      />
    </section>

    <UnitTimeline v-if="unit" :unit="unit" :course-code="course?.courseCode || ''" />

    <section class="home-theater" aria-labelledby="home-theater-title">
      <div class="home-theater-copy">
        <h2 id="home-theater-title">演算台 · 页面置换 LRU</h2>
        <p>改变访问序列，观察缺页与淘汰。</p>
        <router-link to="/experiments/simulation">打开算法模拟</router-link>
      </div>
      <PagingTheater mode="guided" />
    </section>

    <section v-if="isStudent" class="home-tasks" aria-labelledby="home-tasks-title">
      <h2 id="home-tasks-title">学习任务</h2>
      <div v-if="tasksState === 'error'" class="home-placeholder">
        暂时无法加载学习任务
        <button type="button" @click="loadTasks">重试</button>
      </div>
      <router-link v-else to="/tasks" class="home-task-ledger">
        <span><b>{{ tasks.pending || 0 }}</b>待接收</span>
        <span><b>{{ tasks.inProgress || 0 }}</b>进行中</span>
        <span><b>{{ tasks.submitted || 0 }}</b>待批阅</span>
        <span><b>{{ tasks.reviewed || 0 }}</b>已批阅</span>
      </router-link>
    </section>

    <EntryLedger :is-student="isStudent" />
  </div>
</template>
```

- [ ] **Step 7: 首页样式**

在 `HomeView.vue` 的 `<style scoped>` 中至少定义：

```css
.home-view { width: min(1280px, 100%); margin: 0 auto; padding: 8px 0 40px; }
.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(320px, 5fr);
  gap: 24px;
  align-items: start;
  padding: 8px 0 32px;
}
.home-greeting { margin: 0 0 12px; color: var(--oslab-muted-strong); font-size: 14px; }
.home-view h1 {
  margin: 0;
  font-family: var(--oslab-font-display);
  font-size: 32px;
  line-height: 1.25;
}
.home-section { display: block; margin-top: 6px; font-family: var(--oslab-font-ui); font-size: 15px; color: var(--oslab-muted-strong); }
.home-unit-title { margin: 24px 0 0; font-size: 22px; line-height: 1.4; }
.home-unit-goal { margin: 8px 0 0; font-size: 14px; line-height: 1.7; color: var(--oslab-ink-soft); }
.home-progress { height: 8px; margin-top: 18px; background: var(--oslab-panel-muted); border-radius: 999px; overflow: hidden; }
.home-progress span { display: block; height: 100%; background: linear-gradient(90deg, var(--oslab-highlight), var(--oslab-highlight-strong)); transition: width var(--oslab-duration-slow) var(--oslab-ease); }
.home-progress-label { margin: 8px 0 0; color: var(--oslab-muted-strong); font-size: 12px; }
.home-actions { display: flex; gap: 12px; margin-top: 20px; }
.home-primary, .home-secondary { min-height: 40px; display: inline-flex; align-items: center; padding: 0 18px; border-radius: var(--oslab-radius-control, 9px); font-size: 14px; text-decoration: none; }
.home-primary { background: var(--oslab-primary); color: var(--oslab-on-primary); }
.home-secondary { background: var(--oslab-panel); color: var(--oslab-ink); border: 1px solid var(--oslab-line-strong); }
.home-placeholder { margin-top: 16px; padding: 16px; border: 1px dashed var(--oslab-line-strong); border-radius: var(--oslab-radius-card, 12px); color: var(--oslab-muted-strong); font-size: 14px; }
.home-placeholder button, .home-placeholder a { margin-left: 8px; border: 0; background: none; color: var(--oslab-primary); font: inherit; cursor: pointer; text-decoration: none; }
.home-theater { display: grid; grid-template-columns: minmax(0, 4fr) minmax(0, 8fr); gap: 24px; align-items: start; padding: 32px 0; border-top: 1px solid var(--oslab-line); }
.home-theater-copy h2 { margin: 0; font-size: 18px; }
.home-theater-copy p { margin: 10px 0 0; color: var(--oslab-muted-strong); font-size: 14px; line-height: 1.7; }
.home-theater-copy a { display: inline-block; margin-top: 14px; color: var(--oslab-primary); font-size: 14px; text-decoration: none; }
.home-tasks { padding: 28px 0; border-top: 1px solid var(--oslab-line); }
.home-tasks h2 { margin: 0 0 16px; font-size: 18px; }
.home-task-ledger { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; padding: 16px 0; color: inherit; text-decoration: none; }
.home-task-ledger span { display: flex; flex-direction: column; gap: 4px; color: var(--oslab-muted-strong); font-size: 13px; }
.home-task-ledger b { font-family: var(--oslab-font-mono); font-size: 28px; color: var(--oslab-ink); }
@media (max-width: 1100px) {
  .home-hero { grid-template-columns: 1fr; }
  .home-theater { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .home-task-ledger { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .home-actions { flex-direction: column; align-items: stretch; }
  .home-primary, .home-secondary { justify-content: center; }
}
```

- [ ] **Step 8: 运行测试与 lint**

Run:
```bash
node tests/home-contracts.js
npx vue-cli-service lint --no-fix
```
Expected: PASS，无新增 error。

- [ ] **Step 9: 手工验证首页**

Run: `npm run serve`，登录后访问 `/home`：

首页数据需要后端（默认 8121）与数据库已启动；若当前没有运行，先请用户用现有 `start-oslab.ps1` 启动，不要在本任务里并行拉起数据库、后端与前端。

- 当前单元来自真实课程；点击继续学习进入对应单元与活动。
- 画像计数与 `/graph` 学习状态一致；无证据显示"暂无学习证据"。
- 任务数值来自 `/learning/tasks/summary`。
- 六个入口可点击；非学生不显示"学习任务"。
- 断网时各区块独立显示重试，不出现假数据。
- 1440 / 1024 / 768 / 390 无横向溢出。

- [ ] **Step 10: 检查点**

Run: `git diff --stat`
Expected: 首页与三个 home 组件 + 契约测试。不要提交。

---

### Task 6: 全量验收

**Files:**
- 无新增文件。

- [ ] **Step 1: 运行全部契约测试**

Run:
```bash
node tests/paging-scene.js
node tests/landing-contracts.js
node tests/home-contracts.js
node tests/design-system-contracts.js
node tests/ui-guideline-contracts.js
```
Expected: 全部输出 passed，无 assertion error。

- [ ] **Step 2: 运行既有测试**

Run: `node tests/course-semantics.js && node tests/knowledge-graph-render.js && node tests/learning-integration-contracts.js && node tests/member-package-files.js && node tests/node-workbench-cache.js && node tests/simulation-algorithms.js && node tests/simulation-player.js && node tests/user-identity.js && node tests/workbench-layout-contracts.js`
Expected: 全部通过。

- [ ] **Step 3: Lint**

Run: `npx vue-cli-service lint --no-fix`
Expected: 无新增 error。

- [ ] **Step 4: 生产构建**

Run: `NODE_OPTIONS=--max-old-space-size=3584 npm run build`
Expected: 构建成功；`dist/` 生成；无新增 warning 级别错误。

- [ ] **Step 5: 人工验收清单**

逐项确认：
- [ ] `/` 未登录 → `/welcome`；`/welcome` header 显示"登录 / 注册"。
- [ ] 登录弹层三种关闭方式可用，焦点归还触发按钮。
- [ ] `/graph` 深链 → 登录弹层 → 登录后回到 `/graph`。
- [ ] 注册页签、记住账号、密码显隐、校验错误全部正常。
- [ ] 落地页四个锚点、三个练习页签、图谱/画像切换可用。
- [ ] 剧场可暂停、单步、重播；离屏与切后台停止。
- [ ] `prefers-reduced-motion` 下剧场景静态帧与文字步骤，页面无动画。
- [ ] 首页当前单元、画像、任务、入口均来自真实接口。
- [ ] 首页断网时显示区块级重试，不显示假数据。
- [ ] 1440 / 1024 / 768 / 390 四个宽度无横向溢出、无遮挡、无不可读文字。
- [ ] 键盘可完成：登录、页签切换、关闭弹层、所有目录入口。

- [ ] **Step 6: 截图留档**

Run: `npm run serve`，在浏览器中分别截取：
- `/welcome` 1440 宽全页
- `/welcome` 390 宽首屏
- `/home` 1440 宽全页
- `/home` 768 宽
- 登录弹层打开态

如果应用内浏览器不可用，请用户在本地打开并截图；不要声称未验证的视觉结果。

- [ ] **Step 7: 最终检查点**

Run: `git status --short`
Expected: 只包含本次改动的文件；没有未跟踪的临时截图或日志进入 `src/`。不要提交，等待用户确认。
