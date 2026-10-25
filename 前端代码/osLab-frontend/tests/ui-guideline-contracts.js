// Run with: node tests/ui-guideline-contracts.js
// Web Interface Guidelines 修复契约：语言/标题、地标、对比度、确认、可访问名称、
// 表单标签、URL 状态、Intl 日期与离开提醒。
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8");
const readSrc = (relative) => read(path.join("src", relative));
const expectIncludes = (source, needle, message) =>
  assert.ok(source.includes(needle), `${message}: 缺少 ${needle}`);
const expectExcludes = (source, needle, message) =>
  assert.ok(!source.includes(needle), `${message}: 不应包含 ${needle}`);

// ---------- 文档与路由标题 ----------
const html = read("public/index.html");
expectIncludes(html, 'lang="zh-CN"', "html 必须声明中文语言");
expectExcludes(html, 'lang=""', "html lang 不能为空");
expectIncludes(html, 'name="theme-color"', "必须声明 theme-color");
expectIncludes(html, "viewport-fit=cover", "viewport 必须支持安全区");
expectIncludes(html, "<title>OSLab", "必须有稳定的站点标题");

const router = readSrc("router/index.ts");
expectIncludes(router, "afterEach", "路由必须同步页面标题");
expectIncludes(router, "document.title", "路由必须更新 document.title");

// ---------- 地标与跳过导航 ----------
const nestedMainViews = [
  "views/HomeView.vue",
  "views/knowledgeGraph/KnowledgeGraphView.vue",
  "views/learning/CourseHomeView.vue",
  "views/learning/CourseOutlineView.vue",
  "views/learning/CourseUnitView.vue",
  "views/learning/LearningQuestionsView.vue",
  "views/learning/ManageCourseContentView.vue",
  "views/learning/NodeLearningWorkspaceView.vue",
  "views/learning/SimulationView.vue",
  "views/user/StudentProfileView.vue",
];
for (const file of nestedMainViews) {
  expectExcludes(readSrc(file), "<main", `${file} 不能嵌套 main 地标`);
}
for (const file of [
  "layouts/LearningLayout.vue",
  "layouts/TeachingLayout.vue",
  "layouts/UserLayout.vue",
]) {
  const layout = readSrc(file);
  expectIncludes(layout, "skip-link", `${file} 必须有跳过导航链接`);
  expectIncludes(layout, 'id="main-content"', `${file} 必须标记主内容区`);
}

// ---------- 状态标签对比度（WCAG AA） ----------
const css = readSrc("styles/learning-theme.css");
const tokens = new Map();
for (const match of css.matchAll(/--([a-z0-9-]+)\s*:\s*([^;]+);/gi)) {
  if (!tokens.has(match[1])) tokens.set(match[1], match[2].trim());
}
const channel = (value) => {
  const ratio = parseInt(value, 16) / 255;
  return ratio <= 0.03928
    ? ratio / 12.92
    : Math.pow((ratio + 0.055) / 1.055, 2.4);
};
const luminance = (hex) => {
  const value = hex.replace("#", "");
  return (
    0.2126 * channel(value.slice(0, 2)) +
    0.7152 * channel(value.slice(2, 4)) +
    0.0722 * channel(value.slice(4, 6))
  );
};
const contrast = (foreground, background) => {
  const lighter = Math.max(luminance(foreground), luminance(background));
  const darker = Math.min(luminance(foreground), luminance(background));
  return (lighter + 0.05) / (darker + 0.05);
};
for (const status of ["mastered", "review", "weak", "unseen"]) {
  const textToken = tokens.get(`oslab-${status}-text`);
  const softToken = tokens.get(`oslab-${status}-soft`);
  assert.ok(textToken, `必须定义 --oslab-${status}-text 状态文字色`);
  assert.ok(softToken, `必须定义 --oslab-${status}-soft 背景色`);
  assert.ok(
    contrast(textToken, softToken) >= 4.5,
    `--oslab-${status}-text 对 --oslab-${status}-soft 的对比度不足 4.5:1`
  );
  expectIncludes(
    css,
    `color: var(--oslab-${status}-text)`,
    `.oslab-status-tag--${status} 必须使用高对比文字色`
  );
}

// ---------- 破坏性操作 ----------
const userCenter = readSrc("views/user/UserCenterView.vue");
expectIncludes(userCenter, "a-popconfirm", "删除学生必须二次确认");
expectIncludes(userCenter, "showStudentPassword", "学生密码必须默认遮蔽");
expectIncludes(userCenter, "student-upload", "上传文件必须有关联标签");
expectIncludes(userCenter, 'role="status"', "上传结果必须可被读屏播报");
expectExcludes(userCenter, "dayjs(", "用户中心必须使用 Intl 日期");

const manageQuestion = readSrc("views/question/ManageQuestionView.vue");
expectIncludes(manageQuestion, "a-popconfirm", "删除实验必须二次确认");
expectExcludes(manageQuestion, "moment(", "实验管理必须使用 Intl 日期");

// ---------- AI 面板 ----------
const fab = readSrc("components/assistant/FloatingAiAssistant.vue");
for (const needle of [
  'aria-expanded="open"',
  'aria-controls="ai-assistant-panel"',
  "@keydown.space",
  "ai-safe-probe",
  "env(safe-area-inset-bottom)",
  "nudgePanel",
]) {
  expectIncludes(fab, needle, "AI 浮动助手可访问性");
}

const panel = readSrc("components/assistant/AiChatPanel.vue");
for (const needle of [
  'aria-label="关闭 AI 助手"',
  'role="log"',
  'aria-live="polite"',
  'aria-label="向 AI 助手提问"',
  "inputRef",
  "headerMove",
  'tabindex="0"',
]) {
  expectIncludes(panel, needle, "AI 对话面板可访问性");
}

// ---------- 图谱键盘可达 ----------
const graphView = readSrc("views/knowledgeGraph/KnowledgeGraphView.vue");
for (const needle of [
  'aria-label="跳转到节点"',
  "onJumpNode",
  ':aria-pressed="pathFocus"',
  'aria-label="Cypher 语句"',
]) {
  expectIncludes(graphView, needle, "知识图谱键盘可达性");
}
assert.match(
  graphView,
  /<button[^>]*class="path-item"/,
  "补救路径项必须使用 button"
);

// ---------- Tab 键盘导航 ----------
const authDialog = readSrc("components/auth/AuthDialog.vue");
for (const needle of [
  "@keydown.right.prevent",
  "@keydown.left.prevent",
  ':tabindex="!isRegister ? 0 : -1"',
  "focusMode",
]) {
  expectIncludes(authDialog, needle, "登录/注册页签键盘导航");
}
const landingView = readSrc("views/landing/LandingView.vue");
expectIncludes(landingView, 'width="31"', "登录页 logo 必须有宽高");

const nodeView = readSrc("views/learning/NodeLearningWorkspaceView.vue");
for (const needle of [
  "aria-controls",
  "aria-labelledby",
  "onTabKeydown",
  ':tabindex="activeTab === tab.key ? 0 : -1"',
  'aria-label="向节点导师提问"',
]) {
  expectIncludes(nodeView, needle, "节点工作台页签与输入可访问性");
}

// ---------- 表单可访问名称 ----------
const resources = readSrc("views/ResourcesView.vue");
for (const needle of [
  'aria-label="搜索学习资源"',
  'aria-label="按章节筛选"',
  'aria-label="按节点筛选"',
  'aria-label="按类型筛选"',
]) {
  expectIncludes(resources, needle, "资源筛选控件必须有名");
}

const myTasks = readSrc("views/tasks/MyTasksView.vue");
expectIncludes(myTasks, 'aria-label="搜索任务标题"', "任务搜索必须有名");
expectIncludes(myTasks, "router.replace", "任务筛选必须同步到 URL");
expectIncludes(myTasks, "route.query.keyword", "任务搜索必须从 URL 恢复");
expectIncludes(myTasks, "route.query.current", "任务分页必须从 URL 恢复");
expectIncludes(myTasks, "formatDateTime", "任务时间必须使用 Intl 工具");
expectIncludes(myTasks, "<router-link", "任务标题必须是链接");

const manageCourse = readSrc("views/learning/ManageCourseContentView.vue");
for (const needle of [
  'aria-label="选择课程"',
  'aria-label="课程代码"',
  'aria-label="单元代码"',
  'aria-label="审核说明"',
  'aria-label="旧节点代码"',
  'aria-label="单元草稿 JSON"',
]) {
  expectIncludes(manageCourse, needle, "课程管理控件必须有名");
}

const learningQuestions = readSrc("views/learning/LearningQuestionsView.vue");
expectIncludes(
  learningQuestions,
  'aria-label="搜索题目标题"',
  "题目搜索必须有名"
);

// ---------- URL 状态 ----------
const questions = readSrc("views/question/QuestionsView.vue");
for (const needle of [
  "route.query.title",
  "route.query.tags",
  "route.query.current",
  "router.replace",
  "formatDate",
]) {
  expectIncludes(questions, needle, "实验筛选必须同步到 URL");
}

// ---------- Intl 日期工具 ----------
const dateTimeFile = path.join(root, "src/utils/dateTime.ts");
assert.ok(fs.existsSync(dateTimeFile), "必须提供 dateTime 工具");
const dateTime = fs.readFileSync(dateTimeFile, "utf8");
expectIncludes(dateTime, "Intl.DateTimeFormat", "日期工具必须基于 Intl");

// ---------- 未保存离开提醒 ----------
const editor = readSrc("views/tasks/TeachingTaskEditorView.vue");
expectIncludes(editor, "onBeforeRouteLeave", "任务编辑器必须拦截路由离开");
expectIncludes(editor, "beforeunload", "任务编辑器必须拦截页面关闭");

// ---------- 导航语义 ----------
const home = readSrc("views/HomeView.vue");
expectIncludes(home, "<router-link", "首页入口必须使用链接");
expectExcludes(home, '@click="go(', "首页入口不能使用按钮跳转");

const records = readSrc("views/learning/LearningRecordsView.vue");
assert.match(
  records,
  /<button[^>]*class="replay-grid replay-row"/,
  "回放行必须是 button"
);

// ---------- 其他 ----------
const avatar = readSrc("components/UserAvatar.vue");
expectIncludes(avatar, ':width="size"', "头像必须声明 width");
expectIncludes(avatar, ':height="size"', "头像必须声明 height");

const player = readSrc("components/learning/AlgorithmDemoPlayer.vue");
expectIncludes(player, 'aria-label="降低播放速度"', "减速按钮必须有名");
expectIncludes(player, 'aria-label="提高播放速度"', "加速按钮必须有名");

const login = readSrc("views/user/UserLoginView.vue");
expectIncludes(login, "os-field-error", "登录错误必须贴近字段");
expectIncludes(login, 'spellcheck="false"', "账号输入必须关闭拼写检查");
expectIncludes(login, "正在登录…", "登录加载文案必须使用省略号");
const register = readSrc("views/user/UserRegisterView.vue");
expectIncludes(register, "正在创建…", "注册加载文案必须使用省略号");
expectIncludes(register, "os-field-error", "注册错误必须贴近字段");

console.log("UI guideline contracts passed");
