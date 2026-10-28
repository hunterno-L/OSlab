#include <stdio.h>
#include <string.h>
int main(void) {
    char op[16];
    long a = 0;
    if (scanf("%15s", op) != 1) return 0;
    if (!strcmp(op, "alloc")) {
        scanf("%ld", &a);
        printf("va=0x%lx\n", 0x7f000000UL + a * 4096UL);
    } else if (!strcmp(op, "yield")) {
        printf("yield\n");
    } else {
        printf("unknown\n");
    }
    return 0;
}
