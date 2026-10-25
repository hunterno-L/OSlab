<template>
  <div class="assessment-run">
    <a-spin :loading="loading" style="width: 100%">
      <!-- 本轮头部 -->
      <div class="run-head" v-if="run">
        <div>
          <h1 class="run-title">知识测评</h1>
          <div class="side-hint">
            第 {{ currentIndex + 1 }} / {{ questions.length }} 题 · 本轮首次答对
            {{ firstCorrectCount }} 题
          </div>
        </div>
        <a-button size="small" @click="finishRun" :disabled="finished">
          结束本轮
        </a-button>
      </div>

      <!-- 题号导航 -->
      <AssessmentNavigator
        v-if="questions.length"
        class="run-navigator"
        :questions="questions"
        :current-index="currentIndex"
        @select="goToQuestion"
      />

      <!-- 当前题面 -->
      <div v-if="currentQuestion" class="question-area">
        <QuestionAnswerCard
          :key="currentQuestion.id"
          :question="currentQuestion"
          :assessment-run-id="runId"
          :disabled="finished || finishing || currentQuestion.offline"
          :disabled-reason="finished ? '本轮测评已结束' : '本轮测评正在结束'"
          @answered="answerHandler"
        />
        <div class="nav-buttons">
          <a-button
            :disabled="currentIndex === 0"
            @click="goToQuestion(currentIndex - 1)"
          >
            上一题
          </a-button>
          <a-button
            v-if="currentIndex < questions.length - 1"
            :type="nextPrimary ? 'primary' : 'secondary'"
            @click="goToQuestion(currentIndex + 1)"
          >
            下一题
          </a-button>
          <a-button
            v-else
            type="primary"
            :disabled="finished"
            @click="finishRun"
          >
            本轮总结
          </a-button>
        </div>
      </div>

      <!-- 本轮总结 -->
      <div v-if="finished && summary" class="section-card run-summary">
        <div class="section-title">本轮总结</div>
        <a-descriptions :column="3" bordered size="small">
          <a-descriptions-item label="题目数">
            {{ summary.totalQuestions }}
          </a-descriptions-item>
          <a-descriptions-item label="已作答">
            {{ summary.answeredQuestions }}
          </a-descriptions-item>
          <a-descriptions-item label="本轮首次答对">
            {{ summary.firstCorrect }}
          </a-descriptions-item>
        </a-descriptions>
        <div class="btn-row">
          <a-button
            @click="router.push({ path: '/records', query: contextQuery })"
          >
            查看学习记录
          </a-button>
          <a-button type="primary" @click="returnToPractice">
            返回题目练习
          </a-button>
        </div>
      </div>

      <div
        v-if="!loading && !questions.length && !finished"
        class="oslab-empty"
      >
        <span class="oslab-state-title">本轮没有可作答的题目</span>
        <a-button size="small" type="primary" @click="returnToPractice"
          >返回题目练习</a-button
        >
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import AssessmentNavigator from "@/components/learning/AssessmentNavigator.vue";
import QuestionAnswerCard from "@/components/learning/QuestionAnswerCard.vue";
import {
  finishAssessment,
  getAssessmentRun,
  type AssessmentFinishResult,
  type AssessmentRunVO,
  type LearningAnswerResultVO,
  type LearningQuestionVO,
} from "@/api/learning";

const route = useRoute();
const router = useRouter();

// 雪花 id 保持字符串透传（Number 精度丢失会导致查询错误 id）
const runId = computed(() =>
  typeof route.params.runId === "string" ? route.params.runId : ""
);

const loading = ref(false);
const run = ref<AssessmentRunVO | null>(null);
const questions = ref<LearningQuestionVO[]>([]);
const currentIndex = ref(0);
const finished = ref(false);
const finishing = ref(false);
const summary = ref<AssessmentFinishResult | null>(null);
const firstCorrectCount = ref(0);
let loadSequence = 0;

const queryValue = (value: unknown) =>
  Array.isArray(value)
    ? String(value[0] || "")
    : typeof value === "string"
    ? value
    : "";

const contextQuery = computed(() => {
  const result: Record<string, string> = {};
  [
    "source",
    "nodeName",
    "nodeCode",
    "courseCode",
    "sectionCode",
    "unitCode",
    "returnTo",
    "category",
    "chapter",
    "type",
    "difficulty",
    "studyState",
    "title",
    "current",
  ].forEach((key) => {
    const value = queryValue(route.query[key]);
    if (value) result[key] = value;
  });
  return result;
});

const returnToPractice = () => {
  const returnTo = queryValue(route.query.returnTo);
  if (returnTo.startsWith("/") && !returnTo.startsWith("//")) {
    router.push(returnTo);
    return;
  }
  router.push({ path: "/assessments", query: contextQuery.value });
};

const currentQuestion = computed(
  () => questions.value[currentIndex.value] || null
);

/** 反馈展开后「下一题」才是主操作；作答前主操作是卡内的提交答案 */
const feedbackQuestionId = ref("");
const nextPrimary = computed(
  () =>
    Boolean(currentQuestion.value) &&
    feedbackQuestionId.value === String(currentQuestion.value?.id)
);
watch(
  () => (currentQuestion.value ? String(currentQuestion.value.id) : ""),
  () => {
    feedbackQuestionId.value = "";
  }
);

const answerHandler = computed(() => {
  const questionId = currentQuestion.value?.id;
  const answeredRunId = runId.value;
  return (result: LearningAnswerResultVO) =>
    onAnswered(questionId, answeredRunId, result);
});

const load = async () => {
  if (!runId.value) return;
  const sequence = ++loadSequence;
  const requestedRunId = runId.value;
  loading.value = true;
  try {
    const res = await getAssessmentRun(requestedRunId);
    if (sequence !== loadSequence || requestedRunId !== runId.value) return;
    if (res.data?.code === 0) {
      run.value = res.data.data;
      questions.value = (res.data.data.questions || []).map((question) => ({
        ...question,
        offline:
          question.offline ||
          !question.type ||
          question.reviewStatus !== "PUBLISHED",
      }));
      finished.value = res.data.data.status === "FINISHED";
      // 从作答记录读取本轮首次答对数
      firstCorrectCount.value = questions.value.filter(
        (q) => !q.offline && q.firstResult === "CORRECT"
      ).length;
      if (finished.value) {
        const available = questions.value.filter(
          (question) => !question.offline
        );
        summary.value = {
          runId: res.data.data.runId,
          totalQuestions: available.length,
          answeredQuestions: available.filter(
            (question) => question.hasAttempted
          ).length,
          firstCorrect: firstCorrectCount.value,
        };
      }
    } else {
      Message.error(res.data?.message || "加载测评会话失败");
    }
  } catch (e) {
    if (sequence === loadSequence)
      Message.error("加载测评会话失败，请稍后重试");
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
};

watch(
  runId,
  () => {
    run.value = null;
    questions.value = [];
    currentIndex.value = 0;
    finished.value = false;
    finishing.value = false;
    summary.value = null;
    firstCorrectCount.value = 0;
    void load();
  },
  { immediate: true }
);

const goToQuestion = (index: number) => {
  if (index >= 0 && index < questions.value.length) {
    currentIndex.value = index;
  }
};

const onAnswered = (
  questionId: LearningQuestionVO["id"] | undefined,
  answeredRunId: string,
  result: LearningAnswerResultVO
) => {
  if (answeredRunId !== runId.value) return;
  const question = questions.value.find((item) => item.id === questionId);
  if (!question || question.offline) return;
  if (!question.hasAttempted) {
    question.firstResult = result.correct ? "CORRECT" : "WRONG";
  }
  question.hasAttempted = true;
  question.latestResult = result.correct ? "CORRECT" : "WRONG";
  feedbackQuestionId.value = String(question.id);
  firstCorrectCount.value = questions.value.filter(
    (item) => !item.offline && item.firstResult === "CORRECT"
  ).length;
};

const finishRun = async () => {
  if (!run.value || finished.value || finishing.value) return;
  const requestedRunId = runId.value;
  finishing.value = true;
  try {
    const res = await finishAssessment({
      runId: run.value.runId,
      answeredCount: questions.value.filter((q) => q.hasAttempted).length,
      firstCorrect: firstCorrectCount.value,
    });
    if (requestedRunId !== runId.value) return;
    if (res.data?.code === 0) {
      summary.value = res.data.data;
      finished.value = true;
      currentIndex.value = questions.value.length
        ? questions.value.length - 1
        : 0;
    } else {
      Message.error(res.data?.message || "结束本轮失败");
    }
  } catch (e) {
    if (requestedRunId === runId.value)
      Message.error("结束本轮失败，请稍后重试");
  } finally {
    if (requestedRunId === runId.value) finishing.value = false;
  }
};
</script>

<style scoped>
.assessment-run {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-4);
}
.run-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: var(--oslab-space-3);
}
.run-title {
  margin: 0 0 var(--oslab-space-1);
  color: var(--oslab-ink);
  font-size: var(--oslab-text-page);
  font-weight: 600;
  line-height: 1.3;
}
.run-navigator {
  flex-shrink: 0;
}
.question-area {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-3);
}
.nav-buttons {
  display: flex;
  justify-content: space-between;
}
.run-summary {
  padding: var(--oslab-space-4);
}
.section-title {
  font-size: var(--oslab-font-body);
  font-weight: 600;
  color: var(--oslab-ink);
  margin-bottom: var(--oslab-space-2);
}
.btn-row {
  display: flex;
  gap: var(--oslab-space-2);
  margin-top: var(--oslab-space-3);
}
/* C+B 学习图册 */
.run-title {
  font-family: var(--oslab-font-display);
}
.question-area {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.run-summary {
  border-radius: var(--oslab-radius-container);
}

@media (max-width: 640px) {
  .run-title {
    font-size: 22px;
  }
}
</style>
