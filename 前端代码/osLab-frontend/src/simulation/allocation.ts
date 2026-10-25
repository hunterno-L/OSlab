import type {
  AllocationInput,
  AllocationOperation,
  DemoMetric,
  DemoResult,
  DemoStep,
} from "./types";

export type AllocationAlgorithmKey =
  | "allocation.first_fit"
  | "allocation.best_fit"
  | "allocation.worst_fit"
  | "allocation.next_fit";

interface Block {
  start: number;
  size: number;
  process: string | null;
}

export function parseAllocationOperations(text: string): AllocationOperation[] {
  const operations: AllocationOperation[] = [];
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.replace(/#.*$/, "").trim();
    if (!line) continue;
    const parts = line.split(/\s+/);
    if (parts[0].toUpperCase() === "FREE") {
      if (parts.length !== 2) throw new Error(`无法解析回收操作：${line}`);
      operations.push({ type: "FREE", process: parts[1] });
      continue;
    }
    const values =
      parts[0].toUpperCase() === "ALLOC" ? parts.slice(1) : parts.slice();
    if (values.length !== 2) throw new Error(`无法解析分配操作：${line}`);
    operations.push({
      type: "ALLOC",
      process: values[0],
      size: Number(values[1]),
    });
  }
  return operations;
}

export function formatAllocationOperations(
  operations: AllocationOperation[]
): string {
  return operations
    .map((operation) =>
      operation.type === "FREE"
        ? `FREE ${operation.process}`
        : `${operation.process} ${operation.size}`
    )
    .join("\n");
}

export function validateAllocationInput(input: AllocationInput): string {
  const partitions =
    Array.isArray(input.initialPartitions) && input.initialPartitions.length
      ? input.initialPartitions
      : null;
  if (partitions) {
    if (partitions.length > 12) return "初始空闲分区最多 12 块";
    let sum = 0;
    for (const size of partitions) {
      if (!Number.isInteger(size) || size < 1 || size > 4096) {
        return "每个初始空闲分区需为 1–4096 的整数";
      }
      sum += size;
    }
    if (
      !Number.isInteger(input.memorySize) ||
      input.memorySize < 16 ||
      input.memorySize > 4096
    ) {
      return "内存大小需为 16–4096 的整数";
    }
    if (sum !== input.memorySize) {
      return "初始分区总大小需等于内存大小";
    }
  } else if (
    !Number.isInteger(input.memorySize) ||
    input.memorySize < 16 ||
    input.memorySize > 4096
  ) {
    return "内存大小需为 16–4096 的整数";
  }
  if (!input.operations.length) return "请至少填写一条分配或回收操作";
  if (input.operations.length > 30) return "操作最多 30 条";
  for (const operation of input.operations) {
    if (!/^[A-Za-z0-9_-]{1,32}$/.test(operation.process || "")) {
      return "进程名需为 1–32 位字母、数字、下划线或短横线";
    }
    if (
      operation.type === "ALLOC" &&
      (!Number.isInteger(operation.size) ||
        (operation.size as number) < 1 ||
        (operation.size as number) > input.memorySize)
    ) {
      return `分配请求需为 1–${input.memorySize} 的整数`;
    }
  }
  return "";
}

const cloneBlocks = (blocks: Block[]): Block[] =>
  blocks.map((block) => ({ ...block }));

const mergeFree = (blocks: Block[]): Block[] => {
  const sorted = cloneBlocks(blocks).sort((a, b) => a.start - b.start);
  const merged: Block[] = [];
  for (const block of sorted) {
    const last = merged[merged.length - 1];
    if (
      last &&
      last.process === null &&
      block.process === null &&
      last.start + last.size === block.start
    ) {
      last.size += block.size;
    } else {
      merged.push(block);
    }
  }
  return merged;
};

const freeTotal = (blocks: Block[]) =>
  blocks.reduce(
    (sum, block) => sum + (block.process === null ? block.size : 0),
    0
  );

const largestFree = (blocks: Block[]) =>
  blocks.reduce(
    (largest, block) =>
      block.process === null ? Math.max(largest, block.size) : largest,
    0
  );

interface FreeSearch {
  chosen: Block | null;
  /** 按算法实际扫描顺序排列的候选空闲区（仅包含容量足够的块） */
  candidates: Block[];
}

function chooseFreeBlock(
  algorithm: AllocationAlgorithmKey,
  blocks: Block[],
  requestSize: number,
  pointer: number
): FreeSearch {
  const adequate = blocks
    .filter((block) => block.process === null && block.size >= requestSize)
    .slice()
    .sort((a, b) => a.start - b.start);
  if (!adequate.length) return { chosen: null, candidates: [] };
  if (algorithm === "allocation.next_fit") {
    const after = adequate.filter((block) => block.start >= pointer);
    const before = adequate.filter((block) => block.start < pointer);
    const candidates = after.concat(before);
    return { chosen: candidates[0], candidates };
  }
  if (algorithm === "allocation.best_fit") {
    return {
      chosen: adequate
        .slice()
        .sort((a, b) => a.size - b.size || a.start - b.start)[0],
      candidates: adequate,
    };
  }
  if (algorithm === "allocation.worst_fit") {
    return {
      chosen: adequate
        .slice()
        .sort((a, b) => b.size - a.size || a.start - b.start)[0],
      candidates: adequate,
    };
  }
  return { chosen: adequate[0], candidates: adequate };
}

function buildMetrics(
  blocks: Block[],
  failedCount: number,
  successCount: number
): DemoMetric[] {
  const total = freeTotal(blocks);
  const largest = largestFree(blocks);
  return [
    { label: "成功分配", value: String(successCount) },
    { label: "失败操作", value: String(failedCount) },
    { label: "空闲空间", value: String(total) },
    { label: "最大空闲区", value: String(largest) },
    {
      label: "外部碎片率",
      value: total > 0 ? `${((1 - largest / total) * 100).toFixed(1)}%` : "0%",
    },
  ];
}

interface AllocationRange {
  start: number;
  size: number;
}

interface AllocationStepExtras {
  operationType: "ALLOC" | "FREE";
  process: string;
  request: { process: string; size: number } | null;
  /** 按算法扫描顺序排列的候选空闲区（仅容量足够者） */
  candidates: AllocationRange[];
  /** 本次分配选中的空闲区（分配成功后即为被切分的原空闲区） */
  victim: AllocationRange | null;
  allocated: (AllocationRange & { process: string }) | null;
  freed: (AllocationRange & { process: string }) | null;
  /** 回收后是否与相邻空闲区发生了合并 */
  merged: boolean;
  pointerBefore: number;
  freeBefore: number;
}

export function runAllocation(
  algorithm: AllocationAlgorithmKey,
  input: AllocationInput
): DemoResult {
  const initialSizes =
    Array.isArray(input.initialPartitions) && input.initialPartitions.length
      ? input.initialPartitions
      : [input.memorySize];
  const initialBlocks: Block[] = [];
  let address = 0;
  for (const size of initialSizes) {
    initialBlocks.push({ start: address, size, process: null });
    address += size;
  }
  const memorySize = Math.max(
    1,
    initialBlocks.reduce((sum, block) => sum + block.size, 0)
  );
  let blocks: Block[] = cloneBlocks(initialBlocks);
  let pointer = 0;
  let successCount = 0;
  let failedCount = 0;
  const steps: DemoStep[] = [];

  const snapshot = (
    action: string,
    success: boolean,
    message: string,
    extras: AllocationStepExtras
  ) => ({
    blocks: cloneBlocks(blocks),
    pointer,
    action,
    success,
    message,
    freeTotal: freeTotal(blocks),
    largestFree: largestFree(blocks),
    ...extras,
  });

  input.operations.forEach((operation, index) => {
    let success = false;
    let message = "";
    let candidates: AllocationRange[] = [];
    let victim: AllocationRange | null = null;
    let allocated: (AllocationRange & { process: string }) | null = null;
    let freed: (AllocationRange & { process: string }) | null = null;
    let merged = false;
    const pointerBefore = pointer;
    const freeBefore = freeTotal(blocks);
    const request =
      operation.type === "ALLOC"
        ? { process: operation.process, size: operation.size as number }
        : null;
    if (operation.type === "FREE") {
      const target = blocks.find(
        (block) => block.process === operation.process
      );
      if (!target) {
        message = `进程 ${operation.process} 当前没有已分配分区`;
      } else {
        target.process = null;
        const freeBlocksAfterClear = blocks.filter(
          (block) => block.process === null
        ).length;
        blocks = mergeFree(blocks);
        const freeBlocksAfterMerge = blocks.filter(
          (block) => block.process === null
        ).length;
        merged = freeBlocksAfterMerge < freeBlocksAfterClear;
        freed = {
          start: target.start,
          size: target.size,
          process: operation.process,
        };
        success = true;
        message = `回收 ${operation.process} 的分区 [${target.start},${
          target.start + target.size
        })${merged ? "，已与相邻空闲区合并" : "，无相邻空闲区可合并"}`;
      }
    } else {
      const size = operation.size as number;
      const alreadyAllocated = blocks.some(
        (block) => block.process === operation.process
      );
      const search = alreadyAllocated
        ? { chosen: null, candidates: [] as Block[] }
        : chooseFreeBlock(algorithm, blocks, size, pointer);
      candidates = search.candidates.map((block) => ({
        start: block.start,
        size: block.size,
      }));
      const victimBlock = search.chosen;
      if (alreadyAllocated) {
        message = `进程 ${operation.process} 已有分区，需先回收再分配`;
      } else if (!victimBlock) {
        message = `没有不小于 ${size} 的空闲分区，分配失败（外部碎片或空间不足）`;
      } else {
        victim = { start: victimBlock.start, size: victimBlock.size };
        const allocatedBlock: Block = {
          start: victimBlock.start,
          size,
          process: operation.process,
        };
        const remainder = victimBlock.size - size;
        const replacement: Block[] = [allocatedBlock];
        if (remainder > 0) {
          replacement.push({
            start: victimBlock.start + size,
            size: remainder,
            process: null,
          });
        }
        blocks = blocks
          .filter((block) => block !== victimBlock)
          .concat(replacement)
          .sort((a, b) => a.start - b.start);
        pointer = (victimBlock.start + size) % memorySize;
        success = true;
        successCount += 1;
        allocated = {
          start: victimBlock.start,
          size,
          process: operation.process,
        };
        message = `分配 [${victimBlock.start},${victimBlock.start + size}) 给 ${
          operation.process
        }${remainder > 0 ? `，剩余空闲 ${remainder}` : "，无剩余"}`;
      }
    }
    if (!success) failedCount += 1;
    const action = `${operation.type === "FREE" ? "回收" : "分配"} ${
      operation.process
    }${operation.size ? ` · ${operation.size}` : ""}`;
    steps.push({
      index: index + 1,
      title: `${action} → ${success ? "成功" : "失败"}`,
      detail: message,
      tone: success ? "success" : "danger",
      state: snapshot(action, success, message, {
        operationType: operation.type,
        process: operation.process,
        request,
        candidates,
        victim,
        allocated,
        freed,
        merged,
        pointerBefore,
        freeBefore,
      }),
    });
  });

  return {
    steps,
    metrics: buildMetrics(blocks, failedCount, successCount),
    initialState: {
      blocks: cloneBlocks(initialBlocks),
      pointer: 0,
      freeTotal: memorySize,
      largestFree: Math.max(...initialBlocks.map((block) => block.size)),
      action: "",
      success: undefined,
      message: "",
      operationType: null,
      process: "",
      request: null,
      candidates: [],
      victim: null,
      allocated: null,
      freed: null,
      merged: false,
      pointerBefore: 0,
      freeBefore: memorySize,
    },
    notes: [
      "空闲区按地址排序；回收后立即与相邻空闲区合并。",
      "初始空闲分区按输入顺序从地址 0 开始连续排列。",
      "外部碎片率 = 1 − 最大空闲区 / 总空闲空间（总空闲为 0 时记 0）。",
      "Next Fit 从上次分配结束位置向后循环查找。",
    ],
  };
}
