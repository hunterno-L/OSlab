import type {
  CpuInput,
  CpuProcessInput,
  CpuSegment,
  DemoMetric,
  DemoResult,
  DemoStep,
} from "./types";

const MAX_PROCESSES = 10;
const MAX_BURST = 100;

export function validateCpuInput(
  input: CpuInput,
  requiresPriority = false,
  usesQuantum = false
): string {
  if (!Array.isArray(input.processes) || input.processes.length === 0) {
    return "请至少添加一个进程";
  }
  if (input.processes.length > MAX_PROCESSES) {
    return `进程数最多 ${MAX_PROCESSES} 个`;
  }
  const names = new Set<string>();
  for (const process of input.processes) {
    const name = String(process.name || "").trim();
    if (!name) return "每个进程都需要非空进程名";
    if (names.has(name)) return `进程名重复：${name}`;
    names.add(name);
    if (
      !Number.isInteger(process.arrival) ||
      process.arrival < 0 ||
      process.arrival > 999
    ) {
      return `进程 ${name} 的到达时间需为 0–999 的整数`;
    }
    if (
      !Number.isInteger(process.burst) ||
      process.burst < 1 ||
      process.burst > MAX_BURST
    ) {
      return `进程 ${name} 的运行时间需为 1–${MAX_BURST} 的整数`;
    }
    if (
      requiresPriority &&
      (!Number.isInteger(process.priority) ||
        (process.priority as number) < 0 ||
        (process.priority as number) > 99)
    ) {
      return `进程 ${name} 的优先级需为 0–99 的整数（数字越小优先级越高）`;
    }
  }
  if (usesQuantum) {
    if (
      !Number.isInteger(input.quantum) ||
      (input.quantum as number) < 1 ||
      (input.quantum as number) > 20
    ) {
      return "时间片需为 1–20 的整数";
    }
  }
  if (input.mlfqQueues !== undefined) {
    if (
      !Array.isArray(input.mlfqQueues) ||
      input.mlfqQueues.length < 2 ||
      input.mlfqQueues.length > 6
    ) {
      return "多级反馈队列的队列数需为 2–6";
    }
    for (let index = 0; index < input.mlfqQueues.length; index += 1) {
      const slice = input.mlfqQueues[index];
      if (!Number.isInteger(slice) || slice < 1 || slice > 20) {
        return `第 ${index + 1} 级队列时间片需为 1–20 的整数`;
      }
    }
  }
  return "";
}

interface Slice {
  name: string;
  start: number;
  end: number;
  level?: number;
  readyAfter: string[];
  queuesAfter?: string[][];
  /** 队列事件提示：进入 / 降级 / 抢占 / 完成 */
  note?: string;
  /** 代码联动事件：cpu.idle/run/complete/preempt/demote/yield */
  eventKind?: string;
}

interface IndexedProcess extends CpuProcessInput {
  index: number;
}

const indexed = (input: CpuInput): IndexedProcess[] =>
  input.processes.map((process, index) => ({
    name: process.name.trim(),
    arrival: process.arrival,
    burst: process.burst,
    priority: process.priority,
    index,
  }));

const byArrivalThenInput = (a: IndexedProcess, b: IndexedProcess) =>
  a.arrival - b.arrival || a.index - b.index;

const byPriority = (a: IndexedProcess, b: IndexedProcess) =>
  (a.priority ?? 0) - (b.priority ?? 0) ||
  a.arrival - b.arrival ||
  a.index - b.index;

function buildCpuResult(
  input: CpuInput,
  slices: Slice[],
  timeline: CpuSegment[],
  notes: string[]
): DemoResult {
  const processes = indexed(input);
  const completion = new Map<string, number>();
  const firstStart = new Map<string, number>();
  for (const segment of timeline) {
    if (segment.name === "空闲") continue;
    completion.set(segment.name, segment.end);
    if (!firstStart.has(segment.name))
      firstStart.set(segment.name, segment.start);
  }
  // 真值定义（对非抢占与抢占调度一致）：
  //   周转时间 = 完成时刻 − 到达时刻
  //   等待时间 = 周转时间 − 运行时间（即所有处于就绪队列的时间）
  //   响应时间 = 首次开始运行时刻 − 到达时刻
  // 抢占式算法中 firstStart - arrival 只等于响应时间，不能当作等待时间。
  let waitSum = 0;
  let turnSum = 0;
  let responseSum = 0;
  for (const process of processes) {
    const end = completion.get(process.name) ?? process.arrival + process.burst;
    const start = firstStart.get(process.name) ?? process.arrival;
    const turnaround = Math.max(0, end - process.arrival);
    turnSum += turnaround;
    waitSum += Math.max(0, turnaround - process.burst);
    responseSum += Math.max(0, start - process.arrival);
  }
  const busy = processes.reduce((sum, process) => sum + process.burst, 0);
  const minArrival = Math.min(...processes.map((process) => process.arrival));
  const maxEnd = Math.max(...completion.values());
  const span = Math.max(1, maxEnd - minArrival);
  const runSegments = timeline.filter((segment) => segment.name !== "空闲");
  let contextSwitches = 0;
  for (let index = 1; index < runSegments.length; index += 1) {
    if (runSegments[index].name !== runSegments[index - 1].name) {
      contextSwitches += 1;
    }
  }
  const metrics: DemoMetric[] = [
    { label: "平均等待时间", value: (waitSum / processes.length).toFixed(2) },
    { label: "平均周转时间", value: (turnSum / processes.length).toFixed(2) },
    {
      label: "平均响应时间",
      value: (responseSum / processes.length).toFixed(2),
    },
    { label: "CPU 利用率", value: `${((busy / span) * 100).toFixed(1)}%` },
    {
      label: "上下文切换",
      value: String(contextSwitches),
    },
  ];
  const steps: DemoStep[] = slices.map((slice, index) => ({
    index: index + 1,
    title:
      slice.name === "空闲"
        ? `空闲 [${slice.start},${slice.end})`
        : `${slice.name} 运行 [${slice.start},${slice.end})`,
    detail:
      slice.name === "空闲"
        ? slice.note || "没有已到达进程，CPU 空闲。"
        : `${slice.note ? `${slice.note}；` : ""}就绪队列：${
            slice.readyAfter.length ? slice.readyAfter.join(" → ") : "空"
          }`,
    time: slice.end,
    tone: slice.name === "空闲" ? "warning" : "info",
    event: slice.eventKind
      ? {
          kind: slice.eventKind,
          objectId: slice.name === "空闲" ? undefined : slice.name,
          from: slice.level === undefined ? undefined : `Q${slice.level}`,
          to: slice.name === "空闲" ? undefined : slice.name,
          duration: Math.max(0, slice.end - slice.start),
        }
      : undefined,
    state: {
      time: slice.end,
      process: slice.name === "空闲" ? null : slice.name,
      start: slice.start,
      end: slice.end,
      ready: slice.readyAfter,
      queues: slice.queuesAfter,
      level: slice.level,
    },
  }));
  const hasQueueLevels = timeline.some(
    (segment) => segment.queueLevel !== undefined
  );
  const queueCount = Math.max(
    1,
    ...slices.map((slice) => slice.queuesAfter?.length || 0)
  );
  const initialReady = processes
    .filter((process) => process.arrival <= 0)
    .sort(byArrivalThenInput)
    .map((process) => process.name);
  return {
    steps,
    metrics,
    initialState: {
      time: 0,
      process: null,
      ready: initialReady,
      queues: hasQueueLevels
        ? Array.from({ length: queueCount }, (_, level) =>
            level === 0 ? initialReady : []
          )
        : undefined,
    },
    timeline,
    notes: [
      ...notes,
      "等待时间 = 完成 − 到达 − 运行；周转 = 完成 − 到达；响应 = 首次运行 − 到达。",
    ],
  };
}

function fcfs(input: CpuInput): DemoResult {
  const processes = indexed(input).sort(byArrivalThenInput);
  const slices: Slice[] = [];
  const timeline: CpuSegment[] = [];
  let time = 0;
  processes.forEach((process, position) => {
    const start = Math.max(time, process.arrival);
    if (start > time) {
      timeline.push({ name: "空闲", start: time, end: start });
      const idleReady = processes
        .slice(position)
        .filter((candidate) => candidate.arrival <= start)
        .map((candidate) => candidate.name);
      slices.push({
        name: "空闲",
        start: time,
        end: start,
        readyAfter: idleReady,
      });
    }
    time = start + process.burst;
    timeline.push({ name: process.name, start, end: time });
    slices.push({
      name: process.name,
      start,
      end: time,
      readyAfter: processes
        .slice(position + 1)
        .filter((candidate) => candidate.arrival <= time)
        .map((candidate) => candidate.name),
    });
  });
  return buildCpuResult(input, slices, timeline, [
    "FCFS 非抢占；同时到达按输入顺序。",
  ]);
}

function sjf(input: CpuInput): DemoResult {
  const processes = indexed(input).sort(byArrivalThenInput);
  const done = new Set<number>();
  const slices: Slice[] = [];
  const timeline: CpuSegment[] = [];
  let time = 0;
  while (done.size < processes.length) {
    const candidates = processes.filter(
      (process) => !done.has(process.index) && process.arrival <= time
    );
    if (!candidates.length) {
      const nextArrival = Math.min(
        ...processes
          .filter((process) => !done.has(process.index))
          .map((process) => process.arrival)
      );
      if (nextArrival > time) {
        timeline.push({ name: "空闲", start: time, end: nextArrival });
        slices.push({
          name: "空闲",
          start: time,
          end: nextArrival,
          readyAfter: [],
        });
        time = nextArrival;
        continue;
      }
    }
    const pick = candidates.sort(
      (a, b) => a.burst - b.burst || a.arrival - b.arrival || a.index - b.index
    )[0];
    const start = time;
    time += pick.burst;
    done.add(pick.index);
    timeline.push({ name: pick.name, start, end: time });
    slices.push({
      name: pick.name,
      start,
      end: time,
      readyAfter: processes
        .filter(
          (process) =>
            !done.has(process.index) &&
            process.arrival <= time &&
            process.name !== pick.name
        )
        .sort(byArrivalThenInput)
        .map((process) => process.name),
    });
  }
  return buildCpuResult(input, slices, timeline, [
    "SJF 非抢占；burst 相同取先到达者，仍相同取输入顺序。",
  ]);
}

function hrrn(input: CpuInput): DemoResult {
  const processes = indexed(input).sort(byArrivalThenInput);
  const done = new Set<number>();
  const slices: Slice[] = [];
  const timeline: CpuSegment[] = [];
  let time = 0;
  while (done.size < processes.length) {
    let candidates = processes.filter(
      (process) => !done.has(process.index) && process.arrival <= time
    );
    if (!candidates.length) {
      const nextArrival = Math.min(
        ...processes
          .filter((process) => !done.has(process.index))
          .map((process) => process.arrival)
      );
      timeline.push({ name: "空闲", start: time, end: nextArrival });
      slices.push({
        name: "空闲",
        start: time,
        end: nextArrival,
        readyAfter: [],
      });
      time = nextArrival;
      continue;
    }
    candidates = candidates.slice().sort((a, b) => {
      const ratioA = (time - a.arrival + a.burst) / a.burst;
      const ratioB = (time - b.arrival + b.burst) / b.burst;
      return ratioB - ratioA || a.arrival - b.arrival || a.index - b.index;
    });
    const pick = candidates[0];
    const start = time;
    time += pick.burst;
    done.add(pick.index);
    timeline.push({ name: pick.name, start, end: time });
    slices.push({
      name: pick.name,
      start,
      end: time,
      readyAfter: processes
        .filter(
          (process) =>
            !done.has(process.index) &&
            process.arrival <= time &&
            process.name !== pick.name
        )
        .sort(byArrivalThenInput)
        .map((process) => process.name),
    });
  }
  return buildCpuResult(input, slices, timeline, [
    "响应比 =（等待时间 + 服务时间）/ 服务时间；非抢占。",
    "响应比相同取先到达者，仍相同取输入顺序。",
  ]);
}

function priorityNonPreemptive(input: CpuInput): DemoResult {
  const processes = indexed(input).sort(byArrivalThenInput);
  const done = new Set<number>();
  const slices: Slice[] = [];
  const timeline: CpuSegment[] = [];
  let time = 0;
  while (done.size < processes.length) {
    const candidates = processes.filter(
      (process) => !done.has(process.index) && process.arrival <= time
    );
    if (!candidates.length) {
      const nextArrival = Math.min(
        ...processes
          .filter((process) => !done.has(process.index))
          .map((process) => process.arrival)
      );
      timeline.push({ name: "空闲", start: time, end: nextArrival });
      slices.push({
        name: "空闲",
        start: time,
        end: nextArrival,
        readyAfter: [],
      });
      time = nextArrival;
      continue;
    }
    const pick = candidates.slice().sort(byPriority)[0];
    const start = time;
    time += pick.burst;
    done.add(pick.index);
    timeline.push({ name: pick.name, start, end: time });
    slices.push({
      name: pick.name,
      start,
      end: time,
      readyAfter: processes
        .filter(
          (process) =>
            !done.has(process.index) &&
            process.arrival <= time &&
            process.name !== pick.name
        )
        .sort(byArrivalThenInput)
        .map((process) => process.name),
    });
  }
  return buildCpuResult(input, slices, timeline, [
    "优先级数字越小越高；非抢占；优先级相同取先到达者，仍相同取输入顺序。",
  ]);
}

function priorityPreemptive(input: CpuInput): DemoResult {
  const processes = indexed(input).sort(byArrivalThenInput);
  const remaining = new Map<number, number>();
  processes.forEach((process) => remaining.set(process.index, process.burst));
  const ownerAt: Array<number | null> = [];
  let time = 0;
  let doneCount = 0;
  while (doneCount < processes.length) {
    const candidates = processes
      .filter(
        (process) =>
          (remaining.get(process.index) ?? 0) > 0 && process.arrival <= time
      )
      .sort(byPriority);
    if (!candidates.length) {
      ownerAt.push(null);
      time += 1;
      continue;
    }
    const pick = candidates[0];
    ownerAt.push(pick.index);
    const left = (remaining.get(pick.index) ?? 0) - 1;
    remaining.set(pick.index, left);
    if (left === 0) doneCount += 1;
    time += 1;
  }
  // 每个进程最后一次出现的时刻即完成时刻
  const finishAt = new Map<number, number>();
  ownerAt.forEach((owner, unit) => {
    if (owner !== null) finishAt.set(owner, unit + 1);
  });
  const slices: Slice[] = [];
  const timeline: CpuSegment[] = [];
  let cursor = 0;
  while (cursor < ownerAt.length) {
    const owner = ownerAt[cursor];
    let end = cursor + 1;
    while (end < ownerAt.length && ownerAt[end] === owner) end += 1;
    if (owner === null) {
      timeline.push({ name: "空闲", start: cursor, end });
      slices.push({
        name: "空闲",
        start: cursor,
        end,
        readyAfter: [],
      });
    } else {
      const process = processes.find(
        (candidate) => candidate.index === owner
      ) as IndexedProcess;
      timeline.push({ name: process.name, start: cursor, end });
      slices.push({
        name: process.name,
        start: cursor,
        end,
        readyAfter: processes
          .filter((candidate) => {
            const finish = finishAt.get(candidate.index);
            return (
              candidate.arrival <= end &&
              finish !== undefined &&
              finish > end &&
              candidate.name !== process.name
            );
          })
          .sort(byArrivalThenInput)
          .map((candidate) => candidate.name),
      });
    }
    cursor = end;
  }
  return buildCpuResult(input, slices, timeline, [
    "抢占式优先级：新到进程优先级更高（数字更小）时立即抢占；相同优先级不抢占。",
  ]);
}

function roundRobin(input: CpuInput): DemoResult {
  const quantum = input.quantum ?? 2;
  const processes = indexed(input).sort(byArrivalThenInput);
  const remaining = new Map<number, number>();
  processes.forEach((process) => remaining.set(process.index, process.burst));
  const queue: IndexedProcess[] = [];
  let nextArrival = 0;
  let time = 0;
  const slices: Slice[] = [];
  const timeline: CpuSegment[] = [];
  while (remaining.size > 0 || queue.length) {
    while (
      nextArrival < processes.length &&
      processes[nextArrival].arrival <= time
    ) {
      queue.push(processes[nextArrival]);
      nextArrival += 1;
    }
    if (!queue.length) {
      const jump = processes[nextArrival]?.arrival ?? time;
      if (jump > time) {
        timeline.push({ name: "空闲", start: time, end: jump });
        slices.push({
          name: "空闲",
          start: time,
          end: jump,
          readyAfter: [],
          eventKind: "cpu.idle",
        });
        time = jump;
      }
      continue;
    }
    const process = queue.shift() as IndexedProcess;
    const left = remaining.get(process.index) ?? 0;
    const run = Math.min(quantum, left);
    const start = time;
    time += run;
    remaining.set(process.index, left - run);
    // 规则：时间片用尽后先入队当前时刻已到达的新进程，再重新入队当前进程
    while (
      nextArrival < processes.length &&
      processes[nextArrival].arrival <= time
    ) {
      queue.push(processes[nextArrival]);
      nextArrival += 1;
    }
    if ((remaining.get(process.index) ?? 0) > 0 && process.arrival <= time) {
      queue.push(process);
    } else if ((remaining.get(process.index) ?? 0) === 0) {
      remaining.delete(process.index);
    }
    const finished = (remaining.get(process.index) ?? 0) === 0;
    timeline.push({
      name: process.name,
      start,
      end: time,
    });
    slices.push({
      name: process.name,
      start,
      end: time,
      readyAfter: queue.map((item) => item.name),
      eventKind: finished ? "cpu.complete" : "cpu.run",
    });
  }
  return buildCpuResult(input, slices, timeline, [
    `时间片 = ${quantum}；时间片用尽先入队新到达进程，再重新入队当前进程。`,
    "同时到达按输入顺序。",
  ]);
}

function mlfq(input: CpuInput): DemoResult {
  const baseQuantum = input.quantum ?? 1;
  const quanta =
    Array.isArray(input.mlfqQueues) && input.mlfqQueues.length >= 2
      ? input.mlfqQueues.slice(0, 6)
      : [baseQuantum, baseQuantum * 2, baseQuantum * 4];
  const queueCount = quanta.length;
  const processes = indexed(input).sort(byArrivalThenInput);
  const remaining = processes.map((process) => process.burst);
  const finished = new Set<number>();
  const queues: number[][] = Array.from({ length: queueCount }, () => []);
  let nextArrival = 0;
  let time = 0;
  const slices: Slice[] = [];
  const timeline: CpuSegment[] = [];

  const processOf = (index: number) =>
    processes.find((process) => process.index === index) as IndexedProcess;
  const addArrivals = (at: number) => {
    const added: string[] = [];
    while (
      nextArrival < processes.length &&
      processes[nextArrival].arrival <= at
    ) {
      queues[0].push(processes[nextArrival].index);
      added.push(processes[nextArrival].name);
      nextArrival += 1;
    }
    return added;
  };
  const queueNames = () =>
    queues.map((queue) => queue.map((index) => processOf(index).name));

  addArrivals(0);
  while (finished.size < processes.length) {
    const level = queues.findIndex((queue) => queue.length > 0);
    if (level < 0) {
      if (nextArrival >= processes.length) break;
      const jump = processes[nextArrival].arrival;
      timeline.push({ name: "空闲", start: time, end: jump });
      slices.push({
        name: "空闲",
        start: time,
        end: jump,
        readyAfter: [],
        queuesAfter: queueNames(),
        note: "没有就绪进程，CPU 空闲等待下一个到达",
        eventKind: "cpu.idle",
      });
      time = jump;
      addArrivals(time);
      continue;
    }
    const index = queues[level].shift() as number;
    const process = processOf(index);
    const quantum = quanta[level];
    const start = time;
    let end = time + Math.min(quantum, remaining[index]);
    let preempted = false;
    const nextArrivalTime =
      nextArrival < processes.length
        ? processes[nextArrival].arrival
        : Infinity;
    if (level > 0 && nextArrivalTime > time && nextArrivalTime < end) {
      end = nextArrivalTime;
      preempted = true;
    }
    const ran = end - start;
    remaining[index] -= ran;
    time = end;
    const arrived = addArrivals(time);
    const done = remaining[index] === 0;
    let target = level;
    const events: string[] = [];
    let eventKind = "cpu.run";
    if (arrived.length) {
      events.push(`新到达 ${arrived.join("、")} 进入 Q0`);
    }
    if (done) {
      finished.add(index);
      eventKind = "cpu.complete";
      events.push(`${process.name} 完成并离开 Q${level}`);
    } else if (preempted) {
      queues[level].unshift(index);
      eventKind = "cpu.preempt";
      events.push(`${process.name} 被高优先级到达抢占，回到 Q${level} 队首`);
    } else if (ran >= quantum) {
      target = Math.min(level + 1, queueCount - 1);
      queues[target].push(index);
      eventKind = "cpu.demote";
      events.push(
        target === level
          ? `${process.name} 用完 Q${level} 时间片，留在最低队列队尾`
          : `${process.name} 用完 Q${level} 时间片，降级到 Q${target} 队尾`
      );
    } else {
      queues[level].unshift(index);
      eventKind = "cpu.yield";
      events.push(`${process.name} 让出 CPU，回到 Q${level} 队首`);
    }
    timeline.push({
      name: process.name,
      start,
      end,
      queueLevel: level,
    });
    slices.push({
      name: process.name,
      start,
      end,
      level,
      readyAfter: queueNames().flat(),
      queuesAfter: queueNames(),
      note: events.join("；"),
      eventKind,
    });
  }
  return buildCpuResult(input, slices, timeline, [
    `${queueCount} 级反馈队列，时间片 ${quanta.join(" / ")}。`,
    "新进程进入 Q0；高队列进程到达时立即抢占低队列；用完时间片降一级，最低队列不再降级。",
    "被抢占的进程回到原队列队首；同队列内轮转。",
  ]);
}

export const CPU_ALGORITHMS: Record<
  string,
  {
    run: (input: CpuInput) => DemoResult;
    requiresPriority?: boolean;
    usesQuantum?: boolean;
  }
> = {
  "cpu.fcfs": { run: fcfs },
  "cpu.sjf": { run: sjf },
  "cpu.hrrn": { run: hrrn },
  "cpu.priority_nonpreemptive": {
    run: priorityNonPreemptive,
    requiresPriority: true,
  },
  "cpu.priority_preemptive": {
    run: priorityPreemptive,
    requiresPriority: true,
  },
  "cpu.rr": { run: roundRobin, usesQuantum: true },
  "cpu.mlfq": { run: mlfq, usesQuantum: true },
};
