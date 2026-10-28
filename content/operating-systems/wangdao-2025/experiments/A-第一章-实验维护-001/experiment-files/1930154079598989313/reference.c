#include <stdio.h>
static unsigned long al(unsigned long x) { return (x + 0xfffUL) & ~0xfffUL; }
int main(void) {
    unsigned long b, t, d, s;
    if (scanf("%lx %lx %lx %lx", &b, &t, &d, &s) != 4) return 0;
    unsigned long p = b;
    printf("text=0x%lx\n", p);
    p = al(p + t);
    printf("data=0x%lx\n", p);
    p = al(p + d);
    printf("bss=0x%lx\n", p);
    return 0;
}
