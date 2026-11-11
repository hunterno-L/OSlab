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