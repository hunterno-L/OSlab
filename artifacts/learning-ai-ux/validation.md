# ZhiCore 学习体验与 AI 对话整合 — Agent A 验收记录

- 分支：`feature/ai-upgrade`
- 验收日期：2026-11-11
- 环境：Windows 10.0.26200 / JDK 21.0.10 / Node 24 / MySQL 8.4 / Redis / 真实模型 `deepseek-flash`（`AI_ARK_*` 环境变量）
- 后端：A 实例 `http://localhost:8122/api`，并行 B 实例 `8121`；前端 dev server `8081`（代理到运行中的后端）
- 浏览器：headless Microsoft Edge + puppeteer-core（In-app Browser 在本会话不可用，改为同源真实浏览器；截图均人工打开检查）
- 演示账号：`student_demo1 / 123456`（真实登录、真实会话与真实模型调用；无 mock）

## 1. 自动化结果

### 1.1 A 责任域后端测试（最终）

```bash
JAVA_HOME=C:/Users/one/.jdks/ms-21.0.10 ./mvnw.cmd \
  -Dmaven.compiler.useIncrementalCompilation=false \
  -Dtest=AiSessionTurnContractTest,AiHistoryContractTest,TutorOrchestratorTest test
```

结果：`Tests run: 54, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

覆盖点：会话归属/rowVersion、`clientRequestId` 幂等、课程作用域不可被前端改写、历史分页/游标/归属、历史裁剪与预算、HINT 守卫前置、ACTIVE 自测阻断、附件超限拒绝、单会话单运行轮次、队列拒绝可重试终态、题面注入不泄露答案/解析。

### 1.2 后端全量测试

```bash
JAVA_HOME=C:/Users/one/.jdks/ms-21.0.10 ./mvnw.cmd \
  -Dmaven.compiler.useIncrementalCompilation=false test
```

当前结果：`Tests run: 520, Failures: 8, Errors: 0, Skipped: 1`。**不是全绿**，失败均不在 A 责任域：

| 类别 | 用例 | 原因 |
| --- | --- | --- |
| 共享数据依赖（8） | `LearningEventServiceTest.attemptAndEventCountsStayConsistent`、`LearningMasteryServiceTest.nodeWorkbench_returnsPrerequisitesAndSuccessorsWithStatus`、`LearningPathServiceTest.pathForRealTarget_ordersPrerequisitesBeforeTarget`、`LearningRecordsServiceTest.timelineReturnsOwnEventsWithGroupKeyAndFilters`、`LearningRecordsServiceTest.weaknessClassifiesCurrentThenCorrected`、`LearningTeacherServiceTest.importPreviewValidatesItemsWithoutSaving`、`LearningTeacherServiceTest.nodeContentPublishedOnlyForWorkbench`、`LearningTeacherServiceTest.nodeDetailMatchesMappingTableExactly` | 真实数据库内容/映射数据在两轮验收之间被重新灌入或演示操作改变（旧节点 id `2105…` 与当前 `2117…/2120…` 并存；节点名从展示名变为 `os.memory.lru`）。这些用例依赖固定种子数据，不是 A 代码回归 |

说明：B 的 `LearningAssessmentCandidatePoolTest` 已在 09:20 修复（520 个用例，0 errors）；A 的 54 个 AI 契约测试与 Spring 容器加载均通过；剩余 8 个失败需要在干净种子库上重跑或由 B 更新夹具。

### 1.3 前端测试

```bash
cd 前端代码/osLab-frontend
for f in tests/*.js; do node "$f"; done
```

结果：25 个测试文件，24 通过，1 失败：

- 失败：`tests/knowledge-graph-render.js`。该测试读取 `src/components/KnowledgeGraph.vue`，组件在 HEAD 即使用 `echarts/core`、`echarts/charts` 等按需导入，而测试 mock 只接受 `require("echarts")`；组件文件本轮未被修改（`git status` 无变化），属于**基线已有的测试夹具失配**，非本次改动引入。
- A 相关测试全部通过：`ai-conversation-contracts.js`、`ai-help-flow.js`、`ui-guideline-contracts.js`；B 的 `assessment-selftest-contracts.js` 也通过。

### 1.4 Lint 与构建

```bash
npx vue-cli-service lint --no-fix   # exit 0，0 errors，168 warnings（既有 any 等）
NODE_OPTIONS=--max-old-space-size=2048 npm run build  # DONE，exit 0
```

构建在 `.heavy-lock` 下串行执行，构建完成后已删除锁文件。`dist` 未提交。

## 2. 真实浏览器验收

截图目录：`artifacts/learning-ai-ux/screenshots/`。以下均使用真实后端与真实模型，未使用 mock、未伪造流式或取消。

| 场景 | 结果 | 截图 |
| --- | --- | --- |
| 首页 + 停靠面板 | 面板 461px，从 84px 头部下沿开始；全局头部导航/搜索/用户菜单完整可见；正文右侧让位；AI 学习计划只出现当前数据库课程的单元/题目 | `layout-dock-1440.png`、`panel-dock-1440.png`、`final-home-1440.png`、`final-panel-1440.png` |
| 普通问答（真实模型） | 回答带来源引用、FALLBACK 徽标和详情；模型 `deepseek-flash` | `qa-1-1440.png` |
| 追问（A02） | 第二轮引用服务端历史中的上一轮问题，未重复发送 | `qa-followup-1440.png` |
| 题目提示 | “我需要提示”先 `POST practice/attempts` → `POST .../help`（HINT）→ 轮次携带 `attemptContextId`；服务端补入已发布题面后，回答围绕“属性闭包”而非无关的 LRU | `question-hint-1440.png` |
| 实验导师 + 附件 | 只勾选后携带代码附件；面板显示“当前代码（c）”；请求体无 API Key | `experiment-tutor-form-1440.png`、`experiment-tutor-answer-1440.png` |
| 附件超限 | 8001/8000 字时显示“不会静默截断”，两个发送按钮均禁用 | `experiment-attachment-over-limit.png` |
| 节点工作台 | 页面不再有内嵌第二套聊天；点击“向导师提问”打开统一面板，带节点草稿与上下文，不自动发送 | `node-1440.png`、`node-tutor-1440.png` |
| 刷新恢复 | 刷新后恢复服务端历史消息；未发送草稿在刷新后保留 | `restore-1440.png` |
| 最近对话 / 只读 | 新话题真正关闭旧会话；历史显示“已关闭”，打开后只读横幅 + 输入禁用 | `history-1440.png`、`history-readonly-1440.png` |
| 1024px | 浮层 462×736，遮罩可关闭，无横向溢出 | `layout-float-1024.png` |
| 390px / 320px | 全屏面板；320px 下范围切换不换行、隐藏“展开”、输入区与底部提示在视口内 | `layout-full-390.png`、`layout-full-320.png` |
| 200% 缩放（CSS 720×450） | 浮层完整落在视口内，输入与消息可滚动 | `layout-zoom200-720.png` |
| 展开阅读态 | 1100px 居中、`aria-modal=true`、Esc 回到停靠并保留消息；拖拽调宽 461→554.5 并持久化 | `expanded-1440.png` |

## 3. 本轮发现并修复的问题

1. **B 的 Spring 注入冲突**：`SelfTestRunService` 字段名 `questionMapper` 与旧 `QuestionMapper` Bean 同名，导致 50 个 Spring 上下文测试失败、后端无法启动。已记录精确修法；B 于 08:45 改为 `learningQuestionMapper`，容器恢复。
2. **CORS 不允许 PATCH**：`PATCH /learning/ai/sessions/{id}` 在浏览器被 403，导致“新话题/切换目标/切换模型”静默失败。已在 `CorsConfig` 补 `PATCH` 并重启验证：新话题关闭旧会话、历史只读生效。
3. **题卡 HINT 空发送**：B 的 `openAssessmentTutor` 以 `HINT + SEND` 打开但不带 draft，面板不会发送。A 在面板内补中性提示请求（非 OS 专用），真实轮次已带 `attemptContextId`。
4. **题面缺失**：题目辅导只传 `question:<id>`，模型看不到题面，曾给出与当前题无关的 LRU 提示。A 在 `AiTurnService` 服务端读取 PUBLISHED 题目标题/题干/选项注入模型输入，**不注入答案与解析**，并新增单测防泄漏；实测提示已围绕“属性闭包”。
5. **停靠面板遮挡头部**：停靠时面板从顶部覆盖搜索/用户菜单。改为从 84px 头部下沿开始，正文仍右侧让位。
6. **小屏范围按钮换行**：320px 下“当前页面/整门课程”竖排撑高头部。已 `white-space: nowrap`，并在全屏模式隐藏无意义的“展开”。
7. **刷新后草稿被清空**：登录态水合（null→用户）时误调用 `clearAllDrafts`。改为只清理“真实退出/换号”的旧用户草稿；刷新后草稿保留。
8. **首次打开不显示历史**：从 `next-actions` 采纳服务端会话时只设 session，不载轮次。已补 `loadInitialTurns`，首次打开即恢复最近 20 轮。
9. **AI 学习计划课程串扰**：`AiNextActionService` 读取学习证据时原先按用户全量节点，数据库课程会引用 OS 薄弱节点。已通过 `AiContentScopeService.publishedNodeCodes(course)` 把证据限制在当前课程节点；实测 `course.database-systems` 的下一步为 `unit.db.fd-keys` 与 DB 题目，不再出现 OS 节点（`final-home-1440.png`）。

## 4. 隐私与约束核对

- API Key：仅后端环境变量；浏览器 `localStorage` 仅 `oslab-ai-session-pointer-<userId>`，无密钥；轮次请求体、URL、运行时日志、截图均检查过，无 `sk-`/`apiKey`。
- 题面注入只读取当前用户可见的 PUBLISHED `learning_question`，不读答案/解析；题面查找失败时按原消息继续，不阻断 AI。
- 实验附件只在学生勾选/编辑后上传；超过 2000/8000/12000 字上限时显式拒绝并禁用发送，不静默截断。
- 自测 ACTIVE 的提示/答案拦截由 B 的 `TutorAssessmentGuard` 在模型调用前执行；A 的适配层不自行放行无上下文 HINT。
- 未 commit/push/merge/deploy；未改 git config；未 `git add .`；未恢复/提交 `docs/useless/` 与被删文档；未覆盖用户未提交资产（`ai-assistant.*` 头像仍为工作区未提交改动）。
- 未修改 v11/v12 迁移；本轮无 v14（历史分页不需要新表）。v13 归 B。

## 5. 已知限制（需在演示中如实说明）

- **语义检索降级**：当前环境未配置 embedding，`/learning/ai/turns` 返回 `mode=FALLBACK`；模型回答真实、来源来自真实检索结果/规则，但面板会明确显示“降级回答（未使用语义检索）”。不是伪装 AI 成功。
- **模型配置**：`ai_model_profile` 当前为空，模型菜单只显示“平台默认模型”；没有“已测试模型”可切换。
- **全量后端测试未全绿**：8 个失败为共享种子数据依赖问题，明细见 §1.2 与 `decisions.md`；演示前建议在干净种子库重跑。
- **图谱渲染测试**：基线已有的 echarts mock 失配（组件未改），不影响本次交付与构建。
- **我的学习画像“下一步”**：该卡片来自既有 mastery 数据，仍可能显示其他课程的节点（截图中数据库课程画像提示“进程状态”）；A 已修正 AI 学习计划的课程过滤，画像本身属于 B/既有学习域，未在本次改动。
- **浏览器数据**：真实验收以 `student_demo1` 创建了练习尝试、帮助记录、AI 会话与轮次（数据库持久化），属于真实验收副作用；如需干净演示库请重新灌种子。
- 真实模型在无检索资料时会主动说明“资料不足”，不会编造课程结论；这是刻意保留的诚实行为。

## 6. 复现命令

```bash
# 后端（A 验证实例）
cd 后端代码/osLab-backend
JAVA_HOME=C:/Users/one/.jdks/ms-21.0.10 SERVER_PORT=8122 ./mvnw.cmd -q -DskipTests spring-boot:run

# 前端（8081 已有并行实例时可直接使用；独立实例）
cd 前端代码/osLab-frontend
VUE_APP_API_TARGET=http://localhost:8122 NODE_OPTIONS=--max-old-space-size=2048 \
  npx vue-cli-service serve --port 8082

# A 契约测试
JAVA_HOME=C:/Users/one/.jdks/ms-21.0.10 ./mvnw.cmd \
  -Dmaven.compiler.useIncrementalCompilation=false \
  -Dtest=AiSessionTurnContractTest,AiHistoryContractTest,TutorOrchestratorTest test
```
