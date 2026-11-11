/* 典型错误：先切换 curenv，再把 TIMESTACK 现场存进【新进程】。
 * 旧进程的现场因此丢失（T/O 操作能看到它的 cp0_epc、sp、cp0_status 仍是 load_icode
 * 留下的旧值），新进程的入口也被时钟中断现场覆盖（Y 日志里 env_pop_tf 的参数变成
 * TIMESTACK 的 epc 而不是 ELF 入口）。首次运行时同样出错：本该保持 load_icode 设好的
 * 入口，却被写成中断现场。 */
static void env_run(struct Env *e)
{
    curenv = e;
    curenv->env_status = ENV_RUNNABLE;
    curenv->env_tf = *TIMESTACK;
    lcontext(curenv->env_cr3);
    env_pop_tf(&curenv->env_tf);
}

/* ===== 填写区结束 ===== */