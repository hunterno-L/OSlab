<template>
  <div class="simulation-view">
    <a-page-header
      title="交互模拟"
      sub-title="算法过程推演；每一步先预测、后揭示，预测由服务端判分，返回 eventId 才计入学习证据"
    />
    <a-alert v-if="nodeName" style="margin-bottom: 12px">
      当前节点：{{ nodeName }}（预测判分返回 eventId 时计入该节点的学习证据）
      <a-button
        size="mini"
        type="text"
        style="margin-left: 8px"
        @click="goNode()"
      >
        返回当前知识点
      </a-button>
    </a-alert>
    <a-tabs v-model:active-key="activeTab" type="card">
      <!-- 页面置换 -->
      <a-tab-pane key="page" title="页面置换">
        <!-- 1. 配置区 -->
        <a-card title="运行配置">
          <a-space wrap style="margin-bottom: 12px">
            <a-select v-model:model-value="page.algorithm" style="width: 220px">
              <a-option value="FIFO">FIFO 先进先出</a-option>
              <a-option value="LRU">LRU 最近最久未使用</a-option>
              <a-option value="OPT">OPT 最佳置换（对比用）</a-option>
            </a-select>
            <a-input-number
              v-model:model-value="page.frames"
              :min="1"
              :max="10"
            >
              <template #prefix>物理块</template>
            </a-input-number>
            <a-input
              v-model:model-value="page.sequenceText"
              placeholder="页面访问序列，逗号分隔，如 7,0,1,2,0,3"
              style="width: 320px"
            />
            <a-select
              v-model:model-value="page.nodeName"
              placeholder="关联知识节点（可选）"
              allow-clear
              allow-search
              style="width: 220px"
            >
              <a-option v-for="node in nodeNames" :key="node" :value="node">
                {{ node }}
              </a-option>
            </a-select>
            <a-button type="primary" :loading="page.running" @click="runPage">
              运行模拟
            </a-button>
            <a-button :loading="compare.running" @click="runCompare">
              运行对比（FIFO/LRU/OPT）
            </a-button>
          </a-space>
          <div class="side-hint">
            点击运行后，本次输入会冻结为运行配置；之后修改输入会标记“配置已修改，需重新运行”，预测与结果始终绑定同一份配置。对比运行不写学习证据。
          </div>
          <a-alert v-if="page.error" type="error" style="margin-top: 12px">
            {{ page.error }}
          </a-alert>
          <a-alert v-if="compare.error" type="error" style="margin-top: 12px">
            {{ compare.error }}
          </a-alert>
        </a-card>

        <template v-if="page.result">
          <!-- 2. 执行步骤矩阵 -->
          <a-card title="执行步骤矩阵（逐步回放）" style="margin-top: 16px">
            <div class="run-config-line">
              <span>当前运行配置：</span>
              <span class="oslab-mono">{{ pageRunConfigSummary }}</span>
              <a-tag v-if="pageConfigDirty" color="orange">
                配置已修改，需重新运行
              </a-tag>
            </div>

            <div class="playback-bar">
              <a-space>
                <a-tooltip content="步进：直接揭示下一步（不计入学习证据）">
                  <a-button
                    shape="circle"
                    :disabled="playing || revealed >= page.result.steps.length"
                    @click="stepForward"
                  >
                    <template #icon><icon-right /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip content="播放：自动逐步揭示">
                  <a-button
                    shape="circle"
                    :disabled="playing || revealed >= page.result.steps.length"
                    @click="play"
                  >
                    <template #icon><icon-play-arrow-fill /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip content="暂停">
                  <a-button shape="circle" :disabled="!playing" @click="pause">
                    <template #icon><icon-pause /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip content="重置：回到第 0 步，可重新预测">
                  <a-button shape="circle" @click="resetPlayback">
                    <template #icon><icon-refresh /></template>
                  </a-button>
                </a-tooltip>
                <span class="progress-text">
                  已揭示 {{ revealed }} / {{ page.result.steps.length }} 步
                </span>
              </a-space>
            </div>

            <!-- 当前帧：示意槽位，槽位数量来自冻结的运行配置 -->
            <div class="current-frame">
              <template v-if="currentStep">
                <span class="frame-label">第 {{ currentStep.step }} 步</span>
                <a-tag :color="currentStep.fault ? 'red' : 'green'">
                  访问 {{ currentStep.page }}：
                  {{ currentStep.fault ? "缺页" : "命中" }}
                </a-tag>
                <a-tag v-if="currentStep.evicted !== null" color="orange">
                  淘汰 {{ currentStep.evicted }}
                </a-tag>
              </template>
              <span v-else class="frame-label">尚未揭示任何步骤</span>
              <div class="frame-grid-caption">
                示意槽位：frames 为 FIFO/LRU
                队列顺序（队首最旧、最先淘汰），槽位仅按队列顺序示意填充，不代表固定物理帧编号
              </div>
              <div class="frame-grid">
                <div
                  v-for="f in runConfigFrameCount"
                  :key="f"
                  class="frame-slot"
                  :class="{
                    filled:
                      currentStep && currentStep.frames[f - 1] !== undefined,
                    active:
                      currentStep &&
                      currentStep.frames[f - 1] === currentStep.page,
                  }"
                >
                  {{
                    currentStep && currentStep.frames[f - 1] !== undefined
                      ? currentStep.frames[f - 1]
                      : "·"
                  }}
                </div>
              </div>
            </div>

            <a-table
              :data="revealedSteps"
              :pagination="false"
              size="small"
              style="margin-top: 12px"
            >
              <template #columns>
                <a-table-column title="步" data-index="step" :width="60" />
                <a-table-column title="访问页" data-index="page" :width="80" />
                <a-table-column title="驻留队列（队首→队尾）">
                  <template #cell="{ record }">
                    <a-tag
                      v-for="(frame, fIndex) in record.frames"
                      :key="fIndex"
                      :color="frame === record.page ? 'green' : 'gray'"
                      size="small"
                      style="margin-right: 4px"
                    >
                      {{ frame }}
                    </a-tag>
                  </template>
                </a-table-column>
                <a-table-column title="缺页?" :width="80">
                  <template #cell="{ record }">
                    <a-tag :color="record.fault ? 'red' : 'green'" size="small">
                      {{ record.fault ? "缺页" : "命中" }}
                    </a-tag>
                  </template>
                </a-table-column>
                <a-table-column title="淘汰页" :width="80">
                  <template #cell="{ record }">
                    {{ record.evicted ?? "-" }}
                  </template>
                </a-table-column>
              </template>
            </a-table>
            <div class="side-hint">
              每步 frames 数组按队列顺序展示，未映射为固定物理帧行。
            </div>
          </a-card>

          <!-- 3. 结果摘要 -->
          <a-card title="结果摘要" style="margin-top: 16px">
            <a-descriptions
              v-if="allStepsRevealed"
              :column="2"
              bordered
              size="small"
            >
              <a-descriptions-item label="总缺页次数">
                {{ page.result.pageFaults }}
              </a-descriptions-item>
              <a-descriptions-item label="命中率">
                {{ (page.result.hitRate * 100).toFixed(2) }}%
              </a-descriptions-item>
            </a-descriptions>
            <a-alert v-else type="info" style="margin-bottom: 0">
              已揭示 {{ revealed }} / {{ page.result.steps.length }}
              步；全部步骤揭示后显示缺页与命中率汇总，避免提前泄露后续答案。
            </a-alert>
          </a-card>

          <!-- 4. 算法对比（不带节点，不写学习证据） -->
          <a-card
            v-if="compare.rows.length"
            title="FIFO / LRU / OPT 结果对比"
            style="margin-top: 16px"
          >
            <div class="run-config-line">
              <span>对比配置：</span>
              <span class="oslab-mono">{{ compareRunConfigSummary }}</span>
              <a-tag v-if="compareStale" color="orange">
                配置已修改，需重新运行对比
              </a-tag>
            </div>
            <a-table :data="compare.rows" :pagination="false" size="small">
              <template #columns>
                <a-table-column title="算法" data-index="algorithm" />
                <a-table-column title="缺页次数" data-index="pageFaults" />
                <a-table-column title="命中率">
                  <template #cell="{ record }">
                    {{ (record.hitRate * 100).toFixed(2) }}%
                  </template>
                </a-table-column>
              </template>
            </a-table>
            <div class="side-hint">对比运行不带节点上下文，不写学习证据。</div>
          </a-card>

          <!-- 5. 预测练习 -->
          <a-card title="预测练习（服务端判分）" style="margin-top: 16px">
            <a-alert
              v-if="pageConfigDirty"
              type="warning"
              style="margin-bottom: 12px"
            >
              配置已修改，需重新运行后才能提交预测；预测始终绑定当前运行配置，禁止新输入配旧结果。
            </a-alert>
            <template v-if="nextStep">
              <a-space wrap>
                <span>
                  第 {{ nextStep.step }} 步将访问页
                  <b>{{ nextStep.page }}</b>
                  ，结果尚未揭示，请先预测：
                </span>
                <a-select
                  v-model:model-value="page.predictedFaultText"
                  size="small"
                  style="width: 100px"
                >
                  <a-option value="hit">命中</a-option>
                  <a-option value="fault">缺页</a-option>
                </a-select>
                <span>淘汰页</span>
                <a-input-number
                  v-model:model-value="page.predictedEvicted"
                  :min="0"
                  size="small"
                  placeholder="无"
                />
                <a-button
                  type="primary"
                  size="small"
                  :loading="page.predicting"
                  :disabled="pageConfigDirty"
                  @click="predictPage"
                >
                  提交预测
                </a-button>
              </a-space>
            </template>
            <a-alert v-else type="info" style="margin-bottom: 0">
              全部步骤已揭示。点击上方“重置”可回到第 0 步重新练习。
            </a-alert>
            <a-alert
              v-if="page.prediction"
              :type="pagePredictionType"
              style="margin-top: 12px"
            >
              <template #title>{{ pagePredictionText }}</template>
            </a-alert>
            <!-- 完成预测后的行动入口（绑定冻结配置中的节点上下文） -->
            <a-space
              v-if="page.prediction && page.runConfig?.nodeName"
              style="margin-top: 12px"
            >
              <a-button size="small" @click="goNode(page.runConfig.nodeName)">
                返回当前知识点
              </a-button>
              <a-button
                size="small"
                type="primary"
                @click="goExperiments(page.runConfig.nodeName)"
              >
                进入编程实验
              </a-button>
            </a-space>
          </a-card>
        </template>
      </a-tab-pane>

      <!-- CPU 调度 -->
      <a-tab-pane key="cpu" title="CPU 调度">
        <!-- 1. 配置区 -->
        <a-card title="运行配置">
          <a-space wrap style="margin-bottom: 12px">
            <a-select v-model:model-value="cpu.algorithm" style="width: 220px">
              <a-option value="FCFS">FCFS 先来先服务</a-option>
              <a-option value="SJF">SJF 短作业优先（非抢占）</a-option>
              <a-option value="RR">RR 时间片轮转</a-option>
            </a-select>
            <a-input-number
              v-if="cpu.algorithm === 'RR'"
              v-model:model-value="cpu.timeSlice"
              :min="1"
              :max="10"
              placeholder="时间片"
            >
              <template #prefix>时间片</template>
            </a-input-number>
            <a-select
              v-model:model-value="cpu.nodeName"
              placeholder="关联知识节点（可选）"
              allow-clear
              allow-search
              style="width: 220px"
            >
              <a-option v-for="node in nodeNames" :key="node" :value="node">
                {{ node }}
              </a-option>
            </a-select>
          </a-space>
          <a-table :data="cpu.processes" :pagination="false" size="small">
            <template #columns>
              <a-table-column title="进程名" :width="140">
                <template #cell="{ record }">
                  <a-input v-model:model-value="record.name" size="small" />
                </template>
              </a-table-column>
              <a-table-column title="到达时间" :width="140">
                <template #cell="{ record }">
                  <a-input-number
                    v-model:model-value="record.arrivalTime"
                    size="small"
                    :min="0"
                  />
                </template>
              </a-table-column>
              <a-table-column title="运行时间" :width="140">
                <template #cell="{ record }">
                  <a-input-number
                    v-model:model-value="record.burstTime"
                    size="small"
                    :min="1"
                  />
                </template>
              </a-table-column>
              <a-table-column title="操作" :width="80">
                <template #cell="{ rowIndex }">
                  <a-button
                    size="mini"
                    status="danger"
                    @click="cpu.processes.splice(rowIndex, 1)"
                  >
                    删除
                  </a-button>
                </template>
              </a-table-column>
            </template>
          </a-table>
          <a-space style="margin-top: 12px">
            <a-button size="small" @click="addProcess">添加进程</a-button>
            <a-button type="primary" :loading="cpu.running" @click="runCpu">
              运行模拟
            </a-button>
          </a-space>
          <div class="side-hint">
            点击运行后，本次输入会冻结为运行配置；之后修改算法、时间片或进程数据会标记“配置已修改，需重新运行”。
          </div>
          <a-alert v-if="cpu.error" type="error" style="margin-top: 12px">
            {{ cpu.error }}
          </a-alert>
        </a-card>

        <template v-if="cpu.result">
          <!-- 2. 执行步骤（甘特图） -->
          <a-card title="执行甘特图" style="margin-top: 16px">
            <div class="run-config-line">
              <span>当前运行配置：</span>
              <span class="oslab-mono">{{ cpuRunConfigSummary }}</span>
              <a-tag v-if="cpuConfigDirty" color="orange">
                配置已修改，需重新运行
              </a-tag>
            </div>
            <div class="gantt">
              <div
                v-for="(seg, index) in cpu.result.gantt"
                :key="index"
                class="gantt-segment"
                :style="{
                  flexGrow: seg.end - seg.start,
                  background: ganttColor(seg.process),
                }"
              >
                {{ seg.process }}<br />[{{ seg.start }},{{ seg.end }})
              </div>
            </div>
          </a-card>

          <!-- 3. 结果摘要 -->
          <a-card title="结果摘要" style="margin-top: 16px">
            <a-descriptions :column="2" bordered size="small">
              <a-descriptions-item label="平均等待时间">
                {{ cpu.result.avgWaitingTime }}
              </a-descriptions-item>
              <a-descriptions-item label="平均周转时间">
                {{ cpu.result.avgTurnaroundTime }}
              </a-descriptions-item>
            </a-descriptions>
            <a-table
              :data="cpu.result.processStats"
              :pagination="false"
              size="small"
              style="margin-top: 12px"
            >
              <template #columns>
                <a-table-column title="进程" data-index="name" />
                <a-table-column title="到达" data-index="arrivalTime" />
                <a-table-column title="运行" data-index="burstTime" />
                <a-table-column title="开始" data-index="startTime" />
                <a-table-column title="完成" data-index="completionTime" />
                <a-table-column title="等待" data-index="waitingTime" />
                <a-table-column title="周转" data-index="turnaroundTime" />
              </template>
            </a-table>
          </a-card>

          <!-- 预测练习 -->
          <a-card title="预测练习（服务端判分）" style="margin-top: 16px">
            <a-alert
              v-if="cpuConfigDirty"
              type="warning"
              style="margin-bottom: 12px"
            >
              配置已修改，需重新运行后才能提交预测；预测始终绑定当前运行配置，禁止新输入配旧结果。
            </a-alert>
            <a-space wrap>
              <span>第</span>
              <a-input-number
                v-model:model-value="cpu.predictIndex"
                :min="0"
                size="small"
              />
              <span>个时间单位上运行的进程是：</span>
              <a-input
                v-model:model-value="cpu.predictedProcess"
                placeholder="进程名"
                size="small"
                style="width: 100px"
              />
              <a-button
                type="primary"
                size="small"
                :loading="cpu.predicting"
                :disabled="cpuConfigDirty"
                @click="predictCpu"
              >
                提交预测
              </a-button>
            </a-space>
            <a-alert
              v-if="cpu.prediction"
              :type="cpu.prediction.correct ? 'success' : 'error'"
              style="margin-top: 12px"
            >
              <template #title>{{ cpuPredictionText }}</template>
            </a-alert>
            <a-space
              v-if="cpu.prediction && cpu.runConfig?.nodeName"
              style="margin-top: 12px"
            >
              <a-button size="small" @click="goNode(cpu.runConfig.nodeName)">
                返回当前知识点
              </a-button>
              <a-button
                size="small"
                type="primary"
                @click="goExperiments(cpu.runConfig.nodeName)"
              >
                进入编程实验
              </a-button>
            </a-space>
          </a-card>
        </template>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import {
  IconPause,
  IconPlayArrowFill,
  IconRefresh,
  IconRight,
} from "@arco-design/web-vue/es/icon";
import {
  runCpuSimulation,
  runPageSimulation,
  type CpuProcessInput,
} from "@/api/learning";
import { KnowledgeGraphControllerService } from "../../../generated";

const nodeNames = ref<string[]>([]);

const route = useRoute();
const router = useRouter();
const activeTab = ref("page"); // 默认页面置换；节点入口可用 ?tab=cpu 切换

/** 深拷贝当前输入为不可变运行配置（纯 JSON 数据） */
function snapshotConfig<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}

// ---------- CPU 调度 ----------
interface CpuRunConfig {
  algorithm: "FCFS" | "SJF" | "RR";
  timeSlice?: number;
  processes: CpuProcessInput[];
  nodeName?: string;
}

const cpu = reactive({
  algorithm: "FCFS" as "FCFS" | "SJF" | "RR",
  timeSlice: 2,
  nodeName: undefined as string | undefined,
  processes: [
    { name: "P1", arrivalTime: 0, burstTime: 7 },
    { name: "P2", arrivalTime: 2, burstTime: 4 },
    { name: "P3", arrivalTime: 4, burstTime: 1 },
  ] as CpuProcessInput[],
  running: false,
  predicting: false,
  error: "",
  result: null as any,
  predictIndex: 0,
  predictedProcess: "",
  prediction: null as any,
  /** 运行时冻结的输入快照：甘特图、统计与预测都绑定它 */
  runConfig: null as CpuRunConfig | null,
});

// ---------- 页面置换 ----------
type PageAlgorithm = "FIFO" | "LRU" | "OPT";

interface PageRunConfig {
  algorithm: PageAlgorithm;
  frames: number;
  accessSequence: number[];
  nodeName?: string;
}

const page = reactive({
  algorithm: "FIFO" as PageAlgorithm,
  frames: 3,
  sequenceText: "7,0,1,2,0,3,0,4,2,3,0,3,2",
  nodeName: undefined as string | undefined,
  running: false,
  predicting: false,
  error: "",
  result: null as any,
  predictedFaultText: "hit",
  predictedEvicted: undefined as number | undefined,
  prediction: null as any,
  /** 运行时冻结的输入快照：结果、回放、槽位与预测都绑定它 */
  runConfig: null as PageRunConfig | null,
});

// 逐步回放：revealed = 已揭示步数；预测目标始终是第一个未揭示的步骤
const revealed = ref(0);
const playing = ref(false);
let playTimer: number | null = null;
const PLAY_INTERVAL_MS = 900;

const currentStep = computed(() =>
  page.result && revealed.value > 0
    ? page.result.steps[revealed.value - 1]
    : null
);
const revealedSteps = computed(() =>
  page.result ? page.result.steps.slice(0, revealed.value) : []
);
const nextStep = computed(() =>
  page.result ? page.result.steps[revealed.value] ?? null : null
);
const allStepsRevealed = computed(
  () => Boolean(page.result) && revealed.value >= page.result.steps.length
);

// ---------- 冻结配置的展示与脏检查 ----------
const runConfigFrameCount = computed(() => page.runConfig?.frames ?? 0);

const pageRunConfigSummary = computed(() => {
  const config = page.runConfig;
  if (!config) return "—";
  return `${config.algorithm} · 物理块 ${
    config.frames
  } · 序列 [${config.accessSequence.join(",")}]`;
});

const cpuRunConfigSummary = computed(() => {
  const config = cpu.runConfig;
  if (!config) return "—";
  const timeSlice =
    config.algorithm === "RR" && config.timeSlice
      ? ` · 时间片 ${config.timeSlice}`
      : "";
  return `${config.algorithm}${timeSlice} · 进程 [${config.processes
    .map((p) => p.name)
    .join(", ")}]`;
});

const parseSequenceText = (text: string): number[] | null => {
  const seq = text
    .split(/[,，\s]+/)
    .filter(Boolean)
    .map(Number);
  if (!seq.length || seq.some((n) => Number.isNaN(n) || n < 0)) return null;
  return seq;
};

const pageInputsMatch = (config: PageRunConfig) => {
  if (page.algorithm !== config.algorithm || page.frames !== config.frames) {
    return false;
  }
  if ((page.nodeName || undefined) !== (config.nodeName || undefined)) {
    return false;
  }
  const current = parseSequenceText(page.sequenceText);
  if (!current) return false;
  if (current.length !== config.accessSequence.length) return false;
  return current.every((n, i) => n === config.accessSequence[i]);
};

const pageConfigDirty = computed(
  () =>
    Boolean(page.result) &&
    (!page.runConfig || !pageInputsMatch(page.runConfig))
);

const currentCpuProcesses = (): CpuProcessInput[] =>
  cpu.processes.map((p) => ({
    name: (p.name || "").trim(),
    arrivalTime: p.arrivalTime,
    burstTime: p.burstTime,
  }));

const cpuInputsMatch = (config: CpuRunConfig) => {
  if (cpu.algorithm !== config.algorithm) return false;
  const timeSlice = cpu.algorithm === "RR" ? cpu.timeSlice : undefined;
  if (timeSlice !== config.timeSlice) return false;
  if ((cpu.nodeName || undefined) !== (config.nodeName || undefined)) {
    return false;
  }
  const current = currentCpuProcesses();
  if (current.length !== config.processes.length) return false;
  return current.every((p, i) => {
    const other = config.processes[i];
    return (
      p.name === other.name &&
      p.arrivalTime === other.arrivalTime &&
      p.burstTime === other.burstTime
    );
  });
};

const cpuConfigDirty = computed(
  () =>
    Boolean(cpu.result) && (!cpu.runConfig || !cpuInputsMatch(cpu.runConfig))
);

const nodeName = computed(
  () => (activeTab.value === "cpu" ? cpu.nodeName : page.nodeName) || ""
);

const stepForward = () => {
  if (!page.result || revealed.value >= page.result.steps.length) return;
  revealed.value += 1;
};

const play = () => {
  if (playing.value || !page.result) return;
  playing.value = true;
  playTimer = window.setInterval(() => {
    if (!page.result || revealed.value >= page.result.steps.length) {
      pause();
      return;
    }
    revealed.value += 1;
  }, PLAY_INTERVAL_MS);
};

const pause = () => {
  playing.value = false;
  if (playTimer !== null) {
    window.clearInterval(playTimer);
    playTimer = null;
  }
};

const resetPlayback = () => {
  pause();
  revealed.value = 0;
  page.prediction = null;
};

// ---------- 算法对比（不写学习证据） ----------
interface CompareRow {
  algorithm: string;
  pageFaults?: number;
  hitRate?: number;
}

const compare = reactive({
  running: false,
  error: "",
  rows: [] as CompareRow[],
  /** 对比运行时冻结的输入；输入变化后标记需重新运行对比 */
  runConfig: null as { frames: number; accessSequence: number[] } | null,
});

const compareRunConfigSummary = computed(() => {
  const config = compare.runConfig;
  if (!config) return "—";
  return `物理块 ${config.frames} · 序列 [${config.accessSequence.join(",")}]`;
});

const compareStale = computed(() => {
  const config = compare.runConfig;
  if (!compare.rows.length || !config) return false;
  if (page.frames !== config.frames) return true;
  const current = parseSequenceText(page.sequenceText);
  if (!current) return true;
  if (current.length !== config.accessSequence.length) return true;
  return current.some((n, i) => n !== config.accessSequence[i]);
});

const addProcess = () => {
  cpu.processes.push({
    name: `P${cpu.processes.length + 1}`,
    arrivalTime: 0,
    burstTime: 1,
  });
};

const ganttColors = [
  "#5b8ff9",
  "#5ad8a6",
  "#f6bd16",
  "#e8684a",
  "#6dc8ec",
  "#9270ca",
];
const ganttColor = (process: string) => {
  const index = cpu.processes.findIndex((p) => p.name === process);
  return ganttColors[(index < 0 ? 0 : index) % ganttColors.length];
};

const buildCpuRequestBody = (config: CpuRunConfig, withPrediction: boolean) => {
  return {
    algorithm: config.algorithm,
    timeSlice: config.algorithm === "RR" ? config.timeSlice : undefined,
    processes: config.processes,
    nodeName: config.nodeName,
    ...(withPrediction
      ? {
          predictIndex: cpu.predictIndex,
          predictedProcess: cpu.predictedProcess.trim(),
        }
      : {}),
  };
};

const validateCpuProcesses = (): string => {
  if (!cpu.processes.length) return "请至少添加一个进程";
  const names = new Set<string>();
  for (const p of cpu.processes) {
    const name = (p.name || "").trim();
    if (!name) return "每个进程都需要非空的进程名";
    if (names.has(name)) return `进程名重复：${name}`;
    names.add(name);
    if (!Number.isFinite(p.arrivalTime) || p.arrivalTime < 0) {
      return `进程 ${name} 的到达时间需为不小于 0 的数字`;
    }
    if (!Number.isFinite(p.burstTime) || p.burstTime < 1) {
      return `进程 ${name} 的运行时间需不小于 1`;
    }
  }
  return "";
};

const runCpu = async () => {
  const invalid = validateCpuProcesses();
  if (invalid) {
    cpu.error = invalid;
    return;
  }
  cpu.error = "";
  cpu.running = true;
  try {
    const config: CpuRunConfig = {
      algorithm: cpu.algorithm,
      timeSlice: cpu.algorithm === "RR" ? cpu.timeSlice : undefined,
      processes: currentCpuProcesses(),
      nodeName: cpu.nodeName,
    };
    const res = await runCpuSimulation(buildCpuRequestBody(config, false));
    if (res.data?.code === 0) {
      cpu.result = res.data.data;
      cpu.runConfig = snapshotConfig(config);
      cpu.prediction = null;
    } else {
      cpu.error = res.data?.message || "模拟失败";
    }
  } catch (e) {
    cpu.error = "模拟失败，请稍后重试";
  } finally {
    cpu.running = false;
  }
};

const predictCpu = async () => {
  if (!cpu.result || !cpu.runConfig) return;
  if (cpu.predictedProcess.trim() === "") {
    cpu.error = "请先填写预测的进程名";
    return;
  }
  if (cpuConfigDirty.value) {
    cpu.error = "配置已修改，请先重新运行，再提交预测";
    return;
  }
  cpu.predicting = true;
  try {
    // 预测请求使用冻结的 runConfig，禁止新输入配旧结果
    const res = await runCpuSimulation(
      buildCpuRequestBody(cpu.runConfig, true)
    );
    if (res.data?.code === 0) {
      cpu.prediction = res.data.data?.prediction;
      if (cpu.prediction?.eventId) {
        Message.success("预测已计入学习证据");
      }
    } else {
      cpu.error = res.data?.message || "预测判分失败";
    }
  } catch (e) {
    cpu.error = "预测判分失败，请稍后重试";
  } finally {
    cpu.predicting = false;
  }
};

const runPage = async () => {
  const accessSequence = parseSequenceText(page.sequenceText);
  if (!accessSequence) {
    page.error =
      "访问序列格式不正确：请输入以逗号或空格分隔的非负整数，如 7,0,1,2,0,3";
    return;
  }
  if (!Number.isFinite(page.frames) || page.frames < 1) {
    page.error = "物理块数需为 1–10 的整数";
    return;
  }
  page.error = "";
  page.running = true;
  try {
    const config: PageRunConfig = {
      algorithm: page.algorithm,
      frames: page.frames,
      accessSequence,
      nodeName: page.nodeName,
    };
    const res = await runPageSimulation({
      algorithm: config.algorithm,
      frames: config.frames,
      accessSequence: config.accessSequence,
      nodeName: config.nodeName,
    });
    if (res.data?.code === 0) {
      page.result = res.data.data;
      // 冻结本次运行配置：结果、回放、槽位与预测都绑定这份快照
      page.runConfig = snapshotConfig(config);
      page.prediction = null;
      resetPlayback();
    } else {
      page.error = res.data?.message || "模拟失败";
    }
  } catch (e) {
    page.error = "模拟失败，请稍后重试";
  } finally {
    page.running = false;
  }
};

const runCompare = async () => {
  const accessSequence = parseSequenceText(page.sequenceText);
  if (!accessSequence) {
    compare.error =
      "访问序列格式不正确：请输入以逗号或空格分隔的非负整数，如 7,0,1,2,0,3";
    return;
  }
  if (!Number.isFinite(page.frames) || page.frames < 1) {
    compare.error = "物理块数需为 1–10 的整数";
    return;
  }
  compare.error = "";
  compare.running = true;
  compare.rows = [];
  try {
    const algorithms = ["FIFO", "LRU", "OPT"] as const;
    // 对比运行不带节点与预测参数：服务端不会写学习证据
    const responses = await Promise.all(
      algorithms.map((algorithm) =>
        runPageSimulation({
          algorithm,
          frames: page.frames,
          accessSequence,
        })
      )
    );
    compare.rows = responses.map((res, index) => ({
      algorithm: algorithms[index],
      pageFaults: res.data?.data?.pageFaults,
      hitRate: res.data?.data?.hitRate,
    }));
    if (
      compare.rows.some(
        (row) => row.pageFaults === undefined || row.hitRate === undefined
      )
    ) {
      compare.error = "对比运行失败，请稍后重试";
      compare.rows = [];
    } else {
      compare.runConfig = snapshotConfig({
        frames: page.frames,
        accessSequence,
      });
    }
  } catch (e) {
    compare.error = "对比运行失败，请稍后重试";
  } finally {
    compare.running = false;
  }
};

const predictPage = async () => {
  if (!page.result || !page.runConfig) return;
  if (pageConfigDirty.value) {
    page.error = "配置已修改，请先重新运行，再提交预测";
    return;
  }
  const target = revealed.value;
  if (!page.result.steps[target]) {
    page.error = "预测步骤不存在，请重置回放后再试";
    return;
  }
  page.predicting = true;
  try {
    // 预测请求使用冻结的 runConfig，禁止新输入配旧结果
    const res = await runPageSimulation({
      algorithm: page.runConfig.algorithm,
      frames: page.runConfig.frames,
      accessSequence: page.runConfig.accessSequence,
      nodeName: page.runConfig.nodeName,
      predictStep: target,
      predictedFault: page.predictedFaultText === "fault",
      predictedEvicted: page.predictedEvicted,
    });
    if (res.data?.code === 0) {
      page.prediction = res.data.data?.prediction;
      // 提交预测后揭示该步，展示服务端结果
      revealed.value = target + 1;
      if (page.prediction?.eventId) {
        Message.success("预测已计入学习证据");
      }
    } else {
      page.error = res.data?.message || "预测判分失败";
    }
  } catch (e) {
    page.error = "预测判分失败，请稍后重试";
  } finally {
    page.predicting = false;
  }
};

// ---------- 预测反馈文案：是否计入学习证据以 eventId 为准 ----------
const pagePredictionType = computed(() =>
  page.prediction?.faultCorrect && page.prediction?.evictedCorrect
    ? "success"
    : "error"
);

const pagePredictionText = computed(() => {
  const p = page.prediction;
  if (!p) return "";
  if (p.faultCorrect && p.evictedCorrect) {
    return p.eventId
      ? "预测正确！已计入学习证据"
      : "预测正确（演示结果，未计入学习证据）";
  }
  const actual = `真实结果为 ${p.actualFault ? "缺页" : "命中"}`;
  const evicted =
    p.actualEvicted !== null && p.actualEvicted !== undefined
      ? `，淘汰 ${p.actualEvicted}`
      : "";
  return `预测错误：${actual}${evicted}`;
});

const cpuPredictionText = computed(() => {
  const p = cpu.prediction;
  if (!p) return "";
  if (p.correct) {
    return p.eventId
      ? "预测正确！已计入学习证据"
      : "预测正确（演示结果，未计入学习证据）";
  }
  return `预测错误：该时刻应运行 ${p.expectedProcess ?? "（空闲）"}`;
});

const loadNodeNames = async () => {
  try {
    const res =
      await KnowledgeGraphControllerService.getAllKnowledgeGraphNameUsingGet();
    if (res.code === 0) {
      nodeNames.value = res.data || [];
    }
  } catch (e) {
    // 可选
  }
};

// ---------- 跳转（预测行动入口绑定冻结配置中的节点） ----------
const goNode = (target?: string) => {
  const node = target || nodeName.value;
  if (!node) return;
  router.push({ path: `/knowledge/node/${encodeURIComponent(node)}` });
};

const goExperiments = (target?: string) => {
  const node = target || nodeName.value;
  router.push({
    path: "/experiments",
    query: node ? { nodeName: node } : {},
  });
};

onMounted(() => {
  const q = route.query;
  const qNode = typeof q.nodeName === "string" ? q.nodeName : "";
  if (q.tab === "cpu") {
    activeTab.value = "cpu";
    if (qNode) {
      cpu.nodeName = qNode;
    }
  } else {
    // 默认页面置换；带 nodeName 的节点入口也落在页面置换
    activeTab.value = "page";
    if (qNode) {
      page.nodeName = qNode;
    }
  }
});
onMounted(() => {
  loadNodeNames();
});

onBeforeUnmount(() => {
  pause();
});
</script>

<style scoped>
.simulation-view {
  max-width: 1200px;
  margin: 0 auto;
}
.run-config-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--oslab-text-secondary, #86909c);
  font-size: 12px;
}
.gantt {
  display: flex;
  gap: 4px;
}
.gantt-segment {
  min-width: 48px;
  color: #fff;
  text-align: center;
  padding: 12px 4px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
}
.playback-bar {
  margin-bottom: 12px;
}
.progress-text {
  color: var(--color-text-3);
  font-size: 13px;
}
.current-frame {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 4px;
}
.frame-label {
  color: var(--color-text-2);
  font-weight: 500;
}
.frame-grid-caption {
  width: 100%;
  color: var(--oslab-text-secondary, #86909c);
  font-size: 12px;
}
.frame-grid {
  display: flex;
  gap: 8px;
}
.frame-slot {
  width: 44px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--color-border-2);
  border-radius: 4px;
  color: var(--color-text-4);
  font-weight: 600;
}
.frame-slot.filled {
  border-style: solid;
  color: var(--color-text-1);
  background: var(--color-bg-2);
}
.frame-slot.active {
  border-color: rgb(var(--green-6));
  color: rgb(var(--green-6));
}
.side-hint {
  color: var(--color-text-3);
  font-size: 12px;
  margin-top: 8px;
}
</style>
