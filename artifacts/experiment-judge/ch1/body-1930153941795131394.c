/* ===== 学生填写区：实现 readelf.c 的 readelf()，输出所有 Section Header 的序号与地址 ===== */
/* 原文契约：对每个 Section Header 输出 "%x:d:0x%x\n"，两个标识符分别是序号与地址。
 * 定位方式：第 i 个 Section Header 位于 binary + e_shoff + i * e_shentsize，
 * 其 sh_addr 字段在 Elf32_Shdr 内偏移 12 字节处。共 e_shnum 个。
 * size 是映像总字节数，越界不得读取。 */
static void readelf(u_char *binary, int size)
{
    /* finish your code here */
    (void)binary; (void)size;
}
/* ===== 填写区结束 ===== */

int main(void)
{
    uint32_t shoff, shentsize, shnum, total;
    uint32_t i, j;
    char hex[HEXMAX];
    int hi, lo;

    if (scanf("%u %u %u", &shoff, &shentsize, &shnum) != 3) return 0;
    /* 输入在驱动边界严格校验，越界即 return 0，不进入学生代码（harness-spec §6） */
    if (shnum < 1 || shnum > MAXSH) return 0;
    if (shentsize < SHENT_MIN || shentsize > SHENT_MAX) return 0;
    if (shoff < SHOFF_MIN || shoff > SHOFF_MAX) return 0;
    /* 4 字节对齐前置条件：保证学生的结构体指针强转不产生非对齐访问 */
    if ((shoff & 3u) != 0 || (shentsize & 3u) != 0) return 0;
    total = shoff + shnum * shentsize;
    if (total > IMG_MAX) return 0;

    memset(img_store, 0, sizeof(img_store));
    /* 合成真实 ELF32 header：EI_MAG0..3 / ELFCLASS32 / ELFDATA2LSB / EV_CURRENT */
    img[0] = 0x7F; img[1] = 'E'; img[2] = 'L'; img[3] = 'F';
    img[4] = 1; img[5] = 1; img[6] = 1;
    put16(img + 16, 2);          /* e_type = ET_EXEC */
    put16(img + 18, 8);          /* e_machine = EM_MIPS */
    put32(img + 20, 1);          /* e_version */
    put32(img + 24, 0x80000000u);/* e_entry */
    put32(img + 28, 0);          /* e_phoff */
    put32(img + 32, shoff);      /* e_shoff */
    put32(img + 36, 0);          /* e_flags */
    put16(img + 40, 52);         /* e_ehsize */
    put16(img + 42, 0);          /* e_phentsize */
    put16(img + 44, 0);          /* e_phnum */
    put16(img + 46, shentsize);  /* e_shentsize */
    put16(img + 48, shnum);      /* e_shnum */
    put16(img + 50, 0);          /* e_shstrndx */

    /* 逐个读入 Section Header 的十六进制字节流（每行 2*shentsize 个 hex 字符） */
    for (i = 0; i < shnum; i++) {
        if (scanf("%128s", hex) != 1) return 0;
        if (strlen(hex) != (size_t)(2u * shentsize)) return 0;
        for (j = 0; j < shentsize; j++) {
            hi = hexval((unsigned char)hex[2 * j]);
            lo = hexval((unsigned char)hex[2 * j + 1]);
            if (hi < 0 || lo < 0) return 0;
            img[shoff + i * shentsize + j] = (u_char)((hi << 4) | lo);
        }
    }

    readelf(img, (int)total);
    return 0;
}