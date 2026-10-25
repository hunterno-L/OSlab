-- Apply after v6. Additive migration: no existing IDs, grades, snapshots or names are rewritten.
CREATE TABLE IF NOT EXISTS knowledge_node_registry (
 id BIGINT NOT NULL PRIMARY KEY,nodeCode VARCHAR(128) NOT NULL UNIQUE,name VARCHAR(128) NOT NULL,nodeType VARCHAR(32) NOT NULL,
 description TEXT,aliasesJson JSON,homeSectionCode VARCHAR(128),sourceRefsJson JSON,layer VARCHAR(32),
 createTime DATETIME DEFAULT CURRENT_TIMESTAMP,updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,isDelete TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS knowledge_node_relation (
 id BIGINT NOT NULL PRIMARY KEY,fromNodeCode VARCHAR(128) NOT NULL,toNodeCode VARCHAR(128) NOT NULL,relationType VARCHAR(32) NOT NULL,
 reason TEXT,sourceRefJson JSON,createTime DATETIME DEFAULT CURRENT_TIMESTAMP,updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,isDelete TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_node_relation(fromNodeCode,toNodeCode,relationType)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Explicit bindings only. legacyNodeName is a frozen storage identity, not the current display name.
CREATE TABLE IF NOT EXISTS knowledge_node_legacy_identity (
 nodeCode VARCHAR(128) NOT NULL PRIMARY KEY,legacyNodeName VARCHAR(128) NOT NULL UNIQUE,
 createTime DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS learning_question_revision (
 id BIGINT NOT NULL PRIMARY KEY,questionId BIGINT NOT NULL,revision INT NOT NULL,reviewStatus VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
 contentJson JSON NOT NULL,authorId BIGINT NOT NULL,createTime DATETIME DEFAULT CURRENT_TIMESTAMP,updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_question_revision(questionId,revision),KEY idx_question_revision_status(questionId,reviewStatus)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS experiment_node (
 id BIGINT NOT NULL PRIMARY KEY,questionId BIGINT NOT NULL,nodeCode VARCHAR(128) NOT NULL,nodeName VARCHAR(128) NOT NULL,
 role VARCHAR(16) NOT NULL,weight DOUBLE NOT NULL,reason TEXT,createTime DATETIME DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_experiment_node(questionId,nodeCode),KEY idx_experiment_node(nodeCode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Rerunnable column additions on MySQL 8 using information_schema (no destructive procedure).
SET @v7sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_question' AND column_name='questionCode')=0,'ALTER TABLE learning_question ADD questionCode VARCHAR(128) NULL, ADD revision INT NOT NULL DEFAULT 1, ADD UNIQUE KEY uk_question_code(questionCode)','SELECT 1');
PREPARE v7stmt FROM @v7sql; EXECUTE v7stmt; DEALLOCATE PREPARE v7stmt;
SET @v7sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_question_node' AND column_name='nodeCode')=0,'ALTER TABLE learning_question_node ADD nodeCode VARCHAR(128) NULL, ADD role VARCHAR(16) NULL','SELECT 1');
PREPARE v7stmt FROM @v7sql; EXECUTE v7stmt; DEALLOCATE PREPARE v7stmt;
SET @v7sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_attempt' AND column_name='sourceUnitCode')=0,'ALTER TABLE learning_attempt ADD sourceUnitCode VARCHAR(128) NULL, ADD sourceActivityCode VARCHAR(128) NULL, ADD questionRevision INT NULL, ADD questionSnapshot JSON NULL','SELECT 1');
PREPARE v7stmt FROM @v7sql; EXECUTE v7stmt; DEALLOCATE PREPARE v7stmt;
SET @v7sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='question_submit' AND column_name='sourceUnitCode')=0,'ALTER TABLE question_submit ADD sourceUnitCode VARCHAR(128) NULL, ADD sourceActivityCode VARCHAR(128) NULL','SELECT 1');
PREPARE v7stmt FROM @v7sql; EXECUTE v7stmt; DEALLOCATE PREPARE v7stmt;
SET @v7sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_event' AND column_name='nodeCode')=0,'ALTER TABLE learning_event ADD nodeCode VARCHAR(128) NULL, ADD KEY idx_event_node_code(userId,nodeCode)','SELECT 1');
PREPARE v7stmt FROM @v7sql; EXECUTE v7stmt; DEALLOCATE PREPARE v7stmt;
SET @v7sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_mastery' AND column_name='nodeCode')=0,'ALTER TABLE learning_mastery ADD nodeCode VARCHAR(128) NULL, ADD UNIQUE KEY uk_mastery_code(userId,nodeCode)','SELECT 1');
PREPARE v7stmt FROM @v7sql; EXECUTE v7stmt; DEALLOCATE PREPARE v7stmt;
-- Stable codes for existing assessments are deterministic and retain every original row ID.
SET @v7sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='question_submit' AND column_name='experimentMappingSnapshot')=0,'ALTER TABLE question_submit ADD experimentMappingSnapshot JSON NULL','SELECT 1');
PREPARE v7stmt FROM @v7sql; EXECUTE v7stmt; DEALLOCATE PREPARE v7stmt;
UPDATE learning_question SET questionCode=CONCAT('q.legacy.',id) WHERE questionCode IS NULL;
SET @v7sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_question' AND column_name='sectionCode')=0,'ALTER TABLE learning_question ADD sectionCode VARCHAR(128) NULL','SELECT 1');
PREPARE v7stmt FROM @v7sql; EXECUTE v7stmt; DEALLOCATE PREPARE v7stmt;
SET @v7sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_event' AND column_name='sourceUnitCode')=0,'ALTER TABLE learning_event ADD sourceUnitCode VARCHAR(128) NULL, ADD sourceActivityCode VARCHAR(128) NULL','SELECT 1');
PREPARE v7stmt FROM @v7sql; EXECUTE v7stmt; DEALLOCATE PREPARE v7stmt;

CREATE TABLE IF NOT EXISTS experiment_revision (
 id BIGINT NOT NULL PRIMARY KEY, questionId BIGINT NOT NULL, resourceCode VARCHAR(128) NOT NULL,
 revision INT NOT NULL, reviewStatus VARCHAR(24) NOT NULL DEFAULT 'DRAFT', requestJson JSON NOT NULL,
 beforeJson JSON NOT NULL, afterJson JSON NOT NULL, userId BIGINT NOT NULL, contributor VARCHAR(64), batchCode VARCHAR(128),
 reviewerId BIGINT NULL, reviewReason TEXT, appliedTime DATETIME NULL,
 createTime DATETIME DEFAULT CURRENT_TIMESTAMP, updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_experiment_revision(questionId,revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
