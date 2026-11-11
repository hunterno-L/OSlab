/* 操作系统实验 1.4 深入理解 MIPS 汇编与 C 语言的交互
 *
 * 【本文件不参与平台判分】
 * 本实验是「本地构建实验 · 平台不自动判分」（judge-mode.json: LOCAL_BUILD）。
 * 真正要填写的是 boot/start.S 中的 MIPS 汇编（设置栈指针 $sp 并跳转到 main），
 * 完整运行需使用 MIPS 交叉编译器与 gxemul。
 * 完整步骤见同目录 local-build.md 与实验说明的「本地完整验证」小节。
 *
 * 下面这段 C 是实验任务 1「分析给定的 C 代码：fib 函数与 main 函数」的分析对象，
 * 可在本地用交叉编译器生成 MIPS 汇编后逐行对照，也可用宿主 gcc 直接编译运行做行为对照。
 * 它不是判题 harness，平台不会编译或运行它来给分。
 *
 * 本地生成汇编：
 *   mips-linux-gnu-gcc -S -O0 -o fib.s fib.c      # 观察未优化版本的栈帧与 jal/jr
 *   mips-linux-gnu-gcc -S -O2 -o fib-opt.s fib.c  # 观察优化后递归/循环的变化
 * 宿主行为对照：
 *   gcc -std=c99 -Wall -Wextra -O2 -o fib fib.c && ./fib
 */
#include <stdio.h>

/* 实验任务 1 的递归分析对象：观察每次调用如何压栈 $ra/$fp、
 * 参数如何经 $a0 传递、返回值如何经 $v0 回传。 */
static int fib(int n)
{
    if (n <= 1) {
        return n;
    }
    return fib(n - 1) + fib(n - 2);
}

int main(void)
{
    int i;
    /* 实验任务 1 的循环分析对象：观察 slti / bnez / addi 构成的循环控制 */
    for (i = 0; i < 10; i++) {
        printf("fib(%d) = %d\n", i, fib(i));
    }
    printf("本实验为本地构建实验，平台不自动判分。请按 local-build.md 完成 boot/start.S。\n");
    return 0;
}