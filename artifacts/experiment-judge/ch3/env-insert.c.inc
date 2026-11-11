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