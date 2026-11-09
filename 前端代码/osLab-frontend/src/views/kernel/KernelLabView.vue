<template>
  <div class="kl-page">
    <header class="kl-head">
      <div>
        <h1>真实内核实验 · xv6</h1>
        <p>改的是真内核：补丁提交后编译、在 QEMU 里启动、检查内核输出。不是模拟动画。</p>
      </div>
      <a-tag color="arcoblue">RISC-V · QEMU</a-tag>
    </header>

    <a-card class="kl-card" title="这个实验是什么">
      <p class="kl-text">
        xv6 是 MIT 的教学操作系统，几千行代码，跑在 RISC-V 上。你在这里修改它的内核源码，
        平台会把补丁应用到源码、重新编译、在 QEMU 里启动，只有内核真的输出了指定内容才算通过。
      </p>
      <a-divider />
      <p class="kl-text"><strong>可选任务</strong></p>
      <ol class="kl-list">
        <li><strong>启动横幅</strong>：在 <code>kernel/main.c</code> 打印 <code>ZhiCore</code>，判分看内核启动日志</li>
        <li><strong>CPU 数量</strong>：启动时打印 <code>CPUS=</code> 和核数，判分看日志里的标记</li>
        <li><strong>系统调用</strong>：实现 <code>hello()</code> 返回 42，判分跑用户程序 <code>hello</code> 看输出</li>
      </ol>
      <p class="kl-text"><strong>判分步骤</strong></p>
      <ol class="kl-list">
        <li>复制一份干净的 xv6 源码到临时目录</li>
        <li>把你的 unified diff 应用上去（补丁失败直接判 PATCH_ERROR）</li>
        <li>用 riscv 交叉编译器编译内核和文件系统镜像</li>
        <li>在 QEMU 里启动这个内核</li>
        <li>执行任务对应的验证命令，抓内核输出</li>
        <li>输出里出现约定标记才判 PASS，否则 FAIL</li>
      </ol>
      <pre class="kl-example">--- a/kernel/main.c
+++ b/kernel/main.c
@@ -18,7 +18,8 @@
     consoleinit();
     printkinit();
     printk("\n");
     printk("xv6 kernel is booting\n");
+    printk("ZhiCore\n");
     printk("\n");
     kinit();            // physical page allocator</pre>
    </a-card>

    <a-card class="kl-card" title="提交补丁">
      <a-textarea v-model="patch" :auto-size="{ minRows: 8, maxRows: 16 }" placeholder="粘贴 unified diff" />
      <div class="kl-actions">
        <a-select v-model="task" style="width: 220px">
          <a-option value="banner">任务一：启动横幅</a-option>
          <a-option value="cpus">任务二：CPU 数量</a-option>
          <a-option value="hello">任务三：hello 系统调用</a-option>
        </a-select>
        <a-button type="primary" :loading="running" @click="submit">提交并判分</a-button>
        <a-button :disabled="running" @click="patch = EXAMPLE">填入示例</a-button>
        <a-tag v-if="result" :color="result.ok ? 'green' : 'red'">{{ result.stage }} · {{ result.seconds }}s</a-tag>
      </div>
    </a-card>

    <a-card class="kl-card" title="判题过程">
      <div class="kl-stages">
        <div v-for="(stage, index) in stages" :key="stage" class="kl-stage"
             :class="{ on: running && index === activeStage, done: index < activeStage || (!running && result) }">
          <span class="kl-dot" />
          <span>{{ stage }}</span>
        </div>
      </div>
      <pre v-if="result" class="kl-log">{{ result.log || result.error }}</pre>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import axios from "axios";
import { Message } from "@arco-design/web-vue";

const EXAMPLE = `--- a/kernel/main.c
+++ b/kernel/main.c
@@ -18,7 +18,8 @@
     consoleinit();
     printkinit();
     printk("\\n");
     printk("xv6 kernel is booting\\n");
+    printk("ZhiCore\\n");
     printk("\\n");
     kinit();            // physical page allocator`;

const stages = ["复制源码", "应用补丁", "编译内核", "启动 QEMU", "执行测试", "判分"];
const patch = ref("");
const task = ref("banner");
const running = ref(false);
const activeStage = ref(-1);
const result = ref<Record<string, any> | null>(null);
let timer: number | undefined;

const tick = () => {
  timer = window.setInterval(() => {
    if (activeStage.value < stages.length - 1) activeStage.value += 1;
  }, 2500);
};

const submit = async () => {
  running.value = true;
  result.value = null;
  activeStage.value = 0;
  tick();
  try {
    const resp = await axios.post("/api/kernel/submit", { patch: patch.value, task: task.value }, { timeout: 420000 });
    result.value = resp.data;
    activeStage.value = stages.length;
    if (resp.data.ok) Message.success("通过");
    else Message.warning(resp.data.stage || "未通过");
  } catch (e: any) {
    Message.error(e?.message || "提交失败");
  } finally {
    running.value = false;
    if (timer) window.clearInterval(timer);
  }
};
</script>

<style scoped>
.kl-page { max-width: 980px; margin: 0 auto; padding: 24px 16px 48px; display: flex; flex-direction: column; gap: 16px; }
.kl-head { display: flex; justify-content: space-between; align-items: center; }
.kl-head h1 { margin: 0 0 6px; font-size: 22px; }
.kl-head p { margin: 0; color: #6b675c; font-size: 13px; }
.kl-card { border-radius: 12px; }
.kl-text { font-size: 14px; line-height: 1.9; color: #3f3d36; margin: 0; }
.kl-list { margin: 8px 0 0; padding-left: 18px; font-size: 13px; line-height: 1.9; color: #55524a; }
.kl-example { background: #f7f5ec; border-radius: 8px; padding: 12px; font-size: 12px; line-height: 1.7; overflow-x: auto; }
.kl-actions { display: flex; gap: 10px; align-items: center; margin-top: 12px; flex-wrap: wrap; }
.kl-stages { display: flex; gap: 8px; flex-wrap: wrap; }
.kl-stage { display: flex; align-items: center; gap: 6px; padding: 6px 12px; border-radius: 999px; background: #f4f2e9; color: #8a8578; font-size: 12px; transition: 0.3s; }
.kl-stage.on { background: #fdf0c9; color: #6b5b17; }
.kl-stage.done { background: #e9f2e4; color: #3d6b2f; }
.kl-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.kl-log { margin-top: 14px; max-height: 420px; overflow: auto; background: #1f1e1a; color: #e8e4d8; padding: 14px; border-radius: 8px; font-size: 12px; line-height: 1.7; white-space: pre-wrap; }
</style>
