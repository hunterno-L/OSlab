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