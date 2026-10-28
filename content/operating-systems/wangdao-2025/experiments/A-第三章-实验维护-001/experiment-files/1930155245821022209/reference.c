#include <stdio.h>
int main(void) {
    int n;
    if (scanf("%d", &n) != 1) return 0;
    long pages = 0;
    for (int i = 0; i < n; i++) {
        long va, size, pa;
        if (scanf("%lx %ld %lx", &va, &size, &pa) != 3) return 0;
        pages += size / 4096;
    }
    printf("pages=%ld\n", pages);
    return 0;
}
