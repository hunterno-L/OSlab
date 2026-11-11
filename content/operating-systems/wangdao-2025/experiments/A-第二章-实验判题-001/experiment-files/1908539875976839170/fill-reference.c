/* ===== 学生填写区：实现 mm/pmap.c 的 boot_pgdir_walk() 与 pgdir_walk() ===== */

/* 与真实 mm/pmap.c 逐 Step 对应。 */
static Pte *boot_pgdir_walk(Pde *pgdir, uint32_t va, int create)
{
    /* Step 1：取页目录项，并把其中的物理地址换成可访问的页表指针 */
    Pde *pgdir_entryp = &pgdir[PDX(va)];
    Pte *pgtable = (Pte *)KADDR(PTE_ADDR(*pgdir_entryp));

    /* Step 2：页表不存在时按 create 决定新建还是放弃 */
    if (!(*pgdir_entryp & PTE_V) && create == 1) {
        pgtable = (Pte *)alloc(BY2PG, BY2PG, 1);
        if (pgtable == NULL) { return NULL; }
        *pgdir_entryp = PTE_ADDR(PADDR(pgtable)) | PTE_V;
    } else if (!(*pgdir_entryp & PTE_V) && create == 0) {
        return NULL;
    }

    /* Step 3：返回页表内对应下标的页表项 */
    return &pgtable[PTX(va)];
}

static int pgdir_walk(Pde *pgdir, uint32_t va, int create, Pte **ppte)
{
    Pde *pgdir_entryp = &pgdir[PDX(va)];
    Pte *pgtable = (Pte *)KADDR(PTE_ADDR(*pgdir_entryp));
    struct Page *ppage = NULL;
    int err_num;
    *ppte = NULL;
    if (!(*pgdir_entryp & PTE_V) && create == 1) {
        err_num = page_alloc(&ppage);
        if (err_num != 0) { return err_num; }
        /* 页目录持有该页的一次引用，必须计数 */
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