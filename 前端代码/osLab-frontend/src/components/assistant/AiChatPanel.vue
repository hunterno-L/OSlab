<template>
  <section class="ai-panel" role="dialog" aria-label="AI 助手对话面板">
    <header class="ai-panel-header" @pointerdown="onHeaderPointerDown">
      <div class="ai-panel-titles">
        <span class="ai-panel-title">AI 助手</span>
        <span v-if="pageLabel" class="ai-panel-page"
          >当前页面：{{ pageLabel }}</span
        >
      </div>
      <div class="ai-panel-actions">
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
          @click="emit('close')"
        >
          ✕
        </button>
      </div>
    </header>

    <div ref="listRef" class="ai-panel-messages">
      <div v-if="messages.length === 0 && !loading" class="ai-panel-empty">
        <p>随时提问，我会结合你当前所在的页面作答。</p>
        <p>例如：“帮我解释这个实验的验收要求”“这条判题结果说明什么？”</p>
      </div>
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="ai-msg"
        :class="msg.role === 'user' ? 'ai-msg-user' : 'ai-msg-assistant'"
      >
        <!-- AI 回复走 MdViewer（含 rehype-sanitize 消毒）；渲染异常时回退纯文本 -->
        <div v-if="msg.role === 'assistant' && !mdBroken" class="ai-msg-md">
          <MdViewer :value="msg.content" />
        </div>
        <div v-else class="ai-msg-text">{{ msg.content }}</div>
      </div>
      <div v-if="loading" class="ai-msg ai-msg-assistant">
        <div class="ai-msg-text ai-msg-pending">AI 正在思考，请稍候…</div>
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
        v-model="draft"
        class="ai-panel-input"
        rows="3"
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
        {{ loading ? "发送中…" : "发送" }}
      </button>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onErrorCaptured, onMounted, ref, watch } from "vue";
import MdViewer from "../MdViewer.vue";
import { useAiConversation } from "@/composables/useAiConversation";
import { getAiPageContext } from "@/composables/useAiPageContext";

// eslint-disable-next-line no-undef
const emit = defineEmits<{
  (e: "close"): void;
  (e: "headerDrag", event: PointerEvent): void;
}>();

const { messages, loading, errorMsg, canRetry, send, retry, clear } =
  useAiConversation();

const draft = ref("");
const mdBroken = ref(false);
const listRef = ref<HTMLElement | null>(null);
const pageLabel = ref("");

onMounted(() => {
  // 面板仅在打开时挂载，此时读取的即当前页面注册的上下文名
  pageLabel.value = getAiPageContext()?.pageName ?? "";
  scrollToBottom();
});

// Markdown 渲染异常时整体回退为纯文本展示，保证回复始终可读（§8.3-9）
onErrorCaptured((err) => {
  mdBroken.value = true;
  console.error("[AI 助手] Markdown 渲染失败，已回退为纯文本", err);
  return false;
});

watch([() => messages.value.length, loading], () => {
  nextTick(scrollToBottom);
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
    return; // 发送中禁止重复提交（§8.3-5）
  }
  draft.value = "";
  await send(text);
}

async function onRetry() {
  if (loading.value) {
    return;
  }
  await retry();
}

function onClear() {
  if (messages.value.length === 0) {
    return;
  }
  if (window.confirm("确定清空当前会话吗？")) {
    clear();
  }
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
  padding: var(--oslab-space-2) var(--oslab-space-3);
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
  gap: 2px;
  min-width: 0;
}
.ai-panel-title {
  font-weight: 600;
  font-size: var(--oslab-font-body);
  color: var(--oslab-ink);
}
.ai-panel-page {
  font-size: var(--oslab-text-small);
  color: var(--oslab-muted-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ai-panel-actions {
  display: flex;
  gap: var(--oslab-space-1);
  flex-shrink: 0;
}
.ai-panel-btn {
  border: none;
  background: transparent;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  font-family: var(--oslab-font-ui);
  cursor: pointer;
  padding: var(--oslab-space-1) var(--oslab-space-2);
  border-radius: var(--oslab-radius-control);
  transition: background var(--oslab-duration-fast) var(--oslab-ease),
    color var(--oslab-duration-fast) var(--oslab-ease);
}
.ai-panel-btn:hover:not(:disabled) {
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
}
.ai-panel-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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
  gap: var(--oslab-space-2);
  padding: var(--oslab-space-6);
  text-align: center;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.ai-panel-empty p {
  margin: 0;
}
.ai-msg {
  max-width: 88%;
}
.ai-msg-user {
  align-self: flex-end;
}
.ai-msg-assistant {
  align-self: flex-start;
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

/* 错误条 + 重试 */
.ai-panel-error {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
  padding: var(--oslab-space-2) var(--oslab-space-3);
  background: var(--oslab-weak-soft);
  color: var(--oslab-weak);
  font-size: var(--oslab-text-small);
}
.ai-panel-error-text {
  flex: 1;
  min-width: 0;
}
.ai-panel-retry {
  flex-shrink: 0;
  border: 1px solid currentColor;
  background: transparent;
  color: inherit;
  font-size: var(--oslab-text-small);
  font-family: var(--oslab-font-ui);
  padding: 2px var(--oslab-space-3);
  border-radius: var(--oslab-radius-control);
  cursor: pointer;
}
.ai-panel-retry:hover {
  background: rgba(169, 105, 93, 0.12);
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
  min-height: 56px;
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
  border: none;
  background: var(--oslab-primary);
  color: #fff;
  font-family: var(--oslab-font-ui);
  font-size: var(--oslab-font-body);
  padding: var(--oslab-space-2) var(--oslab-space-3);
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
</style>
