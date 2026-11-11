/* 操作系统实验 2.2 初始化全局变量 —— mm/pmap.c: mips_detect_memory()
 * 主机端等价判题环境：真实内核由 CMOS 报告内存大小，这里用 stdin 的两个
 * KB 值模拟 CMOS 读数（basemem_kb / extmem_kb），其余语义与真实源码一致。
 * 官方锚点：maxpa = 0x4000000、npage = maxpa / 4096（见 mm/pmap.c）。
 */
#include <stdio.h>
#include <stdint.h>

#define BY2PG 4096u

/* 平台提供：CMOS 读数与四个待初始化全局变量 */
static uint32_t cmos_basemem_kb;
static uint32_t cmos_extmem_kb;

static uint32_t maxpa;    /* Maximum physical address */
static uint32_t npage;    /* Amount of memory(in pages) */
static uint32_t basemem;  /* Amount of base memory(in bytes) */
static uint32_t extmem;   /* Amount of extended memory(in bytes) */
/* ===== 学生填写区：实现 mm/pmap.c 的 mips_detect_memory() ===== */

/* 单位换算：KB -> 字节乘 1024；字节 -> 页数除 BY2PG。
 * 对应真实源码 maxpa = 0x4000000; basemem = 0x4000000; extmem = 0;
 * npage = maxpa/4096（此处 CMOS 读数由驱动给出，65536 0 即官方 64MB 场景）。 */
void mips_detect_memory(void)
{
    basemem = cmos_basemem_kb * 1024u;
    extmem = cmos_extmem_kb * 1024u;
    maxpa = basemem + extmem;
    npage = maxpa / BY2PG;
}/* ===== 填写区结束 ===== */

int main(void)
{
    if (scanf("%u %u", &cmos_basemem_kb, &cmos_extmem_kb) != 2) { return 0; }
    if (cmos_basemem_kb > 524288u || cmos_extmem_kb > 524288u) { return 0; }
    mips_detect_memory();
    printf("%u %u %u %u\n", maxpa, basemem, extmem, npage);
    return 0;
}