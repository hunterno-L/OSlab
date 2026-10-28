#include <stdio.h>
int main(void) {
    int n;
    if (scanf("%d", &n) != 1) return 0;
    unsigned long base = 0x7f000000UL;
    for (int i = 0; i < n; i++) {
        long sz;
        if (scanf("%ld", &sz) != 1) return 0;
        printf("va=0x%lx\n", base);
        base += (unsigned long)sz * 4096UL;
    }
    return 0;
}
