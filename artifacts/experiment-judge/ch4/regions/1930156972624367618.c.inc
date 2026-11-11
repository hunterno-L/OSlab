/* ===== 学生填写区：实现 lib/syscall_all.c 的 sys_yield() ===== */

/* 题面 4.6 任务描述原文：
 *   「在 lib/syscall_all.c 中实现 void sys_yield(void) 函数，该函数用于实现用户进程
 *     对CPU的放弃，以便操作系统调度器可以切换到其他进程。」
 * 实验任务 2「在 lib/syscall_all.c 中编写 sys_yield 函数，实现进程让出CPU控制权的功能」、
 * 实验任务 5「测试 sys_yield 函数，验证其能够正确地让出CPU控制权，并触发进程调度器
 * 进行进程切换」。
 *
 * MOS 公开语义：本函数体只有一行 —— 把 CPU 交给调度器 sched_yield()（实验 3.14）。
 * 它【不】自己挑下一个进程、【不】自己改 curenv、【不】自己调 env_run：
 * 挑谁、跑多久、怎么切，全部是调度器的职责，sys_yield 只负责「转交」。
 *
 * 【实验任务 4「保存当前进程的现场（如 KERNEL_SP 和 TIMES_TACK）」】
 * 工作区未随附 lib/syscall_all.c 原文，
 * 只有题面文字。按 MOS 公开语义，现场保存并不发生在 sys_yield 内：
 *   - 时钟中断现场由陷阱入口压在 TIMESTACK，系统调用现场压在 KERNEL_SP；
 *   - 「把现场存回旧进程的 env_tf」是 env_run 的第一步（实验 3.10 已考查）；
 *   - 「syscall 指令陷入内核并压现场」是 user/syscall_wrap.S（实验 4.1，Tier B 本地构建）。
 * 因此本填写区保持 MOS 原样，只转交调度器；判题通过 env_run 的记录日志观察
 * 「时间片游标是否真的推进、curenv 是否真的换人」，而不是看它有没有重复保存现场。
 *
 * 【关键契约一】让出 CPU 不等于阻塞自己。当前进程仍是 ENV_RUNNABLE，
 *   下一轮调度还要能选回它；把它置成 ENV_NOT_RUNNABLE 是错的（那是 IPC 接收方的做法）。
 * 【关键契约二】就绪队列里没有 ENV_RUNNABLE 进程时，sched_yield 会直接返回、
 *   不调用 env_run，此时 curenv 保持不变，本函数也不得报错或崩溃。
 * 【关键契约三】本函数返回 void，不写 $v0；分发表里的宿主 glue 会补一个 0 返回值。 */
static void sys_yield(void)
{
    /* finish your code here */
}

/* ===== 填写区结束 ===== */