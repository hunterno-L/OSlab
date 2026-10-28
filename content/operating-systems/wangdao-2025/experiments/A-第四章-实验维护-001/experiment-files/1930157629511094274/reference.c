#include <stdio.h>
int main(void) {
    unsigned long va;
    if (scanf("%lx", &va) != 1) return 0;
    printf("handled=0x%lx\n", va & ~0xfffUL);
    return 0;
}
