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
    for (int j = 0; j < q; j++) {
        long v;
        if (scanf("%ld", &v) != 1) {
            return 0;
        }
        long found = -1;
        for (int i = 0; i < m; i++) {
            if (va[i] == v) {
                found = pa[i];
                break;
            }
        }
        if (found >= 0) {
            printf("%ld\n", found);
        } else {
            printf("MISS\n");
        }
    }
    return 0;
}
