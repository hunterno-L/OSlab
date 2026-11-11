/* ---- 平台提供的内核系统调用入口 handle_sys（实验 4.2 考查时排除本片段） ----
 * MOS 公开语义：
 *   Step 1 现场在 KERNEL_SP（宿主等价 &kernsp_tf），$v0 = regs[2] 是系统调用号；
 *   Step 2 号为 0 / >= SYS_MAX / 对应槽位为 NULL -> regs[2] = -E_NO_SYS 并返回；
 *   Step 3 否则把槽位强转成 syscall_fn，按 MIPS o32 约定传
 *          sysno 与 $a0..$a3 + 栈上第一个参数，即 regs[4..8]；
 *   Step 4 返回值写回 regs[2]（$v0），用户态 msyscall 从这里取结果。
 * 【不做】cp0_epc += 4：跳过 syscall 指令属于陷阱返回路径（实验 4.1，Tier B），
 * 题面 4.2 只要求「解析系统调用号并调用相应的内核函数」。 */

static MOS_UNUSED void handle_sys(void)
{
    struct Trapframe *tf = KERNEL_SP;
    uint32_t sysno = tf->regs[2];
    if (sysno == 0 || sysno >= SYS_MAX || sys_call_table[sysno] == NULL) {
        tf->regs[2] = (uint32_t)(-E_NO_SYS);
        return;
    }
    tf->regs[2] = (uint32_t)((syscall_fn)sys_call_table[sysno])(
        (int)sysno, tf->regs[4], tf->regs[5], tf->regs[6], tf->regs[7], tf->regs[8]);
}