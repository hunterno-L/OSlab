-- v11: ZhiCore AI 升级 —— 统一学习会话、导师轮次与资料索引。
-- Apply after oslab_upgrade_v10_classes.sql. Idempotent and additive.
-- 只新增表：不修改既有表结构，不删除、不发布任何既有数据。
-- 说明：
--   1) 长整型主键由应用侧 MyBatis-Plus ASSIGN_ID（雪花）生成，故不使用 AUTO_INCREMENT；
--   2) 会话/轮次/计划一律持久化，刷新、重启后可恢复，不允许只存在于内存；
--   3) embedding 向量保存在 MySQL（vectorJson），应用内计算相似度，不引入向量数据库或 ES；
--   4) 不同 embedding 模型的向量不得混用：ai_material_chunk.blockId 全局唯一，
--      重建索引时整行替换，embeddingModel 与向量始终同源。

CREATE TABLE IF NOT EXISTS ai_learning_session (
  id BIGINT NOT NULL PRIMARY KEY,
  userId BIGINT NOT NULL,
  courseCode VARCHAR(128) NOT NULL,
  goal VARCHAR(500) NOT NULL DEFAULT '',
  focus VARCHAR(16) NOT NULL DEFAULT 'LEARN',
  timeBudgetMinutes INT NOT NULL DEFAULT 0,
  unitCode VARCHAR(128) NULL,
  currentActionJson JSON NULL,
  summaryJson JSON NULL,
  planJson JSON NULL,
  modelProfileId BIGINT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  rowVersion INT NOT NULL DEFAULT 0,
  isDelete TINYINT NOT NULL DEFAULT 0,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ai_session_user_course (userId, courseCode, status),
  KEY idx_ai_session_user_time (userId, createTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_tutor_turn (
  id BIGINT NOT NULL PRIMARY KEY,
  sessionId BIGINT NOT NULL,
  userId BIGINT NOT NULL,
  clientRequestId VARCHAR(128) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'RUNNING',
  message TEXT NULL,
  answer LONGTEXT NULL,
  mode VARCHAR(16) NOT NULL DEFAULT 'FALLBACK',
  contextJson JSON NULL,
  sourcesJson JSON NULL,
  toolTraceJson JSON NULL,
  diagnosisJson JSON NULL,
  nextActionsJson JSON NULL,
  usageJson JSON NULL,
  modelProfileId BIGINT NULL,
  modelLabel VARCHAR(128) NULL,
  modelCalls INT NOT NULL DEFAULT 0,
  toolCalls INT NOT NULL DEFAULT 0,
  latencyMs BIGINT NOT NULL DEFAULT 0,
  errorMessage VARCHAR(1000) NULL,
  isDelete TINYINT NOT NULL DEFAULT 0,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ai_turn_idempotency (userId, sessionId, clientRequestId),
  KEY idx_ai_turn_status (status, createTime),
  KEY idx_ai_turn_session (sessionId, createTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_material (
  id BIGINT NOT NULL PRIMARY KEY,
  userId BIGINT NOT NULL,
  courseCode VARCHAR(128) NOT NULL,
  ownerScope VARCHAR(16) NOT NULL DEFAULT 'PERSONAL',
  title VARCHAR(200) NOT NULL,
  contentMd LONGTEXT NOT NULL,
  textHash VARCHAR(64) NOT NULL,
  revision INT NOT NULL DEFAULT 1,
  chunkCount INT NOT NULL DEFAULT 0,
  indexStatus VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  isDelete TINYINT NOT NULL DEFAULT 0,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ai_material_owner (userId, courseCode, isDelete),
  KEY idx_ai_material_hash (textHash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_material_chunk (
  id BIGINT NOT NULL PRIMARY KEY,
  blockId VARCHAR(255) NOT NULL,
  sourceId VARCHAR(191) NOT NULL,
  materialId BIGINT NOT NULL DEFAULT 0,
  ownerScope VARCHAR(16) NOT NULL,
  userId BIGINT NOT NULL DEFAULT 0,
  courseCode VARCHAR(128) NOT NULL,
  revision INT NOT NULL DEFAULT 1,
  locator VARCHAR(200) NOT NULL DEFAULT '',
  title VARCHAR(200) NULL,
  nodeCodesJson JSON NULL,
  contentMd LONGTEXT NOT NULL,
  textHash VARCHAR(64) NOT NULL,
  embeddingModel VARCHAR(128) NULL,
  embeddingDims INT NULL,
  embeddingVersion INT NOT NULL DEFAULT 1,
  vectorJson LONGTEXT NULL,
  indexedAt DATETIME NULL,
  isDelete TINYINT NOT NULL DEFAULT 0,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ai_chunk_block (blockId),
  KEY idx_ai_chunk_course (courseCode, ownerScope, isDelete),
  KEY idx_ai_chunk_owner (userId, courseCode, isDelete),
  KEY idx_ai_chunk_material (materialId),
  KEY idx_ai_chunk_model (embeddingModel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
