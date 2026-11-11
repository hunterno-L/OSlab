-- v13: 学习闭环 —— 整组自测（SELF_TEST）模式、草稿与交卷结果。
-- Apply after oslab_upgrade_v12_ai_evidence.sql. Additive only.
-- 边界：
--   1) 草稿保存不判分、不写 learning_event、不更新掌握度/推荐；
--   2) ACTIVE 自测只返回题面、本人草稿与保存状态，不含答案/解析/正确性；
--   3) 交卷由服务端按草稿重算并冻结结果，重复/并发交卷不产生重复证据；
--   4) 历史 run 的 mode 为 NULL，读取时按 PRACTICE 处理；
--   5) 未答题不产生错误观测；综合题由结果页标记待自评；下线/版本失效题不计入有效分母。

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
