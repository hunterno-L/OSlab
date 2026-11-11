/* 生成第三/四章缺失的 content.md（Task 6）。
 *
 * 设计原则：
 *  1. 保留题面原文（任务描述 / 知识要点 / 实验任务）。
 *  2. cases.json.description 逐字嵌入（check-content-consistency 的 B1 verbatim 闸门）。
 *  3. 操作符说明行直接从 description 的「X 参数=语义」声明派生，保证 B3 参数名齐全、
 *     语义与真实驱动一致，不靠手写记忆。
 *  4. description 里的每个「格式片段」再结构化复述一次，满足 B2 的 ≥2 次要求。
 *  5. 常见错误取自学生填写区里已写好的「契约」条目 + 本实验注入的错误变体。
 *  6. 原文缺失的实验必须出现「未随工作区提供」与「等价」两个诚实性标注词。
 *
 * usage: node artifacts/experiment-judge/gen-content-34.mjs [--dry]
 */
import { readFileSync, writeFileSync, existsSync, readdirSync } from "node:fs";
import { join } from "node:path";

const DRY = process.argv.includes("--dry");
const EXP = "content/operating-systems/wangdao-2025/experiments";
const REF = "artifacts/team-handoff/2026-10-17/A-开工包/reference/experiments";
const J = "artifacts/experiment-judge";
const CH = { 3: "A-第三章-实验判题-001", 4: "A-第四章-实验判题-001" };
/* 全库只有 2.1(include/queue.h) 与 3.1(mm/pmap.c) 有真实 originalCode */
const REAL_SOURCE = { "1908900051330306049": "mm/pmap.c" };

const inv = JSON.parse(readFileSync(join(REF, "inventory.json"), "utf8"));
const invArr = Array.isArray(inv) ? inv : (inv.experiments ?? Object.values(inv)[0]);
const titleOf = new Map(invArr.map((e) => [String(e.questionId), e.title ?? ""]));

/* 每个实验注入的错误变体（fill-wrong.c），用于「常见错误」首条 */
const WRONG = {
  "1930156493454495745": "把返回值写进 `$a0`(regs[4]) 而不是 `$v0`(regs[2])，用户态取到的是旧值。",
  "1930156605618573314": "`page_insert` 失败时漏掉 `page_free(p)`，刚申请的物理页被泄漏。",
  "1930156740377366529": "`page_insert` 之前多写一次 `p->pp_ref++`，引用计数虚高，页永远回收不掉。",
  "1930156844614209538": "解映射前先查 `page_lookup`，未映射就返回 `-E_INVAL`，破坏幂等性。",
  "1930156972624367618": "先把自己置 `ENV_NOT_RUNNABLE` 再 `sched_yield`，当前进程被自己阻塞。",
  "1930157095085461505": "漏掉 `env_ipc_receiving` 检查，向没在接收的进程发送也「成功」。",
  "1930157212584693762": "子进程置 `ENV_RUNNABLE` 而非 `ENV_NOT_RUNNABLE`，未经父进程初始化就被调度。",
  "1930157404163723265": "子进程分支用返回的 `envid`(=0) 算 `ENVX`，`env` 指向无关的 `envs[0]`。",
  "1930157516164222977": "可写页只标子进程 COW，漏掉父进程自身那次 `mem_map`，父进程一写就污染子进程。",
  "1930157629511094274": "写回页表项时硬写 `PTE_V|PTE_R`，丢掉 `PTE_COW`/`PTE_LIBRARY` 与只读保护。",
  "1930157740261691394": "异常栈申请在 `xstacktop` 而非 `xstacktop-BY2PG`，差一页，第一次切栈就跑飞。",
  "1930157867273605121": "把临时页映射回原地址时沿用 `perm`，COW 位没清除，同一条写指令反复缺页。",
  "1930157984642813953": "入就绪链表前不做 `in_sched_list` 判重，同一 PCB 挂两次使链表成环。",
  "1930158086405017602": "异常栈/handler/RUNNABLE 的目标写成 0（父进程）而非子进程 envid。",
};

function seg(md, name) {
  const re = new RegExp(`^###\\s+${name}\\s*$`, "m");
  const m = re.exec(md);
  if (!m) { return ""; }
  const rest = md.slice(m.index + m[0].length);
  const nxt = rest.search(/^###\s+/m);
  return (nxt < 0 ? rest : rest.slice(0, nxt)).trim();
}

/* 从 description 派生操作符说明行。B3 只要求行内出现操作符与声明的 ASCII 参数名，
 * 故语义只取声明的首句（第一个逗号前）并限长 —— 完整语义已在上面逐字引用的 description 里，
 * 重复全文会把 content.md 撑出 6000 字节上限。 */
function opLines(desc) {
  const out = [];
  const seen = new Set();
  for (const part of desc.split(/[；;]/)) {
    const m = /^\s*([A-Z])((?:\s+[a-z_0-9]+)*)\s*=([\s\S]*)$/.exec(part);
    if (!m) { continue; }
    const op = m[1];
    if (seen.has(op)) { continue; }
    seen.add(op);
    const args = m[2].trim();
    let body = m[3].trim().replace(/\s+/g, " ").split(/[，,。]/)[0];
    if (body.length > 30) { body = body.slice(0, 30) + "…"; }
    out.push(`- \`${op}\`${args ? " " + args : ""}：${body}`);
  }
  return out;
}

function fragments(desc) {
  const out = [];
  const re = /「([^」]+)」/g;
  let m;
  while ((m = re.exec(desc)) !== null) { if (!out.includes(m[1])) { out.push(m[1]); } }
  return out;
}

/* 学生填写区里的「契约」条目 -> 常见错误 */
function contracts(id, ch) {
  const cands = ch === 4
    ? [join(J, "ch4", "regions", `${id}.c.inc`)]
    : [join(J, "ch3", `body-${id}.c`)];
  for (const f of cands) {
    if (!existsSync(f)) { continue; }
    const txt = readFileSync(f, "utf8");
    const head = txt.slice(0, txt.indexOf("static") > 0 ? txt.indexOf("static") : 4000);
    const lines = head.split("\n")
      .map((l) => l.replace(/^\s*\*?\s?/, "").trim())
      .filter((l) => /^契约[一二三四五六]/.test(l));
    if (lines.length) {
      return lines.map((l) => l.replace(/^契约[一二三四五六]：?/, "").trim());
    }
  }
  return [];
}

function targetOf(id, ch) {
  const f = ch === 4 ? join(J, "ch4", "regions", `${id}.c.inc`) : join(J, "ch3", `body-${id}.c`);
  if (!existsSync(f)) { return { file: "（见 starter.c 填写区）", func: "" }; }
  const first = readFileSync(f, "utf8").split("\n")[0];
  const m = /实现\s+(\S+)\s+的\s+([A-Za-z_][A-Za-z_0-9]*)\s*\(/.exec(first);
  return m ? { file: m[1], func: m[2] } : { file: "（见 starter.c 填写区）", func: "" };
}

let made = 0, over = 0;
for (const ch of [3, 4]) {
  const root = join(EXP, CH[ch], "experiment-files");
  for (const id of readdirSync(root).sort()) {
    const dir = join(root, id);
    if (existsSync(join(dir, "content.md"))) { continue; }
    const cjPath = join(dir, "cases.json");
    if (!existsSync(cjPath)) { continue; }
    const cj = JSON.parse(readFileSync(cjPath, "utf8"));
    const desc = cj.description ?? "";
    const tmPath = join(REF, id, "题面.md");
    const tm = existsSync(tmPath) ? readFileSync(tmPath, "utf8") : "";
    const rawTitle = titleOf.get(id) ?? "";
    const noM = /实验\s*(\d+\.\d+)/.exec(rawTitle);
    const no = noM ? noM[1] : "?";
    const title = rawTitle.replace(/^实验\s*\d+\.\d+\s*/, "").trim() || "实验";
    const tgt = targetOf(id, ch);
    const ops = opLines(desc);
    const frags = fragments(desc);
    const cons = contracts(id, ch);
    const real = REAL_SOURCE[id];

    const sourceNote = real
      ? `**内容来源**：本实验有真实原指导源码 \`${real}\`，\`starter.c\` 的公共段以该文件为锚。`
      : `**内容来源**：\`${tgt.file}\` 的**原指导 Listing 未随工作区提供**（本实验「原始代码.txt」「参考解.txt」实测均 0 字节，只有题面文字）。填写区注释与平台件里的骨架是**按北航题面文字 + MOS/MIPS3000 公开语义构造的等价示例**，不是北航原文转录。`;

    const judgeIntro = cj.cases.length === 0
      ? "- 判题方式：**Tier B 本地构建，平台不自动判分**，完成规则 `SUBMIT`。"
      : `- 判题方式：**Tier A 宿主等价 C harness 自动判分**，完成规则 \`PASS\`。沙箱单文件编译 \`gcc -O2 -pipe main.c -o main\`，输入同时作 stdin 与 argv，非零退出即 Runtime Error；源码上限 65536 字节，本实验 starter.c 实测 ${existsSync(join(dir, "starter.c")) ? readFileSync(join(dir, "starter.c")).length : 0} 字节。`;

    const parts = [];
    parts.push(`# 实验 ${no} ${title}\n`);
    parts.push(`## 实验背景\n\n${seg(tm, "任务描述")}\n`);
    parts.push(`## 知识要点\n\n${seg(tm, "知识要点")}\n`);
    parts.push(`## 关联源码\n\n| 文件 | 作用 | 需要关注的符号 |\n| --- | --- | --- |\n| \`${tgt.file}\` | 本实验填写目标 | ${tgt.func || "见填写区"} |\n| \`include/mmu.h\` | 页表项与地址常量 | PTE_V、PTE_R、PTE_COW、PTE_ADDR、PDX、PTX |\n| \`include/env.h\` | 进程控制块 | struct Env、env_status、env_pgdir、ENVX |\n\n${sourceNote}\n`);
    parts.push(`## 实验任务\n\n${seg(tm, "实验任务")}\n`);

    let judge = `## 本平台判题说明\n\n${judgeIntro}\n`;
    judge += `- 你需要填写：\`${tgt.file}\` 的 \`${tgt.func}\`。其余全部由平台提供，检查驱动不依赖学生实现的任何辅助函数。\n`;
    judge += `- 期望输出一律由真实 gcc 运行参考解得到，再逐条与手算页账本核对；判题按规范化后逐字符相等比较。\n`;
    judge += `- 判题契约（\`cases.json.description\` 原文逐字引用）：\n\n`;
    judge += `> ${desc.replace(/\n/g, "\n> ")}\n\n`;
    if (ops.length) { judge += `**操作符语义**（与上面声明一一对应）：\n\n${ops.join("\n")}\n\n`; }
    if (frags.length) {
      judge += `**输出格式片段复述**：\n\n${frags.map((f) => `- 「${f}」`).join("\n")}\n\n`;
    }
    parts.push(judge);

    if (ch === 4) {
      parts.push(`## 检查驱动与输入协议\n\n完整操作符表、门控宏、物理页账本与用户态夹具说明见 \`artifacts/experiment-judge/ch4/protocol.md\`（\`ch4/driver.c.inc\` 的权威说明，与本实验 starter.c 里的驱动逐字一致）。输入头部为 \`np fm\` 与操作条数 \`n\`（0..96）；畸形/截断/越界输入一律以退出码 0 拒绝，绝不打印伪造结果。\n`);
    }

    let errs = "## 常见错误\n\n";
    const items = [];
    if (WRONG[id]) { items.push(WRONG[id]); }
    for (const c of cons) { items.push(c); }
    if (!items.length) { items.push("未按题面逐条实现算法步骤，或改动了平台件提供的辅助函数与检查驱动。"); }
    errs += items.map((s, i) => `${i + 1}. ${s}`).join("\n") + "\n";
    parts.push(errs);

    let md = parts.join("\n");
    let bytes = Buffer.byteLength(md, "utf8");
    /* 超预算时只压缩「常见错误」的契约条目（题面原文与 description 逐字引用不可动） */
    if (bytes > 6000 && cons.length > 2) {
      const keep = cons.slice(0, 2);
      const it2 = [];
      if (WRONG[id]) { it2.push(WRONG[id]); }
      for (const c of keep) { it2.push(c); }
      const errs2 = "## 常见错误\n\n" + it2.map((s, i) => `${i + 1}. ${s}`).join("\n") + "\n";
      md = md.replace(/## 常见错误\n\n[\s\S]*$/, errs2);
      bytes = Buffer.byteLength(md, "utf8");
    }
    if (bytes > 6000 || bytes < 2500) { over++; console.log(`SIZE ${ch}.${no} ${id} ${bytes}B`); }
    if (!DRY) { writeFileSync(join(dir, "content.md"), md); }
    made++;
    console.log(`${DRY ? "DRY " : "WROTE"} ${no} ${id} ${bytes}B ops=${ops.length} frags=${frags.length} errs=${items.length}`);
  }
}
console.log(`GEN content: made=${made} 超区间=${over}`);
