/* ===== 学生填写区：实现 mm/pmap.c 的 page_insert() 与 page_remove() ===== */

/* 与真实 mm/pmap.c 逐 Step 对应。 */
static int page_insert(Pde *pgdir, struct Page *pp, uint32_t va, uint32_t perm)
{
    uint32_t PERM = perm | PTE_V;
    Pte *pgtable_entry = NULL;
    /* Step 1：先看该 va 当前映射到哪一页 */
    pgdir_walk(pgdir, va, 0, &pgtable_entry);
    if (pgtable_entry != NULL && (*pgtable_entry & PTE_V) != 0) {
        if (pa2page(*pgtable_entry) != pp) {
            page_remove(pgdir, va);
        } else {
            /* 同一页重复映射到同一 va：只重写权限，不再递增 pp_ref */
            tlb_invalidate(pgdir, va);
            *pgtable_entry = page2pa(pp) | PERM;
            return 0;
        }
    }
    /* Step 2：刷新 TLB */
    tlb_invalidate(pgdir, va);
    /* Step 3.1：必要时新建页表，失败返回 -E_NO_MEM */
    if (pgdir_walk(pgdir, va, 1, &pgtable_entry) < 0) {
        return -E_NO_MEM;
    }
    /* Step 3.2：写页表项并递增引用计数 */
    *pgtable_entry = page2pa(pp) | PERM;
    pp->pp_ref++;
    return 0;
}

static void page_remove(Pde *pgdir, uint32_t va)
{
    Pte *pagetable_entry = NULL;
    struct Page *ppage;
    /* Step 1：未映射则直接返回 */
    ppage = page_lookup(pgdir, va, &pagetable_entry);
    if (ppage == NULL) {
        return;
    }
    /* Step 2：引用减一，归零才回收 */
    ppage->pp_ref--;
    if (ppage->pp_ref == 0) {
        page_free(ppage);
    }
    /* Step 3：清页表项并刷新 TLB */
    *pagetable_entry = 0;
    tlb_invalidate(pgdir, va);
}