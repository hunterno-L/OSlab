/* MOS 进程控制块与异常现场的宿主等价定义。
 * 按 MOS/MIPS3000 语义提供字段、地址布局和 CP0 状态位，用于功能验证。
 * 整数统一使用 uint32_t，保持 LLP64 与 LP64 环境下的宽度一致。
 * 完整内核运行需在 MIPS 环境验证。
 */

#define NENV      64u
#define LOG2NENV  6u

/* include/env.h 的进程状态 */
#define ENV_FREE         0u
#define ENV_NOT_RUNNABLE 1u
#define ENV_RUNNABLE     2u

/* include/mmu.h 的地址布局（宿主等价值，PDMAP = 4MB 一个页目录项的覆盖范围）。
 * 用户空间 [0, UTOP)；内核空间 [UTOP, 0x80000000)；
 * UPAGES/UENVS/UVPT 是内核数据开给用户态只读观察的三个窗口，位于 UTOP 之上，
 * 因此每个进程的页目录都要从 boot_pgdir 拷贝这一段内核映射。 */
#define UTOP   0x40000000u
#define UPAGES 0x7F000000u
#define UENVS  0x7F400000u
#define UVPT   0x7F800000u
#define UXSTACKTOP 0x3F000000u   /* user/fork.c 的异常处理栈顶 */
#define USTACKTOP  UTOP          /* 用户初始栈顶（栈页映射在 USTACKTOP-BY2PG） */

/* include/error.h（E_NO_MEM 已由第二章环境段给出） */
#define E_BAD_ENV      2
#define E_INVAL        3
#define E_NO_FREE_ENV  5
#define E_NO_SYS       10
#define E_IPC_NOT_RECV 11

/* 页表项的 MOS 扩展权限位（include/mmu.h） */
#define PTE_COW     0x0008u
#define PTE_LIBRARY 0x0010u
#define PTE_UC      0x0020u

/* MIPS3000 CP0 Status 寄存器位（公开架构语义）。
 * KUc/IEc 是「当前」模式与中断使能，KUp/IEp 是「上一次」的。
 * eret 会把 KUp->KUc、IEp->IEc，所以要用 eret 进入用户态并开中断，
 * 必须置 KUp|IEp 而清 KUc|IEc。 */
#define MIPS_SR_IEc 0x00000001u
#define MIPS_SR_KUc 0x00000002u
#define MIPS_SR_IEp 0x00000004u
#define MIPS_SR_KUp 0x00000008u
#define MIPS_SR_IM4 0x00001000u   /* 开放时钟中断（IP4） */
#define MIPS_SR_CU0 0x10000000u   /* 允许用户态访问 CP0 */
#define ENV_USER_SR (MIPS_SR_KUp | MIPS_SR_IEp | MIPS_SR_IM4 | MIPS_SR_CU0)

/* include/env.h：env_id 的低 LOG2NENV 位是 envs 数组下标 */
#define ENVX(envid) ((uint32_t)(envid) & (NENV - 1u))

/* mm/pmap.c 的向下取整宏（3.1 专用前奏段已定义时用 #ifndef 避免重复） */
#ifndef ROUNDDOWN
#define ROUNDDOWN(a, n) ((((uint32_t)(a)) / (n)) * (n))
#endif

struct Trapframe {
    uint32_t regs[32];
    uint32_t cp0_status;
    uint32_t cp0_epc;
    uint32_t cp0_badvaddr;   /* 缺页/地址错误时的故障虚拟地址 */
    uint32_t cp0_cause;
};

struct Env {
    struct Trapframe env_tf;
    LIST_ENTRY(Env) env_link;         /* 空闲进程链表 */
    uint32_t env_id;
    uint32_t env_parent_id;
    uint32_t env_status;
    Pde *env_pgdir;                   /* 页目录的内核虚拟地址 */
    uint32_t env_cr3;                 /* 页目录的物理地址 */
    LIST_ENTRY(Env) env_sched_link;   /* 就绪（调度）链表 */
    uint32_t env_pri;
    uint32_t env_pgfault_handler;
    uint32_t env_ipc_receiving;
    uint32_t env_ipc_dstva;           /* include/env.h：接收方指定的页映射目标 va，0 表示不传页 */
    uint32_t env_ipc_from;
    uint32_t env_ipc_value;
};