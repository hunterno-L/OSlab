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
/* ===== 学生填写区：实现 mm/pmap.c 的 boot_pgdir_walk() 与 pgdir_walk() ===== */

/* 启动期页表步行：页表不存在且 create==1 时用 alloc(BY2PG, BY2PG, 1) 新建，
 * 并把页目录项写成 PTE_ADDR(PADDR(pgtable)) | PTE_V；
 * 页表不存在且 create==0 时返回 NULL；否则返回 &pgtable[PTX(va)]。 */
static Pte *boot_pgdir_walk(Pde *pgdir, uint32_t va, int create)
{
    /* finish your code here */
    (void)pgdir; (void)va; (void)create;
    return NULL;
}

/* 运行期页表步行：与 boot_pgdir_walk 同构，但新页表来自 page_alloc，
 * 且必须 ppage->pp_ref++（页目录本身持有对该页的一次引用）。
 * 内存不足返回 -E_NO_MEM；create==0 且页表无效时 *ppte = NULL 并返回 0。 */
static int pgdir_walk(Pde *pgdir, uint32_t va, int create, Pte **ppte)
{
    /* finish your code here */
    (void)pgdir; (void)va; (void)create;
    *ppte = NULL;
    return 0;
}

/* ===== 填写区结束 ===== */

#define MAXOPS 64
#define VA_LIMIT 0x04000000u

/* 驱动自有：把页表项指针拆成「页表物理地址 + 页表内下标」。
 * 本环境中页表总是页对齐的，因此低 12 位就是 PTX * 4。 */
static void describe_pte(Pte *p, int *table_pa, int *ptx)
{
    uint32_t off;
    *table_pa = -1;
    *ptx = -1;
    if (!pte_in_range(p)) { return; }
    off = (uint32_t)((unsigned char *)p - phys);
    *table_pa = (int)(off & ~0xFFFu);
    *ptx = (int)((off & 0xFFFu) / (uint32_t)sizeof(Pte));
}

int main(void)
{
    uint32_t np = 0, fm = 0;
    int n = 0, i;
    if (scanf("%u %u", &np, &fm) != 2) { return 0; }
    if (np == 0 || np > NPAGE_MAX || fm > 65536u || fm > np * BY2PG) { return 0; }
    reset_state(np, fm);
    page_init();
    if (scanf("%d", &n) != 1 || n < 0 || n > MAXOPS) { return 0; }
    for (i = 0; i < n; i++) {
        char op = 0;
        uint32_t va = 0;
        int create = 0, ta = -1, tx = -1;
        if (scanf(" %c", &op) != 1) { break; }
        if (op == 'B') {
            Pte *p;
            if (scanf("%u %d", &va, &create) != 2) { return 0; }
            if (va >= VA_LIMIT || create < 0 || create > 1) { return 0; }
            p = boot_pgdir_walk(boot_pgdir, va, create);
            describe_pte(p, &ta, &tx);
            printf("B %d %d\n", ta, tx);
        } else if (op == 'W') {
            Pte *p = NULL;
            int ret;
            if (scanf("%u %d", &va, &create) != 2) { return 0; }
            if (va >= VA_LIMIT || create < 0 || create > 1) { return 0; }
            ret = pgdir_walk(boot_pgdir, va, create, &p);
            describe_pte(p, &ta, &tx);
            printf("W %d %d %d\n", ret, ta, tx);
        } else if (op == 'Q') {
            uint32_t pidx = 0;
            if (scanf("%u", &pidx) != 1 || pidx >= np) { return 0; }
            printf("Q %u %d\n", pidx, (int)pages[pidx].pp_ref);
        } else {
            return 0;
        }
    }
    print_free_list();
    return 0;
}