<template>
  <div class="kl-page">
    <header class="kl-head">
      <h1>真实内核实验（xv6）</h1>
      <p>在 QEMU 里启动 xv6，跑真实内核测试：构建 → 启动 → 执行 → 判分，全程无模拟。</p>
    </header>

    <a-card class="kl-card" title="运行测试">
      <a-space wrap>
        <a-select v-model="test" style="width: 260px">
          <a-option value="boot">启动自检（约 10 秒）</a-option>
          <a-option value="copyin">usertests copyin（约 1 分钟）</a-option>
          <a-option value="usertests">完整自测 usertests -q（约 5 分钟）</a-option>
        </a-select>
        <a-button type="primary" :loading="running" @click="run">运行</a-button>
        <a-tag v-if="result" :color="result.ok ? 'green' : 'red'">
          {{ result.ok ? "PASS" : "FAIL" }} · {{ result.seconds }}s
        </a-tag>
      </a-space>
      <p class="kl-hint">后端会在 WSL 里执行构建与 QEMU，只允许白名单测试，不接收任意命令。</p>
    </a-card>

    <a-card v-if="result" class="kl-card" title="输出">
      <pre class="kl-log">{{ result.log }}</pre>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import axios from "axios";
import { Message } from "@arco-design/web-vue";

const test = ref("boot");
const running = ref(false);
const result = ref<Record<string, any> | null>(null);

const run = async () => {
  running.value = true;
  result.value = null;
  try {
    const resp = await axios.post("/api/kernel/run", { test: test.value }, { timeout: 420000 });
    result.value = resp.data;
    if (resp.data.ok) Message.success("测试通过");
    else Message.warning("测试未通过");
  } catch (e: any) {
    Message.error(e?.message || "运行失败");
  } finally {
    running.value = false;
  }
};
</script>

<style scoped>
.kl-page { max-width: 980px; margin: 0 auto; padding: 24px 16px 48px; display: flex; flex-direction: column; gap: 16px; }
.kl-head h1 { margin: 0 0 6px; font-size: 22px; }
.kl-head p { margin: 0; color: #6b675c; font-size: 13px; }
.kl-card { border-radius: 12px; }
.kl-hint { margin: 12px 0 0; color: #8a8578; font-size: 12px; }
.kl-log { margin: 0; max-height: 560px; overflow: auto; background: #1f1e1a; color: #e8e4d8; padding: 14px; border-radius: 8px; font-size: 12px; line-height: 1.7; white-space: pre-wrap; }
</style>
