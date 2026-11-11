#!/usr/bin/env node
// Offline file check. Node.js only; no dependency install, database, or network.
import { readFile, access } from "node:fs/promises";
import path from "node:path";

const args = process.argv.slice(2);
const workDirectoryIndex = args.indexOf("--work-dir");
const workDirectory = workDirectoryIndex < 0 ? "work" : args[workDirectoryIndex + 1];
if (!workDirectory || workDirectory.startsWith("--")) throw new Error("--work-dir 需要指定资源目录，如 experiment-work");
const root = path.resolve(args.find((arg, index) => !arg.startsWith("--") && (workDirectoryIndex < 0 || index !== workDirectoryIndex + 1)) || ".");
const workRoot = path.resolve(root, workDirectory);
const workFile = (file) => path.join(workDirectory, file);
const allowEmpty = args.includes("--allow-empty");
const issues = [];
const nonempty = (value) => typeof value === "string" && value.trim().length > 0;
const code = (value) => typeof value === "string" && /^[a-z][a-z0-9._-]*$/.test(value);
const check = (condition, at, message) => { if (!condition) issues.push(`${at}: ${message}`); };
const read = async (file) => JSON.parse((await readFile(path.join(root, file), "utf8")).replace(/^\uFEFF/, ""));
async function exists(file) { try { await access(path.join(root, file)); return true; } catch { return false; } }
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
    check(["TEXTBOOK","ADAPTED","SELF_AUTHORED","SELF_AUTHORED_SAMPLE","TOC"].includes(ref.origin), at, "来源类型错误");
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
  let packageCount = 0, nodeCount = 0, chunkCount = 0, questionCount = 0, experimentCount = 0;
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
  if (await exists(workFile("questions.json"))) {
    const data = await read(workFile("questions.json")); packageCount++;
    check(data.schemaVersion === "oslab-authoring/1.0" && data.packageType === "QUESTIONS", "questions", "协议或包类型错误");
    const questions = items(data.questions, "questions"); questionCount = questions.length;
    if (!allowEmpty) check(questions.length > 0 || (await exists(workFile("experiment-maintenance.json")) && (await read(workFile("experiment-maintenance.json"))).experiments?.length > 0), "questions", "题目和实验均为空模板");
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
      check(typeof at === "string" && /^\d+$/.test(at), at, "原实验ID应为数字字符串");
      check(code(experiment.resourceCode), at, "资源编号错误");
      if (experiment.nodeMappings?.length) mapping(experiment.nodeMappings, at);
      for (const target of items(experiment.unitMappings, at)) {
        check(unitCodes.has(target.unitCode) && typeof target.required === "boolean" && ["SUBMIT","PASS"].includes(target.completionRule), at, "单元映射错误");
      }
      for (const change of items(experiment.contentChanges, at)) if (change.file) await asset(change.file, at);
      check(experiment.verification && typeof experiment.verification === "object", at, "缺少核对状态");
    }
  }
  check(packageCount > 0, workDirectory, "没有找到资源包文件");
  if (issues.length) { console.error(issues.map((item) => `- ${item}`).join("\n")); process.exitCode = 1; }
  else console.log(`文件检查通过：${nodeCount}节点、${chunkCount}教学块、${questionCount}题、${experimentCount}实验维护项。${allowEmpty ? "允许空模板，用于开工包检查。" : "仅为文件检查，L仍需核对教材、正式编号和运行结果。"}`);
} catch (error) { console.error(`检查未完成：${error.message}`); process.exitCode = 1; }
