#include <stdio.h>

int main(void) {
    long va, size, pa;
    if (scanf("%ld %ld %ld", &va, &size, &pa) != 3) {
        return 0;
    }
    if (size <= 0 || size % 4096 != 0 || va % 4096 != 0 || pa % 4096 != 0) {
        printf("INVALID\n");
        return 0;
    }
    for (long i = 0; i < size; i += 4096) {
        printf("%ld %ld\n", va + i, pa + i);
    }
    return 0;
}
