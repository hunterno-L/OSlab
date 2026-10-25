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

const cpu = load("../src/simulation/cpu.ts");
const allocation = load("../src/simulation/allocation.ts");
const page = load("../src/simulation/page.ts");
const disk = load("../src/simulation/disk.ts");
const catalog = load("../src/simulation/catalog.ts");

const metric = (result, label) =>
  result.metrics.find((item) => item.label === label)?.value;

const standardProcesses = [
  { name: "P1", arrival: 0, burst: 7, priority: 2 },
  { name: "P2", arrival: 2, burst: 4, priority: 1 },
  { name: "P3", arrival: 4, burst: 1, priority: 3 },
];
const nonIdleSegments = (result) =>
  result.timeline
    .filter((segment) => segment.name !== "空闲")
    .map((segment) => `${segment.name}:${segment.start}-${segment.end}`);

// ---------- CPU 调度 ----------
const fcfs = cpu.CPU_ALGORITHMS["cpu.fcfs"].run({
  processes: standardProcesses,
});
assert.deepEqual(nonIdleSegments(fcfs), ["P1:0-7", "P2:7-11", "P3:11-12"]);
assert.equal(metric(fcfs, "平均等待时间"), "4.00");
assert.equal(metric(fcfs, "平均周转时间"), "8.00");
assert.equal(fcfs.steps.length, 3);

const sjf = cpu.CPU_ALGORITHMS["cpu.sjf"].run({
  processes: standardProcesses,
});
assert.deepEqual(nonIdleSegments(sjf), ["P1:0-7", "P3:7-8", "P2:8-12"]);
assert.equal(metric(sjf, "平均等待时间"), "3.00");

const hrrn = cpu.CPU_ALGORITHMS["cpu.hrrn"].run({
  processes: standardProcesses,
});
assert.deepEqual(nonIdleSegments(hrrn), ["P1:0-7", "P3:7-8", "P2:8-12"]);

const priorityNp = cpu.CPU_ALGORITHMS["cpu.priority_nonpreemptive"].run({
  processes: standardProcesses,
});
assert.deepEqual(nonIdleSegments(priorityNp), [
  "P1:0-7",
  "P2:7-11",
  "P3:11-12",
]);

const priorityP = cpu.CPU_ALGORITHMS["cpu.priority_preemptive"].run({
  processes: standardProcesses,
});
assert.deepEqual(nonIdleSegments(priorityP), [
  "P1:0-2",
  "P2:2-6",
  "P1:6-11",
  "P3:11-12",
]);

const rr = cpu.CPU_ALGORITHMS["cpu.rr"].run({
  processes: standardProcesses,
  quantum: 2,
});
assert.deepEqual(nonIdleSegments(rr), [
  "P1:0-2",
  "P2:2-4",
  "P1:4-6",
  "P3:6-7",
  "P2:7-9",
  "P1:9-11",
  "P1:11-12",
]);
assert.deepEqual(
  cpu.CPU_ALGORITHMS["cpu.rr"].run({
    processes: standardProcesses,
    quantum: 2,
  }),
  rr
);

const mlfq = cpu.CPU_ALGORITHMS["cpu.mlfq"].run({
  processes: standardProcesses,
  quantum: 1,
});
assert(mlfq.steps.length > 0);
assert(mlfq.timeline.some((segment) => segment.queueLevel !== undefined));
assert.deepEqual(
  cpu.CPU_ALGORITHMS["cpu.mlfq"].run({
    processes: standardProcesses,
    quantum: 1,
  }),
  mlfq
);

// ---------- 连续内存分配 ----------
const allocationInput = {
  memorySize: 640,
  operations: [
    { type: "ALLOC", process: "P1", size: 100 },
    { type: "ALLOC", process: "P2", size: 200 },
    { type: "FREE", process: "P1" },
    { type: "ALLOC", process: "P3", size: 150 },
    { type: "ALLOC", process: "P4", size: 400 },
  ],
};
const firstFit = allocation.runAllocation(
  "allocation.first_fit",
  allocationInput
);
assert.deepEqual(
  firstFit.steps
    .at(-1)
    .state.blocks.map((block) => [block.start, block.size, block.process]),
  [
    [0, 100, null],
    [100, 200, "P2"],
    [300, 150, "P3"],
    [450, 190, null],
  ]
);
assert.equal(metric(firstFit, "成功分配"), "3");
assert.equal(metric(firstFit, "失败操作"), "1");
assert.equal(metric(firstFit, "最大空闲区"), "190");

const nextFit = allocation.runAllocation("allocation.next_fit", {
  memorySize: 100,
  operations: [
    { type: "ALLOC", process: "A", size: 20 },
    { type: "ALLOC", process: "B", size: 20 },
    { type: "FREE", process: "A" },
    { type: "ALLOC", process: "C", size: 20 },
  ],
});
assert.deepEqual(
  nextFit.steps
    .at(-1)
    .state.blocks.map((block) => [block.start, block.size, block.process]),
  [
    [0, 20, null],
    [20, 20, "B"],
    [40, 20, "C"],
    [60, 40, null],
  ]
);
assert.equal(nextFit.steps.at(-1).state.pointer, 60);

// ---------- 页面置换 ----------
const pageInput = {
  frames: 3,
  sequence: [7, 0, 1, 2, 0, 3, 0, 4, 2, 3, 0, 3, 2],
};
const fifo = page.runPage("page.fifo", pageInput);
assert.equal(fifo.steps.length, 13);
assert.equal(metric(fifo, "缺页次数"), "10");
assert.equal(metric(fifo, "命中次数"), "3");
assert.deepEqual(fifo.steps[3].state.frames, [0, 1, 2]);
assert.deepEqual(page.runPage("page.fifo", pageInput), fifo);
const lru = page.runPage("page.lru", pageInput);
const opt = page.runPage("page.opt", pageInput);
assert(lru.steps.length === 13 && opt.steps.length === 13);
assert(Number(metric(opt, "缺页次数")) <= Number(metric(lru, "缺页次数")));

// ---------- 磁盘调度 ----------
const diskInput = {
  head: 53,
  requests: [98, 183, 37, 122, 14, 124, 65, 67],
  direction: "UP",
};
const diskFcfs = disk.runDisk("disk.fcfs", diskInput);
assert.equal(metric(diskFcfs, "总寻道长度"), "640");
const sstf = disk.runDisk("disk.sstf", diskInput);
assert.deepEqual(
  sstf.steps
    .filter((step) => step.state.request !== null)
    .map((step) => step.state.request),
  [65, 67, 37, 14, 98, 122, 124, 183]
);
assert.equal(metric(sstf, "总寻道长度"), "236");
const scan = disk.runDisk("disk.scan", diskInput);
assert(scan.steps.some((step) => step.state.actionType === "BOUNDARY"));
assert.equal(metric(scan, "总寻道长度"), "333");
const cscan = disk.runDisk("disk.cscan", diskInput);
assert(cscan.steps.some((step) => step.state.actionType === "JUMP"));
assert.equal(metric(cscan, "总寻道长度"), "183");

// ---------- 计划补齐项：MLFQ 2/6 级、多初始分区、页面 60 帧、磁盘端点 ----------
const mlfq2 = cpu.CPU_ALGORITHMS["cpu.mlfq"].run({
  processes: standardProcesses,
  quantum: 2,
  mlfqQueues: [2, 4],
});
assert.equal(mlfq2.initialState.queues.length, 2);
assert(
  mlfq2.steps.every((step) => (step.state.queues || []).length === 2),
  "MLFQ 2 级队列状态必须保持 2 级"
);
assert(
  mlfq2.steps.some((step) => /降级|完成|抢占/.test(step.detail)),
  "MLFQ 必须呈现进入/降级/抢占/完成事件"
);
const mlfq6 = cpu.CPU_ALGORITHMS["cpu.mlfq"].run({
  processes: standardProcesses,
  quantum: 1,
  mlfqQueues: [1, 2, 3, 4, 5, 6],
});
assert.equal(mlfq6.initialState.queues.length, 6);
assert(
  mlfq6.steps.every((step) => (step.state.queues || []).length === 6),
  "MLFQ 6 级队列状态必须保持 6 级"
);
assert(
  mlfq6.timeline.every(
    (segment) => segment.queueLevel === undefined || segment.queueLevel < 6
  )
);
assert.equal(
  cpu.validateCpuInput(
    { processes: standardProcesses, quantum: 1, mlfqQueues: [1] },
    false,
    true
  ),
  "多级反馈队列的队列数需为 2–6"
);
assert(
  cpu
    .validateCpuInput(
      { processes: standardProcesses, quantum: 1, mlfqQueues: [1, 0] },
      false,
      true
    )
    .includes("1–20")
);

const multiPartition = allocation.runAllocation("allocation.first_fit", {
  memorySize: 300,
  initialPartitions: [100, 200],
  operations: [
    { type: "ALLOC", process: "A", size: 80 },
    { type: "ALLOC", process: "B", size: 150 },
  ],
});
assert.deepEqual(
  multiPartition.initialState.blocks.map((block) => block.size),
  [100, 200]
);
assert.deepEqual(
  multiPartition.steps
    .at(-1)
    .state.blocks.map((block) => [block.start, block.size, block.process]),
  [
    [0, 80, "A"],
    [80, 20, null],
    [100, 150, "B"],
    [250, 50, null],
  ]
);
assert(
  allocation
    .validateAllocationInput({
      memorySize: 300,
      initialPartitions: [100, 100],
      operations: [{ type: "ALLOC", process: "A", size: 10 }],
    })
    .includes("总大小")
);
assert(
  allocation
    .validateAllocationInput({
      memorySize: 300,
      initialPartitions: Array.from({ length: 13 }, () => 10),
      operations: [{ type: "ALLOC", process: "A", size: 10 }],
    })
    .includes("12")
);

const longPageSequence = Array.from({ length: 200 }, (_, index) => index % 70);
const page60 = page.runPage("page.lru", {
  frames: 60,
  sequence: longPageSequence,
});
assert.equal(page60.steps.length, 200);
assert(page60.steps.every((step) => step.state.frames.length <= 60));
assert.equal(
  page.validatePageInput({ frames: 60, sequence: longPageSequence }),
  ""
);
assert(page.validatePageInput({ frames: 61, sequence: [1] }).includes("1–60"));
assert(
  page
    .validatePageInput({ frames: 1, sequence: Array(201).fill(1) })
    .includes("200")
);

const scanUp = disk.runDisk("disk.scan", {
  head: 200,
  requests: [200, 1],
  direction: "UP",
});
assert.equal(metric(scanUp, "总寻道长度"), "199");
assert(
  scanUp.steps
    .filter((step) => step.state.actionType === "BOUNDARY")
    .every((step) => step.state.target === 200)
);
const cscanEndpoint = disk.runDisk("disk.cscan", {
  head: 190,
  requests: [10, 200],
  direction: "UP",
});
assert.equal(metric(cscanEndpoint, "总寻道长度"), "19");
const jumpStep = cscanEndpoint.steps.find(
  (step) => step.state.actionType === "JUMP"
);
assert.equal(jumpStep.state.target, 1);
assert.equal(jumpStep.state.movement, 0);
assert.equal(
  disk.validateDiskInput({
    head: 1,
    requests: [200],
    direction: "DOWN",
  }),
  ""
);
assert(
  disk
    .validateDiskInput({ head: 0, requests: [1], direction: "UP" })
    .includes("1–200")
);
assert(
  disk
    .validateDiskInput({ head: 1, requests: [201], direction: "UP" })
    .includes("1–200")
);

// ---------- 18 项目录与默认输入可复算 ----------
assert.equal(catalog.ALGORITHMS.length, 18);
assert.equal(new Set(catalog.ALGORITHMS.map((item) => item.key)).size, 18);
assert.equal(
  new Set(catalog.ALGORITHMS.map((item) => item.resourceCode)).size,
  18
);
assert.equal(new Set(catalog.ALGORITHMS.map((item) => item.nodeCode)).size, 18);
for (const definition of catalog.ALGORITHMS) {
  const input = JSON.parse(JSON.stringify(definition.defaultInput));
  let result;
  let validation;
  if (definition.category === "cpu") {
    validation = cpu.validateCpuInput(
      input,
      definition.requiresPriority,
      definition.usesQuantum
    );
    result = cpu.CPU_ALGORITHMS[definition.key].run(input);
  } else if (definition.category === "allocation") {
    validation = allocation.validateAllocationInput(input);
    result = allocation.runAllocation(definition.key, input);
  } else if (definition.category === "page") {
    validation = page.validatePageInput(input);
    result = page.runPage(definition.key, input);
  } else {
    validation = disk.validateDiskInput(input);
    result = disk.runDisk(definition.key, input);
  }
  assert.equal(validation, "", `${definition.key}: ${validation}`);
  assert(result.steps.length > 0, `${definition.key} 没有步骤`);
  assert(result.metrics.length > 0, `${definition.key} 没有指标`);
}

// ---------- 边界校验 ----------
assert(
  cpu.validateCpuInput({ processes: [] }).includes("至少"),
  "空 CPU 输入应被拒绝"
);
assert(
  cpu
    .validateCpuInput(
      {
        processes: [{ name: "P1", arrival: 0, burst: 1 }],
        quantum: 0,
      },
      false,
      true
    )
    .includes("时间片"),
  "时间片边界应被拒绝"
);
assert(
  allocation
    .validateAllocationInput({
      memorySize: 10,
      operations: [{ type: "ALLOC", process: "A", size: 5 }],
    })
    .includes("16"),
  "过小内存应被拒绝"
);
assert(
  page.validatePageInput({ frames: 61, sequence: [1] }).includes("1–60"),
  "物理块边界应被拒绝"
);
assert(
  disk
    .validateDiskInput({ head: 0, requests: [1], direction: "UP" })
    .includes("1–200"),
  "磁头下界应被拒绝"
);
assert(
  disk
    .validateDiskInput({ head: 201, requests: [1], direction: "UP" })
    .includes("1–200"),
  "磁头上界应被拒绝"
);

// ---------- CPU 真值 fixture：完成顺序 + 等待/周转/响应 ----------
const clone = (value) => JSON.parse(JSON.stringify(value));
const completionOrder = (result) => {
  const lastEnd = new Map();
  for (const segment of result.timeline) {
    if (segment.name === "空闲") continue;
    lastEnd.set(segment.name, segment.end);
  }
  return [...lastEnd.entries()]
    .sort((a, b) => a[1] - b[1] || a[0].localeCompare(b[0]))
    .map(([name]) => name);
};

const cpuTruth = {
  "cpu.fcfs": {
    order: ["P1", "P2", "P3"],
    waiting: "4.00",
    turnaround: "8.00",
    response: "4.00",
    switches: "2",
  },
  "cpu.sjf": {
    order: ["P1", "P3", "P2"],
    waiting: "3.00",
    turnaround: "7.00",
    response: "3.00",
    switches: "2",
  },
  "cpu.hrrn": {
    order: ["P1", "P3", "P2"],
    waiting: "3.00",
    turnaround: "7.00",
    response: "3.00",
    switches: "2",
  },
  "cpu.priority_nonpreemptive": {
    order: ["P1", "P2", "P3"],
    waiting: "4.00",
    turnaround: "8.00",
    response: "4.00",
    switches: "2",
  },
  "cpu.priority_preemptive": {
    order: ["P2", "P1", "P3"],
    waiting: "3.67",
    turnaround: "7.67",
    response: "2.33",
    switches: "3",
  },
  "cpu.rr": {
    order: ["P3", "P2", "P1"],
    waiting: "3.33",
    turnaround: "7.33",
    response: "0.67",
    switches: "5",
  },
  "cpu.mlfq": {
    order: ["P3", "P1", "P2"],
    waiting: "3.33",
    turnaround: "7.33",
    response: "0.00",
    switches: "7",
  },
};

for (const [key, expected] of Object.entries(cpuTruth)) {
  const result = cpu.CPU_ALGORITHMS[key].run({
    processes: clone(standardProcesses),
    quantum: key === "cpu.mlfq" ? 1 : 2,
  });
  assert.deepEqual(completionOrder(result), expected.order, `${key} 完成顺序`);
  assert.equal(metric(result, "平均等待时间"), expected.waiting, `${key} 等待`);
  assert.equal(
    metric(result, "平均周转时间"),
    expected.turnaround,
    `${key} 周转`
  );
  assert.equal(
    metric(result, "平均响应时间"),
    expected.response,
    `${key} 响应`
  );
  assert.equal(metric(result, "上下文切换"), expected.switches, `${key} 切换`);
  // 每个进程都必须完成且运行了完整 burst
  const runTime = new Map();
  for (const segment of result.timeline) {
    if (segment.name === "空闲") continue;
    runTime.set(
      segment.name,
      (runTime.get(segment.name) || 0) + (segment.end - segment.start)
    );
  }
  for (const process of standardProcesses) {
    assert.equal(
      runTime.get(process.name),
      process.burst,
      `${key}: ${process.name} 运行时间应等于 burst`
    );
  }
  assert(result.notes.some((note) => note.includes("等待时间 = 完成")));
}

// 抢占式算法：P1 响应时间为 0，但等待时间为 4，二者不可混用
const priorityPreemptive = cpu.CPU_ALGORITHMS["cpu.priority_preemptive"].run({
  processes: clone(standardProcesses),
});
const p1Start = priorityPreemptive.timeline.find(
  (segment) => segment.name === "P1"
).start;
const p1End = priorityPreemptive.timeline
  .filter((segment) => segment.name === "P1")
  .at(-1).end;
assert.equal(p1Start - 0, 0, "P1 响应时间应为 0");
assert.equal(p1End - 0 - 7, 4, "P1 等待时间应为 4（周转 − burst）");

// ---------- 页面置换真值 fixture：FIFO / LRU / OPT ----------
const pageTruth = [
  {
    key: "page.fifo",
    faults: "10",
    hits: "3",
    rate: "23.08%",
    evictions: "7",
    finalFrames: [2, 3, 0],
    step6Evicted: 0,
    stateRate: 23.08,
  },
  {
    key: "page.lru",
    faults: "9",
    hits: "4",
    rate: "30.77%",
    evictions: "6",
    finalFrames: [0, 3, 2],
    step6Evicted: 1,
    stateRate: 30.77,
  },
  {
    key: "page.opt",
    faults: "7",
    hits: "6",
    rate: "46.15%",
    evictions: "4",
    finalFrames: [2, 3, 0],
    step6Evicted: 1,
    stateRate: 46.15,
  },
];
for (const expected of pageTruth) {
  const result = page.runPage(expected.key, clone(pageInput));
  assert.equal(metric(result, "缺页次数"), expected.faults, expected.key);
  assert.equal(metric(result, "命中次数"), expected.hits, expected.key);
  assert.equal(metric(result, "命中率"), expected.rate, expected.key);
  assert.equal(metric(result, "淘汰次数"), expected.evictions, expected.key);
  assert.deepEqual(
    result.steps.at(-1).state.frames,
    expected.finalFrames,
    `${expected.key} 最终驻留顺序`
  );
  assert.equal(result.steps[3].state.evicted, 7, `${expected.key} 第 4 步淘汰`);
  assert.equal(
    result.steps[5].state.evicted,
    expected.step6Evicted,
    `${expected.key} 第 6 步淘汰`
  );
  for (const step of result.steps) {
    assert(
      step.state.frames.length <= pageInput.frames,
      `${expected.key} 超帧`
    );
    assert.equal(
      new Set(step.state.frames).size,
      step.state.frames.length,
      `${expected.key} 帧内重复`
    );
  }
  let previousFrames = [];
  for (const step of result.steps) {
    assert.equal(
      step.state.fault,
      !previousFrames.includes(step.state.page),
      `${expected.key} 第 ${step.index} 步命中/缺页与前一状态不一致`
    );
    previousFrames = step.state.frames;
  }
  assert.equal(result.steps.at(-1).state.hitRate, expected.stateRate);
}

// ---------- 分配真值 fixture：First / Best / Worst / Next ----------
const allocationFixture = {
  memorySize: 200,
  operations: [
    { type: "ALLOC", process: "A", size: 30 },
    { type: "ALLOC", process: "B", size: 50 },
    { type: "ALLOC", process: "C", size: 25 },
    { type: "ALLOC", process: "D", size: 30 },
    { type: "FREE", process: "A" },
    { type: "FREE", process: "C" },
    { type: "ALLOC", process: "E", size: 25 },
  ],
};
const allocationTruth = {
  "allocation.first_fit": {
    layout: [
      [0, 25, "E"],
      [25, 5, null],
      [30, 50, "B"],
      [80, 25, null],
      [105, 30, "D"],
      [135, 65, null],
    ],
    largest: "65",
    victim: { start: 0, size: 30 },
  },
  "allocation.best_fit": {
    layout: [
      [0, 30, null],
      [30, 50, "B"],
      [80, 25, "E"],
      [105, 30, "D"],
      [135, 65, null],
    ],
    largest: "65",
    victim: { start: 80, size: 25 },
  },
  "allocation.worst_fit": {
    layout: [
      [0, 30, null],
      [30, 50, "B"],
      [80, 25, null],
      [105, 30, "D"],
      [135, 25, "E"],
      [160, 40, null],
    ],
    largest: "40",
    victim: { start: 135, size: 65 },
  },
  "allocation.next_fit": {
    layout: [
      [0, 30, null],
      [30, 50, "B"],
      [80, 25, null],
      [105, 30, "D"],
      [135, 25, "E"],
      [160, 40, null],
    ],
    largest: "40",
    victim: { start: 135, size: 65 },
  },
};
for (const [key, expected] of Object.entries(allocationTruth)) {
  const result = allocation.runAllocation(key, clone(allocationFixture));
  assert.deepEqual(
    result.steps
      .at(-1)
      .state.blocks.map((block) => [block.start, block.size, block.process]),
    expected.layout,
    `${key} 最终布局`
  );
  assert.equal(metric(result, "空闲空间"), "95", `${key} 空闲空间`);
  assert.equal(metric(result, "最大空闲区"), expected.largest, key);
  assert.equal(metric(result, "失败操作"), "0", `${key} 失败数`);
  assert.equal(metric(result, "成功分配"), "5", `${key} 成功数`);
  const last = result.steps.at(-1).state;
  assert.deepEqual(last.victim, expected.victim, `${key} 选中空闲区`);
  assert.deepEqual(last.allocated, {
    start: expected.victim.start,
    size: 25,
    process: "E",
  });
  assert.equal(last.merged, false);
  assert.equal(last.request.size, 25);
  assert.equal(last.candidates.length, 3, `${key} 候选空闲区数量`);
}

// Next Fit 的候选按指针位置循环扫描
const nextFitCandidates = allocation
  .runAllocation("allocation.next_fit", clone(allocationFixture))
  .steps.at(-1).state.candidates;
assert.deepEqual(nextFitCandidates, [
  { start: 135, size: 65 },
  { start: 0, size: 30 },
  { start: 80, size: 25 },
]);

// 回收合并真值：连续回收两个相邻分区后合并为一块
const mergeResult = allocation.runAllocation("allocation.first_fit", {
  memorySize: 100,
  operations: [
    { type: "ALLOC", process: "A", size: 20 },
    { type: "ALLOC", process: "B", size: 20 },
    { type: "FREE", process: "A" },
    { type: "FREE", process: "B" },
  ],
});
assert.equal(mergeResult.steps[2].state.merged, false);
assert.equal(mergeResult.steps[3].state.merged, true);
assert.deepEqual(
  mergeResult.steps[3].state.blocks.map((block) => [
    block.start,
    block.size,
    block.process,
  ]),
  [[0, 100, null]]
);

// 分配失败真值（四种算法一致）
for (const key of Object.keys(allocationTruth)) {
  const failed = allocation.runAllocation(key, {
    memorySize: 64,
    operations: [
      { type: "ALLOC", process: "A", size: 40 },
      { type: "ALLOC", process: "B", size: 30 },
    ],
  });
  assert.equal(metric(failed, "成功分配"), "1", `${key} 成功数`);
  assert.equal(metric(failed, "失败操作"), "1", `${key} 失败数`);
  assert.equal(metric(failed, "空闲空间"), "24", `${key} 空闲`);
  assert.equal(metric(failed, "最大空闲区"), "24", `${key} 最大空闲`);
  assert.deepEqual(
    failed.steps
      .at(-1)
      .state.blocks.map((block) => [block.start, block.size, block.process]),
    [
      [0, 40, "A"],
      [40, 24, null],
    ]
  );
  assert.equal(failed.steps.at(-1).state.success, false);
  assert.deepEqual(failed.steps.at(-1).state.candidates, []);
  assert.equal(failed.steps.at(-1).state.victim, null);
}

// ---------- 磁盘真值 fixture：顺序、方向与端点规则 ----------
const diskTruth = [
  {
    key: "disk.fcfs",
    direction: "UP",
    order: [98, 183, 37, 122, 14, 124, 65, 67],
    seek: "640",
    boundary: "0",
    jump: "0",
  },
  {
    key: "disk.sstf",
    direction: "DOWN",
    order: [65, 67, 37, 14, 98, 122, 124, 183],
    seek: "236",
    boundary: "0",
    jump: "0",
  },
  {
    key: "disk.scan",
    direction: "UP",
    order: [65, 67, 98, 122, 124, 183, 37, 14],
    seek: "333",
    boundary: "1",
    jump: "0",
    endpoint: 200,
  },
  {
    key: "disk.scan",
    direction: "DOWN",
    order: [37, 14, 65, 67, 98, 122, 124, 183],
    seek: "234",
    boundary: "1",
    jump: "0",
    endpoint: 1,
  },
  {
    key: "disk.cscan",
    direction: "UP",
    order: [65, 67, 98, 122, 124, 183, 14, 37],
    seek: "183",
    boundary: "1",
    jump: "1",
    endpoint: 200,
    jumpTarget: 1,
  },
  {
    key: "disk.cscan",
    direction: "DOWN",
    order: [37, 14, 183, 124, 122, 98, 67, 65],
    seek: "187",
    boundary: "1",
    jump: "1",
    endpoint: 1,
    jumpTarget: 200,
  },
];
for (const expected of diskTruth) {
  const result = disk.runDisk(expected.key, {
    head: 53,
    requests: clone(diskInput.requests),
    direction: expected.direction,
  });
  assert.deepEqual(
    result.steps
      .filter((step) => step.state.request !== null)
      .map((step) => step.state.request),
    expected.order,
    `${expected.key}/${expected.direction} 服务顺序`
  );
  assert.equal(metric(result, "总寻道长度"), expected.seek, expected.key);
  assert.equal(metric(result, "服务请求数"), "8");
  const boundarySteps = result.steps.filter(
    (step) => step.state.actionType === "BOUNDARY"
  );
  const jumpSteps = result.steps.filter(
    (step) => step.state.actionType === "JUMP"
  );
  assert.equal(String(boundarySteps.length), expected.boundary, expected.key);
  assert.equal(String(jumpSteps.length), expected.jump, expected.key);
  if (expected.endpoint !== undefined) {
    assert.equal(boundarySteps[0].state.target, expected.endpoint);
  }
  if (expected.jumpTarget !== undefined) {
    assert.equal(jumpSteps[0].state.target, expected.jumpTarget);
    assert.equal(jumpSteps[0].state.movement, 0, "回跳距离不计入寻道");
  }
  // 每个请求步的移动距离 = |target − from|；累计寻道与指标一致
  let cumulative = 0;
  for (const step of result.steps) {
    const state = step.state;
    assert(state.head >= 1 && state.head <= 200, "磁头越界");
    assert(state.from >= 1 && state.from <= 200, "起点越界");
    if (state.actionType === "JUMP") {
      assert.equal(state.movement, 0);
    } else {
      assert.equal(state.movement, Math.abs(state.target - state.from));
      cumulative += state.movement;
    }
    assert.equal(state.totalSeek, cumulative, "累计寻道与逐步移动一致");
  }
  assert.equal(String(cumulative), expected.seek);
  const metricLabel =
    expected.key === "disk.cscan" ? "回跳次数" : "边界换向次数";
  assert.equal(
    metric(result, metricLabel),
    expected.key === "disk.cscan" ? expected.jump : expected.boundary
  );
}

// ---------- 动画消费契约：每步都有可渲染的真值状态 ----------
for (const definition of catalog.ALGORITHMS) {
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
  assert(result.initialState, `${definition.key} 缺少初始状态`);
  let previousFrames = [];
  for (const step of result.steps) {
    assert(
      step.state && typeof step.state === "object",
      `${definition.key} 状态`
    );
    if (definition.category === "cpu") {
      assert.equal(typeof step.state.start, "number");
      assert.equal(typeof step.state.end, "number");
      assert.equal(step.state.end, step.time);
      assert(Array.isArray(step.state.ready));
    } else if (definition.category === "page") {
      assert(Array.isArray(step.state.frames));
      assert(step.state.frames.length <= input.frames);
      assert.equal(typeof step.state.page, "number");
      assert.equal(typeof step.state.fault, "boolean");
      assert.equal(
        step.state.fault,
        !previousFrames.includes(step.state.page),
        `${definition.key} 第 ${step.index} 步命中/缺页与前一状态不一致`
      );
      previousFrames = step.state.frames;
    } else if (definition.category === "allocation") {
      const blocks = step.state.blocks;
      assert(Array.isArray(blocks) && blocks.length > 0);
      let cursor = 0;
      let free = 0;
      for (const block of blocks) {
        assert.equal(block.start, cursor, `${definition.key} 分区应连续`);
        assert(block.size > 0);
        cursor += block.size;
        if (block.process === null) free += block.size;
      }
      assert.equal(
        cursor,
        input.memorySize,
        `${definition.key} 分区应覆盖内存`
      );
      assert.equal(step.state.freeTotal, free);
      assert(Array.isArray(step.state.candidates));
      if (step.state.operationType === "ALLOC") {
        assert(step.state.request, `${definition.key} 分配请求缺少 request`);
      }
    } else {
      assert(step.state.head >= 1 && step.state.head <= 200);
      assert(step.state.from >= 1 && step.state.from <= 200);
      assert(Array.isArray(step.state.remaining));
      assert(Array.isArray(step.state.order));
      assert(step.state.order.length <= input.requests.length);
    }
  }
  if (definition.category === "cpu" && result.timeline) {
    for (let index = 1; index < result.timeline.length; index += 1) {
      assert.equal(
        result.timeline[index].start,
        result.timeline[index - 1].end,
        `${definition.key} 时间轴应连续`
      );
    }
  }
}

console.log(
  "Simulation algorithms: 18/18 runnable with exact CPU/page/allocation/disk truth fixtures and boundary checks."
);
