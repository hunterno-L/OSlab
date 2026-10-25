import type {
  AlgorithmCategory,
  AlgorithmDefinition,
  AllocationOperation,
  CpuProcessInput,
  DemoInput,
} from "./types";

export const CATEGORY_ORDER: AlgorithmCategory[] = [
  "cpu",
  "allocation",
  "page",
  "disk",
];

export const CATEGORY_LABELS: Record<AlgorithmCategory, string> = {
  cpu: "CPU 调度",
  allocation: "连续内存分配",
  page: "页面置换",
  disk: "磁盘调度",
};

const processes = [
  { name: "P1", arrival: 0, burst: 7, priority: 2 },
  { name: "P2", arrival: 2, burst: 4, priority: 1 },
  { name: "P3", arrival: 4, burst: 1, priority: 3 },
];

const pageInput = {
  frames: 3,
  sequence: [7, 0, 1, 2, 0, 3, 0, 4, 2, 3, 0, 3, 2],
};

const allocationInput = {
  memorySize: 640,
  initialPartitions: [160, 240, 240],
  operations: [
    { type: "ALLOC" as const, process: "P1", size: 100 },
    { type: "ALLOC" as const, process: "P2", size: 200 },
    { type: "FREE" as const, process: "P1" },
    { type: "ALLOC" as const, process: "P3", size: 150 },
    { type: "ALLOC" as const, process: "P4", size: 400 },
  ],
};

const diskInput = {
  head: 53,
  requests: [98, 183, 37, 122, 14, 124, 65, 67],
  direction: "UP" as const,
};

export const ALGORITHMS: AlgorithmDefinition[] = [
  {
    key: "cpu.fcfs",
    resourceCode: "demo.cpu.fcfs",
    title: "CPU 先来先服务（FCFS）",
    shortTitle: "FCFS",
    category: "cpu",
    nodeCode: "os.cpu.fcfs",
    description: "按到达顺序依次运行，非抢占。",
    rules: ["非抢占；进程一旦开始运行直到结束。", "同时到达按输入顺序。"],
    challengeMode: "SERVER_VERIFIED_WHEN_ADAPTED",
    defaultInput: { processes, quantum: 2 },
  },
  {
    key: "cpu.sjf",
    resourceCode: "demo.cpu.sjf",
    title: "短作业优先（SJF）",
    shortTitle: "SJF",
    category: "cpu",
    nodeCode: "os.cpu.sjf",
    description: "从已到达进程中选择运行时间最短者，非抢占。",
    rules: [
      "非抢占；每次在当前已到达进程中选择 burst 最短者。",
      "burst 相同取先到达者，仍相同取输入顺序。",
    ],
    challengeMode: "SERVER_VERIFIED_WHEN_ADAPTED",
    defaultInput: { processes, quantum: 2 },
  },
  {
    key: "cpu.hrrn",
    resourceCode: "demo.cpu.hrrn",
    title: "高响应比优先（HRRN）",
    shortTitle: "HRRN",
    category: "cpu",
    nodeCode: "os.cpu.hrrn",
    description: "按响应比选择进程，兼顾等待时间与服务时间。",
    rules: [
      "响应比 =（等待时间 + 服务时间）/ 服务时间；非抢占。",
      "响应比相同取先到达者，仍相同取输入顺序。",
    ],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    defaultInput: { processes, quantum: 2 },
  },
  {
    key: "cpu.priority_nonpreemptive",
    resourceCode: "demo.cpu.priority_nonpreemptive",
    title: "非抢占优先级调度",
    shortTitle: "非抢占优先级",
    category: "cpu",
    nodeCode: "os.cpu.priority_nonpreemptive",
    description: "按优先级选择已到达进程，进程开始后不被打断。",
    rules: [
      "优先级数字越小越高；非抢占。",
      "优先级相同取先到达者，仍相同取输入顺序。",
    ],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    requiresPriority: true,
    defaultInput: { processes, quantum: 2 },
  },
  {
    key: "cpu.priority_preemptive",
    resourceCode: "demo.cpu.priority_preemptive",
    title: "抢占优先级调度",
    shortTitle: "抢占优先级",
    category: "cpu",
    nodeCode: "os.cpu.priority_preemptive",
    description: "更高优先级进程到达时立即抢占当前进程。",
    rules: [
      "优先级数字越小越高；新到进程优先级更高时立即抢占。",
      "优先级相同不抢占；每次在已到达进程中取最高优先级。",
    ],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    requiresPriority: true,
    defaultInput: { processes, quantum: 2 },
  },
  {
    key: "cpu.rr",
    resourceCode: "demo.cpu.rr",
    title: "时间片轮转（RR）",
    shortTitle: "RR",
    category: "cpu",
    nodeCode: "os.cpu.rr",
    description: "按到达顺序轮转，每个进程最多运行一个时间片。",
    rules: [
      "时间片用尽后，先入队当前时刻已到达的新进程，再重新入队当前进程。",
      "同时到达按输入顺序。",
    ],
    challengeMode: "SERVER_VERIFIED_WHEN_ADAPTED",
    usesQuantum: true,
    defaultInput: { processes, quantum: 2 },
  },
  {
    key: "cpu.mlfq",
    resourceCode: "demo.cpu.mlfq",
    title: "多级反馈队列（MLFQ）",
    shortTitle: "MLFQ",
    category: "cpu",
    nodeCode: "os.cpu.mlfq",
    description: "2–6 级反馈队列，每级时间片可配置。",
    rules: [
      "队列数 2–6，每级时间片独立配置；新进程进入 Q0。",
      "高队列进程到达时立即抢占低队列；同队列内轮转。",
      "用完时间片降一级，最低队列不再降级；被抢占进程回到原队列队首。",
    ],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    usesQuantum: true,
    defaultInput: { processes, quantum: 1, mlfqQueues: [1, 2, 4] },
  },
  {
    key: "allocation.first_fit",
    resourceCode: "demo.allocation.first_fit",
    title: "首次适应（First Fit）",
    shortTitle: "首次适应",
    category: "allocation",
    nodeCode: "os.memory.first_fit",
    description: "从低地址开始选择第一个足够大的空闲区。",
    rules: [
      "按地址递增查找第一个足够大的空闲区。",
      "分配后剩余部分保留为空闲区；初始可有多块空闲分区。",
    ],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    defaultInput: allocationInput,
  },
  {
    key: "allocation.best_fit",
    resourceCode: "demo.allocation.best_fit",
    title: "最佳适应（Best Fit）",
    shortTitle: "最佳适应",
    category: "allocation",
    nodeCode: "os.memory.best_fit",
    description: "选择能满足请求的最小空闲区，保留大块空间。",
    rules: [
      "选择不小于请求的最小空闲区；并列取地址较低者。",
      "容易留下小碎片。",
    ],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    defaultInput: allocationInput,
  },
  {
    key: "allocation.worst_fit",
    resourceCode: "demo.allocation.worst_fit",
    title: "最坏适应（Worst Fit）",
    shortTitle: "最坏适应",
    category: "allocation",
    nodeCode: "os.memory.worst_fit",
    description: "选择最大的空闲区，尽量保留大块可用空间。",
    rules: ["选择最大的空闲区；并列取地址较低者。", "剩余空闲区仍可能足够大。"],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    defaultInput: allocationInput,
  },
  {
    key: "allocation.next_fit",
    resourceCode: "demo.allocation.next_fit",
    title: "邻近适应（Next Fit）",
    shortTitle: "邻近适应",
    category: "allocation",
    nodeCode: "os.memory.next_fit",
    description: "从上次分配结束位置向后循环查找空闲区。",
    rules: [
      "从上次分配结束位置向后查找，到达末尾后回到低地址继续。",
      "回收后合并相邻空闲区。",
    ],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    defaultInput: allocationInput,
  },
  {
    key: "page.fifo",
    resourceCode: "demo.page.fifo",
    title: "FIFO 页面置换",
    shortTitle: "FIFO",
    category: "page",
    nodeCode: "os.memory.fifo",
    description: "缺页时淘汰最早进入驻留队列的页。",
    rules: ["缺页时淘汰队首页；命中不改变驻留顺序。"],
    challengeMode: "SERVER_VERIFIED_WHEN_ADAPTED",
    defaultInput: pageInput,
  },
  {
    key: "page.lru",
    resourceCode: "demo.page.lru",
    title: "LRU 页面置换",
    shortTitle: "LRU",
    category: "page",
    nodeCode: "os.memory.lru",
    description: "缺页时淘汰最近最久未使用的页。",
    rules: ["命中时把该页移到最近使用端；缺页时淘汰队首页。"],
    challengeMode: "SERVER_VERIFIED_WHEN_ADAPTED",
    defaultInput: pageInput,
  },
  {
    key: "page.opt",
    resourceCode: "demo.page.opt",
    title: "OPT 页面置换",
    shortTitle: "OPT",
    category: "page",
    nodeCode: "os.memory.opt",
    description: "淘汰未来最久不再访问的页，用于对比理论最优。",
    rules: [
      "淘汰未来最久不再访问的页；并列时淘汰驻留序最靠前者。",
      "OPT 仅用于对比，不写测评掌握事件。",
    ],
    challengeMode: "PROGRESS_ONLY",
    defaultInput: pageInput,
  },
  {
    key: "disk.fcfs",
    resourceCode: "demo.disk.fcfs",
    title: "磁盘先来先服务（FCFS）",
    shortTitle: "磁盘 FCFS",
    category: "disk",
    nodeCode: "os.disk.fcfs",
    description: "按请求顺序移动磁头。",
    rules: ["按请求到达顺序服务，不重排。", "寻道长度 = 相邻磁头位置差之和。"],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    defaultInput: diskInput,
  },
  {
    key: "disk.sstf",
    resourceCode: "demo.disk.sstf",
    title: "最短寻道时间优先（SSTF）",
    shortTitle: "SSTF",
    category: "disk",
    nodeCode: "os.disk.sstf",
    description: "每次选择距当前磁头最近的请求。",
    rules: ["每次选择距当前磁头最近的请求；并列取较小磁道。", "可能产生饥饿。"],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    defaultInput: diskInput,
  },
  {
    key: "disk.scan",
    resourceCode: "demo.disk.scan",
    title: "磁盘扫描（SCAN）",
    shortTitle: "SCAN",
    category: "disk",
    nodeCode: "os.disk.scan",
    description: "沿当前方向服务到磁盘端点后反向。",
    rules: [
      "沿当前方向服务请求，到达磁盘端点后反向。",
      "端点移动计入寻道长度；磁道范围 1–200。",
    ],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    defaultInput: diskInput,
  },
  {
    key: "disk.cscan",
    resourceCode: "demo.disk.cscan",
    title: "磁盘循环扫描（C-SCAN）",
    shortTitle: "C-SCAN",
    category: "disk",
    nodeCode: "os.disk.cscan",
    description: "只沿当前方向服务，到端点后回跳到另一端。",
    rules: [
      "沿当前方向服务请求，到达磁盘端点后回跳到另一端继续同方向服务。",
      "回跳距离不计入寻道长度；磁道范围 1–200。",
    ],
    challengeMode: "PROGRESS_ONLY_UNTIL_VERIFIED",
    defaultInput: diskInput,
  },
];

// 授权演示资源中的算法思想/特点整理；解析面板与规则分开展示。
const ANALYSIS_BY_KEY: Record<string, string[]> = {
  "cpu.fcfs": [
    "算法思想：按进程到达就绪队列的先后顺序分配 CPU，进程一旦开始运行就直到结束，属于非抢占式调度。",
    "特点：实现简单、公平，不会产生饥饿；但对短作业不利，长作业在前时平均等待时间可能明显偏大。",
  ],
  "cpu.sjf": [
    "算法思想：每次从已到达进程中选择运行时间最短的进程投入运行，属于非抢占式调度。",
    "特点：在非抢占场景下平均等待时间最优；需要预先知道运行时间，长作业可能长期得不到调度（饥饿）。",
  ],
  "cpu.hrrn": [
    "算法思想：响应比 =（等待时间 + 服务时间）/ 服务时间，每次选择响应比最高的进程。",
    "特点：兼顾短作业和等待较久的长作业，避免长作业饥饿；需要动态计算响应比，且仍是非抢占式。",
  ],
  "cpu.priority_nonpreemptive": [
    "算法思想：为每个进程设置优先级，每次从已到达进程中选优先级最高者运行，开始后不被打断。",
    "特点：适合区分紧急任务；低优先级进程在持续有高优先级任务时可能饥饿，优先级相同按到达顺序。",
  ],
  "cpu.priority_preemptive": [
    "算法思想：始终选择已到达进程中优先级最高者运行；更高优先级进程到达时立即抢占当前进程。",
    "特点：响应更及时，适合实时/交互场景；抢占带来额外上下文切换开销，低优先级进程可能饥饿。",
  ],
  "cpu.rr": [
    "算法思想：就绪进程按到达顺序排队，每个进程最多运行一个时间片；时间片用完即重新入队。",
    "特点：响应时间公平、适合分时系统；时间片过大退化为 FCFS，时间片过小则上下文切换开销增大。",
  ],
  "cpu.mlfq": [
    "算法思想：设置多个优先级递减的就绪队列，各级时间片递增；新进程进入最高级队列，用完时间片降级。",
    "特点：兼顾响应速度与吞吐量，无需预知运行时间；低优先级队列可能饥饿，实际系统常用老化提升优先级。",
  ],
  "allocation.first_fit": [
    "算法思想：从低地址开始查找，选择第一个容量足够的空闲分区进行分配。",
    "特点：查找简单、分配速度快，倾向使用低地址空间；低地址会积累大量小碎片，增加后续查找成本。",
  ],
  "allocation.best_fit": [
    "算法思想：在所有足够大的空闲分区中选择容量最小的一块，使剩余空间尽量小。",
    "特点：尽量保留大块空闲区，适合后续大请求；但容易产生大量难以利用的小碎片。",
  ],
  "allocation.worst_fit": [
    "算法思想：每次选择最大的空闲分区进行分配，使分配后的剩余块仍然较大。",
    "特点：剩余分区较大、便于继续分配；但会快速消耗大块空间，碎片化可能更严重。",
  ],
  "allocation.next_fit": [
    "算法思想：从上一次分配结束的位置开始向后查找，到达末尾后回到低地址继续循环。",
    "特点：查找开销小、空闲区使用更均匀；可能跳过前面更合适的空闲区，整体碎片情况介于首次适应之间。",
  ],
  "page.fifo": [
    "算法思想：为每个页面记录进入内存的先后顺序，缺页时淘汰最早进入的页面。",
    "特点：实现简单、开销小；与进程实际访问规律不匹配，可能出现 Belady 异常（物理块增加而缺页率反而上升）。",
  ],
  "page.lru": [
    "算法思想：记录页面最近一次被访问的时间，缺页时淘汰最长时间未被访问的页面。",
    "特点：利用局部性原理，命中率通常优于 FIFO；需要维护访问顺序，硬件/软件开销较大。",
  ],
  "page.opt": [
    "算法思想：缺页时选择未来最长时间不再被访问的页面淘汰，需要预知完整访问序列。",
    "特点：缺页率最低，是理论最优下界和对比基准；实际系统无法预知未来访问，因此不可实现。",
  ],
  "disk.fcfs": [
    "算法思想：按请求到达的先后顺序移动磁头，不进行重排。",
    "特点：公平、实现简单；磁头可能来回大跨度移动，平均寻道长度较大。",
  ],
  "disk.sstf": [
    "算法思想：每次选择距离当前磁头最近的请求服务。",
    "特点：平均寻道长度较短、响应较快；远处请求可能长期得不到服务（饥饿）。",
  ],
  "disk.scan": [
    "算法思想：沿当前方向依次服务请求，到达磁盘端点后反向，像电梯一样往返扫描。",
    "特点：兼顾吞吐与公平，避免 SSTF 的饥饿；端点移动计入寻道长度，中间区域请求响应更及时。",
  ],
  "disk.cscan": [
    "算法思想：只沿一个方向扫描，到达端点后直接回跳到另一端，再继续同方向服务。",
    "特点：各磁道请求等待时间更均匀，消除端点偏爱；回跳距离不计入寻道长度，端点附近请求等待较久。",
  ],
};

for (const algorithm of ALGORITHMS) {
  algorithm.analysis = ANALYSIS_BY_KEY[algorithm.key] || [
    algorithm.description,
    ...algorithm.rules,
  ];
}

export const ALGORITHM_BY_KEY = new Map(
  ALGORITHMS.map((algorithm) => [algorithm.key, algorithm])
);

export const DEFAULT_ALGORITHM_KEY = "cpu.fcfs";

const LEGACY_ALGORITHM_KEYS: Record<string, string> = {
  FCFS: "cpu.fcfs",
  SJF: "cpu.sjf",
  HRRN: "cpu.hrrn",
  PRIORITY: "cpu.priority_nonpreemptive",
  PRIORITY_PREEMPTIVE: "cpu.priority_preemptive",
  RR: "cpu.rr",
  MLFQ: "cpu.mlfq",
  FIFO: "page.fifo",
  LRU: "page.lru",
  OPT: "page.opt",
};

const NODE_ALGORITHM_KEYS: Record<string, string> = Object.fromEntries(
  ALGORITHMS.map((algorithm) => [algorithm.nodeCode, algorithm.key])
);

export function algorithmKeyFromLegacy(value: string, mode?: string): string {
  const raw = value.trim();
  if (ALGORITHM_BY_KEY.has(raw.toLowerCase())) return raw.toLowerCase();
  const normalized = raw.toUpperCase();
  if (LEGACY_ALGORITHM_KEYS[normalized])
    return LEGACY_ALGORITHM_KEYS[normalized];
  if (normalized === "PAGE" || mode === "page") return "page.fifo";
  if (normalized === "CPU" || mode === "cpu") return "cpu.fcfs";
  return "";
}

export function algorithmKeyFromNodeCode(nodeCode?: string): string {
  return nodeCode ? NODE_ALGORITHM_KEYS[nodeCode] || "" : "";
}

export function algorithmsByCategory(
  category: AlgorithmCategory
): AlgorithmDefinition[] {
  return ALGORITHMS.filter((algorithm) => algorithm.category === category);
}

// ---------- 深链输入契约（四类算法） ----------

export interface DemoQueryParse {
  /** 查询串中是否出现了本类别的输入参数 */
  hasInput: boolean;
  input?: DemoInput;
  /** 参数存在但无法解析/越界时的明确错误；此时不得运行 */
  error?: string;
}

export function queryValue(value: unknown): string {
  if (Array.isArray(value)) return value.length ? String(value[0] ?? "") : "";
  if (typeof value === "string") return value;
  if (typeof value === "number" && Number.isFinite(value)) return String(value);
  return "";
}

function parseJson(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    return undefined;
  }
}

const asRecord = (value: unknown): Record<string, unknown> | null =>
  typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;

function parseNumberList(raw: string): number[] {
  const text = raw.trim();
  if (!text) return [];
  if (text.startsWith("[")) {
    const parsed = parseJson(text);
    return Array.isArray(parsed) ? parsed.map((value) => Number(value)) : [];
  }
  return text
    .split(/[,，\s]+/)
    .map((value) => value.trim())
    .filter(Boolean)
    .map(Number);
}

const PROCESS_NAME = /^[A-Za-z0-9_-]{1,32}$/;

function parseCpuQuery(get: (key: string) => string): DemoQueryParse {
  const processesText = get("processes");
  const quantumText = get("timeSlice") || get("quantum");
  if (!processesText && !quantumText) return { hasInput: false };
  if (!processesText) {
    return { hasInput: true, error: "CPU 深链缺少 processes 参数" };
  }
  const parsed = parseJson(processesText);
  if (!Array.isArray(parsed) || parsed.length === 0) {
    return { hasInput: true, error: "processes 需为非空 JSON 数组" };
  }
  if (parsed.length > 10) {
    return { hasInput: true, error: "processes 最多 10 个进程" };
  }
  const names = new Set<string>();
  const processes: CpuProcessInput[] = [];
  for (let index = 0; index < parsed.length; index += 1) {
    const item = asRecord(parsed[index]);
    if (!item) {
      return {
        hasInput: true,
        error: `processes 第 ${index + 1} 项需为对象`,
      };
    }
    const name = String(item.name ?? "").trim();
    if (!name) {
      return {
        hasInput: true,
        error: `processes 第 ${index + 1} 项缺少进程名`,
      };
    }
    if (names.has(name)) {
      return { hasInput: true, error: `进程名重复：${name}` };
    }
    names.add(name);
    const arrival = Number(item.arrivalTime ?? item.arrival);
    const burst = Number(item.burstTime ?? item.burst);
    const priority =
      item.priority === undefined || item.priority === null
        ? 1
        : Number(item.priority);
    if (!Number.isInteger(arrival) || arrival < 0 || arrival > 999) {
      return {
        hasInput: true,
        error: `进程 ${name} 的到达时间需为 0–999 的整数`,
      };
    }
    if (!Number.isInteger(burst) || burst < 1 || burst > 100) {
      return {
        hasInput: true,
        error: `进程 ${name} 的运行时间需为 1–100 的整数`,
      };
    }
    if (!Number.isInteger(priority) || priority < 0 || priority > 99) {
      return {
        hasInput: true,
        error: `进程 ${name} 的优先级需为 0–99 的整数`,
      };
    }
    processes.push({ name, arrival, burst, priority });
  }
  let quantum = 2;
  if (quantumText) {
    quantum = Number(quantumText);
    if (!Number.isInteger(quantum) || quantum < 1 || quantum > 20) {
      return { hasInput: true, error: "时间片需为 1–20 的整数" };
    }
  }
  let mlfqQueues: number[] | undefined;
  const queueText = get("queueSlices") || get("mlfqQueues");
  if (queueText) {
    mlfqQueues = parseNumberList(queueText);
    if (mlfqQueues.length < 2 || mlfqQueues.length > 6) {
      return { hasInput: true, error: "queueSlices 需为 2–6 个时间片" };
    }
    if (
      mlfqQueues.some(
        (slice) => !Number.isInteger(slice) || slice < 1 || slice > 20
      )
    ) {
      return { hasInput: true, error: "queueSlices 每级需为 1–20 的整数" };
    }
  }
  return {
    hasInput: true,
    input: mlfqQueues
      ? { processes, quantum: mlfqQueues[0], mlfqQueues }
      : { processes, quantum },
  };
}

function parsePageQuery(get: (key: string) => string): DemoQueryParse {
  const framesText = get("frames");
  const sequenceText = get("accessSequence") || get("sequence");
  if (!framesText && !sequenceText) return { hasInput: false };
  if (!sequenceText) {
    return { hasInput: true, error: "页面置换深链缺少 accessSequence 参数" };
  }
  const sequence = parseNumberList(sequenceText);
  if (!sequence.length) {
    return { hasInput: true, error: "accessSequence 需为非空页号数组" };
  }
  if (sequence.length > 200) {
    return { hasInput: true, error: "accessSequence 最多 200 步" };
  }
  if (
    sequence.some((page) => !Number.isInteger(page) || page < 0 || page > 9999)
  ) {
    return { hasInput: true, error: "页号需为 0–9999 的整数" };
  }
  let frames = 3;
  if (framesText) {
    frames = Number(framesText);
    if (!Number.isInteger(frames) || frames < 1 || frames > 60) {
      return { hasInput: true, error: "frames 需为 1–60 的整数" };
    }
  }
  return { hasInput: true, input: { frames, sequence } };
}

function parseAllocationOperations(
  raw: string,
  memorySize: number
): { operations?: AllocationOperation[]; error?: string } {
  const operations: AllocationOperation[] = [];
  const text = raw.trim();
  if (!text) return { error: "operations 不能为空" };
  if (text.startsWith("[")) {
    const parsed = parseJson(text);
    if (!Array.isArray(parsed)) {
      return { error: "operations 需为 JSON 数组或逐行文本" };
    }
    if (!parsed.length) return { error: "operations 不能为空" };
    if (parsed.length > 30) return { error: "operations 最多 30 条" };
    for (let index = 0; index < parsed.length; index += 1) {
      const item = asRecord(parsed[index]);
      if (!item) {
        return { error: `operations 第 ${index + 1} 项需为对象` };
      }
      const type = String(item.type ?? "").toUpperCase();
      const process = String(item.process ?? "").trim();
      if (type !== "ALLOC" && type !== "FREE") {
        return {
          error: `operations 第 ${index + 1} 项 type 需为 ALLOC 或 FREE`,
        };
      }
      if (!PROCESS_NAME.test(process)) {
        return {
          error: `operations 第 ${
            index + 1
          } 项进程名需为 1–32 位字母、数字、下划线或短横线`,
        };
      }
      if (type === "FREE") {
        operations.push({ type: "FREE", process });
        continue;
      }
      const size = Number(item.size);
      if (!Number.isInteger(size) || size < 1 || size > memorySize) {
        return {
          error: `operations 第 ${
            index + 1
          } 项分配大小需为 1–${memorySize} 的整数`,
        };
      }
      operations.push({ type: "ALLOC", process, size });
    }
    return { operations };
  }
  for (const rawLine of text.split(/\r?\n|;/)) {
    const line = rawLine.replace(/#.*$/, "").trim();
    if (!line) continue;
    if (operations.length >= 30) return { error: "operations 最多 30 条" };
    const parts = line.split(/\s+/);
    if (parts[0].toUpperCase() === "FREE") {
      if (parts.length !== 2 || !PROCESS_NAME.test(parts[1])) {
        return { error: `无法解析回收操作：${line}` };
      }
      operations.push({ type: "FREE", process: parts[1] });
      continue;
    }
    const values =
      parts[0].toUpperCase() === "ALLOC" ? parts.slice(1) : parts.slice();
    if (values.length !== 2 || !PROCESS_NAME.test(values[0])) {
      return { error: `无法解析分配操作：${line}` };
    }
    const size = Number(values[1]);
    if (!Number.isInteger(size) || size < 1 || size > memorySize) {
      return {
        error: `分配大小需为 1–${memorySize} 的整数：${line}`,
      };
    }
    operations.push({ type: "ALLOC", process: values[0], size });
  }
  if (!operations.length) return { error: "operations 不能为空" };
  return { operations };
}

function parseAllocationQuery(get: (key: string) => string): DemoQueryParse {
  const memoryText = get("memorySize");
  const operationsText = get("operations");
  if (!memoryText && !operationsText) return { hasInput: false };
  if (!memoryText) {
    return { hasInput: true, error: "内存分配深链缺少 memorySize 参数" };
  }
  if (!operationsText) {
    return { hasInput: true, error: "内存分配深链缺少 operations 参数" };
  }
  const memorySize = Number(memoryText);
  if (!Number.isInteger(memorySize) || memorySize < 16 || memorySize > 4096) {
    return { hasInput: true, error: "memorySize 需为 16–4096 的整数" };
  }
  const parsed = parseAllocationOperations(operationsText, memorySize);
  if (parsed.error) return { hasInput: true, error: parsed.error };
  let initialPartitions: number[] | undefined;
  const partitionsText = get("initialPartitions");
  if (partitionsText) {
    initialPartitions = parseNumberList(partitionsText);
    if (
      initialPartitions.length < 1 ||
      initialPartitions.length > 12 ||
      initialPartitions.some(
        (size) => !Number.isInteger(size) || size < 1 || size > 4096
      )
    ) {
      return {
        hasInput: true,
        error: "initialPartitions 需为 1–12 个 1–4096 的整数",
      };
    }
    const total = initialPartitions.reduce((sum, size) => sum + size, 0);
    if (total !== memorySize) {
      return {
        hasInput: true,
        error: "initialPartitions 总大小需等于 memorySize",
      };
    }
  }
  return {
    hasInput: true,
    input: initialPartitions
      ? { memorySize, initialPartitions, operations: parsed.operations || [] }
      : { memorySize, operations: parsed.operations || [] },
  };
}

function parseDiskQuery(get: (key: string) => string): DemoQueryParse {
  const headText = get("head");
  const requestsText = get("requests");
  const directionText = get("direction");
  if (!headText && !requestsText && !directionText) {
    return { hasInput: false };
  }
  if (!headText) {
    return { hasInput: true, error: "磁盘调度深链缺少 head 参数" };
  }
  if (!requestsText) {
    return { hasInput: true, error: "磁盘调度深链缺少 requests 参数" };
  }
  if (!directionText) {
    return { hasInput: true, error: "磁盘调度深链缺少 direction 参数" };
  }
  const head = Number(headText);
  if (!Number.isInteger(head) || head < 1 || head > 200) {
    return { hasInput: true, error: "head 需为 1–200 的整数" };
  }
  const requests = parseNumberList(requestsText);
  if (!requests.length) {
    return { hasInput: true, error: "requests 需为非空磁道数组" };
  }
  if (requests.length > 20) {
    return { hasInput: true, error: "requests 最多 20 个磁道" };
  }
  if (
    requests.some(
      (track) => !Number.isInteger(track) || track < 1 || track > 200
    )
  ) {
    return { hasInput: true, error: "磁道号需为 1–200 的整数" };
  }
  const direction = directionText.trim().toUpperCase();
  if (direction !== "UP" && direction !== "DOWN") {
    return { hasInput: true, error: "direction 只能为 UP 或 DOWN" };
  }
  return {
    hasInput: true,
    input: {
      head,
      requests,
      direction: direction as "UP" | "DOWN",
    },
  };
}

/**
 * 解析四类算法的深链输入。只解析参数，不生成步骤/指标。
 * 参数缺失时返回 hasInput=false，由播放器使用可编辑示例输入。
 */
export function parseDemoQuery(
  category: AlgorithmCategory,
  query: Record<string, unknown>
): DemoQueryParse {
  const get = (key: string) => queryValue(query[key]);
  if (category === "cpu") return parseCpuQuery(get);
  if (category === "allocation") return parseAllocationQuery(get);
  if (category === "page") return parsePageQuery(get);
  return parseDiskQuery(get);
}
