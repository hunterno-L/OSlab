// Run with: node tests/prediction-gate-contracts.js
// “先预测这一步”契约：提交前只显示允许看的前缀；提交后揭示真实轨迹并高亮
// 目标步骤；AI 任务使用服务端 submissionKey，本地练习明确不写证据。
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

const pageModule = (() => {
  const module = { exports: {} };
  new Function(
    "require",
    "module",
    "exports",
    transpile(read("src/simulation/page.ts"))
  )(
    () => {
      throw new Error("simulation/page 不应有运行时依赖");
    },
    module,
    module.exports
  );
  return module.exports;
})();

function visiblePrefix(steps, count) {
  return steps.slice(0, count).map((step) => ({
    step: step.index,
    page: step.state.page,
    fault: step.state.fault,
    evicted: step.state.evicted,
    frames: step.state.frames,
  }));
}

function createGate(props, actions) {
  const emitted = [];
  const descriptor = parse(
    read("src/components/ai/PredictionGate.vue")
  ).descriptor;
  const gate = evaluate(
    descriptor.scriptSetup.content +
      "\nexport { loadAction, submitVictim, submitAggregate, visible, prefixRows, revealedRows, targetStepNumber, candidateVictims, prompt, aggregate, correct, actualValue, analysis, serverResult, evidence, choice, countValue, submitted, error };",
    (name) => {
      if (name === "vue")
        return { ...vue, onMounted: () => undefined, watch: () => undefined };
      if (name === "@/api/ai") return {};
      if (name === "@/components/ai/AiHelpBadge.vue") return { default: {} };
      if (name === "@/components/ai/AiNextActions.vue") return { default: {} };
      if (name === "@/composables/useAiActions") return actions;
      if (name === "@/composables/useAiConversation")
        return {
          useAiConversation: () => ({ nextActions: vue.ref([]) }),
        };
      if (name === "@/simulation/page") return pageModule;
      if (name === "@/simulation/types") return {};
      throw new Error(`unexpected prediction gate dependency: ${name}`);
    },
    {
      defineProps: () => props,
      defineEmits: () => (event, payload) => emitted.push({ event, payload }),
    }
  );
  return { ...gate, emitted, descriptor };
}

async function run() {
  const sequence = [1, 2, 3, 1, 4];
  const fullTrace = pageModule.runPage("page.lru", { frames: 3, sequence });

  // 1) 本地练习：无服务端任务，明确标注不写证据；提交前遮住目标步及之后
  const local = createGate(
    {
      definition: {
        key: "page.lru",
        category: "page",
        title: "LRU 算法",
      },
      input: { frames: 3, sequence },
    },
    {
      getStartedAiAction: () => null,
      startAiPlanAction: async () => null,
      submitAiPlanAction: async () => null,
    }
  );
  await local.loadAction();
  assert.equal(local.visible.value, true);
  assert.equal(local.aggregate.value, false);
  assert.equal(local.emitted[0].event, "availability");
  assert.equal(local.emitted[0].payload, true);
  assert.equal(local.targetStepNumber.value, 5);
  assert.equal(local.prefixRows.value.length, 4);
  const template = local.descriptor.template.content;
  assert.ok(
    template.includes('v-if="!submitted"') &&
      template.includes("<template v-else>"),
    "提交前/提交后必须由 submitted 切换轨迹可见性"
  );
  assert.ok(!local.prompt.value.includes("换出 2"), "预测提示不得提前泄露答案");
  assert.deepEqual(local.candidateVictims.value, [2, 3, 1]);
  local.choice.value = 2;
  local.submitted.value = false;
  await local.submitVictim();
  assert.equal(local.submitted.value, true);
  assert.equal(local.correct.value, true);
  assert.equal(local.actualValue.value, 2);
  assert.equal(local.revealedRows.value.length, 5, "提交后揭示完整真实轨迹");
  assert.equal(local.evidence.value, null, "本地练习不生成服务端证据");
  assert.deepEqual(local.emitted[local.emitted.length - 1], {
    event: "submitted",
    payload: { correct: true, actual: 2 },
  });

  // 2) AI 任务：只给允许看的前缀；提交带 submissionKey，成功后展示证据与下一步
  const calls = { start: [], submit: [] };
  const actionRef = {
    actionId: "act-predict",
    type: "PREDICT",
    targetRef: {
      kind: "SIMULATION",
      code: "page.lru-vs-fifo.victim",
      anchor: null,
    },
    title: "预测 LRU 的换出页（命中后是否更新顺序）",
    prompt:
      "三个物理块，访问序列 1,2,3,1,4。按 LRU，访问 4 发生缺页时应换出哪一页？",
    options: [],
    frames: 3,
    visibleSequence: sequence,
    visibleSteps: visiblePrefix(fullTrace.steps, 4),
    predictStepIndex: 4,
    submissionKey: "sk-predict",
    helpLevel: "HINT",
    expectsText: false,
  };
  const serverResult = {
    result: "PASS",
    correct: true,
    judgeMessage: "预测正确",
    evidence: {
      eventIds: ["ev-predict"],
      skillCode: "os.memory.lru",
      result: "PASS",
      helpLevel: "HINT",
      selfReportedHelp: "NONE",
      eligibleForObservation: true,
      eligibilityReason: "使用提示，已记录帮助等级。",
    },
    analysis: "命中 1 后 LRU 顺序变为 2→3→1，因此换出 2。",
    diffSteps: [],
    masteryChanges: [],
    nextReviewDueAt: "2026-11-04T10:00:00Z",
  };
  const ai = createGate(
    {
      definition: { key: "page.lru", category: "page", title: "LRU 算法" },
      input: null,
      actionId: "act-predict",
      courseCode: "course.operating-systems",
    },
    {
      getStartedAiAction: () => null,
      startAiPlanAction: async (actionId) => {
        calls.start.push(actionId);
        return actionRef;
      },
      submitAiPlanAction: async (actionId, body, courseCode) => {
        calls.submit.push({ actionId, body, courseCode });
        return serverResult;
      },
    }
  );
  await ai.loadAction();
  assert.equal(calls.start.length, 1);
  assert.equal(ai.visible.value, true);
  assert.equal(ai.prefixRows.value.length, 4);
  assert.equal(ai.submitted.value, false);
  assert.equal(ai.prompt.value, actionRef.prompt);
  assert.equal(ai.candidateVictims.value.length, 3);
  ai.choice.value = 2;
  await ai.submitVictim();
  assert.equal(calls.submit.length, 1);
  assert.deepEqual(calls.submit[0].body, {
    submissionKey: "sk-predict",
    predictedProcess: "2",
  });
  assert.equal(calls.submit[0].courseCode, "course.operating-systems");
  assert.equal(ai.serverResult.value?.correct, true);
  assert.equal(ai.evidence.value?.helpLevel, "HINT");
  assert.ok(ai.analysis.value.includes("换出 2"));
  assert.equal(ai.actualValue.value, 2);
  assert.equal(ai.revealedRows.value.length, 5);

  // 3) 聚合预测：提交前连前缀表都不显示，提交后揭示整条序列
  const aggregateRef = {
    ...actionRef,
    prompt:
      "三个物理块，访问序列 1,2,3,4,1,2,5。按 FIFO，整个过程一共发生多少次缺页？",
    frames: 3,
    visibleSequence: [1, 2, 3, 4, 1, 2, 5],
    visibleSteps: [],
    predictStepIndex: -1,
    submissionKey: "sk-count",
    helpLevel: "NONE",
  };
  const aggregate = createGate(
    {
      definition: { key: "page.fifo", category: "page", title: "FIFO 算法" },
      input: null,
      actionId: "act-count",
    },
    {
      getStartedAiAction: () => null,
      startAiPlanAction: async () => aggregateRef,
      submitAiPlanAction: async (actionId, body) => {
        assert.deepEqual(body, {
          submissionKey: "sk-count",
          predictedProcess: "7",
        });
        return {
          ...serverResult,
          correct: true,
          evidence: { ...serverResult.evidence, helpLevel: "NONE" },
        };
      },
    }
  );
  await aggregate.loadAction();
  assert.equal(aggregate.visible.value, true);
  assert.equal(aggregate.aggregate.value, true);
  assert.equal(aggregate.prefixRows.value.length, 0);
  aggregate.countValue.value = 7;
  await aggregate.submitAggregate();
  assert.equal(aggregate.actualValue.value, 7);
  assert.equal(aggregate.revealedRows.value.length, 7);

  // 4) 启动失败：解锁演示，不把页面卡在遮罩里
  const failed = createGate(
    {
      definition: { key: "page.lru", category: "page", title: "LRU" },
      input: null,
      actionId: "act-bad",
    },
    {
      getStartedAiAction: () => null,
      startAiPlanAction: async () => null,
      submitAiPlanAction: async () => null,
    }
  );
  await failed.loadAction();
  assert.equal(failed.visible.value, false);
  assert.ok(failed.error.value.includes("轨迹已解锁"));
  assert.equal(failed.emitted[0].payload, false);

  // 模板契约：提交前轨迹以“封存窗口”锁定，提交后高亮目标步骤
  const simulation = read("src/views/learning/SimulationView.vue");
  for (const needle of [
    "predictionLocked",
    "is-prediction-locked",
    "prediction-lock",
    "轨迹已封存",
    "完整轨迹才会展开",
  ]) {
    assert.ok(simulation.includes(needle), `模拟页缺少预测遮罩契约：${needle}`);
  }
  assert.ok(local.descriptor.template.content.includes("is-target"));
  assert.ok(local.descriptor.template.content.includes("提交后揭示的真实轨迹"));

  console.log(
    "Prediction gate contracts passed: hidden future trace, local practice without evidence, AI submission key/evidence, aggregate reveal and failure unlock."
  );
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
