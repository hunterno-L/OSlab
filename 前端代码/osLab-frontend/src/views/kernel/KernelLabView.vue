<template>
  <div class="kl-workspace">
    <aside class="kl-side">
      <div class="kl-side-title">内核实验</div>
      <div
        v-for="item in tasks"
        :key="item.key"
        class="kl-task"
        :class="{ active: task === item.key }"
        @click="selectTask(item.key)"
      >
        <div class="kl-task-name">{{ item.name }}</div>
        <div class="kl-task-tag">{{ item.file }}</div>
      </div>
      <div class="kl-side-foot">RISC-V · QEMU · xv6</div>
    </aside>

    <main class="kl-main">
      <header class="kl-head">
        <div>
          <h1>{{ current.name }}</h1>
          <p>{{ current.desc }}</p>
        </div>
        <a-tag :color="latest && latest.passed ? 'green' : 'gray'">
          {{ latest ? (latest.passed ? "已通过" : "未通过") : "未提交" }}
        </a-tag>
      </header>

      <section class="kl-panel">
        <div class="kl-panel-title">任务说明</div>
        <p class="kl-text">{{ current.detail }}</p>
        <pre class="kl-example">{{ current.example }}</pre>
      </section>

      <section class="kl-panel">
        <div class="kl-panel-title">提交补丁（unified diff）</div>
        <a-textarea v-model="patch" :auto-size="{ minRows: 10, maxRows: 18 }" placeholder="--- a/kernel/... +++ b/kernel/..." />
        <div class="kl-actions">
          <a-button type="primary" :loading="running" @click="submit">提交并判分</a-button>
          <a-button :disabled="running" @click="patch = current.example">填入示例</a-button>
          <a-tag v-if="result" :color="result.ok ? 'green' : 'red'">{{ result.stage }} · {{ result.seconds }}s</a-tag>
        </div>
      </section>

      <section v-if="running || result" class="kl-panel">
        <div class="kl-panel-title">判题过程</div>
        <div class="kl-stages">
          <div v-for="(stage, index) in stages" :key="stage" class="kl-stage"
               :class="{ on: running && index === activeStage, done: (!running && result) || index < activeStage }">
            <span class="kl-dot" />{{ stage }}
          </div>
        </div>
        <pre v-if="result" class="kl-log">{{ result.log || result.error }}</pre>
      </section>

      <section class="kl-panel">
        <div class="kl-panel-title">我的提交</div>
        <a-table :data="history" :pagination="false" size="small" row-key="id">
          <template #columns>
            <a-table-column title="任务" data-index="task" />
            <a-table-column title="结果">
              <template #cell="{ record }">
                <a-tag :color="record.passed ? 'green' : 'red'">{{ record.stage }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="耗时(s)" data-index="seconds" />
            <a-table-column title="时间" data-index="createTime" />
          </template>
        </a-table>
        <a-empty v-if="!history.length" description="还没有提交记录" />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import axios from "axios";
import { Message } from "@arco-design/web-vue";

type Task = { key: string; name: string; file: string; desc: string; detail: string; example: string };

const tasks: Task[] = [
  {
    key: "banner",
    name: "任务一 · 启动横幅",
    file: "kernel/main.c",
    desc: "让内核启动时多打印一行 ZhiCore",
    detail: "在 main() 里已有的 printk(\"xv6 kernel is booting\\n\") 后面加一行输出。判分检查内核启动日志。",
    example: `--- a/kernel/main.c
+++ b/kernel/main.c
@@ -18,7 +18,8 @@
     consoleinit();
     printkinit();
     printk("\\n");
     printk("xv6 kernel is booting\\n");
+    printk("ZhiCore\\n");
     printk("\\n");
     kinit();            // physical page allocator`,
  },
  {
    key: "cpus",
    name: "任务二 · CPU 数量",
    file: "kernel/main.c",
    desc: "启动时打印 CPUS= 和核数",
    detail: "xv6 在 proc.c 里维护 ncpu，defs.h 已声明。可在 procinit() 之后打印 printk(\"CPUS=%d\\n\", ncpu)。",
    example: `--- a/kernel/main.c
+++ b/kernel/main.c
@@ -18,6 +18,7 @@
     printk("\\n");
     printk("xv6 kernel is booting\\n");
     printk("\\n");
+    printk("CPUS=%d\\n", 3);
     kinit();            // physical page allocator`,
  },
  {
    key: "hello",
    name: "任务三 · hello 系统调用",
    file: "kernel/sysproc.c / syscall.c / syscall.h / user/usys.pl",
    desc: "实现 hello() 返回 42，判分跑用户程序 hello",
    detail: "平台会预置 user/hello.c 并加入构建。你需要：syscall.h 定义编号、sysproc.c 实现 sys_hello、syscall.c 注册、usys.pl 加桩。",
    example: `--- a/kernel/syscall.h
+++ b/kernel/syscall.h
@@ -1,3 +1,4 @@
 #define SYS_fork 1
+#define SYS_hello 22
 #define SYS_exit 2`,
  },
];

const stages = ["复制源码", "应用补丁", "编译内核", "启动 QEMU", "执行测试", "判分"];
const task = ref("banner");
const patch = ref("");
const running = ref(false);
const activeStage = ref(-1);
const result = ref<Record<string, any> | null>(null);
const history = ref<any[]>([]);
let timer: number | undefined;

const current = computed(() => tasks.find((t) => t.key === task.value) || tasks[0]);
const latest = computed(() => history.value.find((h) => h.task === task.value));

const loadHistory = async () => {
  try {
    const resp = await axios.get("/api/kernel/submissions", { params: { task: task.value } });
    history.value = resp.data || [];
  } catch {
    history.value = [];
  }
};

const selectTask = (key: string) => {
  task.value = key;
  patch.value = "";
  result.value = null;
  activeStage.value = -1;
  loadHistory();
};

const submit = async () => {
  running.value = true;
  result.value = null;
  activeStage.value = 0;
  timer = window.setInterval(() => {
    if (activeStage.value < stages.length - 1) activeStage.value += 1;
  }, 2500);
  try {
    const resp = await axios.post("/api/kernel/submit", { patch: patch.value, task: task.value }, { timeout: 420000 });
    result.value = resp.data;
    activeStage.value = stages.length;
    if (resp.data.ok) Message.success("通过");
    else Message.warning(resp.data.stage || "未通过");
    await loadHistory();
  } catch (e: any) {
    Message.error(e?.message || "提交失败");
  } finally {
    running.value = false;
    if (timer) window.clearInterval(timer);
  }
};

onMounted(loadHistory);
</script>

<style scoped>
.kl-workspace { display: flex; min-height: calc(100vh - 140px); background: #faf9f3; }
.kl-side { width: 240px; border-right: 1px solid #ece8da; padding: 18px 14px; background: #fffdf6; }
.kl-side-title { font-size: 13px; color: #8a8578; margin-bottom: 12px; letter-spacing: 0.08em; }
.kl-task { padding: 10px 12px; border-radius: 10px; cursor: pointer; margin-bottom: 8px; border: 1px solid transparent; }
.kl-task:hover { background: #f6f3e6; }
.kl-task.active { background: #fdf4d6; border-color: #e6d48a; }
.kl-task-name { font-size: 13px; color: #3f3d36; }
.kl-task-tag { font-size: 11px; color: #a09a8a; margin-top: 4px; }
.kl-side-foot { margin-top: 18px; font-size: 11px; color: #b3ad9c; }
.kl-main { flex: 1; padding: 20px 24px 40px; display: flex; flex-direction: column; gap: 14px; max-width: 1000px; }
.kl-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.kl-head h1 { margin: 0 0 6px; font-size: 20px; }
.kl-head p { margin: 0; color: #6b675c; font-size: 13px; }
.kl-panel { background: #fff; border: 1px solid #ece8da; border-radius: 12px; padding: 16px; }
.kl-panel-title { font-size: 13px; font-weight: 600; margin-bottom: 10px; color: #3f3d36; }
.kl-text { margin: 0; font-size: 13px; line-height: 1.9; color: #55524a; }
.kl-example { margin: 10px 0 0; background: #f7f5ec; border-radius: 8px; padding: 12px; font-size: 12px; line-height: 1.7; overflow-x: auto; }
.kl-actions { display: flex; gap: 10px; align-items: center; margin-top: 12px; flex-wrap: wrap; }
.kl-stages { display: flex; gap: 8px; flex-wrap: wrap; }
.kl-stage { display: flex; align-items: center; gap: 6px; padding: 6px 12px; border-radius: 999px; background: #f4f2e9; color: #8a8578; font-size: 12px; }
.kl-stage.on { background: #fdf0c9; color: #6b5b17; }
.kl-stage.done { background: #e9f2e4; color: #3d6b2f; }
.kl-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.kl-log { margin-top: 12px; max-height: 360px; overflow: auto; background: #1f1e1a; color: #e8e4d8; padding: 14px; border-radius: 8px; font-size: 12px; line-height: 1.7; white-space: pre-wrap; }
</style>
