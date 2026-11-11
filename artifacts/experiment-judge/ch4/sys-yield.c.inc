/* ---- 平台提供的 lib/syscall_all.c sys_yield（实验 4.6 考查时排除本片段） ----
 * MOS 公开语义：sys_yield 本体只有一行 sched_yield()。
 *
 * 题面 4.6 实验任务 4 提到「保存当前进程的现场（KERNEL_SP 和 TIMESTACK）」：
 * 在真实 MOS 里这一步不在 sys_yield 内完成 —— 时钟中断现场由陷阱入口压在 TIMESTACK，
 * 系统调用现场压在 KERNEL_SP，而「把现场存回 env_tf」是 env_run 的第一步（实验 3.10），
 * 「syscall 指令陷入内核」是 user/syscall_wrap.S（实验 4.1，Tier B 本地构建）。
 * 因此本函数保持 MOS 原样，只转交调度器；判题通过 env_run 的记录日志观察
 * 时间片游标是否真的推进，而不是看它有没有重复保存现场。
 *
 * sys_yield_entry 是宿主 glue（理由见 env-sysno 段），学生不填写。 */

static MOS_UNUSED void sys_yield(void)
{
    sched_yield();
}