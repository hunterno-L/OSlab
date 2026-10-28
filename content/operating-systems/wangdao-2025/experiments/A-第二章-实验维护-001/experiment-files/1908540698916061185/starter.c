#include <stdio.h>

int main(void) {
    int m, q;
    if (scanf("%d %d", &m, &q) != 2) {
        return 0;
    }
    long va[1024], pa[1024];
    for (int i = 0; i < m; i++) {
        if (scanf("%ld %ld", &va[i], &pa[i]) != 2) {
            return 0;
        }
    }
    /* TODO: 依次处理 q 次查询，命中输出 pa，否则输出 MISS */
    return 0;
}
