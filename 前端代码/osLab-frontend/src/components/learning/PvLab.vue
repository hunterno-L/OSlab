<template>
  <div class="pvl">
    <div class="pvl-controls">
      <a-button size="small" type="primary" @click="produce">生产者 P(empty)</a-button>
      <a-button size="small" @click="consume">消费者 P(full)</a-button>
      <a-button size="small" @click="reset">重置</a-button>
      <span class="pvl-cap">缓冲区容量 {{ capacity }}</span>
    </div>

    <div class="pvl-sem">
      <div class="pvl-sem-item">
        <span>empty</span><strong>{{ empty }}</strong>
      </div>
      <div class="pvl-sem-item">
        <span>full</span><strong>{{ full }}</strong>
      </div>
      <div class="pvl-sem-item">
        <span>mutex</span><strong>{{ mutex }}</strong>
      </div>
      <div class="pvl-sem-item">
        <span>阻塞</span><strong>{{ blocked }}</strong>
      </div>
    </div>

    <div class="pvl-buffer">
      <span
        v-for="i in capacity"
        :key="i"
        class="pvl-slot"
        :class="{ filled: buffer.length >= i }"
      >
        {{ buffer[i - 1] === undefined ? "空" : buffer[i - 1] }}
      </span>
    </div>

    <ul class="pvl-log">
      <li v-for="(line, index) in log" :key="index" :class="line.type">{{ line.text }}</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

const props = defineProps<{ preset?: { capacity?: number } }>();

const capacity = props.preset?.capacity || 3;
let seq = 0;

const buffer = ref<Array<number | undefined>>([]);
const empty = ref(capacity);
const full = ref(0);
const mutex = ref(1);
const blocked = ref(0);
const log = ref<Array<{ type: string; text: string }>>([]);

const push = (type: string, text: string) => {
  log.value.unshift({ type, text });
  if (log.value.length > 8) log.value.pop();
};

const produce = () => {
  if (empty.value <= 0) {
    blocked.value += 1;
    push("warn", "empty = 0：生产者阻塞，等待消费者腾出空位");
    return;
  }
  mutex.value -= 1;
  empty.value -= 1;
  seq += 1;
  buffer.value.push(seq);
  full.value += 1;
  mutex.value += 1;
  push("ok", `生产数据 ${seq}：empty=${empty.value}，full=${full.value}`);
};

const consume = () => {
  if (full.value <= 0) {
    blocked.value += 1;
    push("warn", "full = 0：消费者阻塞，等待生产者放入数据");
    return;
  }
  mutex.value -= 1;
  const item = buffer.value.shift();
  full.value -= 1;
  empty.value += 1;
  mutex.value += 1;
  push("ok", `消费数据 ${item}：empty=${empty.value}，full=${full.value}`);
};

const reset = () => {
  buffer.value = [];
  empty.value = capacity;
  full.value = 0;
  mutex.value = 1;
  blocked.value = 0;
  seq = 0;
  log.value = [];
};
</script>

<style scoped>
.pvl { display: flex; flex-direction: column; gap: 14px; }
.pvl-controls { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.pvl-cap { font-size: 12px; color: #8a8578; }
.pvl-sem { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.pvl-sem-item {
  border: 1px solid #efecdf; border-radius: 10px; padding: 10px 12px;
  background: #fdfcf6; display: flex; flex-direction: column; gap: 2px;
}
.pvl-sem-item span { font-size: 12px; color: #8a8578; }
.pvl-sem-item strong { font-size: 20px; }
.pvl-buffer { display: flex; gap: 8px; }
.pvl-slot {
  width: 56px; height: 44px; display: flex; align-items: center; justify-content: center;
  border: 1px dashed #d8d3c0; border-radius: 8px; color: #b3ad9a; font-size: 13px;
}
.pvl-slot.filled { border-style: solid; border-color: #d9c86a; background: #fdf6d8; color: #6b5500; }
.pvl-log { margin: 0; padding-left: 18px; font-size: 13px; line-height: 1.9; }
.pvl-log .ok { color: #3d6b2f; }
.pvl-log .warn { color: #8a3a25; }
</style>
