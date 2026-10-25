import type { DemoResult, DemoStep, PageInput } from "./types";

export type PageAlgorithmKey = "page.fifo" | "page.lru" | "page.opt";

export function parsePageSequence(text: string): number[] {
  return text
    .split(/[,，\s]+/)
    .map((value) => value.trim())
    .filter(Boolean)
    .map(Number);
}

export function validatePageInput(input: PageInput): string {
  if (
    !Number.isInteger(input.frames) ||
    input.frames < 1 ||
    input.frames > 60
  ) {
    return "物理块数需为 1–60 的整数";
  }
  if (!input.sequence.length) return "访问序列不能为空";
  if (input.sequence.length > 200) return "访问序列最多 200 步";
  for (const page of input.sequence) {
    if (!Number.isInteger(page) || page < 0 || page > 9999) {
      return "页号需为 0–9999 的整数";
    }
  }
  return "";
}

function nextUse(sequence: number[], current: number, page: number): number {
  for (let index = current + 1; index < sequence.length; index += 1) {
    if (sequence[index] === page) return index;
  }
  return Number.MAX_SAFE_INTEGER;
}

export function runPage(
  algorithm: PageAlgorithmKey,
  input: PageInput
): DemoResult {
  const memory: number[] = [];
  let faults = 0;
  let evictions = 0;
  const steps: DemoStep[] = [];

  input.sequence.forEach((page, index) => {
    const hit = memory.includes(page);
    let evicted: number | null = null;
    if (!hit) {
      faults += 1;
      if (memory.length >= input.frames) {
        let victimIndex = 0;
        if (algorithm === "page.opt") {
          let farthest = -1;
          // 并列时淘汰驻留序最靠前的页，保证确定性
          memory.forEach((resident, position) => {
            const next = nextUse(input.sequence, index, resident);
            if (next > farthest) {
              farthest = next;
              victimIndex = position;
            }
          });
        }
        evicted = memory.splice(victimIndex, 1)[0];
        evictions += 1;
      }
      memory.push(page);
    } else if (algorithm === "page.lru") {
      memory.splice(memory.indexOf(page), 1);
      memory.push(page);
    }
    steps.push({
      index: index + 1,
      title: `访问页 ${page} → ${hit ? "命中" : "缺页"}${
        evicted !== null ? `，淘汰 ${evicted}` : ""
      }`,
      detail: `驻留顺序（队首→队尾）：${memory.join(" → ") || "空"}`,
      time: index,
      tone: hit ? "success" : evicted !== null ? "warning" : "info",
      state: {
        page,
        fault: !hit,
        evicted,
        frames: [...memory],
        faults,
        hitRate: Math.round((1 - faults / (index + 1)) * 10000) / 100,
      },
    });
  });

  const hits = input.sequence.length - faults;
  return {
    steps,
    metrics: [
      { label: "总访问次数", value: String(input.sequence.length) },
      { label: "缺页次数", value: String(faults) },
      { label: "命中次数", value: String(hits) },
      {
        label: "命中率",
        value: `${((hits / input.sequence.length) * 100).toFixed(2)}%`,
      },
      { label: "淘汰次数", value: String(evictions) },
    ],
    initialState: {
      frames: [],
      page: null,
      fault: false,
      evicted: null,
    },
    notes: [
      "frames 按驻留顺序展示（队首最旧/最久未使用），不代表固定物理帧编号。",
      algorithm === "page.opt"
        ? "OPT 淘汰未来最久不再访问的页；并列时淘汰驻留序最靠前的页。OPT 仅用于对比，不写测评掌握事件。"
        : algorithm === "page.lru"
        ? "LRU 命中时把该页移到最近使用端；缺页时淘汰队首页。"
        : "FIFO 缺页时淘汰最早进入驻留队列的页。",
    ],
  };
}
