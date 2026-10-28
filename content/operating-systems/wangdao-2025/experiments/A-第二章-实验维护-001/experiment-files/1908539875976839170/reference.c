#include <stdio.h>

int main(void) {
    unsigned long va;
    if (scanf("%lu", &va) != 1) {
        return 0;
    }
    unsigned long pdx = (va >> 22) & 0x3FFUL;
    unsigned long ptx = (va >> 12) & 0x3FFUL;
    printf("pdx=%lu\n", pdx);
    printf("ptx=%lu\n", ptx);
    return 0;
}
