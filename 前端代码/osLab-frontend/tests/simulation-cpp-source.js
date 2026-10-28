const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");
const ts = require("typescript");
const vm = require("vm");
const { parse } = require("@vue/compiler-sfc");

const root = path.resolve(__dirname, "..");
const transpile = (source) =>
  ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
    },
  }).outputText;
const sourceModule = { exports: {} };
vm.runInNewContext(
  transpile(
    fs.readFileSync(path.join(root, "src/simulation/cppSource.ts"), "utf8")
  ),
  { module: sourceModule, exports: sourceModule.exports }
);
const { parseCppSource } = sourceModule.exports;

async function main() {
  for (const file of fs.readdirSync(path.join(root, "public/algorithm-code"))) {
    if (!file.endsWith(".cpp")) continue;
    const source = fs.readFileSync(
      path.join(root, "public/algorithm-code", file),
      "utf8"
    );
    const parsed = parseCppSource(source);
    assert(!parsed.rows.some((row) => row.text.includes("@trace:")));
    assert(!source.includes("export default"));
    assert(parsed.phases.initialize?.length, `${file}: initialization mapping`);
    assert(parsed.phases.result?.length, `${file}: result mapping`);
    parsed.rows.forEach((row, index) => {
      assert.equal(row.number, index + 1);
      assert.equal(
        row.tokens.map((token) => token.text).join(""),
        row.text || " "
      );
    });
  }
  assert.throws(() => parseCppSource("<html>404</html>"), /C\+\+/);
  assert.throws(
    () => parseCppSource("import Vue from 'vue'; export default {}"),
    /C\+\+/
  );
  const example =
    '#include <iostream>\nint main() {\n// @trace:run\n// @trace:complete\n  std::cout << "<script>";\n// @trace:result\n  return 0;\n}';
  const parsed = parseCppSource(example);
  assert.equal(parsed.phases.run[0], 3);
  assert.equal(parsed.phases.complete[0], 3);
  assert.equal(parsed.phases.result[0], 4);
  assert(
    parsed.rows[2].tokens.some(
      (token) => token.kind === "string" && token.text === '"<script>"'
    )
  );
  console.log(
    "PASS complete C++ sources, syntax tokens, continuous line numbers and semantic mappings"
  );

  const timers = new Map();
  let timerId = 0;
  const schedule = (callback) => {
    timers.set(++timerId, callback);
    return timerId;
  };
  const flush = () => {
    for (const [id, callback] of timers) {
      timers.delete(id);
      callback();
    }
  };
  const props = {
    definition: { key: "cpu.sjf", title: "SJF", category: "cpu" },
  };
  const requests = [];
  const pending = [];
  const iframe = {};
  const module = { exports: {} };
  const source = parse(
    fs.readFileSync(
      path.join(root, "src/components/learning/AlgorithmDemoPlayer.vue"),
      "utf8"
    )
  ).descriptor.scriptSetup.content;
  vm.runInNewContext(
    transpile(
      source +
        "\nexport { onMessage, frameRef, sourceRows, activeLines, running, paused, runtimeError, loadSource };"
    ),
    {
      module,
      exports: module.exports,
      require: (name) => {
        if (name === "vue")
          return {
            ref: (value) => ({ value }),
            computed: (fn) => ({
              get value() {
                return fn();
              },
            }),
            watch: (getter, callback) => callback(),
            onMounted: () => undefined,
            onBeforeUnmount: () => undefined,
          };
        if (name === "@/simulation/cppSource") return sourceModule.exports;
        if (name === "@/simulation/legacy")
          return {
            LEGACY_DEMO_RESOURCES: {
              "cpu.sjf": { route: "/unit3/SJF", sourceFile: "unit3/SJF.vue" },
            },
            serializeLegacyInput: JSON.stringify,
          };
        throw Error(`Unexpected import ${name}`);
      },
      defineProps: () => props,
      defineEmits: () => () => undefined,
      window: {
        location: { origin: "http://localhost:8081" },
        setTimeout: schedule,
        clearTimeout: (id) => timers.delete(id),
      },
      requestAnimationFrame: schedule,
      cancelAnimationFrame: (id) => timers.delete(id),
      document: { getElementById: () => null },
      fetch: (url) => {
        requests.push(url);
        return new Promise((resolve) => pending.push(resolve));
      },
    }
  );
  const player = module.exports;
  player.frameRef.value = { contentWindow: iframe };
  assert.equal(requests[0], "/algorithm-code/cpu.sjf.cpp");
  pending.shift()({ ok: true, text: async () => example });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(player.sourceRows.value.length, 5);
  const send = (
    type,
    extra = {},
    origin = "http://localhost:8081",
    source = iframe
  ) =>
    player.onMessage({
      origin,
      source,
      data: {
        channel: "oslab-legacy-simulation",
        key: "cpu.sjf",
        type,
        ...extra,
      },
    });
  send("run-start", { runId: 10 });
  send("trace", {
    runId: 10,
    phase: "run",
    sequence: 1,
    file: "src/views/unit3/SJF.vue",
  });
  flush();
  assert.deepEqual(Array.from(player.activeLines.value), [3]);
  send("trace", {
    runId: 9,
    phase: "result",
    sequence: 999,
    file: "src/views/unit3/SJF.vue",
  });
  send("trace", {
    runId: 10,
    phase: "result",
    sequence: 2,
    file: "src/views/unit5/FCFS.vue",
  });
  send(
    "trace",
    {
      runId: 10,
      phase: "result",
      sequence: 2,
      file: "src/views/unit3/SJF.vue",
    },
    "http://attacker.test"
  );
  send(
    "trace",
    {
      runId: 10,
      phase: "result",
      sequence: 2,
      file: "src/views/unit3/SJF.vue",
    },
    undefined,
    {}
  );
  flush();
  assert.deepEqual(Array.from(player.activeLines.value), [3]);
  send("trace", {
    runId: 10,
    phase: "unknown",
    line: 181,
    sequence: 2,
    file: "src/views/unit3/SJF.vue",
  });
  flush();
  assert.deepEqual(
    Array.from(player.activeLines.value),
    [3],
    "raw Vue line numbers must never drive C++ highlights"
  );
  send("play-state", { paused: true });
  assert.equal(player.paused.value, true);
  send("completed", { runId: 10 });
  flush();
  assert.deepEqual(Array.from(player.activeLines.value), [4]);
  send("trace", {
    runId: 10,
    phase: "run",
    sequence: 3,
    file: "src/views/unit3/SJF.vue",
  });
  flush();
  assert.deepEqual(Array.from(player.activeLines.value), [4]);
  send("reset", { runId: 11 });
  assert.equal(player.activeLines.value.length, 0);
  assert.equal(player.running.value, false);
  assert.equal(player.paused.value, false);

  const older = player.loadSource();
  const newer = player.loadSource();
  const first = pending.shift();
  const second = pending.shift();
  second({ ok: true, text: async () => example.replace("iostream", "vector") });
  await newer;
  first({ ok: true, text: async () => example });
  await older;
  assert.equal(player.sourceRows.value[0].text, "#include <vector>");
  console.log(
    "PASS source fetch races, real event mapping, stale run rejection, pause/reset and origin checks"
  );
}
main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
