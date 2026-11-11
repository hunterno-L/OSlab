/* 从已拼装的 starter.c 反推片段清单，重新拼装 + 派生 + 验证。
 * 用途：修改共享片段（如 env-pcbdef.c.inc）后，证明既有 harness 输出不变。
 * usage: node artifacts/experiment-judge/rebuild.mjs <ch3|ch4> [--capture|--no-verify]
 *   --capture   只反推并写出 <ch>-recipes.json，不改动任何文件（须在修改共享片段前运行）
 *   默认        优先按 <ch>-recipes.json 重拼；无该文件时现场反推
 */
import { readFileSync, writeFileSync, readdirSync, existsSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { join } from "node:path";

const J = "artifacts/experiment-judge";
const ch = process.argv[2];
const capture = process.argv.includes("--capture");
const noVerify = process.argv.includes("--no-verify");
if (ch !== "ch3" && ch !== "ch4") {
  console.error("usage: node artifacts/experiment-judge/rebuild.mjs <ch3|ch4> [--capture|--no-verify]");
  process.exit(2);
}

const pool = [];
for (const d of ["ch2", "ch3", "ch4"]) {
  const dir = join(J, d);
  if (!existsSync(dir)) { continue; }
  for (const f of readdirSync(dir)) {
    if (f.endsWith(".c.inc")) { pool.push({ path: join(dir, f), text: readFileSync(join(dir, f), "utf8") }); }
  }
}

/* 片段首行可能重复（ch2 四个 pmap 片段共用同一行注释），
 * 因此取「在本池内唯一的最短行前缀」作为定位标记。 */
function markerOf(f) {
  const lines = f.text.split("\n");
  for (let k = 1; k <= lines.length; k++) {
    const m = lines.slice(0, k).join("\n");
    if (!pool.some((o) => o !== f && o.text.startsWith(m))) { return m; }
  }
  throw new Error(`片段 ${f.path} 无法构造唯一标记`);
}
for (const f of pool) { f.marker = markerOf(f); }

const chNo = ch === "ch3" ? "第三章" : "第四章";
const root = `content/operating-systems/wangdao-2025/experiments/A-${chNo}-实验判题-001/experiment-files`;
const recipePath = join(J, `${ch}-recipes.json`);
const recipes = existsSync(recipePath) && !capture ? JSON.parse(readFileSync(recipePath, "utf8")) : {};
const captured = {};
let rebuilt = 0, failed = 0;

for (const id of readdirSync(root)) {
  const dir = join(root, id);
  const starter = join(dir, "starter.c");
  if (!existsSync(starter)) { continue; }
  const bodyPath = join(J, ch, `body-${id}.c`);
  if (!existsSync(bodyPath)) { console.log(`SKIP ${id}: no body`); continue; }
  const body = { path: bodyPath, text: readFileSync(bodyPath, "utf8") };

  let frags = recipes[id];
  if (!frags) {
    /* 按唯一标记定位片段边界：共享片段可能在拼装后被改过注释/宏，逐字匹配会失败。 */
    const src = readFileSync(starter, "utf8");
    const cands = [...pool, { path: bodyPath, text: body.text, marker: body.text.split("\n")[0] }];
    const marks = [];
    for (const f of cands) {
      let from = 0, n = 0;
      for (;;) {
        const i = src.indexOf(f.marker, from);
        if (i < 0) { break; }
        marks.push({ off: i, path: f.path });
        from = i + f.marker.length;
        n++;
      }
      if (n > 1) { console.error(`FAIL ${id}: 标记 ${f.path} 命中 ${n} 次`); failed++; }
    }
    const bodyMark = marks.find((m) => m.path === bodyPath);
    if (bodyMark && src.slice(bodyMark.off, bodyMark.off + body.text.length) === body.text) {
      // Some bodies already contain the shared driver. Do not append it twice.
      for (let i = marks.length - 1; i >= 0; i--) {
        if (marks[i].path !== bodyPath && marks[i].off > bodyMark.off && marks[i].off < bodyMark.off + body.text.length) { marks.splice(i, 1); }
      }
    }
    marks.sort((a, b) => a.off - b.off);
    frags = marks.map((m) => m.path);
    if (frags.length === 0) { console.error(`FAIL ${id}: 未找到任何片段标记`); failed++; continue; }
  }
  if (frags[frags.length - 1] !== bodyPath) { console.error(`FAIL ${id}: 末片段不是 body`); failed++; continue; }
  captured[id] = frags;
  if (capture) { console.log(`CAPTURE ${id} (${frags.length} fragments)`); rebuilt++; continue; }

  writeFileSync(starter, frags.map((p) => readFileSync(p, "utf8")).join("\n"));
  execFileSync(process.execPath, ["scripts/derive-harness-variants.mjs", dir], { stdio: "inherit" });
  if (!noVerify) {
    try {
      execFileSync(process.execPath, ["scripts/verify-experiment-harness.mjs", dir], { stdio: "inherit" });
      console.log(`OK ${id} (${frags.length} fragments)`);
      rebuilt++;
    } catch {
      console.error(`VERIFY FAIL ${id}`);
      failed++;
    }
  } else { rebuilt++; }
}
if (capture) { writeFileSync(recipePath, JSON.stringify(captured, null, 2) + "\n"); }
console.log(`REBUILD ${ch}: ok=${rebuilt} failed=${failed}`);
process.exit(failed === 0 ? 0 : 1);
