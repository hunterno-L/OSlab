import { readFileSync, writeFileSync } from "node:fs";

const [out, ...fragments] = process.argv.slice(2);
if (!out || fragments.length === 0) {
  console.error("usage: node scripts/assemble-harness.mjs <out.c> <fragment1> [fragment2 ...]");
  process.exit(2);
}

const text = fragments.map((f) => readFileSync(f, "utf8")).join("\n");
writeFileSync(out, text);
console.log(`wrote ${out} (${Buffer.byteLength(text, "utf8")} bytes, ${fragments.length} fragments)`);