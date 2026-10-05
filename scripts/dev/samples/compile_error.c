/*
 * 编译错误程序（真实沙箱验收样例 3/5）
 *
 * 用途：提交后 gcc 编译失败，沙箱应返回 judgeInfo.message = "Compile Error"。
 */
#include <stdio.h>

int main(void) {
    printf("hello" /* 缺少分号，编译失败 */
    return 0;
}
