/* ---- 平台提供的 user/fork.c pgfault（实验 4.13 考查时排除本片段） ----
 *
 * 【宿主环境说明】工作区未随附 user/fork.c 原文。题面 4.13 实验任务 2
 * 要求「判断页是否为写时复制页面 / 为其分配新的物理内存页 / 更新页表项把复制的内容
 * 映射到新页 / 恢复被中断进程的执行」，以下按该文字 + MOS 公开语义构造。
 *
 * 算法（填写区注释里逐条给出，保证可判题）：
 *   Step 1 addr = ROUNDDOWN(va, BY2PG)，perm = user_pte(addr) & 0xFFF；
 *   Step 2 perm 不带 PTE_COW -> 不是写时复制缺页，直接返回（交给内核默认处理）；
 *   Step 3 syscall_mem_alloc(0, UXSTACKTOP - BY2PG, PTE_V | PTE_R) 申请一页临时空间，
 *          失败直接返回，不得继续；
 *   Step 4 user_memcpy(UXSTACKTOP - BY2PG, addr, BY2PG) 把原页整页拷到临时页
 *          （真实用户代码这里是 memcpy，宿主等价见 env-user 段）；
 *   Step 5 syscall_mem_map(0, UXSTACKTOP - BY2PG, 0, addr, PTE_V | PTE_R)
 *          把临时页映射回 addr，权限【去掉 PTE_COW、保留可写】，
 *          这一步 page_insert 会解除旧的 COW 映射并递减旧页 pp_ref；
 *   Step 6 syscall_mem_unmap(0, UXSTACKTOP - BY2PG) 解除临时映射，
 *          新页 pp_ref 由 2 回到 1，本进程独占它。
 * 「恢复被中断进程的执行」由陷阱返回路径完成，用户态处理函数只需返回。 */

static MOS_UNUSED void pgfault(uint32_t va)
{
    uint32_t addr = ROUNDDOWN(va, BY2PG);
    uint32_t perm = user_pte(addr) & 0xFFFu;
    if ((perm & PTE_COW) == 0) { return; }
    if (syscall_mem_alloc(0, UXSTACKTOP - BY2PG, PTE_V | PTE_R) != 0) { return; }
    if (user_memcpy(UXSTACKTOP - BY2PG, addr, BY2PG) != 0) { return; }
    if (syscall_mem_map(0, UXSTACKTOP - BY2PG, 0, addr, PTE_V | PTE_R) != 0) { return; }
    syscall_mem_unmap(0, UXSTACKTOP - BY2PG);
}