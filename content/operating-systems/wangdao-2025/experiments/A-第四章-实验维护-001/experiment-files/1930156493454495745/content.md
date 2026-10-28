### 任务描述

`handle_sys` 按系统调用号分派。本题实现两条：`alloc a` 与 `yield`。

### 输入格式

一行 `op a`，op 为 `alloc` 或 `yield`，a 为整数参数。

### 输出格式

`alloc` 输出 `va=0x<0x7f000000 + a*4096>`；`yield` 输出 `yield`；其他输出 `unknown`。

### 样例

输入：

```
alloc 2
```

输出：

```
va=0x7f002000
```
