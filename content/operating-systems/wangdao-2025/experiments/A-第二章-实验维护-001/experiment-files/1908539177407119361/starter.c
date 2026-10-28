#include <stdio.h>
#include <string.h>

int main(void) {
    int n;
    if (scanf("%d", &n) != 1) {
        return 0;
    }
    int used[1024] = {0};
    char op[8];
    while (scanf("%7s", op) == 1) {
        if (op[0] == 'a') {
            /* TODO: 找编号最小的空闲页并标记占用，输出页号或 -1 */
        } else if (op[0] == 'f') {
            int k;
            if (scanf("%d", &k) != 1) {
                break;
            }
            /* TODO: 释放页 k，输出 ok / no */
        }
    }
    return 0;
}
