/* ===== 学生填写区：实现 lib/env.c 的 env_run() ===== */

/* 【宿主环境说明】工作区未随附 lib/env.c 原文。以下四步按题面 3.10「实验任务 1」列出的四个步骤
 * （保存当前进程状态 → 设置 curenv → lcontext 切换地址空间 → env_pop_tf 恢复并进入用户态）
 * 加 MOS/MIPS3000 公开语义构造，属等价示例，不是原文逐字转录。
 *
 * 逐条对应题面：
 *   「实现保存当前进程状态的步骤」        → curenv->env_tf = *TIMESTACK（整个结构体）
 *   「实现设置 curenv 为新进程的步骤」    → curenv = e; env_status = ENV_RUNNABLE
 *   「实现使用 lcontext 切换…的步骤」     → lcontext(curenv->env_cr3)，传物理地址
 *   「实现使用 env_pop_tf 恢复…的步骤」   → env_pop_tf(&curenv->env_tf)，传新进程现场
 *
 * 题面「理解 Trapframe 和 TIMESTACK」的落点：TIMESTACK 是时钟中断进入内核时
 * 汇编压栈的现场，它属于【被打断的那个旧进程】，所以必须在 curenv 被改写之前
 * 存回旧进程的 env_tf；一旦先写 curenv = e，旧现场就再也找不到归属了。
 * 首次运行时 curenv 为 NULL，没有旧进程，这一步必须跳过。 */
static void env_run(struct Env *e)
{
    if (curenv != NULL) {
        curenv->env_tf = *TIMESTACK;
    }
    curenv = e;
    curenv->env_status = ENV_RUNNABLE;
    lcontext(curenv->env_cr3);
    env_pop_tf(&curenv->env_tf);
}

/* ===== 填写区结束 ===== */