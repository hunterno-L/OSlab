// Run the repository's standalone contracts without relying on shell glob expansion.
const { readdirSync } = require("node:fs");
const { join } = require("node:path");
const { spawnSync } = require("node:child_process");

const scripts = readdirSync(__dirname)
  .filter((name) => name.endsWith(".js") && name !== "run-contracts.js")
  .sort();

for (const name of scripts) {
  console.log(`\n[contracts] ${name}`);
  const result = spawnSync(process.execPath, [join(__dirname, name)], {
    stdio: "inherit",
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status || 1);
}
console.log(`\nAll ${scripts.length} contract scripts passed.`);
