static int fork(void)
{
    uint32_t envid, pn;
    envid = (uint32_t)syscall_env_alloc();
    if (envid == 0) {
        env = &envs[ENVX(envid)];
        return 0;
    }
    for (pn = 0; pn < VPN(USTACKTOP); pn++) {
        if (user_pte(pn << 12) & PTE_V) { duppage(envid, pn); }
    }
    syscall_mem_alloc(envid, UXSTACKTOP - BY2PG, PTE_V | PTE_R);
    syscall_set_pgfault_handler(envid, PGFAULT_ENTRY, UXSTACKTOP);
    syscall_set_env_status(envid, ENV_RUNNABLE);
    return (int)envid;
}