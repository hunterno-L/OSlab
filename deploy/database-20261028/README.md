# 五章数据库部署包（2026-10-28）

本包是当前运行库 `oslab_db` 的**脱敏快照**，包含第一至第五章的课程内容、题目与图谱数据。它与 `数据库文件/` 下的初始种子不同：初始种子只含第一章单元，本包含当前已导入的五章全部内容。

- 来源：本机 `oslab_db`，代码基线 `47bdcb0`
- 用途：2 GB 无 Docker 服务器或任意全新环境的快速还原
- 已验证：MySQL 包导入临时库成功；Neo4j 脚本通过 772 条语句的 `EXPLAIN` 语法校验

## 1. 内容与数量

| 项目 | 数量 |
| --- | --- |
| MySQL 表 | 46 |
| 课程小节 `course_section` | 101 |
| 学习单元 `learning_unit` | 104 |
| 教学块 `learning_content_chunk` | 261 |
| 知识题 `learning_question` | 916 |
| 题目-节点映射 `learning_question_node` | 1603 |
| 知识节点 `knowledge_node_registry` | 196 |
| 节点关系 `knowledge_node_relation` | 254 |
| 教学资源图 `course_asset` | 95 张（以 BLOB 存储，已包含在 SQL 中） |
| 用户 `user` | 35（3 个演示账号 + 32 个已锁定账号） |
| Neo4j 节点 / 关系 | 346 / 425（含 150 个旧图谱节点） |

文件：

- `oslab_db-deploy.sql.gz` —— MySQL 数据（约 14 MB）
- `neo4j-graph-full.cypher` —— Neo4j 全量图谱（346 节点 / 425 关系）
- `SHA256SUMS` —— 校验和

## 2. 脱敏说明

已删除或清空：

- 学习记录：`learning_attempt`、`learning_event`、`learning_mastery`、`learning_review`、`learning_assessment_run`
- 学习进度与演示记录：`learning_activity_progress`、`learning_activity_record`
- 代码提交：`question_submit`
- 任务提交与附件：`learning_task_item_work`、`learning_task_submission`、`learning_task_file`
- 旧花名册：`students`
- 历史备份表：全部 `*_bak_0929`
- 非演示账号的密码哈希、微信标识、头像、资料与姓名；其密码字段统一置为 `LOCKED`，无法登录

保留：

- 课程目录、单元、教学块、知识题、节点、关系、内容修订历史、教学资源图
- 三个演示账号及其原有 BCrypt 口令
- `nana` 等账号行（用于内容作者与历史引用），但密码已锁定

已扫描导出文件，未发现明文口令、火山方舟/AI 密钥或云存储密钥。

## 3. MySQL 恢复

导入前先按主文档创建空的 `oslab_db` 与应用账号。导出文件**不含 `DROP TABLE`**，导入到已有库会直接报错，不会覆盖数据。

```bash
gunzip -c oslab_db-deploy.sql.gz \
  | sudo mysql --default-character-set=utf8mb4 oslab_db
```

校验：

```bash
sudo mysql oslab_db -e "
SELECT COUNT(*) AS units FROM learning_unit;
SELECT COUNT(*) AS questions FROM learning_question;
SELECT COUNT(*) AS chunks FROM learning_content_chunk;
SELECT COUNT(*) AS nodes FROM knowledge_node_registry;
SELECT COUNT(*) AS assets FROM course_asset;"
```

预期：`104 / 916 / 261 / 196 / 95`。

## 4. Neo4j 恢复

脚本使用 `CREATE` 而非 `MERGE`，**只能导入空图数据库**。不要与 `数据库文件/graph.db.dump` 或 `oslab_graph_chapters.cypher` 同时使用，否则会产生重复节点。

```bash
sudo -u neo4j env JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  cypher-shell -a bolt://127.0.0.1:7687 -u neo4j -p '<Neo4j密码>' \
  -f neo4j-graph-full.cypher
```

校验：

```bash
sudo -u neo4j env JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  cypher-shell -a bolt://127.0.0.1:7687 -u neo4j -p '<Neo4j密码>' \
  "MATCH (n:Entity) RETURN count(n) AS nodes;"
```

预期 `346`。

## 5. 演示账号

| 账号 | 角色 | 密码 |
| --- | --- | --- |
| `teacher_demo` | 管理员 | `oslab123456` |
| `student_demo1` | 学生 | `oslab123456` |
| `student_demo2` | 学生 | `oslab123456` |

公开部署前必须修改这些口令，并锁定或删除不需要的账号。

## 6. 注意事项

- 本包不包含学生的作答、事件、掌握度与复习计划；新环境从干净的学习状态开始。
- 教学图片以 `course_asset.contentBytes` 形式存于 MySQL，SQL 包已包含，不需要额外拷贝图片目录。
- 需要保留现有学习记录时，不要使用本包，应改用本机未脱敏的完整备份。
- 导入顺序：MySQL → Neo4j → 启动后端 → 打开 `/teaching/content` 检查内容状态。
