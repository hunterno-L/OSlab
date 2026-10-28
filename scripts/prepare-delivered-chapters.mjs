import fs from "node:fs";
import path from "node:path";

const [knowledgeRoot, questionRoot, outputRoot] = process.argv.slice(2);
if (!knowledgeRoot || !questionRoot || !outputRoot || fs.existsSync(outputRoot)) {
  throw new Error("用法: node prepare-delivered-chapters.mjs J目录 A目录 新输出目录");
}
const read = (file) => JSON.parse(fs.readFileSync(file, "utf8"));
const save = (name, value) => {
  const target = path.join(outputRoot, name);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, `${JSON.stringify(value, null, 2)}\n`, "utf8");
};
const chapterLabels = ["第二章", "第三章", "第四章", "第五章"];
const locate = (root, prefix, label) => {
  const matches = fs.readdirSync(root).filter((name) => name.startsWith(prefix) && name.includes(label));
  if (matches.length !== 1) throw new Error(`无法唯一定位 ${prefix} ${label}`);
  return path.join(root, matches[0]);
};
const entries = chapterLabels.map((label) => {
  const jPath = locate(knowledgeRoot, "J-", label);
  const aPath = locate(questionRoot, "A-", label);
  return { label, jPath, aPath, knowledge: read(path.join(jPath, "knowledge.json")),
    plan: read(path.join(jPath, "chapter-plan.json")),
    objective: read(path.join(aPath, "questions.json")),
    application: read(path.join(aPath, "application-questions.json")) };
});

const registry = read(path.join(questionRoot, "shared-登记补丁", "nodes.json")).nodes;
const oldKnowledge = read("content/operating-systems/wangdao-2025/knowledge/j-memory-page-replacement-001.json");
const firstKnowledge = read("artifacts/content-import/20261019-draft-import/knowledge-original/knowledge.json");
const nodeIndex = new Map(entries.flatMap((entry) => entry.knowledge.nodes).map((node) => [node.nodeCode, node]));
const firstNodes = new Set(firstKnowledge.nodes.map((node) => node.nodeCode));
const allChunks = entries.flatMap((entry) => entry.knowledge.chunks);
const referenced = new Set(entries.flatMap((entry) => [
  ...entry.knowledge.chunks.flatMap((chunk) => chunk.nodeCodes),
  ...entry.knowledge.relations.flatMap((relation) => [relation.fromNodeCode, relation.toNodeCode]),
  ...entry.plan.units.flatMap((unit) => unit.nodeCodes),
  ...[...entry.objective.questions, ...entry.application.questions].flatMap((question) => question.nodeMappings.map((mapping) => mapping.nodeCode)),
]));
const reusedNodes = [];
for (const code of referenced) {
  if (nodeIndex.has(code) || firstNodes.has(code)) continue;
  const registered = registry.find((node) => node.nodeCode === code);
  const original = oldKnowledge.nodes.find((node) => node.nodeCode === code);
  const chunk = allChunks.find((item) => item.nodeCodes.includes(code));
  if (!registered || (!original && !chunk)) throw new Error(`缺少复用节点定义: ${code}`);
  const node = original || {
    nodeCode: code, name: registered.name, nodeType: registered.nodeType, layer: registered.layer,
    description: chunk.contentMd.replace(/[*#`]/g, "").split(/\n|。/).find((text) => text.trim())?.trim().slice(0, 300) || `${registered.name}：见关联课程内容。`,
    aliases: registered.aliases || [], homeSectionCode: registered.homeSectionCode,
    sourceRefs: [chunk.sourceRef],
  };
  reusedNodes.push(node);
  nodeIndex.set(code, node);
}
const sources = read(path.join(questionRoot, "shared-登记补丁", "sources.json"));
const unapprovedSources = new Set(sources.filter((source) => source.authorized === false).map((source) => source.sourceCode));
save("sources.json", sources);
save("reused-nodes.json", { schemaVersion: "oslab-authoring/1.0", packageType: "KNOWLEDGE",
  batchCode: "batch.chapters.reused-nodes.20261027", contributor: "L", courseCode: "course.operating-systems",
  nodes: reusedNodes, chunks: [], relations: [] });

const sections = new Map();
for (const entry of entries) for (const section of entry.plan.sections) {
  const old = sections.get(section.sectionCode);
  if (old && (old.parentSectionCode !== section.parentSectionCode || old.title !== section.title)) {
    throw new Error(`目录定义冲突: ${section.sectionCode}`);
  }
  sections.set(section.sectionCode, section);
}
save("sections.json", [...sections.values()]);

for (const entry of entries) {
  const chapterDir = entry.plan.chapterCode.replace("sec.", "");
  fs.cpSync(entry.jPath, path.join(outputRoot, "original", `J-${chapterDir}`), { recursive: true });
  fs.cpSync(entry.aPath, path.join(outputRoot, "original", `A-${chapterDir}`), { recursive: true });
  const applications = entry.application.questions.map((question) => {
    const prompts = question.parts.filter((part) => part.promptMd && !question.contentMd.includes(part.promptMd));
    const content = question.contentMd + (prompts.length ? `\n\n${prompts.map((part, index) => `${index + 1}. ${part.promptMd}`).join("\n")}` : "");
    const analysis = question.parts.map((part, index) => `### ${question.parts.length > 1 ? `第 ${index + 1} 问` : "参考答案"}\n\n${part.answerMd}`).join("\n\n");
    return { questionCode: question.questionCode, title: question.title, content, type: "COMPREHENSIVE", options: [],
      answer: analysis, analysis, misconception: "", difficulty: question.difficulty, sectionCode: question.sectionCode,
      sourceRef: question.sourceRef, answerSourceRef: question.answerSourceRef, nodeMappings: question.nodeMappings,
      usageSuggestions: question.usageSuggestions || [], authoringParts: question.parts };
  });
  const applicationPack = { ...entry.objective, batchCode: entry.application.batchCode, questions: applications };
  save(`${chapterDir}/knowledge.json`, entry.knowledge);
  save(`${chapterDir}/questions.json`, entry.objective);
  save(`${chapterDir}/application-questions.json`, applicationPack);

  const questions = [...entry.objective.questions, ...applications].filter((question) => !unapprovedSources.has(question.sourceRef.sourceCode));
  const chunksByCode = new Map(entry.knowledge.chunks.map((chunk) => [chunk.chunkCode, chunk]));
  const units = entry.plan.units.map((unit) => {
    const unitNodes = new Set(unit.nodeCodes);
    const matched = questions.filter((question) => question.nodeMappings.some((mapping) => mapping.role === "PRIMARY" && unitNodes.has(mapping.nodeCode)));
    const objectiveQuestions = matched.filter((question) => question.type !== "COMPREHENSIVE");
    const activityPrefix = `act.${unit.unitCode.replace(/^unit\./, "")}`;
    const activities = unit.readingChunkCodes.map((code, index) => {
      const chunk = chunksByCode.get(code);
      if (!chunk) throw new Error(`单元引用未知教学块: ${unit.unitCode} / ${code}`);
      return { activityCode: `${activityPrefix}.read.${index + 1}`, title: chunk.title,
        activityType: chunk.chunkType === "SUMMARY" ? "SUMMARY" : ["FIGURE", "WORKED_EXAMPLE"].includes(chunk.chunkType) ? "WORKED_EXAMPLE" : "READING",
        sortOrder: index + 1, required: true, reference: { kind: "CHUNK", code }, config: {}, completionRule: "CONFIRM" };
    });
    for (const [index, question] of objectiveQuestions.slice(0, 2).entries()) activities.push({
      activityCode: `${activityPrefix}.practice.${index + 1}`, title: `练习：${question.title}`,
      activityType: "PRACTICE", sortOrder: activities.length + 1, required: true,
      reference: { kind: "QUESTION", code: question.questionCode }, config: {}, completionRule: "SUBMIT" });
    return { unitCode: unit.unitCode, sectionCode: unit.sectionCode, title: unit.title, objectives: unit.objectives,
      sortOrder: unit.sortOrder, estimatedMinutes: unit.estimatedMinutes,
      nodeMappings: unit.nodeCodes.map((nodeCode) => ({ nodeCode, role: "TEACHES" })),
      questionMappings: matched.map((question) => ({ questionCode: question.questionCode, usage: "PRACTICE" })),
      activities, prerequisiteUnitCodes: unit.prerequisiteUnitCodes, demoResourceCodes: unit.demoResourceCodes,
      practiceGuidance: unit.practiceGuidance };
  });
  save(`${chapterDir}/units.json`, { schemaVersion: "oslab-authoring/1.0", packageType: "UNITS",
    batchCode: `batch.units.${chapterDir}.20261027`, contributor: "J/L", courseCode: "course.operating-systems", units });
}
save("manifest.json", { createdAt: new Date().toISOString(), knowledgeRoot, questionRoot, reusedNodes: reusedNodes.map((node) => node.nodeCode),
  unapprovedSources: [...unapprovedSources], chapters: entries.map((entry) => ({ chapter: entry.label, code: entry.plan.chapterCode,
    nodes: entry.knowledge.nodes.length, chunks: entry.knowledge.chunks.length, relations: entry.knowledge.relations.length,
    units: entry.plan.units.length, objective: entry.objective.questions.length, application: entry.application.questions.length })) });
console.log(`已准备 ${entries.length} 章，${reusedNodes.length} 个复用节点：${outputRoot}`);
