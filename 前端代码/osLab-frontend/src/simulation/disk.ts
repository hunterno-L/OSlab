import type { DemoResult, DemoStep, DiskInput } from "./types";

export type DiskAlgorithmKey =
  | "disk.fcfs"
  | "disk.sstf"
  | "disk.scan"
  | "disk.cscan";

export const DISK_MIN_TRACK = 1;
export const DISK_MAX_TRACK = 200;

export function parseDiskRequests(text: string): number[] {
  return text
    .split(/[,，\s]+/)
    .map((value) => value.trim())
    .filter(Boolean)
    .map(Number);
}

export function validateDiskInput(input: DiskInput): string {
  if (
    !Number.isInteger(input.head) ||
    input.head < DISK_MIN_TRACK ||
    input.head > DISK_MAX_TRACK
  ) {
    return `磁头位置需为 ${DISK_MIN_TRACK}–${DISK_MAX_TRACK} 的整数`;
  }
  if (!input.requests.length) return "磁道请求不能为空";
  if (input.requests.length > 20) return "磁道请求最多 20 个";
  for (const track of input.requests) {
    if (
      !Number.isInteger(track) ||
      track < DISK_MIN_TRACK ||
      track > DISK_MAX_TRACK
    ) {
      return `磁道号需为 ${DISK_MIN_TRACK}–${DISK_MAX_TRACK} 的整数`;
    }
  }
  if (input.direction !== "UP" && input.direction !== "DOWN") {
    return "扫描方向只能为 UP 或 DOWN";
  }
  return "";
}

type ActionType = "REQUEST" | "BOUNDARY" | "JUMP";

interface DiskAction {
  type: ActionType;
  target: number;
  note?: string;
}

function fcfsActions(input: DiskInput): DiskAction[] {
  return input.requests.map((track) => ({ type: "REQUEST", target: track }));
}

function sstfActions(input: DiskInput): DiskAction[] {
  const pending = input.requests.slice();
  const actions: DiskAction[] = [];
  let head = input.head;
  while (pending.length) {
    let bestIndex = 0;
    for (let index = 1; index < pending.length; index += 1) {
      const distance = Math.abs(pending[index] - head);
      const bestDistance = Math.abs(pending[bestIndex] - head);
      if (
        distance < bestDistance ||
        (distance === bestDistance && pending[index] < pending[bestIndex])
      ) {
        bestIndex = index;
      }
    }
    const target = pending.splice(bestIndex, 1)[0];
    actions.push({ type: "REQUEST", target });
    head = target;
  }
  return actions;
}

function scanActions(input: DiskInput): DiskAction[] {
  const actions: DiskAction[] = [];
  if (input.direction === "UP") {
    const first = input.requests
      .filter((track) => track >= input.head)
      .sort((a, b) => a - b);
    const second = input.requests
      .filter((track) => track < input.head)
      .sort((a, b) => b - a);
    first.forEach((target) => actions.push({ type: "REQUEST", target }));
    if (second.length) {
      actions.push({
        type: "BOUNDARY",
        target: DISK_MAX_TRACK,
        note: "到达磁盘上界，准备反向",
      });
      second.forEach((target) => actions.push({ type: "REQUEST", target }));
    }
  } else {
    const first = input.requests
      .filter((track) => track <= input.head)
      .sort((a, b) => b - a);
    const second = input.requests
      .filter((track) => track > input.head)
      .sort((a, b) => a - b);
    first.forEach((target) => actions.push({ type: "REQUEST", target }));
    if (second.length) {
      actions.push({
        type: "BOUNDARY",
        target: DISK_MIN_TRACK,
        note: "到达磁盘下界，准备反向",
      });
      second.forEach((target) => actions.push({ type: "REQUEST", target }));
    }
  }
  return actions;
}

function cscanActions(input: DiskInput): DiskAction[] {
  const actions: DiskAction[] = [];
  if (input.direction === "UP") {
    const first = input.requests
      .filter((track) => track >= input.head)
      .sort((a, b) => a - b);
    const second = input.requests
      .filter((track) => track < input.head)
      .sort((a, b) => a - b);
    first.forEach((target) => actions.push({ type: "REQUEST", target }));
    if (second.length) {
      actions.push({
        type: "BOUNDARY",
        target: DISK_MAX_TRACK,
        note: "到达磁盘上界",
      });
      actions.push({
        type: "JUMP",
        target: DISK_MIN_TRACK,
        note: `回跳到 ${DISK_MIN_TRACK}，回跳距离不计入寻道长度`,
      });
      second.forEach((target) => actions.push({ type: "REQUEST", target }));
    }
  } else {
    const first = input.requests
      .filter((track) => track <= input.head)
      .sort((a, b) => b - a);
    const second = input.requests
      .filter((track) => track > input.head)
      .sort((a, b) => b - a);
    first.forEach((target) => actions.push({ type: "REQUEST", target }));
    if (second.length) {
      actions.push({
        type: "BOUNDARY",
        target: DISK_MIN_TRACK,
        note: "到达磁盘下界",
      });
      actions.push({
        type: "JUMP",
        target: DISK_MAX_TRACK,
        note: `回跳到最大磁道 ${DISK_MAX_TRACK}，回跳距离不计入寻道长度`,
      });
      second.forEach((target) => actions.push({ type: "REQUEST", target }));
    }
  }
  return actions;
}

export function runDisk(
  algorithm: DiskAlgorithmKey,
  input: DiskInput
): DemoResult {
  const actions =
    algorithm === "disk.fcfs"
      ? fcfsActions(input)
      : algorithm === "disk.sstf"
      ? sstfActions(input)
      : algorithm === "disk.scan"
      ? scanActions(input)
      : cscanActions(input);

  let head = input.head;
  let totalSeek = 0;
  let served = 0;
  let boundaryCount = 0;
  let jumpCount = 0;
  const order: number[] = [];
  const remaining = input.requests.slice();
  const steps: DemoStep[] = [];

  actions.forEach((action, index) => {
    const movement =
      action.type === "JUMP" ? 0 : Math.abs(action.target - head);
    const from = head;
    head = action.target;
    if (action.type === "REQUEST") {
      totalSeek += movement;
      served += 1;
      order.push(action.target);
      const removeAt = remaining.indexOf(action.target);
      if (removeAt >= 0) remaining.splice(removeAt, 1);
    } else {
      totalSeek += movement;
      if (action.type === "BOUNDARY") boundaryCount += 1;
      else jumpCount += 1;
    }
    const remainingText = remaining.length ? remaining.join(", ") : "无";
    const title =
      action.type === "REQUEST"
        ? `处理磁道 ${action.target}`
        : action.type === "BOUNDARY"
        ? `移动到边界 ${action.target}`
        : `回跳到 ${action.target}`;
    const detail =
      action.type === "JUMP"
        ? `从磁道 ${from} 回跳到 ${action.target}；回跳不计入寻道长度。剩余请求：${remainingText}`
        : `磁头 ${from} → ${
            action.target
          }，移动 ${movement}；累计寻道 ${totalSeek}。${
            action.note ? `${action.note}。` : ""
          }剩余请求：${remainingText}`;
    steps.push({
      index: index + 1,
      title,
      detail,
      time: index,
      tone:
        action.type === "REQUEST"
          ? "success"
          : action.type === "BOUNDARY"
          ? "warning"
          : "info",
      state: {
        from,
        head,
        target: action.target,
        request: action.type === "REQUEST" ? action.target : null,
        movement,
        totalSeek,
        order: [...order],
        remaining: [...remaining],
        direction: input.direction,
        actionType: action.type,
      },
    });
  });

  return {
    steps,
    metrics: [
      { label: "总寻道长度", value: String(totalSeek) },
      {
        label: "平均每次请求",
        value: served ? (totalSeek / served).toFixed(2) : "0",
      },
      { label: "服务请求数", value: String(served) },
      {
        label: algorithm === "disk.cscan" ? "回跳次数" : "边界换向次数",
        value: String(algorithm === "disk.cscan" ? jumpCount : boundaryCount),
      },
    ],
    initialState: {
      from: null,
      head: input.head,
      target: null,
      request: null,
      movement: 0,
      totalSeek: 0,
      order: [],
      remaining: input.requests.slice(),
      direction: input.direction,
    },
    notes: [
      `磁盘磁道范围固定为 ${DISK_MIN_TRACK}–${DISK_MAX_TRACK}；寻道长度只统计实际磁头移动。`,
      algorithm === "disk.scan"
        ? "SCAN 沿当前方向服务；反方向仍有请求时先移动到端点再反向，端点移动计入寻道长度。"
        : algorithm === "disk.cscan"
        ? "C-SCAN 只沿当前方向服务；反方向仍有请求时移动到端点后回跳另一端，回跳距离不计入寻道长度。"
        : algorithm === "disk.sstf"
        ? "SSTF 每次选择距当前磁头最近的请求，并列时选择较小磁道。"
        : "FCFS 按请求到达顺序服务，不重排。",
    ],
  };
}
