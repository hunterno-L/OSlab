-- =====================================================================
-- OSLab 知识图谱智能学习闭环 · 数据库增量变更脚本（P0 契约版）
-- 依据：《知识图谱智能学习闭环执行计划.md》第 3.1 节
-- 适用：已有数据库实例（新建库由 deploy/mysql-init 一并导入时同样执行本脚本）
-- 执行方式：mysql -uroot -p oslab_db < oslab_upgrade_v3_learning.sql
-- 幂等性：MySQL 8.0 不支持 CREATE INDEX IF NOT EXISTS，重复执行可能报
--         Duplicate key / Table exists，属正常现象，说明契约已生效。
--
-- 设计约束：
-- 1) 普通题与编程实验题分开存储，禁止改动现有 question 表结构；
-- 2) 学生查询 VO 永不返回标准答案，answer 仅服务端判分使用；
-- 3) learning_event 为不可变事件账本，回放不重算；
-- 4) 节点业务键统一使用 Neo4j EntityNode.name（nodeName），不存 neo4j 内部 id；
-- 5) 幂等：learning_event.idempotencyKey 唯一，防止判题重试重复计分；
-- 6) 班级归属使用现有 students 表（userId/className），P0 不建班级成员表。
-- =====================================================================

-- 旧原型遗留表（不在 P0 契约内）：如存在则清除，避免与新模型混用
DROP TABLE IF EXISTS `learning_class_member`;
DROP TABLE IF EXISTS `learning_annotation`;

-- ---------------------------------------------------------------------
-- 1) learning_question 普通题（JUDGE/SINGLE/MULTIPLE/FILL_BLANK）
--    reviewStatus 状态机：DRAFT -> SUBMITTED -> APPROVED/REJECTED -> PUBLISHED -> OFFLINE
--    options:  JSON 数组 [{"key":"A","value":"..."}]，仅 SINGLE/MULTIPLE 使用
--    answer:   JUDGE 存 "true"/"false"；SINGLE 存 "A"；MULTIPLE 存 ["A","C"]；
--              FILL_BLANK 存可接受答案 JSON 数组（判分时规范化空白与大小写）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `learning_question` (
    `id`           BIGINT       NOT NULL COMMENT 'id（雪花）',
    `title`        VARCHAR(128) NOT NULL COMMENT '标题',
    `content`      TEXT         NOT NULL COMMENT '题干（支持 markdown）',
    `type`         VARCHAR(16)  NOT NULL COMMENT '题型：JUDGE/SINGLE/MULTIPLE/FILL_BLANK',
    `options`      JSON                  DEFAULT NULL COMMENT '选项（SINGLE/MULTIPLE）',
    `answer`       JSON         NOT NULL COMMENT '标准答案（仅服务端判分，禁止下发学生）',
    `analysis`     TEXT                  DEFAULT NULL COMMENT '解析',
    `misconception` TEXT                 DEFAULT NULL COMMENT '常见误区',
    `chapter`      VARCHAR(64)           DEFAULT NULL COMMENT '所属章节',
    `difficulty`   TINYINT      NOT NULL DEFAULT 2 COMMENT '难度 1-5',
    `reviewStatus` VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态：DRAFT/SUBMITTED/APPROVED/REJECTED/PUBLISHED/OFFLINE',
    `userId`       BIGINT       NOT NULL COMMENT '作者用户 id',
    `createTime`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_reviewStatus` (`reviewStatus`),
    KEY `idx_chapter` (`chapter`),
    KEY `idx_userId` (`userId`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '普通题';

-- ---------------------------------------------------------------------
-- 2) learning_question_node 普通题与知识节点映射
--    nodeName 必须是 Neo4j 真实 EntityNode.name，导入前需人工核对
--    weight：该题对节点掌握度的影响权重（0-1，默认 1）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `learning_question_node` (
    `id`         BIGINT      NOT NULL COMMENT 'id（雪花）',
    `questionId` BIGINT      NOT NULL COMMENT '普通题 id',
    `nodeName`   VARCHAR(128) NOT NULL COMMENT '知识节点业务键（EntityNode.name）',
    `weight`     DOUBLE      NOT NULL DEFAULT 1 COMMENT '对节点掌握度的权重 0-1',
    `createTime` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_node` (`questionId`, `nodeName`),
    KEY `idx_nodeName` (`nodeName`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '普通题-知识节点映射';

-- ---------------------------------------------------------------------
-- 3) learning_attempt 作答记录（首次/重复作答分别记录，不覆盖）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `learning_attempt` (
    `id`                BIGINT       NOT NULL COMMENT 'id（雪花）',
    `questionId`        BIGINT       NOT NULL COMMENT '普通题 id',
    `userId`            BIGINT       NOT NULL COMMENT '作答用户 id',
    `submittedAnswer`   VARCHAR(1024) NOT NULL COMMENT '学生提交答案（JSON 字符串）',
    `isCorrect`         TINYINT      NOT NULL COMMENT '是否正确 0/1',
    `isFirst`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否该题首次作答 0/1',
    `hasViewedAnalysis` TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已查看解析 0/1',
    `createTime`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_question` (`userId`, `questionId`),
    KEY `idx_questionId` (`questionId`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '普通题作答记录';

-- ---------------------------------------------------------------------
-- 4) learning_event 不可变学习事件账本（普通题/实验/模拟器统一入口）
--    eventType：ATTEMPT（普通题作答）/ JUDGE（实验判题终态）/ SIMULATION（模拟器预测）
--    resourceType：LEARNING_QUESTION / QUESTION_SUBMIT / SIMULATION
--    result：PASS / FAIL / SYSTEM_ERROR（系统故障不计学生知识失误）
--    idempotencyKey 规则（服务端生成）：
--      ATTEMPT     -> "attempt:{attemptId}:{nodeName}"
--      JUDGE       -> "judge:{questionSubmitId}:{nodeName}"
--      SIMULATION  -> "sim:{userId}:{simulationKey}:{nodeName}"
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `learning_event` (
    `id`             BIGINT       NOT NULL COMMENT 'id（雪花）',
    `userId`         BIGINT       NOT NULL COMMENT '学生用户 id',
    `nodeName`       VARCHAR(128) NOT NULL COMMENT '知识节点业务键',
    `eventType`      VARCHAR(16)  NOT NULL COMMENT '事件类型：ATTEMPT/JUDGE/SIMULATION',
    `resourceType`   VARCHAR(24)  NOT NULL COMMENT '来源类型：LEARNING_QUESTION/QUESTION_SUBMIT/SIMULATION',
    `resourceId`     BIGINT       NOT NULL COMMENT '来源资源 id（题目/提交/模拟练习 id）',
    `result`         VARCHAR(16)  NOT NULL COMMENT '结果：PASS/FAIL/SYSTEM_ERROR',
    `isFirst`        TINYINT      NOT NULL DEFAULT 1 COMMENT '是否独立观测（重复刷题不重复计 BKT）0/1',
    `masteryBefore`  DOUBLE                DEFAULT NULL COMMENT '事件前综合掌握度 pKnown',
    `masteryAfter`   DOUBLE                DEFAULT NULL COMMENT '事件后综合掌握度 pKnown',
    `evidence`       JSON                  DEFAULT NULL COMMENT '证据描述（来源、错因、摘要等）',
    `idempotencyKey` VARCHAR(160) NOT NULL COMMENT '幂等键',
    `createTime`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idempotency` (`idempotencyKey`),
    KEY `idx_user_node` (`userId`, `nodeName`),
    KEY `idx_nodeName` (`nodeName`),
    KEY `idx_createTime` (`createTime`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '学习事件账本（不可变）';

-- ---------------------------------------------------------------------
-- 5) learning_mastery 学生-节点掌握状态（个人图谱数字孪生）
--    conceptScore：概念理解分（ATTEMPT 事件驱动）
--    practiceScore：实践应用分（JUDGE/SIMULATION 事件驱动）
--    pKnown：综合掌握概率（BKT，两类事件按映射权重共同驱动）
--    status：无证据 GRAY；pKnown<0.35 RED；0.35<=pKnown<0.70 YELLOW；
--            pKnown>=0.70 且证据类型>=2 为 GREEN
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `learning_mastery` (
    `id`                     BIGINT       NOT NULL COMMENT 'id（雪花）',
    `userId`                 BIGINT       NOT NULL COMMENT '学生用户 id',
    `nodeName`               VARCHAR(128) NOT NULL COMMENT '知识节点业务键',
    `pKnown`                 DOUBLE       NOT NULL DEFAULT 0.1 COMMENT '综合 BKT 掌握概率',
    `confidence`             DOUBLE       NOT NULL DEFAULT 0 COMMENT '可信度 0-1（证据量与来源种类）',
    `conceptScore`           DOUBLE       NOT NULL DEFAULT 0 COMMENT '概念理解分 0-1',
    `practiceScore`          DOUBLE       NOT NULL DEFAULT 0 COMMENT '实践应用分 0-1',
    `conceptEvidenceCount`   INT          NOT NULL DEFAULT 0 COMMENT '概念证据数（独立观测）',
    `practiceEvidenceCount`  INT          NOT NULL DEFAULT 0 COMMENT '实践证据数（独立观测）',
    `status`                 VARCHAR(8)   NOT NULL DEFAULT 'GRAY' COMMENT '状态：GRAY/RED/YELLOW/GREEN',
    `lastEventId`            BIGINT                DEFAULT NULL COMMENT '最近一次事件 id',
    `createTime`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_node` (`userId`, `nodeName`),
    KEY `idx_userId` (`userId`),
    KEY `idx_nodeName` (`nodeName`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '学生节点掌握状态';

-- ---------------------------------------------------------------------
-- 6) learning_review 题目审核记录（驳回理由、审核人、时间）
--    action：SUBMIT/APPROVE/REJECT/PUBLISH/OFFLINE
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `learning_review` (
    `id`         BIGINT       NOT NULL COMMENT 'id（雪花）',
    `questionId` BIGINT       NOT NULL COMMENT '普通题 id',
    `reviewerId` BIGINT       NOT NULL COMMENT '操作人用户 id',
    `action`     VARCHAR(16)  NOT NULL COMMENT '动作：SUBMIT/APPROVE/REJECT/PUBLISH/OFFLINE',
    `reason`     VARCHAR(512)          DEFAULT NULL COMMENT '审核意见（驳回必填）',
    `createTime` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_questionId` (`questionId`),
    KEY `idx_reviewerId` (`reviewerId`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '题目审核记录';
