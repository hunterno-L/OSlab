-- v10 班级管理：教师创建班级，学生通过邀请码加入。
-- 仅新增表，重复执行安全。
CREATE TABLE IF NOT EXISTS `teaching_class` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `className` VARCHAR(128) NOT NULL,
  `classCode` VARCHAR(32) NOT NULL,
  `description` VARCHAR(512) NULL,
  `creatorId` BIGINT NOT NULL,
  `isDelete` TINYINT NOT NULL DEFAULT 0,
  `createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updatedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teaching_class_name` (`className`),
  UNIQUE KEY `uk_teaching_class_code` (`classCode`),
  KEY `idx_teaching_class_creator` (`creatorId`, `isDelete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `teaching_class_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `classId` BIGINT NOT NULL,
  `userId` BIGINT NOT NULL,
  `joinedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teaching_class_member` (`classId`, `userId`),
  KEY `idx_teaching_class_member_user` (`userId`),
  CONSTRAINT `fk_teaching_class_member_class`
    FOREIGN KEY (`classId`) REFERENCES `teaching_class` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
