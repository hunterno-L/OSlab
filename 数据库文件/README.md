# 数据库安装与升级

本目录是程序运行输入，不属于可删除的临时导出。`oslab_db.sql`、图数据及 v3–v14 迁移被部署脚本引用，需与对应代码版本一同保留。

## 路径 A：基础首装

Docker Compose 的新空数据卷会自动执行 `deploy/mysql-init/10-load-oslab-db.sh`，按以下顺序导入。已有数据卷不会再次执行初始化脚本。

| 顺序 | 文件 |
| --- | --- |
| 1 | `oslab_db.sql` |
| 2 | `oslab_upgrade_v3_learning.sql` |
| 3 | `oslab_upgrade_v4_flow.sql` |
| 4 | `oslab_upgrade_v5_teaching.sql` |
| 5 | `oslab_demo_seed_v3.sql` |
| 6 | `oslab_seed_v4_content.sql` |
| 7 | `oslab_upgrade_v6_courses.sql` |
| 8 | `oslab_upgrade_v7_identity.sql` |
| 9 | `oslab_upgrade_v8_teaching_tasks.sql` |
| 10 | `oslab_upgrade_v9_learning_activities.sql` |
| 11 | `oslab_upgrade_v10_classes.sql` |
| 12 | `oslab_upgrade_v11_ai_session.sql` |
| 13 | `oslab_upgrade_v12_ai_evidence.sql` |
| 14 | `oslab_upgrade_v13_learning_selftest.sql` |
| 15 | `oslab_seed_v14_experiment_prerequisites.sql` |
| 16 | `oslab_upgrade_v14_experiment_judge.sql` |
| 17 | `oslab_upgrade_v14_experiment_comments.sql` |

本机可创建空的 `utf8mb4` 数据库，使用同一顺序逐个导入；命令在仓库根目录执行，例如：

```bash
mysql -u root -p -e "CREATE DATABASE oslab_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql --default-character-set=utf8mb4 -u root -p oslab_db < 数据库文件/oslab_db.sql
# 继续按表格顺序导入后续文件；每条成功后再执行下一条，不使用 --force 吞错。
```

Windows PowerShell 可在 MySQL 交互客户端使用 `SOURCE` 加文件绝对路径，避免使用不支持的 shell 输入重定向。数据库应用账号仅授予该库所需权限，将账号与口令写入本机 `.env`；不要把真实口令写进 SQL。

基础库已内置 v2 的唯一账号索引，不要再执行 `oslab_upgrade_v2.sql`。基础首装包含学习种子和 41 项实验，但不等于五章完整教学内容；完整内容可由教师导入或采用下述快照。

Neo4j 4.4 的空数据目录由 Compose 的 seed 服务加载 `graph.db.dump`。本机加载 dump 必须停库并使用兼容的 `neo4j-admin`；具体命令以安装版本为准。不要与下述全量 Cypher 同时导入。`oslab_graph_chapters.cypher`、`oslab_upgrade_v6_courses_graph.cypher` 供对应历史阶段增量使用，不是重复初始化步骤。

## 路径 B：五章教学快照

[deploy/database-20261028](../deploy/database-20261028/README.md) 保存脱敏的五章快照及校验和。该快照包含到 v10 的结构和五章内容，应恢复到**另一个空库**，不与路径 A 的基础 SQL 叠加。

1. 根据快照 `SHA256SUMS` 核验完整性。
2. 将压缩 SQL 导入新空 MySQL 库，并按快照说明导入**空 Neo4j 图库**。
3. 在恢复的 MySQL 库继续执行上表 **第 12–17 项**，补齐 v11/v12 AI、v13 自测和 v14 实验更新。
4. 检查表、课程、实验数量与页面后，再让程序指向新库。不要在正在运行的业务库试验恢复。

快照 SQL 不带 `DROP TABLE`；恢复失败时换新空库重新验证。两个初始化路径不能混用。Compose 默认采用路径 A；路径 B 是独立的人工恢复流程，不会由首装脚本自动选取。

## 已有库

先备份 MySQL、Neo4j 和任务附件，再检查当前版本。`oslab_upgrade_existing_check.sql` 辅助检查早期结构，但不替代全部 v6–v14 升级。只执行尚缺的版本；尤其不要对已有 v4 列的库重复执行未加条件的 `ALTER`。

v14 文件较大，保存有完整审核修订与快照。它们是版本化迁移输入，应保持原文件内容和顺序；新注释或内容调整使用后续迁移，不能反复重生成旧文件。

## 账号

基础种子保留历史作者账号用于资源引用，README 不提供其口令。学生通过页面注册；首次教师账号可先注册，再由数据库管理员仅将该账号的 `userRole` 改为 `admin`，完成后用教师账户维护班级。

```sql
-- 将此占位账号替换为本次注册且确认归属的账号；不在 SQL 中设置或暴露口令。
UPDATE `user` SET userRole='admin'
WHERE userAccount='your_registered_teacher_account' AND isDelete=0;
```

五章快照自带 `teacher_demo`、`student_demo1`、`student_demo2`，仅供隔离本机演示，初始示例口令见快照说明。公网部署前重置或停用演示账号；这些示例账户不是生产凭据。
