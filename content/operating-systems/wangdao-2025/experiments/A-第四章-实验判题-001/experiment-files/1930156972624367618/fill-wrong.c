static void sys_yield(void)
{
    if (curenv != NULL) { curenv->env_status = ENV_NOT_RUNNABLE; }
    sched_yield();
}