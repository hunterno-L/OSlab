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