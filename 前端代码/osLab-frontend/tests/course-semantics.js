const assert = require("assert").strict,
  fs = require("fs"),
  ts = require("typescript"),
  vm = require("vm"),
  path = require("path");
const context = { exports: {} };
vm.runInNewContext(
  ts.transpileModule(
    fs.readFileSync(
      path.join(__dirname, "../src/utils/graphSemantics.ts"),
      "utf8"
    ),
    {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2020,
      },
    }
  ).outputText,
  context
);
const { normalizedGraphRelation: n, safeLocalLearningUrl: s } = context.exports;
for (const [canonical, legacy] of [
  ["REQUIRES", "dependent"],
  ["CONTAINS", "contain"],
  ["RELATED_TO", "related"],
  ["IMPLEMENTS", "implements"],
  ["CONTRASTS_WITH", "contrasts"],
  ["BASED_ON", "based"],
  ["implemente", "implements"],
])
  assert.equal(n(canonical), legacy);
assert(s("/knowledge/concept/os.memory.lru#chunk-chunk.example"));
for (const url of [
  "//evil.test",
  "https://evil.test",
  "javascript:alert(1)",
  "/knowledge/\\evil",
  "/admin",
])
  assert(!s(url));
console.log(
  "Canonical and historical graph relations and safe textbook provenance navigation passed."
);
