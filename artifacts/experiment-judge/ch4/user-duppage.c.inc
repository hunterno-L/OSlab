/* ---- 平台提供的 user/fork.c duppage（实验 4.10 考查时排除本片段） ----
 *
 * 【宿主环境说明】工作区未随附 user/fork.c 原文。按题面 4.10 实验任务 2
 * 的三类页 + MOS 公开语义构造，属等价示例：只读页（无 PTE_R，即 MOS 命名的可写位 /
 * MIPS 的 D 位）原权限共享；PTE_LIBRARY 共享页原权限共享；可写页【父子双方都标 PTE_COW】，
 * 任一方先写都缺页，由 pgfault 真正复制。
 *
 * 五步：1 addr = pn<<12；perm = user_pte(addr) & 0xFFF；2 无 PTE_V -> return；
 * 3 带 PTE_LIBRARY -> syscall_mem_map(0, addr, envid, addr, perm) 一次；
 * 4 否则带 PTE_R -> 先映射给子进程、再映射给自己，两次都带 perm|PTE_COW，顺序【先子后父】；
 * 5 否则（只读）-> syscall_mem_map(0, addr, envid, addr, perm) 一次。
 * 首参 0 = 当前进程；dstva 与 srcva 同为 addr，父子在同一虚拟地址共享同一物理页。 */

static MOS_UNUSED void duppage(uint32_t envid, uint32_t pn)
{
    uint32_t addr = pn << 12;
    uint32_t perm = user_pte(addr) & 0xFFFu;
    if ((perm & PTE_V) == 0) { return; }
    if (perm & PTE_LIBRARY) {
        syscall_mem_map(0, addr, envid, addr, perm);
    } else if (perm & PTE_R) {
        syscall_mem_map(0, addr, envid, addr, perm | PTE_COW);
        syscall_mem_map(0, addr, 0, addr, perm | PTE_COW);
    } else {
        syscall_mem_map(0, addr, envid, addr, perm);
    }
}