<template>
  <div class="model-settings">
    <header class="model-head">
      <p class="model-sub">
        平台默认与个人兼容接口均可使用；候选配置必须测试通过后才能激活。浏览器只接收脱敏信息。
      </p>
      <a-button size="small" :loading="loading" @click="loadProfiles">
        刷新
      </a-button>
    </header>

    <section class="model-session-note" aria-label="当前课程会话">
      <span class="model-session-label">当前课程会话</span>
      <span v-if="session" class="model-session-value oslab-mono">
        <span>{{ session.courseCode }}</span>
        <span class="model-session-sep" aria-hidden="true"></span>
        <span>模型 {{ activeProfileName }}</span>
      </span>
      <span v-else class="model-muted"
        >尚未建立会话；激活模型后首次提问会使用该配置。</span
      >
      <span class="model-muted model-session-hint"
        >切换模型只改变后续推理，课程、资料、任务与学习记录保持不变。</span
      >
    </section>

    <p v-if="error" class="model-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="model-notice" role="status">{{ notice }}</p>

    <section class="model-list" aria-label="模型配置列表">
      <p
        v-if="loading && !profiles.length"
        class="model-state"
        aria-busy="true"
      >
        正在读取模型配置…
      </p>
      <p v-else-if="!profiles.length" class="model-state">
        暂无可用配置；平台默认配置由部署环境提供。
      </p>
      <article
        v-for="profile in profiles"
        :key="profile.profileId"
        class="model-card"
        :class="{ 'is-active': profile.active }"
      >
        <header class="model-card-head">
          <div class="model-card-title">
            <h2>{{ profile.name || "未命名配置" }}</h2>
            <span v-if="profile.platformDefault" class="model-tag"
              >平台默认</span
            >
            <span v-if="profile.active" class="model-tag is-active"
              >使用中</span
            >
            <span
              class="model-tag"
              :class="profile.testStatus === 'PASSED' ? 'is-passed' : ''"
              >{{ testStatusLabel(profile) }}</span
            >
          </div>
          <span class="oslab-mono model-id">#{{ profile.profileId }}</span>
        </header>

        <dl class="model-meta">
          <div>
            <dt>模型</dt>
            <dd class="oslab-mono">{{ profile.model }}</dd>
          </div>
          <div>
            <dt>服务地址</dt>
            <dd class="oslab-mono">{{ profile.baseUrlMasked || "—" }}</dd>
          </div>
          <div v-if="profile.apiKeyMasked">
            <dt>密钥</dt>
            <dd class="oslab-mono">{{ profile.apiKeyMasked }}</dd>
          </div>
          <div>
            <dt>能力</dt>
            <dd class="model-caps">
              <span :class="{ on: profile.capabilities?.chat }">聊天</span>
              <span :class="{ on: profile.capabilities?.structured }"
                >结构化输出</span
              >
              <span :class="{ on: profile.capabilities?.nativeTools }"
                >原生工具</span
              >
            </dd>
          </div>
          <div>
            <dt>最近测试</dt>
            <dd>{{ profile.testedAt || "尚未测试" }}</dd>
          </div>
        </dl>

        <div
          v-if="testResults[profile.profileId]"
          class="model-test-result"
          :class="testResults[profile.profileId].ok ? 'is-ok' : 'is-fail'"
          role="status"
        >
          <b>{{
            testResults[profile.profileId].ok ? "测试通过" : "测试失败"
          }}</b>
          <span class="oslab-mono"
            >{{ testResults[profile.profileId].latencyMs }} ms</span
          >
          <span v-if="testResults[profile.profileId].error">{{
            testResults[profile.profileId].error
          }}</span>
          <span v-else class="model-test-caps">
            <span
              >聊天
              {{
                yesNo(testResults[profile.profileId].capabilities?.chat)
              }}</span
            >
            <span class="model-test-sep" aria-hidden="true"></span>
            <span
              >结构化
              {{
                yesNo(testResults[profile.profileId].capabilities?.structured)
              }}</span
            >
            <span class="model-test-sep" aria-hidden="true"></span>
            <span
              >原生工具
              {{
                yesNo(testResults[profile.profileId].capabilities?.nativeTools)
              }}</span
            >
          </span>
        </div>

        <div class="model-actions">
          <a-button
            size="small"
            :loading="testingId === profile.profileId"
            @click="testProfile(profile)"
          >
            测试
          </a-button>
          <a-button
            v-if="!profile.active"
            size="small"
            type="primary"
            :disabled="!canActivate(profile)"
            :loading="activatingId === profile.profileId"
            @click="activateProfile(profile)"
          >
            激活
          </a-button>
          <span
            v-if="!profile.active && !canActivate(profile)"
            class="model-muted"
          >
            {{
              profile.platformDefault
                ? "平台默认由部署环境提供，不能在此激活。"
                : "测试通过后才可激活。"
            }}
          </span>
          <span v-if="profile.active" class="model-muted"
            >当前配置；切换后课程与记录保留。</span
          >
        </div>
      </article>
    </section>

    <section class="model-create" aria-labelledby="model-create-title">
      <h2 id="model-create-title">新增个人配置</h2>
      <p class="model-muted">
        API Key
        仅提交到后端加密保存，不在浏览器存储；自定义地址会做协议与内网访问校验。
      </p>
      <a-form
        :model="form"
        layout="vertical"
        class="model-form"
        @submit.prevent
      >
        <a-form-item label="名称" required>
          <a-input
            v-model="form.name"
            placeholder="例如：我的 DeepSeek"
            :max-length="128"
          />
        </a-form-item>
        <a-form-item label="服务类型">
          <a-select v-model="form.serviceType">
            <a-option value="OPENAI_COMPATIBLE">OpenAI 兼容</a-option>
            <a-option value="ARK">火山方舟（ARK）</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="Base URL" required>
          <a-input
            v-model="form.baseUrl"
            placeholder="https://api.example.com/v1"
            :max-length="500"
          />
        </a-form-item>
        <a-form-item label="模型名称" required>
          <a-input
            v-model="form.model"
            placeholder="例如：deepseek-chat"
            :max-length="200"
          />
        </a-form-item>
        <a-form-item label="API Key" required>
          <a-input-password
            v-model="form.apiKey"
            placeholder="只提交到后端，不在本地保存"
            :max-length="500"
          />
        </a-form-item>
      </a-form>
      <p v-if="formError" class="model-error" role="alert">{{ formError }}</p>
      <a-button
        type="primary"
        :loading="creating"
        :disabled="!canCreate"
        @click="createProfile"
      >
        保存为候选配置
      </a-button>
      <p class="model-muted">
        保存后状态为 CANDIDATE；请先测试，通过后再激活。
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  createAiModelProfile,
  listAiModelProfiles,
  testAiModelProfile,
  updateAiModelProfile,
  type AiModelProfileTestVO,
  type AiModelProfileVO,
} from "@/api/ai";
import {
  setAiModelProfile,
  useAiConversation,
} from "@/composables/useAiConversation";

const { session } = useAiConversation();
const profiles = ref<AiModelProfileVO[]>([]);
const loading = ref(false);
const error = ref("");
const notice = ref("");
const creating = ref(false);
const formError = ref("");
const testingId = ref("");
const activatingId = ref("");
const testResults = ref<Record<string, AiModelProfileTestVO>>({});
const form = reactive({
  name: "",
  serviceType: "OPENAI_COMPATIBLE" as "OPENAI_COMPATIBLE" | "ARK",
  baseUrl: "",
  model: "",
  apiKey: "",
});

const activeProfileName = computed(
  () =>
    profiles.value.find((profile) => profile.active)?.name ||
    (session.value?.modelProfileId
      ? `#${session.value.modelProfileId}`
      : "平台默认")
);

const canCreate = computed(
  () =>
    Boolean(form.name.trim()) &&
    Boolean(form.baseUrl.trim()) &&
    Boolean(form.model.trim()) &&
    Boolean(form.apiKey.trim()) &&
    !creating.value
);

const TEST_STATUS_LABELS: Record<string, string> = {
  UNTESTED: "未测试",
  PASSED: "测试通过",
  FAILED: "测试失败",
};
const testStatusLabel = (profile: AiModelProfileVO) => {
  if (profile.platformDefault) return "部署配置";
  return TEST_STATUS_LABELS[profile.testStatus || ""] || "未测试";
};

const yesNo = (value?: boolean) => (value ? "可用" : "不可用");

function canActivate(profile: AiModelProfileVO): boolean {
  if (profile.platformDefault || profile.active) return false;
  return profile.testStatus === "PASSED";
}

async function loadProfiles() {
  loading.value = true;
  error.value = "";
  try {
    const response = await listAiModelProfiles();
    if (response.data.code !== 0) {
      throw new Error(response.data.message || "模型配置读取失败");
    }
    profiles.value = Array.isArray(response.data.data)
      ? response.data.data
      : [];
  } catch (e) {
    error.value = e instanceof Error ? e.message : "模型配置读取失败";
  } finally {
    loading.value = false;
  }
}

async function createProfile() {
  if (!canCreate.value) return;
  creating.value = true;
  formError.value = "";
  notice.value = "";
  try {
    const response = await createAiModelProfile({
      name: form.name.trim(),
      serviceType: form.serviceType,
      baseUrl: form.baseUrl.trim(),
      model: form.model.trim(),
      apiKey: form.apiKey,
    });
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "保存候选配置失败");
    }
    notice.value = `已保存候选配置 #${response.data.data.profileId}，请先测试。`;
    form.name = "";
    form.baseUrl = "";
    form.model = "";
    form.apiKey = "";
    await loadProfiles();
  } catch (e) {
    formError.value = e instanceof Error ? e.message : "保存候选配置失败";
  } finally {
    creating.value = false;
  }
}

async function testProfile(profile: AiModelProfileVO) {
  if (testingId.value) return;
  testingId.value = profile.profileId;
  error.value = "";
  notice.value = "";
  try {
    const response = await testAiModelProfile(profile.profileId);
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "模型测试失败");
    }
    testResults.value = {
      ...testResults.value,
      [profile.profileId]: response.data.data,
    };
    notice.value = response.data.data.ok
      ? "测试通过；现在可以激活该配置。"
      : "测试未通过；原可用配置未被覆盖。";
    await loadProfiles();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "模型测试失败";
  } finally {
    testingId.value = "";
  }
}

async function activateProfile(profile: AiModelProfileVO) {
  if (!canActivate(profile) || activatingId.value) return;
  activatingId.value = profile.profileId;
  error.value = "";
  notice.value = "";
  try {
    const response = await updateAiModelProfile(profile.profileId, {
      active: true,
    });
    if (response.data.code !== 0) {
      throw new Error(response.data.message || "激活配置失败");
    }
    const switched = await setAiModelProfile(profile.profileId);
    notice.value = switched
      ? "已激活，并将当前课程会话的后续推理切到该模型；课程与记录保持不变。"
      : "配置已激活；当前课程会话切换模型失败，请稍后重试或重新打开会话。";
    await loadProfiles();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "激活配置失败";
  } finally {
    activatingId.value = "";
  }
}

onMounted(() => {
  void loadProfiles();
});
</script>

<style scoped>
.model-settings {
  width: min(980px, 100%);
  margin: 0 auto;
  padding: 8px 0 40px;
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-4);
}
.model-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.model-sub {
  margin: 0;
  max-width: 72ch;
  color: var(--oslab-muted-strong);
  font-size: 13px;
  line-height: 1.65;
  text-wrap: pretty;
}
.model-session-note {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px 12px;
  padding: 8px 0;
  border-top: 1px solid var(--oslab-line-soft);
  border-bottom: 1px solid var(--oslab-line-soft);
  font-size: var(--oslab-text-small);
  color: var(--oslab-ink-soft);
}
.model-session-label {
  flex: none;
  color: var(--oslab-muted-strong);
  font-size: 11px;
  font-weight: 700;
}
.model-session-value {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--oslab-ink-soft);
}
.model-session-sep {
  width: 1px;
  height: 9px;
  background: var(--oslab-line-strong);
}
.model-session-hint {
  margin-left: auto;
}
.model-muted {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.55;
}
.model-error {
  margin: 0;
  color: var(--oslab-weak-text);
  font-size: var(--oslab-text-small);
}
.model-notice {
  margin: 0;
  color: var(--oslab-mastered-text);
  font-size: var(--oslab-text-small);
}
.model-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.model-state {
  margin: 0;
  padding: 16px;
  border: 1px dashed var(--oslab-line-strong);
  border-radius: var(--oslab-radius-md);
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.model-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  background: var(--oslab-panel);
  box-shadow: var(--oslab-shadow-card);
}
.model-card.is-active {
  border-color: var(--oslab-line-strong);
  border-top: 3px solid var(--oslab-primary);
  box-shadow: var(--oslab-shadow-card);
}
.model-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.model-card-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.model-card-title h2 {
  margin: 0;
  color: var(--oslab-ink);
  font-size: 16px;
}
.model-tag {
  padding: 0 6px;
  border-radius: 3px;
  background: var(--oslab-panel-muted);
  color: var(--oslab-muted-strong);
  font-size: 10px;
  line-height: 18px;
}
.model-tag.is-active {
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
}
.model-tag.is-passed {
  background: var(--oslab-mastered-soft);
  color: var(--oslab-mastered-text);
}
.model-id {
  color: var(--oslab-muted);
  font-size: 11px;
}
.model-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px 16px;
  margin: 0;
}
.model-meta div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.model-meta dt {
  color: var(--oslab-muted-strong);
  font-size: 11px;
}
.model-meta dd {
  margin: 0;
  color: var(--oslab-ink-soft);
  font-size: 12px;
  overflow-wrap: anywhere;
}
.model-caps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.model-caps span {
  padding: 0 6px;
  border-radius: 3px;
  background: var(--oslab-panel-muted);
  color: var(--oslab-muted);
  font-size: 10px;
  line-height: 18px;
}
.model-caps span.on {
  background: var(--oslab-mastered-soft);
  color: var(--oslab-mastered-text);
}
.model-test-result {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px;
  border-radius: var(--oslab-radius-sm);
  font-size: var(--oslab-text-small);
  line-height: 1.55;
}
.model-test-result.is-ok {
  background: var(--oslab-mastered-soft);
  color: var(--oslab-mastered-text);
}
.model-test-result.is-fail {
  background: var(--oslab-weak-soft);
  color: var(--oslab-weak-text);
}
.model-test-caps {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.model-test-sep {
  width: 1px;
  height: 9px;
  background: var(--oslab-line-strong);
}
.model-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.model-create {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  background: var(--oslab-panel);
}
.model-create h2 {
  margin: 0;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: 18px;
  font-weight: 600;
}
.model-settings :deep(.arco-btn:focus-visible),
.model-settings :deep(.arco-input:focus-visible) {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}
.model-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 0 16px;
}
</style>
