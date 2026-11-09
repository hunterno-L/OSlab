<template>
  <div class="lab">
    <div class="lab-top">
      <div class="lab-top-left">
        <router-link to="/experiments" class="lab-back">← 返回编程实验</router-link>
        <div class="lab-title-row">
          <span class="lab-tag">内核实验</span>
          <h1>{{ meta.title || "加载中…" }}</h1>
        </div>
        <div class="lab-crumb">{{ meta.chapter }} · {{ meta.path }}</div>
      </div>
      <div class="lab-top-right">
        <a-select v-model="task" style="width: 220px" @change="selectTask">
          <a-option v-for="item in taskList" :key="item.code" :value="item.code">
            {{ item.title }}
          </a-option>
        </a-select>
        <a-button type="primary" :loading="running" @click="submit">提交评测</a-button>
        <a-button @click="scrollToHistory">查看提交记录</a-button>
      </div>
    </div>

    <div class="lab-body">
      <section class="lab-doc">
        <div class="lab-tabs">
          <span v-for="tab in tabs" :key="tab" class="lab-tab" :class="{ on: activeTab === tab }" @click="activeTab = tab">
            {{ tab }}
          </span>
        </div>

        <div v-if="activeTab === '实验说明'" class="lab-doc-body">
          <h3>任务描述</h3>
          <p>{{ meta.summary }}</p>
          <h3>知识要点</h3>
          <ol>
            <li v-for="p in meta.points" :key="p">{{ p }}</li>
          </ol>
          <h3>实验任务</h3>
          <ol>
            <li v-for="s in meta.steps" :key="s">{{ s }}</li>
          </ol>
        </div>

        <div v-else-if="activeTab === '知识要点'" class="lab-doc-body">
          <h3>本实验涉及</h3>
          <ul>
            <li v-for="p in meta.points" :key="p">{{ p }}</li>
          </ul>
          <p class="lab-note">{{ meta.guide }}</p>
        </div>

        <div v-else class="lab-doc-body">
          <h3>操作提示</h3>
          <p>{{ meta.guide }}</p>
          <h3>评测方式</h3>
          <p>平台把整份文件覆盖进 xv6 源码，编译后在 QEMU 里启动，抓内核输出；出现标记 <code>{{ meta.marker }}</code> 才判通过。</p>
        </div>
      </section>

      <section class="lab-code">
        <div class="lab-code-head">
          <span>CODE / EDITOR</span>
          <span class="lab-file">{{ meta.path }}</span>
        </div>
        <div class="lab-editor-wrap">
          <div class="lab-gutter" ref="gutterRef">
            <div v-for="n in lineCount" :key="n">{{ n }}</div>
          </div>
          <div class="lab-code-area">
            <pre class="lab-highlight" ref="preRef" v-html="highlighted"></pre>
            <textarea v-model="code" class="lab-editor" spellcheck="false" @scroll="syncScroll" ref="editorRef" />
          </div>
        </div>
      </section>
    </div>

    <section class="lab-eval" ref="evalRef">
      <div class="lab-eval-head">EVALUATION / 评测结果</div>
      <div class="lab-stages">
        <span v-for="(stage, index) in stages" :key="stage" class="lab-stage"
              :class="{ on: running && index === activeStage, done: (!running && result) || index < activeStage }">
          {{ stage }}
        </span>
      </div>
      <pre v-if="result" class="lab-log">{{ result.log || result.error }}</pre>
      <p v-else class="lab-empty">提交后在右侧查看判题进度与结果。</p>

      <a-table :data="history" :pagination="false" size="small" row-key="id" style="margin-top: 12px">
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
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import axios from "axios";
import { Message } from "@arco-design/web-vue";

const tabs = ["实验说明", "知识要点", "实验任务"];
const activeTab = ref("实验说明");
const taskList = ref<Array<Record<string, any>>>([]);
const task = ref("banner");
const meta = ref<Record<string, any>>({});
const code = ref("");
const running = ref(false);
const result = ref<Record<string, any> | null>(null);
const history = ref<any[]>([]);
const activeStage = ref(-1);
const stages = ["复制源码", "写入代码", "编译内核", "启动 QEMU", "执行测试", "判分"];
const evalRef = ref<HTMLElement | null>(null);
const editorRef = ref<HTMLTextAreaElement | null>(null);
const preRef = ref<HTMLElement | null>(null);
const gutterRef = ref<HTMLElement | null>(null);
let timer: number | undefined;

const lineCount = computed(() => Math.max(1, code.value.split("\n").length));

const escapeHtml = (text: string) =>
  text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

const highlighted = computed(() => {
  const escaped = escapeHtml(code.value) + "\n";
  return escaped.replace(/(\/\*[\s\S]*?\*\/|\/\/[^\n]*)/g, '<span class="cm">$1</span>');
});

const loadTasks = async () => {
  try {
    const resp = await axios.get("/api/kernel/tasks");
    taskList.value = resp.data || [];
    if (taskList.value.length && !taskList.value.some((t) => t.code === task.value)) {
      task.value = taskList.value[0].code;
    }
  } catch {
    taskList.value = [];
  }
};

const loadTemplate = async () => {
  const resp = await axios.get(`/api/kernel/task/${task.value}`);
  meta.value = resp.data;
  code.value = resp.data.content || "";
};

const loadHistory = async () => {
  try {
    const resp = await axios.get("/api/kernel/submissions", { params: { task: task.value } });
    history.value = resp.data || [];
  } catch {
    history.value = [];
  }
};

const selectTask = async () => {
  result.value = null;
  activeStage.value = -1;
  await Promise.all([loadTemplate(), loadHistory()]);
};

const syncScroll = () => {
  const ta = editorRef.value;
  if (!ta) return;
  if (preRef.value) {
    preRef.value.scrollTop = ta.scrollTop;
    preRef.value.scrollLeft = ta.scrollLeft;
  }
  if (gutterRef.value) {
    gutterRef.value.scrollTop = ta.scrollTop;
  }
};

const scrollToHistory = () => evalRef.value?.scrollIntoView({ behavior: "smooth" });

const submit = async () => {
  running.value = true;
  result.value = null;
  activeStage.value = 0;
  timer = window.setInterval(() => {
    if (activeStage.value < stages.length - 1) activeStage.value += 1;
  }, 2500);
  try {
    const resp = await axios.post("/api/kernel/submit", { task: task.value, content: code.value }, { timeout: 420000 });
    result.value = resp.data;
    activeStage.value = stages.length;
    if (resp.data.ok) Message.success("通过");
    else Message.warning(resp.data.stage || "未通过");
    await loadHistory();
    scrollToHistory();
  } catch (e: any) {
    Message.error(e?.message || "提交失败");
  } finally {
    running.value = false;
    if (timer) window.clearInterval(timer);
  }
};

onMounted(async () => {
  await loadTasks();
  await selectTask();
});
</script>

<style scoped>
.lab { background: #f7f6ef; min-height: calc(100vh - 120px); padding: 18px 22px 40px; }
.lab-top { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.lab-back { font-size: 13px; color: #4b6b8a; text-decoration: none; }
.lab-title-row { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.lab-title-row h1 { margin: 0; font-size: 24px; }
.lab-tag { border: 1px solid #d9c86a; color: #8a7320; font-size: 12px; padding: 2px 8px; border-radius: 4px; }
.lab-crumb { margin-top: 8px; font-size: 12px; color: #8a8578; }
.lab-top-right { display: flex; gap: 10px; align-items: center; }
.lab-body { display: grid; grid-template-columns: minmax(380px, 1fr) minmax(520px, 1.2fr); gap: 16px; margin-top: 16px; }
.lab-doc, .lab-code, .lab-eval { background: #fff; border: 1px solid #e8e4d4; border-radius: 12px; }
.lab-tabs { display: flex; gap: 18px; padding: 14px 18px 0; border-bottom: 1px solid #eee9d9; }
.lab-tab { font-size: 14px; color: #6b675c; padding-bottom: 10px; cursor: pointer; border-bottom: 2px solid transparent; }
.lab-tab.on { color: #b8860b; border-bottom-color: #d9b93a; }
.lab-doc-body { padding: 16px 20px 22px; font-size: 13.5px; line-height: 1.95; color: #3f3d36; }
.lab-doc-body h3 { font-size: 15px; margin: 16px 0 8px; border-left: 3px solid #d9b93a; padding-left: 8px; }
.lab-doc-body h3:first-child { margin-top: 0; }
.lab-doc-body ol, .lab-doc-body ul { padding-left: 20px; margin: 6px 0; }
.lab-note { background: #fdf8e6; border-radius: 8px; padding: 10px 12px; color: #6b5b17; }
.lab-code { display: flex; flex-direction: column; overflow: hidden; }
.lab-code-head { display: flex; justify-content: space-between; padding: 10px 14px; font-size: 12px; color: #8a8578; border-bottom: 1px solid #eee9d9; }
.lab-file { color: #b3ad9c; }
.lab-editor-wrap { display: flex; background: #2b2b26; min-height: 420px; max-height: 560px; overflow: hidden; }
.lab-gutter { width: 44px; padding: 12px 8px; text-align: right; color: #7d7a6c; font-family: Consolas, Monaco, monospace; font-size: 12px; line-height: 1.65; overflow: hidden; background: #232320; user-select: none; }
.lab-code-area { position: relative; flex: 1; overflow: hidden; }
.lab-highlight { position: absolute; inset: 0; margin: 0; padding: 12px; color: #e8e4d8; font-family: Consolas, Monaco, monospace; font-size: 12.5px; line-height: 1.65; white-space: pre; overflow: hidden; pointer-events: none; }
.lab-highlight :deep(.cm) { color: #7fc97f; font-style: italic; }
.lab-editor { position: absolute; inset: 0; padding: 12px; background: transparent; color: transparent; caret-color: #ffffff; border: none; outline: none; resize: none; font-family: Consolas, Monaco, monospace; font-size: 12.5px; line-height: 1.65; white-space: pre; overflow: auto; }
.lab-editor::selection { background: rgba(120, 160, 220, 0.35); }
.lab-eval { margin-top: 16px; padding: 16px 18px; }
.lab-eval-head { font-size: 12px; letter-spacing: 0.08em; color: #8a8578; margin-bottom: 10px; }
.lab-stages { display: flex; gap: 8px; flex-wrap: wrap; }
.lab-stage { font-size: 12px; padding: 5px 12px; border-radius: 999px; background: #f4f2e9; color: #8a8578; }
.lab-stage.on { background: #fdf0c9; color: #6b5b17; }
.lab-stage.done { background: #e9f2e4; color: #3d6b2f; }
.lab-log { margin-top: 12px; max-height: 320px; overflow: auto; background: #1f1e1a; color: #e8e4d8; padding: 14px; border-radius: 8px; font-size: 12px; line-height: 1.7; white-space: pre-wrap; }
.lab-empty { font-size: 12px; color: #a09a8a; margin: 10px 0 0; }
</style>
