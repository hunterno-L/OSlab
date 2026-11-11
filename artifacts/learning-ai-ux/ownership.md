# 文件所有权与迁移编号（A 维护）

依据 `docs/学习体验与AI对话整合/00-双Agent协作交付计划.md` 第 5 节。一名写入者一个文件；只读审查不受限。

## 1. A 的写入范围（本轮实际使用）

前端（`前端代码/osLab-frontend`）：

- `src/components/assistant/**`：`FloatingAiAssistant.vue`、`AiChatPanel.vue`（将被新面板替代/重写）、新增 `TutorPanel.vue`、`TutorMessage.vue`、`TutorComposer.vue`、`TutorHistoryDrawer.vue`、`TutorContextChips.vue` 等；
- `src/composables/useAiConversation.ts`、`useAiPageContext.ts`、`useAiLauncher.ts`；新增 `useTutorSession.ts`（若拆分）；
- 新增 `src/types/tutor.ts`（或 `types/tutor*`）；
- `src/api/ai.ts`、`src/utils/aiSources.ts`；
- 现有 AI 页接线：`src/views/learning/NodeLearningWorkspaceView.vue`、`src/views/question/ViewQuestionView.vue`（只改导师/上下文/相关布局）；
- 公共 AI 组件：`src/components/ai/AiSourceCard.vue`、`AiToolTrace.vue`、`AiDiagnosisPanel.vue`、`AiModeBadge.vue`、`AiMaterial*`、`src/views/settings/ModelSettingsView.vue`；
- 壳与主题：`src/App.vue`、`src/layouts/LearningLayout.vue`、`src/styles/learning-theme.css`（仅导师布局所需机制）；
- 测试：`tests/ai-conversation-contracts.js`、`ai-source-actions.js`、`model-settings-contracts.js`、`ui-guideline-contracts.js`、`design-system-contracts.js`、`workbench-layout-contracts.js`、`node-workbench-cache.js`，以及新增 AI 行为测试。

后端（`后端代码/osLab-backend`）：

- `controller/LearningAiController.java`；
- `ai/session/**`、`ai/tutor/**`；
- AI 会话/轮次 DTO/VO/Mapper（`model/dto/ai/**`、`model/vo/ai/**`、`mapper/Ai*`）；
- 历史读取与场景策略；模型网关仅在确有兼容需要时小改。

## 2. B 的写入范围（A 只读）

`HomeView.vue`、`src/components/home/**`、`CourseUnitView.vue`、`LearningQuestionsView.vue`、`AssessmentRunView.vue`、`LearningRecordsView.vue`、`ResourcesView.vue`、`KnowledgeGraphView.vue`、`GlobalHeader.vue`、`navigation/appNavigation.ts`、`router/routes.ts`、`src/api/learning.ts`、题卡/答题卡组件、测评后端、`LearningAssessmentRunController.java`、`LearningQuestionController.java`、`ai/evidence/**`（B 的接线部分）、`TutorAssessmentGuard`（B 实现）、`AiNextActionCard`/`AiNextActions`/`AiHelpBadge`、`LearningEventService*`。

判分器、BKT、ReviewPlanner、NextActionPlanner、课程内容、教师端、已发布课程包、算法演示内容：双方都不得改。

## 3. 迁移编号分配（D1 盘点）

迁移目录：仓库根 `数据库文件/`。已发布：v1–v12（v11 AI 会话/轮次/资料；v12 模型配置/证据/复习项）。**不得编辑 v11/v12。**

| 编号 | 归属 | 用途 | 状态 |
| --- | --- | --- | --- |
| v13 | B | 自测模式、草稿、结果（按 B1 契约） | 预留给 B；实际执行前 B 需核实 |
| v14 | A（仅在确证需要时） | 历史分页索引 | 当前判断：**不需要**。会话列表走 `idx_ai_session_user_course(userId, courseCode, status)`；轮次分页走 `idx_ai_turn_session(sessionId, createTime)` 且按雪花 id 倒序。若压测证明需要再占 v14，不改 v11/v12 |

A 本轮后端扩展只新增接口与查询，不改表结构；不阻塞 B 的 v13。

## 4. 跨域接口（A 提供 / B 消费）

1. 前端 `openTutor(request: TutorLaunchRequest)`：`@/composables/useAiLauncher`；类型 `@/types/tutor`。`openAiAssistant()` / `askAiAssistant(text)` 保留薄适配。
2. 后端 `TutorAssessmentGuardPort`（A 定义接口 + 默认放行实现）；B 的 `TutorAssessmentGuard` 以 Spring Bean 实现该接口。A 在 `intent=HINT` 且带 `attemptContextId`/`assessmentRunId` 时先调用再调模型。
3. `POST /learning/ai/sessions/{id}/turns` 扩展字段：`context.attemptContextId`、`context.assessmentRunId`、`tutor.scene/intent/scope/attachments`，旧字段兼容。

## 5. 重任务锁

锁文件：`artifacts/learning-ai-ux/.heavy-lock`。内容为 `A` 或 `B` + 时间戳；存在且非本 Agent 时推迟重任务并记录“未运行（等待锁）”。定向单测不加锁。
