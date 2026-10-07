<template>
  <div class="knowledge-graph-container">
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

// setOption 会重新初始化 force；视野操作只更新坐标变换和视图，保留已收敛布局。
echarts.registerAction(
  { type: "oslabGraphFit", event: "oslabGraphFit", update: "none" },
  (payload, model, api) => {
    model.eachComponent({ mainType: "series", query: payload }, (series) => {
      const view = series.coordinateSystem;
      if (!view || view.type !== "view") return;
      view.setCenter(payload.center, api);
      view.setZoom(payload.zoom);
      series.setCenter(view.getCenter());
      series.setZoom(view.getZoom());
      const chartView = api.getViewOfSeriesModel(series);
      chartView.group.attr({
        x: view.x,
        y: view.y,
        scaleX: view.scaleX,
        scaleY: view.scaleY,
      });
      series.getData().eachItemGraphicEl((symbol) => {
        if (symbol) symbol.setSymbolScale(1 / view.scaleX);
      });
      // 与 ECharts 5.6 graph 的原生 roam 一致，同步下一次滚轮缩放的基准。
      chartView._controllerHost.zoom = view.getZoom();
      chartView.updateLayout(series);
    });
  }
);

// 仅刷新节点/关系样式，不经过 setOption 的布局流水线重新启动 force。
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
// 力导向异步布局，拓扑变化才重建。节点和文字保持屏幕尺寸一致，
// 不使用 labelLayout：其绝对标签定位会与力导向移动、边标签缩放冲突。

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
const CLICK_DELAY = 220;

// ---------- 设计令牌（与 learning-theme.css 同源的画布内实色） ----------
const PRIMARY = "#2059c9";
const REVIEW = "#b98239";
const INK_SOFT = "#37352f";
const MUTED_STRONG = "#757069";
const NODE_BORDER = "rgba(0, 0, 0, 0.15)";

// 课程类别 → 节点底色（§5.2）；按关键词匹配中英文类别名，未标注兜底
const CATEGORY_FILLS = [
  { keys: ["概念", "concept"], fill: "#eaf0fd" },
  { keys: ["算法", "algorithm"], fill: "#f6eddf" },
  { keys: ["功能", "机制", "模块", "function"], fill: "#e9f1ed" },
];
const CATEGORY_FALLBACK_FILL = "#f0f2f0";

const categoryFillOf = (category) => {
  const c = String(category || "")
    .trim()
    .toLowerCase();
  for (const def of CATEGORY_FILLS) {
    if (def.keys.some((k) => c.includes(k))) return def.fill;
  }
  return CATEGORY_FALLBACK_FILL;
};

// 个人掌握状态色（§5.2；只表达状态，不用于装饰）
const STATUS_COLORS = {
  GREEN: "#4f8875",
  YELLOW: "#b98239",
  RED: "#a9695d",
  GRAY: "#8f9994",
};
const STATUS_TEXT = {
  GREEN: "已掌握",
  YELLOW: "待巩固",
  RED: "薄弱",
  GRAY: "未学习",
};

// fillByStatus（只读投影）的节点底色：与 learning-theme.css 的
// --oslab-mastered/review/weak/unseen-soft 令牌同值；状态条/圆点仍用
// STATUS_COLORS 强色，图例（父视图渲染）与节点实际颜色保持一致
const STATUS_FILLS = {
  GREEN: "#e9f1ed",
  YELLOW: "#f6eddf",
  RED: "#f5eae7",
  GRAY: "#f0f2f0",
};

// 关系语义文案；历史拼写 implemente 兼容映射为 implements
const RELATION_TEXT = {
  dependent: "依赖 / 前置知识",
  contain: "包含 / 所属",
  related: "相关",
  implements: "实现",
};
const RELATION_LABEL = {
  dependent: "依赖",
  contain: "包含",
  related: "相关",
  implements: "实现",
};

const normalizeRelationType = (link) => {
  const raw = String(link?.relationType ?? link?.value ?? "")
    .trim()
    .toLowerCase();
  return raw === "implemente" ? "implements" : raw;
};

// ---------- ID 归一化（log3 收尾 B.1） ----------
// 节点 id / 连线 source-target / 选中 id 一律先经同一转换函数字符串化再比较，
// 消灭数字 ID 与字符串 ID 混用导致的"找不到邻居/选中失效"假阴性。
const toId = (value) =>
  value === null || value === undefined ? "" : String(value);

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
      if (item && item.name) {
        next.set(String(item.name), item.color === "blue" ? "blue" : "amber");
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
// 节点高 40（单行）/46（两行）；名称超宽两行截断 + 省略号；全名进 tooltip。
const PAD = 7; // 标签左右内边距
const BAR_W = 6; // 左侧状态条宽
const BAR_GAP = 5;
const DOT_W = 10; // 右上角状态圆点占位
const DOT_GAP = 5;
const CHAR_W = 12; // fontSize 12 下 CJK 字宽估计
const LAT_W = 6.5;
const NAME_LINE_MAX = 108; // 名称单行最大宽度（约 9 个中文字符）
const isWide = (ch) =>
  /[\u2e80-\ua4cf\uac00-\ud7a3\uf900-\ufaff\ufe30-\ufe4f\uff00-\uffef\u3000-\u303f]/.test(
    ch
  );
const textWidth = (s) => {
  let w = 0;
  for (const ch of String(s || "")) w += isWide(ch) ? CHAR_W : LAT_W;
  return w;
};

const splitName = (name) => {
  const s = String(name || "").replace(/\r?\n/g, " ");
  if (!s) return { lines: [""], box: 8 };
  const budget = NAME_LINE_MAX - 4;
  if (textWidth(s) <= NAME_LINE_MAX) {
    return { lines: [s], box: Math.ceil(textWidth(s)) + 2 };
  }
  let i = 0;
  let acc = 0;
  for (; i < s.length; i++) {
    const w = isWide(s[i]) ? CHAR_W : LAT_W;
    if (acc + w > budget) break;
    acc += w;
  }
  const l1 = s.slice(0, i);
  let l2 = s.slice(i);
  if (textWidth(l2) > budget) {
    let j = 0;
    let acc2 = 0;
    for (; j < l2.length; j++) {
      const w = isWide(l2[j]) ? CHAR_W : LAT_W;
      if (acc2 + w > budget - CHAR_W) break;
      acc2 += w;
    }
    l2 = l2.slice(0, j) + "…";
  }
  return { lines: l2 ? [l1, l2] : [l1], box: NAME_LINE_MAX };
};

const nodeSizeOf = (lines, box, showStatusUI) => {
  const inner = showStatusUI ? BAR_W + BAR_GAP + box + DOT_GAP + DOT_W : box;
  const width = Math.max(64, inner + PAD * 2);
  const height = lines.length > 1 ? 46 : 40;
  return [width, height];
};

const buildLabel = (ctx) => {
  const nameStyle = {
    // log3 B.2/B.4：默认态用主/次级文字色；弱化态也不得低于次级文字色
    // （原 #b8b3ab 浅灰会让非邻接文字不可读）
    color: ctx.dimmed ? MUTED_STRONG : INK_SOFT,
    fontSize: 12,
    lineHeight: 15,
    width: ctx.box,
    fontWeight: ctx.selected ? 600 : 500,
  };
  const rich = {
    name: nameStyle,
    sp: { width: DOT_GAP },
    pad: { width: BAR_W + BAR_GAP },
  };
  let text;
  if (ctx.showStatusUI) {
    rich.bar = {
      width: BAR_W,
      height: 22,
      backgroundColor: ctx.statusColor,
      borderRadius: 3,
    };
    rich.dot = { color: ctx.statusColor, fontSize: 9, width: DOT_W };
    text = `{bar| }{name|${ctx.lines[0]}}{sp| }{dot|●}`;
    if (ctx.lines[1]) text += `\n{pad|}{name|${ctx.lines[1]}}`;
  } else {
    text = `{name|${ctx.lines[0]}}`;
    if (ctx.lines[1]) text += `\n{name|${ctx.lines[1]}}`;
  }
  return {
    show: true,
    position: "inside",
    align: "center",
    verticalAlign: "middle",
    fontSize: 12,
    padding: [2, PAD],
    formatter: text,
    rich,
  };
};

const buildNodeStyle = (node, ctx) => {
  const selected =
    (props.selectedId && toId(node.id) === ctx.selectedId) || node.highlight;
  const dimmed =
    !!ctx.selectedId &&
    !selected &&
    !ctx.neighborIds.has(toId(node.id ?? node.name));
  const statusColor =
    STATUS_COLORS[node.status || "GRAY"] || STATUS_COLORS.GRAY;
  const pulsing = pulseMap.value.get(String(node.name || ""));
  const borderColor = pulsing
    ? pulsing === "blue"
      ? PRIMARY
      : REVIEW
    : selected
    ? PRIMARY
    : NODE_BORDER;
  const split = splitName(node.name);
  return {
    id: toId(node.id ?? node.name),
    name: node.name,
    category: node.category,
    chapter: node.chapter,
    description: node.description,
    status: node.status,
    symbol: "rect",
    symbolSize: nodeSizeOf(split.lines, split.box, ctx.showStatusUI),
    itemStyle: {
      // fillByStatus：底色 = 状态浅色（聚合热力投影）；默认 = 类别底色
      color: ctx.fillByStatus
        ? STATUS_FILLS[node.status || "GRAY"] || CATEGORY_FALLBACK_FILL
        : categoryFillOf(node.category || node.label),
      borderColor,
      borderWidth: pulsing ? 3 : selected ? 2 : 1,
      borderRadius: 6,
      opacity: 1,
    },
    label: buildLabel({
      selected,
      dimmed,
      statusColor,
      showStatusUI: ctx.showStatusUI,
      lines: split.lines,
      box: split.box,
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

const buildLink = (link, ctx) => {
  const type = normalizeRelationType(link);
  // R10：dependent 存储为 A→B（A 依赖 B），画布按 B→A 绘制（先学箭尾）
  const isDependent = type === "dependent";
  const from = toId(isDependent ? link.target : link.source);
  const to = toId(isDependent ? link.source : link.target);
  const adjacent =
    !!ctx.selectedId &&
    (toId(link.source) === ctx.selectedId ||
      toId(link.target) === ctx.selectedId);
  const dimmed = !!ctx.selectedId && !adjacent;
  // log3 B.4：非邻接边弱化下限 0.45（原 0.15 会把关系线压到几乎不可见）
  const dim = dimmed ? 0.45 : 1;
  let color;
  let width;
  let lineType;
  let symbol;
  let symbolSize;
  switch (type) {
    case "dependent":
      color = PRIMARY;
      width = 1.8;
      lineType = "solid";
      symbol = ["none", "arrow"];
      symbolSize = [9, 11];
      break;
    case "contain":
      color = "#7d8ba1";
      width = 1.2;
      lineType = "dashed";
      symbol = ["none", "arrow"];
      symbolSize = [7, 8];
      break;
    case "related":
      color = "#b3ada5";
      width = 1.4;
      lineType = "dotted";
      symbol = ["circle", "circle"];
      symbolSize = [3, 3];
      break;
    case "implements":
      color = REVIEW;
      width = 1.5;
      lineType = "dashed";
      symbol = ["none", "arrow"];
      symbolSize = [6, 7];
      break;
    default:
      // 未知/空关系类型：灰色细实线兜底，不抛异常
      color = "#c9c4bd";
      width = 1;
      lineType = "solid";
      symbol = ["none", "none"];
      symbolSize = [0, 0];
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
  }
  // 视图注入的强调样式（如补救学习路径）优先于语义默认值
  const override = link.lineStyle || {};
  return {
    ...link,
    source: from,
    target: to,
    symbol,
    symbolSize,
    lineStyle: {
      color,
      width: adjacent ? width + 0.6 : width,
      type: lineType,
      opacity: dim,
      curveness: ctx.curveOf(link),
      ...override,
    },
    label: {
      // 边的关系文字只由「关系标签」开关控制（用户反馈：选中邻接时
      // 自动脉出的"相关/依赖"标签很惹眼）；语义改由线型/箭头/悬停
      // tooltip 表达，tooltip 的 relationText 不受开关影响。
      show: ctx.showRelationLabels,
      formatter: relLabel,
      fontSize: 10,
      color: MUTED_STRONG,
      backgroundColor: "#ffffff",
      padding: [1, 4],
      borderRadius: 4,
    },
    relationText: tooltipText,
  };
};

const buildSeries = () => {
  // 防御：按 id/name 去重节点，并过滤掉指向不存在节点的连线。
  // 重复 id 或悬空连线会让 ECharts graph series 在
  // createGraphFromNodeEdge 中抛 "Cannot set properties of undefined
  // (setting 'dataIndex')"，错误发生在 watcher 回调里会杀死渲染线程，
  // 页面从此冻结（转圈不停、按钮无响应）。
  const seen = new Set();
  const safeNodes = chartData.nodes.filter((node) => {
    const key = toId(node.id ?? node.name);
    if (!key || seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
  const nodeKeys = new Set(safeNodes.map((node) => toId(node.id ?? node.name)));
  const safeLinks = chartData.links.filter(
    (link) => nodeKeys.has(toId(link.source)) && nodeKeys.has(toId(link.target))
  );
  // 掌握状态条/圆点：数据里没有任何 status（如未登录的结构预览）时自动隐藏
  const showStatusUI =
    props.showStatus && safeNodes.some((node) => !!node.status);
  // log3 B.5：选中 id 只有仍在当前可见节点集合内才生效；
  // 筛选把选中节点过滤掉后按"无选中"处理，不把高亮残留到隐藏节点。
  const selectedId =
    props.selectedId && nodeKeys.has(toId(props.selectedId))
      ? toId(props.selectedId)
      : "";
  const idName = new Map(
    safeNodes.map((node) => [toId(node.id ?? node.name), node.name || ""])
  );
  const neighborIds = new Set();
  if (selectedId) {
    for (const link of safeLinks) {
      if (toId(link.source) === selectedId) {
        neighborIds.add(toId(link.target));
      }
      if (toId(link.target) === selectedId) {
        neighborIds.add(toId(link.source));
      }
    }
  }
  // 稳定轻微曲率：同一对节点的多条边按出现序递增曲率（确定性，避免重叠）
  const pairCount = new Map();
  const curveOf = (link) => {
    const a = toId(link.source);
    const b = toId(link.target);
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
    curveOf,
    nameOf: (id) => idName.get(String(id)) || "",
  };

  // 章节播种（R3）：章节锚点环绕 + 章节内确定性黄金角螺旋散布。
  // 坐标只写入本次传给 ECharts 的派生对象，绝不写回 props 节点数据，
  // 也不设置 fixed —— force 从播种位置开始异步迭代（layoutAnimation: true）。
  const chapterOf = (node) => String(node.chapter || "").trim() || "未分类";
  const chapters = Array.from(new Set(safeNodes.map(chapterOf))).sort((a, b) =>
    a.localeCompare(b, "zh-CN", { numeric: true })
  );
  const chapterIndex = new Map(chapters.map((c, i) => [c, i]));
  const anchorR = 260 + chapters.length * 45;
  const counters = new Map();
  const data = safeNodes.map((node) => {
    const style = buildNodeStyle(node, ctx);
    const ch = chapterOf(node);
    const k = counters.get(ch) || 0;
    counters.set(ch, k + 1);
    const anchorAngle =
      (2 * Math.PI * (chapterIndex.get(ch) || 0)) /
        Math.max(1, chapters.length) -
      Math.PI / 2;
    const spiralR = 26 * Math.sqrt(k);
    const spiralAngle = k * 2.399963229728653;
    style.x = Math.round(
      Math.cos(anchorAngle) * anchorR + spiralR * Math.cos(spiralAngle)
    );
    style.y = Math.round(
      Math.sin(anchorAngle) * anchorR + spiralR * Math.sin(spiralAngle)
    );
    return style;
  });

  return {
    id: "oslab-knowledge-graph",
    type: "graph",
    layout: "force",
    force: {
      repulsion: 1100,
      edgeLength: 200,
      gravity: 0.05,
      friction: 0.6,
      layoutAnimation: true,
    },
    draggable: true,
    roam: true,
    nodeScaleRatio: 0,
    scaleLimit: { min: 0.02, max: 4 },
    data,
    links: safeLinks.map((link) => buildLink(link, ctx)),
    label: { show: true },
    // log3 B.3：focus = "none" 关闭 ECharts 的 adjacency blur（全图默认
    // 虚化的来源）。hover 只强调被悬停元素本身；选中态的主蓝描边与邻接
    // 强调由本组件按节点/边数据逐项绘制，两层不再叠加。
    emphasis: {
      focus: "none",
      scale: false,
      // 悬停不再强制弹出边标签（用户反馈同上）；关系语义走 tooltip
    },
    edgeLabel: {
      fontSize: 10,
      lineHeight: 14,
      color: MUTED_STRONG,
      backgroundColor: "#ffffff",
      padding: [1, 4],
    },
    edgeSymbol: ["none", "arrow"],
    edgeSymbolSize: [0, 8],
  };
};

const initChart = () => {
  myChart = echarts.init(chart.value);
  myChart.on("click", handleChartClick);
  myChart.on("dblclick", handleNodeDblClick);
  myChart.on("rendered", handleChartRendered);
  myChart.on("graphRoam", cancelAutoFit);
  lastTopologySignature = graphTopologySignature();
  updateChart({ rebuild: true });
};

const handleChartClick = (params) => {
  if (params.dataType !== "node") {
    return;
  }
  if (clickTimer) {
    clearTimeout(clickTimer);
  }
  clickTimer = window.setTimeout(() => {
    clickTimer = null;
    // 单击只选中节点（右栏展示详情），不重新请求整图
    emit("node-click", params.data);
  }, CLICK_DELAY);
};

const handleNodeDblClick = (params) => {
  if (params.dataType !== "node") {
    return;
  }
  if (clickTimer) {
    clearTimeout(clickTimer);
    clickTimer = null;
  }
  emit("node-dblclick", params.data);
};

// rebuild=true：数据变化，notMerge 全量重建（清布局记忆，全新迭代）；
// rebuild=false：选中/脉冲/标签开关只刷新视觉，保留布局与视野。

let autoFitPending = false;
let autoFitTimer = null;
const cancelAutoFit = () => {
  autoFitPending = false;
  if (autoFitTimer) {
    window.clearTimeout(autoFitTimer);
    autoFitTimer = null;
  }
};

// 使用布局后的坐标和当前像素变换计算缩放，不能把播种包围盒当最终布局。
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
  let marginX = 32;
  let marginY = 32;
  data.each((index) => {
    const point = data.getItemLayout(index);
    if (!point || !point.every(Number.isFinite)) return;
    minX = Math.min(minX, point[0]);
    minY = Math.min(minY, point[1]);
    maxX = Math.max(maxX, point[0]);
    maxY = Math.max(maxY, point[1]);
    const size = data.getItemVisual(index, "symbolSize");
    if (Array.isArray(size)) {
      marginX = Math.max(marginX, size[0] / 2 + 24);
      marginY = Math.max(marginY, size[1] / 2 + 24);
    }
  });
  if (!Number.isFinite(minX)) return;
  const finder = { seriesIndex: 0 };
  const p1 = myChart.convertToPixel(finder, [minX, minY]);
  const p2 = myChart.convertToPixel(finder, [maxX, maxY]);
  if (![...p1, ...p2].every(Number.isFinite)) return;
  const width = Math.max(1, Math.abs(p2[0] - p1[0]));
  const height = Math.max(1, Math.abs(p2[1] - p1[1]));
  const ratio = Math.min(
    Math.max(1, myChart.getWidth() - marginX * 2) / width,
    Math.max(1, myChart.getHeight() - marginY * 2) / height
  );
  const previousZoom = series.coordinateSystem.getZoom();
  const zoom =
    data.count() === 1 ? 1 : Math.max(0.02, Math.min(4, previousZoom * ratio));
  myChart.dispatchAction({
    type: "oslabGraphFit",
    seriesIndex: 0,
    center: [(minX + maxX) / 2, (minY + maxY) / 2],
    zoom,
  });
};

// 最后一帧力导向结束后再适配；用户主动平移/缩放会取消自动适配。
const handleChartRendered = () => {
  if (!autoFitPending) return;
  if (autoFitTimer) window.clearTimeout(autoFitTimer);
  autoFitTimer = window.setTimeout(() => {
    if (!myChart || !myChart.getWidth() || !myChart.getHeight()) return;
    cancelAutoFit();
    fitCurrentLayout();
  }, 120);
};

const updateChart = ({ rebuild = false } = {}) => {
  const option = {
    backgroundColor: "#ffffff",
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
          const node = params.data || {};
          const parts = [];
          const chapter = String(node.chapter || "").trim();
          if (chapter) parts.push(`章节：${escapeHtml(chapter)}`);
          if (node.category) parts.push(`类别：${escapeHtml(node.category)}`);
          if (props.showStatus && node.status) {
            parts.push(`状态：${STATUS_TEXT[node.status] || "未学习"}`);
          }
          let html = `<div style="font-weight:600;margin-bottom:4px;">${escapeHtml(
            node.name
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
    cancelAutoFit();
    autoFitPending = true;
    myChart.setOption(option, { notMerge: true });
    fitCurrentLayout();
  } else {
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

// 拓扑签名（log3 收尾 E）：节点 id + 连线 + 类别的可序列化指纹。
// 签名不变（仅状态/样式叠加更新：回放步进、班级聚合着色刷新）时保留当前
// 视野；签名变化（筛选、切换数据源）才复位全景 —— 既不破坏既有消费方的
// "新数据全景展示"，又避免回放/播放每步把用户视野强制拉回默认。
const graphTopologySignature = () => {
  const nodes = props.nodes || [];
  const links = props.links || [];
  const categories = props.categories || [];
  return [
    nodes.map((node) => toId(node.id ?? node.name)).join(","),
    links
      .map(
        (link) =>
          `${toId(link.source)}>${toId(link.target)}:${String(
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
  updateChart({ rebuild });
};

// 重置当前可见节点的视野，保留布局；百分比中心不适用于图数据坐标。
const resetView = () => {
  cancelAutoFit();
  autoFitPending = true;
  fitCurrentLayout();
  handleChartRendered();
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
      cancelAutoFit();
      autoFitPending = true;
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
  cancelAutoFit();
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  if (resizeFrame) {
    window.cancelAnimationFrame(resizeFrame);
    resizeFrame = 0;
  }
  if (clickTimer) {
    clearTimeout(clickTimer);
    clickTimer = null;
  }
  if (pulseTimer) {
    clearTimeout(pulseTimer);
    pulseTimer = null;
  }
  if (myChart) {
    myChart.off("click", handleChartClick);
    myChart.off("dblclick", handleNodeDblClick);
    myChart.off("rendered", handleChartRendered);
    myChart.off("graphRoam", cancelAutoFit);
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
