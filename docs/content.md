# 课程内容维护

版本化资料位于 `content/operating-systems/wangdao-2025/`：`knowledge/` 为知识，`questions/` 为题目，`units/` 为单元，`assets/` 为图片，`experiments/` 为实验维护包及源码。样例用于协议与测试，正式发布以服务端审核状态为准。

## 维护流程

1. 在同章资源包中修改正文、关联与用例，保留稳定代码与长整型 ID 的字符串形式。
2. 执行下列只读校验，核对来源、引用和题目节点映射。
3. 管理员从 `/teaching/content` 预览、导入和审核；确认后再发布。不要将本地文件存在视为已上线。
4. 数据库迁移用于安装与升级。已交付迁移不得按新源码随意重生成；新增变更应创建后续迁移。

```bash
node scripts/validate-course-content.mjs
node scripts/verify-page-replacement-content.mjs
node scripts/check-content-consistency.mjs
node scripts/check-experiment-mappings.mjs
```

实验目录包含 `starter.c`、`reference.c`、`wrong.c`、`cases.json`、`judge-config.json` 和内容说明；完整 Minix/MIPS 本地构建材料以实验说明为准。

```bash
# 对一个具有在线用例的实验目录验证三个版本；需要 gcc
node scripts/verify-experiment-harness.mjs <实验目录>
```

导入工具及参数见 [工具说明](../scripts/README.md)。协议细节见 [课程数据契约](contracts.md)。保留原素材来源、署名和使用范围，避免把院校名当作本平台身份。
