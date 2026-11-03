// Run with: node tests/model-settings-contracts.js
// 模型设置契约：只展示脱敏信息；候选必须测试通过才能激活；
// 激活切换当前会话模型但不改动课程与学习记录入口。
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");
const ts = require("typescript");
const { parse } = require("@vue/compiler-sfc");
const vue = require("vue");

const read = (file) =>
  fs.readFileSync(path.join(__dirname, "..", file), "utf8");

function transpile(source) {
  return ts.transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.CommonJS,
    },
  }).outputText;
}

async function run() {
  const descriptor = parse(
    read("src/views/settings/ModelSettingsView.vue")
  ).descriptor;
  const calls = { list: 0, create: [], test: [], update: [], setProfile: [] };
  let profiles = [
    {
      profileId: "0",
      name: "平台默认",
      baseUrlMasked: "https://ark.example.com/***",
      model: "platform-model",
      capabilities: { chat: true, structured: true, nativeTools: false },
      testedAt: null,
      active: true,
      platformDefault: true,
      status: "ACTIVE",
      testStatus: "PASSED",
      apiKeyMasked: "****",
    },
    {
      profileId: "11",
      name: "候选已通过",
      baseUrlMasked: "https://api.example.com/***",
      model: "candidate-a",
      capabilities: { chat: true, structured: true, nativeTools: true },
      testedAt: "2026-11-02T10:00:00Z",
      active: false,
      platformDefault: false,
      status: "TESTED",
      testStatus: "PASSED",
      apiKeyMasked: "sk-****",
    },
    {
      profileId: "12",
      name: "候选未测试",
      baseUrlMasked: "https://api.example.com/***",
      model: "candidate-b",
      capabilities: { chat: false, structured: false, nativeTools: false },
      testedAt: null,
      active: false,
      platformDefault: false,
      status: "CANDIDATE",
      testStatus: "UNTESTED",
      apiKeyMasked: "sk-****",
    },
  ];
  const aiApi = {
    listAiModelProfiles: async () => {
      calls.list += 1;
      return { data: { code: 0, data: profiles.map((item) => ({ ...item })) } };
    },
    createAiModelProfile: async (body) => {
      calls.create.push(body);
      profiles = [
        ...profiles,
        {
          profileId: "13",
          name: body.name,
          baseUrlMasked: "https://new.example.com/***",
          model: body.model,
          capabilities: { chat: false, structured: false, nativeTools: false },
          testedAt: null,
          active: false,
          platformDefault: false,
          status: "CANDIDATE",
          testStatus: "UNTESTED",
          apiKeyMasked: "sk-****",
        },
      ];
      return {
        data: { code: 0, data: { profileId: "13", status: "CANDIDATE" } },
      };
    },
    testAiModelProfile: async (profileId) => {
      calls.test.push(profileId);
      const ok = profileId === "11";
      if (ok) {
        profiles = profiles.map((item) =>
          item.profileId === profileId
            ? { ...item, testStatus: "PASSED" }
            : item
        );
      }
      return {
        data: {
          code: 0,
          data: {
            ok,
            latencyMs: 321,
            capabilities: ok
              ? { chat: true, structured: true, nativeTools: true }
              : { chat: false, structured: false, nativeTools: false },
            ...(ok ? {} : { error: "401 invalid api key" }),
          },
        },
      };
    },
    updateAiModelProfile: async (profileId, body) => {
      calls.update.push({ profileId, body });
      profiles = profiles.map((item) =>
        item.profileId === profileId
          ? { ...item, active: true, status: "ACTIVE" }
          : item.platformDefault
          ? item
          : { ...item, active: false }
      );
      const updated = profiles.find((item) => item.profileId === profileId);
      return { data: { code: 0, data: updated } };
    },
  };
  const session = vue.ref({
    sessionId: "sess-1",
    courseCode: "course.operating-systems",
    modelProfileId: null,
    status: "ACTIVE",
  });
  const settings = (() => {
    const module = { exports: {} };
    new Function(
      "require",
      "module",
      "exports",
      transpile(
        descriptor.scriptSetup.content +
          "\nexport { loadProfiles, profiles, canActivate, testProfile, activateProfile, canCreate, form, createProfile, testResults, notice, error, activeProfileName };"
      )
    )(
      (name) => {
        if (name === "vue") return { ...vue, onMounted: () => undefined };
        if (name === "@/api/ai") return aiApi;
        if (name === "@/composables/useAiConversation")
          return {
            useAiConversation: () => ({ session }),
            setAiModelProfile: async (profileId) => {
              calls.setProfile.push(profileId);
              session.value = { ...session.value, modelProfileId: profileId };
              return true;
            },
          };
        throw new Error(`unexpected model settings dependency: ${name}`);
      },
      module,
      module.exports
    );
    return module.exports;
  })();

  await settings.loadProfiles();
  assert.equal(calls.list, 1);
  assert.equal(settings.profiles.value.length, 3);
  assert.equal(settings.activeProfileName.value, "平台默认");

  const passed = settings.profiles.value.find(
    (profile) => profile.profileId === "11"
  );
  const untested = settings.profiles.value.find(
    (profile) => profile.profileId === "12"
  );
  const platform = settings.profiles.value.find(
    (profile) => profile.profileId === "0"
  );
  assert.equal(settings.canActivate(passed), true);
  assert.equal(settings.canActivate(untested), false, "未测试不得激活");
  assert.equal(settings.canActivate(platform), false, "平台默认不可在此激活");
  const active = { ...passed, active: true };
  assert.equal(settings.canActivate(active), false, "已激活无需重复激活");

  await settings.testProfile(untested);
  assert.equal(calls.test.length, 1);
  assert.equal(settings.testResults.value["12"].ok, false);
  assert.ok(settings.notice.value.includes("测试未通过"));
  assert.equal(settings.canActivate(untested), false, "测试失败仍不可激活");

  const before = calls.update.length;
  await settings.activateProfile(untested);
  assert.equal(calls.update.length, before, "未通过测试不得调用激活接口");
  assert.equal(calls.setProfile.length, 0);

  await settings.activateProfile(passed);
  assert.equal(calls.update.length, 1);
  assert.deepEqual(calls.update[0], {
    profileId: "11",
    body: { active: true },
  });
  assert.deepEqual(calls.setProfile, ["11"]);
  assert.ok(settings.notice.value.includes("课程与记录保持不变"));
  assert.equal(session.value.modelProfileId, "11");

  assert.equal(settings.canCreate.value, false);
  Object.assign(settings.form, {
    name: "我的测试配置",
    serviceType: "OPENAI_COMPATIBLE",
    baseUrl: "https://api.example.com/v1",
    model: "deepseek-chat",
    apiKey: "sk-plaintext-never-stored",
  });
  assert.equal(settings.canCreate.value, true);
  await settings.createProfile();
  assert.equal(calls.create.length, 1);
  assert.deepEqual(calls.create[0], {
    name: "我的测试配置",
    serviceType: "OPENAI_COMPATIBLE",
    baseUrl: "https://api.example.com/v1",
    model: "deepseek-chat",
    apiKey: "sk-plaintext-never-stored",
  });
  assert.ok(settings.notice.value.includes("请先测试"));
  assert.equal(settings.form.apiKey, "", "保存后清空表单中的明文密钥");
  assert.equal(settings.profiles.value.length, 4);

  const template = descriptor.template.content;
  for (const needle of [
    "baseUrlMasked",
    "apiKeyMasked",
    "a-input-password",
    "测试通过后才可激活",
    "课程、资料、任务与学习记录保持不变",
  ]) {
    assert.ok(template.includes(needle), `模型设置页缺少契约：${needle}`);
  }
  assert.ok(
    !template.includes("{{ profile.apiKey }}"),
    "模板不得显示明文 API Key"
  );
  assert.ok(
    !descriptor.scriptSetup.content.includes("localStorage"),
    "模型配置不得写入浏览器存储"
  );

  console.log(
    "Model settings contracts passed: masked display, activation gated on PASSED, session model switch preserves course/records, candidate creation without local secret storage."
  );
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
