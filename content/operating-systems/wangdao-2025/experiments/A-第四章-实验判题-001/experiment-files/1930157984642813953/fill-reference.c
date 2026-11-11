static int sys_set_env_status(int sysno, uint32_t envid, uint32_t status)
{
    struct Env *e = NULL;
    int r;
    (void)sysno;
    if (status != ENV_RUNNABLE && status != ENV_NOT_RUNNABLE) { return -E_INVAL; }
    r = envid2env(envid, &e, 0);
    if (r != 0) { return r; }
    e->env_status = status;
    if (status == ENV_RUNNABLE && !in_sched_list(e)) {
        LIST_INSERT_HEAD(&env_sched_list, e, env_sched_link);
    }
    return 0;
}