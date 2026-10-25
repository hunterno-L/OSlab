export type PagingBeat = 1 | 2 | 3 | 4 | 5 | 6 | "HIT";

export interface PagingPhase {
  beat: PagingBeat;
  label: string;
  weight: number;
}

export interface PagingAccess {
  page: number;
  index: number;
  hit: boolean;
  evicted: number | null;
  frameIndex: number;
  framesBefore: (number | null)[];
  framesAfter: (number | null)[];
  phases: PagingPhase[];
}

export interface PagingScene {
  sequence: number[];
  frames: number;
  accesses: PagingAccess[];
  faults: number;
  hits: number;
  totalWeight: number;
}

export interface PagingSceneOptions {
  sequence?: number[];
  frames?: number;
}

export const BEATS: { id: 1 | 2 | 3 | 4 | 5 | 6; label: string }[] = [
  { id: 1, label: "请求页" },
  { id: 2, label: "TLB 未命中" },
  { id: 3, label: "页表遍历" },
  { id: 4, label: "缺页中断" },
  { id: 5, label: "换入 / 置换" },
  { id: 6, label: "恢复执行" },
];

const DEFAULT_SEQUENCE = [1, 2, 3, 1, 4];
const DEFAULT_FRAMES = 3;

export function buildPagingScene(
  options: PagingSceneOptions = {}
): PagingScene {
  const sequence = options.sequence ?? DEFAULT_SEQUENCE;
  const frames = options.frames ?? DEFAULT_FRAMES;

  if (!Array.isArray(sequence) || sequence.length === 0) {
    throw new Error("sequence must be a non-empty array");
  }
  if (!Number.isInteger(frames) || frames < 1) {
    throw new Error("frames must be a positive integer");
  }

  const slots: (number | null)[] = Array(frames).fill(null);
  const lastUsed = new Map<number, number>();
  const accesses: PagingAccess[] = [];
  let tick = 0;
  let faults = 0;
  let hits = 0;

  for (let index = 0; index < sequence.length; index += 1) {
    const page = sequence[index];
    tick += 1;
    const framesBefore = slots.slice();
    const frameIndex = slots.indexOf(page);
    const hit = frameIndex !== -1;
    const phases: PagingPhase[] = [
      { beat: 1, label: `请求第 ${page} 页`, weight: 1 },
    ];

    if (hit) {
      phases.push({ beat: "HIT", label: "TLB 命中", weight: 1 });
      phases.push({ beat: 6, label: "恢复执行", weight: 1 });
      lastUsed.set(page, tick);
      hits += 1;
      accesses.push({
        page,
        index,
        hit: true,
        evicted: null,
        frameIndex,
        framesBefore,
        framesAfter: slots.slice(),
        phases,
      });
      continue;
    }

    phases.push({ beat: 2, label: "TLB 未命中", weight: 1 });
    phases.push({ beat: 3, label: "遍历页表", weight: 1 });

    let targetIndex = slots.indexOf(null);
    let evicted: number | null = null;

    if (targetIndex === -1) {
      targetIndex = 0;
      let oldest = Infinity;
      for (let i = 0; i < slots.length; i += 1) {
        const resident = slots[i];
        if (resident === null) continue;
        const used = lastUsed.has(resident) ? lastUsed.get(resident) ?? -1 : -1;
        if (used < oldest) {
          oldest = used;
          targetIndex = i;
        }
      }
      evicted = slots[targetIndex];
      phases.push({ beat: 4, label: "缺页中断", weight: 1 });
      phases.push({
        beat: 5,
        label: `换出第 ${evicted} 页，换入第 ${page} 页`,
        weight: 2,
      });
    } else {
      phases.push({ beat: 4, label: "缺页中断", weight: 1 });
      phases.push({ beat: 5, label: `从磁盘换入第 ${page} 页`, weight: 2 });
    }

    slots[targetIndex] = page;
    lastUsed.set(page, tick);
    faults += 1;
    phases.push({ beat: 6, label: "恢复执行", weight: 1 });

    accesses.push({
      page,
      index,
      hit: false,
      evicted,
      frameIndex: targetIndex,
      framesBefore,
      framesAfter: slots.slice(),
      phases,
    });
  }

  const totalWeight = accesses.reduce(
    (sum, access) =>
      sum + access.phases.reduce((inner, phase) => inner + phase.weight, 0),
    0
  );

  return {
    sequence: sequence.slice(),
    frames,
    accesses,
    faults,
    hits,
    totalWeight,
  };
}

export function phaseAt(
  scene: PagingScene,
  weight: number
): { access: PagingAccess; phase: PagingPhase } {
  const total = scene.totalWeight;
  let remaining = ((weight % total) + total) % total;

  for (const access of scene.accesses) {
    for (const phase of access.phases) {
      if (remaining < phase.weight) return { access, phase };
      remaining -= phase.weight;
    }
  }

  const access = scene.accesses[scene.accesses.length - 1];
  return { access, phase: access.phases[access.phases.length - 1] };
}
