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

/* 典型错误：把 CMOS 的 KB 读数直接当成字节数写进 basemem/extmem，
 * 忘了乘 1024，于是 maxpa 与 npage 全部缩小 1024 倍。 */
void mips_detect_memory(void)
{
    basemem = cmos_basemem_kb;
    extmem = cmos_extmem_kb;
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