-- =====================================================================
-- OSLab 内容种子 v4（Phase 9 §13.1：两条知识链内容充实）
-- 依据：《OSLab第三阶段执行计划-学习流程与UI重构.md》§13
-- 幂等性：
--   1) 题目/映射沿用 INSERT IGNORE（固定主键 910015~910026 / 911028~911039）；
--   2) 节点素材 learning_node_content 用 INSERT ... ON DUPLICATE KEY UPDATE
--      （按 uk_nodeName 幂等，重复执行会刷新素材内容并恢复 isDelete=0）。
-- 边界：
--   1) nodeName 均为 Neo4j graph.db 真实节点名（已通过 HTTP API 核对）；
--   2) 素材走 v5 新表 learning_node_content（PUBLISHED 直接下发学生侧）；
--   3) 事件/掌握度仍不预置，由现场真实作答产生。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 两条知识链核心节点素材 ×12（PUBLISHED）
--    链 A：内存管理 → 虚拟内存管理 → 页表 → 缺页中断 → 页面置换 → FIFO / LRU
--    链 B：进程 → 进程状态 → CPU 调度 → 时间片轮转算法 / 进程切换
-- ---------------------------------------------------------------------

-- A1 内存管理（链 A 总起）
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912001, '内存管理',
 '内存管理要解决的核心问题：物理内存有限且被多个进程共享，如何安全高效地分配与使用。

四个关键词：
1. 分配 —— 谁得到哪一段内存（连续分配 / 分页 / 分段）。
2. 映射 —— 逻辑地址如何翻译成物理地址（页表 + MMU + TLB）。
3. 保护 —— 进程之间互不越界（地址检查、权限位）。
4. 扩充 —— 让程序"看起来"拥有远超物理内存的空间（虚拟内存）。

本链后续节点沿"扩充"这条线展开：虚拟内存管理 → 页表 → 缺页中断 → 页面置换。
学习建议：先记住这条因果链，再逐节点深入。',
 '[{"title":"OSTEP 第 13 章：Abstraction: Address Spaces","url":"https://pages.cs.wisc.edu/~remzi/OSTEP/vm-intro.pdf"}]',
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- A2 虚拟内存管理
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912002, '虚拟内存管理',
 '虚拟内存 = 允许进程使用的地址空间超过实际物理内存的技术。

凭什么做得到？—— 程序局部性：任一时刻只密集访问地址空间的一小部分。
于是：把进程地址空间放在磁盘上，只把当前需要的少量页面装入内存；
未装入的页面标记为"不在内存"，访问时由缺页中断现场调入。

三个要点：
1. 每个进程独立地址空间，编译链接不再受物理内存大小限制。
2. 装入内存的单位是页，按需调入（请求分页）。
3. 内存吃紧时靠页面置换腾地方，极端情况会抖动（Thrashing）。

易混淆：虚拟内存的"虚拟"指的是地址空间虚拟，不是内存本身不存在。',
 '[{"title":"OSTEP 第 20 章：Paging: Smaller Tables / 第 21 章：Beyond Physical","url":"https://pages.cs.wisc.edu/~remzi/OSTEP/"}]',
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- A3 页表
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912003, '页表',
 '页表 = 每个进程一张"逻辑页号 → 物理块号"的映射表，是虚拟内存能运转的底账。

地址翻译流程（MMU 硬件完成）：
逻辑地址 = (页号 p, 页内偏移 w) → 查页表得物理块号 f → 物理地址 = (f, w)。

两个工程问题：
1. 页表本身也占内存：32 位空间 / 4KB 页 → 百万级表项，于是有两级页表——
   外层页目录不存在的大区域干脆不分配内层页表，省下大块空间。
2. 页表在内存里，每次翻译都多访存一次：于是有 TLB（快表）缓存热表项，
   命中时翻译零额外访存。

常见误区：页表基址寄存器（PTBR）只是"指向页表"，映射本体在页表里。',
 '[{"title":"OSTEP 第 18 章：Paging: Introduction","url":"https://pages.cs.wisc.edu/~remzi/OSTEP/vm-paging.pdf"}]',
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- A4 缺页中断
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912004, '缺页中断',
 '访问的页面不在内存 → 页表项"存在位"为 0 → 触发缺页中断。

处理流程（操作系统完成）：
1. 保存现场，确认为合法访问（否则段错误终止进程）。
2. 在外存找到缺失页；若内存有空闲块直接调入，否则先按置换算法淘汰一页
   （被淘汰页若被改过，还要写回外存）。
3. 页表项更新为"已在内存"。
4. 关键一步：重新执行被中断的那条指令——让进程毫无察觉地继续。

与普通中断的最大区别：普通中断处理后执行下一条指令；缺页中断处理完
必须重新执行当前指令，因为刚才那次访存并没有完成。

常见误区：把缺页调入当成 Cache 预取——缺页是被动的、按需的。',
 NULL,
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- A5 页面置换
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912005, '页面置换',
 '内存没有空闲块又要调入新页时，挑一个"牺牲页"淘汰——这就是页面置换。

目标：淘汰"未来最不可能再被访问"的页，让缺页率尽量低。

三个经典算法（后续两个节点逐个展开）：
1. FIFO —— 淘汰最早进入内存的页；可能出现 Belady 异常（块数增多缺页反升）。
2. LRU —— 淘汰最近最久未访问的页；栈式算法，无 Belady 异常，但精确实现开销大。
3. OPT —— 淘汰未来最长时间不再访问的页；理论最优、需要预知未来，实际系统做不到，
   常用作对照基准。

工程现实：真实系统普遍用"近似 LRU"（如 Clock 时钟算法），引用位轮询代替精确时间戳。

一个必须警惕的现象：抖动——缺页率过高导致系统忙于换页而几乎无法推进工作。',
 '[{"title":"OSTEP 第 22 章：Beyond Physical Mechanisms","url":"https://pages.cs.wisc.edu/~remzi/OSTEP/vm-beyondphys.pdf"}]',
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- A6 FIFO
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912006, 'FIFO',
 'FIFO（先进先出）页面置换：维护一个按"进入内存时间"排的队列，缺页时淘汰队头。

优点：极简单，一个指针即可。
致命短板：与访问局部性完全脱节——一个早早调入但一直在被频繁使用的页，
仅因为它来得早就被淘汰。

Belady 异常：分配的物理块数增加，缺页次数反而可能上升。
原因是 FIFO 的"年龄"信息不满足栈式性质（同一时刻 n 块的页面集合不一定
包含 n-1 块的集合）。经典反例访问串：1,2,3,4,1,2,5,1,2,3,4,5（3 块 9 次、4 块 10 次）。

对比记忆：LRU / OPT 是栈式算法，不会出现 Belady 异常。',
 NULL,
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- A7 LRU 页面置换算法
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912007, 'LRU 页面置换算法',
 'LRU（最近最久未使用）：缺页时淘汰"最长时间没有被访问过"的页面。

核心直觉：过去的信息预测未来——最近用过的页，近期大概率还会用。

一个最容易踩的实现坑：命中也必须更新访问顺序！
LRU 的排序依据是"最近访问时间"，不只是"调入时间"。命中某页后若不把它
挪到"最近使用"一端，下次缺页就可能错杀这个热页。这是 LRU 编程实验的
高频错误点。

实现方式与代价：
1. 时间戳法：每次访存记时间，缺页时找最旧——访存开销大。
2. 栈/双向链表 + 哈希：命中即移动到队头，淘汰取队尾——精确但工程复杂。
3. 近似 LRU（Clock）：引用位 + 环形指针扫描，硬件友好，实际系统首选。

验证建议：用访问串 7,0,1,2,0,3,0,4,2,3,0,3,2 手推 3 块 LRU，应得 9 次缺页
（同串 FIFO 为 10 次，OPT 为 7 次）。',
 '[{"title":"OS 经典页置换动画对照（含 FIFO/LRU/OPT）","url":"https://pages.cs.wisc.edu/~remzi/OSTEP/vm-beyondphys.pdf"}]',
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- B1 进程（链 B 总起）
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912008, '进程',
 '进程 = 程序在某个数据集合上的一次执行过程，是资源分配的基本单位。

进程 ≠ 程序：程序是静态的指令与数据文件；进程是动态的、有生命周期的执行实体。
同一个程序同时运行三次，是三个独立进程。

进程的"身份证"是 PCB（进程控制块）：标识、状态、优先级、CPU 现场、
内存与文件资源清单……操作系统对进程的一切管理都围绕 PCB 展开，
"PCB 存在即进程存在"。

进程与线程的关系（延伸）：线程是 CPU 调度的基本单位，同进程线程共享地址空间。

本链后续节点：进程状态 → CPU 调度 → 时间片轮转算法 / 进程切换。',
 '[{"title":"OSTEP 第 4 章：Processes","url":"https://pages.cs.wisc.edu/~remzi/OSTEP/cpu-intro.pdf"}]',
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- B2 进程状态
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912009, '进程状态',
 '五态模型：创建 → 就绪 ⇄ 运行 → 终止；运行可转阻塞（等待事件），阻塞在事件完成后回就绪。

合法转换逐条记：
1. 就绪 → 运行：被调度程序选中（唯一进入运行的方式）。
2. 运行 → 就绪：被剥夺 CPU（时间片用完 / 更高优先级到达）。
3. 运行 → 阻塞：主动等待事件（I/O 请求、信号量 P 操作失败）。
4. 阻塞 → 就绪：等待的事件发生（I/O 完成中断、资源被释放）。

两条"不可能"边（高频考点）：
- 阻塞 → 运行：不可能。阻塞结束先回就绪，必须再等调度。
- 就绪 → 阻塞：不可能。还没运行谈不上等事件。

区分两组概念：就绪是"只差 CPU"，阻塞是"在等事件"；阻塞队列与就绪队列
是完全不同的两个队列。',
 NULL,
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- B3 CPU 调度
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912010, 'CPU 调度',
 'CPU 调度：从就绪队列里挑一个进程，把 CPU 分给它。目标是吞吐量、周转时间、
响应时间之间的权衡（交互式系统优先响应时间）。

调度的两个时机问题——什么时候会发生调度：
1. 非剥夺点：进程阻塞 / 正常终止（必须调度）。
2. 可剥夺点：时间片用完、更高优先级进程到达（是否剥夺取决于调度方式）。

三大经典算法（静态/动态看"下次 CPU 区间"的预估）：
1. FCFS（先来先服务）：按到达顺序，简单但对短作业不公平（护航效应）。
2. SJF（短作业优先）：平均周转时间理论最优；需要预知运行时长，可能饿死长作业。
3. RR（时间片轮转）：见下一节点"时间片轮转算法"。

判定题口诀：所有作业同时到达时 SJF 最优；抢占式 SJF（SRTF）还要在
新进程到达时比较剩余时间。',
 '[{"title":"OSTEP 第 7 章：CPU Scheduling","url":"https://pages.cs.wisc.edu/~remzi/OSTEP/cpu-sched.pdf"}]',
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- B4 时间片轮转算法
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912011, '时间片轮转算法',
 'RR（Round Robin）：就绪队列排成环，每个进程最多连续运行一个时间片 q，
用完即被剥夺、排到队尾——交互式系统的响应时间保障。

时间片 q 的两难：
1. q 太大 → 每个进程要等很久才轮到一次，响应变慢；q 趋于无穷时退化为 FCFS。
2. q 太小 → 上下文切换过于频繁，CPU 大量时间耗在切换开销上（有效做功比例下降）。
经验值：切换开销 1% 以内，q 约为 10~100ms 量级。

机制细节：时间片用完由时钟中断触发剥夺，进程状态是"运行 → 就绪"（不是阻塞！），
进的是就绪队列尾；只有等待 I/O 才进阻塞队列。',
 NULL,
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- B5 进程切换
INSERT INTO `learning_node_content`
    (`id`,`nodeName`,`contentMd`,`resourceLinks`,`status`,`updateUserId`,`isDelete`)
VALUES (912012, '进程切换',
 '进程切换 = 把 CPU 从一个进程转给另一个进程，核心动作是"现场保存 + 现场恢复"。

完整流程：
1. 保存当前进程 CPU 现场（寄存器组：PC、SP、通用寄存器……）到它的 PCB。
2. 更新两个 PCB：旧进程改状态（就绪/阻塞）、新进程改运行态，相应队列移动。
3. 切换页表基址（换地址空间）、恢复新进程现场，跳转继续执行。

三个高频误区：
1. 现场保存在 PCB，不是"栈"也不是"页表"——页表只管地址映射。
2. 切换 ≠ 换出内存：进程让出 CPU 后通常仍留在内存（除非内存吃紧才对换）。
3. 切换有真实成本（微秒级 × 高频 = 显著开销），这正是 RR 时间片不能太小的根源。',
 NULL,
 'PUBLISHED',
 (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0)
ON DUPLICATE KEY UPDATE `contentMd` = VALUES(`contentMd`), `resourceLinks` = VALUES(`resourceLinks`),
    `status` = 'PUBLISHED', `updateUserId` = VALUES(`updateUserId`), `isDelete` = 0;

-- ---------------------------------------------------------------------
-- 2) 测评题 +12（910015~910026，全部 PUBLISHED；含 v3 后共 24 道）
--    链 A 6 道（判断 2 / 单选 2 / 多选 1 / 填空 1），链 B 6 道同结构
-- ---------------------------------------------------------------------

-- 910015 判断 · 虚拟内存管理
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910015, '判断：虚拟内存可让进程地址空间超过物理内存',
     '借助虚拟内存技术，进程可使用的地址空间可以超过机器的实际物理内存容量。请判断该说法是否正确。',
     'JUDGE', NULL, '"true"',
     '虚拟内存把进程地址空间放在磁盘上、按需调入内存页，逻辑容量受地址位数限制而非物理内存限制。',
     '误区：以为程序规模必须不超过物理内存——按需调页正是为了突破这个限制。',
     '内存管理', 1, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910016 单选 · 缺页中断
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910016, '单选：缺页中断处理后缺页指令的去向',
     '请求分页系统中，进程访问的页面不在内存而触发缺页中断，操作系统把页面调入内存后，通常如何处理那条触发缺页的指令？',
     'SINGLE',
     '[{"key":"A","value":"放弃该指令，直接执行下一条"},{"key":"B","value":"重新执行该指令，进程无感知地继续"},{"key":"C","value":"跳过本次访存，结果按 0 处理"},{"key":"D","value":"终止当前进程"}]',
     '"B"',
     '缺页处理结束时页表项已更新为"在内存"，重新执行原指令即可完成那次访存；进程对缺页过程完全无感知。',
     '误区：以为缺页后跳过或放弃该指令——普通中断才处理完执行下一条，缺页中断必须重执行当前指令。',
     '内存管理', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910017 多选 · 页面置换三算法对比
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910017, '多选：FIFO / LRU / OPT 置换算法对比',
     '关于 FIFO、LRU、OPT 三种页面置换算法，下列说法正确的有哪些？（多选）',
     'MULTIPLE',
     '[{"key":"A","value":"OPT 淘汰的是未来最长时间内不再被访问的页面"},{"key":"B","value":"LRU 可用栈或计数实现，精确实现开销较大"},{"key":"C","value":"FIFO 是栈式算法，不会出现 Belady 异常"},{"key":"D","value":"OPT 需要预知未来访问序列，实际系统无法直接实现"}]',
     '["A","B","D"]',
     'OPT 依据未来信息做理论最优，无法实用，常作对照基准；LRU 用过去信息近似未来，精确实现需维护访问顺序；FIFO 恰恰会出现 Belady 异常（不是栈式算法）。',
     '误区：把 FIFO 当成栈式算法——块数增多缺页反升的 Belady 异常正是 FIFO 的经典反例。',
     '内存管理', 3, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910018 填空 · OPT
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910018, '填空：OPT 置换的淘汰依据',
     'OPT（最佳置换）算法在缺页时淘汰的是________内不再被访问的页面（从当前时刻向未来看，填写六字：未来最久/最长时间内等相近表述均可尝试）。',
     'FILL_BLANK', NULL,
     '["未来最长时间","未来最久","最长时间内","未来最长时间内"]',
     'OPT 依据"未来访问距离"淘汰最远页面，理论最优；因需预知未来仅作基准。',
     '误区：答成"最近最久未使用"——那是 LRU 的依据（看过去），OPT 看未来。',
     '内存管理', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910019 单选 · TLB 缺失
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910019, '单选：TLB 缺失且页面在内存时的处理',
     '地址翻译时发生 TLB 缺失（快表未命中），但对应页面已经在内存中，处理器接下来要做什么？',
     'SINGLE',
     '[{"key":"A","value":"直接产生缺页中断淘汰一个页面"},{"key":"B","value":"访问内存中的页表取得映射，填入 TLB 后完成翻译"},{"key":"C","value":"把该进程换出到磁盘"},{"key":"D","value":"切换到另一个进程"}]',
     '"B"',
     'TLB 缺失只是快表里没有该表项，页表里映射仍有效：查页表、回填 TLB、继续翻译即可，与页面是否在内存无关。',
     '误区：把 TLB 缺失当成缺页中断——只有页表项存在位为 0（页不在内存）才触发缺页中断。',
     '内存管理', 3, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910020 判断 · 两级页表机制
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910020, '判断：两级页表的主要动机',
     '引入两级页表的主要目的之一，是让没有对应物理页的页表项所在的内层页表不必分配内存，从而节省页表本身占用的空间。请判断该说法是否正确。',
     'JUDGE', NULL, '"true"',
     '单级页表对整个地址空间都要连续分配表项；两级结构中未使用的大区域只需一个"不存在"的外层目录项，内层页表整张省掉。',
     '误区：以为两级页表是为了加快地址翻译——多一级反而多一次访存，加速靠的是 TLB。',
     '内存管理', 3, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910021 判断 · 进程状态（阻塞→运行不可能）
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910021, '判断：阻塞态进程可否直接进入运行态',
     '处于阻塞态的进程，在它等待的事件（例如 I/O 完成）发生后，可以直接进入运行态。请判断该说法是否正确。',
     'JUDGE', NULL, '"false"',
     '事件发生后进程由阻塞态转为就绪态，进入就绪队列等待调度；只有被调度程序选中才能运行。',
     '误区：忽略就绪中间态——"事件完成"只解锁，"上 CPU"必须经过调度。',
     '进程管理', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910022 单选 · CPU 调度（剥夺时机）
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910022, '单选：抢占式调度下可能触发调度的情形',
     '采用可剥夺（抢占式）调度方式时，下列哪些情形可能触发进程调度？',
     'SINGLE',
     '[{"key":"A","value":"一个高优先级进程进入就绪队列"},{"key":"B","value":"当前运行进程的时间片用完"},{"key":"C","value":"当前运行进程发起 I/O 请求而阻塞"},{"key":"D","value":"以上都可能"}]',
     '"D"',
     '高优先级到达与时间片用完是抢占点；进程阻塞/终止时必须重新调度——三类情形都可能触发调度。',
     '误区：以为抢占调度只在时间片用完时发生——优先级抢占同样是经典触发条件。',
     '进程管理', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910023 多选 · 进程切换
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910023, '多选：进程切换时操作系统要做的事',
     '发生进程切换时，操作系统需要完成哪些工作？（多选）',
     'MULTIPLE',
     '[{"key":"A","value":"把当前进程的 CPU 现场保存到它的 PCB"},{"key":"B","value":"更新两个进程的 PCB 状态并调整相应队列"},{"key":"C","value":"恢复新进程的 CPU 现场并切换其地址空间"},{"key":"D","value":"必须把旧进程的全部内存换出到磁盘"}]',
     '["A","B","C"]',
     '切换 = 现场保存入 PCB + 状态与队列更新 + 新进程现场恢复（含页表基址切换）。让出 CPU 的进程通常仍留在内存。',
     '误区：以为切换必须换出内存——只有内存不足时才对换；切换本身不搬数据。',
     '进程管理', 3, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910024 填空 · SJF
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910024, '填空：SJF 优化的指标',
     'SJF（短作业优先）调度在所有作业同时到达的前提下，可使系统的平均________最短（填写四字：周转时间）。',
     'FILL_BLANK', NULL,
     '["周转时间","平均周转时间"]',
     '短作业先跑，后面的短作业不被长作业压着，平均周转时间（完成-到达）理论最优。',
     '误区：答"响应时间"——优化响应时间是 RR 的目标；SJF 优化的是周转类指标。',
     '进程管理', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910025 判断 · 时间片轮转算法（时间片过大）
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910025, '判断：时间片过大的后果',
     '时间片轮转调度中，若时间片设置过大，算法行为会逐渐退化为先来先服务（FCFS）。请判断该说法是否正确。',
     'JUDGE', NULL, '"true"',
     '时间片大到每个进程都能在一个时间片内跑完，就不存在"用完剥夺、排队尾"，等价于按到达顺序依次执行。',
     '误区：以为时间片越大响应越快——恰恰相反，轮转一圈的周期变长，短请求要等很久。',
     '进程管理', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- 910026 单选 · 进程状态（不可能的转换）
INSERT IGNORE INTO `learning_question`
    (`id`,`title`,`content`,`type`,`options`,`answer`,`analysis`,`misconception`,`chapter`,`difficulty`,`reviewStatus`,`userId`,`isDelete`)
VALUES
    (910026, '单选：不可能发生的进程状态转换',
     '下列进程状态转换中，不可能发生的是哪一个？',
     'SINGLE',
     '[{"key":"A","value":"运行态 → 就绪态"},{"key":"B","value":"就绪态 → 运行态"},{"key":"C","value":"阻塞态 → 运行态"},{"key":"D","value":"运行态 → 阻塞态"}]',
     '"C"',
     '运行→就绪（被剥夺）、就绪→运行（被调度）、运行→阻塞（等事件）都是合法转换；阻塞结束只能先回就绪态。',
     '误区：认为阻塞结束可直接运行——调度必须从就绪队列选进程。',
     '进程管理', 2, 'PUBLISHED',
     (SELECT id FROM `user` WHERE userRole = 'admin' ORDER BY id ASC LIMIT 1), 0);

-- ---------------------------------------------------------------------
-- 3) 题目-知识节点映射（911028~911039；nodeName 均为图谱真实节点）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911028, 910015, '虚拟内存管理', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911029, 910015, '内存管理', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911030, 910016, '缺页中断', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911031, 910016, '页面置换', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911032, 910017, '页面置换', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911033, 910017, 'LRU 页面置换算法', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911034, 910017, 'FIFO', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911035, 910018, '页面置换', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911036, 910019, 'TLB 缺失', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911037, 910019, 'TLB', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911038, 910020, '两级页表机制', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911039, 910020, '页表', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911040, 910021, '进程状态', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911041, 910021, '进程', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911042, 910022, 'CPU 调度', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911043, 910022, '进程调度', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911044, 910023, '进程切换', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911045, 910023, '进程控制块', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911046, 910024, 'CPU 调度', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911047, 910025, '时间片轮转算法', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911048, 910025, 'CPU 调度', 0.5);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911049, 910026, '进程状态', 1);
INSERT IGNORE INTO `learning_question_node` (`id`,`questionId`,`nodeName`,`weight`) VALUES (911050, 910026, '进程调度', 0.5);
