/* 逐条复核用例覆盖度（harness-spec §10 第三道门）：
 * 对每个第四章实验跑一次 verify，解析它打印的 reference/starter/wrong 三路真实输出，
 * 统计每个用例上「空填写」与「错误变体」是否被区分开。
 * 判据：starter 必须在每个用例上都与 reference 不同（否则该用例判不出「没写」）；
 * wrong 允许在个别纯边界用例上与 reference 相同，但不得在全部用例上相同。
 * usage: node artifacts/experiment-judge/coverage-ch4.mjs */
import { readdirSync, existsSync, readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { join } from "node:path";

const root = "content/operating-systems/wangdao-2025/experiments/A-第四章-实验判题-001/experiment-files";
let bad = 0, totalCases = 0;
for (const id of readdirSync(root).sort()) {
  const dir = join(root, id);
  if (!existsSync(join(dir, "cases.json"))) { continue; }
  /* Tier B（本地构建，如 4.1）只有 judge-mode.json，没有 judge-config.json，不参与自动判题 */
  if (!existsSync(join(dir, "judge-config.json"))) { console.log(`SKIP ${id}: Tier B 本地构建，无自动判题`); continue; }
  const names = JSON.parse(readFileSync(join(dir, "cases.json"), "utf8")).cases.map((c) => c.name);
  const out = execFileSync(process.execPath, ["scripts/verify-experiment-harness.mjs", dir],
    { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  const rows = [];
  let cur = null;
  for (const line of out.split("\n")) {
    const m = /^\s+\[(\d+)\]\s/.exec(line);
    if (m) { cur = { n: Number(m[1]) }; rows.push(cur); continue; }
    const v = /^\s+(reference|starter|wrong)\s*:\s+(.*)$/.exec(line);
    if (v && cur) { cur[v[1]] = JSON.parse(v[2]); }
  }
  const starterSame = [], wrongSame = [];
  for (const r of rows) {
    totalCases++;
    if (r.starter === r.reference) { starterSame.push(r.n); }
    if (r.wrong === r.reference) { wrongSame.push(r.n); }
  }
  const ok = starterSame.length === 0 && wrongSame.length < rows.length;
  if (!ok) { bad++; }
  const size = readFileSync(join(dir, "starter.c")).length;
  console.log(`${ok ? "OK  " : "BAD "} ${id} cases=${rows.length} starter.c=${size}B headroom=${65536 - size}B`
    + ` starter与参考相同的用例=[${starterSame}] wrong与参考相同的用例=[${wrongSame}]`);
  if (rows.length !== names.length) { console.log(`  !! 用例数不符: ${rows.length} vs ${names.length}`); bad++; }
}
console.log(`COVERAGE ch4: 用例总数=${totalCases} 不合格实验=${bad}`);
process.exit(bad === 0 ? 0 : 1);