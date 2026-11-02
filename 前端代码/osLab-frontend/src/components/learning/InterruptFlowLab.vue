<template>
  <div class="ifl">
    <div class="ifl-track">
      <div
        v-for="(stage, index) in stages"
        :key="stage.key"
        class="ifl-stage"
        :class="{ on: active === index, done: active > index }"
      >
        <span class="ifl-index">{{ index + 1 }}</span>
        <strong>{{ stage.title }}</strong>
        <p>{{ stage.detail }}</p>
      </div>
    </div>

    <div class="ifl-controls">
      <a-button size="small" type="primary" @click="play">{{ playing ? "暂停" : "播放" }}</a-button>
      <a-button size="small" @click="next">单步</a-button>
      <span class="ifl-hint">{{ active >= 0 ? stages[active].title : "点击播放看中断处理流程" }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";

type Stage = { key: string; title: string; detail: string };

const props = defineProps<{ preset?: { stages?: Stage[] } }>();

const DEFAULT: Stage[] = [
  { key: "req", title: "设备发出中断", detail: "中断控制器把中断请求送到 CPU" },
  { key: "save", title: "保存现场", detail: "硬件保存 PC、PSW，切换内核栈" },
  { key: "dispatch", title: "查中断向量", detail: "按中断号找到处理程序入口" },
  { key: "handle", title: "执行中断处理", detail: "驱动程序读写设备、唤醒等待进程" },
  { key: "restore", title: "恢复现场", detail: "恢复寄存器与 PC，返回被中断的指令" },
];

const stages = computed(() => props.preset?.stages?.length ? props.preset.stages : DEFAULT);
const active = ref(-1);
const playing = ref(false);
let timer: number | undefined;

const next = () => {
  active.value = active.value + 1 >= stages.value.length ? 0 : active.value + 1;
};

const play = () => {
  if (playing.value) {
    playing.value = false;
    if (timer) window.clearInterval(timer);
    return;
  }
  playing.value = true;
  if (active.value < 0) next();
  timer = window.setInterval(next, 1200);
};

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer);
});
</script>

<style scoped>
.ifl { display: flex; flex-direction: column; gap: 14px; }
.ifl-track { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; }
.ifl-stage {
  flex: 1; min-width: 150px; border: 1px solid #efecdf; border-radius: 10px;
  padding: 12px; background: #fff; opacity: 0.5; transition: 0.35s;
}
.ifl-stage.done { opacity: 0.85; border-color: #e3dfd2; }
.ifl-stage.on { opacity: 1; border-color: #d9c86a; background: #fdf6d8; transform: translateY(-2px); }
.ifl-index {
  display: inline-flex; width: 20px; height: 20px; border-radius: 50%;
  background: #f2efe3; align-items: center; justify-content: center; font-size: 11px;
}
.ifl-stage strong { display: block; margin-top: 6px; font-size: 13px; }
.ifl-stage p { margin: 4px 0 0; font-size: 12px; color: #6b675c; line-height: 1.7; }
.ifl-controls { display: flex; gap: 10px; align-items: center; }
.ifl-hint { font-size: 12px; color: #8a8578; }
</style>
