<template>
  <div class="qql">
    <div v-if="loading" class="qql-state">正在准备本节点的自测题…</div>
    <div v-else-if="!items.length" class="qql-state">
      本节点暂无可自测的题目，先去「知识内容」过一遍概念。
    </div>

    <template v-else>
      <div class="qql-head">
        <span class="qql-progress">第 {{ index + 1 }} / {{ items.length }} 题</span>
        <span class="qql-score">答对 {{ score }} 题</span>
      </div>

      <p class="qql-title">{{ current.title }}</p>

      <div class="qql-options">
        <button
          v-for="option in options"
          :key="option.key"
          type="button"
          class="qql-option"
          :class="optionClass(option.key)"
          :disabled="answered"
          @click="pick(option.key)"
        >
          <span class="qql-key">{{ option.key }}</span>
          <span class="qql-text">{{ option.value }}</span>
        </button>
      </div>

      <div v-if="answered" class="qql-feedback" :class="correct ? 'ok' : 'bad'">
        <strong>{{ correct ? "答对了" : "答错了" }}</strong>
        <p v-if="analysis">{{ analysis }}</p>
      </div>

      <div v-if="answered" class="qql-actions">
        <a-button type="primary" size="small" @click="next">
          {{ index + 1 >= items.length ? "重新来一轮" : "下一题" }}
        </a-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import {
  answerLearningQuestion,
  listLearningQuestions,
} from "@/api/learning";

type Option = { key: string; value: string };

const props = defineProps<{
  preset?: { nodeCode?: string; nodeName?: string };
}>();

const items = ref<Array<{ id: number; title: string; options: Option[]; answer?: string }>>([]);
const index = ref(0);
const loading = ref(true);
const answered = ref(false);
const correct = ref(false);
const analysis = ref("");
const score = ref(0);
const selectedKey = ref("");

const current = computed(() => items.value[index.value]);
const options = computed<Option[]>(() => current.value?.options || []);

const load = async () => {
  loading.value = true;
  try {
    const res = await listLearningQuestions({
      current: 1,
      pageSize: 5,
      nodeCode: props.preset?.nodeCode,
    });
    const records: any[] = res.data?.data?.records || [];
    items.value = records
      .map((item) => {
        const raw = item.options;
        let opts: Option[] = [];
        try {
          opts = typeof raw === "string" ? JSON.parse(raw) : raw || [];
        } catch {
          opts = [];
        }
        return { id: item.id, title: item.title, options: opts };
      })
      .filter((item) => Array.isArray(item.options) && item.options.length > 1);
  } catch {
    items.value = [];
  } finally {
    loading.value = false;
    index.value = 0;
    answered.value = false;
    score.value = 0;
  }
};

const pick = async (key: string) => {
  if (answered.value || !current.value) return;
  selectedKey.value = key;
  const res = await answerLearningQuestion({
    questionId: current.value.id,
    answer: key,
  } as any);
  const data = res.data?.data;
  answered.value = true;
  correct.value = Boolean(data?.correct);
  analysis.value = data?.analysis || "";
  if (data?.correct) score.value += 1;
};

const next = () => {
  if (index.value + 1 >= items.value.length) {
    index.value = 0;
    score.value = 0;
  } else {
    index.value += 1;
  }
  answered.value = false;
  correct.value = false;
  analysis.value = "";
  selectedKey.value = "";
};

const optionClass = (key: string) => {
  if (!answered.value) return "";
  if (key !== selectedKey.value) return "";
  return correct.value ? "right" : "wrong";
};

onMounted(load);
</script>

<style scoped>
.qql { display: flex; flex-direction: column; gap: 14px; }
.qql-state { font-size: 13px; color: #8a8578; padding: 12px 0; }
.qql-head { display: flex; justify-content: space-between; font-size: 12px; color: #8a8578; }
.qql-title { margin: 0; font-size: 15px; line-height: 1.8; color: #2c2a26; }
.qql-options { display: flex; flex-direction: column; gap: 8px; }
.qql-option {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 10px 12px; border: 1px solid #e3dfd2; border-radius: 10px;
  background: #fff; text-align: left; cursor: pointer; font: inherit; color: #3d3a34;
}
.qql-option:hover:not(:disabled) { border-color: #d9c86a; background: #fdfbf0; }
.qql-option:disabled { cursor: default; }
.qql-option.right { border-color: #9cc48a; background: #eef7e9; }
.qql-option.wrong { border-color: #e0a294; background: #fbe9e4; }
.qql-key {
  flex: none; width: 22px; height: 22px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  background: #f2efe3; font-size: 12px;
}
.qql-text { line-height: 1.7; }
.qql-feedback { border-radius: 10px; padding: 10px 12px; font-size: 13px; }
.qql-feedback.ok { background: #e8f3e4; color: #3d6b2f; }
.qql-feedback.bad { background: #fbe9e4; color: #8a3a25; }
.qql-feedback p { margin: 6px 0 0; line-height: 1.8; }
</style>
