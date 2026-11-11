# 基线记录（A）

记录时间：2026-11-11。分支 `feature/ai-upgrade`，HEAD `4821d4e`。

## 1. 必须保留的未提交改动

### 1.1 FloatingAiAssistant.vue（用户改动，禁止覆盖）

`前端代码/osLab-frontend/src/components/assistant/FloatingAiAssistant.vue`：`git diff --stat` 为 **+57 / -28**。

差异要点（已在改动之上接续，未回退）：

- 入口按钮 52px → 76px，改为头像 `<picture>`：`ai-assistant.gif`（默认）、`ai-assistant.webp`、减弱动画偏好时 `ai-assistant-static.png`；
- 拖动吸附从“底部左右角”改为“左右侧边吸附、垂直位置保留”，函数 `snapFabToBottomCorner` → `snapFabToSide`；
- 按钮悬停/焦点态、拖动态样式随头像调整；`prefers-reduced-motion` 下禁用位移；
- 头像路径使用 `process.env.BASE_URL` 前缀。

### 1.2 未跟踪资源（禁止删除）

| 文件 | 字节 | SHA256 |
| --- | --- | --- |
| `前端代码/osLab-frontend/public/ai-assistant.gif` | 882269 | `2e3c943d7c05b456c5924a56b57514ececf2307626ef3fb821da79fe0a781291` |
| `前端代码/osLab-frontend/public/ai-assistant.webp` | 300918 | `491d59dcee36cf7468da82f3e82c9419a667044ee23ae6dacf5f7119257ed7ec` |
| `前端代码/osLab-frontend/public/ai-assistant-static.png` | 62335 | `155370d56d97562a17aba21666f86ab90c082c698acd0629436875b986398193` |

三个文件均未纳入版本控制，本轮不新增/替换。

### 1.3 其他工作树状态

- `docs/` 下 9 个旧计划文件处于已删除状态；`docs/useless/`、`旧设（useless）/`、`artifacts/` 其他目录为未跟踪。本轮不恢复、不提交、不清理、不写入。
- 本轮产物只写 `artifacts/learning-ai-ux/`。

## 2. 检查基线（本轮开工实测，不沿用旧台账）

### 2.1 前端（`前端代码/osLab-frontend`）

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 契约测试 | `node tests/ai-conversation-contracts.js` | 通过（exit 0） |
| 来源/动作 | `node tests/ai-source-actions.js` | 通过（exit 0） |
| 模型设置 | `node tests/model-settings-contracts.js` | 通过（exit 0） |
| UI 规范 | `node tests/ui-guideline-contracts.js` | 通过（exit 0） |
| 设计系统 | `node tests/design-system-contracts.js` | 通过（exit 0） |
| 工作台布局 | `node tests/workbench-layout-contracts.js` | 通过（exit 0） |
| 工作台缓存 | `node tests/node-workbench-cache.js` | 通过（exit 0） |
| lint | `NODE_OPTIONS=--max-old-space-size=2048 npm run lint -- --no-fix` | exit 0；0 error / 169 warning（既有 warning，未新增前不清零） |

未运行：`npm run build`（重任务，按锁协议在集成阶段串行执行）。

### 2.2 后端（`后端代码/osLab-backend`）

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| AI 会话/导师定向测试 | `./mvnw.cmd "-Dtest=AiSessionTurnContractTest,TutorOrchestratorTest" test` | **通过**：39 tests, 0 failures（JDK 21） |

环境注记（既有，不是本轮代码问题）：

1. 本机默认 `JAVA_HOME=C:\Users\one\.jdks\temurin-1.8.0_504`，而 pom 的 maven-compiler-plugin `source/target=14`，直接运行报“无效的目标发行版: 14”。改用 `JAVA_HOME=C:/Users/one/.jdks/ms-21.0.10`。
2. 仓库路径含中文，`target/maven-status` 旧增量状态按平台编码读取会报 `Input length = 1`。加 `-Dmaven.compiler.useIncrementalCompilation=false` 后通过。后续后端检查统一带这两个环境设置，并在 validation.md 记录。
3. 未运行完整 `./mvnw.cmd test`（重任务，按锁协议串行）。

### 2.3 能力基线（代码事实，非“已完成”声明）

- 会话/轮次已持久化：`ai_learning_session`、`ai_tutor_turn`（v11）；幂等键 `(userId, sessionId, clientRequestId)`。
- **缺失**：课程会话列表接口、轮次历史分页接口；前端恢复只恢复 session 指针，没有消息列表恢复；模型请求未携带最近轮次（TutorOrchestrator 只有本轮 message）。
- 节点工作台（`NodeLearningWorkspaceView.vue`）与实验页（`ViewQuestionView.vue`）仍各自维护本地消息 + 旧 `POST /learning/tutor/ask` 分支。
- 全局悬浮助手为 400×520 浮窗，消息卡层层包裹，composer 固定 2 行且 waiting 时禁用，无 IME 组合态保护、无滚动阈值/新消息提示。
- 硬编码 OS 示例（FIFO/LRU）在全局建议中跨课程出现。
- 模型菜单只有“模型”按钮跳设置页，无会话内切换。

## 3. 运行环境

- 2 核 2GB 目标；前端 `NODE_OPTIONS=--max-old-space-size=2048` 构建。
- 数据库迁移目录：仓库根 `数据库文件/`；已发布 v11（AI 会话/轮次/资料）、v12（模型配置/证据/复习项）。v13 起由 A 统一分配，见 `ownership.md`。
