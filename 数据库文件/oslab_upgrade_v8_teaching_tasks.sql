-- v8: teaching tasks, private execution copies and task provenance.
-- Additive and rerunnable. Apply after oslab_upgrade_v7_identity.sql.
-- Existing rows default to SHARED / PUBLISHED so untouched content keeps working.

CREATE TABLE IF NOT EXISTS learning_task (
  id BIGINT NOT NULL PRIMARY KEY,
  taskCode VARCHAR(64) NOT NULL,
  creatorId BIGINT NOT NULL,
  courseCode VARCHAR(128) NULL,
  unitCode VARCHAR(128) NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  audienceJson JSON NULL,
  publishedPolicyJson JSON NULL,
  dueAt DATETIME NULL,
  allowLate TINYINT NOT NULL DEFAULT 0,
  supplementsJson JSON NULL,
  rowVersion INT NOT NULL DEFAULT 0,
  publishedAt DATETIME NULL,
  closedAt DATETIME NULL,
  isArchived TINYINT NOT NULL DEFAULT 0,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  isDelete TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_task_code (taskCode),
  KEY idx_task_creator (creatorId, status, updateTime),
  KEY idx_task_status (status, dueAt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS learning_task_item (
  id BIGINT NOT NULL PRIMARY KEY,
  taskId BIGINT NOT NULL,
  itemCode VARCHAR(64) NOT NULL,
  sortOrder INT NOT NULL DEFAULT 0,
  itemType VARCHAR(16) NOT NULL,
  sourceMode VARCHAR(16) NULL,
  required TINYINT NOT NULL DEFAULT 1,
  maxScore DECIMAL(6,2) NULL,
  draftJson JSON NULL,
  publishedSnapshotJson JSON NULL,
  runtimeResourceId BIGINT NULL,
  runtimeResourceType VARCHAR(16) NULL,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_task_item (taskId, itemCode),
  KEY idx_task_item_order (taskId, sortOrder)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS learning_task_recipient (
  id BIGINT NOT NULL PRIMARY KEY,
  taskId BIGINT NOT NULL,
  userId BIGINT NOT NULL,
  studentNumberSnapshot VARCHAR(64) NULL,
  classNameSnapshot VARCHAR(128) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  acceptedAt DATETIME NULL,
  latestSubmissionId BIGINT NULL,
  rowVersion INT NOT NULL DEFAULT 0,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_task_recipient (taskId, userId),
  KEY idx_recipient_student (userId, status, taskId),
  KEY idx_recipient_task_status (taskId, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS learning_task_item_work (
  id BIGINT NOT NULL PRIMARY KEY,
  recipientId BIGINT NOT NULL,
  itemId BIGINT NOT NULL,
  roundNo INT NOT NULL DEFAULT 1,
  answerJson JSON NULL,
  textResponse MEDIUMTEXT NULL,
  fileIdsJson JSON NULL,
  lastAttemptId BIGINT NULL,
  lastCodeSubmissionId BIGINT NULL,
  rowVersion INT NOT NULL DEFAULT 0,
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_item_work (recipientId, itemId, roundNo),
  KEY idx_work_recipient (recipientId, roundNo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS learning_task_submission (
  id BIGINT NOT NULL PRIMARY KEY,
  recipientId BIGINT NOT NULL,
  roundNo INT NOT NULL,
  submissionKey VARCHAR(64) NOT NULL,
  submittedSnapshotJson JSON NULL,
  status VARCHAR(16) NOT NULL,
  isLate TINYINT NOT NULL DEFAULT 0,
  autoScore DECIMAL(8,2) NULL,
  manualScore DECIMAL(8,2) NULL,
  totalScore DECIMAL(8,2) NULL,
  reviewJson JSON NULL,
  reviewerId BIGINT NULL,
  reviewedAt DATETIME NULL,
  submittedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_submission_round (recipientId, roundNo),
  UNIQUE KEY uk_submission_key (recipientId, submissionKey),
  KEY idx_submission_status (status, submittedAt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS learning_task_file (
  id BIGINT NOT NULL PRIMARY KEY,
  taskId BIGINT NOT NULL,
  itemCode VARCHAR(64) NOT NULL,
  recipientId BIGINT NULL,
  ownerId BIGINT NOT NULL,
  purpose VARCHAR(24) NOT NULL,
  originalName VARCHAR(255) NOT NULL,
  storageKey VARCHAR(512) NOT NULL,
  ext VARCHAR(16) NOT NULL,
  mime VARCHAR(128) NULL,
  size BIGINT NOT NULL,
  sha256 VARCHAR(64) NULL,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  isDelete TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_task_file_key (storageKey),
  KEY idx_task_file_item (taskId, itemCode, purpose),
  KEY idx_task_file_owner (ownerId, recipientId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Scope columns on shared resources.
SET @v8sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_question' AND column_name='resourceScope')=0,'ALTER TABLE learning_question ADD resourceScope VARCHAR(16) NOT NULL DEFAULT ''SHARED'', ADD ownerTaskCode VARCHAR(64) NULL, ADD ownerTaskItemCode VARCHAR(64) NULL, ADD KEY idx_question_scope(resourceScope)','SELECT 1');
PREPARE v8stmt FROM @v8sql; EXECUTE v8stmt; DEALLOCATE PREPARE v8stmt;

SET @v8sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='question' AND column_name='resourceScope')=0,'ALTER TABLE question ADD resourceScope VARCHAR(16) NOT NULL DEFAULT ''SHARED'', ADD ownerTaskCode VARCHAR(64) NULL, ADD ownerTaskItemCode VARCHAR(64) NULL, ADD KEY idx_question_scope(resourceScope)','SELECT 1');
PREPARE v8stmt FROM @v8sql; EXECUTE v8stmt; DEALLOCATE PREPARE v8stmt;

SET @v8sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='question' AND column_name='publicationStatus')=0,'ALTER TABLE question ADD publicationStatus VARCHAR(16) NOT NULL DEFAULT ''PUBLISHED'', ADD KEY idx_question_publication(publicationStatus)','SELECT 1');
PREPARE v8stmt FROM @v8sql; EXECUTE v8stmt; DEALLOCATE PREPARE v8stmt;

-- Task provenance on attempts, code submissions and the immutable learning ledger.
SET @v8sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_attempt' AND column_name='sourceTaskCode')=0,'ALTER TABLE learning_attempt ADD sourceTaskCode VARCHAR(64) NULL, ADD sourceTaskItemCode VARCHAR(64) NULL, ADD sourceTaskRound INT NULL, ADD evaluationIdentity VARCHAR(128) NULL, ADD KEY idx_attempt_identity(userId, evaluationIdentity), ADD KEY idx_attempt_task(sourceTaskCode, sourceTaskItemCode, userId)','SELECT 1');
PREPARE v8stmt FROM @v8sql; EXECUTE v8stmt; DEALLOCATE PREPARE v8stmt;

SET @v8sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='question_submit' AND column_name='sourceTaskCode')=0,'ALTER TABLE question_submit ADD sourceTaskCode VARCHAR(64) NULL, ADD sourceTaskItemCode VARCHAR(64) NULL, ADD sourceTaskRound INT NULL, ADD evaluationIdentity VARCHAR(128) NULL, ADD KEY idx_submit_identity(userId, evaluationIdentity), ADD KEY idx_submit_task(sourceTaskCode, sourceTaskItemCode)','SELECT 1');
PREPARE v8stmt FROM @v8sql; EXECUTE v8stmt; DEALLOCATE PREPARE v8stmt;

SET @v8sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_event' AND column_name='sourceTaskCode')=0,'ALTER TABLE learning_event ADD sourceTaskCode VARCHAR(64) NULL, ADD sourceTaskItemCode VARCHAR(64) NULL, ADD sourceTaskRound INT NULL, ADD KEY idx_event_task(sourceTaskCode, sourceTaskItemCode)','SELECT 1');
PREPARE v8stmt FROM @v8sql; EXECUTE v8stmt; DEALLOCATE PREPARE v8stmt;
