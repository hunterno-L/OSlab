# OSLab 第三阶段执行计划：学习流程与 UI 重构

> 日期：2026-10-01。本文可直接交给实施 agent，不包含日历排期。
> 目标：在已建立的知识图谱和学习事件底座上，把平台改造成能持续学习、测评、实验、复盘的完整教学产品。
> 本轮只制定计划。源码结论来自当前文件核对；构建、浏览器和接口通过记录来自 2026-09-30 审计，本轮没有重新运行这些验收。

## 1. 本阶段的产品决定

### 1.1 统一名称

| 场景 | 显示名称 | 内容 |
| --- | --- | --- |
| 概念与规则验证 | 知识测评 | 判断、单选、多选、填空 |
| 需要编写并提交代码 | 编程实验 | 代码编辑、提交、判题、诊断 |
| 算法过程推演 | 交互模拟 | 页面置换、CPU 调度、下一步预测 |
| 教学内容 | 知识学习 | 概念、例子、误区、关联资料 |
| 个人学习行为 | 学习记录 | 测评作答、实验提交、模拟预测及状态变化 |
| 内容和班级管理 | 教学管理 | 知识维护、测评管理、实验管理、班级分析 |

新的界面、通知、AI 来源标题、演示文档和本阶段交付统一采用此表。
学生页面不显示服务端实现说明、算法名堆砌或“平台能力入口”说明面板。
内部 `LearningQuestion`、表名、事件枚举和现有 API 路径继续复用，命名调整不等于重建数据模型。
历史证据中的来源字符串保留原始值，通过统一显示层映射成新名称；不可批量改写事件账本。

### 1.2 学生导航只保留四个一级入口

```text
课程学习       知识图谱       实验中心       学习记录

课程学习
  章节目录 -> 知识点工作台
              学习 | 测评 | 实验 | 记录 | 导师

实验中心
  编程实验 | 交互模拟

学习记录
  时间线 | 待巩固
```

知识测评与课程知识点是一体的学习活动，不再与章节目录并列为两个含义重叠的顶栏入口。
资料在课程和节点内部使用，移除资源页中重复列举整个平台按钮的区域。
顶栏用于切换工作领域；侧栏只用于当前领域内的章节、知识点或管理栏目，不再复制顶栏。

### 1.3 管理员采用独立教学管理工作区

管理员增加 `教学管理` 入口，其侧栏包含：班级分析、知识维护、测评管理、实验管理、用户管理。
管理员登录默认进入班级分析；学生默认进入课程学习。
管理员仍能进入学习侧页面，但显示自己的预览上下文，不冒充其他学生。
现有节点新增、删除、关系维护、只读 Cypher、题目审核和用户管理能力均保留，集中到管理工作区。
本阶段沿用后端 `admin/user` 权限，不额外引入一个只有前端定义的教师角色。

### 1.4 三个关键交互改变

1. 知识点工作台由纵向模块堆叠改为有明确职责的页签；首屏只显示当前学习活动和必要摘要。
2. 知识测评由多题长列表改为单题作答、即时反馈、主动下一题、本轮总结；筛选发生在开始测评前。
3. 编程实验提交后留在编辑器页面，展示等待、判题中、终态和补救动作，不再跳到全部提交列表。

## 2. 当前真实起点与修正清单

### 2.1 已有能力，继续复用

依据当前源码及 `docs/learning-phase2-audit.md` 第 7 节：

- 图谱快照已有全量节点、孤立节点、去重关系和章节字段；不重复执行第二阶段的快照重写任务。
- 图谱页已移除演示子图数据源，具备筛选、适配和重置；动态节点工作台路由已存在。
- 知识测评四题型判分、审核状态机、BKT、学习事件、概念与实践分项均存在。
- 规则归因已有实现，GraphRAG 有 AI 和规则回答，FIFO/LRU 与 FCFS/SJF/RR 可复用。
- `learning-theme.css` 已存在并在 `main.ts` 引入；下一步是统一应用，不再新造第二套 token 文件。
- 登录注册知识流组件和页面已有新实现，保留真实账号流程。
- 2026-09-30 审计记录的 150 节点/171 关系是当时数据，不是新版本必须写死的数量。

### 2.2 当前需要改的逻辑

| 编号 | 源码证据 | 本阶段动作 |
| --- | --- | --- |
| U1 | `routes.ts` 两次注册 `/all_question`；`GlobalHeader.vue` 自动把可见路由展开为一级菜单 | 统一路由、一级导航配置和活动项逻辑 |
| U2 | `KnowledgeGraphView.vue` 内另有 `courseNav`；账号身份与图谱默认模式未区分 | 复用共享应用壳，管理与学习上下文分开 |
| U3 | 图谱侧栏将有证据节点的掌握度均值称作“本章已掌握” | 区分学习覆盖率、已掌握比例和已测节点平均掌握度 |
| U4 | `LearningQuestionsView.vue` 同时渲染直达题和分页题卡；章节数组硬编码；只在 mounted 读取 query | 单题测评流程、服务端章节目录、route query 同步 |
| U5 | `NodeLearningWorkspaceView.vue` 同页放全部题卡、路径、事件、导师；推荐只有文字 | 页签化，推荐返回可执行资源，统一返回上下文 |
| U6 | `ViewQuestionView.vue#doSubmit` 成功后跳 `/question_submit`；标签传 `tags`，其他页面多使用 `nodeName` | 留页判题、统一节点上下文和提交详情 |
| U7 | `TeacherDashboardView.vue#drillQuestions` 通过题目标题包含节点名筛选 | 服务端按 `learning_question_node` 精确关联 |
| U8 | 学生只有节点内近期事件；全局 `teacher/timeline` 为管理员接口 | 增加当前学生专用时间线与历史快照读取 |
| U9 | `RecommendationVO` 只有节点、类型、原因、分数；图谱前置路径用单值 Map 丢失多前置分支 | 后端输出有序补救路径和行动资源 |
| U10 | 归因强度按错误类型固定数值，工作台有历史失败时持续优先展示失败 | 展示真实依据强度与诊断范围，明确最近状态、历史错误、已纠正错误 |
| U11 | 学生与管理共用含答案字段的 VO/TS 类型，运行时虽做清空 | 分离学生题面类型与管理编辑类型，保留管理员答案编辑 |
| U12 | 学习闭环验收脚本使用回显沙箱，不编译或运行提交代码 | 联调验收与真实执行验收分开完成和记录 |
| U13 | 工作台所有节点都给出 FIFO/LRU 模拟入口；实验关联用 tags 的 LIKE 匹配 | 按真实节点资源映射选择模拟器，JSON 标签精确匹配实验 |
| U14 | 教师时间线未按班级约束；实验统计按多节点 JUDGE 事件行计数 | 班级范围贯穿下钻与时间线，实验按真实提交去重 |
| U15 | `LearningEventServiceImpl#recordAttemptEvents` 用 `isFirst && insertWithMasteryUpdate`，重复作答跳过事件写入 | 每次有效作答记录行为，仅符合独立观测规则的事件更新模型 |
| U16 | 导师先装满节点和题目来源，可能挤掉本人错误记录与实验来源 | 按提问任务排序证据，保证个性化问题纳入相关本人事件 |

以上为源码审计，不代表已在当前运行环境复现全部行为。

## 3. 路径、资料与可复用接口

### 3.1 路径基准

所有下文相对路径均基于以下明确根目录：

```text
R = E:/web/web/源程序（包含数据库）
F = R/前端代码/osLab-frontend
B = R/后端代码/osLab-backend
J = B/src/main/java/com/yupi/springbootinit
T = B/src/test/java/com/yupi/springbootinit
```

### 3.2 执行前需要读取的资料

- `R/知识图谱智能学习闭环方案.md`：第 4、7、8、11 节的功能目标；本文统一覆盖其历史显示用词。
- `R/docs/learning-phase2-audit.md`：当前已实现、联调记录、延后项；报告中的早期“待做”表须与第 7 节和源码一起判断。
- `R/知识图谱智能学习闭环第二阶段修正与产品化执行计划.md`：只参考已确定的业务契约，不重复执行已完成任务。
- `R/design/frontend-concepts/2026-09-28/opendesign-knowledge-workspace.png`：用户确认的视觉基准，只继承构图、层级、留白和克制程度，文案采用本计划。
- `R/design/auth-entry-2026-09-30/设计与交接.md` 与现有认证源码：参考已实现的排版与动效，不将旧说明中的待实现状态当作当前事实。
- `F/package.json`、`F/src/plugins/axios.ts`、`F/src/api/learning.ts`：已有依赖、响应和权限处理。

旧目录中的深色图稿不作为本阶段视觉标准。

### 3.3 当前允许复用的 API

下表使用 Controller 相对路径；后端 context-path 为 `/api`，完整请求需加一次该前缀，不能与客户端 baseURL 重复拼接。这些接口已经存在，新接口在后续阶段明确标注为拟新增。

| 接口 | 当前用途 | 源码模式 |
| --- | --- | --- |
| GET `/knowledge_graph/getAll` | 课程结构 | `J/controller/KnowledgeGraphController.java`、`J/service/KnowledgeGraphService.java#getGraphSnapshot` |
| GET `/learning/mastery/graph` | 本人状态叠加 | `LearningMasteryController#personalGraph` |
| GET `/learning/mastery/node?nodeName=` | 节点聚合 | `LearningMasteryServiceImpl#nodeWorkbench` |
| GET `/learning/mastery/recommend` | 基础推荐 | `LearningMasteryServiceImpl#recommendations` |
| POST `/learning/questions/list/page` | 测评题目分页 | `LearningQuestionServiceImpl#pageVO` |
| GET `/learning/questions/get?questionId=` | 题面 | `LearningQuestionServiceImpl#getQuestionVO` |
| POST `/learning/questions/answer` | 作答、判分、事件 | `LearningQuestionServiceImpl#answerQuestion` |
| POST `/learning/questions/add`、`update`、`review` | 内容维护和审核 | `LearningQuestionController` |
| POST `/learning/simulation/page`、`cpu` | 算法步骤与预测 | `LearningSimulationServiceImpl` |
| POST `/learning/tutor/ask` | 带节点上下文的导师 | `LearningTutorServiceImpl` |
| GET `/learning/teacher/dashboard`、`timeline` | 管理员班级分析和事件 | `LearningTeacherController` |
| POST `/question/question_submit/do` | 提交实验代码 | `QuestionController#doQuestionSubmit` |
| GET `/question/question_submit/get?id=` | 提交详情 | `QuestionController#getQuestionSubmitById` |

前端学习 API 当前使用原始 Axios response，页面读 `res.data.code`；生成式客户端读 `res.code`。
纠正 `learning.ts` 的 Promise 类型，让其与实际 `AxiosResponse<BaseResponse<T>>` 一致，暂不全站更换返回方式。
不可为了少一层 `.data` 修改全局拦截器并破坏现有客户端。

### 3.4 可直接复用的源码索引

以下位置来自本次静态核对；文件变化后按方法名重新定位，不把行号当作固定契约。

| 用途 | 当前模式位置 | 复用内容 |
| --- | --- | --- |
| 真实图谱快照 | `J/service/KnowledgeGraphService.java:75` | `getGraphSnapshot()` 的节点、关系、章节和去重逻辑 |
| 节点聚合 | `J/service/impl/LearningMasteryServiceImpl.java:173` | `nodeWorkbench(User, String)` 的本人状态与资源聚合 |
| 分页与题面 | `J/service/impl/LearningQuestionServiceImpl.java:130`、`:141` | 已发布过滤、题面读取和 MyBatis-Plus 分页 |
| 作答与反馈 | `J/service/impl/LearningQuestionServiceImpl.java:200` | `answerQuestion(LearningAnswerRequest, User)` 的判分事务 |
| 行为事件 | `J/service/impl/LearningEventServiceImpl.java:77`、`:107`、`:158` | 测评、判题、模拟的来源和幂等键；修正后复用，不复制短路缺陷 |
| 导师来源 | `J/service/impl/LearningTutorServiceImpl.java:96` | 模型调用、来源 DTO、引用编号与规则回答 |
| 分页时间线 | `J/service/impl/LearningTeacherServiceImpl.java:90` | 查询/分页形状；学生绑定本人，教师增加班级约束 |
| 提交与详情 | `J/controller/QuestionController.java:321`、`:352` | 提交返回 ID、详情读取、已有生成客户端 |
| 前端四题型输入 | `F/src/components/learning/QuestionAnswerCard.vue:177` | 作答状态、答案提交和结果事件 |
| 工作台请求 | `F/src/views/learning/NodeLearningWorkspaceView.vue:337`、`:370` | 节点加载、导师请求；改为局部刷新与按节点隔离 |
| 代码提交按钮 | `F/src/views/question/ViewQuestionView.vue:174` | 真实提交请求；替换提交后的跳转行为 |

## 4. Phase 0：文档与源码发现、基线与契约

### 文件与参考

读取第 3 节资料；重点查看 `F/src/router/routes.ts`、`J/service/impl/LearningMasteryServiceImpl.java`、`J/model/dto/learning/LearningQuestionQueryRequest.java` 和现有测试。

### 操作

1. 将第 2 节 U1-U16 与实际源码逐项核对，不延用旧报告的错误完成判断。
2. 建立 `R/docs/phase3-baseline.md`，记录源码状态、构建结果、数据数量和仍缺的 API。
3. 固定 `nodeName`、`chapter`、`assessmentRunId`、`questionSubmitId` 四类上下文，不再混用标签数组和页面标题。
4. 主 agent 冻结路由表、测评状态枚举、行动 DTO、快照字段后再派发页面任务。
5. 将 2026-09-30 全量测试中的模板 Post/COS 问题与学习模块问题分开记录；不得因为旧测试失败删测试或改环境伪造通过。

### 验收

- 有一份当前接口清单和字段样例，明确已存在与拟新增。
- 第二阶段已完成的快照、节点工作台和归因实现进入复用清单。
- 后续任务使用同一导航、同一节点键和同一数据指标定义。

## 5. Phase 1：先改 UI 逻辑的共享基础

### 文件与参考

- 修改 `F/src/router/routes.ts`、`F/src/components/GlobalHeader.vue`、`F/src/layouts/BasicLayout.vue`、`F/src/access/index.ts`。
- 新建 `F/src/layouts/LearningLayout.vue`、`F/src/layouts/TeachingLayout.vue`、`F/src/components/learning/CourseSidebar.vue`。
- 新建 `F/src/navigation/appNavigation.ts`，复用现有 `checkAccess` 和 Arco menu/dropdown。
- 应用已有 `F/src/styles/learning-theme.css`，参照已确认的 OpenDesign 图。

### 5.1 路由归属

| 规范路由 | 页面 | 顶栏归属 |
| --- | --- | --- |
| `/learn` | 章节学习主页 | 课程学习 |
| `/knowledge/node/:nodeName` | 知识点工作台 | 课程学习 |
| `/assessments` | 测评范围和题目选择 | 课程学习 |
| `/assessments/run/:runId` | 单题测评过程 | 课程学习 |
| `/graph` | 图谱探索 | 知识图谱 |
| `/experiments` | 实验目录 | 实验中心 |
| `/experiments/code/:id` | 编程实验工作区 | 实验中心 |
| `/experiments/simulation` | 交互模拟 | 实验中心 |
| `/records` | 本人时间线和待巩固内容 | 学习记录 |
| `/teaching/overview` | 班级分析 | 教学管理 |
| `/teaching/knowledge` | 节点、关系、学习素材维护 | 教学管理 |
| `/teaching/assessments` | 测评管理 | 教学管理 |
| `/teaching/experiments` | 编程实验维护 | 教学管理 |
| `/teaching/users` | 用户管理 | 教学管理 |

这是待实施路由表，不代表上述新路径现在已经可访问。

### 5.2 兼容现有入口

- `/` 根据已确认的登录态跳转到学生 `/learn`、管理员 `/teaching/overview`；未登录进入现有认证流程。
- `/question` 保留 query 并重定向到 `/learn`；`/all_question` 只保留一次并转到 `/experiments`。
- `/learning/questions` 转到 `/assessments`，保留 `nodeName/chapter/questionId/type`。
- `/view/question/:id` 转到 `/experiments/code/:id`；原模拟器、管理、教师、资源路径保留兼容重定向。
- `/question_submit` 转到 `/records?eventType=JUDGE`；`/resources` 转到课程资料视图，保留旧 query 中的定位信息。
- 给每个子路由设置 `navKey` 和面包屑归属，详情页不再因 path 不同丢失顶栏选中态。
- 教学管理路由全部沿用 ADMIN 校验，侧栏从统一配置生成，不靠页面私有列表判断权限。
- 有合法登录前目标则优先恢复该目标，无目标才走角色默认首页。
- 用户菜单用现成 dropdown，包含角色信息与退出，支持键盘和点击，不再依赖短暂 hover。

### 5.3 页面尺寸和视觉

- 顶栏只放一级入口和账号；课程侧栏约 200-220px，主区自适应。
- 内容面板使用细边界与 4-8px 圆角，页面区块不用一层套一层的浮动卡片。
- 蓝色用于选中和主动作，绿/琥珀/红用于学习状态；推荐只做低强度边框强调。
- 页面标题 22-26px、正文 14px 为主，固定断点调整，不按 viewport 连续缩放字体。
- 1024px 以下折叠侧栏，手机图谱为主画布与详情抽屉，编程页允许切换区域；无正文横向溢出。
- 学期从课程元数据读取，没有真实配置则不显示过期年份。

### 5.4 全站更新范围

| 页面组 | 必须更新的页面与组件 | 收口结果 |
| --- | --- | --- |
| 共享入口 | `BasicLayout.vue`、`GlobalHeader.vue`、`NoAuthView.vue` | 统一导航、角色入口、内容宽度、无权限反馈 |
| 课程与节点 | `ChapterQuestionView.vue`、新的课程主页、`NodeLearningWorkspaceView.vue`、`NodeWorkbenchDrawer.vue` | 章节目录、节点页签、返回上下文、统一状态展示 |
| 测评 | `LearningQuestionsView.vue`、`QuestionAnswerCard.vue`、新的测评过程页 | 范围选择、单题作答、解析、总结、待巩固 |
| 图谱 | `KnowledgeGraphView.vue`、`KnowledgeGraph.vue` | 全图探索、个人状态、行动路径、详情抽屉 |
| 实验 | `QuestionsView.vue`、`ViewQuestionView.vue`、`CodeEditor.vue`、`SimulationView.vue` | 目录、编辑器、留页结果、算法过程，使用同一实验工作区 |
| 记录 | `QuestionSubmitView.vue`、新的学习记录页 | 本人记录、结果详情、历史回放，不再出现孤立的旧提交页 |
| 教学管理 | `TeacherDashboardView.vue`、`ManageLearningQuestionsView.vue`、`ManageQuestionView.vue`、`AddQuestionView.vue`、`AdminView.vue`、`UserCenterView.vue` | 分析与维护统一在管理壳内，编辑流程和用户操作完整保留 |
| 认证与资料 | `UserLoginView.vue`、`UserRegisterView.vue`、认证共享组件、`ResourcesView.vue` | 保留已认可的认证设计，统一术语、表单控件和资料归属 |

先完成壳与代表页，再逐组替换旧样式和流程；不是只给图谱页换颜色。
旧示例页面不作为正式入口，仍在使用的创建/编辑/详情页面必须随所属工作区一起更新。

### 验收

路由无重复；学生四入口和管理员教学入口正确；详情页高亮与面包屑正确；旧深链接仍有效；所有主要页面只使用一套壳和 token。
不要为了改导航重写 Vue Router、Vuex 或权限模型。

## 6. Phase 2：补齐学习流需要的后端契约

### 文件与参考

- 修改 `J/controller/LearningMasteryController.java`、`J/service/impl/LearningMasteryServiceImpl.java` 和相关 VO。
- 新建 `J/controller/LearningCourseController.java`、`J/service/LearningCourseService.java` 与实现，聚合真实章节、节点和学习进度。
- 修改 `J/model/dto/learning/LearningQuestionQueryRequest.java`、`J/service/impl/LearningQuestionServiceImpl.java`。
- 修改 `J/service/impl/LearningEventServiceImpl.java`，先修正重复作答账本与观测分离，再接后续学习活动。
- 新建 `J/service/LearningPathService.java` 与实现，读取已有 `KnowledgeGraphService#getGraphSnapshot`。
- 更新 `F/src/api/learning.ts`，复制当前 BaseResponse、登录用户和分页模式。
- 新增迁移 `R/数据库文件/oslab_upgrade_v4_flow.sql`，只添加本阶段需要的字段和表。

### 6.1 课程目录与学习指标

拟新增 GET `/api/learning/course/chapters`，返回：

```text
chapterKey, title, order, nodes[]
knowledgeNodeCount, nodesWithEvidence, masteredNodeCount
assessmentCount, experimentCount
coverageRate, masteredRate, assessedMasteryAverage
```

- 章节来源为真实图谱 chapter 和现有实验章节映射，不保留前端四章节数组。
- 章节顺序由显式配置确定；不要求现有章节名与旧题目集合编号天然相同，需维护明确对应关系。
- 图谱中的实验资源节点不应算进知识点进度分母；必要时增加显式 `nodeKind` 元数据，不靠标题包含“实验”猜类型。
- `coverageRate = 有证据知识节点数 / 章节知识节点数`。
- `masteredRate = GREEN 知识节点数 / 章节知识节点数`。
- `assessedMasteryAverage` 只描述已产生证据节点的均值，不能称作整章完成率；无证据时返回 null。

### 6.2 学习状态与错题查询

扩展测评分页请求：`studyState = ALL | UNANSWERED | NEEDS_REVIEW | CORRECT`。
后端按当前用户最近有效作答判定，最新答错为 NEEDS_REVIEW，最新答对为 CORRECT；保留首次是否答错作为独立字段。
按该状态过滤后再分页，不能只过滤当前前端页。
学生题面增加 `attemptCount/latestAttemptId/latestResult/firstResult`，不附带标准答案。
学生和管理员题面 VO 分离，管理详情继续返回编辑所需答案，不让管理编辑页失去功能。
拟新增 ADMIN 专用 GET `/api/learning/questions/manage/get?questionId=` 和 POST `/api/learning/questions/manage/list/page`；学生题面接口统一返回学生 VO，管理页面切换到管理接口。
作答后的 `LearningAnswerResultVO` 继续返回合法的答案与解析反馈；题面接口不含答案并不意味着学生提交后看不到解析。
补测 `studyState` 跨页、不同用户隔离、纠错后状态改变。

### 6.3 可执行补救路径

拟新增 GET `/api/learning/mastery/path?targetNodeName=`：

```text
LearningPathVO {
  targetNodeName, basisEventIds[], items[]
}
PathItem {
  nodeName, order, status, reason, prerequisites[], action
}
LearningAction {
  kind: NODE | ASSESSMENT | EXPERIMENT | SIMULATION,
  nodeName, resourceId?, algorithms?, chapter?
}
```

实施方法：

1. 使用同一份快照建立多前置邻接表，保留所有 `dependent` 边。
2. 当前存储语义是 `A -dependent-> B` 表示 A 依赖 B，学习顺序必须 B 在 A 前；由现有测试和演示知识链验证。
3. 只将依赖边用于拓扑顺序，`related/contain/implements` 不可混入依赖排序。
4. 先安排缺失/薄弱前置，再安排目标节点的测评、模拟与实验；已掌握项可跳过，最近失败项优先。
5. 检测依赖环并返回涉及节点，不能递归死循环，也不为得到一条路径删除真实边。
6. 选择真实资源，没有资源就返回 NODE 学习动作，不制造无效 resourceId。
7. 前置风险只影响提示与排序，不直接改下游 BKT；基础规则无 AI 也运行。

工作台、课程目录、路径与导师共同复用一个资源解析方法：实验 tags 用 JSON 解析后精确匹配，去掉名称 LIKE；模拟入口按已配置的节点与算法映射返回。
CPU 调度节点进入调度模拟，页面置换节点进入置换模拟，无匹配节点不出现无关模拟按钮。
首次实现可用明确配置表和现有标签，不为此重建一个通用资源管理系统。

返回结构由各页面共同使用，移除图谱页面单前置 Map 和工作台各自拼文案的推荐逻辑。

### 6.4 先建立正确的记录层

在接入新的测评与实验流程前，完成 U15 的事件修正，并提前落地 Phase 7 的快照字段、事件分组和写入骨架。
每次作答有记录，但不等于每次都更新模型；重试、非观测结果保存相同 before/after，独立观测只更新一次。
历史回放页面可稍后实现，新学习活动从开始就保存可回放的数据，避免先跑完演示再补造历史。

### 验收

章节指标分母明确；测评状态服务端跨页查询正确；两个前置分支均出现在目标路径；每个路径行动能解析为真实页面；纠错也留下可追踪且不重复计分的事件。

## 7. Phase 3：课程学习与知识点工作台重组

### 文件与参考

- 新建 `F/src/views/learning/CourseLearningView.vue`。
- 重组 `F/src/views/learning/NodeLearningWorkspaceView.vue`。
- 简化 `F/src/components/learning/NodeWorkbenchDrawer.vue` 为图谱快速预览。
- 复用 `nodeWorkbench()` 和新章节/路径接口；当前工作台的事件、实验、导师调用模式可以复制。

### 7.1 课程首页

左侧为章节目录，主区为当前章节的知识点列表，右侧仅在有推荐时展示“继续学习”。
每个节点行显示标题、学习状态、测评数量、实验数量和进入动作，不放一排缺乏依据的 KPI。
新账号直接展示课程结构；首次学习按钮根据课程顺序选择首个可学节点。
已有记录时继续最近有效节点，失败记录优先提供具体补救动作，而非每次重新浏览全图。
课程内提供“知识测评”和“学习资料”的次级入口；资料视图复用现有 `ResourcesView.vue` 的有效资源，可用 `/learn?view=resources` 直达，不新增一套一级导航。

### 7.2 节点工作台五个页签

| 页签 | 主要内容 | 主动作 |
| --- | --- | --- |
| 学习 | 概念摘要、关键点、一个例子、误区、前后置关系 | 开始测评 |
| 测评 | 数量与本人的完成情况、开始/继续本轮测评 | 继续作答 |
| 实验 | 编程实验和交互模拟、最近结果 | 进入实验 |
| 记录 | 该节点事件和状态变化 | 查看详情 |
| 导师 | 当前节点对话、证据来源 | 发送问题 |

所有页签共用紧凑头部：节点名、章节、状态、概念/实践分数、证据数。
不在每个页签重复画相同掌握度仪表盘。
无记录时显示“尚未测评”，不把 null 转成 0% 的精确结论。
当前推荐只出现一次，具有可点击的行动，不在左右栏各写一份同样文字。

### 7.3 最近状态和上下文

- 当前结果来自最近有效事件；历史失败仍可查阅，但后续成功不能继续显示为当前失败。
- 对“已纠正”使用同一资源的后续有效通过确认，不能凭另一题通过就关闭全部错误。
- 测评按 `questionId` 追踪纠错，实验按提交记录中的 `questionId` 和对应节点追踪；不同 `questionSubmitId` 可以属于同一实验，不能按提交 ID 判断是不是同一题。
- “已纠正这道题”与“已掌握知识点”分别展示；复习成功可以关闭该题待巩固状态，但不伪造新的独立观测把节点刷绿。
- query 的 `tab/chapter/source` 与页面状态同步，浏览器后退恢复到此前章节和页签。
- 跳入测评、模拟、编程时带 `nodeName` 和可验证的站内 `returnTo`。
- 节点切换时将导师会话按节点隔离，不把 A 节点回答插进 B；异步请求提交时记录原节点，再写入对应会话。
- 作答/预测/判题更新通过共享刷新动作影响节点、路径和记录，不整页重载导致解析和编辑器草稿丢失。

### 7.4 学习素材的低成本组织

新增一张 `learning_node_content`，以 `nodeName` 关联，字段包括：

```text
id, nodeName, summary, objectivesJson, keyPointsJson,
exampleMarkdown, misconceptionsJson, resourcesJson,
publishStatus(DRAFT/PUBLISHED), updatedBy, createTime, updateTime
```

有已发布素材则使用，其他节点回退到现有描述与关系；没有素材不阻断图谱和测评。
素材可为空，不要求每个节点都配置，不创建每节点静态页面。
`resourcesJson` 只保存已存在文档或外链引用，保留现有有价值的学习资源。
教学管理用一个共享表单维护，GraphRAG 只检索已发布内容。

### 7.5 图谱导师从来源列表变为学习助手

修改 `J/service/impl/LearningTutorServiceImpl.java`、`TutorAskRequest/TutorSourceVO/TutorAnswerVO`，复用现有模型调用与来源编号规则，不新增另一套聊天服务。

- 依照提问选择证据：概念问题优先已发布素材和依赖关系；失误问题优先本人的对应作答/提交；下一步问题使用 Phase 2 的行动路径。
- 保留当前节点来源，按任务分配剩余来源名额，不能先填满相邻节点和题目后把个人错误证据截掉。
- 来源带实际节点、资源 ID 和可执行 action；点击题目来源进入对应测评，点击实验来源进入代码页，点击事件来源进入本人记录详情。
- 回答中的编号引用定位到对应来源；检索过程默认折叠，不把工程日志当成首屏学习内容。
- 来源为空时陈述资料不足；AI 不可用时仍返回真实资料与具体下一步动作，页面用简短状态标签标识，不显示一大段实现说明。
- 按节点发送最近少量对话作为拟新增的可选上下文字段，使追问能引用同节点前文；不把所有节点的聊天拼到一起。
- 使用已发布素材、本人事件和同一图谱快照；不扩展向量数据库、外部联网检索或全课程自动生成。

### 验收

工作台首屏不再输出全部题卡和事件；五页签能切换并恢复；当前诊断可随纠错改变；两个节点导师上下文不串；素材、测评和实验均从同一 nodeName 进入。

## 8. Phase 4：单题测评、本轮总结与待巩固

### 文件与参考

- 重写 `F/src/views/learning/LearningQuestionsView.vue` 的范围选择部分。
- 新建 `F/src/views/learning/AssessmentRunView.vue`、`F/src/components/learning/AssessmentNavigator.vue`。
- 复用并重组 `F/src/components/learning/QuestionAnswerCard.vue` 的四题型输入和作答请求。
- 新建 `J/controller/LearningAssessmentRunController.java`、对应 service/entity/mapper/VO。
- 修改 `J/model/dto/learning/LearningAnswerRequest.java`、`J/model/entity/LearningAttempt.java`，兼容原有作答入口。

### 8.1 测评开始页

支持按章节、知识点、题型、难度和学习状态选择；显示范围内实际题数。
选择 5 或 10 题形成固定队列，数量不足则使用实际数量并显示真实数字。
从节点进入自动选该节点；从错题进入默认 NEEDS_REVIEW；`questionId` 直达只建立一次单题队列，不在列表中重复渲染。
题目顺序由服务端稳定决定，不能前端随机决定后还声称是智能推荐。

### 8.2 单题过程

```text
待作答 -> 提交中 -> 已反馈 -> 下一题
                      -> 重试当前题（不改变本轮首次得分）
最后一题已反馈 -> 本轮总结 -> 查看薄弱节点 / 继续实验 / 返回学习
```

- 首屏一个题面、一组输入、一处提交，不同时呈现多题按钮。
- 提交完成后保留学生答案，显示解析、误区和节点变化，用户主动下一题；不自动跳走。
- 未提交题可以切换；草稿按题缓存；题号导航稳定区分未答、答对、答错。
- 同题本轮首次结果计入本轮正确率，重试作为纠错记录，不能重试到 100% 覆盖首次结果。
- 首次独立作答与历史已做题分开展示，不把复习正确率称作独立测评能力。
- “待巩固”来源为服务端状态，可刷新后继续，非当前页面临时数组。

### 8.3 最小持久化会话

新增 `learning_assessment_run`：

```text
id, userId, chapter, nodeName, questionIdsJson,
status(ACTIVE/FINISHED), createTime, finishTime
```

`learning_attempt` 增加 nullable `assessmentRunId`。队列固定，顺序与首次结果根据服务端作答读取，避免会话里再存一份重复判分数据。
`LearningAnswerRequest` 增加可选 `submissionKey`，`learning_attempt` 保存该键并建立 `(userId, submissionKey)` 唯一约束；历史作答不带该键仍可读取。
拟新增：

- POST `/api/learning/assessment/start`：范围和数量 -> `runId/questionIds`。
- GET `/api/learning/assessment/run?runId=`：当前进度、题面摘要、已作答反馈。
- POST `/api/learning/assessment/finish`：完成确认与本轮摘要。
- 现有 `/learning/questions/answer` 增加可选 `assessmentRunId`；不带该字段的旧入口保持可用。

题目下线后，未答项明确为不可作答并从有效分母扣除；已答项保留本人作答记录。
提交只进入已有判分事务，不再额外调用一遍事件服务；刷新会话不会重新计算 BKT。
重复作答也写行为记录，其 before/after 可相等；是否计入独立观测由原模型规则决定，不能用短路表达式跳过记录。
提交请求使用稳定提交键识别同一次请求重发，主动重试使用新键；本轮首次得分从该题的首个真实作答记录计算。

### 验收

覆盖四题型、题号切换、刷新续做、重复提交、纠错、队列题下线、跨账号访问。
最后一题后能看到真实得分和具体关联节点；提交前的学生题面不下发答案，提交后的反馈可展示本人该题的答案与解析。

## 9. Phase 5：编程实验留页判题与交互模拟联动

### 文件与参考

- 重组 `F/src/views/question/ViewQuestionView.vue` 与 `F/src/views/question/QuestionsView.vue`。
- 复用 `F/src/components/CodeEditor.vue`，保留 Monaco、语言选择和代码修改回调。
- 复用生成客户端 `QuestionControllerService#doQuestionSubmitUsingPost`、`getQuestionSubmitByIdUsingGet`。
- 修改 `F/src/views/learning/SimulationView.vue`。
- 补 `J/controller/QuestionController.java` 和 `QuestionSubmitService` 的本人详情约束，旧数据结构和沙箱协议保持不变。

### 9.1 编程实验工作区

桌面为左侧实验说明，中间编辑器，右侧提交结果/诊断；1024px 以下右栏改为页签或底部区域。
顶部显示实验名、所属章节、关联节点、语言选择和提交主按钮。
提交得到 `questionSubmitId` 后，留页查询该提交，按真实枚举显示等待、判题中、通过、失败。
查询使用串行轮询而非并行 interval；终态、离页和切换实验时停止；超时提示仍在处理并允许继续查询，不能伪造成失败。
代码草稿按用户、实验、语言区分保存；失败/查询刷新不覆盖草稿。
终态显示判题消息、可用的运行数据、关联节点与补救行动；隐藏的测试期望答案不下发。
“查看提交记录”是次级动作，不是提交后的强制路由。
若暂时没有独立运行服务，页面只显示“提交评测”，不展示伪装成真实运行的按钮。

### 9.2 交互模拟

- 实验中心分 `编程实验` 与 `交互模拟` 两页签，不把模拟器与 OJ 实验叫成同一个功能。
- 默认根据节点入口选页面置换或 CPU；参数、当前步和 nodeName 一致。
- 步进、播放、暂停、重置分别用图标工具按钮与 tooltip；当前帧、命中/缺页、淘汰页保持稳定网格尺寸。
- 预测必须在揭示下一步前进行，提交后显示服务端结果和事件；不在页面已公开下一步的情况下提示“检验掌握”。
- 本阶段完成 FIFO/LRU/OPT 结果对比。OPT 为拟新增，后端补实现与测试后才显示选项；用于对比的运行不写学习事件。
- 已有 FCFS/SJF/RR 保留并统一甘特图和进程数据表，不扩展银行家等新主题。
- 完成预测后提供“返回当前知识点”和“进入关联编程实验”，带相同上下文。

### 9.3 真实执行验收

现有 `scripts/dev/sandbox_stub.py` 只回显输入，能验证协议和事件链，不能验证提交代码可执行。
复用 `R/代码沙箱/server.py`、`runner.py`、`Dockerfile` 和 `deploy/docker-compose.yml` 中已接入的 remote 服务，不再重建沙箱。
为真实沙箱增加固定 LRU 的正确程序、错误程序、编译错误程序、运行时错误程序及超时程序样例，确认不同代码确实产生不同输出。
在 Docker 可用或已配置的真实 remote sandbox 环境执行，记录 sandbox 类型、语言、提交 ID 和终态。
联调脚本补真正的编程提交、终态查询和模拟预测断言，修正目前脚本头部覆盖声明大于正文的问题。
若当前机器无法执行，交付报告分别写明“联调已验证”和“真实执行尚未验证”，不把回显结果作为竞赛实验证据。
不在本阶段主动部署生产服务器。

### 验收

代码提交留页；终态结果和对应 JUDGE 事件可追溯；系统错误不作为知识失误；模拟预测、状态表、OPT 对比有测试；真实代码验收与 stub 联调各有独立记录。

## 10. Phase 6：图谱从可视化升级为行动入口

### 文件与参考

- 修改 `F/src/views/knowledgeGraph/KnowledgeGraphView.vue`、`F/src/components/KnowledgeGraph.vue`。
- 读取已完成的图谱快照接口、Phase 2 路径接口，不再在图谱页面实现另一套路径算法。
- 管理按钮移到 `/teaching/knowledge` 的工具面板，复用现有 Controller 和确认交互。

### 操作

1. 学生进入图谱默认“我的掌握”；课程结构模式按类型/章节强调，不把个人状态叫课程分类。
2. 学生个人图谱仍保留所有节点，无证据节点灰色；焦点路径是显式局部视图，头部区分课程总数与当前可见数量。
3. 管理员维护图谱默认课程结构，右侧显示节点资料、资源配置和管理动作，不默认展示管理员的 0 条个人证据。
4. 普通点击只更新右侧预览；双击或“进入学习”打开节点工作台，不在点击节点时立即跳路由。
5. 推荐路径由后端 PathItem 顺序投影到已有图谱，高亮前置分支和当前目标。
6. 选择路径项可进入实际测评/模拟/编程资源；没有资源时进入知识学习。
7. 学习事件变化只让受影响节点进行短暂描边/颜色过渡，稳定布局，不能每次重排整个画布。
8. 保留 fit/reset/zoom/搜索/章节/状态工具；默认视野可读到主要章节分支，不用把所有节点挤成一团。
9. 管理功能迁移不删除能力；将关系类型、章节和节点类型作为熟悉的选择控件，Cypher 为高级只读工具。

### 验收

图谱不是只有看图与聊天；用户能从证据进入补救路径并完成实际动作；课程计数与筛选计数清楚；完整图谱与管理能力不回退。

## 11. Phase 7：学生学习记录与可信历史回放

### 文件与参考

- 新建 `F/src/views/learning/LearningRecordsView.vue`。
- 扩展 `LearningMasteryController` 和 service；复制当前 `LearningTeacherServiceImpl#timeline` 分页模式，但限定本人。
- 修改 `J/model/entity/LearningEvent.java`、事件 VO 和 `LearningEventServiceImpl` 写入逻辑。
- 在 v4 迁移给 `learning_event` 增加 nullable `snapshotBefore/snapshotAfter` JSON 和 `eventGroupKey`；复用原有不可变事件键。

### 11.1 本人时间线

拟新增 GET `/api/learning/mastery/timeline`，按 `nodeName/eventType/startTime/endTime/current/pageSize` 查询。
用户 ID 从会话取，不接受学生传其他 userId；教师接口保留独立权限。
事件展示资源名称、作答/判题/预测结果、掌握度变化、是否独立证据及资源详情入口。
判断没有题可做、尚未学习与接口失败三种状态，不输出含义混乱的“暂无数据”。

### 11.2 事件快照

新事件与状态更新同事务保存；先修正事件方法的事务边界，不能仅给私有方法加注解或在同类内部调用以为事务已经生效。每次有效行为均记录，非独立观测、编译问题和系统问题按实际观测规则保存不改变模型的前后快照：

```text
pKnown, confidence, conceptScore, practiceScore,
conceptEvidenceCount, practiceEvidenceCount, status
```

历史事件已有 before/after 分数，但没有完整分项和状态条件；不按分数阈值凭空猜 GREEN，因为 GREEN 还依赖两类证据。
拟新增 GET `/api/learning/mastery/snapshot?atEventId=`，按当前用户该时点各节点最后有效快照生成图谱投影。
新事件组使用稳定 `attempt:<attemptId>`、`judge:<questionSubmitId>`、`sim:<预测键>`，同一学习事务影响多节点时返回同一事件组的 after 视图，避免滑块停在部分更新状态。
事件按 `createTime,id` 排序，快照不接收另一用户的事件 ID。
旧事件缺快照时展示已记录的数值和“历史细分状态未记录”，完整状态回放从新增快照基线开始，不补写虚构历史。
基线建立时复制当时 `LearningMastery` 的真实状态，写为 `STATE_BASELINE` 事件并携带完整 after 快照；这不是新学习观测，不调用 BKT、不增加证据数、不计入学习活动或班级得分。
可信度的来源计数也只统计有效独立观测，基线、系统故障等非观测事件不能通过后续计数间接提高可信度。
基线以后从该节点最近一次 after 快照读取；没有证据的节点保持未学习。回放只承诺个人学习状态，底图标明采用当前课程结构，不宣称已重建历史课程拓扑。

### 11.3 归因呈现修正

将“当前失误”“历史失误”“已纠正”分开。
归因来源是题目映射时表述为“本次失败关联的知识点”；只有具体运行/错误证据支持时才展示具体步骤原因。
固定规则强度不包装成经过校准的诊断概率，可用“关联明确/需要进一步确认”并展示依据。
编译问题默认展示代码层诊断，不仅凭编译失败就断言概念薄弱；观测是否影响节点由事件输入规则处理，不改 BKT 公式。

### 验收

学生可独立查全部学习记录；筛选和分页有效；已成功纠错不继续显示当前失败；新快照回放状态真实；回放不会写事件或重算掌握度。

## 12. Phase 8：教学管理、资源完整度与真实下钻

### 文件与参考

- 重组 `F/src/views/learning/TeacherDashboardView.vue`、`ManageLearningQuestionsView.vue` 与旧实验/用户管理页面。
- 新建知识维护页面，复用管理图谱组件和素材编辑表单。
- 扩展 `LearningTeacherController/LearningTeacherServiceImpl`，复制现有 `students` 班级关联和管理员校验模式。

### 12.1 班级分析

拟新增或扩展 `/api/learning/teacher/classes`、`/node/detail?className=&nodeName=`。
班级选择取实际可选班级，不硬编码 `os2026`。
节点下钻必须通过映射表匹配测评题目，通过已确认知识标签/映射匹配实验，不能用标题 contains。
下钻显示：全班人数、有证据人数、薄弱人数、首次测评正确率、实验有效通过率、错误类型、相关活动。
首次正确率分母使用首次有效作答数，不与全部重复作答次数混合；无样本为 null，不能显示 0% 宣称全班不会。
实验通过率以 `questionSubmitId` 去重，排除系统故障；同一提交映射三个节点仍只算一次提交，节点局部统计再限定该节点的提交集合。
节点平均掌握度与学习覆盖率分开展示，匿名化学生事件按班级限定。
给现有教师 timeline 增加 className 条件并使用真实班级成员集合；指定 userId 时还要确认其属于所选班级，未指定 userId 时也不能落到全库事件。
教师切换班级后，下钻与时间线同步切换，不继续显示上一班级事件。
时间窗对比描述“观测到的状态变化”，不在没有对照数据时宣称已证明教学因果效果。

### 12.2 内容管理

- 知识维护：节点、关系、章节、节点类型、共享学习素材、已有资源引用。
- 测评管理：列表筛选、编辑、审核、发布/下线；答案编辑沿用管理员专用接口。
- 实验管理：复用旧创建/编辑，显示节点映射和发布内容；不把旧管理页藏成无人能找到的路由。
- 增加章节资源完整度：知识节点数、已发布素材数、测评覆盖数、实验覆盖数、未关联资源。
- 先提供 JSON 导入预览，逐项验证节点、题型、答案格式和解析，再确认导入草稿；无需为第一版扩展多格式导入框架。
- 导入后的新内容通过真实审核发布，不默认将 AI 生成内容发布。

### 验收

标题不包含节点名的映射题仍能出现在该节点下钻；其他节点题不混入；未测评学生不当作失败样本；管理员四类维护入口真实可用，学生不能调用。

## 13. Phase 9：内容充实、页面收口与一条完整演示

### 文件与参考

- `R/数据库文件/oslab_demo_seed_v3.sql` 与新增 v4 内容种子，复用现有账号，不写生产凭据。
- `F/src/views/ResourcesView.vue`、认证页面、学习/图谱/实验/管理各页面。
- `R/scripts/dev/verify-learning.ps1` 和 `R/演示脚本.md`。

### 13.1 内容边界

本阶段以两条真实知识链为核心，不为整门课生成空壳内容：

```text
内存管理 -> 虚拟内存 -> 页表 -> 缺页中断 -> 页面置换 -> FIFO/LRU/OPT
进程管理 -> 进程状态 -> CPU 调度 -> FCFS/SJF/RR
```

实际前置顺序以经人工确认的依赖关系为准，以上是展示目标，不直接凭此修改整张图。
准备 10-12 个核心节点素材、至少 24 道测评题目，覆盖四题型与常见误区；提供 LRU 和调度的真实实验入口。
每个主节点配置少量高质量内容即可，其他节点继续使用动态模板，不批量制造长讲义。

### 13.2 新的演示主线

1. 学生在课程目录选择页面置换，进入节点学习。
2. 开始 5 题知识测评，故意答错一题；保留反馈并看对应节点变化。
3. 打开图谱，选择后端生成的补救路径，学习缺页与 LRU 关键点。
4. 在交互模拟中预测下一步，查看 FIFO/LRU/OPT 对比。
5. 从同一节点进入 LRU 编程实验，提交错误代码，留页查看真实判题结果和诊断。
6. 通过导师提示和补救活动，再提交正确代码。
7. 返回节点查看新证据，打开学习记录回放学习前后状态。
8. 第二个学生查看同一节点，验证个人状态差异。
9. 管理员进入教学管理，查看班级该节点样本、错题与实验结果，修改并发布一条素材或题目。

不要求“一次通过就变绿”；状态必须服从现有证据和阈值。演示需要 GREEN 时使用足量独立测评与实践事件生成。

### 13.3 UI 收口

- 登录页、导航、表单、题面、错误提示、来源标题和资源页使用同一术语表。
- 主行动每页一处，次级行动明确；工具操作用图标、选项用 select、模式用 tabs/segmented。
- 不渲染工程实现说明、GraphRAG 检索流程长介绍或功能宣传段落；来源和依据在必要时展开。
- 管理员界面保留密集表格和工具栏，学生界面优先学习内容，不把所有页面都改成统计看板。
- 保留认证知识流，动效只承担背景或状态变化，不干扰表单和内容阅读。

### 验收

内容与映射真实存在；上述流程从课程到实验再到记录连续，登录/退出/返回/刷新不丢上下文；全站视觉使用已认可的浅色克制风格。

## 14. Phase 10：验证与交付

### 14.1 自动验证

在对应项目目录执行，记录真实输出：

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd "-Dtest=BktModelTest,LearningQuestionGraderTest,PageReplacerTest,CpuSchedulerTest,LearningAttributionServiceTest,KnowledgeGraphServiceTest,LearningMasteryServiceTest" test
.\mvnw.cmd test
```

```powershell
npm run build
npm run lint
```

全量测试中的既有 Post/COS 失败单列，不写成全量通过；本阶段新测试加入专门套件运行：

- `LearningCourseServiceTest`：章节顺序、节点类型、覆盖率/已掌握比例/均值。
- `LearningQuestionProgressTest`：跨页状态筛选和最新纠错。
- `LearningAssessmentRunTest`：队列、续做、首次得分、题目下线、账号归属。
- `LearningPathServiceTest`：多前置、方向、环、无资源节点、真实 action。
- `LearningSnapshotServiceTest`：历史兼容、快照 before/after、事件组和用户隔离。
- `LearningEventServiceTest`：纠错也记录事件、独立观测与记录分离、基线和系统事件不影响模型、同组多节点写入。
- `LearningTutorServiceTest`：本人错误证据不被截掉、素材发布状态、来源行动、节点会话隔离。
- `LearningTeacherNodeDetailTest`：映射匹配、班级筛选、首次样本分母、多节点提交去重。
- `PageReplacerTest`：增加 OPT 固定样例与与 FIFO/LRU 对照。

前端新增适配 Vue CLI/TS4.5 的组件测试或仓库现成测试方式，覆盖路由活动项、query 改变、单题状态机和编辑器留页结果；不要写一个当前工具链不能运行的假测试命令。

### 14.2 术语检查

新增 `R/scripts/dev/check-ui-terminology.ps1`，检查 Vue template、用户可见字符串、接口 message、AI 来源标题、当前演示文档。
对历史来源字段只检查显示映射，不要求改写旧事件和不可变数据。
检查词通过码点组合配置：`0x666E,0x901A,0x9898`，命中历史显示用语即报文件与位置；本计划和新交付文档也加入检查。
静态检查外，人工检查登录能力简介、通知、解析、来源和管理审核提示，确保没有漏掉动态数据文案。

### 14.3 人工 UI 审核

用户保留人工审核方式：实施 agent 启动本地服务，提交 URL、账号角色说明、页面检查清单；未经新授权不自动打开浏览器或代替用户操作。
检查 1440/1920 桌面与 390 窄屏：长题干、长节点名、代码编辑、图谱、菜单和错误反馈均无互相遮挡。
有数据、无证据、无可选题、等待判题、失败、通过都各有可辨状态。

### 14.4 交付表

| 项目 | 完成证据 |
| --- | --- |
| UI 与路由 | 四学生入口、管理入口、旧深链接、活动项清单 |
| 课程与素材 | 两知识链、已发布内容、测评/实验映射 |
| 知识测评 | 四题型、单题过程、刷新续做、总结、待巩固 |
| 编程实验 | 留页判题、真实提交终态、草稿保留 |
| 交互模拟 | 步进/播放/预测、三算法对比、事件 |
| 图谱和路径 | 多前置路径、真实动作、全图与局部计数 |
| 学习记录 | 本人时间线、历史兼容、快照回放 |
| 教学管理 | 精确下钻、样本分母、资源完整度、内容编辑 |
| 验证 | 构建/测试输出、stub 联调、真实执行独立记录 |
| 视觉与文案 | 人工审核结果、术语检查结果 |

输出 `R/docs/phase3-delivery.md`，每项分“已实现且验证”“已实现待验证”“未完成”，不把上一阶段报告作为本阶段通过证据。

## 15. Agent 执行分组与依赖

| 分组 | 主责 | 依赖与文件边界 |
| --- | --- | --- |
| A：共享 UI 与导航 | Phase 1、Phase 3 页面、视觉收口 | 先定路由和布局；拥有 routes/header/layout/token/navigation |
| B：学习数据与路径 | Phase 2、素材后端、Phase 7 | 拥有聚合/进度/路径/快照/VO，保留 BKT 与既有快照 |
| C：测评与实验过程 | Phase 4、Phase 5 | 依赖 A 路由、B 契约；拥有作答队列、编辑器结果区、模拟器 |
| D：教学管理与内容 | Phase 8、Phase 9 内容、验收脚本 | 依赖 B 指标、A 管理壳；拥有班级下钻、维护界面、种子与主线 |
| 主 agent | Phase 0、契约协调、最终验证 | 集中合并共享 API/SQL/router 改动，提供各阶段验证证据 |

执行顺序：先 Phase 0/1 定结构；Phase 2 后并行接工作台和测评；实验与记录完成后接班级下钻；最后填充内容并验收整条学习主线。
路由、`learning.ts`、SQL 入口和事件写入由主 agent 协调，任务间通过字段契约交接，不在多个分组同时扩散不同版本。
没有条件并行时按同一依赖顺序串行实施即可。

### 直接交接提示

```text
执行 OSLab第三阶段执行计划-学习流程与UI重构.md 中分配给你的阶段。
先读对应源码、最新第二阶段审计和第 3 节接口目录，再写代码。
本文的术语、导航、单题测评和编程留页判题是本阶段产品决定，不自行退回页面堆叠方案。
已有图谱快照、BKT、四题型判分、模拟算法与登录流程优先复用。
新增接口先实现 DTO、后端和测试，再接前端；拟新增路径不能假定已经存在。
允许重写不符合当前流程的新增页面，不覆盖无关用户修改。
管理员管理能力集中展示但保留，学生个人数据仍由登录用户确定。
阶段结束报告改动文件、实际命令输出、人工待审清单和下一阶段依赖。
不提交、推送或部署，除非用户另行授权。
```

本阶段完成标志：学生知道当前学什么、下一步做什么、错误为什么发生、补救是否有效；管理员能维护这些学习活动并看见班级证据，而不是只有更多入口和按钮。
