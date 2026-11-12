# ZhiCore 后端

Spring Boot 2.7.2 单体服务，负责权限、课程、知识图谱、学习证据、AI 导师、实验判题及教师任务。整体功能和团队信息见 [总 README](../../README.md)。

## 开发

需要 JDK 17+，依赖见 [pom.xml](pom.xml)。Maven Wrapper 提供 Maven 3.8.1，首次使用需联网下载。

```powershell
./mvnw.cmd -DskipTests package
java -jar target/springboot-init-0.0.1-SNAPSHOT.jar
```

Linux/macOS 使用 `./mvnw`。启动前准备 MySQL、Redis、Neo4j、Elasticsearch，并向进程注入环境变量。Spring 不自动读取仓库根 `.env`；Windows 可使用根 `start-oslab.ps1`，其他方式见 [环境配置](../../docs/environment.md)。

默认服务为 `http://localhost:8121/api`，存活检查为 `/api/health/liveness`。生产配置使用 `prod`，API 文档默认关闭。数据库安装和升级见 [数据库说明](../../数据库文件/README.md)。

## 结构与测试

`controller/`、`service/`、`mapper/` 为业务层；`course/`、`learning/` 管理课程，`ai/` 编排导师与证据，`task/` 管理教学任务，`judge/` 访问内部沙箱。`src/main/resources/` 保存无真实口令的配置与静态教学资源。

测试命令与隔离服务要求见 [测试说明](../../docs/testing.md)。`./mvnw test` 包含服务集成测试，不应在生产库执行；遗留模板测试由 `legacy` profile 单独管理。

所有真实数据库口令、模型 Key、COS Key、沙箱鉴权和个人密钥加密主密钥均从环境变量注入；不要在源码或 `application*.yml` 中填入。
