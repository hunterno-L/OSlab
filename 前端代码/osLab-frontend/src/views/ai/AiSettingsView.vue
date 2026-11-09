<template>
  <div class="ai-page">
    <header class="ai-head">
      <div>
        <h1>AI 模型设置</h1>
        <p>填充式配置：留空即降级；保存后可测试连通性，切换模型不影响课程与记录。</p>
      </div>
      <router-link to="/ai"><a-button>返回助手</a-button></router-link>
    </header>

    <a-card class="ai-card" title="当前状态">
      <a-space wrap>
        <a-tag :color="config.configured ? 'green' : 'orange'">
          {{ config.configured ? "已配置" : "未配置（降级模式）" }}
        </a-tag>
        <span class="ai-muted">{{ config.apiKeyMasked || "暂无 Key" }}</span>
        <span class="ai-muted">模型：{{ config.model }}</span>
      </a-space>
    </a-card>

    <a-card class="ai-card" title="连接参数">
      <a-form layout="vertical">
        <a-form-item label="Base URL">
          <a-input v-model="form.baseUrl" placeholder="https://api.deepseek.com/v1" />
        </a-form-item>
        <a-form-item label="模型">
          <a-input v-model="form.model" placeholder="deepseek-chat" />
        </a-form-item>
        <a-form-item label="API Key（留空表示不修改）">
          <a-input-password v-model="form.apiKey" placeholder="sk-..." />
        </a-form-item>
        <a-form-item label="Embedding 模型（可选，未填则检索走关键词）">
          <a-input v-model="form.embeddingModel" placeholder="留空即可" />
        </a-form-item>
      </a-form>
      <a-space>
        <a-button type="primary" :loading="saving" @click="save">保存</a-button>
        <a-button :loading="testing" @click="test">测试连通性</a-button>
        <a-tag v-if="testResult" :color="testResult.ok ? 'green' : 'red'">
          {{ testResult.ok ? `可用 ${testResult.latencyMs}ms` : testResult.reason }}
        </a-tag>
      </a-space>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { Message } from "@arco-design/web-vue";
import { aiApi, type AiConfig } from "@/api/ai";

const config = ref<AiConfig>({ configured: false, baseUrl: "", model: "", apiKeyMasked: "" });
const form = reactive({ baseUrl: "", model: "", apiKey: "", embeddingModel: "" });
const testResult = ref<Record<string, any> | null>(null);
const saving = ref(false);
const testing = ref(false);

const load = async () => {
  config.value = await aiApi.getConfig();
  form.baseUrl = config.value.baseUrl;
  form.model = config.value.model;
  form.embeddingModel = config.value.embeddingModel || "";
};

const save = async () => {
  saving.value = true;
  try {
    config.value = await aiApi.saveConfig({ ...form });
    form.apiKey = "";
    Message.success("已保存");
  } catch (e: any) {
    Message.error(e?.message || "保存失败");
  } finally {
    saving.value = false;
  }
};

const test = async () => {
  testing.value = true;
  try {
    testResult.value = await aiApi.testConfig();
  } catch (e: any) {
    testResult.value = { ok: false, reason: e?.message || "测试失败" };
  } finally {
    testing.value = false;
  }
};

onMounted(load);
</script>

<style scoped>
.ai-page { max-width: 820px; margin: 0 auto; padding: 24px 16px 48px; display: flex; flex-direction: column; gap: 16px; }
.ai-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.ai-head h1 { margin: 0 0 6px; font-size: 22px; }
.ai-head p { margin: 0; color: #6b675c; font-size: 13px; }
.ai-card { border-radius: 12px; }
.ai-muted { color: #8a8578; font-size: 12px; }
</style>
