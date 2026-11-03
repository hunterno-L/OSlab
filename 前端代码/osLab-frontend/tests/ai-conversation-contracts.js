// Run with: node tests/ai-conversation-contracts.js
// 统一课程会话契约：localStorage 只存 sessionId 指针、跨页面恢复同一会话、
// clientRequestId 幂等、轮询间隔 ≥1.5s、FALLBACK 可见、下一步计划与会话同步。
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");
const ts = require("typescript");
const vue = require("vue");

const read = (file) =>
  fs.readFileSync(path.join(__dirname, "..", file), "utf8");

function transpile(source) {
  return ts.transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.CommonJS,
    },
  }).outputText;
}

function evaluate(source, requireDependency) {
  const module = { exports: {} };
  new Function("require", "module", "exports", transpile(source))(
    requireDependency,
    module,
    module.exports
  );
  return module.exports;
}

function createStorage() {
  const map = new Map();
  return {
    map,
    getItem: (key) => (map.has(key) ? map.get(key) : null),
    setItem: (key, value) => map.set(key, String(value)),
    removeItem: (key) => map.delete(key),
    dump: () => Object.fromEntries(map.entries()),
  };
}

function createStore(initialUserId) {
  const watchers = [];
  const store = {
    state: { user: { loginUser: { id: initialUserId } } },
    watch(getter, callback) {
      watchers.push({ getter, callback });
      return () => undefined;
    },
  };
  return {
    store,
    setUser(id) {
      store.state.user.loginUser.id = id;
      for (const watcher of watchers) {
        const value = watcher.getter();
        if (value !== watcher.last) {
          const previous = watcher.last;
          watcher.last = value;
          watcher.callback(value, previous);
        }
      }
    },
    settle() {
      for (const watcher of watchers) watcher.last = watcher.getter();
    },
  };
}

async function run() {
  const storage = createStorage();
  const user = createStore(7);
  user.settle();
  const course = "course.database-systems";
  const pointerKey = `oslab-ai-session-pointer-7`;
  storage.setItem(pointerKey, JSON.stringify({ [course]: "sess-restored" }));

  const delays = [];
  const events = [];
  global.localStorage = storage;
  global.window = {
    setTimeout(callback, ms) {
      delays.push(ms);
      callback();
      return 0;
    },
    dispatchEvent(event) {
      events.push(event.type);
      return true;
    },
  };
  global.CustomEvent = class {
    constructor(type, init) {
      this.type = type;
      this.detail = init?.detail;
    }
  };

  const sessions = new Map([
    [
      "sess-restored",
      {
        sessionId: "sess-restored",
        courseCode: course,
        goal: "继续当前任务",
        focus: "LEARN",
        timeBudgetMinutes: null,
        currentAction: null,
        modelProfileId: null,
        rowVersion: 1,
        status: "ACTIVE",
        unitCode: null,
      },
    ],
  ]);
  const calls = { createTurn: [], updates: [], nextActions: [] };
  const api = {
    createAiSession: async (body) => {
      calls.createSession = body;
      const session = {
        sessionId: "sess-new",
        courseCode: body.courseCode,
        goal: body.goal,
        focus: body.focus,
        timeBudgetMinutes: body.timeBudgetMinutes ?? null,
        currentAction: null,
        modelProfileId: null,
        rowVersion: 1,
        status: "ACTIVE",
        unitCode: body.unitCode ?? null,
      };
      sessions.set(session.sessionId, session);
      return { data: { code: 0, data: session } };
    },
    getAiSession: async (sessionId) => {
      const session = sessions.get(sessionId);
      if (!session) {
        return { data: { code: 40400, message: "会话不存在", data: null } };
      }
      return { data: { code: 0, data: { ...session } } };
    },
    updateAiSession: async (sessionId, body) => {
      calls.updates.push({ sessionId, body });
      const session = sessions.get(sessionId);
      if (!session) {
        return { data: { code: 40400, message: "会话不存在", data: null } };
      }
      Object.assign(session, body);
      return { data: { code: 0, data: { ...session } } };
    },
    createAiTurn: async (sessionId, body) => {
      calls.createTurn.push({ sessionId, body });
      return {
        data: {
          code: 0,
          data: {
            turnId: "turn-1",
            status: "RUNNING",
            answer: "",
            mode: "AI",
            sources: [],
            toolTrace: [],
            diagnosis: null,
            nextActions: [],
            usage: null,
          },
        },
      };
    },
    getAiTurn: async () => ({
      data: {
        code: 0,
        data: {
          turnId: "turn-1",
          status: "SUCCEEDED",
          answer: "候选键是最小超键。",
          mode: "FALLBACK",
          sources: [
            {
              sourceId: "src-1",
              ownerScope: "COURSE",
              courseCode: course,
              revision: 2,
              blockId: `${course}::chunk-1`,
              locator: "sec.1#p2",
              excerpt: "候选键定义",
            },
          ],
          toolTrace: [
            { tool: "search_sources", status: "OK", ms: 12, summary: "1 条" },
          ],
          diagnosis: null,
          nextActions: [
            {
              actionId: "act-2",
              type: "QUESTION",
              targetRef: { kind: "QUESTION", code: "q.1", anchor: null },
              reason: "用一道题验证最小性。",
              estimatedMinutes: 3,
              evidenceIds: ["ev-1"],
              status: "PENDING",
              returnRef: null,
            },
          ],
          usage: { model: "masked-model", latencyMs: 800, tokens: null },
        },
      },
    }),
    listAiNextActions: async (params) => {
      calls.nextActions.push(params);
      return {
        data: {
          code: 0,
          data: {
            actions: [
              {
                actionId: "act-3",
                type: "READ",
                targetRef: {
                  kind: "UNIT",
                  code: "unit.db.fd",
                  anchor: "chunk-2",
                },
                reason: "先补属性闭包。",
                estimatedMinutes: 5,
                evidenceIds: ["ev-2"],
                status: "PENDING",
                returnRef: {
                  kind: "QUESTION",
                  code: "q.1",
                  anchor: null,
                },
              },
            ],
            generatedAt: "2026-11-03T10:00:00Z",
            focus: "LEARN",
            sessionId: "sess-plan",
            timeBudgetMinutes: 15,
            dueReviewCount: 2,
          },
        },
      };
    },
  };
  sessions.set("sess-plan", {
    sessionId: "sess-plan",
    courseCode: course,
    goal: "继续当前任务",
    focus: "LEARN",
    timeBudgetMinutes: 15,
    currentAction: null,
    modelProfileId: null,
    rowVersion: 3,
    status: "ACTIVE",
    unitCode: null,
  });

  const pageContext = evaluate(
    read("src/composables/useAiPageContext.ts"),
    () => {
      throw new Error("useAiPageContext 不应有外部依赖");
    }
  );
  pageContext.registerAiPageContext(() => ({
    pageName: "课程单元",
    courseCode: course,
    unitCode: "unit.db.fd",
    nodeCode: "db.closure",
    nodeCodes: ["db.closure", "db.candidate-key", "db.closure"],
    resourceRef: "chunk:chunk-1",
    eventIds: ["ev-1", "ev-1"],
    selectedSourceIds: [],
  }));

  const conversation = evaluate(
    read("src/composables/useAiConversation.ts"),
    (name) => {
      if (name === "vue") return vue;
      if (name === "@/store") return { __esModule: true, default: user.store };
      if (name === "@/api/ai") return api;
      if (name === "@/composables/useAiPageContext") return pageContext;
      throw new Error(`unexpected dependency: ${name}`);
    }
  );
  const state = conversation.useAiConversation();

  // 1) 跨页面恢复同一课程会话；localStorage 只存 sessionId 指针
  await conversation.syncAiCourse(course);
  assert.equal(state.session.value?.sessionId, "sess-restored");
  assert.equal(state.sessionRestored.value, true);
  let pointer = JSON.parse(storage.getItem(pointerKey));
  assert.deepEqual(pointer, { [course]: "sess-restored" });
  for (const [key, value] of storage.map) {
    assert.ok(key.startsWith("oslab-ai-session-pointer-"));
    assert.equal(typeof JSON.parse(value), "object");
    assert.ok(!String(value).includes("候选键"));
  }

  // 2) 一轮提问：clientRequestId + 页面上下文；轮询间隔 ≥1.5s；FALLBACK 保留
  const turn = await conversation.askAi("什么是候选键？");
  assert.equal(turn?.status, "SUCCEEDED");
  assert.equal(calls.createTurn.length, 1);
  assert.equal(calls.createTurn[0].sessionId, "sess-restored");
  assert.ok(calls.createTurn[0].body.clientRequestId);
  assert.equal(calls.createTurn[0].body.context.courseCode, course);
  assert.equal(calls.createTurn[0].body.context.unitCode, "unit.db.fd");
  assert.deepEqual(calls.createTurn[0].body.context.nodeCodes, [
    "db.closure",
    "db.candidate-key",
  ]);
  assert.deepEqual(calls.createTurn[0].body.context.eventIds, ["ev-1"]);
  assert.ok(delays.length >= 1, "RUNNING 轮次必须至少轮询一次");
  assert.ok(
    delays.every((ms) => ms >= 1500),
    `轮询间隔必须 ≥1500ms，实际 ${delays.join(",")}`
  );
  assert.equal(state.messages.value.length, 2);
  assert.equal(state.messages.value[1].mode, "FALLBACK");
  assert.equal(state.messages.value[1].sources.length, 1);
  assert.equal(state.messages.value[1].toolTrace.length, 1);
  assert.equal(state.nextActions.value[0].actionId, "act-2");
  assert.ok(events.includes("oslab:ai-turn-completed"));

  // 3) 下一步计划：采用服务端自动创建的 sessionId，并保持指针
  const actions = await conversation.refreshAiNextActions({
    courseCode: course,
    limit: 5,
  });
  assert.equal(actions.length, 1);
  assert.equal(state.dueReviewCount.value, 2);
  assert.equal(state.planTimeBudgetMinutes.value, 15);
  assert.equal(state.session.value?.sessionId, "sess-plan");
  pointer = JSON.parse(storage.getItem(pointerKey));
  assert.deepEqual(pointer, { [course]: "sess-plan" });

  // 4) 切换用户：清空内存态，使用该用户自己的指针（不串号）
  const oldPointer = storage.getItem(pointerKey);
  user.setUser(8);
  assert.equal(state.session.value, null);
  assert.equal(state.messages.value.length, 0);
  assert.equal(state.activeCourseCode.value, "");
  assert.equal(storage.getItem("oslab-ai-session-pointer-8"), null);
  assert.equal(storage.getItem(pointerKey), oldPointer);

  // 5) 页面上下文：合并去重、显式覆盖、摘要与注销
  const merged = pageContext.buildAiTurnContext({
    courseCode: "course.override",
    nodeCodes: ["db.extra", "db.closure"],
    eventIds: ["ev-3"],
    selectedSourceIds: ["chunk-9"],
  });
  assert.equal(merged.courseCode, "course.override");
  assert.equal(merged.unitCode, "unit.db.fd");
  assert.deepEqual(merged.nodeCodes, [
    "db.closure",
    "db.candidate-key",
    "db.extra",
  ]);
  assert.deepEqual(merged.eventIds, ["ev-1", "ev-3"]);
  assert.deepEqual(merged.selectedSourceIds, ["chunk-9"]);
  const summary = pageContext.buildPageContextSummary();
  assert.ok(summary.includes("课程单元"));
  assert.ok(summary.includes("db.closure"));
  const provider = pageContext.getAiPageContext;
  assert.ok(provider);
  pageContext.unregisterAiPageContext(() => null);
  assert.ok(pageContext.getAiPageContext(), "不得注销其他页面注册的 provider");
  const current = () => null;
  pageContext.registerAiPageContext(current);
  pageContext.unregisterAiPageContext(current);
  assert.equal(pageContext.getAiPageContext(), null);
  assert.equal(pageContext.buildPageContextSummary(), "");

  console.log(
    "AI conversation contracts passed: pointer-only storage, cross-page restore, idempotent turn ids, ≥1.5s polling, FALLBACK visibility, next-action session adoption, user isolation and page-context merging."
  );
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
