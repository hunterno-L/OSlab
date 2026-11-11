/* ===== 学生填写区：实现 lib/traps.c 的 page_fault_handler() ===== */

/* 题面 4.11 任务描述原文：「在 lib/traps.c 中完成 page_fault_handler 函数的实现，
 * 该函数用于处理进程访问不存在的物理页面时引发的缺页中断。」实验任务 2 要求「检查引发
 * 中断的虚拟地址是否合法；在页表中为缺失的页面分配物理内存；更新页表项，建立虚拟地址到
 * 物理地址的映射；处理可能的异常情况，如内存不足」，任务 3 要求「正确地恢复被中断的进程执行」。
 *
 * 【宿主环境说明】工作区未随附 lib/traps.c 原文。真实 MOS 走「转发到用户注册的
 * env_pgfault_handler」的 COW 路径，题面要求的却是【按需分页】；此处以题面文字为准，
 * 属等价示例。签名适配：真实 MOS 从 TIMESTACK 取现场，宿主显式传入 tf。
 *
 * 宿主约定：PTE_V 已置位而 PTE_ADDR 为 0 =「已登记、未分配物理页」的洞（B 操作符造出）。
 * 七步：1 tf/curenv 非法或 pgdir_ok 为假 -> return；2 va=tf->cp0_badvaddr，va>=UTOP -> return；
 * 3 pgdir_walk(pgdir, ROUNDDOWN(va,BY2PG), 0, &pte) 非 0 / pte 为 NULL / 越界 -> return；
 * 4 perm=*pte&0xFFF，无 PTE_V（未登记，真非法访问）-> return；5 PTE_ADDR(*pte)!=0（已有页，
 * 非缺页）-> return；6 page_alloc(&p) 失败 -> return 且页表项一字节不动；
 * 7 *pte = page2pa(p)|perm; p->pp_ref++。
 *
 * 契约一：原样保留低 12 位权限，不得硬写 PTE_V|PTE_R —— 丢掉 PTE_COW(8)/PTE_LIBRARY(16)
 *   会让 4.10/4.13 的写时复制失效，给只读页(perm=1)补可写位则破坏保护。
 * 契约二：create 必须为 0，不得为非法地址凭空建页表；内存不足时不得留下半个页表项。
 * 契约三：不改 tf、不切进程、不动就绪链表；恢复执行由陷阱返回路径（4.1，Tier B）负责。
 * 契约四：va 先 ROUNDDOWN 到页边界，同页任何偏移的重复缺页都落到同一项，不得重复分配。 */
static void page_fault_handler(struct Trapframe *tf)
{
    /* finish your code here */
    (void)tf;
}

/* ===== 填写区结束 ===== */