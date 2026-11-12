# 架构与模块

ZhiCore 是 Vue 3 前端与 Spring Boot 单体后端组成的教学平台。Nginx 提供静态页面并代理 `/api`；代码执行由独立内部沙箱服务处理。

## 学习与教学

学生通过 `/home` 查看课程计划，从 `/graph` 进入课程或知识点，在练习、实验后回到学习记录与复习。统一导师携带课程、单元、题目或实验上下文，材料引用与下一步活动都需属于当前开放课程。

教师由 `admin` 角色承担：`/teaching/overview` 展示班级跟进，`/teaching/classes` 管理班级，`/teaching/tasks` 布置与批阅，`/teaching/content` 维护内容。任务使用资源快照，保留发布时的评分规则与提交上下文。

## 服务边界

| 服务 | 责任 | 数据与持久化 |
| --- | --- | --- |
| 前端 | 页面、路由、编辑器、图谱与算法演示 | 本机浏览器保存隔离草稿；服务端保存真实学习记录 |
| 后端 | 权限、课程、题目、证据、任务、导师编排 | MySQL、Redis、Neo4j；私有附件目录单独挂载 |
| MySQL | 业务与课程主数据、内容修订、学习证据、AI 会话 | Compose `mysql_data` |
| Redis | 登录会话与缓存 | Compose `redis_data` |
| Neo4j | 知识图谱查询与关系探索 | Compose `neo4j_data` / `neo4j_logs` |
| Elasticsearch | 保留的检索与索引同步服务 | Compose `elasticsearch_data` |
| 沙箱 | 编译、运行、超时与资源限制 | 一次性 Worker，无学生代码长期存储 |

## 源码导航

- 前端 `src/views/` 为页面，`src/components/` 为共享组件；`src/composables/` 管理课程与导师会话；`src/api/` 与 `generated/` 调用后端。
- 后端 `controller/` 为 HTTP 入口，`service/` 为业务，`course/` 与 `learning/` 管理课程内容；`ai/` 负责模型配置、资料检索、证据与工具编排；`task/` 管理教学任务；`judge/` 连接沙箱。
- `content/` 是版本化的课程源材料，`数据库文件/` 是首装与升级输入；发布状态以服务端审核及已发布版本为准。
- `代码沙箱/server.py` 接收内部请求，`runner.py` 在隔离容器中执行。后端仅发送请求，不直接运行学生代码。

## 配置与访问

浏览器通过同源 `/api` 访问服务，Compose 只公开前端 HTTP 端口。数据库、缓存、图库、检索和沙箱不向主机开放端口。沙箱 API 可访问 Docker socket 创建 Worker；Worker 不挂载 socket 或宿主文件系统，并关闭网络、限制资源。

AI 服务通过可配置端点调用。共享 API Key 从环境变量读取；个人 API Key 使用 `OSLAB_AI_MASTER_KEY` 加密后保存。密钥不发回浏览器明文。没有模型或 embedding 权限时，返回实际降级状态；不会用模型文字直接改变掌握度。

普通演示运行与可计入掌握度的证据分离。草稿、系统故障、编译错误和已揭示答案等情形按证据资格规则处理，避免误报学习效果。
