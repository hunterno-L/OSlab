### 任务描述

按 Linker Script 规则布局 `.text`、`.data`、`.bss`：各段起始地址向 0x1000 对齐。

### 输入格式

一行四个十六进制整数：`base text_size data_size bss_size`。

### 输出格式

三行：`text=0x..`、`data=0x..`、`bss=0x..`（小写十六进制）。

### 样例

输入：

```
0x1000 0x800 0x400 0x200
```

输出：

```
text=0x1000
data=0x2000
bss=0x3000
```
