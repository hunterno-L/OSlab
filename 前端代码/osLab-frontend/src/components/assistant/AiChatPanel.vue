<template>
  <section class="ai-panel" role="dialog" aria-label="AI 导师对话面板">
    <header
      class="ai-panel-header"
      role="group"
      tabindex="0"
      aria-label="拖动 AI 导师面板（方向键移动）"
      @pointerdown="onHeaderPointerDown"
      @keydown="onHeaderKeydown"
    >
      <div class="ai-panel-titles">
        <span class="ai-panel-title">AI 导师</span>
        <span v-if="pageLabel" class="ai-panel-page">{{ pageLabel }}</span>
        <span v-if="session" class="ai-panel-session oslab-mono">
          <span>{{ session.courseCode }}</span>
          <span class="ai-panel-session-sep" aria-hidden="true"></span>
          <span>{{ session.focus === "REVIEW" ? "复习" : "学习" }}</span>
          <template v-if="session.timeBudgetMinutes">
            <span class="ai-panel-session-sep" aria-hidden="true"></span>
            <span>{{ session.timeBudgetMinutes }} 分钟</span>
          </template>
        </span>
      </div>
      <div class="ai-panel-actions">
        <button
          type="button"
          class="ai-panel-btn"
          title="打开模型设置"
          @click="openModelSettings"
        >
          模型
        </button>
        <button
          type="button"
          class="ai-panel-btn"
          :disabled="loading"
          title="清空当前会话"
          @click="onClear"
        >
          清空
        </button>
        <button
          type="button"
          class="ai-panel-btn"
          title="关闭（Esc）"
          aria-label="关闭 AI 助手"
          @click="emit('close')"
        >
          关闭
        </button>
      </div>
    </header>

    <div
      v-if="sessionRestored && messages.length === 0"
      class="ai-panel-restored"
    >
      <span class="ai-panel-restored-label">持久会话</span>
      <span>已恢复本课程会话；浏览器只保存会话指针。</span>
    </div>

    <section
      v-if="nextActions.length || dueReviewCount > 0"
      class="ai-panel-plan"
      aria-label="当前课程下一步"
    >
      <div class="ai-panel-plan-head">
        <span class="ai-panel-plan-label">当前课程计划</span>
        <button
          v-if="nextActions.length > 1"
          type="button"
          class="ai-panel-plan-toggle"
          @click="planExpanded = !planExpanded"
        >
          {{ planExpanded ? "收起" : `全部 ${nextActions.length} 项` }}
        </button>
      </div>
      <div class="ai-panel-plan-body" :class="{ 'is-expanded': planExpanded }">
        <AiNextActions
          :actions="visiblePlanActions"
          :course-code="courseCode"
          :due-count="dueReviewCount"
          :loading="nextActionsLoading"
          :error="nextActionsError"
          title=""
          empty-text="当前没有可执行的下一步。"
          compact
          :refreshable="false"
        />
      </div>
    </section>

    <div
      ref="listRef"
      class="ai-panel-messages"
      role="log"
      aria-live="polite"
      aria-relevant="additions"
      aria-label="AI 对话记录"
    >
      <div v-if="messages.length === 0 && !loading" class="ai-panel-empty">
        <p>
          导师会带入当前课程、所在页面和本人学习记录作答，并给出可执行的下一步。
        </p>
        <div class="ai-panel-prompts">
          <button
            v-for="prompt in suggestedPrompts"
            :key="prompt"
            type="button"
            class="ai-panel-prompt"
            @click="sendPrompt(prompt)"
          >
            {{ prompt }}
          </button>
        </div>
        <button
          v-if="!nextActions.length"
          type="button"
          class="ai-panel-review"
          @click="startReview"
        >
          按 15 分钟安排复习
        </button>
      </div>

      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="ai-msg"
        :class="msg.role === 'user' ? 'ai-msg-user' : 'ai-msg-assistant'"
      >
        <div v-if="msg.role === 'assistant' && msg.mode" class="ai-msg-meta">
          <AiModeBadge :mode="msg.mode" />
          <span v-if="msg.usage" class="ai-msg-usage oslab-mono">
            <span>{{ msg.usage.model }}</span>
            <span class="ai-msg-usage-sep" aria-hidden="true"></span>
            <span>{{ msg.usage.latencyMs }} ms</span>
          </span>
        </div>
        <!-- AI 回复走 MdViewer（含 rehype-sanitize 消毒）；渲染异常时回退纯文本 -->
        <div v-if="msg.role === 'assistant' && !mdBroken" class="ai-msg-md">
          <MdViewer :value="msg.content" />
        </div>
        <div v-else class="ai-msg-text">{{ msg.content }}</div>

        <AiDiagnosisPanel
          v-if="msg.role === 'assistant' && msg.diagnosis"
          :diagnosis="msg.diagnosis"
        />
        <AiToolTrace
          v-if="msg.role === 'assistant' && msg.toolTrace?.length"
          :entries="msg.toolTrace"
        />

        <div
          v-if="msg.role === 'assistant' && msg.sources?.length"
          class="ai-msg-sources"
        >
          <p class="ai-msg-sources-title">来源（点击打开定位）</p>
          <AiSourceCard
            v-for="(source, sourceIndex) in msg.sources"
            :key="source.sourceId + source.blockId"
            :source="source"
            :index="sourceIndex"
            @open="openSource"
          />
        </div>

        <AiNextActions
          v-if="msg.role === 'assistant' && msg.nextActions?.length"
          :actions="msg.nextActions"
          :course-code="courseCode"
          title="下一步"
          empty-text=""
          compact
          :refreshable="false"
        />

        <div
          v-if="msg.role === 'assistant' && msg.mode === 'FALLBACK'"
          class="ai-msg-fallback"
        >
          <span class="ai-msg-fallback-label">降级回答</span>
          <span>本轮未完成完整语义检索或模型调用，不包装成完整能力。</span>
        </div>
      </div>

      <div v-if="loading" class="ai-msg ai-msg-assistant">
        <div class="ai-msg-text ai-msg-pending">
          {{
            pollingTurnId ? "导师正在检索资料并核对证据…" : "正在建立本轮对话…"
          }}
        </div>
      </div>
    </div>

    <div v-if="errorMsg" class="ai-panel-error">
      <span class="ai-panel-error-text">{{ errorMsg }}</span>
      <button
        v-if="canRetry"
        type="button"
        class="ai-panel-retry"
        @click="onRetry"
      >
        重试
      </button>
    </div>

    <footer class="ai-panel-footer">
      <textarea
        ref="inputRef"
        v-model="draft"
        class="ai-panel-input"
        aria-label="向 AI 助手提问"
        rows="2"
        :disabled="loading"
        placeholder="输入问题（Enter 发送，Shift+Enter 换行）"
        @keydown.enter.exact.prevent="onSend"
        @keydown.esc.prevent.stop="emit('close')"
      ></textarea>
      <button
        type="button"
        class="ai-panel-send"
        :disabled="loading || !draft.trim()"
        @click="onSend"
      >
        {{ loading ? "处理中…" : "发送" }}
      </button>
    </footer>
  </section>
</template>

<script setup lang="ts">
/* eslint-disable no-undef -- Vue SFC 编译器宏由插件注入 */
import {
  computed,
  nextTick,
  onErrorCaptured,
  onMounted,
  ref,
  watch,
} from "vue";
import { useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import MdViewer from "../MdViewer.vue";
import AiSourceCard from "@/components/ai/AiSourceCard.vue";
import AiToolTrace from "@/components/ai/AiToolTrace.vue";
import AiDiagnosisPanel from "@/components/ai/AiDiagnosisPanel.vue";
import AiNextActions from "@/components/ai/AiNextActions.vue";
import AiModeBadge from "@/components/ai/AiModeBadge.vue";
import { useAiConversation } from "@/composables/useAiConversation";
import { getAiPageContext } from "@/composables/useAiPageContext";
import type { AiSourceRef } from "@/api/ai";
import {
  aiSourceRoute,
  highlightSourceInPage,
  openAiMaterialSource,
} from "@/utils/aiSources";

// eslint-disable-next-line no-undef
const props = withDefaults(
  defineProps<{
    /** 页面通过 oslab:ai-ask 带入的初始问题；消费后通知外层清空 */
    initialPrompt?: string;
  }>(),
  { initialPrompt: "" }
);

// eslint-disable-next-line no-undef
const emit = defineEmits<{
  (e: "close"): void;
  (e: "headerDrag", event: PointerEvent): void;
  (e: "headerMove", delta: { dx: number; dy: number }): void;
  (e: "promptConsumed"): void;
}>();

const router = useRouter();
const {
  session,
  messages,
  loading,
  errorMsg,
  canRetry,
  activeCourseCode,
  nextActions,
  nextActionsLoading,
  nextActionsError,
  dueReviewCount,
  pollingTurnId,
  sessionRestored,
  send,
  retry,
  clear,
  syncCourse,
  refreshNextActions,
  setGoal,
} = useAiConversation();

const draft = ref("");
const mdBroken = ref(false);
const listRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLTextAreaElement | null>(null);
const pageLabel = ref("");
const pageCourseCode = ref("");

const courseCode = computed(
  () =>
    pageCourseCode.value || activeCourseCode.value || "course.operating-systems"
);

/** 计划条默认只露出一项，避免挤占对话空间 */
const planExpanded = ref(false);
const visiblePlanActions = computed(() =>
  planExpanded.value ? nextActions.value : nextActions.value.slice(0, 1)
);

const suggestedPrompts = [
  "解释当前页面最关键的规则",
  "我最近错在哪里？请引用我的证据",
  "帮我区分 FIFO 与 LRU 的换出规则",
];

onMounted(() => {
  // 面板仅在打开时挂载，此时读取的即当前页面注册的上下文
  const context = getAiPageContext();
  const pageName = context?.pageName ?? "";
  const pageDetail = context?.unitName || context?.nodeName || "";
  pageLabel.value = pageDetail ? `${pageName} / ${pageDetail}` : pageName;
  pageCourseCode.value = context?.courseCode ?? "";
  if (pageCourseCode.value) {
    void syncCourse(pageCourseCode.value).then(() => refreshNextActions());
  }
  if (messages.value.length) {
    scrollToBottom();
  }
  nextTick(() => inputRef.value?.focus());
  if (props.initialPrompt?.trim()) {
    void consumeInitialPrompt(props.initialPrompt);
  }
});

async function consumeInitialPrompt(text: string) {
  const prompt = text.trim();
  if (!prompt) return;
  emit("promptConsumed");
  await nextTick();
  await sendPrompt(prompt);
}

watch(
  () => props.initialPrompt,
  (value) => {
    if (value?.trim()) void consumeInitialPrompt(value);
  }
);

// Markdown 渲染异常时整体回退为纯文本展示，保证回复始终可读
onErrorCaptured((err) => {
  mdBroken.value = true;
  console.error("[AI 导师] Markdown 渲染失败，已回退为纯文本", err);
  return false;
});

watch([() => messages.value.length, loading], () => {
  if (messages.value.length || loading.value) {
    nextTick(scrollToBottom);
  }
});

function scrollToBottom() {
  const list = listRef.value;
  if (list) {
    list.scrollTop = list.scrollHeight;
  }
}

async function onSend() {
  const text = draft.value.trim();
  if (!text || loading.value) {
    return;
  }
  draft.value = "";
  await send(text);
}

async function sendPrompt(text: string) {
  if (loading.value) return;
  await send(text);
}

async function startReview() {
  if (loading.value) return;
  const updated = await setGoal({
    courseCode: courseCode.value,
    focus: "REVIEW",
    goal: "按可用时间复习",
    timeBudgetMinutes: 15,
  });
  if (!updated) {
    Message.error(errorMsg.value || "复习计划暂时无法建立");
    return;
  }
  await refreshNextActions({ courseCode: courseCode.value });
  Message.success("已按 15 分钟生成复习计划，可在上方任务卡开始");
}

async function onRetry() {
  if (loading.value) {
    return;
  }
  await retry();
}

function openModelSettings() {
  void router.push("/settings/models");
}

function onClear() {
  if (messages.value.length === 0) {
    return;
  }
  if (window.confirm("确定清空当前会话吗？课程与学习记录不会被删除。")) {
    void clear();
  }
}

function openSource(source: AiSourceRef) {
  if (openAiMaterialSource(source)) return;
  if (highlightSourceInPage(source)) return;
  const route = aiSourceRoute(source);
  if (route) {
    void router.push(route);
    Message.info("已打开对应课程章节；可在课程单元内定位资料段落。");
    return;
  }
  Message.warning("该来源暂无可打开的站内位置。");
}

/**
 * 面板头部按下 → 交给外层容器启动拖动；头部内的按钮不作为拖动起点。
 */
function onHeaderPointerDown(e: PointerEvent) {
  if (e.button !== 0) {
    return;
  }
  const target = e.target as HTMLElement | null;
  if (target && target.closest("button")) {
    return;
  }
  emit("headerDrag", e);
}

/** 键盘替代拖动：头部获得焦点后可用方向键移动面板。 */
function onHeaderKeydown(e: KeyboardEvent) {
  if (e.target !== e.currentTarget) return;
  const step = 16;
  const delta =
    e.key === "ArrowLeft"
      ? { dx: -step, dy: 0 }
      : e.key === "ArrowRight"
      ? { dx: step, dy: 0 }
      : e.key === "ArrowUp"
      ? { dx: 0, dy: -step }
      : e.key === "ArrowDown"
      ? { dx: 0, dy: step }
      : null;
  if (!delta) return;
  e.preventDefault();
  emit("headerMove", delta);
}
</script>

<style scoped>
.ai-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: var(--oslab-panel);
  font-family: var(--oslab-font-ui);
  font-size: var(--oslab-font-body);
  color: var(--oslab-ink);
}

/* 头部：唯一可拖动区域 */
.ai-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--oslab-space-2);
  padding: 8px 12px;
  background: var(--oslab-panel-soft);
  border-bottom: 1px solid var(--oslab-line);
  cursor: grab;
  touch-action: none;
  user-select: none;
}
.ai-panel-header:active {
  cursor: grabbing;
}
.ai-panel-titles {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.ai-panel-title {
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.3;
}
.ai-panel-page,
.ai-panel-session {
  color: var(--oslab-muted-strong);
  font-size: 11px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ai-panel-session {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  color: var(--oslab-muted);
  font-size: 10px;
}
.ai-panel-session > span:not(.ai-panel-session-sep) {
  overflow: hidden;
  text-overflow: ellipsis;
}
.ai-panel-session-sep {
  flex: none;
  width: 1px;
  height: 9px;
  background: var(--oslab-line-strong);
}
.ai-panel-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}
.ai-panel-btn {
  min-height: 32px;
  padding: 0 8px;
  border: none;
  background: transparent;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  font-family: var(--oslab-font-ui);
  cursor: pointer;
  border-radius: var(--oslab-radius-sm);
  transition: background var(--oslab-duration-fast) var(--oslab-ease),
    color var(--oslab-duration-fast) var(--oslab-ease);
}
.ai-panel-btn:hover:not(:disabled) {
  background: var(--oslab-panel-muted);
  color: var(--oslab-primary);
}
.ai-panel-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ai-panel-btn:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 1px;
}

.ai-panel-restored {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--oslab-line);
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink-soft);
  font-size: 11px;
  line-height: 1.55;
}
.ai-panel-restored-label {
  flex: none;
  color: var(--oslab-ink);
  font-weight: 700;
}

/* 计划条固定在对话流上方，不随消息滚动 */
.ai-panel-plan {
  flex: none;
  padding: 8px 12px 4px;
  background: var(--oslab-panel-soft);
  border-bottom: 1px solid var(--oslab-line);
}
.ai-panel-plan-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--oslab-space-2);
}
.ai-panel-plan-label {
  color: var(--oslab-muted-strong);
  font-size: 11px;
  font-weight: 700;
}
.ai-panel-plan-toggle {
  min-height: 28px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--oslab-primary);
  font: inherit;
  font-size: 11px;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}
.ai-panel-plan-body {
  max-height: 132px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
}
.ai-panel-plan-body.is-expanded {
  max-height: min(320px, 40vh);
}

/* 消息流：可滚动、可选中文本 */
.ai-panel-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-2);
  padding: var(--oslab-space-3);
  background: var(--oslab-canvas);
  user-select: text;
}
.ai-panel-empty {
  margin: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: min(100%, 340px);
  padding: 12px 4px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.ai-panel-empty p {
  margin: 0;
  line-height: 1.65;
  text-wrap: pretty;
}
.ai-panel-prompts {
  display: flex;
  flex-direction: column;
  margin-top: 2px;
  border-bottom: 1px solid var(--oslab-line-soft);
}
.ai-panel-prompt {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-height: 34px;
  padding: 6px 0;
  border: 0;
  border-top: 1px solid var(--oslab-line-soft);
  background: transparent;
  color: var(--oslab-ink-soft);
  font: inherit;
  font-size: var(--oslab-text-small);
  text-align: left;
  cursor: pointer;
  transition: color var(--oslab-duration-fast) var(--oslab-ease);
}
.ai-panel-prompt::before {
  content: "→";
  flex: none;
  color: var(--oslab-annotation);
}
.ai-panel-prompt:hover {
  color: var(--oslab-primary);
}
.ai-panel-review {
  min-height: 40px;
  padding: 0 12px;
  border: 0;
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
  font: inherit;
  font-size: var(--oslab-text-small);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--oslab-duration-fast) var(--oslab-ease);
}
.ai-panel-review:hover {
  background: var(--oslab-primary-hover);
}
.ai-panel-prompt:focus-visible,
.ai-panel-review:focus-visible,
.ai-panel-plan-toggle:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
  border-radius: var(--oslab-radius-sm);
}
.ai-msg {
  max-width: 92%;
}
.ai-msg-user {
  align-self: flex-end;
}
.ai-msg-assistant {
  align-self: stretch;
}
.ai-msg-text {
  padding: var(--oslab-space-2) var(--oslab-space-3);
  border-radius: var(--oslab-radius-lg);
  font-size: var(--oslab-font-body);
  line-height: var(--oslab-leading-body);
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-msg-user .ai-msg-text {
  background: var(--oslab-primary-soft);
  color: var(--oslab-ink);
}
.ai-msg-assistant .ai-msg-text {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line-soft);
}
.ai-msg-pending {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.ai-msg-md {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-lg);
  padding: var(--oslab-space-2) var(--oslab-space-3);
  word-break: break-word;
}
.ai-msg-md :deep(.bytemd) {
  background: transparent;
  border: none;
  padding: 0;
  min-height: 0;
}
.ai-msg-md :deep(.markdown-body) {
  background: transparent;
  font-size: var(--oslab-font-body);
}
.ai-msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.ai-msg-usage {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--oslab-muted);
  font-size: 10px;
}
.ai-msg-usage-sep {
  width: 1px;
  height: 8px;
  background: var(--oslab-line-strong);
}
.ai-msg-sources {
  display: flex;
  flex-direction: column;
  margin-top: 10px;
}
.ai-msg-sources-title {
  margin: 0 0 2px;
  color: var(--oslab-muted-strong);
  font-size: 11px;
  font-weight: 600;
}
.ai-msg-fallback {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 8px;
  padding: 7px 9px;
  border-radius: var(--oslab-radius-sm);
  background: var(--oslab-review-soft);
  color: var(--oslab-review-text);
  font-size: 11px;
  line-height: 1.55;
}
.ai-msg-fallback-label {
  flex: none;
  font-weight: 700;
}

/* 错误条 + 重试 */
.ai-panel-error {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
  padding: var(--oslab-space-2) var(--oslab-space-3);
  background: var(--oslab-weak-soft);
  color: var(--oslab-weak-text);
  font-size: var(--oslab-text-small);
}
.ai-panel-error-text {
  flex: 1;
  min-width: 0;
}
.ai-panel-retry {
  flex-shrink: 0;
  min-height: 32px;
  padding: 0 8px;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: var(--oslab-text-small);
  font-family: var(--oslab-font-ui);
  text-decoration: underline;
  text-underline-offset: 3px;
  border-radius: var(--oslab-radius-sm);
  cursor: pointer;
}
.ai-panel-retry:hover {
  background: var(--oslab-panel);
}
.ai-panel-retry:focus-visible {
  outline: 2px solid var(--oslab-weak-text);
  outline-offset: 1px;
}

/* 输入区 */
.ai-panel-footer {
  display: flex;
  align-items: flex-end;
  gap: var(--oslab-space-2);
  padding: var(--oslab-space-2) var(--oslab-space-3);
  border-top: 1px solid var(--oslab-line);
  background: var(--oslab-panel);
}
.ai-panel-input {
  flex: 1;
  min-height: 48px;
  resize: none;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-control);
  padding: var(--oslab-space-2);
  font-family: var(--oslab-font-ui);
  font-size: var(--oslab-font-body);
  line-height: var(--oslab-leading-body);
  color: var(--oslab-ink);
  background: var(--oslab-panel);
}
.ai-panel-input:disabled {
  background: var(--oslab-panel-soft);
  color: var(--oslab-muted-strong);
}
.ai-panel-send {
  flex-shrink: 0;
  min-height: 40px;
  border: none;
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
  font-family: var(--oslab-font-ui);
  font-size: var(--oslab-text-small);
  font-weight: 600;
  padding: 0 14px;
  border-radius: var(--oslab-radius-control);
  cursor: pointer;
  transition: background var(--oslab-duration-fast) var(--oslab-ease);
}
.ai-panel-send:hover:not(:disabled) {
  background: var(--oslab-primary-hover);
}
.ai-panel-send:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.ai-panel-send:focus-visible,
.ai-panel-input:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}
@media (prefers-reduced-motion: reduce) {
  .ai-panel-btn,
  .ai-panel-prompt,
  .ai-panel-review,
  .ai-panel-send {
    transition: none;
  }
}
</style>
