# Agent B 执行报告：学习、练习与复习闭环

日期：2026-11-11。分支：`feature/ai-upgrade`。未 commit / push / merge / 部署。

## 1. 实际修改/新增文件清单

### 后端（`后端代码/osLab-backend`）

新增：

- `src/main/java/com/yupi/springbootinit/learning/SelfTestPolicy.java`
- `src/main/java/com/yupi/springbootinit/learning/SelfTestRunService.java`
- `src/main/java/com/yupi/springbootinit/model/entity/LearningAssessmentDraft.java`
- `src/main/java/com/yupi/springbootinit/mapper/LearningAssessmentDraftMapper.java`
- `src/main/java/com/yupi/springbootinit/model/dto/learning/SelfTestDraftSaveRequest.java`
- `src/main/java/com/yupi/springbootinit/model/dto/learning/PracticeAttemptCreateRequest.java`
- `src/main/java/com/yupi/springbootinit/model/vo/learning/SelfTestRunVO.java`
- `src/main/java/com/yupi/springbootinit/model/vo/learning/SelfTestResultVO.java`
- `src/main/java/com/yupi/springbootinit/model/vo/learning/SelfTestDraftSaveVO.java`
- `src/main/java/com/yupi/springbootinit/model/vo/learning/PracticeAttemptVO.java`
- `src/main/java/com/yupi/springbootinit/ai/evidence/TutorAssessmentGuard.java`
- `src/main/java/com/yupi/springbootinit/ai/evidence/PracticeAttemptService.java`
- `src/main/java/com/yupi/springbootinit/controller/LearningPracticeController.java`
- `src/test/java/com/yupi/springbootinit/learning/SelfTestRunServiceTest.java`
- `src/test/java/com/yupi/springbootinit/ai/evidence/TutorAssessmentGuardTest.java`
- `数据库文件/oslab_upgrade_v13_learning_selftest.sql`（仓库根下）

修改：

- `src/main/java/com/yupi/springbootinit/controller/LearningAssessmentRunController.java`
- `src/main/java/com/yupi/springbootinit/controller/LearningQuestionController.java`
- `src/main/java/com/yupi/springbootinit/mapper/LearningAssessmentRunMapper.java`
- `src/main/java/com/yupi/springbootinit/model/entity/LearningAssessmentRun.java`
- `src/main/java/com/yupi/springbootinit/model/dto/learning/LearningAnswerRequest.java`
- `src/main/java/com/yupi/springbootinit/service/LearningQuestionService.java`
- `src/main/java/com/yupi/springbootinit/service/impl/LearningQuestionServiceImpl.java`
- `src/test/java/com/yupi/springbootinit/controller/LearningAssessmentCandidatePoolTest.java`（仅把 mock 改为三参调用）

### 前端（`前端代码/osLab-frontend`）

新增：

- `src/api/assessment.ts`
- `src/utils/assessmentTutor.ts`
- `src/utils/learningEvents.ts`
- `src/components/learning/SelfTestQuestionCard.vue`
- `tests/assessment-selftest-contracts.js`

修改：

- `src/api/learning.ts`
- `src/components/learning/QuestionAnswerCard.vue`
- `src/views/learning/AssessmentRunView.vue`
- `src/views/learning/LearningQuestionsView.vue`
- `src/views/learning/CourseUnitView.vue`
- `src/views/knowledgeGraph/KnowledgeGraphView.vue`（仅求助调用与上下文；该文件同时含 A 的图谱重构）
- `src/components/GlobalHeader.vue`（学习记录入口文案 → 我的学习，路由不变）
- `tests/ai-help-flow.js`、`tests/question-answer-context.js`、`tests/learning-integration-contracts.js`（适配新依赖/保留原业务断言）

执行产物：`artifacts/learning-ai-ux/`：`decisions.md`（B 基线与更新）、`selftest-schema.sql`、`browser-verify*.cjs`、`evidence/`、`screenshots/`。未改 A 的组件/composable/DTO/Controller，未改判分器、BKT、ReviewPlanner、NextActionPlanner、课程内容、教师端。

## 2. 交付给 A 的契约

### 2.1 普通练习与帮助

```http
POST /learning/practice/attempts
{"questionId":"2115261127086305282","courseCode":"course.operating-systems",
 "sourceUnitCode":"unit.os.overview","sourceActivityCode":"act...","clientAttemptKey":"<uuid>"}
→ {"code":0,"data":{"attemptContextId":"2120319137416458242","questionId":"...",
   "questionRevision":1,"submissionKey":"attempt:2120319137416458242",
   "helpLevel":"NONE","status":"READY"}}
```

```http
POST /learning/practice/attempts/{attemptContextId}/help
{"helpLevel":"HINT"}   // 或 ANSWER_REVEALED
→ {"code":0,"data":{"helpLevel":"HINT","eligibleForObservation":false,
   "eligibilityReason":"..."}}
```

`POST /learning/questions/answer` 新增可选 `attemptContextId`、`submissionKey`；有上下文时在答题事务前核对帮助资格。ID 一律按字符串传输。

### 2.2 `TutorAssessmentGuard`

位于 `ai/evidence/TutorAssessmentGuard.java`，A 的 `TutorAssessmentGuardAdapter` 已通过 Spring 可选注入桥接，无需 A 改调用：

```java
TutorAssessmentGuard.Decision decision = guard.check(user, session,
        context, "HINT", message);   // context 来自已校验的 AiCourseContext
if (!decision.isAllowed()) {
    return deny(decision.getPublicReason());
}
// decision.getHelpLevel() / getAttemptContextId() / getAssessmentRunId()
```

行为：ACTIVE 自测中的本组题（即使客户端删掉 runId，也按用户 ACTIVE 自测与题目关联判定）拒绝；普通题校验 `attemptContextId` 与题目绑定；HINT/EXPLAIN 在未消费上下文上先持久化帮助等级再允许；全局场景放行；错误时保守拒绝。

### 2.3 整组自测

```http
POST /learning/assessment/start
{"courseCode":"course.operating-systems","count":5,"mode":"SELF_TEST","studyState":"ALL"}
→ {"runId":"2120322139153997826","questionIds":["2112165588673150978", ...],
   "mode":"SELF_TEST","rowVersion":0}

GET /learning/assessment/run?runId=2120322139153997826
→ ACTIVE 时每个题目仅：id/title/content/type/options/chapter/difficulty/
  reviewStatus/offline/questionRevision/revisionChanged/draftAnswer/markedUnsure
  （无 answer、无 analysis、无 correct/latestCorrect/attemptCount）

PUT /learning/assessment/runs/{runId}/drafts
{"clientSaveId":"<uuid>","rowVersion":3,
 "drafts":[{"questionId":"2112165588673150978","answer":"B","markedUnsure":false}]}
→ {"rowVersion":4,"savedAt":"...","conflict":false,"serverDrafts":[]}
   冲突时 HTTP 200 + conflict=true + 最新 rowVersion + serverDrafts

POST /learning/assessment/finish
{"runId":"2120322139153997826","submissionKey":"<uuid>","rowVersion":4}
→ {"runId":"...","mode":"SELF_TEST","totalQuestions":5,"answeredQuestions":3,
   "firstCorrect":1,"validQuestions":5,"unansweredQuestions":2,
   "pendingSelfAssessmentCount":0,"invalidCount":0}
   同 submissionKey 重发返回完全相同的 FinishResult；不同键返回 50001「本轮已结束」

GET /learning/assessment/runs/{runId}/result
→ 逐题 status（CORRECT/WRONG/UNANSWERED/PENDING_SELF_ASSESSMENT/INVALID_OFFLINE/INVALID_VERSION）、
  统计、错题/不确定题、summaryText、masteryChanges
```

`LearningUpdated`：`oslab:learning-updated`（同时兼容旧 `oslab:learning-changed`），`{courseCode, unitCode, activityCode, runId, reason}`，仅在服务端写入成功后触发。

## 3. 验收矩阵结论与证据

| 编号 | 结论 | 证据 |
| --- | --- | --- |
| B01 | 已通过（附跨域发现） | `screenshots/b01-home.png`、`evidence/phase3.json`：真实当前单元、真实计划、单一主操作；但“下一步”引用了另一课程的节点，见 §7 |
| B02 | 已通过 | `screenshots/b02-per-question-feedback.png`、`evidence/b02-feedback.txt`：选中后主操作变“下一题”，反馈为“回答正确 + 一句依据 + 查看详细解析”，未自动打开导师/跳题 |
| B03 | 已通过 | `evidence/b03-hint-ui.json`；“我需要提示”先建尝试并落 HINT（DB `helpLevel=HINT, eligibleForObservation=0`）再打开导师；`TutorAssessmentGuardTest` 6/6 |
| B04 | 已通过 | `evidence/b04-steps.json`（3 题 draftAnswer=B）、`b04-after-reload.json`（刷新后 1/2/3 题已答、第 2 题不确定、B 选项恢复）、`b04-active-run.json`、`b04-network-index.json`（草稿 PUT 请求体含答案） |
| B05 | 已通过 | `evidence/b05-bypass.json`：普通作答返回 `50001 该题正在整组自测中，请先交卷后再作答`；题目详情无 analysis、`hasAttempted=false`；列表页无解析；导师路径由 Guard 单测覆盖 |
| B06 | 已通过 | `evidence/b06-conflict.json`：另一标签页保存到 v4 后，旧标签页保存出现“草稿版本冲突”，点“载入服务端草稿”后提示消失（`afterApply=false`）；断网时出现“草稿保存失败”且输入保留，恢复网络点“重试保存”后 `draftAnswer=A、已保存`（`evidence/phase5.json`） |
| B07 | 已通过 | `evidence/b07-result.json`（有效 5=答对1+答错2，未答2，33%）；`b07-repeat-same-key.json`（同键两次 `identical=true`）；`b07-concurrent.json`（并发两次均 code 0 且 data 相同；不同键 50001）；DB 该 run 仅 3 条 attempt、3 个 submissionKey |
| B08 | 已通过（下线题见 §7） | `evidence/b08-comprehensive-pending.json`：综合题 PENDING_SELF_ASSESSMENT、`pendingSelfAssessmentCount=1`、`objectiveQuestions=0/accuracy=null`、未答不计错；该 run 的 attempt/event 均为 0；`b08-comprehensive-result.png`。下线/版本失效由 `SelfTestRunServiceTest` 覆盖（invalid、不进分母） |
| B09 | 已通过 | `evidence/phase4c.json`：单元活动完成后 `ACTIVITY_DONE {courseCode, unitCode, activityCode}`；`phase4b.json`：交卷后 `ASSESSMENT_FINISHED {courseCode, unitCode, runId}`，结果页按钮文案为“返回题目练习”，跳转回带课程/单元范围的 `/assessments` |
| C01 | 已通过 | `screenshots/c01-second-course.png`、`evidence/phase3.json`：数据库系统 6 题、知识节点无 OS 词、作答反馈正常、提示面板上下文为数据库系统；未硬编码 OS 题目/节点 |
| C02 | 已通过 | `evidence/c02-cross-account.json`：另一账号访问主 run 的 run/result/finish 全部 `40101 无权访问该测评会话`；新账号 localStorage 隔离，无主 run 文本 |
| C03 | 已通过 | `evidence/phase3.json`：`/resources`、`/experiments`、`/settings/models` 均加载且无错误文案 |
| C04 | 已审查 | 见 §6；B 仅改所有权内文件，未改判分/BKT/调度器/课程内容/教师端；未提交 |

## 4. 自测未判分响应样例与交卷幂等证据

ACTIVE 自测期间真实 `GET /learning/assessment/run?runId=2120322139153997826`（节选，完整见 `evidence/b04-active-run.json`）：

```json
{"code":0,"data":{"runId":"2120322139153997826","status":"ACTIVE","mode":"SELF_TEST",
 "rowVersion":3,"savedAt":"1794359177000","questions":[
  {"id":"2112165588673150978","title":"操作系统管理对象的界定","type":"SINGLE",
   "reviewStatus":"PUBLISHED","questionRevision":1,"offline":false,
   "revisionChanged":false,"draftAnswer":"B","markedUnsure":false},
  {"id":"2115261063991390216","title":"进程死锁描述辨错","type":"SINGLE",
   "reviewStatus":"PUBLISHED","questionRevision":1,"offline":false,
   "revisionChanged":false,"draftAnswer":"B","markedUnsure":true}]},
 "message":"ok"}
```

题目对象只有安全字段，不存在 `answer`/`analysis`/`correct`/`latestCorrect`/`attemptCount`。草稿 PUT 的请求体（真实）包含 `answer:"B"`，但响应与 run 视图都不含判分。

幂等证据：

- 同一 `submissionKey` 连续两次 finish：响应 JSON 完全一致（`b07-repeat-same-key.json`，`identical:true`）。
- 并发两次 finish（同一新 run、同一键）：两次均 `code=0`，`data` 相同（`b07-concurrent.json`，`sameResultsEqual:true`）；不同键返回 `50001 本轮已结束`。
- 主 run 3 道已答题只产生 3 条 `learning_attempt`（3 个不同 submissionKey），未答题不产生 attempt/event；综合题待自评 run 的 attempt=0、event=0。

## 5. 迁移顺序与编号

- 使用 A 分配的 **v13**：`数据库文件/oslab_upgrade_v13_learning_selftest.sql`，应在已发布的 v11/v12 之后执行。
- 内容：`learning_assessment_run` 增加 `mode, rowVersion, questionRevisionsJson, courseCode, lastSaveId, lastSaveAt, submissionKey, submissionRowVersion, finishResultJson`；新增 `learning_assessment_draft`，`UNIQUE(runId, questionId)`；草稿不写 `learning_attempt/learning_event`。
- 旧 run 无 mode 一律按 `PRACTICE`；未修改 v11/v12。
- 本地 MySQL 已执行 v13 并验证列/表存在；部署时按 v11→v12→v13 顺序执行。

## 6. 检查结果分类

**已通过**

- 后端定向：`SelfTestRunServiceTest` 11/11、`TutorAssessmentGuardTest` 6/6、`LearningAssessmentCandidatePoolTest` 5/5、既有 `LearningAssessmentRunControllerTest`、`LearningQuestionAssessmentTest` 均通过。
- 前端契约：`assessment-selftest-contracts`、`question-answer-context`、`ai-help-flow`、`home-contracts`、`learning-integration-contracts`、`course-catalog-multi-course`、`course-semantics` 等通过；全量 26 个脚本中 25 个通过。
- `npm run lint`：0 errors（168 个既有 warnings）。
- `NODE_OPTIONS=--max-old-space-size=2048 npm run build`：连续两次成功（约 60s/次）。
- 浏览器：B01–B09、C01–C03 按 §3 逐项通过，截图已亲自检查（逐题反馈、自测作答、自测结果、综合题待自评、第二课程、单元完成）。

**既有失败**

- 前端 `tests/knowledge-graph-render.js`：未跟踪的旧测试断言 `import("echarts")`，当前图视图使用 `echarts/core`（A 的图谱重构）；失败在用例加载阶段，与 B 的求助接线无关。未改该测试。
- 后端全量最终：519 用例，8 failures，0 errors，1 skipped。首跑的 2 errors 是 `LearningAssessmentCandidatePoolTest` 的两参 mock，修复为三参后该学 5/5；其余 8 failures 全部是依赖本地内容种子的既有集成断言（硬编码旧节点/题目 ID、旧章节名），分布在 `LearningEventServiceTest`(1)、`LearningPathServiceTest`(1)、`LearningRecordsServiceTest`(2)、`LearningTeacherServiceTest`(3)、`LearningMasteryServiceTest`(1)。这些测试文件本轮未修改，断言对象与自测/草稿/帮助链路无关。

**新增失败**

- 无遗留。浏览器回归中发现并修复的 4 个真实缺陷：① `SelfTestRunService` 的 `@Resource` 字段名与旧 `questionMapper` Bean 冲突导致应用无法启动（改为 `learningQuestionMapper`）；② 草稿答案以原始文本入库、读取按 JSON 解析失败，导致刷新丢草稿并可被空答案回写（改为 Jackson 写入 + 旧值容错，并加往返测试）；③ 自测页 `saveStateText` 未定义导致保存状态空白；④ 结果页对所有 `returnTo` 都显示“返回原单元”（改为按目标显示“返回原单元/返回题目练习/返回原位置”）。
- 断网/移动布局：见 §7（未运行项）。

**未运行**

- 真实模型生成提示：本机未配置可用模型，A 面板显示真实降级/规则结论；未把降级结果当模型效果。
- 下线/版本变化题的浏览器场景：需要改课程内容或题目 revision，按约束未改；由 `SelfTestRunServiceTest` 验证（invalid、不进分母、不换题）。
- 断网自动保存失败/重试、390px 移动布局（见 §7；若时间允许补）。
- A 的完整面板可读性/IME/滚动/历史模型状态审查（A 域，仅抽查了提示面板）。

## 7. 未完成项与已知限制

1. **首页跨课程下一步**：当前单元为数据库系统时，“下一步”和 AI 学习计划仍引用 OS 薄弱节点 `os.process.deadlock`（同一计划里阅读项指向 DB 单元）。`HomeView` 已按当前 `courseCode` 请求 `refreshNextActions`；疑似 `NextActionPlanner` 未按课程过滤。按硬约束 B 不改该文件，请 A/用户确认。
2. **提示上下文指向**：C01 中提示面板回复引用了 `question:2117582691588441470`，而当前题卡为 `211758269158841476`（同知识点不同题）。B 已把 `questionId/attemptContextId` 传入 `openTutor`，请 A 核对面板是否优先使用了页面级上下文。
3. **图谱测试**：`knowledge-graph-render.js` 期待 `echarts`，源码用 `echarts/core`；属 A 图谱重构后的既有失败，B 未改。
4. **下线/版本变化浏览器验证**：受“不改课程内容”约束，仅单测覆盖。
5. **真实模型**：未配置模型，提示为真实降级回答（界面有明确说明），不能标记“AI 生成提示已完成”。
6. **测试账号**：本地库新增合成账号 `qa_b_nov11`、`qa_b_other` 及若干自测 run/事件用于回归；未删除（删除属破坏性操作），如需清理请用户确认。
7. **自我截图**：`b04-self-test-answering.png` 展示答题卡/保存状态/无判分，但当前题未选中选项；B02 截图展示了选中与反馈。
8. **390px 移动端**：`b04-mobile-self-test.png` 显示无横向溢出、答题卡与返回/交卷可用；全局 AI 头像在页面底部会遮住“下一题”的一角，属 A 的浮动头像组件（用户拖动偏好），B 未改；滚动后可用。

## 8. 需要 A 或用户配合的事项

1. A：确认 `NextActionPlanner` 的课程范围（首页当前课程与“下一步”不一致）；B 未改该文件。
2. A：核对导师面板对显式 `questionId/attemptContextId` 与页面级上下文的选择顺序；C01 提示文本引用了另一题号。
3. A：将本报告结论收录进 `validation.md`，并在最终 README 说明 v11→v12→v13 顺序；如需 v14 请另行分配。
4. A：图谱测试 `knowledge-graph-render.js` 与 `echarts/core` 的对齐由 A 处理。
5. 用户：确认是否清理本地合成测试账号/自测数据；确认首页“下一步”跨课程是否符合预期。
6. 用户/A：演示前配置可用模型，以便验证真实提示/导师回答，而不是降级结果。
