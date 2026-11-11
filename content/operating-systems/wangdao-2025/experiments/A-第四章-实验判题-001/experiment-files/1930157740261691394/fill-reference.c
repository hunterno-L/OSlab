static int sys_set_pgfault_handler(int sysno, uint32_t envid,
                                   uint32_t func, uint32_t xstacktop)
{
    struct Env *e = NULL;
    int r;
    if (func == 0 || func >= UTOP) { return -E_INVAL; }
    if (xstacktop < BY2PG || xstacktop > UTOP) { return -E_INVAL; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    e->env_pgfault_handler = func;
    return sys_mem_alloc(sysno, envid, xstacktop - BY2PG, PTE_V | PTE_R);
}