/* 操作系统实验 3.1：启动期内存初始化。
 * pages、envs 与 boot_pgdir 为指针，由 bump 分配器 alloc() 分配，
 * 与使用静态页数组的运行时分配模型区分。
 *
 * 等价适配（语义不变）：
 *   1. u_int/u_long -> uint32_t，保证 Windows(LLP64) 与 Linux(LP64) 判题结果一致；
 *   2. 物理内存用一维字节数组 phys[] 模拟，物理地址就是数组下标，
 *      于是 KADDR(pa)=phys+pa、PADDR(kva)=kva-phys，与真实内核线性映射语义相同；
 *   3. panic 不终止进程（沙箱把非零退出判为 Runtime Error）；
 *   4. 真实 alloc/boot_pgdir_walk 在内存耗尽时 panic 后继续执行，宿主 panic 是空操作，
 *      因此这里改为返回 NULL 并由调用方守卫，避免学生写错时段错误；
 *   5. 去掉 mips_vm_init 里的启动期 printf，改由检查驱动打印确定性观测量。
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
#define ROUNDDOWN(a, n) ((((uint32_t)(a)) / (n)) * (n))
#define bzero(p, n)   memset((p), 0, (n))

typedef uint32_t Pde;
typedef uint32_t Pte;

/* include/queue.h：struct Page / struct Env 的链表字段需要这两个宏 */
#define LIST_HEAD(name, type) \
    struct name { struct type *lh_first; }

#define LIST_ENTRY(type) \
    struct { struct type *le_next; struct type **le_prev; }

struct Page { int32_t pp_ref; LIST_ENTRY(Page) pp_link; };
LIST_HEAD(Page_list, Page);

#define NPAGE_MAX 256u

static MOS_UNUSED unsigned char phys[NPAGE_MAX * BY2PG];
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
/* ---- 3.1 启动期环境（续）：全局指针、bump 分配器、两级页表建立 ---- */

static MOS_UNUSED struct Page *pages;      /* 由 mips_vm_init Step 2 赋值 */
static MOS_UNUSED struct Env *envs;        /* 由 mips_vm_init Step 3 赋值 */
static MOS_UNUSED Pde *boot_pgdir;         /* 由 mips_vm_init Step 1 赋值 */
static MOS_UNUSED uint32_t npage;
static MOS_UNUSED uint32_t freemem;
static MOS_UNUSED uint32_t maxpa;
static MOS_UNUSED uint32_t mCONTEXT;       /* 真实内核存页目录地址，宿主存物理地址 */

#define KADDR(pa)  ((void *)(phys + (uint32_t)(pa)))
#define PADDR(kva) ((uint32_t)((unsigned char *)(kva) - phys))

static MOS_UNUSED void panic(const char *msg) { (void)msg; }

/* 真实 mm/pmap.c 第 47–80 行的 alloc()：启动期 bump 分配器。
 * 宿主适配：越界时返回 NULL 而不是 panic 后继续（panic 在宿主是空操作）。 */
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

/* 真实 mm/pmap.c 第 87–113 行的 boot_pgdir_walk()。
 * 宿主适配：alloc 失败返回 NULL（真实代码此处会 panic）。 */
static MOS_UNUSED Pte *boot_pgdir_walk(Pde *pgdir, uint32_t va, int create)
{
    Pde *pgdir_entryp;
    Pte *pgtable;
    if (pgdir == NULL) { return NULL; }
    pgdir_entryp = &pgdir[PDX(va)];
    pgtable = (Pte *)KADDR(PTE_ADDR(*pgdir_entryp));
    if (!(*pgdir_entryp & PTE_V) && create == 1) {
        pgtable = (Pte *)alloc(BY2PG, BY2PG, 1);
        if (pgtable == NULL) { return NULL; }
        *pgdir_entryp = PTE_ADDR(PADDR(pgtable)) | PTE_V;
    } else if (!(*pgdir_entryp & PTE_V) && create == 0) {
        return NULL;
    }
    return &pgtable[PTX(va)];
}

/* 真实 mm/pmap.c 第 123–140 行的 boot_map_segment()（实验 2.6 已考查，此处平台提供）。
 * 宿主适配：boot_pgdir_walk 返回 NULL 时提前返回，不写空指针。 */
static MOS_UNUSED void boot_map_segment(Pde *pgdir, uint32_t va, uint32_t size,
                                        uint32_t pa, int perm)
{
    uint32_t va_temp;
    Pte *pgtable_entry;
    if (size % BY2PG != 0) { return; }
    for (va_temp = va; va_temp < va + size; va_temp += BY2PG, pa += BY2PG) {
        pgtable_entry = boot_pgdir_walk(pgdir, va_temp, 1);
        if (pgtable_entry == NULL) { return; }
        *pgtable_entry = pa | (uint32_t)perm | PTE_V;
    }
}

/* ---- 驱动侧保护与观测量：全部不依赖学生实现 ---- */

static MOS_UNUSED int ptr_in_phys(const void *p)
{
    const unsigned char *b = (const unsigned char *)p;
    return b >= phys && b < phys + sizeof phys;
}

/* 学生可能把 envs/pages 留成 NULL 或写成野指针，先校验再算物理地址 */
static MOS_UNUSED uint32_t pa_of(const void *p)
{
    if (!ptr_in_phys(p)) { return ~0u; }
    return (uint32_t)((const unsigned char *)p - phys);
}

/* 驱动自有的地址翻译：boot_pgdir 可能为 NULL（学生没做 Step 1），
 * 页目录项里的页表物理地址可能越界（学生写坏），两种情形都返回 ~0。 */
static MOS_UNUSED uint32_t boot_va2pa(uint32_t va)
{
    Pde pde;
    uint32_t base;
    Pte pte;
    if (boot_pgdir == NULL || !ptr_in_phys(boot_pgdir)) { return ~0u; }
    pde = boot_pgdir[PDX(va)];
    if (!(pde & PTE_V)) { return ~0u; }
    base = PTE_ADDR(pde);
    if (base + BY2PG > (uint32_t)sizeof phys) { return ~0u; }
    memcpy(&pte, phys + base + PTX(va) * (uint32_t)sizeof(Pte), sizeof pte);
    if (!(pte & PTE_V)) { return ~0u; }
    return PTE_ADDR(pte);
}

/* 从 start 起连续有效映射的页数（上限 1024，防学生写出环状页表） */
static MOS_UNUSED uint32_t count_mapped(uint32_t start)
{
    uint32_t k;
    for (k = 0; k < 1024u; k++) {
        if (boot_va2pa(start + k * BY2PG) == ~0u) { break; }
    }
    return k;
}

/* va 处页表项的低 12 位权限；无映射返回 ~0 */
static MOS_UNUSED uint32_t pte_perm(uint32_t va)
{
    Pde pde;
    uint32_t base;
    Pte pte;
    if (boot_pgdir == NULL || !ptr_in_phys(boot_pgdir)) { return ~0u; }
    pde = boot_pgdir[PDX(va)];
    if (!(pde & PTE_V)) { return ~0u; }
    base = PTE_ADDR(pde);
    if (base + BY2PG > (uint32_t)sizeof phys) { return ~0u; }
    memcpy(&pte, phys + base + PTX(va) * (uint32_t)sizeof(Pte), sizeof pte);
    if (!(pte & PTE_V)) { return ~0u; }
    return pte & 0xFFFu;
}

static MOS_UNUSED void reset_vm(uint32_t np, uint32_t fm)
{
    npage = np;
    freemem = fm;
    maxpa = np * BY2PG;
    if (maxpa > (uint32_t)sizeof phys) { maxpa = (uint32_t)sizeof phys; }
    pages = NULL;
    envs = NULL;
    boot_pgdir = NULL;
    mCONTEXT = 0;
    memset(phys, 0, sizeof phys);
}
/* ===== 学生填写区：实现 mm/pmap.c 的 mips_vm_init()（为 envs 分配内存并映射 UENVS） ===== */

/* 典型错误解（用于验证判题能区分）：
 *   缺陷 1：Step 3 忘记 ROUND 到 BY2PG，直接把 NENV*sizeof(struct Env) 当 size。
 *           boot_map_segment 的 `size % BY2PG != 0` 守卫会整段返回，UENVS 一个页都没映射。
 *   缺陷 2：权限只给 PTE_V，漏了 PTE_R，用户态读 envs 会触发地址错误异常。 */
static void mips_vm_init(void)
{
    Pde *pgdir;
    uint32_t n;

    pgdir = (Pde *)alloc(BY2PG, BY2PG, 1);
    if (pgdir == NULL) { return; }
    mCONTEXT = PADDR(pgdir);
    boot_pgdir = pgdir;

    pages = (struct Page *)alloc(npage * (uint32_t)sizeof(struct Page), BY2PG, 1);
    if (pages == NULL) { return; }
    n = ROUND(npage * (uint32_t)sizeof(struct Page), BY2PG);
    boot_map_segment(pgdir, UPAGES, n, PADDR(pages), PTE_R);

    envs = (struct Env *)alloc(NENV * (uint32_t)sizeof(struct Env), BY2PG, 1);
    if (envs == NULL) { return; }
    n = NENV * (uint32_t)sizeof(struct Env);          /* 缺陷 1：没有 ROUND */
    boot_map_segment(pgdir, UENVS, n, PADDR(envs), PTE_V);  /* 缺陷 2：漏 PTE_R */
}

/* ===== 填写区结束 ===== *//* ===== 填写区结束 ===== */

#define MAXOPS 32

int main(void)
{
    uint32_t np = 0, fm = 0;
    int n = 0, i, did_v = 0;
    if (scanf("%u %u", &np, &fm) != 2) { return 0; }
    if (np == 0 || np > NPAGE_MAX || fm > 65536u) { return 0; }
    reset_vm(np, fm);
    if (scanf("%d", &n) != 1 || n < 0 || n > MAXOPS) { return 0; }
    for (i = 0; i < n; i++) {
        char op = 0;
        if (scanf(" %c", &op) != 1) { break; }
        if (op == 'V') {
            if (did_v) { printf("V SKIP\n"); continue; }
            did_v = 1;
            mips_vm_init();
            printf("V %u %u %u\n", freemem, mCONTEXT, pa_of(boot_pgdir));
        } else if (op == 'E') {
            uint32_t cnt = count_mapped(UENVS);
            uint32_t last = (cnt > 0) ? boot_va2pa(UENVS + (cnt - 1u) * BY2PG) : ~0u;
            printf("E %u %u %u %u %u\n", pa_of(envs), cnt, pte_perm(UENVS),
                   boot_va2pa(UENVS), last);
        } else if (op == 'P') {
            printf("P %u %u %u\n", pa_of(pages), count_mapped(UPAGES), pte_perm(UPAGES));
        } else if (op == 'Q') {
            uint32_t va = 0;
            if (scanf("%u", &va) != 1) { return 0; }
            printf("Q %u\n", boot_va2pa(va));
        } else {
            return 0;
        }
    }
    return 0;
}