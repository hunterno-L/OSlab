# OSLab log3 收尾基线审计（阶段 0，2026-10-04）

依据 `docs/log3-收尾执行计划.md` §4。审计方式：log2 交付后的源码逐点核对 + 命令行验证（未起服务）。log3.txt 原始需求位于 `C:\Users\one\Desktop\log3.txt`（6 条）。

## 1. 基线命令与工作区

- `git rev-parse HEAD` = 84dfdea（与 log2 交付时一致）；工作区 130+ 项未提交改动 = 前三阶段 + log2 交付，全部保留。
- log2 验证基线（2026-10-04）：前端 lint 0 errors/55 warnings、build DONE；后端全量 152/0/6（6 个遗留模板错误）；verify-learning.ps1 ALL PASSED；catalog 接口 HTTP 验证通过。
- 服务此刻未运行（上轮联调后已停止）；阶段 8 联调时按 stop-what-I-start 纪律临时起停。

## 2. 问题 → 源码 → 分派矩阵

| # | 问题（log3.txt/计划 §0） | 源码证据 | 状态 | 分派 |
| --- | --- | --- | --- | --- |
| 1 | 图谱/实验中心两侧留白大、中间拥挤 | `LearningLayout.vue`：content max-width 1440px 对 wide 页面过窄 | 未实现 | A（阶段1） |
| 2 | 图谱节点虚化看不清 | `KnowledgeGraph.vue:514` `emphasis:{focus:"adjacency"}` 使 ECharts 将非邻接元素整体 blur | 未实现 | A |
| 3 | 知识点详情"实践入口/进入实验/做题"与工作台重复 | `KnowledgeGraphView.vue` 详情区（log2 阶段2 引入的次按钮与 practiceEntries） | 未实现 | A |
| 4 | 工作台缺"知识详解"页签（复用 2 份 HTML） | `NodeLearningWorkspaceView.vue` 仅 3 页签；`learningResources.ts` 有 2 份资源；工作台把资源并进"阅读材料"节 | 未实现 | B（阶段2） |
| 5 | 实验页"关联学习/题目练习"重复；"实验导师"应改名"向导师提问"并排第三页签 | `ViewQuestionView.vue`：关联学习行存在；实验导师为右侧面板且走 `/api/chat/ask`（:686） | 部分（面板已有，需迁移+改名+换 API） | B（阶段3） |
| 6 | 全局可拖动 AI 助手缺失 | 无组件（`ChatWindow.vue` 为死代码仅参考）；`ChatControllerService.askQuestionUsingPost` 可复用 | 未实现 | C（阶段4） |
| 7a | "未归类"章节三套语义 | `LearningQuestionServiceImpl` 章节过滤/目录/候选三处；前端 `LearningQuestionsView.vue` 有哨兵空态+startRun 守卫补丁（log2 终审 I-1 修复） | 未实现（后端） | D（阶段5.1） |
| 7b | 测评候选池固定前 50 条 | `LearningAssessmentRunController.java:73` `query.setPageSize(50)` | 未实现 | D（阶段5.2） |
| 7c | 编译错误影响掌握度 | `JudgeServiceImpl`/`LearningEventServiceImpl`：CE 走 FAIL 观测（需实测确认快照变化路径） | 需实测+修复 | D（阶段5.3） |
| 7d | Tutor 证据被邻居/题目挤掉 | `LearningTutorServiceImpl` MAX_SOURCES=8 无分组配额 | 未实现 | D（阶段5.4） |
| 7e | 回放统计文案单位不符 | `LearningRecordsView.vue` 回放统计 vs `LearningMasteryServiceImpl` snapshot 定义 | 需实测 | D（阶段5.5） |
| 8 | 时间线"图谱快照+时间轴+播放"回放 | `LearningRecordsView.vue` 已有单点快照 Modal（replay.*）；`/learning/mastery/snapshot?atEventId=` 已存在 | 部分（单点 Modal，无滑块/播放/图谱投影） | E（阶段6） |
| 9 | 教师端"班级薄弱点图谱"缺图谱视图 | `TeacherDashboardView.vue` 有 nodeHeat 列表/Top5；`dashboard.nodeHeat` 可复用做只读图谱投影 | 部分（列表有，图谱无） | E（阶段7，默认执行路径=补图谱） |

## 3. 实现状态矩阵（已实现/部分/未实现/需实测）

- 已实现且沿用：图谱快照接口、个人掌握叠加、学习事件账本、BKT、四题型判分、测评会话、catalog 目录聚合、verify-learning 8 组断言。
- 部分：实验导师（有面板/会话守卫，API 与位置/命名不符）；时间线回放（单点 Modal）；教师班级分析（有聚合数据无图谱视图）。
- 未实现：wide 布局、图谱反模糊、详情去重、知识详解页签、全局 AI 助手、未归类共享谓词、候选池修复、CE 观测分离、Tutor 配额。
- 需实测（阶段 8 联调确认）：CE→掌握度快照不变性、回放统计单位、教师图谱与列表数据一致性。

## 4. 角色-路由访问矩阵（代码级）

| 角色 | 顶栏入口 | 默认落地 | 说明 |
| --- | --- | --- | --- |
| 学生（user） | 首页/知识图谱/题目练习/实验中心/学习资源 | /graph | 学习记录走头像菜单；/account 学生信息 |
| 管理员（admin） | 学生五入口 + 教学管理 | /teaching/overview | /teaching/* 全部 ADMIN；学习侧页面显示本人预览上下文 |

验收纪律（计划 §1.1）：管理员（截图中的张小明）空学习数据不代表学生缺陷；学生/教师/管理员分开截图验收。

## 5. 计划勘误（以真实路径为准）

- §3.3 `controller/learning/LearningAssessmentRunController.java` → 实际 `controller/LearningAssessmentRunController.java`。
- §3.3 `service/LearningAttributionService.java` → 实际 `learning/LearningAttributionService.java`。
- §3.3 `enums/LearningResultEnum.java` → 实际 `model/enums/learning/LearningResultEnum.java`。

## 6. 裁决（本阶段新立，编号 L3-R#）

- **L3-R1**：实验导师 API 从 `/api/chat/ask` 迁移到 `/api/learning/tutor/ask`——以实验题映射的知识节点作为 `nodeName` 上下文（ViewQuestionView 已持有关联节点数据），代码/判题上下文打包进 question 文本；取代 log2 R6 的 chat/ask 临时许可（log3 §7.2.5 为更新的规范）。前端 `askQuestionUsingPost` 保留给阶段 4 的全局助手。
- **L3-R2**：阶段 0 的"基线截图"按既有纪律交由用户手动完成（计划禁止浏览器自动化）；本文档提供截图矩阵（页面×角色×视口）作为 §12.3 的执行清单，基线与验收截图合并为一次人工验收。
- **L3-R3**：阶段 5.1 完成后端共享谓词后，同步删除前端 LearningQuestionsView 的"未归类"哨兵空态与 startRun 守卫（log2 终审 I-1 补丁），由后端真实能力替代。
- **L3-R4**：教师端走默认执行路径（补班级热力图谱视图，§11.1），不做 11.2 的改名缩减。
