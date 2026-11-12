# 测试与验收

## 前端

先运行 `npm ci`，再在前端目录执行：

```bash
npm run lint -- --no-fix
npm run test:contracts
npm run build
```

`test:contracts` 逐个执行 `tests/*.js` 的独立脚本；测试输入放在 `tests/fixtures/`，不依赖本机历史截图或 `artifacts/`。这些检查覆盖课程切换、题目与测评契约、导师上下文、图谱和算法逻辑，不能替代真实浏览器与数据库联调。

默认套件包含 25 个可独立运行的脚本。另一个 Vue 2 原演示集成测试使用单独入口，需要原项目源码及其已安装的 `sprite-timeline` 等依赖；它不属于主平台构建依赖，也不应依靠开发者本机目录猜测路径：

```bash
npm run test:legacy -- <原Vue2演示项目目录>
```

也可设置 `OSLAB_LEGACY_DEMO_ROOT`。未提供外部输入时，此命令以用法错误退出，不伪装成功或加入默认离线回归。

## 后端

后端使用 JUnit 5 / Mockito。以下为无需启动业务数据库的关键回归套件，在 JDK 17+ 的后端目录执行：

```powershell
./mvnw.cmd test '-Dtest=AiContentScopeServiceTest,AiActionServiceTest,AiSessionTurnContractTest,CourseContractsTest,PublishedCourseScopeTest,LearningTutorServiceTest,TeacherFollowUpsTest,TeacherTaskSummaryTest,TaskAudienceServiceTest,TaskContentLocalBuildTest,TaskRulesTest,ExperimentJudgeModeServiceTest,QuestionSubmitLocalBuildTest,TutorEvidenceScopeTest,TutorOrchestratorTest'
```

Linux/macOS 将 `./mvnw.cmd` 换为 `./mvnw`。完整 `./mvnw test` 包含依赖数据库与其他服务的集成测试，应先指向独立测试库；不要对生产库运行会写数据的测试。遗留模板测试默认排除，`-Plegacy` 只在准备好对应模板服务后使用。

## 环境与课程

```powershell
pwsh -File scripts/tests/environment.test.ps1
```

环境测试使用临时目录，检查配置解析、进程环境优先级、无脚本执行与自动密钥生成。不会连接生产数据库或启动容器。

```bash
node scripts/validate-course-content.mjs
node scripts/verify-page-replacement-content.mjs
node scripts/check-content-consistency.mjs
node scripts/check-experiment-mappings.mjs
```

## 运行验收

按 [环境配置](environment.md) 启动后，先检查 `/api/health/liveness`，再使用隔离账号验证登录、课程加载、题目作答、学习记录、实验草稿恢复、教师班级与任务。真实判题须调用实际沙箱，真实模型验收须提供有效模型配置；不要把降级文本算作模型调用通过。

`scripts/smoke-learning.ps1` 和 `scripts/dev/` 提供接口及演示工具；先阅读参数与写入范围，只在测试环境执行。报告输出到忽略的 `artifacts/`，不携带账号口令、Cookie、模型 Key 或个人数据提交到仓库。

历史设计和批次验收可通过 Git 历史查阅。当前目录只保留可执行测试和必要输入；本轮整理的检查结果记录于 [交付检查](submission.md)。
