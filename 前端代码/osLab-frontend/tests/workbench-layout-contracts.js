// Run with: node tests/workbench-layout-contracts.js
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const read = (file) => fs.readFileSync(path.join(root, "src", file), "utf8");

const graph = read("views/knowledgeGraph/KnowledgeGraphView.vue");
assert.ok(graph.includes("graph-annotation-chip"), "图谱画布必须有 ③ 注记条");
assert.ok(
  graph.includes("NODE / INSPECTOR"),
  "图谱详情必须使用 NODE / INSPECTOR 眉题"
);
assert.ok(graph.includes("detail-definition"), "节点定义必须使用高亮定义块");
assert.ok(graph.includes('edge-tone="muted"'), "课程学习图谱必须启用灰度边");
assert.ok(
  graph.includes("selectedAnnotationNumber"),
  "图谱注记编号必须来自真实批注映射"
);
assert.ok(
  graph.includes("VISIBLE_ANNOTATION_LIMIT"),
  "图谱必须声明小图全量编号上限"
);
assert.ok(
  graph.includes("buildVisibleAnnotations"),
  "图谱必须用纯函数生成小图全量编号"
);
assert.ok(
  graph.includes("rgba(32, 36, 43, 0.12)"),
  "图谱画布必须有可见的点阵纸背景"
);
assert.ok(
  graph.includes('style="width: 260px"') &&
    graph.includes('style="width: 126px"'),
  "搜索与状态筛选必须保持设计稿的固定宽度"
);

const unit = read("views/learning/CourseUnitView.vue");
assert.ok(unit.includes("activity-strip"), "单元页必须有活动卡片带");
assert.ok(unit.includes("unit-ledger"), "单元页必须有右侧学习地图");
assert.ok(unit.includes("activityGroups"), "单元页必须按活动类型汇总");
assert.ok(unit.includes("oslab-highlight-text"), "单元页标题必须使用荧光笔");

const node = read("views/learning/NodeLearningWorkspaceView.vue");
assert.ok(node.includes("node-hero"), "节点页必须有 hero 卡");
assert.ok(node.includes("figure-source"), "节点页教材来源必须用等宽行");
assert.ok(node.includes("tutor-focus-note"), "导师卡必须有 ③ 焦点批注");
assert.ok(node.includes("AnnotationNote"), "节点页必须使用批注组件");

const lab = read("views/question/ViewQuestionView.vue");
assert.ok(lab.includes("oslab-lab-b"), "实验页必须挂 B 主题作用域");
assert.ok(lab.includes("lab-fig"), "实验页必须有 FIG 标签");
assert.ok(lab.includes("lab-spec"), "实验页必须有等宽规格行");
assert.ok(lab.includes("lab-margin-note"), "实验说明必须有一条页边注记");

console.log("Workbench layout contracts passed: graph, unit, node, lab.");
