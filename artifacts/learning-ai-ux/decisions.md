# 决策与跨 Agent 请求台账

规则：追加 / 局部编辑，不整文件覆盖。A 的决策与 B 的「B 基线」小节并存。

## A 决策

### D1-1 契约导出名与落点（冻结）

- 类型：`src/types/tutor.ts`，导出 `TutorScene`、`TutorIntent`、`TutorContextScope`、`TutorAttachment`、`TutorLaunchRequest`、`TUTOR_ATTACHMENT_LIMITS`；
- 入口：`src/composables/useAiLauncher.ts` 新增 `openTutor(request: TutorLaunchRequest)`；
- 兼容：`openAiAssistant()` / `askAiAssistant(text)` 保留，内部转成 `openTutor` 的 `GLOBAL/ASK` 请求，不维护会话；
- 面板单例：`src/components/assistant/FloatingAiAssistant.vue`（App.vue 挂载），监听 `oslab:tutor-open` 与旧事件 `oslab:ai-open` / `oslab:ai-ask`。

理由：B 只依赖 `@/types/tutor` 与 `@/composables/useAiLauncher` 两个落点，不触碰面板内部。

### D1-2 场景/意图/作用域与 focus 分离

`TutorScene/Intent/Scope` 只描述本轮辅导；`AiSessionFocus (LEARN|REVIEW)` 仍是会话目标。后端把 `tutor` 存入轮次上下文 JSON（服务端持久），前端发送前冻结；路由变化只影响下一轮。

### D1-3 后端历史接口形状（冻结）

- `GET /api/learning/ai/sessions?courseCode=&cursor=&limit=20` →
  `{ items: [{sessionId, courseCode, title, status, updatedAt, focus}], nextCursor }`；
  `limit` 上限 50；`cursor` 为不透明字符串（`base64(millis:id)`）；只查当前用户 + 指定课程；标题 = 会话 goal（非空）否则首条用户消息截断 60 字；不调用模型。
- `GET /api/learning/ai/sessions/{id}/turns?before=&limit=20` →
  `{ items: [{turnId, clientRequestId, userMessage, createdAt, contextSummary, turn: AiTurnVO}], nextCursor }`；
  按 `sessionId` 倒序取，返回时按时间正序；`before` = 上一页最旧 turnId；已关闭会话本人可只读；不返回系统提示、密钥或工具内部载荷（`turn.toolTrace` 仅暴露既有 status/summary）。
- 权限行为测试：他人会话 40300/无权限、课程不符不返回。

### D1-4 多轮上下文预算

- 只读同一 user + 同一 session、本轮之前、状态 SUCCEEDED 的轮次；
- 默认最近 6 轮，单轮问答合计截断 1200 字，总预算 6000 字（`ai.turn.history-max-turns` / `history-max-chars` 可配置）；
- 历史作为“对话数据”注入回答提示（明确非指令）；不把历史来源编号当作当前来源；
- 不新增模型调用（历史不计入 plan/answer 的 2 次预算之外）。

### D1-5 帮助守卫接线

- A 定义 `ai/tutor/TutorAssessmentGuardPort`：
  `GuardDecision check(User user, AiLearningSession session, AiCourseContext context, String intent, String message)`；
  `GuardDecision {allowed, publicReason, attemptContextId, assessmentRunId, helpLevel}`；
- 默认实现 `DefaultTutorAssessmentGuard`（`@ConditionalOnMissingBean`）：
  - 非 HINT 一律放行；
  - HINT 且 `attemptContextId`/`assessmentRunId` 均为空 → 拒绝（“提示需要关联有效题目尝试”）；
  - HINT 带 id → 默认放行但标记 `helpLevel=HINT_UNVERIFIED`，并在工具轨迹里如实说明“守卫由默认实现处理”；
- B 的 `TutorAssessmentGuard` 实现 `TutorAssessmentGuardPort` 后自动取代默认实现（Spring 注入优先具体 Bean；若 B 用 `@Primary`/同名 bean 亦可）。B 的签名不一致时由 B 适配，不改 A 的调用点。
- 拒绝时不调用模型、轮次终态 `FAILED`（不可重试），错误信息只含公开原因。

### D1-6 旧本地导师历史

节点页/实验页旧 localStorage 消息不再作为可信服务端会话：
- 不自动上传、不删除；迁移后入口消失，旧数据保留在原 key（不读取、不展示为新会话）；
- 旧后端 `POST /learning/tutor/ask` 接口保留不动；
- 页面内联导师 UI 移除，统一由 `openTutor` 打开全局面板。

### D1-7 面板三态与页面让位

- 侧边停靠：视口 ≥1180px 时右侧 460px（可 400–560px 调整，宽高存 localStorage，按用户 id）；`body[data-tutor-dock="on"]` 给 LearningLayout 让位 padding；
- 覆盖：720–1179px 时浮层 460px 覆盖，不挤压；
- 展开：≥900px 且用户点“展开”时模态 1100px（正文列 760px），焦点陷阱 + Esc；同一会话/草稿/滚动锚点；
- 窄屏 <720px：全屏面板，返回/关闭明确；
- 关闭只收起，不清消息/不取消任务。

### D1-8 模型菜单

只列 `testStatus=PASSED` 或 `active` 的配置（复用现有 `/ai/model-profiles`）；会话 `modelProfileId` 切换走 PATCH session（下一轮生效）；当前轮展示轮次 `usage.model` 真实值；无可用配置时菜单引导 `/settings/models`，不静默替换。

### D1-9 前端历史与请求身份

- 消息身份 = `sessionId + turnId`（用户消息用 `clientRequestId` 关联）；
- 状态命名空间 = `userId + courseCode`（每课程一个 `sessionId` 指针 + drafts）；异步响应校验 `epoch`（用户切换）与 `courseCode/sessionId`；
- 未定请求：相同 `clientRequestId` 重发即恢复；已知 `turnId` 先 GET；终态失败后的“重试”使用新 `clientRequestId` 并标记“第 N 次尝试”，不新增用户气泡；
- 草稿存 `sessionStorage`（`userId+courseCode` 隔离，有限长 4000 字，24h 过期，退出登录清理）。

### D1-10 旧事件兼容

`oslab:ai-ask` 的 `detail.text` 映射为 `openTutor({scene:'GLOBAL', intent:'ASK', delivery:'SEND'})` 的预填 + 立即发送；`oslab:ai-open` 只打开面板。旧调用方逐步迁移，但兼容层不维护会话。

## 待对方处理（B）

1. **Guard 实现**：请让 `TutorAssessmentGuard` 实现 `com.yupi.springbootinit.ai.tutor.TutorAssessmentGuardPort`，在 `intent=HINT`（题目提示）时：
   - 校验 `attemptContextId`/`assessmentRunId` 绑定当前用户与题目；
   - 自测 ACTIVE 中本组题 → `allowed=false` + 公开原因；
   - 允许时先持久化帮助等级（HINT / ANSWER_REVEALED）再返回，`helpLevel` 回填真实值；
   - 若接口签名需要调整，请先更新 `01-共享契约与验收.md` 并在本文件追加说明，A 只改调用适配。
2. **普通尝试接口**：题卡“提示/讲解这题”在调用 `openTutor` 前，请先 `POST /learning/practice/attempts` 拿到 `attemptContextId`，随 `TutorLaunchRequest.attemptContextId` 传入。A 的 `ViewQuestionView` 是旧编程实验页，不建普通尝试。
3. **完成通知**：请按 01 §6 发 `LearningUpdated`（建议 store action / window 事件 `oslab:learning-updated`），A 的导师面板会据此刷新下一步；若你选择其他事件名，请追加到本小节。
4. **AiNextActions 兼容**：A 继续以只读方式消费 `AiNextActions`/`AiNextActionCard`；若你需要改 props，先在此登记。
5. **迁移 v13**：留给你的自测迁移；A 本轮不加表、不改 v11/v12。

## B 基线

核对对象：`LearningAssessmentRunController`、`LearningQuestionServiceImpl`、`LearningEventServiceImpl`、`LearningEvidenceContextService`、`LearningAttempt`、`learning_attempt/learning_event` 既有语义。

### 1. 测评会话（核对时已存在）

- 表 `learning_assessment_run`：`id,userId,chapter,nodeName,questionIdsJson,status,createTime,finishTime`；没有 mode / 版本 / 草稿。
- `POST /learning/assessment/start`：学生口径（PUBLISHED + SHARED + 非 COMPREHENSIVE）取全量 eligible id，再按服务端确定性等距抽样。
- `GET /learning/assessment/run`：只返回本轮作答统计（按 `assessmentRunId` 过滤），不混入终身作答；但会返回 `analysis`/`misconception`。
- `POST /learning/assessment/finish`：忽略客户端 `answeredCount/firstCorrect`（既有测试断言），服务端按本轮 attempt 重算；下线题不计入分母；FINISHED 后再次 finish 报 `OPERATION_ERROR`。
- 旧 run 无 mode 字段，按 `PRACTICE` 处理。

### 2. 逐题作答与证据

- `/learning/questions/answer` 服务端判分；`submissionKey` 同用户幂等；`isFirst` 用 `evaluationIdentity` 终身去重；综合题在携带 runId/单元/活动时直接拒绝。
- v12 证据上下文已存在：帮助等级 NONE→HINT→ANSWER_REVEALED 单向升级，`recordHelp` 用 `REQUIRES_NEW` 先落库。
- 缺口（已在本轮补齐）：`LearningAnswerRequest` 无 `attemptContextId`，Controller 未传给三参方法；没有“ACTIVE 自测中的题”服务端判定。

### 3. 前端现状

- `AssessmentRunView`：立即作答 + 即时反馈 + 结束摘要；无模式、草稿、答题卡、结果页。
- `LearningQuestionsView`：“连续测评”直接 start；`QuestionAnswerCard` 一次展开全部反馈，仅 AI 任务有帮助记录。

### 4. 既有测试约束

- `LearningAssessmentRunControllerTest` 直接 new 控制器并反射注入 4 个字段；PRACTICE 分支不得触碰新依赖（已按此实现）。
- 前端测试是源码契约断言；修改页面时保留目录、nodeCode、studyState、竞态守卫、作答后不重载列表等既有保证。

### 5. B 的落地决定（已实现）

1. `SELF_TEST` 走独立 `learning/SelfTestRunService`，控制器按 `run.mode` 分派；PRACTICE 原样。
2. `SelfTestPolicy` 只依赖 run mapper，供答题服务与 `TutorAssessmentGuard` 共用，避免循环依赖。
3. start 固定 `questionRevisionsJson`；finish 时下线/版本变化题列入 invalid，不换题、不进有效分母。
4. 综合题允许进入 SELF_TEST；交卷标 `PENDING_SELF_ASSESSMENT`，不生成 attempt/事件、不计客观正确率。
5. 草稿 `PUT /learning/assessment/runs/{id}/drafts` + run 级 `rowVersion` 乐观锁；冲突返回 200 + `conflict=true` + 服务端草稿。
6. 交卷用 run 行锁 + 状态检查 + `submissionKey/submissionRowVersion` 幂等；同键不同版本报冲突。
7. 普通练习经 `POST /learning/practice/attempts` 建立证据上下文；`/questions/answer` 新增可选 `attemptContextId`；帮助经 `POST /learning/practice/attempts/{id}/help` 先持久化。
8. `TutorAssessmentGuard` 在 `ai/evidence`，A 的 `TutorAssessmentGuardAdapter` 已适配调用；B 未直接实现 `TutorAssessmentGuardPort`，以避免与 A 的适配层重复。

### 6. 迁移

- A 已分配 **v13**；正式脚本：`数据库文件/oslab_upgrade_v13_learning_selftest.sql`（自测模式/草稿/结果字段 + `learning_assessment_draft`）。未改 v11/v12。
- 会话/轮次历史分页 A 判断不需要 v14。

## 集成阻塞（A 记录，B 处理；2026-11-11 08:44）

- **现象**：全量 `mvnw test` 517 个用例中 50 个 `Failed to load ApplicationContext`，全部指向同一根因；后端无法启动，浏览器实测被阻塞。
- **根因（B 的文件）**：`learning/SelfTestRunService.java:74` 的 `@Resource private LearningQuestionMapper questionMapper;`。`@Resource` 先按字段名注入，容器里已存在名为 `questionMapper` 的 `mapper.QuestionMapper` Bean，类型不匹配：
  `BeanNotOfRequiredTypeException: Bean named 'questionMapper' is expected to be of type LearningQuestionMapper but was actually of type jdk.proxy2.$Proxy163`。
- **建议修法（B 任选其一，A 不改 B 文件）**：
  1. `@Resource(name = "learningQuestionMapper")`；或
  2. 字段改名为 `learningQuestionMapper`（保持类型不变）。
- **验证命令**：
  `JAVA_HOME=C:/Users/one/.jdks/ms-21.0.10 ./mvnw.cmd "-Dmaven.compiler.useIncrementalCompilation=false" "-Dtest=UserServiceTest,LearningMasteryServiceTest" test`
- 修复前 A 只能运行不加载 Spring 容器的纯单元测试（AI 契约 52/52 已通过）；修复后 A 重跑全量并补浏览器验收。

### 更新（08:47，B 已修复注入，剩余为 B 自己的测试夹具）

- 已在 `SelfTestRunService` 改用 `learningQuestionMapper`，Spring 容器恢复加载。
- 全量结果：517 用例，8 failures + 12 errors，全部位于 B 的新测试夹具，A 域无失败：
  - `SelfTestRunServiceTest.setUp:102` × 10：仍反射写入旧字段名 `questionMapper`，需改为 `learningQuestionMapper`；
  - `LearningAssessmentCandidatePoolTest.startBuildsQueueFromFullEligibleIdSetWithoutFiftyCap:96`、`startPassesChapterFilterThroughToEligibleQuery:136`：mock 的 `listEligibleQuestionIds` 未命中新调用，抛“范围内没有可作答的题目”。
- 请 B 修好后通知；A 会以最终全绿结果写入 `validation.md`。

### 更新（09:13，A 侧集成修复，B 无需重复改）

- **CORS PATCH**：`CorsConfig` 的 allowedMethods 缺 `PATCH`，浏览器里“新话题/切换目标/切换模型”等 `PATCH /learning/ai/sessions/{id}` 被 403，前端静默降级为“本地清空”。A 已补 `PATCH`；重启后端后实测新话题会真正关闭旧会话，历史列表显示“已关闭”，只读横幅与禁用输入均生效。
- **题卡 HINT 空草稿**：B 的 `openAssessmentTutor` 以 `HINT + SEND` 打开但不带 draft，A 的面板现补一条中性提示请求，真实轮次已带 `attemptContextId`。
- **首次打开历史**：从 `next-actions` 采用服务端会话时，A 现在同步补载最近 20 轮，避免模型看得到旧轮次而界面只显示本轮。
- **草稿**：修复页面刷新时“未加载→已登录”水合把本标签页草稿误删的问题；刷新后草稿保留。
- `SelfTestRunServiceTest` 已于 09:04 修复；`LearningAssessmentCandidatePoolTest` 已于 09:20 修复（三参 mock），最新全量：519 用例、8 failures、0 errors，剩余 8 个为共享种子数据依赖，非 A 域。
  - 精确原因（已修）：控制器改为 `listEligibleQuestionIds(query, user, selfTest)` 三参调用，测试仍 stub 两参（`LearningAssessmentCandidatePoolTest.java:96/136`）；把 `when(...)` 改成 `when(questionService.listEligibleQuestionIds(any(...), eq(user), anyBoolean()))` 即可。

## B 更新（2026-11-11 09:2x，浏览器回归后）

1. **候选池测试**：已按上述方式改成三参 stub，`LearningAssessmentCandidatePoolTest` 5/5 通过。
2. **全量后端结果（修复后）**：519 用例，8 failures，0 errors，1 skipped。剩余 8 个失败全部是依赖本地内容种子的既有集成断言，断言里硬编码了旧节点/题目 ID 与旧章节名（如期望节点集 `2105…`、实际 `2117…`；期望“LRU 页面置换算法”、实际 `os.memory.lru`），分布在 `LearningEventServiceTest`（1）、`LearningPathServiceTest`（1）、`LearningRecordsServiceTest`（2）、`LearningTeacherServiceTest`（3）、`LearningMasteryServiceTest`（1）。这些测试文件本轮未修改，失败与自测/草稿/帮助链路无关；B 未改判分/BKT/ReviewPlanner/NextActionPlanner。
3. **浏览器回归发现的真实缺陷（已修）**：
   - `SelfTestRunService` 草稿答案原先以原始字符串入库（`A`），读取时按 JSON 解析失败 → 刷新后草稿变空，且会被空答案回写；已改为 Jackson 序列化写入、对旧原始值容错解析，并补 `savedDraftAnswerIsJsonAndReadableFromView` 往返测试。
   - `AssessmentRunView` 模板引用了未定义的 `saveStateText`，导致“保存状态”永远空白；已补计算属性。
   - 结果页任何 `returnTo` 都显示“返回原单元”，但题卡发起时原位置是题目练习页；已按目标路径显示“返回原单元/返回题目练习/返回原位置”，实际跳转逻辑未变。
4. **浏览器实测**：逐题反馈、自测作答/草稿/刷新恢复、双标签版本冲突、交卷幂等/并发、综合题待自评、跨账号隔离、第二课程、单元活动完成通知均已通过；证据在 `evidence/` 与 `screenshots/`。
5. **需 A/后续确认（B 未改）**：
   - 首页当前课程为“数据库系统”时，“下一步”与 AI 学习计划仍会引用 OS 薄弱节点 `os.process.deadlock`（阅读项同时给 DB 单元）。`HomeView` 已按当前 `courseCode` 请求 `refreshNextActions`，疑似 `NextActionPlanner` 未按课程过滤；按约束 B 不改该文件，请 A/用户确认。
   - C01 提示面板回复文本引用了 `question:2117582691588441470`，而当前题卡为 `211758269158841476`（同一知识点但 ID 不同）。B 已把 `questionId/attemptContextId` 传入 `openTutor`，请 A 核对面板是否优先使用了页面级上下文而非本轮显式题目。

