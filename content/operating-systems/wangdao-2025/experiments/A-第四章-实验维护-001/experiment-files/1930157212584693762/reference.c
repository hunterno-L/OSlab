#include <stdio.h>
int main(void) {
    long p;
    if (scanf("%ld", &p) != 1) return 0;
    printf("child=%ld\n", p + 1);
    printf("status=RUNNABLE\n");
    return 0;
}
