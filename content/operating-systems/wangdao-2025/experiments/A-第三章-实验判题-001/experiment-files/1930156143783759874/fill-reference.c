/* ===== 学生填写区：实现 lib/sched.c 的 sched_yield()（时钟中断里的时间片轮转调度） ===== */

/* 【宿主环境说明】工作区未随附 lib/sched.c 原文。以下实现按题面 3.14「基于时间片轮转算法、通过时钟
 * 中断触发、只有就绪状态参与、用链表管理就绪进程、按优先级调度」这五条要求，
 * 加 MOS/MIPS3000 公开语义构造，属等价示例，不是原文逐字转录。
 * 宿主把「优先级」具体化为 env_pri = 一轮能连续运行的时钟滴答数（填写区注释已给出算法）。
 *
 * 三个易错点，逐条对应题面：
 *   「时间片轮转」  → 重选时必须从 cur 的【下一个】开始环形查找，而不是从链表头开始；
 *                     从头开始会让链表头进程独占 CPU，其余进程饿死。
 *   「就绪状态」    → 只挑 env_status == ENV_RUNNABLE；一个都挑不到就直接 return，
 *                     绝不调用 env_run（没有可运行进程时切换现场是错的）。
 *   「优先级设置」  → env_pri 为 0 是合法值，必须钳到至少 1 个滴答，否则 left 恒为 0，
 *                     该进程每轮都被重新选中却永远跑不动，形成活锁。 */
static void sched_yield(void)
{
    static struct Env *cur = NULL;
    static int left = 0;
    struct Env *e;
    int steps;

    if (cur == NULL || left == 0 || !env_in_range(cur) || cur->env_status != ENV_RUNNABLE) {
        e = (cur == NULL || !env_in_range(cur)) ? LIST_FIRST(&env_sched_list)
                                               : LIST_NEXT(cur, env_sched_link);
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