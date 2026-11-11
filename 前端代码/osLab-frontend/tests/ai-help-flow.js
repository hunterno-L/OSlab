// Run with: node tests/ai-help-flow.js
// 题卡 × 统一任务：提交前记录提示/答案揭示、复用 submissionKey 提交、
// 服务端证据与帮助徽标回显，以及提示不会直接泄露答案。
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

function evaluate(source, requireDependency, bindings = {}) {
  const module = { exports: {} };
  new Function(
    "require",
    "module",
    "exports",
    ...Object.keys(bindings),
    transpile(source)
  )(requireDependency, module, module.exports, ...Object.values(bindings));
  return module.exports;
}

function fakeStore() {
  return {
    state: { user: { loginUser: { id: 7 } } },
    watch: () => undefined,
  };
}

async function run() {
  const events = [];
  const prompts = [];
  global.window = {
    confirm: () => global.__confirmResult !== false,
    dispatchEvent(event) {
      events.push(event.type);
      return true;
    },
  };
  global.CustomEvent = class {
    constructor(type, init) {
      this.type = type;
      this.detail = init?.detail;
    }
  };

  const context = evaluate(read("src/composables/useAiPageContext.ts"), () => {
    throw new Error("useAiPageContext 不应有外部依赖");
  });
  context.registerAiPageContext(() => ({
    pageName: "课程单元",
    courseCode: "course.operating-systems",
    resourceRef: "question:42",
  }));

  const calls = { start: [], help: [], submit: [] };
  let helpLevel = "NONE";
  const aiApi = {
    startAiAction: async (actionId) => {
      calls.start.push(actionId);
      return {
        data: {
          code: 0,
          data: {
            actionId,
            status: "STARTED",
            activityRef: {
              actionId,
              type: "QUESTION",
              targetRef: { kind: "QUESTION", code: "42", anchor: null },
              title: "LRU 换出页",
              prompt: "选择换出页",
              options: [],
              frames: null,
              visibleSequence: [],
              visibleSteps: [],
              predictStepIndex: -1,
              submissionKey: "sk-1",
              helpLevel: "NONE",
              expectsText: false,
            },
          },
        },
      };
    },
    recordAiActionHelp: async (actionId, body) => {
      calls.help.push({ actionId, body });
      helpLevel = body.helpLevel;
      return {
        data: {
          code: 0,
          data: {
            actionId,
            helpLevel,
            selfReportedHelp: "NONE",
            eligibleForObservation: helpLevel === "NONE",
            eligibilityReason:
              helpLevel === "ANSWER_REVEALED"
                ? "已看答案，本次作答不计独立观测。"
                : helpLevel === "HINT"
                ? "使用提示，本次作答保留为非独立观测。"
                : "",
          },
        },
      };
    },
    submitAiAction: async (actionId, body) => {
      calls.submit.push({ actionId, body });
      return {
        data: {
          code: 0,
          data: {
            result: "PASS",
            correct: true,
            judgeMessage: "回答正确",
            evidence: {
              eventIds: ["ev-9"],
              skillCode: "os.memory.lru",
              result: "PASS",
              helpLevel,
              selfReportedHelp: "NONE",
              eligibleForObservation: helpLevel === "NONE",
              eligibilityReason: "",
            },
            analysis: "你按最近使用顺序找到了最久未使用的页。",
            diffSteps: [],
            masteryChanges: [],
            nextReviewDueAt: "2026-11-06T10:00:00Z",
          },
        },
      };
    },
  };
  const actions = evaluate(read("src/composables/useAiActions.ts"), (name) => {
    if (name === "vue") return vue;
    if (name === "@/store") return { __esModule: true, default: fakeStore() };
    if (name === "@/api/ai") return aiApi;
    if (name === "@/composables/useAiConversation")
      return { refreshAiNextActions: async () => [] };
    if (name === "@/composables/useAiPageContext") return context;
    throw new Error(`unexpected useAiActions dependency: ${name}`);
  });

  const cardSource = parse(
    read("src/components/learning/QuestionAnswerCard.vue")
  ).descriptor;
  const card = evaluate(
    cardSource.scriptSetup.content +
      "\nexport { loadAiAction, markHelp, submit, currentHelpLevel, aiRef, evidence, helpNotice, result, nextReviewDueAt, judgeAnswer };",
    (name) => {
      if (name === "vue")
        return {
          ...vue,
          onMounted: () => undefined,
          watch: () => undefined,
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
      if (name === "@/api/learning")
        return { answerLearningQuestion: async () => ({ data: { code: 0 } }) };
      if (name === "@/api/assessment")
        return {
          createPracticeAttempt: async () => ({
            data: {
              code: 0,
              data: {
                attemptContextId: "ctx-1",
                questionId: "42",
                questionRevision: 1,
                submissionKey: "sk-practice",
                helpLevel: "NONE",
                status: "READY",
              },
            },
          }),
          recordPracticeHelp: async () => ({
            data: {
              code: 0,
              data: {
                attemptContextId: "ctx-1",
                helpLevel: "HINT",
                eligibleForObservation: false,
                eligibilityReason: "使用提示",
              },
            },
          }),
        };
      if (name === "@/utils/assessmentTutor")
        return {
          openAssessmentTutor: (request) =>
            prompts.push(`hint:${request.intent}`),
        };
      if (name === "@/utils/learningEvents")
        return {
          notifyLearningUpdated: () => events.push("oslab:learning-updated"),
        };
      if (name === "@/api/ai") return {};
      if (name === "@/composables/useAiActions") return actions;
      if (name === "@/composables/useAiConversation")
        return {
          useAiConversation: () => ({ errorMsg: vue.ref("") }),
          askAi: async () => null,
        };
      if (name === "@/composables/useAiLauncher")
        return {
          askAiAssistant: (text) => prompts.push(text),
        };
      if (name === "@/composables/useAiPageContext")
        return { getAiPageContext: context.getAiPageContext };
      if (name === "@/utils/aiSources")
        return {
          aiSourceRoute: () => null,
          highlightSourceInPage: () => false,
          openAiMaterialSource: () => false,
        };
      if (name.endsWith(".vue")) return { default: {} };
      throw new Error(`unexpected card dependency: ${name}`);
    },
    {
      defineProps: () => ({
        question: {
          id: 42,
          type: "JUDGE",
          title: "LRU 会换出哪一页？",
          reviewStatus: "PUBLISHED",
        },
        aiActionId: "act-help",
        courseCode: "course.operating-systems",
      }),
      defineEmits: () => () => undefined,
    }
  );

  assert.equal(card.currentHelpLevel.value, "UNKNOWN");
  await card.loadAiAction();
  await card.loadAiAction();
  assert.equal(calls.start.length, 1, "同一任务只启动一次，命中内存缓存");
  assert.equal(card.aiRef.value?.submissionKey, "sk-1");
  assert.equal(card.currentHelpLevel.value, "NONE");

  // 答案揭示：取消确认不写库；确认后必须在提交之前落库
  global.__confirmResult = false;
  await card.markHelp("ANSWER_REVEALED");
  assert.equal(calls.help.length, 0);
  global.__confirmResult = true;
  await card.markHelp("ANSWER_REVEALED");
  assert.equal(calls.help.length, 1);
  assert.equal(calls.help[0].actionId, "act-help");
  assert.equal(calls.help[0].body.helpLevel, "ANSWER_REVEALED");
  assert.equal(card.currentHelpLevel.value, "ANSWER_REVEALED");
  assert.ok(card.helpNotice.value.includes("不计独立观测"));

  card.judgeAnswer.value = "true";
  await card.submit();
  assert.equal(calls.submit.length, 1);
  assert.equal(calls.submit[0].body.submissionKey, "sk-1");
  assert.equal(calls.submit[0].body.choiceKey, "true");
  assert.equal(card.result.value?.correct, true);
  assert.equal(card.evidence.value?.helpLevel, "ANSWER_REVEALED");
  assert.equal(card.nextReviewDueAt.value, "2026-11-06T10:00:00Z");
  assert.ok(events.includes("oslab:learning-changed"));

  // 再次提交复用同一 submissionKey，服务端据幂等键返回同一结果
  await card.submit();
  assert.equal(calls.submit.length, 2);
  assert.equal(calls.submit[1].body.submissionKey, "sk-1");

  // 提示：先落库帮助等级，再打开导师；提示词不得要求直接给答案
  const hintContext = evaluate(
    read("src/composables/useAiPageContext.ts"),
    () => {
      throw new Error("useAiPageContext 不应有外部依赖");
    }
  );
  hintContext.registerAiPageContext(() => ({
    pageName: "课程单元",
    courseCode: "course.operating-systems",
  }));
  const callsHint = { help: [], submit: [] };
  const hintCard = evaluate(
    cardSource.scriptSetup.content +
      "\nexport { loadAiAction, markHelp, submit, currentHelpLevel, aiRef };",
    (name) => {
      if (name === "vue")
        return { ...vue, onMounted: () => undefined, watch: () => undefined };
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
      if (name === "@/api/learning")
        return { answerLearningQuestion: async () => ({ data: { code: 0 } }) };
      if (name === "@/api/assessment")
        return {
          createPracticeAttempt: async () => ({
            data: {
              code: 0,
              data: {
                attemptContextId: "ctx-1",
                questionId: "42",
                questionRevision: 1,
                submissionKey: "sk-practice",
                helpLevel: "NONE",
                status: "READY",
              },
            },
          }),
          recordPracticeHelp: async () => ({
            data: {
              code: 0,
              data: {
                attemptContextId: "ctx-1",
                helpLevel: "HINT",
                eligibleForObservation: false,
                eligibilityReason: "使用提示",
              },
            },
          }),
        };
      if (name === "@/utils/assessmentTutor")
        return {
          openAssessmentTutor: (request) =>
            prompts.push(`hint:${request.intent}`),
        };
      if (name === "@/utils/learningEvents")
        return {
          notifyLearningUpdated: () => events.push("oslab:learning-updated"),
        };
      if (name === "@/api/ai") return {};
      if (name === "@/composables/useAiLauncher")
        return { askAiAssistant: (text) => prompts.push(text) };
      if (name === "@/composables/useAiPageContext")
        return { getAiPageContext: hintContext.getAiPageContext };
      if (name === "@/utils/aiSources")
        return {
          aiSourceRoute: () => null,
          highlightSourceInPage: () => false,
          openAiMaterialSource: () => false,
        };
      if (name === "@/composables/useAiActions")
        return {
          getStartedAiAction: () => null,
          startAiPlanAction: async (actionId) => ({
            actionId,
            type: "QUESTION",
            targetRef: { kind: "QUESTION", code: "42", anchor: null },
            title: "题",
            prompt: "",
            options: [],
            frames: null,
            visibleSequence: [],
            visibleSteps: [],
            predictStepIndex: -1,
            submissionKey: "sk-hint",
            helpLevel: "NONE",
            expectsText: false,
          }),
          recordAiHelp: async (actionId, level) => {
            callsHint.help.push({ actionId, level });
            return {
              actionId,
              helpLevel: level,
              selfReportedHelp: "NONE",
              eligibleForObservation: false,
              eligibilityReason: "使用提示",
            };
          },
          submitAiPlanAction: async (actionId, body) => {
            callsHint.submit.push({ actionId, body });
            return null;
          },
        };
      if (name === "@/composables/useAiConversation")
        return {
          useAiConversation: () => ({ errorMsg: vue.ref("") }),
          askAi: async () => null,
        };
      if (name.endsWith(".vue")) return { default: {} };
      throw new Error(`unexpected card dependency: ${name}`);
    },
    {
      defineProps: () => ({
        question: {
          id: 42,
          type: "JUDGE",
          title: "LRU 会换出哪一页？",
          reviewStatus: "PUBLISHED",
        },
        aiActionId: "act-hint",
        courseCode: "course.operating-systems",
      }),
      defineEmits: () => () => undefined,
    }
  );
  const promptCount = prompts.length;
  await hintCard.loadAiAction();
  await hintCard.markHelp("HINT");
  assert.equal(callsHint.help.length, 1);
  assert.equal(callsHint.help[0].level, "HINT");
  assert.ok(prompts.length > promptCount, "提示应在帮助等级落库后打开导师");
  const hintPrompt = prompts[prompts.length - 1];
  assert.ok(hintPrompt.includes("不直接给出答案"), "提示不得直接泄露答案");
  assert.equal(callsHint.submit.length, 0, "记录帮助不能触发提交");

  // 模板契约：帮助徽标可见，答案揭示与普通提示分开标识
  const template = cardSource.template.content;
  assert.ok(template.includes("<AiHelpBadge"), "题卡必须显示帮助等级徽标");
  assert.ok(template.includes("markHelp('ANSWER_REVEALED')"));
  assert.ok(template.includes("markHelp('HINT')"));
  const badgeSource = read("src/components/ai/AiHelpBadge.vue");
  for (const label of [
    "未使用平台提示",
    "使用过提示",
    "已看答案",
    "帮助情况未知",
  ]) {
    assert.ok(badgeSource.includes(label), `帮助徽标缺少「${label}」`);
  }

  console.log(
    "AI help flow passed: start caching, help-before-submit, confirm on answer reveal, idempotent submission key, evidence/badge reflection and non-leaking hint prompt."
  );
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
