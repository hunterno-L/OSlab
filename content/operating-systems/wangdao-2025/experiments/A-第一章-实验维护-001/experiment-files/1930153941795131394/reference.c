#include <stdio.h>
int main(void) {
    int n;
    if (scanf("%d", &n) != 1) return 0;
    for (int i = 0; i < n; i++) {
        unsigned long a;
        if (scanf("%lx", &a) != 1) return 0;
        printf("%x:d:0x%lx\n", i, a);
    }
    return 0;
}
