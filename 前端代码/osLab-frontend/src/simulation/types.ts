export type AlgorithmCategory = "cpu" | "allocation" | "page" | "disk";
export type ChallengeMode =
  | "SERVER_VERIFIED_WHEN_ADAPTED"
  | "PROGRESS_ONLY_UNTIL_VERIFIED"
  | "PROGRESS_ONLY";

export interface CpuProcessInput {
  name: string;
  arrival: number;
  burst: number;
  priority?: number;
}

export interface CpuInput {
  processes: CpuProcessInput[];
  quantum?: number;
  /** MLFQ 每级时间片（2–6 级）；缺省时按 q / 2q / 4q 生成三级 */
  mlfqQueues?: number[];
}

export interface AllocationOperation {
  type: "ALLOC" | "FREE";
  process: string;
  size?: number;
}

export interface AllocationInput {
  memorySize: number;
  /** 初始空闲分区大小（按地址递增）；缺省视为单块 [memorySize] */
  initialPartitions?: number[];
  operations: AllocationOperation[];
}

export interface PageInput {
  frames: number;
  sequence: number[];
}

export type DiskDirection = "UP" | "DOWN";

export interface DiskInput {
  head: number;
  requests: number[];
  direction: DiskDirection;
}

export type DemoInput = CpuInput | AllocationInput | PageInput | DiskInput;

export interface CpuSegment {
  name: string;
  start: number;
  end: number;
  queueLevel?: number;
}

/**
 * 步骤事件：只描述本步骤在演示对象上的动作，供播放器与动画层消费。
 * 事件不参与算法计算，真值步骤仍由本地算法函数生成。
 */
export interface DemoEvent {
  kind: string;
  objectId?: string;
  from?: string;
  to?: string;
  duration?: number;
}

export interface DemoStep {
  /** 1-based 展示序号 */
  index: number;
  title: string;
  detail: string;
  time?: number;
  tone?: "info" | "success" | "warning" | "danger";
  state: Record<string, unknown>;
  event?: DemoEvent;
  codeLines?: number[];
  activeCodeLines?: number[];
}

export interface DemoMetric {
  label: string;
  value: string;
}

export interface DemoResult {
  steps: DemoStep[];
  metrics: DemoMetric[];
  initialState: Record<string, unknown>;
  timeline?: CpuSegment[];
  notes?: string[];
}

export interface AlgorithmDefinition {
  key: string;
  resourceCode: string;
  title: string;
  shortTitle: string;
  category: AlgorithmCategory;
  nodeCode: string;
  description: string;
  rules: string[];
  /** 资源中已有的算法思想/特点解析（可展开阅读） */
  analysis?: string[];
  challengeMode: ChallengeMode;
  requiresPriority?: boolean;
  usesQuantum?: boolean;
  defaultInput: DemoInput;
}

export interface DemoValidation {
  valid: boolean;
  message?: string;
}
