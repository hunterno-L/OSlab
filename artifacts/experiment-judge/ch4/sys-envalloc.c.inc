/* ---- 平台提供的 lib/syscall_all.c sys_env_alloc（实验 4.8 考查时排除本片段） ----
 *
 * 【宿主环境说明】题面 4.8 未给签名，按 MOS 公开语义取 int sys_env_alloc(int sysno)。
 *
 * MOS 公开语义（fork 的第一步）：
 *   Step 1 env_alloc(&e, curenv->env_id) 申请一个 PCB，父进程就是当前进程；
 *          失败原样上抛（-E_NO_FREE_ENV / -E_NO_MEM）；
 *   Step 2 e->env_tf = curenv->env_tf —— 整体复制父进程现场，
 *          子进程因此从与父进程相同的位置继续执行；
 *   Step 3 e->env_tf.regs[2] = 0 —— $v0 是返回值寄存器，
 *          子进程看到 fork 返回 0，父进程看到子进程 envid，这是 fork 的分叉点；
 *   Step 4 e->env_status = ENV_NOT_RUNNABLE —— 新进程还不能被调度，
 *          必须等父进程把地址空间复制完（duppage）再显式唤醒；
 *   Step 5 返回 e->env_id（不是下标，是带生成号的完整标识）。
 * 本函数【不】把子进程挂进就绪链表，也【不】复制任何用户页。 */

static MOS_UNUSED int sys_env_alloc(int sysno)
{
    struct Env *e = NULL;
    int r;
    (void)sysno;
    if (curenv == NULL || !env_in_range(curenv)) { return -E_BAD_ENV; }
    r = env_alloc(&e, curenv->env_id);
    if (r != 0) { return r; }
    e->env_tf = curenv->env_tf;
    e->env_tf.regs[2] = 0;
    e->env_status = ENV_NOT_RUNNABLE;
    return (int)e->env_id;
}