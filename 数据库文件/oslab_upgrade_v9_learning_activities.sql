-- v9: 普通算法演示运行记录（与掌握证据账本分离）
-- Apply after oslab_upgrade_v8_teaching_tasks.sql. Idempotent and additive.
-- 该表只记录学生完整运行一次的普通演示（算法、输入快照、结果摘要），
-- 不参与 BKT/掌握度更新；判分型模拟仍写 learning_event。

CREATE TABLE IF NOT EXISTS learning_activity_record (
  id BIGINT NOT NULL PRIMARY KEY,
  userId BIGINT NOT NULL,
  activityType VARCHAR(32) NOT NULL,
  title VARCHAR(200) NOT NULL,
  algorithmKey VARCHAR(64) NULL,
  courseCode VARCHAR(128) NULL,
  unitCode VARCHAR(128) NULL,
  activityCode VARCHAR(128) NULL,
  inputJson JSON NULL,
  resultJson JSON NULL,
  occurredAt DATETIME NOT NULL,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_activity_record_user_time (userId, occurredAt),
  KEY idx_activity_record_user_type (userId, activityType)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
