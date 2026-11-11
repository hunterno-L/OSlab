/* ===== 学生填写区：实现 user/fork.c 的 pgfault() ===== */

/* 题面 4.13 任务描述原文：「在 user/fork.c 中实现 pgfault 函数，该函数用于处理 fork
 * 过程中可能发生的缺页中断，确保子进程能够正确地复制父进程的内存页。」实验任务 2 要求
 * 「判断页是否为写时复制的页面；为写时复制的页面分配新的物理内存页；更新页表项，将复制的
 * 内容映射到新分配的物理内存页；恢复被中断的进程执行」，任务 3 要求「正确处理不同类型的
 * 缺页中断」。
 *
 * 【宿主环境说明】工作区未随附 user/fork.c 原文，以下按题面文字 + MOS 公开语义构造，
 * 属等价示例。宿主把 memcpy 等价为 user_memcpy、vpt[pn] 等价为 user_pte。
 *
 * 六步：1 addr = ROUNDDOWN(va, BY2PG)；perm = user_pte(addr) & 0xFFF；
 * 2 perm 不带 PTE_COW(0x8) -> 不是写时复制缺页，直接 return；
 * 3 syscall_mem_alloc(0, UXSTACKTOP-BY2PG, PTE_V|PTE_R) 申请临时页，失败 return；
 * 4 user_memcpy(UXSTACKTOP-BY2PG, addr, BY2PG) 整页拷原页到临时页，失败 return；
 * 5 syscall_mem_map(0, UXSTACKTOP-BY2PG, 0, addr, PTE_V|PTE_R) 映射回 addr，失败 return；
 *   这一步 page_insert 会解除旧 COW 映射并递减旧页 pp_ref；
 * 6 syscall_mem_unmap(0, UXSTACKTOP-BY2PG) 解除临时映射，新页 pp_ref 由 2 回到 1，本进程独占。
 *
 * 契约一：第 5 步权限必须是 PTE_V|PTE_R —— 【去掉 COW、保留可写】。沿用 perm 会把 COW 位
 *   留在新页上，同一条写指令反复缺页，进程原地死循环。
 * 契约二：第 4 步必须整页 BY2PG 字节、源是 addr 而非 va（va 可能带页内偏移）。
 * 契约三：任一步失败立即 return，不得在临时页没申请到时就去 memcpy 或 unmap。
 * 契约四：第 6 步不可省 —— 省掉它临时页一直挂着，新页 pp_ref 停在 2，本进程并未独占该页。
 * 「恢复被中断的进程执行」由陷阱返回路径完成，用户态处理函数只需返回。 */
static void pgfault(uint32_t va)
{
    /* finish your code here */
    (void)va;
}

/* ===== 填写区结束 ===== */