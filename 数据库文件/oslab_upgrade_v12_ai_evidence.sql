-- v12: ZhiCore AI 升级 —— 个人模型配置、学习证据资格与复习阶梯。
-- Apply after oslab_upgrade_v11_ai_session.sql. Idempotent and additive.
-- 只新增表：不修改既有 learning_attempt / learning_event / learning_mastery 结构，
-- 不复制既有判题与成绩为另一份事实源。
-- 说明：
--   1) ai_model_profile.apiKeyCipher 为 AES-GCM 密文（Base64），主密钥只来自环境变量
--      OSLAB_AI_MASTER_KEY；库内、日志与接口响应只出现脱敏值，永不出现明文密钥；
--   2) learning_evidence_context 必须在答题事务之前落库（帮助等级 NONE/HINT/ANSWER_REVEALED），
--      由答题事务读取并决定证据资格；本表不写掌握度，掌握度仍只由 learning_event 驱动；
--   3) learning_review_item 与 learning_mastery 相互独立：答对一道重复题不得刷成掌握。

CREATE TABLE IF NOT EXISTS ai_model_profile (
  id BIGINT NOT NULL PRIMARY KEY,
  userId BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  serviceType VARCHAR(32) NOT NULL DEFAULT 'OPENAI_COMPATIBLE',
  baseUrl VARCHAR(512) NOT NULL,
  model VARCHAR(128) NOT NULL,
  apiKeyCipher TEXT NULL,
  apiKeyMasked VARCHAR(64) NULL,
  capabilitiesJson JSON NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'CANDIDATE',
  testStatus VARCHAR(24) NOT NULL DEFAULT 'UNTESTED',
  testLatencyMs BIGINT NULL,
  testMessage VARCHAR(1000) NULL,
  testedAt DATETIME NULL,
  active TINYINT NOT NULL DEFAULT 0,
  rowVersion INT NOT NULL DEFAULT 0,
  isDelete TINYINT NOT NULL DEFAULT 0,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ai_profile_owner (userId, isDelete),
  KEY idx_ai_profile_active (userId, active, isDelete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS learning_evidence_context (
  id BIGINT NOT NULL PRIMARY KEY,
  userId BIGINT NOT NULL,
  contextKey VARCHAR(191) NOT NULL,
  courseCode VARCHAR(128) NULL,
  activityKind VARCHAR(24) NOT NULL,
  activityCode VARCHAR(191) NOT NULL,
  questionRevision INT NULL,
  skillCode VARCHAR(128) NULL,
  sessionId BIGINT NULL,
  turnId BIGINT NULL,
  attemptId BIGINT NULL,
  helpLevel VARCHAR(24) NOT NULL DEFAULT 'NONE',
  selfReportedHelp VARCHAR(24) NOT NULL DEFAULT 'NONE',
  helpSourceJson JSON NULL,
  eligibleForObservation TINYINT NOT NULL DEFAULT 1,
  eligibilityReason VARCHAR(200) NULL,
  result VARCHAR(24) NULL,
  eventIdsJson JSON NULL,
  consumed TINYINT NOT NULL DEFAULT 0,
  rowVersion INT NOT NULL DEFAULT 0,
  isDelete TINYINT NOT NULL DEFAULT 0,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_evidence_context_key (userId, contextKey),
  KEY idx_evidence_activity (userId, activityKind, activityCode),
  KEY idx_evidence_user_time (userId, createTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS learning_review_item (
  id BIGINT NOT NULL PRIMARY KEY,
  userId BIGINT NOT NULL,
  courseCode VARCHAR(128) NOT NULL,
  reviewKey VARCHAR(191) NOT NULL,
  skillCode VARCHAR(128) NULL,
  nodeCode VARCHAR(128) NULL,
  activityKind VARCHAR(24) NOT NULL,
  activityCode VARCHAR(191) NOT NULL,
  sourceEventId BIGINT NULL,
  stage INT NOT NULL DEFAULT 0,
  dueAt DATETIME NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  resultJson JSON NULL,
  isDelete TINYINT NOT NULL DEFAULT 0,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_review_item_key (userId, reviewKey),
  KEY idx_review_due (userId, status, dueAt),
  KEY idx_review_course (userId, courseCode, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
