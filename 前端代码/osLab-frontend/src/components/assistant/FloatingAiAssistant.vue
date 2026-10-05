<template>
  <Teleport to="body">
    <template v-if="visible">
      <!-- 全局唯一入口按钮：76px 圆形；点击开关、按住可拖动 -->
      <button
        ref="fabRef"
        type="button"
        class="ai-fab"
        :class="{ 'ai-fab-dragging': dragging }"
        :style="{ left: btnPos.x + 'px', top: btnPos.y + 'px' }"
        aria-label="AI 助手"
        title="AI 助手（点击开关，可拖动）"
        @pointerdown="startDrag($event, 'fab')"
        @keydown.enter.prevent="open = !open"
      >
        AI
      </button>
      <!-- 面板：Teleport 到 body，约 400×520，max 约束在视口内 -->
      <div
        v-if="open"
        ref="panelWrapRef"
        class="ai-float-panel"
        :style="{ left: panelPos.x + 'px', top: panelPos.y + 'px' }"
      >
        <AiChatPanel
          @close="open = false"
          @header-drag="startDrag($event, 'panel')"
        />
      </div>
    </template>
  </Teleport>
</template>

<script setup lang="ts">
/**
 * 全局可拖动 AI 助手（log3 阶段 4）：
 * - App.vue 挂载的 singleton，Teleport 到 body；各路由页不重复挂载（§8.3-1）；
 * - 只有按钮与面板头部可拖动（Pointer Events + setPointerCapture，
 *   位移阈值区分点击与拖动，拖动不触发开/关）（§8.3-3）；
 * - 拖动后与 resize/缩放/窄屏变化时自动 clamp 回视口内（§8.3-4）；
 * - 位置按用户 id 写 localStorage，登录用户变化时重新载入（§8.3-6）；
 * - 登录/注册页隐藏，退出登录隐藏（§8.3-10）；
 * - 命名“AI 助手”，与节点/实验 Tutor 无关（§8.3-11）。
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
import AiChatPanel from "./AiChatPanel.vue";

const FAB_SIZE = 76;
const PANEL_WIDTH = 400;
const PANEL_HEIGHT = 520;
const VIEWPORT_MARGIN = 8;
const DRAG_THRESHOLD = 4;
const POSITION_PREFIX = "oslab-ai-assistant-position-";

interface Position {
  x: number;
  y: number;
}

interface DragState {
  kind: "fab" | "panel";
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
  () => route.path === "/user/login" || route.path === "/user/register"
);
const visible = computed(() => isLoggedIn.value && !hiddenByRoute.value);

const open = ref(false);
const fabRef = ref<HTMLElement | null>(null);
const panelWrapRef = ref<HTMLElement | null>(null);
const btnPos = ref<Position>({ x: 0, y: 0 });
const panelPos = ref<Position>({ x: 0, y: 0 });
const panelPlaced = ref(false);
const dragging = ref(false);
let drag: DragState | null = null;

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

function panelSize(): Position {
  const el = panelWrapRef.value;
  return {
    x:
      el?.offsetWidth ||
      Math.min(PANEL_WIDTH, window.innerWidth - VIEWPORT_MARGIN * 2),
    y:
      el?.offsetHeight ||
      Math.min(PANEL_HEIGHT, window.innerHeight - VIEWPORT_MARGIN * 2),
  };
}

/** 把按钮与面板（若已挂载）都收回可见范围（§8.3-4） */
function clampAll() {
  btnPos.value = clampPos(btnPos.value, FAB_SIZE, FAB_SIZE);
  if (panelWrapRef.value) {
    const size = panelSize();
    panelPos.value = clampPos(panelPos.value, size.x, size.y);
  }
}

/** 面板未被手动放置过时，默认贴按钮弹出（优先左侧，放不下换右侧） */
function placePanelNearFab() {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const w = Math.min(PANEL_WIDTH, vw - VIEWPORT_MARGIN * 2);
  const h = Math.min(PANEL_HEIGHT, vh - VIEWPORT_MARGIN * 2);
  let x = btnPos.value.x - w - 12;
  if (x < VIEWPORT_MARGIN) {
    x = btnPos.value.x + FAB_SIZE + 12;
  }
  x = clampNum(x, VIEWPORT_MARGIN, vw - w - VIEWPORT_MARGIN);
  const y = clampNum(
    btnPos.value.y + FAB_SIZE / 2 - h / 2,
    VIEWPORT_MARGIN,
    vh - h - VIEWPORT_MARGIN
  );
  panelPos.value = { x, y };
}

function defaultFabPos(): Position {
  return {
    x: window.innerWidth - FAB_SIZE - 24,
    y: window.innerHeight - FAB_SIZE - 24,
  };
}

function positionKey(): string {
  const id = loginUser.value?.id;
  return id !== undefined && id !== null ? `${POSITION_PREFIX}${id}` : "";
}

function loadPosition() {
  btnPos.value = defaultFabPos();
  panelPlaced.value = false;
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
          if (typeof saved.px === "number" && typeof saved.py === "number") {
            panelPos.value = { x: saved.px, y: saved.py };
            panelPlaced.value = true;
          }
        }
      }
    } catch (e) {
      // 忽略损坏的本地数据，使用默认位置
      console.warn("[AI 助手] 位置数据读取失败，已重置", e);
    }
  }
  clampAll();
}

function persistPosition() {
  const key = positionKey();
  if (!key) {
    return;
  }
  try {
    localStorage.setItem(
      key,
      JSON.stringify({
        bx: btnPos.value.x,
        by: btnPos.value.y,
        px: panelPos.value.x,
        py: panelPos.value.y,
      })
    );
  } catch (e) {
    console.warn("[AI 助手] 位置保存失败", e);
  }
}

function startDrag(e: PointerEvent, kind: "fab" | "panel") {
  if (e.button !== 0) {
    return;
  }
  const el =
    kind === "fab"
      ? fabRef.value ?? (e.currentTarget as HTMLElement | null)
      : panelWrapRef.value ?? (e.currentTarget as HTMLElement | null);
  if (!el) {
    return;
  }
  const pos = kind === "fab" ? btnPos.value : panelPos.value;
  drag = {
    kind,
    pointerId: e.pointerId,
    startClientX: e.clientX,
    startClientY: e.clientY,
    originX: pos.x,
    originY: pos.y,
    moved: false,
  };
  el.setPointerCapture(e.pointerId);
  el.addEventListener("pointermove", onDragMove);
  el.addEventListener("pointerup", onDragEnd);
  el.addEventListener("pointercancel", onDragEnd);
}

function onDragMove(e: PointerEvent) {
  if (!drag || e.pointerId !== drag.pointerId) {
    return;
  }
  const dx = e.clientX - drag.startClientX;
  const dy = e.clientY - drag.startClientY;
  if (!drag.moved) {
    // 位移阈值：未超过阈值不视为拖动，避免误触发
    if (Math.hypot(dx, dy) < DRAG_THRESHOLD) {
      return;
    }
    drag.moved = true;
    dragging.value = true;
  }
  const size = drag.kind === "fab" ? { x: FAB_SIZE, y: FAB_SIZE } : panelSize();
  const next = clampPos(
    { x: drag.originX + dx, y: drag.originY + dy },
    size.x,
    size.y
  );
  if (drag.kind === "fab") {
    btnPos.value = next;
  } else {
    panelPos.value = next;
  }
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
  if (!state) {
    return;
  }
  // 点击与拖动区分：按钮未拖动 → 开/关面板（§8.3-3）
  if (state.kind === "fab" && !state.moved) {
    open.value = !open.value;
  }
  if (state.moved) {
    if (state.kind === "panel") {
      panelPlaced.value = true;
    }
    persistPosition();
  }
}

function onGlobalKeydown(e: KeyboardEvent) {
  if (e.key === "Escape" && open.value) {
    e.preventDefault();
    open.value = false; // Escape 关闭（§8.3-5）
  }
}

function handleViewportChange() {
  // resize / 浏览器缩放 / 窄屏切换：自动回收可见范围（§8.3-4）
  clampAll();
}

watch(open, (value) => {
  if (value) {
    if (!panelPlaced.value) {
      placePanelNearFab();
    }
    nextTick(clampAll);
    window.addEventListener("keydown", onGlobalKeydown);
  } else {
    window.removeEventListener("keydown", onGlobalKeydown);
  }
});

watch(visible, (value) => {
  if (!value) {
    open.value = false;
  }
});

// 登录用户变化（含退出登录）：关闭面板并按新用户重新载入位置
watch(
  () => loginUser.value?.id,
  () => {
    open.value = false;
    loadPosition();
  }
);

onMounted(() => {
  loadPosition();
  window.addEventListener("resize", handleViewportChange);
  window.visualViewport?.addEventListener("resize", handleViewportChange);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleViewportChange);
  window.visualViewport?.removeEventListener("resize", handleViewportChange);
  window.removeEventListener("keydown", onGlobalKeydown);
});
</script>

<style scoped>
/* 入口按钮：44px 圆形，主蓝克制样式 */
.ai-fab {
  position: fixed;
  z-index: 2999;
  width: 76px;
  height: 76px;
  border: none;
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-primary);
  color: #fff;
  font-family: var(--oslab-font-ui);
  font-size: 28px;
  font-weight: 600;
  line-height: 1;
  cursor: grab;
  touch-action: none;
  user-select: none;
  box-shadow: var(--oslab-shadow-overlay);
  transition: background var(--oslab-duration-fast) var(--oslab-ease);
}
.ai-fab:hover {
  background: var(--oslab-primary-hover);
}
.ai-fab-dragging {
  cursor: grabbing;
  transition: none;
}

/* 浮层面板：仅浮层允许阴影（learning-theme 约定） */
.ai-float-panel {
  position: fixed;
  z-index: 3000;
  width: 400px;
  height: 520px;
  max-width: calc(100vw - 16px);
  max-height: calc(100vh - 16px);
  display: flex;
  overflow: hidden;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  box-shadow: var(--oslab-shadow-overlay);
}
</style>
