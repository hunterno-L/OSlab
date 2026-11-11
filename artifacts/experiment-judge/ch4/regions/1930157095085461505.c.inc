/* ===== 学生填写区：实现 lib/syscall_all.c 的 sys_ipc_recv() 与 sys_ipc_can_send() ===== */

/* 题面 4.7 任务描述原文：
 *   「在 lib/syscall_all.c 中实现进程间通信（IPC）相关的系统调用函数，包括
 *     sys_ipc_recv 和 sys_ipc_can_send，以支持用户进程之间的消息传递和同步。」
 * 实验任务 2「编写 sys_ipc_recv 函数，实现进程接收消息的功能：设置 env_ipc_receiving
 * 标志，表示进程准备接收消息；阻塞当前进程，直到有消息到达；处理接收到的消息并返回」、
 * 实验任务 3「编写 sys_ipc_can_send 函数：检查目标进程是否处于可接收状态；发送消息并
 * 更新目标进程的状态；返回发送结果」、实验任务 4「实现进程状态的管理和调度，确保进程
 * 在接收和发送消息时能够正确地被调度」。
 *
 * 【宿主环境说明】题面未给出这两个函数的签名，工作区也未随附 lib/syscall_all.c
 * 原文。以下签名与步骤按 MOS 公开语义
 * 构造，属等价示例而非原文转录；u_int 一律写成等宽的 uint32_t。
 *
 * sys_ipc_recv(sysno, dstva) —— 接收方，五步：
 *   Step 1 dstva >= UTOP -> 返回 -E_INVAL（传页目标必须在用户空间）；
 *   Step 2 置 curenv->env_ipc_receiving = 1，清空 env_ipc_from / env_ipc_value，
 *          记下 env_ipc_dstva = dstva（dstva 为 0 表示本次只收值不收页）；
 *   Step 3 env_status = ENV_NOT_RUNNABLE —— 阻塞自己，不再参与调度；
 *   Step 4 env_tf.regs[2] = 0（$v0 返回 0，真正的消息值由 env_ipc_value 带出）；
 *   Step 5 调 sys_yield() 让出 CPU，然后返回 0。
 *
 * sys_ipc_can_send(sysno, envid, value, srcva, perm) —— 发送方，五步：
 *   Step 1 srcva >= UTOP -> 返回 -E_INVAL；
 *   Step 2 envid2env(envid, &e, 0) 取接收方 PCB，失败原样上抛；
 *   Step 3 e->env_ipc_receiving 为 0 -> 返回 -E_IPC_NOT_RECV（对方没在等消息）；
 *   Step 4 srcva != 0 时调 sys_mem_map(sysno, curenv->env_id, srcva, envid,
 *          e->env_ipc_dstva, perm) 把发送方的页共享到接收方登记的地址，
 *          失败原样上抛；
 *   Step 5 填 e->env_ipc_value = value、e->env_ipc_from = curenv->env_id，
 *          清 e->env_ipc_receiving，置 e->env_status = ENV_RUNNABLE 唤醒对方，返回 0。
 *
 * 【关键契约一】Step 3 的接收态检查不可省：向没在等消息的进程发送必须失败，
 *   且不得改动它的任何 IPC 字段与进程状态。重复发送同一接收方，第二次必须失败。
 * 【关键契约二】Step 4 失败时接收方必须保持原样（仍在接收、仍被阻塞），
 *   即「要么整个发送成功，要么什么都不改」，不能先把对方唤醒再报错。
 * 【关键契约三】env_ipc_from 记的是【发送方】curenv->env_id，不是接收方自己的 envid。
 * 【关键契约四】阻塞的是接收方，发送方不阻塞；唤醒靠把接收方置回 ENV_RUNNABLE，
 *   本函数【不】调用 sys_yield，也【不】自己改 curenv。
 * 【关键契约五】再次 recv 必须清掉上一轮残留的 from / value，否则会读到旧消息。
 * sysno 由分发表传入，recv 不使用它；can_send 只把它原样转交给 sys_mem_map。 */
static int sys_ipc_recv(int sysno, uint32_t dstva)
{
    /* finish your code here */
    (void)sysno; (void)dstva;
    return -E_INVAL;
}

static int sys_ipc_can_send(int sysno, uint32_t envid, uint32_t value,
                            uint32_t srcva, uint32_t perm)
{
    /* finish your code here */
    (void)sysno; (void)envid; (void)value; (void)srcva; (void)perm;
    return -E_INVAL;
}

/* ===== 填写区结束 ===== */