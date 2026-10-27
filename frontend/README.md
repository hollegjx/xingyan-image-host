# 星檐图床 · 前端

Vue 3 + TypeScript + Vite。项目介绍见[仓库首页](../README.md)，完整联调见[启动说明](../docs/getting-started.md)。

## 运行

在本目录执行，Node.js 要求为 `^20.19.0 || >=22.12.0`：

```sh
npm ci
npm run dev:design   # 合成数据、只读预览，不依赖后端
```

真实联调时停止预览，改运行 `npm run dev`。两者默认端口均为 5173；普通模式通过 Vite 将 `/api` 与 WebSocket 转发到本机 8123 后端。Session 请求保持 `withCredentials: true`。

## 检查与构建

```sh
npm run type-check
npm run test:unit -- --run
npm run build
```

浏览器交互回归会在独立 5174 端口启动构建后的预览，不复用 5173 开发页面：

```sh
npx playwright install chromium
npm run test:ui
```

这些回归主要使用模拟接口；通过不等于真实数据库、COS 或 AI 验收。开发预览模式禁止用于生产构建。

`npm run lint` 和 `npm run format` 会修改文件；只读风格检查可使用 `npx eslint .`。`npm run openapi` 默认从本地后端读取接口定义并重新生成 `src/service/api/`，会修改文件。

## 从哪里修改

| 内容 | 位置 |
| --- | --- |
| 页面与导航 | `src/pages/`、`src/router/`、`src/layouts/` |
| 组件与动效 | `src/components/`、`src/styles/`、`src/composables/` |
| 登录与偏好 | `src/stores/` |
| 请求与接口 | `src/request.ts`、`src/service/` |
| 本地预览数据 | `dev/design-preview.ts` |
| 单元／浏览器测试 | `src/**/__tests__/`、`e2e/` |

“我的投稿”只管理本人向公共图库提交的图片，不合并个人或团队空间。个人资料页展示后端已有资料；显示偏好保存于当前浏览器，不代表云端账户资料编辑。

## 环境配置

`.env.example` 给出 API 前缀配置。`VITE_*` 会被打包到浏览器，严禁存放密钥。默认构建使用同源 `/api`；`build:tc` 使用现有 `/tuku/api` 反向代理路径，部署到其他环境时应按实际路由调整。

详见[安全说明](../SECURITY.md)。
