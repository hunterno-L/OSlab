#include <stdio.h>
int main(void) {
    unsigned long va, size;
    if (scanf("%lx %ld", &va, &size) != 2) return 0;
    printf("dup=%ld\n", size / 4096);
    for (unsigned long i = 0; i < size; i += 4096)
        printf("va=0x%lx\n", va + i);
    return 0;
}
