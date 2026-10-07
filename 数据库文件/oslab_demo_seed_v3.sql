-- =====================================================================
-- OSLab 智能学习闭环 · 演示种子数据（Phase 9，可重复执行 / INSERT IGNORE 幂等）
-- 依赖：oslab_upgrade_v3_learning.sql 的 learning_* 6 张 P0 表已创建。
-- 说明：
--   1) 知识节点名称均来自 Neo4j graph.db 真实节点（含 Phase 9 预置的
--      页面置换/FIFO/LRU 页面置换算法/进程状态/CPU 调度，见 tmp/seed-graph.cypher）；
--   2) 普通题 userId 取第一位管理员（演示中"教师 A"）；不把真实密码写入 SQL；
--   3) 事件表不预置数据，掌握度由现场真实作答/判题/模拟产生（保真实可追溯）；
--   4) 班级归属使用现有 students 表（userId/className），不维护班级成员表。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 已发布普通题 ×8（四种题型各 2 道，覆盖中断/内存/进程/文件四章节点）
--    固定主键 910001~910008；答案 JSON 由服务端判分使用，永不下发学生
-- ---------------------------------------------------------------------

-- 910001 判断题 · 中断
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910001, '中断判断：中断会保存断点',
     '发生中断时，硬件会保存被打断程序的断点信息（如返回地址/状态寄存器），以便中断处理结束后恢复执行。请判断该说法是否正确。',
     'JUDGE', NULL, '"true"',
     '中断响应由硬件自动保存断点（PC/PSW），这是中断返回后能继续执行的基础。',
     '误区：以为中断现场全部由软件保存——断点由硬件保存，通用寄存器现场由处理程序保存。',
     '中断与系统调用', 1, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910002 判断题 · 文件
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910002, '文件判断：目录也是一种文件',
     '在多数操作系统中，目录（目录项集合）本身也以文件形式组织与管理。请判断该说法是否正确。',
     'JUDGE', NULL, '"true"',
     '目录是记录文件名与 FCB/ inode 映射关系的特殊文件，称为目录文件。',
     '误区：把目录当成纯粹的表格结构，忽略其同样占用磁盘块、由文件系统管理。',
     '文件系统', 1, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910003 单选题 · 系统调用
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910003, '单选：用户程序进入内核态的入口',
     '用户程序请求操作系统服务（如读文件）时，通过哪种机制从用户态进入内核态？',
     'SINGLE',
     '[{"key":"A","value":"函数调用（call 指令）"},{"key":"B","value":"系统调用（陷入 trap 指令）"},{"key":"C","value":"普通跳转（jmp 指令）"},{"key":"D","value":"进程切换"}]',
     '"B"',
     '系统调用通过陷入指令（trap/syscall）主动触发访管中断，完成用户态到内核态的受控切换。',
     '误区：把系统调用当成普通函数调用——普通 call 不改变特权级，无法进入内核。',
     '中断与系统调用', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910004 单选题 · 页表与地址映射
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910004, '单选：页式存储中逻辑地址到物理地址的转换依据',
     '页式存储管理中，逻辑地址到物理地址的转换主要依据什么数据结构完成？',
     'SINGLE',
     '[{"key":"A","value":"空闲链表"},{"key":"B","value":"进程控制块"},{"key":"C","value":"页表"},{"key":"D","value":"目录文件"}]',
     '"C"',
     '页表记录逻辑页号到物理块号的映射；MMU 按页号查页表得到物理块号，再拼接页内偏移。',
     '误区：认为转换只靠硬件寄存器——页表基址寄存器只指向页表，映射本体在页表中。',
     '内存管理', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910005 多选题 · TLB 与内存访问
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910005, '多选：关于 TLB 的正确说法',
     '下列关于 TLB（快表）的说法中，正确的有哪些？（多选）',
     'MULTIPLE',
     '[{"key":"A","value":"TLB 是页表项的高速缓存"},{"key":"B","value":"TLB 命中可避免访问内存中的页表"},{"key":"C","value":"TLB 容量通常远大于内存"},{"key":"D","value":"进程切换时通常需要刷新或更换 TLB 上下文"}]',
     '["A","B","D"]',
     'TLB 缓存近期使用的页表项，命中时直接得到物理块号；容量小（KB 级）；切换进程时地址空间改变，需刷新/打标记防止映射错乱。',
     '误区：认为 TLB 是内存的缓存（那是 Cache），或忽略切换进程时的 TLB 一致性问题。',
     '内存管理', 3, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910006 多选题 · 进程与进程控制块
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910006, '多选：进程控制块（PCB）中通常包含的内容',
     '进程控制块 PCB 中通常记录哪些信息？（多选）',
     'MULTIPLE',
     '[{"key":"A","value":"进程标识符（PID）"},{"key":"B","value":"进程状态"},{"key":"C","value":"CPU 现场寄存器快照"},{"key":"D","value":"源程序代码正文"}]',
     '["A","B","C"]',
     'PCB 保存标识、状态、调度与现场信息，是进程存在的唯一标志；程序正文属于代码段，不在 PCB 中。',
     '误区：把代码本身当成 PCB 内容——PCB 只保存管理信息与现场，不保存程序正文。',
     '进程管理', 3, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910007 填空题 · 进程调度
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910007, '填空：时间片轮转调度中时间片用完触发的动作',
     '时间片轮转（RR）调度中，正在运行的进程时间片用完时，将被剥夺 CPU 并转入________队列（填写该队列的中文名称）。',
     'FILL_BLANK', NULL,
     '["就绪","就绪队列","就绪 队列"]',
     'RR 中时间片耗尽产生时钟中断，进程回到就绪队列末尾等待下一轮调度。',
     '误区：填“阻塞队列”——阻塞是等待 I/O 等事件，与时间片用完无关。',
     '进程管理', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910008 填空题 · 进程切换
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910008, '填空：进程切换时保存与恢复 CPU 现场的位置',
     '进程切换时，被换下进程的 CPU 现场（寄存器组）被保存在它的________中（填写三个字，即进程管理核心数据结构的英文缩写）。',
     'FILL_BLANK', NULL,
     '["pcb","PCB","进程控制块"]',
     'CPU 现场保存到 PCB，调度新进程时再从其 PCB 恢复现场。',
     '误区：填“页表”——页表负责地址映射，不保存寄存器现场。',
     '进程管理', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- ---------------------------------------------------------------------
-- 2) 题目-知识节点映射（节点名与图谱真实节点一致）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911001, 910001, '中断');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911002, 910001, '时钟中断');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911003, 910002, '文件');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911004, 910003, '系统调用');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911005, 910004, '页表');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911006, 910004, '地址映射');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911007, 910005, 'TLB');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911008, 910005, '页表');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911009, 910006, '进程');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911010, 910006, '进程控制块');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911011, 910007, '进程调度');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911012, 910007, '时间片轮转算法');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911013, 910008, '进程切换');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911014, 910008, '进程');

-- ---------------------------------------------------------------------
-- 3) 审核演示链预置数据
--    910009 DRAFT（教师现场编辑后提交）
--    910010 SUBMITTED（供教师 B 现场驳回→教师 A 修改重提→审核发布）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910009, '单选：缺页中断发生后由谁装入缺失页面',
     '（草稿演示题）请求分页系统中，缺页中断处理后，缺失页面由操作系统从何处调入内存？',
     'SINGLE',
     '[{"key":"A","value":"外存（对换区/文件区）"},{"key":"B","value":"寄存器"},{"key":"C","value":"Cache 自动加载"},{"key":"D","value":"无需调入"}]',
     '"A"',
     '缺页中断处理程序负责从外存调入缺失页；内存不足时按置换算法淘汰一页。',
     '误区：把缺页调入与 Cache 预取混淆。',
     '内存管理', 3, 'DRAFT',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910010, '单选：TLB 重填异常的直接后果',
     '（待审核演示题）发生 TLB 重填异常后，最直接的处理结果是什么？',
     'SINGLE',
     '[{"key":"A","value":"终止当前进程"},{"key":"B","value":"从页表取出映射并重填 TLB 后重执行该指令"},{"key":"C","value":"立即进行进程调度"},{"key":"D","value":"清空整个内存"}]',
     '"B"',
     'TLB 重填异常处理：查页表获得映射写入 TLB，然后重新执行引发异常的指令。',
     '误区：把重填异常当成致命错误——它是可恢复的正常异常。',
     '内存管理', 3, 'SUBMITTED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911015, 910009, '地址映射');
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`) VALUES (911016, 910010, 'TLB 重填');

-- ---------------------------------------------------------------------
-- 4) 演示主线路径题目：页面置换 / LRU / CPU 调度（PUBLISHED，支撑 §2 主线）
-- ---------------------------------------------------------------------

-- 910011 判断题 · LRU 页面置换算法（主线：教师发布、学生答错判"命中后无需更新顺序"误区）
-- 收尾计划 §5.2 四元组契约（F08 修正，语义必须自洽，缺一即为过期内容）：
--   题干：陈述句"LRU 命中一个页面后，无需更新该页的最近访问顺序"（不得用"是否需要更新"问句形态）；
--   标准答案：false（该说法错误）；用于制造错误的学生答案：true；
--   解析：每次访问都更新最近访问顺序，包括命中。
-- 已有库若存在旧形态内容，无需重建：scripts/dev/verify-learning.ps1（S3.4）会按本段
-- 内容做幂等 UPDATE（条件 id=910011），或手工执行下方 VALUES 中对应字段。
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910011, '判断：LRU 命中后无需更新访问顺序',
     '使用 LRU 页面置换算法时，若访问的页面已在内存中（命中），由于没有页面被淘汰，因此不需要更新该页的最近访问顺序。请判断该说法是否正确。',
     'JUDGE', NULL, '"false"',
     'LRU 依据"最近最久未使用"做淘汰决策，每次访问（无论命中或缺页调入）都必须把该页更新为"最近使用"，否则下次淘汰判断就会出错。',
     '常见误区：把"命中页也要更新访问顺序"当成多余步骤——漏更新是 LRU 实现中最典型的错误。',
     '内存管理', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910012 单选题 · 页面置换（Belady 异常归属）
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910012, '单选：可能出现 Belady 异常的置换算法',
     '增加分配的物理块数后，缺页次数反而可能上升的异常（Belady 异常）可能出现在哪种置换算法中？',
     'SINGLE',
     '[{"key":"A","value":"FIFO（先进先出）"},{"key":"B","value":"LRU（最近最久未使用）"},{"key":"C","value":"OPT（最佳置换）"},{"key":"D","value":"LFU（最不经常使用）"}]',
     '"A"',
     'FIFO 只记录进入内存的先后顺序，与访问局部性无关，物理块数增多时缺页可能不降反升；LRU/OPT 属于栈式算法，不会出现 Belady 异常。',
     '误区：认为"任何算法块越多缺页越少"——FIFO 是经典反例。',
     '内存管理', 3, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910013 多选题 · CPU 调度与进程状态
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910013, '多选：时间片轮转（RR）调度中的正确说法',
     '关于时间片轮转（RR）调度，下列说法正确的有哪些？（多选）',
     'MULTIPLE',
     '[{"key":"A","value":"时间片用完产生时钟中断，运行进程被剥夺 CPU"},{"key":"B","value":"被剥夺的进程回到就绪队列末尾等待下一轮"},{"key":"C","value":"时间片用完的进程进入阻塞队列"},{"key":"D","value":"时间片过短会带来频繁的上下文切换开销"}]',
     '["A","B","D"]',
     'RR 中时间片耗尽触发时钟中断，当前进程由运行态转就绪态并排到就绪队列末尾；时间片过短则切换频繁、开销大。阻塞队列只在等待 I/O 等事件时进入。',
     '误区：把"时间片用完"当成阻塞——阻塞是等待事件，与被剥夺 CPU 是两回事。',
     '进程管理', 3, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910014 填空题 · FIFO 页面置换
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910014, '填空：FIFO 置换淘汰的页面',
     'FIFO 页面置换算法淘汰的是________进入内存的页面（填写中文名词，如"最早"或"最晚"）。',
     'FILL_BLANK', NULL,
     '["最早","最早进入","最早调入"]',
     'FIFO 维护页面进入内存的先后队列，缺页时淘汰队头（最早进入内存）的页面。',
     '误区：与 LRU 混淆——LRU 看"最近访问时间"，FIFO 看"进入时间"。',
     '内存管理', 1, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911017, 910011, 'LRU 页面置换算法', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911018, 910011, '页面置换', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911019, 910012, '页面置换', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911020, 910012, 'FIFO', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911021, 910013, 'CPU 调度', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911022, 910013, '进程状态', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911023, 910014, 'FIFO', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911024, 910014, '页面置换', 0.5);

-- ---------------------------------------------------------------------
-- 5) 契约 §14 演示节点补全：虚拟内存 / 缺页中断
--    虚拟内存概念在图谱中的真实节点名为"虚拟内存管理"；缺页中断由 Belady
--    题与审核演示题支撑。nodeName 均为 Neo4j 真实 EntityNode.name（已核对）。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911025, 910004, '虚拟内存管理', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911026, 910012, '缺页中断', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911027, 910009, '缺页中断', 1);
