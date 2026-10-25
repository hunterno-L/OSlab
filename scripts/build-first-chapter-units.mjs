import fs from "fs";
import path from "path";

const root = path.resolve(".");
const knowledge = JSON.parse(
  fs.readFileSync(
    path.join(
      root,
      "artifacts/content-import/20261019-draft-import/knowledge-original/knowledge.json"
    ),
    "utf8"
  )
);
const questions = JSON.parse(
  fs.readFileSync(
    path.join(
      root,
      "artifacts/content-import/20261019-draft-import/questions-original/questions.json"
    ),
    "utf8"
  )
).questions;
const nodes = new Map(knowledge.nodes.map((node) => [node.nodeCode, node]));
const chunks = new Map(knowledge.chunks.map((chunk) => [chunk.chunkCode, chunk]));
const questionByCode = new Map(
  questions.map((question) => [question.questionCode, question])
);

// 争议题未解决前保持 DRAFT，不进入正式单元映射，避免阻断其余单元发布。
const pendingQuestionCodes = new Set([
  "q.wd25.s1_3.005",
  "q.wd25.s1_3.016",
  "q.wd25.s1_2.009",
  "q.ext.s1_2.001",
  "q.ext.s1_3.001",
  "q.ext.s1_3.003",
]);

const units = [
  {
    unitCode: "unit.overview.basic-concepts",
    sectionCode: "sec.overview.basic-concepts",
    title: "操作系统是什么，有哪些基本特征",
    estimatedMinutes: 18,
    objectives: [
      "能说明操作系统在硬件、应用程序和用户之间的位置与基本职责",
      "能在具体情境中区分并发与并行、共享的两种访问方式",
      "能用时分与空分解释虚拟资源，并说明异步性的来源",
    ],
    chunkSuffixes: [
      "s1_1.system-role",
      "s1_1.concurrent-parallel",
      "s1_1.bread-writing",
      "s1_1.sharing-modes",
      "s1_1.concurrency-sharing-link",
      "s1_1.virtual-resources",
      "s1_1.asynchronous-progress",
    ],
    selectedQuestions: [
      "q.wd25.s1_1.001",
      "q.wd25.s1_1.005",
      "q.wd25.s1_1.006",
    ],
  },
  {
    unitCode: "unit.overview.services",
    sectionCode: "sec.overview.basic-concepts",
    title: "操作系统为谁服务、管理什么",
    estimatedMinutes: 16,
    objectives: [
      "能按处理机、存储器、文件和设备说明资源管理职责",
      "能区分命令接口、程序接口和图形用户界面",
      "能说明操作系统作为扩展机器的作用",
    ],
    chunkSuffixes: [
      "s1_1.resource-manager",
      "s1_1.command-control",
      "s1_1.program-service",
      "s1_1.machine-extension",
      "s1_1.basic-summary",
    ],
    selectedQuestions: [
      "q.wd25.s1_1.004",
      "q.wd25.s1_1.009",
      "q.wd25.s1_1.014",
    ],
  },
  {
    unitCode: "unit.overview.history",
    sectionCode: "sec.overview.history",
    title: "从手工操作到现代操作系统",
    estimatedMinutes: 28,
    objectives: [
      "能沿时间线比较手工、单道批处理、多道批处理、分时和实时系统",
      "能说明多道程序设计利用等待时间的前提与代价",
      "能区分网络系统与分布式系统的共享和透明性",
    ],
    chunkSuffixes: [
      "s1_2.manual-bottleneck",
      "s1_2.single-batch",
      "s1_2.multi-batch",
      "s1_2.batch-benefit-cost",
      "s1_2.timeslice-terminals",
      "s1_2.deadline-systems",
      "s1_2.network-distribution",
      "s1_2.personal-and-special",
      "s1_2.figure-1-1",
    ],
    selectedQuestions: [
      "q.wd25.s1_2.001",
      "q.wd25.s1_2.008",
      "q.wd25.s1_2.017",
    ],
  },
  {
    unitCode: "unit.overview.runtime-modes",
    sectionCode: "sec.overview.runtime",
    title: "程序如何在用户态与内核态运行",
    estimatedMinutes: 22,
    objectives: [
      "能区分用户态与内核态、普通指令与特权指令",
      "能说明内核、原语和内核态数据结构的作用",
      "能判断受控状态切换与事件类型",
    ],
    chunkSuffixes: [
      "s1_3.privilege-model",
      "s1_3.kernel-scope",
      "s1_3.atomic-primitives",
      "s1_3.kernel-state-data",
      "s1_3.entry-and-mode",
      "s1_3.mode-events",
    ],
    selectedQuestions: [
      "q.wd25.s1_3.006",
      "q.wd25.s1_3.008",
      "q.wd25.s1_3.036",
    ],
  },
  {
    unitCode: "unit.overview.interrupts",
    sectionCode: "sec.overview.runtime",
    title: "中断与异常怎样改变执行过程",
    estimatedMinutes: 22,
    objectives: [
      "能按事件来源区分外部中断与内部异常",
      "能说明中断处理、恢复和返回的基本过程",
      "能区分中断入口与普通 CALL",
    ],
    chunkSuffixes: [
      "s1_3.clock-interrupt",
      "s1_3.event-origins",
      "s1_3.exception-kinds",
      "s1_3.service-return",
      "s1_3.call-comparison",
      "s1_3.figure-1-2",
    ],
    selectedQuestions: [
      "q.wd25.s1_3.004",
      "q.wd25.s1_3.018",
      "q.wd25.s1_3.029",
    ],
  },
  {
    unitCode: "unit.overview.system-calls",
    sectionCode: "sec.overview.runtime",
    title: "应用如何请求操作系统服务",
    estimatedMinutes: 22,
    objectives: [
      "能描述一次系统调用从用户态进入内核再返回的顺序",
      "能区分系统调用、普通过程调用和库函数",
      "能说明系统调用的服务入口与参数传递",
    ],
    chunkSuffixes: [
      "s1_3.system-services",
      "s1_3.system-call-sequence",
      "s1_3.figure-1-3",
    ],
    optionalChunkSuffixes: ["s1_1.program-service", "s1_3.entry-and-mode"],
    selectedQuestions: [
      "q.wd25.s1_3.003",
      "q.wd25.s1_3.020",
      "q.wd25.s1_3.030",
    ],
  },
  {
    unitCode: "unit.overview.structure",
    sectionCode: "sec.overview.structure",
    title: "操作系统如何组织内部结构",
    estimatedMinutes: 28,
    objectives: [
      "能比较分层与模块化的组织边界和取舍",
      "能说明宏内核、微内核和外核的职责划分",
      "能结合图示判断结构类型的收益与开销",
    ],
    chunkSuffixes: [
      "s1_4.layer-organization",
      "s1_4.module-boundaries",
      "s1_4.large-small-kernel",
      "s1_4.server-isolation",
      "s1_4.mechanisms-policies",
      "s1_4.microkernel-cost",
      "s1_4.exokernel-resource",
      "s1_4.figure-1-4",
      "s1_4.figure-1-5",
      "s1_4.figure-1-6",
    ],
    selectedQuestions: [
      "q.wd25.s1_6.002",
      "q.wd25.s1_6.006",
      "q.wd25.s1_6.022",
    ],
  },
  {
    unitCode: "unit.overview.boot",
    sectionCode: "sec.overview.boot",
    title: "计算机启动时发生什么",
    estimatedMinutes: 12,
    objectives: [
      "能按教材模型说明 BIOS/MBR 与引导记录的作用",
      "能区分引导程序、引导记录和启动管理器的位置与职责",
    ],
    chunkSuffixes: ["s1_5.firmware-to-os", "s1_5.boot-records"],
    selectedQuestions: [
      "q.wd25.s1_6.012",
      "q.wd25.s1_6.015",
      "q.wd25.s1_6.020",
    ],
  },
  {
    unitCode: "unit.overview.virtual-machine",
    sectionCode: "sec.overview.virtual-machine",
    title: "虚拟机把什么抽象给用户",
    estimatedMinutes: 16,
    objectives: [
      "能说明客户机、宿主与 VMM 的关系",
      "能比较裸金属与寄居两类虚拟机管理程序的差异",
      "能判断虚拟资源与权限边界",
    ],
    chunkSuffixes: [
      "s1_6.vm-environment",
      "s1_6.virtual-privileges",
      "s1_6.hosted-machine",
      "s1_6.vmm-summary",
      "s1_6.figure-1-7",
    ],
    selectedQuestions: [
      "q.wd25.s1_6.017",
      "q.wd25.s1_6.018",
      "q.wd25.s1_6.019",
    ],
  },
  {
    unitCode: "unit.overview.review",
    sectionCode: "sec.overview.review",
    title: "本章复习与综合测评",
    estimatedMinutes: 24,
    objectives: [
      "能把并发/并行、特权指令与访管、内核结构三条主线联系起来",
      "能根据个人错题与待复习节点选择下一步复习内容",
      "能在跨节综合题中说明教材概念的适用边界",
    ],
    chunkSuffixes: [
      "s1_7.time-scale-review",
      "s1_7.instruction-review",
      "s1_7.trap-review",
      "s1_7.microkernel-review",
    ],
    selectedQuestions: [
      "q.wd25.s1_1.013",
      "q.wd25.s1_1.014",
      "q.wd25.s1_2.019",
      "q.wd25.s1_6.020",
      "q.wd25.s1_6.021",
      "q.wd25.s1_6.022",
    ],
  },
];

const issue = (message) => {
  throw new Error(message);
};
const chunkCode = (suffix) => `chunk.wd25.overview.${suffix}.001`;
const activitySafe = (value) =>
  value
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "");

const seenChunks = new Set();
const seenQuestions = new Set();
const outputUnits = units.map((config, unitIndex) => {
  const suffix = config.unitCode.replace("unit.overview.", "");
  const unitChunks = [...config.chunkSuffixes, ...(config.optionalChunkSuffixes || [])]
    .map((item) => {
      const code = chunkCode(item);
      const chunk = chunks.get(code);
      if (!chunk) issue(`未知 chunkCode: ${code}`);
      if (config.chunkSuffixes.includes(item) && seenChunks.has(code)) {
        issue(`必读 chunk 重复: ${code}`);
      }
      if (config.chunkSuffixes.includes(item)) seenChunks.add(code);
      return { chunk, optional: (config.optionalChunkSuffixes || []).includes(item) };
    });

  const primaryNodes = [];
  const nodeMappings = [];
  const addNode = (nodeCode, role) => {
    if (!nodeCode) return;
    if (!nodes.has(nodeCode)) issue(`未知 nodeCode: ${nodeCode}`);
    const existing = nodeMappings.find((item) => item.nodeCode === nodeCode);
    if (existing) {
      if (role === "TEACHES") existing.role = "TEACHES";
      return;
    }
    nodeMappings.push({ nodeCode, role });
  };

  for (const { chunk } of unitChunks) {
    for (const nodeCode of chunk.nodeCodes || []) addNode(nodeCode, "TEACHES");
  }

  const questionMappings = [];
  for (const question of questions) {
    for (const suggestion of question.usageSuggestions || []) {
      if (suggestion.unitCode !== config.unitCode) continue;
      if (pendingQuestionCodes.has(question.questionCode)) continue;
      if (questionMappings.some((item) => item.questionCode === question.questionCode)) {
        continue;
      }
      questionMappings.push({
        questionCode: question.questionCode,
        usage: suggestion.usage,
      });
    }
  }
  questionMappings.sort((a, b) => a.questionCode.localeCompare(b.questionCode));

  const selected = config.selectedQuestions.map((code) => {
    const question = questionByCode.get(code);
    if (!question) issue(`未知 questionCode: ${code}`);
    const mapping = questionMappings.find((item) => item.questionCode === code);
    if (!mapping) issue(`必做题未出现在 ${config.unitCode} 的 questionMappings: ${code}`);
    seenQuestions.add(code);
    for (const mappingNode of question.nodeMappings || []) {
      if (mappingNode.role !== "PRIMARY") continue;
      primaryNodes.push(mappingNode.nodeCode);
      addNode(mappingNode.nodeCode, "PREREQUISITE");
    }
    return question;
  });

  const activities = [];
  let sortOrder = 1;
  for (const { chunk, optional } of unitChunks) {
    const activityType =
      chunk.chunkType === "SUMMARY"
        ? "SUMMARY"
        : chunk.chunkType === "FIGURE" || chunk.chunkType === "WORKED_EXAMPLE"
        ? "WORKED_EXAMPLE"
        : "READING";
    const chunkKey = chunk.chunkCode
      .replace("chunk.wd25.overview.", "")
      .replace(/\.001$/, "");
    activities.push({
      activityCode: `act.overview.${suffix}.read.${activitySafe(chunkKey)}`,
      title: `${activityType === "SUMMARY" ? "小结" : activityType === "WORKED_EXAMPLE" ? "示例/图示" : "阅读"}：${chunk.title}`,
      activityType,
      sortOrder: sortOrder++,
      required: !optional,
      reference: { kind: "CHUNK", code: chunk.chunkCode },
      config: {},
      completionRule: "CONFIRM",
    });
  }
  for (const question of selected) {
    const suggestion = questionMappings.find(
      (item) => item.questionCode === question.questionCode
    );
    const assessment = suggestion.usage === "ASSESSMENT" || suffix === "review";
    activities.push({
      activityCode: `act.overview.${suffix}.q.${activitySafe(
        question.questionCode.replace(/^q\./, "")
      )}`,
      title: `${assessment ? "测评" : "练习"}：${question.title}`,
      activityType: assessment ? "ASSESSMENT" : "PRACTICE",
      sortOrder: sortOrder++,
      required: true,
      reference: { kind: "QUESTION", code: question.questionCode },
      config: {},
      completionRule: assessment ? "PASS" : "SUBMIT",
    });
  }

  return {
    unitCode: config.unitCode,
    sectionCode: config.sectionCode,
    title: config.title,
    sortOrder: unitIndex + 1,
    estimatedMinutes: config.estimatedMinutes,
    status: "DRAFT",
    objectives: config.objectives,
    nodeMappings,
    questionMappings,
    activities,
  };
});

if (seenChunks.size !== chunks.size) {
  const missing = [...chunks.keys()].filter((code) => !seenChunks.has(code));
  issue(`仍有未编排教学块: ${missing.join(", ")}`);
}
const usedQuestions = new Set();
for (const unit of outputUnits) {
  for (const mapping of unit.questionMappings) usedQuestions.add(mapping.questionCode);
}
const unmapped = questions
  .map((question) => question.questionCode)
  .filter((code) => !usedQuestions.has(code) && !pendingQuestionCodes.has(code));
if (unmapped.length) {
  issue(`仍有题目未进入任何单元映射: ${unmapped.join(", ")}`);
}

const output = {
  schemaVersion: "oslab-authoring/1.0",
  packageType: "UNITS",
  batchCode: "batch.overview.units.first-chapter.001",
  contributor: "OSLab agent（第一章十单元编排草案）",
  courseCode: "course.operating-systems",
  note:
    "第一章十单元编排草案，供 L 审核后走 preview → commit → 审核 → 发布；本文件不执行数据库导入或发布。" +
    "J v3 知识包沿用既有 35 节点/57 块/27 关系/7 图，不再重审或重导。" +
    "questionMappings 采用 A 的 usageSuggestions；六道争议题（q.wd25.s1_3.005/.016、q.wd25.s1_2.009、q.ext.s1_2.001、q.ext.s1_3.001/.003）在结论确认前保持 DRAFT 且不进入正式映射。" +
    "必做活动只选 2–4 道（复习单元 6 道），其余仅作配套练习。" +
    "第一章未加入模拟或编程实验；所有活动引用真实 chunkCode/questionCode。" +
    "本包未执行导入或发布：需先发布教学块与题目，再对单元执行 preview → commit → 审核 → 发布并确认图谱同步。",
  units: outputUnits,
};

const outDir = path.join(root, "artifacts/course-first-chapter/20261020");
fs.mkdirSync(outDir, { recursive: true });
const outFile = path.join(outDir, "units-first-chapter.draft.json");
fs.writeFileSync(outFile, `${JSON.stringify(output, null, 2)}\n`, "utf8");

console.log(`Wrote ${outFile}`);
console.log(`Units: ${outputUnits.length}`);
console.log(
  `Activities: ${outputUnits.reduce((sum, unit) => sum + unit.activities.length, 0)}`
);
console.log(
  `Question mappings: ${outputUnits.reduce((sum, unit) => sum + unit.questionMappings.length, 0)}`
);
console.log(
  `Required question activities: ${outputUnits.reduce(
    (sum, unit) =>
      sum +
      unit.activities.filter(
        (activity) =>
          activity.required &&
          (activity.activityType === "PRACTICE" ||
            activity.activityType === "ASSESSMENT")
      ).length,
    0
  )}`
);
console.log(`Chunks covered: ${seenChunks.size}/${chunks.size}`);
console.log(
  `Questions mapped: ${usedQuestions.size}/${questions.length - pendingQuestionCodes.size} (pending disputes: ${pendingQuestionCodes.size})`
);
