# 操作系统课程源材料

本目录保存知识、题目、学习单元、资源图片与实验源码。资源身份使用稳定代码，长整型 ID 保持字符串；已发布状态以平台审核结果为准。

| 目录 | 内容 |
| --- | --- |
| `knowledge/` | 知识节点、关系与正文 |
| `questions/` | 客观题及综合题材料 |
| `units/` | 单元目标、资料和活动编排 |
| `assets/` | 可定位的教学图片与来源信息 |
| `experiments/` | 41 项实验的维护包、说明、初始/参考/错误实现与用例 |
| `verification/` | 可重复核验的算法案例 |
| `batches/` | 内容批次入口 |
| `samples/`、`starter-templates/`、`handoffs/` | 协议样例与来源材料，不代表自动发布 |

维护流程见 [内容维护](../../../docs/content.md)，身份与包约定见 [课程数据契约](../../../docs/contracts.md)，校验命令见 [工具说明](../../../scripts/README.md)。

```bash
# 从仓库根目录运行，不写数据库
node scripts/validate-course-content.mjs
node scripts/verify-page-replacement-content.mjs
node scripts/check-content-consistency.mjs
node scripts/check-experiment-mappings.mjs
```

首次安装与完整五章快照的区别见 [数据库说明](../../../数据库文件/README.md)。文件存在、校验通过、导入成功和正式发布是不同状态；不要跳过管理员预览与审核。
