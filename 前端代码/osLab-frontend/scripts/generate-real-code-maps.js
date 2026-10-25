/**
 * Generate src/simulation/codeMaps.ts CODE_MAPS from the real simulation source.
 * Event line mappings are derived by searching real source lines, so the panel
 * can never show a line that is not in the implementation file.
 *
 * Run from 前端代码/osLab-frontend:
 *   node scripts/generate-real-code-maps.js
 */
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const readSource = (relative) =>
  fs
    .readFileSync(path.join(root, "src/simulation", relative), "utf8")
    .replace(/\r/g, "");

const sourceCache = new Map();
const sourceOf = (file) => {
  if (!sourceCache.has(file)) sourceCache.set(file, readSource(file));
  return sourceCache.get(file);
};

function extractFunction(file, name) {
  const source = sourceOf(file);
  const start = source.indexOf(`function ${name}(`);
  if (start < 0) throw new Error(`${file}: function ${name} not found`);
  const end = source.indexOf("\n}\n", start);
  if (end < 0) throw new Error(`${file}: end of function ${name} not found`);
  return source.slice(start, end + 3);
}

function lineIndexOf(lines, definition) {
  const needle =
    typeof definition === "string" ? definition : definition.needle;
  const occurrence =
    typeof definition === "string" ? 0 : definition.occurrence || 0;
  const found = [];
  lines.forEach((line, index) => {
    if (line.includes(needle)) found.push(index + 1);
  });
  if (!found.length) throw new Error(`source line not found: ${needle}`);
  if (occurrence > 0) {
    if (!found[occurrence - 1]) {
      throw new Error(
        `source line occurrence ${occurrence} not found: ${needle}`
      );
    }
    return [found[occurrence - 1]];
  }
  return found;
}

function eventMap(lines, definitions) {
  const map = {};
  for (const [kind, needles] of Object.entries(definitions)) {
    const indexes = new Set();
    needles.forEach((needle) => {
      lineIndexOf(lines, needle).forEach((index) => indexes.add(index));
    });
    map[kind] = [...indexes].sort((a, b) => a - b);
  }
  return map;
}

const cpuProcessDefinitions = {
  "cpu.fcfs": {
    file: "cpu.ts",
    symbol: "fcfs",
    functions: ["fcfs"],
    events: {
      "cpu.idle": ["if (start > time)", 'name: "空闲"'],
      "cpu.run": [
        "const start = Math.max(time, process.arrival);",
        "time = start + process.burst;",
        "name: process.name",
      ],
    },
  },
  "cpu.sjf": {
    file: "cpu.ts",
    symbol: "sjf",
    functions: ["sjf"],
    events: {
      "cpu.idle": [
        "if (!candidates.length)",
        'name: "空闲"',
        "time = nextArrival;",
      ],
      "cpu.run": [
        "const pick = candidates.sort(",
        "time += pick.burst;",
        "done.add(pick.index);",
        "name: pick.name",
      ],
    },
  },
  "cpu.hrrn": {
    file: "cpu.ts",
    symbol: "hrrn",
    functions: ["hrrn"],
    events: {
      "cpu.idle": [
        "if (!candidates.length)",
        'name: "空闲"',
        "time = nextArrival;",
      ],
      "cpu.run": [
        "const ratioA =",
        "const ratioB =",
        "const pick = candidates[0];",
        "time += pick.burst;",
        "name: pick.name",
      ],
    },
  },
  "cpu.priority_nonpreemptive": {
    file: "cpu.ts",
    symbol: "priorityNonPreemptive",
    functions: ["priorityNonPreemptive"],
    events: {
      "cpu.idle": [
        "if (!candidates.length)",
        'name: "空闲"',
        "time = nextArrival;",
      ],
      "cpu.run": [
        "const pick = candidates.slice().sort(byPriority)[0];",
        "time += pick.burst;",
        "done.add(pick.index);",
        "name: pick.name",
      ],
    },
  },
  "cpu.priority_preemptive": {
    file: "cpu.ts",
    symbol: "priorityPreemptive",
    functions: ["priorityPreemptive"],
    events: {
      "cpu.idle": ["if (!candidates.length)", "ownerAt.push(null);"],
      "cpu.run": [
        "const pick = candidates[0];",
        "remaining.set(pick.index, left);",
        "finishAt.set(owner, unit + 1);",
        "name: process.name",
      ],
    },
  },
  "cpu.rr": {
    file: "cpu.ts",
    symbol: "roundRobin",
    functions: ["roundRobin"],
    events: {
      "cpu.idle": ["if (!queue.length)", 'name: "空闲"', "time = jump;"],
      "cpu.run": [
        "const process = queue.shift()",
        "const run = Math.min(quantum, left);",
        "remaining.set(process.index, left - run);",
        "queue.push(process);",
        "name: process.name",
      ],
      "cpu.complete": [
        "remaining.delete(process.index);",
        "name: process.name",
      ],
    },
  },
  "cpu.mlfq": {
    file: "cpu.ts",
    symbol: "mlfq",
    functions: ["mlfq"],
    events: {
      "cpu.idle": ["if (level < 0)", 'name: "空闲"', "time = jump;"],
      "cpu.run": [
        "const index = queues[level].shift()",
        "let end = time + Math.min(quantum, remaining[index]);",
        "if (level > 0 && nextArrivalTime",
        "name: process.name",
      ],
      "cpu.complete": [
        "finished.add(index);",
        "完成并离开",
        "name: process.name",
      ],
      "cpu.preempt": [
        { needle: "queues[level].unshift(index);", occurrence: 1 },
        "被高优先级到达抢占",
        "name: process.name",
      ],
      "cpu.demote": [
        "target = Math.min(level + 1, queueCount - 1);",
        "queues[target].push(index);",
        "降级到 Q",
        "name: process.name",
      ],
      "cpu.yield": [
        { needle: "queues[level].unshift(index);", occurrence: 2 },
        "让出 CPU",
        "name: process.name",
      ],
    },
  },
};

const allocationEvents = {
  "allocation.free": [
    'if (operation.type === "FREE")',
    "target.process = null;",
    "blocks = mergeFree(blocks);",
    "freed = {",
  ],
  "allocation.free_fail": [
    "if (!target)",
    { needle: "message = `进程", occurrence: 1 },
  ],
  "allocation.alloc": [
    "const search = alreadyAllocated",
    "const victimBlock = search.chosen;",
    "const allocatedBlock: Block = {",
    "pointer = (victimBlock.start + size) % memorySize;",
  ],
  "allocation.alloc_fail": [
    "if (alreadyAllocated) {",
    { needle: "message = `进程", occurrence: 2 },
    "} else if (!victimBlock) {",
    "message = `没有不小于",
  ],
};

const allocationBranch = {
  "allocation.first_fit":
    "return { chosen: adequate[0], candidates: adequate };",
  "allocation.best_fit":
    ".sort((a, b) => a.size - b.size || a.start - b.start)[0],",
  "allocation.worst_fit":
    ".sort((a, b) => b.size - a.size || a.start - b.start)[0],",
  "allocation.next_fit": 'if (algorithm === "allocation.next_fit") {',
};

const pageEvents = {
  "page.fifo": {
    "page.hit": ["const hit = memory.includes(page);", "steps.push({"],
    "page.load": [
      "if (!hit)",
      "faults += 1;",
      { needle: "memory.push(page);", occurrence: 1 },
    ],
    "page.replace": [
      "if (memory.length >= input.frames)",
      "let victimIndex = 0;",
      "evicted = memory.splice(victimIndex, 1)[0];",
      { needle: "memory.push(page);", occurrence: 1 },
    ],
  },
  "page.lru": {
    "page.hit": [
      "const hit = memory.includes(page);",
      '} else if (algorithm === "page.lru") {',
      "memory.splice(memory.indexOf(page), 1);",
      { needle: "memory.push(page);", occurrence: 2 },
      "steps.push({",
    ],
    "page.load": [
      "if (!hit)",
      "faults += 1;",
      { needle: "memory.push(page);", occurrence: 1 },
    ],
    "page.replace": [
      "if (memory.length >= input.frames)",
      "let victimIndex = 0;",
      "evicted = memory.splice(victimIndex, 1)[0];",
      { needle: "memory.push(page);", occurrence: 1 },
    ],
  },
  "page.opt": {
    "page.hit": ["const hit = memory.includes(page);", "steps.push({"],
    "page.load": [
      "if (!hit)",
      "faults += 1;",
      { needle: "memory.push(page);", occurrence: 1 },
    ],
    "page.replace": [
      "if (memory.length >= input.frames)",
      "let farthest = -1;",
      "const next = nextUse(input.sequence, index, resident);",
      "evicted = memory.splice(victimIndex, 1)[0];",
      { needle: "memory.push(page);", occurrence: 1 },
    ],
  },
};

const diskDefinitions = {
  "disk.fcfs": {
    functions: ["fcfsActions", "runDisk"],
    symbol: "fcfsActions + runDisk",
    events: {
      "disk.seek": [
        "const movement =",
        { needle: "totalSeek += movement;", occurrence: 1 },
        "order.push(action.target);",
      ],
    },
  },
  "disk.sstf": {
    functions: ["sstfActions", "runDisk"],
    symbol: "sstfActions + runDisk",
    events: {
      "disk.seek": [
        "const target = pending.splice(bestIndex, 1)[0];",
        "const movement =",
        { needle: "totalSeek += movement;", occurrence: 1 },
        "order.push(action.target);",
      ],
    },
  },
  "disk.scan": {
    functions: ["scanActions", "runDisk"],
    symbol: "scanActions + runDisk",
    events: {
      "disk.seek": [
        "const movement =",
        { needle: "totalSeek += movement;", occurrence: 1 },
        "order.push(action.target);",
      ],
      "disk.boundary": [
        "boundaryCount += 1;",
        { needle: "totalSeek += movement;", occurrence: 2 },
      ],
    },
  },
  "disk.cscan": {
    functions: ["cscanActions", "runDisk"],
    symbol: "cscanActions + runDisk",
    events: {
      "disk.seek": [
        "const movement =",
        { needle: "totalSeek += movement;", occurrence: 1 },
        "order.push(action.target);",
      ],
      "disk.boundary": [
        "boundaryCount += 1;",
        { needle: "totalSeek += movement;", occurrence: 2 },
      ],
      "disk.jump": ['action.type === "JUMP" ? 0', "else jumpCount += 1;"],
    },
  },
};

function buildMap(config) {
  const lineArrays = config.functions.map((name) =>
    extractFunction(config.file, name).split("\n")
  );
  const lines = [];
  lineArrays.forEach((array) => {
    lines.push(...array);
  });
  while (lines.length && !lines[lines.length - 1].trim()) lines.pop();
  return {
    sourceFile: `src/simulation/${config.file}`,
    symbol: config.symbol,
    language: "typescript",
    lines,
    eventMap: eventMap(lines, config.events),
  };
}

const maps = {};
for (const [key, config] of Object.entries(cpuProcessDefinitions)) {
  maps[key] = buildMap(config);
}
for (const [key, branch] of Object.entries(allocationBranch)) {
  maps[key] = {
    ...buildMap({
      file: "allocation.ts",
      symbol: "chooseFreeBlock + runAllocation",
      functions: ["chooseFreeBlock", "runAllocation"],
      events: {
        ...allocationEvents,
        "allocation.alloc": [...allocationEvents["allocation.alloc"], branch],
      },
    }),
  };
}
for (const [key, events] of Object.entries(pageEvents)) {
  maps[key] = buildMap({
    file: "page.ts",
    symbol: "runPage",
    functions: ["runPage"],
    events,
  });
}
for (const [key, config] of Object.entries(diskDefinitions)) {
  maps[key] = buildMap({
    file: "disk.ts",
    symbol: config.symbol,
    functions: config.functions,
    events: config.events,
  });
}

const serialized = JSON.stringify(maps, null, 2).replace(
  /"([a-z0-9._]+)": \{/g,
  '"$1": {'
);

const targetFile = path.join(root, "src/simulation/codeMaps.ts");
const current = fs.readFileSync(targetFile, "utf8");
const startMarker =
  "export const CODE_MAPS: Record<string, AlgorithmCodeMap> = ";
const endMarker = "\nexport const CODE_MAP_KEYS";
const start = current.indexOf(startMarker);
const end = current.indexOf(endMarker);
if (start < 0 || end < 0) throw new Error("CODE_MAPS block markers not found");
const next = `${startMarker}${serialized};${current.slice(end)}`;
fs.writeFileSync(targetFile, current.slice(0, start) + next);
console.log(
  `Generated ${Object.keys(maps).length} real code maps -> ${targetFile}`
);
