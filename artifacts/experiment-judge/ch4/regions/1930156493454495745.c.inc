/* ===== 学生填写区：实现内核系统调用入口 handle_sys() ===== */

/* 题面 4.2「实现内核的系统调用处理机制」任务描述原文：
 *   「编写 handle_sys 函数，完成内核部分的系统调用处理机制，使得用户空间的系统调用
 *     能够正确地在内核中执行并返回结果。」
 * 实验任务 2「编写 handle_sys 函数，该函数负责解析系统调用号，并调用相应的内核函数」、
 * 实验任务 6「完成 handle_sys 函数，使得内核部分的系统调用机制可以正常工作」。
 * （实验任务 3 的 syscall 特权指令封装属 user/syscall_wrap.S，是实验 4.1，Tier B 本地构建。）
 *
 * MOS 公开语义，四步：
 *   Step 1 现场在 KERNEL_SP（宿主等价 &kernsp_tf）；$v0 = tf->regs[2] 是系统调用号；
 *   Step 2 号为 0、或 >= SYS_MAX、或 sys_call_table[sysno] 为 NULL（0/6/7 号空槽位）
 *          -> tf->regs[2] = (uint32_t)(-E_NO_SYS) 并立即返回；
 *   Step 3 否则把槽位强转成 syscall_fn，传 (int)sysno 与 tf->regs[4..8]
 *          （MIPS o32：$a0..$a3 是 regs[4..7]，栈上第一个参数是 regs[8]）；
 *   Step 4 把返回值写回 tf->regs[2]，用户态 msyscall 从这里取结果。
 * 【不要】做 cp0_epc += 4：跳过 syscall 指令属陷阱返回路径，不在本题考查范围。
 * 平台已提供 sys_call_table[SYS_MAX]（void * 数组，见 env-systable 段）与 syscall_fn 类型。 */
static void handle_sys(void)
{
    /* finish your code here */
    struct Trapframe *tf = KERNEL_SP;
    (void)tf;
}

/* ===== 填写区结束 ===== */