# 星檐图床 · 后端

Spring Boot 2.7 + MyBatis-Plus。源码／编译目标为 Java 8；本次验证使用 JDK 21 与 Maven 3.9.9。

数据库初始化、Redis、COS 和可选服务的配置见[完整启动说明](../docs/getting-started.md)。本仓库不包含真实 `application.yml` 或任何服务器凭据。

## 运行与打包

在本目录执行，先按启动说明设置环境变量：

```sh
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=classpath:application.example.yml"
```

```sh
mvn verify
java -jar target/tuku1-0.0.1-SNAPSHOT.jar --spring.config.location=classpath:application.example.yml
```

JAR 启动同样需要真实数据库、Redis 与 COS 配置，不是无依赖的演示模式。只看界面请使用前端 `dev:design`。

## 自动化测试

```sh
mvn test
```

默认测试使用 H2 和测试替身；COS 及 AI 的模拟结果不代表真实云端验收。完整应用上下文测试需要非空的 COS 客户端参数，可在**仅用于测试的进程**中提供下面这些明确的虚构值，不要用于运行真实服务：

```sh
COS_SECRET_ID=local-test-id COS_SECRET_KEY=local-test-key \
COS_REGION=ap-test COS_BUCKET=unit-test-000 mvn verify
```

设置 `REDIS_SERVER_BIN` 为本机 redis-server 可执行文件路径后，会额外运行自行启动并关闭隔离 Redis 的测试；未设置时相关用例跳过。默认不运行 `RUN_CACHE_BENCHMARK=true` 的耗时采样。

不要为普通测试设置 `MYSQL_TEST_SERVER_URL` 或真实云端凭据。MySQL 可选测试会创建和删除随机数据库，只能针对专门的测试实例；不把线上数据库当作测试环境。

## 源码导航

- `controller/`、`model/dto/`、`model/vo/`：HTTP 接口及请求／响应结构。
- `service/`、`manager/`：业务、缓存、文件处理、异步任务和空间权限。
- `domain/`、`application/`、`infrastructure/`：协作领域规则、应用编排与基础设施。
- `mapper/`：数据库访问接口。
- `sql/`：空库结构与已有数据库迁移；不要无差别执行全部脚本。

安全边界与发布检查见[仓库安全说明](../SECURITY.md)。
