<template>
  <div
    class="knowledge-graph-container"
    role="img"
    :aria-label="`知识图谱：${nodes.length} 个节点，${links.length} 条关系。点击节点可在右侧查看详情。`"
  >
    <div
      ref="chart"
      class="knowledge-graph-chart"
      :style="chartHeight ? { height: chartHeight } : undefined"
    ></div>
  </div>
</template>

<script>
// 按需引入：仅注册知识图谱用到的 graph 系列、tooltip 组件与 canvas 渲染器，
// 避免把 ECharts 全量图表（约 1MB）打进图谱路由的分块。
import * as echarts from "echarts/core";
import { GraphChart } from "echarts/charts";
import { TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([GraphChart, TooltipComponent, CanvasRenderer]);

// 样式更新不重启力布局，缩放和选中保留当前坐标。
// 视野缩放/居中一律使用 ECharts 公开的 graphRoam（zoom + dx/dy），
// 不直接访问 graph view 的私有字段。
echarts.registerAction(
  {
    type: "oslabGraphStyle",
    event: "oslabGraphStyle",
    update: "updateVisual",
  },
  (payload, model) => {
    model.eachComponent({ mainType: "series", query: payload }, (series) => {
      const nodes = series.getData();
      const links = series.getEdgeData();
      nodes.each((index) =>
        nodes.getItemModel(index).mergeOption(payload.nodes[index])
      );
      links.each((index) =>
        links.getItemModel(index).mergeOption(payload.links[index])
      );
    });
  }
);

// Native force handles animation, cooling and drag reheating. Add rectangular
// collision projection because its point repulsion alone cannot protect cards.
echarts.registerLayout(
  echarts.PRIORITY.VISUAL.POST_CHART_LAYOUT + 1,
  (model) => {
    model.eachSeriesByType("graph", (series) => {
      const solver = series.forceLayout;
      if (series.id !== "oslab-knowledge-graph" || !solver) return;
      if (solver.__oslabWrapped) return;
      solver.__oslabWrapped = true;
      const data = series.getData();
      const graph = series.getGraph();
      const fixed = new Set();
      const syncEdges = () => {
        graph.eachEdge((edge) => {
          const a = edge.node1.getLayout();
          const b = edge.node2.getLayout();
          const points = [a.slice(), b.slice()];
          const curve = Number(edge.getModel().get("lineStyle.curveness")) || 0;
          if (curve)
            points.push([
              (a[0] + b[0]) / 2 - (a[1] - b[1]) * curve,
              (a[1] + b[1]) / 2 - (b[0] - a[0]) * curve,
            ]);
          edge.setLayout(points);
        });
      };
      const restore = () => {
        data.each((index) => {
          const saved = series.__oslabSettledPoints?.get(data.getId(index));
          const point = data.getItemLayout(index);
          if (saved && point) {
            point[0] = saved[0];
            point[1] = saved[1];
          }
        });
        syncEdges();
      };
      // resize runs the native layout stage again; restore the settled geometry
      // instead of nudging every node and restarting it on a viewport change.
      if (series.__oslabForceSettled) restore();
      const boxes = data.mapArray((index) => {
        const size = data.getItemModel(index).get("symbolSize");
        return { index, width: Number(size[0]), height: Number(size[1]) };
      });
      const anchors = data.mapArray((index) => {
        const item = data.getItemModel(index);
        return [Number(item.get("x")), Number(item.get("y"))];
      });
      const separate = (passes) => {
        for (let pass = 0; pass < passes; pass += 1) {
          for (let i = 0; i < boxes.length; i += 1) {
            const a = boxes[i],
              pa = data.getItemLayout(a.index);
            for (let j = i + 1; j < boxes.length; j += 1) {
              const b = boxes[j],
                pb = data.getItemLayout(b.index);
              const dx = pb[0] - pa[0],
                dy = pb[1] - pa[1];
              const ox = (a.width + b.width) / 2 + 28 - Math.abs(dx);
              const oy = (a.height + b.height) / 2 + 28 - Math.abs(dy);
              if (
                ox <= 0 ||
                oy <= 0 ||
                (fixed.has(a.index) && fixed.has(b.index))
              )
                continue;
              const axis = ox < oy ? 0 : 1;
              const amount =
                ((axis === 0 ? ox : oy) + 0.1) *
                ((axis === 0 ? dx : dy) < 0 ? -1 : 1);
              const aWeight = fixed.has(a.index)
                ? 0
                : fixed.has(b.index)
                ? 1
                : 0.5;
              pa[axis] -= amount * aWeight;
              pb[axis] += amount * (1 - aWeight);
            }
          }
        }
      };
      const step = solver.step.bind(solver);
      solver.step = (callback) => {
        if (series.__oslabForceSettled) {
          restore();
          callback?.(true);
          return;
        }
        step(callback);
      };
      solver.afterStep((particles, edges, stopped) => {
        const before = particles.map((particle) => particle.p.slice());
        particles.forEach((particle, index) => {
          if (fixed.has(index)) return;
          particle.p[0] += (anchors[index][0] - particle.p[0]) * 0.08;
          particle.p[1] += (anchors[index][1] - particle.p[1]) * 0.08;
        });
        particles.forEach((particle, index) =>
          data.setItemLayout(index, particle.p)
        );
        separate(stopped ? 80 : 3);
        if (stopped) {
          const occupied = [];
          for (const box of boxes) {
            const point = data.getItemLayout(box.index);
            const neighbors = occupied.filter(
              (other) =>
                Math.abs(point[0] - other.point[0]) <
                (box.width + other.width) / 2 + 28
            );
            const clear = (y) =>
              neighbors.every(
                (other) =>
                  Math.abs(y - other.point[1]) >=
                  (box.height + other.height) / 2 + 28
              );
            if (!clear(point[1]) && !fixed.has(box.index)) {
              const candidates = neighbors
                .flatMap((other) => {
                  const gap = (box.height + other.height) / 2 + 29;
                  return [other.point[1] - gap, other.point[1] + gap];
                })
                .sort(
                  (a, b) => Math.abs(a - point[1]) - Math.abs(b - point[1])
                );
              point[1] = candidates.find(clear);
            }
            occupied.push({ ...box, point });
          }
        }
        particles.forEach((particle, index) => {
          particle.pp[0] += particle.p[0] - before[index][0];
          particle.pp[1] += particle.p[1] - before[index][1];
          series.preservedPoints[data.getId(index)] = particle.p;
        });
        syncEdges();
        if (stopped) {
          series.__oslabForceSettled = true;
          series.__oslabSettledPoints = new Map(
            data.mapArray((index) => [
              data.getId(index),
              data.getItemLayout(index).slice(),
            ])
          );
        }
      });
      const warmUp = solver.warmUp.bind(solver);
      solver.warmUp = () => {
        series.__oslabForceSettled = false;
        warmUp();
      };
      const setFixed = solver.setFixed.bind(solver);
      const setUnfixed = solver.setUnfixed.bind(solver);
      solver.setFixed = (index) => {
        fixed.add(index);
        setFixed(index);
      };
      solver.setUnfixed = (index) => {
        anchors[index] = data.getItemLayout(index).slice();
        fixed.delete(index);
        setUnfixed(index);
      };
    });
  }
);
</script>

<script setup>
import { ref, reactive, watch, onMounted, onBeforeUnmount } from "vue";

// 类别底色和掌握状态独立表达；dependent 连线反向绘制为学习顺序。
// 章节播种后运行力布局；拖动重新加热，选中和缩放保留布局。
// 不使用 labelLayout：其绝对标签定位会与确定性坐标、边标签缩放冲突。

// eslint-disable-next-line no-undef
const props = defineProps({
  nodes: { type: Array, required: true },
  links: { type: Array, required: true },
  categories: { type: Array, required: true },
  // 当前选中节点（2px 主蓝描边 + 邻接边强调；非邻接保持可读性下限，
  // 选中不在当前可见节点集合内时本组件视为无选中）
  selectedId: { type: String, default: "" },
  // 状态变化脉冲：[{ name, color: "blue" | "amber" }]，1.2s 后自动消退
  pulses: { type: Array, default: () => [] },
  // 是否渲染个人掌握状态条/圆点（数据里没有任何 status 时自动隐藏）
  showStatus: { type: Boolean, default: true },
  // 常显关系文字标签开关（hover / 选中相邻边始终显示）
  showRelationLabels: { type: Boolean, default: false },
  // 编号批注：key = 节点稳定 key，value = 显示编号。只给被批注引用的节点渲染编号。
  annotations: { type: Object, default: () => ({}) },
  // 边色调：semantic = 按关系类型着色（其他页面默认）；
  // muted = 课程学习图谱的灰度边，只有选中邻接/路径使用钴蓝。
  edgeTone: { type: String, default: "semantic" },
  // 只读投影模式（log3 收尾 E）：节点底色按掌握状态浅色渲染（班级聚合热力图）。
  // 默认 false，保持"类别底色 + 状态条/圆点"的个人图谱渲染完全不变。
  fillByStatus: { type: Boolean, default: false },
  // 画布高度覆盖（嵌入回放弹窗/教师看板时使用，如 "420px"）；
  // 空串保持默认 clamp 高度，不影响既有页面。
  chartHeight: { type: String, default: "" },
});

const cloneProps = (source) => ({
  nodes: (source?.nodes ?? props.nodes ?? []).map((node) => ({ ...node })),
  links: (source?.links ?? props.links ?? []).map((link) => ({ ...link })),
  categories: (source?.categories ?? props.categories ?? []).map((c) => ({
    ...c,
  })),
});

const chartData = reactive(cloneProps());

// eslint-disable-next-line no-undef
const emit = defineEmits(["node-click", "node-dblclick"]);

const chart = ref(null);
let myChart = null;
let clickTimer = null;
// 当前一帧的完整节点数据（updateVisual 不会回写 series.data，点击载荷从这里取）
let renderedNodeByKey = new Map();
const CLICK_DELAY = 220;
// 当前缩放级别（由 graphRoam / fit 同步）与悬停节点（关系标签按需显示）
const currentZoom = ref(1);
const hoveredNodeId = ref("");
let zoomFrame = 0;
let layoutFrame = 0;
let labelRefresh = 0;
let fittedSeries = null;

// ---------- 设计令牌（learning-theme.css 图谱 token 的逐项镜像） ----------
// ECharts 画布/SSR 字符串无法读取 CSS 变量，因此在这里保留唯一一份
// 画布内实色；每个值都能在 learning-theme.css 的 --oslab-graph-* /
// --oslab-mastered 等 token 中找到同名来源，测试会逐项比对，禁止在
// 组件其他位置新增裸色值。
const GRAPH_TOKENS = Object.freeze({
  surface: "transparent",
  nodeBorder: "#c3cedb",
  nodeRadius: 8,
  category: Object.freeze({
    concept: "#e8eefc",
    algorithm: "#f1e9fa",
    function: "#e4f2ed",
    fallback: "#ecf0f5",
  }),
  nodeFill: Object.freeze({
    concept: "#f8faff",
    algorithm: "#fbf8ff",
    function: "#f6fbf9",
    fallback: "#fafbfd",
  }),
  nodeShadow: "rgba(32, 36, 43, 0.08)",
  selection: "#2447d8",
  selectionRing: "rgba(36, 71, 216, 0.18)",
  path: "#8fa6c4",
  pathStrong: "#6b809f",
  pathEdge: "#3f66a5",
  pathRing: "rgba(107, 128, 159, 0.28)",
  reviewRing: "rgba(184, 137, 46, 0.26)",
  inkSoft: "#3a4150",
  textSoft: "#66707c",
  labelSurface: "#ffffff",
  highlightSoft: "#fff7c2",
  highlight: "#f2d64b",
  mutedEdge: "#b0b6bf",
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
    GRAY: "#eceff4",
  }),
});

const PRIMARY = GRAPH_TOKENS.selection;
const REVIEW = GRAPH_TOKENS.relation.implements;
const INK_SOFT = GRAPH_TOKENS.inkSoft;
const MUTED_STRONG = GRAPH_TOKENS.textSoft;
const NODE_BORDER = GRAPH_TOKENS.nodeBorder;
// 单元/补救路径提示：低强度细边，绝不与选中主蓝描边共用
const PATH_HINT_BORDER = GRAPH_TOKENS.path;

// 课程类别 → 节点底色（§5.2）；优先使用稳定的 nodeType，旧数据回退 category。
// 类别只占底色，不参与名称文字色，也不使用状态色系。
const CATEGORY_FILLS = [
  { keys: ["概念", "concept"], fill: GRAPH_TOKENS.nodeFill.concept },
  { keys: ["算法", "algorithm"], fill: GRAPH_TOKENS.nodeFill.algorithm },
  {
    keys: ["功能", "机制", "模块", "function", "mechanism"],
    fill: GRAPH_TOKENS.nodeFill.function,
  },
  {
    keys: ["实现", "implementation"],
    fill: GRAPH_TOKENS.nodeFill.fallback,
  },
];
const CATEGORY_FALLBACK_FILL = GRAPH_TOKENS.nodeFill.fallback;

const NODE_TYPE_TEXT = Object.freeze({
  CONCEPT: "概念",
  ALGORITHM: "算法",
  MECHANISM: "机制",
  IMPLEMENTATION: "实现",
});
const nodeTypeTextOf = (node) =>
  NODE_TYPE_TEXT[String(node?.nodeType || "").toUpperCase()] ||
  node?.category ||
  "未标注";

const categoryFillOf = (value) => {
  const c = String(value || "")
    .trim()
    .toLowerCase();
  for (const def of CATEGORY_FILLS) {
    if (def.keys.some((k) => c.includes(k))) return def.fill;
  }
  return CATEGORY_FALLBACK_FILL;
};

// 个人掌握状态色（§5.2；只表达状态，不用于装饰）
const STATUS_COLORS = GRAPH_TOKENS.status;
const STATUS_TEXT = {
  GREEN: "已掌握",
  YELLOW: "待巩固",
  RED: "薄弱",
  GRAY: "未学习",
};

// fillByStatus（只读投影）的节点底色：与 learning-theme.css 的
// --oslab-mastered/review/weak/unseen-soft 令牌同值；名称文字仍用
// STATUS_COLORS 强色，图例（父视图渲染）与节点实际颜色保持一致
const STATUS_FILLS = GRAPH_TOKENS.statusFill;

// 关系语义文案；历史拼写 implemente 兼容映射为 implements
const RELATION_TEXT = {
  contrasts: "对比",
  based: "依据 / 原理",
  dependent: "依赖 / 前置知识",
  contain: "包含 / 所属",
  related: "相关",
  implements: "实现",
};
const RELATION_LABEL = {
  contrasts: "对比",
  based: "依据",
  dependent: "依赖",
  contain: "包含",
  related: "相关",
  implements: "实现",
};

const normalizeRelationType = (link) => {
  const raw = String(link?.relationType ?? link?.value ?? "")
    .trim()
    .toLowerCase();
  return (
    {
      requires: "dependent",
      contains: "contain",
      related_to: "related",
      implemente: "implements",
      contrasts_with: "contrasts",
      based_on: "based",
    }[raw] || raw
  );
};

// ---------- 节点身份规则（唯一来源，log3 收尾 B.1 的升级） ----------
// nodeKey = nodeCode || stableId || String(id) || name
// data.id、link.source/target、selectedId、点击载荷都使用同一规则；
// displayName 只用于展示，绝不参与身份比较。
const identityPart = (value) =>
  value === null || value === undefined || value === ""
    ? ""
    : String(value).trim();
const nodeKeyOf = (node) => {
  if (!node) return "";
  return (
    identityPart(node.nodeCode) ||
    identityPart(node.stableId) ||
    identityPart(node.id) ||
    identityPart(node.name)
  );
};

// 兼容旧消费方仍传原始 id/name 的 selectedId 与连线端点
const createKeyResolver = (nodes) => {
  const aliases = new Map();
  for (const node of nodes || []) {
    const key = nodeKeyOf(node);
    if (!key) continue;
    for (const candidate of [
      node.nodeCode,
      node.stableId,
      node.id,
      node.name,
    ]) {
      const alias = identityPart(candidate);
      if (alias && !aliases.has(alias)) aliases.set(alias, key);
    }
  }
  return (value) => {
    const raw = identityPart(value);
    return aliases.get(raw) || raw;
  };
};

// ---------- 状态脉冲（1.2s，进步蓝 / 退步琥珀） ----------
const pulseMap = ref(new Map());
let pulseTimer = null;
// eslint-disable-next-line no-undef
watch(
  () => props.pulses,
  (list) => {
    if (!Array.isArray(list) || list.length === 0) return;
    const next = new Map();
    for (const item of list) {
      if (item && (item.key || item.name)) {
        next.set(
          String(item.key || item.name),
          item.color === "blue" ? "blue" : "amber"
        );
      }
    }
    if (next.size === 0) return;
    pulseMap.value = next;
    if (myChart) updateChart();
    if (pulseTimer) clearTimeout(pulseTimer);
    pulseTimer = window.setTimeout(() => {
      pulseMap.value = new Map();
      if (myChart) updateChart();
    }, 1200);
  },
  { deep: true }
);

// ---------- 节点几何与富文本标签 ----------
// 小图按名称长度分档；章节图减少卡片占地，大图使用稳定的类型尺寸。
// 名称超出省略，完整名称保留在 tooltip 和详情中；尺寸不随缩放/选中变化。
const NODE_TIERS = Object.freeze({
  compact: Object.freeze({ width: 112, height: 32 }),
  regular: Object.freeze({ width: 120, height: 34 }),
  wide: Object.freeze({ width: 128, height: 36 }),
});
// 小图（<=40 节点）用更宽的索引卡，避免章节视图里长名称被截断；
// 大图保持紧凑档，避免 150+ 节点互相挤压。
const NODE_TIERS_ROOMY = Object.freeze({
  compact: Object.freeze({ width: 140, height: 38 }),
  regular: Object.freeze({ width: 152, height: 40 }),
  wide: Object.freeze({ width: 164, height: 42 }),
});
const NODE_TIERS_CHAPTER = Object.freeze({
  compact: Object.freeze({ width: 128, height: 34 }),
  regular: Object.freeze({ width: 140, height: 36 }),
  wide: Object.freeze({ width: 152, height: 38 }),
});
// 兼容旧消费方的导出名：以常规档为基准。
const NODE_PADDING_X = 8;
const NODE_PADDING_Y = 3;
const STATUS_DOT_SPACE = 12;
const NAME_MAX_WIDTH = NODE_TIERS.compact.width - NODE_PADDING_X * 2;
const CHAR_W = 13;
const LAT_W = 7.5;
const isWide = (ch) =>
  /[\u2e80-\ua4cf\uac00-\ud7a3\uf900-\ufaff\ufe30-\ufe4f\uff00-\uffef\u3000-\u303f]/.test(
    ch
  );
const textWidth = (s) => {
  let w = 0;
  for (const ch of String(s || "")) w += isWide(ch) ? CHAR_W : LAT_W;
  return w;
};

const truncateName = (name, availableWidth) => {
  const s = String(name || "").replace(/\r?\n/g, " ");
  const maxWidth = Math.max(
    CHAR_W * 3,
    Number(availableWidth) || NAME_MAX_WIDTH
  );
  if (!s || textWidth(s) <= maxWidth) return s;
  let index = 0;
  let acc = 0;
  for (; index < s.length; index++) {
    const w = isWide(s[index]) ? CHAR_W : LAT_W;
    if (acc + w > maxWidth - CHAR_W) break;
    acc += w;
  }
  return `${s.slice(0, index)}…`;
};

const nodeTierOf = (node) => {
  const raw = String(node?.nodeType || node?.category || "").toLowerCase();
  if (/实现|implementation/.test(raw)) return "wide";
  if (/算法|机制|功能|algorithm|mechanism|function/.test(raw)) return "regular";
  return "compact";
};
const NODE_AUTO_WIDTH_LIMIT = 40;
const nodeSizeOf = (node) => {
  const count = props.nodes?.length ?? 0;
  const fixed = (
    count > NODE_AUTO_WIDTH_LIMIT
      ? NODE_TIERS
      : count <= 12
      ? NODE_TIERS_ROOMY
      : NODE_TIERS_CHAPTER
  )[nodeTierOf(node)];
  if (count > NODE_AUTO_WIDTH_LIMIT) return fixed;
  const label = String(node.displayName || node.name || "");
  const widthOfName = textWidth(label);
  // Reserve the full status dot and padding before choosing a width tier.
  const requiredWidth = widthOfName + NODE_PADDING_X * 2 + STATUS_DOT_SPACE;
  const tiers =
    count <= 12 ? [96, 120, 150, 180, 210] : [88, 108, 132, 156, 180];
  const width =
    tiers.find((candidate) => candidate >= requiredWidth) ||
    tiers[tiers.length - 1];
  return { width, height: fixed.height };
};
const layoutNodeSizeOf = (node) => {
  const size = nodeSizeOf(node);
  if (!Number(props.annotations?.[nodeKeyOf(node)])) return size;
  const count = Object.keys(props.annotations || {}).length;
  return {
    width: Math.min(
      240,
      size.width + (count === (props.nodes?.length ?? 0) ? 20 : 32)
    ),
    height: size.height,
  };
};

const buildLabel = (ctx) => {
  // 名称始终使用正文色，掌握状态只用状态圆点表达；弱状态不把标题变淡。
  // 小图缩放较小时切到紧凑模式：只保留名称、隐藏编号/圆点，
  // 避免屏幕尺寸文字挤爆已缩小的索引卡。
  const compact = !!ctx.compactLabels;
  // ECharts keeps graph labels in screen pixels while symbols scale with roam.
  // Scale every rich-text dimension explicitly, including its truncation box.
  const scale = Math.min(1.3, currentZoom.value);
  const dotWidth = compact ? 0 : ctx.showStatusUI ? STATUS_DOT_SPACE : 0;
  const numWidth = compact ? 0 : ctx.annotationNumber ? 20 : 0;
  const labelWidth = Math.max(1, ctx.width * currentZoom.value - 16 * scale);
  const nameWidth = labelWidth / scale - 8 - dotWidth - numWidth;
  const displayName = truncateName(ctx.displayName, nameWidth);
  const rich = {
    num: {
      color: "#ffffff",
      backgroundColor: PRIMARY,
      fontSize: 9 * scale,
      lineHeight: 16 * scale,
      width: 16 * scale,
      height: 16 * scale,
      borderRadius: 8 * scale,
      align: "center",
      verticalAlign: "middle",
      padding: [2 * scale, 0, 0, 0],
    },
    dot: {
      color: ctx.statusColor,
      fontSize: 9 * scale,
      lineHeight: 15 * scale,
      width: STATUS_DOT_SPACE * scale,
      align: "left",
    },
    name: {
      color: ctx.dimmed ? MUTED_STRONG : INK_SOFT,
      fontSize: 12.5 * scale,
      lineHeight: 15 * scale,
      align: "left",
      fontWeight: ctx.isSelected ? 600 : 500,
      backgroundColor: ctx.isSelected
        ? GRAPH_TOKENS.highlightSoft
        : "transparent",
      padding: ctx.isSelected ? [scale, 3 * scale] : 0,
    },
  };
  const numPrefix =
    !compact && ctx.annotationNumber ? `{num|${ctx.annotationNumber}} ` : "";
  const dot = !compact && ctx.showStatusUI ? "{dot|●}" : "";
  const text = `${numPrefix}${dot}{name|${displayName}}`;
  return {
    show: true,
    position: "inside",
    align: "center",
    verticalAlign: "middle",
    width: labelWidth,
    padding: [NODE_PADDING_Y * scale, 0],
    overflow: "truncate",
    ellipsis: "…",
    formatter: text,
    rich,
  };
};

const buildNodeStyle = (node, ctx) => {
  const key = nodeKeyOf(node);
  // 三种状态严格分离：选中只认规范化 selectedId；highlight 只作路径提示；
  // 脉冲只作状态变化提示，均不得互相伪装。
  const isSelected = !!ctx.selectedId && key === ctx.selectedId;
  const isPathHighlighted = !!(node.pathHighlighted || node.highlight);
  const isPulsing = pulseMap.value.get(nodeKeyOf(node));
  const dimmed = !!ctx.selectedId && !isSelected && !ctx.neighborIds.has(key);
  const statusColor =
    STATUS_COLORS[node.status || "GRAY"] || STATUS_COLORS.GRAY;
  // 蓝色脉冲在路径节点上必须使用路径 token，不能借用选中主蓝。
  const pulseBorder = isPulsing
    ? isPulsing === "blue"
      ? isPathHighlighted
        ? GRAPH_TOKENS.pathStrong
        : PRIMARY
      : REVIEW
    : "";
  const borderColor = isSelected
    ? PRIMARY
    : isPulsing
    ? pulseBorder
    : isPathHighlighted
    ? PATH_HINT_BORDER
    : ctx.fillByStatus
    ? // 教师班级聚合投影：底色已表达状态，1px 状态色边线与聚合图例同源
      statusColor
    : NODE_BORDER;
  // 选中：2px 锐利描边 + 3px 低透明度外圈；路径提示只保留 1px 细边。
  const pulseShadow = isPulsing
    ? isPulsing === "blue"
      ? isPathHighlighted
        ? GRAPH_TOKENS.pathRing
        : GRAPH_TOKENS.selectionRing
      : GRAPH_TOKENS.reviewRing
    : "transparent";
  const annotationNumber = Number(props.annotations?.[key]) || 0;
  const isSmallGraph = (props.nodes?.length ?? 0) <= 40;
  const size = layoutNodeSizeOf(node);
  const rest = { ...node };
  delete rest.highlight;
  return {
    ...rest,
    id: key,
    nodeCode: node.nodeCode,
    stableId: node.stableId,
    name: node.name,
    displayName: node.displayName || node.name,
    nodeType: node.nodeType,
    category: node.category,
    chapter: node.chapter,
    description: node.description,
    status: node.status,
    pathHighlighted: isPathHighlighted,
    highlightReason:
      node.highlightReason || (node.highlight ? "path-highlight" : undefined),
    symbol: "rect",
    symbolSize: [size.width, size.height],
    itemStyle: {
      // fillByStatus：底色 = 状态浅色（聚合热力投影）；默认 = 类别底色
      color: ctx.fillByStatus
        ? STATUS_FILLS[node.status || "GRAY"] || CATEGORY_FALLBACK_FILL
        : categoryFillOf(node.nodeType || node.category || node.label),
      borderColor,
      // 只有选中态使用 2px；脉冲只加外圈，避免非选中节点伪装成选中
      borderWidth: isSelected ? 2 : 1,
      borderRadius: GRAPH_TOKENS.nodeRadius,
      opacity: 1,
      shadowBlur: isSelected
        ? isSmallGraph
          ? 10
          : 3
        : isPulsing
        ? 4
        : isSmallGraph
        ? 6
        : 0,
      shadowColor: isSelected
        ? GRAPH_TOKENS.selectionRing
        : isPulsing
        ? pulseShadow
        : isSmallGraph
        ? GRAPH_TOKENS.nodeShadow
        : "transparent",
      shadowOffsetY: isSelected ? 0 : isSmallGraph ? 2 : 0,
    },
    label: buildLabel({
      isSelected,
      dimmed,
      statusColor,
      showStatusUI: ctx.showStatusUI,
      compactLabels: ctx.compactLabels,
      displayName: node.displayName || node.name,
      width: size.width,
      annotationNumber,
    }),
  };
};

// ---------- 连线（关系语义 + R10 渲染方向 + 邻接强调） ----------
const escapeHtml = (s) =>
  String(s ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[
        c
      ])
  );

const buildLink = (link, ctx, linkIndex) => {
  const type = normalizeRelationType(link);
  // 连线端点同样走 nodeKey 归一化后再进入 ECharts
  const sourceKey = ctx.resolveKey(link.source);
  const targetKey = ctx.resolveKey(link.target);
  // R10：dependent 存储为 A→B（A 依赖 B），画布按 B→A 绘制（先学箭尾）
  const isDependent = type === "dependent";
  const from = isDependent ? targetKey : sourceKey;
  const to = isDependent ? sourceKey : targetKey;
  const adjacent =
    (!!ctx.selectedId &&
      (sourceKey === ctx.selectedId || targetKey === ctx.selectedId)) ||
    (!!ctx.hoveredId &&
      (sourceKey === ctx.hoveredId || targetKey === ctx.hoveredId));
  const dimmed = !!ctx.selectedId && !adjacent;
  // 非邻接边弱化下限：保持关系结构可读，不再压到几乎不可见
  const dim = dimmed ? 0.55 : 1;
  // 视图注入的路径强调（只看学习路径）优先于语义默认值；标记转成
  // 组件内 token，避免在视图中散落画布色值。
  const override = { ...(link.lineStyle || {}) };
  const pathEmphasis = !!override.pathEmphasis;
  delete override.pathEmphasis;
  let color;
  let width;
  let lineType;
  let symbol;
  let symbolSize;
  switch (type) {
    case "dependent":
      color = GRAPH_TOKENS.relation.dependent;
      width = 1.8;
      lineType = "solid";
      symbol = ["none", "arrow"];
      symbolSize = [9, 11];
      break;
    case "contain":
      color = GRAPH_TOKENS.relation.contain;
      width = 1.5;
      lineType = "dashed";
      symbol = ["none", "arrow"];
      symbolSize = [7, 8];
      break;
    case "related":
    case "contrasts":
      color = GRAPH_TOKENS.relation.related;
      width = 1.6;
      lineType = "dotted";
      symbol = ["circle", "circle"];
      symbolSize = [3, 3];
      break;
    case "implements":
    case "based":
      color = GRAPH_TOKENS.relation.implements;
      width = 1.6;
      lineType = "dashed";
      symbol = ["none", "arrow"];
      symbolSize = [6, 7];
      break;
    default:
      // 未知/空关系类型：灰色细实线兜底，不抛异常
      color = GRAPH_TOKENS.relation.fallback;
      width = 1.3;
      lineType = "solid";
      symbol = ["none", "none"];
      symbolSize = [0, 0];
  }
  if (props.edgeTone === "muted") {
    if (pathEmphasis || adjacent) {
      color = GRAPH_TOKENS.pathEdge;
      width = pathEmphasis ? 2 : 1.6;
      lineType = [4, 4];
    } else {
      color = GRAPH_TOKENS.mutedEdge;
      width = 1.4;
      lineType = type === "related" ? [1, 4] : [3, 5];
    }
  }
  if (pathEmphasis) {
    // 路径强调：统一的短蓝线，不发光，与依赖语义蓝/选中邻接宽度区分
    color = GRAPH_TOKENS.pathEdge;
    width = 2.2;
    lineType = props.edgeTone === "muted" ? [4, 4] : "solid";
  }
  const relText = RELATION_TEXT[type] || "未知关系";
  const relLabel = RELATION_LABEL[type] || "未知";
  const nameA = ctx.nameOf(link.source);
  const nameB = ctx.nameOf(link.target);
  let tooltipText = relText;
  if (isDependent) {
    tooltipText = `${relText}：先学「${nameB}」，再学「${nameA}」（「${nameA}」依赖「${nameB}」）`;
  } else if (type === "contain") {
    tooltipText = `${relText}：「${nameA}」包含「${nameB}」`;
  } else if (type === "implements") {
    tooltipText = `${relText}：「${nameA}」实现「${nameB}」`;
  } else if (type === "related") {
    tooltipText = `${relText}：「${nameA}」与「${nameB}」相关`;
  } else if (type === "contrasts") {
    tooltipText = `${relText}：「${nameA}」与「${nameB}」对比`;
  } else if (type === "based") {
    tooltipText = `${relText}：「${nameA}」以「${nameB}」为依据`;
  }
  const baseCurve = Number(ctx.curveOf(link)) || 0;
  const curve = pathEmphasis
    ? 0.08
    : Math.abs(baseCurve) < 0.08
    ? 0.12
    : baseCurve;
  return {
    ...link,
    source: from,
    target: to,
    symbol,
    symbolSize,
    lineStyle: {
      color,
      width: pathEmphasis ? width : adjacent ? width + 0.6 : width,
      type: lineType,
      opacity: pathEmphasis ? 1 : dim,
      curveness: curve,
      cap: "round",
      ...override,
    },
    label: {
      // 标签由开关 + 缩放层级 + 避让结果共同决定；关闭时不残留文字。
      show: ctx.showRelationLabels && ctx.labelIndexes.has(linkIndex),
      formatter: relLabel,
      position: "middle",
      distance: 0,
      align: "center",
      verticalAlign: "middle",
      fontSize: 10.5,
      lineHeight: 14,
      rotate: 0,
      color: MUTED_STRONG,
      backgroundColor: GRAPH_TOKENS.labelSurface,
      padding: [1, 4],
      borderRadius: 2,
    },
    relationText: tooltipText,
  };
};

// ---------- 确定性布局：章节播种 → 拓扑力导向 → 矩形碰撞修正 ----------
// 章节内按 sectionCode/输入顺序播种，章节块按画布比例装箱；同步有限步
// 松弛使关联节点聚拢，再作为实时力布局的稳定起点，视觉刷新不重新播种。
const NODE_LABEL_COMPACT_ZOOM = 0.68;
const RELATION_LABEL_MAX = 36;
const RELATION_LABEL_MAX_COMPACT = 18;
const LAYOUT_GAP_X = 40;
const LAYOUT_GAP_Y = 40;
const LAYOUT_BLOCK_GAP = 34;

const compareChapters = (a, b) => {
  if (a === b) return 0;
  if (a === "未分类") return 1;
  if (b === "未分类") return -1;
  return a.localeCompare(b, "zh-CN", { numeric: true });
};

const ORGANIC_LAYOUT_LIMIT = 12;

/**
 * 小图放射布局：选中节点作为根，直连节点分布在一环右侧扇形，
 * 其余节点在二环；角度/半径用固定正弦扰动，保持确定性与稳定。
 */
const computeOrganicLayout = (nodes, links, selectedKey, resolveKey) => {
  const keys = nodes.map((node) => nodeKeyOf(node));
  const keySet = new Set(keys);
  const adjacency = new Map(keys.map((key) => [key, new Set()]));
  for (const link of links || []) {
    const source = resolveKey(link.source);
    const target = resolveKey(link.target);
    if (!keySet.has(source) || !keySet.has(target) || source === target) {
      continue;
    }
    adjacency.get(source).add(target);
    adjacency.get(target).add(source);
  }
  const root = keySet.has(selectedKey)
    ? selectedKey
    : keys
        .slice()
        .sort(
          (a, b) =>
            adjacency.get(b).size - adjacency.get(a).size ||
            keys.indexOf(a) - keys.indexOf(b)
        )[0];
  const direct = Array.from(adjacency.get(root) || []).filter(
    (key) => key !== root
  );
  const directSet = new Set(direct);
  const rest = keys.filter((key) => key !== root && !directSet.has(key));
  const aspect = Math.max(
    0.8,
    Math.min(
      1.8,
      (myChart?.getWidth?.() || 1120) / (myChart?.getHeight?.() || 620)
    )
  );
  const scaleX = Math.min(1.15, Math.max(0.85, aspect));
  const maxWidth = Math.max(
    ...nodes.map((node) => layoutNodeSizeOf(node).width)
  );
  const maxHeight = Math.max(
    ...nodes.map((node) => layoutNodeSizeOf(node).height)
  );
  const rootX = -Math.round(maxWidth * 0.65);
  const ring1X = Math.max(maxWidth * 1.05, 215 * scaleX);
  const ring1Y = Math.max(maxHeight * 1.2, 140);
  const positions = new Map([[root, { x: rootX, y: 0 }]]);
  direct.forEach((key, index) => {
    const angle =
      -Math.PI * 0.72 + (index / Math.max(1, direct.length)) * Math.PI * 1.44;
    const wobble = 1 + 0.07 * Math.sin((index + 1) * 1.7);
    positions.set(key, {
      x: Math.round(rootX + Math.cos(angle) * ring1X * wobble),
      y: Math.round(Math.sin(angle) * ring1Y * wobble),
    });
  });
  const ring2X = Math.max(maxWidth * 2.1, 340 * scaleX);
  const ring2Y = Math.max(maxHeight * 2.1, 225);
  rest.forEach((key, index) => {
    const angle = (index / Math.max(1, rest.length)) * Math.PI * 2 + 0.42;
    const wobble = 1 + 0.06 * Math.sin((index + 2) * 2.1);
    positions.set(key, {
      x: Math.round(rootX + Math.cos(angle) * ring2X * wobble),
      y: Math.round(Math.sin(angle) * ring2Y * wobble),
    });
  });
  return resolveNodeOverlaps(nodes, positions, selectedKey);
};

const resolveNodeOverlaps = (nodes, positions, fixedKey = "") => {
  const boxes = nodes.map((node) => ({
    key: nodeKeyOf(node),
    size: layoutNodeSizeOf(node),
  }));
  for (let pass = 0; pass < 48; pass += 1) {
    let moved = false;
    for (let i = 0; i < boxes.length; i += 1) {
      for (let j = i + 1; j < boxes.length; j += 1) {
        const a = boxes[i];
        const b = boxes[j];
        const pa = positions.get(a.key);
        const pb = positions.get(b.key);
        if (!pa || !pb) continue;
        const overlapX =
          (a.size.width + b.size.width) / 2 + 12 - Math.abs(pa.x - pb.x);
        const overlapY =
          (a.size.height + b.size.height) / 2 + 12 - Math.abs(pa.y - pb.y);
        if (overlapX <= 0 || overlapY <= 0) continue;
        const moveA = b.key === fixedKey;
        const point = moveA ? pa : pb;
        const other = moveA ? pb : pa;
        const distance = (overlapX <= overlapY ? overlapX : overlapY) + 1;
        if (overlapX <= overlapY) {
          const direction =
            point.x === other.x
              ? i % 2
                ? -1
                : 1
              : Math.sign(point.x - other.x);
          point.x += direction * distance;
        } else {
          const direction =
            point.y === other.y
              ? i % 2
                ? -1
                : 1
              : Math.sign(point.y - other.y);
          point.y += direction * distance;
        }
        moved = true;
      }
    }
    if (!moved) break;
  }
  // Dense rectangular chains may still oscillate at the iteration limit. Place
  // any remaining collision at its closest legal vertical boundary; already
  // accepted boxes stay fixed, so this finite fallback guarantees separation.
  const occupied = [];
  for (const box of boxes) {
    const point = positions.get(box.key);
    if (!point) continue;
    const neighbors = occupied.filter(
      (other) =>
        Math.abs(point.x - other.point.x) <
        (box.size.width + other.size.width) / 2 + 12
    );
    const isClear = (y) =>
      neighbors.every(
        (other) =>
          Math.abs(y - other.point.y) >=
          (box.size.height + other.size.height) / 2 + 12
      );
    if (!isClear(point.y)) {
      const candidates = neighbors.flatMap((other) => {
        const gap = (box.size.height + other.size.height) / 2 + 13;
        return [other.point.y - gap, other.point.y + gap];
      });
      candidates.sort(
        (a, b) => Math.abs(a - point.y) - Math.abs(b - point.y) || a - b
      );
      point.y = candidates.find(isClear);
    }
    occupied.push({ ...box, point });
  }
  return positions;
};

const computeChapterLayout = (nodes) => {
  const groups = new Map();
  nodes.forEach((node, index) => {
    const chapter = String(node.chapter || "").trim() || "未分类";
    if (!groups.has(chapter)) groups.set(chapter, []);
    groups.get(chapter).push({ node, index });
  });
  const chapterNames = [...groups.keys()].sort(compareChapters);
  const blocks = chapterNames.map((chapter) => {
    const items = groups
      .get(chapter)
      .slice()
      .sort((a, b) => {
        const sectionA = String(a.node.homeSectionCode || "").trim();
        const sectionB = String(b.node.homeSectionCode || "").trim();
        if (sectionA !== sectionB) {
          return sectionA.localeCompare(sectionB, "zh-CN", { numeric: true });
        }
        return a.index - b.index;
      });
    const cols = Math.max(
      1,
      Math.min(12, Math.ceil(Math.sqrt(Math.max(1, items.length))))
    );
    const rows = Math.ceil(items.length / cols);
    const columnWidths = Array.from({ length: cols }, (_, col) =>
      Math.max(
        ...items
          .filter((_, index) => index % cols === col)
          .map(({ node }) => layoutNodeSizeOf(node).width)
      )
    );
    const rowHeights = Array.from({ length: rows }, (_, row) =>
      Math.max(
        ...items
          .slice(row * cols, row * cols + cols)
          .map(({ node }) => layoutNodeSizeOf(node).height)
      )
    );
    return {
      chapter,
      items,
      cols,
      rows,
      columnWidths,
      rowHeights,
      width:
        columnWidths.reduce((sum, width) => sum + width, 0) +
        LAYOUT_GAP_X * (cols - 1),
      height:
        rowHeights.reduce((sum, height) => sum + height, 0) +
        LAYOUT_GAP_Y * (rows - 1),
    };
  });

  const chartWidth = myChart?.getWidth?.() || 1120;
  const chartHeight = myChart?.getHeight?.() || 620;
  const targetAspect = Math.max(0.5, chartWidth / Math.max(1, chartHeight));

  let best = null;
  for (
    let blockCols = 1;
    blockCols <= Math.max(1, blocks.length);
    blockCols++
  ) {
    const blockRows = Math.ceil(blocks.length / blockCols);
    let totalWidth = 0;
    let totalHeight = 0;
    for (let row = 0; row < blockRows; row++) {
      let rowWidth = 0;
      let rowHeight = 0;
      for (let col = 0; col < blockCols; col++) {
        const block = blocks[row * blockCols + col];
        if (!block) continue;
        rowWidth += block.width + LAYOUT_BLOCK_GAP;
        rowHeight = Math.max(rowHeight, block.height);
      }
      totalWidth = Math.max(totalWidth, rowWidth - LAYOUT_BLOCK_GAP);
      totalHeight += rowHeight + LAYOUT_BLOCK_GAP;
    }
    totalHeight -= LAYOUT_BLOCK_GAP;
    const actualAspect = Math.max(1, totalWidth) / Math.max(1, totalHeight);
    const score = Math.abs(Math.log(actualAspect / targetAspect));
    if (!best || score < best.score - 1e-9) {
      best = { blockCols, totalWidth, totalHeight, score };
    }
  }

  const positions = new Map();
  const startY = -(best?.totalHeight || 1) / 2;
  let currentY = startY;
  const blockRows = Math.ceil(blocks.length / (best?.blockCols || 1));
  for (let row = 0; row < blockRows; row++) {
    let currentX = -(best?.totalWidth || 1) / 2;
    let rowHeight = 0;
    for (let col = 0; col < (best?.blockCols || 1); col++) {
      const block = blocks[row * (best?.blockCols || 1) + col];
      if (!block) continue;
      block.items.forEach((item, position) => {
        const localCol = position % block.cols;
        const localRow = Math.floor(position / block.cols);
        const x =
          currentX +
          block.columnWidths
            .slice(0, localCol)
            .reduce((sum, width) => sum + width + LAYOUT_GAP_X, 0);
        const y =
          currentY +
          block.rowHeights
            .slice(0, localRow)
            .reduce((sum, height) => sum + height + LAYOUT_GAP_Y, 0);
        positions.set(nodeKeyOf(item.node), {
          x: Math.round(x + block.columnWidths[localCol] / 2),
          y: Math.round(y + block.rowHeights[localRow] / 2),
        });
      });
      currentX += block.width + LAYOUT_BLOCK_GAP;
      rowHeight = Math.max(rowHeight, block.height);
    }
    currentY += rowHeight + LAYOUT_BLOCK_GAP;
  }
  return positions;
};

const refineTopologyLayout = (nodes, links, positions, resolveKey) => {
  if (nodes.length < 2 || !links.length) return positions;
  const particles = nodes.map((node) => {
    const key = nodeKeyOf(node);
    const seed = positions.get(key);
    return {
      key,
      chapter: String(node.chapter || "").trim() || "未分类",
      ...layoutNodeSizeOf(node),
      x: seed.x,
      y: seed.y,
      seedX: seed.x,
      seedY: seed.y,
      vx: 0,
      vy: 0,
      fx: 0,
      fy: 0,
      degree: 0,
    };
  });
  const byKey = new Map(particles.map((particle) => [particle.key, particle]));
  const pairs = new Set();
  const springs = [];
  for (const link of links) {
    const a = byKey.get(resolveKey(link.source));
    const b = byKey.get(resolveKey(link.target));
    if (!a || !b || a === b) continue;
    const pair = JSON.stringify([a.key, b.key].sort());
    // Parallel relationship types share a spring, so adding a label does not
    // increase the physical weight of the same connection.
    if (pairs.has(pair)) continue;
    pairs.add(pair);
    a.degree += 1;
    b.degree += 1;
    springs.push({ a, b });
  }
  if (!springs.length) return positions;
  const chapters = new Map();
  for (const particle of particles) {
    const group = chapters.get(particle.chapter) || { x: 0, y: 0, count: 0 };
    group.x += particle.seedX;
    group.y += particle.seedY;
    group.count += 1;
    chapters.set(particle.chapter, group);
  }
  for (const group of chapters.values()) {
    group.x /= group.count;
    group.y /= group.count;
  }
  // O(steps * nodes²), bounded at 120 steps (~2 million pairs for 185 nodes).
  // Cooling and seed anchors keep chapter regions stable without pinning the
  // original rows/columns. The solver runs only when topology is rebuilt.
  const steps = 120;
  for (let step = 0; step < steps; step += 1) {
    for (const particle of particles) {
      const group = chapters.get(particle.chapter);
      particle.fx =
        (particle.seedX - particle.x) * 0.006 + (group.x - particle.x) * 0.003;
      particle.fy =
        (particle.seedY - particle.y) * 0.006 + (group.y - particle.y) * 0.003;
    }
    for (let i = 0; i < particles.length; i += 1) {
      const a = particles[i];
      for (let j = i + 1; j < particles.length; j += 1) {
        const b = particles[j];
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const distance = Math.max(1, Math.hypot(dx, dy));
        const repel = Math.min(5, 16000 / (distance * distance));
        const fx = (dx / distance) * repel;
        const fy = (dy / distance) * repel;
        a.fx -= fx;
        a.fy -= fy;
        b.fx += fx;
        b.fy += fy;
      }
    }
    for (const { a, b } of springs) {
      const dx = b.x - a.x;
      const dy = b.y - a.y;
      const distance = Math.max(1, Math.hypot(dx, dy));
      // Directional rectangular extent reserves a readable gap for the edge.
      const extent =
        ((a.width + b.width) * Math.abs(dx) +
          (a.height + b.height) * Math.abs(dy)) /
        (2 * distance);
      const rest = Math.max(90, extent + 54);
      const chapterWeight = a.chapter === b.chapter ? 1 : 0.24;
      const strength =
        (0.075 * chapterWeight) / Math.sqrt(Math.max(a.degree, b.degree));
      const force = (distance - rest) * strength;
      const fx = (dx / distance) * force;
      const fy = (dy / distance) * force;
      a.fx += fx;
      a.fy += fy;
      b.fx -= fx;
      b.fy -= fy;
    }
    const maxStep = 10 * (1 - step / steps) + 0.5;
    for (const particle of particles) {
      particle.vx = (particle.vx + particle.fx) * 0.68;
      particle.vy = (particle.vy + particle.fy) * 0.68;
      const speed = Math.max(1, Math.hypot(particle.vx, particle.vy) / maxStep);
      particle.x += particle.vx / speed;
      particle.y += particle.vy / speed;
    }
    // Symmetric projection uses real card dimensions (including annotations).
    // Correct throughout relaxation so springs cannot build dense point piles.
    for (let i = 0; i < particles.length; i += 1) {
      const a = particles[i];
      for (let j = i + 1; j < particles.length; j += 1) {
        const b = particles[j];
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const overlapX = (a.width + b.width) / 2 + 20 - Math.abs(dx);
        const overlapY = (a.height + b.height) / 2 + 20 - Math.abs(dy);
        if (overlapX <= 0 || overlapY <= 0) continue;
        if (overlapX < overlapY) {
          const move = ((overlapX + 0.1) / 2) * (dx < 0 ? -1 : 1);
          a.x -= move;
          b.x += move;
        } else {
          const move = ((overlapY + 0.1) / 2) * (dy < 0 ? -1 : 1);
          a.y -= move;
          b.y += move;
        }
      }
    }
  }
  for (const particle of particles) {
    positions.set(particle.key, {
      x: Math.round(particle.x * 10) / 10,
      y: Math.round(particle.y * 10) / 10,
    });
  }
  return resolveNodeOverlaps(nodes, positions);
};

const computeDeterministicLayout = (
  nodes,
  links = [],
  selectedKey = "",
  resolveKey = (value) => String(value)
) => {
  if (nodes.length > 0 && nodes.length <= ORGANIC_LAYOUT_LIMIT) {
    return computeOrganicLayout(nodes, links, selectedKey, resolveKey);
  }
  return refineTopologyLayout(
    nodes,
    links,
    computeChapterLayout(nodes),
    resolveKey
  );
};

const boxesOverlap = (a, b) =>
  a.x1 < b.x2 && a.x2 > b.x1 && a.y1 < b.y2 && a.y2 > b.y1;

const computeRelationLabelIndexes = (links, positions, nodeBoxes, ctx) => {
  const indexes = new Set();
  if (!props.showRelationLabels) return indexes;
  const zoom = currentZoom.value;
  const candidates = [];
  links.forEach((link, index) => {
    const sourceKey = ctx.resolveKey(link.source);
    const targetKey = ctx.resolveKey(link.target);
    const type = normalizeRelationType(link);
    const source = positions.get(type === "dependent" ? targetKey : sourceKey);
    const target = positions.get(type === "dependent" ? sourceKey : targetKey);
    if (!source || !target) return;
    const hoverAdjacent =
      !!ctx.hoveredId &&
      (sourceKey === ctx.hoveredId || targetKey === ctx.hoveredId);
    const selectedAdjacent =
      !!ctx.selectedId &&
      (sourceKey === ctx.selectedId || targetKey === ctx.selectedId);
    const priority = hoverAdjacent ? 0 : selectedAdjacent ? 1 : 2;
    const dx = target.x - source.x;
    const dy = target.y - source.y;
    const length = Math.hypot(dx, dy);
    if (length * zoom < 24) return;
    const label = RELATION_LABEL[type] || "未知";
    const baseCurve = Number(ctx.curveOf(link)) || 0;
    const curve = link.lineStyle?.pathEmphasis
      ? 0.08
      : Math.abs(baseCurve) < 0.08
      ? 0.12
      : baseCurve;
    const width = Math.max(22, label.length * 10.5 + 10);
    const height = 18;
    // Edge labels rotate along the actual curve. Test their screen-size box at
    // the quadratic midpoint rather than the midpoint of the straight chord.
    candidates.push({
      index,
      priority,
      width: (Math.abs(dx) * width + Math.abs(dy) * height) / length / zoom,
      height: (Math.abs(dy) * width + Math.abs(dx) * height) / length / zoom,
      midX: (source.x + target.x) / 2 + (dy * curve) / 2,
      midY: (source.y + target.y) / 2 - (dx * curve) / 2,
      labelWidth: label.length,
    });
  });
  candidates.sort(
    (a, b) =>
      a.priority - b.priority ||
      a.labelWidth - b.labelWidth ||
      a.index - b.index
  );
  const placed = [];
  const limit = zoom < 0.9 ? RELATION_LABEL_MAX_COMPACT : RELATION_LABEL_MAX;
  for (const candidate of candidates) {
    if (placed.length >= limit) break;
    const box = {
      x1: candidate.midX - candidate.width / 2 - 3,
      x2: candidate.midX + candidate.width / 2 + 3,
      y1: candidate.midY - candidate.height / 2 - 2,
      y2: candidate.midY + candidate.height / 2 + 2,
    };
    if (nodeBoxes.some((nodeBox) => boxesOverlap(box, nodeBox))) {
      continue;
    }
    if (placed.some((occupied) => boxesOverlap(box, occupied))) continue;
    placed.push(box);
    indexes.add(candidate.index);
  }
  return indexes;
};

const buildSeries = ({ rebuild = false } = {}) => {
  // 防御：按 id/name 去重节点，并过滤掉指向不存在节点的连线。
  // 重复 id 或悬空连线会让 ECharts graph series 在
  // createGraphFromNodeEdge 中抛 "Cannot set properties of undefined
  // (setting 'dataIndex')"，错误发生在 watcher 回调里会杀死渲染线程，
  // 页面从此冻结（转圈不停、按钮无响应）。
  const seen = new Set();
  const safeNodes = chartData.nodes.filter((node) => {
    const key = nodeKeyOf(node);
    if (!key || seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
  const nodeKeys = new Set(safeNodes.map((node) => nodeKeyOf(node)));
  const resolveKey = createKeyResolver(safeNodes);
  const safeLinks = chartData.links.filter(
    (link) =>
      nodeKeys.has(resolveKey(link.source)) &&
      nodeKeys.has(resolveKey(link.target))
  );
  // 掌握状态条/圆点：数据里没有任何 status（如未登录的结构预览）时自动隐藏
  const showStatusUI =
    props.showStatus && safeNodes.some((node) => !!node.status);
  // log3 B.5：选中 id 只有仍在当前可见节点集合内才生效；
  // 筛选把选中节点过滤掉后按"无选中"处理，不把高亮残留到隐藏节点。
  // 兼容旧消费方传原始 id/name：先经同一别名表归一化到 nodeKey。
  const requestedSelectedId = resolveKey(props.selectedId);
  const selectedId =
    props.selectedId && nodeKeys.has(requestedSelectedId)
      ? requestedSelectedId
      : "";
  const idName = new Map(
    safeNodes.map((node) => [
      nodeKeyOf(node),
      node.displayName || node.name || "",
    ])
  );
  const neighborIds = new Set();
  if (selectedId) {
    for (const link of safeLinks) {
      const sourceKey = resolveKey(link.source);
      const targetKey = resolveKey(link.target);
      if (sourceKey === selectedId) {
        neighborIds.add(targetKey);
      }
      if (targetKey === selectedId) {
        neighborIds.add(sourceKey);
      }
    }
  }
  // 稳定轻微曲率：同一对节点的多条边按出现序递增曲率（确定性，避免重叠）
  const pairCount = new Map();
  const curves = new Map();
  safeLinks.forEach((link) => {
    const a = resolveKey(link.source);
    const b = resolveKey(link.target);
    const key = a < b ? `${a}|${b}` : `${b}|${a}`;
    const n = pairCount.get(key) || 0;
    pairCount.set(key, n + 1);
    curves.set(link, n === 0 ? 0 : Math.min(0.08 * n, 0.24));
  });
  const curveOf = (link) => curves.get(link) || 0;
  const ctx = {
    selectedId,
    neighborIds,
    showStatusUI,
    showRelationLabels: props.showRelationLabels,
    fillByStatus: props.fillByStatus,
    compactLabels: currentZoom.value < NODE_LABEL_COMPACT_ZOOM,
    hoveredId: hoveredNodeId.value,
    labelIndexes: new Set(),
    curveOf,
    resolveKey,
    nameOf: (id) => idName.get(resolveKey(id)) || "",
  };

  let positions = new Map();
  // Visual changes must use the coordinates already on screen. Changing the
  // selection never moves the graph or uses a different layout to place labels.
  if (!rebuild && myChart) {
    const existing = myChart.getModel()?.getSeriesByIndex(0)?.getData();
    existing?.each((index) => {
      const key = existing.getId(index);
      const point = existing.getItemLayout(index);
      if (nodeKeys.has(key) && point?.every(Number.isFinite)) {
        positions.set(key, { x: point[0], y: point[1] });
      }
    });
  }
  if (positions.size !== safeNodes.length) {
    positions = computeDeterministicLayout(
      safeNodes,
      safeLinks,
      selectedId,
      resolveKey
    );
  }
  const nodeBoxes = safeNodes.map((node) => {
    const point = positions.get(nodeKeyOf(node)) || { x: 0, y: 0 };
    const size = layoutNodeSizeOf(node);
    return {
      key: nodeKeyOf(node),
      x1: point.x - size.width / 2 - 3,
      y1: point.y - size.height / 2 - 3,
      x2: point.x + size.width / 2 + 3,
      y2: point.y + size.height / 2 + 3,
    };
  });
  ctx.labelIndexes = computeRelationLabelIndexes(
    safeLinks,
    positions,
    nodeBoxes,
    ctx
  );

  const data = safeNodes.map((node) => {
    const style = buildNodeStyle(node, ctx);
    const position = positions.get(nodeKeyOf(node)) || { x: 0, y: 0 };
    style.x = position.x;
    style.y = position.y;
    return style;
  });
  renderedNodeByKey = new Map(data.map((node) => [node.id, node]));
  const xs = data.map((node) => node.x);
  const ys = data.map((node) => node.y);

  return {
    id: "oslab-knowledge-graph",
    type: "graph",
    // 从现有章节坐标播种，运行真正的力布局；拖动时由 ECharts 重新加热。
    layout: "force",
    draggable: true,
    force: {
      initLayout: "none",
      repulsion: data.length <= 12 ? 500 : 1400,
      edgeLength: [150, 220],
      gravity: 0.04,
      friction: 0.18,
      layoutAnimation: true,
    },
    roam: true,
    // Keep one layout unit equal to one pixel before roam. Otherwise ECharts
    // stretches center coordinates to the container independently of symbol
    // sizes, undoing every spacing and collision calculation above.
    left: "center",
    top: "middle",
    width: data.length ? Math.max(2, Math.max(...xs) - Math.min(...xs)) : 2,
    height: data.length ? Math.max(2, Math.max(...ys) - Math.min(...ys)) : 2,
    // 节点与标签随画布同比缩放，相对间距恒定，任何缩放级别都不会互相覆盖。
    nodeScaleRatio: 1,
    scaleLimit: { min: 0.05, max: 4 },
    data,
    links: safeLinks.map((link, index) => buildLink(link, ctx, index)),
    label: { show: true },
    // log3 B.3：focus = "none" 关闭 ECharts 的 adjacency blur（全图默认
    // 虚化的来源）。hover 只强调被悬停元素本身；选中态的主蓝描边与邻接
    // 强调由本组件按节点/边数据逐项绘制，两层不再叠加。
    emphasis: {
      focus: "none",
      scale: false,
    },
    edgeLabel: {
      fontSize: 10.5,
      lineHeight: 14,
      rotate: 0,
      color: MUTED_STRONG,
      backgroundColor: GRAPH_TOKENS.labelSurface,
      padding: [1, 4],
    },
    edgeSymbol: ["none", "arrow"],
    edgeSymbolSize: [0, 8],
  };
};

const readZoom = () => {
  if (!myChart) return currentZoom.value;
  const option = myChart.getOption();
  const zoom = Number(option?.series?.[0]?.zoom);
  return Number.isFinite(zoom) && zoom > 0 ? zoom : currentZoom.value;
};

const syncZoom = () => {
  const zoom = readZoom();
  if (Math.abs(zoom - currentZoom.value) < 0.0001) return;
  currentZoom.value = zoom;
  if (zoomFrame) return;
  zoomFrame = window.requestAnimationFrame(() => {
    zoomFrame = 0;
    // Rich labels use screen pixels: refresh on every zoom, not just when a
    // threshold is crossed. Panning keeps the same labels and costs no refresh.
    updateChart();
  });
};

const handleNodeMouseOver = (params) => {
  if (params?.dataType !== "node" || !props.showRelationLabels) return;
  const key = nodeKeyOf(params.data);
  if (!key || key === hoveredNodeId.value) return;
  hoveredNodeId.value = key;
  updateChart();
};

const handleNodeMouseOut = (params) => {
  if (params?.dataType !== "node" || !props.showRelationLabels) return;
  if (!hoveredNodeId.value) return;
  hoveredNodeId.value = "";
  updateChart();
};

const initChart = () => {
  myChart = echarts.init(chart.value);
  myChart.on("click", handleChartClick);
  myChart.on("dblclick", handleNodeDblClick);
  myChart.on("graphRoam", syncZoom);
  myChart.on("mouseover", handleNodeMouseOver);
  myChart.on("mouseout", handleNodeMouseOut);
  myChart.on("rendered", handleLayoutRendered);
  lastTopologySignature = graphTopologySignature();
  updateChart({ rebuild: true });
};

const handleLayoutRendered = () => {
  const series = myChart?.getModel()?.getSeriesByIndex(0);
  if (!series?.forceLayout) return;
  avoidRenderedLabelOverlaps();
  if (series.__oslabForceSettled && fittedSeries !== series) {
    fittedSeries = series;
    if (!layoutFrame)
      layoutFrame = window.requestAnimationFrame(() => {
        layoutFrame = 0;
        fitCurrentLayout();
      });
  } else if (!series.__oslabForceSettled && !labelRefresh) {
    labelRefresh = window.setTimeout(() => {
      labelRefresh = 0;
      if (myChart) updateChart();
    }, 120);
  }
};

// Force motion and endpoint clipping can shift an edge label several pixels.
// Check the final screen rectangles as well as the predictive label placement.
const avoidRenderedLabelOverlaps = () => {
  const series = myChart?.getModel()?.getSeriesByIndex(0);
  if (!series) return;
  const rectOf = (element) => {
    const rect = element.getBoundingRect().clone();
    rect.applyTransform(element.getComputedTransform());
    return rect;
  };
  const cards = [];
  series.getData().eachItemGraphicEl((element) => {
    const card = element.childAt(0);
    if (card) cards.push(rectOf(card));
  });
  const placed = [];
  series.getEdgeData().eachItemGraphicEl((element) => {
    const label = element.getTextContent();
    if (!label || label.ignore || !label.style.text) return;
    const rect = rectOf(label);
    const overlaps = (other) =>
      rect.x < other.x + other.width + 2 &&
      rect.x + rect.width + 2 > other.x &&
      rect.y < other.y + other.height + 2 &&
      rect.y + rect.height + 2 > other.y;
    if (cards.some(overlaps) || placed.some(overlaps)) label.hide();
    else placed.push(rect);
  });
};

const clickPayloadOf = (params) => {
  const key = nodeKeyOf(params?.data);
  return renderedNodeByKey.get(key) || params?.data || null;
};

const handleChartClick = (params) => {
  // 点击空白画布或关系线不改变选中态
  if (params?.dataType !== "node") {
    return;
  }
  if (clickTimer) {
    window.clearTimeout(clickTimer);
  }
  clickTimer = window.setTimeout(() => {
    clickTimer = null;
    // 单击只选中节点（右栏展示详情），不重新请求整图
    const payload = clickPayloadOf(params);
    if (payload) emit("node-click", { ...payload });
  }, CLICK_DELAY);
};

const handleNodeDblClick = (params) => {
  if (params?.dataType !== "node") {
    return;
  }
  if (clickTimer) {
    window.clearTimeout(clickTimer);
    clickTimer = null;
  }
  const payload = clickPayloadOf(params);
  if (payload) emit("node-dblclick", { ...payload });
};

// rebuild=true：数据/拓扑变化，notMerge 全量重建并重新适配视野；
// rebuild=false：选中/脉冲/标签开关只刷新视觉，保留布局与视野。

// 视野适配：使用已渲染节点的像素包围盒，包含节点的真实尺寸，
// 用公开 graphRoam 的 zoom + dx/dy 完成缩放与居中。
const fitCurrentLayout = () => {
  if (!myChart || !myChart.getWidth() || !myChart.getHeight()) return;
  const series = myChart.getModel().getSeriesByIndex(0);
  if (!series) return;
  const data = series.getData();
  if (!data.count()) return;
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  data.each((index) => {
    const symbol = data.getItemGraphicEl(index)?.childAt(0);
    if (!symbol) return;
    const rect = symbol.getBoundingRect().clone();
    rect.applyTransform(symbol.getComputedTransform());
    if (![rect.x, rect.y, rect.width, rect.height].every(Number.isFinite))
      return;
    minX = Math.min(minX, rect.x);
    minY = Math.min(minY, rect.y);
    maxX = Math.max(maxX, rect.x + rect.width);
    maxY = Math.max(maxY, rect.y + rect.height);
  });
  if (!Number.isFinite(minX)) return;
  const centerX = (minX + maxX) / 2;
  const centerY = (minY + maxY) / 2;
  const pixelWidth = Math.max(1, maxX - minX);
  const pixelHeight = Math.max(1, maxY - minY);
  const availW = Math.max(1, myChart.getWidth() - 40);
  const availH = Math.max(1, myChart.getHeight() - 40);
  const current = readZoom();
  const ratio = Math.min(availW / pixelWidth, availH / pixelHeight);
  const targetZoom = Math.max(0.05, Math.min(1.3, current * ratio));
  const zoomRatio = targetZoom / Math.max(0.0001, current);
  currentZoom.value = targetZoom;
  myChart.dispatchAction({
    type: "graphRoam",
    seriesIndex: 0,
    zoom: zoomRatio,
    originX: myChart.getWidth() / 2,
    originY: myChart.getHeight() / 2,
  });
  // 缩放围绕画布中心；再平移到内容中心。dx/dy 与 ECharts roam 的
  // 拖拽语义一致：content 移动量 = 画布中心 − 当前中心像素。
  {
    const dx = (myChart.getWidth() / 2 - centerX) * zoomRatio;
    const dy = (myChart.getHeight() / 2 - centerY) * zoomRatio;
    if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) {
      myChart.dispatchAction({
        type: "graphRoam",
        seriesIndex: 0,
        dx,
        dy,
      });
    }
  }
  updateChart();
};

const updateChart = ({ rebuild = false } = {}) => {
  const option = {
    backgroundColor: GRAPH_TOKENS.surface,
    tooltip: {
      trigger: "item",
      confine: true,
      extraCssText: `
      max-width: 320px;
      white-space: normal !important;
      word-wrap: break-word !important;
    `,
      formatter: (params) => {
        if (params.dataType === "node") {
          const node =
            renderedNodeByKey.get(nodeKeyOf(params.data)) || params.data || {};
          const parts = [];
          const chapter = String(node.chapter || "").trim();
          if (chapter) parts.push(`章节：${escapeHtml(chapter)}`);
          if (node.nodeType || node.category)
            parts.push(`类型：${escapeHtml(nodeTypeTextOf(node))}`);
          if (props.showStatus && node.status) {
            parts.push(`状态：${STATUS_TEXT[node.status] || "未学习"}`);
          }
          let html = `<div style="font-weight:600;margin-bottom:4px;">${escapeHtml(
            node.displayName || node.name
          )}</div>`;
          if (parts.length) {
            html += `<div style="margin-bottom:4px;">${parts.join(
              " · "
            )}</div>`;
          }
          if (node.description) {
            html += `<div style="color:${MUTED_STRONG};">${escapeHtml(
              node.description
            )}</div>`;
          }
          return html;
        }
        if (params.dataType === "edge") {
          return (params.data && params.data.relationText) || params.name || "";
        }
        return params.name || "";
      },
    },
    series: [buildSeries({ rebuild })],
  };
  if (rebuild) {
    myChart.setOption(option, { notMerge: true });
    fitCurrentLayout();
  } else {
    // updateVisual 不会自动清理 ECharts 的 hover/emphasis 状态；
    // 先显式 downplay 全部节点与关系，再应用新的选中/状态样式，
    // 保证 hover→click→mouseout 与快速 A→B 切换不残留旧强调。
    myChart.dispatchAction({ type: "downplay", seriesIndex: 0 });
    myChart.dispatchAction({
      type: "downplay",
      seriesIndex: 0,
      dataType: "edge",
    });
    myChart.dispatchAction({
      type: "oslabGraphStyle",
      seriesIndex: 0,
      nodes: option.series[0].data,
      links: option.series[0].links,
    });
  }
  avoidRenderedLabelOverlaps();
};

// 选中节点变化时只刷新高亮，不重算布局
watch(
  () => props.selectedId,
  () => {
    if (myChart) {
      updateChart();
    }
  }
);

// 关系标签开关变化时只刷新标签，不重算布局
watch(
  () => props.showRelationLabels,
  () => {
    if (myChart) {
      updateChart();
    }
  }
);

// 批注映射变化时只刷新标签，不重算布局
watch(
  () => props.annotations,
  () => {
    if (myChart) {
      updateChart();
    }
  },
  { deep: true }
);

// 拓扑签名（log3 收尾 E）：节点 id + 连线 + 类别的可序列化指纹。
// 签名不变（仅状态/样式叠加更新：回放步进、班级聚合着色刷新）时保留当前
// 视野；签名变化（筛选、切换数据源）才复位全景 —— 既不破坏既有消费方的
// "新数据全景展示"，又避免回放/播放每步把用户视野强制拉回默认。
const graphTopologySignature = () => {
  const nodes = props.nodes || [];
  const links = props.links || [];
  const categories = props.categories || [];
  const resolveKey = createKeyResolver(nodes);
  return [
    nodes.map((node) => nodeKeyOf(node)).join(","),
    links
      .map(
        (link) =>
          `${resolveKey(link.source)}>${resolveKey(link.target)}:${String(
            link.relationType ?? link.value ?? ""
          )}`
      )
      .join(","),
    categories.map((category) => String(category.name ?? "")).join(","),
  ].join("|");
};
let lastTopologySignature = "";

// 数据变化时重建视图；仅拓扑变化才复位视野
watch(
  () => [props.nodes, props.links, props.categories],
  () => {
    if (!myChart) return;
    applyGraphState();
  },
  { deep: true }
);

const applyGraphState = () => {
  const signature = graphTopologySignature();
  const rebuild = signature !== lastTopologySignature;
  lastTopologySignature = signature;
  const next = cloneProps();
  chartData.nodes = next.nodes;
  chartData.links = next.links;
  chartData.categories = next.categories;
  // 被筛掉的悬停节点不得继续驱动关系标签。
  if (
    hoveredNodeId.value &&
    !next.nodes.some((node) => nodeKeyOf(node) === hoveredNodeId.value)
  ) {
    hoveredNodeId.value = "";
  }
  updateChart({ rebuild });
};

// 重置当前可见节点的视野，保留布局；百分比中心不适用于图数据坐标。
const resetView = () => {
  fitCurrentLayout();
};

// ---------- 画布尺寸自适应（log3 收尾 B.7） ----------
// ResizeObserver 监听容器宽高变化（窗口缩放/侧栏折叠/详情展开），在下一帧
// 窗口变化后重新适配当前可见图谱；rAF 合并同一帧内的多次回调。
let resizeObserver = null;
let resizeFrame = 0;
const handleContainerResize = () => {
  if (resizeFrame) return;
  resizeFrame = window.requestAnimationFrame(() => {
    resizeFrame = 0;
    if (myChart) {
      myChart.resize();
      fitCurrentLayout();
    }
  });
};

onMounted(() => {
  initChart();
  if (typeof ResizeObserver !== "undefined" && chart.value) {
    resizeObserver = new ResizeObserver(handleContainerResize);
    resizeObserver.observe(chart.value);
  }
});

// resetView 为规范名称；fit/reset 是历史别名（同一动作），保持外部调用兼容
// eslint-disable-next-line no-undef
defineExpose({ resetView, fit: resetView, reset: resetView });

onBeforeUnmount(() => {
  if (layoutFrame) window.cancelAnimationFrame(layoutFrame);
  if (labelRefresh) window.clearTimeout(labelRefresh);
  if (zoomFrame) {
    window.cancelAnimationFrame(zoomFrame);
    zoomFrame = 0;
  }
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  if (resizeFrame) {
    window.cancelAnimationFrame(resizeFrame);
    resizeFrame = 0;
  }
  if (clickTimer) {
    window.clearTimeout(clickTimer);
    clickTimer = null;
  }
  if (pulseTimer) {
    clearTimeout(pulseTimer);
    pulseTimer = null;
  }
  if (myChart) {
    myChart.off("click", handleChartClick);
    myChart.off("dblclick", handleNodeDblClick);
    myChart.off("graphRoam", syncZoom);
    myChart.off("mouseover", handleNodeMouseOver);
    myChart.off("mouseout", handleNodeMouseOut);
    myChart.off("rendered", handleLayoutRendered);
    myChart.dispose();
    myChart = null;
  }
});
</script>

<style scoped>
.knowledge-graph-container {
  width: 100%;
  height: 100%;
}

.knowledge-graph-chart {
  width: 100%;
  /* log3 收尾 B.9：稳定高度约束 —— 随视口在 480-840px 间伸缩，
     不随节点/标签/详情内容抖动；窄视口仍有可用的最小画布 */
  height: clamp(480px, calc(100vh - 340px), 840px);
}
</style>
