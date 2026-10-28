### 任务描述

`page_insert(va, pa)` 在页表中写入页表项，`page_remove(va)` 清空页表项。本题约定页表项 `PTE = (pa & ~0xFFF) | 0x3`（0x3 表示有效位 V 与可读位 R）。

### 输入格式

一行两个整数 `va pa`（十进制，字节）。

### 输出格式

若 `va` 或 `pa` 未按 4096 对齐，输出 `INVALID`。否则输出两行（十六进制，小写）：

```
pte_insert=0x<插入后的 PTE>
pte_remove=0x0
```

### 样例

输入：

```
4096 8192
```

输出：

```
pte_insert=0x2003
pte_remove=0x0
```
