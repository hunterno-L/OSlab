#include <stdio.h>

int main(void) {
    long va, pa;
    if (scanf("%ld %ld", &va, &pa) != 2) {
        return 0;
    }
    if (va % 4096 != 0 || pa % 4096 != 0) {
        printf("INVALID\n");
        return 0;
    }
    unsigned long pte = ((unsigned long)pa & ~0xFFFUL) | 0x3UL;
    printf("pte_insert=0x%lx\n", pte);
    printf("pte_remove=0x0\n");
    return 0;
}
