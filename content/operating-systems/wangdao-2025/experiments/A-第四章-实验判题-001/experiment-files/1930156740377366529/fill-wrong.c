static int sys_mem_map(int sysno, uint32_t srcid, uint32_t srcva,
                       uint32_t dstid, uint32_t dstva, uint32_t perm)
{
    struct Env *src = NULL;
    struct Env *dst = NULL;
    struct Page *p;
    Pte *pte = NULL;
    int r;
    (void)sysno;
    if (srcva >= UTOP || dstva >= UTOP) { return -E_INVAL; }
    r = envid2env(srcid, &src, 0);
    if (r != 0) { return r; }
    r = envid2env(dstid, &dst, 0);
    if (r != 0) { return r; }
    p = page_lookup(src->env_pgdir, srcva, &pte);
    if (p == NULL) { return -E_INVAL; }
    p->pp_ref++;
    return page_insert(dst->env_pgdir, p, dstva, perm);
}