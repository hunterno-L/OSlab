static void page_fault_handler(struct Trapframe *tf)
{
    uint32_t va, perm;
    Pte *pte = NULL;
    struct Page *p = NULL;
    if (tf == NULL || curenv == NULL || !env_in_range(curenv)) { return; }
    if (!pgdir_ok(curenv->env_pgdir)) { return; }
    va = tf->cp0_badvaddr;
    if (va >= UTOP) { return; }
    if (pgdir_walk(curenv->env_pgdir, ROUNDDOWN(va, BY2PG), 0, &pte) != 0) { return; }
    if (pte == NULL || !pte_in_range(pte)) { return; }
    perm = *pte & 0xFFFu;
    if ((perm & PTE_V) == 0) { return; }
    if (PTE_ADDR(*pte) != 0) { return; }
    if (page_alloc(&p) != 0) { return; }
    *pte = page2pa(p) | perm;
    p->pp_ref++;
}