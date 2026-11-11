# Agent A 交付报告（AI 对话 / 统一导师 / 集成）

- 分支：`feature/ai-upgrade`（未 commit / 未 push）
- 日期：2026-11-11
- 交付范围：A1–A5

## 1. 交付清单

### 1.1 契约与入口

- `src/types/tutor.ts`：`TutorLaunchRequest`、`TutorScene`、`TutorIntent`、`TutorContextScope`、`TutorAttachment`、校验/归一化/长度上限。
- `src/composables/useTutorLaunch.ts`：页面→全局面板的待处理启动状态与 `oslab:tutor-open` 事件；不持有消息/会话。
- `src/composables/useAiLauncher.ts`：正式 `openTutor`；`openAiAssistant`、`askAiAssistant` 仅保留为薄适配。
- 后端 `AiRequests.CreateTurn.tutor`、`AiCourseContext.attemptContextId/assessmentRunId`；`TutorAssessmentGuardPort`/`DefaultTutorAssessmentGuard`/`TutorAssessmentGuardAdapter` 适配 B 的服务端守卫。
- `AiNextActionService` + `AiContentScopeService.publishedNodeCodes`：AI 学习计划证据按当前课程节点过滤，避免数据库课程引用 OS 薄弱节点。

### 1.2 会话、历史与多轮

- 后端新增 `GET /learning/ai/sessions`、`GET /learning/ai/sessions/{id}/turns`：课程必填、Base64 会话游标、轮次 id 分页、归属/课程校验、关闭会话可读。
- 最近 6 个 SUCCEEDED 轮次进入模型提示：问题 1/3、回答 2/3、每轮最小 200 字符、总预算 6000；历史来源明确不可复用为引用。
- 前端 `useTutorSession.ts`：按 `userId+courseCode` 隔离状态；sessionStorage 草稿；localStorage 仅会话指针；`clientRequestId` 幂等；未定状态恢复；失败重试是新尝试；历史分页去重；单会话单运行轮次。
- 刷新/首次打开恢复：从指针或 `next-actions` 采纳 ACTIVE 会话后补载最近 20 轮。

### 1.3 导师面板

- `TutorPanel.vue`：dock/float/full/expanded 共享同一消息核心；460px 可调 400–560；expand 1100px；320/390 全屏。
- 15px/1.7 正文、13px 代码、复制按钮、来源/详情、FALLBACK 徽标、下一步；自动增高输入框、IME、草稿保护、滚动阈值/新回复跳转。
- 最近对话（真实会话）、新话题（关闭旧会话）、只读历史、已测试模型菜单（无 profile 时仅平台默认）。
- `FloatingAiAssistant.vue`：保留用户原有头像 FAB、左右吸附、垂直位置记忆；新增停靠让位、拖拽调宽、展开焦点陷阱、Esc 行为、旧事件兼容。

### 1.4 场景迁移

- 节点工作台：移除页面内嵌聊天，保留证据/下一步；“向统一导师提问/向导师提问/解释当前节点”只提交上下文和草稿。
- 编程实验：新增统一导师页签，代码/判题结果/说明选段都要显式勾选；超过 2000/8000/12000 字显式拒绝；发送或只填草稿两条路径。
- 题卡：B 的 `openAssessmentTutor` 走 A 的 `openTutor`；HINT 空草稿由面板补中性请求，轮次携带 `attemptContextId`。
- 图谱页由 B 迁移到 `openTutorScene`；A 保留兼容适配。

### 1.5 服务端题面补全（本轮新增）

- `AiTurnService.withResourceContext`：当 `resourceRef=question:<id>` 时读取 PUBLISHED、未删除的 `learning_question`，把标题/题干/选项放入模型输入；**不包含答案/解析**；查找失败不影响原消息。
- 新增单测 `questionStemIsAddedToModelInputWithoutLeakingAnswer`，验证题面进入、答案/解析不进入。
- 背景：修复前题卡提示只带 id，模型曾对“属性闭包”题给出无关的 LRU 提示；修复后实测回答围绕闭包。

### 1.6 集成修复

- `CorsConfig` 补 `PATCH`：浏览器里会话关闭/目标切换/模型切换不再 403；实测“新话题”真正落库，历史显示“已关闭”。
- 停靠面板从 84px 头部下沿开始，避免遮挡全局搜索/用户菜单。
- 320px 范围按钮不换行，全屏隐藏“展开”。
- 修复刷新水合误删草稿；首次采纳服务端会话补载历史。

## 2. 验证摘要

- A 后端契约测试：**54/54 通过**。
- 前端测试：25 个文件 **24 通过**；唯一失败 `knowledge-graph-render.js` 为基线已有的 echarts mock 失配（组件未修改）。
- `npm run lint`：**0 errors，168 warnings**。
- `npm run build`（2GB heap、`.heavy-lock`）：**成功**。
- 真实浏览器：普通问答、追问、题目提示、实验附件、节点入口、刷新恢复/草稿、历史只读/新话题、1024/390/320/200% 均实测并人工检查截图；详见 `validation.md` 与 `screenshots/`。
- 真实模型 `deepseek-flash`；当前无 embedding，回答显示 FALLBACK，未伪装成 AI 全链路成功。

## 3. 已知问题与交接

1. **B 的 `LearningAssessmentCandidatePoolTest` 两个用例**已于 09:20 修复（三参 mock），全量 520 用例 0 errors。
2. **8 个共享数据依赖测试**因种子/演示数据变化失败（旧节点 id、展示名变化），不是 A 代码回归；建议在干净种子库重跑，或由 B 更新夹具。
3. **`ai_model_profile` 为空**：模型菜单只显示平台默认模型；无“已测试模型”可切换。
4. **图谱渲染测试**：基线已有的 `echarts/core` 与测试 `echarts` mock 失配，组件文件未修改。
5. **FALLBACK**：环境未配置 embedding；若验收要求 `mode=AI` 必须有真实语义检索，需要配置 embedding（`AI_ARK_EMBEDDING_*` 或模型档案）。
6. B 的共享/自测页面仍在并行改动：最终合并前请以最新工作区重跑 `validation.md` 的命令；A 未编辑 B 的 `SelfTestRunService`、`AssessmentRunView`、`LearningQuestionServiceImpl` 等文件。

## 4. 约束遵守

- 未 commit/push/merge/deploy；未改 git config；未 `git add .`；未 `git reset --hard`/`git clean`；未删除未跟踪文件；未恢复/提交 `docs/useless/` 与被删文档。
- 未覆盖用户未提交的 `FloatingAiAssistant.vue` 头像/拖拽改动与 `public/ai-assistant*` 资产；新宿主保留头像 FAB、侧边吸附和垂直记忆。
- 未修改 v11/v12 迁移；v13 归 B；本轮无 v14。
- 重任务均在 `artifacts/learning-ai-ux/.heavy-lock` 下串行执行，完成后删除锁。

## 5. 关键文件索引

后端：

- `ai/session/AiTurnService.java`（守卫、历史、附件、题面注入、单会话锁）
- `ai/session/AiHistoryService.java`（会话/轮次分页与上下文摘要）
- `ai/tutor/TutorOrchestrator.java`、`TutorTool.java`、`TutorAssessmentGuard*.java`
- `controller/LearningAiController.java`、`config/CorsConfig.java`
- 测试：`AiSessionTurnContractTest`（30）、`AiHistoryContractTest`（7）、`TutorOrchestratorTest`（16）

前端：

- `types/tutor.ts`、`composables/useTutorLaunch.ts`、`useAiLauncher.ts`、`useTutorSession.ts`
- `components/assistant/FloatingAiAssistant.vue`、`TutorPanel.vue`
- `views/learning/NodeLearningWorkspaceView.vue`、`views/question/ViewQuestionView.vue`
- `styles/learning-theme.css`（停靠让位）
- 测试：`tests/ai-conversation-contracts.js`、`tests/ui-guideline-contracts.js`

文档：

- `artifacts/learning-ai-ux/validation.md`（验收与限制）
- `artifacts/learning-ai-ux/demo.md`（5–8 分钟演示）
- `artifacts/learning-ai-ux/decisions.md`（D1 冻结决定与 B 协作/阻塞记录）
- `artifacts/learning-ai-ux/baseline.md`、`ownership.md`
