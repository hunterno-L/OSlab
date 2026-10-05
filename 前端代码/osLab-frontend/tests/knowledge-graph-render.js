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
const script = parse(source).descriptor.scriptSetup.content;
const compiled = ts.transpileModule(
  script +
    "\nexport { initChart, applyGraphState, updateChart, resetView, handleContainerResize };",
  {
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.CommonJS,
    },
  }
).outputText;

function createHarness(input, width = 1120, height = 620, asyncLayout = false) {
  const graph = echarts.init(null, null, {
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
  const callbacks = new Map();
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
    () => () => undefined,
    () => undefined,
    {
      ...timers,
      requestAnimationFrame: (callback) => timers.setTimeout(callback, 16),
      cancelAnimationFrame: (id) => callbacks.delete(id),
    },
    undefined
  );
  return {
    graph,
    props,
    timers,
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

function assertLabelsAttached(graph) {
  const series = graph.getModel().getSeriesByIndex(0);
  const data = series.getData();
  data.each((index) => {
    const symbol = data.getItemGraphicEl(index).childAt(0);
    const nodeRect = globalRect(symbol);
    const textRect = globalRect(symbol.getTextContent());
    const tolerance = 3;
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
    for (const zoom of [0.2, 0.6, 1, 1.8]) {
      harness.graph.setOption({ series: [{ zoom }] });
      harness.flush();
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

test("animated force settles before the final auto-fit", () => {
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

if (failures.length) process.exitCode = 1;
