<template>
  <section
    class="tutor-panel"
    :class="`mode-${mode}`"
    role="dialog"
    :aria-modal="mode === 'expanded'"
    aria-label="AI 导师对话"
  >
    <header class="tutor-head">
      <div class="tutor-head-title">
        <h2 class="tutor-title">AI 导师</h2>
        <p class="tutor-subtitle">
          <span>{{ courseLabel }}</span>
          <span
            v-if="sceneLabel"
            class="tutor-subtitle-sep"
            aria-hidden="true"
          ></span>
          <span v-if="sceneLabel">{{ sceneLabel }}</span>
        </p>
      </div>
      <div class="tutor-head-actions">
        <button
          type="button"
          class="tutor-head-btn"
          :aria-expanded="historyOpen"
          @click="toggleHistory"
        >
          最近对话
        </button>
        <button
          v-if="mode !== 'expanded' && mode !== 'full'"
          type="button"
          class="tutor-head-btn"
          @click="emit('expand')"
        >
          展开
        </button>
        <button
          v-else-if="mode === 'expanded'"
          type="button"
          class="tutor-head-btn"
          @click="emit('collapse')"
        >
          收起
        </button>
        <div class="tutor-more">
          <button
            type="button"
            class="tutor-head-btn tutor-more-trigger"
            aria-label="更多操作"
            :aria-expanded="moreOpen"
            @click="moreOpen = !moreOpen"
          >
            ⋯
          </button>
          <div v-if="moreOpen" class="tutor-more-menu" role="menu">
            <button type="button" role="menuitem" @click="onNewTopic">
              新话题
            </button>
            <button type="button" role="menuitem" @click="openModelSettings">
              模型设置
            </button>
          </div>
        </div>
        <button
          type="button"
          class="tutor-head-btn tutor-close"
          aria-label="关闭导师面板"
          @click="emit('close')"
        >
          ×
        </button>
      </div>
    </header>

    <div class="tutor-context-row">
      <div class="tutor-scope" role="group" aria-label="本轮上下文范围">
        <button
          type="button"
          :class="{ active: scope === 'PAGE' }"
          @click="scope = 'PAGE'"
        >
          当前页面
        </button>
        <button
          type="button"
          :class="{ active: scope === 'COURSE' }"
          @click="scope = 'COURSE'"
        >
          整门课程
        </button>
      </div>
      <span v-if="pageContextLabel" class="tutor-context-label">{{
        pageContextLabel
      }}</span>
    </div>

    <div v-if="isReadOnlySession" class="tutor-banner">
      <span>这是已关闭的历史会话，只可查看。</span>
      <button type="button" @click="onNewTopic">开始新话题</button>
    </div>

    <div
      ref="listRef"
      class="tutor-messages"
      role="log"
      aria-live="polite"
      aria-relevant="additions"
      aria-label="导师对话记录"
      @scroll="onScroll"
    >
      <button
        v-if="historyHasMore"
        type="button"
        class="tutor-load-earlier"
        :disabled="historyLoading"
        @click="loadEarlier"
      >
        {{ historyLoading ? "正在加载更早记录…" : "加载更早记录" }}
      </button>
      <div v-if="historyError" class="tutor-history-error" role="alert">
        <span>{{ historyError }}</span>
        <button type="button" @click="retryHistory">重试</button>
      </div>

      <div v-if="!messages.length && !loading" class="tutor-empty">
        <p class="tutor-empty-intro">
          导师会带入当前课程、页面上下文和你自己的学习记录；打开面板不会自动提问。
        </p>
        <div class="tutor-suggestions">
          <button
            v-for="suggestion in suggestions"
            :key="suggestion"
            type="button"
            class="tutor-suggestion"
            @click="useSuggestion(suggestion)"
          >
            {{ suggestion }}
          </button>
        </div>
      </div>

      <article
        v-for="(message, index) in messages"
        :key="messageKey(message, index)"
        class="tutor-msg"
        :class="`is-${message.role}`"
      >
        <template v-if="message.role === 'user'">
          <p class="tutor-user-bubble">{{ message.content }}</p>
          <p v-if="messageMeta(message)" class="tutor-user-meta">
            {{ messageMeta(message) }}
          </p>
        </template>

        <template v-else>
          <div class="tutor-answer-head">
            <AiModeBadge v-if="message.mode" :mode="message.mode" />
            <span
              v-if="message.usage?.model"
              class="tutor-model-name oslab-mono"
              >{{ message.usage.model }}</span
            >
            <span
              v-if="message.attempt && message.attempt > 1"
              class="tutor-attempt"
              >第 {{ message.attempt }} 次尝试</span
            >
            <button
              v-if="message.content && !message.pending && !message.error"
              type="button"
              class="tutor-mini-btn"
              @click="copyText(message.content, messageKey(message, index))"
            >
              {{ copiedKey === messageKey(message, index) ? "已复制" : "复制" }}
            </button>
          </div>

          <div v-if="message.pending" class="tutor-pending" aria-live="polite">
            正在回复…
          </div>
          <div
            v-else-if="message.content && !message.error && !mdBroken"
            class="tutor-answer tutor-md"
          >
            <MdViewer :value="message.content" />
          </div>
          <div
            v-else-if="message.content && !message.error"
            class="tutor-answer tutor-plain"
          >
            {{ message.content }}
          </div>

          <div
            v-if="message.mode === 'FALLBACK' && !message.pending"
            class="tutor-fallback"
          >
            本轮为降级回答：模型或语义检索未完成，内容来自真实检索与规则结论。
          </div>

          <div v-if="message.sources?.length" class="tutor-sources">
            <button
              type="button"
              class="tutor-sources-toggle"
              :aria-expanded="isSourcesOpen(message)"
              @click="toggleSources(message)"
            >
              来源 {{ message.sources.length }}
              <span aria-hidden="true">{{
                isSourcesOpen(message) ? "▾" : "▸"
              }}</span>
            </button>
            <div v-if="isSourcesOpen(message)" class="tutor-source-list">
              <AiSourceCard
                v-for="(source, sourceIndex) in message.sources"
                :key="source.sourceId + source.blockId"
                :source="source"
                :index="sourceIndex"
                @open="openSource"
              />
            </div>
          </div>

          <details v-if="hasDetails(message)" class="tutor-details">
            <summary>详情</summary>
            <p v-if="message.usage" class="tutor-usage oslab-mono">
              {{ message.usage.model }} · {{ message.usage.latencyMs }} ms
              <template v-if="message.usage.tokens">
                · {{ message.usage.tokens.total }} tokens
              </template>
              <template v-if="message.usage.toolCalls !== undefined">
                · {{ message.usage.toolCalls }} 次工具
              </template>
            </p>
            <AiToolTrace :entries="message.toolTrace || []" />
            <AiDiagnosisPanel
              v-if="message.diagnosis"
              :diagnosis="message.diagnosis"
            />
          </details>

          <div
            v-if="
              message.nextActions?.length && !message.pending && !message.error
            "
            class="tutor-next"
          >
            <AiNextActions
              :actions="message.nextActions"
              :course-code="courseCode"
              title="下一步"
              empty-text=""
              compact
              :limit="1"
              :refreshable="false"
            />
          </div>

          <div v-if="message.error" class="tutor-msg-error">
            <span>{{ message.content }}</span>
            <button
              v-if="isLastMessage(index) && retryable && !pendingTurn"
              type="button"
              @click="onRetry"
            >
              重试
            </button>
          </div>
        </template>
      </article>

      <div v-if="loading && !hasPendingBubble" class="tutor-pending">
        正在回复…
      </div>
    </div>

    <button
      v-if="showJumpToBottom"
      type="button"
      class="tutor-jump"
      @click="scrollToBottom(true)"
    >
      有新回复 · 回到底部
    </button>

    <div v-if="errorMsg" class="tutor-error" role="alert">
      <span class="tutor-error-text">{{ errorMsg }}</span>
      <button
        v-if="pendingTurn"
        type="button"
        class="tutor-error-action"
        @click="onResume"
      >
        恢复
      </button>
      <button
        v-else-if="retryable"
        type="button"
        class="tutor-error-action"
        @click="onRetry"
      >
        重试
      </button>
    </div>

    <footer class="tutor-composer">
      <div v-if="incomingDraft !== null" class="tutor-draft-conflict">
        <span>带入的建议会覆盖未发送的草稿。</span>
        <button type="button" @click="applyIncomingDraft">替换</button>
        <button type="button" class="keep" @click="incomingDraft = null">
          保留原草稿
        </button>
      </div>
      <div v-if="attachments.length" class="tutor-attachments">
        <span
          v-for="(attachment, index) in attachments"
          :key="`${attachment.kind}-${index}-${attachment.label}`"
          class="tutor-attachment"
        >
          <span class="tutor-attachment-kind">{{
            attachmentKindLabel(attachment)
          }}</span>
          <span class="tutor-attachment-label">{{
            attachment.label || "附带内容"
          }}</span>
          <button
            type="button"
            :aria-label="`移除附带材料 ${attachment.label || ''}`"
            @click="removeAttachment(index)"
          >
            ×
          </button>
        </span>
        <span v-if="nextIntent === 'HINT'" class="tutor-attachment-hint"
          >本轮按“提示”请求，会先记录帮助资格</span
        >
      </div>
      <div class="tutor-input-row">
        <textarea
          ref="inputRef"
          v-model="draft"
          class="tutor-input"
          rows="1"
          aria-label="向导师提问"
          :placeholder="inputPlaceholder"
          :disabled="isReadOnlySession"
          @input="resizeInput"
          @compositionstart="composing = true"
          @compositionend="onCompositionEnd"
          @keydown="onKeydown"
        ></textarea>
        <button
          type="button"
          class="tutor-send"
          :disabled="!canSend"
          :aria-label="loading ? '导师正在回复' : '发送'"
          @click="onSend"
        >
          <span v-if="loading" class="tutor-send-wait">…</span>
          <span v-else aria-hidden="true">↑</span>
        </button>
      </div>
      <div class="tutor-composer-foot">
        <div class="tutor-model">
          <span class="tutor-focus">{{ focusLabel }}</span>
          <div class="tutor-model-menu">
            <button
              type="button"
              class="tutor-model-trigger"
              :aria-expanded="modelMenuOpen"
              @click="toggleModelMenu"
            >
              {{ currentModelLabel }} ▾
            </button>
            <div v-if="modelMenuOpen" class="tutor-model-list" role="menu">
              <button
                v-for="profile in usableProfiles"
                :key="profile.profileId"
                type="button"
                role="menuitem"
                :class="{
                  active: session?.modelProfileId === profile.profileId,
                }"
                @click="chooseModel(profile)"
              >
                <span>{{ profile.name }}</span>
                <span class="oslab-mono">{{ profile.model }}</span>
              </button>
              <button
                v-if="!usableProfiles.length"
                type="button"
                role="menuitem"
                @click="openModelSettings"
              >
                暂无已通过测试的模型，去设置
              </button>
              <button
                type="button"
                role="menuitem"
                class="tutor-model-settings"
                @click="openModelSettings"
              >
                管理模型…
              </button>
            </div>
          </div>
        </div>
        <span class="tutor-input-hint">
          {{
            loading
              ? "导师正在回复，可先写下一条"
              : "Enter 发送 · Shift+Enter 换行"
          }}
        </span>
      </div>
    </footer>

    <aside v-if="historyOpen" class="tutor-history" aria-label="最近对话">
      <header class="tutor-history-head">
        <h3>最近对话</h3>
        <button
          type="button"
          class="tutor-mini-btn"
          @click="historyOpen = false"
        >
          关闭
        </button>
      </header>
      <p class="tutor-history-note">只显示当前账号、当前课程的真实会话。</p>
      <div class="tutor-history-list">
        <button
          v-for="item in sessionHistory"
          :key="item.sessionId"
          type="button"
          class="tutor-history-item"
          :class="{ active: item.sessionId === session?.sessionId }"
          @click="chooseHistory(item)"
        >
          <span class="tutor-history-title">{{ item.title }}</span>
          <span class="tutor-history-meta">
            {{ item.status === "ACTIVE" ? "进行中" : "已关闭" }}
            <template v-if="item.updatedAt">
              · {{ formatTime(item.updatedAt) }}</template
            >
          </span>
        </button>
        <p v-if="sessionHistoryLoading" class="tutor-history-state">
          正在读取…
        </p>
        <p v-else-if="sessionHistoryError" class="tutor-history-state is-error">
          {{ sessionHistoryError }}
        </p>
        <p v-else-if="!sessionHistory.length" class="tutor-history-state">
          当前课程还没有历史会话。
        </p>
      </div>
      <div class="tutor-history-actions">
        <button
          v-if="currentSessionsCursor"
          type="button"
          class="tutor-mini-btn"
          :disabled="sessionHistoryLoading"
          @click="loadMoreHistory"
        >
          加载更多
        </button>
        <button type="button" class="tutor-history-new" @click="onNewTopic">
          新话题
        </button>
      </div>
    </aside>
  </section>
</template>

<script setup lang="ts">
/* eslint-disable no-undef -- Vue SFC 编译器宏由插件注入 */
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onErrorCaptured,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import MdViewer from "../MdViewer.vue";
import AiSourceCard from "@/components/ai/AiSourceCard.vue";
import AiToolTrace from "@/components/ai/AiToolTrace.vue";
import AiDiagnosisPanel from "@/components/ai/AiDiagnosisPanel.vue";
import AiNextActions from "@/components/ai/AiNextActions.vue";
import AiModeBadge from "@/components/ai/AiModeBadge.vue";
import {
  listAiModelProfiles,
  type AiModelProfileVO,
  type AiSourceRef,
} from "@/api/ai";
import { useAiConversation } from "@/composables/useAiConversation";
import {
  currentCourseCode,
  resolveCurrentCourse,
} from "@/composables/useCurrentCourse";
import { getAiPageContext } from "@/composables/useAiPageContext";
import {
  pendingTutorLaunch,
  consumeTutorLaunch,
} from "@/composables/useTutorLaunch";
import type {
  TutorAttachment,
  TutorContextScope,
  TutorIntent,
  TutorLaunchRequest,
  TutorScene,
} from "@/types/tutor";
import {
  TUTOR_INTENT_LABELS,
  TUTOR_SCENE_LABELS,
  normalizeIntent,
  normalizeScene,
} from "@/types/tutor";
import {
  aiSourceRoute,
  highlightSourceInPage,
  openAiMaterialSource,
} from "@/utils/aiSources";

// eslint-disable-next-line no-undef
withDefaults(
  defineProps<{
    mode?: "dock" | "float" | "expanded" | "full";
  }>(),
  { mode: "dock" }
);

// eslint-disable-next-line no-undef
const emit = defineEmits<{
  (e: "close"): void;
  (e: "expand"): void;
  (e: "collapse"): void;
}>();

const router = useRouter();
const route = useRoute();
const {
  session,
  messages,
  loading,
  errorMsg,
  retryable,
  activeCourseCode,
  sessionHistory,
  sessionHistoryLoading,
  sessionHistoryError,
  currentSessionsCursor,
  isReadOnlySession,
  pendingTurn,
  historyLoading,
  historyHasMore,
  historyError,
  send,
  retry,
  resume,
  newTopic,
  syncCourse,
  loadSessionHistory,
  openHistorySession,
  loadOlderTurns,
  retryHistory,
  setModelProfile,
  readDraft,
  writeDraft,
  clearDraft,
} = useAiConversation();

const listRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLTextAreaElement | null>(null);
const pageContext = ref(getAiPageContext());
const draft = ref("");
const incomingDraft = ref<string | null>(null);
const scene = ref<TutorScene>("GLOBAL");
const nextIntent = ref<TutorIntent>("ASK");
const scope = ref<TutorContextScope>("PAGE");
const attachments = ref<TutorAttachment[]>([]);
const contextRefs = ref<{
  unitCode?: string;
  nodeCodes?: string[];
  resourceRef?: string;
  selectedSourceIds?: string[];
  eventIds?: string[];
  attemptContextId?: string;
  assessmentRunId?: string;
}>({});
const composing = ref(false);
const historyOpen = ref(false);
const moreOpen = ref(false);
const modelMenuOpen = ref(false);
const profiles = ref<AiModelProfileVO[]>([]);
const mdBroken = ref(false);
const atBottom = ref(true);
const hasNewReply = ref(false);
const copiedKey = ref("");
const expandedSources = reactive(new Set<string>());
const pendingLaunchNonce = ref(0);
let panelDisposed = false;
let draftCourse = "";

const courseCode = computed(() => currentCourseCode.value);
const courseLabel = computed(
  () =>
    pageContext.value?.courseTitle ||
    session.value?.courseCode ||
    pageContext.value?.courseCode ||
    "当前课程"
);
const sceneLabel = computed(() => {
  const sceneName = TUTOR_SCENE_LABELS[scene.value] || "";
  if (nextIntent.value === "ASK") return sceneName;
  return `${sceneName} · ${TUTOR_INTENT_LABELS[nextIntent.value]}`;
});
const pageContextLabel = computed(() => {
  const ctx = pageContext.value;
  if (!ctx) return "";
  const detail = ctx.unitName || ctx.nodeName || ctx.experimentTitle || "";
  return detail ? `${ctx.pageName} / ${detail}` : ctx.pageName;
});
const focusLabel = computed(() =>
  session.value?.focus === "REVIEW" ? "复习" : "学习"
);
const usableProfiles = computed(() =>
  profiles.value.filter(
    (profile) => profile.active || profile.testStatus === "PASSED"
  )
);
const currentModelLabel = computed(() => {
  const id = session.value?.modelProfileId;
  if (!id) return "平台默认模型";
  const profile = profiles.value.find((item) => item.profileId === id);
  return profile ? profile.name : "已选模型";
});
const canSend = computed(
  () =>
    Boolean(courseCode.value && draft.value.trim()) &&
    !loading.value &&
    !isReadOnlySession.value
);
const inputPlaceholder = computed(() => {
  if (isReadOnlySession.value) return "已关闭会话，仅可查看历史";
  if (loading.value) return "导师正在回复；可以先写下一条，完成后发送";
  return "问导师一个与当前课程有关的问题";
});
const hasPendingBubble = computed(() =>
  messages.value.some((message) => message.pending)
);
const showJumpToBottom = computed(
  () => !atBottom.value && (hasNewReply.value || messages.value.length > 0)
);
const suggestions = computed(() => {
  const ctx = pageContext.value;
  const focus = ctx?.nodeName || ctx?.unitName || ctx?.experimentTitle || "";
  if (focus) {
    return [
      `解释「${focus}」的关键规则`,
      `用一个自检问题检查我对「${focus}」的理解`,
      "结合我的学习记录，下一步应该做什么？",
    ];
  }
  return [
    "解释当前页面最关键的规则",
    "结合我的学习记录，下一步应该做什么？",
    "帮我区分这门课里最容易混淆的两个概念",
  ];
});

onErrorCaptured((err) => {
  mdBroken.value = true;
  console.error("[AI 导师] Markdown 渲染失败，已回退为纯文本", err);
  return false;
});

onMounted(async () => {
  pageContext.value = getAiPageContext();
  if (!currentCourseCode.value) {
    try {
      await resolveCurrentCourse(
        pageContext.value?.courseCode,
        () => !panelDisposed
      );
    } catch {
      // Without a validated course, sending stays disabled.
    }
  }
  if (panelDisposed) return;
  const initialCourse = currentCourseCode.value;
  if (initialCourse) {
    if (draftCourse && draftCourse !== initialCourse)
      writeDraft(draft.value, draftCourse);
    draftCourse = initialCourse;
    draft.value = readDraft(initialCourse);
    await syncCourse(initialCourse);
  }
  if (panelDisposed) return;
  resizeInput();
  nextTick(() => inputRef.value?.focus());
  scrollToBottom(false);
  if (messages.value.length) scrollToBottom(false);
  const launch = consumeTutorLaunch();
  if (launch) void applyLaunch(launch);
  void loadProfiles();
  startCodeObserver();
  enhanceCodeBlocks();
  document.addEventListener("pointerdown", onDocumentPointerDown);
});

onBeforeUnmount(() => {
  panelDisposed = true;
  document.removeEventListener("pointerdown", onDocumentPointerDown);
  codeObserver?.disconnect();
  codeObserver = null;
  if (draftCourse) writeDraft(draft.value, draftCourse);
});

watch(pendingTutorLaunch, (value) => {
  if (!value || value.nonce === pendingLaunchNonce.value) return;
  pendingLaunchNonce.value = value.nonce;
  void applyLaunch(value.request);
});

watch(
  () => [route.fullPath, currentCourseCode.value],
  () => {
    const next = getAiPageContext();
    pageContext.value = next;
    const code = currentCourseCode.value;
    if (code !== draftCourse) {
      if (draftCourse) writeDraft(draft.value, draftCourse);
      draftCourse = code;
      draft.value = code ? readDraft(code) : "";
      resizeInput();
    }
    if (code && code !== activeCourseCode.value) void syncCourse(code);
  },
  { flush: "sync" }
);

watch(
  () => [messages.value.length, loading.value] as const,
  () => {
    if (atBottom.value) {
      nextTick(() => scrollToBottom(false));
    } else if (!loading.value) {
      hasNewReply.value = true;
    }
    nextTick(enhanceCodeBlocks);
  }
);

watch(
  () =>
    messages.value
      .map(
        (message) =>
          `${message.turnId || message.clientRequestId || ""}:${
            message.content?.length || 0
          }`
      )
      .join("|"),
  () => {
    if (atBottom.value) nextTick(() => scrollToBottom(false));
    else hasNewReply.value = true;
    nextTick(enhanceCodeBlocks);
  }
);

watch(
  () => draft.value,
  () => {
    if (draftCourse) writeDraft(draft.value, draftCourse);
    resizeInput();
  }
);

function resizeInput() {
  const input = inputRef.value;
  if (!input) return;
  input.style.height = "auto";
  const line = 24;
  input.style.height = `${Math.min(input.scrollHeight, line * 6 + 20)}px`;
}

function onCompositionEnd() {
  composing.value = false;
  resizeInput();
}

function onKeydown(e: KeyboardEvent) {
  if (e.key !== "Enter") return;
  if (e.isComposing || composing.value) return;
  if (e.shiftKey) return;
  e.preventDefault();
  void onSend();
}

function onDocumentPointerDown(e: PointerEvent) {
  const target = e.target as HTMLElement | null;
  if (!target) return;
  if (!target.closest(".tutor-more")) moreOpen.value = false;
  if (!target.closest(".tutor-model-menu")) modelMenuOpen.value = false;
}

function messageKey(
  message: { role: string; turnId?: string; clientRequestId?: string },
  index: number
) {
  return message.turnId
    ? `t-${message.turnId}`
    : message.clientRequestId
    ? `r-${message.clientRequestId}-${message.role}`
    : `${message.role}-${index}`;
}

function isLastMessage(index: number) {
  return index === messages.value.length - 1;
}

function messageMeta(message: { contextSummary?: string }) {
  return message.contextSummary || "";
}

function hasDetails(message: {
  usage?: unknown;
  toolTrace?: unknown[];
  diagnosis?: unknown;
}) {
  return Boolean(
    message.usage || message.toolTrace?.length || message.diagnosis
  );
}

function isSourcesOpen(message: { turnId?: string; clientRequestId?: string }) {
  const key = message.turnId || message.clientRequestId || "";
  return expandedSources.has(key);
}

function toggleSources(message: { turnId?: string; clientRequestId?: string }) {
  const key = message.turnId || message.clientRequestId || "";
  if (!key) return;
  if (expandedSources.has(key)) expandedSources.delete(key);
  else expandedSources.add(key);
}

function attachmentKindLabel(attachment: TutorAttachment) {
  return attachment.kind === "SELECTION"
    ? "选段"
    : attachment.kind === "CODE"
    ? "代码"
    : "报错";
}

function removeAttachment(index: number) {
  attachments.value = attachments.value.filter((_, i) => i !== index);
}

async function copyText(text: string, key = "") {
  try {
    await navigator.clipboard.writeText(text);
    copiedKey.value = key;
    Message.success("已复制");
    window.setTimeout(() => {
      copiedKey.value = "";
    }, 1600);
  } catch {
    Message.warning("复制失败，请手动选择文本复制。");
  }
}

/** 给代码块注入“复制”按钮（只添加自有 DOM，不改变 Markdown 净化链路）。 */
let codeObserver: MutationObserver | null = null;

function enhanceCodeBlocks() {
  const root = listRef.value;
  if (!root) return;
  root.querySelectorAll(".tutor-answer pre").forEach((pre) => {
    if (pre.querySelector(".tutor-code-copy")) return;
    const button = document.createElement("button");
    button.type = "button";
    button.className = "tutor-code-copy";
    button.textContent = "复制代码";
    button.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      void copyText(pre.textContent || "");
    });
    if (getComputedStyle(pre).position === "static") {
      (pre as HTMLElement).style.position = "relative";
    }
    pre.appendChild(button);
  });
}

function startCodeObserver() {
  const root = listRef.value;
  if (!root || typeof MutationObserver === "undefined") return;
  codeObserver = new MutationObserver(() => enhanceCodeBlocks());
  codeObserver.observe(root, { childList: true, subtree: true });
}

function scrollToBottom(smooth = true) {
  const list = listRef.value;
  if (!list) return;
  list.scrollTo({
    top: list.scrollHeight,
    behavior: smooth ? "smooth" : "auto",
  });
  atBottom.value = true;
  hasNewReply.value = false;
}

function onScroll() {
  const list = listRef.value;
  if (!list) return;
  const distance = list.scrollHeight - list.scrollTop - list.clientHeight;
  atBottom.value = distance < 64;
  if (atBottom.value) hasNewReply.value = false;
}

async function loadEarlier() {
  const list = listRef.value;
  const before = list ? list.scrollHeight : 0;
  await loadOlderTurns();
  await nextTick();
  if (list) {
    list.scrollTop += list.scrollHeight - before;
  }
}

function useSuggestion(text: string) {
  draft.value = text;
  nextTick(() => {
    inputRef.value?.focus();
    resizeInput();
  });
}

function applyIncomingDraft() {
  if (incomingDraft.value === null) return;
  draft.value = incomingDraft.value;
  incomingDraft.value = null;
  nextTick(resizeInput);
}

function inferSceneFromPage(): TutorScene {
  const ctx = pageContext.value;
  if (!ctx) return "GLOBAL";
  const page = ctx.pageName || "";
  if (ctx.experimentTitle || page.includes("实验")) return "EXPERIMENT";
  if (page.includes("节点")) return "NODE";
  if (page.includes("题")) return "QUESTION";
  if (ctx.unitName || ctx.unitCode) return "UNIT";
  return "GLOBAL";
}

async function applyLaunch(request: TutorLaunchRequest) {
  const requestCourse = request.courseCode;
  if (requestCourse && requestCourse !== activeCourseCode.value) {
    await syncCourse(requestCourse);
    draft.value = readDraft(requestCourse);
  }
  scene.value = normalizeScene(request.scene || inferSceneFromPage());
  nextIntent.value = normalizeIntent(request.intent);
  scope.value = request.scope || "PAGE";
  attachments.value = (request.attachments || []).slice(
    0,
    5
  ) as TutorAttachment[];
  contextRefs.value = {
    unitCode: request.unitCode,
    nodeCodes: request.nodeCodes,
    resourceRef: request.resourceRef,
    selectedSourceIds: request.selectedSourceIds,
    eventIds: request.eventIds,
    attemptContextId: request.attemptContextId,
    assessmentRunId: request.assessmentRunId,
  };
  // 题卡入口以 HINT + SEND 打开时不带草稿；补一条中性提示请求，避免空发送
  const launchDraft =
    request.draft ||
    (request.delivery === "SEND" && nextIntent.value === "HINT"
      ? "请给我一个不直接给出答案的提示，帮助我自己找到这道题的解法。"
      : "");
  if (launchDraft) {
    if (request.delivery === "SEND") {
      draft.value = launchDraft;
      await nextTick();
      await sendCurrent();
    } else if (draft.value.trim() && draft.value !== launchDraft) {
      incomingDraft.value = launchDraft;
    } else {
      draft.value = launchDraft;
    }
  }
  nextTick(() => {
    inputRef.value?.focus();
    resizeInput();
  });
}

function buildLaunch(): TutorLaunchRequest {
  return {
    scene: scene.value,
    intent: nextIntent.value,
    courseCode: courseCode.value || activeCourseCode.value,
    scope: scope.value,
    ...contextRefs.value,
    attachments: attachments.value.length ? attachments.value : undefined,
    delivery: "SEND",
  };
}

async function sendCurrent() {
  const text = draft.value.trim();
  if (!text || loading.value) return;
  const intentForRound = nextIntent.value;
  draft.value = "";
  const launch = buildLaunch();
  launch.intent = intentForRound;
  nextTick(resizeInput);
  const turn = await send({ message: text, launch });
  // 失败（非未定/超时）时把原问题放回输入框，避免用户重新输入
  if (!turn && !pendingTurn.value) {
    draft.value = text;
    nextTick(resizeInput);
  } else if (turn !== null) {
    clearDraft(courseCode.value);
  }
  // “提示”只对本次请求有效；追问恢复为普通提问，但保留答题上下文
  if (intentForRound === "HINT") nextIntent.value = "ASK";
}

async function onSend() {
  if (loading.value) {
    Message.info("导师正在回复当前会话，请稍候。");
    return;
  }
  if (isReadOnlySession.value) {
    Message.info("已关闭会话只可查看；请先开始新话题。");
    return;
  }
  await sendCurrent();
}

async function onRetry() {
  await retry();
}

async function onResume() {
  await resume();
}

async function onNewTopic() {
  moreOpen.value = false;
  if (loading.value) {
    Message.info("当前回复还在进行，完成后可开始新话题。");
    return;
  }
  if (
    messages.value.length &&
    !window.confirm("开始新话题会保留历史记录，但清空当前对话区。继续吗？")
  ) {
    return;
  }
  const ok = await newTopic(courseCode.value);
  if (ok) {
    draft.value = "";
    attachments.value = [];
    contextRefs.value = {};
    nextIntent.value = "ASK";
    historyOpen.value = false;
    Message.success("已开始新话题；下一次发送会创建新会话。");
  }
}

function toggleHistory() {
  historyOpen.value = !historyOpen.value;
  if (historyOpen.value) {
    void loadSessionHistory({ courseCode: courseCode.value, reset: true });
  }
}

async function loadMoreHistory() {
  await loadSessionHistory({ courseCode: courseCode.value });
}

async function chooseHistory(item: { sessionId: string; title: string }) {
  if (loading.value) {
    Message.info("当前回复进行中，完成后可切换会话。");
    return;
  }
  const ok = await openHistorySession(item.sessionId);
  if (ok) {
    historyOpen.value = false;
    draft.value = "";
    attachments.value = [];
    contextRefs.value = {};
    nextIntent.value = "ASK";
    await nextTick();
    scrollToBottom(false);
  } else if (errorMsg.value) {
    Message.error(errorMsg.value);
  }
}

function openSource(source: AiSourceRef) {
  if (openAiMaterialSource(source)) return;
  if (highlightSourceInPage(source)) return;
  const target = aiSourceRoute(source);
  if (target) {
    void router.push(target);
    Message.info("已打开对应课程章节；可在课程单元内定位资料段落。");
    return;
  }
  Message.warning("该来源暂无可打开的站内位置。");
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return `${date.getMonth() + 1}/${date.getDate()} ${String(
    date.getHours()
  ).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

async function loadProfiles() {
  try {
    const response = await listAiModelProfiles();
    if (response.data.code === 0 && response.data.data) {
      profiles.value = response.data.data;
    }
  } catch {
    // 模型列表读取失败不阻塞对话；菜单显示“平台默认模型”
  }
}

function toggleModelMenu() {
  modelMenuOpen.value = !modelMenuOpen.value;
  if (modelMenuOpen.value) void loadProfiles();
}

async function chooseModel(profile: AiModelProfileVO) {
  modelMenuOpen.value = false;
  const ok = await setModelProfile(profile.profileId);
  if (ok) {
    Message.success(`已切换：下一轮使用 ${profile.name}`);
  } else {
    Message.error(errorMsg.value || "切换模型失败");
  }
}

function openModelSettings() {
  modelMenuOpen.value = false;
  moreOpen.value = false;
  void router.push("/settings/models");
}
</script>

<style scoped>
.tutor-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-width: 0;
  background: var(--oslab-panel);
  color: var(--oslab-ink);
  font-family: var(--oslab-font-ui);
}

/* ---------- 头部 ---------- */
.tutor-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px 8px;
  border-bottom: 1px solid var(--oslab-line);
  background: var(--oslab-panel);
}
.tutor-head-title {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.tutor-title {
  margin: 0;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: 16px;
  font-weight: 650;
  line-height: 1.3;
}
.tutor-subtitle {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--oslab-muted-strong);
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tutor-subtitle-sep {
  flex: none;
  width: 1px;
  height: 9px;
  background: var(--oslab-line-strong);
}
.tutor-head-actions {
  flex: none;
  display: flex;
  align-items: center;
  gap: 2px;
}
.tutor-head-btn {
  min-height: 36px;
  padding: 0 8px;
  border: 0;
  border-radius: var(--oslab-radius-sm);
  background: transparent;
  color: var(--oslab-muted-strong);
  font: inherit;
  font-size: var(--oslab-text-small);
  cursor: pointer;
}
.tutor-head-btn:hover,
.tutor-head-btn[aria-expanded="true"] {
  background: var(--oslab-panel-soft);
  color: var(--oslab-primary);
}
.tutor-head-btn:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 1px;
}
.tutor-close {
  font-size: 20px;
  line-height: 1;
  padding: 0 10px;
}
.tutor-more {
  position: relative;
}
.tutor-more-menu,
.tutor-model-list {
  position: absolute;
  z-index: 5;
  right: 0;
  top: calc(100% + 4px);
  min-width: 168px;
  display: flex;
  flex-direction: column;
  padding: 4px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-panel);
  box-shadow: var(--oslab-shadow-overlay);
}
.tutor-more-menu button,
.tutor-model-list button {
  min-height: 36px;
  padding: 6px 10px;
  border: 0;
  border-radius: var(--oslab-radius-sm);
  background: transparent;
  color: var(--oslab-ink-soft);
  font: inherit;
  font-size: var(--oslab-text-small);
  text-align: left;
  cursor: pointer;
}
.tutor-more-menu button:hover,
.tutor-model-list button:hover,
.tutor-model-list button.active {
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
}
.tutor-model-list button {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

/* ---------- 上下文 ---------- */
.tutor-context-row {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--oslab-line-soft);
  background: var(--oslab-panel-soft);
  font-size: 11px;
  color: var(--oslab-muted-strong);
}
.tutor-scope {
  display: inline-flex;
  flex: none;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  overflow: hidden;
  background: var(--oslab-panel);
}
.tutor-scope button {
  min-height: 26px;
  padding: 0 9px;
  border: 0;
  background: transparent;
  color: var(--oslab-muted-strong);
  font: inherit;
  font-size: 11px;
  white-space: nowrap;
  cursor: pointer;
}
.tutor-scope button.active {
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
}
.tutor-context-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tutor-banner {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 12px;
  background: var(--oslab-review-soft);
  color: var(--oslab-review-text);
  font-size: 12px;
}
.tutor-banner button {
  flex: none;
  min-height: 32px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--oslab-radius-sm);
  background: var(--oslab-panel);
  color: var(--oslab-primary);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

/* ---------- 消息流 ---------- */
.tutor-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 14px 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  background: var(--oslab-panel);
  scrollbar-width: thin;
}
.tutor-load-earlier {
  align-self: center;
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel);
  color: var(--oslab-muted-strong);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}
.tutor-load-earlier:disabled {
  opacity: 0.6;
  cursor: default;
}
.tutor-history-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--oslab-radius-sm);
  background: var(--oslab-weak-soft);
  color: var(--oslab-weak-text);
  font-size: 12px;
}
.tutor-history-error button {
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
.tutor-empty {
  margin: auto;
  width: min(100%, 420px);
  display: flex;
  flex-direction: column;
  gap: 14px;
  color: var(--oslab-muted-strong);
}
.tutor-empty-intro {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  text-wrap: pretty;
}
.tutor-suggestions {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--oslab-line-soft);
}
.tutor-suggestion {
  min-height: 44px;
  padding: 10px 0;
  border: 0;
  border-bottom: 1px solid var(--oslab-line-soft);
  background: transparent;
  color: var(--oslab-ink-soft);
  font: inherit;
  font-size: 13px;
  line-height: 1.55;
  text-align: left;
  cursor: pointer;
}
.tutor-suggestion::before {
  content: "→";
  margin-right: 8px;
  color: var(--oslab-annotation);
}
.tutor-suggestion:hover {
  color: var(--oslab-primary);
}

.tutor-msg {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.tutor-msg.is-user {
  align-items: flex-end;
  gap: 3px;
}
.tutor-user-bubble {
  margin: 0;
  max-width: 88%;
  padding: 8px 12px;
  border-radius: 12px 12px 4px 12px;
  background: var(--oslab-primary-soft);
  color: var(--oslab-ink);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  text-wrap: pretty;
}
.tutor-user-meta {
  margin: 0;
  max-width: 88%;
  color: var(--oslab-muted);
  font-size: 10px;
  line-height: 1.4;
  text-align: right;
}
.tutor-answer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  color: var(--oslab-muted-strong);
  font-size: 10px;
}
.tutor-model-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tutor-attempt {
  color: var(--oslab-annotation);
}
.tutor-mini-btn {
  margin-left: auto;
  min-height: 28px;
  padding: 0 6px;
  border: 0;
  border-radius: var(--oslab-radius-sm);
  background: transparent;
  color: var(--oslab-muted-strong);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}
.tutor-mini-btn:hover {
  background: var(--oslab-panel-soft);
  color: var(--oslab-primary);
}
.tutor-answer {
  color: var(--oslab-ink);
  font-size: 15px;
  line-height: 1.7;
  word-break: break-word;
  overflow-wrap: anywhere;
}
.tutor-answer.tutor-md :deep(.markdown-body) {
  background: transparent;
  color: inherit;
  font-family: inherit;
  font-size: 15px;
  line-height: 1.7;
}
.tutor-answer.tutor-md :deep(.markdown-body p) {
  margin: 0 0 0.75em;
}
.tutor-answer.tutor-md :deep(.markdown-body pre) {
  position: relative;
  margin: 10px 0;
  padding: 12px 14px;
  border-radius: var(--oslab-radius-sm);
  background: var(--oslab-panel-soft);
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
}
.tutor-answer.tutor-md :deep(.markdown-body code) {
  font-size: 13px;
}
.tutor-answer.tutor-md :deep(.markdown-body table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
  font-size: 13px;
}
.tutor-answer.tutor-md :deep(.markdown-body img) {
  max-width: 100%;
  height: auto;
}
.tutor-answer.tutor-md :deep(.tutor-code-copy) {
  position: absolute;
  top: 6px;
  right: 6px;
  min-height: 26px;
  padding: 0 8px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-sm);
  background: var(--oslab-panel);
  color: var(--oslab-muted-strong);
  font-size: 10px;
  cursor: pointer;
  opacity: 0;
  transition: opacity var(--oslab-duration-fast) var(--oslab-ease);
}
.tutor-answer.tutor-md :deep(.markdown-body pre:hover .tutor-code-copy),
.tutor-answer.tutor-md :deep(.tutor-code-copy:focus-visible) {
  opacity: 1;
}
.tutor-plain {
  white-space: pre-wrap;
}
.tutor-pending {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
  color: var(--oslab-muted-strong);
  font-size: 12px;
}
.tutor-pending::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--oslab-primary);
  animation: tutor-pulse 1.4s ease-in-out infinite;
}
@keyframes tutor-pulse {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}
.tutor-fallback {
  margin-top: 8px;
  padding: 7px 9px;
  border-left: 2px solid var(--oslab-review-text);
  background: var(--oslab-review-soft);
  color: var(--oslab-review-text);
  font-size: 11px;
  line-height: 1.55;
}
.tutor-sources {
  margin-top: 10px;
  border-top: 1px solid var(--oslab-line-soft);
}
.tutor-sources-toggle {
  min-height: 34px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--oslab-muted-strong);
  font: inherit;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}
.tutor-sources-toggle:hover {
  color: var(--oslab-primary);
}
.tutor-source-list {
  display: flex;
  flex-direction: column;
}
.tutor-details {
  margin-top: 8px;
  color: var(--oslab-muted-strong);
  font-size: 11px;
}
.tutor-details summary {
  min-height: 30px;
  line-height: 30px;
  cursor: pointer;
  list-style: none;
}
.tutor-details summary::-webkit-details-marker {
  display: none;
}
.tutor-details summary::before {
  content: "▸ ";
  color: var(--oslab-annotation);
}
.tutor-details[open] summary::before {
  content: "▾ ";
}
.tutor-usage {
  margin: 0 0 6px;
  color: var(--oslab-muted);
  font-size: 10px;
}
.tutor-next {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--oslab-line-soft);
}
.tutor-msg-error {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: var(--oslab-radius-sm);
  background: var(--oslab-weak-soft);
  color: var(--oslab-weak-text);
  font-size: 12px;
  line-height: 1.6;
}
.tutor-msg-error button {
  flex: none;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}

.tutor-jump {
  position: absolute;
  left: 50%;
  bottom: 158px;
  transform: translateX(-50%);
  z-index: 3;
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel);
  color: var(--oslab-primary);
  font: inherit;
  font-size: 11px;
  font-weight: 600;
  box-shadow: var(--oslab-shadow-card);
  cursor: pointer;
}

/* ---------- 错误条 ---------- */
.tutor-error {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: var(--oslab-weak-soft);
  color: var(--oslab-weak-text);
  font-size: 12px;
}
.tutor-error-text {
  flex: 1;
  min-width: 0;
  line-height: 1.55;
}
.tutor-error-action {
  flex: none;
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid currentColor;
  border-radius: var(--oslab-radius-sm);
  background: transparent;
  color: inherit;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

/* ---------- 输入区 ---------- */
.tutor-composer {
  flex: none;
  position: relative;
  padding: 8px 12px 10px;
  border-top: 1px solid var(--oslab-line);
  background: var(--oslab-panel);
}
.tutor-draft-conflict {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  padding: 6px 8px;
  border-radius: var(--oslab-radius-sm);
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink-soft);
  font-size: 11px;
}
.tutor-draft-conflict button {
  border: 0;
  background: transparent;
  color: var(--oslab-primary);
  font: inherit;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}
.tutor-draft-conflict button.keep {
  color: var(--oslab-muted-strong);
}
.tutor-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}
.tutor-attachment {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  padding: 3px 4px 3px 8px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-soft);
  font-size: 10px;
  color: var(--oslab-ink-soft);
}
.tutor-attachment-kind {
  flex: none;
  color: var(--oslab-annotation);
  font-weight: 700;
}
.tutor-attachment-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tutor-attachment button {
  flex: none;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--oslab-muted-strong);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}
.tutor-attachment button:hover {
  background: var(--oslab-line-soft);
}
.tutor-attachment-hint {
  align-self: center;
  color: var(--oslab-muted);
  font-size: 10px;
}
.tutor-input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 6px 6px 6px 12px;
  border: 1px solid var(--oslab-line);
  border-radius: 14px;
  background: var(--oslab-panel);
}
.tutor-input-row:focus-within {
  border-color: var(--oslab-primary);
  box-shadow: 0 0 0 1px var(--oslab-primary);
}
.tutor-input {
  flex: 1;
  min-height: 24px;
  max-height: 164px;
  resize: none;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-ui);
  font-size: 14px;
  line-height: 24px;
  padding: 0;
}
.tutor-input::placeholder {
  color: var(--oslab-muted);
}
.tutor-input:disabled {
  color: var(--oslab-muted-strong);
}
.tutor-send {
  flex: none;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
  font-size: 17px;
  line-height: 1;
  cursor: pointer;
}
.tutor-send:hover:not(:disabled) {
  background: var(--oslab-primary-hover);
}
.tutor-send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.tutor-send:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}
.tutor-send-wait {
  font-size: 16px;
  letter-spacing: 1px;
}
.tutor-composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  color: var(--oslab-muted);
  font-size: 10px;
}
.tutor-model {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.tutor-focus {
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
  font-size: 10px;
  font-weight: 600;
}
.tutor-model-menu {
  position: relative;
}
.tutor-model-trigger {
  max-width: 180px;
  min-height: 26px;
  padding: 0 6px;
  border: 0;
  border-radius: var(--oslab-radius-sm);
  background: transparent;
  color: var(--oslab-muted-strong);
  font: inherit;
  font-size: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}
.tutor-model-trigger:hover {
  background: var(--oslab-panel-soft);
  color: var(--oslab-primary);
}
.tutor-model-list {
  right: auto;
  left: 0;
  bottom: calc(100% + 4px);
  top: auto;
}
.tutor-input-hint {
  flex: none;
  white-space: nowrap;
}

/* ---------- 历史抽屉 ---------- */
.tutor-history {
  position: absolute;
  inset: 0;
  z-index: 4;
  display: flex;
  flex-direction: column;
  background: var(--oslab-panel);
}
.tutor-history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px 6px;
}
.tutor-history-head h3 {
  margin: 0;
  font-family: var(--oslab-font-display);
  font-size: 15px;
}
.tutor-history-note {
  margin: 0;
  padding: 0 14px 8px;
  color: var(--oslab-muted);
  font-size: 11px;
}
.tutor-history-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 8px;
}
.tutor-history-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  min-height: 52px;
  padding: 8px 10px;
  border: 0;
  border-bottom: 1px solid var(--oslab-line-soft);
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.tutor-history-item:hover,
.tutor-history-item.active {
  background: var(--oslab-primary-soft);
}
.tutor-history-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.tutor-history-meta {
  color: var(--oslab-muted);
  font-size: 10px;
}
.tutor-history-state {
  padding: 12px;
  color: var(--oslab-muted-strong);
  font-size: 12px;
}
.tutor-history-state.is-error {
  color: var(--oslab-weak-text);
}
.tutor-history-actions {
  flex: none;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid var(--oslab-line);
}
.tutor-history-new {
  min-height: 34px;
  padding: 0 12px;
  border: 0;
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.tutor-history .tutor-mini-btn {
  margin-left: 0;
  border: 1px solid var(--oslab-line);
}

/* ---------- 三态 ---------- */
.mode-dock,
.mode-float {
  height: 100%;
}
.mode-expanded {
  max-width: 1100px;
  margin: 0 auto;
  background: var(--oslab-panel);
}
.mode-expanded .tutor-messages {
  padding: 18px min(6vw, 64px) 24px;
}
.mode-expanded .tutor-answer {
  font-size: 16px;
  line-height: 1.75;
}
.mode-expanded .tutor-answer.tutor-md :deep(.markdown-body) {
  font-size: 16px;
  line-height: 1.75;
}
.mode-expanded .tutor-msg {
  width: min(100%, 780px);
  margin: 0 auto;
}
.mode-expanded .tutor-msg.is-user {
  margin-right: auto;
}
.mode-full .tutor-messages {
  padding: 12px 14px 16px;
}
.mode-full .tutor-jump {
  bottom: 150px;
}

@media (max-width: 720px) {
  .tutor-head {
    padding: 8px 10px;
  }
  .tutor-head-btn {
    min-height: 40px;
    padding: 0 7px;
  }
  .tutor-messages {
    padding: 12px 12px 16px;
  }
  .tutor-input-hint {
    display: none;
  }
  .tutor-answer {
    font-size: 15px;
  }
}

@media (pointer: coarse) {
  .tutor-head-btn,
  .tutor-mini-btn,
  .tutor-scope button,
  .tutor-suggestion,
  .tutor-send {
    min-height: 44px;
  }
  .tutor-send {
    min-width: 44px;
    height: 44px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .tutor-pending::before {
    animation: none;
  }
  .tutor-answer.tutor-md :deep(.tutor-code-copy) {
    transition: none;
  }
}
</style>
