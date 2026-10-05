import ACCESS_ENUM from "@/access/accessEnum";

/**
 * 全站统一导航配置（Phase 1 §5.1/§5.2）。
 * 顶栏 = 一级工作领域；侧栏 = 当前领域内的栏目。
 * navKey 用于详情页保持顶栏选中态；旧路径通过 redirects 归一到规范路由。
 */
export interface NavItem {
  key: string;
  label: string;
  path: string;
  access?: string;
  children?: { key: string; label: string; path: string; access?: string }[];
}

export const APP_NAVIGATION: NavItem[] = [
  {
    key: "home",
    label: "首页",
    path: "/home",
    access: ACCESS_ENUM.USER,
  },
  {
    key: "graph",
    label: "知识图谱",
    path: "/graph",
    access: ACCESS_ENUM.USER,
  },
  {
    key: "assessments",
    label: "题目练习",
    path: "/assessments",
    access: ACCESS_ENUM.USER,
    children: [
      {
        key: "assessment-all",
        label: "全部题目",
        path: "/assessments",
        access: ACCESS_ENUM.USER,
      },
      {
        key: "assessment-review",
        label: "待巩固",
        path: "/assessments?studyState=NEEDS_REVIEW",
        access: ACCESS_ENUM.USER,
      },
    ],
  },
  {
    key: "experiments",
    label: "实验中心",
    path: "/experiments",
    access: ACCESS_ENUM.USER,
    children: [
      {
        key: "exp-code",
        label: "编程实验",
        path: "/experiments",
        access: ACCESS_ENUM.USER,
      },
      {
        key: "exp-simulation",
        label: "交互模拟",
        path: "/experiments/simulation",
        access: ACCESS_ENUM.USER,
      },
    ],
  },
  {
    key: "resources",
    label: "学习资源",
    path: "/resources",
    access: ACCESS_ENUM.USER,
  },
  {
    key: "teaching",
    label: "教学管理",
    path: "/teaching/overview",
    access: ACCESS_ENUM.ADMIN,
    children: [
      {
        key: "teaching-overview",
        label: "班级分析",
        path: "/teaching/overview",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        key: "teaching-knowledge",
        label: "知识维护",
        path: "/teaching/knowledge",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        key: "teaching-assessments",
        label: "测评管理",
        path: "/teaching/assessments",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        key: "teaching-experiments",
        label: "实验管理",
        path: "/teaching/experiments",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        key: "teaching-users",
        label: "用户管理",
        path: "/teaching/users",
        access: ACCESS_ENUM.ADMIN,
      },
    ],
  },
];

/** 非顶栏入口：由头像菜单进入，但页面仍可使用自己的上下文侧栏。 */
export const AUXILIARY_NAVIGATION: NavItem[] = [
  {
    key: "records",
    label: "学习记录",
    path: "/records",
    access: ACCESS_ENUM.USER,
    children: [
      {
        key: "records-timeline",
        label: "时间线",
        path: "/records",
        access: ACCESS_ENUM.USER,
      },
      {
        key: "records-review",
        label: "待巩固",
        path: "/records?view=review",
        access: ACCESS_ENUM.USER,
      },
    ],
  },
];

/** 学生默认首页 / 管理员默认首页 */
export const ROLE_HOME: Record<string, string> = {
  [ACCESS_ENUM.USER]: "/graph",
  [ACCESS_ENUM.ADMIN]: "/teaching/overview",
};

/** 由路径解析所属一级领域（详情页保持顶栏选中态） */
export function resolveNavKey(path: string): string | null {
  if (path.startsWith("/home")) return "home";
  if (path.startsWith("/assessments")) return "assessments";
  if (path.startsWith("/graph") || path.startsWith("/knowledge/node"))
    return "graph";
  if (path.startsWith("/experiments") || path.startsWith("/view/question"))
    return "experiments";
  if (path.startsWith("/resources")) return "resources";
  if (path.startsWith("/records")) return "records";
  if (path.startsWith("/teaching")) return "teaching";
  return null;
}

/** 旧路径 → 规范路径重定向表（保留 query） */
export const LEGACY_REDIRECTS: Record<string, string> = {
  "/learn": "/experiments",
  "/question": "/experiments",
  "/learning/questions": "/assessments",
  "/all_question": "/experiments",
  "/view/question": "/experiments/code",
  "/question_submit": "/records?eventType=JUDGE",
  "/learn/resources": "/resources",
  "/learning/manage": "/teaching/assessments",
  "/learning/teacher": "/teaching/overview",
  "/center": "/teaching/users",
  "/manage/question": "/teaching/experiments",
  "/add/question": "/teaching/experiments",
  "/knowledgeGraph": "/graph",
};
