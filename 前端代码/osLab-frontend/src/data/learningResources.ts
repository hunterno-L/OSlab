/**
 * 静态学习资料目录。
 *
 * 资源的节点关联使用知识图谱中的业务名称，新增资料时只需追加一项，
 * 页面会自动提供章节、节点筛选和节点跳转。
 */
export type LearningResourceType = "HTML" | "DOCUMENT";

export interface LearningResource {
  id: string;
  title: string;
  type: LearningResourceType;
  url: string;
  description: string;
  chapter: string;
  nodeNames: string[];
}

const learningResources: LearningResource[] = [
  {
    id: "process-scheduling",
    title: "进程调度算法",
    type: "HTML",
    url: "/resources/process-scheduling.html",
    description: "通过配置进程、逐步执行和甘特图，观察调度算法的决策过程。",
    chapter: "进程管理",
    nodeNames: ["进程调度", "CPU 调度", "时间片轮转算法"],
  },
  {
    id: "syscall-concepts",
    title: "系统调用概念详解",
    type: "HTML",
    url: "/resources/syscall-concepts.html",
    description: "从用户态陷入、参数传递到内核分发，梳理系统调用的完整路径。",
    chapter: "中断与系统调用",
    nodeNames: ["系统调用", "中断", "用户态", "内核态"],
  },
];

/** 返回当前已发布的资料目录。 */
export function getLearningResources(): LearningResource[] {
  return learningResources;
}

/** 按知识节点精确匹配资源，避免把相似节点名混在一起。 */
export function getResourcesForNode(name: string): LearningResource[] {
  const nodeName = name.trim();
  if (!nodeName) return [];
  return learningResources.filter((resource) =>
    resource.nodeNames.includes(nodeName)
  );
}
