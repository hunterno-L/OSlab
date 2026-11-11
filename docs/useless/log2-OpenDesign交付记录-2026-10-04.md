# OSLab log2 OpenDesign 重构交付记录

- 日期：2026-10-04
- 执行依据：`docs/log2-OpenDesign执行计划.md`（阶段 0-8）+ `docs/log2-OpenDesign视觉与交互设计.md`
- 证据口径：仅采信本次实际运行的命令输出；每阶段验证由控制方统一执行（实施代理按约束不运行命令）。
- 执行方式：阶段 0/1/7 由控制方内联完成；阶段 2-6 由实施代理完成（no-Bash 契约，阶段 3 代理在写报告前终止、代码已完整落盘，控制方通读核对后采纳并补记报告）。全程未 commit/reset/checkout，保留全部既有未提交改动。

## 1. 阶段完成与验证总表

| 阶段 | 内容 | 验证（真实输出） |
| --- | --- | --- |
| 0 | 基线：HEAD 84dfdea、工作区 125 项既有改动、lint 0err/55warn、build DONE、package exit 0 | `stage0-baseline.md` |
| 1 | 视觉基础：learning-theme.css 重写（OpenDesign token 全集 + Arco 桥接 + 46 处旧变量兼容别名 + .oslab-* 工具类）；GlobalHeader 白底细线/token 化；LearningLayout 画布/1440px/hideSidebar 机制；CourseSidebar token 化 | lint 0 errors / 55 warnings，build DONE |
| 2 | 图谱重做：类别底色 + 左 6px 状态条/角点双维表达；四类关系线型语义（dependent 蓝实线实心箭头，按学习顺序 B→A 渲染；implemente 历史拼写兼容）；章节播种力导向（坐标不回写）；详情面板（证据/关联分组/唯一主 CTA"进入学习"）；管理操作收进折叠区（5 Modal 保留） | lint 0 errors（26 个 prettier error 定点修复后）+ build DONE |
| 3 | 工作台收敛：三页签（知识内容/题目练习/实践）；全宽就地答题卡（弃窄抽屉）；阅读材料合并双来源；导师面板常驻 336px（证据卡 + GraphRAG/规则标识 + 来源跳转 + 检索轨迹折叠）；重复入口（关联实验/关联资源/学习资源/双导师）清零 | lint 0 errors（13 个 prettier 定点修复）+ build DONE |
| 4a | 后端目录聚合：`POST /api/learning/questions/catalog`（章节→知识点→题目数/attempted/needsReview/correct，按 questionId 去重，"未归类"恒排最后，PUBLISHED 口径，3 次查询内存聚合） | `mvnw test -Dtest=LearningQuestionCatalogTest` → 7/7，BUILD SUCCESS |
| 4b | 题目练习学习流：去左侧课程侧栏（routes meta.hideSidebar）；章节条→知识点条→筛选条→单题大卡片；右 240px 本组题目索引（<1024px 抽屉）；目录计数全部来自 catalog；四答案格式与 start 参数白名单严格保持 | lint 0 errors（28 个 prettier 定点修复）+ build DONE |
| 5 | 实验中心删"学习资料"整块；编程实验页恢复**实验导师**（/api/chat/ask 单文本上下文：标题/ID/语言/代码片段/最近判题，历史+pending+重试+用户/切题守卫，localStorage 按用户+实验隔离）；模拟器 runConfig 深拷贝冻结（改输入即标记"需重新运行"并禁用预测）、frames=队列顺序标注、eventId 判据 | lint 0 errors / 55 warnings + build DONE |
| 6 | 教师看板重排：仅用 5 个只读接口；KPI 带口径文案、null 显示"暂无"不伪造 0%；节点下钻/班级时间线（班级约束+分页）/章节完整度；无日期筛选/排名/任务下发等虚构功能；字段→接口映射表见 task-6-report.md | lint 0 errors + build DONE |
| 7 | 跨页清理与联调：见 §2/§3 | 全量后端测试 + HTTP 冒烟 ALL PASSED |
| 8 | 交付记录（本文） | — |

## 2. 阶段 7 检查结果

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 旧入口清理 | rg/grep：`学习资料`(QuestionsView)、`节点导师`(ViewQuestionView)、`关联实验/关联资源`(工作台) | 全部 0 命中 |
| 侧栏隐藏 | routes.ts | /assessments 与 /assessments/run/:runId 均已设 `hideSidebar: true` |
| 术语检查 | `pwsh scripts/dev/check-ui-terminology.ps1` | RESULT=FAILED：仅 `docs/phase3-delivery.md` 4 处历史命中（与 2026-10-03 基线完全一致；历史交付报告不重写）；**源码零未豁免命中** |
| 空白检查 | `git diff --check` | 仅 CRLF 换行提示，无空白错误 |
| 后端全量测试 | `./mvnw.cmd test` | **152 tests, Failures 0, Errors 6, Skipped 1** —— 6 个 Errors 全部为遗留模板测试（Post/PostFavour/PostThumb 缺表、CosManagerTest 缺凭据），与 10-02 基线一致 |
| 后端打包 | `./mvnw.cmd package -DskipTests` | exit 0 |
| HTTP 冒烟 | 回显沙箱(8090) + 后端 jar(8121) + `scripts/dev/verify-learning.ps1` | **FAILURES = 0，ALL PASSED**（三账号登录/图谱快照/审核链/作答判分与答案隔离/工作台/教师端/模拟预测事件/实验判题终态全链路） |
| 新接口 HTTP 验证 | POST `/api/learning/questions/catalog`（student_demo1 会话） | 全量：code=0，75 题/4 章节去重正确；`{"chapter":"内存管理"}`：code=0，61 题与全量自洽，节点 attempted/needsReview/correct 真实（LRU：52/42/15/27） |

联调后已停止本次启动的后端与沙箱 stub（8121/8090 端口已释放），不遗留常驻进程。

## 3. 阶段 7 发现并修复的既有缺陷（重要）

**MyBatis-Plus 静态列缓存污染（潜伏自 10-03）**：5 个 Mockito 测试类（LearningAssessmentRunControllerTest / LearningQuestionAssessmentTest / LearningQuestionBatchTest / LearningWorkbenchQueryTest / LearningQuestionCatalogTest）用 `new MybatisConfiguration()` 手工初始化 TableInfo，默认 `mapUnderscoreToCamelCase=true` 把蛇形列名写入 MP 全 JVM 静态缓存，导致同 JVM 后续 Spring 上下文生成混合 SQL（SELECT 驼峰 / WHERE user_id），LearningMastery/LearningTeacher/LearningRecords 集成测试在**全量运行时** 22 个 Errors（单独运行全部通过，10-03 的定向套件未覆盖）。修复：5 处构造点显式 `setMapUnderscoreToCamelCase(false)` 与生产 yml 对齐；修复后全量回到 152/0/6 基线。

## 4. 接口变化

| 接口 | 变化 | 说明 |
| --- | --- | --- |
| POST `/api/learning/questions/catalog` | 新增 | 入参可选 `{chapter}`；出参 `{totalQuestions, chapters:[{chapter, questionCount, nodes:[{nodeName, questionCount, attempted, needsReview, correct}]}]}`；登录态，本人口径；只读聚合，不触碰事件/BKT |

其余接口契约未变；前端 `learning.ts` 新增 catalog 类型与调用（`res.data.code` 约定），模拟器/导师/教师接口零改动。

## 5. 修改文件清单（本轮 log2）

- 前端（12）：styles/learning-theme.css、components/GlobalHeader.vue、layouts/LearningLayout.vue、components/learning/CourseSidebar.vue、components/KnowledgeGraph.vue、views/knowledgeGraph/KnowledgeGraphView.vue、views/learning/NodeLearningWorkspaceView.vue、components/learning/NodeWorkbenchDrawer.vue、views/learning/LearningQuestionsView.vue、views/learning/AssessmentRunView.vue、components/learning/QuestionAnswerCard.vue、components/learning/AssessmentNavigator.vue、views/question/QuestionsView.vue、views/question/ViewQuestionView.vue、views/learning/SimulationView.vue、views/learning/TeacherDashboardView.vue、router/routes.ts、api/learning.ts（含 4 个文件为定点 prettier 修复；ChatWindow.vue 未动）
- 后端（新增 5）：model/vo/learning/LearningCatalogVO|ChapterVO|NodeVO、model/dto/learning/LearningCatalogRequest、test/.../LearningQuestionCatalogTest；修改 3：LearningQuestionService/Impl/Controller（纯追加）；修复 5：§3 所列测试类
- 未动：数据库迁移（无新表/列）、Neo4j、docker-compose、判题与事件链路

## 6. 已知缺口与遗留（如实记录）

1. **"未归类"列表缺口**：catalog 支持"未归类"组，但 `list/page` 与 `assessment/start` 的 chapter 为精确匹配。最终审查将前端降级描述升级为 Important（I-1）并已修复：选中"未归类"时**一律显示说明性空态**（不再渲染会误当全量列表的语境失真），并禁止以该分组开连续测评；后端补 isNull 特判后可恢复直达列表。
2. contain 关系"空心箭头"以灰蓝细箭头近似（ECharts edgeSymbol 无描边箭头符号）。
3. 实验导师走 `/api/chat/ask` 单文本（计划允许的最小实现），不是结构化 GraphRAG；错误上下文到判题消息粒度。
4. CPU 模拟页无算法对比区（该功能此前不存在，未虚构）。
5. catalog 过滤调用时 totalQuestions=该章节和（自洽不变量）；前端全量调用一次后本地切片，不受影响。
6. 前端组件测试仍无通道（vue-cli+TS4.5 既有限制，沿用人工 UI 审核替代）；workbench 缓存裸测 `node tests/node-workbench-cache.js` 保持可用。
7. 术语脚本 exit 1 仅因历史交付文档 4 处命中（见 §2）。
8. 连续测评候选池为按创建时间倒序前 50 题（既有后端行为）；题目超过 50 时较早题目不进连续测评，后续可改为映射表直查。
9. 清理候选：NodeWorkbenchDrawer.vue 在本轮重做后疑似无调用方（声明的 mastery-changed 未触发），下轮统一清理时确认后处理。

## 7. 人工 UI 验收清单（待用户执行）

入口：先 `.\start-oslab.ps1`（或手工起 stub+后端+前端）。账号密码均 `oslab123456`：teacher_demo（管理员）、student_demo1 / student_demo2（隔离互查）。

| # | 页面 | 检查点 |
| --- | --- | --- |
| 1 | 全局 | 顶栏 64px 白底细线、当前项蓝色下划线、头像下拉（学生信息/学习记录/退出）；1440/1920 与 390 宽 |
| 2 | 知识图谱 /graph | 类别底色 + 状态条/角点；四类关系线型与方向（dependent 箭头=学习顺序）；选中只强调相邻边；关系标签开关；右侧详情证据/关联分组/"进入学习"唯一主按钮；管理员折叠维护区 |
| 3 | 节点工作台 /knowledge/node/:nodeName | 三页签；题目全宽就地答题与反馈顺序；实践两类入口；导师常驻与来源跳转；窄屏导师折叠到底部 |
| 4 | 题目练习 /assessments | **无左侧课程侧栏**；章节条→知识点条（catalog 计数）→筛选；单题大卡片；上一题/下一题；"未归类"空态说明 |
| 5 | 测评过程 /assessments/run/:runId | 题号导航四态；刷新续做不丢队；完成后总结 |
| 6 | 实验中心 /experiments | 无"学习资料"卡；章节分组实验列表 |
| 7 | 编程实验 /experiments/code/:id | 三栏；失败后"实验导师"含题目/代码/判题上下文，可重试；切题旧回复不残留；草稿保留 |
| 8 | 交互模拟 /experiments/simulation | 配置区→步骤矩阵→摘要→对比→预测；改输入即提示"需重新运行"且预测禁用；"已计入学习证据"仅 eventId 存在时显示 |
| 9 | 教师看板 /teaching/overview | KPI 口径文案；切班级零残留；节点下钻；时间线分页；空态"暂无班级数据" |
| 10 | 学习记录 /records | 时间线/待巩固；快照回放；头像菜单进入 |

## 8. 演示主线可用性（log2 计划 §11）

学生登录 → 图谱看"页面置换"前置关系 → LRU 节点工作台 → 题目练习提交一次错误答案 → 反馈/关联节点/下一步 → 页面置换模拟运行 LRU 并预测 → 编程实验提交错误代码 → 实验导师 → 修正再提交 → 图谱看证据与状态变化 → 管理员教师看板看班级薄弱点。以上每一步均有真实接口与数据支撑（stub 判题；真实沙箱执行仍未验证，沿用 10-02/10-03 记录的边界）。
