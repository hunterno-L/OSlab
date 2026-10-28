/* The frame runs the original Vue 2 components and SpriteJS 3 animations.
 * The only animation observers are the two call-site helpers emitted by Babel.
 * They never create snapshots, infer decisions from results or change keyframes.
 */
(function () {
  const CHANNEL = "oslab-legacy-simulation";
  const ROUTES = {
    FCFS: "cpu.fcfs", SJF: "cpu.sjf", HighResponse: "cpu.hrrn",
    HighPrio: "cpu.priority_nonpreemptive", HighPrio2: "cpu.priority_preemptive",
    RR: "cpu.rr", FeedbackQueue: "cpu.mlfq", FF: "allocation.first_fit",
    BF: "allocation.best_fit", WF: "allocation.worst_fit", NF: "allocation.next_fit",
    PageFF: "page.fifo", PageLRU: "page.lru", PageOPT: "page.opt",
    FCFS1: "disk.fcfs", SSTF: "disk.sstf", SCAN: "disk.scan", CSCAN: "disk.cscan",
  };
  const state = {
    key: "", view: null, input: null, runId: 0, running: false,
    dirty: false, horizon: 0, frameId: 0, startTime: 0,
    events: [], animations: [], callbacks: [], tracePending: false,
    configured: false, traceSequence: 0, result: null,
  };
  window.__oslabLegacyState = state;
  const post = (type, payload = {}) => window.parent.postMessage({
    channel: CHANNEL, type, key: state.key, runId: state.runId, ...payload,
  }, window.location.origin);
  const currentTime = () => Number(state.view?.layer?.timeline?.currentTime) || 0;
  const clone = (value) => JSON.parse(JSON.stringify(value));

  function emitTrace(file, line, kind = "execute", extra = {}) {
    if (!state.running || !Number.isInteger(line) || line < 1) return;
    const trace = { file, line, kind, time: currentTime(), sequence: ++state.traceSequence, ...extra };
    state.events.push(trace);
    if (state.events.length > 2000) state.events.splice(0, state.events.length - 2000);
    state.pendingTrace = trace;
    if (!state.tracePending) {
      state.tracePending = true;
      Promise.resolve().then(() => {
        state.tracePending = false;
        if (state.pendingTrace && state.running) post("trace", state.pendingTrace);
      });
    }
  }
  window.__oslabLegacyTrace = emitTrace;

  window.__oslabLegacyAnimate = function (target, keyframes, timing, file, line) {
    const animation = target.animate(keyframes, timing);
    if (!state.running) return animation;
    const timeline = target.layer?.timeline || state.view?.layer?.timeline;
    const runId = state.runId;
    const delay = Number(timing?.delay) || 0;
    const duration = Number(timing?.duration) || 0;
    const iterations = Number(timing?.iterations) || 1;
    const start = Number(timeline?.currentTime) || 0;
    const record = { file, line, start: start + delay, end: start + delay + duration * iterations,
      target, animation, began: false, ended: false };
    state.animations.push(record);
    state.horizon = Math.max(state.horizon, record.end);
    if (timeline) {
      timeline.setTimeout(() => {
        if (runId !== state.runId || !state.running) return;
        record.began = true;
        emitTrace(file, line, "animation", { duration, animationIndex: state.animations.indexOf(record) });
      }, delay);
    }
    return animation;
  };

  window.__oslabLegacyTimeout = function (timeline, callback, delay, file, line) {
    if (!state.running) return timeline.setTimeout(callback, delay);
    const runId = state.runId;
    const due = (Number(timeline.currentTime) || 0) + (Number(delay) || 0);
    const record = { file, line, due, executed: false };
    state.callbacks.push(record);
    state.horizon = Math.max(state.horizon, due);
    return timeline.setTimeout(function (...args) {
      if (runId !== state.runId) return;
      record.executed = true;
      emitTrace(file, line, "callback");
      try { return callback.apply(this, args); }
      catch (error) { fail(error); throw error; }
    }, delay);
  };

  function readInput(view = state.view) {
    const key = state.key;
    if (key.startsWith("cpu.")) {
      const slices = (view.lineForm?.lineObjs || []).map((item) => Number(item.size));
      return {
        processes: view.form.pcbItem0.map((item) => ({ name: String(item.pcb_name || ""),
          arrival: Number(item.pcb_arrive), burst: Number(item.pcb_work), priority: Number(item.prior ?? 1) })),
        quantum: Number(view.timepiece ?? slices[0] ?? 2),
        ...(slices.length ? { mlfqQueues: slices } : {}),
      };
    }
    if (key.startsWith("allocation.")) {
      const partitions = view.partition.partition_input.map((item) => Number(item.size));
      return { memorySize: partitions.reduce((sum, size) => sum + size, 0), initialPartitions: partitions,
        operations: view.process.process_input.map((item) => ({ type: "ALLOC",
          process: String(item.pcb_name || ""), size: Number(item.pcb_need_size) })) };
    }
    if (key.startsWith("page.")) return { frames: Number(view.phy_num), sequence: view.page.page_input.map((item) => Number(item.no)) };
    return { head: Number(view.begin_no), requests: view.partition.partition_input.map((item) => Number(item.no)),
      direction: key === "disk.scan" || key === "disk.cscan" ? "DOWN" : "UP" };
  }

  function writeInput(view, input) {
    if (state.key.startsWith("cpu.")) {
      const prototype = view.$options.data.call(view).form.pcbItem0[0];
      view.form.pcbItem0 = input.processes.map((process) => ({ ...clone(prototype),
        pcb_name: process.name, pcb_arrive: process.arrival, pcb_work: process.burst,
        ...(Object.prototype.hasOwnProperty.call(prototype, "prior") ? { prior: process.priority ?? 1 } : {}) }));
      if (input.quantum !== undefined && "timepiece" in view) view.timepiece = input.quantum;
      if (view.lineForm) {
        const slices = input.mlfqQueues || [input.quantum || 1, (input.quantum || 1) * 2, (input.quantum || 1) * 4];
        view.queueNum = slices.length;
        view.lineForm.lineObjs = slices.map((size) => ({ size }));
        if (view.layer && typeof view.changeCanvas === "function") view.changeCanvas();
      }
    } else if (state.key.startsWith("allocation.")) {
      if (input.operations.some((operation) => operation.type !== "ALLOC")) {
        throw new Error("原演示只包含分区分配；该深链中的 FREE 回收操作不属于原算法界面，请删除 FREE 后提交。");
      }
      view.partition.partition_input = (input.initialPartitions || [input.memorySize]).map((size) => ({ size, restSpace: size, pid: [] }));
      view.process.process_input = input.operations.map((operation) => ({ pcb_name: operation.process, pcb_need_size: operation.size }));
    } else if (state.key.startsWith("page.")) {
      view.phy_num = input.frames;
      view.page.page_input = input.sequence.map((no) => ({ no }));
    } else {
      view.begin_no = input.head;
      view.partition.partition_input = input.requests.map((no) => ({ no }));
      const originalDirection = "DOWN";
      if ((state.key === "disk.scan" || state.key === "disk.cscan") && input.direction !== originalDirection) {
        post("notice", { message: `原 ${state.key === "disk.scan" ? "SCAN" : "C-SCAN"} 演示固定${originalDirection === "DOWN" ? "向小磁道" : "向大磁道"}扫描，已保留磁头位置和全部请求。` });
      }
    }
  }

  function disposeStage(view) {
    const layer = view.layer;
    if (layer?.timeline) { layer.timeline.playbackRate = 0; layer.timeline.clear(); }
    const visit = (node) => {
      for (const animation of node?.animations || []) animation.cancel();
      for (const child of node?.childNodes || []) visit(child);
    };
    if (layer) { visit(layer); layer.removeAllChildren(); }
    const container = document.getElementById("adaptive") || document.getElementById("adaptive_5");
    if (container) container.replaceChildren();
  }

  function rebuild(view, input) {
    state.running = false;
    state.runId += 1;
    if (state.frameId) cancelAnimationFrame(state.frameId);
    disposeStage(view);
    const fresh = view.$options.data.call(view);
    for (const [key, value] of Object.entries(fresh)) view.$set(view.$data, key, value);
    state.animations = [];
    state.callbacks = [];
    state.events = [];
    state.traceSequence = 0;
    state.dirty = false;
    writeInput(view, input);
    view.$options.methods.init.call(view);
    if (state.key.startsWith("allocation.")) view.$options.methods.onSubmit1.call(view);
    return view.$nextTick();
  }

  function resultSummary(view) {
    if (state.key.startsWith("cpu.")) return { rows: clone(view.pcbTable || []) };
    if (state.key.startsWith("allocation.")) return { rows: clone(view.allocTables || []) };
    if (state.key.startsWith("page.")) return { faults: view.broken_times, faultRate: view.broken_times / view.page.page_input.length };
    return { seekLength: view.all_move_length,
      serviceOrder: (view.disk_line || []).map((item) => Number(item.no)) };
  }

  function watchCompletion() {
    if (!state.running) return;
    const time = currentTime();
    const pending = state.callbacks.some((record) => !record.executed);
    // Endpoints are observations of the actual animate call timing; completion
    // also waits for original timeline callbacks, including nested callbacks.
    if (!pending && time >= state.horizon + 40) {
      state.result = resultSummary(state.view);
      state.running = false;
      post("completed", { input: state.input, summary: {
        ...state.result, source: "original-spritejs", animationCount: state.animations.length,
        callbackCount: state.callbacks.length, traceCount: state.traceSequence,
        timelineDuration: time - state.startTime,
      }, dirty: state.dirty });
      return;
    }
    state.frameId = requestAnimationFrame(watchCompletion);
  }

  function fail(error) {
    state.running = false;
    state.dirty = true;
    post("error", { message: error?.message || String(error) });
  }

  async function run(view, method, args) {
    try {
      const input = readInput(view);
      const speed = Math.max(0.1, Number(view.playbackRate) || 1);
      await rebuild(view, input);
      view.playbackRate = speed;
      view.layer.timeline.playbackRate = speed;
      state.input = clone(input);
      state.running = true;
      state.dirty = false;
      state.horizon = currentTime();
      state.startTime = currentTime();
      post("run-start", { input: state.input });
      await view.$options.methods[method].apply(view, args);
      state.frameId = requestAnimationFrame(watchCompletion);
    } catch (error) { fail(error); }
  }

  function wrapView(view) {
    const method = state.key.startsWith("cpu.") ? "submitForm" : state.key.startsWith("allocation.") ? "onSubmit2" : "onSubmit1";
    view[method] = (...args) => run(view, method, args);
    for (const reset of ["reset", "reset2"]) {
      if (view.$options.methods[reset]) view[reset] = async () => {
        try { await rebuild(view, readInput(view)); post("reset"); }
        catch (error) { fail(error); }
      };
    }
    const rate = (value) => {
      const speed = Math.max(0.1, Math.round(value * 10) / 10);
      view.playbackRate = speed;
      if (view.layer?.timeline) view.layer.timeline.playbackRate = view.pause ? 0 : speed;
      post("play-state", { paused: Boolean(view.pause), speed });
    };
    view.speedDown = () => rate(Number(view.playbackRate) - 0.1);
    view.speedUp = () => rate(Number(view.playbackRate) + 0.1);
    view.halfSpeed = () => rate(0.5);
    view.oneSpeed = () => rate(1);
    view.doubleSpeed = () => rate(2);
    view.stop = () => { view.pause = !view.pause; rate(Number(view.playbackRate)); };
    view.$nextTick(() => {
      const labels = { speedDown: "降低播放速度", speedUp: "提高播放速度" };
      for (const button of view.$el.querySelectorAll("button")) {
        if (button.textContent.includes("减速")) button.setAttribute("aria-label", labels.speedDown);
        if (button.textContent.includes("加速")) button.setAttribute("aria-label", labels.speedUp);
      }
    });
  }

  window.__oslabLegacyAttachVue = function (Vue) {
    const oldErrorHandler = Vue.config.errorHandler;
    Vue.config.errorHandler = (error, vm, info) => {
      fail(error);
      if (oldErrorHandler) oldErrorHandler(error, vm, info);
      else console.error(error);
    };
    Vue.mixin({
      mounted() {
        if (!Object.prototype.hasOwnProperty.call(this.$data, "layer") || !this.$route) return;
        const key = ROUTES[this.$route.name];
        if (!key) return;
        state.key = key;
        state.view = this;
        window.__oslabLegacyView = this;
        wrapView(this);
        this.$nextTick(() => post("mounted"));
      },
      beforeDestroy() {
        if (this !== state.view) return;
        state.running = false;
        state.runId += 1;
        if (state.frameId) cancelAnimationFrame(state.frameId);
        disposeStage(this);
      },
    });
  };

  window.addEventListener("message", async (event) => {
    if (event.source !== window.parent || event.origin !== window.location.origin) return;
    const message = event.data;
    if (!message || message.channel !== CHANNEL || message.key !== state.key) return;
    const view = state.view;
    if (!view) return;
    if (message.type === "configure") {
      try {
        if (message.input) await rebuild(view, message.input);
        state.input = readInput(view);
        state.configured = true;
        post("ready", { input: state.input });
      } catch (error) { fail(error); }
    }
  });
  window.addEventListener("error", (event) => fail(event.error || event.message));
  window.addEventListener("unhandledrejection", (event) => fail(event.reason));
  let lastHeight = 0;
  const reportSize = () => {
    const height = Math.ceil(document.getElementById("app")?.getBoundingClientRect().height || 850);
    if (height !== lastHeight) { lastHeight = height; post("resize", { height }); }
  };
  new ResizeObserver(reportSize).observe(document.documentElement);
  window.addEventListener("load", reportSize);
})();
