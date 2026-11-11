/* ---- 平台提供的 lib/syscall_all.c sys_set_env_status（实验 4.14 考查时排除） ----
 *
 * 【宿主环境说明】题面 4.14 未给签名，按 MOS 公开语义取
 * int sys_set_env_status(int sysno, u_int envid, u_int status)。
 *
 * 题面 4.14 实验任务 2 逐条要求：
 *   「检查传入的进程状态是否有效」-> 只接受 ENV_RUNNABLE / ENV_NOT_RUNNABLE，
 *       ENV_FREE 不能由用户设置（销毁进程是另一条路径），其余值 -> -E_INVAL；
 *   「更新进程控制块中的 env_status 字段」；
 *   「如果状态为 RUNNABLE，将进程加入调度链表」-> 头插 env_sched_list。
 * 入链前用 in_sched_list 判重，重复设置为 RUNNABLE 不得把同一 PCB 挂两次
 * （否则就绪链表成环，sched_yield 会死循环）。
 * 置为 NOT_RUNNABLE 时【不】主动摘链：题面没有这条要求，且 sched_yield 本身
 * 只挑 ENV_RUNNABLE 的进程，留在链上的阻塞进程会被自然跳过。 */

static MOS_UNUSED int sys_set_env_status(int sysno, uint32_t envid, uint32_t status)
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