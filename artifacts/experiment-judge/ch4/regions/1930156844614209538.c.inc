/* ===== 学生填写区：实现 lib/syscall_all.c 的 sys_mem_unmap() ===== */

/* 题面 4.5 任务描述原文：
 *   「在 lib/syscall_all.c 中实现 int sys_mem_unmap(int sysno, u_int envid, u_int va)
 *     函数，该函数用于解除某个进程地址空间虚拟内存和物理内存之间的映射关系。」
 * 实验任务 4「实现内存解除映射的逻辑，包括删除虚拟地址到物理地址的映射关系」、
 * 实验任务 6「确保解除映射后，进程不再能访问之前映射的物理内存页」。
 * （宿主把 u_int 写成等宽的 uint32_t。）
 *
 * MOS 公开语义，四步：
 *   Step 1 va >= UTOP -> 返回 -E_INVAL（内核映射不许用户解除）；
 *   Step 2 envid2env(envid, &e, 0) 取 PCB，失败原样上抛；
 *   Step 3 page_remove(e->env_pgdir, va) 摘掉页表项并递减 pp_ref，
 *          归零时把物理页还回空闲链表；
 *   Step 4 返回 0。
 * 【关键契约一】va 处本来就没有映射时 page_remove 是空操作，本函数仍返回 0，
 *   不是错误 —— 解除映射是幂等的，重复解除同一地址不得报错也不得重复回收。
 * 【关键契约二】共享页只递减引用计数，只有最后一个使用者解除映射后物理页才回收；
 *   绝不能绕过 page_remove 直接 page_free，否则别的进程还挂着的页会被提前放回空闲链表。
 * sysno 由分发表传入，本函数不使用它。 */
static int sys_mem_unmap(int sysno, uint32_t envid, uint32_t va)
{
    /* finish your code here */
    (void)sysno; (void)envid; (void)va;
    return -E_INVAL;
}

/* ===== 填写区结束 ===== */