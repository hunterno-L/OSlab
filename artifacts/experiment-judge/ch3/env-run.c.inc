/* ---- 平台提供的 lib/env.c env_run（实验 3.10 考查时排除本片段） ----
 * MOS 公开语义四步：
 *   Step 1 若已有当前进程，把 TIMESTACK 上的现场保存回它的 env_tf；
 *   Step 2 curenv = e，并置 ENV_RUNNABLE；
 *   Step 3 lcontext(curenv->env_cr3) 切换地址空间（传【物理地址】，不是虚拟地址）；
 *   Step 4 env_pop_tf(&curenv->env_tf) 恢复寄存器并 eret 进入用户态。
 * 宿主等价：lcontext/env_pop_tf 是记录函数（见 env-pcb 段），不做真实汇编切换，
 * 但调用参数与调用时刻的 curenv 都被记录，四步顺序仍被真实校验。 */

static MOS_UNUSED void env_run(struct Env *e)
{
    if (!env_in_range(e)) { return; }
    if (curenv != NULL && env_in_range(curenv)) {
        curenv->env_tf = *TIMESTACK;
    }
    curenv = e;
    curenv->env_status = ENV_RUNNABLE;
    lcontext(curenv->env_cr3);
    env_pop_tf(&curenv->env_tf);
}