/*
 * 运行时错误程序（真实沙箱验收样例 4/5）
 *
 * 用途：编译通过、运行时触发 SIGFPE（除零），进程非零退出，
 * 沙箱应返回 judgeInfo.message = "Runtime Error"。
 * volatile 防止 -O2 把除法常量折叠掉。
 */
#include <stdio.h>

int main(void) {
    volatile int zero = 0;
    volatile int bad = 1 / zero; /* 运行时除零 */
    printf("%d\n", bad);
    return 0;
}
