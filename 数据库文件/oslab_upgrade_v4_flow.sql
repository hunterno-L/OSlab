-- =====================================================================
-- OSLab 学习流程 v4 增量（Phase 4：单题测评会话）
-- 依据：《OSLab第三阶段执行计划-学习流程与UI重构.md》§8.3
-- 幂等性：表用 IF NOT EXISTS；列重复执行会报 Duplicate column（属正常）。
-- =====================================================================

-- 测评会话表：队列固定（questionIdsJson），状态 ACTIVE/FINISHED
CREATE TABLE IF NOT EXISTS `learning_assessment_run` (
    `id`              BIGINT      NOT NULL COMMENT 'id（雪花）',
    `userId`          BIGINT      NOT NULL COMMENT '学生用户 id',
    `chapter`         VARCHAR(64)          DEFAULT NULL COMMENT '章节（可空=混合）',
    `nodeName`        VARCHAR(128)         DEFAULT NULL COMMENT '知识节点（可空=多节点）',
    `questionIdsJson` JSON        NOT NULL COMMENT '本轮题目队列（服务端固定顺序）',
    `status`          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/FINISHED',
    `createTime`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `finishTime`      DATETIME             DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_userId` (`userId`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '知识测评会话';

-- 作答表扩展：本轮归属 + 提交幂等键（历史行保持 NULL 兼容）
ALTER TABLE `learning_attempt`
    ADD COLUMN `assessmentRunId` BIGINT NULL COMMENT '所属测评会话 id（旧数据为 NULL）',
    ADD COLUMN `submissionKey`   VARCHAR(128) NULL COMMENT '提交幂等键（userId 维度唯一）',
    ADD UNIQUE KEY `uk_user_submission` (`userId`, `submissionKey`);

-- =====================================================================
-- OSLab 学习记录 v4 增量（Phase 7 §11.2：事件快照与事件组）
-- snapshotBefore/After：事件前后完整掌握快照 JSON
--   {pKnown, confidence, conceptScore, practiceScore,
--    conceptEvidenceCount, practiceEvidenceCount, status}
-- eventGroupKey：同一学习事务的多节点事件共享组键
--   attempt:{attemptId} / judge:{questionSubmitId} / sim:{userId}:{simulationKey}
--   / baseline:{userId}:{nodeName}
-- 历史事件快照列保持 NULL：回放展示已记录数值并标注“历史细分状态未记录”，
-- 不补写虚构历史；可信完整回放从 STATE_BASELINE 基线事件开始。
-- =====================================================================
ALTER TABLE `learning_event`
    ADD COLUMN `snapshotBefore` TEXT NULL COMMENT '事件前完整掌握快照（JSON，7 字段）',
    ADD COLUMN `snapshotAfter`  TEXT NULL COMMENT '事件后完整掌握快照（JSON，7 字段）',
    ADD COLUMN `eventGroupKey`  VARCHAR(160) NULL COMMENT '事件组键（同事务多节点共享）',
    ADD KEY `idx_user_group` (`userId`, `eventGroupKey`);
