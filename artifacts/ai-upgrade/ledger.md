# ZhiCore AI 升级执行台账（收尾）

- 2026-11-03 完成：S1（后端）+ S2（前端/内容）+ 协调者集成与验收，全部提交于 `feature/ai-upgrade`，合并进 `main`（merge `ce4a597`，--no-ff）并经 SSH push；回退锚点标签 `pre-ai-upgrade-20261103`（= 合并前 main）已推送。
- 回退方式：`git revert -m 1 ce4a597`（保留历史）或本地 `git reset --hard pre-ai-upgrade-20261103`（改写历史，慎用）。
- 验收真实记录（artifacts/ai-platform-demo/，不入库）：smoke 22/22 mode=AI（协调者复跑 RunKey 20261103-223619）；检索评价 20/20、主线 8/8、隔离违规 0；诊断 8 例 0 断言失败；跨课程 smoke 21/21；后端 488 tests 仅既有 5 类数据漂移失败；前端 lint 0 error、24/24 tests、build 绿。
- 交付补充（协调者）：README 更新（AI 能力/配置/验收/目录 v3–v12）；deploy loader 与 compose 挂载补 v11/v12 及七表契约校验；两份规格文档入库。
- 已知限制：8 项既有数据漂移测试失败（升级前即存在）；数据库包用自带校验器（官方校验器硬编码 OS）；帮助历史无独立列表接口；impeccable URL 级审计未跑；演示需可用模型端点（当前工作区已配 deepseek-flash + qwen3.7-text-embedding）。
- 会话：S1 3654bb0e…（完成）、S2 b76e4528…（完成）。
