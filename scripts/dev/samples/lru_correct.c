/*
 * LRU 页面置换 —— 正确实现（真实沙箱验收样例 1/5）
 *
 * 输入（stdin，两行）：
 *   第 1 行：物理块数 frames
 *   第 2 行：逗号分隔的页面访问序列
 * 输出：LRU 缺页次数
 *
 * 固定输入示例：      3\n7,0,1,2,0,3,0,4,2,3,0,3,2
 * 期望输出：          9
 *
 * 用途：提交到判题用例为上述输入/输出的题目（或直接 POST 代码沙箱
 * /executeCode），真实沙箱应编译并运行成功，判题消息 Accepted。
 * 协议对齐 scripts/dev/../../代码沙箱/runner.py：language=c，源文件 main.c，
 * gcc -O2 -pipe main.c -o main。
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
    int lastUsed[16]; /* 每帧最近一次访问的序号 */
    int count = 0, faults = 0, tick = 0;

    for (char *p = strtok(line, ", \t\r\n"); p; p = strtok(NULL, ", \t\r\n")) {
        int page = atoi(p);
        tick++;
        int hit = 0;
        for (int i = 0; i < count; i++) {
            if (memory[i] == page) {
                hit = 1;
                lastUsed[i] = tick; /* 正确点：命中也必须更新访问顺序 */
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
