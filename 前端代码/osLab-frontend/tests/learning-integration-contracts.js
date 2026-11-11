const assert = require("assert");
const fs = require("fs");
const path = require("path");

const read = (relative) =>
  fs.readFileSync(path.join(__dirname, relative), "utf8");

const navigation = read("../src/navigation/appNavigation.ts");
const routes = read("../src/router/routes.ts");
const questionView = read("../src/views/learning/LearningQuestionsView.vue");
const unitView = read("../src/views/learning/CourseUnitView.vue");
const nodeView = read("../src/views/learning/NodeLearningWorkspaceView.vue");
const simulationView = read("../src/views/learning/SimulationView.vue");
const player = read("../src/components/learning/AlgorithmDemoPlayer.vue");
const questionApi = read("../src/api/learning.ts");
const courseApi = read("../src/api/course.ts");
const courseService = read(
  "../../../后端代码/osLab-backend/src/main/java/com/yupi/springbootinit/course/CourseService.java"
);
const questionService = read(
  "../../../后端代码/osLab-backend/src/main/java/com/yupi/springbootinit/service/impl/LearningQuestionServiceImpl.java"
);

// 单入口与路由上下文
assert(navigation.includes('path: "/graph"'), "导航必须保留 /graph");
assert(!/path:\s*"\/courses"/.test(navigation), "导航不再有独立 /courses 项");
assert(
  /path:\s*"\/courses"[\s\S]{0,160}path:\s*"\/graph"/.test(routes),
  "/courses 必须兼容跳转到 /graph"
);
assert(
  /path:\s*"\/courses\/:courseCode\/units\/:unitCode"[\s\S]{0,220}navKey:\s*"graph"/.test(
    routes
  ),
  "单元页 navKey 必须归入 graph"
);
assert(
  /path:\s*"\/knowledge\/concept\/:nodeCode"/.test(routes),
  "稳定节点工作台路由存在"
);
assert(
  /path:\s*"\/experiments\/simulation"[\s\S]{0,320}contentWidth:\s*"wide"/.test(
    routes
  ),
  "模拟入口应使用宽内容区"
);

// 节点返回上下文
assert(nodeView.includes("returnActivity"), "节点工作台需读取 returnActivity");
assert(nodeView.includes("returnMode"), "节点工作台需读取 returnMode");
assert(
  /switchNode[\s\S]{0,1200}returnTo/.test(nodeView),
  "switchNode 必须保留 returnTo"
);
assert(
  /switchNode[\s\S]{0,1200}returnActivity/.test(nodeView),
  "switchNode 必须保留 returnActivity"
);
assert(
  /switchNode[\s\S]{0,1400}returnMode/.test(nodeView),
  "switchNode 必须保留 returnMode"
);

// 单元页活动与题目来源
assert(
  /watch\(\s*\(\)\s*=>\s*route\.query\.activity/.test(unitView),
  "单元页必须监听 query.activity"
);
assert(
  /watch\(\s*\(\)\s*=>\s*\[route\.params\.unitCode,\s*route\.query\.mode\]/.test(
    unitView
  ),
  "单元页必须监听 mode/unitCode"
);
assert(unitView.includes("sourceUnitCode"), "单元页题目必须传 sourceUnitCode");
assert(
  unitView.includes("sourceActivityCode"),
  "单元页题目必须传 sourceActivityCode"
);
assert(unitView.includes("algorithmKey"), "演示活动必须跳转统一 algorithmKey");

// 题目流：纵向多卡、目录/节点/状态、分页、无题型难度分类
assert(
  questionView.includes('v-for="question in streamQuestions"'),
  "题目流必须纵向渲染多张卡"
);
assert(questionView.includes("CourseCatalogSidebar"), "题目页必须复用课程目录");
assert(questionView.includes("nodeCode"), "题目页必须使用稳定 nodeCode");
assert(questionView.includes("studyState"), "题目页必须保留个人状态筛选");
assert(questionView.includes("pageSize"), "题目页必须分批加载");
assert(
  questionView.includes("last") ||
    questionView.includes("Epoch") ||
    questionView.includes("epoch"),
  "题目页必须有请求竞态守卫"
);
assert(!questionView.includes("typeOptions"), "题目页不应保留题型分类");
assert(!questionView.includes("difficultyOptions"), "题目页不应保留难度分类");
assert(
  questionView.includes("refreshCatalog(false)"),
  "作答后不应重载题卡列表"
);

// API 契约
assert(
  questionApi.includes("courseCode?: string"),
  "题目查询 DTO 必须有 courseCode"
);
assert(
  questionApi.includes("sectionCode?: string"),
  "题目查询 DTO 必须有 sectionCode"
);
assert(
  questionApi.includes("unitCode?: string"),
  "题目查询 DTO 必须有 unitCode"
);
assert(
  questionApi.includes("nodeCode?: string"),
  "题目查询 DTO 必须有 nodeCode"
);
assert(
  questionApi.includes("LearningCatalogSectionVO"),
  "catalog 必须返回小节树类型"
);
assert(
  courseApi.includes("questionMappings"),
  "单元契约必须包含 questionMappings"
);
assert(
  courseService.includes('"questionMappings"'),
  "单元响应必须保留 questionMappings"
);
assert(
  questionService.includes("LearningQuestionScopeService"),
  "题目服务必须接入共享范围解析器"
);

// 模拟播放器：18 项目录、共享入口、离页停止
assert(
  simulationView.includes("AlgorithmDemoPlayer"),
  "模拟页必须复用共享播放器"
);
assert(
  simulationView.includes("algorithmKey"),
  "模拟页必须使用稳定 algorithmKey"
);
assert(!simulationView.includes("runCpuSimulation"), "旧 CPU 页面调用不得残留");
assert(!simulationView.includes("runPageSimulation"), "旧页面置换调用不得残留");
assert(player.includes("onBeforeUnmount"), "播放器必须有离页清理");
assert(
  player.includes('removeEventListener("message", onMessage)'),
  "离页必须移除 legacy 消息监听"
);
assert(
  player.includes("clearTimeout(pendingFlush)"),
  "离页必须清理挂起的 flush"
);
assert(player.includes("sourceRequest += 1"), "离页必须让旧请求失效");
assert(
  player.includes("target.postMessage"),
  "播放器必须通过消息桥接控制 legacy 演示"
);
const catalog = read("../src/simulation/catalog.ts");
const algorithmKeys = [...catalog.matchAll(/key:\s*"([a-z]+\.[a-z_]+)"/g)].map(
  (match) => match[1]
);
assert.equal(algorithmKeys.length, 18, "模拟目录必须登记 18 项");

console.log(
  "Learning integration contracts passed: navigation, query context, question stream, scoped API, simulation player."
);
