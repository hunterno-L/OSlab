import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const dir = process.argv[2];
if (!dir) {
  console.error("usage: node scripts/derive-harness-variants.mjs <experiment-dir>");
  process.exit(2);
}

const START = "/* ===== 学生填写区";
const END = "/* ===== 填写区结束 ===== */";

const starter = readFileSync(join(dir, "starter.c"), "utf8");
const start = starter.indexOf(START);
const end = starter.indexOf(END);
if (start < 0 || end < 0 || end < start) {
  console.error("starter.c must contain the marked fill region");
  process.exit(2);
}

for (const variant of ["reference", "wrong"]) {
  const region = readFileSync(join(dir, `fill-${variant}.c`), "utf8");
  const out = starter.slice(0, start) + region + starter.slice(end);
  writeFileSync(join(dir, `${variant}.c`), out);
  console.log(`wrote ${variant}.c (${Buffer.byteLength(out, "utf8")} bytes)`);
}