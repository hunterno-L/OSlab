/* ===== 学生填写区：实现 lib/syscall_all.c 的 sys_env_alloc() ===== */

/* 题面 4.8 任务描述原文：
 *   「在 lib/syscall_all.c 中实现 sys_env_alloc 函数，该函数用于创建一个新的进程环境，
 *     并为 fork 系统调用提供支持。」
 * 实验任务 2「编写 sys_env_alloc 函数，实现创建新进程环境的功能：分配内存以存储新进程的
 * 环境信息；初始化新进程的环境，包括寄存器、栈等；设置新进程的状态，确保它不会立即被
 * 调度」、实验任务 3「确保 sys_env_alloc 函数遵循系统调用规范，正确处理错误情况」。
 * 实验任务 4「实现 fork 系统调用，利用 sys_env_alloc 创建子进程」由实验 4.9 / 4.15
 * 单独考查（user/fork.c），本填写区只负责内核侧这一步。
 *
 * 【宿主环境说明】题面未给签名，工作区也未随附 lib/syscall_all.c 原文。以下签名与步骤按 MOS 公开语义构造，
 * 属等价示例而非原文转录。
 *
 * MOS 公开语义（fork 的第一步），五步：
 *   Step 1 env_alloc(&e, curenv->env_id) 申请一个 PCB，父进程就是当前进程；
 *          失败原样上抛（-E_NO_FREE_ENV / -E_NO_MEM），不得吞掉错误码；
 *   Step 2 e->env_tf = curenv->env_tf —— 整体复制父进程现场，
 *          子进程因此从与父进程相同的位置继续执行；
 *   Step 3 e->env_tf.regs[2] = 0 —— $v0 是返回值寄存器，子进程看到 fork 返回 0，
 *          父进程看到子进程 envid，这是 fork 的分叉点；
 *   Step 4 e->env_status = ENV_NOT_RUNNABLE —— 新进程还不能被调度，
 *          必须等父进程把地址空间复制完（duppage）再显式唤醒；
 *   Step 5 返回 e->env_id（不是下标，是带生成号的完整标识）。
 *
 * 【关键契约一】Step 4 不可写成 ENV_RUNNABLE：子进程的用户页还没复制，
 *   此时被调度出去会跑到一片空白地址空间上。挂进就绪链表也不属于本函数的职责。
 * 【关键契约二】Step 2 是【整体】结构体赋值，不是只抄 cp0_epc；
 *   栈指针 regs[29]、cp0_status 等都必须与父进程一致（env_alloc 已把它们初始化好，
 *   复制父进程现场后仍以父进程为准）。Step 3 只改 $v0 这一个寄存器。
 * 【关键契约三】父进程的现场与状态不得被本函数改动。
 * 【关键契约四】本函数【不】复制任何用户页、【不】调 sys_yield、【不】改 curenv。
 * sysno 由分发表传入，本函数不使用它。 */
static int sys_env_alloc(int sysno)
{
    /* finish your code here */
    (void)sysno;
    return -E_INVAL;
}

/* ===== 填写区结束 ===== */