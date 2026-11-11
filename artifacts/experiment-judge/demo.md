# 北航实验演示与复验

本机前端与后端使用已导入的 `oslab_db`。正常平台账号登录即可；MySQL root 是数据库管理账号，不能用作平台登录。隔离验证库的临时测试账号及口令仅存系统 TEMP，不进入文档。

1. 打开“编程实验”，进入实验2.1（`/experiments/code/1908449571747745793`）。核对原任务、补充判题协议及起始代码；在桌面滚动左说明，标签栏保持可见，右编辑器不随说明滚动。
2. 编辑草稿并刷新，确认按当前用户恢复；切换 C/C++ 再切回 C。打开关联图谱，确认画布可见；“向导师提问”只填入，确认问题进入面板。当前步骤不发送模型请求。
3. 打开实验1.1（`/experiments/code/1917131319528452098`），阅读概念子任务说明：平台判构建依赖的合法顺序，AC不能证明完整MIPS内核构建成功。
4. 打开实验1.4（`/experiments/code/1930154331844431873`），确认本地MIPS提示和浅灰禁用的提交按钮。此模式不自动判分；任务引用使用既有人工验收。
5. 在390px或320px视口确认页面单列且不横向溢出。截图清单和实测边界见 `ui-layout.md`。

自动评测演示须另有真实Docker沙箱服务。本轮未点击自动实验提交，不展示虚构AC或轮询结果。具备环境后，使用 `fill-reference.c` 完成相应学生区，保留完整harness，再提交；同时用空填写及 `fill-wrong.c` 验证应失败。5项B须在实验说明指定的MIPS/GXemul工具链执行后，另行核准发布资源和单元新稿。

离线组包：

```powershell
node scripts/check-experiment-mappings.mjs
node scripts/check-content-consistency.mjs --all
node scripts/build-experiment-package.mjs --all
node scripts/import-experiment-package.mjs content/operating-systems/wangdao-2025/experiments --validate-only
```

在线导入使用脚本 `--help` 所列环境变量，先 `--preview`，再 `--commit --apply`。只有真实完成宿主运行核验才使用 `--publish --runtime-verified`；脚本会发布36项，并以退出码1明确报告5项B和6单元暂缓，不能当作全部发布成功。

全新安装先运行base及v3–v13，再运行公共前置seed和v14。存量库先备份，仅对匹配基线应用v14；不要使用MySQL `--force`。不要重复执行v4/v13这类非幂等ALTER。两个迁移验收脚本是本次隔离库的实测证据工具，不能用来代替生产迁移流程。
