<template>
  <div class="unit-form">
    <a-empty v-if="!hasUnit" description="选择或新建学习单元后在此编辑" />
    <template v-else>
      <a-form layout="vertical">
        <div class="grid-2">
          <a-form-item label="单元代码（稳定）" required>
            <a-input v-model="unit.unitCode" />
          </a-form-item>
          <a-form-item label="标题" required>
            <a-input v-model="unit.title" />
          </a-form-item>
        </div>
        <div class="grid-3">
          <a-form-item label="课程代码" required>
            <a-input v-model="unit.courseCode" />
          </a-form-item>
          <a-form-item label="所属小节代码" required>
            <a-input v-model="unit.sectionCode" />
          </a-form-item>
          <a-form-item label="排序">
            <a-input-number v-model="unit.sortOrder" :min="0" />
          </a-form-item>
        </div>
        <a-form-item label="预计学习分钟">
          <a-input-number v-model="unit.estimatedMinutes" :min="0" />
        </a-form-item>
        <a-form-item label="学习目标">
          <div
            v-for="(goal, index) in unit.objectives || []"
            :key="index"
            class="row"
          >
            <a-input v-model="unit.objectives[index]" />
            <a-button size="small" @click="unit.objectives.splice(index, 1)"
              >删除</a-button
            >
          </div>
          <a-button size="small" @click="addObjective">添加目标</a-button>
        </a-form-item>
        <a-form-item label="教学节点映射">
          <div
            v-for="(mapping, index) in unit.nodeMappings || []"
            :key="index"
            class="row"
          >
            <a-select
              v-model="mapping.nodeCode"
              allow-search
              placeholder="nodeCode"
              style="flex: 1"
            >
              <a-option
                v-for="node in nodes"
                :key="node.nodeCode"
                :value="node.nodeCode"
              >
                {{ node.name }}（{{ node.nodeCode }}）
              </a-option>
            </a-select>
            <a-select v-model="mapping.role" style="width: 130px">
              <a-option value="TEACHES">教学</a-option>
              <a-option value="PREREQUISITE">先修</a-option>
            </a-select>
            <a-button size="small" @click="unit.nodeMappings.splice(index, 1)"
              >删除</a-button
            >
          </div>
          <a-button size="small" @click="addNodeMapping">添加节点</a-button>
        </a-form-item>

        <a-form-item label="活动列表">
          <div
            v-for="(activity, index) in unit.activities || []"
            :key="index"
            class="activity-card"
          >
            <div class="grid-2">
              <a-input v-model="activity.title" placeholder="活动标题" />
              <a-select v-model="activity.activityType" placeholder="活动类型">
                <a-option value="READING">阅读</a-option>
                <a-option value="WORKED_EXAMPLE">示例</a-option>
                <a-option value="PRACTICE">练习</a-option>
                <a-option value="ASSESSMENT">测评</a-option>
                <a-option value="EXPERIMENT">实验</a-option>
                <a-option value="SIMULATION">模拟</a-option>
              </a-select>
            </div>
            <div class="grid-2">
              <a-select
                v-model="activity.reference.kind"
                placeholder="引用类型"
              >
                <a-option value="CHUNK">教学块</a-option>
                <a-option value="QUESTION">题目</a-option>
                <a-option value="RESOURCE">资源</a-option>
              </a-select>
              <a-input
                v-model="activity.reference.code"
                placeholder="稳定代码"
              />
            </div>
            <div class="grid-3">
              <a-select v-model="activity.completionRule">
                <a-option value="CONFIRM">确认完成</a-option>
                <a-option value="SUBMIT">提交</a-option>
                <a-option value="PASS">通过</a-option>
              </a-select>
              <a-space>
                <a-switch v-model="activity.required" size="small" />
                <span>必做</span>
              </a-space>
              <a-input-number
                v-model="activity.sortOrder"
                :min="0"
                placeholder="排序"
              />
            </div>
            <div class="row">
              <a-input
                v-model="activity.activityCode"
                placeholder="活动稳定代码"
              />
              <a-button
                size="small"
                status="danger"
                @click="unit.activities.splice(index, 1)"
              >
                删除活动
              </a-button>
            </div>
          </div>
          <a-button size="small" @click="addActivity">添加活动</a-button>
        </a-form-item>

        <a-collapse>
          <a-collapse-item header="高级 JSON（完整单元定义）" key="advanced">
            <a-textarea
              :model-value="modelValue"
              :auto-size="{ minRows: 10 }"
              readonly
            />
            <p class="hint">
              表单编辑会重写单元草稿；如需精确维护未暴露字段，请在下方原始 JSON
              区直接编辑并保存。
            </p>
          </a-collapse-item>
        </a-collapse>
      </a-form>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, defineEmits, defineProps, ref, watch } from "vue";

const props = defineProps<{ modelValue?: string; nodes: any[] }>();
const emit = defineEmits<{
  (event: "update:modelValue", value: string): void;
}>();
const unit = ref<any>({});

const hasUnit = computed(() => Boolean(unit.value && unit.value.unitCode));

const parse = (value?: string) => {
  try {
    const parsed = JSON.parse(value || "{}");
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
};

watch(
  () => props.modelValue,
  (value) => {
    const parsed = parse(value);
    if (JSON.stringify(parsed) !== JSON.stringify(unit.value)) {
      unit.value = parsed;
      unit.value.objectives = unit.value.objectives || [];
      unit.value.nodeMappings = unit.value.nodeMappings || [];
      unit.value.activities = unit.value.activities
        ? unit.value.activities.map((activity: any) => ({
            ...activity,
            reference: activity.reference || { kind: "CHUNK", code: "" },
            config: activity.config || {},
          }))
        : [];
    }
  },
  { immediate: true }
);

watch(
  unit,
  () => {
    if (!hasUnit.value) return;
    const serialized = JSON.stringify(unit.value, null, 2);
    if (serialized !== (props.modelValue || ""))
      emit("update:modelValue", serialized);
  },
  { deep: true }
);

const addObjective = () => {
  unit.value.objectives = unit.value.objectives || [];
  unit.value.objectives.push("");
};
const addNodeMapping = () => {
  unit.value.nodeMappings = unit.value.nodeMappings || [];
  unit.value.nodeMappings.push({ nodeCode: "", role: "TEACHES" });
};
const addActivity = () => {
  unit.value.activities = unit.value.activities || [];
  unit.value.activities.push({
    activityCode: `act.${Date.now().toString(36)}`,
    title: "",
    activityType: "READING",
    sortOrder: (unit.value.activities.length + 1) * 10,
    required: true,
    reference: { kind: "CHUNK", code: "" },
    completionRule: "CONFIRM",
    config: {},
  });
};
</script>

<style scoped>
.unit-form {
  margin-top: 12px;
}
.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.activity-card {
  border: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.11));
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 10px;
}
.hint {
  color: var(--oslab-muted-strong, #757069);
  font-size: 12px;
}
@media (max-width: 900px) {
  .grid-2,
  .grid-3 {
    grid-template-columns: 1fr;
  }
}
</style>
