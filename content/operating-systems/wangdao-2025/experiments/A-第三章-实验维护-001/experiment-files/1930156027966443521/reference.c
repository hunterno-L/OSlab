#include <stdio.h>
int main(void) {
    int p, t;
    if (scanf("%d %d", &p, &t) != 2) return 0;
    for (int i = 0; i < t; i++) printf("%d\n", i % p);
    return 0;
}
