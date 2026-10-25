#!/usr/bin/env node
// Prepare offline J/A resources from tracked authoring files and the bundled SQL snapshot.
// Does not connect to a database, execute SQL, or export users/submissions.
import { readFile, writeFile, mkdir, copyFile, readdir, access } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";

const workspace = fileURLToPath(new URL("../", import.meta.url));
const content = path.join(workspace, "content/operating-systems/wangdao-2025");
const remainingChapters = process.argv.includes("--remaining-chapters");
const destination = path.resolve(process.argv.slice(2).find((arg) => !arg.startsWith("--")) || path.join(workspace, remainingChapters ? "artifacts/team-handoff/2026-10-20/第二至五章交付版" : "artifacts/team-handoff/2026-10-17/按章交付版"));
const refreshReference = process.argv.includes("--refresh-reference");
const sqlPath = path.join(workspace, "数据库文件/oslab_db.sql");
const sql = await readFile(sqlPath, "utf8");
const load = async (relative) => JSON.parse(await readFile(path.join(content, relative), "utf8"));
const saveJson = async (target, data) => {
  await mkdir(path.dirname(target), { recursive: true });
  await writeFile(target, JSON.stringify(data, null, 2) + "\n", "utf8");
};

function tableColumns(table) {
  const escaped = table.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = sql.match(new RegExp("CREATE TABLE `" + escaped + "` \\(([\\s\\S]*?)\\n\\)", "m"));
  if (!match) throw new Error(`SQL snapshot does not define ${table}`);
  return [...match[1].matchAll(/^\s*`([^`]+)`\s/gm)].map((item) => item[1]);
}

function tableRows(table) {
  const columns = tableColumns(table);
  const pattern = new RegExp("^INSERT INTO `" + table + "`(?:\\s*\\([^)]*\\))?\\s+VALUES\\s+", "gm");
  const rows = [];
  for (const statement of sql.matchAll(pattern)) {
    let index = statement.index + statement[0].length;
    const whitespace = () => { while (/\s/.test(sql[index] || "") && index < sql.length) index++; };
    while (index < sql.length) {
      whitespace();
      if (sql[index] !== "(") throw new Error(`Expected tuple for ${table} at ${index}`);
      index++;
      const values = [];
      while (index < sql.length) {
        whitespace();
        let value = "";
        if (sql[index] === "'") {
          index++;
          let closed = false;
          while (index < sql.length) {
            const char = sql[index++];
            if (char === "\\") {
              const next = sql[index++];
              const escapes = { "0":"\0", b:"\b", n:"\n", r:"\r", t:"\t", Z:"\x1a" };
              value += escapes[next] ?? next;
            } else if (char === "'") {
              if (sql[index] === "'") { value += "'"; index++; }
              else { closed = true; break; }
            } else value += char;
          }
          if (!closed) throw new Error(`Unclosed SQL literal in ${table}`);
        } else {
          const start = index;
          while (index < sql.length && ![",", ")"].includes(sql[index])) index++;
          value = sql.slice(start, index).trim();
          if (value === "NULL") value = null;
        }
        values.push(value);
        whitespace();
        if (sql[index] === ",") { index++; continue; }
        if (sql[index] === ")") { index++; break; }
        throw new Error(`Expected tuple delimiter in ${table} at ${index}`);
      }
      if (values.length !== columns.length) throw new Error(`${table}: ${values.length} values for ${columns.length} columns`);
      rows.push(Object.fromEntries(columns.map((column, position) => [column, values[position]])));
      whitespace();
      if (sql[index] === ";") break;
      if (sql[index] !== ",") throw new Error(`Expected statement delimiter in ${table} at ${index}`);
      index++;
    }
  }
  return rows;
}

const rawQuestions = tableRows("question");
const rawSets = tableRows("question_sets");
const rawMappings = tableRows("question_set_map");
const activeQuestions = rawQuestions.filter((row) => row.isDelete === "0");
if (!activeQuestions.length) throw new Error("No active experiment records found in SQL snapshot");
const knownIds = new Set();
for (const row of activeQuestions) {
  if (!/^\d+$/.test(row.id) || knownIds.has(row.id)) throw new Error(`Invalid or duplicate experiment id: ${row.id}`);
  knownIds.add(row.id);
}
const setById = new Map(rawSets.map((row) => [row.id, row]));
function jsonField(row, key, fallback) {
  if (row[key] == null || row[key] === "") return fallback;
  try { return JSON.parse(row[key]); }
  catch { throw new Error(`Invalid ${key} JSON in experiment ${row.id}`); }
}
const experiments = activeQuestions.map((row) => {
  const setIds = rawMappings.filter((map) => map.questionId === row.id && map.isDelete !== "1")
    .map((map) => map.setId ?? map.questionSetId).filter(Boolean);
  return {
    questionId: row.id,
    resourceCode: `exp.legacy.${row.id}`,
    title: row.title,
    content: row.content || "",
    tags: jsonField(row, "tags", []),
    referenceAnswer: row.answer || "",
    originalCode: row.originalCode || "",
    judgeCase: jsonField(row, "judgeCase", []),
    judgeConfig: jsonField(row, "judgeConfig", {}),
    legacySetIds: setIds,
    legacySets: setIds.map((id) => setById.get(id)).filter(Boolean)
      .map((set) => ({ id:set.id, title:set.title ?? set.setName ?? set.name ?? "" })),
    snapshotUpdatedTime: row.updateTime,
    runtimeValidated: false
  };
});

const overview = await load("catalog/overview-authoring.json");
const sections = await load("catalog/sections.json");
const registry = await load("catalog/nodes.json");
const sources = await load("catalog/sources.json");
const sharedRegistry = { ...registry, nodes: [...registry.nodes] };
if (remainingChapters) {
  const priorKnowledge = path.join(workspace, "artifacts/content-import/20261019-draft-import/knowledge-original/knowledge.json");
  let priorNodes = [];
  try { priorNodes = JSON.parse((await readFile(priorKnowledge, "utf8")).replace(/^\uFEFF/, "")).nodes; }
  catch (error) { if (error.code !== "ENOENT") throw error; }
  sharedRegistry.nodes = [...new Map([...registry.nodes, ...overview.nodes, ...priorNodes].map((node) => [node.nodeCode, node])).values()];
  sharedRegistry.note = "已包含前章实际草稿导入使用的稳定代码，便于跨章复用；候选结构与发布仍由L确认。";
}
const chapters = [
  {number:2, folder:"ch02", word:"第二章", title:"进程与线程", key:"process", range:"2.1—2.5"},
  {number:3, folder:"ch03", word:"第三章", title:"内存管理", key:"memory", range:"3.1—3.3"},
  {number:4, folder:"ch04", word:"第四章", title:"文件管理", key:"files", range:"4.1—4.4"},
  {number:5, folder:"ch05", word:"第五章", title:"输入／输出（I/O）管理", key:"io", range:"5.1—5.4"}
];

async function copy(source, target) {
  await mkdir(path.dirname(target), { recursive: true });
  await copyFile(source, target);
}
async function copyTree(source, target) {
  await mkdir(target, { recursive: true });
  for (const entry of await readdir(source, { withFileTypes: true })) {
    const from = path.join(source, entry.name), to = path.join(target, entry.name);
    if (entry.isDirectory()) await copyTree(from, to);
    else if (entry.isFile()) await copy(from, to);
  }
}
const sourceAsset = "D:/Codex/tmp/codex-clipboard-2f4061d6-041f-47da-aa72-e53f6eb082b1.png";
const ready = ["J-开工包", "A-开工包"];
for (const name of ready) {
  try { await access(path.join(destination, name)); if (!refreshReference) throw new Error(`Output already exists: ${name}; choose a new output directory or --refresh-reference`); }
  catch (error) { if (error.code !== "ENOENT") throw error; }
}
for (const member of ["J", "A"]) {
  const root = path.join(destination, `${member}-开工包`);
  let workExists = true;
  try { await access(path.join(root, "work")); } catch { workExists = false; }
  if (!remainingChapters && !workExists) await copyTree(path.join(content, "starter-templates", member), path.join(root, "work"));
  if (!remainingChapters) await mkdir(path.join(root, "work/assets"), { recursive: true });
  if (member === "A") {
    let experimentWorkExists = true;
    try { await access(path.join(root, "experiment-work")); } catch { experimentWorkExists = false; }
    if (!remainingChapters && !experimentWorkExists) await copyTree(path.join(content, "starter-templates/A-experiments"), path.join(root, "experiment-work"));
    if (!remainingChapters) {
      await mkdir(path.join(root, "experiment-work/assets"), { recursive: true });
      await mkdir(path.join(root, "experiment-work/experiment-files"), { recursive: true });
    }
  }
  if (remainingChapters) for (const chapter of chapters) {
    const chapterRoot = path.join(root, "work", chapter.folder);
    let chapterExists = true;
    try { await access(chapterRoot); } catch { chapterExists = false; }
    if (!chapterExists) {
      await copyTree(path.join(content, "starter-templates/continuation", member), chapterRoot);
      await mkdir(path.join(chapterRoot, "assets"), { recursive: true });
      const primaryFile = member === "J" ? "knowledge.json" : "questions.json";
      const pack = JSON.parse(await readFile(path.join(chapterRoot, primaryFile), "utf8"));
      pack.batchCode = `batch.${member.toLowerCase()}.${chapter.key}.chapter.001`;
      pack.note = `${chapter.word} ${chapter.title}（${chapter.range}）空模板；正式交付须完成实际内容及自审。`;
      await saveJson(path.join(chapterRoot, primaryFile), pack);
      const secondFile = member === "J" ? "chapter-plan.json" : "application-questions.json";
      const secondary = JSON.parse(await readFile(path.join(chapterRoot, secondFile), "utf8"));
      if (member === "J") secondary.chapterCode = `sec.${chapter.key}`;
      else secondary.batchCode = `${pack.batchCode}.application`;
      await saveJson(path.join(chapterRoot, secondFile), secondary);
      await writeFile(path.join(chapterRoot, "交接说明.md"), `# ${member} ${chapter.word}交接\n\n章节：${chapter.title}；范围：${chapter.range}；版本：待填写\n\n## 实际覆盖\n\n按节列代码、教材／答案页码、题型组或教学块、未覆盖项；从实际JSON统计数量。\n\n## J/A 对齐\n\n所用节点与单元版本、增改清单、已核对映射：待填写。\n\n## 待 L 决定\n\n仅列确实未能解决的条目、依据及建议；无则写无。\n\n## 交付状态\n\n空模板，未开始；正式交付时替换为实际结果。只交资源文件，由 L 导入、接入和发布。\n`, "utf8");
      await writeFile(path.join(chapterRoot, "自审记录.md"), `# ${member} ${chapter.word}自审\n\n状态：未执行（空模板）。\n\n按包内 ${member === "J" ? "15-J" : "16-A"} 自审流程实际核对教材、内容、映射及附件；记录实际命令与结果、修改项和待 L 决定事项。${member === "J" ? "另核对单元目标、先修和阅读块顺序。" : "另核对综合题小问、参考解、评分点与分值。"}\n`, "utf8");
    }
    if (member === "A") {
      const experimentWork = path.join(root, "experiment-work", chapter.folder);
      let exists = true;
      try { await access(experimentWork); } catch { exists = false; }
      if (!exists) {
        await copyTree(path.join(content, "starter-templates/continuation/A-experiments"), experimentWork);
        await mkdir(path.join(experimentWork, "experiment-files"), { recursive: true });
        const pack = await load("starter-templates/continuation/A-experiments/experiment-maintenance.json");
        pack.batchCode = `batch.a.experiments.${chapter.key}.001`;
        pack.note = `${chapter.word}实验实际维护；保留原questionId，补框架、参考解、真实用例和映射。无适用实验则不提交空包。`;
        await saveJson(path.join(experimentWork, "experiment-maintenance.json"), pack);
        await writeFile(path.join(experimentWork, "交接说明.md"), `# A ${chapter.word}实验维护\n\n原快照版本：见 reference/experiments/inventory.json（非实时数据库）。\n\n## 本批范围与修订\n\n按原 questionId 列题目、实际修订文件、框架语言、节点／单元。不能仅用盘点说明代替修订。\n\n## 验证\n\n逐项列编译运行命令、参考解／错误解／边界预期与实际结果；未运行写具体缺失条件。\n\n## 待 L 处理\n\n只列平台接入和确实缺少的执行条件；版本：待填写；空模板未完成。\n`, "utf8");
        await writeFile(path.join(experimentWork, "自审记录.md"), "# A 实验自审\n\n状态：未执行（空模板）。按16文档完成文件、框架、参考实现、用例、映射及可用环境下的运行检查后，填写真实结果。\n", "utf8");
      }
    }
  }
  await copyTree(path.join(workspace, "docs/教材驱动课程重构"), path.join(root, "docs/教材驱动课程重构"));
  await copy(path.join(content, "starter-templates/字段填写速查.md"), path.join(root, "字段填写速查.md"));
  await saveJson(path.join(root, "shared/overview-authoring.json"), overview);
  await saveJson(path.join(root, "shared/sections.json"), sections);
  await saveJson(path.join(root, "shared/nodes.json"), sharedRegistry);
  if (remainingChapters) await saveJson(path.join(root, "shared/chapters.json"), { chapters });
  await saveJson(path.join(root, "shared/sources.json"), sources);
  await copy(path.join(content, "catalog/demos.json"), path.join(root, "shared/demos.json"));
  for (const file of ["knowledge.sample.json", "questions.sample.json", "units.sample.json", "chapter-plan.sample.json", "application-questions.sample.json", "handoff.template.md"]) {
    await copy(path.join(content, "samples", file), path.join(root, "reference/samples", file));
  }
  const nested = path.join(root, "content/operating-systems/wangdao-2025");
  for (const folder of ["catalog", "samples", "knowledge", "questions", "units", "handoffs", "assets", "batches", "verification"]) {
    await copyTree(path.join(content, folder), path.join(nested, folder));
  }
  await copy(path.join(content, "README.md"), path.join(nested, "README.md"));
  try { await copy(sourceAsset, path.join(root, "reference/第一章目录.png")); }
  catch (error) { if (error.code !== "ENOENT") throw error; }
  await copy(path.join(workspace, "scripts/check-member-package.mjs"), path.join(root, "tools/check-member-package.mjs"));
  const memberDelivery = member === "J" ? "J-第一章-知识-001" : "A-第一章-题目-001";
  const memberReviewFlow = member === "J" ? "15-J交付前审查流程.md" : "16-A交付前审查流程.md";
  const experimentNote = member === "A" ? `
## 独立实验维护

实验模板在 experiment-work/，原资料在 reference/experiments/。只填写实际维护范围，把修订代码和用例放 experiment-work/experiment-files/；完成后另交 A-实验维护-001。不与整章题目混包，不在每章重复全部 48 项盘点，也不以修完全部实验作为第一章交付前提。

可选文件检查：\`node tools/check-member-package.mjs . --work-dir experiment-work\`。空模板检查额外加 \`--allow-empty\`，不表示实际完成。
` : "";
  const readme = remainingChapters ? `# ${member} 开工包：第二至五章按章交付

依次整理第二章进程与线程、第三章内存管理、第四章文件管理、第五章I/O管理。一章完成即交付并继续下一章，不等待 L 导入；第一章现有成果保留。

1. 先读 [19-连续交付任务](docs/教材驱动课程重构/19-第二至五章交付任务.md)，再读 ${member === "J" ? "02-J指南与15-J自审流程" : "03-A指南、07-实验指南、16-A自审和20-综合应用题规范"}。
2. 编辑 work/ch02 至 work/ch05。${member === "J" ? "每章交 knowledge.json、chapter-plan.json、图片、交接说明和自审。先把节点及单元草案直接给 A 对齐。" : "每章交 questions.json、application-questions.json、图片、交接说明和自审。使用 J 的本章节点及单元，不漏综合应用题。"}
3. 每章打包为 ${member}-第二章-${member === "J" ? "知识" : "题目"}-001，之后替换章名。只回交本章真实成果，不带其他章空模板、shared/ 或全量参考快照。
4. 文件检查：\`node tools/check-member-package.mjs . --work-dir work/ch02${member === "A" ? ' --with-j "../J-第二章-知识-001"' : ""}\`。仅检查空模板才加 --allow-empty。
${member === "A" ? '5. experiment-work/ch02 至 ch05 另交 A-第二章-实验维护-001 等。实际补 originalCode 起始框架、answer参考解及真实用例；保留原 questionId。参考快照在 reference/experiments。检查命令换成 --work-dir experiment-work/ch02，并继续 --with-j 引用J。' : '5. 单元目标、先修、阅读块覆盖、演示建议与章节目录自行核对，L最终确认并接入系统。'}

chapter-plan 与综合题是离线整理协议，当前服务尚不直接导入／自动主观评分；现有客观题与实验修订沿用已实现协议。J/A 只交资源包，不需要平台账号、数据库、GitHub或 L 提供教材截图。
` : `# ${member} 开工包（按章交付版）

当前任务：重新依据自己的《王道操作系统》教材整理第一章“计算机系统概述”全章（1.1—1.7），正式包名为 ${memberDelivery}。

**现有 1.1 成果及退修版不采用，不复用、不合并、不导入。** 本章连同 1.1 一起重新整理。公共预分配编号、协议和系统工具继续使用，空模板不是已完成内容。

1. 阅读 [按章交付与重整要求](docs/教材驱动课程重构/14-按章交付与第一章重整要求.md) 和 [开工与交包说明](docs/教材驱动课程重构/08-JA开工与资源包交接.md)。
2. 查 [第一章编号清单](shared/overview-authoring.json) 与 [字段填写速查](字段填写速查.md)。正文、关系及题目依据教材核实，缺少或拆合编号告诉 L/A/J，不声称预分配表就是 J 的最终成果。
3. 编辑 work/ 中的整章文件，把图片放 work/assets/，在交接说明按 1.1—1.7 填覆盖表。内部可依小节制作，正式完成后一次交整章包。
4. 交付前完整执行 [交付前审查流程](docs/教材驱动课程重构/${memberReviewFlow})，先修改实际资源再复查，随包附真实自审记录，或写入交接说明的自审一节。
5. 把 work/ 重命名为 ${memberDelivery} 并压缩交 L。reference/、shared/、协议样例和其他章样板不作为本章成果回交。

J/A 可以并行，不必同时交包。过程中只沟通格式、编号和材料问题；L 负责整章校验、节点与题目映射汇总、导入、活动编排和学生验收。无需平台账号、数据库、GitHub 或等待 L 提供正文截图。

可选文件检查：\`node tools/check-member-package.mjs .\`。空模板检查额外加 \`--allow-empty\`，不能据此认定整章完成。
${experimentNote}`;
  await writeFile(path.join(root, "开始这里.md"), readme, "utf8");
}

const aRoot = path.join(destination, "A-开工包");
const experimentRoot = path.join(aRoot, "reference/experiments");
await mkdir(experimentRoot, { recursive: true });
const inventory = {
  schemaVersion:"oslab-experiment-snapshot/1.0",
  source:"数据库文件/oslab_db.sql",
  sourceSha256:createHash("sha256").update(sql).digest("hex"),
  sourceStatus:"REPOSITORY_SQL_SNAPSHOT_NOT_LIVE_DATABASE",
  note:"仅导出实验教学字段，不包含用户、学生资料和提交记录。L在导入前与当前运行库核对。参考答案及代码是原快照材料，未运行验证。",
  total:experiments.length,
  experiments:experiments.map(({ referenceAnswer, originalCode, content:body, judgeCase, judgeConfig, ...item }) => ({
    ...item,
    directory:`reference/experiments/${item.questionId}`,
    hasReferenceAnswer:Boolean(referenceAnswer), hasOriginalCode:Boolean(originalCode),
    judgeCaseCount:judgeCase.length,
    reviewHints: [
      ...(!originalCode ? ["缺少原始代码／框架"] : []),
      ...(!referenceAnswer ? ["缺少参考解"] : []),
      ...(judgeCase.some((test) => test.input === "123" && test.output === "123") ? ["用例含123→123，需核对是否为占位用例"] : [])
    ]
  }))
};
await saveJson(path.join(experimentRoot, "inventory.json"), inventory);
const summary = ["# 已有编程实验教学资料快照", "", inventory.note, "", `共 ${experiments.length} 个有效实验，来源为仓库 SQL 快照，非当前运行库。`, "", "| 原 ID | 实验名称 | 标签 | 用例数 |", "| --- | --- | --- | ---: |"];
for (const experiment of experiments) {
  const root = path.join(experimentRoot, experiment.questionId);
  await saveJson(path.join(root, "experiment.json"), experiment);
  await saveJson(path.join(root, "judge-cases.json"), experiment.judgeCase);
  await saveJson(path.join(root, "judge-config.json"), experiment.judgeConfig);
  await writeFile(path.join(root, "题面.md"), experiment.content + "\n", "utf8");
  await writeFile(path.join(root, "原始代码.txt"), experiment.originalCode, "utf8");
  await writeFile(path.join(root, "参考解.txt"), experiment.referenceAnswer, "utf8");
  summary.push(`| ${experiment.questionId} | ${experiment.title.replaceAll("|", "\\|")} | ${experiment.tags.join("、")} | ${experiment.judgeCase.length} |`);
}
await writeFile(path.join(experimentRoot, "实验清单.md"), summary.join("\n") + "\n", "utf8");

const manifest = {
  date:"2026-10-17", preparedBy:"L", deliveryModel:"CHAPTER_PACKAGES_J_A_SUBMIT_FILES_L_IMPORTS",
  status:"ACTIVE_CHAPTER_DELIVERY", contentDeliveryGranularity:"CHAPTER",
  bundles:ready,
  chapter:"第一章 计算机系统概述",
  overviewNodes:overview.nodes.length, overviewUnits:overview.units.length,
  experimentSnapshotCount:experiments.length,
  experimentSource:inventory.source, experimentSourceSha256:inventory.sourceSha256,
  textbookAvailableToMembers:true,
  firstDelivery:"J交第一章整章知识包，A交第一章整章题目包；第一章含1.1重新依据教材整理，不采用旧1.1成果",
  packageNames:{J:"J-第一章-知识-001", A:"A-第一章-题目-001", experiments:"A-实验维护-001"},
  batchCodes:{J:"batch.j.overview.chapter.001", A:"batch.a.overview.chapter.001", experiments:"batch.a.experiments.001"},
  reviewProcedures:{J:"docs/教材驱动课程重构/15-J交付前审查流程.md", A:"docs/教材驱动课程重构/16-A交付前审查流程.md"},
  reviewRecord:"实际自审记录可独立为自审记录.md，或写入对应交接说明；先修改资源再复查",
  priorSection11ContentPolicy:"NOT_ADOPTED_DO_NOT_REUSE_MERGE_OR_IMPORT",
  experimentDelivery:"SEPARATE_MAINTENANCE_PACKAGE_NOT_REPEATED_PER_CHAPTER",
  note:"未导入数据库，未执行原SQL，未运行实验。"
};
if (remainingChapters) Object.assign(manifest, {
  date:"2026-10-20", chapter:"第二章至第五章逐章交付", chapters,
  firstDelivery:"第二章整章；J交知识与单元草案，A交客观题和综合应用题，实验按章独立修订；完成即继续下一章",
  packageNames:{J:"J-第二章-知识-001（逐章替换章名）", A:"A-第二章-题目-001（逐章替换章名）", experiments:"A-第二章-实验维护-001（逐章替换章名）"},
  batchCodes:{J:"batch.j.process.chapter.001", A:"batch.a.process.chapter.001", application:"batch.a.process.chapter.001.application", experiments:"batch.a.experiments.process.001"},
  priorSection11ContentPolicy:"FIRST_CHAPTER_EXISTING_RESULTS_PRESERVED",
  continuationGuide:"docs/教材驱动课程重构/19-第二至五章交付任务.md",
  newOfflineFormats:{chapterPlan:"oslab-chapter-plan/1.0", applicationQuestions:"oslab-application-authoring/1.0"},
  integrationStatus:"NEW_OFFLINE_FORMATS_AWAIT_SYSTEM_INTEGRATION_NO_SUBJECTIVE_AUTO_GRADING",
  experimentDelivery:"SEPARATE_ACTUAL_REVISIONS_BY_PRIMARY_CHAPTER_NO_REPEATED_FULL_INVENTORY"
});
await saveJson(path.join(destination, "handoff-manifest.json"), manifest);
console.log(`已准备 J/A 开工目录：${destination}`);
console.log(remainingChapters ? `已准备第二至五章工作目录、综合题及单元草案模板；实验快照 ${experiments.length} 项。` : `第一章预分配 ${overview.nodes.length} 个节点、${overview.units.length} 个单元；实验快照 ${experiments.length} 项。`);
