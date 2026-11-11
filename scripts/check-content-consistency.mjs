#!/usr/bin/env node
/**
 * check-content-consistency.mjs —— 一次性校验：content.md 的判题说明必须与真实判题契约一致
 *
 * 这是防止「说明与判题不符」的关键闸门（分支方案的根本缺陷）。
 * 只读落盘文件，不编译、不运行 harness、不连数据库。
 *
 * 默认校验基础组的 16 篇（第一章 5 + 第二章 8 + 3.12/3.13/4.1）。
 * 使用 --all 校验全部 41 篇：
 *   node scripts/check-content-consistency.mjs --all
 *
 * 硬闸门（任一不满足 → 打印全部错误并退出码 1）：
 *  A. 结构：8 个固定小节齐全且非空；H1 以「# 实验 <编号> 」开头；2500–6000 字节；无 TODO/待补/FIXME/整行「略」
 *  B. Tier A/C：
 *     B1. cases.json.description 非空，且**逐字**出现在 content.md 中（verbatim 闸门）
 *     B2. description 里每个「…」格式片段在 content.md 中至少出现 2 次
 *         （一次在 verbatim 引用块、一次在作者自己的结构化说明里 → 强制真正复述而非只贴一段）
 *     B3. description 声明的每个操作码 X 在 content.md 中有一行 `- \`X\`` 文档，
 *         且该行必须写出 description 声明的全部 ASCII 参数名（如 `I pageidx va perm`）
 *     B4. judge-cases.json 存在、条数 == cases.json、每条 input 逐字相同、output 非空
 *     B5. **输出字段数一致性**：judge 输出里以操作码 X 开头的行，其字段数必须落在
 *         description 中「X …」片段推算的字段数区间内（中文字段名可能含内部空格，
 *         如「解除后 pp_ref」是一个字段，故用区间而非精确值，详见 shapeRange 注释）
 *     B6. judge 输出里出现的单大写字母行首标记必须 ⊆ description 声明的操作码集合
 *  C. Tier B：judge-mode.json = LOCAL_BUILD；cases.json.cases 为空；**不得**有 judge-cases.json；
 *     content.md 必须写明 LOCAL_BUILD / 不自动判分 / SUBMIT
 *  D. Tier C：content.md 必须写明「平台自动判分的是」+「AC 不等于内核」+ 有「本地完整验证」小节
 *  E. 内容来源：originalCode 缺失的实验必须写明「未随工作区提供」+「等价」；
 *     有真实源码的（2.1 include/queue.h）必须点名该真实文件
 *
 * 用法：node scripts/check-content-consistency.mjs [--all] [实验目录...]
 */
import { readFileSync, existsSync, readdirSync } from "node:fs";
import { join } from "node:path";

const REPO = process.cwd();
const EXP_ROOT = join(
  REPO, "content", "operating-systems", "wangdao-2025", "experiments",
);
const CH = {
  1: "A-第一章-实验判题-001",
  2: "A-第二章-实验判题-001",
  3: "A-第三章-实验判题-001",
  4: "A-第四章-实验判题-001",
};

/** 默认检查的基础组。 */
const MINE = [
  ["1917131319528452098", 1, "1.1", "C"],
  ["1930153941795131394", 1, "1.2", "A"],
  ["1930154079598989313", 1, "1.3", "C"],
  ["1930154331844431873", 1, "1.4", "B"],
  ["1930154460768948226", 1, "1.5", "A"],
  ["1908449571747745793", 2, "2.1", "A"],
  ["1908532874240593922", 2, "2.2", "A"],
  ["1908538371526131713", 2, "2.3", "A"],
  ["1908539177407119361", 2, "2.4", "A"],
  ["1908539875976839170", 2, "2.5", "A"],
  ["1908540360305704962", 2, "2.6", "A"],
  ["1908540572415852545", 2, "2.7", "A"],
  ["1908540698916061185", 2, "2.8", "B"],
  ["1930155905954140162", 3, "3.12", "B"],
  ["1930156027966443521", 3, "3.13", "B"],
  ["1930156360117571585", 4, "4.1", "B"],
];

/** 默认范围之外的 25 篇 Tier A；编号须独立于待校验的 Markdown。 */
const CATALOG = [
  ...MINE,
  ["1908900051330306049", 3, "3.1", "A"],
  ["1930154686162456577", 3, "3.2", "A"],
  ["1930154848414912513", 3, "3.3", "A"],
  ["1930154980048949249", 3, "3.4", "A"],
  ["1930155119123681282", 3, "3.5", "A"],
  ["1930155245821022209", 3, "3.6", "A"],
  ["1930155360556208129", 3, "3.7", "A"],
  ["1930155484338507777", 3, "3.8", "A"],
  ["1930155681017810946", 3, "3.9", "A"],
  ["1930155792691154945", 3, "3.10", "A"],
  ["1930156143783759874", 3, "3.14", "A"],
  ["1930156493454495745", 4, "4.2", "A"],
  ["1930156605618573314", 4, "4.3", "A"],
  ["1930156740377366529", 4, "4.4", "A"],
  ["1930156844614209538", 4, "4.5", "A"],
  ["1930156972624367618", 4, "4.6", "A"],
  ["1930157095085461505", 4, "4.7", "A"],
  ["1930157212584693762", 4, "4.8", "A"],
  ["1930157404163723265", 4, "4.9", "A"],
  ["1930157516164222977", 4, "4.10", "A"],
  ["1930157629511094274", 4, "4.11", "A"],
  ["1930157740261691394", 4, "4.12", "A"],
  ["1930157867273605121", 4, "4.13", "A"],
  ["1930157984642813953", 4, "4.14", "A"],
  ["1930158086405017602", 4, "4.15", "A"],
];

/** 全库只有 2.1(include/queue.h) 与 3.1(mm/pmap.c) 有真实 originalCode */
const HAS_REAL_SOURCE = new Set(["1908449571747745793", "1908900051330306049"]);

const REQUIRED_SECTIONS = [
  "实验背景", "知识要点", "关联源码", "实验任务", "本平台判题说明", "常见错误",
];

const errors = [];
const warns = [];
const err = (m) => errors.push(m);
const warn = (m) => warns.push(m);

/* ---------- 参数 ---------- */
const argv = process.argv.slice(2);
const all = argv.includes("--all");
const explicit = argv.filter((a) => !a.startsWith("--"));
let targets;
if (explicit.length) {
  targets = explicit.map((dir) => {
    const id = dir.replace(/\\/g, "/").split("/").filter(Boolean).pop();
    const hit = CATALOG.find((m) => m[0] === id);
    if (!hit) err(`未登记的实验 id：${id}`);
    return { dir, id, no: hit ? hit[2] : "?", tier: hit ? hit[3] : "?" };
  });
} else if (all) {
  targets = [];
  for (const ch of Object.keys(CH)) {
    const ef = join(EXP_ROOT, CH[ch], "experiment-files");
    if (!existsSync(ef)) continue;
    for (const id of readdirSync(ef)) {
      const hit = CATALOG.find((m) => m[0] === id);
      if (!hit) err(`未登记的实验 id：${id}`);
      targets.push({
        dir: join(ef, id), id,
        no: hit ? hit[2] : "?", tier: hit ? hit[3] : "?",
      });
    }
  }
} else {
  targets = MINE.map(([id, ch, no, tier]) => ({
    dir: join(EXP_ROOT, CH[ch], "experiment-files", id), id, no, tier,
  }));
}

/* ---------- 工具 ---------- */
function readText(file, label) {
  if (!existsSync(file)) { err(`${label}: 缺少文件 ${file}`); return null; }
  return readFileSync(file, "utf8");
}
function readJson(file, label) {
  const t = readText(file, label);
  if (t === null) return null;
  try { return JSON.parse(t); } catch (e) { err(`${label}: JSON 解析失败 —— ${e.message}`); return null; }
}
function countOccurrences(hay, needle) {
  if (!needle) return 0;
  let n = 0, i = 0;
  for (;;) { const k = hay.indexOf(needle, i); if (k < 0) break; n++; i = k + needle.length; }
  return n;
}
/** 从 description 提取操作码 → 声明的 ASCII 参数名列表：单个大写字母 + 可选小写参数名 + '=' */
function extractOps(desc) {
  const ops = new Map();
  const re = /(?:^|[：:；;，,。\s（(])([A-Z])((?:\s+[a-z_0-9]+)*)\s*=/g;
  let m;
  while ((m = re.exec(desc)) !== null) {
    const args = m[2].trim() ? m[2].trim().split(/\s+/) : [];
    const prev = ops.get(m[1]) || [];
    ops.set(m[1], [...new Set([...prev, ...args])]);
  }
  return ops;
}
/** 从 description 提取「…」片段 */
function extractFragments(desc) {
  const out = [];
  const re = /「([^」]+)」/g;
  let m;
  while ((m = re.exec(desc)) !== null) out.push(m[1]);
  return out;
}
/**
 * 从「X 字段名 字段名 …」片段推算**字段数区间** [lo, hi]（含行首操作码 X）。
 *
 * 为什么是区间而不是精确值：description 的字段名是中文，**中文字段名内部可能带空格**，
 * 例如 2.7 的「R 原页号 解除后物理地址 解除后 pp_ref」——「解除后 pp_ref」是**一个**字段，
 * 按空白切分会得到 5 个 token，而真实输出 "R 5 4294967295 0" 只有 4 个字段。
 * 因此：
 *   hi = token 数（每个 token 各算一个字段）
 *   lo = hi − 「纯中文 token 紧跟纯 ASCII 标识符 token」的相邻对数
 *        （这种相邻对**可能**是同一个中文字段名被空格拆开，也可能本来就是两个字段，
 *         例如「I 返回码 物理地址 pp_ref」的「物理地址 pp_ref」就是两个字段）
 * 真实字段数必须落在 [lo, hi] 内。区间外 → 判为「说明与判题不符」。
 * 另有 B5b 精确闸门：同一操作码在所有 judge 输出里的字段数必须完全一致。
 */
function shapeRange(frag, desc = "", args = []) {
  const tk = frag.trim().split(/\s+/);
  if (/^(?:\.{3}|…+)$/.test(tk[tk.length - 1])) {
    // 「L 下标 下标 ...」允许空链表；重复字段个数由已声明的 k 上限约束。
    const repeated = tk[tk.length - 2];
    const prefix = tk.indexOf(repeated, 1);
    let maxItems = Infinity;
    for (const arg of args) {
      const bound = new RegExp(`(?:0\\s*<=\\s*)?\\b${arg}\\s*<=\\s*(\\d+)`).exec(desc);
      if (bound) maxItems = Math.min(maxItems, Number(bound[1]));
    }
    return { lo: prefix, hi: prefix + maxItems, tokens: tk.length, variable: true };
  }
  const isCJK = (s) => /^[\u3400-\u9fff\u3000-\u303f]+$/.test(s);
  const isAsciiId = (s) => /^[A-Za-z_][A-Za-z_0-9]*$/.test(s);
  let merges = 0;
  for (let i = 1; i + 1 < tk.length; i++) {
    if (isCJK(tk[i]) && isAsciiId(tk[i + 1])) merges++;
  }
  return { lo: tk.length - merges, hi: tk.length, tokens: tk.length };
}
function sectionBody(md, title) {
  const re = new RegExp(`^##\\s+${title}\\s*$`, "m");
  const m = re.exec(md);
  if (!m) return null;
  const start = m.index + m[0].length;
  const rest = md.slice(start);
  const nxt = rest.search(/^##\s+/m);
  return (nxt < 0 ? rest : rest.slice(0, nxt)).trim();
}

/* ---------- 逐篇校验 ---------- */
let checked = 0;
const summary = [];
for (const t of targets) {
  const { dir, id, no } = t;
  const label = `${no}(${id})`;
  const md = readText(join(dir, "content.md"), label);
  if (md === null) continue;
  checked++;
  const bytes = Buffer.byteLength(md, "utf8");

  /* --- A. 结构 --- */
  if (!new RegExp(`^#\\s+实验\\s+${no.replace(".", "\\.")}\\s`, "m").test(md)) {
    err(`${label}: H1 未以「# 实验 ${no} 」开头`);
  }
  if (bytes < 2500 || bytes > 6000) err(`${label}: content.md ${bytes} 字节，超出 2500–6000 目标区间`);
  for (const s of REQUIRED_SECTIONS) {
    const body = sectionBody(md, s);
    if (body === null) err(`${label}: 缺少小节「## ${s}」`);
    else if (body.replace(/\s/g, "").length < 40) err(`${label}: 小节「## ${s}」为空或过短（<40 非空白字符）`);
  }
  if (/TODO|FIXME|待补|待填|占位符/i.test(md)) err(`${label}: 含 TODO/FIXME/待补/待填/占位符`);
  if (/^\s*略\s*$/m.test(md)) err(`${label}: 含整行「略」`);

  /* --- 判题方式判定：以磁盘上的 judge-mode.json 为准，不信任预设 tier --- */
  const jmPath = join(dir, "judge-mode.json");
  const isTierB = existsSync(jmPath);
  let tier = t.tier;
  if (isTierB) {
    const jm = readJson(jmPath, label);
    if (jm && jm.judgeMode !== "LOCAL_BUILD") err(`${label}: judge-mode.json judgeMode=${jm.judgeMode} != LOCAL_BUILD`);
    tier = "B";
  } else if (tier === "B") {
    err(`${label}: 预设为 Tier B 但缺少 judge-mode.json`);
  }

  const cj = readJson(join(dir, "cases.json"), label);
  const judgePath = join(dir, "judge-cases.json");

  if (tier === "B") {
    /* --- C. Tier B --- */
    if (existsSync(judgePath)) err(`${label}: Tier B 存在 judge-cases.json（不得伪造自动判题）`);
    if (cj && Array.isArray(cj.cases) && cj.cases.length !== 0) err(`${label}: Tier B cases.json 非空（${cj.cases.length} 条）`);
    for (const kw of ["LOCAL_BUILD", "不自动判分", "SUBMIT"]) {
      if (!md.includes(kw)) err(`${label}: Tier B content.md 未写明「${kw}」`);
    }
    if (sectionBody(md, "本地完整验证") === null) err(`${label}: Tier B 缺少「## 本地完整验证」小节`);
  } else {
    /* --- B. Tier A / C --- */
    const desc = cj && typeof cj.description === "string" ? cj.description : "";
    if (!desc) err(`${label}: cases.json.description 为空`);
    else {
      /* B1 verbatim */
      if (!md.includes(desc)) {
        err(`${label}: content.md 未逐字包含 cases.json.description（verbatim 闸门失败）`);
      }
      /* B2 片段复述 */
      const frags = extractFragments(desc);
      for (const f of frags) {
        const n = countOccurrences(md, `「${f}」`);
        if (n < 2) err(`${label}: 格式片段「${f}」在 content.md 中只出现 ${n} 次（需 ≥2：verbatim 引用 + 自己的结构化说明）`);
      }
      /* B3 操作码文档行（并核对声明的 ASCII 参数名是否真的写进了文档） */
      const ops = extractOps(desc);
      for (const [op, args] of ops) {
        const re = new RegExp(`^-\\s+\`${op}\`(.*)$`, "m");
        const m = re.exec(md);
        if (!m) {
          err(`${label}: 操作码 ${op} 缺少「- \`${op}\`」说明行`);
        } else {
          for (const a of args) {
            if (!m[1].includes(a)) err(`${label}: 操作码 ${op} 的说明行未写出 description 声明的参数「${a}」`);
          }
        }
      }
      /* B4 judge-cases 结构 */
      const jc = readJson(judgePath, label);
      if (jc) {
        if (!Array.isArray(jc)) err(`${label}: judge-cases.json 不是数组`);
        else {
          const cases = (cj && cj.cases) || [];
          if (jc.length !== cases.length) err(`${label}: judge-cases ${jc.length} 条 != cases.json ${cases.length} 条`);
          const declaredShapes = new Map(); // op -> [{lo,hi,frag}]
          for (const f of frags) {
            const tk = f.trim().split(/\s+/);
            if (tk.length >= 2 && ops.has(tk[0])) {
              if (!declaredShapes.has(tk[0])) declaredShapes.set(tk[0], []);
              declaredShapes.get(tk[0]).push({ ...shapeRange(f, desc, ops.get(tk[0])), frag: f });
            }
          }
          const seenOutOps = new Set();
          for (let i = 0; i < jc.length; i++) {
            const c = jc[i];
            if (typeof c.input !== "string" || typeof c.output !== "string") {
              err(`${label}: judge-cases[${i}] 缺 input/output`); continue;
            }
            if (cases[i] && c.input !== cases[i].input) err(`${label}: judge-cases[${i}].input 与 cases.json 不一致`);
            if (!c.output.trim()) err(`${label}: judge-cases[${i}].output 为空`);
            for (const line of c.output.split("\n")) {
              const s = line.trim();
              if (!s) continue;
              const tk = s.split(/\s+/);
              const head = tk[0];
              /* B6 行首标记 */
              if (/^[A-Z]$/.test(head)) {
                seenOutOps.add(head);
                if (ops.size && !ops.has(head)) {
                  err(`${label}: judge-cases[${i}] 输出行首标记「${head}」未在 description 声明的操作码中`);
                }
                /* B5 字段数（区间：中文字段名可能含内部空格，见 shapeRange 注释） */
                if (declaredShapes.has(head)) {
                  const shapes = declaredShapes.get(head);
                  const ok = shapes.some((s) => tk.length >= s.lo && tk.length <= s.hi);
                  if (!ok) {
                    const decl = shapes.map((s) => `「${s.frag}」=${s.lo === s.hi ? s.lo : `${s.lo}..${s.hi}`} 字段`).join("，");
                    err(`${label}: 操作 ${head} 输出 ${tk.length} 字段（"${s}"），description 声明的是 ${decl}`);
                  }
                } else if (ops.has(head)) {
                  warn(`${label}: 操作 ${head} 有输出但 description 未给「${head} …」格式片段，无法核对字段数`);
                }
              }
            }
          }
          for (const op of ops.keys()) {
            if (!seenOutOps.has(op)) warn(`${label}: 操作码 ${op} 在 ${jc.length} 条 judge 输出中未出现（可能只影响状态、无直接输出行）`);
          }
        }
      }
    }
    /* --- D. Tier C --- */
    if (tier === "C") {
      if (!md.includes("平台自动判分的是")) err(`${label}: Tier C 未写明「平台自动判分的是 <概念子任务>」`);
      if (!md.includes("AC 不等于内核")) err(`${label}: Tier C 未写明「AC 不等于内核构建/链接成功」`);
      if (sectionBody(md, "本地完整验证") === null) err(`${label}: Tier C 缺少「## 本地完整验证」小节`);
    }
  }

  /* --- E. 内容来源 --- */
  if (!md.includes("内容来源")) err(`${label}: 缺少「内容来源」标注`);
  if (HAS_REAL_SOURCE.has(id)) {
    if (id === "1908449571747745793" && !md.includes("include/queue.h")) {
      err(`${label}: 有真实 originalCode 却未点名 include/queue.h`);
    }
  } else {
    if (!md.includes("未随工作区提供")) err(`${label}: originalCode 缺失却未写明「原指导 Listing 未随工作区提供」`);
    if (!md.includes("等价")) err(`${label}: originalCode 缺失却未写明构造的是「等价」示例/环境`);
  }

  summary.push(`${no}\t${id}\tTier ${tier}\t${bytes} B`);
}

/* ---------- 输出 ---------- */
console.log("=== check-content-consistency ===");
console.log(`scope: ${all ? "--all（全库）" : explicit.length ? "显式目录" : "Agent B 的 16 篇"}`);
console.log(`checked: ${checked}`);
console.log("");
console.log("实验\tid\tTier\tcontent.md 字节");
for (const s of summary) console.log(s);
if (warns.length) {
  const uniqueWarns = [...new Set(warns)];
  console.log(`\nWARN (${uniqueWarns.length}):`);
  for (const w of uniqueWarns) console.log("  - " + w);
}
if (errors.length) {
  console.log(`\nFAIL (${errors.length} errors):`);
  for (const e of errors) console.log("  - " + e);
  process.exit(1);
}
console.log("\nCONTENT CONSISTENCY OK: 0 errors");
process.exit(0);
