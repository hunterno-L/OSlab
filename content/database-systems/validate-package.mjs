#!/usr/bin/env node
// 数据库课程包本地校验：官方 scripts/validate-course-content.mjs 目前把课程硬编码为
// course.operating-systems，无法直接校验本包；这里复用同一 schema 规则并额外用
// 属性闭包法复核 6 道题的答案与候选键最小性。只读文件，不写数据库。
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(fileURLToPath(import.meta.url));
const batchFile = process.argv[2] || "batches/fd-keys-001.json";
const problems = [];
const check = (condition, location, message) => {
  if (!condition) problems.push(`${location}: ${message}`);
};
const nonempty = (value) =>
  typeof value === "string" && value.trim().length > 0;
const codePattern = /^[a-z][a-z0-9._-]*$/;
const readJson = async (relative) =>
  JSON.parse(
    (await readFile(path.join(root, relative), "utf8")).replace(/^\uFEFF/, "")
  );

const relations = new Set([
  "REQUIRES",
  "CONTAINS",
  "CONTRASTS_WITH",
  "BASED_ON",
  "IMPLEMENTS",
  "RELATED_TO",
]);
const chunkTypes = new Set([
  "DEFINITION",
  "EXPLANATION",
  "ALGORITHM",
  "WORKED_EXAMPLE",
  "FIGURE",
  "SUMMARY",
]);

function closure(attributes, dependencies) {
  const result = new Set(attributes);
  let changed = true;
  while (changed) {
    changed = false;
    for (const dependency of dependencies) {
      if (dependency.from.every((item) => result.has(item))) {
        for (const item of dependency.to) {
          if (!result.has(item)) {
            result.add(item);
            changed = true;
          }
        }
      }
    }
  }
  return [...result].sort();
}

function subsets(items) {
  const out = [];
  for (let mask = 1; mask < 1 << items.length; mask += 1) {
    out.push(items.filter((_, index) => mask & (1 << index)));
  }
  return out;
}

function candidateKeys(attributes, dependencies) {
  const all = [...attributes].sort().join(",");
  const supers = subsets(attributes).filter(
    (subset) => closure(subset, dependencies).join(",") === all
  );
  return supers
    .filter(
      (subset) =>
        !supers.some(
          (other) =>
            other.length < subset.length &&
            other.every((item) => subset.includes(item))
        )
    )
    .map((subset) => subset.join(""));
}

function verifyQuestionAnswers(questions) {
  const fds = (pairs) =>
    pairs.map(([from, to]) => ({ from: [from], to: [to] }));
  const closureCases = [
    {
      code: "q.db.fd.closure-basic",
      fds: fds([["A", "B"], ["B", "C"]]),
      attributes: ["A", "B", "C", "D"],
      input: ["A"],
      expected: ["A", "B", "C"],
    },
    {
      code: "q.db.fd.closure-composite",
      fds: [
        { from: ["A", "B"], to: ["C"] },
        { from: ["C"], to: ["D"] },
        { from: ["D"], to: ["E"] },
      ],
      attributes: ["A", "B", "C", "D", "E"],
      input: ["A", "B"],
      expected: ["A", "B", "C", "D", "E"],
    },
  ];
  for (const test of closureCases) {
    const question = questions.find((item) => item.questionCode === test.code);
    check(Boolean(question), test.code, "题目不存在");
    if (!question) continue;
    const actual = closure(test.input, test.fds);
    check(
      actual.join(",") === test.expected.join(","),
      test.code,
      `闭包复算不符：${actual.join(",")}`
    );
    const expectedKey = question.answer;
    const matched = (question.options || []).find(
      (option) =>
        option.key === expectedKey &&
        option.value.replace(/[^A-Z,]/g, "") === test.expected.join(",")
    );
    check(Boolean(matched), test.code, "答案选项与闭包结果不一致");
  }

  const keyCases = [
    {
      code: "q.db.fd.candidate-minimal",
      attributes: ["A", "B", "C"],
      fds: fds([["A", "B"], ["B", "C"]]),
      expected: ["A"],
      answer: "A",
    },
    {
      code: "q.db.fd.candidate-count",
      attributes: ["A", "B", "C"],
      fds: [
        { from: ["A"], to: ["B"] },
        { from: ["B"], to: ["A"] },
        { from: ["A"], to: ["C"] },
      ],
      expected: ["A", "B"],
      answer: ["2"],
    },
  ];
  for (const test of keyCases) {
    const question = questions.find((item) => item.questionCode === test.code);
    check(Boolean(question), test.code, "题目不存在");
    if (!question) continue;
    const actual = candidateKeys(test.attributes, test.fds);
    check(
      actual.join(",") === test.expected.join(","),
      test.code,
      `候选键复算不符：${actual.join(",")}`
    );
    if (Array.isArray(test.answer)) {
      check(
        Array.isArray(question.answer) &&
          question.answer.join(",") === test.answer.join(","),
        test.code,
        "填空答案与候选键数量不一致"
      );
    }
  }

  const superkeyQuestion = questions.find(
    (item) => item.questionCode === "q.db.fd.superkey-not-candidate"
  );
  check(Boolean(superkeyQuestion), "q.db.fd.superkey-not-candidate", "题目不存在");
  if (superkeyQuestion) {
    const candidates = fds([["A", "B"], ["B", "C"]]);
    const attributes = ["A", "B", "C"];
    const all = attributes.join(",");
    const isSuperkey = (set) => closure(set, candidates).join(",") === all;
    check(
      isSuperkey(["A", "B"]) && !isSuperkey(["B"]) && !isSuperkey(["C"]),
      "q.db.fd.superkey-not-candidate",
      "{A,B} 必须是超键且 B/C 不是超键"
    );
    const expectedOption = (superkeyQuestion.options || []).find(
      (option) => option.key === superkeyQuestion.answer
    );
    check(
      expectedOption?.value === "{A,B}",
      "q.db.fd.superkey-not-candidate",
      "答案应指向 {A,B}"
    );
  }
  const judge = questions.find(
    (item) => item.questionCode === "q.db.fd.superkey-judge"
  );
  check(judge?.answer === "true", "q.db.fd.superkey-judge", "超键定义判断题答案应为 true");
}

async function run() {
  const batch = await readJson(batchFile);
  check(batch.schemaVersion === "oslab-authoring/1.0", batchFile, "协议版本错误");
  check(batch.courseCode === "course.database-systems", batchFile, "课程编号错误");

  const sources = await readJson("catalog/sources.json");
  const sections = (await readJson("catalog/sections.json")).sections;
  const catalogNodes = (await readJson("catalog/nodes.json")).nodes;
  const demos = (await readJson("catalog/demos.json")).resources;
  const sourceCodes = new Set(sources.map((item) => item.sourceCode));
  const sectionCodes = new Set(sections.map((item) => item.sectionCode));
  const nodeCodes = new Set(catalogNodes.map((item) => item.nodeCode));
  check(sources.length === 1, "sources", "数据库课程包应有 1 个来源");
  check(sections.length === 2, "sections", "数据库课程包应有 2 个目录项");
  check(catalogNodes.length === 3, "nodes", "数据库课程包应有 3 个知识点");
  check(demos.length === 0, "demos", "数据库课程包不使用演示资源");

  const sourceRef = (ref, location) => {
    check(ref && sourceCodes.has(ref.sourceCode), location, "sourceCode 未登记");
    check(sectionCodes.has(ref?.sectionCode), location, "sectionCode 未登记");
    check(nonempty(ref?.locator), location, "缺少定位说明");
    check(
      ref?.pageStart === null && ref?.pageEnd === null,
      location,
      "自编讲义页码应为 null"
    );
  };

  const knowledge = await readJson(batch.knowledgePackage);
  const questionsPackage = await readJson(batch.questionPackage);
  const unitsPackage = await readJson(batch.unitPackage);
  for (const pack of [knowledge, questionsPackage, unitsPackage]) {
    check(pack.schemaVersion === "oslab-authoring/1.0", pack.batchCode, "协议版本错误");
    check(pack.courseCode === "course.database-systems", pack.batchCode, "课程编号错误");
    check(pack.batchCode === batch.batchCode, pack.batchCode, "批次号不一致");
  }

  check(knowledge.packageType === "KNOWLEDGE", knowledge.batchCode, "需要 KNOWLEDGE 包");
  check((knowledge.nodes || []).length === 3, "knowledge.nodes", "知识点应为 3 个");
  check((knowledge.chunks || []).length === 3, "knowledge.chunks", "讲义应为 3 段");
  for (const node of knowledge.nodes || []) {
    check(codePattern.test(node.nodeCode), node.nodeCode, "节点编号错误");
    check(nonempty(node.name) && nonempty(node.description), node.nodeCode, "节点名称或简介缺失");
    check(sectionCodes.has(node.homeSectionCode), node.nodeCode, "节点目录不存在");
    check(Array.isArray(node.sourceRefs) && node.sourceRefs.length > 0, node.nodeCode, "缺少出处");
    for (const ref of node.sourceRefs || []) sourceRef(ref, node.nodeCode);
  }
  for (const chunk of knowledge.chunks || []) {
    check(codePattern.test(chunk.chunkCode), chunk.chunkCode, "讲义编号错误");
    check(chunkTypes.has(chunk.chunkType), chunk.chunkCode, "讲义类型错误");
    check(sectionCodes.has(chunk.sectionCode), chunk.chunkCode, "讲义目录不存在");
    check(nonempty(chunk.title) && nonempty(chunk.contentMd), chunk.chunkCode, "讲义标题或正文缺失");
    check(
      Array.isArray(chunk.nodeCodes) && chunk.nodeCodes.length > 0,
      chunk.chunkCode,
      "讲义必须关联知识点"
    );
    for (const nodeCode of chunk.nodeCodes || []) {
      check(nodeCodes.has(nodeCode), chunk.chunkCode, `节点未登记：${nodeCode}`);
    }
    sourceRef(chunk.sourceRef, chunk.chunkCode);
  }
  for (const relation of knowledge.relations || []) {
    check(nodeCodes.has(relation.fromNodeCode), relation.fromNodeCode, "关系起点未登记");
    check(nodeCodes.has(relation.toNodeCode), relation.toNodeCode, "关系终点未登记");
    check(relations.has(relation.relationType), relation.fromNodeCode, "关系类型错误");
    check(nonempty(relation.reason), relation.fromNodeCode, "关系缺少依据");
    sourceRef(relation.sourceRef, relation.fromNodeCode);
  }

  const questions = questionsPackage.questions || [];
  check(questionsPackage.packageType === "QUESTIONS", questionsPackage.batchCode, "需要 QUESTIONS 包");
  check(questions.length === 6, "questions", "客观题应为 6 道");
  const questionCodes = new Set(questions.map((item) => item.questionCode));
  for (const question of questions) {
    check(codePattern.test(question.questionCode), question.questionCode, "题号错误");
    check(nonempty(question.title) && nonempty(question.content), question.questionCode, "题干缺失");
    check(nonempty(question.analysis), question.questionCode, "解析缺失");
    check(sectionCodes.has(question.sectionCode), question.questionCode, "题目目录不存在");
    check(
      Array.isArray(question.nodeMappings) && question.nodeMappings.length > 0,
      question.questionCode,
      "缺少知识点映射"
    );
    check(
      question.nodeMappings.some((item) => item.role === "PRIMARY"),
      question.questionCode,
      "缺少 PRIMARY 映射"
    );
    for (const mapping of question.nodeMappings) {
      check(nodeCodes.has(mapping.nodeCode), question.questionCode, `节点未登记：${mapping.nodeCode}`);
      check(mapping.weight > 0 && mapping.weight <= 1, question.questionCode, "权重越界");
    }
    sourceRef(question.sourceRef, question.questionCode);
  }
  verifyQuestionAnswers(questions);

  check(unitsPackage.packageType === "UNITS", unitsPackage.batchCode, "需要 UNITS 包");
  const units = unitsPackage.units || [];
  check(units.length === 1, "units", "应有 1 个学习单元");
  const chunkCodes = new Set((knowledge.chunks || []).map((item) => item.chunkCode));
  const activityCodes = new Set();
  for (const unit of units) {
    check(unit.unitCode === "unit.db.fd-keys", unit.unitCode, "单元编号错误");
    check(sectionCodes.has(unit.sectionCode), unit.unitCode, "单元目录不存在");
    check(
      Array.isArray(unit.nodeMappings) && unit.nodeMappings.length === 3,
      unit.unitCode,
      "单元应覆盖 3 个知识点"
    );
    for (const mapping of unit.nodeMappings || []) {
      check(nodeCodes.has(mapping.nodeCode), unit.unitCode, `节点未登记：${mapping.nodeCode}`);
    }
    const activities = unit.activities || [];
    check(activities.length === 9, unit.unitCode, "单元应有 9 个活动");
    for (const activity of activities) {
      check(codePattern.test(activity.activityCode), activity.activityCode, "活动编号错误");
      check(!activityCodes.has(activity.activityCode), activity.activityCode, "活动编号重复");
      activityCodes.add(activity.activityCode);
      if (["READING", "WORKED_EXAMPLE", "SUMMARY"].includes(activity.activityType)) {
        check(chunkCodes.has(activity.reference?.code), activity.activityCode, "讲义引用不存在");
      } else {
        check(questionCodes.has(activity.reference?.code), activity.activityCode, "题目引用不存在");
      }
    }
  }

  const mapping = await readJson(batch.misconceptionMapping);
  const skills = mapping.courses?.[0]?.skills || [];
  check(skills.length === 3, "misconceptions", "应有 3 个技能误区映射");
  for (const skill of skills) {
    check(nodeCodes.has(skill.skillCode), skill.skillCode, "技能不在知识点中");
    check((skill.misconceptions || []).length >= 2, skill.skillCode, "每个技能至少 2 个误区");
    check((skill.verification || []).length >= 1, skill.skillCode, "缺少核验活动");
    for (const item of skill.verification || []) {
      check(questionCodes.has(item.questionCode), skill.skillCode, "核验题号不存在");
      check(activityCodes.has(item.activityCode), skill.skillCode, "核验活动不存在");
    }
  }

  if (problems.length) {
    console.error(`数据库课程包检查失败（${problems.length} 项）：`);
    for (const problem of problems) console.error(`- ${problem}`);
    process.exitCode = 1;
    return;
  }
  console.log(
    `数据库课程包检查通过：3 个知识点、3 段讲义、${questions.length} 道题、1 个单元、${activityCodes.size} 个活动；属性闭包与候选键最小性复算一致。`
  );
}

run().catch((error) => {
  console.error(`无法完成数据库课程包检查：${error.message}`);
  process.exitCode = 1;
});
