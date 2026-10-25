const assert = require("assert").strict,
  fs = require("fs"),
  ts = require("typescript"),
  vm = require("vm");
const source = fs.readFileSync(
  require("path").join(__dirname, "../src/utils/memberPackageFiles.ts"),
  "utf8"
);
const context = { exports: {}, Set, Error };
vm.runInNewContext(
  ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
    },
  }).outputText,
  context
);
const select = context.exports.selectPackageAttachments;
const file = (path) => ({
  name: path.split("/").pop(),
  webkitRelativePath: path,
});
const pkg = {
  schemaVersion: "oslab-authoring/1.0",
  chunks: [
    {
      contentMd: "图 ![图](assets/图1.png)",
      assets: [{ path: "assets/图1.png" }],
    },
  ],
};
for (const main of [
  "J/work/knowledge.json",
  "J/knowledge/batch.json",
  "J/knowledge.json",
]) {
  const assets = select(main, pkg, [
    file(main),
    file("J/assets/图1.png"),
    file("J/assets/unrelated.png"),
    file("J/experiment-files/private.cpp"),
  ]);
  assert.equal(assets.length, 1);
  assert.equal(assets[0].path, "assets/图1.png");
}
const experiment = {
  schemaVersion: "oslab-experiment-maintenance/1.0",
  experiments: [
    {
      contentChanges: ["cpp", "md", "json", "txt"].map((ext) => ({
        file: "experiment-files/example." + ext,
      })),
    },
  ],
};
const paths = ["cpp", "md", "json", "txt"].map(
  (ext) => "A/experiment-files/example." + ext
);
assert.equal(
  select("A/work/experiment-maintenance.json", experiment, paths.map(file))
    .length,
  4
);
assert.throws(
  () => select("J/work/knowledge.json", pkg, [file("other/assets/图1.png")]),
  /缺少/
);
assert.equal(
  select("knowledge.json", pkg, [{ name: "图1.png" }])[0].path,
  "assets/图1.png"
);
console.log(
  "Member package attachment roots, referenced-only images, experiment text extensions and missing files passed."
);
