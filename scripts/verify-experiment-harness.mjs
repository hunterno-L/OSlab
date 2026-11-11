import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const dir = process.argv[2];
if (!dir) {
  console.error("usage: node scripts/verify-experiment-harness.mjs <experiment-dir>");
  process.exit(2);
}

const CFLAGS = ["-std=c99", "-Wall", "-Wextra", "-Werror", "-O2", "-pipe"];
const MAX_SOURCE_BYTES = 65536;
const MAX_CASES = 20;
const MAX_INPUT_BYTES = 8192;

const spec = JSON.parse(readFileSync(join(dir, "cases.json"), "utf8"));
const config = JSON.parse(readFileSync(join(dir, "judge-config.json"), "utf8"));
const variants = ["starter", "reference", "wrong"];
const work = mkdtempSync(join(tmpdir(), "oslab-harness-"));
const results = {};

function compileAndRun(variant) {
  const source = readFileSync(join(dir, `${variant}.c`), "utf8");
  const bytes = Buffer.byteLength(source, "utf8");
  if (bytes > MAX_SOURCE_BYTES) {
    throw new Error(`${variant}.c exceeds sandbox source limit: ${bytes} > ${MAX_SOURCE_BYTES}`);
  }
  const src = join(work, "main.c");
  const exe = join(work, process.platform === "win32" ? "main.exe" : "main");
  writeFileSync(src, source);
  try {
    execFileSync("gcc", [...CFLAGS, src, "-o", exe], { stdio: "pipe" });
  } catch (e) {
    // 把 gcc 诊断按文本抛出，否则 execFileSync 只给出 Buffer 字节 dump，无法定位。
    throw new Error(`${variant}.c 编译失败:\n${(e.stderr ?? "").toString("utf8")}`);
  }
  return spec.cases.map((c) => {
    const inputBytes = Buffer.byteLength(c.input, "utf8");
    if (inputBytes > MAX_INPUT_BYTES) {
      throw new Error(`case "${c.name}" exceeds sandbox input limit: ${inputBytes}`);
    }
    // 沙箱把 input 同时作为 argv 追加；harness 必须忽略 argv，这里一并验证。
    const argv = c.input.split(/\s+/).filter(Boolean);
    try {
      return execFileSync(exe, argv, {
        input: c.input,
        timeout: config.timeLimit,
        stdio: "pipe",
        encoding: "utf8",
      });
    } catch (e) {
      throw new Error(
        `${variant}.c 在用例 "${c.name}" 上运行失败（退出码 ${e.status}）:\n` +
        `${(e.stderr ?? "").toString("utf8")}`
      );
    }
  });
}

// 与 DefaultJudgeStrategy.normalizeOutput 完全一致的归一化。
function normalize(output) {
  return String(output ?? "")
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n")
    .replace(/[\t ]+(?=\n)/g, "")
    .replace(/\n+$/, "");
}

try {
  if (spec.cases.length === 0 || spec.cases.length > MAX_CASES) {
    throw new Error(`case count must be 1..${MAX_CASES}, got ${spec.cases.length}`);
  }
  for (const variant of variants) {
    results[variant] = compileAndRun(variant).map(normalize);
  }

  const expected = results.reference;
  const failures = [];

  if (new Set(expected).size !== expected.length) {
    failures.push("reference outputs are not distinct across cases; scenario coverage is too weak");
  }
  for (const variant of ["starter", "wrong"]) {
    const same = results[variant].filter((o, i) => o === expected[i]).length;
    if (same === expected.length) {
      failures.push(`${variant}.c passes every case; it does not demonstrate a detectable defect`);
    }
  }
  if (expected.some((o) => o.length === 0)) {
    failures.push("reference produced empty output for some case");
  }

  const judgeCases = spec.cases.map((c, i) => ({ input: c.input, output: expected[i] }));
  writeFileSync(join(dir, "judge-cases.json"), JSON.stringify(judgeCases, null, 2) + "\n");

  console.log(`experiment: ${dir}`);
  spec.cases.forEach((c, i) => {
    console.log(`  [${i + 1}] ${c.name}`);
    console.log(`      reference: ${JSON.stringify(expected[i])}`);
    console.log(`      starter  : ${JSON.stringify(results.starter[i])}`);
    console.log(`      wrong    : ${JSON.stringify(results.wrong[i])}`);
  });
  if (failures.length) {
    console.error("VERIFY FAILED:");
    for (const f of failures) console.error(`  - ${f}`);
    process.exit(1);
  }
  const inert = spec.cases.filter(
    (c, i) => results.starter[i] === expected[i] && results.wrong[i] === expected[i]
  );
  if (inert.length) {
    console.log(`NOTE: ${inert.length} 个用例为纯边界用例（starter 与 wrong 都通过）：${inert.map((c) => c.name).join("；")}`);
  }
  console.log("VERIFY OK: judge-cases.json written from real reference runs");
} finally {
  rmSync(work, { recursive: true, force: true });
}