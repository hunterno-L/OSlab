static int fork(void)
{
    uint32_t envid, pn;
    envid = (uint32_t)syscall_env_alloc();
    if (envid == 0) {
        env = &envs[ENVX(syscall_getenvid())];
        return 0;
    }
    for (pn = 0; pn < VPN(USTACKTOP); pn++) {
        if (user_pte(pn << 12) & PTE_V) { duppage(envid, pn); }
    }
    syscall_mem_alloc(0, UXSTACKTOP - BY2PG, PTE_V | PTE_R);
    syscall_set_pgfault_handler(0, PGFAULT_ENTRY, UXSTACKTOP);
    syscall_set_env_status(0, ENV_RUNNABLE);
    return (int)envid;
}