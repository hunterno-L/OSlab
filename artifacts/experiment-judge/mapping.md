# 41 个北航编程实验 —— 图谱节点映射与学习单元映射总表

生成方式：`node artifacts/experiment-judge/ch1/gen-all-mappings.mjs`（一次性生成器，Agent B 独占目录）。
数据来源：`artifacts/experiment-judge/registry.txt`（199 个 `knowledge_node_registry` 节点，只读 SELECT 导出）、
`artifacts/experiment-judge/units.txt`（**104** 个已发布 `learning_unit`，只读 SELECT 导出；计划 md 写的 105 比实际多 1，以实际清单为准）、
`artifacts/experiment-judge/current-content.tsv`（46 行 `question.content` 原文，用于确定每个实验的真实目标函数）。

**范围**：41 个真实实验 = 第一章 5 + 第二章 8 + 第三章 13（无 3.11）+ 第四章 15。
**已排除**：5 条垃圾重复行（`1918931948987031554` 4.3 / `1918932928252157953` 4.5 / `1918933052214812674` 4.6 /
`1918934540198682625` 4.7 / `1918938841746878466` 4.8，content 仅 5–18 字节）与
2 条软删除行（`1918945654303125506` 4.10 / `1920028942673477633` 4.11）。

**Tier 分布**：A（沙箱可功能判题）34 个 / B（LOCAL_BUILD，不自动判分）5 个 / C（概念子任务可判）2 个。

**规则**：每实验恰好 1 个 PRIMARY（weight 1.0）+ 1–3 个 RELATED；`nodeName` 由生成器直接从 registry 读取，逐字相等；
`required` 一律 `false`（实验是深化环节，不卡既有单元主线，也不改动 104 个已发布单元的 `requiredCount` 语义）；
Tier A/C 用 `completionRule:"PASS"`，Tier B 用 `"SUBMIT"`。

**落库路径**（Task 8，主 agent 执行，本轮未做任何数据库写操作）：
`nodes.json` → `experiment_node` 表（`CourseContentService.java:331` 的 EXPERIMENT 活动节点唯一来源）；
`units.json` → `ExperimentMaintenanceService.java:80-82` 的 `unitMappings`，自动创建 `act.exp.<snowflake>` 活动。
注意 `CourseContentService.java:268`：学生端读的是 `publishedJson`，**只插表不改 JSON 等于没接线**。

---

## 总表（41 行）

| # | 实验 id | 标题 | Tier | PRIMARY 节点 | RELATED 节点(weight) | 目标单元 / 完成规则 | 真实目标函数或源码 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | `1917131319528452098` | 构建 Minix 内核 | C | `os.memory.loading_linking`<br>程序的链接与装入 | `os.boot.boot_process`(0.7)<br>`os.runtime.kernel`(0.6) | `unit.memory.functions`<br>PASS<br>`unit.overview.boot`<br>PASS | 顶层 Makefile / include.mk（CROSS_COMPILE、LD）；判分子任务 build_order() |
| 1.2 | `1930153941795131394` | 深入理解 ELF 文件格式 | A | `os.memory.loading_linking`<br>程序的链接与装入 | `os.memory.process_image`(0.6)<br>`os.memory.logical_address`(0.5) | `unit.memory.functions`<br>PASS | readelf.c 的 readelf()；输出契约 "%x:d:0x%x\n" |
| 1.3 | `1930154079598989313` | 使用 Linker Script 控制内核加载地址 | C | `os.memory.loading_linking`<br>程序的链接与装入 | `os.memory.process_image`(0.7)<br>`os.boot.boot_process`(0.6) | `unit.memory.functions`<br>PASS<br>`unit.overview.boot`<br>PASS | tools/scse0_3.lds；判分子任务 layout_sections() |
| 1.4 | `1930154331844431873` | 深入理解 MIPS 汇编与 C 语言的交互 | B | `os.boot.boot_process`<br>操作系统引导过程 | `os.runtime.kernel_mode`(0.7)<br>`os.runtime.kernel`(0.5) | `unit.overview.boot`<br>SUBMIT<br>`unit.overview.runtime-modes`<br>SUBMIT | boot/start.S（设 $sp 后跳 main）+ fib/main 的交叉编译汇编分析 |
| 1.5 | `1930154460768948226` | 实现内核中的 printf 函数 | A | `os.runtime.kernel`<br>操作系统内核 | `os.io.device_driver`(0.7)<br>`os.runtime.kernel_mode`(0.5) | `unit.overview.runtime-modes`<br>PASS<br>`unit.io.driver`<br>PASS | lib/print.c 的 _Printf()；输出经 drivers/gzconsole/console.c |
| 2.1 | `1908449571747745793` | 内存控制块 | A | `os.memory.paging`<br>基本分页存储管理 | `os.memory.memory_management`(0.7)<br>`os.memory.page_table`(0.6) | `unit.memory.paging-basics`<br>PASS<br>`unit.memory.functions`<br>PASS | include/queue.h 的 LIST_INSERT_AFTER / LIST_INSERT_TAIL |
| 2.2 | `1908532874240593922` | 初始化全局变量 | A | `os.memory.paging`<br>基本分页存储管理 | `os.memory.memory_management`(0.7)<br>`os.memory.logical_address`(0.5) | `unit.memory.functions`<br>PASS<br>`unit.memory.paging-basics`<br>PASS | mm/pmap.c 的 mips_detect_memory() |
| 2.3 | `1908538371526131713` | 空闲内存管理 | A | `os.memory.paging`<br>基本分页存储管理 | `os.memory.memory_management`(0.7)<br>`os.memory.logical_address`(0.5) | `unit.memory.paging-basics`<br>PASS | mm/pmap.c 的 page_init() |
| 2.4 | `1908539177407119361` | 内存分配与释放管理 | A | `os.memory.paging`<br>基本分页存储管理 | `os.memory.memory_management`(0.7)<br>`os.memory.sharing`(0.6) | `unit.memory.paging-basics`<br>PASS | mm/pmap.c 的 page_alloc() / page_free() |
| 2.5 | `1908539875976839170` | 页表创建 | A | `os.memory.page_table`<br>页表 | `os.memory.multilevel_page_table`(0.8)<br>`os.memory.address_translation`(0.7) | `unit.memory.paging-basics`<br>PASS<br>`unit.memory.tlb-multilevel`<br>PASS | mm/pmap.c 的 boot_pgdir_walk() / pgdir_walk() |
| 2.6 | `1908540360305704962` | 地址映射 | A | `os.memory.address_translation`<br>地址翻译 | `os.memory.page_table`(0.8)<br>`os.memory.logical_address`(0.6) | `unit.memory.paging-basics`<br>PASS<br>`unit.memory.address-translation`<br>PASS | mm/pmap.c 的 boot_map_segment() |
| 2.7 | `1908540572415852545` | 页表项插入与移除 | A | `os.memory.page_table`<br>页表 | `os.memory.tlb`(0.8)<br>`os.memory.sharing`(0.6) | `unit.memory.paging-basics`<br>PASS<br>`unit.memory.tlb-multilevel`<br>PASS | mm/pmap.c 的 page_insert() / page_remove() |
| 2.8 | `1908540698916061185` | TLB 管理 | B | `os.memory.tlb`<br>TLB 快表 | `os.memory.address_translation`(0.7)<br>`os.runtime.exception`(0.6) | `unit.memory.tlb-multilevel`<br>SUBMIT | mm/tlb_asm.S 的 tlb_out（两行空白填 tlbp / tlbwi） |
| 3.1 | `1908900051330306049` | 初始化进程控制块数组 | A | `os.process.pcb`<br>进程控制块 | `os.memory.process_image`(0.6)<br>`os.process.process_state`(0.6) | `unit.process.composition`<br>PASS<br>`unit.memory.image-protection`<br>PASS | mm/pmap.c 的 mips_vm_init()（为 envs[NENV] 分配并映射到 UENVS） |
| 3.2 | `1930154686162456577` | 管理进程控制块数组 | A | `os.process.pcb`<br>进程控制块 | `os.process.process_control`(0.7)<br>`os.process.process_state`(0.6) | `unit.process.composition`<br>PASS<br>`unit.process.control`<br>PASS | lib/env.c 的 env_init() |
| 3.3 | `1930154848414912513` | 进程标识 | A | `os.process.pcb`<br>进程控制块 | `os.process.process`(0.7)<br>`os.process.process_control`(0.5) | `unit.process.composition`<br>PASS<br>`unit.process.control`<br>PASS | lib/env.c 的 envid2env()（配合 mkenvid()） |
| 3.4 | `1930154980048949249` | 初始化进程虚拟内存布局 | A | `os.memory.process_image`<br>进程内存映像 | `os.memory.page_table`(0.8)<br>`os.memory.virtual_memory`(0.6) | `unit.memory.image-protection`<br>PASS<br>`unit.memory.paging-basics`<br>PASS | lib/env.c 的 env_setup_vm() |
| 3.5 | `1930155119123681282` | 设置进程控制块 | A | `os.process.pcb`<br>进程控制块 | `os.runtime.mode_switch`(0.8)<br>`os.process.process_control`(0.7) | `unit.process.composition`<br>PASS<br>`unit.overview.runtime-modes`<br>PASS | lib/env.c 的 env_alloc() |
| 3.6 | `1930155245821022209` | 加载镜像映射 | A | `os.memory.process_image`<br>进程内存映像 | `os.memory.loading_linking`(0.8)<br>`os.memory.page_table`(0.6) | `unit.memory.image-protection`<br>PASS<br>`unit.memory.functions`<br>PASS | lib/env.c 的 load_icode_mapper()（配合 load_elf()） |
| 3.7 | `1930155360556208129` | 完整加载镜像 | A | `os.memory.process_image`<br>进程内存映像 | `os.memory.loading_linking`(0.7)<br>`os.process.pcb`(0.6) | `unit.memory.image-protection`<br>PASS<br>`unit.process.composition`<br>PASS | lib/env.c 的 load_icode() |
| 3.8 | `1930155484338507777` | 创建进程 | A | `os.process.process_control`<br>进程控制 | `os.process.pcb`(0.8)<br>`os.cpu.rr`(0.5) | `unit.process.control`<br>PASS<br>`unit.process.composition`<br>PASS | lib/env.c 的 env_create() / env_create_priority() |
| 3.9 | `1930155681017810946` | 进程创建宏命令应用 | A | `os.process.process_control`<br>进程控制 | `os.boot.boot_process`(0.7)<br>`os.cpu.rr`(0.5) | `unit.process.control`<br>PASS<br>`unit.overview.boot`<br>PASS | init/init.c 中的 ENV_CREATE_PRIORITY(user_A,2) / ENV_CREATE_PRIORITY(user_B,1) |
| 3.10 | `1930155792691154945` | 进程运行与切换 | A | `os.cpu.context_switch`<br>上下文切换 | `os.process.process_state`(0.7)<br>`os.runtime.mode_switch`(0.6) | `unit.process.states`<br>PASS<br>`unit.overview.runtime-modes`<br>PASS | lib/env.c 的 env_run()（配合 lcontext / env_pop_tf / TIMESTACK） |
| 3.12 | `1930155905954140162` | 异常处理 | B | `os.runtime.exception`<br>异常 | `os.runtime.interrupt_handling`(0.7)<br>`os.memory.loading_linking`(0.5) | `unit.overview.interrupts`<br>SUBMIT | lds 中 0x80000080 异常入口 + 入口处的异常分发汇编 |
| 3.13 | `1930156027966443521` | 时钟中断 | B | `os.runtime.interrupt`<br>中断 | `os.cpu.rr`(0.8)<br>`os.runtime.interrupt_handling`(0.6) | `unit.overview.interrupts`<br>SUBMIT<br>`unit.process.rr-mlq-mlfq`<br>SUBMIT | kclock_init() / timer_irq() / lds 中断入口 / sched_yield() |
| 3.14 | `1930156143783759874` | 进程调度实验 | A | `os.cpu.rr`<br>时间片轮转 | `os.cpu.scheduler`(0.8)<br>`os.process.process_state`(0.6) | `unit.process.rr-mlq-mlfq`<br>PASS<br>`unit.process.states`<br>PASS | lib/sched.c 的 sched_yield() + env.c 的就绪链表改造 |
| 4.1 | `1930156360117571585` | 实现用户空间到内核空间的系统调用机制 | B | `os.runtime.system_call`<br>系统调用 | `os.runtime.trap_instruction`(0.8)<br>`os.runtime.mode_switch`(0.7) | `unit.overview.system-calls`<br>SUBMIT<br>`unit.overview.runtime-modes`<br>SUBMIT | user/syscall_wrap.S 的 syscall 封装 + 内核侧 mysyscall |
| 4.2 | `1930156493454495745` | 实现内核的系统调用处理机制 | A | `os.runtime.system_call`<br>系统调用 | `os.runtime.mode_switch`(0.7)<br>`os.runtime.exception`(0.6) | `unit.overview.system-calls`<br>PASS<br>`unit.overview.runtime-modes`<br>PASS | handle_sys() + sys_call_table[] |
| 4.3 | `1930156605618573314` | 基础系统调用函数 sys_mem_alloc | A | `os.runtime.system_call`<br>系统调用 | `os.memory.virtual_memory`(0.7)<br>`os.memory.page_table`(0.6) | `unit.overview.system-calls`<br>PASS<br>`unit.memory.image-protection`<br>PASS | lib/syscall_all.c 的 sys_mem_alloc(sysno, envid, va, perm) |
| 4.4 | `1930156740377366529` | 基础系统调用函数 sys_mem_map | A | `os.memory.sharing`<br>内存共享 | `os.runtime.system_call`(0.8)<br>`os.memory.page_table`(0.6) | `unit.memory.image-protection`<br>PASS<br>`unit.memory.mmap-performance`<br>PASS | lib/syscall_all.c 的 sys_mem_map(sysno, srcid, srcva, dstid, dstva, perm) |
| 4.5 | `1930156844614209538` | 基础系统调用函数 sys_mem_unmap | A | `os.memory.sharing`<br>内存共享 | `os.runtime.system_call`(0.8)<br>`os.memory.tlb`(0.5) | `unit.memory.image-protection`<br>PASS<br>`unit.memory.mmap-performance`<br>PASS | lib/syscall_all.c 的 sys_mem_unmap(sysno, envid, va) |
| 4.6 | `1930156972624367618` | 基础系统调用函数 sys_yield | A | `os.cpu.rr`<br>时间片轮转 | `os.cpu.context_switch`(0.8)<br>`os.runtime.system_call`(0.6) | `unit.process.rr-mlq-mlfq`<br>PASS<br>`unit.overview.system-calls`<br>PASS | lib/syscall_all.c 的 sys_yield()（KERNEL_SP / TIMESTACK 现场保存） |
| 4.7 | `1930157095085461505` | 实现进程间通信（IPC）机制 | A | `os.process.ipc`<br>进程间通信 | `os.process.message_passing`(0.8)<br>`os.process.process_state`(0.6) | `unit.process.ipc`<br>PASS<br>`unit.process.states`<br>PASS | lib/syscall_all.c 的 sys_ipc_recv() / sys_ipc_can_send() |
| 4.8 | `1930157212584693762` | 进程创建系统调用 sys_env_alloc | A | `os.process.process_control`<br>进程控制 | `os.runtime.system_call`(0.8)<br>`os.process.pcb`(0.6) | `unit.process.control`<br>PASS<br>`unit.overview.system-calls`<br>PASS | lib/syscall_all.c 的 sys_env_alloc() |
| 4.9 | `1930157404163723265` | 实现 fork 系统调用中的 sys_env_alloc | A | `os.process.process_control`<br>进程控制 | `os.process.pcb`(0.7)<br>`os.runtime.system_call`(0.6) | `unit.process.control`<br>PASS<br>`unit.overview.system-calls`<br>PASS | user/fork.c 的 fork() 中调用 sys_env_alloc 与子进程返回逻辑 |
| 4.10 | `1930157516164222977` | 实现 fork 系统调用中的 duppage 函数 | A | `os.memory.sharing`<br>内存共享 | `os.memory.page_fault`(0.8)<br>`os.memory.page_table`(0.6) | `unit.memory.virtual-concepts`<br>PASS<br>`unit.memory.demand-paging`<br>PASS | user/fork.c 的 duppage()（PTE_COW / PTE_LIBRARY 分类处理） |
| 4.11 | `1930157629511094274` | 实现缺页中断处理函数 page_fault_handler | A | `os.memory.page_fault`<br>缺页中断 | `os.memory.demand_paging`(0.8)<br>`os.runtime.exception`(0.6) | `unit.memory.demand-paging`<br>PASS<br>`unit.overview.interrupts`<br>PASS | lib/traps.c 的 page_fault_handler() |
| 4.12 | `1930157740261691394` | 实现设置缺页处理函数的系统调用 sys_set_pgfault_handler | A | `os.memory.page_fault`<br>缺页中断 | `os.runtime.system_call`(0.8)<br>`os.process.pcb`(0.6) | `unit.memory.demand-paging`<br>PASS<br>`unit.overview.system-calls`<br>PASS | lib/syscall_all.c 的 sys_set_pgfault_handler() |
| 4.13 | `1930157867273605121` | 实现 fork 系统调用中的缺页处理函数 pgfault | A | `os.memory.page_fault`<br>缺页中断 | `os.memory.sharing`(0.8)<br>`os.memory.demand_paging`(0.6) | `unit.memory.virtual-concepts`<br>PASS<br>`unit.memory.demand-paging`<br>PASS | user/fork.c 的 pgfault() |
| 4.14 | `1930157984642813953` | 实现进程状态设置系统调用 sys_set_env_status | A | `os.process.process_state`<br>进程状态与转换 | `os.cpu.scheduler`(0.7)<br>`os.runtime.system_call`(0.6) | `unit.process.states`<br>PASS<br>`unit.overview.system-calls`<br>PASS | lib/syscall_all.c 的 sys_set_env_status() |
| 4.15 | `1930158086405017602` | 实现 fork 系统调用 | A | `os.process.process_control`<br>进程控制 | `os.memory.sharing`(0.8)<br>`os.process.pcb`(0.6) | `unit.process.control`<br>PASS<br>`unit.memory.demand-paging`<br>PASS | user/fork.c 的 fork() 父进程部分 |

---

## 节点使用频次（共用到 32 / 199 个 registry 节点）

| nodeCode | nodeName | 被引用实验数 |
| --- | --- | --- |
| `os.process.pcb` | 进程控制块 | 10 |
| `os.runtime.system_call` | 系统调用 | 10 |
| `os.memory.page_table` | 页表 | 9 |
| `os.process.process_control` | 进程控制 | 8 |
| `os.memory.sharing` | 内存共享 | 7 |
| `os.memory.loading_linking` | 程序的链接与装入 | 6 |
| `os.memory.process_image` | 进程内存映像 | 6 |
| `os.process.process_state` | 进程状态与转换 | 6 |
| `os.cpu.rr` | 时间片轮转 | 5 |
| `os.boot.boot_process` | 操作系统引导过程 | 4 |
| `os.memory.logical_address` | 逻辑地址与物理地址 | 4 |
| `os.memory.memory_management` | 内存管理 | 4 |
| `os.memory.page_fault` | 缺页中断 | 4 |
| `os.memory.paging` | 基本分页存储管理 | 4 |
| `os.runtime.exception` | 异常 | 4 |
| `os.runtime.mode_switch` | 处理器状态切换 | 4 |
| `os.memory.address_translation` | 地址翻译 | 3 |
| `os.memory.tlb` | TLB 快表 | 3 |
| `os.runtime.kernel` | 操作系统内核 | 3 |
| `os.cpu.context_switch` | 上下文切换 | 2 |
| `os.cpu.scheduler` | 调度程序 | 2 |
| `os.memory.demand_paging` | 请求分页存储管理 | 2 |
| `os.memory.virtual_memory` | 虚拟内存 | 2 |
| `os.runtime.interrupt_handling` | 中断处理流程 | 2 |
| `os.runtime.kernel_mode` | 内核态 | 2 |
| `os.io.device_driver` | 设备驱动程序 | 1 |
| `os.memory.multilevel_page_table` | 多级页表 | 1 |
| `os.process.ipc` | 进程间通信 | 1 |
| `os.process.message_passing` | 消息传递通信 | 1 |
| `os.process.process` | 进程 | 1 |
| `os.runtime.interrupt` | 中断 | 1 |
| `os.runtime.trap_instruction` | 访管指令 | 1 |

## 单元使用频次（共用到 18 / 104 个已发布单元）

| unitCode | title | 被引用实验数 |
| --- | --- | --- |
| `unit.memory.paging-basics` | 分页概念与基本地址变换 | 8 |
| `unit.overview.system-calls` | 应用如何请求操作系统服务 | 8 |
| `unit.memory.image-protection` | 进程内存映像与保护共享 | 7 |
| `unit.process.control` | 进程控制原语 | 7 |
| `unit.memory.functions` | 内存管理功能与程序的链接装入 | 6 |
| `unit.overview.runtime-modes` | 程序如何在用户态与内核态运行 | 6 |
| `unit.process.composition` | 进程实体与 PCB | 6 |
| `unit.memory.demand-paging` | 请求分页的页表、缺页与地址变换 | 5 |
| `unit.overview.boot` | 计算机启动时发生什么 | 4 |
| `unit.process.states` | 进程状态与转换 | 4 |
| `unit.memory.tlb-multilevel` | 快表与多级页表 | 3 |
| `unit.overview.interrupts` | 中断与异常怎样改变执行过程 | 3 |
| `unit.process.rr-mlq-mlfq` | 轮转、多级队列与多级反馈队列 | 3 |
| `unit.memory.mmap-performance` | 内存映射文件与虚拟存储性能 | 2 |
| `unit.memory.virtual-concepts` | 虚拟内存基本概念 | 2 |
| `unit.io.driver` | 设备驱动程序接口 | 1 |
| `unit.memory.address-translation` | 虚实地址翻译综合例题 | 1 |
| `unit.process.ipc` | 进程通信的三种方式 | 1 |

---

## nodeProposals（确认缺口，**未自行新增节点**，等用户确认）

现有 199 个节点来自王道考研知识图谱，粒度偏「概念/算法/机制」，与北航 MOS 内核实验的工程实体存在以下缺口。
本轮**没有**向 `knowledge_node_registry` 插入任何节点，只用现有节点做了最贴近的映射：

| 建议 nodeCode | 建议 name | nodeType | 缺口影响的实验 | 目前落在哪个现有节点上 |
| --- | --- | --- | --- | --- |
| `os.memory.cow` | 写时复制 | MECHANISM | 4.10 duppage / 4.13 pgfault / 4.15 fork | `os.memory.sharing` + `os.memory.page_fault`（COW 是「共享 + 缺页」的组合机制，现有节点无法单独表达） |
| `os.memory.frame_free_list` | 空闲页框链表与物理页分配 | MECHANISM | 2.1 queue.h 宏 / 2.3 page_init / 2.4 page_alloc·page_free | `os.memory.paging`（该节点偏「基本分页存储管理/地址变换」，与物理页框链表管理粒度不完全对齐） |
| `os.runtime.elf` | ELF 文件格式 | CONCEPT | 1.2 readelf / 3.6 load_icode_mapper / 3.7 load_icode | `os.memory.loading_linking`（过程性 MECHANISM，缺少文件格式本身的 CONCEPT 节点） |
| `os.boot.build_system` | 内核构建与交叉编译 | MECHANISM | 1.1 Makefile/include.mk | `os.memory.loading_linking` + `os.boot.boot_process`（后者讲引导时序，不是构建） |

## tierProposals

**空。** 本轮未发现新的、够格的概念同构候选。Tier C 严格限定为 1.1、1.3 两个，未自行扩大；
5 个 Tier B（1.4 / 2.8 / 3.12 / 3.13 / 4.1）一律 `judgeMode:"LOCAL_BUILD"`、`cases.json` 为 `{"cases":[]}`，
**没有为任何一个编造算法代理题**。
