/* ===== 学生填写区：实现 user/fork.c 的 fork()（本实验侧重父进程分支） ===== */

/* 题面 4.15 任务描述原文：「在 user/fork.c 中完成 fork 函数中父进程部分的实现，包括
 * 地址空间的遍历、duppage、分配子进程的异常处理栈、设置子进程的处理函数、设置子进程的
 * 运行状态，并返回子进程的 envid。」实验任务 2 逐条要求「地址空间的遍历和 duppage 处理；
 * 分配子进程的异常处理栈；设置子进程的处理函数；设置子进程的运行状态；返回子进程的 envid」。
 *
 * 【宿主环境说明】工作区未随附 user/fork.c 原文，按题面文字 + MOS 公开语义构造，
 * 属等价示例。真实 MOS 就是一个 fork()，拆成 4.9（子进程分支）与 4.15（父进程分支），
 * 故两题填写区都是整个 fork()，区别只在用例侧重哪一半。duppage 由平台提供（4.10 已考）。
 *
 * 七步：1 envid = syscall_env_alloc()；
 * 2 【子进程分支】envid == 0 -> env = &envs[ENVX(syscall_getenvid())]，return 0；
 * 3 【父进程分支】遍历 pn = 0 .. VPN(USTACKTOP)-1，凡 user_pte(pn<<12) 带 PTE_V 的页
 *   都调 duppage(envid, pn)；
 * 4 syscall_mem_alloc(envid, UXSTACKTOP-BY2PG, PTE_V|PTE_R) 申请子进程异常栈那一页；
 * 5 syscall_set_pgfault_handler(envid, PGFAULT_ENTRY, UXSTACKTOP) 登记缺页处理函数；
 * 6 syscall_set_env_status(envid, ENV_RUNNABLE) 唤醒子进程；7 return envid。
 *
 * 契约一：遍历上界是 VPN(USTACKTOP)，且【只对有 PTE_V 的页】调 duppage。
 * 契约二：第 4/5/6 步的目标都是【子进程 envid】，不是 0；写成 0 会把异常栈与处理函数
 *   装到父进程自己身上，子进程一缺页就跑飞，而且永远不会被调度。
 * 契约三：异常栈那一页在 UXSTACKTOP-BY2PG（栈向下生长），第 5 步传的栈顶是 UXSTACKTOP，
 *   处理函数入口是 PGFAULT_ENTRY；顺序必须先申请栈页再登记处理函数。
 * 契约四：第 6 步必须置 ENV_RUNNABLE 把子进程挂进就绪链表；父进程分支返回子进程 envid
 *   （正数），子进程分支返回 0。 */
static int fork(void)
{
    /* finish your code here */
    return -E_INVAL;
}

/* ===== 填写区结束 ===== */