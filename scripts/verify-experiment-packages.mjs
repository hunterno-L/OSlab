#!/usr/bin/env node
// 离线校验实验维护包：结构 + 参考解编译运行 + 错误解必须失败。
// 用法：
//   node scripts/verify-experiment-packages.mjs [包目录或父目录]
// 依赖：gcc/g++（可用 OSLAB_GCC 指定完整路径）
// 退出码：0 全部通过；1 有失败项。

import { readFile, readdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import os from "node:os";
import path from "node:path";

const gcc = process.env.OSLAB_GCC || "gcc";
const gccDir = path.dirname(gcc);
const target = process.argv[2] || "content/operating-systems/wangdao-2025/experiments";

function normalize(text) {
  if (text == null) return "";
  return String(text)
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n")
    .split("\n")
    .map((line) => line.replace(/[ \t]+$/g, ""))
    .join("\n")
    .replace(/\n+$/g, "");
}

function compileAndRun(sourceFile, dir, input, timeLimitMs) {
  execFileSync(gcc, ["-O2", "-pipe", sourceFile, "-o", "main.exe"], {
    cwd: dir,
    env: { ...process.env, PATH: `${gccDir}${path.delimiter}${process.env.PATH || ""}` },
    stdio: "pipe",
    timeout: 30000,
  });
  return execFileSync(path.join(dir, "main.exe"), [], {
    cwd: dir,
    input,
    timeout: Math.max(1000, Number(timeLimitMs) || 2000),
    stdio: "pipe",
  }).toString("utf8");
}

async function findPackages(dir) {
  const out = [];
  const entries = await readdir(dir, { withFileTypes: true });
  if (entries.some((e) => e.name === "experiment-maintenance.json")) {
    return [dir];
  }
  for (const e of entries) {
    if (!e.isDirectory()) continue;
    const child = path.join(dir, e.name);
    const files = await readdir(child);
    if (files.includes("experiment-maintenance.json")) out.push(child);
  }
  return out;
}

async function verifyPackage(pkgDir) {
  const raw = await readFile(path.join(pkgDir, "experiment-maintenance.json"), "utf8");
  const pack = JSON.parse(raw.replace(/^\uFEFF/, ""));
  const issues = [];
  if (pack.schemaVersion !== "oslab-experiment-maintenance/1.0") issues.push("schemaVersion 错误");
  if (!pack.batchCode) issues.push("缺少 batchCode");
  if (!Array.isArray(pack.experiments) || pack.experiments.length === 0) issues.push("experiments 为空");

  let passed = 0;
  for (const e of pack.experiments) {
    const id = String(e.questionId);
    const changes = Object.fromEntries((e.contentChanges || []).map((c) => [c.field, c.file]));
    for (const field of ["content", "originalCode", "answer", "judgeCase", "judgeConfig"]) {
      if (!changes[field]) issues.push(`${id}: 缺少 ${field}`);
    }
    if (issues.length && issues[issues.length - 1].startsWith(`${id}: 缺少`)) continue;

    const read = (rel) => readFile(path.join(pkgDir, rel), "utf8");
    const cases = JSON.parse(await read(changes.judgeCase));
    const config = JSON.parse(await read(changes.judgeConfig));
    if (!Array.isArray(cases) || cases.length === 0) issues.push(`${id}: judgeCase 为空`);
    if (!(config.timeLimit > 0) || !(config.memoryLimit > 0)) issues.push(`${id}: judgeConfig 非法`);
    if (!(await read(changes.content)).trim()) issues.push(`${id}: 题面为空`);

    const dir = await mkdtemp(path.join(os.tmpdir(), "oslab-verify-"));
    try {
      const refPath = path.join(dir, "main.c");
      await writeFile(refPath, await read(changes.answer), "utf8");
      let refOk = true;
      for (let i = 0; i < cases.length; i++) {
        const got = compileAndRun(refPath, dir, cases[i].input, config.timeLimit);
        if (normalize(got) !== normalize(cases[i].output)) {
          issues.push(`${id}: 参考解 case ${i} 不匹配`);
          refOk = false;
        }
      }
      const wrongRel = `experiment-files/${id}/wrong.c`;
      try {
        const wrongPath = path.join(dir, "wrong.c");
        await writeFile(wrongPath, await read(wrongRel), "utf8");
        let wrongFails = false;
        for (const c of cases) {
          const got = compileAndRun(wrongPath, dir, c.input, config.timeLimit);
          if (normalize(got) !== normalize(c.output)) {
            wrongFails = true;
            break;
          }
        }
        if (!wrongFails) issues.push(`${id}: 错误解竟然通过了全部用例`);
      } catch {
        issues.push(`${id}: 缺少或无法编译 wrong.c`);
      }
      if (refOk) passed++;
    } catch (err) {
      issues.push(`${id}: 编译/运行失败 (${String(err.message).slice(0, 120)})`);
    } finally {
      await rm(dir, { recursive: true, force: true });
    }
  }
  return { pack, passed, total: pack.experiments.length, issues };
}

const packages = await findPackages(target);
if (packages.length === 0) {
  console.error("没有找到实验维护包");
  process.exit(1);
}

let failed = 0;
for (const dir of packages) {
  const { pack, passed, total, issues } = await verifyPackage(dir);
  console.log(`${path.basename(dir)} [${pack.batchCode}] 通过 ${passed}/${total}`);
  for (const issue of issues) console.log(`  - ${issue}`);
  if (issues.length) failed++;
}
process.exit(failed ? 1 : 0);
