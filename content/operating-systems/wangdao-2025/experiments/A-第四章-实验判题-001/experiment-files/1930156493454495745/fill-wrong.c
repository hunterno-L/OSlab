/* 错误变体：分发逻辑与边界检查都对，但把返回值写进了 $a0（regs[4]）而不是 $v0（regs[2]）。
 * 这是 MIPS 寄存器编号混淆的典型学生错误：用户态 msyscall 从 regs[2] 取结果，
 * 于是每个系统调用都「看起来返回了系统调用号本身」。 */
static void handle_sys(void)
{
    struct Trapframe *tf = KERNEL_SP;
    uint32_t sysno = tf->regs[2];
    if (sysno == 0 || sysno >= SYS_MAX || sys_call_table[sysno] == NULL) {
        tf->regs[2] = (uint32_t)(-E_NO_SYS);
        return;
    }
    tf->regs[4] = (uint32_t)((syscall_fn)sys_call_table[sysno])(
        (int)sysno, tf->regs[4], tf->regs[5], tf->regs[6], tf->regs[7], tf->regs[8]);
}