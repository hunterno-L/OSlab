<template>
  <div class="ai-page">
    <header class="ai-head">
      <div>
        <h1>AI 学习助手</h1>
        <p>统一课程上下文：检索教材、读取本人证据、给出下一步。未配置模型时自动降级。</p>
      </div>
      <router-link to="/ai/settings">
        <a-button>模型设置</a-button>
      </router-link>
    </header>

    <a-card class="ai-card" title="会话">
      <a-space wrap>
        <a-input v-model="courseCode" placeholder="课程编码" style="width: 260px" />
        <a-select v-model="goal" style="width: 140px">
          <a-option value="LEARN">继续学习</a-option>
          <a-option value="REVIEW">复习</a-option>
        </a-select>
        <a-input-number v-model="minutes" :min="5" :max="120" placeholder="可用分钟" />
        <a-button type="primary" :loading="starting" @click="start">开始会话</a-button>
        <a-tag v-if="sessionId" color="arcoblue">已连接 {{ sessionId.slice(0, 8) }}</a-tag>
      </a-space>
    </a-card>

    <a-card class="ai-card" title="提问">
      <a-textarea v-model="question" :auto-size="{ minRows: 3, maxRows: 6 }" placeholder="例如：缺页中断和页面置换是什么关系？" />
      <div class="ai-actions">
        <a-button type="primary" :loading="asking" :disabled="!question.trim()" @click="ask">提问</a-button>
        <a-button :loading="planning" @click="loadPlan">生成下一步</a-button>
        <a-tag v-if="answer" :color="answer.mode === 'MODEL' ? 'green' : 'orange'">
          {{ answer.mode === "MODEL" ? "模型回答" : "降级回答" }}
        </a-tag>
      </div>
    </a-card>

    <a-card v-if="answer" class="ai-card" title="回答">
      <pre class="ai-answer">{{ answer.answer }}</pre>
      <a-divider />
      <div class="ai-meta">
        <span>工具轨迹：</span>
        <a-tag v-for="tool in answer.tools" :key="tool.tool">{{ tool.tool }} · {{ tool.resultCount }}</a-tag>
      </div>
      <div class="ai-sources">
        <div class="ai-sources-title">引用来源</div>
        <a-list :data="answer.sources" size="small" :bordered="false">
          <template #item="{ item }">
            <a-list-item>
              <div>
                <div class="ai-source-title">{{ item.title }}</div>
                <div class="ai-source-id">{{ item.sourceId }}</div>
                <div class="ai-source-snippet">{{ item.snippet }}</div>
              </div>
            </a-list-item>
          </template>
        </a-list>
        <a-empty v-if="!answer.sources.length" description="没有命中教材段落" />
      </div>
      <div class="ai-meta">
        记录：作答 {{ answer.evidence.totalAttempts ?? 0 }} 次，答对 {{ answer.evidence.correct ?? 0 }} 次，待复习 {{ answer.evidence.needsReview ?? 0 }}
      </div>
    </a-card>

    <a-card v-if="tasks.length" class="ai-card" title="下一步">
      <a-list :data="tasks" size="small">
        <template #item="{ item }">
          <a-list-item>
            <div class="ai-task">
              <strong>{{ item.title }}</strong>
              <span class="ai-task-reason">{{ item.reason }}</span>
              <router-link :to="item.link"><a-button size="mini">去完成</a-button></router-link>
            </div>
          </a-list-item>
        </template>
      </a-list>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { Message } from "@arco-design/web-vue";
import { aiApi, type AiAnswer, type AiTask } from "@/api/ai";

const courseCode = ref("course.operating-systems");
const goal = ref("LEARN");
const minutes = ref(15);
const question = ref("");
const sessionId = ref("");
const answer = ref<AiAnswer | null>(null);
const tasks = ref<AiTask[]>([]);
const starting = ref(false);
const asking = ref(false);
const planning = ref(false);

const start = async () => {
  starting.value = true;
  try {
    const session = await aiApi.startSession({
      courseCode: courseCode.value,
      goal: goal.value,
      timeBudgetMinutes: minutes.value,
    });
    sessionId.value = session.sessionId;
    answer.value = null;
    tasks.value = [];
    Message.success("会话已建立");
  } catch (e: any) {
    Message.error(e?.message || "建立会话失败");
  } finally {
    starting.value = false;
  }
};

const ask = async () => {
  asking.value = true;
  try {
    if (!sessionId.value) {
      await start();
      if (!sessionId.value) return;
    }
    answer.value = await aiApi.ask({ sessionId: sessionId.value, question: question.value });
    await aiApi.help({ sessionId: sessionId.value, eventType: "TUTOR_ANSWER", payload: { question: question.value } });
  } catch (e: any) {
    Message.error(e?.message || "提问失败");
  } finally {
    asking.value = false;
  }
};

const loadPlan = async () => {
  planning.value = true;
  try {
    if (!sessionId.value) {
      await start();
      if (!sessionId.value) return;
    }
    const data = await aiApi.plan({ sessionId: sessionId.value });
    tasks.value = data.tasks || [];
    if (!tasks.value.length) {
      Message.info("暂时没有可安排的下一步，先完成一次练习或测评");
    }
  } catch (e: any) {
    Message.error(e?.message || "生成计划失败");
  } finally {
    planning.value = false;
  }
};
</script>

<style scoped>
.ai-page { max-width: 980px; margin: 0 auto; padding: 24px 16px 48px; display: flex; flex-direction: column; gap: 16px; }
.ai-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.ai-head h1 { margin: 0 0 6px; font-size: 22px; }
.ai-head p { margin: 0; color: #6b675c; font-size: 13px; }
.ai-card { border-radius: 12px; }
.ai-actions { display: flex; gap: 10px; align-items: center; margin-top: 12px; }
.ai-answer { white-space: pre-wrap; font-family: inherit; font-size: 14px; line-height: 1.8; margin: 0; }
.ai-meta { margin-top: 10px; font-size: 12px; color: #6b675c; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.ai-sources { margin-top: 8px; }
.ai-sources-title { font-size: 13px; font-weight: 600; margin-bottom: 6px; }
.ai-source-title { font-size: 13px; }
.ai-source-id { font-size: 11px; color: #a09a8a; }
.ai-source-snippet { font-size: 12px; color: #55524a; line-height: 1.7; }
.ai-task { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.ai-task-reason { color: #6b675c; font-size: 12px; }
</style>
