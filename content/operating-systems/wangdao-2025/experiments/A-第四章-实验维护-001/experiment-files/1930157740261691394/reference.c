#include <stdio.h>
int main(void) {
    unsigned long h;
    if (scanf("%lx", &h) != 1) return 0;
    printf("handler=0x%lx\n", h);
    return 0;
}
