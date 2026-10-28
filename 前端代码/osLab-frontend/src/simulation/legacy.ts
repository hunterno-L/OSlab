import type {
  AlgorithmDefinition,
  AllocationInput,
  CpuInput,
  DemoInput,
  DiskInput,
  PageInput,
} from "./types";

export interface LegacyDemoResource {
  route: string;
  sourceFile: string;
  component: string;
}

const cpuResources: Record<string, LegacyDemoResource> = {
  "cpu.fcfs": {
    route: "/unit3/FCFS",
    sourceFile: "unit3/FCFS.vue",
    component: "FCFS",
  },
  "cpu.sjf": {
    route: "/unit3/SJF",
    sourceFile: "unit3/SJF.vue",
    component: "SJF",
  },
  "cpu.hrrn": {
    route: "/unit3/HighResponse",
    sourceFile: "unit3/HighResponse.vue",
    component: "HighResponse",
  },
  "cpu.priority_nonpreemptive": {
    route: "/unit3/HighPrio",
    sourceFile: "unit3/HighPrio.vue",
    component: "HighPrio",
  },
  "cpu.priority_preemptive": {
    route: "/unit3/HighPrio2",
    sourceFile: "unit3/HighPrio2.vue",
    component: "HighPrio2",
  },
  "cpu.rr": {
    route: "/unit3/RR",
    sourceFile: "unit3/RR.vue",
    component: "RR",
  },
  "cpu.mlfq": {
    route: "/unit3/FeedbackQueue",
    sourceFile: "unit3/FeedbackQueue.vue",
    component: "FeedbackQueue",
  },
};

const allocationResources: Record<string, LegacyDemoResource> = {
  "allocation.first_fit": {
    route: "/unit4/PartitionAllocation_FF",
    sourceFile: "unit4/PartitionAllocation_FF.vue",
    component: "FF",
  },
  "allocation.best_fit": {
    route: "/unit4/PartitionAllocation_BF",
    sourceFile: "unit4/PartitionAllocation_BF.vue",
    component: "BF",
  },
  "allocation.worst_fit": {
    route: "/unit4/PartitionAllocation_WF",
    sourceFile: "unit4/PartitionAllocation_WF.vue",
    component: "WF",
  },
  "allocation.next_fit": {
    route: "/unit4/PartitionAllocation_NF",
    sourceFile: "unit4/PartitionAllocation_NF.vue",
    component: "NF",
  },
};

const pageResources: Record<string, LegacyDemoResource> = {
  "page.fifo": {
    route: "/unit4/Replacement_FF",
    sourceFile: "unit4/Replacement_FF.vue",
    component: "PageFF",
  },
  "page.lru": {
    route: "/unit4/Replacement_LRU",
    sourceFile: "unit4/Replacement_LRU.vue",
    component: "PageLRU",
  },
  "page.opt": {
    route: "/unit4/Replacement_OPT",
    sourceFile: "unit4/Replacement_OPT.vue",
    component: "PageOPT",
  },
};

const diskResources: Record<string, LegacyDemoResource> = {
  "disk.fcfs": {
    route: "/unit5/FCFS",
    sourceFile: "unit5/FCFS.vue",
    component: "FCFS1",
  },
  "disk.sstf": {
    route: "/unit5/SSTF",
    sourceFile: "unit5/SSTF.vue",
    component: "SSTF",
  },
  "disk.scan": {
    route: "/unit5/SCAN",
    sourceFile: "unit5/SCAN.vue",
    component: "SCAN",
  },
  "disk.cscan": {
    route: "/unit5/CSCAN",
    sourceFile: "unit5/CSCAN.vue",
    component: "CSCAN",
  },
};

export const LEGACY_DEMO_RESOURCES: Record<string, LegacyDemoResource> = {
  ...cpuResources,
  ...allocationResources,
  ...pageResources,
  ...diskResources,
};

export function legacySourceUrl(resource: LegacyDemoResource): string {
  return `/legacy-simulation/source/${resource.sourceFile}`;
}

export function serializeLegacyInput(
  definition: AlgorithmDefinition,
  input: DemoInput
): string {
  if (definition.category === "cpu") {
    const cpu = input as CpuInput;
    return JSON.stringify({
      processes: cpu.processes,
      quantum: cpu.quantum,
      mlfqQueues: cpu.mlfqQueues,
    });
  }
  if (definition.category === "allocation") {
    const allocation = input as AllocationInput;
    return JSON.stringify({
      memorySize: allocation.memorySize,
      initialPartitions: allocation.initialPartitions,
      operations: allocation.operations,
    });
  }
  if (definition.category === "page") {
    const page = input as PageInput;
    return JSON.stringify(page);
  }
  const disk = input as DiskInput;
  return JSON.stringify(disk);
}
