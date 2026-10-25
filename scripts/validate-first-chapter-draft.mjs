#!/usr/bin/env node
// Read-only validation of the staged first-chapter unit draft. No database/network.
import fs from "fs";
import path from "path";

const root = path.resolve(".");
const draftPath = path.join(
  root,
  "artifacts/course-first-chapter/20261020/units-first-chapter.draft.json"
);
const knowledgePath = path.join(
  root,
  "artifacts/content-import/20261019-draft-import/knowledge-original/knowledge.json"
);
const questionsPath = path.join(
  root,
  "artifacts/content-import/20261019-draft-import/questions-original/questions.json"
);
const sectionsPath = path.join(
  root,
  "content/operating-systems/wangdao-2025/catalog/sections.json"
);

const draft = JSON.parse(fs.readFileSync(draftPath, "utf8"));
const knowledge = JSON.parse(fs.readFileSync(knowledgePath, "utf8"));
const questions = JSON.parse(fs.readFileSync(questionsPath, "utf8")).questions;
const sections = new Set(
  JSON.parse(fs.readFileSync(sectionsPath, "utf8")).sections.map(
    (section) => section.sectionCode
  )
);
const chunks = new Map(knowledge.chunks.map((chunk) => [chunk.chunkCode, chunk]));
const questionsByCode = new Map(
  questions.map((question) => [question.questionCode, question])
);
const nodeCodes = new Set(knowledge.nodes.map((node) => node.nodeCode));
const problems = [];
const check = (condition, at, message) => {
  if (!condition) problems.push(`${at}: ${message}`);
};
const codePattern = /^[a-z][a-z0-9._-]*$/;
const nonempty = (value) => typeof value === "string" && value.trim().length > 0;

check(draft.schemaVersion === "oslab-authoring/1.0", "package", "schemaVersion 错误");
check(draft.packageType === "UNITS", "package", "packageType 错误");
check(draft.courseCode === "course.operating-systems", "package", "courseCode 错误");
check(Array.isArray(draft.units) && draft.units.length === 10, "package", "应包含十个单元");

const unitCodes = new Set();
const activityCodes = new Set();
const mappedQuestions = new Set();
let requiredQuestionActivities = 0;
let coveredChunks = new Set();
let totalActivities = 0;

for (const unit of draft.units) {
  const at = unit.unitCode;
  check(nonempty(unit.unitCode) && codePattern.test(unit.unitCode), at, "unitCode 格式错误");
  check(!unitCodes.has(unit.unitCode), at, "unitCode 重复");
  unitCodes.add(unit.unitCode);
  check(sections.has(unit.sectionCode), at, "sectionCode 未登记");
  check(nonempty(unit.title), at, "缺少标题");
  check(Number.isInteger(unit.sortOrder) && unit.sortOrder > 0, at, "sortOrder 错误");
  check(
    Number.isFinite(unit.estimatedMinutes) && unit.estimatedMinutes > 0,
    at,
    "estimatedMinutes 错误"
  );
  check(Array.isArray(unit.objectives) && unit.objectives.length >= 2, at, "目标至少两条");
  check(unit.status === "DRAFT", at, "编排草案应为 DRAFT");

  check(Array.isArray(unit.nodeMappings) && unit.nodeMappings.length > 0, at, "缺少节点映射");
  const nodeSeen = new Set();
  for (const mapping of unit.nodeMappings) {
    check(nodeCodes.has(mapping.nodeCode), at, `未知 nodeCode ${mapping.nodeCode}`);
    check(!nodeSeen.has(mapping.nodeCode), at, `nodeCode 重复 ${mapping.nodeCode}`);
    nodeSeen.add(mapping.nodeCode);
    check(
      ["TEACHES", "PREREQUISITE", "EXTENSION"].includes(mapping.role),
      at,
      `节点角色错误 ${mapping.role}`
    );
  }

  check(Array.isArray(unit.questionMappings), at, "缺少 questionMappings");
  const questionSeen = new Set();
  for (const mapping of unit.questionMappings) {
    check(
      questionsByCode.has(mapping.questionCode),
      at,
      `未知 questionCode ${mapping.questionCode}`
    );
    check(
      !questionSeen.has(mapping.questionCode),
      at,
      `questionCode 重复 ${mapping.questionCode}`
    );
    questionSeen.add(mapping.questionCode);
    mappedQuestions.add(mapping.questionCode);
    check(
      ["PRACTICE", "ASSESSMENT", "REVIEW"].includes(mapping.usage),
      at,
      `usage 错误 ${mapping.usage}`
    );
  }

  check(Array.isArray(unit.activities) && unit.activities.length > 0, at, "缺少活动");
  const sortOrders = new Set();
  const unitActivityCodes = new Set();
  for (const activity of unit.activities) {
    totalActivities += 1;
    const activityAt = activity.activityCode || at;
    check(
      nonempty(activity.activityCode) && codePattern.test(activity.activityCode),
      activityAt,
      "activityCode 格式错误"
    );
    check(!activityCodes.has(activity.activityCode), activityAt, "activityCode 跨单元重复");
    check(!unitActivityCodes.has(activity.activityCode), activityAt, "activityCode 单元内重复");
    activityCodes.add(activity.activityCode);
    unitActivityCodes.add(activity.activityCode);
    check(nonempty(activity.title), activityAt, "缺少活动标题");
    check(typeof activity.required === "boolean", activityAt, "required 类型错误");
    check(
      Number.isInteger(activity.sortOrder) && activity.sortOrder > 0,
      activityAt,
      "sortOrder 错误"
    );
    check(!sortOrders.has(activity.sortOrder), activityAt, "sortOrder 重复");
    sortOrders.add(activity.sortOrder);
    const reference = activity.reference || {};
    if (["READING", "WORKED_EXAMPLE", "SUMMARY"].includes(activity.activityType)) {
      check(reference.kind === "CHUNK", activityAt, "阅读活动必须引用 CHUNK");
      check(activity.completionRule === "CONFIRM", activityAt, "阅读活动完成规则错误");
      const chunk = chunks.get(reference.code);
      check(Boolean(chunk), activityAt, `未知 chunkCode ${reference.code}`);
      if (chunk) coveredChunks.add(chunk.chunkCode);
    } else if (["PRACTICE", "ASSESSMENT"].includes(activity.activityType)) {
      check(reference.kind === "QUESTION", activityAt, "题目活动必须引用 QUESTION");
      check(["SUBMIT", "PASS"].includes(activity.completionRule), activityAt, "题目活动完成规则错误");
      check(
        questionsByCode.has(reference.code),
        activityAt,
        `未知 questionCode ${reference.code}`
      );
      check(questionSeen.has(reference.code), activityAt, "题目活动未进入 questionMappings");
      if (activity.required) requiredQuestionActivities += 1;
    } else {
      check(false, activityAt, `未知活动类型 ${activity.activityType}`);
    }
  }
}

// A 六题修订草案：只校验文件契约与来源登记，不执行导入。
const revisionPath = path.join(
  root,
  "artifacts/course-first-chapter/20261020/a-six-question-revisions.draft.json"
);
const revision = JSON.parse(fs.readFileSync(revisionPath, "utf8"));
const sourceRegistry = JSON.parse(
  fs.readFileSync(
    path.join(root, "content/operating-systems/wangdao-2025/catalog/sources.json"),
    "utf8"
  )
);
const sourceCodes = new Set(sourceRegistry.map((item) => item.sourceCode));
check(revision.packageType === "QUESTIONS", "revisions", "修订包类型错误");
check(Array.isArray(revision.questions) && revision.questions.length === 6, "revisions", "应有六题");
for (const question of revision.questions) {
  const at = question.questionCode;
  check(questionsByCode.has(at), "revisions", `未知题号 ${at}`);
  check(sourceCodes.has(question.sourceRef?.sourceCode), at, "来源未登记");
  check(
    ["TEXTBOOK", "ADAPTED", "EXTERNAL_QUESTION"].includes(question.sourceRef?.origin),
    at,
    "来源类型错误"
  );
  check(sections.has(question.sectionCode), at, "sectionCode 未登记");
  check(Array.isArray(question.options) && question.options.length >= 2, at, "选项不足");
  check(
    question.options.some((option) => option.key === question.answer),
    at,
    "答案不在选项中"
  );
  check(Array.isArray(question.nodeMappings) && question.nodeMappings.length > 0, at, "缺少节点映射");
  for (const mapping of question.nodeMappings) {
    check(nodeCodes.has(mapping.nodeCode), at, `未知 nodeCode ${mapping.nodeCode}`);
  }
  for (const basis of question.basisSourceRefs || []) {
    check(sourceCodes.has(basis.sourceCode), at, "basisSourceRefs 来源未登记");
  }
}

const pending = new Set([
  "q.wd25.s1_3.005",
  "q.wd25.s1_3.016",
  "q.wd25.s1_2.009",
  "q.ext.s1_2.001",
  "q.ext.s1_3.001",
  "q.ext.s1_3.003",
]);
for (const code of pending) {
  check(!mappedQuestions.has(code), code, "争议题不应进入正式映射");
}
check(mappedQuestions.size === questions.length - pending.size, "mapped questions", "映射题数不符");
check(coveredChunks.size === chunks.size, "chunks", `教学块覆盖 ${coveredChunks.size}/${chunks.size}`);

if (problems.length) {
  console.error(`第一章编排草案检查失败（${problems.length} 项）：`);
  for (const problem of problems) console.error(`- ${problem}`);
  process.exitCode = 1;
} else {
  console.log(
    `第一章编排草案检查通过：${draft.units.length} 单元、${totalActivities} 活动、${requiredQuestionActivities} 道必做题目活动、` +
      `${mappedQuestions.size} 道正式映射题（${pending.size} 道争议题待定）、${coveredChunks.size}/${chunks.size} 教学块。`
  );
  console.log("仅验证文件契约与引用；未导入、未发布、未写数据库。");
}
