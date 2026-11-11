static int sys_mem_unmap(int sysno, uint32_t envid, uint32_t va)
{
    struct Env *e = NULL;
    Pte *pte = NULL;
    int r;
    (void)sysno;
    if (va >= UTOP) { return -E_INVAL; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    if (page_lookup(e->env_pgdir, va, &pte) == NULL) { return -E_INVAL; }
    page_remove(e->env_pgdir, va);
    return 0;
}