/* ===== 学生填写区：实现 user/fork.c 的 fork()（本实验侧重子进程分支） ===== */

/* 题面 4.9 任务描述原文：「在 user/fork.c 中实现 fork 函数，包括调用 sys_env_alloc
 * 创建子进程环境，并正确设置子进程的执行逻辑。」实验任务 2 要求「调用 sys_env_alloc 获取
 * 子进程的 envid；根据 sys_env_alloc 的返回值决定 fork 的返回值」，任务 3 要求「设置子进程
 * 的 env 指针指向自身的进程控制块；确保子进程能够从 fork 函数中返回并开始执行」。
 *
 * 【宿主环境说明】工作区未随附 user/fork.c 原文，按题面文字 + MOS 公开语义构造，
 * 属等价示例。真实 MOS 就是一个 fork()，拆成 4.9（子进程分支）与 4.15（父进程分支），
 * 故两题填写区都是整个 fork()，区别只在用例侧重哪一半。
 * 宿主夹具：驱动 Y 操作符令 syscall_env_alloc 返回 0，模拟「子进程带 $v0=0 恢复执行」，
 * 并把 env 先置 NULL 作哨兵 —— 子进程分支必须把它改回自己的 PCB。
 *
 * 七步：1 envid = syscall_env_alloc()；
 * 2 【子进程分支】envid == 0 -> env = &envs[ENVX(syscall_getenvid())]，return 0；
 * 3 【父进程分支】遍历 pn = 0 .. VPN(USTACKTOP)-1，凡 user_pte(pn<<12) 带 PTE_V 的页
 *   都调 duppage(envid, pn)；
 * 4 syscall_mem_alloc(envid, UXSTACKTOP-BY2PG, PTE_V|PTE_R) 申请子进程异常栈那一页；
 * 5 syscall_set_pgfault_handler(envid, PGFAULT_ENTRY, UXSTACKTOP) 登记缺页处理函数；
 * 6 syscall_set_env_status(envid, ENV_RUNNABLE) 唤醒子进程；7 return envid。
 *
 * 契约一：子进程分支的 env 必须取【自己的】envid（syscall_getenvid()）再 ENVX，
 *   不能用第 1 步返回的 envid —— 那是 0，ENVX(0) 会把 env 指向 envs[0] 这个无关 PCB。
 * 契约二：子进程分支 return 0，父进程分支 return 子进程 envid，这就是 fork 的分叉；
 *   子进程分支【不得】再遍历地址空间、申请异常栈或设置状态。
 * 契约三：第 1 步返回负值时不得当成子进程分支，也不得继续做父进程的动作。
 * 契约四：env 是用户态全局指针，子进程后续 user_pte/user_memcpy 全靠它翻译地址。 */
static int fork(void)
{
    /* finish your code here */
    return -E_INVAL;
}

/* ===== 填写区结束 ===== */