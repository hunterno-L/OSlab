const fs = require("fs");
const os = require("os");
const path = require("path");
const { spawnSync } = require("child_process");

const projectRoot = path.resolve(__dirname, "..");
const originalRoot =
  process.env.OSLAB_LEGACY_DEMO_ROOT ||
  path.resolve(
    projectRoot,
    "../../..",
    "demo_operatingsystem 2025.5.26",
    "demo_operatingsystem"
  );
const outputRoot = path.join(
  projectRoot,
  "public",
  "legacy-simulation"
);
const stageRoot = fs.mkdtempSync(path.join(os.tmpdir(), "oslab-legacy-sim-"));
const buildOutput = path.join(stageRoot, "dist");
const componentFolders = ["unit3", "unit4", "unit5"];

function copyTree(source, target) {
  fs.mkdirSync(target, { recursive: true });
  for (const entry of fs.readdirSync(source, { withFileTypes: true })) {
    const from = path.join(source, entry.name);
    const to = path.join(target, entry.name);
    if (entry.isDirectory()) copyTree(from, to);
    else if (entry.isFile()) fs.copyFileSync(from, to);
  }
}

function removePreviousOutput() {
  if (!fs.existsSync(outputRoot)) return;
  const marker = path.join(outputRoot, ".generated-by-oslab-legacy-build");
  if (!fs.existsSync(marker)) {
    throw new Error(
      `Refusing to replace unmarked legacy asset directory: ${outputRoot}`
    );
  }
  fs.rmSync(outputRoot, { recursive: true, force: true });
}

if (!fs.existsSync(path.join(originalRoot, "node_modules", "@vue", "cli-service"))) {
  throw new Error(
    `Legacy build dependencies were not found under ${originalRoot}. Set OSLAB_LEGACY_DEMO_ROOT to the Vue 2 demo checkout.`
  );
}

try {
  fs.cpSync(path.join(originalRoot, "src"), path.join(stageRoot, "src"), {
    recursive: true,
  });
  fs.cpSync(path.join(originalRoot, "public"), path.join(stageRoot, "public"), {
    recursive: true,
  });
  fs.copyFileSync(path.join(originalRoot, "package.json"), path.join(stageRoot, "package.json"));
  for (const folder of componentFolders) {
    if (!fs.existsSync(path.join(stageRoot, "src", "views", folder))) {
      throw new Error(`Missing original algorithm folder: src/views/${folder}`);
    }
  }

  fs.writeFileSync(
    path.join(stageRoot, "src", "App.vue"),
    '<template><router-view /></template>\n<style>body{margin:0}</style>\n'
  );
  const mainPath = path.join(stageRoot, "src", "main.js");
  const main = fs.readFileSync(mainPath, "utf8");
  if (!main.includes("new Vue({")) {
    throw new Error("Unexpected legacy Vue entry point; refusing to patch it.");
  }
  fs.writeFileSync(
    mainPath,
    main.replace(
      "Vue.config.productionTip = false",
      'Vue.config.productionTip = false\nif (window.__oslabLegacyAttachVue) window.__oslabLegacyAttachVue(Vue);'
    )
  );

  const indexPath = path.join(stageRoot, "public", "index.html");
  const html = fs.readFileSync(indexPath, "utf8");
  if (!html.includes("</body>")) throw new Error("Legacy index.html has no body end.");
  fs.writeFileSync(
    indexPath,
    html.replace(
      "</body>",
      '<script src="<%= BASE_URL %>legacy-bridge.js"></script></body>'
    )
  );
  fs.copyFileSync(
    path.join(__dirname, "legacy-simulation-bridge.js"),
    path.join(stageRoot, "public", "legacy-bridge.js")
  );
  fs.mkdirSync(path.join(stageRoot, "scripts"), { recursive: true });
  fs.copyFileSync(
    path.join(__dirname, "legacy-simulation-trace.cjs"),
    path.join(stageRoot, "scripts", "legacy-simulation-trace.cjs")
  );
  fs.writeFileSync(
    path.join(stageRoot, "babel.config.js"),
    'module.exports = { plugins: [require.resolve("./scripts/legacy-simulation-trace.cjs")] };\n'
  );
  fs.writeFileSync(
    path.join(stageRoot, "vue.config.js"),
    'module.exports = { publicPath: "/legacy-simulation/", productionSourceMap: false, parallel: false };\n'
  );
  fs.symlinkSync(path.join(originalRoot, "node_modules"), path.join(stageRoot, "node_modules"), "junction");

  const cliPath = path.join(
    originalRoot,
    "node_modules",
    "@vue",
    "cli-service",
    "bin",
    "vue-cli-service.js"
  );
  const build = spawnSync(
    process.execPath,
    [
      cliPath,
      "build",
      "--dest",
      buildOutput,
    ],
    { cwd: stageRoot, stdio: "inherit", env: process.env }
  );
  if (build.error) throw build.error;
  if (build.status !== 0) throw new Error(`Legacy Vue build exited with ${build.status}.`);

  fs.writeFileSync(
    path.join(buildOutput, ".generated-by-oslab-legacy-build"),
    `${path.basename(originalRoot)}\n`
  );
  const sourceOutput = path.join(buildOutput, "source");
  fs.mkdirSync(sourceOutput, { recursive: true });
  for (const folder of componentFolders) {
    copyTree(
      path.join(originalRoot, "src", "views", folder),
      path.join(sourceOutput, folder)
    );
  }
  // Publish only after the entire original runtime and source tree are ready.
  removePreviousOutput();
  fs.cpSync(buildOutput, outputRoot, { recursive: true });
} finally {
  fs.rmSync(stageRoot, { recursive: true, force: true });
}
