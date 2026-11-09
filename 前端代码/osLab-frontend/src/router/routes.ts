import { RouteRecordRaw } from "vue-router";
import store from "@/store";
import UserLayout from "@/layouts/UserLayout.vue";
import ACCESS_ENUM from "@/access/accessEnum";
import { ROLE_HOME } from "@/navigation/appNavigation";
import LearningLayout from "@/layouts/LearningLayout.vue";
import TeachingLayout from "@/layouts/TeachingLayout.vue";

const UserLoginView = () => import("@/views/user/UserLoginView.vue");
const UserRegisterView = () => import("@/views/user/UserRegisterView.vue");
const NoAuthView = () => import("@/views/NoAuthView.vue");
const ManageQuestionView = () =>
  import("@/views/question/ManageQuestionView.vue");
const AddQuestionView = () => import("@/views/question/AddQuestionView.vue");
const QuestionsView = () => import("@/views/question/QuestionsView.vue");
const ViewQuestionView = () => import("@/views/question/ViewQuestionView.vue");
const LearningRecordsView = () =>
  import("@/views/learning/LearningRecordsView.vue");
const UserCenterView = () => import("@/views/user/UserCenterView.vue");
const KnowledgeGraphView = () =>
  import("@/views/knowledgeGraph/KnowledgeGraphView.vue");
const LearningQuestionsView = () =>
  import("@/views/learning/LearningQuestionsView.vue");
const ManageLearningQuestionsView = () =>
  import("@/views/learning/ManageLearningQuestionsView.vue");
const SimulationView = () => import("@/views/learning/SimulationView.vue");
const TeacherDashboardView = () =>
  import("@/views/learning/TeacherDashboardView.vue");
const ResourcesView = () => import("@/views/ResourcesView.vue");
const NodeLearningWorkspaceView = () =>
  import("@/views/learning/NodeLearningWorkspaceView.vue");
const AssessmentRunView = () =>
  import("@/views/learning/AssessmentRunView.vue");
const HomeView = () => import("@/views/HomeView.vue");
const StudentProfileView = () => import("@/views/user/StudentProfileView.vue");
const MyTasksView = () => import("@/views/tasks/MyTasksView.vue");
const TaskDetailView = () => import("@/views/tasks/TaskDetailView.vue");
const TeachingTasksView = () => import("@/views/tasks/TeachingTasksView.vue");
const TeachingTaskEditorView = () =>
  import("@/views/tasks/TeachingTaskEditorView.vue");
const TeachingTaskReviewView = () =>
  import("@/views/tasks/TeachingTaskReviewView.vue");
const AiWorkspaceView = () => import("@/views/ai/AiWorkspaceView.vue");
const AiSettingsView = () => import("@/views/ai/AiSettingsView.vue");
const KernelLabView = () => import("@/views/kernel/KernelLabView.vue");

/**
 * Phase 1 路由重构：嵌套布局壳 + 规范路径 + 旧路径兼容重定向（保留 query/params）。
 * navKey 用于顶栏选中态；学习与教学分别使用独立布局壳。
 */
export const routes: Array<RouteRecordRaw> = [
  {
    path: "/welcome",
    name: "平台介绍",
    component: UserLayout,
    meta: { hideInMenu: true },
  },
  {
    path: "/user",
    name: "用户",
    component: UserLayout,
    children: [
      { path: "/user/login", name: "用户登录", component: UserLoginView },
      { path: "/user/register", name: "用户注册", component: UserRegisterView },
    ],
    meta: { hideInMenu: true },
  },
  {
    path: "/",
    name: "学习工作区",
    component: LearningLayout,
    meta: { hideInMenu: true },
    children: [
      {
        path: "",
        name: "角色首页",
        redirect: () => {
          const role = store.state.user.loginUser?.userRole;
          return ROLE_HOME[role] || "/welcome";
        },
        meta: { hideInMenu: true },
      },
      // 旧课程首页/目录保留兼容跳转，统一回到课程图谱并携带课程上下文
      {
        path: "/courses",
        redirect: (to) => ({ path: "/graph", query: to.query }),
        meta: { hideInMenu: true },
      },
      {
        path: "/courses/:courseCode",
        redirect: (to) => ({
          path: "/graph",
          query: { ...to.query, courseCode: to.params.courseCode },
        }),
        meta: { hideInMenu: true },
      },
      {
        path: "/courses/:courseCode/units/:unitCode",
        name: "课程教学条目",
        component: () => import("@/views/learning/CourseUnitView.vue"),
        meta: {
          access: ACCESS_ENUM.USER,
          navKey: "graph",
          hideSidebar: true,
          contentWidth: "wide",
        },
      },
      {
        path: "/home",
        name: "首页",
        component: HomeView,
        meta: { access: ACCESS_ENUM.USER, navKey: "home" },
      },
      {
        path: "/assessments",
        name: "知识测评",
        component: LearningQuestionsView,
        meta: {
          access: ACCESS_ENUM.USER,
          navKey: "assessments",
          pageTitle: "题目练习",
          // 题目练习不渲染全局侧栏，页面内使用课程目录 + 纵向题卡流
          hideSidebar: true,
          contentWidth: "wide",
        },
      },
      {
        path: "/assessments/run/:runId",
        name: "单题测评",
        component: AssessmentRunView,
        meta: {
          access: ACCESS_ENUM.USER,
          navKey: "assessments",
          hideInMenu: true,
          hideSidebar: true,
        },
      },
      {
        path: "/graph",
        name: "课程学习",
        component: KnowledgeGraphView,
        meta: {
          access: ACCESS_ENUM.USER,
          navKey: "graph",
          pageTitle: "课程学习",
          // log3 收尾 A：图谱画布是页面主工作区，使用 wide 内容宽度（1760px）
          contentWidth: "wide",
        },
      },
      {
        path: "/knowledge/node/:nodeName",
        name: "节点学习工作台",
        component: NodeLearningWorkspaceView,
        meta: { access: ACCESS_ENUM.USER, navKey: "graph", hideInMenu: true },
      },
      {
        path: "/knowledge/concept/:nodeCode",
        name: "知识概念学习工作台",
        component: NodeLearningWorkspaceView,
        meta: { access: ACCESS_ENUM.USER, navKey: "graph", hideInMenu: true },
      },
      {
        path: "/experiments",
        name: "编程实验",
        component: QuestionsView,
        meta: {
          access: ACCESS_ENUM.USER,
          navKey: "experiments",
          pageTitle: "编程实验",
          hideSidebar: true,
          // 编程实验内容使用宽工作区布局
          //（阶段 3 再做页内重排）
          contentWidth: "wide",
        },
      },
      {
        path: "/experiments/code/:id",
        name: "编程实验工作区",
        component: ViewQuestionView,
        props: true,
        meta: {
          access: ACCESS_ENUM.USER,
          navKey: "experiments",
          hideInMenu: true,
          // log3 收尾 A：实验工作区同样放宽（阶段 3 重排，宽度机制先行）
          contentWidth: "wide",
        },
      },
      {
        path: "/experiments/simulation",
        name: "交互模拟",
        component: SimulationView,
        meta: {
          access: ACCESS_ENUM.USER,
          navKey: "algorithm-demo",
          contentWidth: "wide",
          hideSidebar: true,
          pageTitle: "算法演示",
        },
      },
      {
        path: "/records",
        name: "学习记录",
        component: LearningRecordsView,
        meta: { access: ACCESS_ENUM.USER, navKey: "records" },
      },
      {
        path: "/resources",
        name: "学习资源",
        component: ResourcesView,
        meta: { access: ACCESS_ENUM.USER, navKey: "resources" },
      },
      {
        path: "/tasks",
        name: "我的学习任务",
        component: MyTasksView,
        meta: {
          access: ACCESS_ENUM.USER,
          navKey: "tasks",
          pageTitle: "学习任务",
          hideSidebar: true,
          contentWidth: "wide",
        },
      },
      {
        path: "/tasks/:taskCode",
        name: "学习任务详情",
        component: TaskDetailView,
        meta: {
          access: ACCESS_ENUM.USER,
          navKey: "tasks",
          hideInMenu: true,
          hideSidebar: true,
          contentWidth: "wide",
        },
      },
      {
        path: "/account",
        name: "学生信息",
        component: StudentProfileView,
        meta: { access: ACCESS_ENUM.USER, hideInMenu: true },
      },
      {
        path: "/classes",
        name: "我的班级",
        component: () => import("@/views/classes/MyClassesView.vue"),
        meta: {
          access: ACCESS_ENUM.USER,
          hideInMenu: true,
          hideSidebar: true,
          pageTitle: "我的班级",
          contentWidth: "wide",
        },
      },
    ],
  },
  {
    path: "/teaching",
    name: "教学管理工作区",
    component: TeachingLayout,
    meta: { hideInMenu: true },
    children: [
      {
        path: "/teaching/content",
        name: "课程内容管理",
        component: () => import("@/views/learning/ManageCourseContentView.vue"),
        meta: { access: ACCESS_ENUM.ADMIN, navKey: "teaching" },
      },
      {
        path: "/teaching/overview",
        name: "班级分析",
        component: TeacherDashboardView,
        meta: { access: ACCESS_ENUM.ADMIN, navKey: "teaching" },
      },
      {
        path: "/teaching/knowledge",
        name: "知识维护",
        component: KnowledgeGraphView,
        meta: { access: ACCESS_ENUM.ADMIN, navKey: "teaching" },
      },
      {
        path: "/teaching/tasks",
        name: "教师学习任务",
        component: TeachingTasksView,
        meta: { access: ACCESS_ENUM.ADMIN, navKey: "tasks" },
      },
      {
        path: "/teaching/classes",
        name: "班级管理",
        component: () => import("@/views/classes/TeachingClassesView.vue"),
        meta: {
          access: ACCESS_ENUM.ADMIN,
          navKey: "teaching",
          pageTitle: "班级管理",
          hideSidebar: true,
          contentWidth: "wide",
        },
      },
      {
        path: "/teaching/tasks/new",
        name: "新建学习任务",
        component: TeachingTaskEditorView,
        meta: { access: ACCESS_ENUM.ADMIN, navKey: "tasks", hideInMenu: true },
      },
      {
        path: "/teaching/tasks/:taskCode/edit",
        name: "编辑学习任务",
        component: TeachingTaskEditorView,
        meta: { access: ACCESS_ENUM.ADMIN, navKey: "tasks", hideInMenu: true },
      },
      {
        path: "/teaching/tasks/:taskCode/preview",
        name: "预览学习任务",
        component: TeachingTaskEditorView,
        meta: { access: ACCESS_ENUM.ADMIN, navKey: "tasks", hideInMenu: true },
      },
      {
        path: "/teaching/tasks/:taskCode/review",
        name: "批阅学习任务",
        component: TeachingTaskReviewView,
        meta: { access: ACCESS_ENUM.ADMIN, navKey: "tasks", hideInMenu: true },
      },
      {
        path: "/teaching/assessments",
        name: "测评管理",
        component: ManageLearningQuestionsView,
        meta: { access: ACCESS_ENUM.ADMIN, navKey: "teaching" },
      },
      {
        path: "/teaching/experiments",
        name: "实验管理",
        component: ManageQuestionView,
        meta: { access: ACCESS_ENUM.ADMIN, navKey: "teaching" },
      },
      // 实验编辑器（AddQuestionView 依赖 path 含 "update" 区分新增/更新，
      // 两个路径复用同一组件；查询 id 存在时为更新模式）
      {
        path: "/teaching/experiments/add",
        name: "新建实验",
        component: AddQuestionView,
        meta: {
          access: ACCESS_ENUM.ADMIN,
          navKey: "teaching",
          hideInMenu: true,
        },
      },
      {
        path: "/teaching/experiments/update",
        name: "修改实验",
        component: AddQuestionView,
        meta: {
          access: ACCESS_ENUM.ADMIN,
          navKey: "teaching",
          hideInMenu: true,
        },
      },
      {
        path: "/teaching/users",
        name: "用户管理",
        component: UserCenterView,
        meta: { access: ACCESS_ENUM.ADMIN, navKey: "teaching" },
      },
    ],
  },
  // ---- 旧路径兼容重定向（保留 query；/view 保留 params）----
  {
    path: "/learn",
    redirect: (to) => ({ path: "/experiments", query: to.query }),
    meta: { hideInMenu: true },
  },
  {
    path: "/question",
    redirect: (to) => ({ path: "/experiments", query: to.query }),
    meta: { hideInMenu: true },
  },
  {
    path: "/learning/questions",
    redirect: (to) => ({ path: "/assessments", query: to.query }),
    meta: { hideInMenu: true },
  },
  {
    path: "/learning/simulation",
    redirect: (to) => ({ path: "/experiments/simulation", query: to.query }),
    meta: { hideInMenu: true },
  },
  {
    path: "/all_question",
    redirect: (to) => ({ path: "/experiments", query: to.query }),
    meta: { hideInMenu: true },
  },
  {
    path: "/view/question/:id",
    redirect: (to) => ({
      path: `/experiments/code/${to.params.id}`,
      query: to.query,
      hash: to.hash,
    }),
    meta: { hideInMenu: true },
  },
  {
    path: "/question_submit",
    redirect: (to) => ({
      path: "/records",
      query: { ...to.query, eventType: "JUDGE" },
    }),
    meta: { hideInMenu: true },
  },
  {
    path: "/learn/resources",
    redirect: (to) => ({ path: "/resources", query: to.query }),
    meta: { hideInMenu: true },
  },
  {
    path: "/profile",
    redirect: (to) => ({ path: "/account", query: to.query }),
    meta: { hideInMenu: true },
  },
  {
    path: "/learning/manage",
    redirect: () => ({ path: "/teaching/assessments" }),
    meta: { hideInMenu: true },
  },
  {
    path: "/learning/teacher",
    redirect: () => ({ path: "/teaching/overview" }),
    meta: { hideInMenu: true },
  },
  {
    path: "/center",
    redirect: () => ({ path: "/teaching/users" }),
    meta: { hideInMenu: true },
  },
  {
    path: "/manage/question",
    redirect: () => ({ path: "/teaching/experiments" }),
    meta: { hideInMenu: true },
  },
  {
    path: "/add/question",
    redirect: (to) => ({
      path: "/teaching/experiments/add",
      query: to.query,
    }),
    meta: { hideInMenu: true },
  },
  {
    path: "/update/question",
    redirect: (to) => ({
      path: "/teaching/experiments/update",
      query: to.query,
    }),
    meta: { hideInMenu: true },
  },
  {
    path: "/learning/tasks",
    redirect: (to) => ({ path: "/tasks", query: to.query }),
    meta: { hideInMenu: true },
  },
  {
    path: "/knowledgeGraph",
    redirect: () => ({ path: "/graph" }),
    meta: { hideInMenu: true },
  },
  {
    path: "/knowledge-graph",
    redirect: () => ({ path: "/graph" }),
    meta: { hideInMenu: true },
  },
  {
    path: "/ai",
    name: "AI 学习助手",
    component: AiWorkspaceView,
    meta: { access: ACCESS_ENUM.USER },
  },
  {
    path: "/ai/settings",
    name: "AI 模型设置",
    component: AiSettingsView,
    meta: { access: ACCESS_ENUM.USER },
  },
  {
    path: "/kernel",
    name: "内核实验",
    component: KernelLabView,
    meta: { access: ACCESS_ENUM.USER },
  },
  {
    path: "/noAuth",
    name: "无权限",
    component: NoAuthView,
    meta: { hideInMenu: true },
  },
];
