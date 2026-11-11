/* 典型错误：重新挑选时从【链表头】开始找，而不是从 cur 的下一个开始。
 * 单看一次调度似乎没问题（确实挑到了一个 RUNNABLE 进程），但时间片用尽后
 * 又挑回链表头那个进程，于是链表头独占 CPU、其余进程永远饿死 —— 轮转失效。
 * Y 日志会显示每个滴答的 curenv 下标恒为链表头，而正确实现应按 pri 轮流切换。 */
static void sched_yield(void)
{
    static struct Env *cur = NULL;
    static int left = 0;
    struct Env *e;
    int steps;

    if (cur == NULL || left == 0 || !env_in_range(cur) || cur->env_status != ENV_RUNNABLE) {
        e = LIST_FIRST(&env_sched_list);
        steps = 0;
        while (steps < (int)NENV) {
            if (e == NULL) { e = LIST_FIRST(&env_sched_list); }
            if (e == NULL) { return; }
            if (!env_in_range(e)) { return; }
            if (e->env_status == ENV_RUNNABLE) { break; }
            e = LIST_NEXT(e, env_sched_link);
            steps++;
        }
        if (steps >= (int)NENV || e == NULL || !env_in_range(e)) { return; }
        cur = e;
        left = (cur->env_pri == 0) ? 1 : (int)cur->env_pri;
    }
    left--;
    env_run(cur);
}

/* ===== 填写区结束 ===== */