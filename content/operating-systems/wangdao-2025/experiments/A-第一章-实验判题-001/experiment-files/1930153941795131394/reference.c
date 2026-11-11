/* 操作系统实验 1.2 深入理解 ELF 文件格式 —— 主机端等价判题环境
 * 真实源码：MOS readelf.c / kerelf.h（ELF32 规范结构）
 * 等价适配（不改变语义）：
 *   1. 地址与偏移一律 uint32_t：MinGW(LLP64) 与 Linux(LP64) 下宽度一致（harness-spec §7）；
 *   2. ELF 映像用一维字节数组承载，由驱动按 stdin 合成真实的 ELF32 小端字节流，
 *      学生做的仍是「从字节流里按 e_shoff / e_shentsize / e_shnum 定位 Section Header」；
 *   3. 宿主为小端 x86-64，与 ELF32 的 ELFDATA2LSB 编码一致，故结构体指针强转可直接读；
 *      驱动保证映像基址与 e_shoff、e_shentsize 均 4 字节对齐，避免非对齐访问 UB；
 *   4. 输出契约逐字采用原文：printf("%x:d:0x%x\n", 序号, 地址)。
 * 本段由平台提供，学生只填写标记区域内的函数。
 */
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#define MOS_UNUSED __attribute__((unused))

typedef unsigned char u_char;

/* kerelf.h 的真实 ELF32 结构（52 字节 header / 40 字节 section header，小端） */
typedef struct {
    u_char   e_ident[16];
    uint16_t e_type;
    uint16_t e_machine;
    uint32_t e_version;
    uint32_t e_entry;
    uint32_t e_phoff;
    uint32_t e_shoff;
    uint32_t e_flags;
    uint16_t e_ehsize;
    uint16_t e_phentsize;
    uint16_t e_phnum;
    uint16_t e_shentsize;
    uint16_t e_shnum;
    uint16_t e_shstrndx;
} Elf32_Ehdr;

typedef struct {
    uint32_t sh_name;
    uint32_t sh_type;
    uint32_t sh_flags;
    uint32_t sh_addr;
    uint32_t sh_offset;
    uint32_t sh_size;
    uint32_t sh_link;
    uint32_t sh_info;
    uint32_t sh_addralign;
    uint32_t sh_entsize;
} Elf32_Shdr;

#define MAXSH      16
#define SHENT_MIN  40
#define SHENT_MAX  64
#define SHOFF_MIN  52
#define SHOFF_MAX  4096
#define IMG_MAX    8192
#define HEXMAX     (2 * SHENT_MAX + 1)

/* 用 uint32_t 数组做后备存储，保证映像基址至少 4 字节对齐 */
static uint32_t img_store[IMG_MAX / 4];
static u_char *const img = (u_char *)img_store;

/* 小端字节写入：驱动侧不依赖宿主字节序 */
MOS_UNUSED static void put16(u_char *p, uint32_t v)
{
    p[0] = (u_char)(v & 0xFFu);
    p[1] = (u_char)((v >> 8) & 0xFFu);
}

MOS_UNUSED static void put32(u_char *p, uint32_t v)
{
    p[0] = (u_char)(v & 0xFFu);
    p[1] = (u_char)((v >> 8) & 0xFFu);
    p[2] = (u_char)((v >> 16) & 0xFFu);
    p[3] = (u_char)((v >> 24) & 0xFFu);
}

MOS_UNUSED static int hexval(int c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}
/* ===== 学生填写区：实现 readelf.c 的 readelf()，输出所有 Section Header 的序号与地址 ===== */
/* 参考解：与 MOS readelf.c 的目标实现一致。
 * 第 i 个 Section Header 的文件偏移 = e_shoff + i * e_shentsize（必须乘 e_shentsize，
 * 因为 Section Header Table 是定长记录数组，记录长度由 e_shentsize 给出，不一定等于 40）。
 * 输出格式逐字采用原文 "%x:d:0x%x\n"。
 * 额外做 size 边界检查：越界不读，避免错误 e_shoff/e_shnum 造成越界访问。 */
static void readelf(u_char *binary, int size)
{
    Elf32_Ehdr *ehdr = (Elf32_Ehdr *)binary;
    Elf32_Shdr *shdr;
    uint32_t off;
    int i;
    if (size < (int)sizeof(Elf32_Ehdr)) return;
    for (i = 0; i < (int)ehdr->e_shnum; i++) {
        off = ehdr->e_shoff + (uint32_t)i * (uint32_t)ehdr->e_shentsize;
        if (off + (uint32_t)sizeof(Elf32_Shdr) > (uint32_t)size) return;
        shdr = (Elf32_Shdr *)(binary + off);
        printf("%x:d:0x%x\n", i, (unsigned)shdr->sh_addr);
    }
}/* ===== 填写区结束 ===== */

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