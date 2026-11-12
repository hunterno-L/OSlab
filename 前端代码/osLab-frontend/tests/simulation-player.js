const assert = require("assert");
const fs = require("fs");
const path = require("path");
const ts = require("typescript");
const vm = require("vm");

function load(relativePath) {
  const context = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, relativePath), "utf8");
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
    },
  }).outputText;
  vm.runInNewContext(output, context);
  return context.exports;
}

const catalog = load("../src/simulation/catalog.ts");
const codeMaps = load("../src/simulation/codeMaps.ts");
const playback = load("../src/simulation/playback.ts");
const cpu = load("../src/simulation/cpu.ts");
const allocation = load("../src/simulation/allocation.ts");
const page = load("../src/simulation/page.ts");
const disk = load("../src/simulation/disk.ts");

let passed = 0;
const failures = [];

function test(name, fn) {
  try {
    fn();
    passed += 1;
    console.log(`PASS ${name}`);
  } catch (error) {
    failures.push({ name, error });
    console.error(`FAIL ${name}`);
    console.error(`  ${error && error.message ? error.message : error}`);
  }
}

const clone = (value) => JSON.parse(JSON.stringify(value));

function truthRun(definition) {
  const input = clone(definition.defaultInput);
  let result;
  if (definition.category === "cpu") {
    result = cpu.CPU_ALGORITHMS[definition.key].run(input);
  } else if (definition.category === "allocation") {
    result = allocation.runAllocation(definition.key, input);
  } else if (definition.category === "page") {
    result = page.runPage(definition.key, input);
  } else {
    result = disk.runDisk(definition.key, input);
  }
  return { input, result };
}

const sampleResult = (count = 3) => ({
  steps: Array.from({ length: count }, (_, index) => ({
    index: index + 1,
    title: `步骤 ${index + 1}`,
    detail: `说明 ${index + 1}`,
    state: { time: index + 1 },
  })),
  metrics: [{ label: "指标", value: "1" }],
  initialState: { time: 0 },
});

test("real code maps are verified against implementation sources", () => {
  const fileCache = new Map();
  const normalized = (text) => text.replace(/\s+/g, " ").trim();
  for (const definition of catalog.ALGORITHMS) {
    const map = codeMaps.getCodeMap(definition.key);
    assert.equal(
      map.language,
      "typescript",
      `${definition.key} 必须是真实代码`
    );
    assert(map.sourceFile, `${definition.key} 缺少源码文件`);
    assert(map.symbol, `${definition.key} 缺少实现符号`);
    if (!fileCache.has(map.sourceFile)) {
      fileCache.set(
        map.sourceFile,
        normalized(
          fs.readFileSync(path.join(__dirname, "..", map.sourceFile), "utf8")
        )
      );
    }
    const source = fileCache.get(map.sourceFile);
    for (const line of map.lines) {
      if (!line.trim()) continue;
      assert(
        source.includes(normalized(line)),
        `${definition.key} 面板行不在 ${map.sourceFile} 中：${line}`
      );
    }
    assert(
      map.lines.some((line) => line.includes("function ")),
      `${definition.key} 必须展示真实函数实现`
    );
  }
});

// ---------- 1. 18 项算法都有代码映射 / 事件映射 ----------
test("all 18 algorithms register a code map with valid lines", () => {
  assert.equal(catalog.ALGORITHMS.length, 18);
  const catalogKeys = catalog.ALGORITHMS.map((item) => item.key).sort();
  assert.deepEqual(codeMaps.CODE_MAP_KEYS.slice().sort(), catalogKeys);
  for (const definition of catalog.ALGORITHMS) {
    const map = codeMaps.getCodeMap(definition.key);
    assert(map, `${definition.key} 缺少 code map`);
    assert(map.lines.length >= 5, `${definition.key} 实现代码行过少`);
    assert(
      Array.isArray(definition.analysis) && definition.analysis.length >= 2,
      `${definition.key} 缺少算法解析（思想/特点）`
    );
    map.lines.forEach((line, index) => {
      assert.equal(
        typeof line,
        "string",
        `${definition.key} 第 ${index + 1} 行不是字符串`
      );
    });
    // 源码中的空行保留用于可读性，但至少有非空实现行。
    assert(
      map.lines.some((item) => item.trim().length > 0),
      `${definition.key} 没有实现代码行`
    );
    assert(
      Object.keys(map.eventMap).length > 0,
      `${definition.key} 没有事件映射`
    );
    for (const [kind, lines] of Object.entries(map.eventMap)) {
      assert(
        Array.isArray(lines) && lines.length > 0,
        `${definition.key}:${kind}`
      );
      assert.equal(
        new Set(lines).size,
        lines.length,
        `${definition.key}:${kind} 行号重复`
      );
      for (const line of lines) {
        assert(Number.isInteger(line), `${definition.key}:${kind} 行号非整数`);
        assert(
          line >= 1 && line <= map.lines.length,
          `${definition.key}:${kind} 行号越界`
        );
      }
    }
  }
});

test("every step produced by local truth functions has a mapped event kind", () => {
  const seenKinds = new Set();
  for (const definition of catalog.ALGORITHMS) {
    const { input, result } = truthRun(definition);
    const annotated = codeMaps.annotateResult(definition, input, result);
    assert.equal(annotated.steps.length, result.steps.length);
    const map = codeMaps.getCodeMap(definition.key);
    for (const step of annotated.steps) {
      assert(step.event, `${definition.key} 第 ${step.index} 步缺少 event`);
      assert(
        step.event.kind,
        `${definition.key} 第 ${step.index} 步 event.kind 为空`
      );
      seenKinds.add(step.event.kind);
      const mapped = map.eventMap[step.event.kind] || [];
      const allowlisted = codeMaps.NO_MAPPING_EVENT_KINDS.includes(
        step.event.kind
      );
      assert(
        mapped.length > 0 || allowlisted,
        `${definition.key} 的事件 ${step.event.kind} 既无映射也未登记 allowlist`
      );
      assert.deepEqual(
        step.activeCodeLines,
        mapped.slice(),
        `${definition.key} 第 ${step.index} 步活动代码行与映射不一致`
      );
      assert.deepEqual(
        step.codeLines,
        mapped.length ? mapped.slice() : undefined,
        `${definition.key} 第 ${step.index} 步 codeLines 不一致`
      );
    }
  }
  // 默认进程都在 0 时刻前到达；补一个延迟到达用例覆盖 CPU 空闲事件
  const idleDefinition = catalog.ALGORITHM_BY_KEY.get("cpu.fcfs");
  const idleInput = {
    processes: [{ name: "P1", arrival: 5, burst: 2, priority: 1 }],
    quantum: 2,
  };
  const idleAnnotated = codeMaps.annotateResult(
    idleDefinition,
    idleInput,
    cpu.CPU_ALGORITHMS["cpu.fcfs"].run(idleInput)
  );
  for (const step of idleAnnotated.steps) seenKinds.add(step.event.kind);
  assert(
    idleAnnotated.steps.some((step) => step.event.kind === "cpu.idle"),
    "延迟到达用例应产生 CPU 空闲事件"
  );

  // 默认分配用例没有回收失败；补一个 FREE 不存在进程的用例
  const freeFailDefinition = catalog.ALGORITHM_BY_KEY.get(
    "allocation.first_fit"
  );
  const freeFailInput = {
    memorySize: 100,
    operations: [{ type: "FREE", process: "P9" }],
  };
  const freeFailAnnotated = codeMaps.annotateResult(
    freeFailDefinition,
    freeFailInput,
    allocation.runAllocation("allocation.first_fit", freeFailInput)
  );
  for (const step of freeFailAnnotated.steps) seenKinds.add(step.event.kind);
  assert(
    freeFailAnnotated.steps.some(
      (step) => step.event.kind === "allocation.free_fail"
    ),
    "回收不存在进程应产生 allocation.free_fail"
  );

  for (const kind of [
    "cpu.run",
    "cpu.idle",
    "page.hit",
    "page.load",
    "page.replace",
    "allocation.alloc",
    "allocation.alloc_fail",
    "allocation.free",
    "allocation.free_fail",
    "disk.seek",
    "disk.boundary",
    "disk.jump",
  ]) {
    assert(seenKinds.has(kind), `默认输入未覆盖事件 ${kind}`);
  }
});

// ---------- 2. 播放状态机 ----------
test("playback transitions idle -> play -> pause -> step -> reset", () => {
  let state = playback.createPlayerState();
  assert.equal(state.playState, "idle");
  assert.equal(state.activeStep, 0);
  assert.equal(state.speed, 1);
  assert.equal(state.dirty, false);
  assert.equal(playback.canComplete(state), false);

  state = playback.load(state, { processes: [] }, sampleResult(3));
  assert.equal(state.playState, "idle");
  assert.equal(state.activeStep, 0);
  assert.equal(state.runConfig.processes.length, 0);
  assert.equal(playback.canComplete(state), false);

  state = playback.play(state);
  assert.equal(state.playState, "playing");
  state = playback.pause(state);
  assert.equal(state.playState, "paused");
  state = playback.stepForward(state);
  assert.equal(state.activeStep, 1);
  assert.equal(state.playState, "paused");
  state = playback.stepForward(state);
  assert.equal(state.activeStep, 2);
  state = playback.reset(state);
  assert.equal(state.activeStep, 0);
  assert.equal(state.playState, "idle");
});

test("forward/back bounds and finish state", () => {
  let state = playback.load(
    playback.createPlayerState(),
    { frames: 3, sequence: [1] },
    sampleResult(3)
  );
  state = playback.stepBack(state);
  assert.equal(state.activeStep, 0);
  assert.equal(state.playState, "idle");

  state = playback.stepForward(state);
  state = playback.stepForward(state);
  state = playback.stepForward(state);
  assert.equal(state.activeStep, 3);
  assert.equal(state.playState, "finished");
  assert.equal(playback.canComplete(state), true);
  assert.equal(playback.isAtEnd(state), true);

  state = playback.stepForward(state);
  assert.equal(state.activeStep, 3);
  assert.equal(state.playState, "finished");

  state = playback.stepBack(state);
  assert.equal(state.activeStep, 2);
  assert.equal(state.playState, "paused");
  assert.equal(playback.canComplete(state), false);

  state = playback.seek(state, -5);
  assert.equal(state.activeStep, 0);
  assert.equal(state.playState, "idle");
  state = playback.seek(state, 99);
  assert.equal(state.activeStep, 3);
  assert.equal(state.playState, "finished");
  state = playback.reset(state);
  assert.equal(state.activeStep, 0);
  assert.equal(state.playState, "idle");

  state = playback.play(state);
  assert.equal(state.activeStep, 0);
  assert.equal(state.playState, "playing");
});

test("load deep-clones runConfig and steps", () => {
  const config = {
    memorySize: 640,
    operations: [{ type: "ALLOC", process: "P1", size: 100 }],
  };
  const result = sampleResult(2);
  let state = playback.load(playback.createPlayerState(), config, result);
  config.memorySize = 128;
  config.operations[0].process = "MUTATED";
  result.steps[0].title = "MUTATED";
  assert.equal(state.runConfig.memorySize, 640);
  assert.equal(state.runConfig.operations[0].process, "P1");
  assert.equal(state.steps[0].title, "步骤 1");
});

test("speed changes preserve the state and clamp invalid values", () => {
  let state = playback.load(
    playback.createPlayerState(),
    { head: 53, requests: [98], direction: "UP" },
    sampleResult(2)
  );
  state = playback.play(state);
  state = playback.setSpeed(state, 2);
  assert.equal(state.speed, 2);
  assert.equal(state.playState, "playing");
  state = playback.setSpeed(state, 0.5);
  assert.equal(state.speed, 0.5);
  assert.equal(playback.stepIntervalMs(0.5), 1800);
  state = playback.setSpeed(state, 3);
  assert.equal(state.speed, 3);
  state = playback.setSpeed(state, 0.05);
  assert.equal(state.speed, 0.1);
  state = playback.setSpeed(state, 8);
  assert.equal(state.speed, 4);
  state = playback.setSpeed(state, Number.NaN);
  assert.equal(state.speed, 1);
});

test("dirty input invalidates playback and completion until reload", () => {
  let state = playback.load(
    playback.createPlayerState(),
    { frames: 3, sequence: [7, 0, 1] },
    sampleResult(3)
  );
  state = playback.play(state);
  state = playback.markDirty(state);
  assert.equal(state.dirty, true);
  assert.equal(state.playState, "paused");
  assert.equal(playback.canComplete(state), false);

  const before = state.activeStep;
  state = playback.stepForward(state);
  assert.equal(state.activeStep, before);
  assert.equal(state.playState, "error");
  assert(state.error.includes("重新运行"));

  state = playback.play(state);
  assert.equal(state.playState, "error");
  assert.equal(playback.canComplete(state), false);

  state = playback.finish(state);
  assert.equal(state.playState, "finished");
  assert.equal(state.dirty, true);
  assert.equal(playback.canComplete(state), false);

  state = playback.load(
    state,
    { frames: 3, sequence: [7, 0, 1] },
    sampleResult(3)
  );
  assert.equal(state.dirty, false);
  state = playback.finish(state);
  assert.equal(playback.canComplete(state), true);
});

test("empty results enter error state and cannot complete", () => {
  let state = playback.load(
    playback.createPlayerState(),
    { frames: 3, sequence: [] },
    { steps: [], metrics: [], initialState: {} }
  );
  assert.equal(state.playState, "error");
  assert(state.error.length > 0);
  state = playback.play(state);
  assert.equal(state.playState, "error");
  state = playback.stepForward(state);
  assert.equal(state.activeStep, 0);
  assert.equal(playback.canComplete(state), false);
  const fresh = playback.play(playback.createPlayerState());
  assert.equal(fresh.playState, "error");
});

test("actions are pure and do not mutate the previous state", () => {
  const state = playback.load(
    playback.createPlayerState(),
    { frames: 3, sequence: [1] },
    sampleResult(2)
  );
  const played = playback.play(state);
  assert.equal(state.playState, "idle");
  assert.equal(played.playState, "playing");
  const stepped = playback.stepForward(played);
  assert.equal(played.activeStep, 0);
  assert.equal(stepped.activeStep, 1);
});

// ---------- 3. 四类深链输入 ----------
test("CPU deep-link parser accepts processes and timeSlice", () => {
  const parsed = catalog.parseDemoQuery("cpu", {
    processes: JSON.stringify([
      { name: "P1", arrivalTime: 0, burstTime: 7, priority: 2 },
      { name: "P2", arrival: 2, burst: 4 },
    ]),
    timeSlice: "3",
  });
  assert.equal(parsed.error, undefined);
  assert.equal(parsed.hasInput, true);
  assert.equal(parsed.input.processes.length, 2);
  assert.deepEqual(parsed.input.processes[0], {
    name: "P1",
    arrival: 0,
    burst: 7,
    priority: 2,
  });
  assert.equal(parsed.input.processes[1].priority, 1);
  assert.equal(parsed.input.quantum, 3);
  const alias = catalog.parseDemoQuery("cpu", {
    processes: JSON.stringify([{ name: "P1", arrival: 0, burst: 1 }]),
    quantum: "2",
  });
  assert.equal(alias.input.quantum, 2);
  const mlfq = catalog.parseDemoQuery("cpu", {
    processes: JSON.stringify([{ name: "P1", arrival: 0, burst: 1 }]),
    queueSlices: "2,4,8",
  });
  assert.equal(mlfq.error, undefined);
  assert.deepEqual(mlfq.input.mlfqQueues, [2, 4, 8]);
  assert.equal(mlfq.input.quantum, 2);
});

test("page deep-link parser accepts frames and accessSequence", () => {
  const parsed = catalog.parseDemoQuery("page", {
    frames: "3",
    accessSequence: JSON.stringify([7, 0, 1, 2]),
  });
  assert.equal(parsed.error, undefined);
  assert.deepEqual(parsed.input, { frames: 3, sequence: [7, 0, 1, 2] });
  const legacy = catalog.parseDemoQuery("page", {
    accessSequence: "7,0,1",
  });
  assert.equal(legacy.error, undefined);
  assert.deepEqual(legacy.input, { frames: 3, sequence: [7, 0, 1] });
  const large = catalog.parseDemoQuery("page", {
    frames: "60",
    accessSequence: Array.from({ length: 200 }, (_, index) => index % 70).join(
      ","
    ),
  });
  assert.equal(large.error, undefined);
  assert.equal(large.input.frames, 60);
  assert.equal(large.input.sequence.length, 200);
});

test("allocation deep-link parser accepts memorySize and operations", () => {
  const parsed = catalog.parseDemoQuery("allocation", {
    memorySize: "640",
    operations: JSON.stringify([
      { type: "ALLOC", process: "P1", size: 100 },
      { type: "FREE", process: "P1" },
    ]),
  });
  assert.equal(parsed.error, undefined);
  assert.deepEqual(parsed.input, {
    memorySize: 640,
    operations: [
      { type: "ALLOC", process: "P1", size: 100 },
      { type: "FREE", process: "P1" },
    ],
  });
  const text = catalog.parseDemoQuery("allocation", {
    memorySize: "100",
    operations: "P1 20\nFREE P1",
  });
  assert.equal(text.error, undefined);
  assert.equal(text.input.operations.length, 2);
  const multipart = catalog.parseDemoQuery("allocation", {
    memorySize: "300",
    initialPartitions: "100,200",
    operations: "A 80\nB 150",
  });
  assert.equal(multipart.error, undefined);
  assert.deepEqual(multipart.input.initialPartitions, [100, 200]);
});

test("disk deep-link parser accepts head, requests and direction", () => {
  const parsed = catalog.parseDemoQuery("disk", {
    head: "53",
    requests: JSON.stringify([98, 183, 37]),
    direction: "UP",
  });
  assert.equal(parsed.error, undefined);
  assert.deepEqual(parsed.input, {
    head: 53,
    requests: [98, 183, 37],
    direction: "UP",
  });
  const text = catalog.parseDemoQuery("disk", {
    head: "100",
    requests: "10,20,30",
    direction: "down",
  });
  assert.equal(text.error, undefined);
  assert.equal(text.input.direction, "DOWN");
  const endpoint = catalog.parseDemoQuery("disk", {
    head: "200",
    requests: "1,200",
    direction: "UP",
  });
  assert.equal(endpoint.error, undefined);
  assert.equal(endpoint.input.head, 200);
});

test("deep-link parsers reject missing or invalid values", () => {
  const cases = [
    ["cpu", { timeSlice: "2" }],
    ["cpu", { processes: "not json" }],
    ["cpu", { processes: JSON.stringify({ name: "P1" }) }],
    [
      "cpu",
      { processes: JSON.stringify([{ name: "P1", arrival: 0, burst: 0 }]) },
    ],
    [
      "cpu",
      {
        processes: JSON.stringify([
          { name: "P1", arrival: 0, burst: 1 },
          { name: "P1", arrival: 1, burst: 1 },
        ]),
      },
    ],
    [
      "cpu",
      {
        processes: JSON.stringify([{ name: "P1", arrival: 0, burst: 1 }]),
        timeSlice: "0",
      },
    ],
    ["page", { frames: "3" }],
    ["page", { frames: "0", accessSequence: "[1]" }],
    ["page", { frames: "3", accessSequence: "[1,-1]" }],
    ["page", { frames: "3", accessSequence: "[]" }],
    ["allocation", { memorySize: "640" }],
    ["allocation", { memorySize: "640", operations: "[]" }],
    ["allocation", { operations: "[]" }],
    ["allocation", { memorySize: "10", operations: "P1 5" }],
    [
      "allocation",
      {
        memorySize: "640",
        operations: JSON.stringify([{ type: "ALLOC", process: "P1", size: 0 }]),
      },
    ],
    [
      "allocation",
      {
        memorySize: "640",
        operations: JSON.stringify([{ type: "COPY", process: "P1" }]),
      },
    ],
    ["disk", { requests: "[1]" }],
    ["disk", { head: "53", requests: "[1]" }],
    ["disk", { head: "201", requests: "[1]", direction: "UP" }],
    ["disk", { head: "0", requests: "[1]", direction: "UP" }],
    ["disk", { head: "53", requests: "[201]", direction: "UP" }],
    ["disk", { head: "53", requests: "[]", direction: "UP" }],
    ["disk", { head: "53", requests: "[1]", direction: "LEFT" }],
  ];
  for (const [category, query] of cases) {
    const parsed = catalog.parseDemoQuery(category, query);
    assert.equal(parsed.hasInput, true, `${category} 应识别到输入`);
    assert(parsed.error, `${category} 应拒绝 ${JSON.stringify(query)}`);
    assert.equal(parsed.input, undefined, `${category} 错误时不得返回输入`);
  }
});

test("deep-link parsers report no input when category parameters are absent", () => {
  for (const category of ["cpu", "page", "allocation", "disk"]) {
    const parsed = catalog.parseDemoQuery(category, {
      algorithmKey: "cpu.fcfs",
      nodeCode: "os.cpu.fcfs",
    });
    assert.equal(parsed.hasInput, false);
    assert.equal(parsed.error, undefined);
    assert.equal(parsed.input, undefined);
  }
});

// ---------- 4. 无映射时不伪造代码行 ----------
test("no active code lines without an event or a mapping", () => {
  assert.deepEqual(codeMaps.resolveActiveCodeLines("cpu.fcfs"), []);
  assert.deepEqual(
    codeMaps.resolveActiveCodeLines("cpu.fcfs", { kind: "unknown.kind" }),
    []
  );
  assert.deepEqual(
    codeMaps.resolveActiveCodeLines("missing.algorithm", { kind: "cpu.run" }),
    []
  );
  const idleLines = codeMaps.getCodeMap("cpu.fcfs").eventMap["cpu.idle"];
  assert.ok(idleLines.length > 0);
  assert.deepEqual(
    codeMaps.resolveActiveCodeLines("cpu.fcfs", { kind: "cpu.idle" }),
    idleLines
  );

  const definition = catalog.ALGORITHM_BY_KEY.get("cpu.fcfs");
  const annotated = codeMaps.annotateResult(
    definition,
    definition.defaultInput,
    {
      steps: [
        {
          index: 1,
          title: "未知事件",
          detail: "没有映射",
          state: { process: "P1", start: 0, end: 1 },
          event: { kind: "unknown.kind" },
        },
      ],
      metrics: [],
      initialState: {},
    }
  );
  assert.deepEqual(annotated.steps[0].activeCodeLines, []);
  assert.equal(annotated.steps[0].codeLines, undefined);
});

// ---------- 5. 重置归零与动画状态契约 ----------
test("reset on every algorithm returns to step 0 with initial visual state", () => {
  for (const definition of catalog.ALGORITHMS) {
    const { input, result } = truthRun(definition);
    const annotated = codeMaps.annotateResult(definition, input, result);
    let state = playback.load(playback.createPlayerState(), input, annotated);
    assert.equal(state.activeStep, 0, `${definition.key} 载入后应为第 0 步`);
    assert.doesNotThrow(
      () => JSON.stringify(result.initialState),
      `${definition.key} 初始状态必须可被深拷贝消费`
    );
    if (definition.category === "cpu") {
      assert.equal(result.initialState.time, 0);
      assert.equal(result.initialState.process, null);
      assert(Array.isArray(result.initialState.ready));
    } else if (definition.category === "page") {
      assert.deepEqual(result.initialState.frames, []);
      assert.equal(result.initialState.page, null);
      assert.equal(result.initialState.fault, false);
      assert.equal(result.initialState.evicted, null);
    } else if (definition.category === "allocation") {
      const expectedSizes = input.initialPartitions?.length
        ? input.initialPartitions
        : [input.memorySize];
      assert.equal(result.initialState.blocks.length, expectedSizes.length);
      assert.equal(result.initialState.blocks[0].start, 0);
      assert.deepEqual(
        result.initialState.blocks.map((block) => block.size),
        expectedSizes,
        `${definition.key} 初始分区应与输入一致`
      );
      assert.equal(
        result.initialState.freeTotal,
        expectedSizes.reduce((sum, size) => sum + size, 0)
      );
      assert.equal(result.initialState.pointer, 0);
      assert.deepEqual(result.initialState.candidates, []);
    } else {
      assert.equal(result.initialState.head, input.head);
      assert.deepEqual(result.initialState.order, []);
      assert.deepEqual(result.initialState.remaining, input.requests);
      assert.equal(result.initialState.totalSeek, 0);
    }

    state = playback.finish(state);
    assert.equal(state.activeStep, state.steps.length);
    assert.equal(playback.canComplete(state), true);
    state = playback.reset(state);
    assert.equal(state.activeStep, 0, `${definition.key} 重置后应回到第 0 步`);
    assert.equal(state.playState, "idle");
    assert.equal(state.dirty, false);
    assert.equal(playback.canComplete(state), false);
    assert.equal(state.steps.length, annotated.steps.length);
    assert(state.runConfig, `${definition.key} 重置不得丢弃本次运行配置`);

    state = playback.play(state);
    assert.equal(state.activeStep, 0);
    assert.equal(state.playState, "playing");
    state = playback.stepForward(state);
    assert.equal(
      state.activeStep,
      1,
      `${definition.key} 重置后单步应前进 1 步`
    );
  }
});

test("every step event matches the truth state consumed by animations", () => {
  for (const definition of catalog.ALGORITHMS) {
    const { input, result } = truthRun(definition);
    const annotated = codeMaps.annotateResult(definition, input, result);
    for (const step of annotated.steps) {
      const event = step.event;
      assert(
        event && event.kind,
        `${definition.key} 第 ${step.index} 步无事件`
      );
      if (definition.category === "cpu") {
        const duration = Number(step.state.end) - Number(step.state.start);
        assert.equal(event.duration, duration, `${definition.key} 运行时长`);
        if (step.state.process === null) {
          assert.equal(event.kind, "cpu.idle");
        } else {
          assert(
            [
              "cpu.run",
              "cpu.complete",
              "cpu.preempt",
              "cpu.demote",
              "cpu.yield",
            ].includes(event.kind),
            `${definition.key} 非空闲事件应为 CPU 运行类事件`
          );
          assert.equal(event.objectId, step.state.process);
        }
      } else if (definition.category === "page") {
        const expected = step.state.fault
          ? step.state.evicted === null
            ? "page.load"
            : "page.replace"
          : "page.hit";
        assert.equal(event.kind, expected, `${definition.key} 页面事件`);
        assert.equal(event.objectId, String(step.state.page));
        if (step.state.evicted !== null) {
          assert.equal(event.from, String(step.state.evicted));
        }
      } else if (definition.category === "allocation") {
        const operation = input.operations[step.index - 1];
        const expected =
          operation.type === "FREE"
            ? step.state.success
              ? "allocation.free"
              : "allocation.free_fail"
            : step.state.success
            ? "allocation.alloc"
            : "allocation.alloc_fail";
        assert.equal(event.kind, expected, `${definition.key} 分配事件`);
        assert.equal(event.objectId, operation.process);
        if (step.state.success && step.state.operationType === "ALLOC") {
          assert.equal(
            event.to,
            `[${step.state.allocated.start},${
              step.state.allocated.start + step.state.allocated.size
            })`
          );
        }
      } else {
        const expected =
          step.state.actionType === "JUMP"
            ? "disk.jump"
            : step.state.actionType === "BOUNDARY"
            ? "disk.boundary"
            : "disk.seek";
        assert.equal(event.kind, expected, `${definition.key} 磁盘事件`);
        assert.equal(event.from, String(step.state.from));
        assert.equal(event.to, String(step.state.target));
        assert.equal(
          event.duration,
          Math.abs(Number(step.state.target) - Number(step.state.from))
        );
      }
    }
  }
});

// ---------- 6. 资源映射清单 ----------
test("resource map covers all 18 keys with resource codes and sources", () => {
  const artifactPath = path.join(
    __dirname,
    "fixtures/simulation-resource-map.json"
  );
  const artifact = JSON.parse(fs.readFileSync(artifactPath, "utf8"));
  assert.equal(artifact.algorithms.length, 18);
  const byKey = new Map(
    artifact.algorithms.map((item) => [item.algorithmKey, item])
  );
  for (const definition of catalog.ALGORITHMS) {
    const entry = byKey.get(definition.key);
    assert(entry, `资源清单缺少 ${definition.key}`);
    assert.equal(entry.resourceCode, definition.resourceCode);
    assert(
      entry.source && entry.source.endsWith(".vue"),
      `${definition.key} 缺少 source`
    );
    assert(entry.animationType, `${definition.key} 缺少 animationType`);
    assert(Array.isArray(entry.inputFields) && entry.inputFields.length > 0);
    assert(Array.isArray(entry.constraints) && entry.constraints.length > 0);
  }
});

console.log("");
if (failures.length) {
  console.error(
    `Simulation player: ${passed} passed, ${failures.length} failed.`
  );
  process.exit(1);
}
console.log(`Simulation player: ${passed} passed, 0 failed.`);
