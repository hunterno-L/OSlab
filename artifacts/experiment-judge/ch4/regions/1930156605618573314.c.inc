/* ===== 学生填写区：实现 lib/syscall_all.c 的 sys_mem_alloc() ===== */

/* 题面 4.3 任务描述原文：
 *   「在 lib/syscall_all.c 中实现
 *     int sys_mem_alloc(int sysno, u_int envid, u_int va, u_int perm) 函数，
 *     该函数用于分配内存，是进程间通信（IPC）和 fork 等系统调用的基础。」
 * 实验任务 3「调用 pmap.c 中定义的相关函数，完成虚拟内存到物理内存的映射」、
 * 实验任务 5「测试 sys_mem_alloc 函数，验证其能够正确分配内存并映射到物理内存」。
 * （宿主把 u_int 写成等宽的 uint32_t，避免 64 位主机上 unsigned long 变宽。）
 *
 * MOS 公开语义，五步：
 *   Step 1 perm |= PTE_V —— 用户只给权限位，有效位由内核置；
 *   Step 2 va >= UTOP 属内核地址空间，用户无权申请 -> 返回 -E_INVAL；
 *   Step 3 envid2env(envid, &e, 0) 取目标 PCB，失败原样上抛（checkperm 传 0：
 *          内存分配允许父进程替子进程做，不做权限检查）；
 *   Step 4 page_alloc(&p) 拿一页物理内存，失败原样上抛；
 *   Step 5 page_insert(e->env_pgdir, p, va, perm) 建立映射；
 *          失败必须 page_free(p) 把刚拿到的页还回空闲链表 —— page_alloc 不递增
 *          pp_ref，此时 pp_ref 为 0，page_free 正好回收；漏掉这一步就是物理页泄漏。
 * 引用计数由 page_insert 负责递增（实验 2.7 已验证的契约），本函数不额外递增。
 * sysno 由分发表传入，本函数不使用它。 */
static int sys_mem_alloc(int sysno, uint32_t envid, uint32_t va, uint32_t perm)
{
    /* finish your code here */
    (void)sysno; (void)envid; (void)va; (void)perm;
    return -E_INVAL;
}

/* ===== 填写区结束 ===== */