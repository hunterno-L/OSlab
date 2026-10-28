#include <stdio.h>
int main(void) {
    unsigned long va, size, pa;
    if (scanf("%lx %ld %lx", &va, &size, &pa) != 3) return 0;
    for (unsigned long i = 0; i < size; i += 4096)
        printf("%lu %lu\n", va + i, pa + i);
    return 0;
}
