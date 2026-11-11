/* ===== 学生填写区：实现 mm/pmap.c 的 page_insert() 与 page_remove() ===== */

/* 把物理页 pp 映射到虚拟地址 va，权限为 perm | PTE_V。
 * Step 1: 先 pgdir_walk(pgdir, va, 0, &pte)。若该 va 已有效映射：
 *         - 映射的就是 pp：只刷新 TLB 并重写页表项，直接返回 0，
 *           【不要】再次递增 pp_ref（同一页重复映射到同一 va 只算一次引用）；
 *         - 映射的是别的页：先 page_remove(pgdir, va) 释放旧映射。
 * Step 2: tlb_invalidate(pgdir, va)。
 * Step 3: pgdir_walk(pgdir, va, 1, &pte)，失败返回 -E_NO_MEM；
 *         成功则 *pte = page2pa(pp) | perm | PTE_V，并 pp->pp_ref++。 */
static int page_insert(Pde *pgdir, struct Page *pp, uint32_t va, uint32_t perm)
{
    /* finish your code here */
    (void)pgdir; (void)pp; (void)va; (void)perm;
    return -E_NO_MEM;
}

/* 解除 va 处的映射。
 * Step 1: page_lookup 取页与页表项，未映射则直接返回。
 * Step 2: pp_ref--，减到 0 时 page_free 回收该页。
 * Step 3: 页表项清 0 并 tlb_invalidate。 */
static void page_remove(Pde *pgdir, uint32_t va)
{
    /* finish your code here */
    (void)pgdir; (void)va;
}

/* ===== 填写区结束 ===== */

#define MAXOPS 48
#define VA_LIMIT 0x04000000u

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
        if (scanf(" %c", &op) != 1) { break; }
        if (op == 'I') {
            uint32_t pidx = 0, va = 0, perm = 0;
            int ret;
            if (scanf("%u %u %u", &pidx, &va, &perm) != 3) { return 0; }
            if (pidx >= np || va >= VA_LIMIT || perm > 0xFFFu) { return 0; }
            /* 驱动模拟「调用方已持有该物理页」：仅在该页仍挂在空闲链表上时
             * 摘下来并把 pp_ref 归零；已在用的页不动，否则重复 insert 的
             * 引用计数语义就被驱动自己抹掉了。 */
            if (in_free_list(&pages[pidx])) {
                LIST_REMOVE(&pages[pidx], pp_link);
                pages[pidx].pp_ref = 0;
            }
            ret = page_insert(boot_pgdir, &pages[pidx], va, perm);
            printf("I %d %u %d\n", ret, va2pa_safe(va), (int)pages[pidx].pp_ref);
        } else if (op == 'R') {
            uint32_t va = 0;
            int oldidx = -1, ref = 0;
            struct Page *old;
            if (scanf("%u", &va) != 1 || va >= VA_LIMIT) { return 0; }
            old = page_lookup(boot_pgdir, va, NULL);
            if (page_in_range(old)) { oldidx = (int)(old - pages); }
            page_remove(boot_pgdir, va);
            if (oldidx >= 0) { ref = (int)pages[oldidx].pp_ref; }
            printf("R %d %u %d\n", oldidx, va2pa_safe(va), ref);
        } else if (op == 'Q') {
            uint32_t va = 0;
            if (scanf("%u", &va) != 1 || va >= VA_LIMIT) { return 0; }
            printf("Q %u\n", va2pa_safe(va));
        } else if (op == 'P') {
            uint32_t pidx = 0;
            if (scanf("%u", &pidx) != 1 || pidx >= np) { return 0; }
            printf("P %u %d\n", pidx, (int)pages[pidx].pp_ref);
        } else {
            return 0;
        }
    }
    return 0;
}