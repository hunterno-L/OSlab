#include <stdio.h>
int main(void) {
    unsigned long va, size;
    if (scanf("%lx %ld", &va, &size) != 2) return 0;
    printf("unmapped=%ld\n", size / 4096);
    return 0;
}
