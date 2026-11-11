-- 暂存：整组自测（SELF_TEST）所需结构。版本号待 A 统一分配后迁入 数据库文件/。
-- 只做增量：不改判分器、BKT、事件账本语义，不复制成绩事实源。

ALTER TABLE `learning_assessment_run`
  ADD COLUMN `mode` VARCHAR(16) NOT NULL DEFAULT 'PRACTICE' COMMENT 'PRACTICE / SELF_TEST',
  ADD COLUMN `rowVersion` INT NOT NULL DEFAULT 0 COMMENT '草稿乐观锁版本',
  ADD COLUMN `questionRevisionsJson` TEXT NULL COMMENT '开始时的题目版本快照 {questionId: revision}',
  ADD COLUMN `courseCode` VARCHAR(128) NULL,
  ADD COLUMN `lastSaveId` VARCHAR(64) NULL COMMENT '最近一次草稿保存的客户端幂等键',
  ADD COLUMN `lastSaveAt` DATETIME NULL,
  ADD COLUMN `submissionKey` VARCHAR(64) NULL COMMENT '交卷幂等键',
  ADD COLUMN `submissionRowVersion` INT NULL COMMENT '交卷时的草稿版本',
  ADD COLUMN `finishResultJson` LONGTEXT NULL COMMENT '服务端生成的最终结果（只读回放）';

CREATE TABLE IF NOT EXISTS `learning_assessment_draft` (
  `id` BIGINT NOT NULL,
  `runId` BIGINT NOT NULL,
  `userId` BIGINT NOT NULL,
  `questionId` BIGINT NOT NULL,
  `answerJson` TEXT NULL COMMENT '学生草稿答案 JSON（string / string[] / null）',
  `markedUnsure` TINYINT NOT NULL DEFAULT 0,
  `questionRevision` INT NULL COMMENT '开始自测时的题目版本',
  `isDelete` TINYINT NOT NULL DEFAULT 0,
  `createTime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_question` (`runId`, `questionId`),
  KEY `idx_user_run` (`userId`, `runId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='整组自测草稿（保存不判分、不产生学习事件）';
