/* ---- 第四章共享：用户态系统调用库（lib/syscall.c 的宿主等价） ----
 *
 * 【宿主环境说明】工作区未随附 lib/syscall.c、user/fork.c 原文。
 * 真实用户态靠 user/syscall_wrap.S 的 syscall 特权指令陷入内核（4.1，Tier B 本地构建），
 * 宿主没有 MIPS 特权指令，改为【填好 KERNEL_SP 现场后直接调 handle_sys】；寄存器编号
 * （$v0=regs[2]，$a0..$a3=regs[4..7]，栈上首参=regs[8]）与返回值取回位置照搬 MIPS o32，
 * 故 4.2 的分发逻辑仍被真实校验。
 *
 * 本段只用于用户态实验，只给出 fork/duppage/pgfault 真正用到的 6 个包装：yield 与 IPC
 * 的内核侧已被裁掉（槽位留 NULL），其包装永远只拿到 -E_NO_SYS 且无人调用，一并不提供。
 * 访存与页表观察窗口（user_pte / user_memcpy / VPN / PGFAULT_ENTRY）拆在 env-usermem 段。
 */

/* user/fork.c 的全局 env：指向当前进程自己的 PCB（真实 MOS 由 UENVS 窗口取得） */
static MOS_UNUSED struct Env *env;

/* 真实 MOS 用 SYS_getenvid 取自身 envid；第四章只列 9 个系统调用号，
 * 不含 getenvid，故宿主由平台直接返回 curenv->env_id —— 与该系统调用的结果等价。 */
static MOS_UNUSED uint32_t syscall_getenvid(void)
{
    return (curenv != NULL && env_in_range(curenv)) ? curenv->env_id : 0u;
}

static MOS_UNUSED int msyscall(int sysno, uint32_t a0, uint32_t a1,
                               uint32_t a2, uint32_t a3, uint32_t a4)
{
    kernsp_tf.regs[2] = (uint32_t)sysno;
    kernsp_tf.regs[4] = a0;
    kernsp_tf.regs[5] = a1;
    kernsp_tf.regs[6] = a2;
    kernsp_tf.regs[7] = a3;
    kernsp_tf.regs[8] = a4;
    handle_sys();
    return (int)kernsp_tf.regs[2];
}

static MOS_UNUSED int syscall_mem_alloc(uint32_t envid, uint32_t va, uint32_t perm)
{
    return msyscall(SYS_mem_alloc, envid, va, perm, 0, 0);
}

static MOS_UNUSED int syscall_mem_map(uint32_t srcid, uint32_t srcva,
                                      uint32_t dstid, uint32_t dstva, uint32_t perm)
{
    return msyscall(SYS_mem_map, srcid, srcva, dstid, dstva, perm);
}

static MOS_UNUSED int syscall_mem_unmap(uint32_t envid, uint32_t va)
{
    return msyscall(SYS_mem_unmap, envid, va, 0, 0, 0);
}

/* 宿主夹具：真实硬件上子进程从 fork 返回时 $v0 已被内核置 0，故走 envid==0 分支。
 * 宿主的 syscall_env_alloc 永远返回非 0 的子进程 envid，该分支本来不可达；驱动 Y 操作符
 * 置起本标志模拟「子进程带 $v0=0 恢复执行」，只影响这一次调用，不改学生代码与父进程语义。 */
static MOS_UNUSED int fork_child_stub;

static MOS_UNUSED int syscall_env_alloc(void)
{
    if (fork_child_stub) { return 0; }
    return msyscall(SYS_env_alloc, 0, 0, 0, 0, 0);
}

static MOS_UNUSED int syscall_set_pgfault_handler(uint32_t envid, uint32_t func,
                                                  uint32_t xstacktop)
{
    return msyscall(SYS_set_pgfault_handler, envid, func, xstacktop, 0, 0);
}

static MOS_UNUSED int syscall_set_env_status(uint32_t envid, uint32_t status)
{
    return msyscall(SYS_set_env_status, envid, status, 0, 0, 0);
}