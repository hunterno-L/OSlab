#include <stdio.h>
static long f(int n) { return n < 2 ? n : f(n - 1) + f(n - 2); }
int main(void) {
    int n;
    if (scanf("%d", &n) != 1) return 0;
    printf("%ld\n", f(n));
    return 0;
}
