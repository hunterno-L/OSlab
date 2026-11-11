static int sys_mem_alloc(int sysno, uint32_t envid, uint32_t va, uint32_t perm)
{
    struct Env *e = NULL;
    struct Page *p = NULL;
    int r;
    (void)sysno;
    perm |= PTE_V;
    if (va >= UTOP) { return -E_INVAL; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    r = page_alloc(&p);
    if (r != 0) { return r; }
    r = page_insert(e->env_pgdir, p, va, perm);
    return r;
}