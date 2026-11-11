# 第一章发布回执（2026-10-20）

执行人：L 授权的实施 agent。分支 `feature/learning-loop-ui-rework`，代码基线 `93ccf44` + 工作区课程重构改动。发布前数据库备份在 `tmp/release-20261020/oslab_db-before-release.sql`（不随仓库交付）。

## 结果

| 项目 | 结果 |
| --- | --- |
| 外部题来源 | 3 条 `EXTERNAL_QUESTION` 来源已登记，`authorized=true` |
| 六题修订 | preview `updated=6`，commit `import.assessment.2112515121160593413`；六题均为 revision 2 |
| 第一章题目 | 103/103 PUBLISHED（97 道 wd25 + 6 道 ext） |
| 全部题目 | 203 道有效题：200 PUBLISHED、2 DRAFT、1 SUBMITTED（均为旧章节遗留） |
| 教学块 | 57/57 PUBLISHED |
| 学习单元 | 10/10 PUBLISHED；92 个活动、66 条节点映射、107 条配套题映射 |
| 图谱同步 | `SYNCED`，pending=0 |
| 来源总数 | 7（含 3 条外部题来源） |
| 资源 | 29 项仍为 DRAFT（18 项算法演示未启用为课程活动；独立播放器不受影响） |

## 学生路径

使用 `student_demo2` 从第一单元走到章末复习：

- 10/10 单元 `COMPLETED`，92/92 活动完成；首单元含 1 道故意答错题，`completed=1 / passed=0`，单元 `goalStatus=REVIEW_RECOMMENDED`；其余 32 道作答判对。
- 错题事件只落在 `os.overview.operating_system`，首次答错掌握度 0.1 → 0.11232876712328767（RED），符合 BKT。
- 课程最终 `COURSE_COMPLETED`；复习模式定位 `mainlineUpdated=false`，不倒退主线。
- 单元题目量与题目练习目录计数一致；六道修订题可在独立题库找到。
- 62 项学生路径检查与 11 项掌握度投影检查全部通过，见 `07-student-path.json`、`07b-mastery-projection.json`。

## 发布期间发现并修复

1. **实践维度误显示 0%**：概念学习者没有实践证据时，掌握度投影返回 `practiceScore=0`。已修正后端投影（无证据返回 null），并在图谱/工作台/记录页显示“未评估”。
2. **课程标题乱码**：`course_catalog` 的标题/描述是历史错误编码（“鎿嶄綔绯荤粺”），已恢复为“操作系统”/“教材驱动课程；第一章正文与测评待成员材料审核发布”。
3. **测试数据污染**：一次全量后端测试连接了开发库，写入 14 道测试题及 28 次作答、46 条事件、36 条审核、14 条节点映射。经 L 明确授权后按题目 ID 精确清理；题目总数恢复为 203（200 PUBLISHED），student_demo2 的 92 条进度与掌握度未受影响。审计清单见 `test-data-cleanup-audit.tsv`。

## 验证

- 后端：全量测试 211 run / 0 failures / 0 errors / 1 skipped（该次运行后已清理其开发库污染；后端此后未再改动）。
- 前端：发布阶段构建 hash `5f3f0e8c621b467e`；反馈修正后最终构建 hash `75710cc0a0958966`。
- 内容：`validate-course-content.mjs` 与 `validate-first-chapter-draft.mjs` 通过。
- 未执行：旧节点/旧题清理；18 项演示资源发布；提交/推送/部署。L 已人工查看（2026-10-21），未使用浏览器自动化。

## 回执文件

`02-sources.json`、`03a-revision-preview.json`、`03b-revision-commit.json`、`03c-question-publish.json`、`04-chunk-publish.json`、`05a-units-preview.json`、`05b-units-commit.json`、`06a-unit-publish.json`、`06b-graph-sync.json`、`07-student-path.json`、`07b-mastery-projection.json`、`test-data-cleanup-audit.tsv`。
