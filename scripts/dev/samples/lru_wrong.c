/*
 * LRU 页面置换 —— 错误实现（真实沙箱验收样例 2/5）
 *
 * 典型误区：命中时不更新该页的最近访问顺序，LRU 退化为 FIFO。
 *
 * 固定输入示例：      3\n7,0,1,2,0,3,0,4,2,3,0,3,2
 * 实际输出：          10（正确 LRU 应为 9）
 *
 * 用途：同一题目同一用例下，程序可运行但输出错误，判题消息 Wrong Answer，
 * 与 lru_correct.c 一起验证“不同代码确实产生不同判题结果”。
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void) {
    char line[4096];
    if (fgets(line, sizeof(line), stdin) == NULL) {
        return 1;
    }
    int frames = atoi(line);
    if (frames <= 0 || frames > 16) {
        return 1;
    }
    if (fgets(line, sizeof(line), stdin) == NULL) {
        return 1;
    }

    int memory[16];
    int lastUsed[16];
    int count = 0, faults = 0, tick = 0;

    for (char *p = strtok(line, ", \t\r\n"); p; p = strtok(NULL, ", \t\r\n")) {
        int page = atoi(p);
        tick++;
        int hit = 0;
        for (int i = 0; i < count; i++) {
            if (memory[i] == page) {
                hit = 1;
                /* 错误点：命中时未更新 lastUsed[i]，淘汰决策退化为进入顺序 */
                break;
            }
        }
        if (!hit) {
            faults++;
            if (count < frames) {
                memory[count] = page;
                lastUsed[count] = tick;
                count++;
            } else {
                int victim = 0;
                for (int i = 1; i < count; i++) {
                    if (lastUsed[i] < lastUsed[victim]) {
                        victim = i;
                    }
                }
                memory[victim] = page;
                lastUsed[victim] = tick;
            }
        }
    }
    printf("%d\n", faults);
    return 0;
}
