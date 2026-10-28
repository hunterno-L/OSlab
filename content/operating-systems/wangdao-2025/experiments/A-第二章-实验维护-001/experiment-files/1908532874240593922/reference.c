#include <stdio.h>

int main(void) {
    long maxpa;
    if (scanf("%ld", &maxpa) != 1) {
        return 0;
    }
    long npage = maxpa / 4096;
    long basemem_kb = maxpa / 1024;
    printf("npage=%ld\n", npage);
    printf("basemem_kb=%ld\n", basemem_kb);
    return 0;
}
