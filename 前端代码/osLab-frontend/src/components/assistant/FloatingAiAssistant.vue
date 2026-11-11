<template>
  <Teleport to="body">
    <template v-if="visible">
      <!-- 全局唯一入口按钮：76px 圆形头像；点击开关、按住可上下拖放（保留用户改动） -->
      <button
        ref="fabRef"
        type="button"
        class="ai-fab"
        :class="{ 'ai-fab-dragging': dragging }"
        :style="{ left: btnPos.x + 'px', top: btnPos.y + 'px' }"
        aria-label="AI 助手"
        :aria-expanded="open"
        aria-controls="ai-assistant-panel"
        title="AI 助手（点击开关，拖动可上下放置）"
        @pointerdown="startDrag($event)"
        @keydown.enter.prevent="togglePanel"
        @keydown.space.prevent="togglePanel"
      >
        <picture class="ai-fab-avatar">
          <source
            media="(prefers-reduced-motion: reduce)"
            :srcset="aiAvatarStaticUrl"
          />
          <source type="image/webp" :srcset="aiAvatarWebpUrl" />
          <img :src="aiAvatarUrl" alt="" draggable="false" aria-hidden="true" />
        </picture>
      </button>

      <div
        v-if="open && mode !== 'dock'"
        class="ai-scrim"
        :class="{ 'is-expanded': mode === 'expanded' }"
        aria-hidden="true"
        @click="onScrimClick"
      ></div>

      <div
        v-if="open"
        id="ai-assistant-panel"
        ref="panelWrapRef"
        class="ai-tutor-wrap"
        :class="`is-${mode}`"
        :style="wrapStyle"
      >
        <button
          v-if="mode === 'dock' || mode === 'float'"
          type="button"
          class="ai-resize"
          aria-label="调整导师面板宽度（400 到 560 像素）"
          @pointerdown="startResize"
        ></button>
        <TutorPanel
          :mode="panelMode"
          @close="closePanel"
          @expand="expandPanel"
          @collapse="collapsePanel"
        />
      </div>
      <span ref="safeAreaRef" class="ai-safe-probe" aria-hidden="true"></span>
    </template>
  </Teleport>
</template>

<script setup lang="ts">
/**
 * 全局唯一 AI 导师宿主（App.vue 挂载，Teleport 到 body）。
 *
 * - 保留用户未提交的头像入口与左右侧边吸附、垂直位置记忆；
 * - 面板三态：侧边停靠（≥1180px）/ 覆盖浮层（720–1179px）/ 窄屏全屏；
 * - 展开为宽阅读模态（复用同一 TutorPanel 实例、草稿、滚动位置与进行中请求）；
 * - 关闭只收起界面，不取消任务；再次打开显示真实持久状态；
 * - 登录/注册/无权限页隐藏；登录用户变化时关闭并重载位置。
 */
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";
import { useRoute } from "vue-router";
import { useStore } from "vuex";
import ACCESS_ENUM from "@/access/accessEnum";
import TutorPanel from "./TutorPanel.vue";
import { pendingTutorLaunch } from "@/composables/useTutorLaunch";
import { askAiAssistant } from "@/composables/useAiLauncher";

type PanelMode = "dock" | "float" | "full" | "expanded";

const FAB_SIZE = 76;
const FAB_MARGIN = 20;
const VIEWPORT_MARGIN = 12;
const DRAG_THRESHOLD = 4;
const MIN_TUTOR_WIDTH = 400;
const MAX_TUTOR_WIDTH = 560;
const DEFAULT_TUTOR_WIDTH = 460;
const POSITION_PREFIX = "oslab-ai-assistant-position-";
const WIDTH_PREFIX = "oslab-ai-tutor-width-";
const aiAvatarUrl = `${process.env.BASE_URL}ai-assistant.gif`;
const aiAvatarWebpUrl = `${process.env.BASE_URL}ai-assistant.webp`;
const aiAvatarStaticUrl = `${process.env.BASE_URL}ai-assistant-static.png`;

interface Position {
  x: number;
  y: number;
}

interface DragState {
  pointerId: number;
  startClientX: number;
  startClientY: number;
  originX: number;
  originY: number;
  moved: boolean;
}

const store = useStore();
const route = useRoute();

const loginUser = computed<any>(() => store.state?.user?.loginUser ?? {});
const isLoggedIn = computed(
  () =>
    !!loginUser.value?.id && loginUser.value?.userRole !== ACCESS_ENUM.NOT_LOGIN
);
const hiddenByRoute = computed(
  () =>
    route.path === "/user/login" ||
    route.path === "/user/register" ||
    route.path === "/noAuth"
);
const visible = computed(() => isLoggedIn.value && !hiddenByRoute.value);

const open = ref(false);
const mode = ref<PanelMode>("dock");
const baseMode = ref<Exclude<PanelMode, "expanded">>("dock");
const tutorWidth = ref(DEFAULT_TUTOR_WIDTH);
const fabRef = ref<HTMLElement | null>(null);
const panelWrapRef = ref<HTMLElement | null>(null);
const safeAreaRef = ref<HTMLElement | null>(null);
const btnPos = ref<Position>({ x: 0, y: 0 });
const dragging = ref(false);
let drag: DragState | null = null;
let resize: {
  pointerId: number;
  startX: number;
  startWidth: number;
} | null = null;
let previousFocus: HTMLElement | null = null;

const panelMode = computed(() => {
  if (mode.value === "expanded") return "expanded" as const;
  if (mode.value === "full") return "full" as const;
  return mode.value === "dock" ? ("dock" as const) : ("float" as const);
});
const wrapStyle = computed(() => {
  if (mode.value === "dock" || mode.value === "float") {
    return { width: `${tutorWidth.value}px` };
  }
  return undefined;
});

function clampNum(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), Math.max(min, max));
}

function clampPos(pos: Position, width: number, height: number): Position {
  return {
    x: clampNum(
      pos.x,
      VIEWPORT_MARGIN,
      window.innerWidth - width - VIEWPORT_MARGIN
    ),
    y: clampNum(
      pos.y,
      VIEWPORT_MARGIN,
      window.innerHeight - height - VIEWPORT_MARGIN
    ),
  };
}

function safeAreaInsets() {
  const style = safeAreaRef.value
    ? window.getComputedStyle(safeAreaRef.value)
    : null;
  return {
    left: parseFloat(style?.paddingLeft || "0") || 0,
    right: parseFloat(style?.paddingRight || "0") || 0,
    bottom: parseFloat(style?.paddingBottom || "0") || 0,
  };
}

function defaultFabPos(): Position {
  const insets = safeAreaInsets();
  return {
    x: window.innerWidth - FAB_SIZE - FAB_MARGIN - insets.right,
    y: window.innerHeight - FAB_SIZE - FAB_MARGIN - insets.bottom,
  };
}

// 浮动助手停靠左右侧边，垂直位置由用户拖放并保留（保留用户未提交改动）
function snapFabToSide() {
  const vw = window.innerWidth;
  const insets = safeAreaInsets();
  const leftSide = btnPos.value.x + FAB_SIZE / 2 < vw / 2;
  btnPos.value = clampPos(
    {
      x: leftSide
        ? FAB_MARGIN + insets.left
        : vw - FAB_SIZE - FAB_MARGIN - insets.right,
      y: btnPos.value.y,
    },
    FAB_SIZE,
    FAB_SIZE
  );
}

function positionKey(): string {
  const id = loginUser.value?.id;
  return id !== undefined && id !== null ? `${POSITION_PREFIX}${id}` : "";
}

function widthKey(): string {
  const id = loginUser.value?.id;
  return id !== undefined && id !== null ? `${WIDTH_PREFIX}${id}` : "";
}

function loadPosition() {
  btnPos.value = defaultFabPos();
  const key = positionKey();
  if (key) {
    try {
      const raw = localStorage.getItem(key);
      if (raw) {
        const saved = JSON.parse(raw);
        if (
          saved &&
          typeof saved.bx === "number" &&
          typeof saved.by === "number"
        ) {
          btnPos.value = { x: saved.bx, y: saved.by };
        }
      }
    } catch (e) {
      console.warn("[AI 助手] 位置数据读取失败，已重置", e);
    }
  }
  btnPos.value = clampPos(btnPos.value, FAB_SIZE, FAB_SIZE);
  snapFabToSide();
}

function loadWidth() {
  tutorWidth.value = DEFAULT_TUTOR_WIDTH;
  const key = widthKey();
  if (!key) return;
  try {
    const raw = localStorage.getItem(key);
    const width = raw ? Number(raw) : NaN;
    if (Number.isFinite(width)) {
      tutorWidth.value = clampNum(width, MIN_TUTOR_WIDTH, MAX_TUTOR_WIDTH);
    }
  } catch {
    // ignore
  }
}

function persistPosition() {
  const key = positionKey();
  if (key) {
    try {
      localStorage.setItem(
        key,
        JSON.stringify({ bx: btnPos.value.x, by: btnPos.value.y })
      );
    } catch (e) {
      console.warn("[AI 助手] 位置保存失败", e);
    }
  }
  const widthK = widthKey();
  if (widthK) {
    try {
      localStorage.setItem(widthK, String(tutorWidth.value));
    } catch {
      // ignore
    }
  }
}

function startDrag(e: PointerEvent) {
  if (e.button !== 0) return;
  const el = fabRef.value ?? (e.currentTarget as HTMLElement | null);
  if (!el) return;
  drag = {
    pointerId: e.pointerId,
    startClientX: e.clientX,
    startClientY: e.clientY,
    originX: btnPos.value.x,
    originY: btnPos.value.y,
    moved: false,
  };
  el.setPointerCapture(e.pointerId);
  el.addEventListener("pointermove", onDragMove);
  el.addEventListener("pointerup", onDragEnd);
  el.addEventListener("pointercancel", onDragEnd);
}

function onDragMove(e: PointerEvent) {
  if (!drag || e.pointerId !== drag.pointerId) return;
  const dx = e.clientX - drag.startClientX;
  const dy = e.clientY - drag.startClientY;
  if (!drag.moved) {
    if (Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
    drag.moved = true;
    dragging.value = true;
  }
  btnPos.value = clampPos(
    { x: drag.originX + dx, y: drag.originY + dy },
    FAB_SIZE,
    FAB_SIZE
  );
}

function onDragEnd(e: PointerEvent) {
  const el = e.currentTarget as HTMLElement | null;
  if (el) {
    el.removeEventListener("pointermove", onDragMove);
    el.removeEventListener("pointerup", onDragEnd);
    el.removeEventListener("pointercancel", onDragEnd);
    if (drag && el.hasPointerCapture?.(drag.pointerId)) {
      el.releasePointerCapture(drag.pointerId);
    }
  }
  const state = drag;
  drag = null;
  dragging.value = false;
  if (!state) return;
  if (!state.moved) {
    togglePanel();
  } else {
    snapFabToSide();
    persistPosition();
  }
}

function startResize(e: PointerEvent) {
  if (e.button !== 0) return;
  resize = {
    pointerId: e.pointerId,
    startX: e.clientX,
    startWidth: tutorWidth.value,
  };
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  window.addEventListener("pointermove", onResizeMove);
  window.addEventListener("pointerup", onResizeEnd);
  window.addEventListener("pointercancel", onResizeEnd);
}

function onResizeMove(e: PointerEvent) {
  if (!resize || e.pointerId !== resize.pointerId) return;
  tutorWidth.value = clampNum(
    resize.startWidth - (e.clientX - resize.startX),
    MIN_TUTOR_WIDTH,
    MAX_TUTOR_WIDTH
  );
}

function onResizeEnd() {
  if (!resize) return;
  resize = null;
  window.removeEventListener("pointermove", onResizeMove);
  window.removeEventListener("pointerup", onResizeEnd);
  window.removeEventListener("pointercancel", onResizeEnd);
  persistPosition();
}

function baseModeForViewport(): Exclude<PanelMode, "expanded"> {
  if (window.innerWidth >= 1180) return "dock";
  if (window.innerWidth >= 720) return "float";
  return "full";
}

function applyDockState() {
  const docked = visible.value && open.value && mode.value === "dock";
  if (docked) {
    document.body.setAttribute("data-tutor-dock", "on");
    document.documentElement.style.setProperty(
      "--tutor-dock-width",
      `${tutorWidth.value}px`
    );
  } else {
    document.body.removeAttribute("data-tutor-dock");
    document.documentElement.style.removeProperty("--tutor-dock-width");
  }
}

function togglePanel() {
  if (open.value) closePanel();
  else openPanel();
}

function openPanel() {
  if (open.value) return;
  baseMode.value = baseModeForViewport();
  mode.value = baseMode.value;
  open.value = true;
  nextTick(applyDockState);
}

function closePanel() {
  const shouldRestoreFocus = panelWrapRef.value?.contains(
    document.activeElement
  );
  open.value = false;
  mode.value = baseMode.value;
  applyDockState();
  if (shouldRestoreFocus) nextTick(() => fabRef.value?.focus());
}

function expandPanel() {
  if (mode.value === "expanded") return;
  if (window.innerWidth < 900) {
    mode.value = "full";
    return;
  }
  previousFocus = document.activeElement as HTMLElement | null;
  baseMode.value = baseModeForViewport();
  mode.value = "expanded";
  applyDockState();
  nextTick(() => {
    const first =
      panelWrapRef.value?.querySelector<HTMLElement>("textarea, button");
    first?.focus();
  });
}

function collapsePanel() {
  if (mode.value !== "expanded") return;
  mode.value = baseModeForViewport();
  applyDockState();
  nextTick(() => {
    if (previousFocus && document.contains(previousFocus)) {
      previousFocus.focus();
    } else {
      fabRef.value?.focus();
    }
    previousFocus = null;
  });
}

function onScrimClick() {
  if (mode.value === "expanded") collapsePanel();
  else closePanel();
}

function focusables(root: HTMLElement): HTMLElement[] {
  return Array.from(
    root.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], textarea, input, select, [tabindex]:not([tabindex="-1"])'
    )
  ).filter((el) => el.offsetParent !== null);
}

function onGlobalKeydown(e: KeyboardEvent) {
  if (!open.value) return;
  if (e.key === "Escape") {
    e.preventDefault();
    if (mode.value === "expanded") collapsePanel();
    else closePanel();
    return;
  }
  if (e.key !== "Tab" || mode.value !== "expanded") return;
  const root = panelWrapRef.value;
  if (!root) return;
  const items = focusables(root);
  if (!items.length) return;
  const first = items[0];
  const last = items[items.length - 1];
  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault();
    last.focus();
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault();
    first.focus();
  }
}

function onTutorOpenRequest() {
  openPanel();
}

/** 旧事件 oslab:ai-ask 的 detail.text 语义：预填并立即发送，不维护第二套会话。 */
function onLegacyAiAsk(event: Event) {
  const detail = (event as CustomEvent<{ text?: string }>).detail;
  const text = typeof detail?.text === "string" ? detail.text.trim() : "";
  if (text) {
    askAiAssistant(text);
    return;
  }
  openPanel();
}

function handleViewportChange() {
  btnPos.value = clampPos(btnPos.value, FAB_SIZE, FAB_SIZE);
  snapFabToSide();
  if (!open.value) return;
  if (mode.value === "expanded") {
    if (window.innerWidth < 900) {
      collapsePanel();
      mode.value = "full";
      applyDockState();
    }
    return;
  }
  const next = baseModeForViewport();
  if (next !== mode.value) {
    mode.value = next;
    applyDockState();
  }
}

watch([open, mode, visible, tutorWidth], applyDockState);

watch(visible, (value) => {
  if (!value) closePanel();
});

watch(pendingTutorLaunch, (value) => {
  if (value) openPanel();
});

// 登录用户变化（含退出登录）：关闭面板并重载位置与宽度
watch(
  () => loginUser.value?.id,
  async () => {
    closePanel();
    await nextTick();
    loadPosition();
    loadWidth();
  }
);

onMounted(() => {
  loadPosition();
  loadWidth();
  window.addEventListener("resize", handleViewportChange);
  window.visualViewport?.addEventListener("resize", handleViewportChange);
  window.addEventListener("keydown", onGlobalKeydown);
  window.addEventListener("oslab:tutor-open", onTutorOpenRequest);
  window.addEventListener("oslab:ai-open", onTutorOpenRequest);
  window.addEventListener("oslab:ai-ask", onLegacyAiAsk);
  applyDockState();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleViewportChange);
  window.visualViewport?.removeEventListener("resize", handleViewportChange);
  window.removeEventListener("keydown", onGlobalKeydown);
  window.removeEventListener("oslab:tutor-open", onTutorOpenRequest);
  window.removeEventListener("oslab:ai-open", onTutorOpenRequest);
  window.removeEventListener("oslab:ai-ask", onLegacyAiAsk);
  document.body.removeAttribute("data-tutor-dock");
  document.documentElement.style.removeProperty("--tutor-dock-width");
});
</script>

<style scoped>
/* 入口按钮：76px 圆形头像，点击开关、按住可上下拖放（用户改动保留） */
.ai-fab {
  position: fixed;
  z-index: 2999;
  width: 76px;
  height: 76px;
  box-sizing: border-box;
  padding: 0;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.92);
  border-radius: var(--oslab-radius-pill);
  background: #ffffff;
  cursor: grab;
  touch-action: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  opacity: 1;
  box-shadow: 0 8px 22px rgba(20, 40, 25, 0.22);
  transition: box-shadow var(--oslab-duration-fast) var(--oslab-ease),
    transform var(--oslab-duration-fast) var(--oslab-ease);
}
.ai-fab:hover,
.ai-fab:focus-visible {
  box-shadow: 0 10px 26px rgba(20, 40, 25, 0.3);
}
.ai-fab:hover {
  transform: translateY(-2px);
}
.ai-fab:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 3px;
}
.ai-fab-dragging,
.ai-fab-dragging:hover {
  cursor: grabbing;
  transform: none;
  transition: none;
}
.ai-fab-avatar,
.ai-fab-avatar img {
  display: block;
  width: 100%;
  height: 100%;
}
.ai-fab-avatar img {
  object-fit: cover;
  border-radius: 50%;
  -webkit-user-drag: none;
}

.ai-scrim {
  position: fixed;
  inset: 0;
  z-index: 2998;
  background: rgba(16, 26, 20, 0.28);
}
.ai-scrim.is-expanded {
  z-index: 3099;
  background: rgba(10, 18, 14, 0.42);
}

.ai-tutor-wrap {
  position: fixed;
  z-index: 3000;
  display: flex;
  overflow: hidden;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
}
.ai-tutor-wrap.is-dock {
  /* 从全局头部下沿开始，头部导航与搜索保持完整可用 */
  top: 84px;
  right: 0;
  bottom: 0;
  width: var(--tutor-dock-width, 460px);
  max-width: calc(100vw - 16px);
  border-width: 1px 0 0 1px;
  box-shadow: -8px 0 28px rgba(16, 30, 22, 0.14);
}
.ai-tutor-wrap.is-float {
  top: 16px;
  right: 16px;
  bottom: 16px;
  max-width: calc(100vw - 32px);
  border-radius: var(--oslab-radius-lg);
  box-shadow: var(--oslab-shadow-overlay);
}
.ai-tutor-wrap.is-expanded {
  inset: 0;
  z-index: 3100;
  align-items: stretch;
  justify-content: center;
  padding: 32px;
  background: transparent;
  border: 0;
  pointer-events: none;
}
.ai-tutor-wrap.is-expanded > :deep(.tutor-panel) {
  pointer-events: auto;
  width: min(1100px, 100%);
  height: 100%;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  box-shadow: var(--oslab-shadow-overlay);
  overflow: hidden;
}
.ai-tutor-wrap.is-full {
  inset: 0;
  z-index: 3100;
  border: 0;
}
.ai-tutor-wrap.is-full > :deep(.tutor-panel) {
  height: 100%;
}
.ai-resize {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 7px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: col-resize;
  z-index: 2;
  touch-action: none;
}
.ai-resize:hover,
.ai-resize:focus-visible {
  background: var(--oslab-primary-soft);
  outline: none;
}

.ai-safe-probe {
  position: fixed;
  left: 0;
  bottom: 0;
  width: 0;
  height: 0;
  padding: env(safe-area-inset-top) env(safe-area-inset-right)
    env(safe-area-inset-bottom) env(safe-area-inset-left);
  visibility: hidden;
  pointer-events: none;
}

@media (max-width: 900px) {
  .ai-tutor-wrap.is-expanded {
    padding: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-fab {
    transition: none;
  }
  .ai-fab:hover {
    transform: none;
  }
}
</style>
