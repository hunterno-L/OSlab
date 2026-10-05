-- =====================================================================
-- OSLab 教学管理 v5 增量（Phase 8：§12.2 共享学习素材）
-- 依据：《OSLab第三阶段执行计划-学习流程与UI重构.md》§12.2
-- 幂等性：表用 IF NOT EXISTS，可重复执行。
-- =====================================================================

-- 节点共享学习素材：知识维护编辑，学生侧仅消费 PUBLISHED
CREATE TABLE IF NOT EXISTS `learning_node_content` (
    `id`            BIGINT       NOT NULL COMMENT 'id（雪花）',
    `nodeName`      VARCHAR(128) NOT NULL COMMENT '知识节点业务键（Neo4j EntityNode.name，唯一）',
    `contentMd`     MEDIUMTEXT   NULL COMMENT '学习素材正文（纯文本/轻量 markdown，学生侧原样展示）',
    `resourceLinks` JSON         NULL COMMENT '关联资料 JSON 数组 [{title,url}]',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/OFFLINE',
    `updateUserId`  BIGINT       NOT NULL COMMENT '最近维护人用户 id',
    `createTime`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_nodeName` (`nodeName`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT '节点共享学习素材（教学管理维护）';
