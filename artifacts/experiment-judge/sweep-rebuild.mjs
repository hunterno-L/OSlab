/* 一次性协调重构建：证明共享片段改动（env-pcb 拆出 env-recorder、用户态片段瘦身、
 * 驱动 X 操作符改为 CH4_PGFAULT_DRIVER 门控）对既有 harness 输出零影响。
 * usage: node artifacts/experiment-judge/sweep-rebuild.mjs
 * 串行执行（2 核 2GB），逐个打印 starter 字节数与 VERIFY 结果。 */
import { readFileSync, existsSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { join } from "node:path";

const J = "artifacts/experiment-judge";
const recipes = JSON.parse(readFileSync(join(J, "ch3-recipes.json"), "utf8"));

function fragName(p) {
  return p.replace(/^.*experiment-judge./, "").replace(/[.]c[.]inc$/, "");
}

const jobs = [];
for (const id of Object.keys(recipes)) {
  if (id === "1908900051330306049") { continue; }
  jobs.push({ ch: "3", id, args: recipes[id].slice(8, -1).map(fragName) });
}

/* 第四章：EXCL 与额外片段按各实验的考查目标固定列出 */
const ch4 = [
  ["1930156493454495745", "ch4/env-handlesys", []],
  ["1930156605618573314", "ch4/sys-memalloc", []],
  ["1930156740377366529", "ch4/sys-memmap", []],
  ["1930156844614209538", "ch4/sys-memunmap", []],
  ["1930156972624367618", "ch4/sys-yield", []],
  ["1930157095085461505", "ch4/sys-ipc", []],
  ["1930157212584693762", "ch4/sys-envalloc", []],
  ["1930157404163723265", "ch4/user-fork", ["ch4/env-user", "ch4/env-usermem", "ch4/user-duppage"]],
  ["1930157516164222977", "ch4/user-duppage", ["ch4/env-user", "ch4/env-usermem", "ch4/user-fork"]],
  ["1930157629511094274", "ch4/env-traps", []],
  ["1930157740261691394", "ch4/sys-setpgfault", []],
  ["1930157867273605121", "ch4/user-pgfault", ["ch4/env-user", "ch4/env-usermem", "ch4/user-dump"]],
  ["1930157984642813953", "ch4/sys-setstatus", []],
  ["1930158086405017602", "ch4/user-fork", ["ch4/env-user", "ch4/env-usermem", "ch4/user-duppage"]],
];
for (const [id, excl, extra] of ch4) { jobs.push({ ch: "4", id, args: [excl, ...extra] }); }

let ok = 0, fail = 0;
for (const j of jobs) {
  const script = join(J, `ch${j.ch}`, "mk.sh");
  process.stdout.write(`### ch${j.ch} ${j.id} ${j.args.join(" ")}\n`);
  try {
    const out = execFileSync("sh", [script, ...(j.ch === "3" ? ["3", j.id] : [j.id]), ...j.args],
      { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
    for (const line of out.split("\n")) {
      if (/starter[.]c|VERIFY|total runs|NOTE|error:/.test(line)) { process.stdout.write(line + "\n"); }
    }
    ok++;
  } catch (e) {
    process.stdout.write(`FAIL ch${j.ch} ${j.id}\n${(e.stdout ?? "") + (e.stderr ?? "")}\n`);
    fail++;
  }
}
process.stdout.write(`SWEEP ok=${ok} fail=${fail}\n`);
process.exit(fail === 0 ? 0 : 1);