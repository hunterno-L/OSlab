<template>
  <div class="experiment-editor">
    <a-card>
      <template #title>
        <div class="head">
          <span>{{ form.id ? "编辑编程实验" : "新建编程实验" }}</span>
          <a-space>
            <a-tag v-if="experiment?.publicationStatus" :color="statusColor">
              {{ statusText }}
            </a-tag>
            <a-button size="small" @click="router.push('/teaching/experiments')"
              >返回列表</a-button
            >
          </a-space>
        </div>
      </template>

      <a-alert v-if="experiment?.pendingRevision" type="warning" class="notice">
        已有待应用修订（第
        {{ experiment.pendingRevision }} 版）。学生当前仍使用已发布版本；
        点击“应用修订”后才会生效。
        <a-button
          size="mini"
          type="primary"
          style="margin-left: 10px"
          @click="applyRevision"
        >
          应用修订
        </a-button>
      </a-alert>

      <a-form :model="form" layout="vertical">
        <a-form-item label="标题" required>
          <a-input v-model="form.title" maxlength="80" />
        </a-form-item>
        <a-form-item label="实验说明（Markdown）" required>
          <MdEditor
            :value="form.content"
            :handle-change="(v: string) => (form.content = v)"
          />
        </a-form-item>
        <a-form-item label="关联知识点（图谱节点，可选）">
          <a-select
            v-model="form.nodeCodes"
            multiple
            allow-search
            placeholder="选择知识节点"
          >
            <a-option
              v-for="node in nodes"
              :key="node.nodeCode"
              :value="node.nodeCode"
            >
              {{ node.name }}（{{ node.nodeCode }}）
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="标签">
          <a-input-tag v-model="form.tags" placeholder="输入后回车添加" />
        </a-form-item>
        <a-form-item label="初始框架代码">
          <div class="code-editor-wrap">
            <CodeEditor
              :value="form.originalCode"
              :language="form.language"
              :handle-change="(v: string) => (form.originalCode = v)"
            />
          </div>
        </a-form-item>
        <a-form-item label="参考解">
          <a-textarea
            v-model="form.answer"
            class="mono"
            :auto-size="{ minRows: 4 }"
          />
        </a-form-item>

        <a-divider>判题配置</a-divider>
        <div class="grid-3">
          <a-form-item label="时间限制 (ms)">
            <a-input-number v-model="form.judgeConfig.timeLimit" :min="1" />
          </a-form-item>
          <a-form-item label="内存限制 (MB)">
            <a-input-number v-model="form.judgeConfig.memoryLimit" :min="1" />
          </a-form-item>
          <a-form-item label="堆栈限制">
            <a-input-number v-model="form.judgeConfig.stackLimit" :min="1" />
          </a-form-item>
        </div>

        <a-form-item label="测试用例（隐藏于学生）">
          <div
            v-for="(testCase, index) in form.judgeCase"
            :key="index"
            class="case-row"
          >
            <a-input v-model="testCase.input" placeholder="输入" />
            <a-input v-model="testCase.output" placeholder="期望输出" />
            <a-button size="small" @click="form.judgeCase.splice(index, 1)"
              >删除</a-button
            >
          </div>
          <a-button
            size="small"
            @click="form.judgeCase.push({ input: '', output: '' })"
          >
            添加测试用例
          </a-button>
        </a-form-item>
      </a-form>

      <div class="actions">
        <a-space>
          <a-button
            v-if="!form.id"
            type="primary"
            :loading="saving"
            @click="save"
          >
            创建草稿
          </a-button>
          <template v-else>
            <a-button type="primary" :loading="saving" @click="save">
              {{
                experiment?.publicationStatus === "DRAFT"
                  ? "保存草稿"
                  : "保存改稿"
              }}
            </a-button>
            <a-button
              v-if="experiment?.publicationStatus === 'PUBLISHED'"
              type="success"
              :loading="saving"
              @click="save(true)"
            >
              保存并应用
            </a-button>
            <a-button
              v-if="experiment?.publicationStatus === 'DRAFT'"
              type="success"
              :loading="publishing"
              @click="publish"
            >
              发布实验
            </a-button>
            <a-button
              v-if="experiment?.publicationStatus === 'PUBLISHED'"
              status="warning"
              @click="offline"
            >
              下线实验
            </a-button>
          </template>
        </a-space>
        <span class="hint">
          共享实验始终保留原 ID
          与历史提交；新实验在发布前不会出现在学生实验列表。
        </span>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import MdEditor from "@/components/MdEditor.vue";
import CodeEditor from "@/components/CodeEditor.vue";
import {
  applyExperimentDraft,
  createExperimentDraft,
  getExperimentDraft,
  getTeachingCatalog,
  offlineExperiment,
  publishExperimentDraft,
  saveExperimentDraft,
} from "@/api/tasks";

const route = useRoute();
const router = useRouter();
const experiment = ref<any>(null);
const nodes = ref<any[]>([]);
const saving = ref(false);
const publishing = ref(false);

const form = reactive({
  id: "",
  title: "",
  content: "",
  originalCode: "",
  answer: "",
  tags: [] as string[],
  nodeCodes: [] as string[],
  language: "c",
  judgeConfig: { timeLimit: 1000, memoryLimit: 256, stackLimit: 128 },
  judgeCase: [{ input: "", output: "" }] as { input: string; output: string }[],
});

const statusText = computed(() => {
  const status = experiment.value?.publicationStatus;
  return status === "DRAFT"
    ? "草稿"
    : status === "OFFLINE"
    ? "已下线"
    : "已发布";
});
const statusColor = computed(() => {
  const status = experiment.value?.publicationStatus;
  return status === "DRAFT"
    ? "gray"
    : status === "OFFLINE"
    ? "orange"
    : "green";
});

const payload = (apply = false) => ({
  title: form.title,
  content: form.content,
  originalCode: form.originalCode,
  answer: form.answer,
  tags: form.tags,
  nodeCodes: form.nodeCodes,
  judgeConfig: form.judgeConfig,
  judgeCase: form.judgeCase,
  apply,
});

const validate = () => {
  if (!form.title.trim() || !form.content.trim()) {
    Message.warning("标题和实验说明不能为空");
    return false;
  }
  for (const testCase of form.judgeCase) {
    if (!testCase.input.trim() && !testCase.output.trim()) continue;
    if (!testCase.input.trim() || !testCase.output.trim()) {
      Message.warning("测试用例需同时填写输入和期望输出");
      return false;
    }
  }
  return true;
};

const save = async (apply = false) => {
  if (!validate()) return;
  saving.value = true;
  try {
    const res = form.id
      ? await saveExperimentDraft(form.id, payload(apply))
      : await createExperimentDraft(payload());
    if (res.data?.code === 0) {
      Message.success(apply ? "已应用修订" : "已保存");
      if (!form.id) {
        await router.replace(
          `/teaching/experiments/update?id=${res.data.data.id}`
        );
        form.id = String(res.data.data.id);
      }
      await load();
    } else {
      Message.error(res.data?.message || "保存失败");
    }
  } finally {
    saving.value = false;
  }
};

const publish = async () => {
  if (!form.id) return;
  publishing.value = true;
  try {
    const res = await publishExperimentDraft(form.id);
    if (res.data?.code === 0) {
      Message.success("实验已发布");
      await load();
    } else {
      Message.error(res.data?.message || "发布失败");
    }
  } finally {
    publishing.value = false;
  }
};

const applyRevision = async () => {
  const res = await applyExperimentDraft(form.id);
  if (res.data?.code === 0) {
    Message.success("修订已应用");
    await load();
  } else {
    Message.error(res.data?.message || "应用失败");
  }
};

const offline = async () => {
  const res = await offlineExperiment(form.id);
  if (res.data?.code === 0) {
    Message.success("实验已下线");
    await load();
  } else {
    Message.error(res.data?.message || "下线失败");
  }
};

const load = async () => {
  const id = String(route.query.id || form.id || "");
  if (!id) {
    experiment.value = null;
    return;
  }
  form.id = id;
  const res = await getExperimentDraft(id);
  if (res.data?.code === 0) {
    const data = res.data.data;
    const pending = data.pending || data;
    experiment.value = data;
    form.title = pending.title || data.title || "";
    form.content = pending.content || data.content || "";
    form.originalCode = pending.originalCode || data.originalCode || "";
    form.answer = pending.answer || data.answer || "";
    form.tags = Array.isArray(pending.tags) ? pending.tags : [];
    form.nodeCodes = data.nodeCodes || [];
    form.judgeConfig = pending.judgeConfig ||
      data.judgeConfig || {
        timeLimit: 1000,
        memoryLimit: 256,
        stackLimit: 128,
      };
    form.judgeCase =
      Array.isArray(pending.judgeCase) && pending.judgeCase.length
        ? pending.judgeCase
        : [{ input: "", output: "" }];
  } else {
    Message.error(res.data?.message || "实验加载失败");
  }
};

const loadNodes = async () => {
  try {
    const res = await getTeachingCatalog();
    if (res.data?.code === 0) nodes.value = res.data.data?.nodes || [];
  } catch {
    // 节点不可用时仍可保存实验本身
  }
};

onMounted(async () => {
  await loadNodes();
  await load();
});
</script>

<style scoped>
.experiment-editor {
  max-width: 1100px;
  margin: 0 auto;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.notice {
  margin-bottom: 12px;
}
.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.case-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.code-editor-wrap {
  height: 360px;
  border-radius: 8px;
  overflow: hidden;
}
.mono :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
.actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 14px;
}
.hint {
  color: var(--oslab-muted-strong, #757069);
  font-size: 12px;
}
</style>
