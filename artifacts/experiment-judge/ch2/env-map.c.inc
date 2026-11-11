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