#include <stdio.h>
int main(void) {
    long a, b, k;
    if (scanf("%ld %ld %ld", &a, &b, &k) != 3) return 0;
    for (long i = 0; i < k; i++) printf("%ld\n", i % 2 ? b : a);
    return 0;
}
