CREATE TABLE IF NOT EXISTS `kernel_submission` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `userId` BIGINT NOT NULL,
  `task` VARCHAR(32) NOT NULL,
  `stage` VARCHAR(24) NOT NULL,
  `passed` TINYINT NOT NULL DEFAULT 0,
  `seconds` DECIMAL(8,1) NOT NULL DEFAULT 0,
  `patchHash` VARCHAR(64) NULL,
  `patchText` MEDIUMTEXT NULL,
  `logTail` TEXT NULL,
  `createTime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_kernel_sub_user` (`userId`),
  KEY `idx_kernel_sub_task` (`task`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
