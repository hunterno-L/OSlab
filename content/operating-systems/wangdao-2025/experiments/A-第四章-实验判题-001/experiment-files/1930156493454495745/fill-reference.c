static void handle_sys(void)
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