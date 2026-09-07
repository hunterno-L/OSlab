# NullForge 在线判题系统(Online Judge)

## 简介
### 单体项目
- 基于 **Spring Boot** 和 Vue 3 构建的单体应用。
- 管理员可以通过前台界面创建和管理编程题目，用户能够搜索、阅读题目并在线编写和提交代码。
- 系统会根据题目的测试用例，对用户提交的代码进行编译、运行并评判输出是否正确。
### 代码沙箱
- 代码沙箱：https://github.com/zhangna-5byg5aic/noj-code-sandbox
- 该代码沙箱服务是一个独立的编译和运行环境，能够对编程题目进行自动评测。
- 基于 **Docker 技术**，沙箱能够为每个代码提交提供隔离的运行环境，确保代码的安全和稳定运行。该服务支持Java语言代码的编译、运行和输出评。
- 作为独立服务，其他开发者也可以通过 API 接口接入沙箱服务，利用其来构建自己的在线编程评测系统。
### 微服务项目
- 微服务项目：https://github.com/zhangna-5byg5aic/noj-backend-microservices
- 采用 Spring Cloud 微服务架构
- 系统的功能被拆分为多个独立的微服务模块，如题目管理服务、用户服务、代码评测服务等。
- 优势在于高扩展性和模块化，各个微服务可以独立部署、维护和扩展。

## 目录
- [快速开始](#快速开始)
- [系统架构](#系统架构)
- [使用说明](#使用说明)
- [技术细节](#技术细节)
## 快速开始
### 环境要求
- JDK 1.8
- Redis 
- MySQL 8.1.0
- Spring Cloud Alibaba 2021.0.5.0
## 系统架构
- 业务流程图
![image.png](img%2Fimage.png)
- 时序图
![image2.png](img%2Fimage2.png)
- 架构图
![image 3.png](img%2Fimage%203.png)
## 使用说明
- 登录
![login.png](img%2Flogin.png)
- 浏览题目
![viewquestion.png](img%2Fviewquestion.png)
- 做题页面
![doquestion.png](img%2Fdoquestion.png)
- 查看提交
![viewsubmit.png](img%2Fviewsubmit.png)
- 创建题目
![createquestion.png](img%2Fcreatequestion.png)
- 管理题目
![mangequestion.png](img%2Fmangequestion.png)
## 技术细节
### 代码沙箱
采用了**模板方法模式**。模板方法定义了沙箱的基本流程： 
1. 初始化运行环境
2. 编译代码
3. 执行代码
4. 收集整理输出
5. 清理文件
- Java原生代码沙箱的实现采用Java进程类Process执行代码，Docker实现需要启动容器再执行
#### 原生实现——Java程序安全控制
1. 超时控制
- 创建一个守护线程，超时后自动中断Process
```java
// 超时控制
new Thread(() -> {
    try {
        Thread.sleep(TIME_OUT);
        System.out.println("超时了，中断");
        runProcess.destroy();
    } catch (InterruptedException e) {
        throw new RuntimeException(e);
    }
}).start();
```
2. 限制资源分配
- 指定JVM参数，使得每个java程序的执行占用的最大堆内存空间小于系统默认
3. 限制权限-Java安全管理器
- 限制用户对文件，内存，CPU，网络等资源的操作和访问
- Java安全管理器是Java提供的保护JVM、Java安全的机制，可以实现更严格的资源和操作限制
- 编写安全管理器，集成SecurityManager
#### Docker实现
- 引入依赖
```yml
<!-- https://mvnrepository.com/artifact/com.github.docker-java/docker-java -->
<dependency>
    <groupId>com.github.docker-java</groupId>
    <artifactId>docker-java</artifactId>
    <version>3.3.0</version>
</dependency>
<!-- https://mvnrepository.com/artifact/com.github.docker-java/docker-java-transport-httpclient5 -->
<dependency>
    <groupId>com.github.docker-java</groupId>
    <artifactId>docker-java-transport-httpclient5</artifactId>
    <version>3.3.0</version>
</dependency>
```
- java操作docker
```java
// 获取默认的 Docker Client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
// 拉取镜像
                PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
                PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
    @Override
    public void onNext(PullResponseItem item) {
        System.out.println("下载镜像：" + item.getStatus());
        super.onNext(item);
        }
        };
        try {
        pullImageCmd
        .exec(pullImageResultCallback)
        .awaitCompletion();
        } catch (InterruptedException e) {
        System.out.println("拉取镜像异常");
        throw new RuntimeException(e);
        }
```
### 单体项目改造微服务
#### 微服务划分
**依赖服务：**
* 注册中心：Nacos
* 微服务网关（yuoj-backend-gateway）：Gateway 聚合所有的接口，统一接受处理前端的请求

**公共模块：**
* common 公共模块（yuoj-backend-common）：全局异常处理器、请求响应封装类、公用的工具类等
* model 模型模块（yuoj-backend-model）：很多服务公用的实体类
* 公用接口模块（yuoj-backend-service-client）：只存放接口，不存放实现，多个服务共享

**业务功能：**
* 用户服务（yuoj-backend-user-service）：
* 题目服务（yuoj-backend-question-service）
* 判题服务（yuoj-backend-judge-service）

#### Open Feign 组件实现跨服务的远程调用
1. 开启 openfeign 的支持
2. 定义 Feign 客户端接口
```java
/**
 * 判题服务
 */
@FeignClient(name = "noj-backend-judge-service", path = "/api/judge/inner")
public interface JudgeFeignClient {
    /**
     * 判题
     * @param questionSubmitId
     * @return
     */
    @PostMapping("/do")
    QuestionSubmit doJudge(@RequestParam("questionSubmitId") long questionSubmitId);

}
```
3. 编写 feignClient 服务的实现类
```java
/**
 * 该服务仅内部调用，不是给前端的
 */
@RestController
@RequestMapping("/inner")

public class JudgeInnerController implements JudgeFeignClient {
    @Resource
    private JudgeService judgeService;

    /**
     * 判题
     * @param questionSubmitId
     * @return
     */
    @Override
    @PostMapping("/do")
    public QuestionSubmit doJudge(@RequestParam("questionSubmitId") long questionSubmitId){
        return judgeService.doJudge(questionSubmitId);
    }
}
```
4. 开启 Nacos 的配置，让服务之间能够互相发现
- 所有模块引入 Nacos 依赖，然后给业务服务（包括网关）增加配置
```yml
spring:
    cloud:
        nacos:
          discovery:
            server-addr: 127.0.0.1:8848
```
- 给业务服务项目启动类打上注解，开启服务发现、找到对应的客户端 Bean 的位置
```java
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.yupi.yuojbackendserviceclient.service"})
```
- 全局引入负载均衡器依赖
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-loadbalancer</artifactId>
    <version>3.1.5</version>
</dependency>
```
5. 使用 Feign 客户端
- 可以在服务内部通过注入的方式来使用 Feign 客户端，像调用本地方法一样调用远程服务

#### 接口路由
- 统一地接受前端的请求，转发请求到对应的服务
```yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
    gateway:
      routes:
        - id: yuoj-backend-user-service
          uri: lb://yuoj-backend-user-service
          predicates:
            - Path=/api/user/**
        - id: yuoj-backend-question-service
          uri: lb://yuoj-backend-question-service
          predicates:
            - Path=/api/question/**
        - id: yuoj-backend-judge-service
          uri: lb://yuoj-backend-judge-service
          predicates:
            - Path=/api/judge/**
  application:
    name: yuoj-backend-gateway
  main:
    web-application-type: reactive
server:
  port: 8101
```
#### 聚合文档
1. 给所有业务服务引入依赖，同时开启接口文档的配置
```xml
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-openapi2-spring-boot-starter</artifactId>
    <version>4.3.0</version>
</dependency>
```
```yml
knife4j:
  enable: true
```
2. 给网关配置集中管理接口文档
```xml
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-gateway-spring-boot-starter</artifactId>
    <version>4.3.0</version>
</dependency>
```
```yml
knife4j:
  gateway:
    # ① 第一个配置，开启gateway聚合组件
    enabled: true
    # ② 第二行配置，设置聚合模式采用discover服务发现的模式
    strategy: discover
    discover:
      # ③ 第三行配置，开启discover模式
      enabled: true
      # ④ 第四行配置，聚合子服务全部为Swagger2规范的文档
      version: swagger2

```
3. 访问地址即可查看聚合接口文档

