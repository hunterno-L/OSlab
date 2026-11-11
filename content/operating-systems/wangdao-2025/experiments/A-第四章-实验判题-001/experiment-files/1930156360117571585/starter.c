/* 操作系统实验 4.1 实现用户空间到内核空间的系统调用机制
 *
 * 【本文件不参与平台判分】
 * 本实验是「本地构建实验 · 平台不自动判分」（judge-mode.json: LOCAL_BUILD）。
 * 真正要填写的是 user/syscall_wrap.S 中的 syscall 封装汇编：
 * 按 MIPS o32 调用规范搬参数 → 执行特权指令 syscall 陷入内核态 → 取返回值。
 * 沙箱是 x86-64 用户态单文件 C，无 MIPS 交叉汇编器、无 CP0、无内核态，无法功能判题。
 * 完整步骤见同目录 local-build.md。
 *
 * 平台不会编译或运行本文件来给分；cases.json 为空，不生成 judge-cases.json。
 * 下面仅以注释形式随脚手架下发 MIPS o32 系统调用约定速查。
 *
 * MIPS o32 系统调用约定（公开语义）：
 *   $v0        系统调用号（陷入前由用户态填入；返回后是内核的返回值）
 *   $a0..$a3   前 4 个整型/指针参数
 *   第 5 个及以后参数：按 o32 栈帧规则放在调用者栈上（$sp+16 起）
 *   $t0..$t9   调用者保存（caller-saved），syscall 后可能被内核破坏
 *   $s0..$s7   被调用者保存（callee-saved），封装函数若用到必须自己保存/恢复
 *   $ra        返回地址；封装函数被 jal 调用，$ra 必须保存后才能再调别的函数
 *   syscall    特权触发指令：CPU 置 Cause.ExcCode=8(Sys)、EPC=该指令地址、
 *              Status.EXL=1，PC 跳到 0x80000080（BEV=0），进入内核异常分发
 *   eret       内核返回用户态：清 EXL、PC=EPC。注意 EPC 指向 syscall 指令本身，
 *              内核通常需要把 EPC 加 4，否则会反复陷入同一条 syscall
 *
 * 完整链路：
 *   用户库函数 → user/syscall_wrap.S 的 syscall 封装 → syscall 指令
 *   → 0x80000080 异常分发（实验 3.12）→ ExcCode=8 → handle_sys（实验 4.2）
 *   → 具体 sys_* 内核函数（实验 4.3 起）→ 返回值写回 Trapframe 的 $v0 → eret
 */
#include <stdio.h>

int main(void)
{
    printf("实验 4.1 系统调用机制：本地构建实验，平台不自动判分。\n");
    printf("请在本地 MIPS 环境完成 user/syscall_wrap.S 的 syscall 封装与内核侧 mysyscall。\n");
    printf("步骤见 local-build.md。\n");
    return 0;
}