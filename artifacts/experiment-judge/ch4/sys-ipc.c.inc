/* ---- 平台提供的 lib/syscall_all.c IPC 两个系统调用（实验 4.7 考查时排除本片段） ----
 *
 * 【宿主环境说明】题面 4.7 只给出行为要求（置 env_ipc_receiving 标志、
 * 阻塞当前进程、检查目标是否可接收、发送消息并更新目标状态），未给函数签名。
 * 以下签名与步骤按 MOS 公开语义构造，属等价示例。
 *
 * sys_ipc_recv(dstva)：
 *   Step 1 dstva >= UTOP -> -E_INVAL（接收方指定的传页目标必须在用户空间）；
 *   Step 2 置 env_ipc_receiving = 1，清空 env_ipc_from / env_ipc_value，
 *          记下 env_ipc_dstva = dstva（dstva 为 0 表示本次只收值不收页）；
 *   Step 3 env_status = ENV_NOT_RUNNABLE —— 阻塞，不再参与调度；
 *   Step 4 env_tf.regs[2] = 0（返回值 0，真正的消息值由 env_ipc_value 带出）；
 *   Step 5 sys_yield() 让出 CPU，返回 0。
 *
 * sys_ipc_can_send(envid, value, srcva, perm)：
 *   Step 1 srcva >= UTOP -> -E_INVAL；
 *   Step 2 envid2env(envid, &e, 0)，失败原样上抛；
 *   Step 3 e->env_ipc_receiving 为 0 -> -E_IPC_NOT_RECV（对方没在等消息）；
 *   Step 4 srcva != 0 时调 sys_mem_map 把发送方 srcva 处的页共享到接收方
 *          事先登记的 env_ipc_dstva 处，失败原样上抛且【不得】修改接收方状态；
 *   Step 5 填 env_ipc_value / env_ipc_from = curenv->env_id，
 *          清 env_ipc_receiving，置 env_status = ENV_RUNNABLE 唤醒对方，返回 0。
 * 注意：发送方不阻塞，唤醒的是接收方。 */

static MOS_UNUSED int sys_ipc_recv(int sysno, uint32_t dstva)
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

static MOS_UNUSED int sys_ipc_can_send(int sysno, uint32_t envid, uint32_t value,
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