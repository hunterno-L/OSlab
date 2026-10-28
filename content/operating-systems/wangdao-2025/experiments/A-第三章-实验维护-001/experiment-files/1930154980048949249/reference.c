#include <stdio.h>
int main(void) {
    unsigned long n;
    if (scanf("%lu", &n) != 1) return 0;
    printf("utop=0x%lx\n", 0x7f400000UL + n * 4096UL);
    return 0;
}
