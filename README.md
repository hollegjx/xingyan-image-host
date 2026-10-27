<p align="center">
  <img src="frontend/public/favicon.svg" width="72" height="72" alt="星檐图床标识" />
</p>

<h1 align="center">星檐图床</h1>
<p align="center">为每一张图片，留一处屋檐。</p>

星檐是一个前后端分离的图片管理项目：收纳日常的画面，也分享偶然的光。界面以黑白、留白和轻微动效为主，图片保留原色；公共分享、个人空间和团队协作各有清晰入口。

## 可以做什么

- **浏览与投稿**：分页检索公共图片，管理自己的投稿、草稿和审核状态。
- **收纳与协作**：个人／团队空间、成员角色与权限、容量和数量额度、协作编辑状态同步。
- **图片处理**：上传、裁剪、缩略图与展示图、颜色检索；AI 扩图及以图搜图为可选功能。
- **图库分析**：按权限查看空间使用量、分类、标签、大小和用户活动等分析。
- **细节体验**：列表状态与返回位置恢复、未保存内容保护、页面过渡、可关闭的背景粒子，以及尊重系统减少动态效果的偏好。

私有图片读写、AI 与外部图片搜索需要独立配置；示例配置默认关闭这些能力。代码包含实现，不代表未配置时就能使用外部服务。完整条件见[启动说明](docs/getting-started.md)。

## 技术组成

| 部分 | 主要技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Ant Design Vue、ECharts |
| 后端 | Java、Spring Boot 2.7、MyBatis-Plus、MySQL |
| 缓存与登录 | Caffeine、Redis、Spring Session |
| 图片与协作 | 腾讯云 COS、WebSocket、Disruptor |
| 自动化测试 | Vitest、Playwright、JUnit、H2、隔离 Redis 测试 |

## 仓库结构

```text
xingyan-image-host/
├── frontend/             # Vue 页面、组件、接口调用和前端测试
├── backend/              # Java 服务、SQL 和后端测试
├── docs/                 # 启动与开发说明
└── scripts/              # 整仓库安全检查
```

前后端放在同一仓库便于展示和联调，仍然独立安装依赖、构建和运行；这不是把两个服务合并成一个进程。

## 先看一眼界面

需要符合 `frontend/package.json` 要求的 Node.js：`^20.19.0 || >=22.12.0`。

```sh
cd frontend
npm ci
npm run dev:design
```

打开 `http://localhost:5173/`。此模式使用本地合成数据，不需要后端、数据库或云密钥；不执行真实登录、上传、删除或 AI 请求。它只用于界面预览，不是业务验收模式，也不能打包为生产版本。

## 完整启动与检查

- [前后端启动、数据库初始化、可选能力](docs/getting-started.md)
- [前端开发与测试](frontend/README.md)
- [后端开发与测试](backend/README.md)
- [配置安全与发布检查](SECURITY.md)

本仓库从当前源码快照建立独立 Git 历史，不包含原开发仓库的提交历史、真实环境配置、数据库备份或内部记录。当前为私有整理阶段，公开前仍需完成安全检查及项目级许可证选择。

## 使用边界

- 真实 COS、AI 和外部搜索需要使用自己的账号、权限与额度，自动化测试不等于云端真实验收。
- 普通用户资料页目前以展示为主；尚无普通用户自助修改资料／密码接口。
- 上生产前应单独审查 CORS、TLS、Cookie、接口文档暴露、云权限、依赖版本及备份策略；开发默认配置不是生产加固模板。
- 尚未添加整项目 `LICENSE`。第三方组件信息分别见 [前端说明](frontend/THIRD_PARTY_NOTICES.md) 与 [后端说明](backend/THIRD_PARTY_NOTICES.md)。
