# learning 模块第二阶段审计（Phase 0，2026-09-30）

依据《知识图谱智能学习闭环第二阶段修正与产品化执行计划.md》§4 执行的二次审计与基线冻结记录。

## 1. 基线验证（真实命令输出）

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 后端全量测试 | `mvnw test` | 52 tests：46 通过，**6 Errors 均为遗留模板测试**（`PostMapperTest`/`PostFavourMapperTest`/`PostFavourServiceTest`/`PostThumbServiceTest` 引用不存在的 `post` 表；`CosManagerTest` 需要 COS 凭据）——与 learning 模块无关的基线既有问题 |
| learning 单测 | `mvnw test -Dtest='BktModelTest,LearningQuestionGraderTest,CpuSchedulerTest,PageReplacerTest'` | 28/28 通过（2026-09-29 记录，本阶段 Phase 1 起每阶段复跑） |
| 前端构建 | `npm run build` | DONE，无错误 |
| 前端 lint | `npm run lint` | 0 errors / 78 warnings（均为遗留 `no-explicit-any` 类警告，无新增 error） |
| 学习闭环冒烟 | `tmp/smoke-learning.ps1` | **ALL PASSED（FAILURES = 0）**——含并行执行者新增断言：`correct` 字段命名、raw JSON 无 `correctAnswer`/`answer` 键、重复作答不重复计数、LRU 演示路径、OJ 判题 PASS/FAIL 双事件 |

### 基线期间发现并修复的两个环境级缺陷

1. **pknown/pKnown 重复 JSON 键**：`MasteryNodeVO.pKnown` 是 JavaBeans 陷阱命名（第二字母大写），字段上的 `@JsonProperty("pKnown")` 与 Lombok 生成的 `getPKnown()`（Jackson 推导为 `pknown`）不合并，同一对象输出两个键。PowerShell 5.1 `ConvertFrom-Json` 对重复键直接拒绝整个响应（data=null），导致冒烟第 5 步全挂；浏览器宽容解析所以页面看不出。修复：显式手写 `getPKnown()` 并标注 `@JsonProperty("pKnown")`，单一键名（已验证 127 节点 0 重复）。
2. **回显沙箱 stub 缺失**：冒烟第 9 步的 OJ 判题依赖 remote 沙箱端点；生产工厂只接受 `remote` 类型（demo 类型被硬编码拒绝，属正确的安全约束）。重建 `tmp/sandbox_stub.py`（回显语义：outputList=inputList，判题策略层真实比较用例产生 Accepted/WA），启动方式已写入脚本头注释。

## 2. 并行执行者已完成的改动（2026-09-30 09:50 批次，17 个文件）

本审计执行时发现另一执行者已推进部分 Phase 2 内容，均已纳入基线：

- `LearningAnswerResultVO`：字段重命名 `isCorrect`→`correct`，删除 `correctAnswer`（契约 §2.2 学生答案隔离的类型层落地）；配套 `LearningQuestionServiceImpl`、前端 `api/learning.ts`、`QuestionAnswerCard.vue` 同步。
- `LearningQuestionVO`/`TutorSourceVO`/`LearningEventServiceImpl`/`LearningTutorServiceImpl`/`LearningQuestionServiceImpl` 契约微调。
- 前端导航与页面：`routes.ts`（章节学习/实验项目/在线测评/算法实验室/学习资源）、`ResourcesView.vue`、`ChatWindow.vue`、`QuestionSubmitView.vue`、`UserCenterView.vue`。
- `tmp/smoke-learning.ps1` 升级为第二版（上述新断言）+ `数据库文件/oslab_demo_seed_v3.sql` 更新。

## 3. 图谱完整性缺陷量化（Phase 1 的输入）

| 数据源 | 节点 | 关系 | 孤立节点 |
| --- | --- | --- | --- |
| Neo4j 实际（MATCH 计数） | **150** | **171** | **1** |
| `/api/learning/mastery/graph` 返回 | **127** | **134** | 未包含 |

**缺口：API 丢失 23 个节点（含 1 个孤立节点）与 37 条关系。** 根因与计划 §0.3 一致：`KnowledgeGraphService#getAllKnowledgeGraph()` 按"节点数 × 关系类型数"逐节点查询，孤立节点和未被关系查询覆盖的节点被丢弃；`convertToEChartsFormat` 只从关系结果反推节点，放大缺失。Phase 1 以一次性快照查询修复。

## 4. P0/P1/P2 功能矩阵（当前状态）

| 优先级 | 项 | 状态 |
| --- | --- | --- |
| P0 | 图谱快照（全节点/去重边/稳定方向） | ❌ 待做（缺陷已量化，见 §3） |
| P0 | 双视图基于真实数据（去 curated 演示数据源） | ❌ 待做（Phase 3） |
| P0 | 真实 chapter 字段（当前借用 category） | ❌ 待做（Phase 1） |
| P0 | 双击进动态工作台（删除静态 HTML 映射） | ❌ 待做（Phase 3/4） |
| P0 | 学生/管理员题目 VO 类型层答案隔离 | ✅ 已由并行执行者完成（correct 重命名 + correctAnswer 删除） |
| P0 | 动态节点工作台主页面 | ❌ 待做（Phase 4；现有 NodeWorkbenchDrawer 为抽屉形态） |
| P1 | 证据摘要/前后置/推荐理由增强 | 部分（mastery 已有 recentReason；工作台待扩展，Phase 2/4） |
| P1 | 时间轴 before/after 回放 | ❌ 待做（Phase 8） |
| P1 | 判题失败结构化归因 | ❌ 待做（Phase 7） |
| P2 | OPT、CPU 动画 | 不做（计划明确非硬门槛） |

## 5. 环境与运行说明

- 本地服务：MySQL 8.4 / Redis / Neo4j 4.4.39（Windows 服务，常驻）；后端 8121；前端 dev server 8081。
- 冒烟前置：`python tmp/sandbox_stub.py`（回显沙箱，127.0.0.1:8090）+ 后端以 `CODESANDBOX_TYPE=remote CODESANDBOX_REMOTE_URL=http://127.0.0.1:8090 CODESANDBOX_REMOTE_AUTH_SECRET=smoke-secret` 启动。
- AI（DeepSeek）经用户环境变量注入，导师 AI 模式可用；无 key 时自动降级（已验证）。

## 6. 结论

基线冻结完成。核心闭环（判分/BKT/事件/冒烟）在当前源码+新鲜构建下全绿；图谱完整性缺陷已量化并作为 Phase 1 的第一优先输入。无未记录的环境失败。

## 7. 第二阶段执行状态（滚动更新，2026-09-30）

| Phase | 内容 | 状态 | 验证 |
| --- | --- | --- | --- |
| 0 | 二次审计与基线冻结 | ✅ | 本文 §1-§3 + 台账 |
| 1 | 图谱快照/chapter/孤立节点 | ✅ | KnowledgeGraphServiceTest 10/10；API 150/171=Neo4j |
| 2 | PersonalGraphVO/NodeWorkbenchVO 聚合 | ✅ | LearningMasteryServiceTest 9/9；API 实测 totals/statusCounts/推荐 |
| 3 | 图谱页双视图重构（去 curated） | ✅ | 浏览器实测 150/171 上屏、路径聚焦、fit/reset、双击进工作台 |
| 4 | 动态节点工作台页面 | ✅ | 浏览器实测全区块 + 真实判题事件；19/19 后端测试 |
| 5 | 普通题/题目管理 UI | ✅（错题筛选待后端参数） | 现有筛选/深链接/题卡核对 |
| 6 | 页面置换实验室深链接 | ✅ | build + lint |
| 7 | 规则错误归因 | ✅ | AttributionServiceTest 8/8；工作台实测 WRONG_ANSWER/0.9 |
| 8 | 时间轴 before/after | ✅（工作台时间线已含 before/after）；教师下钻部分 | — |
| 9 | 全站 UI tokens | ⏳ 部分未做 | — |
| 10 | 种子/演示/统一验收脚本重建 | ✅ scripts/dev/verify-learning.ps1（21/21 PASS）+ scripts/dev/sandbox_stub.py | 实测 |
| 11 | 最终验证与交付 | ✅ 自动验证全绿（build DONE/lint 0 err/learning 套件 55+9 全过/全量 74 tests 仅 6 遗留 Errors）；静态审查通过（无 Math.random、无静态详情映射、学生响应无答案键）；双角色实测：学生无管理按钮且 API 被拒(40101/50000)、管理员四管理入口+题目管理+教师看板可见可用、匿名跳登录；教师下钻实测（heatRows=4 → 下钻卡 41%） | 实测 |

### 遗留（deferred）
- LearningQuestionVO.correctAnswer 类型声明仍在（学生 JSON 恒无该键，已实测；后端仅管理员视角回填）。完整 VO 拆分与并行执行者的 in-flight 改动合并后再做，避免冲突。
- tmp/ 目录（含旧版 smoke-learning.ps1）已被清理；统一验收脚本将在 Phase 10 重建于 scripts/dev/（sandbox_stub.py 已重建于此）。
