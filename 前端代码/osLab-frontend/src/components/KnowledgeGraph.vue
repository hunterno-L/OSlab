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

<script setup>
import { ref, reactive, watch, onMounted, onBeforeUnmount } from "vue";
import * as echarts from "echarts";

// 仅刷新节点/关系样式；布局为确定性坐标，不经过布局流水线。
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

// 类别底色和掌握状态独立表达；dependent 连线反向绘制为学习顺序。
// 确定性分组布局，拓扑变化才重建；节点、标签与边按同一缩放比例变换。
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
let lastZoomBand = 1;

// ---------- 设计令牌（learning-theme.css 图谱 token 的逐项镜像） ----------
// ECharts 画布/SSR 字符串无法读取 CSS 变量，因此在这里保留唯一一份
// 画布内实色；每个值都能在 learning-theme.css 的 --oslab-graph-* /
// --oslab-mastered 等 token 中找到同名来源，测试会逐项比对，禁止在
// 组件其他位置新增裸色值。
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
  nodeFill: Object.freeze({
    concept: "#f8faff",
    algorithm: "#fbf8ff",
    function: "#f6fbf9",
    fallback: "#fafbf7",
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
  textSoft: "#6b7168",
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
    GRAY: "#eeefe9",
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
// 节点只识别概念并表达掌握状态：固定三档尺寸（小图 132–156，大图 112–128），
// 名称最多两行、超出省略；类型说明交给图例和右侧详情。尺寸只由稳定的
// nodeType 决定，不随名称长度或每次渲染抖动。
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
  const fixed = (
    (props.nodes?.length ?? 0) <= NODE_AUTO_WIDTH_LIMIT
      ? NODE_TIERS_ROOMY
      : NODE_TIERS
  )[nodeTierOf(node)];
  if ((props.nodes?.length ?? 0) > NODE_AUTO_WIDTH_LIMIT) return fixed;
  const label = String(node.displayName || node.name || "");
  const len = label.length;
  const width =
    len <= 2 ? 96 : len <= 4 ? 120 : len <= 6 ? 150 : len <= 8 ? 180 : 210;
  return { width, height: fixed.height };
};

const buildLabel = (ctx) => {
  // 名称始终使用正文色，掌握状态只用状态圆点表达；弱状态不把标题变淡。
  // 小图缩放较小时切到紧凑模式：只保留名称、隐藏编号/圆点，
  // 避免屏幕尺寸文字挤爆已缩小的索引卡。
  const compact = !!ctx.compactLabels;
  const dotWidth = compact ? 0 : ctx.showStatusUI ? STATUS_DOT_SPACE : 0;
  const numWidth = compact ? 0 : ctx.annotationNumber ? 20 : 0;
  const labelWidth = compact
    ? Math.max(24, ctx.width * currentZoom.value - 4)
    : ctx.width - ((props.nodes?.length ?? 0) <= 40 ? 32 : 16);
  const nameWidth = compact
    ? Math.max(24, (labelWidth - 8) / 0.8)
    : labelWidth - 8 - dotWidth - numWidth;
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
      fontSize: compact ? 10 : 12.5,
      lineHeight: compact ? 12 : 15,
      align: "left",
      fontWeight: ctx.isSelected ? 600 : 500,
      backgroundColor: ctx.isSelected
        ? GRAPH_TOKENS.highlightSoft
        : "transparent",
      padding: ctx.isSelected ? (compact ? [0, 1] : [1, 3]) : 0,
    },
  };
  const numPrefix =
    !compact && ctx.annotationNumber ? `{num|${ctx.annotationNumber}} ` : "";
  const dot = !compact && ctx.showStatusUI ? "{dot|●}" : "";
  const text = `${numPrefix}${dot}{name|${displayName}}`;
  return {
    show: ctx.labelsVisible,
    position: "inside",
    align: "center",
    verticalAlign: "middle",
    width: labelWidth,
    padding: compact ? [2, 2] : [NODE_PADDING_Y, 0],
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
  const baseSize = nodeSizeOf(node);
  const annotationCount = Object.keys(props.annotations || {}).length;
  const allNumbered =
    annotationCount > 0 && annotationCount === (props.nodes?.length ?? 0);
  const annotationBump = allNumbered ? 20 : 32;
  const size = annotationNumber
    ? {
        width: Math.min(240, baseSize.width + annotationBump),
        height: baseSize.height,
      }
    : baseSize;
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
      labelsVisible: ctx.labelsVisible,
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
      offset: [0, -8],
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

// ---------- 确定性分组布局 ----------
// 章节是稳定的空间分组；章节内按 sectionCode 与输入顺序排列，章节块按
// 画布比例做行列装箱。没有随机播种，也没有异步力导向，筛选/回放不抖动。
const NODE_LABEL_MIN_ZOOM = 0.72;
const RELATION_LABEL_MIN_ZOOM = 0.25;
const RELATION_LABEL_ZOOM = 0.25;
const RELATION_LABEL_DENSE_ZOOM = 1.6;
const RELATION_LABEL_MAX = 36;
const RELATION_LABEL_MAX_COMPACT = 18;
const LAYOUT_CELL_W = 140;
const LAYOUT_CELL_H = 50;
const LAYOUT_BLOCK_GAP = 0.35;

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
  const rootX = -160;
  const ring1X = 215 * scaleX;
  const ring1Y = 140;
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
  const ring2X = 340 * scaleX;
  const ring2Y = 225;
  rest.forEach((key, index) => {
    const angle = (index / Math.max(1, rest.length)) * Math.PI * 2 + 0.42;
    const wobble = 1 + 0.06 * Math.sin((index + 2) * 2.1);
    positions.set(key, {
      x: Math.round(rootX + Math.cos(angle) * ring2X * wobble),
      y: Math.round(Math.sin(angle) * ring2Y * wobble),
    });
  });
  return positions;
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
    return {
      chapter,
      items,
      cols,
      rows: Math.ceil(items.length / cols),
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
        rowWidth += block.cols + LAYOUT_BLOCK_GAP;
        rowHeight = Math.max(rowHeight, block.rows);
      }
      totalWidth = Math.max(totalWidth, rowWidth - LAYOUT_BLOCK_GAP);
      totalHeight += rowHeight + LAYOUT_BLOCK_GAP;
    }
    totalHeight -= LAYOUT_BLOCK_GAP;
    const actualAspect =
      (Math.max(1, totalWidth) * LAYOUT_CELL_W) /
      (Math.max(1, totalHeight) * LAYOUT_CELL_H);
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
        positions.set(nodeKeyOf(item.node), {
          x: Math.round((currentX + localCol) * LAYOUT_CELL_W),
          y: Math.round((currentY + localRow) * LAYOUT_CELL_H),
        });
      });
      currentX += block.cols + LAYOUT_BLOCK_GAP;
      rowHeight = Math.max(rowHeight, block.rows);
    }
    currentY += rowHeight + LAYOUT_BLOCK_GAP;
  }
  return positions;
};

const boxesOverlap = (a, b) =>
  a.x1 < b.x2 && a.x2 > b.x1 && a.y1 < b.y2 && a.y2 > b.y1;

const computeRelationLabelIndexes = (links, positions, nodeBoxes, ctx) => {
  const indexes = new Set();
  if (
    !props.showRelationLabels ||
    currentZoom.value < RELATION_LABEL_MIN_ZOOM
  ) {
    return indexes;
  }
  const zoom = currentZoom.value;
  const candidates = [];
  links.forEach((link, index) => {
    const sourceKey = ctx.resolveKey(link.source);
    const targetKey = ctx.resolveKey(link.target);
    const source = positions.get(sourceKey);
    const target = positions.get(targetKey);
    if (!source || !target) return;
    const hoverAdjacent =
      !!ctx.hoveredId &&
      (sourceKey === ctx.hoveredId || targetKey === ctx.hoveredId);
    const selectedAdjacent =
      !!ctx.selectedId &&
      (sourceKey === ctx.selectedId || targetKey === ctx.selectedId);
    let priority = 99;
    if (hoverAdjacent) {
      priority = 0;
    } else if (selectedAdjacent) {
      priority = 1;
    } else if (
      !ctx.selectedId &&
      !ctx.hoveredId &&
      zoom >= RELATION_LABEL_ZOOM
    ) {
      priority = 2;
    } else if (zoom >= RELATION_LABEL_DENSE_ZOOM) {
      priority = 3;
    }
    if (priority === 99) return;
    if (Math.hypot(target.x - source.x, target.y - source.y) < 24) return;
    const type = normalizeRelationType(link);
    const label = RELATION_LABEL[type] || "未知";
    const labelScale = Math.max(0.5, zoom);
    candidates.push({
      index,
      priority,
      width: Math.max(22, label.length * 10.5 + 10) / labelScale,
      height: 16 / labelScale,
      sourceKey,
      targetKey,
      midX: (source.x + target.x) / 2,
      midY: (source.y + target.y) / 2,
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
    if (candidate.priority >= 2 && placed.length >= limit) break;
    const box = {
      x1: candidate.midX - candidate.width / 2 - 3,
      x2: candidate.midX + candidate.width / 2 + 3,
      y1: candidate.midY - candidate.height / 2 - 2,
      y2: candidate.midY + candidate.height / 2 + 2,
    };
    if (
      nodeBoxes.some(
        (nodeBox) =>
          nodeBox.key !== candidate.sourceKey &&
          nodeBox.key !== candidate.targetKey &&
          boxesOverlap(box, nodeBox)
      )
    ) {
      continue;
    }
    if (placed.some((occupied) => boxesOverlap(box, occupied))) continue;
    placed.push(box);
    indexes.add(candidate.index);
  }
  return indexes;
};

const buildSeries = () => {
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
  const curveOf = (link) => {
    const a = resolveKey(link.source);
    const b = resolveKey(link.target);
    const key = a < b ? `${a}|${b}` : `${b}|${a}`;
    const n = pairCount.get(key) || 0;
    pairCount.set(key, n + 1);
    return n === 0 ? 0 : Math.min(0.08 * n, 0.24);
  };
  const ctx = {
    selectedId,
    neighborIds,
    showStatusUI,
    showRelationLabels: props.showRelationLabels,
    fillByStatus: props.fillByStatus,
    labelsVisible:
      currentZoom.value >=
      ((props.nodes?.length ?? 0) <= 12 ? 0.35 : NODE_LABEL_MIN_ZOOM),
    compactLabels:
      (props.nodes?.length ?? 0) <= 12 &&
      currentZoom.value < NODE_LABEL_MIN_ZOOM,
    hoveredId: hoveredNodeId.value,
    labelIndexes: new Set(),
    curveOf,
    resolveKey,
    nameOf: (id) => idName.get(resolveKey(id)) || "",
  };

  const positions = computeDeterministicLayout(
    safeNodes,
    safeLinks,
    selectedId,
    resolveKey
  );
  const nodeBoxes = safeNodes.map((node) => {
    const point = positions.get(nodeKeyOf(node)) || { x: 0, y: 0 };
    const size = nodeSizeOf(node);
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

  return {
    id: "oslab-knowledge-graph",
    type: "graph",
    // 确定性分组/分层坐标；不再使用随机力导向，筛选/回放不改变相对布局规则。
    layout: "none",
    draggable: false,
    roam: true,
    // 节点与标签随画布同比缩放，相对间距恒定，任何缩放级别都不会互相覆盖。
    nodeScaleRatio: 1,
    scaleLimit: { min: 0.05, max: 4 },
    data,
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

const zoomBandOf = (zoom) => {
  if (zoom < RELATION_LABEL_MIN_ZOOM) return 0;
  if (zoom < NODE_LABEL_MIN_ZOOM) return 1;
  if (zoom < RELATION_LABEL_DENSE_ZOOM) return 2;
  return 3;
};

const readZoom = () => {
  if (!myChart) return currentZoom.value;
  const option = myChart.getOption();
  const zoom = Number(option?.series?.[0]?.zoom);
  return Number.isFinite(zoom) && zoom > 0 ? zoom : currentZoom.value;
};

const syncZoom = () => {
  const zoom = readZoom();
  currentZoom.value = zoom;
  const band = zoomBandOf(zoom);
  if (band !== lastZoomBand) {
    lastZoomBand = band;
    // 缩放跨过标签层级时只刷新视觉，不改坐标与视野。
    updateChart();
  }
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
  lastTopologySignature = graphTopologySignature();
  updateChart({ rebuild: true });
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

// 视野适配：基于确定性坐标计算内容包围盒（含节点尺寸），
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
  let maxHalfWidth = 0;
  let maxHalfHeight = 0;
  data.each((index) => {
    const point = data.getItemLayout(index);
    if (!point || !point.every(Number.isFinite)) return;
    minX = Math.min(minX, point[0]);
    minY = Math.min(minY, point[1]);
    maxX = Math.max(maxX, point[0]);
    maxY = Math.max(maxY, point[1]);
    const size = data.getItemVisual(index, "symbolSize");
    if (Array.isArray(size)) {
      maxHalfWidth = Math.max(maxHalfWidth, size[0] / 2);
      maxHalfHeight = Math.max(maxHalfHeight, size[1] / 2);
    }
  });
  if (!Number.isFinite(minX)) return;
  const centerX = (minX + maxX) / 2;
  const centerY = (minY + maxY) / 2;
  // 节点随缩放同比变化，按数据单位把节点半径并入包围盒。
  const p1 = myChart.convertToPixel({ seriesIndex: 0 }, [
    minX - maxHalfWidth,
    minY - maxHalfHeight,
  ]);
  const p2 = myChart.convertToPixel({ seriesIndex: 0 }, [
    maxX + maxHalfWidth,
    maxY + maxHalfHeight,
  ]);
  if (!p1 || !p2 || ![...p1, ...p2].every(Number.isFinite)) return;
  const pixelWidth = Math.max(1, Math.abs(p2[0] - p1[0]));
  const pixelHeight = Math.max(1, Math.abs(p2[1] - p1[1]));
  const availW = Math.max(1, myChart.getWidth() - 24);
  const availH = Math.max(1, myChart.getHeight() - 24);
  const current = readZoom();
  const ratio = Math.min(availW / pixelWidth, availH / pixelHeight);
  const targetZoom = Math.max(0.05, Math.min(1.45, current * ratio));
  const zoomRatio = targetZoom / Math.max(0.0001, current);
  const previousBand = zoomBandOf(currentZoom.value);
  currentZoom.value = targetZoom;
  lastZoomBand = zoomBandOf(targetZoom);
  myChart.dispatchAction({
    type: "graphRoam",
    seriesIndex: 0,
    zoom: zoomRatio,
    originX: myChart.getWidth() / 2,
    originY: myChart.getHeight() / 2,
  });
  // 适配可能跨过标签可读阈值：同步刷新标签显示层级（不改变布局与视野）。
  if (lastZoomBand !== previousBand) {
    updateChart();
  }
  // 缩放围绕画布中心；再平移到内容中心。dx/dy 与 ECharts roam 的
  // 拖拽语义一致：content 移动量 = 画布中心 − 当前中心像素。
  const centerPixel = myChart.convertToPixel({ seriesIndex: 0 }, [
    centerX,
    centerY,
  ]);
  if (centerPixel && centerPixel.every(Number.isFinite)) {
    const dx = myChart.getWidth() / 2 - centerPixel[0];
    const dy = myChart.getHeight() / 2 - centerPixel[1];
    if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) {
      myChart.dispatchAction({
        type: "graphRoam",
        seriesIndex: 0,
        dx,
        dy,
      });
    }
  }
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
    series: [buildSeries()],
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
