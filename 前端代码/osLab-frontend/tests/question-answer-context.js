// Run with: node tests/question-answer-context.js
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");
const ts = require("typescript");
const { parse } = require("@vue/compiler-sfc");
const vue = require("vue");
const axios = require("axios");

const read = (file) =>
  fs.readFileSync(path.join(__dirname, "..", file), "utf8");

function evaluate(source, requireDependency, bindings = {}) {
  const module = { exports: {} };
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.CommonJS,
    },
  }).outputText;
  new Function(
    "require",
    "module",
    "exports",
    ...Object.keys(bindings),
    compiled
  )(requireDependency, module, module.exports, ...Object.values(bindings));
  return module.exports;
}

const contextModule = evaluate(read("src/utils/courseContext.ts"));
const { courseContext } = contextModule;
const validContext = {
  sourceUnitCode: "unit.test",
  sourceActivityCode: "activity.test",
};
const incompleteContexts = [
  {},
  { sourceUnitCode: "", sourceActivityCode: "" },
  { sourceUnitCode: " \t", sourceActivityCode: "\n" },
  { sourceUnitCode: "unit.test" },
  { sourceActivityCode: "activity.test" },
  { sourceUnitCode: "unit.test", sourceActivityCode: " " },
  { sourceUnitCode: null, sourceActivityCode: "activity.test" },
  {
    sourceUnitCode: ["unit.test", "unit.other"],
    sourceActivityCode: "activity.test",
  },
  { sourceUnitCode: "unit.test", sourceActivityCode: 42 },
];

function createHarness(props = {}, responseMode = "success") {
  const requests = [];
  const messages = [];
  const events = [];
  const emitted = [];
  const http = axios.create();
  http.defaults.adapter = async (config) => {
    requests.push({ url: config.url, data: JSON.parse(config.data) });
    const response = {
      config,
      headers: {},
      status: responseMode === "http-error" ? 503 : 200,
      data:
        responseMode === "business-error"
          ? { code: 40000, message: "来源活动题目不匹配", data: null }
          : {
              code: 0,
              message: "ok",
              data: { correct: true, attemptId: "88" },
            },
    };
    if (responseMode === "network-error") throw new Error("offline");
    if (responseMode === "http-error") {
      const error = new Error("unavailable");
      error.response = response;
      throw error;
    }
    return response;
  };
  const browser = {
    location: { pathname: "/assessments", search: "" },
    dispatchEvent: (event) => events.push(event.type),
  };
  const dependency = (name) => {
    if (name === "axios") return { default: http };
    if (name === "@/utils/courseContext") return contextModule;
    if (name === "@arco-design/web-vue/es/message")
      return {
        default: {
          error: (message) => messages.push(message),
          warning: (message) => messages.push(message),
        },
      };
    throw new Error(`Unexpected import: ${name}`);
  };
  evaluate(read("src/plugins/axios.ts"), dependency, { window: browser });
  const api = evaluate(read("src/api/learning.ts"), dependency, {
    window: browser,
    Event: class {
      constructor(type) {
        this.type = type;
      }
    },
  });
  const courseApi = evaluate(read("src/api/course.ts"), dependency);
  assert.equal(courseApi.courseContext, courseContext);
  const source = parse(read("src/components/learning/QuestionAnswerCard.vue"))
    .descriptor.scriptSetup.content;
  const card = evaluate(
    source +
      "\nexport { submit, submitting, judgeAnswer, multipleAnswer, fillAnswer, comprehensiveAnswer, result };",
    (name) => {
      if (name === "vue")
        return {
          ...vue,
          onMounted: () => undefined,
          onBeforeUnmount: () => undefined,
        };
      if (name === "vue-router")
        return { useRouter: () => ({ push: async () => undefined }) };
      if (name === "@arco-design/web-vue")
        return {
          Message: {
            info: () => undefined,
            warning: () => undefined,
            error: () => undefined,
            success: () => undefined,
          },
        };
      if (name === "@/api/learning") return api;
      if (name === "@/api/ai") return {};
      if (name === "@/utils/courseContext") return contextModule;
      if (name === "@/composables/useAiActions")
        return {
          getStartedAiAction: () => null,
          recordAiHelp: async () => null,
          startAiPlanAction: async () => null,
          submitAiPlanAction: async () => null,
        };
      if (name === "@/composables/useAiConversation")
        return {
          useAiConversation: () => ({ errorMsg: vue.ref("") }),
          askAi: async () => null,
        };
      if (name === "@/composables/useAiLauncher")
        return { askAiAssistant: () => undefined };
      if (name === "@/composables/useAiPageContext")
        return { getAiPageContext: () => null };
      if (name === "@/utils/aiSources")
        return {
          aiSourceRoute: () => null,
          highlightSourceInPage: () => false,
          openAiMaterialSource: () => false,
        };
      if (name.endsWith(".vue")) return { default: {} };
      throw new Error(`Unexpected component import: ${name}`);
    },
    {
      defineProps: () => ({
        question: { id: 42, type: "JUDGE", reviewStatus: "PUBLISHED" },
        ...props,
      }),
      defineEmits:
        () =>
        (...args) =>
          emitted.push(args),
    }
  );
  card.judgeAnswer.value = "true";
  return { ...card, requests, messages, events, emitted };
}

function practiceContext(query) {
  const source = parse(read("src/views/learning/LearningQuestionsView.vue"))
    .descriptor.scriptSetup.content;
  return evaluate(source + "\nexport { sourceContext };", (name) => {
    if (name === "vue")
      return {
        ...vue,
        onMounted: () => undefined,
        onBeforeUnmount: () => undefined,
        watch: () => undefined,
      };
    if (name === "vue-router")
      return { useRoute: () => ({ query }), useRouter: () => ({}) };
    if (name === "@/utils/courseContext") return contextModule;
    if (name === "@/composables/useCourseCatalog")
      return {
        useCourseCatalog: () => ({
          courseCode: vue.ref("course.test"),
          unitByCode: vue.ref(new Map()),
          sectionByCode: vue.ref(new Map()),
        }),
      };
    return {};
  }).sourceContext.value;
}

async function run() {
  for (const context of incompleteContexts) {
    assert.deepEqual(courseContext(context), {});
    assert.deepEqual(
      practiceContext({ unitCode: "filter.only", ...context }),
      {}
    );
    const card = createHarness(practiceContext(context));
    await card.submit();
    const payload = card.requests[0].data;
    assert.equal(payload.questionId, 42);
    assert.equal(payload.answer, "true");
    assert.ok(payload.submissionKey);
    assert.ok(!Object.hasOwn(payload, "sourceUnitCode"));
    assert.ok(!Object.hasOwn(payload, "sourceActivityCode"));
    assert.equal(card.result.value.correct, true);
    assert.equal(card.emitted.length, 1);
    assert.deepEqual(card.events, ["oslab:learning-changed"]);
    assert.deepEqual(card.messages, []);
    assert.equal(card.submitting.value, false);
  }

  const paddedContext = {
    sourceUnitCode: "  unit.test ",
    sourceActivityCode: " activity.test\t",
  };
  assert.deepEqual(practiceContext(paddedContext), validContext);
  const card = createHarness({
    ...practiceContext(paddedContext),
    assessmentRunId: "9223372036854775806",
  });
  await card.submit();
  const first = card.requests[0].data;
  assert.equal(first.sourceUnitCode, validContext.sourceUnitCode);
  assert.equal(first.sourceActivityCode, validContext.sourceActivityCode);
  assert.equal(first.assessmentRunId, "9223372036854775806");
  await card.submit();
  assert.notEqual(first.submissionKey, card.requests[1].data.submissionKey);

  // An explicit partial context supplied by another caller must still reach
  // the backend validation rather than silently becoming ordinary practice.
  const partial = createHarness(
    { sourceUnitCode: "unit.test" },
    "business-error"
  );
  await partial.submit();
  assert.equal(partial.requests[0].data.sourceUnitCode, "unit.test");
  assert.ok(!Object.hasOwn(partial.requests[0].data, "sourceActivityCode"));
  assert.equal(partial.result.value, null);
  assert.equal(partial.messages.length, 1);
  assert.deepEqual(partial.events, []);
  assert.deepEqual(partial.emitted, []);

  for (const mode of ["business-error", "network-error", "http-error"]) {
    const rejected = createHarness(validContext, mode);
    await rejected.submit();
    assert.equal(
      rejected.messages.length,
      1,
      `${mode} must show exactly one toast`
    );
    assert.equal(rejected.result.value, null);
    assert.equal(rejected.submitting.value, false);
    assert.deepEqual(rejected.events, []);
    assert.deepEqual(rejected.emitted, []);
  }
  const comprehensive = createHarness({
    question: { id: 42, type: "COMPREHENSIVE", reviewStatus: "PUBLISHED" },
  });
  comprehensive.comprehensiveAnswer.value = "自己的推导";
  await comprehensive.submit();
  assert.equal(comprehensive.requests[0].data.answer, "自己的推导");
  console.log(
    "Answer submission passed: absent/blank/partial/ambiguous URL context, valid activity and assessment context, fresh submission keys, success feedback and single error toasts."
  );
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
