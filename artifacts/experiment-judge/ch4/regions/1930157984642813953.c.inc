/* ===== 学生填写区：实现 lib/syscall_all.c 的 sys_set_env_status() ===== */

/* 题面 4.14 任务描述原文：
 *   「在 lib/syscall_all.c 中实现 sys_set_env_status 函数，该函数用于设置进程的状态，
 *     使其可以运行或处于其他状态。」
 * 实验任务 2「编写 sys_set_env_status 函数，实现设置进程状态的功能：检查传入的进程状态
 * 是否有效；更新进程控制块中的 env_status 字段；如果状态为 RUNNABLE，将进程加入调度
 * 链表」、实验任务 3「确保函数遵循系统调用规范，正确处理错误情况」。
 *
 * 【宿主环境说明】题面未给签名，工作区也未随附 lib/syscall_all.c 原文。以下签名与步骤按 MOS 公开语义构造，
 * 属等价示例而非原文转录；u_int 一律写成等宽的 uint32_t。
 *
 * MOS 公开语义，四步：
 *   Step 1 校验状态：只接受 ENV_RUNNABLE(2) 与 ENV_NOT_RUNNABLE(1)，
 *          其余值（含 ENV_FREE(0)）-> 返回 -E_INVAL；
 *   Step 2 envid2env(envid, &e, 0) 取 PCB，失败原样上抛（envid 为 0 表示当前进程）；
 *   Step 3 e->env_status = status；
 *   Step 4 status 为 ENV_RUNNABLE 且 e 尚不在就绪链表上时，
 *          LIST_INSERT_HEAD(&env_sched_list, e, env_sched_link) 头插；返回 0。
 *
 * 【关键契约一】入链前必须用 in_sched_list(e) 判重。重复把同一 PCB 头插会让就绪链表
 *   成环（BSD LIST_INSERT_HEAD 不重设 le_prev），sched_yield 随后就在环里打转。
 *   「唤醒一个已经在链上的进程」是 fork / IPC 的常态，必须幂等。
 * 【关键契约二】ENV_FREE 不能由用户设置：销毁进程是另一条路径，
 *   用户态把进程置成 FREE 会让 PCB 状态与空闲链表不一致。
 * 【关键契约三】置为 NOT_RUNNABLE 时【不】主动摘链：题面没有这条要求，
 *   且 sched_yield 只挑 ENV_RUNNABLE 的进程，留在链上的阻塞进程会被自然跳过。
 * 【关键契约四】参数校验必须在 envid2env 与改状态之前完成，失败不得留下任何副作用。
 * sysno 由分发表传入，本函数不使用它。 */
static int sys_set_env_status(int sysno, uint32_t envid, uint32_t status)
{
    /* finish your code here */
    (void)sysno; (void)envid; (void)status;
    return -E_INVAL;
}

/* ===== 填写区结束 ===== */