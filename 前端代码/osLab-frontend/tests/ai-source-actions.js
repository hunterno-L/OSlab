// Run with: node tests/ai-source-actions.js
// 来源引用卡与下一步入口：课程来源可定位到章节 chunk，个人资料打开段落对话框；
// 下一步映射复用原页面入口，补学返回到上一任务且不重启任务。
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");
const ts = require("typescript");

const read = (file) =>
  fs.readFileSync(path.join(__dirname, "..", file), "utf8");

function evaluate(source, requireDependency) {
  const module = { exports: {} };
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.CommonJS,
    },
  }).outputText;
  new Function("require", "module", "exports", compiled)(
    requireDependency,
    module,
    module.exports
  );
  return module.exports;
}

const actions = evaluate(read("src/utils/aiActions.ts"), () => {
  throw new Error("aiActions 不应有运行时依赖");
});
const sources = evaluate(read("src/utils/aiSources.ts"), (name) => {
  if (name === "@/utils/aiActions") return actions;
  throw new Error(`unexpected aiSources dependency: ${name}`);
});

const events = [];
const timers = [];
global.window = {
  setTimeout(callback, ms) {
    timers.push({ callback, ms });
    return timers.length;
  },
  dispatchEvent(event) {
    events.push(event);
    return true;
  },
};
global.CustomEvent = class {
  constructor(type, init) {
    this.type = type;
    this.detail = init?.detail;
  }
};
const classes = new Set();
let scrolled = 0;
global.document = {
  querySelector(selector) {
    if (selector === "#chunk-chunk-2") {
      return {
        scrollIntoView() {
          scrolled += 1;
        },
        classList: {
          add: (name) => classes.add(name),
          remove: (name) => classes.delete(name),
        },
      };
    }
    return null;
  },
};

const courseSource = {
  sourceId: "chunk-2",
  ownerScope: "COURSE",
  courseCode: "course.database-systems",
  revision: 3,
  blockId: "course.database-systems::chunk-2::p1",
  locator: "sec.fd#chunk-2-p1",
  excerpt: "候选键必须满足最小性。",
};
const personalSource = {
  sourceId: "material:88",
  ownerScope: "PERSONAL",
  courseCode: "course.operating-systems",
  revision: 1,
  blockId: "course.operating-systems::material:88::p2",
  locator: "material:88#p2",
  excerpt: "我的闭包笔记",
};

assert.equal(sources.aiSourceTitle(courseSource), "候选键必须满足最小性。");
assert.equal(sources.aiSourceScopeLabel(courseSource), "课程资料");
assert.equal(sources.aiSourceScopeLabel(personalSource), "我的资料");
assert.equal(sources.aiSourceRevisionLabel(courseSource), "修订 3");
assert.equal(
  sources.aiSourceRevisionLabel({ ...courseSource, revision: null }),
  "版本未知"
);
assert.equal(sources.aiSourceChunkCode(courseSource), "chunk-2");
assert.equal(sources.aiSourceSectionCode(courseSource), "sec.fd");
assert.equal(sources.aiSourceMaterialId(personalSource), "88");

const route = sources.aiSourceRoute(courseSource);
assert.deepEqual(route, {
  path: "/graph",
  query: {
    courseCode: "course.database-systems",
    sectionCode: "sec.fd",
    chunk: "chunk-2",
  },
});
assert.equal(sources.aiSourceRoute(personalSource), null);

assert.equal(sources.highlightSourceInPage(courseSource), true);
assert.equal(scrolled, 1);
assert.ok(classes.has("ai-source-highlight"));
assert.equal(timers.length, 1);
assert.equal(timers[0].ms, 2600);
timers[0].callback();
assert.ok(!classes.has("ai-source-highlight"), "高亮应在 2.6s 后移除");

assert.equal(sources.aiSourceMaterialId(courseSource), "");
assert.equal(sources.openAiMaterialSource(personalSource), true);
assert.equal(events.length, 1);
assert.equal(events[0].type, "oslab:ai-open-material");
assert.deepEqual(events[0].detail, {
  materialId: "88",
  paragraph: 2,
  title: "material:88#p2",
});
assert.equal(sources.openAiMaterialSource(courseSource), false);
sources.openAiMaterial("99", 4);
assert.equal(events.length, 2);
assert.deepEqual(events[1].detail, { materialId: "99", paragraph: 4 });

const unitAction = {
  actionId: "act-unit",
  type: "READ",
  targetRef: { kind: "UNIT", code: "unit.db.fd", anchor: "chunk-2" },
  reason: "",
  evidenceIds: [],
  estimatedMinutes: 5,
  status: "PENDING",
  returnRef: { kind: "QUESTION", code: "q.1", anchor: null },
};
assert.deepEqual(
  actions.nextActionTarget(unitAction, "course.database-systems"),
  {
    path: "/courses/course.database-systems/units/unit.db.fd",
    query: { activity: "chunk-2", aiActionId: "act-unit" },
  }
);
assert.deepEqual(
  actions.targetLocation(unitAction.returnRef, "course.database-systems"),
  {
    path: "/assessments",
    query: {
      courseCode: "course.database-systems",
      questionId: "q.1",
    },
  }
);
const questionAction = {
  ...unitAction,
  actionId: "act-q",
  targetRef: { kind: "QUESTION", code: "q.9", anchor: null },
};
assert.deepEqual(
  actions.nextActionTarget(questionAction, "course.operating-systems"),
  {
    path: "/assessments",
    query: {
      courseCode: "course.operating-systems",
      questionId: "q.9",
      aiActionId: "act-q",
    },
  }
);
const predictAction = {
  ...unitAction,
  actionId: "act-p",
  targetRef: {
    kind: "SIMULATION",
    code: "page.lru-vs-fifo.victim",
    anchor: null,
  },
};
const predictTarget = actions.nextActionTarget(
  predictAction,
  "course.operating-systems"
);
assert.equal(predictTarget.path, "/experiments/simulation");
assert.equal(predictTarget.query.algorithmKey, "page.lru");
assert.equal(predictTarget.query.predictCase, "page.lru-vs-fifo.victim");
assert.equal(predictTarget.query.aiActionId, "act-p");
assert.equal(
  actions.nextActionTarget(
    { ...unitAction, targetRef: { kind: "MATERIAL", code: "x", anchor: null } },
    "course.operating-systems"
  ),
  null
);
assert.equal(
  actions.simulationAlgorithmForPredictCase("page.count.faults-vs-evictions"),
  "page.fifo"
);
assert.equal(
  actions.simulationAlgorithmForPredictCase("page.lru-vs-fifo.victim"),
  "page.lru"
);
assert.equal(
  actions.simulationAlgorithmForPredictCase("page.lru.hit-update-order"),
  "page.lru"
);
assert.equal(actions.materialIdFromBlockId(personalSource.blockId), "88");
assert.equal(actions.paragraphFromLocator(personalSource.locator), 2);

const cardSource = read("src/components/ai/AiNextActionCard.vue");
assert.ok(cardSource.includes("补学后返回"));
assert.ok(cardSource.includes("targetLocation(props.action.returnRef"));

console.log(
  "AI source/action contracts passed: source location, personal material dialog, next-action routes, remediation return without restarting the previous action."
);
