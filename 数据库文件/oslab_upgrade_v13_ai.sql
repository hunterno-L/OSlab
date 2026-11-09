-- ZhiCore AI 升级（P0 骨架）：会话、个人模型配置、帮助记录
CREATE TABLE IF NOT EXISTS `ai_session` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `sessionId` VARCHAR(64) NOT NULL,
  `userId` BIGINT NOT NULL,
  `courseCode` VARCHAR(128) NULL,
  `unitCode` VARCHAR(128) NULL,
  `nodeCodes` VARCHAR(512) NULL,
  `goal` VARCHAR(32) NOT NULL DEFAULT 'LEARN',
  `timeBudgetMinutes` INT NULL,
  `modelConfigId` BIGINT NULL,
  `createTime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `isDelete` TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_ai_session_sid` (`sessionId`),
  KEY `idx_ai_session_user` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_user_config` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `userId` BIGINT NOT NULL,
  `baseUrl` VARCHAR(255) NOT NULL DEFAULT 'https://ark.cn-beijing.volces.com/api/v3',
  `apiKey` VARCHAR(512) NULL,
  `model` VARCHAR(128) NOT NULL DEFAULT 'deepseek-v3-250324',
  `embeddingModel` VARCHAR(128) NULL,
  `createTime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_ai_config_user` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_help_event` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `sessionId` VARCHAR(64) NOT NULL,
  `userId` BIGINT NOT NULL,
  `eventType` VARCHAR(32) NOT NULL,
  `questionId` BIGINT NULL,
  `payloadJson` JSON NULL,
  `createTime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_ai_help_session` (`sessionId`),
  KEY `idx_ai_help_user` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
