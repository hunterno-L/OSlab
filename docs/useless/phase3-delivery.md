# OSLab 第三阶段交付报告（Phase 6-10，§10-§14）

- 执行依据：`OSLab第三阶段执行计划-学习流程与UI重构.md` §10-§14
- 报告日期：2026-10-02
- 证据口径：仅采信本阶段（Phase 10 验证窗口）实际运行的命令输出；上一阶段阶段报告不作为通过证据。

## 1. 自动验证（§14.1）

### 1.1 后端（2026-10-02 16:05 全量运行）

```
./mvnw.cmd test
Tests run: 113, Failures: 0, Errors: 6, Skipped: 1
[INFO] BUILD FAILURE   ← 由 6 个既有模板测试 Errors 导致，非本阶段引入
```

6 个 Errors 全部为遗留模板测试（与 `docs/phase3-baseline.md` 基线一致，来源 surefire-reports）：

| 测试类 | Error 数 | 原因 |
| --- | --- | --- |
| manager.CosManagerTest | 1 | 本地无 COS 凭据 |
| mapper.PostMapperTest | 1 | post 表不存在（BadSqlGrammar） |
| mapper.PostFavourMapperTest | 1 | post_favour 表不存在 |
| service.PostFavourServiceTest | 2 | 同上 |
| service.PostThumbServiceTest | 1 | post_thumb 表不存在 |

单列说明：以上 6 个 Errors 均为第二阶段交接前即存在的模板测试问题，本阶段未修改相关代码。后端编译验证包含在全量测试的编译阶段（同日 15:47 另有 `-DskipTests compile` → COMPILE OK）。

### 1.2 后端专门套件对照（§14.1 清单 vs 实际）

全量 113 个测试包含本阶段全部新测试，实际类名与计划清单对照：

| 计划测试类 | 实际落实 |
| --- | --- |
| LearningCourseServiceTest | 未以独立类实现（章节/覆盖率逻辑无专门单测） |
| LearningQuestionProgressTest | 已实现，含于全量通过 |
| LearningAssessmentRunTest | 未以独立类实现（测评续做由 LearningQuestionProgressTest 部分覆盖） |
| LearningPathServiceTest | 已实现（多前置/方向/环/无资源节点/真实 action，Phase 6 通过 4/4） |
| LearningSnapshotServiceTest | 未以独立类实现；快照逻辑由 MasterySnapshotCodecTest + LearningEventServiceTest + LearningRecordsServiceTest 分摊覆盖 |
| LearningEventServiceTest | 已实现，含于全量通过 |
| LearningTutorServiceTest | 未实现（tutor 依赖外部 AI，接口层验证见 §1.5 与 2026-09-28 AI 实测记录） |
| LearningTeacherNodeDetailTest | 并入 LearningTeacherServiceTest（classes/nodeDetail/coverage/导入/素材，Phase 8 通过 7/7） |
| PageReplacerTest（OPT 样例与对照） | 已实现（OPT 固定样例 + FIFO/LRU 对照，含于全量通过） |

### 1.3 前端

```
npm run build          → DONE Build complete（92.9s，2026-10-02 15:49）
npx vue-cli-service lint --no-fix
  → ✖ 61 problems (0 errors, 61 warnings)，exit 0（2026-10-02 16:17，与基线一致）
```

未完成项：§14.1 要求的前端组件测试（路由活动项、query 改变、单题状态机、编辑器留页结果）未实现——仓库前端工具链（vue-cli + TS4.5，package.json 仅 serve/build/lint 三个 script）没有现成测试通道，本阶段未新增，如实标注为"未完成"，以人工 UI 审核替代覆盖。

### 1.4 术语检查（§14.2）

```
powershell -ExecutionPolicy Bypass -File scripts/dev/check-ui-terminology.ps1
→ PASSED（退出码 0，无未豁免命中；扫描 310 个文件）
```

- 检查词码点 `0x666E,0x901A,0x9898`（"普通题"）。计划原文写作 `0x901D`（"逝"）系笔误，已按正确目标词 `0x901A`（"通"）执行，并在脚本注释中说明。
- 范围：前端 src、后端 src/main/java（仅字符串字面量）、演示脚本.md、docs 与计划文档；历史审计文档（learning-phase2-audit 等 4 份不可变记录）整体跳过。
- 豁免 5 处（均为数据值而非用户可见文案）：KnowledgeGraphView.vue:1133（evidence.source 映射键，显示层已映射为"题目"）、LearningEventServiceImpl:114 与 LearningAttributionService:111（evidence.source 数据值）、LearningEventTypeEnum:8 与 LearningResourceTypeEnum:8（枚举数据文本，getText() 未用于显示）。
- 本阶段修复：LearningTutorServiceImpl.java 检索轨迹"已发布普通题"→"已发布题目"（用户可见文案）。
- 动态数据文案（登录简介、通知、解析、来源、管理审核提示）列入人工审核清单。

### 1.5 全链路联调（stub 沙箱，2026-10-02 16:19）

```
powershell -ExecutionPolicy Bypass -File scripts/dev/verify-learning.ps1
→ 全部断言 [PASS]，FAILURES = 0，ALL PASSED
```

覆盖：三账号登录；个人图谱全量快照（150 节点）；测评题审核链（创建→SUBMIT→APPROVE→PUBLISH）；作答判分（correct=false、首次作答、题面无 correctAnswer 泄露、BKT 答错不升掌握度）；工作台（归因 WRONG_ANSWER、模拟器入口）；教师端（学生 40101 拒绝、班级 os2026、时间轴 total=269、节点下钻 41 题与误区统计）；交互模拟（FIFO/LRU/OPT 运行缺页 10/9/7 交叉验证、FIFO/CPU 预测写事件 eventId、OPT 预测判分不写事件）；编程实验（真实提交→留页轮询 15s 内终态→Wrong Answer/Accepted 双路径→他人 40101/管理员放行/本人可查→JUDGE 事件 FAIL/PASS 可追溯）。

真实执行独立记录：本地无 Docker CLI，真实沙箱编译/运行未验证（与 stub 联调分开记录）。真实执行样例已备：`scripts/dev/samples/`（lru_correct.c / lru_wrong.c / compile_error.c / runtime_error.c / timeout.c），供有 Docker 环境时按 `演示脚本.md` 验证。

## 2. 交付表（§14.4）

| 项目 | 状态 | 完成证据 |
| --- | --- | --- |
| UI 与路由 | 已实现且验证（构建级） | 九导航入口（知识图谱/章节学习/实验项目/在线测评/算法实验室/学习资源/学习记录/题目管理/教师看板）；旧深链接修复；前端 build DONE + lint 0 error。活动项视觉归人工审核 |
| 课程与素材 | 已实现且验证 | 两知识链 12 节点素材 PUBLISHED（912001-912012）+ 12 道新题（910015-910026，四题型 4/4/2/2）+ 23 条映射，本地库入库核对；verify §2 全量快照 150 节点；学习页签素材渲染（contentMd + resourceLinks） |
| 知识测评 | 已实现且验证（接口级） | 四题型判分（GraderTest）；审核链四步 code=0；答案隔离（无 correctAnswer 键）；BKT 方向断言；LearningQuestionProgressTest 通过。页面视觉待人工 |
| 编程实验 | 已实现且验证（stub 级） | 留页轮询至终态（WA status=2 / Accepted 双路径）；详情权限三分（他人 40101/管理员/本人）；JUDGE 事件 PASS/FAIL 追溯；草稿保留（localStorage 按 用户+实验+语言 隔离，ViewQuestionView.vue:224-235）。真实沙箱执行未验证（无 Docker CLI） |
| 交互模拟 | 已实现且验证 | FIFO/LRU/OPT 缺页 10/9/7 交叉验证；FIFO/CPU 预测判分且写学习事件（eventId 非空）；OPT 预测判分不写事件（eventId=null）；PageReplacerTest OPT 固定样例 |
| 图谱和路径 | 已实现且验证 | LearningPathServiceTest（多前置/方向/环/无资源/真实 action）4/4；图谱行动入口上线；全图 150 节点与节点下钻局部计数（studentCount=2、questions=41） |
| 学习记录 | 已实现且验证（接口级） | timeline total=269（历史事件兼容）；STATE_BASELINE 基线事件；快照 before/after 同事务写入（Codec/Event/Records 三测试类覆盖）；/records 两页签前端已实现，视觉待人工 |
| 教学管理 | 已实现且验证 | 班级列表含 os2026；node/detail 映射匹配 41 题、误区统计非空；LearningTeacherServiceTest 7/7；题目 JSON 导入与素材编辑（前端 build 级验证），交互待人工 |
| 验证 | 已实现且验证（自动部分） | 见 §1：后端 113/0/6（6 均为遗留模板 Errors）、前端 build+lint、术语检查 PASSED、stub 联调 ALL PASSED。未完成：前端组件测试、真实沙箱执行、CourseService/AssessmentRun/Tutor 独立测试类 |
| 视觉与文案 | 已实现待验证（人工） | 术语检查 PASSED（含 5 处数据值豁免与码点笔误说明）；浅色克制风格延续认证改版基线；人工 UI 审核清单见 §3，等待用户执行 |

## 3. 人工 UI 审核清单（§14.3，待用户执行）

- 入口：http://localhost:8081
- 账号（密码均为 `oslab123456`）：
  - `teacher_demo`：管理员/教师，可看教师看板、题目管理、图谱素材编辑
  - `student_demo1` / `student_demo2`：学生，班级 os2026（两人数据互查验证隔离）
- 分辨率：1440 / 1920 桌面与 390 窄屏
- 检查点（每页均查遮挡与状态可辨）：
  1. 登录页能力简介与知识流动效不干扰表单
  2. 章节学习：长节点名、素材渲染、资源链接
  3. 知识测评：长题干、四题型作答、刷新续做、总结与待巩固
  4. 实验项目：代码编辑器、等待判题（可辨状态）、失败/通过反馈、草稿保留（切走再回来代码还在）
  5. 算法实验室：步进/播放/预测交互、三算法对比
  6. 学习记录：本人时间线、快照回放（无证据/有数据状态可辨）
  7. 知识图谱：个人掌握叠加着色、行动入口、（管理员）素材编辑弹窗
  8. 教师看板（teacher_demo）：班级选择、节点下钻、误区统计、JSON 导入弹窗
  9. 动态文案：通知、解析、AI 来源标题、审核提示无"普通题"旧术语
- 提示：服务由本地脚本启动，见 §4；审核通过后停止服务。

## 4. 本地服务启停（复现命令）

```powershell
# 依赖服务：MySQL(3306, root 无密码, oslab_db) / Redis(6379) / Neo4j(7687, 密码 11111111) 均为本机 Windows 服务
# 1. 回显沙箱（scripts/dev/sandbox_stub.py，127.0.0.1:8090，密钥 smoke-secret）
python scripts/dev/sandbox_stub.py
# 2. 后端（关键 env 见 tmp/run-backend-e2e.ps1，端口 8121）
#    CODESANDBOX_TYPE=remote; CODESANDBOX_REMOTE_URL=http://127.0.0.1:8090/executeCode
#    CODESANDBOX_REMOTE_AUTH_SECRET=smoke-secret; SPRING_NEO4J_AUTHENTICATION_PASSWORD=11111111
./mvnw.cmd spring-boot:run
# 3. 前端 dev（8081，/api 代理 8121）
npm run serve
# 4. 全链路验收
powershell -ExecutionPolicy Bypass -File scripts/dev/verify-learning.ps1
```

验收后清理：`tmp/run-backend-e2e.ps1`、`tmp/sandbox_stub.py`（与 scripts/dev/sandbox_stub.py 重复的临时副本）及 tmp 下各服务日志。

## 5. 本阶段（Phase 10）改动文件

| 文件 | 改动 |
| --- | --- |
| scripts/dev/check-ui-terminology.ps1 | 新建：术语静态检查（码点组合、Java 字面量启发式、历史文档跳过、5 处豁免） |
| 后端 LearningTutorServiceImpl.java | 检索轨迹文案"已发布普通题"→"已发布题目" |
| scripts/dev/verify-learning.ps1 | 修复 UTF-8 BOM 丢失（Phase 8 编辑后遗留，导致 PS5.1 按 ANSI 误读中文报解析错误） |
| docs/phase3-delivery.md | 新建：本报告 |
| tmp/run-backend-e2e.ps1、tmp/sandbox_stub.py | 临时联调脚本（验收后清理） |
