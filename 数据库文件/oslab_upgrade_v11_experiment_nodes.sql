-- =====================================================================
-- v11：实验维护交付的可复现数据补丁
-- 内容：知识节点注册（实验 nodeMappings 依赖）+ 历史垃圾/重复实验软删
--       + 演示账号昵称（仅空值时补齐）
--
-- 适用：全新库（基础 dump + v3–v10 之后）或已有库，均可重复执行。
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 oslab_db < 本文件
-- 说明：全部语句幂等；重复执行不会产生重复节点或重复删除。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1) 知识节点注册
--    实验维护包里的 nodeMappings 必须能在 knowledge_node_registry 解析到；
--    全新库该表为空，不先注册会导致导入报“未知 nodeCode”。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `knowledge_node_registry`
    (`id`, `nodeCode`, `name`, `nodeType`, `isDelete`)
VALUES
    (7800000000000000000, 'os.overview',              '操作系统概述', 'CONCEPT', 0),
    (7800000000000000001, 'os.memory',                '内存管理',     'CONCEPT', 0),
    (7800000000000000002, 'os.memory.alloc',          '内存分配',     'CONCEPT', 0),
    (7800000000000000003, 'os.memory.page-table',     '页表',         'CONCEPT', 0),
    (7800000000000000004, 'os.memory.tlb',            'TLB',          'CONCEPT', 0),
    (7800000000000000005, 'os.memory.virtual',        '虚拟内存管理', 'CONCEPT', 0),
    (7800000000000000006, 'os.memory.page-fault',     '缺页中断',     'CONCEPT', 0),
    (7800000000000000007, 'os.process',               '进程',         'CONCEPT', 0),
    (7800000000000000008, 'os.process.image',         '进程镜像',     'CONCEPT', 0),
    (7800000000000000009, 'os.process.switch',        '进程切换',     'CONCEPT', 0),
    (7800000000000000010, 'os.process.scheduling',    '进程调度',     'CONCEPT', 0),
    (7800000000000000011, 'os.process.state',         '进程状态',     'CONCEPT', 0),
    (7800000000000000012, 'os.interrupt',             '中断',         'CONCEPT', 0),
    (7800000000000000013, 'os.syscall',               '系统调用',     'CONCEPT', 0),
    (7800000000000000014, 'os.ipc',                   '进程间通信',   'CONCEPT', 0);

-- ---------------------------------------------------------------------
-- 2) 历史垃圾/重复实验软删
--    Test / 1/1Test 与 5 条旧版占位（“啊啊啊”“11111”等）同名的重复行。
-- ---------------------------------------------------------------------
UPDATE `question` SET `isDelete` = 1
WHERE `id` IN (
    1925422266552942594,
    1925417914018824193,
    1918931948987031554,
    1918932928252157953,
    1918933052214812674,
    1918934540198682625,
    1918938841746878466
);

-- 同步软删这些实验的章节映射，避免章节计数仍包含已删除项
UPDATE `question_set_map` SET `isDelete` = 1
WHERE `questionId` IN (
    1925422266552942594,
    1925417914018824193,
    1918931948987031554,
    1918932928252157953,
    1918933052214812674,
    1918934540198682625,
    1918938841746878466
);

-- 修正历史导入（未带 utf8mb4）造成的课程名乱码；仅在仍为乱码时改写
UPDATE `course_catalog`
SET `title` = '操作系统',
    `description` = '教材驱动课程；第一章正文与测评待成员材料审核发布'
WHERE `courseCode` = 'course.operating-systems'
  AND `title` REGEXP '鎿|绯|绋';

-- ---------------------------------------------------------------------
-- 3) 演示账号昵称（仅当为空时补齐，不覆盖已设置的值）
-- ---------------------------------------------------------------------
UPDATE `user` SET `userName` = '演示学生A'
WHERE `userAccount` = 'student_demo1'
  AND (`userName` IS NULL OR `userName` = '');

UPDATE `user` SET `userName` = '演示学生B'
WHERE `userAccount` = 'student_demo2'
  AND (`userName` IS NULL OR `userName` = '');

-- ---------------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------------
SELECT 'nodes' AS item, COUNT(*) AS c
FROM `knowledge_node_registry`
WHERE `nodeCode` LIKE 'os.%' AND `isDelete` = 0
UNION ALL
SELECT 'valid_experiments', COUNT(*) FROM `question`
WHERE `isDelete` = 0
UNION ALL
SELECT 'experiments_with_cases', COUNT(*) FROM `question`
WHERE `isDelete` = 0 AND `judgeCase` <> '[{"input":"","output":""}]';
