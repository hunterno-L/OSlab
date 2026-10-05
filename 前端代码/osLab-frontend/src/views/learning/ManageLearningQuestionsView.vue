<template>
  <div class="manage-learning">
    <a-page-header
      title="题目管理与审核"
      sub-title="草稿 → 提交 → 审核 → 发布；学生仅可见已发布题目"
    >
      <template #extra>
        <a-space>
          <a-radio-group v-model:model-value="mode" type="button">
            <a-radio value="mine">我的题目</a-radio>
            <a-radio value="review">审核队列</a-radio>
          </a-radio-group>
          <a-button v-if="isAdmin" @click="openImport">JSON 导入</a-button>
          <a-button type="primary" @click="openCreate">新建题目</a-button>
        </a-space>
      </template>
    </a-page-header>

    <a-table
      :data="questions"
      :loading="loading"
      :pagination="{
        current: query.current,
        pageSize: query.pageSize,
        total,
        showTotal: true,
      }"
      @page-change="onPageChange"
    >
      <template #columns>
        <a-table-column title="ID" data-index="id" :width="90" />
        <a-table-column title="标题" data-index="title" :width="220" />
        <a-table-column title="题型" :width="90">
          <template #cell="{ record }">{{ typeText(record.type) }}</template>
        </a-table-column>
        <a-table-column title="章节" data-index="chapter" :width="110" />
        <a-table-column title="难度" data-index="difficulty" :width="70" />
        <a-table-column title="知识节点" :width="200">
          <template #cell="{ record }">
            <a-tag
              v-for="node in record.nodeNames || []"
              :key="node"
              size="small"
            >
              {{ node }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="状态" :width="110">
          <template #cell="{ record }">
            <a-tag :color="statusColor(record.reviewStatus)">
              {{ statusText(record.reviewStatus) }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column
          v-if="mode === 'review'"
          title="作者"
          data-index="userId"
          :width="90"
        />
        <a-table-column title="操作" :width="300">
          <template #cell="{ record }">
            <a-space wrap>
              <a-button size="small" @click="openDetail(record)">查看</a-button>
              <template v-if="mode === 'mine'">
                <a-button
                  v-if="canEdit(record)"
                  size="small"
                  type="primary"
                  @click="openEdit(record)"
                >
                  编辑
                </a-button>
                <a-button
                  v-if="canSubmit(record)"
                  size="small"
                  type="primary"
                  status="success"
                  @click="doReview(record, 'SUBMIT')"
                >
                  {{
                    record.reviewStatus === "REJECTED"
                      ? "修改后重新提交"
                      : "提交审核"
                  }}
                </a-button>
              </template>
              <template v-else>
                <a-button
                  v-if="record.reviewStatus === 'SUBMITTED'"
                  size="small"
                  type="primary"
                  status="success"
                  @click="doReview(record, 'APPROVE')"
                >
                  通过
                </a-button>
                <a-button
                  v-if="record.reviewStatus === 'SUBMITTED'"
                  size="small"
                  status="danger"
                  @click="openReject(record)"
                >
                  驳回
                </a-button>
                <a-button
                  v-if="record.reviewStatus === 'APPROVED'"
                  size="small"
                  type="primary"
                  @click="doReview(record, 'PUBLISH')"
                >
                  发布
                </a-button>
                <a-button
                  v-if="record.reviewStatus === 'PUBLISHED'"
                  size="small"
                  status="warning"
                  @click="doReview(record, 'OFFLINE')"
                >
                  下线
                </a-button>
              </template>
            </a-space>
          </template>
        </a-table-column>
      </template>
    </a-table>

    <!-- 新建/编辑 -->
    <a-modal
      v-model:visible="formVisible"
      :title="form.id ? '编辑题目' : '新建题目'"
      :width="680"
      :confirm-loading="formSubmitting"
      @ok="submitForm"
    >
      <a-form :model="form" label-align="left" auto-label-width>
        <a-form-item label="标题" required>
          <a-input v-model:model-value="form.title" />
        </a-form-item>
        <a-form-item label="题型" required>
          <a-select v-model:model-value="form.type" :disabled="!!form.id">
            <a-option value="JUDGE">判断题</a-option>
            <a-option value="SINGLE">单选题</a-option>
            <a-option value="MULTIPLE">多选题</a-option>
            <a-option value="FILL_BLANK">填空题</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="题干" required>
          <a-textarea
            v-model:model-value="form.content"
            :auto-size="{ minRows: 3 }"
          />
        </a-form-item>
        <template v-if="form.type === 'SINGLE' || form.type === 'MULTIPLE'">
          <a-form-item
            v-for="(opt, index) in form.optionRows"
            :key="index"
            :label="`选项 ${optionKey(index)}`"
          >
            <a-space>
              <a-input v-model:model-value="opt.value" placeholder="选项内容" />
              <a-button size="small" @click="removeOption(index)"
                >移除</a-button
              >
            </a-space>
          </a-form-item>
          <a-form-item label=" ">
            <a-button size="small" @click="addOption">添加选项</a-button>
          </a-form-item>
        </template>

        <!-- 答案 -->
        <a-form-item label="标准答案" required>
          <a-radio-group
            v-if="form.type === 'JUDGE'"
            v-model:model-value="form.judgeAnswer"
          >
            <a-radio value="true">正确</a-radio>
            <a-radio value="false">错误</a-radio>
          </a-radio-group>
          <a-radio-group
            v-else-if="form.type === 'SINGLE'"
            v-model:model-value="form.singleAnswer"
          >
            <a-radio
              v-for="(opt, index) in form.optionRows"
              :key="index"
              :value="optionKey(index)"
            >
              {{ optionKey(index) }}
            </a-radio>
          </a-radio-group>
          <a-checkbox-group
            v-else-if="form.type === 'MULTIPLE'"
            v-model:model-value="form.multipleAnswer"
          >
            <a-checkbox
              v-for="(opt, index) in form.optionRows"
              :key="index"
              :value="optionKey(index)"
            >
              {{ optionKey(index) }}
            </a-checkbox>
          </a-checkbox-group>
          <a-textarea
            v-else-if="form.type === 'FILL_BLANK'"
            v-model:model-value="form.fillAnswer"
            placeholder="多个可接受答案用 | 分隔，例如：LRU|最近最久未使用"
          />
        </a-form-item>
        <a-form-item label="解析">
          <a-textarea
            v-model:model-value="form.analysis"
            :auto-size="{ minRows: 2 }"
          />
        </a-form-item>
        <a-form-item label="常见误区">
          <a-input v-model:model-value="form.misconception" />
        </a-form-item>
        <a-form-item label="章节">
          <a-input
            v-model:model-value="form.chapter"
            placeholder="例如：进程管理"
          />
        </a-form-item>
        <a-form-item label="难度">
          <a-input-number
            v-model:model-value="form.difficulty"
            :min="1"
            :max="5"
          />
        </a-form-item>
        <a-form-item label="知识节点">
          <a-select
            v-model:model-value="form.nodeNames"
            multiple
            allow-search
            placeholder="关联图谱真实节点"
          >
            <a-option v-for="node in nodeNames" :key="node" :value="node">
              {{ node }}
            </a-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 驳回理由 -->
    <a-modal
      v-model:visible="rejectVisible"
      title="驳回（必填理由）"
      :confirm-loading="rejectSubmitting"
      @ok="confirmReject"
    >
      <a-textarea
        v-model:model-value="rejectReason"
        :auto-size="{ minRows: 3 }"
        placeholder="说明驳回原因，作者修改后可重新提交"
      />
    </a-modal>

    <!-- 查看详情 -->
    <a-modal
      v-model:visible="detailVisible"
      title="题目详情"
      :width="640"
      :footer="false"
    >
      <a-descriptions :column="2" bordered size="small">
        <a-descriptions-item label="标题">{{
          detail?.title
        }}</a-descriptions-item>
        <a-descriptions-item label="题型">{{
          typeText(detail?.type)
        }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          {{ statusText(detail?.reviewStatus) }}
        </a-descriptions-item>
        <a-descriptions-item label="难度">{{
          detail?.difficulty
        }}</a-descriptions-item>
        <a-descriptions-item label="题干" :span="2">
          {{ detail?.content }}
        </a-descriptions-item>
        <a-descriptions-item label="选项" :span="2">
          {{ formatOptions(detail?.options) }}
        </a-descriptions-item>
        <a-descriptions-item label="标准答案" :span="2">
          {{ formatAnswer(detail?.correctAnswer, detail?.type) }}
        </a-descriptions-item>
        <a-descriptions-item label="解析" :span="2">
          {{ detail?.analysis || "-" }}
        </a-descriptions-item>
        <a-descriptions-item label="知识节点" :span="2">
          <a-tag
            v-for="node in detail?.nodeNames || []"
            :key="node"
            size="small"
          >
            {{ node }}
          </a-tag>
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>

    <!-- JSON 批量导入（预览 → 确认导入草稿） -->
    <a-modal
      v-model:visible="importVisible"
      title="JSON 批量导入题目"
      :width="760"
      :footer="false"
    >
      <template v-if="!importPreview">
        <a-alert type="normal" style="margin-bottom: 12px">
          粘贴 JSON 数组，每项包含 title / content / type / answer / nodeNames
          等字段。先预览逐项校验，确认后仅有效项会创建为草稿，仍需走正常审核流程。
        </a-alert>
        <a-textarea
          v-model:model-value="importText"
          :auto-size="{ minRows: 10, maxRows: 20 }"
          placeholder='[{"title":"题目标题","content":"题干","type":"JUDGE","answer":"true","nodeNames":["LRU 页面置换算法"],"chapter":"内存管理"}]'
        />
        <div style="margin-top: 12px; text-align: right">
          <a-space>
            <a-button @click="importVisible = false">取消</a-button>
            <a-button
              type="primary"
              :loading="importPreviewing"
              @click="doImportPreview"
            >
              预览校验
            </a-button>
          </a-space>
        </div>
      </template>
      <template v-else>
        <a-alert type="info" style="margin-bottom: 12px">
          共 {{ importPreview.total }} 项：有效
          {{ importPreview.validCount }} 项，无效
          {{ importPreview.invalidCount }} 项；确认后仅导入有效项为草稿。
        </a-alert>
        <a-table :data="importPreview.items" :pagination="false">
          <template #columns>
            <a-table-column title="#" data-index="index" :width="50" />
            <a-table-column title="标题" data-index="title" :width="200" />
            <a-table-column title="题型" :width="90">
              <template #cell="{ record }">{{
                typeText(record.type)
              }}</template>
            </a-table-column>
            <a-table-column title="校验" :width="90">
              <template #cell="{ record }">
                <a-tag :color="record.valid ? 'green' : 'red'">
                  {{ record.valid ? "通过" : "失败" }}
                </a-tag>
              </template>
            </a-table-column>
            <a-table-column title="说明">
              <template #cell="{ record }">{{ record.error || "-" }}</template>
            </a-table-column>
          </template>
        </a-table>
        <div style="margin-top: 12px; text-align: right">
          <a-space>
            <a-button @click="importPreview = null">返回修改</a-button>
            <a-button
              type="primary"
              :loading="importCommitting"
              :disabled="!importPreview.validCount"
              @click="doImportCommit"
            >
              确认导入 {{ importPreview.validCount }} 项
            </a-button>
          </a-space>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { Message } from "@arco-design/web-vue";
import {
  addLearningQuestion,
  getManageQuestion,
  importLearningQuestionsCommit,
  importLearningQuestionsPreview,
  pageManageQuestions,
  reviewLearningQuestion,
  updateLearningQuestion,
} from "@/api/learning";
import type { ImportPreviewVO } from "@/api/learning";
import { KnowledgeGraphControllerService } from "../../../generated";
import store from "@/store";
import ACCESS_ENUM from "@/access/accessEnum";

const mode = ref<"mine" | "review">("mine");
const isAdmin = computed(
  () => store.state.user.loginUser?.userRole === ACCESS_ENUM.ADMIN
);
watch(mode, () => load());

const loading = ref(false);
const questions = ref<any[]>([]);
const total = ref(0);
const nodeNames = ref<string[]>([]);
const query = ref<any>({ current: 1, pageSize: 10 });

const load = async () => {
  loading.value = true;
  try {
    const params: any = { ...query.value };
    if (mode.value === "mine") {
      params.myOnly = true;
    } else {
      params.reviewQueue = true;
    }
    const res = await pageManageQuestions(params);
    if (res.data?.code === 0) {
      questions.value = res.data.data?.records || [];
      total.value = res.data.data?.total || 0;
    } else {
      Message.error(res.data?.message || "加载失败");
    }
  } catch (e) {
    Message.error("加载失败，请稍后重试");
  } finally {
    loading.value = false;
  }
};

const onPageChange = (page: number) => {
  query.value.current = page;
  load();
};

// 表单
const formVisible = ref(false);
const formSubmitting = ref(false);
const emptyForm = () => ({
  id: undefined as any,
  title: "",
  type: "SINGLE",
  content: "",
  optionRows: [
    { value: "" },
    { value: "" },
    { value: "" },
    { value: "" },
  ] as any[],
  judgeAnswer: "true",
  singleAnswer: "A",
  multipleAnswer: [] as string[],
  fillAnswer: "",
  analysis: "",
  misconception: "",
  chapter: "",
  difficulty: 2,
  nodeNames: [] as string[],
  // 编辑回填的原始答案
  originalAnswer: undefined as any,
  originalOptions: undefined as any,
});
const form = ref(emptyForm());

const openCreate = () => {
  form.value = emptyForm();
  formVisible.value = true;
};

const openEdit = async (record: any) => {
  form.value = emptyForm();
  form.value.id = record.id;
  form.value.title = record.title;
  form.value.type = record.type;
  form.value.content = record.content;
  form.value.analysis = record.analysis || "";
  form.value.misconception = record.misconception || "";
  form.value.chapter = record.chapter || "";
  form.value.difficulty = record.difficulty || 2;
  form.value.nodeNames = [...(record.nodeNames || [])];
  const detail = await getManageQuestion(record.id);
  const manageRecord = detail.data?.data || record;
  form.value.originalAnswer = manageRecord.correctAnswer;
  form.value.originalOptions = record.options;
  if (record.type === "SINGLE" || record.type === "MULTIPLE") {
    const opts = parseOptions(record.options);
    form.value.optionRows = opts.length
      ? opts.map((o: any) => ({ value: o.value }))
      : form.value.optionRows;
    const keys = parseAnswerKeys(manageRecord.correctAnswer, record.type);
    if (record.type === "SINGLE") {
      form.value.singleAnswer = keys[0] || "A";
    } else {
      form.value.multipleAnswer = keys;
    }
  } else if (record.type === "JUDGE") {
    form.value.judgeAnswer = parseJudge(manageRecord.correctAnswer);
  } else if (record.type === "FILL_BLANK") {
    form.value.fillAnswer = parseFill(manageRecord.correctAnswer);
  }
  formVisible.value = true;
};

const optionKey = (index: number) => String.fromCharCode(65 + index); // A, B, C ...

const addOption = () => {
  if (form.value.optionRows.length >= 8) {
    Message.warning("最多 8 个选项");
    return;
  }
  form.value.optionRows.push({ value: "" });
};

const removeOption = (index: number) => {
  form.value.optionRows.splice(index, 1);
};

const buildAnswer = () => {
  switch (form.value.type) {
    case "JUDGE":
      return form.value.judgeAnswer;
    case "SINGLE":
      return form.value.singleAnswer;
    case "MULTIPLE":
      return [...form.value.multipleAnswer].sort();
    case "FILL_BLANK":
      return form.value.fillAnswer
        .split("|")
        .map((s: string) => s.trim())
        .filter(Boolean);
    default:
      return null;
  }
};

const buildOptions = () => {
  if (form.value.type !== "SINGLE" && form.value.type !== "MULTIPLE") {
    return undefined;
  }
  return form.value.optionRows.map((row, index) => ({
    key: optionKey(index),
    value: row.value,
  }));
};

const submitForm = async () => {
  if (!form.value.title.trim() || !form.value.content.trim()) {
    Message.warning("标题和题干不能为空");
    return;
  }
  const payload: any = {
    title: form.value.title,
    type: form.value.type,
    content: form.value.content,
    options: buildOptions(),
    answer: buildAnswer(),
    analysis: form.value.analysis,
    misconception: form.value.misconception,
    chapter: form.value.chapter,
    difficulty: form.value.difficulty,
    nodeNames: form.value.nodeNames,
  };
  formSubmitting.value = true;
  try {
    const res = form.value.id
      ? await updateLearningQuestion({ ...payload, id: form.value.id })
      : await addLearningQuestion(payload);
    if (res.data?.code === 0) {
      Message.success(form.value.id ? "已保存（回到草稿）" : "已创建草稿");
      formVisible.value = false;
      await load();
    } else {
      Message.error(res.data?.message || "保存失败");
    }
  } catch (e) {
    Message.error("保存失败，请稍后重试");
  } finally {
    formSubmitting.value = false;
  }
};

// 审核操作
const doReview = async (record: any, action: string) => {
  try {
    const res = await reviewLearningQuestion({
      questionId: record.id,
      action,
    });
    if (res.data?.code === 0) {
      Message.success("操作成功");
      await load();
    } else {
      Message.error(res.data?.message || "操作失败");
    }
  } catch (e) {
    Message.error("操作失败，请稍后重试");
  }
};

const rejectVisible = ref(false);
const rejectReason = ref("");
const rejectTarget = ref<any>(null);
const rejectSubmitting = ref(false);
const openReject = (record: any) => {
  rejectTarget.value = record;
  rejectReason.value = "";
  rejectVisible.value = true;
};
const confirmReject = async () => {
  if (!rejectReason.value.trim()) {
    Message.warning("驳回必须填写理由");
    return;
  }
  rejectSubmitting.value = true;
  try {
    const res = await reviewLearningQuestion({
      questionId: rejectTarget.value.id,
      action: "REJECT",
      reason: rejectReason.value.trim(),
    });
    if (res.data?.code === 0) {
      Message.success("已驳回");
      rejectVisible.value = false;
      await load();
    } else {
      Message.error(res.data?.message || "驳回失败");
    }
  } catch (e) {
    Message.error("驳回失败，请稍后重试");
  } finally {
    rejectSubmitting.value = false;
  }
};

// 详情
const detailVisible = ref(false);
const detail = ref<any>(null);
const openDetail = async (record: any) => {
  try {
    const detailRes = await getManageQuestion(record.id);
    if (detailRes.data?.code === 0) {
      detail.value = detailRes.data.data;
      detailVisible.value = true;
      return;
    }
  } catch (e) {
    // 回退到列表行数据
  }
  detail.value = record;
  detailVisible.value = true;
};

// JSON 批量导入（预览 → 确认导入草稿）
const importVisible = ref(false);
const importText = ref("");
const importPreview = ref<ImportPreviewVO | null>(null);
const importPreviewing = ref(false);
const importCommitting = ref(false);

const openImport = () => {
  importText.value = "";
  importPreview.value = null;
  importVisible.value = true;
};

const doImportPreview = async () => {
  if (!importText.value.trim()) {
    Message.warning("请先粘贴 JSON 数组");
    return;
  }
  importPreviewing.value = true;
  try {
    const res = await importLearningQuestionsPreview(importText.value);
    if (res.data?.code === 0) {
      importPreview.value = res.data.data;
    } else {
      Message.error(res.data?.message || "预览失败，请检查 JSON 格式");
    }
  } catch (e) {
    Message.error("预览失败，请稍后重试");
  } finally {
    importPreviewing.value = false;
  }
};

const doImportCommit = async () => {
  importCommitting.value = true;
  try {
    const res = await importLearningQuestionsCommit(importText.value);
    if (res.data?.code === 0) {
      const result = res.data.data;
      Message.success(
        `已导入 ${result.createdCount} 项草稿，跳过 ${result.skippedCount} 项`
      );
      importVisible.value = false;
      await load();
    } else {
      Message.error(res.data?.message || "导入失败");
    }
  } catch (e) {
    Message.error("导入失败，请稍后重试");
  } finally {
    importCommitting.value = false;
  }
};

// 工具
const canEdit = (record: any) =>
  ["DRAFT", "REJECTED", "OFFLINE"].includes(record.reviewStatus);
const canSubmit = (record: any) =>
  ["DRAFT", "REJECTED", "OFFLINE"].includes(record.reviewStatus);

const typeText = (type?: string) => {
  const map: Record<string, string> = {
    JUDGE: "判断题",
    SINGLE: "单选题",
    MULTIPLE: "多选题",
    FILL_BLANK: "填空题",
  };
  return map[type || ""] || type || "-";
};

const statusText = (status?: string) => {
  const map: Record<string, string> = {
    DRAFT: "草稿",
    SUBMITTED: "待审核",
    APPROVED: "待发布",
    REJECTED: "已驳回",
    PUBLISHED: "已发布",
    OFFLINE: "已下线",
  };
  return map[status || ""] || status || "-";
};

const statusColor = (status?: string) => {
  const map: Record<string, string> = {
    DRAFT: "gray",
    SUBMITTED: "blue",
    APPROVED: "arcoblue",
    REJECTED: "red",
    PUBLISHED: "green",
    OFFLINE: "orange",
  };
  return map[status || ""] || "gray";
};

const parseOptions = (options: any): any[] => {
  if (!options) return [];
  try {
    return Array.isArray(options) ? options : JSON.parse(options);
  } catch (e) {
    return [];
  }
};

const parseAnswerKeys = (answerJson: string, type: string): string[] => {
  try {
    const parsed = JSON.parse(answerJson);
    if (Array.isArray(parsed)) return parsed;
    return [String(parsed)];
  } catch (e) {
    return [];
  }
};

const parseJudge = (answerJson: string): string => {
  try {
    return JSON.parse(answerJson) === false ? "false" : "true";
  } catch (e) {
    return "true";
  }
};

const parseFill = (answerJson: string): string => {
  try {
    const parsed = JSON.parse(answerJson);
    if (Array.isArray(parsed)) return parsed.join("|");
    return String(parsed);
  } catch (e) {
    return answerJson || "";
  }
};

const formatOptions = (options: any) => {
  const opts = parseOptions(options);
  return opts.length
    ? opts.map((o: any) => `${o.key}. ${o.value}`).join("；")
    : "-";
};

const formatAnswer = (answerJson?: string, type?: string) => {
  if (!answerJson) return "-";
  try {
    const parsed = JSON.parse(answerJson);
    if (type === "JUDGE") {
      return parsed === "true" ? "正确" : "错误";
    }
    if (Array.isArray(parsed)) return parsed.join("、");
    return String(parsed);
  } catch (e) {
    return answerJson;
  }
};

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

onMounted(() => {
  load();
  loadNodeNames();
});
</script>

<style scoped>
.manage-learning {
  max-width: 1400px;
  margin: 0 auto;
}
</style>
