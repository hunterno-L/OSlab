#!/usr/bin/env node
// 导入实验维护包（oslab-experiment-maintenance/1.0）。
//
// 用法：
//   node scripts/import-experiment-package.mjs <包目录|父目录> <baseUrl> <账号> <口令> [--dry-run]
//
// 例：
//   node scripts/import-experiment-package.mjs \
//     content/operating-systems/wangdao-2025/experiments \
//     http://127.0.0.1:8121/api admin '口令'
//
// 流程：login → 每个包 preview → commit → review(SUBMIT → APPROVE → APPLY)
// 幂等：已 APPLIED 的实验会跳过 review。
// --dry-run 只做 preview，不写库。

import { readFile, readdir } from "node:fs/promises";
import path from "node:path";

const rawArgs = process.argv.slice(2);
const dryRun = rawArgs.includes("--dry-run");
const args = rawArgs.filter((a) => a !== "--dry-run");
const [target, baseUrl = "http://127.0.0.1:8121/api", account, password] = args;

if (!target || !account || !password) {
  console.error("用法: node scripts/import-experiment-package.mjs <包目录|父目录> <baseUrl> <账号> <口令> [--dry-run]");
  process.exit(2);
}

async function request(method, url, body, cookie) {
  const res = await fetch(url, {
    method,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...(cookie ? { Cookie: cookie } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let data;
  try {
    data = JSON.parse(text);
  } catch {
    throw new Error(`HTTP ${res.status}: ${text.slice(0, 300)}`);
  }
  return { res, data };
}

async function login() {
  const { res, data } = await request("POST", `${baseUrl}/user/login`, {
    userAccount: account,
    userPassword: password,
  });
  if (data.code !== 0) throw new Error(`登录失败: ${data.message}`);
  const cookie =
    res.headers.getSetCookie?.().map((c) => c.split(";")[0]).join("; ") ||
    res.headers.get("set-cookie")?.split(";")[0];
  if (!cookie) throw new Error("登录成功但没有拿到会话 Cookie");
  return cookie;
}

async function findPackages(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  if (entries.some((e) => e.name === "experiment-maintenance.json")) return [dir];
  const out = [];
  for (const e of entries) {
    if (!e.isDirectory()) continue;
    const child = path.join(dir, e.name);
    if ((await readdir(child)).includes("experiment-maintenance.json")) out.push(child);
  }
  return out;
}

async function collectFiles(dir) {
  const root = path.join(dir, "experiment-files");
  const out = [];
  async function walk(current) {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) await walk(full);
      else if (entry.isFile()) {
        out.push({
          path: path.relative(dir, full).split(path.sep).join("/"),
          contentBase64: (await readFile(full)).toString("base64"),
        });
      }
    }
  }
  await walk(root);
  return out;
}

async function importPackage(dir, cookie) {
  const pack = JSON.parse((await readFile(path.join(dir, "experiment-maintenance.json"), "utf8")).replace(/^\uFEFF/, ""));
  const payload = { package: pack, files: await collectFiles(dir) };
  console.log(`\n== ${pack.batchCode}: ${pack.experiments.length} 个实验，${payload.files.length} 个附件`);

  const preview = await request("POST", `${baseUrl}/teaching/experiments/import/preview`, payload, cookie);
  if (preview.data.code !== 0) throw new Error(`preview 失败: ${preview.data.message}`);
  const pv = preview.data.data;
  console.log(`preview: valid=${pv.valid} items=${pv.items.length} errors=${pv.errors.length}`);
  if (!pv.valid) {
    for (const e of pv.errors) console.error("  ", e);
    return { failed: true };
  }
  if (dryRun) return { failed: false, applied: 0 };

  const commit = await request("POST", `${baseUrl}/teaching/experiments/import/commit`, payload, cookie);
  if (commit.data.code !== 0) throw new Error(`commit 失败: ${commit.data.message}`);
  console.log(`commit: updated=${commit.data.data.updated} unchanged=${commit.data.data.unchanged}`);

  let applied = 0;
  for (const e of pack.experiments) {
    const id = String(e.questionId);
    const current = await request("GET", `${baseUrl}/teaching/experiments/${id}/mappings`, undefined, cookie);
    if (current.data.code !== 0) throw new Error(`读取 ${id} 映射失败: ${current.data.message}`);
    if (current.data.data.status === "APPLIED") {
      console.log(`  skip ${id}（已应用）`);
      continue;
    }
    for (const action of ["SUBMIT", "APPROVE", "APPLY"]) {
      const body = { action };
      if (action === "APPLY") {
        body.confirmed = true;
        body.reason = "批量导入";
      }
      const r = await request("POST", `${baseUrl}/teaching/experiments/${id}/review`, body, cookie);
      if (r.data.code !== 0) throw new Error(`review ${id} ${action} 失败: ${r.data.message}`);
    }
    applied++;
    console.log(`  applied ${id} ${e.title}`);
  }
  return { failed: false, applied };
}

async function main() {
  const cookie = await login();
  console.log("登录成功");
  const packages = await findPackages(path.resolve(target));
  if (packages.length === 0) throw new Error("没有找到实验维护包");
  let failed = 0;
  let applied = 0;
  for (const dir of packages) {
    const r = await importPackage(dir, cookie);
    if (r.failed) failed++;
    applied += r.applied;
  }
  console.log(`\n完成：包 ${packages.length} 个，失败 ${failed}，应用 ${applied} 个实验`);
  if (failed) process.exit(1);
}

main().catch((e) => {
  console.error("失败:", e.message);
  process.exit(1);
});
