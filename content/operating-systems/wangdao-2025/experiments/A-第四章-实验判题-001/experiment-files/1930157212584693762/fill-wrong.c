static int sys_env_alloc(int sysno)
{
    struct Env *e = NULL;
    int r;
    (void)sysno;
    if (curenv == NULL || !env_in_range(curenv)) { return -E_BAD_ENV; }
    r = env_alloc(&e, curenv->env_id);
    if (r != 0) { return r; }
    e->env_tf = curenv->env_tf;
    e->env_tf.regs[2] = 0;
    e->env_status = ENV_RUNNABLE;
    return (int)e->env_id;
}