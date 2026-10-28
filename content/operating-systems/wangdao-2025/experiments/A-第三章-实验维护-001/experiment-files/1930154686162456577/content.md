### 任务描述

`env_init()` 依次把头插法把 PCB 挂入 `env_free_list`，因此链表顺序与编号相反。

### 输入格式

一行整数 `n`。

### 输出格式

两行：`free_head=0` 与 `free_list=` 后接降序编号 n-1..0。

### 样例

输入：

```
3
```

输出：

```
free_head=0
free_list=2 1 0
```
