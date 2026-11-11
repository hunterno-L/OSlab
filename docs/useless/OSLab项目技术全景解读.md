# OSLab 项目技术全景解读

> 面向新接手的工程师：从产品模型到每一层技术实现，读完本文应能独立开发、排障和验收本项目。
> 基准版本：feature/learning-loop-ui-rework @ 93ccf44（2026-10-06）。历史文档只作线索，以本文与源码为准。

---

## 1. 项目是什么

**OSLab = 基于知识图谱数字孪生与实验行为闭环的操作系统智能学习平台**（课程/竞赛项目）。

一句话：把学生对每个知识点的**每一次作答、代码提交、模拟预测**，转化为知识图谱上**可解释的学习证据**，自动完成薄弱点定位、补救路径规划、掌握度追踪与班级教学分析。

核心产品判断（设计红线，改代码前先读）：

1. **图谱必须参与教学决策**，不是展示页。节点颜色 = 学生个人掌握状态（GRAY/RED/YELLOW/GREEN），随行为实时变化。
2. **每个智能判断都要有证据**。掌握度变化一定对应不可变事件账本里的一条记录（before/after 快照 + 来源 + 幂等键）。
3. **AI 是增强层**。判分、归因、路径、掌握度全部有确定性规则；AI（GraphRAG 导师）不可用时闭环照常运行（降级 FALLBACK，界面明示）。
4. **答案隔离红线**：学生 VO 永不下发 `correctAnswer`/`answer`，解析仅在本人作答后返回；判分全部在服务端。
5. 掌握度模型是**教科书 5 参 BKT**（含 pLearn 学习转移项，对对/错观测都生效——答错在低先验下可轻微上升 0.10→0.1123，这是契约，见 §6.4，不要"修复"它）。

典型闭环体验：学生答错 LRU 判断题 → 服务端判分 → 事件账本写入 ATTEMPT（幂等键 + 快照）→ BKT 更新 `learning_mastery` → 图谱节点变 RED → 补救路径给出前置学习 + 行动入口 → 交互模拟预测 / 编程实验重新验证 → JUDGE/ATTEMPT 新证据 → 状态回升 → 时间线回放全程。教师端聚合班级薄弱热力。

---

## 2. 总体架构

```text
浏览器（Vue 3 + Arco Design，:8080/:8081 dev / :80 nginx）
   │  同源 /api（devServer 代理或 nginx 反代）
   ▼
Spring Boot 2.7.2 单体（:8121，context-path=/api）
   ├── MySQL 8.x    业务库 oslab_db（用户/题目/提交/learning 域 8 表）
   ├── Redis        会话（Spring Session，30 天）+ 缓存
   ├── Neo4j 4.4    知识图谱（:Entity 150 节点/171 边，章节/类别/依赖关系）
   ├── 火山方舟 SDK → DeepSeek   GraphRAG 导师 / 全局 AI 助手（可降级）
   └── 代码沙箱（判题）
        本地开发：scripts/dev/sandbox_stub.py（:8090 回显协议）
        Docker：隔离容器沙箱（每提交一个无网络 Worker，Java/C/C++/Go）
```

两种运行形态：

| 形态 | 中间件 | 判题 | 入口 |
| --- | --- | --- | --- |
| **本地原生**（日常开发） | Windows 服务：MySQL/Redis/Neo4j（Neo4j 本地免认证，启动脚本注入口令 11111111） | 回显 stub（:8090，协议与真实沙箱一致） | `start-oslab.ps1` / `stop-oslab.ps1` |
| **Docker Compose**（交付形态） | 全容器化，密码走 `deploy/.env` | 真实隔离沙箱容器（挂 docker.sock 创建 Worker） | `deploy/`，仅 frontend 暴露 80 |

---

## 3. 技术栈与版本

| 层 | 技术 | 版本/说明 |
| --- | --- | --- |
| 前端 | Vue / vue-router / Vuex | 3.2 / 4.x / 4.x（Options + `<script setup>` 混用） |
| | Arco Design Vue | 2.56（全量引入；色阶经 `learning-theme.css` 桥接为 OSLab 蓝） |
| | TypeScript / vue-cli | 4.5 / 5.0（**无单测框架**，裸 Node 测试见 §12） |
| | echarts / monaco / bytemd | 5.6（图谱）/ 0.50（代码编辑器）/ markdown 渲染 + rehype-sanitize |
| | OpenAPI 生成客户端 | `generated/`（openapi-typescript-codegen, axios）+ 手写 `src/api/learning.ts`，**双约定并存**（§9.2） |
| 后端 | Spring Boot / Java | 2.7.2 / pom 声明 1.8 但编译 target 14（实际按 14 编译） |
| | MyBatis-Plus | 3.5.2；`map-underscore-to-camel-case: false`——**数据库列名是驼峰**（userId/reviewStatus），勿引入蛇形 |
| | Neo4j | OGM 3.2 + 原生 driver（快照走原生 Cypher） |
| | 会话 | spring-session-data-redis，key `user_login` |
| | AI | volcengine ark sdk（deepseek-v3-250324），key 全走环境变量 |
| | 判题 | 无本地执行依赖；`RemoteCodeSandbox` POST 远程 HTTP（header `auth` 鉴权） |
| 测试 | JUnit5 + Mockito；裸 Node 脚本 | 后端 175 测试（其中 6 个 legacy 已隔离）；前端 3 个裸测 |
| 部署 | vue-cli build + nginx / docker-compose | 沙箱容器挂 docker.sock、无网络 Worker（§13.3） |

---

## 4. 仓库结构导览

```text
R = 仓库根
├── 前端代码/osLab-frontend/        F/  Vue3 应用 + generated 客户端 + tests/ 裸测
├── 后端代码/osLab-backend/         B/  Spring Boot（J = .../springbootinit, T = src/test）
├── 数据库文件/                     基础 dump、v2-v5 升级 SQL、双内容种子、图谱章节 cypher、存量库增量脚本
├── deploy/                         docker-compose.yml、mysql-init/、neo4j-init/、.env.example、README
├── scripts/dev/                    verify-learning.ps1（统一验收）、check-ui-terminology.ps1、
│                                   sandbox_stub.py、prepare-demo-accounts.ps1、samples/（真实沙箱 C 样例）
├── scripts/smoke-learning.ps1      verify 的薄入口（兼容旧名）
├── start/stop-oslab.ps1            本地一键启停
├── docs/                           全部计划、审计、交付记录、最终验收报告、人工审核清单
├── artifacts/final-check/          验收运行产物（RunKey 命名的 JSON/清单）
├── design/                         登录页与前端视觉基准（OpenDesign 流程产物）
└── 代码沙箱/                       真实沙箱 server.py/runner.py/Dockerfile（compose 构建）
```

关键源码索引（接手首选阅读顺序）：

| 文件 | 内容 |
| --- | --- |
| `B/.../service/impl/LearningEventServiceImpl.java` | **事件账本核心**：三条写入路径、幂等、观测规则、条纹锁、BKT 更新（§6） |
| `B/.../service/impl/LearningQuestionServiceImpl.java` | 四题型判分、审核状态机、studyState 过滤、章节共享谓词、目录聚合 |
| `B/.../service/impl/LearningMasteryServiceImpl.java` | 个人图谱/节点工作台/时间线/快照回放聚合 |
| `B/.../service/impl/LearningPathServiceImpl.java` | 补救路径（多前置闭包 + Tarjan 检环 + Kahn 拓扑） |
| `B/.../learning/` | 纯函数域：BktModel、CpuScheduler、PageReplacer、LearningQuestionGrader、LearningAttributionService、FailureStateClassifier、MasterySnapshotCodec、LearningResourceResolver |
| `B/.../judge/JudgeServiceImpl.java` | 异步判题（CAS 抢占 + 终态挂钩事件账本） |
| `F/src/api/learning.ts` | 学习域全部前端 API（AxiosResponse 约定）+ 类型 |
| `F/src/router/routes.ts` + `F/src/navigation/appNavigation.ts` | 路由表（15 条旧深链接重定向）、导航模型、meta（navKey/hideSidebar/contentWidth） |
| `F/src/components/KnowledgeGraph.vue` | 图谱画布（§9.5） |
| `F/src/views/learning/NodeLearningWorkspaceView.vue` | 节点工作台四页签 + 常驻导师 |

---

## 5. 数据模型

### 5.1 MySQL `oslab_db`

**底座表**（2025 基础 dump）：`user`（含 userAvatar/userProfile；userAccount 唯一索引 uk_userAccount）、`students`（名册：studentNumber/name/className/userId → 教师班级归属的唯一事实源）、`question`（编程实验题：judgeCase/judgeConfig/tags JSON）、`question_sets/question_set_map`（实验按章节集组织）、`question_submit`（提交与判题结果）。

**learning 域 8 表**（v3 契约 6 表 + v4/v5 增量）：

| 表 | 作用 | 关键字段/约束 |
| --- | --- | --- |
| `learning_question` | 概念题（与编程题分表） | type(JUDGE/SINGLE/MULTIPLE/FILL_BLANK)、options/answer(JSON，**answer 仅服务端**)、analysis、misconception、chapter、difficulty、reviewStatus(DRAFT→SUBMITTED→APPROVED→PUBLISHED→OFFLINE，REJECTED 分支) |
| `learning_question_node` | 题-节点映射 | nodeName（=Neo4j EntityNode.name，业务键）、weight(0-1 掌握度权重)、uk(questionId,nodeName) |
| `learning_attempt` | 概念题作答 | submittedAnswer、isCorrect、isFirst（独立观测标记）、hasViewedAnalysis、assessmentRunId、submissionKey + uk(userId,submissionKey)（提交幂等） |
| `learning_event` | **不可变事件账本** | eventType(ATTEMPT/JUDGE/SIMULATION/STATE_BASELINE)、result(PASS/FAIL/SYSTEM_ERROR/BASELINE)、resourceType/Id、isFirst、masteryBefore/After、evidence(JSON 归因)、**idempotencyKey 唯一**、snapshotBefore/After(7 字段 JSON)、eventGroupKey（attempt:<id>:<node> / judge:<submitId>:<node> / sim:<uid>:<key>:<node>，一次学习事务影响多节点共享组键） |
| `learning_mastery` | 学生-节点掌握状态 | uk(userId,nodeName)、pKnown、confidence、conceptScore/practiceScore（双分独立 BKT）、conceptEvidenceCount/practiceEvidenceCount、status(GRAY/RED/YELLOW/GREEN)、lastEventId |
| `learning_review` | 审核流水 | action(SUBMIT/APPROVE/REJECT/PUBLISH/OFFLINE)、reason（驳回必填）、reviewerId |
| `learning_assessment_run` | 测评会话 | questionIdsJson（**服务端固定队列**）、status(ACTIVE/FINISHED)、chapter/nodeName（可空） |
| `learning_node_content` | 节点共享素材 | nodeName 唯一、contentMd(MEDIUMTEXT)、resourceLinks(JSON)、status(DRAFT/PUBLISHED/OFFLINE) |

列名风格：**驼峰**（`map-underscore-to-camel-case: false` + 手写 SQL 用驼峰）。任何新测试若手工初始化 TableInfo，必须 `new MybatisConfiguration()` 后立即 `setMapUnderscoreToCamelCase(false)`——否则蛇形列名污染 MP 全 JVM 静态缓存，集成测试**只在全量跑时**炸（§12 地雷）。

### 5.2 Neo4j

- `(:Entity {name, label, description, chapter})`，150 节点/171 边；`name` 是业务键（前端路由参数、映射表外键）。
- 关系四种：`dependent`（**A dependent→ B 表示 A 依赖 B，学习顺序先 B 后 A**——唯一参与路径算法的关系）、`contain`、`related`、`implements`（历史数据存在拼写 `implemente`，显示层归一化兼容，不迁移）。
- `label` = 课程类别（概念/算法/功能…）→ 节点底色；`chapter` = 章节（硬件管理 14/内存管理 45/进程管理 29/中断与系统调用 27/文件系统 23/操作系统概述 6/未分类 6）。
- 全量快照：`KnowledgeGraphService.getGraphSnapshot()` 单条 Cypher 取全图（含孤立节点、边按三元组去重）——个人图谱/工作台/路径/教师图谱的统一底图。
- 本地补齐章节：`数据库文件/oslab_graph_chapters.cypher`（144 节点精确映射，幂等；6 个节点有意未分类）。

---

## 6. 学习闭环的核心实现（最重要一章）

数据流：**作答/判题/预测 → 规则归因 → 事件账本 → BKT 更新 → 图谱状态与推荐 → 补救行动**。

### 6.1 事件账本（LearningEventServiceImpl）

三条写入路径，全部幂等（idempotencyKey 唯一索引 + DuplicateKeyException 捕获）：

| 路径 | 触发 | 幂等键 | 权重 |
| --- | --- | --- | --- |
| `recordAttemptEvents` | 概念题作答 | `attempt:<attemptId>:<nodeName>` | 题-节点映射 weight |
| `recordJudgeEvents` | 编程提交终态 | `judge:<submitId>:<nodeName>` | question.tags JSON 精确映射（不再 LIKE） |
| `recordSimulationEvent` | 模拟预测 | `sim:<userId>:<md5(配置)>:<nodeName>` | 单节点 |

关键规则：

1. **独立观测 vs 行为记录分离**：只有 `isFirst=1` 的 ATTEMPT、result 非 SYSTEM_ERROR 的 JUDGE/SIMULATION 更新模型；重复作答**照记事件**但 before==after；SYSTEM_ERROR/CE 走"冻结"路径（诊断可见，模型零变化，isFirst=0 双保险）。
2. **幂等守卫先于落库**：掌握行读改写在幂等检查**之后**（历史上曾在守卫前原地改写导致重复回调增量，已修，测试固化）。
3. **并发控制**：64 条纹锁（hash(userId+nodeName)）串行读改写——异步判题与并发作答不会互相覆盖。
4. **不可变 + 快照**：事件插入即带 masteryBefore/After 与完整 7 字段快照（pKnown, confidence, conceptScore, practiceScore, conceptEvidenceCount, practiceEvidenceCount, status）；同事务多节点共享 eventGroupKey（回放不出现"半更新"状态）。
5. **基线补偿**：发现无快照的历史事件时自动补一条 `STATE_BASELINE`（不算学习观测、不计证据）。
6. 判题失败不影响事件链：`recordJudgeEvents` 编程式事务、异常被 JudgeServiceImpl 捕获，**绝不影响判题主流程**。

### 6.2 BKT 掌握度（bkt/BktModel + 事件更新）

```text
5 参：pInit=0.10, pLearn=0.10, pGuess=0.20, pSlip=0.10（未做数据校准）
update(pKnown, correct, pLearn, pGuess, pSlip)：
  correct:   posterior = p*(1-pSlip) / (p*(1-pSlip) + (1-p)*pGuess)
  wrong:     posterior = p*pSlip     / (p*pSlip     + (1-p)*(1-pGuess))
  updated   = posterior + (1-posterior)*pLearn      ← 学习转移对对/错都生效（教科书）
事件层：after = before + mappingWeight * (updated - before)
```

- 首次答错（权重 1，新节点）：0.10 → ≈ **0.1123288**，状态 RED。**这是契约**（语义裁决 L3-R12；验收脚本按公式断言，无"答错必降"断言）。
- **双分独立更新**：conceptScore 只吃 ATTEMPT，practiceScore 只吃 JUDGE/SIMULATION；pKnown 综合两者。
- 可信度 = 去重来源数 × 0.25（封顶 1）。
- 状态机：GRAY（无记录）→ RED <0.35 → YELLOW → **GREEN ≥0.70 且概念/实践两类证据都有**；`MasteryStatusEnum.derive` 统一推导，无记录显示"尚未测评"，不把 null 当 0%。
- "已纠正这道题" ≠ "已掌握知识点"：复习成功关闭该题待巩固状态，但不伪造独立观测刷绿节点。

### 6.3 归因与失败状态（learning/ 包纯函数）

- `LearningAttributionService.attribute(eventType, judgeMessage, nodeName)`：WA/RE/TLE/编译/系统错误 → errorType、confidence（按类型固定值，展示为"关联明确/需进一步确认"，不伪装成校准概率）、recommendedAction、knowledgeImpact（**系统错误不计知识失误**）。CE 判定按消息 value/text/关键字**三重匹配**（判题链路传英文 "Compile Error"，历史上曾只匹配中文枚举值导致 CE 被当 FAIL 计分——已修并有测试）。
- `FailureStateClassifier.classify(events)`：CURRENT/HISTORICAL/CORRECTED/NONE——"已纠正"由**同一资源的后续有效通过**确认；工作台/记录页据此区分当前失误与历史失误。

### 6.4 补救路径（LearningPathServiceImpl）

1. 图谱快照 → 仅取 `dependent` 边建**多前置邻接表**（A→B 表示先学 B）。
2. BFS 求前置闭包 → Tarjan SCC（显式栈）检环 → 环上节点追加路径末尾并提示管理员（不递归死循环、不删真实边）。
3. Kahn 拓扑排序，优先队列：GRAY<RED<YELLOW<GREEN，同级 pKnown 升序（最薄弱先学）。
4. 每项给 reason + **真实可执行 action**（kind: ASSESSMENT/SIMULATION/EXPERIMENT/NODE；资源解析唯一入口 `LearningResourceResolver`：实验 tags JSON 精确匹配、"调度/置换"→ 对应模拟器，**绝不伪造 resourceId**）。
5. basisEventIds：薄弱节点最近事件（≤10）——推荐可解释。

### 6.5 概念题域（LearningQuestionServiceImpl）

- **判分** `LearningQuestionGrader.grade`：JUDGE 规范化比较、SINGLE 精确、MULTIPLE 集合相等、FILL_BLANK 多可接受答案（trim/合并空白/大小写不敏感）。
- **审核状态机**：DRAFT→(SUBMIT 作者)→SUBMITTED→(APPROVE)→APPROVED→(PUBLISH)→PUBLISHED→(OFFLINE)；SUBMITTED→REJECT(必填 reason)→REJECTED；改题仅限 DRAFT/REJECTED/OFFLINE 且改后回 DRAFT；每次流转写 learning_review。JSON 导入：预览逐项校验 → commit 仅落 DRAFT（走真实审核）。
- **VO 隔离**：学生 VO 永无 answer（copyProperties 后先清空 analysis/misconception，answer 不复制）；管理端独立接口 `/learning/questions/manage/get|list/page`。
- **studyState 过滤**（ALL/UNANSWERED/NEEDS_REVIEW/CORRECT）：按本人"最新有效作答"判定，服务端过滤后分页（非前端页内过滤）。
- **章节共享谓词**：`chapterKey/matchesChapter` 把 null/空白/字面"未归类"视为同一章节——目录统计、学生分页、测评候选**三处同一实现**（杜绝三套语义）。
- **测评会话**：`assessment/start` 取全量 eligible（**无"前 50 条"上限**，确定性等距抽样）→ 服务端固定队列 questionIdsJson → run/run/finish；归属校验（学生只能读写自己的 run）。
- **目录聚合** `POST /learning/questions/catalog`：章节→知识点→{questionCount, attempted, needsReview, correct}，按 questionId 去重，3 次查询内存聚合。

### 6.6 模拟器（learning/ 纯函数 + 服务端判分）

- `PageReplacer`（FIFO/LRU/OPT）与 `CpuScheduler`（FCFS/非抢占 SJF/RR）：确定性实现，逐步输出帧/甘特。
- 预测**一律服务端重算判分**（前端上报的正确性不被采信）；带 nodeName 时按 md5(归一化配置) 幂等写 SIMULATION 事件；**对比性运行（如 OPT 对比）不带 nodeName，不写事件**——前端只有 `prediction.eventId` 存在才显示"已计入学习证据"。
- 前端 runConfig 冻结：运行时深拷贝输入，改输入即标记"需重新运行"并禁用预测提交（禁止新输入配旧结果）。

### 6.7 GraphRAG 导师与全局 AI 助手

- 节点导师 `POST /api/learning/tutor/ask` `{nodeName, question}` → LearningTutorServiceImpl：语料 = 种子节点邻域（≤4）+ 已发布题解析 + 实验题 + **本人**学习事件（他人事件绝不入证据包）；**意图分组配额**保证本人失败记录与"下一步动作"不被邻居/题目挤掉；来源带 `actionType`(NODE/QUESTION/EXPERIMENT/EVENT/RESOURCE) + targetId/targetUrl（前端可跳转）；Prompt 约束"先提示定位再解释，不输出完整可提交代码"；无 AI key 自动 FALLBACK（规则化来源 + 下一步，仍可用）。限流 6 次/分。
- 实验页"向导师提问"= 同一 tutorAsk，nodeName 取 路由 query → 实验题首个映射节点 → 皆无则禁用并说明；实验上下文（标题/语言/代码片段/最近判题）打包进 question 文本。
- 全局 AI 助手 = `/api/chat/ask`（单字符串进出，限流 10/分），前端把显式注册的页面上下文（useAiPageContext：图谱/工作台/实验页 mount 注册）+ 最近 8 条消息序列化为一条请求；按用户隔离 localStorage；**不宣称 GraphRAG**。限流 10 次/分。

### 6.8 教师聚合（LearningTeacherServiceImpl，全部 @AuthCheck admin）

- 班级归属唯一事实源 = `students` 表（userId/className），不硬编码班级。
- dashboard：薄弱点 Top、节点热力、题目正确率——**首次正确率分母 = 首次有效作答数**；实验通过率按 questionSubmitId 去重（一提交映射多节点只算一次）；无样本显示 null（"暂无样本"），不显示 0%。
- node/detail 通过映射表精确匹配题目（不用标题 contains）；timeline 按 className 约束成员（指定 userId 须属该班）。
- 前端班级热力图谱 = 公开拓扑快照 + nodeHeat 聚合四色（0.35/0.7 与列表一致），只读（管理按钮在 /teaching/knowledge）。

---

## 7. 判题与沙箱

```text
提交 → QuestionSubmitServiceImpl.doQuestionSubmit
  校验语言/限流(3 次/分) → 落库 WAITING → submitNum+1
  → CompletableFuture.runAsync(judgeService.doJudge)     ← 无 @EnableAsync，裸 ForkJoinPool
doJudge:
  CAS UPDATE WAITING→RUNNING（失败即抛"已进入判题流程"，防并发重复判题/重复计数）
  → 读 judgeCase/judgeConfig（默认 1000ms/256MB）
  → CodeSandboxProxy(工厂).executeCode(request)          ← 工厂只接受 "remote"
  → 沙箱失败(编译/运行错)直接落 judgeInfo
  → 否则 JudgeManager.doJudge（固定 DefaultJudgeStrategy，对照用例输出）
  → ACCEPTED 时 acceptedNum+1；终态 SUCCEED/FAILED
  → learningEventService.recordJudgeEvents（幂等；SYSTEM_ERROR 冻结）
  → 全链路 catch：基础设施异常 → SYSTEM_ERROR + FAILED，同样写事件
```

- **绝不静默判对**：沙箱未配置/不可达直接 SYSTEM_ERROR，不伪造结果。
- 真实沙箱协议（RemoteCodeSandbox）：POST `${CODESANDBOX_REMOTE_URL}`（header `auth: secret`）→ `{code, language, inputList/expectedList...}` → 输出对照。本地 stub（sandbox_stub.py）回显 inputList，使判题策略层真实产生 Accepted/WA（CE/RE/TLE 只有真实沙箱能产生）。
- 真实样例：`scripts/dev/samples/`（lru_correct/lru_wrong/compile_error/runtime_error/timeout.c）。
- 判题结果语义：**status=2 只表示"判题过程结束"，是否 AC 必须再看 judgeInfo.message**。

---

## 8. 前端实现细节

### 8.1 路由、导航与权限

- `routes.ts`：三布局壳（UserLayout 登录注册 / LearningLayout 学生 / TeachingLayout 管理员）+ **15 条旧深链接重定向**（/learn、/question、/view/question/:id、/question_submit、/learning/* 等，带 query/params）。
- 路由 meta 契约：`navKey`（顶栏选中态，详情页保持所属入口高亮）、`hideSidebar: true`（题目练习等页关闭课程侧栏，主内容占满）、`contentWidth: "wide"`（图谱/实验中心容器放宽至 1760px）。
- 权限：`access/index.ts` 全局 beforeEach——未取到登录态先 `store.dispatch("user/getLoginUser")`（后端不可达 catch 住按未登录处理，防白屏）；需要登录→ `/user/login?redirect=`；权限不足 → `/noAuth`（正式无权限页）。学生 ROLE_HOME=/graph，管理员=/teaching/overview。
- 导航双源：实际重定向在 routes.ts；`appNavigation.ts` 持有 APP_NAVIGATION（学生五入口+管理员教学管理）/AUXILIARY_NAVIGATION（学习记录走头像菜单）/ROLE_HOME/resolveNavKey。**改路由两处必须对齐**。

### 8.2 双 API 读取约定（最高频地雷）

| 层 | 返回 | 判成功 |
| --- | --- | --- |
| 手写 `src/api/learning.ts` | `AxiosResponse<BaseResponse<T>>` | `res.data.code === 0`，数据 `res.data.data` |
| `generated/services/*` | BaseResponse 体 | `res.code === 0`，数据 `res.data` |
| 全局 axios 拦截器 | 业务错误**不 reject**，原样返回 | 调用方必须自己判 code（漏判 = 失败当成功渲染）；40100 → 跳登录 |

### 8.3 图谱画布（KnowledgeGraph.vue）

- 数据：结构来自 `GET /knowledge_graph/getAll` 快照；个人状态叠加 `mastery/graph`；`nodeScaleRatio: 0` —— **固定屏幕节点尺寸**（符号不随 zoom 缩放，标签永远贴进符号）。
- 布局：章节播种力导向（章节锚点半径 260+45×n 环绕 + 章节内黄金角螺旋 26√k；force repulsion 1100/edgeLength 200/gravity 0.05，layoutAnimation 开启）——坐标只写进传给 ECharts 的派生对象，**绝不写回 props 数据、不设 fixed**。
- 视野：两个自定义 action——`oslabGraphFit`（通过 view 坐标系 setCenter/setZoom 实现适配，center 为节点包围盒中心的**图坐标**——series.center 不是容器像素）与 `oslabGraphStyle`（updateVisual 级样式刷新，保留布局与视野）；重建后安排一次 **autoFit**（力导向异步收敛后执行 `fitCurrentLayout`），用户一旦拖动/缩放（graphRoam）即取消，尊重手动视野。
- 节点视觉：类别底色（概念蓝/算法琥珀/功能绿/未标注灰）× 掌握状态**左 6px 状态条 + 右上角圆点**（zrender 富文本 token 实现）；选中 = 主蓝描边；pulseMap 驱动状态变化脉冲。
- 边语义：dependent 主蓝实线实心箭头（渲染为 B→A 学习方向，tooltip 注明原始语义"A 依赖 B"）、contain 灰蓝虚线、related 灰点线圆端、implements/implemente 琥珀短划；**边文字仅受"关系标签"开关控制**（选中邻接/悬停不自动弹出）；关系语义兜底走 tooltip。
- 渲染保护：节点去重、悬空边过滤（防 createGraphFromNodeEdge 抛错冻结渲染线程）、rebuild notMerge / 纯样式 merge、ResizeObserver。`tests/knowledge-graph-render.js`（7 场景，含零尺寸恢复/窄视口/动画收敛后适配）是保护网，**改图谱必跑**。

### 8.4 节点工作台与缓存

- 四页签（知识内容/知识详解/题目练习/实践）+ 常驻导师面板（336px，<1024px 折叠底部）。
- `useNodeWorkbench`：按 userId+nodeName 的 LRU 缓存（24 键/30s TTL/in-flight 合并/旧请求守卫）；失效统一走 `window` 事件 **`oslab:learning-changed`**（作答/判题/模拟成功后派发）——新增会改掌握度的操作必须派发它。
- 答题：全宽就地 QuestionAnswerCard（一次展开一道），submissionKey 幂等；`tab=resources` 是"知识详解"稳定参数（懒加载 iframe，多资源 chip 切换；教师外链一律新窗口，不进 iframe）。

### 8.5 设计系统与身份

- `src/styles/learning-theme.css`：全站 token（画布 #f7f7f5、面板白、主蓝 #2059c9、四状态色及 soft 变体、4px 间距、圆角 8/6/999、150-200ms 缓动）+ Arco 色阶桥接 + 46 处旧变量兼容别名 + 通用状态类（.oslab-empty/.oslab-loading/.oslab-error/.oslab-status-tag/.oslab-page-title 等）。**新代码只用新 token**；禁止渐变/发光/玻璃拟态。
- 身份：`store/user.ts` —— `getLoginUser`（带请求序号守卫）、`updateUser`（五字段整体替换）、`resetUser`（失效/登出完整清空）；头像渲染统一 `components/UserAvatar.vue` + `utils/userPresentation.ts`（trim→真实昵称→空白"用户"→首字素→"用"；失败键 = userId+userAvatar）。LoginUserVO **没有 userAccount 字段**，不要拿昵称当账号。

### 8.6 全局 AI 助手

`components/assistant/`（FloatingAiAssistant 单例 Teleport + AiChatPanel + useAiConversation/useAiPageContext）：Pointer Events 拖动（4px 阈值区分点击）、clamp 回视口、按用户隔离 localStorage（会话+位置，未放置不持久化面板坐标）、Esc/清空/重试、MdViewer 安全渲染；登录/注册/noAuth 隐藏；命名"AI 助手"（与导师区分）；上下文 = 三页显式注册的小对象（含实验页 lastJudgeResult），**禁止 DOM 全量抓取**。

---

## 9. 后端横切设施

| 设施 | 实现 |
| --- | --- |
| 统一返回 | BaseResponse{code,data,message} + ErrorCode；GlobalExceptionHandler 捕获 BusinessException/RuntimeException |
| 权限 | `@AuthCheck(role)` 方法级 AOP（**不支持类级**——教师控制器逐方法标注）；登录态从 Redis session 取，学习域接口统一 `userService.getLoginUser(request)`，**不接受前端传 userId**（越权面归零） |
| 限流 | 自研窗口计数 RateLimiter：判题 3/分、节点导师 6/分、全局助手与通用问答 10/分 |
| ID 序列化 | Jackson 全局 Long→String（防前端精度丢失）；前端 ID 一律按字符串比较/透传 |
| 事务 | 判题事件用编程式事务包裹多节点写入；掌握行更新在幂等检查之后落库 |
| 文档 | knife4j `/api/doc.html`（prod 默认关） |
| 遗留模块 | Post/ES/COS/微信公众号/Excel 导入为 OJ 模板遗留（与学习平台并存但基本无引用）；遗留测试 @Tag("legacy") 由 `-Plegacy` 单独跑 |

---

## 10. 测试与验收体系

### 10.1 后端（`./mvnw.cmd test`）

- 产品套件 **169/0/0/1**：BktModelTest、LearningQuestionGraderTest、PageReplacerTest（OPT 对照）、CpuSchedulerTest、LearningAttributionServiceTest、FailureStateClassifierTest、MasterySnapshotCodecTest、LearningEventServiceTest、**LearningEventObservationTest**（CE 观测分离/幂等零突变/教科书 BKT 值 0.1123±0.001）、LearningQuestionCatalogTest、LearningChapterPredicateTest、LearningAssessmentCandidatePoolTest（61 题候选）、LearningQuestionProgressTest、LearningQuestionAssessmentTest、LearningPathServiceTest、LearningMasteryServiceTest、LearningRecordsServiceTest、LearningTeacherServiceTest、LearningTutorServiceTest、LearningWorkbenchQueryTest、KnowledgeGraphChapterTest 等。
- legacy 组：Post*/CosManagerTest 6 方法（缺表/缺云凭据），`-Plegacy` 单独跑，默认排除。
- **纪律：永远跑全量**——Mockito 测试手工初始化 MP TableInfo 的静态缓存污染只在全量执行时暴露。

### 10.2 前端（无测试框架，裸 Node 脚本）

```powershell
npx vue-cli-service lint --no-fix        # 0 errors（60 warnings 为遗留 any/unused）
node tests/user-identity.js              # 身份回退/清空/迟到守卫
node tests/node-workbench-cache.js       # 工作台缓存
node tests/knowledge-graph-render.js     # 图谱渲染 7 场景（改图谱必跑）
npm run build
```

### 10.3 全链路验收

```powershell
python scripts/dev/sandbox_stub.py       # 回显沙箱 :8090（secret smoke-secret）
# 后端以 CODESANDBOX_TYPE=remote → http://127.0.0.1:8090/executeCode 启动
powershell -File scripts/dev/verify-learning.ps1
# 参数：BaseUrl/账号/口令(参数或 OSLAB_DEMO_PASSWORD)/ClassName/JudgeMode(Stub|Real|None)/RunKey/ReportPath
# 逐请求断言业务 code；失败/异常 exit 1；结果写 artifacts/final-check/<RunKey>.json/.txt
# 基准：PASS=80 FAIL=0 SKIP=7（SKIP 为 Real-only CE 样例等如实标注）
```

- 断言组：账号与权限、图谱快照、审核链、四题型判分与答案隔离、submissionKey 幂等、BKT **公式对照**（无"答错必降"断言——契约见 §6.2）、工作台归因、教师端权限/下钻、模拟预测事件、WA/AC 追溯提交 ID、快照回读。
- 数据标记用 RunKey（**不复位环境**）；口令走参数/环境变量。
- 辅助脚本：`prepare-demo-accounts.ps1 -DemoPassword <口令>`（幂等账号+名册绑定+种子节点名核对）；`check-ui-terminology.ps1`（旧术语"普通题"扫描，0 未豁免命中为过，历史文档命中按规则豁免）。

### 10.4 人工验收

`docs/最终人工审核清单.md`（34 项，覆盖顶栏身份/图谱回归点/工作台/测评/实验/记录/教师/助手/术语）。

---

## 11. 部署与环境

### 11.1 本地原生（开发态）

- `start-oslab.ps1`：确保 Windows 服务（MySQL/neo4j/Redis）→ 起后端 jar（注入 Neo4j 口令 11111111、CODESANDBOX remote → stub）→ 起前端 dev（8080 被占自动 8081）；`stop-oslab.ps1` 按**命令行特征**杀进程（勿按端口杀——8080 常被无关进程占用）。
- 本地 Neo4j 免认证（conf 曾因口令遗失切 `auth_enabled=false`，备份在 conf.bak-auth）；Docker 路径仍走认证。
- 演示账号：teacher_demo(admin)/student_demo1/2（密码 oslab123456）；`prepare-demo-accounts.ps1` 幂等准备。

### 11.2 Docker Compose（交付态）

- 仅 frontend 暴露 80（`OSLAB_HTTP_PORT` 可改）；全部密码从 `deploy/.env`（缺失拒启）。
- MySQL 首启由 `mysql-init/10-load-oslab-db.sh` 按序导入 **基础→v3→v4→v5→v3 种子→v4 内容种子**，尾部 9 项契约校验失败即停；已有卷用 `数据库文件/oslab_upgrade_existing_check.sql`（utf8mb4 客户端）做幂等增量（重复账号需先处置）。
- Neo4j：一次性 `neo4j-seed` 服务恢复 graph.db.dump（防覆盖标记机制，4.4 只认 `neo4j-admin load` 语法）。
- 沙箱：独立网络、仅 backend 可达、挂 docker.sock 为每次提交创建**无网络/非 root/只读根/降权/限时**的 Worker 容器（Java/C/C++/Go）。
- ES 仅服务遗留 Post 搜索。

### 11.3 线上

39.106.136.15（裸机精简栈）——**只读检查**（首页 200；/api/health/liveness 曾返回 500，待排查）；不在现网跑写数据的验收脚本。

---

## 12. 新人地雷清单（踩过的坑，改码前通读）

1. **双 API 约定**：learning.ts 读 `res.data.code`，generated 读 `res.code`；拦截器不 reject 业务错误，漏判 code = 失败当成功。
2. **MP 列名是驼峰**：新测试手工初始化 TableInfo 必须 `setMapUnderscoreToCamelCase(false)`，否则蛇形列名污染静态缓存——**只有全量测试才暴露**。
3. **`series.center` 是图坐标**（节点 x/y 空间，不是容器像素）；图谱视野适配用"包围盒中心图坐标 + fit zoom"。坐标**绝不写回节点数据、不设 fixed、不关 layoutAnimation**（O(n²) 卡死）。
4. **BKT 契约**：答错可轻微上升（0.10→0.1123）是教科书行为；不要加"答错必降"断言或纯后验实现（已有裁决与测试）。
5. ** nodeName 一切外键**：路由参数已解码（勿二次 decode）；题目映射/素材/缓存键全用它。
6. **缓存失效**：改掌握度的操作派发 `oslab:learning-changed`；工作台缓存键含 userId。
7. **答案隔离**：学生 VO 无 answer/correctAnswer；解析仅本人作答后下发；新接口照此办理。
8. **CE 判定**：判题消息是英文 "Compile Error"，匹配必须 value/text/关键字三重。
9. **dayjs 是幽灵依赖**（UserCenterView 直接 import 但不在 package.json）；ManageQuestionView 用 querystring/moment 旧风格。
10. **ps 脚本 UTF-8 BOM**；MySQL 客户端执行含中文存储过程的 SQL 用 `--default-character-set=utf8mb4`。
11. **功率边界**：8080 端口常有无关进程，杀 OSLab 进程按命令行特征（stop-oslab.ps1）。
12. **术语红线**：用户可见文案不得出现"普通题"（check-ui-terminology.ps1 会扫源码字面量与文档）。
13. **历史文档**（docs/phase3-delivery.md 等）保留原样不重写；术语脚本对其有文件级豁免规则。

---

## 13. 演进史（理解现状的来龙去脉）

| 时间 | 里程碑 | 交付记录 |
| --- | --- | --- |
| 09-29 | 一阶段：P0 契约冻结（14 API/6 表/5 参 BKT）、闭环原型 40/40 冒烟 | docs/learning-contract-audit.md、验收记录-2026-09-29.md |
| 09-30 | 二阶段：图谱快照对齐（150/171）、动态工作台、规则归因、统一验收脚本 | docs/learning-phase2-audit.md |
| 10-01~02 | 三阶段：单题测评会话、VO 分离、补救路径、教学管理、术语统一、UI 收口 | docs/phase3-baseline/delivery |
| 10-03 | log.txt 导航重构（首页/学生默认进图谱）、144 节点章节补齐、工作台提速 | docs/log-ui-delivery-2026-10-03.md |
| 10-04 | log2 OpenDesign 重构：token 全站、图谱关系语义、工作台三页签、题目练习学习流（新增 catalog API）、实验导师、模拟 runConfig、教师看板 | docs/log2-OpenDesign交付记录-2026-10-04.md |
| 10-05 | log3 收尾：图谱反模糊与 wide、知识详解页签、实验三页签（Tutor 迁移）、全局 AI 助手、契约修正（谓词/候选池/CE 观测/Tutor 配额）、时间线回放、教师热力图谱 | docs/log3-收尾交付记录-2026-10-05.md |
| 10-06 | 最终检查：身份统一、脚本重写（参数化/退出码/BKT 公式断言）、DB 初始化补齐、legacy 测试隔离、全站按钮/路由审计 | docs/最终验收报告-2026-10-06.md、最终人工审核清单.md |

关键裁决：**L3-R12**（教科书 BKT 含学习转移是契约）；**FC-R1**（图谱以固定屏幕尺寸+自定义 action 的现行实现为准）。

---

## 14. 已知限制与待办

1. 真实沙箱判题与全新 Docker 卷初始化**未在真机验证**（本机无 docker；样例/Real 模式/加载器契约校验就绪）。
2. 线上 /api/health/liveness 500 待排查；线上版本落后于本分支（未部署）。
3. 前端无组件测试框架（vue-cli+TS4.5 限制），以 3 个裸测 + 人工清单替代；60 条 lint warnings 为遗留 any/unused。
4. dayjs 幽灵依赖、5 个未路由死文件（HelloWorld 等）待清理；`/noAuth` 原为占位页已修复。
5. BKT 参数未做真实数据校准；时间衰减/前置传播为方案 P2 未实现。
6. 演示账号口令为演示级约定值；生产部署必须全部替换（compose 走 .env）。

---

## 15. 三十条"一分钟上手指引"

1. 起环境：`start-oslab.ps1` + `python scripts/dev/sandbox_stub.py`。
2. 跑验收：`verify-learning.ps1` → 看 `PASS=80 FAIL=0`。
3. 起前端调试：8081 热更新；后端 8121 改码需重启 jar（`mvnw package -DskipTests`）。
4. 改图谱：只改 `KnowledgeGraph.vue`/`KnowledgeGraphView.vue` → 必跑 `node tests/knowledge-graph-render.js`。
5. 改判分/事件/BKT：改 `learning/` 纯函数 + 对应 ServiceImpl → 必跑后端**全量**测试。
6. 加接口：Controller → Service → VO → learning.ts（res.data.code）→ 若改掌握度记得派发 `oslab:learning-changed`。
7. 加页面：routes.ts（meta: navKey/hideSidebar/contentWidth）+ appNavigation 对齐 + 15 条旧重定向检查。
8. 提交前：前端 lint/build/3 裸测 + 后端全量 + `check-ui-terminology.ps1` + `git diff --check`。
