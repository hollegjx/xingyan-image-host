# 本地启动

有两条路线：只看界面，运行设计预览；要验证登录、查询和上传，则启动完整前后端。

## 1. 只预览界面

```sh
cd frontend
npm ci
npm run dev:design
```

访问 `http://localhost:5173/`。预览数据是本地合成数据，写操作被拒绝，不会联系线上服务。完整联调前停止此进程，改用普通 `npm run dev`。

## 2. 完整联调的准备

| 依赖 | 说明 |
| --- | --- |
| Node.js | `^20.19.0 || >=22.12.0`，与前端 package.json 一致 |
| Java / Maven | 源码与字节码目标为 Java 8；本次构建验证使用 JDK 21、Maven 3.9.9 |
| MySQL | 使用独立的本地开发库；以下命令只用于空的 `tuku` 数据库 |
| Redis | Session 共享与分布式缓存需要 Redis，默认 localhost:6379 |
| COS | 实际后端启动／上传需提供自己的有效配置；不需要 COS 时可先用设计预览 |

不要使用真实生产数据库进行下列初始化，也不要把开发代理直接指向生产服务。

### 初始化空数据库

在仓库根目录执行。`-p` 会交互询问密码，不要把密码写进命令或提交。

```sh
mysql -u root -p -e "CREATE DATABASE tuku CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p tuku < backend/sql/create_sql.sql
mysql -u root -p tuku < backend/sql/alter_ai_picture_task.sql
```

第一份脚本已包含用户、图片、空间、成员、缓存版本与私有文件清理表；第二份创建 AI 任务相关表。`create_sql.sql` 包含固定的 `USE tuku`，且用户表及部分索引创建不是幂等操作，不能直接重复运行或当作旧库升级脚本。

其余 `alter_*.sql` 供已有数据库按实际结构升级。不要把全部文件无差别执行一遍；已有 AI 旧表升级可核对 `alter_ai_qwen_migration.sql`。应用不会自动替你执行这些 SQL。

### 配置后端

通过 IDE 的运行环境变量或本机安全的环境配置设置以下值，不要把真实值写进提交。Spring Boot 不会自动加载任意 `.env` 文件。

| 变量 | 用途／默认值 |
| --- | --- |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号／密码；示例账号默认 root，密码默认空 |
| `SPRING_DATASOURCE_URL` | 可覆盖示例 JDBC 地址；默认 jdbc:mysql://localhost:3306/tuku |
| `REDIS_HOST` / `REDIS_PORT` | 默认 localhost / 6379 |
| `REDIS_USERNAME` / `REDIS_PASSWORD` | 按本地 Redis 的认证配置填写 |
| `REDIS_DATABASE` | 默认 0；建议开发环境独立使用 |
| `COS_SECRET_ID` / `COS_SECRET_KEY` | 自己的云访问凭据，不能填入前端环境变量 |
| `COS_REGION` / `COS_BUCKET` | 真实地域与公共图片使用的存储桶 |
| `PICTURE_COLLABORATION_ALLOWED_ORIGINS` | WebSocket 来源白名单，逗号分隔；默认仅允许 localhost 的 5173、4173 端口 |

示例值为空不等于服务已配置；COS 客户端会校验凭据参数。以图搜图、AI、私有存储默认关闭，先保持关闭验证基础服务。

部署协同编辑时，通过服务器环境变量配置准确的前端 Origin，例如 `https://images.example.com`（仅为示例）；Origin 不包含路径，不要使用 `*`。此变量由 `application.example.yml` 映射到 `picture.collaboration.allowed-origins`；使用已有自定义配置时，也需要添加同样的环境变量映射，或显式设置该 Spring 属性。真实生产域名不要写入源码或测试。

从后端目录启动，显式加载不含秘密的示例配置：

```sh
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=classpath:application.example.yml"
```

后端默认监听 8123，接口前缀为 `/api`。本地接口文档为 `http://localhost:8123/api/doc.html`；不要原样向公网暴露开发接口文档。

也可以复制示例为被 Git 忽略的 `application.yml`，但仍建议使用环境变量，不在文件中写入真实密钥。没有安全默认配置就不要直接裸启动生产 JAR。

### 启动前端

另开一个终端，从仓库根目录运行：

```sh
cd frontend
npm ci
npm run dev
```

访问 `http://localhost:5173/`。`/api` 和 WebSocket 由 Vite 代理到 `http://localhost:8123`，登录使用 Session Cookie。保持访问主机一致，不混用 localhost 与 127.0.0.1。

先注册自己的普通用户，再登录验证；仓库不提供真实账号或预置管理员密码。公共图库没有已审核图片时显示空状态是正常的。

## 3. 按需启用的能力

### 私有／团队图片存储

配置与公共存储桶不同、同地域的专用私有桶，确认匿名无法读取、没有公共策略或公开 CDN，再设置 `PICTURE_PRIVATE_STORAGE_BUCKET` 和 `PICTURE_PRIVATE_STORAGE_ENABLED=true`。空间记录与对象访问权限是两层保护，不能用普通公共图片 URL 代替私有存储。

### AI 图片处理

配置 `DASHSCOPE_API_KEY`、`DASHSCOPE_WORKSPACE_ID`，核对提供方模型和服务可用性。在 `picture.ai-editing` 中显式配置允许的用户 ID、每日额度后，再设置 `PICTURE_AI_EDITING_ENABLED=true`。默认允许用户列表为空，不能仅改总开关就认为所有用户都可调用。调用可能消耗额度；开发和测试不得连接真实付费账号进行重试试验。

### 以图搜图

`PICTURE_IMAGE_SEARCH_ENABLED` 默认关闭。实现依赖外部网页接口，稳定性和使用条件需要自行验收；启用前明确用户同意外发图片，仅用于允许公开的图片。

## 4. 构建与验收

前后端命令见各自 README。基础建议：前端类型检查、单元测试与构建；后端 `mvn verify`；最后运行根目录安全扫描。

默认前端构建 `npm run build` 使用同源 `/api`。现有 `build:tc` 模式使用 `/tuku` 前缀，最终请求为 `/tuku/api`，只有反向代理具有对应路由时才使用；不要把这一模式当作任意站点的默认部署方式。

生产发布还需配置 HTTPS、反向代理、Cookie、CORS 白名单、数据库权限与备份。此文档不包含真实服务器地址、账号或一键生产部署脚本。
