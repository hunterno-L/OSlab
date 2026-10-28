#include <stdio.h>

int main(void) {
    int n;
    if (scanf("%d", &n) != 1) {
        return 0;
    }
    int used[1024] = {0};
    char op[8];
    while (scanf("%7s", op) == 1) {
        if (op[0] == 'a') {
            int k = -1;
            for (int i = 0; i < n; i++) {
                if (!used[i]) {
                    used[i] = 1;
                    k = i;
                    break;
                }
            }
            printf("%d\n", k);
        } else if (op[0] == 'f') {
            int k;
            if (scanf("%d", &k) != 1) {
                break;
            }
            if (k >= 0 && k < n && used[k]) {
                used[k] = 0;
                printf("ok\n");
            } else {
                printf("no\n");
            }
        }
    }
    return 0;
}
