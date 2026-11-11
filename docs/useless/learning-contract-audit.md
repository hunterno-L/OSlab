# learning 模块契约审计（Phase 0，2026-09-29）

对照《知识图谱智能学习闭环执行计划.md》第 3 节冻结契约，对现有 learning 原型逐文件审计。基线验证：后端 `mvnw -DskipTests compile` 与 `mvnw test`（BKT/判分/模拟器 4 套件）、前端 `npm run build` 均通过（2026-09-29 上午记录）。

## 结论概览

- 契约 14 个 API 全部已存在，路径正确；无需新增端点。
- 核心偏差：learning_mastery 缺少 conceptScore/practiceScore 及两类证据计数；BKT 缺 pLearn 参数；状态阈值 0.4/0.8 ≠ 契约 0.35/0.70 且缺"两类证据"GREEN 条件；学生作答响应下发了 correctAnswer（违反答案保密红线）。
- 违禁/超范围：learning_class_member、learning_annotation 两张表及全部配套代码必须删除；教师聚合改用 students 表。

## 处置清单

### 后端

| 文件 | 处置 | 理由 |
| --- | --- | --- |
| controller/LearningQuestionController | 修改 | 删除多余 POST /learning/questions/delete |
| controller/LearningMasteryController | 修改 | 删除多余 GET /learning/mastery/node/mastery |
| controller/LearningSimulationController | 保留 | 与契约一致 |
| controller/LearningTeacherController | 重写 | 删除 annotations 三端点与 FORBIDDEN class/member/add |
| controller/LearningTutorController | 保留 | 与契约一致（限流为附加项） |
| service/LearningEventService + Impl | 重写 | concept/practice 分项更新、复合 pKnown、5 参 BKT、simKey→simulationKey |
| service/LearningMasteryService + Impl | 重写 | 移除 observationCount 依赖，输出双分项 |
| service/LearningQuestionService + Impl | 修改 | 作答响应不再返回 correctAnswer；删除 delete/RESUBMIT；hasViewedAnalysis 语义修正 |
| service/LearningSimulationService + Impl | 保留 | 服务端真值判分正确 |
| service/LearningTeacherService + Impl | 重写 | 班级解析改用 students（userId/className）；删除 annotations |
| service/LearningTutorService + Impl | 保留 | 检索/引用/降级符合契约 |
| bkt/BktModel | 重写 | update 改为 5 参 (pKnown, correct, pLearn, pGuess, pSlip) |
| learning/LearningQuestionGrader、CpuScheduler、PageReplacer | 保留 | 纯函数，契约一致 |
| mapper/LearningClassMemberMapper、LearningAnnotationMapper | 删除 | 违禁/超范围表 |
| model/entity/LearningMastery | 重写 | 去 pLearn/pGuess/pSlip/observationCount，加 conceptScore/practiceScore/两类证据计数 |
| model/entity/LearningQuestionNode | 修改 | 加 weight |
| model/entity/LearningClassMember、LearningAnnotation | 删除 | 违禁/超范围 |
| model/dto、vo、enums 中的 Annotation* 与 MasteryNodeVO(observationCount) | 删除/重写 | 同步契约字段 |
| enums/MasteryStatusEnum | 重写 | RED<0.35、YELLOW<0.70、GREEN≥0.70 且证据类型≥2 |
| enums/ReviewActionEnum | 修改 | 移除 RESUBMIT |
| judge/JudgeServiceImpl | 保留 | 终态挂钩（L134/L144）签名与契约一致 |
| test/bkt/BktModelTest | 重写 | 适配 5 参与阈值 |
| test/learning/LearningQuestionGraderTest | 保留 | 契约一致 |

### SQL

| 文件 | 处置 | 内容 |
| --- | --- | --- |
| 数据库文件/oslab_upgrade_v3_learning.sql | 重写 | 仅 6 张 P0 表；learning_question_node 加 weight；learning_mastery 换为契约列；删 class_member/annotation |
| 数据库文件/oslab_demo_seed_v3.sql | 修改 | 删除 class_member/annotation 种子；题目种子保留（910001-910010） |

### 前端

| 文件 | 处置 | 理由 |
| --- | --- | --- |
| src/api/learning.ts | 重写 | 改用全局 axios 实例（修复拦截器不生效缺陷）；全程 TS 类型；删除 delete/annotations 函数 |
| views/learning/TeacherDashboardView.vue | 修改 | 删除批注卡片；热力阈值 0.35/0.70 |
| views/learning/ManageLearningQuestionsView.vue | 修改 | 删除删除按钮与 RESUBMIT |
| views/learning/LearningQuestionsView.vue | 修改 | 补章节筛选；去 any |
| views/learning/SimulationView.vue | 保留 | 清理死字段 |
| components/learning/NodeWorkbenchDrawer.vue | 修改 | 概念/实践双进度条；去 observationCount；状态文案 待巩固/已掌握 |
| components/learning/QuestionAnswerCard.vue | 修改 | 不再展示 correctAnswer |
| views/knowledgeGraph/KnowledgeGraphView.vue | 修改 | 阈值与状态色对齐契约（Phase 4 按视觉基准重构） |
| router/routes.ts | 保留 | 4 条 learning 路由无重复 |

## 共享文件触点

- JudgeServiceImpl.java L20/L53-54/L134-135/L144-145（保留不动）
- deploy/mysql-init/10-load-oslab-db.sh L8-16（注释 8 张表→6 张，其余不动）
- routes.ts L17-20/L54-84（保留）

## 执行顺序

Phase 1 SQL/实体 → Phase 2 判分与审核 → Phase 3 事件/BKT → Phase 4-8 前端与教师视图 → Phase 9 种子 → Phase 10 清理 → 第 16 节集成验证。
