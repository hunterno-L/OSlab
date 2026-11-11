/* ===== 学生填写区：实现 lib/syscall_all.c 的 sys_mem_map() ===== */

/* 题面 4.4 任务描述原文：
 *   「在 lib/syscall_all.c 中实现
 *     int sys_mem_map(int sysno, u_int srcid, u_int srcva, u_int dstid, u_int dstva, u_int perm)
 *     函数，该函数用于将源进程地址空间中的内存映射到目标进程的地址空间中。」
 * 实验任务 4「实现内存映射的逻辑，包括源地址到目标地址的映射关系建立」、
 * 实验任务 6「确保内存映射后，两个进程能够共享同一物理内存页」。
 * （宿主把 u_int 写成等宽的 uint32_t。）
 *
 * MOS 公开语义，四步：
 *   Step 1 srcva / dstva 任一 >= UTOP -> 返回 -E_INVAL；
 *   Step 2 两次 envid2env(..., 0) 取源/目标 PCB，失败原样上抛；
 *   Step 3 page_lookup(src->env_pgdir, srcva, &pte) 在【源】页表里找物理页，
 *          找不到 -> -E_INVAL（page_lookup 自身已拒绝无页表项 / PTE_V 为 0 /
 *          物理地址越界三种情况）；
 *   Step 4 page_insert(dst->env_pgdir, p, dstva, perm)，perm 原样传下去
 *          （PTE_COW / PTE_LIBRARY 靠它传递，这是 4.10 duppage 的基础）。
 * 【关键契约】page_insert 自己会补 PTE_V 并递增 pp_ref，本函数不得再手动 ++pp_ref，
 * 否则共享页的引用计数被重复计数，最后一个使用者解除映射后物理页永远回收不了。
 * 【关键契约】本函数不解除源进程的映射：共享后同一页 pp_ref 至少为 2。
 * sysno 由分发表传入，本函数不使用它。 */
static int sys_mem_map(int sysno, uint32_t srcid, uint32_t srcva,
                       uint32_t dstid, uint32_t dstva, uint32_t perm)
{
    /* finish your code here */
    (void)sysno; (void)srcid; (void)srcva; (void)dstid; (void)dstva; (void)perm;
    return -E_INVAL;
}

/* ===== 填写区结束 ===== */