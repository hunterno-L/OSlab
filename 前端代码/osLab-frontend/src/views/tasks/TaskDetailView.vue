<template>
  <div v-if="task" class="task-detail">
    <a-card class="task-head">
      <div class="head-top">
        <a-button size="small" @click="goBack">返回任务列表</a-button>
        <a-space>
          <a-tag :color="task.taskStatus === 'PUBLISHED' ? 'arcoblue' : 'gray'">
            {{ taskStatusText }}
          </a-tag>
          <a-tag v-if="task.status" :color="recipientColor">{{
            recipientText
          }}</a-tag>
          <a-tag v-if="task.overdue" color="red">已逾期</a-tag>
        </a-space>
      </div>
      <h1 class="task-title">{{ task.title }}</h1>
      <div v-if="task.description" class="task-description">
        {{ task.description }}
      </div>
      <a-descriptions :column="3" size="small" class="task-meta">
        <a-descriptions-item label="发布教师">{{
          task.teacherName || "—"
        }}</a-descriptions-item>
        <a-descriptions-item label="截止时间">
          {{ task.dueAt ? formatTime(task.dueAt) : "不限" }}
          <span v-if="task.allowLate" class="muted">（允许逾期）</span>
        </a-descriptions-item>
        <a-descriptions-item label="当前轮次">
          第 {{ task.currentRound || 1 }} 轮
          <span v-if="task.status === 'RETURNED'" class="muted"
            >（教师已退回）</span
          >
        </a-descriptions-item>
      </a-descriptions>
      <a-alert
        v-for="(note, index) in task.supplements || []"
        :key="index"
        type="info"
        class="supplement"
      >
        {{ note.content }}
      </a-alert>
      <a-alert v-if="latestReturnReason" type="warning" class="supplement">
        退回原因：{{ latestReturnReason }}
      </a-alert>
      <div v-if="task.status === 'PENDING'" class="accept-bar">
        <span>阅读任务详情不会自动接收；接收后才能保存进度与提交。</span>
        <a-button type="primary" :loading="accepting" @click="handleAccept"
          >接收任务</a-button
        >
      </div>
    </a-card>

    <a-card
      v-for="(item, index) in task.items"
      :id="`item-${item.itemCode}`"
      :key="item.itemCode"
      class="task-item"
    >
      <template #title>
        <div class="item-title">
          <span class="item-index">{{ index + 1 }}</span>
          <span>{{ item.content?.title || item.itemType }}</span>
          <a-tag size="small">{{ typeText(item.itemType) }}</a-tag>
          <a-tag v-if="item.required" size="small" color="orange">必做</a-tag>
          <a-tag
            v-if="item.maxScore !== null && item.maxScore !== undefined"
            size="small"
            color="green"
          >
            {{ formatNumber(item.maxScore) }} 分
          </a-tag>
          <span v-if="states[item.itemCode]?.savedAt" class="saved-hint">
            已保存 {{ states[item.itemCode].savedAt }}
          </span>
        </div>
      </template>

      <!-- 文字说明 -->
      <template v-if="item.itemType === 'TEXT'">
        <MdViewer
          v-if="item.content.contentMd"
          :value="item.content.contentMd"
        />
        <template v-if="editable(item)">
          <div v-if="item.content.requireText" class="field-block">
            <div class="field-label">
              {{ item.content.textPrompt || "文字答复" }}
            </div>
            <a-textarea
              v-model="states[item.itemCode].text"
              :auto-size="{ minRows: 4 }"
              placeholder="填写你的答复"
              @blur="saveItem(item)"
            />
          </div>
          <div v-if="item.content.requireFile" class="field-block">
            <div class="field-label">上传文件（必填）</div>
            <file-list
              :item="item"
              :state="states[item.itemCode]"
              @upload="handleUpload"
              @remove="removeFile"
            />
          </div>
          <a-checkbox
            v-if="item.content.requireConfirm"
            v-model="states[item.itemCode].confirm"
            class="confirm-box"
            @change="toggleConfirm(item)"
          >
            我已阅读并确认本项内容
          </a-checkbox>
        </template>
        <a-alert
          v-else-if="
            !item.content.requireText &&
            !item.content.requireFile &&
            !item.content.requireConfirm
          "
          type="normal"
          class="read-only-hint"
        >
          正式提交任务即视为确认完成本项。
        </a-alert>
      </template>

      <!-- 资料 -->
      <template v-else-if="item.itemType === 'MATERIAL'">
        <MdViewer
          v-if="item.content.description"
          :value="item.content.description"
        />
        <div class="file-list">
          <a
            v-for="file in item.content.files || []"
            :key="file.fileId"
            class="file-row"
            :href="file.downloadPath"
            target="_blank"
            rel="noopener"
          >
            <icon-file /> {{ file.originalName }}
            <span class="muted">{{ humanSize(file.size) }}</span>
          </a>
        </div>
        <template v-if="editable(item)">
          <a-checkbox
            v-if="item.content.requireConfirm"
            v-model="states[item.itemCode].confirm"
            class="confirm-box"
            @change="toggleConfirm(item)"
          >
            我已阅读资料
          </a-checkbox>
          <div v-if="item.content.requireFile" class="field-block">
            <div class="field-label">提交作业文件（必填）</div>
            <file-list
              :item="item"
              :state="states[item.itemCode]"
              @upload="handleUpload"
              @remove="removeFile"
            />
          </div>
        </template>
      </template>

      <!-- 客观题 -->
      <template v-else-if="item.itemType === 'QUESTION'">
        <MdViewer :value="item.content.content || ''" />
        <div class="question-answer">
          <a-radio-group
            v-if="item.content.type === 'JUDGE'"
            v-model="states[item.itemCode].answer"
            :disabled="!editable(item)"
          >
            <a-radio value="true">正确</a-radio>
            <a-radio value="false">错误</a-radio>
          </a-radio-group>
          <a-radio-group
            v-else-if="item.content.type === 'SINGLE'"
            v-model="states[item.itemCode].answer"
            :disabled="!editable(item)"
            direction="vertical"
          >
            <a-radio
              v-for="option in item.content.options || []"
              :key="option.key"
              :value="option.key"
            >
              <strong>{{ option.key }}.</strong>
              <MdViewer :value="option.value" class="option-md" />
            </a-radio>
          </a-radio-group>
          <a-checkbox-group
            v-else-if="item.content.type === 'MULTIPLE'"
            v-model="states[item.itemCode].answer"
            :disabled="!editable(item)"
            direction="vertical"
          >
            <a-checkbox
              v-for="option in item.content.options || []"
              :key="option.key"
              :value="option.key"
            >
              <strong>{{ option.key }}.</strong>
              <MdViewer :value="option.value" class="option-md" />
            </a-checkbox>
          </a-checkbox-group>
          <a-textarea
            v-else
            v-model="states[item.itemCode].answer"
            :disabled="!editable(item)"
            :auto-size="{ minRows: 2 }"
            placeholder="填写答案"
          />
        </div>
        <div v-if="editable(item)" class="inline-actions">
          <a-button
            type="primary"
            :loading="states[item.itemCode].checking"
            @click="checkAnswer(item)"
          >
            检查答案
          </a-button>
          <span class="muted"
            >检查后才计入正式提交；未检查的答案只作为草稿保存。</span
          >
        </div>
        <a-alert
          v-if="states[item.itemCode].attempt"
          :type="states[item.itemCode].attempt.correct ? 'success' : 'warning'"
          class="result-alert"
        >
          <template #title>
            {{
              states[item.itemCode].attempt.correct ? "回答正确" : "回答错误"
            }}
          </template>
          <div v-if="states[item.itemCode].attempt.analysis">
            <strong>解析：</strong>{{ states[item.itemCode].attempt.analysis }}
          </div>
        </a-alert>
      </template>

      <!-- 编程实验 / 实践报告 -->
      <template v-else-if="item.itemType === 'EXPERIMENT'">
        <MdViewer :value="item.content.content || ''" />
        <div v-if="item.content.manualReview">
          <a-alert type="normal" class="read-only-hint">
            本实验由教师人工评阅，不执行代码判题。
          </a-alert>
          <div
            v-if="item.content.requireText || editable(item)"
            class="field-block"
          >
            <div class="field-label">
              {{ item.content.requireText ? "实验说明（必填）" : "补充说明" }}
            </div>
            <a-textarea
              v-model="states[item.itemCode].text"
              :disabled="!editable(item)"
              :auto-size="{ minRows: 5 }"
              placeholder="填写实验过程、结论或说明"
            />
          </div>
          <div v-if="item.content.requireFile" class="field-block">
            <div class="field-label">上传报告文件（必填）</div>
            <file-list
              :item="item"
              :state="states[item.itemCode]"
              @upload="handleUpload"
              @remove="removeFile"
            />
          </div>
        </div>
        <template v-else>
          <div v-if="(item.content.samples || []).length" class="samples">
            <div class="field-label">样例</div>
            <a-table
              :data="item.content.samples"
              :pagination="false"
              size="small"
            >
              <template #columns>
                <a-table-column title="输入" data-index="input" />
                <a-table-column title="输出" data-index="output" />
              </template>
            </a-table>
          </div>
          <div class="code-head">
            <a-space>
              <span class="field-label">编程语言</span>
              <a-select
                v-model="states[item.itemCode].language"
                :disabled="!editable(item)"
                style="width: 130px"
              >
                <a-option
                  v-for="language in item.content.languages || [
                    'java',
                    'cpp',
                    'c',
                    'go',
                  ]"
                  :key="language"
                  :value="language"
                >
                  {{ language }}
                </a-option>
              </a-select>
              <a-button
                size="small"
                :disabled="!editable(item)"
                @click="resetCode(item)"
              >
                使用初始代码
              </a-button>
            </a-space>
            <a-space>
              <a-button
                type="primary"
                :loading="states[item.itemCode].submitting"
                :disabled="!editable(item)"
                @click="submitCode(item)"
              >
                提交判题
              </a-button>
            </a-space>
          </div>
          <div class="code-wrap">
            <CodeEditor
              v-if="editable(item)"
              :value="states[item.itemCode].code"
              :language="states[item.itemCode].language"
              :handle-change="(value: string) => (states[item.itemCode].code = value)"
            />
            <pre v-else class="code-block">{{
              states[item.itemCode].code
            }}</pre>
          </div>
          <a-alert
            v-if="states[item.itemCode].codeSubmission"
            :type="judgeAlertType(item)"
            class="result-alert"
          >
            <template #title> 判题状态：{{ judgeStatusText(item) }} </template>
            <div v-if="judgeMessage(item)">
              判题结果：{{ judgeMessage(item) }}
            </div>
            <div id="code-result" class="muted">
              判题由服务器异步执行；提交任务前必须等待当前判题结束。
            </div>
          </a-alert>
        </template>
      </template>
    </a-card>

    <div class="action-bar">
      <div class="action-info">
        <span v-if="task.status === 'SUBMITTED'">已提交，等待教师批阅。</span>
        <span v-else-if="task.status === 'REVIEWED'"
          >已批阅，可查看历史与反馈。</span
        >
        <span v-else-if="task.status === 'RETURNED'"
          >教师已退回，请修改后重新提交。</span
        >
        <span v-else-if="!task.allowed?.canWork">接收任务后可保存与提交。</span>
        <span v-else
          >当前第 {{ task.editableRound || 1 }} 轮，可随时保存进度。</span
        >
      </div>
      <a-space>
        <a-button @click="openHistory">历次提交</a-button>
        <a-button
          v-if="task.allowed?.canWork"
          :loading="savingAll"
          @click="saveAll"
        >
          保存进度
        </a-button>
        <a-button
          v-if="task.allowed?.canSubmit"
          type="primary"
          :loading="submitting"
          @click="confirmSubmit"
        >
          提交任务
        </a-button>
      </a-space>
    </div>

    <a-drawer
      v-model:visible="historyVisible"
      title="历次正式提交"
      :width="520"
      :footer="false"
    >
      <a-spin :loading="historyLoading" style="width: 100%">
        <a-empty v-if="!history.length" description="还没有正式提交" />
        <div v-else class="history-list">
          <button
            v-for="entry in history"
            :key="entry.submissionId"
            type="button"
            class="history-row"
            @click="openSubmission(entry.submissionId)"
          >
            <div class="history-main">
              <strong>第 {{ entry.roundNo }} 轮</strong>
              <a-tag size="small" :color="statusColor(entry.status)">{{
                statusText(entry.status)
              }}</a-tag>
              <a-tag v-if="entry.isLate" size="small" color="red">逾期</a-tag>
            </div>
            <div class="history-meta">
              <span>{{ formatTime(entry.submittedAt) }}</span>
              <span
                v-if="
                  taskScored &&
                  entry.totalScore !== null &&
                  entry.totalScore !== undefined
                "
              >
                {{ formatNumber(entry.totalScore) }} 分
              </span>
              <span v-else-if="!taskScored">已完成</span>
            </div>
            <div v-if="entry.returnReason" class="history-return">
              退回原因：{{ entry.returnReason }}
            </div>
            <div v-else-if="entry.comment" class="history-return">
              教师评语：{{ entry.comment }}
            </div>
          </button>
        </div>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:visible="submissionVisible"
      :title="`第 ${submission?.roundNo || ''} 轮提交`"
      :footer="false"
      :width="820"
    >
      <a-spin :loading="submissionLoading" style="width: 100%">
        <template v-if="submission">
          <a-descriptions :column="3" size="small" class="submission-meta">
            <a-descriptions-item label="状态">{{
              statusText(submission.status)
            }}</a-descriptions-item>
            <a-descriptions-item label="自动得分">
              {{ taskScored ? submission.autoScore ?? "—" : "不计分" }}
            </a-descriptions-item>
            <a-descriptions-item label="总分">
              {{
                taskScored
                  ? submission.totalScore ??
                    (submission.status === "SUBMITTED" ? "待批阅" : "—")
                  : "已完成"
              }}
            </a-descriptions-item>
          </a-descriptions>
          <a-alert
            v-if="submission.review?.returnReason"
            type="warning"
            class="supplement"
          >
            退回原因：{{ submission.review.returnReason }}
          </a-alert>
          <a-alert
            v-if="submission.review?.overallComment"
            type="info"
            class="supplement"
          >
            教师评语：{{ submission.review.overallComment }}
          </a-alert>
          <div
            v-for="entry in submission.items || []"
            :key="entry.itemCode"
            class="submission-item"
          >
            <div class="submission-item-head">
              <strong>{{ entry.title }}</strong>
              <span class="muted">{{ typeText(entry.itemType) }}</span>
              <a-tag v-if="entry.manual" size="small" color="orange"
                >人工批阅</a-tag
              >
              <span v-if="entry.autoScore !== undefined" class="muted">
                自动得分 {{ entry.autoScore }}
              </span>
            </div>
            <div v-if="entry.answer !== undefined" class="submission-line">
              <span class="label">作答</span>
              <code>{{ renderAnswer(entry.answer) }}</code>
            </div>
            <div v-if="entry.correct !== undefined" class="submission-line">
              <span class="label">结果</span>
              <a-tag :color="entry.correct ? 'green' : 'red'">{{
                entry.correct ? "正确" : "错误"
              }}</a-tag>
            </div>
            <div v-if="entry.passed !== undefined" class="submission-line">
              <span class="label">实验</span>
              <a-tag :color="entry.passed ? 'green' : 'red'">{{
                entry.passed ? "通过" : "未通过"
              }}</a-tag>
            </div>
            <div v-if="entry.textResponse" class="submission-line">
              <span class="label">文字</span>
              <span class="pre-wrap">{{ entry.textResponse }}</span>
            </div>
            <div v-if="(entry.files || []).length" class="submission-line">
              <span class="label">文件</span>
              <a
                v-for="file in entry.files"
                :key="file.fileId"
                class="file-inline"
                :href="file.downloadPath"
                target="_blank"
                rel="noopener"
              >
                {{ file.originalName }}
              </a>
            </div>
            <div v-if="entry.code?.code" class="submission-line">
              <span class="label">代码</span>
              <pre class="code-block">{{ entry.code.code }}</pre>
            </div>
            <div v-if="reviewItem(entry.itemCode)" class="submission-line">
              <span class="label">批阅</span>
              <span>
                {{ reviewItem(entry.itemCode).score }} 分
                <span v-if="reviewItem(entry.itemCode).comment">
                  · {{ reviewItem(entry.itemCode).comment }}
                </span>
              </span>
            </div>
          </div>
        </template>
      </a-spin>
    </a-modal>
  </div>
  <a-spin v-else :loading="loading" class="page-loading" />
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Message, Modal } from "@arco-design/web-vue";
import MdViewer from "@/components/MdViewer.vue";
import CodeEditor from "@/components/CodeEditor.vue";
import FileList from "./components/TaskFileList.vue";
import {
  acceptTask,
  answerTaskQuestion,
  getTaskCodeStatus,
  getTaskDetail,
  getTaskHistory,
  getTaskSubmission,
  saveTaskWork,
  submitTask,
  submitTaskCode,
  uploadTaskFile,
  type TaskDetail,
  type TaskItem,
} from "@/api/tasks";

interface ItemState {
  answer: any;
  text: string;
  code: string;
  language: string;
  fileIds: string[];
  files: any[];
  confirm: boolean;
  attempt: any;
  codeSubmission: any;
  savedAt: string;
  checking: boolean;
  submitting: boolean;
}

const route = useRoute();
const router = useRouter();
const taskCode = computed(() => String(route.params.taskCode || ""));
const task = ref<TaskDetail | null>(null);
const loading = ref(false);
const accepting = ref(false);
const savingAll = ref(false);
const submitting = ref(false);
const historyVisible = ref(false);
const historyLoading = ref(false);
const history = ref<any[]>([]);
const submissionVisible = ref(false);
const submissionLoading = ref(false);
const submission = ref<any>(null);
const states = reactive<Record<string, ItemState>>({});
const pollTimers: number[] = [];

const latestReturnReason = computed(() => {
  const entry = (task.value?.history || []).find(
    (row: any) => row.returnReason
  );
  return entry?.returnReason || "";
});
const taskScored = computed(() =>
  (task.value?.items || []).some(
    (item) => item.maxScore !== null && item.maxScore !== undefined
  )
);

const taskStatusText = computed(() => {
  const map: Record<string, string> = {
    DRAFT: "草稿",
    PUBLISHED: "进行中",
    CLOSED: "已关闭",
  };
  return map[task.value?.taskStatus || ""] || task.value?.taskStatus || "";
});

const recipientText = computed(() => statusText(task.value?.status || ""));
const recipientColor = computed(() => statusColor(task.value?.status || ""));

const initStates = () => {
  if (!task.value) return;
  for (const key of Object.keys(states)) delete states[key];
  for (const item of task.value.items || []) {
    const work = (item as any).work || {};
    const content = item.content || {};
    let answer: any = work.answer ?? null;
    if (content.type === "MULTIPLE" && !Array.isArray(answer)) answer = [];
    if (
      (content.type === "JUDGE" || content.type === "SINGLE") &&
      typeof answer !== "string"
    )
      answer = "";
    if (content.type === "FILL_BLANK" && answer && typeof answer === "object") {
      answer = JSON.stringify(answer);
    }
    const codeDraft =
      answer &&
      typeof answer === "object" &&
      !Array.isArray(answer) &&
      answer.code
        ? answer.code
        : "";
    states[item.itemCode] = {
      answer,
      text: work.textResponse || "",
      code: codeDraft || content.originalCode || "",
      language:
        (answer && typeof answer === "object" && answer.language) ||
        (content.languages || [])[0] ||
        "java",
      fileIds: (work.files || []).map((file: any) => String(file.fileId)),
      files: work.files || [],
      confirm: Boolean(answer && typeof answer === "object" && answer.confirm),
      attempt: work.lastAttempt
        ? {
            correct: work.lastAttempt.correct,
            analysis: work.lastAttempt.analysis,
            attemptId: work.lastAttempt.attemptId,
          }
        : null,
      codeSubmission: work.lastCodeSubmission || null,
      savedAt: "",
      checking: false,
      submitting: false,
    };
  }
};

const load = async () => {
  loading.value = true;
  try {
    const res = await getTaskDetail(taskCode.value);
    if (res.data?.code === 0) {
      task.value = res.data.data;
      initStates();
      if (res.data.data?.status && res.data.data.status !== "PENDING") {
        await loadHistory(true);
      }
    } else {
      Message.error(res.data?.message || "任务加载失败");
    }
  } catch {
    Message.error("任务加载失败");
  } finally {
    loading.value = false;
  }
};

const loadHistory = async (silent = false) => {
  if (!silent) historyLoading.value = true;
  try {
    const res = await getTaskHistory(taskCode.value);
    if (res.data?.code === 0) history.value = res.data.data || [];
  } finally {
    historyLoading.value = false;
  }
};

const editable = (item: TaskItem) => Boolean(task.value?.allowed?.canWork);
const typeText = (type?: string) =>
  ((
    {
      TEXT: "文字",
      MATERIAL: "资料",
      QUESTION: "客观题",
      EXPERIMENT: "编程实验",
    } as Record<string, string>
  )[type || ""] ||
  type ||
  "");
const statusText = (status?: string) =>
  ((
    {
      PENDING: "待接收",
      IN_PROGRESS: "进行中",
      RETURNED: "退回修改",
      SUBMITTED: "待批阅",
      REVIEWED: "已批阅",
    } as Record<string, string>
  )[status || ""] ||
  status ||
  "");
const statusColor = (status?: string) =>
  ((
    {
      PENDING: "blue",
      IN_PROGRESS: "arcoblue",
      RETURNED: "orange",
      SUBMITTED: "gold",
      REVIEWED: "green",
    } as Record<string, string>
  )[status || ""] || "gray");

const formatTime = (value?: string) => {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate()
  )} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};
const formatNumber = (value: any) => {
  const number = Number(value);
  return Number.isFinite(number) ? number.toFixed(0) : "—";
};
const humanSize = (size?: number) => {
  if (!size) return "";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
};

const newKey = () => {
  if (typeof crypto !== "undefined" && crypto.randomUUID)
    return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const stamp = () => {
  const date = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

const goBack = () => router.push("/tasks");

const handleAccept = async () => {
  accepting.value = true;
  try {
    const res = await acceptTask(taskCode.value);
    if (res.data?.code === 0) {
      task.value = res.data.data;
      initStates();
      Message.success("已接收任务");
    } else {
      Message.error(res.data?.message || "接收失败");
    }
  } finally {
    accepting.value = false;
  }
};

const buildAnswer = (item: TaskItem) => {
  const state = states[item.itemCode];
  switch (item.content.type) {
    case "JUDGE":
    case "SINGLE":
      return state.answer || null;
    case "MULTIPLE":
      return Array.isArray(state.answer) ? [...state.answer].sort() : [];
    case "FILL_BLANK":
      return state.answer || "";
    default:
      return state.answer;
  }
};

const saveItem = async (item: TaskItem) => {
  if (!editable(item)) return false;
  const state = states[item.itemCode];
  try {
    const res = await saveTaskWork(taskCode.value, item.itemCode, {
      roundNo: task.value?.editableRound || 1,
      answer: state.answer,
      textResponse: state.text,
      fileIds: state.fileIds.map((id) => Number(id)),
    });
    if (res.data?.code === 0) {
      state.savedAt = stamp();
      return true;
    }
    Message.error(res.data?.message || "保存失败");
    return false;
  } catch {
    Message.error("保存失败");
    return false;
  }
};

const saveAll = async () => {
  if (!task.value) return;
  savingAll.value = true;
  try {
    for (const item of task.value.items) {
      if (await saveItem(item)) continue;
      break;
    }
    Message.success("进度已保存");
  } finally {
    savingAll.value = false;
  }
};

const checkAnswer = async (item: TaskItem) => {
  const state = states[item.itemCode];
  const answer = buildAnswer(item);
  if (
    answer === null ||
    answer === "" ||
    (Array.isArray(answer) && answer.length === 0)
  ) {
    Message.warning("请先作答");
    return;
  }
  state.checking = true;
  try {
    const res = await answerTaskQuestion(taskCode.value, item.itemCode, {
      roundNo: task.value?.editableRound || 1,
      submissionKey: newKey(),
      answer,
    });
    if (res.data?.code === 0) {
      state.attempt = res.data.data;
      state.savedAt = stamp();
      if (res.data.data.correct) Message.success("回答正确");
      else Message.warning("回答错误，可查看解析后继续尝试");
      await loadHistory(true);
    } else {
      Message.error(res.data?.message || "提交答案失败");
    }
  } finally {
    state.checking = false;
  }
};

const toggleConfirm = async (item: TaskItem) => {
  const state = states[item.itemCode];
  if (
    !state.answer ||
    typeof state.answer !== "object" ||
    Array.isArray(state.answer)
  ) {
    state.answer = {};
  }
  state.answer.confirm = state.confirm;
  await saveItem(item);
};

const handleUpload = async (option: any, item: TaskItem) => {
  const state = states[item.itemCode];
  try {
    const raw = option.fileItem?.file || option.file;
    const res = await uploadTaskFile(
      raw,
      taskCode.value,
      item.itemCode,
      "STUDENT_WORK"
    );
    if (res.data?.code === 0) {
      const meta = res.data.data;
      state.fileIds.push(String(meta.fileId));
      state.files.push(meta);
      await saveItem(item);
      option.onSuccess?.(meta);
      Message.success("上传成功");
    } else {
      Message.error(res.data?.message || "上传失败");
      option.onError?.();
    }
  } catch {
    Message.error("上传失败");
    option.onError?.();
  }
};

const removeFile = async (item: TaskItem, fileId: string) => {
  const state = states[item.itemCode];
  state.fileIds = state.fileIds.filter((id) => id !== fileId);
  state.files = state.files.filter((file) => String(file.fileId) !== fileId);
  await saveItem(item);
};

const resetCode = (item: TaskItem) => {
  states[item.itemCode].code = item.content.originalCode || "";
};

const submitCode = async (item: TaskItem) => {
  const state = states[item.itemCode];
  if (!state.code.trim()) {
    Message.warning("请先编写代码");
    return;
  }
  state.submitting = true;
  try {
    const res = await submitTaskCode(taskCode.value, item.itemCode, {
      roundNo: task.value?.editableRound || 1,
      submissionKey: newKey(),
      language: state.language,
      code: state.code,
    });
    if (res.data?.code === 0) {
      state.codeSubmission = { ...res.data.data, status: 0 };
      state.savedAt = stamp();
      Message.success("已提交判题，正在等待结果");
      pollCode(item, res.data.data.codeSubmissionId, 0);
    } else {
      Message.error(res.data?.message || "提交判题失败");
    }
  } finally {
    state.submitting = false;
  }
};

const pollCode = (item: TaskItem, id: string, attempt: number) => {
  if (attempt > 40) {
    Message.warning("判题时间较长，请稍后重新打开任务查看结果");
    return;
  }
  const timer = window.setTimeout(async () => {
    try {
      const res = await getTaskCodeStatus(taskCode.value, item.itemCode, id);
      if (res.data?.code === 0) {
        states[item.itemCode].codeSubmission = res.data.data;
        if (res.data.data.terminal) {
          const message = res.data.data.judgeInfo?.message || "判题结束";
          if (message === "Accepted") Message.success("判题通过");
          else Message.warning(`判题结果：${message}`);
          return;
        }
      }
    } catch {
      // 轮询失败时继续有限重试
    }
    pollCode(item, id, attempt + 1);
  }, 1500);
  pollTimers.push(timer);
};

const judgeStatusText = (item: TaskItem) => {
  const status = states[item.itemCode].codeSubmission?.status;
  if (status === 0) return "等待判题";
  if (status === 1) return "判题中";
  if (status === 2) return "判题完成";
  if (status === 3) return "判题系统故障";
  return "未知";
};
const judgeMessage = (item: TaskItem) =>
  states[item.itemCode].codeSubmission?.judgeInfo?.message || "";
const judgeAlertType = (item: TaskItem) => {
  const status = states[item.itemCode].codeSubmission?.status;
  if (status === 2)
    return judgeMessage(item) === "Accepted" ? "success" : "warning";
  if (status === 3) return "error";
  return "info";
};

const confirmSubmit = () => {
  Modal.confirm({
    title: "确认提交任务",
    content: "提交后将冻结本轮答案、代码与文件；如需继续修改需由教师退回。",
    okText: "确认提交",
    cancelText: "再检查一下",
    onOk: async () => {
      await saveAll();
      submitting.value = true;
      try {
        const res = await submitTask(taskCode.value, {
          roundNo: task.value?.editableRound || 1,
          submissionKey: newKey(),
        });
        if (res.data?.code === 0) {
          Message.success("任务已提交");
          await load();
          await openHistory();
          if (res.data.data?.submissionId) {
            await openSubmission(String(res.data.data.submissionId));
          }
        } else {
          Message.error(res.data?.message || "提交失败");
        }
      } catch {
        Message.error("提交失败");
      } finally {
        submitting.value = false;
      }
    },
  });
};

const openHistory = async () => {
  historyVisible.value = true;
  await loadHistory();
};

const openSubmission = async (id: string) => {
  submissionVisible.value = true;
  submissionLoading.value = true;
  try {
    const res = await getTaskSubmission(taskCode.value, id);
    if (res.data?.code === 0) submission.value = res.data.data;
  } finally {
    submissionLoading.value = false;
  }
};

const renderAnswer = (value: any) => {
  if (value === null || value === undefined) return "—";
  if (typeof value === "string") return value;
  return JSON.stringify(value);
};

const reviewItem = (itemCode: string) => {
  const items = submission.value?.review?.items || [];
  return items.find((row: any) => row.itemCode === itemCode);
};

onMounted(load);
onUnmounted(() => {
  for (const timer of pollTimers) window.clearTimeout(timer);
});
</script>

<style scoped>
.task-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 74px;
}
.task-head {
  border-radius: var(--oslab-radius-lg, 8px);
}
.head-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.task-title {
  margin: 14px 0 6px;
  font-size: 24px;
  font-weight: 600;
  color: var(--oslab-ink, #1d2129);
}
.task-description {
  color: var(--oslab-ink-soft, #37352f);
  margin-bottom: 10px;
}
.task-meta {
  margin-top: 8px;
}
.supplement {
  margin-top: 10px;
}
.accept-bar {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--oslab-primary-soft, #eaf0fd);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.task-item {
  border-radius: var(--oslab-radius-lg, 8px);
}
.item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.item-index {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--oslab-primary-soft, #eaf0fd);
  color: var(--oslab-primary, #2447d8);
  font-size: 12px;
  font-weight: 600;
}
.saved-hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--oslab-muted-strong, #757069);
}
.field-block {
  margin-top: 14px;
}
.field-label {
  font-weight: 600;
  color: var(--oslab-ink, #1d2129);
  margin-bottom: 6px;
}
.confirm-box {
  margin-top: 12px;
}
.read-only-hint {
  margin-top: 10px;
}
.file-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}
.file-row {
  display: flex;
  gap: 8px;
  align-items: center;
  color: var(--oslab-primary, #2447d8);
}
.question-answer {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.option-md {
  display: inline-block;
  margin-left: 4px;
}
.inline-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 12px;
}
.result-alert {
  margin-top: 12px;
}
.samples {
  margin-top: 12px;
}
.code-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 14px 0 8px;
  gap: 12px;
  flex-wrap: wrap;
}
.code-wrap {
  border-radius: 8px;
  overflow: hidden;
  margin-top: 8px;
}
.action-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: var(--oslab-panel, #fff);
  border: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.11));
  border-radius: var(--oslab-radius-lg, 8px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  z-index: 5;
}
.action-info {
  color: var(--oslab-muted-strong, #757069);
  font-size: 13px;
}
.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.history-row {
  text-align: left;
  border: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.11));
  border-radius: 8px;
  padding: 12px;
  background: var(--oslab-panel, #fff);
  cursor: pointer;
  font-family: inherit;
}
.history-main {
  display: flex;
  gap: 8px;
  align-items: center;
}
.history-meta {
  display: flex;
  gap: 12px;
  margin-top: 4px;
  color: var(--oslab-muted-strong, #757069);
  font-size: 12px;
}
.history-return {
  margin-top: 6px;
  color: rgb(180, 100, 0);
  font-size: 12px;
}
.submission-meta {
  margin-bottom: 10px;
}
.submission-item {
  padding: 12px 0;
  border-top: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.08));
}
.submission-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.submission-line {
  margin-top: 6px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.submission-line .label {
  flex: 0 0 48px;
  color: var(--oslab-muted-strong, #757069);
}
.pre-wrap {
  white-space: pre-wrap;
}
.code-block {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 10px;
  border-radius: 6px;
  font-size: 12px;
}
.file-inline {
  margin-right: 10px;
  color: var(--oslab-primary, #2447d8);
}
.muted {
  color: var(--oslab-muted-strong, #757069);
}
.page-loading {
  display: block;
  margin: 120px auto;
}
/* C+B 学习图册 */
.task-detail {
  background: transparent;
}
.task-head,
.task-item,
.field-block,
.question-answer,
.submission-item,
.code-wrap {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
}
.task-title {
  font-family: var(--oslab-font-display);
  font-size: 28px;
}
.action-bar {
  position: sticky;
  bottom: 12px;
  padding: 10px 12px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-overlay);
}

@media (max-width: 900px) {
  .accept-bar,
  .action-bar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
