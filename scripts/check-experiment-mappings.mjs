#!/usr/bin/env node
/**
 * check-experiment-mappings.mjs —— 离线校验：41 个实验的 nodes.json / units.json
 *
 * 独立于生成器：只读落盘后的 JSON 文件 + verification/ 中的公共身份与单元快照，
 * 不复用生成器里的任何数据结构，避免「生成器和校验器犯同一个错」。
 *
 * 硬闸门（任一不满足 → 打印全部错误并以退出码 1 结束）：
 *   1. 实验目录集合 == 41 个真实实验 id（5 条垃圾重复行 + 2 条软删除行必须不在其中）
 *   2. 每个目录都有 nodes.json 与 units.json，且 questionId == 目录名（字符串，不做 Number 转换）
 *   3. 所有 nodeCode ∈ knowledge_node_registry
 *   4. 所有 nodeName 逐字 == registry.name
 *   5. 所有 unitCode ∈ 已发布 learning_unit 清单
 *   6. 每实验恰好 1 个 PRIMARY，RELATED 数 1..3
 *   7. 无重复 (questionId, nodeCode)
 *   8. 无重复 (questionId, unitCode)
 *   9. required 一律 false；completionRule ∈ {PASS, SUBMIT} 且与 Tier 一致（B=SUBMIT，A/C=PASS）
 *  10. weight 为 (0,1] 的有限数；PRIMARY weight == 1.0
 *  11. reason 非空、长度 >= 20、不含「按主题映射」这类空话
 *  12. Tier B 目录必须有 judge-mode.json(LOCAL_BUILD) 且**不得**有 judge-cases.json
 *
 * 用法：node scripts/check-experiment-mappings.mjs
 */
import { readFileSync, existsSync, readdirSync } from "node:fs";
import { join } from "node:path";

const REPO = process.cwd();
const FIXTURES = join(REPO, "content", "operating-systems", "wangdao-2025", "verification", "experiment-mappings");
const EXP_ROOT = join(
  REPO, "content", "operating-systems", "wangdao-2025", "experiments",
);

const errors = [];
const warns = [];
function err(msg) { errors.push(msg); }

/* ---------- 期望的 41 个真实实验 id（字符串） ---------- */
const EXPECTED = [
  // 第一章 5
  "1917131319528452098", "1930153941795131394", "1930154079598989313",
  "1930154331844431873", "1930154460768948226",
  // 第二章 8
  "1908449571747745793", "1908532874240593922", "1908538371526131713",
  "1908539177407119361", "1908539875976839170", "1908540360305704962",
  "1908540572415852545", "1908540698916061185",
  // 第三章 13（无 3.11）
  "1908900051330306049", "1930154686162456577", "1930154848414912513",
  "1930154980048949249", "1930155119123681282", "1930155245821022209",
  "1930155360556208129", "1930155484338507777", "1930155681017810946",
  "1930155792691154945", "1930155905954140162", "1930156027966443521",
  "1930156143783759874",
  // 第四章 15
  "1930156360117571585", "1930156493454495745", "1930156605618573314",
  "1930156740377366529", "1930156844614209538", "1930156972624367618",
  "1930157095085461505", "1930157212584693762", "1930157404163723265",
  "1930157516164222977", "1930157629511094274", "1930157740261691394",
  "1930157867273605121", "1930157984642813953", "1930158086405017602",
];
const FORBIDDEN = [
  "1918931948987031554", "1918932928252157953", "1918933052214812674",
  "1918934540198682625", "1918938841746878466",
  "1918945654303125506", "1920028942673477633",
];
const TIER_C = new Set(["1917131319528452098", "1930154079598989313"]);
const TIER_B = new Set([
  "1930154331844431873", "1908540698916061185", "1930155905954140162",
  "1930156027966443521", "1930156360117571585",
]);
if (EXPECTED.length !== 41) err(`内部错误：EXPECTED 长度 ${EXPECTED.length} != 41`);
if (new Set(EXPECTED).size !== 41) err("内部错误：EXPECTED 有重复 id");
for (const id of FORBIDDEN) {
  if (EXPECTED.includes(id)) err(`内部错误：禁止 id 出现在 EXPECTED：${id}`);
}

/* ---------- 解析 mysql -t 导出 ---------- */
function parseTable(file, keyCol) {
  if (!existsSync(file)) { err(`缺少导出文件：${file}`); return new Map(); }
  const m = new Map();
  const dups = [];
  for (const raw of readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line.startsWith("|")) continue;
    const cells = line.split("|").slice(1, -1).map((s) => s.trim());
    if (cells.length < 2 || cells[0] === keyCol) continue;
    if (m.has(cells[0])) dups.push(cells[0]);
    m.set(cells[0], cells);
  }
  for (const d of dups) err(`${file} 中 ${keyCol} 重复：${d}`);
  return m;
}

const registry = parseTable(join(FIXTURES, "registry.txt"), "nodeCode");
const units = parseTable(join(FIXTURES, "units.txt"), "unitCode");
if (registry.size !== 199) err(`registry.txt 节点数 ${registry.size} != 199（实测 DB 为 199）`);
// 实测 DB：courseCode='course.operating-systems' AND isDelete=0 → 104 行，
// 全部 reviewStatus='PUBLISHED' 且 publishedJson 非 NULL。计划 md 写的 105 比实际多 1。
if (units.size !== 104) err(`units.txt 单元数 ${units.size} != 104（实测 DB 为 104，全部 PUBLISHED）`);

/* ---------- 枚举磁盘上的实验目录 ---------- */
const found = new Map(); // id -> dir
if (!existsSync(EXP_ROOT)) err(`实验根目录不存在：${EXP_ROOT}`);
else {
  for (const batch of readdirSync(EXP_ROOT)) {
    const ef = join(EXP_ROOT, batch, "experiment-files");
    if (!existsSync(ef)) continue;
    for (const id of readdirSync(ef)) {
      const dir = join(ef, id);
      if (found.has(id)) err(`实验 id 在多个批次目录重复出现：${id}`);
      found.set(id, dir);
    }
  }
}
for (const id of EXPECTED) {
  if (!found.has(id)) err(`缺少实验目录：${id}`);
}
for (const id of found.keys()) {
  if (FORBIDDEN.includes(id)) err(`垃圾/软删除实验目录出现在交付集中：${id}`);
  else if (!EXPECTED.includes(id)) err(`出现不在 41 个真实实验清单中的目录：${id}`);
}

/* ---------- 逐实验校验 ---------- */
const globalNodePairs = new Set();
const globalUnitPairs = new Set();
let checked = 0;
let totalNodeMappings = 0;
let totalUnitMappings = 0;
const tierCount = { A: 0, B: 0, C: 0 };

function readJson(file, label) {
  if (!existsSync(file)) { err(`${label}：缺少文件 ${file}`); return null; }
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch (e) {
    err(`${label}：JSON 解析失败 ${file} —— ${e.message}`);
    return null;
  }
}

for (const id of EXPECTED) {
  const dir = found.get(id);
  if (!dir) continue;
  checked++;
  const tier = TIER_B.has(id) ? "B" : TIER_C.has(id) ? "C" : "A";
  tierCount[tier]++;

  /* --- nodes.json --- */
  const nj = readJson(join(dir, "nodes.json"), id);
  if (nj) {
    if (nj.questionId !== id) err(`${id}: nodes.json questionId=${JSON.stringify(nj.questionId)} != 目录名`);
    if (typeof nj.questionId !== "string") err(`${id}: nodes.json questionId 不是字符串（19 位雪花 id 必须当字符串）`);
    const ms = nj.nodeMappings;
    if (!Array.isArray(ms) || ms.length === 0) err(`${id}: nodeMappings 不是非空数组`);
    else {
      totalNodeMappings += ms.length;
      const primaries = ms.filter((m) => m.role === "PRIMARY");
      const relateds = ms.filter((m) => m.role === "RELATED");
      if (primaries.length !== 1) err(`${id}: PRIMARY 数 ${primaries.length} != 1`);
      if (relateds.length < 1 || relateds.length > 3) err(`${id}: RELATED 数 ${relateds.length} 不在 1..3`);
      if (primaries.length + relateds.length !== ms.length) err(`${id}: 存在 role 既非 PRIMARY 也非 RELATED 的映射`);
      for (const m of ms) {
        const row = registry.get(m.nodeCode);
        if (!row) err(`${id}: nodeCode 不在 registry：${m.nodeCode}`);
        else if (m.nodeName !== row[1]) {
          err(`${id}: nodeName 与 registry 不等 —— ${m.nodeCode}: 文件="${m.nodeName}" registry="${row[1]}"`);
        }
        const pair = `${id}|${m.nodeCode}`;
        if (globalNodePairs.has(pair)) err(`重复 (questionId,nodeCode)：${pair}`);
        globalNodePairs.add(pair);
        if (!(typeof m.weight === "number" && Number.isFinite(m.weight) && m.weight > 0 && m.weight <= 1)) {
          err(`${id}: ${m.nodeCode} weight 非法：${JSON.stringify(m.weight)}`);
        }
        if (m.role === "PRIMARY" && m.weight !== 1.0) err(`${id}: PRIMARY ${m.nodeCode} weight != 1.0`);
        if (typeof m.reason !== "string" || m.reason.trim().length < 20) {
          err(`${id}: ${m.nodeCode} reason 为空或过短（<20 字符）`);
        } else if (/按主题映射|主题相关|主题对应|^略$|TODO/.test(m.reason)) {
          err(`${id}: ${m.nodeCode} reason 含空话/占位：${m.reason}`);
        }
      }
    }
  }

  /* --- units.json --- */
  const uj = readJson(join(dir, "units.json"), id);
  if (uj) {
    if (uj.questionId !== id) err(`${id}: units.json questionId=${JSON.stringify(uj.questionId)} != 目录名`);
    if (typeof uj.questionId !== "string") err(`${id}: units.json questionId 不是字符串`);
    const ms = uj.unitMappings;
    if (!Array.isArray(ms) || ms.length === 0) err(`${id}: unitMappings 不是非空数组`);
    else {
      totalUnitMappings += ms.length;
      for (const m of ms) {
        if (!units.has(m.unitCode)) err(`${id}: unitCode 不在已发布清单：${m.unitCode}`);
        const pair = `${id}|${m.unitCode}`;
        if (globalUnitPairs.has(pair)) err(`重复 (questionId,unitCode)：${pair}`);
        globalUnitPairs.add(pair);
        if (m.required !== false) err(`${id}: ${m.unitCode} required=${JSON.stringify(m.required)}，必须一律 false`);
        if (m.completionRule !== "PASS" && m.completionRule !== "SUBMIT") {
          err(`${id}: ${m.unitCode} completionRule 非法：${JSON.stringify(m.completionRule)}`);
        } else {
          const expect = tier === "B" ? "SUBMIT" : "PASS";
          if (m.completionRule !== expect) {
            err(`${id}: Tier ${tier} 应为 completionRule=${expect}，实为 ${m.completionRule}`);
          }
        }
      }
    }
  }

  /* --- Tier B 不得伪造自动判题 --- */
  if (tier === "B") {
    const jm = readJson(join(dir, "judge-mode.json"), id);
    if (jm && jm.judgeMode !== "LOCAL_BUILD") err(`${id}: Tier B judgeMode=${jm.judgeMode} != LOCAL_BUILD`);
    if (jm && (typeof jm.reason !== "string" || jm.reason.trim().length < 20)) err(`${id}: Tier B judge-mode.json reason 为空或过短`);
    if (existsSync(join(dir, "judge-cases.json"))) err(`${id}: Tier B 目录存在 judge-cases.json（不得伪造自动判题）`);
    const cj = readJson(join(dir, "cases.json"), id);
    if (cj && Array.isArray(cj.cases) && cj.cases.length !== 0) err(`${id}: Tier B cases.json 非空（${cj.cases.length} 条）`);
  } else {
    if (!existsSync(join(dir, "judge-cases.json"))) warns.push(`${id}: Tier ${tier} 尚无 judge-cases.json（harness 可能由其他 agent 并行交付中）`);
  }
}

/* ---------- 输出 ---------- */
console.log("=== check-experiment-mappings ===");
console.log(`registry nodes            : ${registry.size} (期望 199)`);
console.log(`published units           : ${units.size} (期望 104；计划 md 写 105，实测 DB 为 104)`);
console.log(`experiment dirs on disk   : ${found.size}`);
console.log(`experiments checked       : ${checked} / 41`);
console.log(`tier                      : A=${tierCount.A} B=${tierCount.B} C=${tierCount.C}`);
console.log(`nodeMappings total        : ${totalNodeMappings}`);
console.log(`unitMappings total        : ${totalUnitMappings}`);
console.log(`distinct nodeCodes used   : ${new Set([...globalNodePairs].map((p) => p.split("|")[1])).size}`);
console.log(`distinct unitCodes used   : ${new Set([...globalUnitPairs].map((p) => p.split("|")[1])).size}`);
console.log(`duplicate (qid,nodeCode)  : ${totalNodeMappings - globalNodePairs.size}`);
console.log(`duplicate (qid,unitCode)  : ${totalUnitMappings - globalUnitPairs.size}`);
if (warns.length) {
  console.log(`\nWARN (${warns.length}):`);
  for (const w of warns) console.log("  - " + w);
}
if (errors.length) {
  console.log(`\nFAIL (${errors.length} errors):`);
  for (const e of errors) console.log("  - " + e);
  process.exit(1);
}
console.log("\nMAPPING CHECK OK: 0 errors");
process.exit(0);
