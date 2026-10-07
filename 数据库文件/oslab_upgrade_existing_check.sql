-- =====================================================================
-- OSLab 已有库增量校验与补齐脚本（执行计划 §6.2.1-2 / task C-1 交付 3）
-- 适用：存量 oslab_db（此前只装到 v3 或更早），升级到 v4/v5 契约。
--       全新环境请直接走 deploy/mysql-init 的 Docker 初始化，勿用本脚本。
-- 执行方式：
--   mysql -uroot -p --default-character-set=utf8mb4 oslab_db < oslab_upgrade_existing_check.sql
-- 推荐的已有库完整升级顺序（均幂等，可重复执行）：
--   1. mysql ... oslab_db < oslab_upgrade_v3_learning.sql   （CREATE TABLE IF NOT EXISTS）
--   2. mysql ... oslab_db < oslab_upgrade_v5_teaching.sql   （CREATE TABLE IF NOT EXISTS）
--   3. mysql ... oslab_db < oslab_upgrade_existing_check.sql（本脚本：校验+条件补齐 v2/v4）
--   4. mysql ... oslab_db < oslab_demo_seed_v3.sql          （INSERT IGNORE）
--   5. mysql ... oslab_db < oslab_seed_v4_content.sql       （IGNORE/ON DUPLICATE KEY，
--      依赖已有管理员账号：updateUserId 取自 user 表第一位 admin）
-- 幂等性设计（§6.2.2 红线）：
--   * 所有 ALTER 先查 information_schema，缺什么补什么；
--     绝不允许"重复执行 v4 的 ALTER 并忽略一切错误"的方式升级存量库。
--   * 补齐动作经 PREPARE/EXECUTE 动态执行，每次执行都打印 [ADDED]/[EXISTS] 日志。
--   * uk_userAccount（v2 产物）在缺失时补加；若历史数据存在重复账号，
--     第 1 部分的排查查询会列出，且补索引会以明确错误失败（不吞错）。
--   * DDL 为隐式提交，本脚本不使用事务包裹。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 第 0 部分：前置自检 —— learning_* 基础表缺失则直接中止
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS oslab_upgrade_precheck;
DELIMITER $$
CREATE PROCEDURE oslab_upgrade_precheck()
BEGIN
    DECLARE v_table_count INT;
    SELECT COUNT(*) INTO v_table_count
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME IN ('learning_question', 'learning_question_node',
                          'learning_attempt', 'learning_event');
    IF v_table_count < 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT =
                'learning_* 基础表缺失：请先执行 oslab_upgrade_v3_learning.sql 再运行本脚本';
    END IF;
END $$
DELIMITER ;
CALL oslab_upgrade_precheck();
DROP PROCEDURE oslab_upgrade_precheck;

-- ---------------------------------------------------------------------
-- 第 1 部分：升级前状态报告
-- ---------------------------------------------------------------------
-- 1.1 若存在重复账号，补 uk_userAccount 唯一索引前必须先清理（v2 脚本同款排查语句）
SELECT userAccount, COUNT(*) AS dup_count
  FROM `user`
 GROUP BY userAccount HAVING COUNT(*) > 1;

-- 1.2 当前 v2/v4 契约对象的存在情况（行为：表.对象 / 类型）
SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME) AS object_path, 'COLUMN' AS object_type
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'learning_attempt'
         AND COLUMN_NAME IN ('assessmentRunId', 'submissionKey'))
     OR (TABLE_NAME = 'learning_event'
         AND COLUMN_NAME IN ('snapshotBefore', 'snapshotAfter', 'eventGroupKey')))
UNION ALL
SELECT CONCAT(TABLE_NAME, '.', INDEX_NAME) AS object_path, 'INDEX' AS object_type
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'user' AND INDEX_NAME = 'uk_userAccount')
     OR (TABLE_NAME = 'learning_attempt' AND INDEX_NAME = 'uk_user_submission')
     OR (TABLE_NAME = 'learning_event' AND INDEX_NAME = 'idx_user_group'));

-- ---------------------------------------------------------------------
-- 第 2 部分：条件补齐（缺则加，已存在则跳过）
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS oslab_create_table_if_missing;
DELIMITER $$
CREATE PROCEDURE oslab_create_table_if_missing(
    IN p_table VARCHAR(64), IN p_create_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table) THEN
        SET @oslab_ddl = p_create_ddl;
        PREPARE oslab_stmt FROM @oslab_ddl;
        EXECUTE oslab_stmt;
        DEALLOCATE PREPARE oslab_stmt;
        SELECT CONCAT('[ADDED] TABLE ', p_table) AS upgrade_log;
    ELSE
        SELECT CONCAT('[EXISTS] TABLE ', p_table) AS upgrade_log;
    END IF;
END $$
DELIMITER ;

-- v4 的建表部分（CREATE TABLE IF NOT EXISTS 本身幂等）。v4 文件的 ALTER 不适合
-- 对存量库直接重跑，因此这里把 learning_assessment_run 一并条件补齐，
-- 保证只执行过 v3 的旧库经本脚本即可补全 v4 全部结构。
CALL oslab_create_table_if_missing('learning_assessment_run',
    'CREATE TABLE IF NOT EXISTS `learning_assessment_run` (
        `id`              BIGINT      NOT NULL COMMENT ''id（雪花）'',
        `userId`          BIGINT      NOT NULL COMMENT ''学生用户 id'',
        `chapter`         VARCHAR(64)          DEFAULT NULL COMMENT ''章节（可空=混合）'',
        `nodeName`        VARCHAR(128)         DEFAULT NULL COMMENT ''知识节点（可空=多节点）'',
        `questionIdsJson` JSON        NOT NULL COMMENT ''本轮题目队列（服务端固定顺序）'',
        `status`          VARCHAR(16) NOT NULL DEFAULT ''ACTIVE'' COMMENT ''状态：ACTIVE/FINISHED'',
        `createTime`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
        `finishTime`      DATETIME             DEFAULT NULL COMMENT ''完成时间'',
        PRIMARY KEY (`id`),
        KEY `idx_userId` (`userId`),
        KEY `idx_status` (`status`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ''知识测评会话''');

DROP PROCEDURE oslab_create_table_if_missing;

DROP PROCEDURE IF EXISTS oslab_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE oslab_add_column_if_missing(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_column_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table AND COLUMN_NAME = p_column) THEN
        SET @oslab_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_column_ddl);
        PREPARE oslab_stmt FROM @oslab_ddl;
        EXECUTE oslab_stmt;
        DEALLOCATE PREPARE oslab_stmt;
        SELECT CONCAT('[ADDED] ', p_table, '.', p_column) AS upgrade_log;
    ELSE
        SELECT CONCAT('[EXISTS] ', p_table, '.', p_column) AS upgrade_log;
    END IF;
END $$
DELIMITER ;

DROP PROCEDURE IF EXISTS oslab_add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE oslab_add_index_if_missing(
    IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_index_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @oslab_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_index_ddl);
        PREPARE oslab_stmt FROM @oslab_ddl;
        EXECUTE oslab_stmt;
        DEALLOCATE PREPARE oslab_stmt;
        SELECT CONCAT('[ADDED] ', p_table, '.', p_index) AS upgrade_log;
    ELSE
        SELECT CONCAT('[EXISTS] ', p_table, '.', p_index) AS upgrade_log;
    END IF;
END $$
DELIMITER ;

-- v2：user.userAccount 唯一索引（基础 oslab_db.sql 已内置；仅旧库可能缺失）
CALL oslab_add_index_if_missing('user', 'uk_userAccount',
    'UNIQUE KEY `uk_userAccount` (`userAccount`)');

-- v4：learning_attempt 增加测评会话归属与提交幂等键（历史行保持 NULL 兼容）
CALL oslab_add_column_if_missing('learning_attempt', 'assessmentRunId',
    '`assessmentRunId` BIGINT NULL COMMENT ''所属测评会话 id（旧数据为 NULL）''');
CALL oslab_add_column_if_missing('learning_attempt', 'submissionKey',
    '`submissionKey` VARCHAR(128) NULL COMMENT ''提交幂等键（userId 维度唯一）''');
CALL oslab_add_index_if_missing('learning_attempt', 'uk_user_submission',
    'UNIQUE KEY `uk_user_submission` (`userId`, `submissionKey`)');

-- v4：learning_event 增加事件快照与事件组列及索引（历史事件快照列保持 NULL）
CALL oslab_add_column_if_missing('learning_event', 'snapshotBefore',
    '`snapshotBefore` TEXT NULL COMMENT ''事件前完整掌握快照（JSON，7 字段）''');
CALL oslab_add_column_if_missing('learning_event', 'snapshotAfter',
    '`snapshotAfter` TEXT NULL COMMENT ''事件后完整掌握快照（JSON，7 字段）''');
CALL oslab_add_column_if_missing('learning_event', 'eventGroupKey',
    '`eventGroupKey` VARCHAR(160) NULL COMMENT ''事件组键（同事务多节点共享）''');
CALL oslab_add_index_if_missing('learning_event', 'idx_user_group',
    'KEY `idx_user_group` (`userId`, `eventGroupKey`)');

DROP PROCEDURE oslab_add_column_if_missing;
DROP PROCEDURE oslab_add_index_if_missing;

-- ---------------------------------------------------------------------
-- 第 3 部分：升级后契约校验报告
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS oslab_contract_report;
CREATE TEMPORARY TABLE oslab_contract_report (
    item    VARCHAR(128) NOT NULL,
    expected VARCHAR(32) NOT NULL,
    actual   VARCHAR(32) NOT NULL,
    verdict  VARCHAR(8)  NOT NULL
);

DROP PROCEDURE IF EXISTS oslab_report;
DELIMITER $$
CREATE PROCEDURE oslab_report(IN p_item VARCHAR(128), IN p_expected BIGINT, IN p_actual BIGINT)
BEGIN
    INSERT INTO oslab_contract_report (item, expected, actual, verdict)
    VALUES (p_item, p_expected, p_actual, IF(p_actual = p_expected, 'PASS', 'FAIL'));
END $$
DELIMITER ;

DROP PROCEDURE IF EXISTS oslab_report_info;
DELIMITER $$
CREATE PROCEDURE oslab_report_info(IN p_item VARCHAR(128), IN p_expected VARCHAR(32), IN p_actual VARCHAR(32))
BEGIN
    INSERT INTO oslab_contract_report (item, expected, actual, verdict)
    VALUES (p_item, p_expected, p_actual, 'INFO');
END $$
DELIMITER ;

SET @actual = (SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ('learning_question', 'learning_question_node',
                                     'learning_attempt', 'learning_event',
                                     'learning_mastery', 'learning_review'));
CALL oslab_report('v3 learning_* 六张表存在', 6, @actual);

SET @actual = (SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ('learning_assessment_run', 'learning_node_content'));
CALL oslab_report('learning_assessment_run / learning_node_content 表存在', 2, @actual);

SET @actual = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'learning_attempt'
                  AND COLUMN_NAME IN ('assessmentRunId', 'submissionKey'));
CALL oslab_report('learning_attempt.assessmentRunId/submissionKey 列存在', 2, @actual);

SET @actual = (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'learning_attempt'
                  AND INDEX_NAME = 'uk_user_submission');
CALL oslab_report('learning_attempt.uk_user_submission 索引存在', 1, @actual);

SET @actual = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'learning_event'
                  AND COLUMN_NAME IN ('snapshotBefore', 'snapshotAfter', 'eventGroupKey'));
CALL oslab_report('learning_event.snapshotBefore/snapshotAfter/eventGroupKey 列存在', 3, @actual);

SET @actual = (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'learning_event'
                  AND INDEX_NAME = 'idx_user_group');
CALL oslab_report('learning_event.idx_user_group 索引存在', 1, @actual);

SET @actual = (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user'
                  AND INDEX_NAME = 'uk_userAccount');
CALL oslab_report('user.uk_userAccount 索引存在（v2 产物）', 1, @actual);

SET @actual = (SELECT COUNT(*) FROM `user` WHERE userRole = 'admin' AND isDelete = 0);
CALL oslab_report('存在有效管理员（种子 updateUserId 依赖，非 0 即通过）', 0, IF(@actual > 0, 0, 1));

-- INFO 行：种子行数在执行第 4/5 步种子 SQL 后应达到的值；当前不计失败
SET @actual = (SELECT COUNT(*) FROM learning_question WHERE id BETWEEN 910001 AND 910026);
CALL oslab_report_info('题目种子 910001-910026 行数（种子执行后应为 26）', '26', @actual);
SET @actual = (SELECT COUNT(*) FROM learning_question_node WHERE id BETWEEN 911001 AND 911050);
CALL oslab_report_info('题目-节点映射种子 911001-911050 行数（种子执行后应为 50）', '50', @actual);
SET @actual = (SELECT COUNT(*) FROM learning_node_content);
CALL oslab_report_info('节点素材 learning_node_content 行数（内容种子执行后应为 12）', '12', @actual);

SELECT * FROM oslab_contract_report ORDER BY verdict DESC, item;

SELECT IF(SUM(verdict = 'FAIL') = 0, 'ALL PASS（结构契约齐备，可继续执行种子 SQL）',
          CONCAT('HAS FAIL：', SUM(verdict = 'FAIL'), ' 项未达标，请先处理再继续')) AS 总体结论
  FROM oslab_contract_report;

DROP PROCEDURE oslab_report;
DROP PROCEDURE oslab_report_info;
DROP TEMPORARY TABLE oslab_contract_report;

-- ---------------------------------------------------------------------
-- 使用说明（结尾备忘）
-- ---------------------------------------------------------------------
-- * 本脚本可重复执行：重复运行时第 2 部分全部打印 [EXISTS]，第 3 部分应全 PASS。
-- * 第 3 部分 INFO 行为种子预期值：按"推荐的已有库完整升级顺序"执行完
--   第 4/5 步种子 SQL 后，可重跑本脚本复核为 PASS。
-- * 第 1 部分 1.1 查询结果非空时：先清理重复 userAccount（保留一条，其余按
--   业务确认后软删），再执行 uk_userAccount 的补加，否则补索引会明确报错。
-- * v4 的列/索引在本脚本中逐项条件执行；不要直接对存量库重复执行
--   oslab_upgrade_v4_flow.sql（其 ALTER 会因列已存在而报错，且不允许靠
--   忽略错误掩盖真实失败）。
