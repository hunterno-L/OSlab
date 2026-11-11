# 第三阶段基线（Phase 0，2026-10-01）

依据《OSLab第三阶段执行计划-学习流程与UI重构.md》§4。所有 U 项已与当前源码逐项核对。

## 1. U1-U16 核对结果

| 编号 | 核对证据 | 结论 |
| --- | --- | --- |
| U1 | `routes.ts` L56/L115 两次注册 `/all_question`（"实验项目"可见 + "浏览全部题目"隐藏）；GlobalHeader 展开可见路由为一级菜单 | 确认，需统一 |
| U2 | `KnowledgeGraphView.vue` L1082 `courseNav` computed 自建课程导航 | 确认，需移入共享壳 |
| U3 | 图谱侧栏"本章已掌握"= 章节内非 GRAY 节点 pKnown 均值 | 确认，需拆 覆盖率/已掌握比例/已测均值 |
| U4 | `LearningQuestionsView.vue` L97 硬编码四章节数组；直达题+分页题卡同屏；query 仅 mounted 读取 | 确认 |
| U5 | `NodeLearningWorkspaceView.vue` 同页全模块堆叠（诊断/路径/题卡/时间线/导师） | 确认，需页签化 |
| U6 | `ViewQuestionView.vue` L185 提交后跳 `/question_submit` | 确认，需留页判题 |
| U7 | `TeacherDashboardView.vue` drillQuestions 用标题 contains 过滤 | 确认，需映射表精确关联 |
| U8 | 后端无 `/learning/mastery/timeline`（仅教师 timeline） | 确认，需学生专用时间线 |
| U9 | RecommendationVO 仅 nodeName/type/reason/score；personalGraph 前置 Map 单值 | 确认，需路径服务 |
| U10 | 归因 confidence 按类型固定值；lastFailure 持续展示 | 确认 |
| U11 | 学生与管理共用 LearningQuestionVO（含 correctAnswer 字段，学生视图运行时 null） | 确认，需 VO 分离 |
| U12 | 验收脚本依赖回显沙箱 stub | 确认（scripts/dev/sandbox_stub.py） |
| U13 | 工作台无条件给出 FIFO/LRU 入口；实验关联用 tags LIKE | 确认 |
| U14 | 教师 timeline 无班级约束；实验统计按 JUDGE 事件行计 | 确认 |
| U15 | `LearningEventServiceImpl` L96 `isFirst && insertWithMasteryUpdate` | 确认，重复作答跳过事件写入，需分离"记录"与"观测" |
| U16 | 导师来源先装满节点与题目 | 确认（来源配额未按任务排序） |

## 2. 复用清单（不重做）

- 图谱快照 `getGraphSnapshot()`：全量节点/去重边/chapter/统计（第二阶段 Phase 1 交付）
- `nodeWorkbench` 聚合、`recommendations`、学习事件幂等写入、四题型判分、BKT、规则归因、教师聚合
- 前端：`learning-theme.css`、QuestionAnswerCard 四题型、nodeWorkbench 加载/导师请求模式、node 工作台路由
- 认证页（auth-entry-2026-09-30）保留

## 3. 当前接口清单

见计划 §3.3 表格；全部已存在并验证。拟新增接口：`/learning/course/chapters`、`/learning/mastery/path`、`/learning/mastery/timeline`、`/learning/mastery/snapshot`、`/learning/assessment/*`、`/learning/questions/manage/*`、`/learning/teacher/classes`、`/learning/teacher/node/detail`。

## 4. 数据量快照

Neo4j：150 节点 / 171 关系（含本阶段新增的 页面置换/FIFO/LRU 页面置换算法/进程状态/CPU 调度 与 缺页中断→页表、进程状态→进程 边）。
MySQL：learning_* 六张 P0 表 + 种子题（含 LRU 判断题 910011 等）；学习事件由冒烟/验收脚本产生。

## 5. 测试基线

后端全量 74 tests：0 Failures、6 Errors 全部为遗留模板测试（Post* 缺 `post` 表、CosManagerTest 缺凭据），与 learning 模块无关——不删测试、不改环境伪造通过。learning 专项 55+9 全绿。
前端：build DONE、lint 0 errors。

## 6. 契约冻结（Phase 0 输出）

- 节点键：`nodeName`（Neo4j EntityNode.name）；章节：`chapter`（缺省"未分类"）
- 测评会话：`assessmentRunId`（新增表）；提交幂等：`submissionKey`
- 事件组：`attempt:<attemptId>` / `judge:<questionSubmitId>` / `sim:<预测键>`
- 导航：学生四入口（课程学习/知识图谱/实验中心/学习记录）+ 管理员"教学管理"；具体路径表见计划 §5.1
- 术语表：知识测评/编程实验/交互模拟/知识学习/学习记录/教学管理（历史 evidence.source 原值保留，显示层映射）
