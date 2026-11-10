SET NAMES utf8mb4;
UPDATE question SET originalCode='/*
 * 实验2.2 初始化全局变量
 * 任务：在 MIPS 内核启动阶段，mips_detect_memory() 负责初始化物理内存相关的全局变量。给定物理内存上限 maxpa（字节），计算：
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1908532874240593922;
UPDATE question SET originalCode='/*
 * 实验2.3 空闲内存管理
 * 任务：page_init() 负责把全部物理页挂入空闲链表 page_free_list。本题用数组模拟：给定物理页数 n，输出空闲页总数和按升序排列的空闲页号。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1908538371526131713;
UPDATE question SET originalCode='/*
 * 实验2.5 页表创建
 * 任务：MIPS 采用两级页表。给定虚拟地址 va，计算页目录索引 pdx 与页表索引 ptx：
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1908539875976839170;
UPDATE question SET originalCode='/*
 * 实验2.6 地址映射
 * 任务：boot_map_segment() 把一段连续的虚拟地址映射到连续的物理地址，粒度为 4KB 页。给定 va_start、size、pa_start，逐页输出映射关系。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1908540360305704962;
UPDATE question SET originalCode='/*
 * 实验2.7 页表项插入与移除
 * 任务：page_insert(va, pa) 在页表中写入页表项，page_remove(va) 清空页表项。本题约定页表项 PTE = (pa & ~0xFFF) | 0x3（0x3 表示有效位 V 与可读位 R）。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1908540572415852545;
UPDATE question SET originalCode='/*
 * 实验3.1 初始化进程控制块数组
 * 任务：为 NENV 个进程控制块分配空间并初始化。输出环境数组大小与空闲链表。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1908900051330306049;
UPDATE question SET originalCode='/*
 * 实验1.1 构建 Minix 内核
 * 任务：内核构建由 Makefile 驱动。给定目标依赖关系，输出一个合法的构建顺序（依赖先于目标；同层按编号升序）。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1917131319528452098;
UPDATE question SET originalCode='/*
 * 实验4.3 基础系统调用函数 `sys_mem_alloc`
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 *
 * 样例输入：
 * 
 * 样例输出：
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1918931948987031554;
UPDATE question SET originalCode='/*
 * 实验4.5 基础系统调用函数`sys_mem_unmap`
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 *
 * 样例输入：
 * 
 * 样例输出：
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1918932928252157953;
UPDATE question SET originalCode='/*
 * 实验4.6 基础系统调用函数`sys_yield`
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 *
 * 样例输入：
 * 
 * 样例输出：
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1918933052214812674;
UPDATE question SET originalCode='/*
 * 实验4.7 实现进程间通信（IPC）机制
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 *
 * 样例输入：
 * 
 * 样例输出：
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1918934540198682625;
UPDATE question SET originalCode='/*
 * 实验4.8 进程创建系统调用 `sys_env_alloc`
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 *
 * 样例输入：
 * 
 * 样例输出：
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1918938841746878466;
UPDATE question SET originalCode='/*
 * 实验1.2 深入理解 ELF 文件格式
 * 任务：阅读 ELF 的 Section Header。给定各节区地址，按下标输出 %x:d:0x%x 形式的节区信息。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930153941795131394;
UPDATE question SET originalCode='/*
 * 实验1.3 使用 Linker Script 控制内核加载地址
 * 任务：按 Linker Script 规则布局 .text、.data、.bss：各段起始地址向 0x1000 对齐。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930154079598989313;
UPDATE question SET originalCode='/*
 * 实验1.4 深入理解 MIPS 汇编与 C 语言的交互
 * 任务：以递归斐波那契观察函数调用与栈帧。给定 n，输出 F(n)，其中 F(0)=0、F(1)=1。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930154331844431873;
UPDATE question SET originalCode='/*
 * 实验1.5 实现内核中的 printf 函数
 * 任务：实现格式化输出：读入一个字符与一个整数，分别以 %c、%d、%x 输出。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930154460768948226;
UPDATE question SET originalCode='/*
 * 实验3.2 管理进程控制块数组
 * 任务：env_init() 依次把头插法把 PCB 挂入 env_free_list，因此链表顺序与编号相反。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930154686162456577;
UPDATE question SET originalCode='/*
 * 实验3.3 进程标识
 * 任务：envid2env 通过 env_id 取 PCB：环境编号取模得到下标。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930154848414912513;
UPDATE question SET originalCode='/*
 * 实验3.4 初始化进程虚拟内存布局
 * 任务：进程地址空间顶端 UTOP 位于用户空间高地址。给定物理页数，计算用户空间顶端地址。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930154980048949249;
UPDATE question SET originalCode='/*
 * 实验3.5 设置进程控制块
 * 任务：env_alloc 从空闲链表取出一个 PCB 并初始化为可运行。给定已用数量 n，新进程编号为 n。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930155119123681282;
UPDATE question SET originalCode='/*
 * 实验3.6 加载镜像映射
 * 任务：把 ELF 各段映射进进程地址空间。给定若干段 va size pa，统计映射的页总数（每页 4096 字节）。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930155245821022209;
UPDATE question SET originalCode='/*
 * 实验3.7 完整加载镜像
 * 任务：初始化用户栈：把参数个数与参数字符串按顺序输出。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930155360556208129;
UPDATE question SET originalCode='/*
 * 实验3.8 创建进程
 * 任务：env_create 加载二进制并设置优先级，返回进程号。本题固定返回 0 号进程。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930155484338507777;
UPDATE question SET originalCode='/*
 * 实验3.9 进程创建宏命令应用
 * 任务：用 ENV_CREATE 批量创建进程。给定 n 个名字，按创建顺序输出进程号与名字。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930155681017810946;
UPDATE question SET originalCode='/*
 * 实验3.10 进程运行与切换
 * 任务：两个进程轮转执行。给定两个进程号与切换次数，输出每次运行的进程号。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930155792691154945;
UPDATE question SET originalCode='/*
 * 实验3.12 异常处理
 * 任务：按 MIPS 异常码选择处理程序。给定异常码，输出处理程序名称。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930155905954140162;
UPDATE question SET originalCode='/*
 * 实验3.13 时钟中断
 * 任务：每次时钟中断触发一次轮转调度。给定进程数与中断次数，输出每次中断后运行的进程号。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930156027966443521;
UPDATE question SET originalCode='/*
 * 实验3.14 进程调度实验
 * 任务：实现时间片轮转（RR）：按到达顺序排队，每轮执行 quantum 个单位，未完成的进程回到队尾。输出完成顺序。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930156143783759874;
UPDATE question SET originalCode='/*
 * 实验4.1 实现用户空间到内核空间的系统调用机制
 * 任务：syscall_wrap.S 把系统调用号放进 $v0 并触发异常。给定系统调用号，输出对应的内核函数名。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930156360117571585;
UPDATE question SET originalCode='/*
 * 实验4.2 实现内核的系统调用处理机制
 * 任务：handle_sys 按系统调用号分派。本题实现两条：alloc a 与 yield。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930156493454495745;
UPDATE question SET originalCode='/*
 * 实验4.3 基础系统调用函数 `sys_mem_alloc`
 * 任务：sys_mem_alloc 从用户地址空间顺序分配页。给定 n 次分配的页数，输出每次返回的虚拟地址。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930156605618573314;
UPDATE question SET originalCode='/*
 * 实验4.4 基础系统调用函数`sys_mem_map`
 * 任务：sys_mem_map 把用户虚拟地址映射到物理地址，逐页输出。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930156740377366529;
UPDATE question SET originalCode='/*
 * 实验4.5 基础系统调用函数`sys_mem_unmap`
 * 任务：sys_mem_unmap 取消一段虚拟地址映射。给定起始地址与长度，输出解除映射的页数。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930156844614209538;
UPDATE question SET originalCode='/*
 * 实验4.6 基础系统调用函数`sys_yield`
 * 任务：sys_yield 主动让出 CPU。给定调用次数，输出每次让出的记录。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930156972624367618;
UPDATE question SET originalCode='/*
 * 实验4.7 实现进程间通信（IPC）机制
 * 任务：进程通过 sys_ipc_try_send 发送消息。给定 n 条消息，按顺序输出接收记录。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930157095085461505;
UPDATE question SET originalCode='/*
 * 实验4.8 进程创建系统调用 `sys_env_alloc`
 * 任务：sys_env_alloc 为子进程分配 PCB，子进程号 = 父进程号 + 1。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930157212584693762;
UPDATE question SET originalCode='/*
 * 实验4.9 实现 `fork` 系统调用中的 `sys_env_alloc` 
 * 任务：fork 中先调用 sys_env_alloc 得到子进程，再复制地址空间。本题输出父子进程号。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930157404163723265;
UPDATE question SET originalCode='/*
 * 实验4.10 实现 `fork` 系统调用中的 `duppage` 函数
 * 任务：duppage 把父进程的一段地址空间复制到子进程。给定起始地址与长度，输出复制的页。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930157516164222977;
UPDATE question SET originalCode='/*
 * 实验4.11 实现缺页中断处理函数 `page_fault_handler`
 * 任务：缺页中断按页对齐定位故障页。给定故障地址，输出处理的页基址。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930157629511094274;
UPDATE question SET originalCode='/*
 * 实验4.12 实现设置缺页处理函数的系统调用 `sys_set_pgfault_handler`
 * 任务：sys_set_pgfault_handler 保存用户缺页处理函数地址。给定地址，输出设置结果。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930157740261691394;
UPDATE question SET originalCode='/*
 * 实验4.13 实现 `fork` 系统调用中的缺页处理函数 `pgfault`
 * 任务：pgfault 复制故障页到子进程。给定故障地址，输出复制页的基址。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930157867273605121;
UPDATE question SET originalCode='/*
 * 实验4.14 实现进程状态设置系统调用 `sys_set_env_status`
 * 任务：sys_set_env_status 设置进程状态。给定进程号与状态码：0=FREE，1=NOT_RUNNABLE，2=RUNNABLE。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930157984642813953;
UPDATE question SET originalCode='/*
 * 实验4.15 实现 `fork` 系统调用
 * 任务：完整实现 fork：父进程返回子进程号，子进程返回 0。本题输出父进程视角的结果。
 * 输入输出按题目描述；下面是骨架，按 TODO 补全后提交评测。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void)
{
    /* TODO 1: 按题目输入格式读取数据 */
    /* TODO 2: 按题意计算 */
    /* TODO 3: 严格按输出格式打印，注意换行 */

    return 0;
}
' WHERE id=1930158086405017602;
