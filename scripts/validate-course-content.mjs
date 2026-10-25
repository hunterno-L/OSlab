#!/usr/bin/env node
// Read-only checks for the shared authoring files. No database or network calls.
import { readFile, readdir, access } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const defaultRoot = fileURLToPath(
  new URL("../content/operating-systems/wangdao-2025/", import.meta.url)
);
const batchFile = process.argv[2] === "--batch" ? process.argv[3] : null;
const root = path.resolve(batchFile ? defaultRoot : process.argv[2] || defaultRoot);
const problems = [];
const codePattern = /^[a-z][a-z0-9._-]*$/;
const nodeTypes = new Set(["CONCEPT", "ALGORITHM", "MECHANISM", "IMPLEMENTATION"]);
const relationTypes = new Set([
  "REQUIRES", "CONTAINS", "CONTRASTS_WITH", "BASED_ON", "IMPLEMENTS", "RELATED_TO"
]);
const origins = new Set([
  "TEXTBOOK", "ADAPTED", "SELF_AUTHORED", "SELF_AUTHORED_SAMPLE",
  "EXTERNAL_QUESTION", "EXTERNAL_DEMO", "TOC"
]);
function check(condition, location, message) {
  if (!condition) problems.push(`${location}: ${message}`);
}
function nonempty(value) {
  return typeof value === "string" && value.trim().length > 0;
}
function list(value, location) {
  check(Array.isArray(value), location, "需要数组");
  return Array.isArray(value) ? value : [];
}
function code(value, location) {
  check(typeof value === "string" && codePattern.test(value), location, "编号格式错误");
}
function unique(items, key, location) {
  const seen = new Set();
  for (const item of items) {
    const value = typeof item === "string" ? item : item?.[key];
    check(value != null && !seen.has(value), location, `缺少或重复 ${key}: ${value}`);
    seen.add(value);
  }
}
async function json(relative) {
  const raw = await readFile(path.join(root, relative), "utf8");
  return JSON.parse(raw.replace(/^\uFEFF/, ""));
}
async function optionalPackages(directory) {
  let entries;
  try {
    entries = await readdir(path.join(root, directory), { withFileTypes: true });
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }
  return entries.filter((entry) => entry.isFile() && entry.name.endsWith(".json"))
    .map((entry) => `${directory}/${entry.name}`).sort();
}
function acyclic(edges, location) {
  const adjacency = new Map();
  for (const [from, to] of edges) {
    if (!adjacency.has(from)) adjacency.set(from, []);
    adjacency.get(from).push(to);
  }
  const active = new Set();
  const done = new Set();
  function visit(current) {
    if (active.has(current)) {
      problems.push(`${location}: 出现环，涉及 ${current}`);
      return;
    }
    if (done.has(current)) return;
    active.add(current);
    for (const next of adjacency.get(current) || []) visit(next);
    active.delete(current);
    done.add(current);
  }
  for (const from of adjacency.keys()) visit(from);
}

try {
  const sources = list(await json("catalog/sources.json"), "sources");
  const sections = list((await json("catalog/sections.json")).sections, "sections");
  const registryNodes = list((await json("catalog/nodes.json")).nodes, "nodes");
  const resources = list((await json("catalog/demos.json")).resources, "demos");
  unique(sources, "sourceCode", "sources");
  unique(sections, "sectionCode", "sections");
  unique(registryNodes, "nodeCode", "nodes");
  unique(resources, "resourceCode", "demos");
  const sourceCodes = new Set(sources.map((item) => item.sourceCode));
  const sectionCodes = new Set(sections.map((item) => item.sectionCode));
  const nodeCodes = new Set(registryNodes.map((item) => item.nodeCode));
  const resourceByCode = new Map(resources.map((item) => [item.resourceCode, item]));
  for (const source of sources) code(source.sourceCode, "sources");
  for (const section of sections) {
    code(section.sectionCode, "sections");
    check(nonempty(section.title), section.sectionCode, "缺少标题");
    check(section.parentSectionCode === null || sectionCodes.has(section.parentSectionCode), section.sectionCode, "父目录不存在");
    check(Number.isInteger(section.sortOrder) && section.sortOrder > 0, section.sectionCode, "顺序需为正整数");
  }
  acyclic(sections.filter((item) => item.parentSectionCode).map((item) => [item.sectionCode, item.parentSectionCode]), "目录");
  function nodeShape(node, location) {
    code(node.nodeCode, location);
    check(nonempty(node.name), location, "缺少节点名称");
    check(nodeTypes.has(node.nodeType), location, "节点类型错误");
    check(["CORE", "IMPLEMENTATION"].includes(node.layer), location, "节点层级错误");
    check(sectionCodes.has(node.homeSectionCode), location, "节点主目录不存在");
    check(list(node.aliases, location).every(nonempty), location, "别名需为非空字符串");
  }
  for (const node of registryNodes) nodeShape(node, node.nodeCode);
  for (const resource of resources) {
    code(resource.resourceCode, "demos");
    check(nonempty(resource.title) && nonempty(resource.algorithmKey), resource.resourceCode, "资源标题或算法代码缺失");
    check(sectionCodes.has(resource.sectionCode), resource.resourceCode, "资源目录不存在");
    for (const nodeCode of list(resource.nodeCodes, resource.resourceCode)) check(nodeCodes.has(nodeCode), resource.resourceCode, `节点不存在: ${nodeCode}`);
  }
  let batchManifest = null;
  if (batchFile) {
    batchManifest = await json(batchFile);
    check(batchManifest.schemaVersion === "oslab-authoring/1.0", batchFile, "批次协议版本错误");
    check(Array.isArray(batchManifest.importOrder), batchFile, "批次缺少 importOrder");
  }
  const packageFiles = batchManifest
    ? [batchManifest.knowledgePackage, batchManifest.questionPackage, batchManifest.unitPackage]
      .filter(nonempty)
    : [
      "samples/knowledge.sample.json", "samples/questions.sample.json", "samples/units.sample.json",
      ...await optionalPackages("knowledge"), ...await optionalPackages("questions"), ...await optionalPackages("units")
    ];
  unique(packageFiles, "packageFile", batchFile || "packages");
  const packages = [];
  for (const file of packageFiles) {
    try { packages.push({ file, data: await json(file) }); }
    catch (error) { problems.push(`${file}: ${error.message}`); }
  }
  const chunks = new Map();
  const questions = new Map();
  const units = new Map();
  for (const { data } of packages) {
    for (const node of Array.isArray(data.nodes) ? data.nodes : []) nodeCodes.add(node.nodeCode);
    for (const chunk of Array.isArray(data.chunks) ? data.chunks : []) chunks.set(chunk.chunkCode, chunk);
    for (const question of Array.isArray(data.questions) ? data.questions : []) questions.set(question.questionCode, question);
    for (const unit of Array.isArray(data.units) ? data.units : []) units.set(unit.unitCode, unit);
  }
  function sourceRef(ref, location) {
    check(ref && typeof ref === "object", location, "缺少 sourceRef");
    if (!ref || typeof ref !== "object") return;
    check(sourceCodes.has(ref.sourceCode), location, `来源未登记: ${ref.sourceCode}`);
    check(origins.has(ref.origin), location, "来源类型错误");
    check(sectionCodes.has(ref.sectionCode), location, "来源目录不存在");
    check(nonempty(ref.locator), location, "缺少定位说明");
    const hasPages = Number.isInteger(ref.pageStart) && ref.pageStart > 0 && Number.isInteger(ref.pageEnd) && ref.pageEnd >= ref.pageStart;
    check(hasPages || (ref.pageStart === null && ref.pageEnd === null), location, "页码范围错误");
    if (ref.pageStatus != null) check(["VISIBLE", "INFERRED", "NOT_VISIBLE"].includes(ref.pageStatus), location, "页码状态错误");
    if (["TEXTBOOK", "ADAPTED"].includes(ref.origin)) {
      const traceableWithoutPage = ref.pageStatus === "NOT_VISIBLE" && ref.pageStart === null && ref.pageEnd === null && nonempty(ref.sourceAsset);
      check(hasPages || traceableWithoutPage, location, "教材材料需印刷页码，或以截图和定位说明明确记录页码缺失");
    }
    check(ref.sourceQuestionNumber === null || nonempty(ref.sourceQuestionNumber), location, "原题号用字符串或 null");
  }
  function mappings(value, roles, location, weighted = false) {
    const items = list(value, location);
    check(items.length > 0, location, "需要至少一个知识节点关联");
    unique(items, "nodeCode", location);
    for (const item of items) {
      check(nodeCodes.has(item.nodeCode), location, `节点未登记: ${item.nodeCode}`);
      check(roles.includes(item.role), location, `关联角色错误: ${item.role}`);
      if (weighted) check(Number.isFinite(item.weight) && item.weight > 0 && item.weight <= 1, location, "权重需在 (0,1] 内");
    }
    return items;
  }
  function questionShape(question, location, legacy = false) {
    check(nonempty(question.title) && nonempty(question.content) && nonempty(question.analysis), location, "题目需要标题、完整题干和解析");
    check(Number.isInteger(question.difficulty) && question.difficulty >= 1 && question.difficulty <= 5, location, "难度需为 1—5 整数");
    const options = list(question.options, location);
    unique(options, "key", location);
    for (const option of options) check(/^[A-Z]$/.test(option.key) && nonempty(option.value), location, "选项需要大写代码及文字");
    const keys = new Set(options.map((item) => item.key));
    switch (question.type) {
      case "JUDGE": check(["true", "false"].includes(question.answer) && options.length === 0, location, "判断答案需为字符串 true/false，选项为空"); break;
      case "SINGLE": check(options.length >= 2 && typeof question.answer === "string" && keys.has(question.answer), location, "单选答案需为一个现有选项代码"); break;
      case "MULTIPLE": {
        const answers = list(question.answer, location);
        unique(answers, "answer", location);
        check(options.length >= 2 && answers.length > 0 && answers.every((item) => keys.has(item)), location, "多选答案必须为现有选项集合");
        break;
      }
      case "FILL_BLANK": {
        const answers = list(question.answer, location);
        check(options.length === 0 && answers.length > 0 && answers.every(nonempty), location, "填空为单空可接受写法数组，选项为空");
        break;
      }
      default: check(false, location, `不支持题型: ${question.type}`);
    }
    if (legacy) {
      check(list(question.nodeNames, location).length > 0, location, "旧格式需要节点名称");
      return;
    }
    code(question.questionCode, location);
    check(sectionCodes.has(question.sectionCode), location, "题目目录不存在");
    sourceRef(question.sourceRef, location);
    const items = mappings(question.nodeMappings, ["PRIMARY", "SECONDARY"], location, true);
    check(items.some((item) => item.role === "PRIMARY"), location, "缺少主考查节点");
    for (const suggestion of list(question.usageSuggestions, location)) {
      check(units.has(suggestion.unitCode), location, `建议单元未登记: ${suggestion.unitCode}`);
      check(["PRACTICE", "ASSESSMENT", "REVIEW"].includes(suggestion.usage), location, "用途类型错误");
    }
    const ungrouped = question.parentQuestionCode === null && question.partIndex === null;
    const grouped = typeof question.parentQuestionCode === "string" && codePattern.test(question.parentQuestionCode) && Number.isInteger(question.partIndex) && question.partIndex > 0;
    check(ungrouped || grouped, location, "拆题需共同来源题号与正整数 partIndex；未拆题两者均为 null");
  }
  const prerequisiteEdges = [];
  const allActivities = new Map();
  for (const { file, data } of packages) {
    check(data.schemaVersion === "oslab-authoring/1.0", file, "协议版本错误");
    code(data.batchCode, file);
    check(nonempty(data.contributor) && data.courseCode === "course.operating-systems", file, "成员或课程编号错误");
    if (data.packageType === "KNOWLEDGE") {
      const nodes = list(data.nodes, file), blocks = list(data.chunks, file), relations = list(data.relations, file);
      unique(nodes, "nodeCode", file); unique(blocks, "chunkCode", file);
      for (const node of nodes) {
        nodeShape(node, node.nodeCode);
        check(nonempty(node.description), node.nodeCode, "缺少节点简介");
        const refs = list(node.sourceRefs, node.nodeCode);
        check(refs.length > 0, node.nodeCode, "缺少节点出处");
        refs.forEach((ref) => sourceRef(ref, node.nodeCode));
      }
      for (const block of blocks) {
        const loc = block.chunkCode;
        code(loc, file);
        check(sectionCodes.has(block.sectionCode), loc, "内容目录不存在");
        check(["DEFINITION", "EXPLANATION", "ALGORITHM", "WORKED_EXAMPLE", "FIGURE", "SUMMARY"].includes(block.chunkType), loc, "块类型错误");
        check(nonempty(block.title) && nonempty(block.contentMd), loc, "标题或正文缺失");
        const codes = list(block.nodeCodes, loc);
        check(codes.length > 0, loc, "缺少解释节点"); unique(codes, "nodeCode", loc);
        codes.forEach((value) => check(nodeCodes.has(value), loc, `节点未登记: ${value}`));
        sourceRef(block.sourceRef, loc);
        for (const asset of list(block.assets, loc)) {
          check(nonempty(asset.path) && nonempty(asset.caption), loc, "附件路径或说明缺失");
          sourceRef(asset.sourceRef, loc);
          if (nonempty(asset.path)) {
            try { await access(path.join(root, asset.path)); }
            catch { problems.push(`${loc}: 附件不存在: ${asset.path}`); }
          }
        }
      }
      const seenRelations = new Set();
      for (const relation of relations) {
        const loc = `${file} ${relation.fromNodeCode} -> ${relation.toNodeCode}`;
        check(nodeCodes.has(relation.fromNodeCode) && nodeCodes.has(relation.toNodeCode), loc, "关系端点未登记");
        check(relation.fromNodeCode !== relation.toNodeCode, loc, "关系不能自指");
        check(relationTypes.has(relation.relationType) && nonempty(relation.reason), loc, "关系类型或依据错误");
        if (["CONTRASTS_WITH", "RELATED_TO"].includes(relation.relationType)) check(relation.fromNodeCode < relation.toNodeCode, loc, "无向关系按代码排序保存");
        const key = `${relation.relationType}:${relation.fromNodeCode}:${relation.toNodeCode}`;
        check(!seenRelations.has(key), loc, "关系重复"); seenRelations.add(key);
        sourceRef(relation.sourceRef, loc);
        if (relation.relationType === "REQUIRES") prerequisiteEdges.push([relation.fromNodeCode, relation.toNodeCode]);
      }
    } else if (data.packageType === "QUESTIONS") {
      const items = list(data.questions, file);
      check(items.length <= 100, file, "每批题目不超过 100 道");
      unique(items, "questionCode", file);
      items.forEach((question) => questionShape(question, question.questionCode));
    } else if (data.packageType === "UNITS") {
      const items = list(data.units, file);
      unique(items, "unitCode", file);
      for (const unit of items) {
        const loc = unit.unitCode;
        code(loc, file);
        check(nonempty(unit.title) && sectionCodes.has(unit.sectionCode), loc, "单元标题或目录错误");
        check(list(unit.objectives, loc).length > 0 && unit.objectives.every(nonempty), loc, "需要可验证学习目标");
        check(Number.isInteger(unit.sortOrder) && unit.sortOrder > 0 && Number.isFinite(unit.estimatedMinutes) && unit.estimatedMinutes > 0, loc, "单元顺序或预计学习时长错误");
        check(["DRAFT", "SUBMITTED", "APPROVED", "PUBLISHED", "REJECTED"].includes(unit.status), loc, "单元状态错误");
        mappings(unit.nodeMappings, ["TEACHES", "PREREQUISITE", "EXTENSION"], loc);
        const activities = list(unit.activities, loc);
        if (unit.status === "PUBLISHED") check(activities.length > 0, loc, "已发布单元不能没有活动");
        unique(activities, "activityCode", loc); unique(activities, "sortOrder", loc);
        for (const activity of activities) {
          const activityLoc = activity.activityCode;
          code(activityLoc, loc);
          const previousUnit = allActivities.get(activityLoc);
          check(!previousUnit || previousUnit === loc, activityLoc, "同一活动编号被不同单元占用"); allActivities.set(activityLoc, loc);
          check(nonempty(activity.title) && typeof activity.required === "boolean", activityLoc, "标题或 required 类型错误");
          check(Number.isInteger(activity.sortOrder) && activity.sortOrder > 0, activityLoc, "活动顺序错误");
          const reference = activity.reference || {};
          let expectedKind, rules;
          if (["READING", "WORKED_EXAMPLE", "SUMMARY"].includes(activity.activityType)) { expectedKind = "CHUNK"; rules = ["CONFIRM"]; }
          else if (activity.activityType === "DEMO") { expectedKind = "RESOURCE"; rules = ["RUN_TO_END"]; }
          else if (["PRACTICE", "ASSESSMENT"].includes(activity.activityType)) { expectedKind = "QUESTION"; rules = ["SUBMIT", "PASS"]; }
          else if (activity.activityType === "EXPERIMENT") { expectedKind = "RESOURCE"; rules = ["SUBMIT", "PASS"]; }
          else { check(false, activityLoc, "未知活动类型"); continue; }
          check(reference.kind === expectedKind && rules.includes(activity.completionRule), activityLoc, "引用类型或完成条件与活动不一致");
          const dictionary = expectedKind === "CHUNK" ? chunks : expectedKind === "QUESTION" ? questions : resourceByCode;
          check(dictionary.has(reference.code), activityLoc, `引用资源不存在: ${reference.code}`);
          if (activity.activityType === "DEMO") check(resourceByCode.get(reference.code)?.resourceType === "DEMO", activityLoc, "演示活动必须引用 DEMO");
          if (activity.activityType === "EXPERIMENT") check(resourceByCode.get(reference.code)?.resourceType === "EXPERIMENT", activityLoc, "实验活动必须引用 EXPERIMENT");
        }
      }
    } else check(false, file, "未知 packageType");
  }
  for (const { file, data } of packages) {
    const references = [];
    function collect(value) {
      if (!value || typeof value !== "object") return;
      if (nonempty(value.sourceAsset)) references.push(value.sourceAsset);
      if (nonempty(value.answerAsset)) references.push(value.answerAsset);
      for (const nested of Object.values(value)) collect(nested);
    }
    collect(data);
    for (const assetPath of new Set(references)) {
      try { await access(path.join(root, assetPath)); }
      catch { problems.push(`${file}: 出处截图不存在: ${assetPath}`); }
    }
  }
  acyclic(prerequisiteEdges, "REQUIRES 前置关系");
  if (!batchManifest) {
    const legacy = list(await json("samples/questions.legacy.sample.json"), "legacy");
    legacy.forEach((question, index) => questionShape(question, `legacy[${index}]`, true));
  }
  if (problems.length) {
    console.error(`内容检查失败（${problems.length} 项）：\n${problems.map((item) => `- ${item}`).join("\n")}`);
    process.exitCode = 1;
  } else {
    const prefix = batchManifest ? `批次 ${batchManifest.batchCode} 检查通过` : "内容检查通过";
    console.log(`${prefix}：${sources.length} 个来源、${sections.length} 个目录项、${nodeCodes.size} 个节点代码、${resources.length} 个演示登记、${packages.length} 个内容包、${chunks.size} 个教学块、${questions.size} 道题、${units.size} 个单元。`);
    console.log("仅验证文件契约与引用；教材原文、答案正确性及运行环境需另行核对。未写数据库。");
  }
} catch (error) {
  console.error(`无法完成内容检查：${error.message}`);
  process.exitCode = 1;
}
