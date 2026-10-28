#include <stdio.h>
int main(void) {
    long n;
    if (scanf("%ld", &n) != 1) return 0;
    printf("env_id=%ld\n", n);
    printf("status=RUNNABLE\n");
    return 0;
}
