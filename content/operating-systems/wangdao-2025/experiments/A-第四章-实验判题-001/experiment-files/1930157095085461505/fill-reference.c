static int sys_ipc_recv(int sysno, uint32_t dstva)
{
    (void)sysno;
    if (dstva >= UTOP) { return -E_INVAL; }
    if (curenv == NULL || !env_in_range(curenv)) { return -E_BAD_ENV; }
    curenv->env_ipc_receiving = 1;
    curenv->env_ipc_from = 0;
    curenv->env_ipc_value = 0;
    curenv->env_ipc_dstva = dstva;
    curenv->env_status = ENV_NOT_RUNNABLE;
    curenv->env_tf.regs[2] = 0;
    sys_yield();
    return 0;
}

static int sys_ipc_can_send(int sysno, uint32_t envid, uint32_t value,
                            uint32_t srcva, uint32_t perm)
{
    struct Env *e = NULL;
    int r;
    if (srcva >= UTOP) { return -E_INVAL; }
    if (curenv == NULL || !env_in_range(curenv)) { return -E_BAD_ENV; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    if (!e->env_ipc_receiving) { return -E_IPC_NOT_RECV; }
    if (srcva != 0) {
        r = sys_mem_map(sysno, curenv->env_id, srcva, envid, e->env_ipc_dstva, perm);
        if (r != 0) { return r; }
    }
    e->env_ipc_value = value;
    e->env_ipc_from = curenv->env_id;
    e->env_ipc_receiving = 0;
    e->env_status = ENV_RUNNABLE;
    return 0;
}