/* Exercise instrumented ORIGINAL Vue 2 scripts with their real sprite-timeline
 * package. Canvas nodes are replaced by recorders; scheduling/pausing is not.
 * Run: node tests/legacy-simulation-events.js [original Vue 2 checkout]
 */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const root = path.resolve(__dirname, "..");
const original = path.resolve(
  process.argv[2] ||
    process.env.OSLAB_LEGACY_DEMO_ROOT ||
    path.join(
      root,
      "../../../demo_operatingsystem 2025.5.26/demo_operatingsystem"
    )
);
const babel = require(require.resolve("@babel/core", {
  paths: [original, root],
}));
const tracePlugin = require("../scripts/legacy-simulation-trace.cjs");
const bridge = fs.readFileSync(
  path.join(root, "scripts/legacy-simulation-bridge.js"),
  "utf8"
);
const timelineCode = fs.readFileSync(
  require.resolve("sprite-timeline/dist/sprite-timeline.js", {
    paths: [original],
  }),
  "utf8"
);
const phases = new Set(
  "initialize ready select run idle complete preempt yield demote search allocate fail hit fault replace load seek boundary jump result".split(
    " "
  )
);

function scriptOf(filename, instrument = true) {
  const fullpath = path.join(original, "src/views", filename);
  let script = fs
    .readFileSync(fullpath, "utf8")
    .match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
  if (instrument)
    script = babel.transformSync(script, {
      filename: fullpath,
      plugins: [tracePlugin],
      configFile: false,
      babelrc: false,
    }).code;
  // The original C-SCAN jump label has a missing fifth parameter. Apply this
  // signature-only compatibility correction in memory to exercise that branch;
  // production must apply the same correction to its staged copy separately.
  if (filename === "unit5/CSCAN.vue")
    script = script.replace(
      /drawArrow0\(len,\s*start,\s*no,\s*last\)/,
      "drawArrow0(len, start, no, last, pos)"
    );
  return script
    .replace(/^import[^\n]*\n/gm, "")
    .replace("export default", "module.exports =");
}

function harness(filename, route, instrument = true) {
  const timers = new Map();
  const messages = [];
  const nativeAnimations = [];
  const scenes = [];
  const listeners = {};
  let now = 0;
  let timerId = 0;
  const setTimer = (callback, delay = 0) => {
    const id = ++timerId;
    timers.set(id, { due: now + Math.max(0, Number(delay) || 0), callback });
    return id;
  };
  const advance = (elapsed) => {
    const until = now + elapsed;
    let budget = 100000;
    while (budget--) {
      let next;
      for (const [id, timer] of timers) {
        if (timer.due <= until && (!next || timer.due < next[1].due))
          next = [id, timer];
      }
      if (!next) break;
      now = next[1].due;
      timers.delete(next[0]);
      next[1].callback();
    }
    assert(budget > 0, "timeline became stuck in a timer loop");
    now = until;
  };
  const element = (values = {}) => ({
    children: [],
    offsetWidth: 0,
    offsetHeight: 0,
    offsetTop: 0,
    offsetLeft: 0,
    scrollWidth: 0,
    scrollHeight: 0,
    offsetParent: null,
    replaceChildren() {
      this.children = [];
      this.cleared = (this.cleared || 0) + 1;
    },
    querySelectorAll() {
      return [];
    },
    // Deliberately wrong transformed dimensions: resize must ignore these.
    getBoundingClientRect() {
      return { width: 256, height: 100 };
    },
    ...values,
  });
  const app = element({
    offsetWidth: 1280,
    offsetHeight: 700,
    scrollWidth: 1440,
    scrollHeight: 900,
  });
  const stage = element({
    offsetWidth: 1300,
    offsetHeight: 500,
    offsetTop: 650,
    offsetParent: app,
  });
  // Vue 2 replaces its mount #app with the original #outer component root.
  // Keep #app absent so the frame size regression is exercised in every case.
  const elements = { outer: app };
  for (const id of ["adaptive", "adaptive_5", "adaptive_PA", "adaptiveR"])
    elements[id] = element();
  app.querySelectorAll = (selector) => (selector === "button" ? [] : [stage]);
  const document = {
    getElementById: (id) => elements[id] || null,
    documentElement: element(),
  };
  let resize;
  const observedElements = new Set();
  const window = {
    location: { origin: "https://oslab.test" },
    parent: {
      postMessage: (message, origin) => {
        assert.equal(origin, window.location.origin);
        messages.push(message);
      },
    },
    addEventListener: (name, listener) => {
      listeners[name] = listener;
    },
  };
  const context = vm.createContext({
    window,
    document,
    // eslint-disable-next-line @typescript-eslint/no-empty-function -- VM 沙箱需要静默 console
    console: { log() {}, warn() {}, error() {} },
    setTimeout: setTimer,
    clearTimeout: (id) => timers.delete(id),
    requestAnimationFrame: (fn) => setTimer(() => fn(now), 16),
    cancelAnimationFrame: (id) => timers.delete(id),
    ResizeObserver: class {
      constructor(fn) {
        resize = fn;
      }
      observe(element) {
        observedElements.add(element);
      }
      unobserve(element) {
        observedElements.delete(element);
      }
    },
    Date: class extends Date {
      static now() {
        return now;
      }
    },
    module: { exports: {} },
  });
  vm.runInContext(timelineCode, context, { filename: "sprite-timeline.js" });
  const Timeline = window.Timeline;

  class Node {
    constructor(attributes = {}) {
      this.attributes =
        typeof attributes === "string"
          ? { text: attributes }
          : { ...attributes };
      this.children = [];
      this.animations = new Set();
    }
    get childNodes() {
      return this.children;
    }
    toJSON() {
      return { attributes: this.attributes };
    }
    get layer() {
      return this._layer || this.parent?.layer;
    }
    get text() {
      return this.attributes.text;
    }
    get pos() {
      return this.attributes.pos;
    }
    attr(attributes, value) {
      if (typeof attributes === "string") {
        if (arguments.length === 1) return this.attributes[attributes];
        this.attributes[attributes] = value;
      } else Object.assign(this.attributes, attributes);
      return this;
    }
    append(...nodes) {
      for (const node of nodes) this.appendChild(node);
    }
    appendChild(node) {
      node.parent = this;
      this.children.push(node);
      return node;
    }
    removeAllChildren() {
      for (const child of [...this.children]) child.remove();
    }
    remove() {
      if (this.parent)
        this.parent.children = this.parent.children.filter(
          (node) => node !== this
        );
      this.parent = null;
    }
    animate(keyframes, timing) {
      const timeline = this.layer.timeline;
      const record = {
        node: this,
        keyframes,
        timing,
        scheduled: timeline.currentTime,
        began: null,
      };
      nativeAnimations.push(record);
      const ids = [];
      const animation = {
        cancel: () => {
          ids.forEach((id) => timeline.clearTimeout(id));
          this.animations.delete(animation);
        },
      };
      this.animations.add(animation);
      ids.push(
        timeline.setTimeout(() => {
          record.began = timeline.currentTime;
        }, timing.delay || 0)
      );
      ids.push(
        timeline.setTimeout(() => {
          Object.assign(this.attributes, keyframes[keyframes.length - 1]);
        }, (timing.delay || 0) + (timing.duration || 0))
      );
      record.animation = animation;
      return animation;
    }
  }
  class Scene {
    constructor(options) {
      this.options = options;
      scenes.push(this);
    }
    layer() {
      const layer = new Node();
      layer._layer = layer;
      layer.timeline = new Timeline({ nowtime: () => now });
      this.options.container?.children.push(layer);
      return layer;
    }
  }
  context.spritejs = {
    Scene,
    Sprite: Node,
    Rect: Node,
    Label: Node,
    Group: Node,
    Path: Node,
    Gradient: Node,
  };
  vm.runInContext(bridge, context, { filename: "legacy-simulation-bridge.js" });
  context.module = { exports: {} };
  const globals = fs
    .readFileSync(path.join(original, "src/global.vue"), "utf8")
    .match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1]
    .replace("export default", "module.exports =");
  vm.runInContext(globals, context);
  const GLOBAL = context.module.exports;
  context.module = { exports: {} };
  context.navbar = {};
  vm.runInContext(scriptOf(filename, instrument), context, { filename });
  const options = context.module.exports;
  const view = {
    GLOBAL,
    $options: options,
    $route: { name: route },
    $el: app,
    $nextTick(fn) {
      return Promise.resolve().then(() => fn?.());
    },
    $set(target, key, value) {
      target[key] = value;
    },
  };
  Object.assign(view, options.data.call(view));
  view.$data = view;
  for (const [name, method] of Object.entries(options.methods))
    view[name] = method.bind(view);
  let mixin;
  window.__oslabLegacyAttachVue({
    config: {},
    mixin(value) {
      mixin = value;
    },
  });
  options.methods.init.call(view);
  mixin.mounted.call(view);
  const state = window.__oslabLegacyState;
  const configure = async (input) =>
    listeners.message({
      source: window.parent,
      origin: window.location.origin,
      data: {
        channel: "oslab-legacy-simulation",
        key: state.key,
        type: "configure",
        input,
      },
    });
  const run = () =>
    view[
      state.key.startsWith("cpu.")
        ? "submitForm"
        : state.key.startsWith("allocation.")
        ? "onSubmit2"
        : "onSubmit1"
    ]();
  const traceMessages = () =>
    messages.filter((message) => message.type === "trace");
  const snapshot = () =>
    JSON.parse(
      JSON.stringify({
        animations: nativeAnimations.map((animation) => ({
          keyframes: animation.keyframes,
          timing: animation.timing,
          scheduled: animation.scheduled,
        })),
        results: {
          rows: view.pcbTable || view.allocTables,
          faults: view.broken_times,
          seekLength: view.all_move_length,
          order: view.disk_line?.map((item) => Number(item.no)),
        },
      })
    );
  return {
    window,
    context,
    view,
    state,
    configure,
    run,
    advance,
    messages,
    traceMessages,
    nativeAnimations,
    elements,
    stage,
    resize: () => resize(),
    scenes,
    snapshot,
    observedElements,
    destroy: () => mixin.beforeDestroy.call(view),
  };
}

async function main() {
  // The actual transformed branches drive events at their original callback
  // times. Their up-front scheduling pass must not emit future decisions.
  const cpuInput = {
    processes: [
      { name: "A", arrival: 0, burst: 2 },
      { name: "B", arrival: 5, burst: 1 },
    ],
  };
  const cpu = harness("unit3/FCFS.vue", "FCFS");
  await cpu.configure(cpuInput);
  await cpu.run();
  assert.deepEqual(
    cpu.traceMessages().map((event) => event.phase),
    ["initialize"]
  );
  cpu.advance(1);
  assert(cpu.traceMessages().some((event) => event.phase === "ready"));
  assert(!cpu.traceMessages().some((event) => event.phase === "run"));
  cpu.view.stop();
  const pausedCount = cpu.traceMessages().length;
  const pausedTime = cpu.view.layer.timeline.currentTime;
  cpu.advance(10000);
  assert.equal(
    cpu.traceMessages().length,
    pausedCount,
    "paused timeline emitted semantic decisions"
  );
  assert.equal(cpu.view.layer.timeline.currentTime, pausedTime);
  cpu.view.stop();
  const firstRun = Math.min(
    ...cpu.state.animations
      .filter((animation) => animation.semantic.phase === "run")
      .map((animation) => animation.start)
  );
  cpu.advance(firstRun - cpu.view.layer.timeline.currentTime + 1);
  assert(cpu.traceMessages().some((event) => event.phase === "run"));
  cpu.advance(cpu.state.horizon + 100);
  assert.equal(cpu.state.running, false);
  assert(cpu.messages.some((message) => message.type === "completed"));
  assert.equal(cpu.traceMessages().at(-1).phase, "result");
  assert(cpu.traceMessages().every((event) => phases.has(event.phase)));
  assert(
    cpu.state.animations.every((record) =>
      cpu.nativeAnimations.some(
        (native) =>
          native.animation === record.animation &&
          native.keyframes &&
          native.timing
      )
    )
  );
  const baselineCPU = harness("unit3/FCFS.vue", "FCFS", false);
  await baselineCPU.configure(cpuInput);
  await baselineCPU.run();
  baselineCPU.advance(1000000);
  assert.deepEqual(
    cpu.snapshot(),
    baselineCPU.snapshot(),
    "FCFS instrumentation changed original animations or results"
  );
  baselineCPU.destroy();
  console.log(
    `FCFS: ${cpu.traceMessages().length} semantic events, ${
      cpu.state.animations.length
    } original animations, ${cpu.state.callbacks.length} original callbacks`
  );

  // Rebuild, concurrent starts and even manually invoked stale native callbacks
  // cannot deliver an old run under the new id or mutate the new canvas.
  await cpu.run();
  const oldRun = cpu.state.runId;
  let oldCallback;
  const handle = {};
  const fakeTimeline = {
    currentTime: 0,
    setTimeout(callback) {
      oldCallback = callback;
      return handle;
    },
  };
  let called = 0;
  assert.equal(
    cpu.window.__oslabLegacyTimeout(
      fakeTimeline,
      () => called++,
      1,
      "old.vue",
      1,
      { phase: "run" }
    ),
    handle
  );
  await cpu.view.reset();
  oldCallback();
  assert.equal(called, 0);
  assert(cpu.state.runId > oldRun);
  assert.equal(cpu.state.events.length, 0);
  assert(
    ["adaptive", "adaptive_5", "adaptive_PA", "adaptiveR"].every(
      (id) => cpu.elements[id].cleared > 0
    )
  );
  const starts = cpu.messages.filter(
    (message) => message.type === "run-start"
  ).length;
  await Promise.all([cpu.run(), cpu.run()]);
  assert.equal(
    cpu.messages.filter((message) => message.type === "run-start").length,
    starts + 1
  );
  cpu.advance(cpu.state.horizon + 100);

  // Cancel the original submitForm specifically while it is suspended at its
  // original await, rather than only racing the bridge's rebuild promise.
  const originalYF = cpu.view.YF;
  let continueOriginal;
  cpu.view.YF = function (...args) {
    originalYF(...args);
    return new Promise((resolve) => {
      continueOriginal = resolve;
    });
  };
  const suspended = cpu.run();
  for (let tick = 0; tick < 10 && !continueOriginal; tick++)
    await Promise.resolve();
  assert.equal(typeof continueOriginal, "function");
  await cpu.view.reset();
  continueOriginal();
  await suspended;
  assert.equal(
    cpu.state.events.length,
    0,
    "cancelled submitForm emitted new trace events"
  );
  assert.equal(
    cpu.state.animations.length,
    0,
    "cancelled submitForm constructed stale animations"
  );
  cpu.view.YF = originalYF;

  // The timeout wrapper preserves the receiver, arguments and native return
  // value, including for non-running setup callbacks.
  let callback;
  const receiver = {};
  const callbackTimeline = {
    currentTime: 0,
    setTimeout(fn) {
      callback = fn;
      return handle;
    },
  };
  assert.equal(
    cpu.window.__oslabLegacyTimeout(
      callbackTimeline,
      function (value) {
        assert.equal(this, receiver);
        return value + 1;
      },
      1,
      "test.vue",
      1
    ),
    handle
  );
  assert.equal(callback.call(receiver, 41), 42);
  cpu.resize();
  const size = cpu.messages
    .filter((message) => message.type === "resize")
    .at(-1);
  assert.equal(size.width, 1440);
  assert.equal(size.height, 1150);
  assert.equal(size.scrollHeight, 900);
  assert(
    cpu.observedElements.has(cpu.elements.outer),
    "replaced Vue root was not observed"
  );
  cpu.elements.outer.scrollHeight = 1300;
  cpu.resize();
  assert.equal(
    cpu.messages.filter((message) => message.type === "resize").at(-1).height,
    1300,
    "growing results table was cut off"
  );
  delete cpu.elements.outer;
  cpu.view.$el.scrollHeight = 1400;
  cpu.resize();
  assert.equal(
    cpu.messages.filter((message) => message.type === "resize").at(-1).height,
    1400,
    "Vue $el fallback was not measured"
  );

  // Two runs can clear and repopulate a table before ResizeObserver paints.
  // No observer callback is invoked here: each submit must explicitly report
  // its final dimensions after rebuild reported the temporary smaller root.
  const sizing = harness("unit3/SJF.vue", "SJF");
  sizing.view.$options.methods.submitForm = function () {
    sizing.elements.outer.scrollHeight = 1400;
  };
  for (let repeat = 0; repeat < 2; repeat++) {
    sizing.elements.outer.scrollHeight = 900;
    await sizing.run();
    await Promise.resolve();
    const sizes = sizing.messages.filter(
      (message) => message.type === "resize"
    );
    assert.equal(sizes.at(-2).height, 1150);
    assert.equal(
      sizes.at(-1).height,
      1400,
      "repeat submit lost final result height"
    );
  }
  sizing.destroy();

  const cases = [
    ["unit3/SJF.vue", "SJF", ["run", "complete"]],
    ["unit3/HighResponse.vue", "HighResponse", ["run", "complete"]],
    ["unit3/HighPrio.vue", "HighPrio", ["run", "complete"]],
    ["unit3/HighPrio2.vue", "HighPrio2", ["preempt", "complete"]],
    ["unit3/RR.vue", "RR", ["yield", "complete"]],
    ["unit3/FeedbackQueue.vue", "FeedbackQueue", ["demote", "complete"]],
    ["unit4/PartitionAllocation_FF.vue", "FF", ["search", "allocate", "fail"]],
    ["unit4/PartitionAllocation_BF.vue", "BF", ["search", "allocate", "fail"]],
    ["unit4/PartitionAllocation_WF.vue", "WF", ["search", "allocate", "fail"]],
    ["unit4/PartitionAllocation_NF.vue", "NF", ["search", "allocate", "fail"]],
    [
      "unit4/Replacement_FF.vue",
      "PageFF",
      ["fault", "hit", "load", "replace", "result"],
    ],
    [
      "unit4/Replacement_LRU.vue",
      "PageLRU",
      ["fault", "hit", "load", "replace", "result"],
    ],
    [
      "unit4/Replacement_OPT.vue",
      "PageOPT",
      ["fault", "hit", "load", "replace", "result"],
    ],
    ["unit5/FCFS.vue", "FCFS1", ["seek", "result"]],
    ["unit5/SSTF.vue", "SSTF", ["seek", "result"]],
    ["unit5/SCAN.vue", "SCAN", ["seek", "boundary", "result"]],
    ["unit5/CSCAN.vue", "CSCAN", ["seek", "jump", "result"]],
  ];
  for (const [filename, route, expected] of cases) {
    const h = harness(filename, route);
    const input = filename.startsWith("unit3/")
      ? {
          processes: [
            { name: "A", arrival: 0, burst: 5, priority: 1 },
            { name: "B", arrival: 1, burst: 1, priority: 9 },
          ],
          quantum: 2,
          mlfqQueues: [1, 2, 4],
        }
      : filename.includes("PartitionAllocation_")
      ? {
          memorySize: 80,
          initialPartitions: [30, 50],
          operations: [
            { type: "ALLOC", process: "A", size: 10 },
            { type: "ALLOC", process: "B", size: 100 },
          ],
        }
      : filename.includes("Replacement_")
      ? { frames: 2, sequence: [1, 2, 1, 3] }
      : {
          head: 50,
          requests: [20, 80, 10],
          direction: route === "SCAN" ? "UP" : "DOWN",
        };
    await h.configure(input);
    await h.run();
    let budget = 100;
    while (h.state.running && budget--)
      h.advance(
        Math.max(
          1000,
          h.state.horizon - h.view.layer.timeline.currentTime + 100
        )
      );
    assert(budget > 0, `${route} never completed`);
    const errors = h.messages.filter((message) => message.type === "error");
    assert.deepEqual(errors, [], `${route}: ${JSON.stringify(errors)}`);
    const observed = new Set(h.traceMessages().map((event) => event.phase));
    for (const phase of expected)
      assert(
        observed.has(phase),
        `${route} missing ${phase}; got ${[...observed]}`
      );
    assert(observed.has("initialize"), `${route} missing initialization`);
    assert(
      h
        .traceMessages()
        .every(
          (event) =>
            phases.has(event.phase) &&
            Number.isInteger(event.line) &&
            event.line > 0
        )
    );
    assert(
      h.messages.some((message) => message.type === "completed"),
      `${route} missing completion`
    );
    if (filename.startsWith("unit5/")) {
      const completed = h.messages.find(
        (message) => message.type === "completed"
      );
      assert.deepEqual(
        [...completed.summary.serviceOrder],
        [
          ...(h.view.partitionSort_line || h.view.disk_line).map((item) =>
            Number(item.no)
          ),
        ]
      );
      assert.equal(
        completed.summary.serviceOrder.length,
        input.requests.length
      );
    }
    const baseline = harness(filename, route, false);
    await baseline.configure(input);
    await baseline.run();
    baseline.advance(1000000);
    assert.deepEqual(
      h.snapshot(),
      baseline.snapshot(),
      `${route} instrumentation changed original animations or results`
    );
    baseline.destroy();
    if (route === "SCAN" || route === "CSCAN") {
      const start = h.messages.find((message) => message.type === "run-start");
      assert.equal(start.input.direction, route === "SCAN" ? "DOWN" : "UP");
    }
    console.log(
      `${route}: ${h.traceMessages().length} semantic events, ${
        h.state.animations.length
      } original animations, ${h.state.callbacks.length} original callbacks`
    );
    h.destroy();
  }
  for (const [queueSizes, arrival, expected] of [
    [[1, 2], 1, "yield"],
    [[1, 2, 4], 2, "preempt"],
  ]) {
    const h = harness("unit3/FeedbackQueue.vue", "FeedbackQueue");
    await h.configure({
      processes: [
        { name: "A", arrival: 0, burst: 10 },
        { name: "B", arrival, burst: 1 },
      ],
      mlfqQueues: queueSizes,
    });
    await h.run();
    h.advance(h.state.horizon + 100);
    assert(
      h.traceMessages().some((event) => event.phase === expected),
      `MLFQ missing executed ${expected} branch`
    );
    h.destroy();
  }
  console.log(
    "PASS: all 18 original algorithms, real timeline pause/resume, callback identity, reset/run guards and transform-independent frame size"
  );
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
