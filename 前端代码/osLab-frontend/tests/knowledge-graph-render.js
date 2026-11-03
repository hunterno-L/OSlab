// Run with: node tests/knowledge-graph-render.js
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");
const { parse } = require("@vue/compiler-sfc");
const ts = require("typescript");
const echarts = require("echarts");

const source = fs.readFileSync(
  path.join(__dirname, "../src/components/KnowledgeGraph.vue"),
  "utf8"
);
const descriptor = parse(source).descriptor;
const script = descriptor.script.content + descriptor.scriptSetup.content;
const compiled = ts.transpileModule(
  script +
    "\nexport { initChart, applyGraphState, updateChart, resetView, handleContainerResize, handleChartClick, handleNodeDblClick, computeChapterLayout, computeDeterministicLayout, createKeyResolver, GRAPH_TOKENS, NAME_MAX_WIDTH, STATUS_COLORS, STATUS_TEXT, CATEGORY_FILLS, pulseMap };",
  {
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.CommonJS,
    },
  }
).outputText;

function createHarness(input, width = 1120, height = 620, asyncLayout = false) {
  let graph;
  const createGraph = () => {
    graph = echarts.init(null, null, {
      renderer: "svg",
      ssr: true,
      width,
      height,
    });
    // SSR frames are explicit; only the graph solver uses the virtual clock.
    graph.getZr().animation.stop();
    graph.getZr().animation.start = () => undefined;
    const setOption = graph.setOption.bind(graph);
    // Run the same force solver synchronously in SSR to avoid test timers.
    graph.setOption = (option, ...args) => {
      option.animation = false;
      for (const series of option.series || []) {
        if (series.force) series.force.layoutAnimation = asyncLayout;
      }
      return setOption(option, ...args);
    };
    // SSR has no DOM clientWidth/clientHeight; keep the simulated container size.
    const resize = graph.resize.bind(graph);
    graph.resize = (options = {}) => {
      width = options.width ?? width;
      height = options.height ?? height;
      return resize({ ...options, width, height });
    };
  };
  const callbacks = new Map();
  const emitted = [];
  let timerId = 0;
  let clock = 0;
  const timers = {
    setTimeout: (callback, delay = 0) => {
      callbacks.set(++timerId, { callback, at: clock + delay });
      return timerId;
    },
    clearTimeout: (id) => callbacks.delete(id),
  };
  const props = {};
  const vue = {
    ref: (value) => ({ value }),
    reactive: (value) => value,
    watch: () => undefined,
    onMounted: () => undefined,
    onBeforeUnmount: () => undefined,
  };
  const module = { exports: {} };
  new Function(
    "require",
    "module",
    "exports",
    "defineProps",
    "defineEmits",
    "defineExpose",
    "window",
    "ResizeObserver",
    compiled
  )(
    (name) => {
      if (name === "vue") return vue;
      assert.equal(name, "echarts");
      return {
        init: () => graph,
        registerAction: echarts.registerAction,
        registerLayout: echarts.registerLayout,
        PRIORITY: echarts.PRIORITY,
      };
    },
    module,
    module.exports,
    (definitions) => {
      for (const [key, value] of Object.entries(definitions)) {
        props[key] = Object.prototype.hasOwnProperty.call(input, key)
          ? input[key]
          : typeof value.default === "function"
          ? value.default()
          : value.default;
      }
      return props;
    },
    () => (event, payload) => emitted.push({ event, payload }),
    () => undefined,
    {
      ...timers,
      requestAnimationFrame: (callback) => timers.setTimeout(callback, 16),
      cancelAnimationFrame: (id) => callbacks.delete(id),
    },
    undefined
  );
  createGraph();
  return {
    graph,
    props,
    timers,
    emitted,
    ...module.exports,
    flush() {
      let previousSvg = "";
      const renderFrame = () => {
        const svg = graph.renderToSVGString();
        graph.getZr().animation.stop();
        if (svg !== previousSvg) {
          previousSvg = svg;
          graph.trigger("rendered");
        }
      };
      renderFrame();
      let cycles = 0;
      while (callbacks.size) {
        assert.ok(++cycles < 2000, "auto-fit must not create a render loop");
        const [id, pending] = Array.from(callbacks.entries()).sort(
          (a, b) => a[1].at - b[1].at
        )[0];
        callbacks.delete(id);
        clock = pending.at;
        pending.callback();
        renderFrame();
      }
    },
  };
}

const viewSource = fs.readFileSync(
  path.join(__dirname, "../src/views/knowledgeGraph/KnowledgeGraphView.vue"),
  "utf8"
);
const viewTemplate = parse(viewSource).descriptor.template.content;
const viewScript = parse(viewSource).descriptor.scriptSetup.content;
const compiledView = ts.transpileModule(
  viewScript +
    "\nexport { nodes, links, selectedNodeKey, selectedNode, selectedNodeId, searchText, statusFilter, filteredNodes, canvasGraph, canvasBaseNodes, neighborKeys, relationGroups, buildVisibleAnnotations, selectAndFocus, selectUnitNode, handleNodeClick, handleNodeDblClick, enterNodeWorkspace, openNodeQuestions, nodeContextQuery, nodeKeyForName, nodeKeyOf, selectedSectionCode, selectedUnitCode, pathData, selectedWorkbench, canEnterNodeWorkspace };",
  {
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.CommonJS,
    },
  }
).outputText;

function createViewHarness(options = {}) {
  const vue = require("vue");
  // Mirror vue-router: fullPath and query always describe the same location.
  const fullPath = options.fullPath || "/graph";
  const parsedQuery = {};
  for (const [key, value] of new URLSearchParams(fullPath.split("?")[1])) {
    parsedQuery[key] = value;
  }
  const route = {
    path: options.path || "/graph",
    query: { ...parsedQuery, ...(options.query || {}) },
    fullPath,
  };
  const pushed = [];
  const replaced = [];
  const router = {
    push(location) {
      pushed.push(location);
      return Promise.resolve();
    },
    replace(location) {
      replaced.push(location);
      return Promise.resolve();
    },
    resolve(location) {
      const search = new URLSearchParams();
      for (const [key, value] of Object.entries(location.query || {})) {
        if (value === null || value === undefined) continue;
        for (const item of Array.isArray(value) ? value : [value]) {
          search.append(key, item);
        }
      }
      const query = search.toString();
      return {
        path: location.path,
        fullPath: query ? `${location.path}?${query}` : location.path,
      };
    },
  };
  const courseCode = vue.ref("");
  const courseTitle = vue.ref("操作系统");
  const sections = vue.ref([]);
  const units = vue.ref([]);
  const catalog = {
    courseCode,
    courseTitle,
    resume: vue.ref(null),
    loading: vue.ref(false),
    error: vue.ref(""),
    sections,
    courses: vue.ref([]),
    outlines: vue.ref({}),
    courseProgress: vue.ref(0),
    sectionByCode: vue.computed(
      () => new Map(sections.value.map((item) => [item.sectionCode, item]))
    ),
    unitByCode: vue.computed(
      () => new Map(units.value.map((item) => [item.unitCode, item]))
    ),
    units,
    load: async () => undefined,
    ancestorCodes: (code) => new Set([code]),
    descendantCodes: (code) => new Set([code]),
    sectionCrumb: () => "",
    courseCodeOfSection: () => "",
    courseCodeOfUnit: () => "",
  };
  const loginUser = options.loginUser || {
    id: 1,
    userRole: "user",
  };
  const module = { exports: {} };
  new Function(
    "require",
    "module",
    "exports",
    "window",
    "ResizeObserver",
    compiledView
  )(
    (name) => {
      switch (name) {
        case "vue":
          return {
            ...vue,
            watch: (source, callback, watchOptions) =>
              vue.watch(source, callback, {
                ...watchOptions,
                flush: "sync",
              }),
            onMounted: () => undefined,
            onBeforeUnmount: () => undefined,
          };
        case "vue-router":
          return { useRouter: () => router, useRoute: () => route };
        case "@arco-design/web-vue":
          return {
            Message: {
              info: () => undefined,
              warning: () => undefined,
              error: () => undefined,
              success: () => undefined,
            },
          };
        case "@/components/KnowledgeGraph.vue":
        case "@/components/learning/CourseCatalogSidebar.vue":
          return {};
        case "@/api/course":
          return {
            getCourseUnit: async () => null,
            unitPath: (course, unit) => `/courses/${course}/units/${unit}`,
          };
        case "@/composables/useCourseCatalog":
          return { useCourseCatalog: () => catalog };
        case "@/utils/masteryDisplay":
          return { scoreText: () => "-" };
        case "@/api/learning":
          return {
            personalGraph: async () => ({
              data: { code: 0, data: null },
            }),
            masteryPath: async () => ({ data: { code: 0, data: null } }),
            getNodeContent: async () => ({ data: { code: 0, data: null } }),
            saveNodeContent: async () => ({ data: { code: 0 } }),
          };
        case "@/composables/useAiPageContext":
          return {
            registerAiPageContext: () => undefined,
            unregisterAiPageContext: () => undefined,
            getAiPageContext: () => null,
          };
        case "@/composables/useAiConversation":
          return {
            useAiConversation: () => ({
              nextActions: vue.ref([]),
              nextActionsLoading: vue.ref(false),
              nextActionsError: vue.ref(""),
              dueReviewCount: vue.ref(0),
              refreshNextActions: async () => [],
              syncCourse: async () => undefined,
            }),
          };
        case "@/composables/useAiLauncher":
          return { askAiAssistant: () => undefined };
        case "@/components/ai/AiNextActions.vue":
          return {};
        case "@/composables/useNodeWorkbench":
          return {
            loadNodeWorkbench: async () => ({}),
            peekNodeWorkbench: () => null,
          };
        case "../../../generated":
          return {
            KnowledgeGraphControllerService: new Proxy(
              {},
              {
                get: () => async () => ({
                  code: 0,
                  message: "",
                  data: { nodes: [], links: [], categories: [] },
                }),
              }
            ),
          };
        case "@/store":
          return {
            __esModule: true,
            default: { state: { user: { loginUser } } },
          };
        case "@/access/accessEnum":
          return {
            __esModule: true,
            default: {
              ADMIN: "admin",
              USER: "user",
              NOT_LOGIN: "not_login",
            },
          };
        default:
          throw new Error(
            `Unexpected import in KnowledgeGraphView.vue: ${name}`
          );
      }
    },
    module,
    module.exports,
    {
      setTimeout,
      clearTimeout,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => undefined,
    },
    class ResizeObserver {
      observe() {
        return undefined;
      }
      disconnect() {
        return undefined;
      }
    }
  );
  return {
    ...module.exports,
    route,
    router,
    pushed,
    replaced,
    catalog,
  };
}

function makeGraph(count, chapterCount = 1) {
  return {
    nodes: Array.from({ length: count }, (_, index) => ({
      id: String(index),
      name: index % 3 ? `知识点 ${index}` : `sys_mem_${index} 函数说明`,
      category: ["概念", "算法", "功能"][index % 3],
      chapter: `章节 ${index % chapterCount}`,
      status: "GRAY",
    })),
    links: Array.from({ length: Math.max(0, count - 1) }, (_, index) => ({
      source: String(index),
      target: String(index + 1),
      relationType: ["dependent", "contain", "related", "implements"][
        index % 4
      ],
    })),
    categories: [],
    selectedId: "0",
    showRelationLabels: true,
  };
}

function assertNodesInView(graph) {
  const series = graph.getModel().getSeriesByIndex(0);
  const data = series.getData();
  data.each((index) => {
    const point = graph.convertToPixel(
      { seriesIndex: 0 },
      data.getItemLayout(index)
    );
    assert.ok(point.every(Number.isFinite), "node coordinates must be finite");
    assert.ok(
      point[0] >= 0 &&
        point[0] <= graph.getWidth() &&
        point[1] >= 0 &&
        point[1] <= graph.getHeight(),
      `node ${index} is outside the viewport: ${point}`
    );
  });
}

function globalRect(element) {
  const rect = element.getBoundingRect().clone();
  rect.applyTransform(element.getComputedTransform());
  return rect;
}

function nodeCardRects(graph) {
  const data = graph.getModel().getSeriesByIndex(0).getData();
  return data.mapArray((index) =>
    globalRect(data.getItemGraphicEl(index).childAt(0))
  );
}

function assertNodeCardsDoNotOverlap(graph, gap = 1) {
  const rects = nodeCardRects(graph);
  for (let a = 0; a < rects.length; a += 1) {
    for (let b = a + 1; b < rects.length; b += 1) {
      const first = rects[a];
      const second = rects[b];
      assert.ok(
        first.x + first.width + gap <= second.x ||
          second.x + second.width + gap <= first.x ||
          first.y + first.height + gap <= second.y ||
          second.y + second.height + gap <= first.y,
        `node cards ${a} and ${b} overlap at rendered pixels: ${JSON.stringify({
          first,
          second,
        })}`
      );
    }
  }
}

function assertRelationLabelsDoNotCoverNodes(graph) {
  const series = graph.getModel().getSeriesByIndex(0);
  const nodeRects = nodeCardRects(graph);
  series.getEdgeData().eachItemGraphicEl((edge) => {
    const label = edge.getTextContent();
    if (!label || label.ignore || !label.style.text) return;
    const labelRect = globalRect(label);
    nodeRects.forEach((nodeRect) => {
      const overlaps =
        labelRect.x < nodeRect.x + nodeRect.width &&
        labelRect.x + labelRect.width > nodeRect.x &&
        labelRect.y < nodeRect.y + nodeRect.height &&
        labelRect.y + labelRect.height > nodeRect.y;
      assert.equal(
        overlaps,
        false,
        "visible relation labels must not cover node cards"
      );
    });
  });
}

function assertLabelsAttached(graph) {
  const series = graph.getModel().getSeriesByIndex(0);
  const data = series.getData();
  data.each((index) => {
    const symbol = data.getItemGraphicEl(index).childAt(0);
    const nodeRect = globalRect(symbol);
    const textRect = globalRect(symbol.getTextContent());
    // 小图索引卡放大后，ECharts 在缩放态下文本盒与符号盒会有数像素差异；
    // 4px 仍能拦住真正的溢出回归。
    const tolerance = 4;
    assert.ok(
      textRect.x >= nodeRect.x - tolerance &&
        textRect.y >= nodeRect.y - tolerance &&
        textRect.x + textRect.width <=
          nodeRect.x + nodeRect.width + tolerance &&
        textRect.y + textRect.height <=
          nodeRect.y + nodeRect.height + tolerance,
      `node ${index} label must stay inside its rectangle: ${JSON.stringify({
        nodeRect,
        textRect,
      })}`
    );
  });
  const edges = series.getEdgeData();
  edges.eachItemGraphicEl((element) => {
    const text = element.getTextContent();
    if (!text || text.ignore) return;
    const transform = text.getComputedTransform();
    const scale = transform ? Math.hypot(transform[0], transform[1]) : 1;
    assert.ok(
      scale >= 0.95 && scale <= 1.05,
      `relation labels must use screen-size text, not scale ${scale}`
    );
  });
}

function nodePositions(graph) {
  const data = graph.getModel().getSeriesByIndex(0).getData();
  return data.mapArray((index) => data.getItemLayout(index).slice());
}

function graphData(harness) {
  return harness.graph.getModel().getSeriesByIndex(0).getData();
}

function graphEdgeData(harness) {
  return harness.graph.getModel().getSeriesByIndex(0).getEdgeData();
}

function nodeStyle(harness, index) {
  const data = graphData(harness);
  const model = data.getItemModel(index);
  const visual = data.getItemVisual(index, "style");
  return {
    ...((model.option && model.option.itemStyle) || {}),
    ...(model.getItemStyle() || {}),
    ...(visual || {}),
  };
}

function nodeLabelStyle(harness, index) {
  const model = graphData(harness).getItemModel(index);
  return {
    color: model.get("label.rich.name.color"),
    fontWeight: model.get("label.rich.name.fontWeight"),
  };
}

function edgeStyle(harness, index) {
  const data = graphEdgeData(harness);
  const model = data.getItemModel(index);
  const visual = data.getItemVisual(index, "style");
  return {
    ...((model.option && model.option.lineStyle) || {}),
    ...(model.getItemStyle() || {}),
    ...(visual || {}),
  };
}

function makeIdentityGraph() {
  return {
    nodes: [
      {
        id: "1",
        nodeCode: "os.a",
        stableId: "stable-a",
        name: "a.internal",
        displayName: "同名知识点",
        category: "概念",
        chapter: "第一章",
        status: "RED",
        highlightReason: "teaches-current-unit",
      },
      {
        id: "2",
        nodeCode: "os.b",
        stableId: "stable-b",
        name: "b.internal",
        displayName: "同名知识点",
        category: "算法",
        chapter: "第一章",
        status: "GREEN",
      },
    ],
    links: [{ source: "1", target: "2", relationType: "dependent" }],
    categories: [],
    selectedId: "",
    showRelationLabels: true,
  };
}

function makeViewNode(overrides = {}) {
  return {
    id: "1",
    name: "node.internal",
    category: "概念",
    chapter: "第一章",
    description: "",
    status: "GRAY",
    pKnown: null,
    confidence: null,
    conceptScore: null,
    practiceScore: null,
    conceptEvidenceCount: 0,
    practiceEvidenceCount: 0,
    ...overrides,
  };
}

const failures = [];
function test(name, callback) {
  try {
    callback();
    console.log(`PASS ${name}`);
  } catch (error) {
    failures.push(error);
    console.error(`FAIL ${name}\n${error.stack}`);
  }
}

test("five-node learning path stays visible after reset", () => {
  const harness = createHarness(makeGraph(5));
  try {
    harness.initChart();
    harness.flush();
    harness.resetView();
    harness.flush();
    assertNodesInView(harness.graph);
    assertLabelsAttached(harness.graph);
  } finally {
    harness.graph.dispose();
  }
});

test("150-node course, chapter filter and path filter automatically fit", () => {
  const harness = createHarness(makeGraph(150, 6));
  try {
    harness.initChart();
    harness.flush();
    assertNodesInView(harness.graph);
    for (const input of [makeGraph(23), makeGraph(5), makeGraph(1)]) {
      Object.assign(harness.props, input);
      harness.applyGraphState();
      harness.flush();
      assertNodesInView(harness.graph);
    }
  } finally {
    harness.graph.dispose();
  }
});

test("node labels and relation labels survive zoom and hover", () => {
  const harness = createHarness(makeGraph(23));
  try {
    harness.initChart();
    harness.flush();
    const series = harness.graph.getModel().getSeriesByIndex(0);
    assert.equal(
      series.get("nodeScaleRatio"),
      1,
      "nodes and labels scale with the canvas so their relative spacing stays stable"
    );
    let currentZoom = 1;
    for (const zoom of [0.2, 0.6, 1, 1.8, 2.5, 4]) {
      harness.graph.dispatchAction({
        type: "graphRoam",
        seriesIndex: 0,
        zoom: zoom / currentZoom,
        originX: harness.graph.getWidth() / 2,
        originY: harness.graph.getHeight() / 2,
      });
      currentZoom = zoom;
      harness.flush();
      if (zoom >= 0.18) {
        assertLabelsAttached(harness.graph);
        harness.graph.dispatchAction({
          type: "highlight",
          seriesIndex: 0,
          dataType: "edge",
          dataIndex: 1,
        });
        harness.flush();
        assertLabelsAttached(harness.graph);
        harness.graph.dispatchAction({ type: "downplay", seriesIndex: 0 });
      }
    }
  } finally {
    harness.graph.dispose();
  }
});

test("rendered node cards do not collide in large and zoomed-out graphs", () => {
  for (const count of [13, 35, 150, 185]) {
    const harness = createHarness(makeGraph(count, count > 100 ? 6 : 1));
    try {
      harness.initChart();
      harness.flush();
      assertNodeCardsDoNotOverlap(harness.graph, 0.5);
      const before = nodeCardRects(harness.graph);
      harness.graph.dispatchAction({
        type: "graphRoam",
        seriesIndex: 0,
        zoom: 0.55,
        originX: harness.graph.getWidth() / 2,
        originY: harness.graph.getHeight() / 2,
      });
      harness.flush();
      assertNodeCardsDoNotOverlap(harness.graph, 0.5);
      assert.notDeepEqual(nodeCardRects(harness.graph), before);
      assert.ok(
        graphData(harness).getItemModel(0).get("label.show"),
        "zooming out must not switch node labels off"
      );
    } finally {
      harness.graph.dispose();
    }
  }
});

test("chapter force refinement responds to topology and shortens interleaved connections", () => {
  const input = makeGraph(35);
  // Seven interleaved five-node communities deliberately cross the seed grid.
  input.links = input.nodes.flatMap((node, index) =>
    index < 7
      ? [1, 2, 3, 4].map((offset) => ({
          source: node.id,
          target: String(index + offset * 7),
          relationType: "related",
        }))
      : []
  );
  const harness = createHarness(input);
  try {
    const resolver = harness.createKeyResolver(input.nodes);
    const seed = harness.computeChapterLayout(input.nodes);
    const relaxed = harness.computeDeterministicLayout(
      input.nodes,
      input.links,
      "",
      resolver
    );
    const averageLength = (positions) =>
      input.links.reduce((sum, link) => {
        const a = positions.get(link.source);
        const b = positions.get(link.target);
        return sum + Math.hypot(a.x - b.x, a.y - b.y);
      }, 0) / input.links.length;
    assert.ok(
      averageLength(relaxed) < averageLength(seed) * 0.85,
      `connected communities must pull together: seed=${averageLength(
        seed
      )}, relaxed=${averageLength(relaxed)}`
    );
    assert.deepEqual(
      harness.computeDeterministicLayout(
        input.nodes,
        input.links,
        "",
        resolver
      ),
      relaxed,
      "the same graph must converge to the same coordinates"
    );
    assert.deepEqual(
      harness.computeDeterministicLayout(input.nodes, [], "", resolver),
      seed,
      "isolated nodes keep their chapter seed rather than drifting"
    );
    const connectedRows = new Set(
      [...relaxed.values()].map((point) => point.y)
    );
    assert.ok(
      connectedRows.size > Math.sqrt(input.nodes.length) * 2,
      "refinement must leave the rectangular rows instead of repainting the grid"
    );
  } finally {
    harness.graph.dispose();
  }
});

test("185-node force layout keeps dense links, labels and the camera stable", () => {
  const input = makeGraph(185, 6);
  input.selectedId = "";
  input.links.push(
    ...input.nodes.flatMap((node, index) =>
      [6, 12]
        .filter((offset) => index + offset < input.nodes.length)
        .map((offset) => ({
          source: node.id,
          target: String(index + offset),
          relationType: "related",
        }))
    )
  );
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    assertNodesInView(harness.graph);
    assertNodeCardsDoNotOverlap(harness.graph, 0.5);
    assertLabelsAttached(harness.graph);
    assertRelationLabelsDoNotCoverNodes(harness.graph);
    const positions = nodePositions(harness.graph);
    harness.graph.dispatchAction({
      type: "graphRoam",
      seriesIndex: 0,
      dx: 48,
      dy: -22,
      zoom: 1.2,
      originX: 560,
      originY: 310,
    });
    harness.flush();
    const camera = harness.graph.getOption().series[0];
    harness.props.selectedId = "34";
    harness.props.nodes[34].status = "GREEN";
    harness.applyGraphState();
    harness.props.showRelationLabels = false;
    harness.updateChart();
    harness.flush();
    const after = harness.graph.getOption().series[0];
    assert.deepEqual(nodePositions(harness.graph), positions);
    assert.deepEqual(after.center, camera.center);
    assert.equal(after.zoom, camera.zoom);
    harness.graph.resize({ width: 390, height: 420 });
    harness.handleContainerResize();
    harness.flush();
    assertNodesInView(harness.graph);
    assertNodeCardsDoNotOverlap(harness.graph, 0.5);
    assertLabelsAttached(harness.graph);
    assert.deepEqual(nodePositions(harness.graph), positions);
  } finally {
    harness.graph.dispose();
  }
});

test("35-node chapter cards use bounded text-aware sizes with annotation clearance", () => {
  const input = makeGraph(35);
  input.nodes[0].displayName = "短名";
  input.nodes[1].displayName = "长名称知识点示例需要完整内容提示";
  input.annotations = Object.fromEntries(
    input.nodes.map((node, index) => [node.id, index + 1])
  );
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    const data = harness.graph.getOption().series[0].data;
    assert.ok(data[0].symbolSize[0] < data[1].symbolSize[0]);
    assert.ok(data.every((node) => node.symbolSize[0] <= 200));
    assert.ok(data.every((node) => node.symbolSize[1] <= 38));
    assertNodeCardsDoNotOverlap(harness.graph, 0.5);
    assertLabelsAttached(harness.graph);
    assertRelationLabelsDoNotCoverNodes(harness.graph);
  } finally {
    harness.graph.dispose();
  }
});

test("force refinement keeps duplicate names separate through stable node keys", () => {
  const input = makeGraph(35);
  input.selectedId = "34";
  for (const node of input.nodes) {
    node.nodeCode = `os.node.${node.id}`;
    node.stableId = `stable-${node.id}`;
    node.name = "重复内部名称";
    node.displayName = "同名知识点";
  }
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    const data = graphData(harness);
    assert.equal(data.count(), 35);
    assert.equal(new Set(data.mapArray((index) => data.getId(index))).size, 35);
    assert.equal(data.getId(34), "os.node.34");
    assert.equal(
      nodeStyle(harness, 34).borderColor,
      harness.GRAPH_TOKENS.selection
    );
    assert.equal(graphEdgeData(harness).count(), 34);
    assertNodeCardsDoNotOverlap(harness.graph, 0.5);
    assertLabelsAttached(harness.graph);
  } finally {
    harness.graph.dispose();
  }
});

test("visible relation labels avoid rendered node cards at fit and zoom", () => {
  const input = makeGraph(35, 4);
  input.selectedId = "";
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    assertRelationLabelsDoNotCoverNodes(harness.graph);
    harness.graph.dispatchAction({
      type: "graphRoam",
      seriesIndex: 0,
      zoom: 1.7,
      originX: harness.graph.getWidth() / 2,
      originY: harness.graph.getHeight() / 2,
    });
    harness.flush();
    assertRelationLabelsDoNotCoverNodes(harness.graph);
  } finally {
    harness.graph.dispose();
  }
});

test("small graphs keep compact names readable at moderate zoom", () => {
  const harness = createHarness(makeGraph(12));
  try {
    harness.initChart();
    harness.flush();
    harness.graph.dispatchAction({
      type: "graphRoam",
      seriesIndex: 0,
      zoom: 0.4,
      originX: harness.graph.getWidth() / 2,
      originY: harness.graph.getHeight() / 2,
    });
    harness.flush();
    const model = graphData(harness).getItemModel(0);
    assert.equal(
      model.get("label.show"),
      true,
      "small graphs must keep labels visible at moderate zoom"
    );
    const formatter = String(model.get("label.formatter"));
    assert.ok(
      formatter.includes("{name|"),
      "compact labels must keep the name"
    );
    assert.ok(
      !formatter.includes("{num|"),
      "compact labels must drop the number badge to stay readable"
    );
    assertLabelsAttached(harness.graph);
  } finally {
    harness.graph.dispose();
  }
});

test("muted edges stay visible and gently curved", () => {
  const input = makeGraph(3);
  input.edgeTone = "muted";
  input.selectedId = "0";
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    const distant = edgeStyle(harness, 1);
    assert.ok((distant.width || 0) >= 1.4, "muted edges must be visible");
    assert.ok(
      (distant.curveness || 0) >= 0.08,
      "muted edges must curve instead of looking like straight scratches"
    );
  } finally {
    harness.graph.dispose();
  }
});

test("relation label toggle shows labels and clears them when disabled", () => {
  const input = makeGraph(12);
  input.selectedId = "0";
  input.showRelationLabels = true;
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    const visibleLabels = () => {
      const edgeData = harness.graph
        .getModel()
        .getSeriesByIndex(0)
        .getEdgeData();
      let visible = 0;
      edgeData.each((index) => {
        if (edgeData.getItemModel(index).get("label.show")) visible += 1;
      });
      return visible;
    };
    assert.ok(visibleLabels() > 0, "开启关系标签后选中节点的邻边必须显示标签");

    harness.props.showRelationLabels = false;
    harness.updateChart();
    harness.flush();
    assert.equal(visibleLabels(), 0, "关闭关系标签后不得残留标签");
  } finally {
    harness.graph.dispose();
  }
});

test("150-node course shows some non-overlapping relation labels at fit", () => {
  const input = makeGraph(150, 6);
  input.selectedId = "";
  input.showRelationLabels = true;
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    const edgeData = harness.graph.getModel().getSeriesByIndex(0).getEdgeData();
    let visible = 0;
    edgeData.each((index) => {
      if (edgeData.getItemModel(index).get("label.show")) visible += 1;
    });
    assert.ok(visible > 0, "大图默认适配下开启关系标签至少应显示避让后的标签");
  } finally {
    harness.graph.dispose();
  }
});

test("mastery, selection and label toggles preserve camera and layout", () => {
  const harness = createHarness(makeGraph(5));
  try {
    harness.initChart();
    harness.flush();
    harness.graph.dispatchAction({
      type: "graphRoam",
      seriesIndex: 0,
      dx: 35,
      dy: 20,
    });
    const before = harness.graph.getOption().series[0];
    const beforePositions = nodePositions(harness.graph);
    harness.props.nodes[1].status = "RED";
    harness.applyGraphState();
    harness.props.selectedId = "1";
    harness.props.showRelationLabels = false;
    harness.updateChart();
    harness.flush();
    const after = harness.graph.getOption().series[0];
    assert.deepEqual(after.center, before.center);
    assert.equal(after.zoom, before.zoom);
    const afterPositions = nodePositions(harness.graph);
    assert.ok(
      afterPositions.every(
        (point, index) =>
          Math.hypot(
            point[0] - beforePositions[index][0],
            point[1] - beforePositions[index][1]
          ) < 1
      ),
      "style updates must not restart the converged force layout"
    );
    assertLabelsAttached(harness.graph);
  } finally {
    harness.graph.dispose();
  }
});

test("zero-size mount recovers on resize", () => {
  const harness = createHarness(makeGraph(5), 0, 0);
  try {
    harness.initChart();
    harness.graph.resize({ width: 500, height: 420 });
    harness.handleContainerResize();
    harness.flush();
    assertNodesInView(harness.graph);
  } finally {
    harness.graph.dispose();
  }
});

test("course graph remains visible after a narrow resize", () => {
  const harness = createHarness(makeGraph(150, 6));
  try {
    harness.initChart();
    harness.flush();
    harness.graph.resize({ width: 390, height: 420 });
    harness.handleContainerResize();
    harness.flush();
    assertNodesInView(harness.graph);
    assertLabelsAttached(harness.graph);
  } finally {
    harness.graph.dispose();
  }
});

test("animated force layout settles and fits without moving the camera afterward", () => {
  const harness = createHarness(makeGraph(5), 1120, 620, true);
  const nativeSetTimeout = global.setTimeout;
  const nativeClearTimeout = global.clearTimeout;
  try {
    global.setTimeout = harness.timers.setTimeout;
    global.clearTimeout = harness.timers.clearTimeout;
    harness.initChart();
    harness.resetView();
    harness.flush();
    assertNodesInView(harness.graph);
    assertLabelsAttached(harness.graph);
  } finally {
    harness.graph.dispose();
    global.setTimeout = nativeSetTimeout;
    global.clearTimeout = nativeClearTimeout;
  }
});

test("dragging reheats the solver and moves connected cards without overlaps", () => {
  const harness = createHarness(makeGraph(13), 1120, 620, true);
  const nativeSetTimeout = global.setTimeout;
  const nativeClearTimeout = global.clearTimeout;
  try {
    global.setTimeout = harness.timers.setTimeout;
    global.clearTimeout = harness.timers.clearTimeout;
    harness.initChart();
    harness.flush();
    const series = harness.graph.getModel().getSeriesByIndex(0);
    assert.equal(series.get("layout"), "force");
    assert.equal(series.get("draggable"), true);
    assert.equal(series.__oslabForceSettled, true);
    const before = nodePositions(harness.graph);
    const element = series.getData().getItemGraphicEl(5);
    element.attr({ x: element.x + 180, y: element.y + 100 });
    element.trigger("drag", { offsetX: element.x, offsetY: element.y });
    assert.equal(series.__oslabForceSettled, false);
    // A pointer drag supplies multiple moves. The first move starts ECharts'
    // synchronous force tick; the following move supplies the held position.
    element.attr({ x: before[5][0] + 180, y: before[5][1] + 100 });
    element.trigger("drag", { offsetX: element.x, offsetY: element.y });
    element.trigger("dragend", {});
    harness.flush();
    const after = nodePositions(harness.graph);
    assert.equal(series.__oslabForceSettled, true);
    assert.notDeepEqual(
      after[5],
      before[5],
      "dragged card must retain its new area"
    );
    assert.ok(
      [4, 6].some(
        (index) =>
          Math.hypot(
            after[index][0] - before[index][0],
            after[index][1] - before[index][1]
          ) > 1
      ),
      "connected cards must respond to the force after dragging"
    );
    assertNodeCardsDoNotOverlap(harness.graph, 0.5);
    assertRelationLabelsDoNotCoverNodes(harness.graph);
  } finally {
    harness.graph.dispose();
    global.setTimeout = nativeSetTimeout;
    global.clearTimeout = nativeClearTimeout;
  }
});

test("canonical relations preserve prerequisite direction and Chinese display names", () => {
  const input = makeGraph(7);
  input.nodes[0].name = "os.memory.lru";
  input.nodes[0].displayName = "最近最久未使用";
  const types = [
    "REQUIRES",
    "CONTAINS",
    "RELATED_TO",
    "IMPLEMENTS",
    "CONTRASTS_WITH",
    "BASED_ON",
  ];
  input.links = input.links.map((link, index) => ({
    ...link,
    value: types[index],
    relationType: types[index],
  }));
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    const series = harness.graph.getOption().series[0];
    const links = series.links || series.edges;
    assert.equal(links[0].source, String(input.links[0].target));
    assert.equal(links[0].target, String(input.links[0].source));
    assert.deepEqual(links[4].symbol, ["circle", "circle"]);
    assert.deepEqual(links[5].symbol, ["none", "arrow"]);
    assert(links[4].relationText.includes("对比"));
    assert(links[5].relationText.includes("依据"));
    assert.equal(series.data[0].name, "os.memory.lru");
    assert.equal(series.data[0].displayName, "最近最久未使用");
    assert(!links[0].relationText.includes("os.memory.lru"));
  } finally {
    harness.graph.dispose();
  }
});

test("workbench entry uses the current node and a stable nodeKey", () => {
  assert.ok(
    viewTemplate.includes('@click="enterNodeWorkspace()"'),
    "the detail CTA must call enterNodeWorkspace() without an event argument"
  );
  assert.ok(
    !viewTemplate.includes('@click="enterNodeWorkspace"'),
    "the detail CTA must not bind enterNodeWorkspace directly"
  );
  assert.ok(
    viewTemplate.includes('@click="selectAndFocus(item.nodeKey)"'),
    "related-node entries must pass a stable nodeKey"
  );
  assert.ok(
    viewTemplate.includes('@click="selectUnitNode(mapping)"'),
    "unit mapping entries must pass the mapping for stable-key resolution"
  );
  assert.ok(
    viewTemplate.includes("nodeKeyForName(item.nodeName)"),
    "remediation path entries must resolve a stable nodeKey"
  );

  const harness = createViewHarness({
    fullPath: "/graph?courseCode=os&sectionCode=sec-1&nodeCode=os.memory.lru",
  });
  harness.nodes.value = [
    makeViewNode({
      id: "node-1",
      nodeCode: "os.memory.lru",
      name: "os.memory.lru",
      displayName: "最近最久未使用",
      homeSectionCode: "sec-1",
      status: "RED",
    }),
  ];
  harness.selectAndFocus("os.memory.lru", false);
  assert.equal(harness.canEnterNodeWorkspace.value, true);
  harness.enterNodeWorkspace();
  assert.equal(harness.pushed.length, 1);
  assert.equal(harness.pushed[0].path, "/knowledge/concept/os.memory.lru");
  assert.equal(harness.pushed[0].query.nodeCode, "os.memory.lru");
  assert.equal(harness.pushed[0].query.sectionCode, "sec-1");
  assert.equal(
    harness.pushed[0].query.returnTo,
    "/graph?courseCode=os&sectionCode=sec-1&nodeCode=os.memory.lru"
  );
  // A raw event object must never be interpreted as a node key.
  harness.enterNodeWorkspace({ type: "click" });
  assert.equal(harness.pushed.length, 1);

  const legacy = createViewHarness();
  legacy.nodes.value = [
    makeViewNode({
      id: "legacy-1",
      name: "旧节点",
      displayName: "旧节点",
    }),
  ];
  legacy.selectAndFocus("旧节点", false);
  legacy.enterNodeWorkspace();
  assert.equal(
    legacy.pushed[0].path,
    `/knowledge/node/${encodeURIComponent("旧节点")}`
  );
  assert.equal(legacy.pushed[0].query.nodeCode, undefined);

  const empty = createViewHarness();
  empty.enterNodeWorkspace();
  assert.equal(empty.pushed.length, 0, "no default node may be opened");
});

test("entering a different node returns to the entered node context", () => {
  const harness = createViewHarness({
    path: "/graph",
    query: {
      courseCode: "os",
      sectionCode: "sec-1",
      unitCode: "unit-1",
      nodeCode: "os.a",
    },
    fullPath:
      "/graph?courseCode=os&sectionCode=sec-1&unitCode=unit-1&nodeCode=os.a",
  });
  harness.nodes.value = [
    makeViewNode({
      id: "1",
      nodeCode: "os.a",
      name: "a.internal",
      displayName: "A",
      homeSectionCode: "sec-1",
    }),
    makeViewNode({
      id: "2",
      nodeCode: "os.b",
      name: "b.internal",
      displayName: "B",
      homeSectionCode: "sec-1",
    }),
  ];
  harness.links.value = [
    { source: "1", target: "2", relationType: "dependent" },
  ];
  harness.selectAndFocus("os.a", false);
  assert.equal(harness.selectedNodeKey.value, "os.a");

  // 双击未选中的 B：工作台打开 B，回跳地址也必须指向 B，而不是仍选中的 A
  harness.handleNodeDblClick({
    dataType: "node",
    nodeCode: "os.b",
    name: "b.internal",
    displayName: "B",
  });
  const entered = harness.pushed[harness.pushed.length - 1];
  assert.equal(entered.path, "/knowledge/concept/os.b");
  const returnQuery = new URLSearchParams(entered.query.returnTo.split("?")[1]);
  assert.equal(returnQuery.get("nodeCode"), "os.b");
  assert.equal(returnQuery.get("nodeName"), null);
  assert.equal(returnQuery.get("courseCode"), "os");
  assert.equal(returnQuery.get("sectionCode"), "sec-1");
  assert.equal(returnQuery.get("unitCode"), "unit-1");
  assert.ok(
    !entered.query.returnTo.includes("nodeCode=os.a"),
    `returnTo must not keep the stale selection: ${entered.query.returnTo}`
  );

  // 右侧按钮与问题入口进入当前选中节点，回跳同样指向该节点
  harness.selectAndFocus("os.b", false);
  harness.enterNodeWorkspace();
  const buttonEntry = harness.pushed[harness.pushed.length - 1];
  assert.equal(
    new URLSearchParams(buttonEntry.query.returnTo.split("?")[1]).get(
      "nodeCode"
    ),
    "os.b"
  );
  harness.openNodeQuestions();
  const questionsEntry = harness.pushed[harness.pushed.length - 1];
  assert.equal(questionsEntry.path, "/assessments");
  assert.equal(
    new URLSearchParams(questionsEntry.query.returnTo.split("?")[1]).get(
      "nodeCode"
    ),
    "os.b"
  );

  // 旧节点（无 nodeCode）回跳必须清掉过期 nodeCode，改用 nodeName
  harness.nodes.value = [
    ...harness.nodes.value,
    makeViewNode({ id: "legacy-1", name: "旧节点", displayName: "旧节点" }),
  ];
  harness.handleNodeDblClick({ id: "legacy-1", name: "旧节点" });
  const legacyEntry = harness.pushed[harness.pushed.length - 1];
  assert.equal(
    legacyEntry.path,
    `/knowledge/node/${encodeURIComponent("旧节点")}`
  );
  const legacyReturn = new URLSearchParams(
    legacyEntry.query.returnTo.split("?")[1]
  );
  assert.equal(legacyReturn.get("nodeCode"), null);
  assert.equal(legacyReturn.get("nodeName"), "旧节点");
  assert.equal(legacyReturn.get("courseCode"), "os");
});

test("node A to B selection clears A and keeps one adjacency set", () => {
  const input = makeGraph(4);
  input.nodes.forEach((node, index) => {
    node.status = ["RED", "GREEN", "YELLOW", "GRAY"][index];
  });
  input.selectedId = "0";
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    assert.equal(nodeStyle(harness, 0).borderColor, "#2447d8");
    assert.equal(nodeStyle(harness, 0).borderWidth, 2);
    assert.deepEqual(nodeLabelStyle(harness, 0), {
      color: "#3a4150",
      fontWeight: 600,
    });

    harness.props.selectedId = "1";
    harness.updateChart();
    harness.flush();

    assert.equal(nodeStyle(harness, 0).borderColor, "#c9ccbe");
    assert.equal(nodeStyle(harness, 0).borderWidth, 1);
    assert.deepEqual(nodeLabelStyle(harness, 0), {
      color: "#3a4150",
      fontWeight: 500,
    });
    assert.equal(nodeStyle(harness, 1).borderColor, "#2447d8");
    assert.equal(nodeStyle(harness, 1).borderWidth, 2);
    assert.deepEqual(nodeLabelStyle(harness, 1), {
      color: "#3a4150",
      fontWeight: 600,
    });
    assert.equal(edgeStyle(harness, 0).opacity, 1);
    assert.equal(edgeStyle(harness, 2).opacity, 0.55);
    assert.ok(edgeStyle(harness, 0).width > edgeStyle(harness, 2).width);

    // 关系标签开关只改文本，不得清掉选中态或邻接强调
    harness.props.showRelationLabels = false;
    harness.updateChart();
    harness.flush();
    assert.equal(nodeStyle(harness, 1).borderColor, "#2447d8");
    assert.equal(edgeStyle(harness, 0).opacity, 1);

    // 状态刷新仍保持单个选中，文字色跟随新状态
    harness.props.nodes[1].status = "RED";
    harness.applyGraphState();
    harness.flush();
    assert.equal(nodeStyle(harness, 1).borderColor, "#2447d8");
    assert.deepEqual(nodeLabelStyle(harness, 1), {
      color: "#3a4150",
      fontWeight: 600,
    });
    assert.equal(
      graphData(harness).getItemModel(1).get("label.rich.dot.color"),
      "#b8543f"
    );
  } finally {
    harness.graph.dispose();
  }
});

test("rapid A to B clicks only emit the last node", () => {
  const input = makeGraph(2);
  input.selectedId = "";
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    const data = harness.graph.getOption().series[0].data;
    harness.handleChartClick({ dataType: "node", data: data[0] });
    harness.handleChartClick({ dataType: "node", data: data[1] });
    harness.flush();
    assert.equal(harness.emitted.length, 1);
    assert.equal(harness.emitted[0].event, "node-click");
    assert.equal(harness.emitted[0].payload.id, "1");
  } finally {
    harness.graph.dispose();
  }
});

test("hover then click then mouseout leaves no emphasis residue", () => {
  const input = makeGraph(3);
  input.selectedId = "";
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    harness.graph.dispatchAction({
      type: "highlight",
      seriesIndex: 0,
      dataIndex: 0,
    });
    assert.ok(graphData(harness).getItemGraphicEl(0), "node graphic exists");

    harness.props.selectedId = "0";
    harness.updateChart();
    harness.flush();

    const refreshed = graphData(harness).getItemGraphicEl(0);
    const states = (refreshed && refreshed.currentStates) || [];
    assert.ok(
      !states.includes("emphasis"),
      `style refresh kept the hover emphasis state: ${states}`
    );

    harness.graph.dispatchAction({ type: "downplay", seriesIndex: 0 });
    harness.flush();
    const afterMouseOut = graphData(harness).getItemGraphicEl(0);
    assert.ok(
      !((afterMouseOut && afterMouseOut.currentStates) || []).includes(
        "emphasis"
      ),
      "mouseout must leave the node in its normal state"
    );
    assert.equal(nodeStyle(harness, 0).borderColor, "#2447d8");
  } finally {
    harness.graph.dispose();
  }
});

test("nodes use compact stable tiers with category tint and clamped labels", () => {
  const input = makeGraph(5);
  input.selectedId = "";
  input.nodes[0].name = "短";
  input.nodes[1].displayName =
    "一个非常非常长需要截断并且无法在两行内完整显示的知识点名称示例";
  input.nodes[4].category = "未定义类别";
  const harness = createHarness(input);
  try {
    const { GRAPH_TOKENS, STATUS_COLORS } = harness;
    harness.initChart();
    harness.flush();
    const option = harness.graph.getOption().series[0];
    const sizes = new Set();
    for (const node of option.data) {
      assert.equal(node.symbol, "rect");
      assert.equal(node.itemStyle.borderRadius, 8);
      assert.equal(node.itemStyle.borderWidth, 1);
      assert.equal(node.itemStyle.shadowBlur, 6);
      assert.ok(
        node.symbolSize[0] >= 72 &&
          node.symbolSize[0] <= 240 &&
          node.symbolSize[1] >= 38 &&
          node.symbolSize[1] <= 42,
        `small-graph card width must fit the label within bounds: ${node.symbolSize}`
      );
      sizes.add(`${node.symbolSize[0]}x${node.symbolSize[1]}`);
      const nameLines = String(node.label.formatter)
        .split("\n")
        .filter((line) => line.includes("{name|"));
      assert.ok(
        nameLines.length <= 2,
        `names must clamp to two lines: ${node.label.formatter}`
      );
    }
    assert.ok(
      sizes.size >= 3,
      "varied names must produce varied card widths on small graphs"
    );
    assert.ok(
      option.data[0].symbolSize[0] < 140,
      "short labels must produce narrower cards"
    );
    assert.ok(
      option.data[1].symbolSize[0] > 164,
      "long labels must produce wider cards"
    );
    assert.equal(option.data[0].itemStyle.color, GRAPH_TOKENS.nodeFill.concept);
    assert.equal(
      option.data[1].itemStyle.color,
      GRAPH_TOKENS.nodeFill.algorithm
    );
    assert.equal(
      option.data[2].itemStyle.color,
      GRAPH_TOKENS.nodeFill.function
    );
    assert.equal(
      option.data[4].itemStyle.color,
      GRAPH_TOKENS.nodeFill.fallback
    );

    const long = option.data[1];
    assert.equal(
      long.displayName,
      "一个非常非常长需要截断并且无法在两行内完整显示的知识点名称示例"
    );
    assert.ok(
      long.label.formatter.includes("…"),
      "a long name is truncated with an ellipsis"
    );
    assert.ok(
      !long.label.formatter.includes(long.displayName),
      "the clamped label must not render the full name"
    );
    assert.ok(long.label.formatter.includes("{dot|●}"));
    assert.equal(long.label.rich.name.color, "#3a4150");
    assert.equal(long.label.rich.dot.color, STATUS_COLORS.GRAY);
  } finally {
    harness.graph.dispose();
  }
});

test("annotation numbers render only on referenced nodes", () => {
  const input = makeGraph(3);
  input.annotations = { 0: 3, 1: 1 };
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    const data = harness.graph.getOption().series[0].data;
    assert.ok(String(data[0].label.formatter).includes("{num|3}"));
    assert.ok(String(data[1].label.formatter).includes("{num|1}"));
    assert.ok(!String(data[2].label.formatter).includes("{num|"));
    assert.equal(
      data[0].label.rich.name.backgroundColor,
      harness.GRAPH_TOKENS.highlightSoft
    );
  } finally {
    harness.graph.dispose();
  }
});

test("small organic layout keeps twelve numbered cards apart", () => {
  const input = makeGraph(12);
  input.annotations = Object.fromEntries(
    input.nodes.map((node, index) => [node.id, index + 1])
  );
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    const data = harness.graph.getOption().series[0].data;
    assert.equal(
      data.filter((node) => String(node.label.formatter).includes("{num|"))
        .length,
      12,
      "all twelve visible nodes must render a number"
    );
    let overlaps = 0;
    for (let i = 0; i < data.length; i++) {
      for (let j = i + 1; j < data.length; j++) {
        const a = data[i];
        const b = data[j];
        if (
          Math.abs(a.x - b.x) < (a.symbolSize[0] + b.symbolSize[0]) / 2 + 8 &&
          Math.abs(a.y - b.y) < (a.symbolSize[1] + b.symbolSize[1]) / 2 + 8
        ) {
          overlaps += 1;
        }
      }
    }
    assert.equal(overlaps, 0, "organic layout must keep card boxes apart");
  } finally {
    harness.graph.dispose();
  }
});

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

test("selected node uses an attached selection outline", () => {
  const harness = createHarness(makeGraph(3));
  try {
    harness.initChart();
    harness.flush();
    const series = harness.graph.getOption().series[0];
    assert.equal(
      graphData(harness).getItemModel(0).get("itemStyle.borderWidth"),
      2
    );
    assert.equal(series.markPoint, undefined);
  } finally {
    harness.graph.dispose();
  }
});

test("small graphs number every visible node", () => {
  const harness = createViewHarness();
  const nodes = Array.from({ length: 11 }, (_, index) => ({
    id: String(index),
    name: `node-${index}`,
  }));
  const result = harness.buildVisibleAnnotations(nodes, "2", [], 12);
  assert.equal(Object.keys(result).length, 11);
  assert.equal(result["0"], 1);
  assert.equal(result["2"], 3);
  assert.equal(result["10"], 11);

  const selectedFive = harness.buildVisibleAnnotations(nodes, "5", [], 12);
  assert.equal(selectedFive["5"], 3, "selected node must always be ③");
  assert.equal(selectedFive["0"], 1);
  assert.equal(selectedFive["10"], 11);
});

test("large graphs fall back to selected node and two nearest relations", () => {
  const harness = createViewHarness();
  const nodes = Array.from({ length: 150 }, (_, index) => ({
    id: String(index),
    name: `node-${index}`,
  }));
  const result = harness.buildVisibleAnnotations(
    nodes,
    "3",
    [{ nodeKey: "7" }, { nodeKey: "9" }],
    12
  );
  assert.deepEqual(result, { 3: 3, 7: 1, 9: 2 });
});

test("RED/GREEN/YELLOW/GRAY status dots survive selection", () => {
  const input = makeGraph(4);
  const statuses = ["RED", "GREEN", "YELLOW", "GRAY"];
  input.nodes.forEach((node, index) => {
    node.status = statuses[index];
  });
  input.selectedId = "0";
  const harness = createHarness(input);
  try {
    const { GRAPH_TOKENS, STATUS_COLORS } = harness;
    harness.initChart();
    harness.flush();
    const assertStatusLabels = () => {
      statuses.forEach((status, index) => {
        const model = graphData(harness).getItemModel(index);
        const nameColor = model.get("label.rich.name.color");
        assert.ok(
          nameColor === "#3a4150" || nameColor === "#6b7168",
          `${status} title stays readable, never status-tinted: ${nameColor}`
        );
        assert.equal(model.get("label.rich.dot.color"), STATUS_COLORS[status]);
        assert.ok(
          String(model.get("label.formatter")).includes("{dot|●}"),
          `${status} status dot must stay visible`
        );
      });
    };
    assertStatusLabels();
    assert.equal(nodeStyle(harness, 0).borderColor, GRAPH_TOKENS.selection);
    assert.equal(nodeStyle(harness, 0).borderWidth, 2);
    assert.equal(nodeStyle(harness, 0).shadowColor, GRAPH_TOKENS.selectionRing);
    assert.ok(nodeStyle(harness, 0).shadowBlur >= 2);
    assert.equal(nodeStyle(harness, 1).borderColor, GRAPH_TOKENS.nodeBorder);
    assert.equal(nodeStyle(harness, 1).shadowBlur, 6);

    harness.props.selectedId = "2";
    harness.updateChart();
    harness.flush();
    assertStatusLabels();
    assert.equal(nodeStyle(harness, 2).borderColor, GRAPH_TOKENS.selection);
    assert.equal(nodeStyle(harness, 0).borderColor, GRAPH_TOKENS.nodeBorder);
    assert.equal(
      nodeStyle(harness, 0).shadowBlur,
      6,
      "non-selected cards keep only the soft card shadow"
    );
  } finally {
    harness.graph.dispose();
  }
});

test("fillByStatus keeps aggregate status fill, border and text", () => {
  const input = makeGraph(4);
  input.selectedId = "";
  input.fillByStatus = true;
  const statuses = ["GREEN", "YELLOW", "RED", "GRAY"];
  input.nodes.forEach((node, index) => {
    node.status = statuses[index];
  });
  const harness = createHarness(input);
  try {
    const { GRAPH_TOKENS, STATUS_COLORS } = harness;
    harness.initChart();
    harness.flush();
    statuses.forEach((status, index) => {
      const style = nodeStyle(harness, index);
      assert.equal(style.color, GRAPH_TOKENS.statusFill[status]);
      assert.equal(style.borderColor, STATUS_COLORS[status]);
      assert.equal(style.borderWidth, 1);
      assert.equal(
        graphData(harness).getItemModel(index).get("label.rich.name.color"),
        "#3a4150"
      );
      assert.equal(
        graphData(harness).getItemModel(index).get("label.rich.dot.color"),
        STATUS_COLORS[status]
      );
    });
  } finally {
    harness.graph.dispose();
  }
});

test("path emphasis uses the path edge token, not selection blue", () => {
  assert.ok(
    viewSource.includes("lineStyle: { pathEmphasis: true }"),
    "the view must delegate path line styling to the component token"
  );
  const input = makeGraph(2);
  input.selectedId = "";
  input.links[0].lineStyle = { pathEmphasis: true };
  const harness = createHarness(input);
  try {
    const { GRAPH_TOKENS } = harness;
    harness.initChart();
    harness.flush();
    const style = edgeStyle(harness, 0);
    assert.equal(style.color, GRAPH_TOKENS.pathEdge);
    assert.equal(style.width, 2.2);
    assert.notEqual(style.color, GRAPH_TOKENS.selection);
  } finally {
    harness.graph.dispose();
  }
});

test("blue pulse on a path node uses the path token, never primary", () => {
  const input = makeGraph(2);
  input.selectedId = "";
  input.nodes[1].pathHighlighted = true;
  const harness = createHarness(input);
  try {
    const { GRAPH_TOKENS } = harness;
    harness.initChart();
    harness.flush();
    harness.pulseMap.value = new Map([[String(input.nodes[1].id), "blue"]]);
    harness.updateChart();
    harness.flush();
    assert.equal(nodeStyle(harness, 1).borderColor, GRAPH_TOKENS.pathStrong);
    assert.equal(
      nodeStyle(harness, 1).borderWidth,
      1,
      "a pulse must not create a second 2px selection outline"
    );
    assert.notEqual(nodeStyle(harness, 1).borderColor, GRAPH_TOKENS.selection);
    assert.equal(nodeStyle(harness, 1).shadowColor, GRAPH_TOKENS.pathRing);

    harness.pulseMap.value = new Map();
    harness.updateChart();
    harness.flush();
    assert.equal(nodeStyle(harness, 1).borderColor, GRAPH_TOKENS.path);
    assert.equal(nodeStyle(harness, 1).borderWidth, 1);
  } finally {
    harness.graph.dispose();
  }
});

test("graph palette mirrors learning-theme tokens", () => {
  const paletteHarness = createHarness(makeGraph(1));
  try {
    const { GRAPH_TOKENS } = paletteHarness;
    const themeSource = fs.readFileSync(
      path.join(__dirname, "../src/styles/learning-theme.css"),
      "utf8"
    );
    const cssTokens = new Map();
    for (const match of themeSource.matchAll(
      /--([a-z0-9-]+)\s*:\s*([^;]+);/gi
    )) {
      if (!cssTokens.has(match[1])) cssTokens.set(match[1], match[2]);
    }
    const normalize = (value) =>
      String(value).replace(/\s+/g, " ").trim().toLowerCase();
    const assertToken = (name, actual) => {
      const expected = cssTokens.get(name);
      assert.ok(expected, `learning-theme.css must define --${name}`);
      assert.equal(
        normalize(actual),
        normalize(expected),
        `GRAPH_TOKENS.${name} must mirror --${name}`
      );
    };

    assertToken("oslab-graph-surface", GRAPH_TOKENS.surface);
    assertToken("oslab-graph-node-border", GRAPH_TOKENS.nodeBorder);
    assertToken("oslab-graph-node-radius", `${GRAPH_TOKENS.nodeRadius}px`);
    assertToken("oslab-graph-category-concept", GRAPH_TOKENS.category.concept);
    assertToken(
      "oslab-graph-category-algorithm",
      GRAPH_TOKENS.category.algorithm
    );
    assertToken(
      "oslab-graph-category-function",
      GRAPH_TOKENS.category.function
    );
    assertToken("oslab-graph-category-default", GRAPH_TOKENS.category.fallback);
    assertToken("oslab-graph-node-fill-concept", GRAPH_TOKENS.nodeFill.concept);
    assertToken(
      "oslab-graph-node-fill-algorithm",
      GRAPH_TOKENS.nodeFill.algorithm
    );
    assertToken(
      "oslab-graph-node-fill-function",
      GRAPH_TOKENS.nodeFill.function
    );
    assertToken(
      "oslab-graph-node-fill-default",
      GRAPH_TOKENS.nodeFill.fallback
    );
    assertToken("oslab-graph-node-shadow", GRAPH_TOKENS.nodeShadow);
    assertToken("oslab-graph-selection", GRAPH_TOKENS.selection);
    assertToken("oslab-graph-selection-ring", GRAPH_TOKENS.selectionRing);
    assertToken("oslab-graph-path", GRAPH_TOKENS.path);
    assertToken("oslab-graph-path-strong", GRAPH_TOKENS.pathStrong);
    assertToken("oslab-graph-path-edge", GRAPH_TOKENS.pathEdge);
    assertToken("oslab-graph-path-ring", GRAPH_TOKENS.pathRing);
    assertToken("oslab-graph-review-ring", GRAPH_TOKENS.reviewRing);
    assertToken("oslab-graph-text-soft", GRAPH_TOKENS.textSoft);
    assertToken("oslab-graph-label-surface", GRAPH_TOKENS.labelSurface);
    assertToken("oslab-graph-highlight-soft", GRAPH_TOKENS.highlightSoft);
    assertToken("oslab-graph-highlight", GRAPH_TOKENS.highlight);
    assertToken("oslab-graph-edge-muted", GRAPH_TOKENS.mutedEdge);
    assertToken(
      "oslab-graph-relation-dependent",
      GRAPH_TOKENS.relation.dependent
    );
    assertToken("oslab-graph-relation-contain", GRAPH_TOKENS.relation.contain);
    assertToken("oslab-graph-relation-related", GRAPH_TOKENS.relation.related);
    assertToken(
      "oslab-graph-relation-implements",
      GRAPH_TOKENS.relation.implements
    );
    assertToken(
      "oslab-graph-relation-fallback",
      GRAPH_TOKENS.relation.fallback
    );
    assertToken("oslab-primary", GRAPH_TOKENS.selection);
    assertToken("oslab-ink-soft", GRAPH_TOKENS.inkSoft);
    assertToken("oslab-muted-strong", GRAPH_TOKENS.textSoft);
    assertToken("oslab-mastered", GRAPH_TOKENS.status.GREEN);
    assertToken("oslab-review", GRAPH_TOKENS.status.YELLOW);
    assertToken("oslab-weak", GRAPH_TOKENS.status.RED);
    assertToken("oslab-unseen", GRAPH_TOKENS.status.GRAY);
    assertToken("oslab-mastered-soft", GRAPH_TOKENS.statusFill.GREEN);
    assertToken("oslab-review-soft", GRAPH_TOKENS.statusFill.YELLOW);
    assertToken("oslab-weak-soft", GRAPH_TOKENS.statusFill.RED);
    assertToken("oslab-unseen-soft", GRAPH_TOKENS.statusFill.GRAY);
  } finally {
    paletteHarness.graph.dispose();
  }
});

test("legend explains category, mastery and path with theme tokens", () => {
  for (const caption of ["类型", "掌握", "路径"]) {
    assert.ok(
      viewTemplate.includes(`legend-caption">${caption}</span>`),
      `legend must contain the ${caption} group`
    );
  }
  assert.ok(viewTemplate.includes("legend-status-mastered"));
  assert.ok(viewTemplate.includes("legend-status-review"));
  assert.ok(viewTemplate.includes("legend-status-weak"));
  assert.ok(viewTemplate.includes("legend-status-unseen"));
  assert.ok(viewTemplate.includes("rel-path"));
  assert.ok(
    !viewTemplate.includes('style="background: #'),
    "legend swatches must use theme tokens, not inline color literals"
  );

  assert.match(
    viewSource,
    /\.swatch-concept\s*\{\s*background:\s*var\(--oslab-graph-category-concept\)/
  );
  assert.match(
    viewSource,
    /\.swatch-algorithm\s*\{\s*background:\s*var\(--oslab-graph-category-algorithm\)/
  );
  assert.match(
    viewSource,
    /\.swatch-function\s*\{\s*background:\s*var\(--oslab-graph-category-function\)/
  );
  assert.match(
    viewSource,
    /\.legend-status-mastered\s*\{\s*color:\s*var\(--oslab-mastered(?:-text)?\)/
  );
  assert.match(
    viewSource,
    /\.legend-status-review\s*\{\s*color:\s*var\(--oslab-review(?:-text)?\)/
  );
  assert.match(
    viewSource,
    /\.legend-status-weak\s*\{\s*color:\s*var\(--oslab-weak(?:-text)?\)/
  );
  assert.match(
    viewSource,
    /\.legend-status-unseen\s*\{\s*color:\s*var\(--oslab-unseen(?:-text)?\)/
  );
  assert.match(
    viewSource,
    /\.rel-path\s*\{\s*border-top:\s*2px solid var\(--oslab-graph-path-edge\)/
  );
});

test("unit path hint stays distinct from the selected outline", () => {
  const input = makeGraph(3);
  input.selectedId = "";
  input.nodes[0].pathHighlighted = true;
  input.nodes[0].status = "RED";
  input.nodes[1].highlight = true;
  input.nodes[1].status = "GREEN";
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    assert.equal(nodeStyle(harness, 0).borderColor, "#8fa6c4");
    assert.equal(nodeStyle(harness, 0).borderWidth, 1);
    assert.deepEqual(nodeLabelStyle(harness, 0), {
      color: "#3a4150",
      fontWeight: 500,
    });
    assert.equal(nodeStyle(harness, 1).borderColor, "#8fa6c4");
    assert.equal(nodeStyle(harness, 1).borderWidth, 1);
    assert.deepEqual(nodeLabelStyle(harness, 1), {
      color: "#3a4150",
      fontWeight: 500,
    });

    harness.props.selectedId = "0";
    harness.updateChart();
    harness.flush();
    assert.equal(nodeStyle(harness, 0).borderColor, "#2447d8");
    assert.equal(nodeStyle(harness, 0).borderWidth, 2);
    assert.deepEqual(nodeLabelStyle(harness, 0), {
      color: "#3a4150",
      fontWeight: 600,
    });
    assert.equal(
      nodeStyle(harness, 1).borderColor,
      "#8fa6c4",
      "the unselected path hint must not become a second selection"
    );
  } finally {
    harness.graph.dispose();
  }
});

test("duplicate display names keep distinct nodeCode identity", () => {
  const harness = createHarness(makeIdentityGraph());
  try {
    harness.initChart();
    harness.flush();
    const option = harness.graph.getOption().series[0];
    assert.deepEqual(
      option.data.map((node) => node.id),
      ["os.a", "os.b"]
    );
    assert.equal(option.data[0].nodeCode, "os.a");
    assert.equal(option.data[0].stableId, "stable-a");
    assert.equal(option.data[0].displayName, "同名知识点");
    assert.equal(option.data[0].status, "RED");
    assert.equal(option.data[0].highlightReason, "teaches-current-unit");
    assert.equal(option.data[1].nodeCode, "os.b");
    const links = option.links || option.edges;
    assert.deepEqual([links[0].source, links[0].target].sort(), [
      "os.a",
      "os.b",
    ]);

    harness.handleChartClick({ dataType: "node", data: option.data[1] });
    harness.flush();
    assert.equal(harness.emitted.length, 1);
    assert.equal(harness.emitted[0].event, "node-click");
    assert.equal(harness.emitted[0].payload.nodeCode, "os.b");
    assert.equal(harness.emitted[0].payload.stableId, "stable-b");
    assert.equal(harness.emitted[0].payload.name, "b.internal");
    assert.equal(harness.emitted[0].payload.displayName, "同名知识点");
    assert.equal(harness.emitted[0].payload.status, "GREEN");

    harness.handleNodeDblClick({ dataType: "node", data: option.data[1] });
    assert.equal(harness.emitted[1].event, "node-dblclick");
    assert.equal(harness.emitted[1].payload.nodeCode, "os.b");
  } finally {
    harness.graph.dispose();
  }
});

test("blank canvas or relation clicks do not change selection", () => {
  const harness = createHarness(makeGraph(2));
  try {
    harness.initChart();
    harness.flush();
    harness.handleChartClick({ dataType: "edge", data: { relationText: "x" } });
    harness.flush();
    assert.equal(harness.emitted.length, 0);
    harness.handleChartClick({
      dataType: "node",
      data: harness.graph.getOption().series[0].data[0],
    });
    harness.flush();
    assert.equal(harness.emitted.length, 1);
  } finally {
    harness.graph.dispose();
  }
});

test("clicking node B routes to B with one selected detail", () => {
  const harness = createViewHarness();
  harness.nodes.value = [
    makeViewNode({
      id: "1",
      nodeCode: "os.a",
      name: "a.internal",
      displayName: "同名知识点",
      status: "RED",
    }),
    makeViewNode({
      id: "2",
      nodeCode: "os.b",
      name: "b.internal",
      displayName: "同名知识点",
      status: "GREEN",
    }),
  ];
  harness.links.value = [
    { source: "1", target: "2", relationType: "dependent" },
  ];
  harness.selectAndFocus("os.a", false);
  assert.equal(harness.selectedNodeKey.value, "os.a");
  harness.selectUnitNode({
    nodeCode: "missing-code",
    name: "a.internal",
    role: "TEACHES",
  });
  assert.equal(
    harness.selectedNodeKey.value,
    "os.a",
    "unit mapping entries must resolve a stable key by name fallback"
  );
  const groups = harness.relationGroups.value;
  const prerequisite = groups.prerequisites.find(
    (item) => item.nodeKey === "os.b"
  );
  assert.ok(prerequisite, "related entry must carry a stable nodeKey");

  harness.handleNodeClick({
    dataType: "node",
    nodeCode: "os.b",
    stableId: "stable-b",
    id: "os.b",
    name: "b.internal",
    displayName: "同名知识点",
    status: "GREEN",
  });
  assert.equal(harness.selectedNodeKey.value, "os.b");
  assert.equal(harness.selectedNodeId.value, "os.b");
  assert.equal(harness.selectedNode.value.name, "b.internal");
  assert.ok(harness.neighborKeys.value.has("os.a"));
  assert.equal(
    harness.replaced[harness.replaced.length - 1].query.nodeCode,
    "os.b"
  );
  harness.handleNodeDblClick({
    dataType: "node",
    nodeCode: "os.b",
    name: "b.internal",
    displayName: "同名知识点",
    status: "GREEN",
  });
  assert.equal(
    harness.pushed[harness.pushed.length - 1].path,
    "/knowledge/concept/os.b"
  );
  harness.selectAndFocus(prerequisite.nodeKey, false);
  assert.equal(harness.selectedNodeKey.value, "os.b");
  harness.enterNodeWorkspace();
  assert.equal(
    harness.pushed[harness.pushed.length - 1].path,
    "/knowledge/concept/os.b"
  );
});

test("a filtered-out selection leaves no hidden adjacency emphasis", () => {
  const input = makeGraph(3);
  input.selectedId = "0";
  const harness = createHarness(input);
  try {
    harness.initChart();
    harness.flush();
    assert.equal(nodeStyle(harness, 0).borderColor, "#2447d8");

    harness.props.nodes = input.nodes.slice(1);
    harness.props.links = input.links.filter(
      (link) => link.source !== "0" && link.target !== "0"
    );
    harness.applyGraphState();
    harness.flush();

    assert.equal(nodeStyle(harness, 0).borderColor, "#c9ccbe");
    assert.equal(nodeStyle(harness, 1).borderColor, "#c9ccbe");
    assert.equal(edgeStyle(harness, 0).opacity, 1);
  } finally {
    harness.graph.dispose();
  }
});

test("filtering out the selected node clears selection and detail", () => {
  const harness = createViewHarness();
  harness.nodes.value = [
    makeViewNode({
      id: "1",
      nodeCode: "os.a",
      name: "a.internal",
      displayName: "同名知识点",
      status: "GRAY",
    }),
    makeViewNode({
      id: "2",
      nodeCode: "os.b",
      name: "b.internal",
      displayName: "同名知识点",
      status: "GREEN",
    }),
  ];
  harness.links.value = [
    { source: "1", target: "2", relationType: "dependent" },
  ];
  harness.selectAndFocus("os.a", false);
  assert.equal(harness.selectedNodeId.value, "os.a");
  assert.ok(harness.neighborKeys.value.has("os.b"));
  harness.selectedWorkbench.value = {
    lastFailure: { errorType: "DEMO" },
  };
  harness.pathData.value = {
    items: [{ nodeName: "b.internal", order: 1, reason: "demo" }],
    targetNodeName: "a.internal",
  };

  harness.statusFilter.value = "GREEN";
  assert.equal(
    harness.filteredNodes.value.some(
      (node) => harness.nodeKeyOf(node) === "os.a"
    ),
    false
  );
  assert.equal(harness.selectedNodeKey.value, "");
  assert.equal(harness.selectedNode.value, null);
  assert.equal(harness.selectedNodeId.value, "");
  assert.equal(harness.selectedWorkbench.value, null);
  assert.equal(harness.pathData.value, null);
  assert.ok(
    !harness.canvasGraph.value.nodes.some(
      (node) => harness.nodeKeyOf(node) === "os.a"
    )
  );
});

if (failures.length) process.exitCode = 1;
