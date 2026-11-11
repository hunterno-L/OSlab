# 教师端内容管理与学习任务 — 实施基线

更新：2026-10-21
执行依据：`docs/教师端内容管理与学习任务执行计划.md`

## 1. 工作区与环境

- 分支：`feature/learning-loop-ui-rework`，工作区已有大量未提交修改，全部保留，不执行自动提交/推送/部署。
- JDK：`JAVA_HOME=C:\Users\one\.jdks\temurin-1.8.0_504`；另有 `ms-21.0.10` 可用。`pom.xml` 同时声明 `java.version=1.8` 与 compiler `source/target=14`，实际构建使用 JDK 21 运行 Maven wrapper，产物按项目现有约定部署。
- Maven：系统 PATH 无 `mvn`，使用 `后端代码/osLab-backend/mvnw`（Maven 3.8.1 wrapper）。
- Node：v24.15.0 / npm 11.12.1；前端只有 `serve/build/lint` 脚本，没有 `npm test`。
- MySQL：本机 3306 已运行，`oslab_db` 已应用至 `oslab_upgrade_v7_identity.sql`。
- 数据样本：33 个用户（1 个 admin：`nana`），26 条学生名册记录均绑定用户；5 个班级；64 个实验、234 道普通题（含 PUBLISHED）、10 个学习单元、1 门课程。
- 迁移编号：现有最新为 v7，本轮新增 `oslab_upgrade_v8_teaching_tasks.sql`。

## 2. 复用点（已核对源码）

| 能力 | 复用方式 |
| --- | --- |
| 教师身份 | `UserRoleEnum` 仅 user/admin/ban；教师端继续使用 ADMIN，服务层再校验 `creatorId`。 |
| 名单 | `students(className,studentNumber,userId)` + `LearningTeacherServiceImpl.classes()`；发布时快照，不新建名册。 |
| 普通题判分 | `LearningQuestionServiceImpl.answerQuestion` + `LearningQuestionGrader`；任务副本仍走该链路。 |
| 代码判题 | `QuestionSubmitServiceImpl.doQuestionSubmit` → `JudgeServiceImpl` → 沙箱；任务副本仍走该链路。 |
| 学习事件/掌握 | `LearningEventServiceImpl.recordAttemptEvents/recordJudgeEvents`，任务上下文通过 attempt/submit 带入事件。 |
| 题目修订 | `TeachingAssessmentService` 保留稳定 `questionCode` 与修订/发布。 |
| 实验维护 | `ExperimentMaintenanceService` 保留原 `questionId`；本轮补表单友好的草稿创建与应用入口。 |
| 节点关系 | `knowledge_node_registry` + `knowledge_node_relation`，同步复用 `CourseGraphSyncService`。 |
| 单元 | `learning_unit.draftJson/publishedJson`；教师页仍写草稿，发布快照不变。 |
| 事务 | `learningSqlTransactionManager` 为 `@Primary`，任务发布/提交/批阅使用普通 `@Transactional` 即走 MySQL。 |
| 附件响应 | 复用课程下载的流式响应写法；新增任务私有目录与授权。 |

## 3. 本轮固定契约

### 3.1 任务状态

- 任务：`DRAFT → PUBLISHED → CLOSED`，`isArchived` 独立。
- 接收人：`PENDING / IN_PROGRESS / SUBMITTED / RETURNED / REVIEWED`；逾期由 `dueAt + allowLate` 推导，不落状态。
- 轮次：首次提交 `roundNo=1`；退回后下一次保存/提交进入 `roundNo+1`；历史轮次与文件不覆盖。
- 幂等：`rowVersion` 乐观锁；发布只发生一次；正式提交 `(recipientId,submissionKey)` 唯一。

### 3.2 内容块

`itemType ∈ TEXT / MATERIAL / QUESTION / EXPERIMENT`；`sourceMode ∈ REFERENCE / AUTHORED / (空)`。

草稿 `draftJson` 保存编辑内容；发布时 `publishedSnapshotJson` 保存完整快照（含答案/用例，仅服务端与教师可用）。题目/实验发布时生成 `learning_question` / `question` 私有执行副本：

- `resourceScope = TASK`，`ownerTaskCode`，`ownerTaskItemCode`；
- 副本 ID 写入 `runtimeResourceId`，判分/判题全部使用副本；
- 通用题目/实验列表、候选池、计数、`/learning/questions/*`、`/question/*` 不返回 TASK 行；
- 任务副本仅能通过任务上下文访问。

### 3.3 资源可见性与证据

- 新增 `evaluationIdentity`：引用题按“来源稳定代码 + 规范化判分内容指纹”生成；自编题按规范化题干/选项/答案/主节点生成。首次独立观测按身份去重，历史空身份记录按原 `questionId` 兼容。
- `learning_attempt / question_submit / learning_event` 增加 `sourceTaskCode / sourceTaskItemCode / sourceTaskRound`；课程来源字段保持原义。
- 接收、下载、教师批阅、任务总分不写掌握度；只有真实作答/判题事件进入 BKT。

### 3.4 附件

- 目录：`oslab.tasks.storage-dir`，默认 `./data/task-files`，位于私有目录。
- 每文件 ≤ 10 MiB；允许 `doc/docx/pdf/txt/md/png/jpg/jpeg/webp`；每块 ≤ 5 个附件。
- 存储键服务端生成；下载按任务身份授权，返回原文件名、`nosniff`。正式提交引用的文件不可覆盖/删除。

## 4. v8 迁移清单

新增表：`learning_task`、`learning_task_item`、`learning_task_recipient`、`learning_task_item_work`、`learning_task_submission`、`learning_task_file`。

扩展表：

- `learning_question` / `question`：`resourceScope`（默认 SHARED）、`ownerTaskCode`、`ownerTaskItemCode`；
- `question`：`publicationStatus`（旧有效实验默认 PUBLISHED）；
- `learning_attempt` / `question_submit`：任务来源字段、`evaluationIdentity`；
- `learning_event`：任务来源字段。

## 5. 交付顺序

0. 基线（本文件）
1. v8 迁移、实体/Mapper、资源作用域与附件
2. 教师内容编辑补齐（实验草稿/应用、节点关系、单元表单接口）
3. 教师任务草稿/预览/名单/发布
4. 学生接收/保存/作答/判题
5. 正式提交/批阅/退回
6. 导航与前端页面
7. 编译、迁移、HTTP 闭环与交付文档
