/* 操作系统实验 第二章 内存管理 —— 主机端等价判题环境（公共段）
 * 真实源码：MOS mm/pmap.c、include/mmu.h、include/queue.h
 * 等价适配（不改变语义）：
 *   1. u_long/u_int -> uint32_t，保证 Windows(LLP64) 与 Linux(LP64) 下地址宽度一致；
 *   2. 物理内存用一维字节数组 phys[] 模拟，物理地址就是数组下标，
 *      因此 KADDR/PADDR 退化为数组偏移，与真实内核的线性映射语义相同；
 *   3. panic 不终止进程，tlb_invalidate 为空操作（主机无 TLB）。
 * 本段由平台提供，学生只填写标记区域内的函数。
 */
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#define MOS_UNUSED __attribute__((unused))

#define BY2PG    4096u
#define PDMAP    (4u * 1024u * 1024u)
#define PTE_V    0x0001u
#define PTE_R    0x0002u
#define E_NO_MEM 4

#define PTE_ADDR(pte) ((uint32_t)(pte) & ~0xFFFu)
#define PDX(va)       (((uint32_t)(va) >> 22) & 0x3FFu)
#define PTX(va)       (((uint32_t)(va) >> 12) & 0x3FFu)
#define ROUND(a, n)   (((((uint32_t)(a)) + (n) - 1u) / (n)) * (n))
#define bzero(p, n)   memset((p), 0, (n))

typedef uint32_t Pde;
typedef uint32_t Pte;

/* include/queue.h 真实链表宏（实验 2.1 已验证版本） */
#define LIST_HEAD(name, type) \
    struct name { struct type *lh_first; }

#define LIST_ENTRY(type) \
    struct { struct type *le_next; struct type **le_prev; }

#define LIST_EMPTY(head)      ((head)->lh_first == NULL)
#define LIST_FIRST(head)      ((head)->lh_first)
#define LIST_NEXT(elm, field) ((elm)->field.le_next)

#define LIST_INIT(head) do { LIST_FIRST((head)) = NULL; } while (0)

#define LIST_FOREACH(var, head, field) \
    for ((var) = LIST_FIRST((head)); (var); (var) = LIST_NEXT((var), field))

#define LIST_INSERT_HEAD(head, elm, field) do { \
    if ((LIST_NEXT((elm), field) = LIST_FIRST((head))) != NULL) \
        LIST_FIRST((head))->field.le_prev = &LIST_NEXT((elm), field); \
    LIST_FIRST((head)) = (elm); \
    (elm)->field.le_prev = &LIST_FIRST((head)); \
} while (0)

#define LIST_INSERT_TAIL(head, elm, field) do { \
    if (LIST_FIRST((head)) == NULL) { \
        LIST_FIRST((head)) = (elm); \
        (elm)->field.le_prev = &LIST_FIRST((head)); \
    } else { \
        LIST_NEXT((elm), field) = LIST_FIRST((head)); \
        while (LIST_NEXT(LIST_NEXT((elm), field), field) != NULL) \
            LIST_NEXT((elm), field) = LIST_NEXT(LIST_NEXT((elm), field), field); \
        (elm)->field.le_prev = &LIST_NEXT(LIST_NEXT((elm), field), field); \
        LIST_NEXT(LIST_NEXT((elm), field), field) = (elm); \
    } \
    LIST_NEXT((elm), field) = NULL; \
} while (0)

#define LIST_REMOVE(elm, field) do { \
    if (LIST_NEXT((elm), field) != NULL) \
        LIST_NEXT((elm), field)->field.le_prev = (elm)->field.le_prev; \
    *(elm)->field.le_prev = LIST_NEXT((elm), field); \
} while (0)

struct Page { int32_t pp_ref; LIST_ENTRY(Page) pp_link; };
LIST_HEAD(Page_list, Page);

#define NPAGE_MAX 256u

static MOS_UNUSED unsigned char phys[NPAGE_MAX * BY2PG];
static MOS_UNUSED struct Page pages[NPAGE_MAX];
static MOS_UNUSED struct Page_list page_free_list;
static MOS_UNUSED uint32_t npage;
static MOS_UNUSED uint32_t freemem;
static MOS_UNUSED uint32_t maxpa;
static MOS_UNUSED Pde boot_pgdir[1024];

#define KADDR(pa)  ((void *)(phys + (uint32_t)(pa)))
#define PADDR(kva) ((uint32_t)((unsigned char *)(kva) - phys))

static MOS_UNUSED uint32_t page2pa(struct Page *pp)
{
    return (uint32_t)(pp - pages) * BY2PG;
}

/* 越界物理地址钳到最后一页：学生写出坏页表项时应当判「答案错误」，
 * 而不是让进程段错误被沙箱判成 Runtime Error。 */
static MOS_UNUSED struct Page *pa2page(uint32_t pa)
{
    uint32_t idx = pa / BY2PG;
    if (idx >= NPAGE_MAX) { idx = NPAGE_MAX - 1; }
    return &pages[idx];
}

static MOS_UNUSED void *page2kva(struct Page *pp)
{
    return (void *)(phys + page2pa(pp));
}

static MOS_UNUSED void panic(const char *msg) { (void)msg; }

static MOS_UNUSED void tlb_invalidate(Pde *pgdir, uint32_t va)
{
    (void)pgdir; (void)va;
}

/* 真实 mm/pmap.c 的启动期 bump 分配器，仅在建页表时使用 */
static MOS_UNUSED void *alloc(uint32_t n, uint32_t align, int clear)
{
    uint32_t alloced;
    freemem = ROUND(freemem, align);
    alloced = freemem;
    freemem += n;
    if (clear) { bzero(phys + alloced, n); }
    if (freemem > maxpa || freemem > (uint32_t)sizeof phys) { return NULL; }
    return (void *)(phys + alloced);
}

/* ---- 驱动侧保护：任何学生错误都不允许让进程崩溃 ----
 * 沙箱把非零退出判为 Runtime Error，学生就看不到「答案错误」。
 * 所有链表步行都设上限，所有指针都先做范围校验。 */
static MOS_UNUSED int page_in_range(struct Page *p)
{
    return p != NULL && p >= pages && p < pages + NPAGE_MAX;
}

static MOS_UNUSED int in_free_list(struct Page *target)
{
    struct Page *p = LIST_FIRST(&page_free_list);
    int steps = 0;
    while (p != NULL && steps < (int)NPAGE_MAX) {
        if (!page_in_range(p)) { return 0; }
        if (p == target) { return 1; }
        p = LIST_NEXT(p, pp_link);
        steps++;
    }
    return 0;
}

static MOS_UNUSED void print_free_list(void)
{
    struct Page *p = LIST_FIRST(&page_free_list);
    int steps = 0;
    int first = 1;
    while (p != NULL && steps < (int)NPAGE_MAX) {
        if (!page_in_range(p)) { break; }
        if (!first) { printf(" "); }
        printf("%d", (int)(p - pages));
        first = 0;
        p = LIST_NEXT(p, pp_link);
        steps++;
    }
    printf("\n");
}

static MOS_UNUSED int pte_in_range(Pte *p)
{
    unsigned char *b = (unsigned char *)p;
    if (p == NULL) { return 0; }
    if (b < phys || b + sizeof(Pte) > phys + sizeof phys) { return 0; }
    return ((uint32_t)(b - phys) % (uint32_t)sizeof(Pte)) == 0;
}

/* 驱动自有的地址翻译，不依赖学生实现，且对越界页表项免疫 */
static MOS_UNUSED uint32_t va2pa_safe(uint32_t va)
{
    Pde pde = boot_pgdir[PDX(va)];
    uint32_t base;
    Pte pte;
    if (!(pde & PTE_V)) { return ~0u; }
    base = PTE_ADDR(pde);
    if (base > (uint32_t)sizeof phys || base + BY2PG > (uint32_t)sizeof phys) { return ~0u; }
    memcpy(&pte, phys + base + PTX(va) * (uint32_t)sizeof(Pte), sizeof pte);
    if (!(pte & PTE_V)) { return ~0u; }
    return PTE_ADDR(pte);
}

static MOS_UNUSED void reset_state(uint32_t np, uint32_t fm)
{
    uint32_t i;
    npage = np;
    freemem = fm;
    maxpa = (uint32_t)sizeof phys;
    LIST_INIT(&page_free_list);
    memset(boot_pgdir, 0, sizeof boot_pgdir);
    memset(phys, 0, sizeof phys);
    for (i = 0; i < NPAGE_MAX; i++) {
        pages[i].pp_ref = -1;
        memset(&pages[i].pp_link, 0, sizeof pages[i].pp_link);
    }
}
/* ---- 平台提供的 mm/pmap.c 实现（本实验不考查） ---- */

static MOS_UNUSED void page_init(void)
{
    uint32_t count = 0;
    LIST_INIT(&page_free_list);
    freemem = ROUND(freemem, BY2PG);
    for (count = 0; count < freemem / BY2PG; count++) {
        pages[count].pp_ref = 1;
    }
    for (; count < npage; count++) {
        pages[count].pp_ref = 0;
        LIST_INSERT_HEAD(&page_free_list, &pages[count], pp_link);
    }
}
/* ---- 平台提供的 mm/pmap.c 实现（本实验不考查） ---- */

static MOS_UNUSED int page_alloc(struct Page **pp)
{
    struct Page *ppage_temp;
    if (LIST_EMPTY(&page_free_list)) { return -E_NO_MEM; }
    ppage_temp = LIST_FIRST(&page_free_list);
    LIST_REMOVE(ppage_temp, pp_link);
    *pp = ppage_temp;
    bzero(page2kva(ppage_temp), BY2PG);
    return 0;
}

static MOS_UNUSED void page_free(struct Page *pp)
{
    if (pp->pp_ref == 1) { return; }
    if (pp->pp_ref == 0) {
        LIST_INSERT_HEAD(&page_free_list, pp, pp_link);
        return;
    }
    panic("pp->pp_ref is less than zero");
}
/* ---- 平台提供的 mm/pmap.c 实现（本实验不考查） ---- */

static MOS_UNUSED Pte *boot_pgdir_walk(Pde *pgdir, uint32_t va, int create)
{
    Pde *pgdir_entryp = &pgdir[PDX(va)];
    Pte *pgtable = (Pte *)KADDR(PTE_ADDR(*pgdir_entryp));
    if (!(*pgdir_entryp & PTE_V) && create == 1) {
        pgtable = (Pte *)alloc(BY2PG, BY2PG, 1);
        if (pgtable == NULL) { return NULL; }
        *pgdir_entryp = PTE_ADDR(PADDR(pgtable)) | PTE_V;
    } else if (!(*pgdir_entryp & PTE_V) && create == 0) {
        return NULL;
    }
    return &pgtable[PTX(va)];
}

static MOS_UNUSED int pgdir_walk(Pde *pgdir, uint32_t va, int create, Pte **ppte)
{
    Pde *pgdir_entryp = &pgdir[PDX(va)];
    Pte *pgtable = (Pte *)KADDR(PTE_ADDR(*pgdir_entryp));
    struct Page *ppage = NULL;
    int err_num;
    *ppte = NULL;
    if (!(*pgdir_entryp & PTE_V) && create == 1) {
        err_num = page_alloc(&ppage);
        if (err_num != 0) { return err_num; }
        ppage->pp_ref++;
        pgtable = (Pte *)page2kva(ppage);
        *pgdir_entryp = page2pa(ppage) | PTE_V;
    } else if (!(*pgdir_entryp & PTE_V) && create == 0) {
        *ppte = NULL;
        return 0;
    }
    *ppte = &pgtable[PTX(va)];
    return 0;
}
/* ---- 平台提供的 mm/pmap.c 实现（本实验不考查） ---- */

static MOS_UNUSED struct Page *page_lookup(Pde *pgdir, uint32_t va, Pte **ppte)
{
    Pte *pte = NULL;
    struct Page *ppage;
    pgdir_walk(pgdir, va, 0, &pte);
    if (pte == NULL || !pte_in_range(pte)) { return NULL; }
    if ((*pte & PTE_V) == 0) { return NULL; }
    if (PTE_ADDR(*pte) >= npage * BY2PG) { return NULL; }
    ppage = pa2page(*pte);
    if (ppte) { *ppte = pte; }
    return ppage;
}

static MOS_UNUSED void page_decref(struct Page *pp)
{
    if (--pp->pp_ref == 0) { page_free(pp); }
}

/* include/pmap.h 的等价前置声明：page_insert 内部要调用 page_remove */
static int page_insert(Pde *pgdir, struct Page *pp, uint32_t va, uint32_t perm);
static void page_remove(Pde *pgdir, uint32_t va);
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
/* ---- 第三章/第四章共享：MOS 进程管理宿主环境（lib/env.c 的等价骨架） ----
 *
 * 【宿主环境说明】工作区未提供 lib/env.c 原文。本段的函数语义按题面文字 +
 * MOS/MIPS3000 公开语义构造，属等价示例，不是原文逐字转录。
 *
 * 宿主等价适配：
 *   1. envs[] 是静态数组（真实内核里它由 mips_vm_init 用 alloc 分配、经 UENVS 窗口访问），
 *      因为第三章考查的是进程管理逻辑而不是启动期分配；物理内存 arena 仍沿用第二章模型。
 *   2. TIMESTACK / KERNEL_SP 在真实内核里是汇编常量地址，宿主改为两个 struct Trapframe
 *      全局量，宏直接给出指针 —— 避免在 64 位主机上把 uint32 地址强转成指针造成截断 UB。
 *   3. lcontext() / env_pop_tf() 在真实内核里是 MIPS 汇编（写 CP0 Context、恢复寄存器、eret），
 *      宿主用【记录函数】等价替代：只把调用参数与调用时刻的 curenv 记进事件日志，
 *      不做真实上下文切换。学生 env_run 的四步调用顺序仍被真实校验。
 *   4. panic 不终止进程；一切链表步行带 NENV 步数上限；一切学生返回的指针先做范围校验。
 */

LIST_HEAD(Env_list, Env);

static MOS_UNUSED struct Env envs[NENV];
static MOS_UNUSED struct Env_list env_free_list;
static MOS_UNUSED struct Env_list env_sched_list;
static MOS_UNUSED struct Env *curenv;
static MOS_UNUSED uint32_t envid_gen;      /* mkenvid 的生成号计数器 */

/* 宿主等价：TIMESTACK（时钟中断现场）与 KERNEL_SP（系统调用现场） */
static MOS_UNUSED struct Trapframe timestack_tf;
static MOS_UNUSED struct Trapframe kernsp_tf;
#define TIMESTACK (&timestack_tf)
#define KERNEL_SP (&kernsp_tf)

/* include/env.h 的真实 mkenvid：低 LOG2NENV 位是下标，高位是生成号，
 * 这样同一个 PCB 槽位被复用后 env_id 不同，旧 id 会被 envid2env 拒绝。 */
static MOS_UNUSED uint32_t mkenvid(struct Env *e)
{
    envid_gen++;
    return (envid_gen << (1u + LOG2NENV)) | (uint32_t)(e - envs);
}

/* ---- 驱动侧保护 ---- */

static MOS_UNUSED int env_in_range(struct Env *e)
{
    return e != NULL && e >= envs && e < envs + NENV;
}

static MOS_UNUSED int env_idx(struct Env *e)
{
    if (!env_in_range(e)) { return -1; }
    return (int)(e - envs);
}

static MOS_UNUSED int in_env_free_list(struct Env *target)
{
    struct Env *e = LIST_FIRST(&env_free_list);
    int steps = 0;
    while (e != NULL && steps < (int)NENV) {
        if (!env_in_range(e)) { return 0; }
        if (e == target) { return 1; }
        e = LIST_NEXT(e, env_link);
        steps++;
    }
    return 0;
}

static MOS_UNUSED int in_sched_list(struct Env *target)
{
    struct Env *e = LIST_FIRST(&env_sched_list);
    int steps = 0;
    while (e != NULL && steps < (int)NENV) {
        if (!env_in_range(e)) { return 0; }
        if (e == target) { return 1; }
        e = LIST_NEXT(e, env_sched_link);
        steps++;
    }
    return 0;
}

/* 学生可能把 env_pgdir 留成 NULL 或写成野指针；页目录必须容得下 1024 个 Pde */
static MOS_UNUSED int pgdir_ok(Pde *p)
{
    unsigned char *b;
    if (p == NULL) { return 0; }
    if (p == boot_pgdir) { return 1; }
    b = (unsigned char *)p;
    if (b < phys || b + 1024u * (uint32_t)sizeof(Pde) > phys + sizeof phys) { return 0; }
    return ((uint32_t)(b - phys) % (uint32_t)sizeof(Pde)) == 0;
}

/* 驱动自有的地址翻译：不依赖学生实现，对越界页目录/页表项免疫 */
static MOS_UNUSED uint32_t env_va2pa(Pde *pgdir, uint32_t va)
{
    Pde pde;
    uint32_t base;
    Pte pte;
    if (!pgdir_ok(pgdir)) { return ~0u; }
    pde = pgdir[PDX(va)];
    if (!(pde & PTE_V)) { return ~0u; }
    base = PTE_ADDR(pde);
    if (base + BY2PG > (uint32_t)sizeof phys) { return ~0u; }
    memcpy(&pte, phys + base + PTX(va) * (uint32_t)sizeof(Pte), sizeof pte);
    if (!(pte & PTE_V)) { return ~0u; }
    if (PTE_ADDR(pte) + BY2PG > (uint32_t)sizeof phys) { return ~0u; }
    return PTE_ADDR(pte);
}

/* va 处页表项原始值；无页表/无映射返回 ~0 */
static MOS_UNUSED uint32_t env_pte(Pde *pgdir, uint32_t va)
{
    Pde pde;
    uint32_t base;
    Pte pte;
    if (!pgdir_ok(pgdir)) { return ~0u; }
    pde = pgdir[PDX(va)];
    if (!(pde & PTE_V)) { return ~0u; }
    base = PTE_ADDR(pde);
    if (base + BY2PG > (uint32_t)sizeof phys) { return ~0u; }
    memcpy(&pte, phys + base + PTX(va) * (uint32_t)sizeof(Pte), sizeof pte);
    return pte;
}

/* 内核段（PDX(UTOP)..1023，跳过 UVPT 自映射槽）中与 boot_pgdir 逐字相同的项数。
 * 满分 = 1024 - PDX(UTOP) - 1 = 767。跳过 UVPT 是因为它本来就该被自映射覆盖。 */
static MOS_UNUSED uint32_t kernel_pde_copied(Pde *pgdir)
{
    uint32_t i, cnt = 0;
    if (!pgdir_ok(pgdir)) { return ~0u; }
    for (i = PDX(UTOP); i < 1024u; i++) {
        if (i == PDX(UVPT)) { continue; }
        if (pgdir[i] == boot_pgdir[i]) { cnt++; }
    }
    return cnt;
}

/* 学生可能把 env_pgdir 留成 NULL 或野指针，先校验再算物理地址 */
static MOS_UNUSED int ptr_in_phys(const void *p)
{
    const unsigned char *b = (const unsigned char *)p;
    return b >= phys && b < phys + sizeof phys;
}

static MOS_UNUSED uint32_t pa_of(const void *p)
{
    if (!ptr_in_phys(p)) { return ~0u; }
    return (uint32_t)((const unsigned char *)p - phys);
}

/* 页目录所在物理页的 pp_ref；页目录非法时返回 -1 */
static MOS_UNUSED int pgdir_pp_ref(struct Env *e)
{
    uint32_t pa;
    if (!env_in_range(e) || !pgdir_ok(e->env_pgdir)) { return -1; }
    pa = pa_of(e->env_pgdir);
    if (pa == ~0u || pa % BY2PG != 0 || pa / BY2PG >= NPAGE_MAX) { return -1; }
    return (int)pages[pa / BY2PG].pp_ref;
}

/* ---- lcontext / env_pop_tf 的宿主记录替身 ----
 * 三个替身函数拆在 env-recorder 段（装配时紧跟本段）；数组与 MAXREC 留在这里。 */

#define MAXREC 96
static MOS_UNUSED int rec_kind[MAXREC];      /* 0 = lcontext, 1 = env_pop_tf */
static MOS_UNUSED uint32_t rec_arg[MAXREC];  /* lcontext: 页目录物理地址; env_pop_tf: cp0_epc */
static MOS_UNUSED int rec_cur[MAXREC];       /* 调用时刻 curenv 的下标，-1 = NULL/越界 */
static MOS_UNUSED int rec_n;

/* ---- 状态复位与驱动辅助 ---- */

/* 宿主等价：真实内核启动后 boot_pgdir 已含内核段映射（由 mips_vm_init 建立）。
 * 这里用启动期 bump 分配器建立同样的内核映射，供 env_setup_vm 拷贝到各进程页目录。
 * 只映射少量页，避免 1MB 宿主 arena 被耗尽；映射目标都是随后 page_init 标记为已用的页。
 * 必须在 page_init 之前调用（真实启动顺序：mips_vm_init -> page_init）。 */
static MOS_UNUSED void boot_kernel_map(void)
{
    uint32_t k;
    for (k = 0; k < 4u; k++) {
        Pte *pte = boot_pgdir_walk(boot_pgdir, UTOP + k * BY2PG, 1);
        if (pte == NULL) { return; }
        *pte = (k * BY2PG) | PTE_V | PTE_R;
    }
    for (k = 0; k < 2u; k++) {
        Pte *pte = boot_pgdir_walk(boot_pgdir, UENVS + k * BY2PG, 1);
        if (pte == NULL) { return; }
        *pte = ((2u + k) * BY2PG) | PTE_V | PTE_R;
    }
}

/* 宿主测试夹具（可选）：真实内核的 boot_pgdir 在 UTOP 以下是空的，因为那 1GB 属于
 * 用户态地址空间。这里故意留一条「boot 期遗留的用户态映射」（va 0x00400000 -> pa 4096），
 * 用来检验 env_setup_vm 是否只拷内核段 PDX(UTOP)..1023，而不是整张 1024 项页目录。
 * 必须在 page_init 之前建立：否则 bump 分配器会把已挂上空闲链表的页当页表发出去。 */
static MOS_UNUSED void boot_stale_user_map(void)
{
    Pte *pte = boot_pgdir_walk(boot_pgdir, 0x00400000u, 1);
    if (pte == NULL) { return; }
    *pte = 0x00001000u | PTE_V | PTE_R;
}

static MOS_UNUSED void reset_env_state_ex(uint32_t np, uint32_t fm, uint32_t stale)
{
    uint32_t i;
    reset_state(np, fm);
    boot_kernel_map();
    if (stale) { boot_stale_user_map(); }
    page_init();
    for (i = 0; i < NENV; i++) {
        memset(&envs[i], 0, sizeof envs[i]);
        envs[i].env_status = ENV_FREE;
    }
    LIST_INIT(&env_free_list);
    LIST_INIT(&env_sched_list);
    curenv = NULL;
    envid_gen = 0;
    memset(&timestack_tf, 0, sizeof timestack_tf);
    memset(&kernsp_tf, 0, sizeof kernsp_tf);
    rec_n = 0;
    memset(rec_kind, 0, sizeof rec_kind);
    memset(rec_arg, 0, sizeof rec_arg);
    memset(rec_cur, 0, sizeof rec_cur);
}

static MOS_UNUSED void reset_env_state(uint32_t np, uint32_t fm)
{
    reset_env_state_ex(np, fm, 0);
}

/* 平台侧的进程销毁（真实 MOS 的 env_destroy 简化版）：仅供驱动构造场景，
 * 学生不填写。已空闲的进程不重复入链，避免错误实现造成环。 */
static MOS_UNUSED void env_free(struct Env *e)
{
    if (!env_in_range(e)) { return; }
    if (in_sched_list(e)) { LIST_REMOVE(e, env_sched_link); }
    e->env_status = ENV_FREE;
    e->env_id = 0;
    if (!in_env_free_list(e)) { LIST_INSERT_HEAD(&env_free_list, e, env_link); }
}

static MOS_UNUSED void print_env_list(struct Env_list *head, int sched)
{
    struct Env *e = LIST_FIRST(head);
    int steps = 0, first = 1;
    while (e != NULL && steps < (int)NENV) {
        if (!env_in_range(e)) { break; }
        if (!first) { printf(" "); }
        printf("%d", (int)(e - envs));
        first = 0;
        e = sched ? LIST_NEXT(e, env_sched_link) : LIST_NEXT(e, env_link);
        steps++;
    }
    printf("\n");
}

/* 链表长度（带步数上限，学生写出环时不死循环） */
static MOS_UNUSED int count_env_list(struct Env_list *head, int sched)
{
    struct Env *e = LIST_FIRST(head);
    int steps = 0;
    while (e != NULL && steps < (int)NENV) {
        if (!env_in_range(e)) { break; }
        e = sched ? LIST_NEXT(e, env_sched_link) : LIST_NEXT(e, env_link);
        steps++;
    }
    return steps;
}

/* 空闲物理页数（带步数上限） */
static MOS_UNUSED int count_free_pages(void)
{
    struct Page *p = LIST_FIRST(&page_free_list);
    int steps = 0;
    while (p != NULL && steps < (int)NPAGE_MAX) {
        if (!page_in_range(p)) { break; }
        p = LIST_NEXT(p, pp_link);
        steps++;
    }
    return steps;
}

/* 打印链表前 k 个下标（k 由 stdin 给出，带步数上限） */
static MOS_UNUSED void print_env_list_n(struct Env_list *head, int sched, int k)
{
    struct Env *e = LIST_FIRST(head);
    int steps = 0, first = 1;
    while (e != NULL && steps < (int)NENV && steps < k) {
        if (!env_in_range(e)) { break; }
        if (!first) { printf(" "); }
        printf("%d", (int)(e - envs));
        first = 0;
        e = sched ? LIST_NEXT(e, env_sched_link) : LIST_NEXT(e, env_link);
        steps++;
    }
    printf("\n");
}
/* ---- 第三章/第四章共享：lcontext / env_pop_tf 的宿主记录替身 ----
 * 从 env-pcb 段拆出，只为源码预算：这两个替身仅被 env_run 调用，用户态实验
 * （CH4_USER_DRIVER）已裁掉 env-run，故 mk.sh 在该模式下连本片段一起排除。
 * rec_* 数组与 MAXREC 仍留在 env-pcb 段（复位与驱动 Y 操作符要用）。 */

/* struct Trapframe 只可能落在 envs[] 的 env_tf 或两个现场全局量上 */
static MOS_UNUSED int tf_in_range(struct Trapframe *tf)
{
    unsigned char *b = (unsigned char *)tf;
    if (tf == NULL) { return 0; }
    if (b == (unsigned char *)&timestack_tf || b == (unsigned char *)&kernsp_tf) { return 1; }
    if (b >= (unsigned char *)envs && b < (unsigned char *)(envs + NENV)) {
        return ((uint32_t)(b - (unsigned char *)envs) % (uint32_t)sizeof(struct Env)) == 0;
    }
    return 0;
}

/* 真实内核：汇编写 CP0 Context 寄存器切换地址空间。宿主：只记录。 */
static MOS_UNUSED void lcontext(uint32_t pgdir_pa)
{
    if (rec_n < MAXREC) {
        rec_kind[rec_n] = 0;
        rec_arg[rec_n] = pgdir_pa;
        rec_cur[rec_n] = env_idx(curenv);
    }
    rec_n++;
}

/* 真实内核：汇编恢复 32 个寄存器并 eret 进入用户态。宿主：只记录。 */
static MOS_UNUSED void env_pop_tf(struct Trapframe *tf)
{
    if (rec_n < MAXREC) {
        rec_kind[rec_n] = 1;
        rec_arg[rec_n] = tf_in_range(tf) ? tf->cp0_epc : ~0u;
        rec_cur[rec_n] = env_idx(curenv);
    }
    rec_n++;
}
/* ---- 平台提供的 mm/pmap.c page_insert / page_remove（实验 2.7 已验证版） ----
 * 第三章/第四章不考查这两个函数，直接内联第二章验证通过的实现。 */

static MOS_UNUSED int page_insert(Pde *pgdir, struct Page *pp, uint32_t va, uint32_t perm)
{
    uint32_t PERM = perm | PTE_V;
    Pte *pgtable_entry = NULL;
    if (!pgdir_ok(pgdir) || !page_in_range(pp)) { return -E_NO_MEM; }
    pgdir_walk(pgdir, va, 0, &pgtable_entry);
    if (pgtable_entry != NULL && pte_in_range(pgtable_entry) && (*pgtable_entry & PTE_V) != 0) {
        if (pa2page(*pgtable_entry) != pp) {
            page_remove(pgdir, va);
        } else {
            tlb_invalidate(pgdir, va);
            *pgtable_entry = page2pa(pp) | PERM;
            return 0;
        }
    }
    tlb_invalidate(pgdir, va);
    if (pgdir_walk(pgdir, va, 1, &pgtable_entry) < 0) { return -E_NO_MEM; }
    if (pgtable_entry == NULL || !pte_in_range(pgtable_entry)) { return -E_NO_MEM; }
    *pgtable_entry = page2pa(pp) | PERM;
    pp->pp_ref++;
    return 0;
}

static MOS_UNUSED void page_remove(Pde *pgdir, uint32_t va)
{
    Pte *pagetable_entry = NULL;
    struct Page *ppage;
    if (!pgdir_ok(pgdir)) { return; }
    ppage = page_lookup(pgdir, va, &pagetable_entry);
    if (ppage == NULL || !page_in_range(ppage)) { return; }
    ppage->pp_ref--;
    if (ppage->pp_ref == 0) { page_free(ppage); }
    if (pagetable_entry != NULL && pte_in_range(pagetable_entry)) { *pagetable_entry = 0; }
    tlb_invalidate(pgdir, va);
}
/* ---- 平台提供的 lib/env.c env_init（实验 3.2 考查时排除本片段） ----
 * 与第二章 page_init 同构：升序遍历 + LIST_INSERT_HEAD，
 * 因此空闲链表遍历顺序是 NENV-1 .. 0（题面「注意插入的顺序」）。 */

static MOS_UNUSED void env_init(void)
{
    int i;
    LIST_INIT(&env_free_list);
    for (i = 0; i < (int)NENV; i++) {
        envs[i].env_status = ENV_FREE;
        LIST_INSERT_HEAD(&env_free_list, &envs[i], env_link);
    }
}
/* ---- 平台提供的 lib/env.c env_setup_vm（实验 3.4 考查时排除本片段） ----
 * MOS 公开语义：
 *   Step 1 分配一页作页目录，并把 pp_ref 递增（页目录被本进程长期持有）；
 *   Step 2 env_pgdir = 页目录内核虚拟地址，env_cr3 = 页目录物理地址；
 *   Step 3 把 boot_pgdir 的内核段（PDX(UTOP)..1023）逐项拷贝过来，
 *          使每个进程都能看到同一份内核映射；
 *   Step 4 UVPT 自映射：env_pgdir[PDX(UVPT)] = env_cr3 | PTE_V | PTE_R，
 *          让用户态能通过 UVPT 窗口只读地观察自己的页表。 */

static MOS_UNUSED int env_setup_vm(struct Env *e)
{
    uint32_t i;
    int r;
    struct Page *p = NULL;
    if (!env_in_range(e)) { return -E_BAD_ENV; }
    r = page_alloc(&p);
    if (r != 0) { return r; }
    p->pp_ref++;
    e->env_pgdir = (Pde *)page2kva(p);
    e->env_cr3 = page2pa(p);
    for (i = PDX(UTOP); i < 1024u; i++) { e->env_pgdir[i] = boot_pgdir[i]; }
    e->env_pgdir[PDX(UVPT)] = e->env_cr3 | PTE_V | PTE_R;
    return 0;
}
/* ===== 学生填写区：实现 lib/env.c 的 env_alloc() ===== */

/* 典型错误解（四个缺陷都能被用例观测到）：
 *   缺陷 1：env_status 直接设成 ENV_RUNNABLE。新进程还没加载映像就被调度会跑飞。
 *           观测点：N 的 status 字段（参考解 1，本解 2）。
 *   缺陷 2：cp0_status 直接置「当前」位 KUc|IEc，而不是「上一次」位 KUp|IEp。
 *           eret 会把 KUp->KUc、IEp->IEc，于是本解 eret 后反而回到内核态且关中断。
 *           观测点：N 的 sr 与 KUc/IEc/KUp/IEp 四个位字段。
 *   缺陷 3：先 LIST_REMOVE 再调 env_setup_vm。失败时 PCB 已被摘出空闲链表却没人持有，
 *           永久泄漏一个 PCB 槽位。观测点：用例 3 第 5 次分配失败后的 L 4 表头。
 *   缺陷 4：不清空 Trapframe，也不复位 env_pgfault_handler / 三个 IPC 字段。
 *           复用槽位时会继承上一个进程的缺页处理函数与 IPC 状态。
 *           观测点：用例 5 先 D 污染再分配，P 输出（参考解全 0，本解 12345/1/777/888）。 */
static int env_alloc(struct Env **newenv, uint32_t parent_id)
{
    int r;
    struct Env *e;
    *newenv = NULL;
    if (LIST_EMPTY(&env_free_list)) { return -E_NO_FREE_ENV; }
    e = LIST_FIRST(&env_free_list);
    LIST_REMOVE(e, env_link);                       /* 缺陷 3：摘链太早 */
    r = env_setup_vm(e);
    if (r != 0) { return r; }
    e->env_id = mkenvid(e);
    e->env_parent_id = parent_id;
    e->env_status = ENV_RUNNABLE;                   /* 缺陷 1 */
    e->env_pri = 1;
    e->env_tf.regs[29] = USTACKTOP;
    e->env_tf.cp0_status = MIPS_SR_KUc | MIPS_SR_IEc | MIPS_SR_IM4 | MIPS_SR_CU0;  /* 缺陷 2 */
    *newenv = e;                                    /* 缺陷 4：没有 memset / 没有复位 IPC */
    return 0;
}

/* ===== 填写区结束 ===== *//* ===== 填写区结束 ===== */

#define MAXOPS 80

/* stdin: 第一行 "npage freemem"（1..256 / 0..65536），第二行操作数 n（0..MAXOPS），随后 n 个操作：
 *   I        env_init()，打印 "I 空闲PCB数"（重复调用打印 I SKIP）
 *   A parent 调学生 env_alloc(&e,parent)，打印 "A 返回码 PCB下标 新envid"
 *            （下标 -1 表示 *newenv 为空或越界；envid 取驱动自己保存的 e->env_id）
 *   N idx    打印 "N idx env_id parent_id status pri sp sr KUc IEc KUp IEp"
 *   P idx    打印 "P idx env_pgfault_handler ipc_receiving ipc_from ipc_value"
 *   D idx    驱动把 envs[idx] 的 Trapframe/优先级/状态/IPC 字段全部污染成哨兵值，
 *            用于检验 env_alloc 是否真的重新初始化了这些字段（打印 "D idx"）
 *   F idx    env_free(&envs[idx])，打印 "F idx"
 *   U idx    curenv = &envs[idx]，打印 "U idx"
 *   L k      打印空闲 PCB 链表前 k 个下标（k 0..64）
 *   W idx    打印 "W idx 页目录物理地址 env_cr3"（检验 env_setup_vm 是否被调用）
 *   C        打印空闲物理页数
 * idx 必须 <64，否则驱动直接结束。
 */
int main(void)
{
    uint32_t np = 0, fm = 0;
    int n = 0, i, did_i = 0;
    if (scanf("%u %u", &np, &fm) != 2) { return 0; }
    if (np == 0 || np > NPAGE_MAX || fm > 65536u) { return 0; }
    reset_env_state(np, fm);
    if (scanf("%d", &n) != 1 || n < 0 || n > MAXOPS) { return 0; }
    for (i = 0; i < n; i++) {
        char op = 0;
        if (scanf(" %c", &op) != 1) { break; }
        if (op == 'I') {
            if (did_i) { printf("I SKIP\n"); continue; }
            did_i = 1;
            env_init();
            printf("I %d\n", count_env_list(&env_free_list, 0));
        } else if (op == 'A') {
            uint32_t parent = 0;
            struct Env *e = NULL;
            int r;
            if (scanf("%u", &parent) != 1) { return 0; }
            r = env_alloc(&e, parent);
            if (env_in_range(e)) {
                printf("A %d %d %u\n", r, env_idx(e), e->env_id);
            } else {
                printf("A %d -1 0\n", r);
            }
        } else if (op == 'N') {
            uint32_t idx = 0;
            struct Env *e;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            e = &envs[idx];
            printf("N %u %u %u %u %u %u %u %u %u %u %u\n", idx, e->env_id,
                   e->env_parent_id, e->env_status, e->env_pri,
                   e->env_tf.regs[29], e->env_tf.cp0_status,
                   (e->env_tf.cp0_status & MIPS_SR_KUc) ? 1u : 0u,
                   (e->env_tf.cp0_status & MIPS_SR_IEc) ? 1u : 0u,
                   (e->env_tf.cp0_status & MIPS_SR_KUp) ? 1u : 0u,
                   (e->env_tf.cp0_status & MIPS_SR_IEp) ? 1u : 0u);
        } else if (op == 'P') {
            uint32_t idx = 0;
            struct Env *e;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            e = &envs[idx];
            printf("P %u %u %u %u %u\n", idx, e->env_pgfault_handler,
                   e->env_ipc_receiving, e->env_ipc_from, e->env_ipc_value);
        } else if (op == 'D') {
            uint32_t idx = 0, k;
            struct Env *e;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            e = &envs[idx];
            for (k = 0; k < 32u; k++) { e->env_tf.regs[k] = 0xDEADBEEFu; }
            e->env_tf.cp0_status = 0xFFFFFFFFu;
            e->env_tf.cp0_epc = 0xFFFFFFFFu;
            e->env_tf.cp0_badvaddr = 0xFFFFFFFFu;
            e->env_tf.cp0_cause = 0xFFFFFFFFu;
            e->env_pri = 99u;
            e->env_status = ENV_RUNNABLE;
            e->env_pgfault_handler = 12345u;
            e->env_ipc_receiving = 1u;
            e->env_ipc_from = 777u;
            e->env_ipc_value = 888u;
            printf("D %u\n", idx);
        } else if (op == 'F') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            env_free(&envs[idx]);
            printf("F %u\n", idx);
        } else if (op == 'U') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            curenv = &envs[idx];
            printf("U %u\n", idx);
        } else if (op == 'L') {
            int k = 0;
            if (scanf("%d", &k) != 1 || k < 0 || k > (int)NENV) { return 0; }
            printf("L ");
            print_env_list_n(&env_free_list, 0, k);
        } else if (op == 'W') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("W %u %u %u\n", idx, pa_of(envs[idx].env_pgdir), envs[idx].env_cr3);
        } else if (op == 'C') {
            printf("C %d\n", count_free_pages());
        } else {
            return 0;
        }
    }
    return 0;
}