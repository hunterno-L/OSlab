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
/* ---- 平台提供的 lib/env.c envid2env（实验 3.3 考查时排除本片段） ----
 * MOS 公开语义：
 *   envid == 0        -> 返回当前进程 curenv；
 *   ENVX(envid)       -> 取低 LOG2NENV 位作为 envs 下标；
 *   槽位空闲或 env_id 不匹配（生成号已变） -> -E_BAD_ENV；
 *   checkperm 非 0    -> 只有当前进程自己或当前进程的子进程才有权操作。
 * 前置条件：checkperm 非 0 时 curenv 必须非空（真实内核里系统调用总在进程上下文中发生，
 * 检查驱动在输入边界强制这一点）。 */

static MOS_UNUSED int envid2env(uint32_t envid, struct Env **penv, int checkperm)
{
    struct Env *e;
    *penv = NULL;
    if (envid == 0) {
        *penv = curenv;
        return 0;
    }
    e = &envs[ENVX(envid)];
    if (e->env_status == ENV_FREE || e->env_id != envid) { return -E_BAD_ENV; }
    if (checkperm && e != curenv && e->env_id != curenv->env_parent_id) { return -E_BAD_ENV; }
    *penv = e;
    return 0;
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
/* ---- 平台提供的 lib/env.c env_alloc（实验 3.5 考查时排除本片段） ----
 * MOS 公开语义：
 *   Step 1 从 env_free_list 取一个空闲 PCB，空则 -E_NO_FREE_ENV；
 *   Step 2 调 env_setup_vm 建立虚拟内存布局，失败必须原样返回错误码
 *          （此时 PCB 仍留在空闲链表上，不能被摘掉）；
 *   Step 3 填 env_id（mkenvid）、env_parent_id、env_status = ENV_NOT_RUNNABLE、env_pri = 1；
 *   Step 4 清空 Trapframe，设初始 sp = USTACKTOP，
 *          cp0_status 置 KUp|IEp|IM4|CU0 且清 KUc|IEc —— eret 后才能进入用户态并开中断；
 *   Step 5 把 PCB 从空闲链表摘下，*newenv 指向它。 */

/* 实验 3.4 把 env_setup_vm 当作学生填写目标（body 在本片段之后），故先前置声明。
 * 当 env-setupvm.c.inc 作为平台件被包含时，这条声明与随后的定义兼容，无副作用。 */
static MOS_UNUSED int env_setup_vm(struct Env *e);

static MOS_UNUSED int env_alloc(struct Env **newenv, uint32_t parent_id)
{
    int r;
    struct Env *e;
    *newenv = NULL;
    if (LIST_EMPTY(&env_free_list)) { return -E_NO_FREE_ENV; }
    e = LIST_FIRST(&env_free_list);
    if (!env_in_range(e)) { return -E_NO_FREE_ENV; }
    r = env_setup_vm(e);
    if (r != 0) { return r; }
    e->env_id = mkenvid(e);
    e->env_parent_id = parent_id;
    e->env_status = ENV_NOT_RUNNABLE;
    e->env_pri = 1;
    memset(&e->env_tf, 0, sizeof e->env_tf);
    e->env_tf.regs[29] = USTACKTOP;
    e->env_tf.cp0_status = ENV_USER_SR;
    e->env_pgfault_handler = 0;
    e->env_ipc_receiving = 0;
    e->env_ipc_dstva = 0;
    e->env_ipc_from = 0;
    e->env_ipc_value = 0;
    LIST_REMOVE(e, env_link);
    *newenv = e;
    return 0;
}
/* ---- 平台提供的 lib/env.c env_run（实验 3.10 考查时排除本片段） ----
 * MOS 公开语义四步：
 *   Step 1 若已有当前进程，把 TIMESTACK 上的现场保存回它的 env_tf；
 *   Step 2 curenv = e，并置 ENV_RUNNABLE；
 *   Step 3 lcontext(curenv->env_cr3) 切换地址空间（传【物理地址】，不是虚拟地址）；
 *   Step 4 env_pop_tf(&curenv->env_tf) 恢复寄存器并 eret 进入用户态。
 * 宿主等价：lcontext/env_pop_tf 是记录函数（见 env-pcb 段），不做真实汇编切换，
 * 但调用参数与调用时刻的 curenv 都被记录，四步顺序仍被真实校验。 */

static MOS_UNUSED void env_run(struct Env *e)
{
    if (!env_in_range(e)) { return; }
    if (curenv != NULL && env_in_range(curenv)) {
        curenv->env_tf = *TIMESTACK;
    }
    curenv = e;
    curenv->env_status = ENV_RUNNABLE;
    lcontext(curenv->env_cr3);
    env_pop_tf(&curenv->env_tf);
}
/* ---- 平台提供的 lib/sched.c sched_yield（实验 3.14 考查时排除本片段） ----
 *
 * 【宿主环境说明】工作区未提供 lib/sched.c 原文。题面 3.14 要求
 * 「时间片轮转 + 优先级」「只有 ENV_RUNNABLE 参与」「用链表管理就绪进程」，
 * 以下按该文字 + MOS 公开语义构造等价实现：env_pri 即该进程一轮能连续跑的时钟滴答数。
 *
 * 算法（填写区注释里也会逐条给出，保证可判题）：
 *   静态游标 cur 与剩余时间片 left 跨调用保持；
 *   若 cur 为空 / left 为 0 / cur 已不是 ENV_RUNNABLE，则从 cur 的下一个开始
 *   在 env_sched_list 上环形查找第一个 ENV_RUNNABLE 的进程（最多 NENV 步），
 *   找不到就直接返回（不调用 env_run）；找到则 left = max(1, env_pri)；
 *   然后 left--，env_run(cur)。 */

static MOS_UNUSED void sched_yield(void)
{
    static struct Env *cur = NULL;
    static int left = 0;
    struct Env *e;
    int steps;
    if (cur == NULL || left == 0 || !env_in_range(cur) || cur->env_status != ENV_RUNNABLE) {
        e = (cur == NULL || !env_in_range(cur)) ? LIST_FIRST(&env_sched_list)
                                               : LIST_NEXT(cur, env_sched_link);
        steps = 0;
        while (steps < (int)NENV) {
            if (e == NULL) { e = LIST_FIRST(&env_sched_list); }
            if (e == NULL) { return; }
            if (!env_in_range(e)) { return; }
            if (e->env_status == ENV_RUNNABLE) { break; }
            e = LIST_NEXT(e, env_sched_link);
            steps++;
        }
        if (steps >= (int)NENV || e == NULL || !env_in_range(e)) { return; }
        cur = e;
        left = (cur->env_pri == 0) ? 1 : (int)cur->env_pri;
    }
    left--;
    env_run(cur);
}
/* ---- 第四章共享：系统调用号、分发表类型与内核侧前置声明 ----
 * 真实源码锚：include/syscall.h 的 SYS_* 号、lib/syscall_all.c 的 sys_call_table[]。
 *
 * 【宿主环境说明】工作区未随附 include/syscall.h、lib/syscall_all.c 原文。
 * 系统调用号取 MOS 公开值；函数签名取题面逐字给出的形式，题面未给签名的
 * （sys_ipc_recv / sys_ipc_can_send / sys_env_alloc / sys_set_pgfault_handler /
 *  sys_set_env_status）按 MOS 公开语义构造，属等价示例而非原文转录。
 *
 * 第四章只要求 9 个系统调用，因此 0 / 6 / 7 号槽位为空，
 * handle_sys 遇到空槽位或越界号必须返回 -E_NO_SYS。
 *
 * 【分发表类型】真实 MOS 的 sys_call_table 是 void *[]，handle_sys 把槽位强转成
 * 「sysno + 5 个参数」的函数指针再调用（MIPS o32 下多余参数被被调方忽略）。
 * 宿主沿用同一约定，故 sys_mem_alloc 等能保持题面逐字给出的签名。
 * void * -> 函数指针的强转在 -Wall -Wextra -Werror 下合法
 * （改成同类型函数指针强转会触发 -Wcast-function-type）。
 *
 * 【CH4_USER_DRIVER】用户态实验（4.9/4.10/4.13/4.15）定义此宏：这些用户代码只用
 * 6 个系统调用，从不触发 yield 与 IPC，故平台省掉 sys-yield、sys-ipc 两段及其前置声明，
 * 分发表对应槽位留 NULL（handle_sys 正确判成 -E_NO_SYS），以满足 65536 字节单文件上限。
 * 这是源码预算裁剪，不是语义裁剪：被考查函数的行为与完整平台逐字一致。
 */

#define SYS_mem_alloc           1
#define SYS_mem_map             2
#define SYS_mem_unmap           3
#define SYS_env_alloc           4
#define SYS_set_env_status      5
#define SYS_ipc_recv            8
#define SYS_ipc_can_send        9
#define SYS_set_pgfault_handler 10
#define SYS_yield               11
#define SYS_MAX                 12

/* handle_sys 强转用的目标类型：正好是真实 msyscall 压入的 6 个值 */
typedef int (*syscall_fn)(int sysno, uint32_t a0, uint32_t a1, uint32_t a2,
                          uint32_t a3, uint32_t a4);

/* ---- 内核侧系统调用函数（题面给出的签名逐字保留） ---- */
static MOS_UNUSED int sys_mem_alloc(int sysno, uint32_t envid, uint32_t va, uint32_t perm);
static MOS_UNUSED int sys_mem_map(int sysno, uint32_t srcid, uint32_t srcva,
                                  uint32_t dstid, uint32_t dstva, uint32_t perm);
static MOS_UNUSED int sys_mem_unmap(int sysno, uint32_t envid, uint32_t va);
#ifndef CH4_USER_DRIVER
static MOS_UNUSED void sys_yield(void);
static MOS_UNUSED int sys_ipc_recv(int sysno, uint32_t dstva);
static MOS_UNUSED int sys_ipc_can_send(int sysno, uint32_t envid, uint32_t value,
                                       uint32_t srcva, uint32_t perm);
#endif
static MOS_UNUSED int sys_env_alloc(int sysno);
static MOS_UNUSED int sys_set_pgfault_handler(int sysno, uint32_t envid,
                                              uint32_t func, uint32_t xstacktop);
static MOS_UNUSED int sys_set_env_status(int sysno, uint32_t envid, uint32_t status);

/* 宿主 glue：sys_yield 的签名是 void(void)，直接放进 void* 表再按 6 参数 int
 * 返回类型调用，返回值会是 eax 里的残留值（不确定）。真实内核靠 msyscall 忽略
 * SYS_yield 的返回值规避这一点；宿主判题要求输出确定，故用本包装函数补一个 0。
 * 学生填写的 sys_yield 本体签名不变。 */
#ifndef CH4_USER_DRIVER
static MOS_UNUSED int sys_yield_entry(int sysno, uint32_t a0, uint32_t a1,
                                      uint32_t a2, uint32_t a3, uint32_t a4);
#endif

/* 内核侧系统调用入口。缺页处理 page_fault_handler 不在此前置声明：
 * 只有实验 4.11 会定义它，而 gcc 对「声明了 static 却始终没定义」的函数会报错，
 * MOS_UNUSED 压不住这条诊断，故它的声明留给 env-traps 段自身。 */
static MOS_UNUSED void handle_sys(void);
/* ---- 平台提供的 lib/syscall_all.c sys_mem_alloc（实验 4.3 考查时排除本片段） ----
 * MOS 公开语义：
 *   Step 1 perm 补上 PTE_V（用户只给权限位，有效位由内核置）；
 *   Step 2 va >= UTOP 属内核地址空间，用户无权申请 -> -E_INVAL；
 *   Step 3 envid2env(envid, &e, 0) 取目标 PCB，失败原样上抛；
 *   Step 4 page_alloc 拿一页物理内存，失败原样上抛；
 *   Step 5 page_insert 建立映射；失败必须 page_free 把刚拿到的页还回空闲链表
 *          （page_alloc 不递增 pp_ref，此时 pp_ref 为 0，page_free 正好回收）。
 * 引用计数由 page_insert 负责（实验 2.7 已验证的契约），本函数不额外递增。 */

static MOS_UNUSED int sys_mem_alloc(int sysno, uint32_t envid, uint32_t va, uint32_t perm)
{
    struct Env *e = NULL;
    struct Page *p = NULL;
    int r;
    (void)sysno;
    perm |= PTE_V;
    if (va >= UTOP) { return -E_INVAL; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    r = page_alloc(&p);
    if (r != 0) { return r; }
    r = page_insert(e->env_pgdir, p, va, perm);
    if (r != 0) { page_free(p); }
    return r;
}
/* ---- 平台提供的 lib/syscall_all.c sys_mem_unmap（实验 4.5 考查时排除本片段） ----
 * MOS 公开语义：解除 envid 进程地址空间中 va 处的映射。
 *   Step 1 va >= UTOP -> -E_INVAL（内核映射不许用户解除）；
 *   Step 2 envid2env(..., 0) 取 PCB，失败原样上抛；
 *   Step 3 page_remove 摘掉页表项并递减 pp_ref，归零时把物理页还回空闲链表；
 *   Step 4 返回 0。
 * 关键：va 处本来就没有映射时 page_remove 是空操作，本函数仍返回 0（不是错误）。
 * 共享页只递减引用计数，只有最后一个使用者解除映射后物理页才被回收。 */

static MOS_UNUSED int sys_mem_unmap(int sysno, uint32_t envid, uint32_t va)
{
    struct Env *e = NULL;
    int r;
    (void)sysno;
    if (va >= UTOP) { return -E_INVAL; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    page_remove(e->env_pgdir, va);
    return 0;
}
/* ---- 平台提供的 lib/syscall_all.c sys_yield（实验 4.6 考查时排除本片段） ----
 * MOS 公开语义：sys_yield 本体只有一行 sched_yield()。
 *
 * 题面 4.6 实验任务 4 提到「保存当前进程的现场（KERNEL_SP 和 TIMESTACK）」：
 * 在真实 MOS 里这一步不在 sys_yield 内完成 —— 时钟中断现场由陷阱入口压在 TIMESTACK，
 * 系统调用现场压在 KERNEL_SP，而「把现场存回 env_tf」是 env_run 的第一步（实验 3.10），
 * 「syscall 指令陷入内核」是 user/syscall_wrap.S（实验 4.1，Tier B 本地构建）。
 * 因此本函数保持 MOS 原样，只转交调度器；判题通过 env_run 的记录日志观察
 * 时间片游标是否真的推进，而不是看它有没有重复保存现场。
 *
 * sys_yield_entry 是宿主 glue（理由见 env-sysno 段），学生不填写。 */

static MOS_UNUSED void sys_yield(void)
{
    sched_yield();
}
/* ---- 平台提供的 lib/syscall_all.c IPC 两个系统调用（实验 4.7 考查时排除本片段） ----
 *
 * 【宿主环境说明】题面 4.7 只给出行为要求（置 env_ipc_receiving 标志、
 * 阻塞当前进程、检查目标是否可接收、发送消息并更新目标状态），未给函数签名。
 * 以下签名与步骤按 MOS 公开语义构造，属等价示例。
 *
 * sys_ipc_recv(dstva)：
 *   Step 1 dstva >= UTOP -> -E_INVAL（接收方指定的传页目标必须在用户空间）；
 *   Step 2 置 env_ipc_receiving = 1，清空 env_ipc_from / env_ipc_value，
 *          记下 env_ipc_dstva = dstva（dstva 为 0 表示本次只收值不收页）；
 *   Step 3 env_status = ENV_NOT_RUNNABLE —— 阻塞，不再参与调度；
 *   Step 4 env_tf.regs[2] = 0（返回值 0，真正的消息值由 env_ipc_value 带出）；
 *   Step 5 sys_yield() 让出 CPU，返回 0。
 *
 * sys_ipc_can_send(envid, value, srcva, perm)：
 *   Step 1 srcva >= UTOP -> -E_INVAL；
 *   Step 2 envid2env(envid, &e, 0)，失败原样上抛；
 *   Step 3 e->env_ipc_receiving 为 0 -> -E_IPC_NOT_RECV（对方没在等消息）；
 *   Step 4 srcva != 0 时调 sys_mem_map 把发送方 srcva 处的页共享到接收方
 *          事先登记的 env_ipc_dstva 处，失败原样上抛且【不得】修改接收方状态；
 *   Step 5 填 env_ipc_value / env_ipc_from = curenv->env_id，
 *          清 env_ipc_receiving，置 env_status = ENV_RUNNABLE 唤醒对方，返回 0。
 * 注意：发送方不阻塞，唤醒的是接收方。 */

static MOS_UNUSED int sys_ipc_recv(int sysno, uint32_t dstva)
{
    (void)sysno;
    if (dstva >= UTOP) { return -E_INVAL; }
    if (curenv == NULL || !env_in_range(curenv)) { return -E_BAD_ENV; }
    curenv->env_ipc_receiving = 1;
    curenv->env_ipc_from = 0;
    curenv->env_ipc_value = 0;
    curenv->env_ipc_dstva = dstva;
    curenv->env_status = ENV_NOT_RUNNABLE;
    curenv->env_tf.regs[2] = 0;
    sys_yield();
    return 0;
}

static MOS_UNUSED int sys_ipc_can_send(int sysno, uint32_t envid, uint32_t value,
                                       uint32_t srcva, uint32_t perm)
{
    struct Env *e = NULL;
    int r;
    if (srcva >= UTOP) { return -E_INVAL; }
    if (curenv == NULL || !env_in_range(curenv)) { return -E_BAD_ENV; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    if (!e->env_ipc_receiving) { return -E_IPC_NOT_RECV; }
    if (srcva != 0) {
        r = sys_mem_map(sysno, curenv->env_id, srcva, envid, e->env_ipc_dstva, perm);
        if (r != 0) { return r; }
    }
    e->env_ipc_value = value;
    e->env_ipc_from = curenv->env_id;
    e->env_ipc_receiving = 0;
    e->env_status = ENV_RUNNABLE;
    return 0;
}
/* ---- 平台提供的 lib/syscall_all.c sys_env_alloc（实验 4.8 考查时排除本片段） ----
 *
 * 【宿主环境说明】题面 4.8 未给签名，按 MOS 公开语义取 int sys_env_alloc(int sysno)。
 *
 * MOS 公开语义（fork 的第一步）：
 *   Step 1 env_alloc(&e, curenv->env_id) 申请一个 PCB，父进程就是当前进程；
 *          失败原样上抛（-E_NO_FREE_ENV / -E_NO_MEM）；
 *   Step 2 e->env_tf = curenv->env_tf —— 整体复制父进程现场，
 *          子进程因此从与父进程相同的位置继续执行；
 *   Step 3 e->env_tf.regs[2] = 0 —— $v0 是返回值寄存器，
 *          子进程看到 fork 返回 0，父进程看到子进程 envid，这是 fork 的分叉点；
 *   Step 4 e->env_status = ENV_NOT_RUNNABLE —— 新进程还不能被调度，
 *          必须等父进程把地址空间复制完（duppage）再显式唤醒；
 *   Step 5 返回 e->env_id（不是下标，是带生成号的完整标识）。
 * 本函数【不】把子进程挂进就绪链表，也【不】复制任何用户页。 */

static MOS_UNUSED int sys_env_alloc(int sysno)
{
    struct Env *e = NULL;
    int r;
    (void)sysno;
    if (curenv == NULL || !env_in_range(curenv)) { return -E_BAD_ENV; }
    r = env_alloc(&e, curenv->env_id);
    if (r != 0) { return r; }
    e->env_tf = curenv->env_tf;
    e->env_tf.regs[2] = 0;
    e->env_status = ENV_NOT_RUNNABLE;
    return (int)e->env_id;
}
/* ---- 平台提供的 lib/syscall_all.c sys_set_pgfault_handler（实验 4.12 考查时排除） ----
 *
 * 【宿主环境说明】题面 4.12 未给签名，按 MOS 公开语义取
 * int sys_set_pgfault_handler(int sysno, u_int envid, u_int func, u_int xstacktop)。
 *
 * 题面 4.12 实验任务 2 逐条要求：
 *   「检查传入的处理函数指针是否有效」-> func 必须非 0 且落在用户空间 [0, UTOP)；
 *   「调用 syscall_mem_alloc 为处理函数分配内存」-> 为异常处理栈申请一页，
 *       映射在 xstacktop - BY2PG，权限 PTE_V | PTE_R（栈必须可写）；
 *   「设置 env_pgfault_handler 字段为传入的处理函数」-> e->env_pgfault_handler = func。
 * 步骤顺序：先校验参数 -> envid2env -> 写 handler -> 申请异常栈页，错误原样上抛。
 *
 * 宿主 PCB 未设 env_xstacktop 字段：真实 MOS 用它给陷阱入口汇编切换异常栈，
 * 而那段汇编属实验 4.1（Tier B 本地构建），第四章 14 个可判题函数都不读它，
 * 故按 YAGNI 不加字段，xstacktop 只用于算出要申请的那一页。 */

static MOS_UNUSED int sys_set_pgfault_handler(int sysno, uint32_t envid,
                                              uint32_t func, uint32_t xstacktop)
{
    struct Env *e = NULL;
    int r;
    if (func == 0 || func >= UTOP) { return -E_INVAL; }
    if (xstacktop < BY2PG || xstacktop > UTOP) { return -E_INVAL; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    e->env_pgfault_handler = func;
    return sys_mem_alloc(sysno, envid, xstacktop - BY2PG, PTE_V | PTE_R);
}
/* ---- 平台提供的 lib/syscall_all.c sys_set_env_status（实验 4.14 考查时排除） ----
 *
 * 【宿主环境说明】题面 4.14 未给签名，按 MOS 公开语义取
 * int sys_set_env_status(int sysno, u_int envid, u_int status)。
 *
 * 题面 4.14 实验任务 2 逐条要求：
 *   「检查传入的进程状态是否有效」-> 只接受 ENV_RUNNABLE / ENV_NOT_RUNNABLE，
 *       ENV_FREE 不能由用户设置（销毁进程是另一条路径），其余值 -> -E_INVAL；
 *   「更新进程控制块中的 env_status 字段」；
 *   「如果状态为 RUNNABLE，将进程加入调度链表」-> 头插 env_sched_list。
 * 入链前用 in_sched_list 判重，重复设置为 RUNNABLE 不得把同一 PCB 挂两次
 * （否则就绪链表成环，sched_yield 会死循环）。
 * 置为 NOT_RUNNABLE 时【不】主动摘链：题面没有这条要求，且 sched_yield 本身
 * 只挑 ENV_RUNNABLE 的进程，留在链上的阻塞进程会被自然跳过。 */

static MOS_UNUSED int sys_set_env_status(int sysno, uint32_t envid, uint32_t status)
{
    struct Env *e = NULL;
    int r;
    (void)sysno;
    if (status != ENV_RUNNABLE && status != ENV_NOT_RUNNABLE) { return -E_INVAL; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    e->env_status = status;
    if (status == ENV_RUNNABLE && !in_sched_list(e)) {
        LIST_INSERT_HEAD(&env_sched_list, e, env_sched_link);
    }
    return 0;
}
/* ---- 平台提供的 lib/syscall_all.c sys_call_table[]（实验 4.2 一并考查） ----
 * 真实 MOS 就是一张 void * 数组，用系统调用号作下标（C99 指定初始化器）。
 * 0 / 6 / 7 号槽位保持 NULL：第四章只要求 9 个系统调用，
 * handle_sys 必须把这些空槽位判成 -E_NO_SYS，而不是跳进去执行。
 * SYS_yield 槽位放的是宿主 glue sys_yield_entry（理由见 env-sysno 段）。
 * CH4_USER_DRIVER（用户态实验）下 yield / IPC 三段被裁掉，对应槽位留 NULL。 */

#ifndef CH4_USER_DRIVER
/* 宿主 glue：真实 MOS 的 sys_yield 返回 void，而本表统一用 syscall_fn（返回 int）。
 * 适配层放在这里而不是 sys-yield 段，这样实验 4.6 排除 sys-yield 时只挖掉学生要写的
 * sys_yield 本体，表与 glue 仍由平台提供。 */
static MOS_UNUSED int sys_yield_entry(int sysno, uint32_t a0, uint32_t a1,
                                      uint32_t a2, uint32_t a3, uint32_t a4)
{
    (void)sysno; (void)a0; (void)a1; (void)a2; (void)a3; (void)a4;
    sys_yield();
    return 0;
}
#endif

static MOS_UNUSED void *sys_call_table[SYS_MAX] = {
    [SYS_mem_alloc]           = sys_mem_alloc,
    [SYS_mem_map]             = sys_mem_map,
    [SYS_mem_unmap]           = sys_mem_unmap,
    [SYS_env_alloc]           = sys_env_alloc,
    [SYS_set_env_status]      = sys_set_env_status,
#ifndef CH4_USER_DRIVER
    [SYS_ipc_recv]            = sys_ipc_recv,
    [SYS_ipc_can_send]        = sys_ipc_can_send,
#endif
    [SYS_set_pgfault_handler] = sys_set_pgfault_handler,
#ifndef CH4_USER_DRIVER
    [SYS_yield]               = sys_yield_entry,
#endif
};
/* ---- 平台提供的内核系统调用入口 handle_sys（实验 4.2 考查时排除本片段） ----
 * MOS 公开语义：
 *   Step 1 现场在 KERNEL_SP（宿主等价 &kernsp_tf），$v0 = regs[2] 是系统调用号；
 *   Step 2 号为 0 / >= SYS_MAX / 对应槽位为 NULL -> regs[2] = -E_NO_SYS 并返回；
 *   Step 3 否则把槽位强转成 syscall_fn，按 MIPS o32 约定传
 *          sysno 与 $a0..$a3 + 栈上第一个参数，即 regs[4..8]；
 *   Step 4 返回值写回 regs[2]（$v0），用户态 msyscall 从这里取结果。
 * 【不做】cp0_epc += 4：跳过 syscall 指令属于陷阱返回路径（实验 4.1，Tier B），
 * 题面 4.2 只要求「解析系统调用号并调用相应的内核函数」。 */

static MOS_UNUSED void handle_sys(void)
{
    struct Trapframe *tf = KERNEL_SP;
    uint32_t sysno = tf->regs[2];
    if (sysno == 0 || sysno >= SYS_MAX || sys_call_table[sysno] == NULL) {
        tf->regs[2] = (uint32_t)(-E_NO_SYS);
        return;
    }
    tf->regs[2] = (uint32_t)((syscall_fn)sys_call_table[sysno])(
        (int)sysno, tf->regs[4], tf->regs[5], tf->regs[6], tf->regs[7], tf->regs[8]);
}
static int sys_mem_map(int sysno, uint32_t srcid, uint32_t srcva,
                       uint32_t dstid, uint32_t dstva, uint32_t perm)
{
    struct Env *src = NULL;
    struct Env *dst = NULL;
    struct Page *p;
    Pte *pte = NULL;
    int r;
    (void)sysno;
    if (srcva >= UTOP || dstva >= UTOP) { return -E_INVAL; }
    r = envid2env(srcid, &src, 0);
    if (r != 0) { return r; }
    r = envid2env(dstid, &dst, 0);
    if (r != 0) { return r; }
    p = page_lookup(src->env_pgdir, srcva, &pte);
    if (p == NULL) { return -E_INVAL; }
    return page_insert(dst->env_pgdir, p, dstva, perm);
}/* ===== 填写区结束 ===== */
/* ---- 第四章共享检查驱动（平台提供，学生不填写） ----
 *
 * stdin：第一行 np fm（物理页数 1..NPAGE_MAX、空闲内存上界字节数），第二行操作条数
 * n（0..MAXOPS），随后 n 行每行一个操作符。完整操作符表、门控宏与页账本见
 * artifacts/experiment-judge/ch4/protocol.md 及本实验 content.md「检查驱动与输入协议」。
 * 骨架：I A U S R P G Q O T N J E H L F C 恒有；K 除 CH4_TRAP_DRIVER 外恒有；
 * B/M 需 CH4_TRAP_DRIVER；Y/Z 在非用户态是事件日志；用户态下 W 随 CH4_USER_DRIVER、
 * V/Y 需 CH4_FORK_DRIVER（Y 改为子进程分支夹具）、D 需 CH4_DUPPAGE_DRIVER、
 * X/Z 需 CH4_PGFAULT_DRIVER。未启用的操作符按未知输入拒绝。
 *
 * 边界原则（harness-spec §6）：任何畸形/截断/越界输入都必须以退出码 0 结束，
 * 否则沙箱会把「答案错误」误判成 Runtime Error。故所有 scanf 都校验返回值，
 * 所有下标/地址都先做范围检查；不满足前置条件的输入直接 return 0，绝不打印伪造结果。
 */

#define MAXOPS 96

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
            printf("A %d %d %u\n", r, env_idx(e), env_in_range(e) ? e->env_id : 0u);
        } else if (op == 'U') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            curenv = &envs[idx];
#ifdef CH4_USER_DRIVER
            env = &envs[idx];
#endif
            printf("U %u\n", idx);
        } else if (op == 'S') {
            uint32_t idx = 0, st = 0;
            if (scanf("%u %u", &idx, &st) != 2 || idx >= NENV || st > 2u) { return 0; }
            envs[idx].env_status = st;
            printf("S %u %u\n", idx, st);
        } else if (op == 'R') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            if (!in_sched_list(&envs[idx])) {
                LIST_INSERT_HEAD(&env_sched_list, &envs[idx], env_sched_link);
            }
            printf("R %u\n", idx);
        } else if (op == 'P') {
            uint32_t idx = 0, va = 0, perm = 0;
            struct Page *p = NULL;
            int r;
            if (scanf("%u %u %u", &idx, &va, &perm) != 3 || idx >= NENV || va >= UTOP) { return 0; }
            if (!pgdir_ok(envs[idx].env_pgdir)) { printf("P -3 4294967295\n"); continue; }
            r = page_alloc(&p);
            if (r == 0) { r = page_insert(envs[idx].env_pgdir, p, va, perm); }
            printf("P %d %u\n", r, (r == 0) ? page2pa(p) : ~0u);
#ifdef CH4_TRAP_DRIVER
        } else if (op == 'B') {
            uint32_t idx = 0, va = 0, perm = 0;
            Pte *pte = NULL;
            int r;
            if (scanf("%u %u %u", &idx, &va, &perm) != 3 || idx >= NENV || va >= UTOP) { return 0; }
            if (!pgdir_ok(envs[idx].env_pgdir)) { printf("B -3\n"); continue; }
            r = pgdir_walk(envs[idx].env_pgdir, va, 1, &pte);
            if (r != 0 || pte == NULL) { printf("B %d\n", r); continue; }
            *pte = (Pte)(perm & 0xFFFu);
            printf("B 0\n");
#endif
#ifndef CH4_TRAP_DRIVER
        } else if (op == 'K') {
            uint32_t s = 0, a0 = 0, a1 = 0, a2 = 0, a3 = 0, a4 = 0;
            if (scanf("%u %u %u %u %u %u", &s, &a0, &a1, &a2, &a3, &a4) != 6) { return 0; }
            /* 真实内核里 handle_sys 只在进程上下文中被调用（curenv 一定非空）；
             * 多个 sys_* 会解引用 curenv，驱动在输入边界拒绝无现场的调用。 */
            if (curenv == NULL) { return 0; }
            kernsp_tf.regs[2] = s;
            kernsp_tf.regs[4] = a0;
            kernsp_tf.regs[5] = a1;
            kernsp_tf.regs[6] = a2;
            kernsp_tf.regs[7] = a3;
            kernsp_tf.regs[8] = a4;
            handle_sys();
            printf("K %d\n", (int)kernsp_tf.regs[2]);
#endif
        } else if (op == 'G') {
            printf("G %d\n", (int)kernsp_tf.regs[2]);
        } else if (op == 'Q') {
            uint32_t idx = 0, va = 0;
            if (scanf("%u %u", &idx, &va) != 2 || idx >= NENV) { return 0; }
            printf("Q %u\n", env_va2pa(envs[idx].env_pgdir, va));
        } else if (op == 'O') {
            uint32_t idx = 0, va = 0;
            if (scanf("%u %u", &idx, &va) != 2 || idx >= NENV) { return 0; }
            printf("O %u\n", env_pte(envs[idx].env_pgdir, va));
        } else if (op == 'T') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("T %u %u %u %u %u %u\n", idx, envs[idx].env_id, envs[idx].env_parent_id,
                   envs[idx].env_status, envs[idx].env_pri, envs[idx].env_cr3);
        } else if (op == 'N') {
            /* 给 PCB 现场写可辨认的 cp0_epc 与 $v0，检验现场整体复制与子进程 $v0 归零 */
            uint32_t idx = 0, epc = 0, v0 = 0;
            if (scanf("%u %u %u", &idx, &epc, &v0) != 3 || idx >= NENV) { return 0; }
            envs[idx].env_tf.cp0_epc = epc;
            envs[idx].env_tf.regs[2] = v0;
            printf("N %u %u %u\n", idx, epc, v0);
        } else if (op == 'J') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("J %u %u %u %u %u\n", idx, envs[idx].env_tf.cp0_epc,
                   envs[idx].env_tf.regs[2], envs[idx].env_tf.regs[29],
                   envs[idx].env_tf.cp0_status);
        } else if (op == 'E') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("E %u %u %u %u %u %u\n", idx, envs[idx].env_pgfault_handler,
                   envs[idx].env_ipc_receiving, envs[idx].env_ipc_dstva,
                   envs[idx].env_ipc_from, envs[idx].env_ipc_value);
#ifdef CH4_TRAP_DRIVER
        } else if (op == 'M') {
            uint32_t va = 0;
            if (scanf("%u", &va) != 1) { return 0; }
            timestack_tf.cp0_badvaddr = va;
            page_fault_handler(TIMESTACK);
            printf("M %u\n", (curenv != NULL && env_in_range(curenv))
                   ? env_pte(curenv->env_pgdir, va) : ~0u);
#endif
#ifdef CH4_USER_DRIVER
#ifdef CH4_FORK_DRIVER
        } else if (op == 'V') {
            int r;
            /* fork 第一步就是 syscall_env_alloc，它会解引用 curenv */
            if (curenv == NULL || env == NULL) { return 0; }
            r = fork();
            printf("V %d %d %d\n", r, r > 0 ? (int)ENVX((uint32_t)r) : -1, env_idx(env));
        } else if (op == 'Y') {
            int r;
            if (curenv == NULL || env == NULL) { return 0; }
            /* 模拟子进程带 $v0=0 从 fork 返回。env 先置 NULL 作哨兵：
             * 子进程分支必须把它改回自己的 PCB，漏写就会留下 -1。 */
            env = NULL;
            fork_child_stub = 1;
            r = fork();
            fork_child_stub = 0;
            printf("Y %d %d\n", r, env_idx(env));
#endif
#ifdef CH4_DUPPAGE_DRIVER
        } else if (op == 'D') {
            uint32_t envid = 0, pn = 0;
            if (scanf("%u %u", &envid, &pn) != 2) { return 0; }
            if (env == NULL || !env_in_range(env) || pn >= VPN(USTACKTOP)) { return 0; }
            duppage(envid, pn);
            printf("D %u %u\n", user_pte(pn << 12),
                   env_pte(envs[ENVX(envid)].env_pgdir, pn << 12));
#endif
#ifdef CH4_PGFAULT_DRIVER
        } else if (op == 'X') {
            uint32_t va = 0;
            if (scanf("%u", &va) != 1) { return 0; }
            if (env == NULL || !env_in_range(env)) { return 0; }
            pgfault(va);
            printf("X %u\n", user_pte(va));
        } else if (op == 'Z') {
            uint32_t va = 0;
            if (scanf("%u", &va) != 1) { return 0; }
            dump_user_page(va);
#endif
        } else if (op == 'W') {
            uint32_t dst = 0, src = 0, len = 0;
            if (scanf("%u %u %u", &dst, &src, &len) != 3 || len > BY2PG) { return 0; }
            if (env == NULL || !env_in_range(env)) { return 0; }
            printf("W %d\n", user_memcpy(dst, src, len));
#endif
        } else if (op == 'H') {
            struct Env *e = LIST_FIRST(&env_sched_list);
            printf("H %d\n", env_idx(e));
        } else if (op == 'L') {
            printf("L ");
            print_env_list(&env_sched_list, 1);
        } else if (op == 'F') {
            printf("F ");
            print_env_list(&env_free_list, 0);
        } else if (op == 'C') {
            printf("C %d\n", count_free_pages());
#ifndef CH4_USER_DRIVER
        } else if (op == 'Y') {
            int k, lim = rec_n < MAXREC ? rec_n : MAXREC;
            printf("Y %d\n", rec_n);
            for (k = 0; k < lim; k++) { printf("%d %u %d\n", rec_kind[k], rec_arg[k], rec_cur[k]); }
        } else if (op == 'Z') {
            rec_n = 0;
            printf("Z\n");
#endif
        } else {
            return 0;
        }
    }
    return 0;
}