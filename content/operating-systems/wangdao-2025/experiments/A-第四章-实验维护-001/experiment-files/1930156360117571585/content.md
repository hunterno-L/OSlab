### 任务描述

`syscall_wrap.S` 把系统调用号放进 `$v0` 并触发异常。给定系统调用号，输出对应的内核函数名。

### 输入格式

一行整数 `no`。

### 输出格式

一行函数名：1=sys_putchar，2=sys_getchar，3=sys_exofork，4=sys_env_alloc，5=sys_yield，6=sys_ipc_try_send，7=sys_ipc_recv，8=sys_mem_alloc，其余=unknown。

### 样例

输入：

```
4
```

输出：

```
sys_env_alloc
```
