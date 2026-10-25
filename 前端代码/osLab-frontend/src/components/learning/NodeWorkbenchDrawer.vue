<template>
  <a-drawer
    :visible="visible"
    :width="drawerWidth"
    :footer="false"
    unmount-on-close
    @cancel="close"
    @close="close"
  >
    <template #title>
      节点快速预览
      <span
        v-if="workbench?.mastery"
        class="oslab-status-tag drawer-status"
        :class="statusTagClass(workbench.mastery.status)"
      >
        {{ statusText(workbench.mastery.status) }}
      </span>
      <span
        v-else
        class="oslab-status-tag oslab-status-tag--unseen drawer-status"
        >未学习</span
      >
    </template>

    <a-spin :loading="loading" style="width: 100%">
      <!-- 概念卡（节点描述） -->
      <div v-if="workbench?.mastery?.description" class="concept-card">
        {{ workbench.mastery.description }}
      </div>

      <!-- 掌握状态 -->
      <a-descriptions
        v-if="workbench?.mastery"
        :column="2"
        bordered
        size="small"
        style="margin-top: 12px"
      >
        <a-descriptions-item label="掌握度">
          {{ percentText(workbench.mastery.pKnown) }}
        </a-descriptions-item>
        <a-descriptions-item label="可信度">
          {{ percentText(workbench.mastery.confidence) }}
        </a-descriptions-item>
      </a-descriptions>
      <template v-if="workbench?.mastery">
        <div class="score-row">
          <span class="score-label">概念理解</span>
          <a-progress
            :percent="workbench.mastery.conceptScore ?? 0"
            size="small"
            style="flex: 1"
          />
          <span class="hint">{{
            workbench.mastery.conceptEvidenceCount
              ? workbench.mastery.conceptEvidenceCount + " 条证据"
              : "未评估"
          }}</span>
        </div>
        <div class="score-row">
          <span class="score-label">实践应用</span>
          <a-progress
            :percent="workbench.mastery.practiceScore ?? 0"
            size="small"
            style="flex: 1"
          />
          <span class="hint">{{
            workbench.mastery.practiceEvidenceCount
              ? workbench.mastery.practiceEvidenceCount + " 条证据"
              : "未评估"
          }}</span>
        </div>
        <div
          v-if="workbench.mastery.recentReason"
          class="hint"
          style="margin-top: 4px"
        >
          最近变化：{{ workbench.mastery.recentReason }}
        </div>
      </template>

      <!-- 快速动作：完整工作台承载练习/实验/导师 -->
      <NodeCourseLinks :node-code="workbench?.mastery?.nodeCode" />
      <a-button
        type="primary"
        long
        class="workspace-cta"
        @click="openWorkspace"
      >
        进入节点学习工作台
      </a-button>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";
import { Message } from "@arco-design/web-vue";
import { type NodeWorkbenchVO } from "@/api/learning";
import NodeCourseLinks from "@/components/learning/NodeCourseLinks.vue";
import {
  loadNodeWorkbench,
  peekNodeWorkbench,
} from "@/composables/useNodeWorkbench";

// eslint-disable-next-line no-undef
const props = defineProps<{
  visible: boolean;
  nodeName: string;
}>();

// eslint-disable-next-line no-undef
const emit = defineEmits(["update:visible", "mastery-changed"]);

const router = useRouter();
const store = useStore();
const userId = computed(() =>
  String(store.state.user?.loginUser?.id || "anonymous")
);
const loading = ref(false);
const workbench = ref<NodeWorkbenchVO | null>(null);
const viewportWidth = ref(window.innerWidth);
const drawerWidth = computed(() => Math.min(460, viewportWidth.value));
const updateViewport = () => {
  viewportWidth.value = window.innerWidth;
};
window.addEventListener("resize", updateViewport);
let requestSequence = 0;

const load = async () => {
  if (!props.nodeName) return;
  const requestNode = props.nodeName;
  const requestUser = userId.value;
  const sequence = ++requestSequence;
  loading.value = true;
  try {
    const data = await loadNodeWorkbench(requestUser, requestNode);
    if (
      sequence !== requestSequence ||
      requestNode !== props.nodeName ||
      requestUser !== userId.value ||
      !props.visible
    )
      return;
    workbench.value = data;
  } catch (e) {
    if (sequence === requestSequence && props.visible)
      Message.error("加载节点预览失败，请稍后重试");
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
};

watch(
  () => [props.visible, props.nodeName, userId.value],
  ([visible]) => {
    if (visible) {
      workbench.value = peekNodeWorkbench(userId.value, props.nodeName);
      load();
    } else {
      requestSequence += 1;
      loading.value = false;
    }
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  requestSequence += 1;
  window.removeEventListener("resize", updateViewport);
});

const close = () => emit("update:visible", false);

const openWorkspace = () => {
  if (!props.nodeName) return;
  close();
  router.push({
    path: `/knowledge/node/${encodeURIComponent(props.nodeName)}`,
  });
};

// 格式化工具
const percentText = (value: number | null | undefined) =>
  value === null || value === undefined ? "-" : `${Math.round(value * 100)}%`;

const statusText = (status: string) => {
  const map: Record<string, string> = {
    GRAY: "未学习",
    RED: "薄弱",
    YELLOW: "待巩固",
    GREEN: "已掌握",
  };
  return map[status] || status;
};

const STATUS_TAG_CLASS: Record<string, string> = {
  GREEN: "oslab-status-tag--mastered",
  YELLOW: "oslab-status-tag--review",
  RED: "oslab-status-tag--weak",
  GRAY: "oslab-status-tag--unseen",
};

const statusTagClass = (status: string) =>
  STATUS_TAG_CLASS[status] || "oslab-status-tag--unseen";
</script>

<style scoped>
.hint {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.drawer-status {
  margin-left: 8px;
  vertical-align: middle;
}
.concept-card {
  background: var(--oslab-panel-soft);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  padding: 12px;
  font-size: var(--oslab-font-body);
  line-height: 1.7;
  color: var(--oslab-ink-soft);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.workspace-cta {
  margin-top: 16px;
}
.score-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.score-label {
  width: 60px;
  font-size: var(--oslab-font-body);
  color: var(--oslab-ink-soft);
  flex-shrink: 0;
}
</style>
