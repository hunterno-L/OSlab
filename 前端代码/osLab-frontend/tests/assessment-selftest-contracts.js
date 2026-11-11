// Run with: node tests/assessment-selftest-contracts.js
// 整组自测前端边界：交卷前不渲染解析/对错；草稿带版本；交卷键跨刷新稳定。
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");

const read = (file) =>
  fs.readFileSync(path.join(__dirname, "..", file), "utf8");

const view = read("src/views/learning/AssessmentRunView.vue");
const card = read("src/components/learning/SelfTestQuestionCard.vue");
const questions = read("src/views/learning/LearningQuestionsView.vue");
const answerCard = read("src/components/learning/QuestionAnswerCard.vue");
const api = read("src/api/assessment.ts");

// 1) 自测进行中的模板块不得包含解析/对错/正确答案
const start = view.indexOf('v-if="isSelfTest && run && !finished"');
const end = view.indexOf("整组自测：结果");
assert.ok(start > 0 && end > start, "自测进行中与结果模板必须分离");
const activeBlock = view.slice(start, end);
for (const forbidden of [
  "analysis",
  "misconception",
  "correct",
  "firstResult",
]) {
  assert.ok(
    !activeBlock.includes(forbidden),
    `自测进行中模板不得出现 ${forbidden}`
  );
}
assert.ok(
  activeBlock.includes("SelfTestQuestionCard"),
  "自测进行中使用无反馈的专用题卡"
);
assert.ok(
  !activeBlock.includes("<QuestionAnswerCard"),
  "自测进行中不得复用会即时判分的题卡"
);
assert.ok(
  card.includes("自测期间不显示对错与解析"),
  "自测题卡必须明确说明交卷前不判分"
);
assert.ok(
  !card.includes("item.analysis") && !card.includes("result.correct"),
  "自测题卡不得渲染结果字段"
);

// 2) 草稿保存带 rowVersion，冲突不覆盖
assert.ok(
  view.includes("saveAssessmentDrafts(runId.value"),
  "自测必须调用服务端草稿接口"
);
assert.ok(view.includes("rowVersion: version"), "草稿保存必须带当前版本");
assert.ok(view.includes("data.conflict"), "必须处理版本冲突");
assert.ok(
  view.includes("载入服务端草稿"),
  "版本冲突必须提供服务端草稿恢复入口"
);
assert.ok(
  view.includes("applyDrafts(data.serverDrafts)") ||
    view.includes("applyDrafts(serverDrafts)"),
  "冲突响应必须能载入服务端草稿"
);

// 3) 自动保存/切题/退出/交卷前完成最后一次保存
assert.ok(view.includes("scheduleSave"), "必须有防抖自动保存");
assert.ok(
  /goToQuestion[\s\S]{0,220}await flushSave\(\)/.test(view),
  "切题前必须完成最后一次保存"
);
assert.ok(
  /exitTemporarily[\s\S]{0,400}await flushSave\(\)/.test(view),
  "暂存退出前必须完成最后一次保存"
);
assert.ok(
  /finishRun[\s\S]{0,500}await flushSave\(\)/.test(view),
  "交卷前必须等待草稿保存成功"
);
assert.ok(
  /unanswered\.length/.test(view) && view.includes("标记不确定"),
  "交卷前列出未答题与标记题"
);

// 4) 交卷键按用户+run 持久化，双击/刷新重发使用同一键
assert.ok(
  view.includes("sessionStorage.getItem") &&
    view.includes("sessionStorage.setItem"),
  "交卷键必须持久化到 sessionStorage"
);
assert.ok(
  view.includes("oslab:selftest:submission:"),
  "交卷键必须按用户与 run 隔离"
);
assert.ok(
  /finishAssessment\(\{[\s\S]{0,400}submissionKey: submissionKey\(\)/.test(
    view
  ),
  "交卷必须携带稳定 submissionKey"
);

// 5) 结果页优先展示本组结果、错题/不确定入口与一个下一步
assert.ok(view.includes("wrongQuestionIds"), "结果必须包含错题入口");
assert.ok(view.includes("unsureQuestionIds"), "结果必须包含不确定题入口");
assert.ok(view.includes("PENDING_SELF_ASSESSMENT"), "综合题必须显示待自评");
assert.ok(
  view.includes("不计入有效分母") || view.includes("失效（不计分）"),
  "失效题必须单独说明"
);
assert.ok(
  view.includes("返回原单元") && view.includes("查看学习记录"),
  "结果页必须提供返回学习位置"
);

// 6) 练习入口可选择模式，且自测默认 5/10 可选
assert.ok(questions.includes("整组自测"), "练习入口必须有整组自测");
assert.ok(questions.includes('value="SELF_TEST"'), "自测模式值必须存在");
assert.ok(questions.includes("mode: runMode.value"), "start 必须携带模式");
assert.ok(questions.includes("runCount"), "必须保留 1–20 题数量上限");
assert.ok(questions.includes(':max="20"'), "自测数量上限仍为 20");

// 7) 逐题练习：提交前建立尝试上下文，帮助先落库再打开导师
assert.ok(
  api.includes("/api/learning/practice/attempts"),
  "必须提供普通尝试接口"
);
assert.ok(
  /ensurePracticeAttempt[\s\S]{0,240}createPracticeAttempt/.test(answerCard),
  "题卡提交前必须建立尝试上下文"
);
assert.ok(
  /answerLearningQuestion\({[\s\S]{0,320}attemptContextId: attempt\.attemptContextId/.test(
    answerCard
  ),
  "普通作答必须携带 attemptContextId"
);
assert.ok(
  /recordPracticeHelp\(attempt\.attemptContextId[\s\S]{0,900}openAssessmentTutor/.test(
    answerCard
  ),
  "提示必须先记录帮助再打开导师"
);
assert.ok(
  answerCard.includes("查看详细解析") && answerCard.includes("detailedOpen"),
  "逐题反馈必须收敛为简短结果 + 可展开解析"
);
assert.ok(
  !/每答一题[\s\S]{0,20}openAssessmentTutor/.test(answerCard),
  "提交答案不得自动打开导师"
);

// 8) 结果读取不触发新学习事件；完成通知带课程与 run
assert.ok(
  view.includes("notifyLearningUpdated") &&
    view.includes("ASSESSMENT_FINISHED"),
  "交卷成功必须发出 LearningUpdated"
);
assert.ok(
  !/loadResult[\s\S]{0,260}finishAssessment/.test(view),
  "读取结果不得再次交卷"
);

console.log(
  "Self-test contracts passed: no pre-finish answer leak in UI, versioned drafts, stable submission key, result/follow-up entries, practice attempt binding and help-before-hint."
);
