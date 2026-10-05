/*
 * 超时程序（真实沙箱验收样例 5/5）
 *
 * 用途：编译通过、运行不结束，超过题目 timeLimit（默认 1000ms）后
 * 沙箱应返回 judgeInfo.message = "Time Limit Exceeded"。
 * volatile 计数防止 -O2 优化掉空循环。
 */
#include <stdio.h>

int main(void) {
    volatile long x = 0;
    for (;;) {
        x++;
    }
    /* unreachable */
    printf("%ld\n", x);
    return 0;
}
