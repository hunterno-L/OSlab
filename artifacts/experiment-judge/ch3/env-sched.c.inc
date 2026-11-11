/* ---- 平台提供的 lib/sched.c sched_yield（实验 3.14 考查时排除本片段） ----
 *
 * 【宿主环境说明】工作区未提供 lib/sched.c 原文。题面 3.14 要求
 * 「时间片轮转 + 优先级」「只有 ENV_RUNNABLE 参与」「用链表管理就绪进程」，
 * 以下按该文字 + MOS 公开语义构造等价实现：env_pri 即该进程一轮能连续跑的时钟滴答数。
 *
 * 算法（填写区注释里也会逐条给出，保证可判题）：
 *   静态游标 cur 与剩余时间片 left 跨调用保持；
 *   若 cur 为空 / left 为 0 / cur 已不是 ENV_RUNNABLE，则从 cur 的下一个开始
 *   在 env_sched_list 上环形查找第一个 ENV_RUNNABLE 的进程（最多 NENV 步），
 *   找不到就直接返回（不调用 env_run）；找到则 left = max(1, env_pri)；
 *   然后 left--，env_run(cur)。 */

static MOS_UNUSED void sched_yield(void)
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