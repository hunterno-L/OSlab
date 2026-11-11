import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const args = process.argv.slice(2);
let probeFile = null;
if (args[0] === "--probes") {
  probeFile = args[1];
  args.splice(0, 2);
}
const dirs = args;
if (dirs.length === 0) {
  console.error(
    "usage: node scripts/probe-harness-robustness.mjs [--probes <probes.json>] <experiment-dir> [more dirs ...]"
  );
  process.exit(2);
}

const CFLAGS = ["-std=c99", "-Wall", "-Wextra", "-Werror", "-O2", "-pipe"];

// harness-spec §6：驱动必须在任何畸形/截断/越界输入下正常退出（exit 0），
// 否则沙箱会把「答案错误」误判成 Runtime Error。
const DEFAULT_PROBES = [
  ["empty", ""],
  ["zeros", "0 0\n"],
  ["huge-unsigned", "4294967295 4294967295\n"],
  ["negative", "-1 -1\n"],
  ["op-count-overflow", "8 8192\n9999\nA\n"],
  ["unknown-op", "8 8192\n1\nZ\n"],
  ["truncated-op", "8 8192\n1\nA\n"],
  ["truncated-header", "8\n"],
  ["garbage", "hello world\n"],
  ["out-of-range-page", "8 8192\n1\nI 999 0 2\n"],
  ["out-of-range-va", "8 8192\n1\nB 4294967295 1\n"],
  ["negative-ref", "8 8192\n3\nA\nS 1 -1\nF 1\n"],
  ["double-free", "8 8192\n4\nA\nF 1\nF 1\nA\n"],
  ["free-unallocated", "8 8192\n1\nF 1\n"],
  ["perm-overflow", "8 8192\n1\nM 0 4096 0 99999\n"],
  ["size-overflow", "8 8192\n1\nM 0 4294967295 0 2\n"],
];

// 各章 stdin 语法不同，用 --probes 传入本章的畸形输入集（[[name, input], ...]）。
// 不传则用第二章默认集，保证 verify-ch2/probe-ch2 的历史日志可复现。
const PROBES = probeFile ? JSON.parse(readFileSync(probeFile, "utf8")) : DEFAULT_PROBES;
if (!Array.isArray(PROBES) || PROBES.length === 0) {
  console.error("probe set must be a non-empty array of [name, input] pairs");
  process.exit(2);
}
for (const p of PROBES) {
  if (!Array.isArray(p) || p.length !== 2 || typeof p[0] !== "string" || typeof p[1] !== "string") {
    console.error(`malformed probe entry: ${JSON.stringify(p)}`);
    process.exit(2);
  }
}

const work = mkdtempSync(join(tmpdir(), "oslab-probe-"));
let bad = 0;
let runs = 0;

try {
  for (const dir of dirs) {
    for (const variant of ["starter", "reference", "wrong"]) {
      const src = join(work, "main.c");
      const exe = join(work, process.platform === "win32" ? "main.exe" : "main");
      writeFileSync(src, readFileSync(join(dir, `${variant}.c`), "utf8"));
      execFileSync("gcc", [...CFLAGS, src, "-o", exe], { stdio: "pipe" });
      for (const [name, input] of PROBES) {
        runs++;
        try {
          execFileSync(exe, input.split(/\s+/).filter(Boolean), {
            input,
            timeout: 5000,
            stdio: "pipe",
            encoding: "utf8",
          });
        } catch (e) {
          bad++;
          console.error(`CRASH/FAIL ${dir} ${variant}.c probe=${name} status=${e.status} signal=${e.signal}`);
        }
      }
    }
    console.log(`probed ${dir}: 3 variants x ${PROBES.length} inputs`);
  }
  console.log(`total runs=${runs} failures=${bad}`);
  process.exit(bad === 0 ? 0 : 1);
} finally {
  rmSync(work, { recursive: true, force: true });
}