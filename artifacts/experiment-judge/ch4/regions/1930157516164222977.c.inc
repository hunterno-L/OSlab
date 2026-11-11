/* ===== 学生填写区：实现 user/fork.c 的 duppage() ===== */

/* 题面 4.10 任务描述原文：「在 user/fork.c 中实现 duppage 函数，该函数用于在 fork
 * 系统调用中处理页表项的复制，以确保父子进程能够正确地共享或独立访问内存页。」
 * 实验任务 2 要求「对于只读页面，直接映射给子进程；对于共享页面，保持共享状态；
 * 对于写时复制页面，进行复制操作」，任务 3 要求「正确地处理所有类型的页表项」。
 *
 * 【宿主环境说明】工作区未随附 user/fork.c 原文，以下按题面文字 + MOS 公开语义
 * 构造，属等价示例而非原文转录。宿主把 vpt[pn] 等价为 user_pte(pn<<12)。
 *
 * 五步：1 addr=pn<<12；perm=user_pte(addr)&0xFFF；2 无 PTE_V -> return，不做任何系统调用；
 * 3 带 PTE_LIBRARY(0x10) -> syscall_mem_map(0, addr, envid, addr, perm) 一次；
 * 4 否则带 PTE_R(0x2，可写位) -> 先 syscall_mem_map(0,addr,envid,addr,perm|PTE_COW)，
 *   再 syscall_mem_map(0,addr,0,addr,perm|PTE_COW)，顺序固定【先子后父】；
 * 5 否则（只读）-> syscall_mem_map(0, addr, envid, addr, perm) 一次。
 * 首参 0 = 当前进程；dstva 与 srcva 同为 addr，父子在同一虚拟地址共享同一物理页。
 *
 * 契约一：可写页必须【父子双方都标 PTE_COW(0x8)】；只标子进程则父进程仍可写，
 *   一写就静默改到共享页上，子进程被污染，写时复制失效。
 * 契约二：只读页与 LIBRARY 共享页【不得】加 COW，原权限共享一次即可。
 * 契约三：LIBRARY 分支必须优先于 PTE_R —— 共享页即使可写也保持共享，不复制。
 * 契约四：不检查 syscall_mem_map 返回值（MOS 原样语义），不分配物理页、不改 env、不切进程；
 *   真正的复制发生在后续写缺页时由 pgfault 完成。 */
static void duppage(uint32_t envid, uint32_t pn)
{
    /* finish your code here */
    (void)envid; (void)pn;
}

/* ===== 填写区结束 ===== */