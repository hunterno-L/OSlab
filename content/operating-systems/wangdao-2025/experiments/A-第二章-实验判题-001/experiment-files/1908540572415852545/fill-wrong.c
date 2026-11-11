/* ===== 学生填写区：实现 mm/pmap.c 的 page_insert() 与 page_remove() ===== */

/* 典型错误：漏掉「已映射的就是 pp」这条提前返回分支，
 * 于是同一页重复 insert 到同一 va 时 pp_ref 被重复递增。
 * 之后 page_remove 一次减不到 0，页永远回不到空闲链表（内存泄漏）。 */
static int page_insert(Pde *pgdir, struct Page *pp, uint32_t va, uint32_t perm)
{
    uint32_t PERM = perm | PTE_V;
    Pte *pgtable_entry = NULL;
    pgdir_walk(pgdir, va, 0, &pgtable_entry);
    if (pgtable_entry != NULL && (*pgtable_entry & PTE_V) != 0) {
        if (pa2page(*pgtable_entry) != pp) {
            page_remove(pgdir, va);
        }
    }
    tlb_invalidate(pgdir, va);
    if (pgdir_walk(pgdir, va, 1, &pgtable_entry) < 0) {
        return -E_NO_MEM;
    }
    *pgtable_entry = page2pa(pp) | PERM;
    pp->pp_ref++;
    return 0;
}

static void page_remove(Pde *pgdir, uint32_t va)
{
    Pte *pagetable_entry = NULL;
    struct Page *ppage;
    ppage = page_lookup(pgdir, va, &pagetable_entry);
    if (ppage == NULL) {
        return;
    }
    ppage->pp_ref--;
    if (ppage->pp_ref == 0) {
        page_free(ppage);
    }
    *pagetable_entry = 0;
    tlb_invalidate(pgdir, va);
}