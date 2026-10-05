// Run with: node tests/node-workbench-cache.js
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");
const ts = require("typescript");

const source = fs.readFileSync(
  path.join(__dirname, "../src/composables/useNodeWorkbench.ts"),
  "utf8"
);
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    target: ts.ScriptTarget.ES2020,
    module: ts.ModuleKind.CommonJS,
  },
}).outputText;
const listeners = new Map();
global.window = {
  addEventListener: (name, callback) => listeners.set(name, callback),
};
let requests = [];
let implementation = (nodeName) =>
  new Promise((resolve, reject) =>
    requests.push({ nodeName, resolve, reject })
  );
const cacheModule = { exports: {} };
new Function("require", "module", "exports", compiled)(
  (name) => {
    assert.equal(name, "@/api/learning");
    return { nodeWorkbench: (nodeName) => implementation(nodeName) };
  },
  cacheModule,
  cacheModule.exports
);
const { loadNodeWorkbench, peekNodeWorkbench, invalidateNodeWorkbench } =
  cacheModule.exports;
const response = (name) => ({
  data: { code: 0, message: "", data: { mastery: { name } } },
});

async function run() {
  const first = loadNodeWorkbench(1, "LRU");
  const duplicate = loadNodeWorkbench(1, "LRU");
  assert.equal(
    first,
    duplicate,
    "same user and node share the in-flight promise"
  );
  assert.equal(requests.length, 1);
  requests[0].resolve(response("user-1"));
  await first;
  assert.equal(peekNodeWorkbench(1, "LRU").mastery.name, "user-1");
  await loadNodeWorkbench(1, "LRU");
  assert.equal(requests.length, 1, "fresh reads reuse the cached response");

  const otherUser = loadNodeWorkbench(2, "LRU");
  const otherNode = loadNodeWorkbench(1, "FIFO");
  assert.equal(requests.length, 3, "users and node names are separate keys");
  requests[1].resolve(response("user-2"));
  requests[2].resolve(response("FIFO"));
  await Promise.all([otherUser, otherNode]);
  assert.equal(peekNodeWorkbench(1, "LRU").mastery.name, "user-1");
  assert.equal(peekNodeWorkbench(2, "LRU").mastery.name, "user-2");

  listeners.get("oslab:learning-changed")();
  assert.equal(
    peekNodeWorkbench(1, "LRU"),
    null,
    "learning mutations invalidate cached data"
  );
  const stale = loadNodeWorkbench(1, "LRU");
  invalidateNodeWorkbench();
  requests[3].resolve(response("outdated"));
  await stale;
  assert.equal(
    peekNodeWorkbench(1, "LRU"),
    null,
    "invalidated pending responses cannot repopulate the cache"
  );

  let calls = 0;
  implementation = async (nodeName) => {
    calls += 1;
    return response(nodeName);
  };
  const realNow = Date.now;
  let now = realNow();
  Date.now = () => now;
  try {
    await loadNodeWorkbench(1, "LRU");
    now += 30001;
    assert.equal(
      peekNodeWorkbench(1, "LRU"),
      null,
      "entries expire after 30 seconds"
    );
    await loadNodeWorkbench(1, "LRU");
    assert.equal(calls, 2);
    invalidateNodeWorkbench();
    for (let index = 0; index < 25; index += 1)
      await loadNodeWorkbench(1, `node-${index}`);
    assert.equal(
      peekNodeWorkbench(1, "node-0"),
      null,
      "the cache is bounded to 24 nodes"
    );
    assert.notEqual(peekNodeWorkbench(1, "node-24"), null);
  } finally {
    Date.now = realNow;
  }

  invalidateNodeWorkbench();
  implementation = async () => ({
    data: { code: 50000, message: "unavailable", data: null },
  });
  await assert.rejects(loadNodeWorkbench(1, "LRU"), /unavailable/);
  assert.equal(
    peekNodeWorkbench(1, "LRU"),
    null,
    "business failures are never cached"
  );
  console.log(
    "Workbench cache: deduplication, isolation, mutation invalidation, stale-response guard, TTL, capacity and error checks passed."
  );
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
