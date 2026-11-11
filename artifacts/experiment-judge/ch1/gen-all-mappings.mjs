#!/usr/bin/env node
/**
 * 为全部 41 个实验生成 nodes.json、units.json 与 mapping.md。
 *
 * 映射覆盖全部四章，共 82 个 JSON 文件。
 *
 * nodeName 一律从 registry.txt 现场读取，保证与 knowledge_node_registry.name 逐字相等。
 * unitCode 一律先对 units.txt 做存在性断言，不存在直接抛错。
 *
 * 用法：node artifacts/experiment-judge/ch1/gen-all-mappings.mjs
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ART = join(HERE, "..");
const REPO = join(ART, "..", "..");
const EXP_ROOT = join(
  REPO,
  "content",
  "operating-systems",
  "wangdao-2025",
  "experiments",
);

/* ---------- 解析 mysql -t 表格 ---------- */
function parseTable(path, keyCol) {
  const rows = new Map();
  for (const raw of readFileSync(path, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line.startsWith("|")) continue;
    const cells = line.split("|").slice(1, -1).map((s) => s.trim());
    if (cells.length < 2) continue;
    if (cells[0] === keyCol) continue;
    rows.set(cells[0], cells);
  }
  return rows;
}

const registry = parseTable(join(ART, "registry.txt"), "nodeCode");
const units = parseTable(join(ART, "units.txt"), "unitCode");
if (registry.size !== 199) throw new Error(`registry 节点数 ${registry.size} != 199`);
/* 实测：SELECT COUNT(*) FROM learning_unit WHERE courseCode='course.operating-systems'
 * AND isDelete=0 → 104（全部 reviewStatus='PUBLISHED' 且 publishedJson 非 NULL）。
 * 计划 md 写的「105 个已发布单元」比实际多 1，以实际清单为准。 */
if (units.size !== 104) throw new Error(`已发布单元数 ${units.size} != 104`);

/* ---------- 41 个真实实验（5 条垃圾重复行与 2 条软删除行不在此列） ---------- */
const CH = {
  1: "A-第一章-实验判题-001",
  2: "A-第二章-实验判题-001",
  3: "A-第三章-实验判题-001",
  4: "A-第四章-实验判题-001",
};

/** N = [nodeCode, weight, reason]；U = [unitCode, completionRule] */
const DATA = [
  /* ================= 第一章 ================= */
  {
    id: "1917131319528452098", ch: 1, no: "1.1", title: "构建 Minix 内核", tier: "C",
    target: "顶层 Makefile / include.mk（CROSS_COMPILE、LD）；判分子任务 build_order()",
    N: [
      ["os.memory.loading_linking", 1.0, "子任务判的是顶层 Makefile 里 vmlinuz ← {modules, objects} ← kernel.o ← lib.o 的合法重建顺序，即「多个目标文件链接成一个可执行内核镜像」这一链接装入过程的构建前置阶段；实验任务 3 改 include.mk 的 CROSS_COMPILE/LD 正是为链接阶段选对工具链。"],
      ["os.boot.boot_process", 0.7, "实验任务 5 要求检查 gzemul/vmlinuz 是否生成并能正确运行，该镜像就是 gxemul 引导的内核；构建顺序错误会让镜像缺段而无法引导。"],
      ["os.runtime.kernel", 0.6, "构建产物 vmlinuz 是宏内核镜像，Makefile 里 modules 与 objects 两个变量的区别决定了内核是递归 make 各模块还是一次性链接全部 .o。"],
    ],
    U: [["unit.memory.functions", "PASS"], ["unit.overview.boot", "PASS"]],
  },
  {
    id: "1930153941795131394", ch: 1, no: "1.2", title: "深入理解 ELF 文件格式", tier: "A",
    target: "readelf.c 的 readelf()；输出契约 \"%x:d:0x%x\\n\"",
    N: [
      ["os.memory.loading_linking", 1.0, "readelf() 遍历 Section Header Table 输出每个节的序号与 sh_addr，正是「链接器把多个 .o 的节合并成一个可执行文件」的结果视图；实验任务 4 要求与 readelf 工具输出逐条比对。"],
      ["os.memory.process_image", 0.6, "各节的 sh_addr 决定装入后代码段/数据段/BSS 在内存映像中的位置，读节表就是读进程内存映像的布局蓝图。"],
      ["os.memory.logical_address", 0.5, "sh_addr 是段的虚拟（逻辑）地址而非文件偏移，子任务用 e_shoff + i*e_shentsize 定位记录、再取偏移 12 处的 sh_addr，正是区分文件偏移与逻辑地址的练习。"],
    ],
    U: [["unit.memory.functions", "PASS"]],
  },
  {
    id: "1930154079598989313", ch: 1, no: "1.3", title: "使用 Linker Script 控制内核加载地址", tier: "C",
    target: "tools/scse0_3.lds；判分子任务 layout_sections()",
    N: [
      ["os.memory.loading_linking", 1.0, "layout_sections() 按 ld 的 location counter 语义逐段 ALIGN 向上对齐后顺序放置，算出 .text/.data/.bss 的 VMA 与按页对齐的 _end；这组数就是学生用 readelf -S/-s 核对的同一组加载地址。"],
      ["os.memory.process_image", 0.7, "段顺序 .text → .rodata → .data → .bss 与段间页对齐决定了内核内存映像的布局，_end 之后才是可分配的空闲物理内存。"],
      ["os.boot.boot_process", 0.6, "基址取 0x80000000 一带是 MIPS kseg0（不经 TLB 的固定映射区），内核启动早期页表未建好只能跑在这里；_end 不对齐会让 page_init 把内核自身占用的半页当空闲页分配出去。"],
    ],
    U: [["unit.memory.functions", "PASS"], ["unit.overview.boot", "PASS"]],
  },
  {
    id: "1930154331844431873", ch: 1, no: "1.4", title: "深入理解 MIPS 汇编与 C 语言的交互", tier: "B",
    target: "boot/start.S（设 $sp 后跳 main）+ fib/main 的交叉编译汇编分析",
    N: [
      ["os.boot.boot_process", 1.0, "boot/start.S 是内核引导的第一段汇编：必须先把 $sp 设到 bootstacktop、清 $fp，才能 jal main 进入 C 世界；缺栈指针时任何 C 函数调用都会踩坏内存。"],
      ["os.runtime.kernel_mode", 0.7, "启动汇编运行在 kseg0 内核态，分析对象 fib/main 经 mips-linux-gnu-gcc -S 生成的 slti/bnez 条件跳转与 jal/jr 调用返回都是内核态代码。"],
      ["os.runtime.kernel", 0.5, "实验要求观察 C 的递归与循环在汇编层的栈操作（压栈/弹栈），这是理解内核为何不能依赖宿主 libc、必须自带启动代码的前提。"],
    ],
    U: [["unit.overview.boot", "SUBMIT"], ["unit.overview.runtime-modes", "SUBMIT"]],
  },
  {
    id: "1930154460768948226", ch: 1, no: "1.5", title: "实现内核中的 printf 函数", tier: "A",
    target: "lib/print.c 的 _Printf()；输出经 drivers/gzconsole/console.c",
    N: [
      ["os.runtime.kernel", 1.0, "补全 lib/print.c 的 _Printf()：内核开发不使用 C 标准库，格式串解析、宽度/精度/填充/左对齐都必须自己实现，再经 console.c 逐字符输出。"],
      ["os.io.device_driver", 0.7, "_Printf 解析出的字符最终由 drivers/gzconsole/console.c 的控制台驱动写到串口设备，内核输出链是「格式化层 → 驱动层 → 设备」。"],
      ["os.runtime.kernel_mode", 0.5, "内核态下没有宿主 libc 可借用，子任务用 struct ArgList 模拟 va_list、用 print_char 模拟控制台 sink，正是内核态自给自足编程的体现。"],
    ],
    U: [["unit.overview.runtime-modes", "PASS"], ["unit.io.driver", "PASS"]],
  },

  /* ================= 第二章 ================= */
  {
    id: "1908449571747745793", ch: 2, no: "2.1", title: "内存控制块", tier: "A",
    target: "include/queue.h 的 LIST_INSERT_AFTER / LIST_INSERT_TAIL",
    N: [
      ["os.memory.paging", 1.0, "实现 include/queue.h 的 LIST_INSERT_AFTER/LIST_INSERT_TAIL，操作对象是 Page 结构体的 pp_link；判题观测量是遍历得到的 pp_ref 序列，即 4KB 物理页框在 page_free_list 中的挂链顺序。"],
      ["os.memory.memory_management", 0.7, "Page 的 pp_ref 记录一页被多少页表项引用，是物理内存管理的元数据；宏写成 do{...}while(0) 才能保证链表操作在 if/else 分支中语义正确。"],
      ["os.memory.page_table", 0.6, "pp_ref 的增减由 page_insert/page_remove 维护，这两个链表宏是页表项引用计数得以正确工作的基础设施。"],
    ],
    U: [["unit.memory.paging-basics", "PASS"], ["unit.memory.functions", "PASS"]],
  },
  {
    id: "1908532874240593922", ch: 2, no: "2.2", title: "初始化全局变量", tier: "A",
    target: "mm/pmap.c 的 mips_detect_memory()",
    N: [
      ["os.memory.paging", 1.0, "mips_detect_memory() 由 basemem_kb/extmem_kb 两个 CMOS 读数算出 maxpa 与 npage，npage = maxpa/BY2PG 就是 4KB 分页管理的页框总数上限。"],
      ["os.memory.memory_management", 0.7, "maxpa/basemem/extmem/npage 四个全局变量界定内核可用物理内存的大小与范围，此后 page_init/page_alloc 都不得越过 npage。"],
      ["os.memory.logical_address", 0.5, "basemem 与 extmem 分别对应 1MB 以下与 1MB 以上的物理内存范围，子任务输出四个十进制数正是物理地址空间划分的直接量化。"],
    ],
    U: [["unit.memory.functions", "PASS"], ["unit.memory.paging-basics", "PASS"]],
  },
  {
    id: "1908538371526131713", ch: 2, no: "2.3", title: "空闲内存管理", tier: "A",
    target: "mm/pmap.c 的 page_init()",
    N: [
      ["os.memory.paging", 1.0, "page_init() 把内核占用之外、npage 之内的物理页用 queue.h 宏头插进 page_free_list；判题输出空闲链表页号序列（真实源码头插故为递减序）与 used/free/other 计数。"],
      ["os.memory.memory_management", 0.7, "区分已分配（内核占用）与未分配页、并统计各自数量，是物理内存管理的基本职责；pp_ref 非 0 的页不得进空闲链表。"],
      ["os.memory.logical_address", 0.5, "page_init 以物理页号为单位遍历 [0, npage)，处理的是物理地址 i*BY2PG 而非虚拟地址，体现物理地址空间与逻辑地址空间的分离。"],
    ],
    U: [["unit.memory.paging-basics", "PASS"]],
  },
  {
    id: "1908539177407119361", ch: 2, no: "2.4", title: "内存分配与释放管理", tier: "A",
    target: "mm/pmap.c 的 page_alloc() / page_free()",
    N: [
      ["os.memory.paging", 1.0, "page_alloc() 从 page_free_list 摘一页并清零，page_free() 把 pp_ref 归零的页重新挂回；判题观测量是分配返回的页号、首字是否为 0（驱动先写 0xDEADBEEF）与末态空闲链表序列。"],
      ["os.memory.memory_management", 0.7, "分配与释放必须成对且维护 pp_ref，否则同一物理页会被两个页表项同时占用；实验还要求在 mips_init 中注释掉 page_check 以便逐步验证。"],
      ["os.memory.sharing", 0.6, "page_free 只有在 pp_ref 归零时才能真正回收物理页，这是共享页安全回收的前提；子任务的 S 操作显式设置 pp_ref 来检验这一点。"],
    ],
    U: [["unit.memory.paging-basics", "PASS"]],
  },
  {
    id: "1908539875976839170", ch: 2, no: "2.5", title: "页表创建", tier: "A",
    target: "mm/pmap.c 的 boot_pgdir_walk() / pgdir_walk()",
    N: [
      ["os.memory.page_table", 1.0, "boot_pgdir_walk()/pgdir_walk() 用 PDX(va) 取页目录项、PTX(va) 取页表项，create 为真时还要 page_alloc 一页作二级页表并令其 pp_ref++。"],
      ["os.memory.multilevel_page_table", 0.8, "PDX/PTX 两级索引就是 MIPS 两级页表的具体实现；判题输出「页表物理地址 PTX」正是二级索引的两个分量。"],
      ["os.memory.address_translation", 0.7, "walk 的结果是 va→pa 翻译链上的中间节点；boot 期与运行期两条路径的区别在于能否为新页表分配物理页。"],
    ],
    U: [["unit.memory.paging-basics", "PASS"], ["unit.memory.tlb-multilevel", "PASS"]],
  },
  {
    id: "1908540360305704962", ch: 2, no: "2.6", title: "地址映射", tier: "A",
    target: "mm/pmap.c 的 boot_map_segment()",
    N: [
      ["os.memory.address_translation", 1.0, "boot_map_segment(pgdir, va, size, pa, perm) 逐页调用 boot_pgdir_walk 并写入 pa|perm|PTE_V；判题观测量是区间首页与末页的页表项原值。"],
      ["os.memory.page_table", 0.8, "映射的落地形式就是页表项：所属页表不存在时槽位读作 4294967295(~0)，页表已存在但未映射读作 0，已映射读作 pa|perm|PTE_V。"],
      ["os.memory.logical_address", 0.6, "内核启动期把 kseg0 虚拟地址映射到对应物理地址（va − 0x80000000），是逻辑地址与物理地址关系的直接构造。"],
    ],
    U: [["unit.memory.paging-basics", "PASS"], ["unit.memory.address-translation", "PASS"]],
  },
  {
    id: "1908540572415852545", ch: 2, no: "2.7", title: "页表项插入与移除", tier: "A",
    target: "mm/pmap.c 的 page_insert() / page_remove()",
    N: [
      ["os.memory.page_table", 1.0, "page_insert()/page_remove() 维护页表项与 pp_ref：插入前若该 va 已映射旧页必须先 page_remove，移除后 pp_ref--；判题输出返回码、物理地址与 pp_ref。"],
      ["os.memory.tlb", 0.8, "content 明确要求在 page_insert 中调用 tlb_invalidate 更新 TLB，否则 CPU 仍用旧快表项访问已被换出的物理页。"],
      ["os.memory.sharing", 0.6, "判题的 P 操作直接查询 pp_ref：同一物理页被多个 va 或多个页目录映射时 pp_ref>1，page_remove 只能减引用而不能直接回收。"],
    ],
    U: [["unit.memory.paging-basics", "PASS"], ["unit.memory.tlb-multilevel", "PASS"]],
  },
  {
    id: "1908540698916061185", ch: 2, no: "2.8", title: "TLB 管理", tier: "B",
    target: "mm/tlb_asm.S 的 tlb_out（两行空白填 tlbp / tlbwi）",
    N: [
      ["os.memory.tlb", 1.0, "在 mm/tlb_asm.S 的 tlb_out 空出的两行填 tlbp/tlbwi：tlbp 按 EntryHi 的 VPN 查 TLB 并写 Index，未命中时 Index 最高位置 1 后跳 NOFOUND；tlbwi 把 EntryLo0/1 写入 Index 指定项。"],
      ["os.memory.address_translation", 0.7, "TLB 是 va→pa 翻译的硬件缓存，tlb_out 处理的正是翻译失败（TLB Miss / TLB Invalid）路径。"],
      ["os.runtime.exception", 0.6, "TLB Miss 由 CP0 触发异常进入 0x80000000 的 TLB Refill 入口，tlb_out 是该异常处理链上的一环；tlbp 之后 4 条 nop 是 CP0 冒险间隔而非分支延迟槽。"],
    ],
    U: [["unit.memory.tlb-multilevel", "SUBMIT"]],
  },

  /* ================= 第三章 ================= */
  {
    id: "1908900051330306049", ch: 3, no: "3.1", title: "初始化进程控制块数组", tier: "A",
    target: "mm/pmap.c 的 mips_vm_init()（为 envs[NENV] 分配并映射到 UENVS）",
    N: [
      ["os.process.pcb", 1.0, "mips_vm_init 为 envs[NENV] 分配物理内存并映射到 UENVS；Env 的 env_tf/env_link/env_id/env_parent_id/env_status/env_pgdir/env_cr3/env_sched_link/env_pri 就是 PCB 的全部字段。"],
      ["os.memory.process_image", 0.6, "envs 数组被映射到用户可见的 UENVS 区域，是内核为进程管理预留的内存映像的一部分，分配方式参照已写好的 pages 数组。"],
      ["os.process.process_state", 0.6, "content 明确列出 ENV_FREE/ENV_NOT_RUNNABLE/ENV_RUNNABLE 三种状态，envs 分配完成后全部 PCB 处于 ENV_FREE。"],
    ],
    U: [["unit.process.composition", "PASS"], ["unit.memory.image-protection", "PASS"]],
  },
  {
    id: "1930154686162456577", ch: 3, no: "3.2", title: "管理进程控制块数组", tier: "A",
    target: "lib/env.c 的 env_init()",
    N: [
      ["os.process.pcb", 1.0, "lib/env.c 的 env_init() 把 NENV 个 Env 全部置 ENV_FREE 并挂进 env_free_list；判的是空闲 PCB 链表的插入顺序（content 特别提醒「注意插入的顺序」）。"],
      ["os.process.process_control", 0.7, "env_free_list 是进程创建原语 env_alloc 取 PCB 的唯一来源，初始化顺序直接决定后续 envid 的分配次序。"],
      ["os.process.process_state", 0.6, "初始化时所有 PCB 的 env_status 必须置 ENV_FREE，否则 env_alloc 会把正在运行的进程当空闲 PCB 分配出去。"],
    ],
    U: [["unit.process.composition", "PASS"], ["unit.process.control", "PASS"]],
  },
  {
    id: "1930154848414912513", ch: 3, no: "3.3", title: "进程标识", tier: "A",
    target: "lib/env.c 的 envid2env()（配合 mkenvid()）",
    N: [
      ["os.process.pcb", 1.0, "env.c 的 envid2env() 由 env_id 反查 Env*：mkenvid 把数组下标与代际计数编码进 envid，envid2env 必须校验下标范围与代际匹配后才能返回 PCB 指针。"],
      ["os.process.process", 0.7, "env_id 是进程存在的唯一标识，content 要求理解「如何通过 env_id 找到对应 PCB」，即进程与 PCB 一一对应关系。"],
      ["os.process.process_control", 0.5, "系统调用层（sys_ipc_can_send、sys_env_alloc）都先用 envid2env 把用户传来的 envid 转成内核 PCB 指针并校验合法性，是进程控制原语的入口守卫。"],
    ],
    U: [["unit.process.composition", "PASS"], ["unit.process.control", "PASS"]],
  },
  {
    id: "1930154980048949249", ch: 3, no: "3.4", title: "初始化进程虚拟内存布局", tier: "A",
    target: "lib/env.c 的 env_setup_vm()",
    N: [
      ["os.memory.process_image", 1.0, "env_setup_vm() 为新进程 page_alloc 一页作页目录，把 boot_pgdir 中内核部分的映射复制过来，并设置 env_pgdir 与 env_cr3，形成该进程的虚拟内存布局。"],
      ["os.memory.page_table", 0.8, "复制的是页目录项（PDE）：每个进程有独立页目录但共享内核部分的二级页表，被共享页表的 pp_ref 需相应增加。"],
      ["os.memory.virtual_memory", 0.6, "用户空间各自独立、内核空间所有进程共享，这就是虚拟内存给每个进程的地址空间抽象；content 还要求理解 CR3 存放页目录基地址的作用。"],
    ],
    U: [["unit.memory.image-protection", "PASS"], ["unit.memory.paging-basics", "PASS"]],
  },
  {
    id: "1930155119123681282", ch: 3, no: "3.5", title: "设置进程控制块", tier: "A",
    target: "lib/env.c 的 env_alloc()",
    N: [
      ["os.process.pcb", 1.0, "env_alloc() 从 env_free_list 摘一个 PCB，初始化 env_id/env_status/env_parent_id/env_pri，设置 env_tf 的 $sp 与 cp0_status，并调用 env_setup_vm。"],
      ["os.runtime.mode_switch", 0.8, "content 明确要求设置 cpo_status 的 KUc/IEc/KUp 位，使进程首次被 env_pop_tf 恢复时进入用户态而不是留在内核态。"],
      ["os.process.process_control", 0.7, "env_alloc 是进程创建原语的核心一步，与 env_setup_vm/load_icode 串成完整创建流程。"],
    ],
    U: [["unit.process.composition", "PASS"], ["unit.overview.runtime-modes", "PASS"]],
  },
  {
    id: "1930155245821022209", ch: 3, no: "3.6", title: "加载镜像映射", tier: "A",
    target: "lib/env.c 的 load_icode_mapper()（配合 load_elf()）",
    N: [
      ["os.memory.process_image", 1.0, "load_icode_mapper() 把 ELF 每个 Program Header 描述的段按 p_vaddr 逐页映射进进程地址空间，p_filesz 部分拷内容、p_memsz 多出的部分清零。"],
      ["os.memory.loading_linking", 0.8, "load_elf 解析 ELF 程序头并把 (va, size, 文件内容) 交给 mapper，正是「装入」阶段把可执行文件搬进内存的过程。"],
      ["os.memory.page_table", 0.6, "每映射一页都要 page_alloc + page_insert 并增加 pp_ref；段边界不对齐时同一物理页可能被两个段共用。"],
    ],
    U: [["unit.memory.image-protection", "PASS"], ["unit.memory.functions", "PASS"]],
  },
  {
    id: "1930155360556208129", ch: 3, no: "3.7", title: "完整加载镜像", tier: "A",
    target: "lib/env.c 的 load_icode()",
    N: [
      ["os.memory.process_image", 1.0, "load_icode() 调 load_elf/load_icode_mapper 装载全部段，再为用户栈分配物理页并设置初始 $sp 与入口点，形成完整可运行的进程内存映像。"],
      ["os.memory.loading_linking", 0.7, "ELF 的 e_entry 被写入 PCB 的 PC，是链接阶段确定的入口地址在装入阶段被真正使用的地方。"],
      ["os.process.pcb", 0.6, "初始栈指针与入口点都写进 Env 的 env_tf（Trapframe），进程首次被调度时由 env_pop_tf 恢复到寄存器。"],
    ],
    U: [["unit.memory.image-protection", "PASS"], ["unit.process.composition", "PASS"]],
  },
  {
    id: "1930155484338507777", ch: 3, no: "3.8", title: "创建进程", tier: "A",
    target: "lib/env.c 的 env_create() / env_create_priority()",
    N: [
      ["os.process.process_control", 1.0, "env_create() = env_alloc() + load_icode()，env_create_priority() 额外设置 env_pri；这是进程创建原语的完整实现。"],
      ["os.process.pcb", 0.8, "创建过程就是分配并初始化一个 Env：状态、寄存器集合、页目录、优先级全部落到 PCB 字段上。"],
      ["os.cpu.rr", 0.5, "env_create_priority 设置的 env_pri 在 MOS 里被 sched_yield 用作时间片长度，是时间片轮转调度的参数来源。"],
    ],
    U: [["unit.process.control", "PASS"], ["unit.process.composition", "PASS"]],
  },
  {
    id: "1930155681017810946", ch: 3, no: "3.9", title: "进程创建宏命令应用", tier: "A",
    target: "init/init.c 中的 ENV_CREATE_PRIORITY(user_A,2) / ENV_CREATE_PRIORITY(user_B,1)",
    N: [
      ["os.process.process_control", 1.0, "在 init/init.c 里加 ENV_CREATE_PRIORITY(user_A,2) 与 ENV_CREATE_PRIORITY(user_B,1)，宏展开后调用 env_create_priority 完成两次进程创建。"],
      ["os.boot.boot_process", 0.7, "init.c 是内核引导完成后创建第一批用户进程的位置，宏必须插在内存/进程子系统初始化之后、调度器启动之前。"],
      ["os.cpu.rr", 0.5, "两个进程优先级 2 与 1 不同，用于观察时钟中断驱动的时间片轮转下两者的交替输出比例。"],
    ],
    U: [["unit.process.control", "PASS"], ["unit.overview.boot", "PASS"]],
  },
  {
    id: "1930155792691154945", ch: 3, no: "3.10", title: "进程运行与切换", tier: "A",
    target: "lib/env.c 的 env_run()（配合 lcontext / env_pop_tf / TIMESTACK）",
    N: [
      ["os.cpu.context_switch", 1.0, "env_run() 保存当前进程现场到 TIMESTACK 上的 Trapframe、设 curenv、用 lcontext 切换页目录、再 env_pop_tf 恢复寄存器进入用户态，是上下文切换的完整实现。"],
      ["os.process.process_state", 0.7, "切换前后进程在就绪态与运行态之间转换，curenv 指向当前占用 CPU 的 PCB。"],
      ["os.runtime.mode_switch", 0.6, "env_pop_tf 通过恢复 CP0 Status 并 eret 从内核态回到用户态，是处理器状态切换的具体落地。"],
    ],
    U: [["unit.process.states", "PASS"], ["unit.overview.runtime-modes", "PASS"]],
  },
  {
    id: "1930155905954140162", ch: 3, no: "3.12", title: "异常处理", tier: "B",
    target: "lds 中 0x80000080 异常入口 + 入口处的异常分发汇编",
    N: [
      ["os.runtime.exception", 1.0, "在链接器脚本中把异常处理入口固定到 0x80000080，并在该处编写分发代码读 CP0 Cause 的 ExcCode（bit6..2）后跳转到相应处理程序。"],
      ["os.runtime.interrupt_handling", 0.7, "异常与中断共用 0x80000080 通用入口，分发代码必须同时按 ExcCode 与 Cause.IP（bit15..8）分派，是中断处理流程的入口环节。"],
      ["os.memory.loading_linking", 0.5, "入口地址由链接器脚本定位（专用 .exc.handler 段），是链接阶段决定代码物理位置的直接应用。"],
    ],
    U: [["unit.overview.interrupts", "SUBMIT"]],
  },
  {
    id: "1930156027966443521", ch: 3, no: "3.13", title: "时钟中断", tier: "B",
    target: "kclock_init() / timer_irq() / lds 中断入口 / sched_yield()",
    N: [
      ["os.runtime.interrupt", 1.0, "kclock_init 设置定时器频率并把时钟中断绑到 4 号中断线（CP0 Status.IM4 / Cause.IP4），timer_irq 在每次时钟中断发生时被调用。"],
      ["os.cpu.rr", 0.8, "content 明确「时钟中断与时间片轮转算法的关系」：timer_irq 调 sched_yield，时间片由周期性时钟中断触发，没有时钟中断就没有轮转。"],
      ["os.runtime.interrupt_handling", 0.6, "lds 把中断入口设到 0x80000080，分发代码识别 IP4 后跳 timer_irq，是中断处理流程在 MIPS 上的硬件落地。"],
    ],
    U: [["unit.overview.interrupts", "SUBMIT"], ["unit.process.rr-mlq-mlfq", "SUBMIT"]],
  },
  {
    id: "1930156143783759874", ch: 3, no: "3.14", title: "进程调度实验", tier: "A",
    target: "lib/sched.c 的 sched_yield() + env.c 的就绪链表改造",
    N: [
      ["os.cpu.rr", 1.0, "补全 sched_yield()：用两个链表管理 ENV_RUNNABLE 进程，按 env_pri 决定时间片，时钟中断到来时把当前进程挂回就绪链表并切换到下一个。"],
      ["os.cpu.scheduler", 0.8, "sched_yield 就是调度程序本体，负责从就绪队列挑下一个进程并交给 env_run 执行。"],
      ["os.process.process_state", 0.6, "调度只涉及就绪态与运行态之间的转换，ENV_NOT_RUNNABLE（阻塞）进程不得进就绪链表。"],
    ],
    U: [["unit.process.rr-mlq-mlfq", "PASS"], ["unit.process.states", "PASS"]],
  },

  /* ================= 第四章 ================= */
  {
    id: "1930156360117571585", ch: 4, no: "4.1", title: "实现用户空间到内核空间的系统调用机制", tier: "B",
    target: "user/syscall_wrap.S 的 syscall 封装 + 内核侧 mysyscall",
    N: [
      ["os.runtime.system_call", 1.0, "编写 user/syscall_wrap.S 的 syscall 封装：按 MIPS o32 把调用号与参数搬进 $v0/$a0-$a3（第 5 个及以后参数走栈），执行特权 syscall 陷入内核，再从 $v0 取返回值。"],
      ["os.runtime.trap_instruction", 0.8, "MIPS 的 syscall 就是访管指令，触发 ExcCode=8(Sys) 异常，是用户态主动请求内核服务的唯一入口。"],
      ["os.runtime.mode_switch", 0.7, "syscall 触发后 CPU 置 Status.EXL 进入内核态、切到内核栈与内核页表，返回时 eret 回用户态；内核侧须把返回值写回 Trapframe 的 $v0 并令 EPC += 4。"],
    ],
    U: [["unit.overview.system-calls", "SUBMIT"], ["unit.overview.runtime-modes", "SUBMIT"]],
  },
  {
    id: "1930156493454495745", ch: 4, no: "4.2", title: "实现内核的系统调用处理机制", tier: "A",
    target: "handle_sys() + sys_call_table[]",
    N: [
      ["os.runtime.system_call", 1.0, "handle_sys() 从 Trapframe 取 $v0 里的系统调用号，查 sys_call_table 分派到对应内核函数，并把返回值写回 Trapframe 的 $v0。"],
      ["os.runtime.mode_switch", 0.7, "handle_sys 运行在内核态，参数来自用户态保存在 Trapframe 里的寄存器，必须校验用户指针合法性后再解引用。"],
      ["os.runtime.exception", 0.6, "系统调用在 MIPS 上按异常处理（ExcCode=8 Sys），handle_sys 是异常分发链上 Sys 分支的处理程序。"],
    ],
    U: [["unit.overview.system-calls", "PASS"], ["unit.overview.runtime-modes", "PASS"]],
  },
  {
    id: "1930156605618573314", ch: 4, no: "4.3", title: "基础系统调用函数 sys_mem_alloc", tier: "A",
    target: "lib/syscall_all.c 的 sys_mem_alloc(sysno, envid, va, perm)",
    N: [
      ["os.runtime.system_call", 1.0, "sys_mem_alloc(sysno, envid, va, perm) 是暴露给用户态的内存分配系统调用，内部用 envid2env 取目标 PCB 后调 pmap.c 的 page_alloc + page_insert。"],
      ["os.memory.virtual_memory", 0.7, "为指定进程的指定虚拟地址按需分配一页物理内存并建立映射，是虚拟内存按需调页的内核侧实现，也是 IPC 与 fork 的基础。"],
      ["os.memory.page_table", 0.6, "实际落地是 pmap.c 的 page_insert，perm 决定页表项的权限位，返回码必须遵循 MIPS 调用规范传回用户态。"],
    ],
    U: [["unit.overview.system-calls", "PASS"], ["unit.memory.image-protection", "PASS"]],
  },
  {
    id: "1930156740377366529", ch: 4, no: "4.4", title: "基础系统调用函数 sys_mem_map", tier: "A",
    target: "lib/syscall_all.c 的 sys_mem_map(sysno, srcid, srcva, dstid, dstva, perm)",
    N: [
      ["os.memory.sharing", 1.0, "sys_mem_map 把源进程 srcva 处的物理页映射到目标进程 dstva，两进程从此共享同一物理页、pp_ref 增 1；content 明确要求「确保内存映射后两个进程能够共享同一物理内存页」。"],
      ["os.runtime.system_call", 0.8, "它是 IPC 传页的基础系统调用，参数含两个 envid，内核必须先用 envid2env 校验两个进程都存在再操作页表。"],
      ["os.memory.page_table", 0.6, "实现上先 pgdir_walk 取源页表项拿到物理页，再 page_insert 到目标页目录，perm 决定目标侧权限。"],
    ],
    U: [["unit.memory.image-protection", "PASS"], ["unit.memory.mmap-performance", "PASS"]],
  },
  {
    id: "1930156844614209538", ch: 4, no: "4.5", title: "基础系统调用函数 sys_mem_unmap", tier: "A",
    target: "lib/syscall_all.c 的 sys_mem_unmap(sysno, envid, va)",
    N: [
      ["os.memory.sharing", 1.0, "sys_mem_unmap(envid, va) 解除该进程 va 的映射，page_remove 令 pp_ref--；只有 pp_ref 归零物理页才真正回收，共享页不能因一方解映射而失效。"],
      ["os.runtime.system_call", 0.8, "用户态通过它归还 IPC 收到的页，是 sys_mem_map 的逆操作系统调用；content 要求「解除映射后进程不再能访问之前映射的物理内存页」。"],
      ["os.memory.tlb", 0.5, "解除映射后必须 tlb_invalidate，否则快表里仍留着旧翻译，进程还能访问已归还的物理页。"],
    ],
    U: [["unit.memory.image-protection", "PASS"], ["unit.memory.mmap-performance", "PASS"]],
  },
  {
    id: "1930156972624367618", ch: 4, no: "4.6", title: "基础系统调用函数 sys_yield", tier: "A",
    target: "lib/syscall_all.c 的 sys_yield()（KERNEL_SP / TIMESTACK 现场保存）",
    N: [
      ["os.cpu.rr", 1.0, "sys_yield() 让用户进程主动放弃 CPU，保存现场后调 sched_yield 切换到下一个就绪进程，是时间片轮转的自愿让出路径（与时钟中断的强制让出互补）。"],
      ["os.cpu.context_switch", 0.8, "content 要求实现现场保存（KERNEL_SP 与 TIMESTACK）与切换后正确恢复执行状态，正是上下文切换的两半。"],
      ["os.runtime.system_call", 0.6, "用户态不能直接调内核函数 sched_yield，必须经 syscall 陷入内核由 sys_yield 代劳。"],
    ],
    U: [["unit.process.rr-mlq-mlfq", "PASS"], ["unit.overview.system-calls", "PASS"]],
  },
  {
    id: "1930157095085461505", ch: 4, no: "4.7", title: "实现进程间通信（IPC）机制", tier: "A",
    target: "lib/syscall_all.c 的 sys_ipc_recv() / sys_ipc_can_send()",
    N: [
      ["os.process.ipc", 1.0, "sys_ipc_recv 设 env_ipc_receiving 标志并阻塞当前进程，sys_ipc_can_send 检查目标是否处于可接收状态后投递消息并更新其状态，构成 MOS 的进程间通信机制。"],
      ["os.process.message_passing", 0.8, "IPC 传的是 (envid, value, dstva, perm) 消息，属消息传递通信；带页时再用 sys_mem_map/sys_mem_unmap 把物理页映射过去。"],
      ["os.process.process_state", 0.6, "接收方在 ENV_NOT_RUNNABLE 上阻塞、发送方把它置回 ENV_RUNNABLE，是进程状态转换的直接应用。"],
    ],
    U: [["unit.process.ipc", "PASS"], ["unit.process.states", "PASS"]],
  },
  {
    id: "1930157212584693762", ch: 4, no: "4.8", title: "进程创建系统调用 sys_env_alloc", tier: "A",
    target: "lib/syscall_all.c 的 sys_env_alloc()",
    N: [
      ["os.process.process_control", 1.0, "sys_env_alloc 在内核里调 env_alloc 创建子进程环境、复制父进程 Trapframe，并把子进程置为不会立即被调度的状态（ENV_NOT_RUNNABLE）。"],
      ["os.runtime.system_call", 0.8, "fork 的用户态实现必须经这个系统调用才能创建 PCB，用户态无权直接操作 envs 数组；返回值决定 fork 在父子两侧的不同返回。"],
      ["os.process.pcb", 0.6, "新进程的 env_id/env_parent_id/env_tf/env_pgdir 都在此初始化，env_parent_id 记录父进程 envid。"],
    ],
    U: [["unit.process.control", "PASS"], ["unit.overview.system-calls", "PASS"]],
  },
  {
    id: "1930157404163723265", ch: 4, no: "4.9", title: "实现 fork 系统调用中的 sys_env_alloc", tier: "A",
    target: "user/fork.c 的 fork() 中调用 sys_env_alloc 与子进程返回逻辑",
    N: [
      ["os.process.process_control", 1.0, "user/fork.c 的 fork() 调 sys_env_alloc 拿子进程 envid，按返回值区分父子：父返回子 envid，子返回 0 并从 fork 中返回开始执行。"],
      ["os.process.pcb", 0.7, "子进程必须把用户态的 env 指针指向自身的 Env（经 UENVS 与 envid 下标），否则后续 syscall 会操作父进程的控制块。"],
      ["os.runtime.system_call", 0.6, "fork 不是单条系统调用，而是 sys_env_alloc + duppage + sys_set_pgfault_handler + sys_set_env_status 的系统调用序列。"],
    ],
    U: [["unit.process.control", "PASS"], ["unit.overview.system-calls", "PASS"]],
  },
  {
    id: "1930157516164222977", ch: 4, no: "4.10", title: "实现 fork 系统调用中的 duppage 函数", tier: "A",
    target: "user/fork.c 的 duppage()（PTE_COW / PTE_LIBRARY 分类处理）",
    N: [
      ["os.memory.sharing", 1.0, "duppage(envid, pn) 按权限分类处理父进程的页：只读页直接映射给子进程、PTE_LIBRARY 共享页保持共享、可写页置 PTE_COW 后父子都只读映射。"],
      ["os.memory.page_fault", 0.8, "PTE_COW 页被写时触发缺页，由 pgfault 真正复制物理页；duppage 只是把复制推迟到写发生的那一刻。"],
      ["os.memory.page_table", 0.6, "实现上读父进程页表项的 PTE_V/PTE_COW/PTE_LIBRARY 标志位，再用 sys_mem_map 建立子进程映射。"],
    ],
    U: [["unit.memory.virtual-concepts", "PASS"], ["unit.memory.demand-paging", "PASS"]],
  },
  {
    id: "1930157629511094274", ch: 4, no: "4.11", title: "实现缺页中断处理函数 page_fault_handler", tier: "A",
    target: "lib/traps.c 的 page_fault_handler()",
    N: [
      ["os.memory.page_fault", 1.0, "lib/traps.c 的 page_fault_handler 取引发缺页的虚拟地址、校验合法性、为缺失页分配物理内存、更新页表项建立映射，并处理内存不足等异常后恢复被中断进程。"],
      ["os.memory.demand_paging", 0.8, "缺页中断是请求分页的核心机制：页不在内存时由中断驱动调入，而不是预先全部装入。"],
      ["os.runtime.exception", 0.6, "缺页在 MIPS 上是 TLB Miss/Mod 异常，处理程序运行在异常处理栈（Exception Stack Frame）上，content 明确要求理解异常现场保存。"],
    ],
    U: [["unit.memory.demand-paging", "PASS"], ["unit.overview.interrupts", "PASS"]],
  },
  {
    id: "1930157740261691394", ch: 4, no: "4.12", title: "实现设置缺页处理函数的系统调用 sys_set_pgfault_handler", tier: "A",
    target: "lib/syscall_all.c 的 sys_set_pgfault_handler()",
    N: [
      ["os.memory.page_fault", 1.0, "sys_set_pgfault_handler 校验用户传入的处理函数指针，用 syscall_mem_alloc 为异常处理栈分配内存，并把 handler 写进 Env 的 env_pgfault_handler。"],
      ["os.runtime.system_call", 0.8, "用户态无法直接改内核里的 PCB 字段，必须经该系统调用登记自己的缺页处理函数，且内核须按 MIPS 调用规范正确处理错误情况。"],
      ["os.process.pcb", 0.6, "登记结果落在 struct Env 的 env_pgfault_handler 字段，缺页发生时内核据此把控制流转交给用户态处理函数。"],
    ],
    U: [["unit.memory.demand-paging", "PASS"], ["unit.overview.system-calls", "PASS"]],
  },
  {
    id: "1930157867273605121", ch: 4, no: "4.13", title: "实现 fork 系统调用中的缺页处理函数 pgfault", tier: "A",
    target: "user/fork.c 的 pgfault()",
    N: [
      ["os.memory.page_fault", 1.0, "user/fork.c 的 pgfault 判断出错页是否为写时复制页，若是则分配新物理页、复制原页内容、更新页表项映射到新页，最后恢复被中断进程执行。"],
      ["os.memory.sharing", 0.8, "COW 的本质是父子进程先共享同一物理页（pp_ref>1），写的一方负责复制并解除共享。"],
      ["os.memory.demand_paging", 0.6, "复制被推迟到第一次写触发缺页时才做，是按需调页思想在 fork 上的应用。"],
    ],
    U: [["unit.memory.virtual-concepts", "PASS"], ["unit.memory.demand-paging", "PASS"]],
  },
  {
    id: "1930157984642813953", ch: 4, no: "4.14", title: "实现进程状态设置系统调用 sys_set_env_status", tier: "A",
    target: "lib/syscall_all.c 的 sys_set_env_status()",
    N: [
      ["os.process.process_state", 1.0, "sys_set_env_status 检查传入状态是否有效后更新 Env 的 env_status，若为 RUNNABLE 则把进程加入调度链表。"],
      ["os.cpu.scheduler", 0.7, "fork 的最后一步用该系统调用把子进程置为 RUNNABLE，子进程从此进入调度器的就绪队列。"],
      ["os.runtime.system_call", 0.6, "进程状态是内核数据，用户态只能经系统调用请求修改，且内核必须拒绝非法状态值。"],
    ],
    U: [["unit.process.states", "PASS"], ["unit.overview.system-calls", "PASS"]],
  },
  {
    id: "1930158086405017602", ch: 4, no: "4.15", title: "实现 fork 系统调用", tier: "A",
    target: "user/fork.c 的 fork() 父进程部分",
    N: [
      ["os.process.process_control", 1.0, "fork 父进程部分：遍历地址空间逐页 duppage、为子进程分配异常处理栈、设置子进程处理函数、设置子进程运行状态、返回子进程 envid，是进程创建原语在用户态的完整组装。"],
      ["os.memory.sharing", 0.8, "fork 后父子共享全部可写页的 COW 映射，地址空间遍历的每一步都在建立共享关系。"],
      ["os.process.pcb", 0.6, "子进程 PCB 由 sys_env_alloc 创建，fork 负责把父进程的内存映像与异常处理配置复制进去。"],
    ],
    U: [["unit.process.control", "PASS"], ["unit.memory.demand-paging", "PASS"]],
  },
];

/* ---------- 自检 ---------- */
const EXPECTED_IDS = new Set(DATA.map((d) => d.id));
if (DATA.length !== 41) throw new Error(`DATA 条数 ${DATA.length} != 41`);
if (EXPECTED_IDS.size !== 41) throw new Error("DATA 中存在重复 id");

const FORBIDDEN = new Set([
  "1918931948987031554", "1918932928252157953", "1918933052214812674",
  "1918934540198682625", "1918938841746878466", // 5 条垃圾重复行
  "1918945654303125506", "1920028942673477633", // 2 条软删除行
]);
for (const id of FORBIDDEN) {
  if (EXPECTED_IDS.has(id)) throw new Error(`垃圾/软删除 id 混入：${id}`);
}

let written = 0;
const tableRows = [];
for (const d of DATA) {
  const dir = join(EXP_ROOT, CH[d.ch], "experiment-files", d.id);
  if (!existsSync(dir)) throw new Error(`实验目录不存在：${dir}`);

  const primaries = d.N.filter((n, i) => i === 0);
  if (primaries.length !== 1) throw new Error(`${d.id} PRIMARY 数 != 1`);
  const related = d.N.slice(1);
  if (related.length < 1 || related.length > 3) {
    throw new Error(`${d.id} RELATED 数 ${related.length} 不在 1..3`);
  }

  const nodeMappings = d.N.map(([code, weight, reason], i) => {
    const row = registry.get(code);
    if (!row) throw new Error(`${d.id} nodeCode 不在 registry：${code}`);
    if (!reason || reason.length < 20) throw new Error(`${d.id} ${code} reason 过短`);
    if (/按主题映射|主题相关|略/.test(reason)) throw new Error(`${d.id} ${code} reason 含空话`);
    return {
      nodeCode: code,
      nodeName: row[1],
      role: i === 0 ? "PRIMARY" : "RELATED",
      weight,
      reason,
    };
  });
  const seenN = new Set();
  for (const m of nodeMappings) {
    if (seenN.has(m.nodeCode)) throw new Error(`${d.id} 重复 nodeCode ${m.nodeCode}`);
    seenN.add(m.nodeCode);
  }

  const unitMappings = d.U.map(([code, rule]) => {
    if (!units.has(code)) throw new Error(`${d.id} unitCode 不在已发布清单：${code}`);
    const expect = d.tier === "B" ? "SUBMIT" : "PASS";
    if (rule !== expect) throw new Error(`${d.id} Tier ${d.tier} 应为 ${expect}，实为 ${rule}`);
    return { unitCode: code, required: false, completionRule: rule };
  });
  const seenU = new Set();
  for (const m of unitMappings) {
    if (seenU.has(m.unitCode)) throw new Error(`${d.id} 重复 unitCode ${m.unitCode}`);
    seenU.add(m.unitCode);
  }

  mkdirSync(dir, { recursive: true });
  writeFileSync(
    join(dir, "nodes.json"),
    JSON.stringify({ questionId: d.id, nodeMappings }, null, 2) + "\n",
    "utf8",
  );
  writeFileSync(
    join(dir, "units.json"),
    JSON.stringify({ questionId: d.id, unitMappings }, null, 2) + "\n",
    "utf8",
  );
  written += 2;

  const p = nodeMappings[0];
  const rel = nodeMappings.slice(1).map((m) => `\`${m.nodeCode}\`(${m.weight})`).join("<br>");
  const us = unitMappings.map((m) => `\`${m.unitCode}\`<br>${m.completionRule}`).join("<br>");
  tableRows.push(
    `| ${d.no} | \`${d.id}\` | ${d.title} | ${d.tier} | \`${p.nodeCode}\`<br>${p.nodeName} | ${rel} | ${us} | ${d.target} |`,
  );
}

/* ---------- mapping.md ---------- */
const tierCount = { A: 0, B: 0, C: 0 };
for (const d of DATA) tierCount[d.tier]++;
const nodeUse = new Map();
for (const d of DATA) for (const [c] of d.N) nodeUse.set(c, (nodeUse.get(c) || 0) + 1);
const unitUse = new Map();
for (const d of DATA) for (const [c] of d.U) unitUse.set(c, (unitUse.get(c) || 0) + 1);

const md = `# 41 个北航编程实验 —— 图谱节点映射与学习单元映射总表

生成方式：\`node artifacts/experiment-judge/ch1/gen-all-mappings.mjs\`（一次性生成器，Agent B 独占目录）。
数据来源：\`artifacts/experiment-judge/registry.txt\`（199 个 \`knowledge_node_registry\` 节点，只读 SELECT 导出）、
\`artifacts/experiment-judge/units.txt\`（**104** 个已发布 \`learning_unit\`，只读 SELECT 导出；计划 md 写的 105 比实际多 1，以实际清单为准）、
\`artifacts/experiment-judge/current-content.tsv\`（46 行 \`question.content\` 原文，用于确定每个实验的真实目标函数）。

**范围**：41 个真实实验 = 第一章 5 + 第二章 8 + 第三章 13（无 3.11）+ 第四章 15。
**已排除**：5 条垃圾重复行（\`1918931948987031554\` 4.3 / \`1918932928252157953\` 4.5 / \`1918933052214812674\` 4.6 /
\`1918934540198682625\` 4.7 / \`1918938841746878466\` 4.8，content 仅 5–18 字节）与
2 条软删除行（\`1918945654303125506\` 4.10 / \`1920028942673477633\` 4.11）。

**Tier 分布**：A（沙箱可功能判题）${tierCount.A} 个 / B（LOCAL_BUILD，不自动判分）${tierCount.B} 个 / C（概念子任务可判）${tierCount.C} 个。

**规则**：每实验恰好 1 个 PRIMARY（weight 1.0）+ 1–3 个 RELATED；\`nodeName\` 由生成器直接从 registry 读取，逐字相等；
\`required\` 一律 \`false\`（实验是深化环节，不卡既有单元主线，也不改动 104 个已发布单元的 \`requiredCount\` 语义）；
Tier A/C 用 \`completionRule:"PASS"\`，Tier B 用 \`"SUBMIT"\`。

**落库路径**（Task 8，主 agent 执行，本轮未做任何数据库写操作）：
\`nodes.json\` → \`experiment_node\` 表（\`CourseContentService.java:331\` 的 EXPERIMENT 活动节点唯一来源）；
\`units.json\` → \`ExperimentMaintenanceService.java:80-82\` 的 \`unitMappings\`，自动创建 \`act.exp.<snowflake>\` 活动。
注意 \`CourseContentService.java:268\`：学生端读的是 \`publishedJson\`，**只插表不改 JSON 等于没接线**。

---

## 总表（41 行）

| # | 实验 id | 标题 | Tier | PRIMARY 节点 | RELATED 节点(weight) | 目标单元 / 完成规则 | 真实目标函数或源码 |
| --- | --- | --- | --- | --- | --- | --- | --- |
${tableRows.join("\n")}

---

## 节点使用频次（共用到 ${nodeUse.size} / 199 个 registry 节点）

| nodeCode | nodeName | 被引用实验数 |
| --- | --- | --- |
${[...nodeUse.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([c, n]) => `| \`${c}\` | ${registry.get(c)[1]} | ${n} |`).join("\n")}

## 单元使用频次（共用到 ${unitUse.size} / 104 个已发布单元）

| unitCode | title | 被引用实验数 |
| --- | --- | --- |
${[...unitUse.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([c, n]) => `| \`${c}\` | ${units.get(c)[1]} | ${n} |`).join("\n")}

---

## nodeProposals（确认缺口，**未自行新增节点**，等用户确认）

现有 199 个节点来自王道考研知识图谱，粒度偏「概念/算法/机制」，与北航 MOS 内核实验的工程实体存在以下缺口。
本轮**没有**向 \`knowledge_node_registry\` 插入任何节点，只用现有节点做了最贴近的映射：

| 建议 nodeCode | 建议 name | nodeType | 缺口影响的实验 | 目前落在哪个现有节点上 |
| --- | --- | --- | --- | --- |
| \`os.memory.cow\` | 写时复制 | MECHANISM | 4.10 duppage / 4.13 pgfault / 4.15 fork | \`os.memory.sharing\` + \`os.memory.page_fault\`（COW 是「共享 + 缺页」的组合机制，现有节点无法单独表达） |
| \`os.memory.frame_free_list\` | 空闲页框链表与物理页分配 | MECHANISM | 2.1 queue.h 宏 / 2.3 page_init / 2.4 page_alloc·page_free | \`os.memory.paging\`（该节点偏「基本分页存储管理/地址变换」，与物理页框链表管理粒度不完全对齐） |
| \`os.runtime.elf\` | ELF 文件格式 | CONCEPT | 1.2 readelf / 3.6 load_icode_mapper / 3.7 load_icode | \`os.memory.loading_linking\`（过程性 MECHANISM，缺少文件格式本身的 CONCEPT 节点） |
| \`os.boot.build_system\` | 内核构建与交叉编译 | MECHANISM | 1.1 Makefile/include.mk | \`os.memory.loading_linking\` + \`os.boot.boot_process\`（后者讲引导时序，不是构建） |

## tierProposals

**空。** 本轮未发现新的、够格的概念同构候选。Tier C 严格限定为 1.1、1.3 两个，未自行扩大；
5 个 Tier B（1.4 / 2.8 / 3.12 / 3.13 / 4.1）一律 \`judgeMode:"LOCAL_BUILD"\`、\`cases.json\` 为 \`{"cases":[]}\`，
**没有为任何一个编造算法代理题**。
`;

writeFileSync(join(ART, "mapping.md"), md, "utf8");
console.log(`OK: wrote ${written} json files for ${DATA.length} experiments`);
console.log(`OK: mapping.md (${Buffer.byteLength(md, "utf8")} bytes)`);
console.log(`tier: A=${tierCount.A} B=${tierCount.B} C=${tierCount.C}`);
console.log(`distinct nodes used=${nodeUse.size}/199, distinct units used=${unitUse.size}/104`);
