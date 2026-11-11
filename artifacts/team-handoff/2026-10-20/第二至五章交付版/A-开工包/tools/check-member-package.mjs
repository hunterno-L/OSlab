#!/usr/bin/env node
// Offline file check. Node.js only; no dependency install, database, or network.
import { readFile, access } from "node:fs/promises";
import path from "node:path";

const args = process.argv.slice(2);
let workDirectory = "work", rootArgument = ".";
const withJ = [];
for (let index = 0; index < args.length; index++) {
  if (["--work-dir", "--with-j"].includes(args[index])) {
    const flag = args[index], value = args[++index];
    if (!value || value.startsWith("--")) throw new Error(`${flag} 需要目录参数`);
    if (flag === "--work-dir") workDirectory = value;
    else withJ.push(value);
  } else if (args[index] !== "--allow-empty") {
    if (args[index].startsWith("--")) throw new Error(`未知参数 ${args[index]}`);
    rootArgument = args[index];
  }
}
const root = path.resolve(rootArgument);
const workRoot = path.resolve(root, workDirectory);
const workFile = (file) => path.resolve(workRoot, file);
const allowEmpty = args.includes("--allow-empty");
const issues = [];
const nonempty = (value) => typeof value === "string" && value.trim().length > 0;
const code = (value) => typeof value === "string" && /^[a-z][a-z0-9._-]*$/.test(value);
const check = (condition, at, message) => { if (!condition) issues.push(`${at}: ${message}`); };
const read = async (file) => JSON.parse((await readFile(path.resolve(root, file), "utf8")).replace(/^\uFEFF/, ""));
async function exists(file) { try { await access(path.resolve(root, file)); return true; } catch { return false; } }
function items(value, at) { check(Array.isArray(value), at, "应为数组"); return Array.isArray(value) ? value : []; }
function unique(values, at) { check(new Set(values).size === values.length, at, "编号重复"); }

try {
  const overview = await read("shared/overview-authoring.json");
  const registry = await read("shared/nodes.json");
  const sections = new Set((await read("shared/sections.json")).sections.map((item) => item.sectionCode));
  const sources = new Set((await read("shared/sources.json")).map((item) => item.sourceCode));
  const nodeCodes = new Set([...registry.nodes, ...overview.nodes].map((item) => item.nodeCode));
  const unitCodes = new Set(overview.units.map((item) => item.unitCode));
  const legacyUnitPath = "reference/samples/units.sample.json";
  if (await exists(legacyUnitPath)) for (const unit of (await read(legacyUnitPath)).units) unitCodes.add(unit.unitCode);
  const demoCodes = new Set((await exists("shared/demos.json")) ? (await read("shared/demos.json")).resources.map((item) => item.resourceCode) : []);
  const planPath = workFile("chapter-plan.json");
  const plan = await exists(planPath) ? await read(planPath) : null;
  function registerPlan(value) {
    for (const section of items(value.sections, "chapter-plan.sections")) sections.add(section.sectionCode);
    for (const unit of items(value.units, "chapter-plan.units")) unitCodes.add(unit.unitCode);
  }
  for (const directory of withJ) {
    const jRoot = path.resolve(root, directory);
    const knowledge = await read(path.join(jRoot, "knowledge.json"));
    check(knowledge.packageType === "KNOWLEDGE", "--with-j", "应引用J的本章知识包目录");
    for (const node of items(knowledge.nodes, "J.nodes")) nodeCodes.add(node.nodeCode);
    registerPlan(await read(path.join(jRoot, "chapter-plan.json")));
  }
  if (plan) registerPlan(plan);
  async function asset(relative, at) {
    check(nonempty(relative), at, "附件路径为空");
    if (!nonempty(relative)) return;
    const target = path.resolve(workRoot, relative);
    const within = path.relative(workRoot, target);
    check(!within.startsWith("..") && !path.isAbsolute(within), at, "附件应位于资源包目录内");
    try { await access(target); } catch { issues.push(`${at}: 附件不存在 ${relative}`); }
  }
  async function sourceRef(ref, at) {
    check(ref && typeof ref === "object", at, "缺少sourceRef");
    if (!ref || typeof ref !== "object") return;
    check(sources.has(ref.sourceCode), at, "来源编号未登记");
    check(["TEXTBOOK","ADAPTED","SELF_AUTHORED","SELF_AUTHORED_SAMPLE","EXTERNAL_QUESTION","TOC"].includes(ref.origin), at, "来源类型错误");
    check(sections.has(ref.sectionCode), at, "来源目录未登记");
    check(nonempty(ref.locator), at, "缺少来源定位");
    const pages = Number.isInteger(ref.pageStart) && ref.pageStart > 0 && Number.isInteger(ref.pageEnd) && ref.pageEnd >= ref.pageStart;
    const noPages = ref.pageStart === null && ref.pageEnd === null;
    check(pages || noPages, at, "页码范围错误");
    if (["TEXTBOOK","ADAPTED"].includes(ref.origin)) check(pages || (noPages && ref.pageStatus === "NOT_VISIBLE" && nonempty(ref.sourceAsset)), at, "教材材料需页码，或明确说明页码不可见并提供截图");
    if (ref.sourceAsset) await asset(ref.sourceAsset, at);
    if (ref.answerAsset) await asset(ref.answerAsset, at);
  }
  function mapping(value, at) {
    const values = items(value, at);
    check(values.length > 0, at, "缺少实际考查节点；待定项请写建议清单");
    unique(values.map((item) => item.nodeCode), at);
    for (const item of values) {
      check(nodeCodes.has(item.nodeCode), at, `未知nodeCode ${item.nodeCode}`);
      check(["PRIMARY","SECONDARY"].includes(item.role), at, "考查角色错误");
      check(Number.isFinite(item.weight) && item.weight > 0 && item.weight <= 1, at, "权重应在(0,1]内");
    }
    check(values.some((item) => item.role === "PRIMARY"), at, "缺少主考查节点");
  }
  let packageCount = 0, nodeCount = 0, chunkCount = 0, questionCount = 0, applicationCount = 0, experimentCount = 0, changedExperimentCount = 0;
  if (await exists(workFile("knowledge.json"))) {
    const data = await read(workFile("knowledge.json")); packageCount++;
    check(data.schemaVersion === "oslab-authoring/1.0" && data.packageType === "KNOWLEDGE", "knowledge", "协议或包类型错误");
    const nodes = items(data.nodes, "nodes"), chunks = items(data.chunks, "chunks"), relations = items(data.relations, "relations");
    nodeCount = nodes.length; chunkCount = chunks.length;
    if (!allowEmpty) check(nodes.length > 0 && chunks.length > 0, "knowledge", "知识包仍为空模板");
    unique(nodes.map((item) => item.nodeCode), "nodes"); unique(chunks.map((item) => item.chunkCode), "chunks");
    for (const node of nodes) { check(code(node.nodeCode), "nodes", "编号格式错误"); nodeCodes.add(node.nodeCode); }
    for (const node of nodes) {
      check(nonempty(node.name) && nonempty(node.description), node.nodeCode, "缺少名称或简介");
      check(["CONCEPT","ALGORITHM","MECHANISM","IMPLEMENTATION"].includes(node.nodeType), node.nodeCode, "节点类型错误");
      check(sections.has(node.homeSectionCode), node.nodeCode, "主目录未登记");
      const refs = items(node.sourceRefs, node.nodeCode); check(refs.length > 0, node.nodeCode, "缺少出处");
      for (const ref of refs) await sourceRef(ref, node.nodeCode);
    }
    for (const chunk of chunks) {
      check(code(chunk.chunkCode) && nonempty(chunk.title) && nonempty(chunk.contentMd), chunk.chunkCode, "缺少编号、标题或正文");
      check(sections.has(chunk.sectionCode), chunk.chunkCode, "内容目录未登记");
      const mapped = items(chunk.nodeCodes, chunk.chunkCode); check(mapped.length > 0 && mapped.every((value) => nodeCodes.has(value)), chunk.chunkCode, "内容节点引用错误");
      await sourceRef(chunk.sourceRef, chunk.chunkCode);
      for (const item of items(chunk.assets, chunk.chunkCode)) { await asset(item.path, chunk.chunkCode); await sourceRef(item.sourceRef, chunk.chunkCode); }
    }
    for (const relation of relations) {
      const at = `${relation.fromNodeCode}->${relation.toNodeCode}`;
      check(nodeCodes.has(relation.fromNodeCode) && nodeCodes.has(relation.toNodeCode) && relation.fromNodeCode !== relation.toNodeCode, at, "关系端点错误");
      check(["REQUIRES","CONTAINS","CONTRASTS_WITH","BASED_ON","IMPLEMENTS","RELATED_TO"].includes(relation.relationType) && nonempty(relation.reason), at, "关系类型或依据错误");
      if (["CONTRASTS_WITH","RELATED_TO"].includes(relation.relationType)) check(relation.fromNodeCode < relation.toNodeCode, at, "无方向关系按代码排序");
      await sourceRef(relation.sourceRef, at);
    }
  }
  if (plan) {
    check(plan.schemaVersion === "oslab-chapter-plan/1.0" && plan.status === "PROPOSED" && sections.has(plan.chapterCode), "chapter-plan", "协议、章代码或建议状态错误");
    if (!allowEmpty) check(plan.units.length > 0, "chapter-plan", "缺少本章单元编排建议");
    unique(plan.sections.map((item) => item.sectionCode), "chapter-plan.sections");
    unique(plan.units.map((item) => item.unitCode), "chapter-plan.units");
    for (const section of plan.sections) {
      check(code(section.sectionCode) && nonempty(section.title) && nonempty(section.textbookNumber) && sections.has(section.parentSectionCode), section.sectionCode, "目录建议字段或父目录错误");
    }
    const knowledge = await exists(workFile("knowledge.json")) ? await read(workFile("knowledge.json")) : null;
    const chunkCodes = new Set(knowledge?.chunks.map((item) => item.chunkCode) || []), referenced = new Set();
    for (const unit of plan.units) {
      check(code(unit.unitCode) && sections.has(unit.sectionCode) && nonempty(unit.title), unit.unitCode, "单元代码、目录或标题错误");
      check(items(unit.objectives, unit.unitCode).length > 0 && unit.objectives.every(nonempty), unit.unitCode, "缺少可验证目标");
      check(Number.isInteger(unit.sortOrder) && unit.sortOrder > 0 && Number.isFinite(unit.estimatedMinutes) && unit.estimatedMinutes > 0, unit.unitCode, "顺序或预计时长错误");
      check(items(unit.nodeCodes, unit.unitCode).length > 0 && unit.nodeCodes.every((value) => nodeCodes.has(value)), unit.unitCode, "单元节点引用错误");
      for (const value of items(unit.prerequisiteUnitCodes, unit.unitCode)) check(value !== unit.unitCode && unitCodes.has(value), unit.unitCode, `未知或自身先修单元 ${value}`);
      for (const value of items(unit.readingChunkCodes, unit.unitCode)) { check(chunkCodes.has(value), unit.unitCode, `未知阅读块 ${value}`); referenced.add(value); }
      for (const value of items(unit.demoResourceCodes, unit.unitCode)) check(demoCodes.has(value), unit.unitCode, `未知演示 ${value}`);
      check(nonempty(unit.practiceGuidance), unit.unitCode, "缺少练习安排建议");
    }
    for (const value of chunkCodes) check(referenced.has(value), value, "未安排在本章阅读顺序中");
    const prerequisites = new Map(plan.units.map((unit) => [unit.unitCode, unit.prerequisiteUnitCodes || []])), visited = new Set(), active = new Set();
    function visit(unit) {
      if (active.has(unit)) { check(false, unit, "单元先修形成环"); return; }
      if (visited.has(unit)) return;
      active.add(unit);
      for (const next of prerequisites.get(unit) || []) visit(next);
      active.delete(unit); visited.add(unit);
    }
    for (const unit of prerequisites.keys()) visit(unit);
  }
  const applicationFile = workFile("application-questions.json");
  if (await exists(applicationFile)) {
    const data = await read(applicationFile); packageCount++;
    check(data.schemaVersion === "oslab-application-authoring/1.0" && data.packageType === "APPLICATION_QUESTIONS" && data.contributor === "A" && data.courseCode === "course.operating-systems" && code(data.batchCode), "application-questions", "协议、类型或包身份错误");
    const questions = items(data.questions, "application-questions"); applicationCount = questions.length;
    if (!allowEmpty && questions.length === 0) {
      const objectiveCount = await exists(workFile("questions.json")) ? (await read(workFile("questions.json"))).questions.length : 0;
      check(objectiveCount > 0, "application-questions", "综合题及客观题均为空模板");
    }
    unique(questions.map((item) => item.questionCode), "application-questions");
    const allCodes = questions.map((item) => item.questionCode);
    if (await exists(workFile("questions.json"))) allCodes.push(...(await read(workFile("questions.json"))).questions.map((item) => item.questionCode));
    unique(allCodes, "客观题与综合题不能使用相同questionCode");
    for (const question of questions) {
      const at = question.questionCode, parts = items(question.parts, at);
      check(code(at) && nonempty(question.title) && nonempty(question.contentMd) && question.type === "COMPREHENSIVE", at, "综合题缺少编号、题面或类型");
      check(sections.has(question.sectionCode) && Number.isInteger(question.difficulty) && question.difficulty >= 1 && question.difficulty <= 5, at, "目录或难度错误");
      check(Number.isFinite(question.maxScore) && question.maxScore > 0 && parts.length > 0, at, "总分或小问为空");
      mapping(question.nodeMappings, at);
      await sourceRef(question.sourceRef, at); await sourceRef(question.answerSourceRef, at);
      for (const item of items(question.assets, at)) { await asset(item.path, at); await sourceRef(item.sourceRef, at); }
      for (const use of items(question.usageSuggestions, at)) check(unitCodes.has(use.unitCode) && ["PRACTICE","ASSESSMENT","REVIEW"].includes(use.usage), at, "单元用途错误");
      unique(parts.map((item) => item.partCode), at);
      for (const part of parts) {
        const rubric = items(part.rubric, part.partCode);
        check(code(part.partCode) && nonempty(part.promptMd) && nonempty(part.answerMd), at, "小问缺少编号、问题或参考解");
        check(["SHORT_ANSWER","CALCULATION","PROOF","DRAWING","PSEUDOCODE","MIXED"].includes(part.responseType), part.partCode, "作答形式错误");
        check(Number.isFinite(part.maxScore) && part.maxScore > 0 && rubric.length > 0, part.partCode, "小问分值或评分点为空");
        mapping(part.nodeMappings, part.partCode);
        const mapped = new Set(part.nodeMappings.map((item) => item.nodeCode));
        unique(rubric.map((item) => item.rubricCode), part.partCode);
        for (const point of rubric) {
          check(code(point.rubricCode) && nonempty(point.description) && Number.isFinite(point.points) && point.points > 0, part.partCode, "评分点格式错误");
          check(items(point.nodeCodes, part.partCode).length > 0 && point.nodeCodes.every((value) => mapped.has(value)), part.partCode, "评分点节点不属于小问考查范围");
        }
        check(Math.abs(rubric.reduce((sum, item) => sum + item.points, 0) - part.maxScore) < 1e-8, part.partCode, "评分点分值之和不等于小问分值");
        for (const key of ["commonErrors", "acceptableAlternatives"]) check(items(part[key], part.partCode).every(nonempty), part.partCode, `${key}应为字符串数组`);
      }
      check(Math.abs(parts.reduce((sum, item) => sum + item.maxScore, 0) - question.maxScore) < 1e-8, at, "小问分值之和不等于总分");
    }
  }
  if (await exists(workFile("questions.json"))) {
    const data = await read(workFile("questions.json")); packageCount++;
    check(data.schemaVersion === "oslab-authoring/1.0" && data.packageType === "QUESTIONS", "questions", "协议或包类型错误");
    const questions = items(data.questions, "questions"); questionCount = questions.length;
    if (!allowEmpty) check(questions.length > 0 || applicationCount > 0 || (await exists(workFile("experiment-maintenance.json")) && (await read(workFile("experiment-maintenance.json"))).experiments?.length > 0), "questions", "题目和实验均为空模板");
    unique(questions.map((item) => item.questionCode), "questions");
    for (const question of questions) {
      const at = question.questionCode;
      check(code(at) && nonempty(question.title) && nonempty(question.content) && nonempty(question.analysis), at, "缺少编号、题干或解析");
      check(sections.has(question.sectionCode), at, "题目目录未登记");
      const options = items(question.options, at), keys = options.map((item) => item.key); unique(keys, at);
      if (question.type === "JUDGE") check(options.length === 0 && ["true","false"].includes(question.answer), at, "判断答案用字符串true/false");
      else if (question.type === "SINGLE") check(keys.length >= 2 && typeof question.answer === "string" && keys.includes(question.answer), at, "单选答案需为选项代码");
      else if (question.type === "MULTIPLE") { const answers = items(question.answer, at); check(answers.length > 0 && answers.every((answer) => keys.includes(answer)), at, "多选答案错误"); unique(answers, at); }
      else if (question.type === "FILL_BLANK") check(options.length === 0 && Array.isArray(question.answer) && question.answer.length > 0 && question.answer.every(nonempty), at, "单空答案用可接受写法数组");
      else check(false, at, "未知题型");
      mapping(question.nodeMappings, at);
      for (const suggestion of items(question.usageSuggestions, at)) check(unitCodes.has(suggestion.unitCode), at, `未知单元 ${suggestion.unitCode}`);
      await sourceRef(question.sourceRef, at);
    }
  }
  if (await exists(workFile("experiment-maintenance.json"))) {
    const data = await read(workFile("experiment-maintenance.json")); packageCount++;
    check(data.schemaVersion === "oslab-experiment-maintenance/1.0", "experiments", "实验维护协议错误");
    const experiments = items(data.experiments, "experiments"); experimentCount = experiments.length;
    if (!allowEmpty) check(experiments.length > 0, "experiments", "实验维护包仍为空模板");
    unique(experiments.map((item) => item.questionId), "experiments");
    for (const experiment of experiments) {
      const at = experiment.questionId;
      check(typeof at === "string" && /^[1-9][0-9]*$/.test(at), at, "原实验ID应为非零数字字符串");
      check(typeof experiment.resourceCode === "string" && /^[a-z][a-z0-9._-]{1,127}$/.test(experiment.resourceCode), at, "资源编号错误");
      check(nonempty(experiment.title), at, "缺少原实验标题");
      mapping(experiment.nodeMappings, at);
      unique(items(experiment.unitMappings, at).map((target) => target.unitCode), at);
      for (const target of items(experiment.unitMappings, at)) {
        check(unitCodes.has(target.unitCode) && typeof target.required === "boolean" && ["SUBMIT","PASS"].includes(target.completionRule), at, "单元映射错误");
      }
      const changes = items(experiment.contentChanges, at);
      if (changes.length) changedExperimentCount++;
      unique(changes.map((item) => item.field), at);
      for (const change of changes) {
        check(["content","originalCode","answer","judgeCase","judgeConfig","tags"].includes(change.field) && nonempty(change.reason), at, "修订字段或原因错误");
        check(nonempty(change.file) && change.file.startsWith("experiment-files/") && !change.file.includes("..") && !change.file.includes("//") && !/[\\:?#]/.test(change.file) && change.file.length <= 300, at, "修订文件路径须为experiment-files/内的有效相对路径");
        await asset(change.file, at);
        if (nonempty(change.file)) {
          const bytes = await readFile(path.resolve(workRoot, change.file));
          const text = new TextDecoder("utf-8", { fatal: true }).decode(bytes).replace(/^\uFEFF/, "");
          check(bytes.length <= 4 * 1024 * 1024 && nonempty(text), at, "修订文件为空或超过4MiB");
          if (["judgeCase","judgeConfig","tags"].includes(change.field)) {
            const value = JSON.parse(text);
            if (change.field === "judgeCase") check(Array.isArray(value) && value.length > 0 && value.every((item) => typeof item.input === "string" && typeof item.output === "string"), at, "judgeCase需非空input/output字符串数组");
            if (change.field === "judgeConfig") check(Number.isFinite(value.timeLimit) && value.timeLimit > 0 && Number.isFinite(value.memoryLimit) && value.memoryLimit > 0, at, "judgeConfig需正数timeLimit/memoryLimit");
            if (change.field === "tags") check(Array.isArray(value) && value.every(nonempty), at, "tags需字符串数组");
          }
        }
      }
      check(experiment.verification && typeof experiment.verification === "object", at, "缺少核对状态");
    }
  }
  check(packageCount > 0, workDirectory, "没有找到资源包文件");
  if (issues.length) { console.error(issues.map((item) => `- ${item}`).join("\n")); process.exitCode = 1; }
  else console.log(`文件检查通过：${nodeCount}节点、${chunkCount}教学块、${questionCount}客观题、${applicationCount}综合题、${plan?.units.length || 0}单元建议、${experimentCount}实验维护项（${changedExperimentCount}项含实际修订文件）。${allowEmpty ? "允许空模板，用于开工包检查。" : "仅为文件检查；综合题和编排建议待系统接入，不代表内容正确或实际运行通过。"}`);
} catch (error) { console.error(`检查未完成：${error.message}`); process.exitCode = 1; }
