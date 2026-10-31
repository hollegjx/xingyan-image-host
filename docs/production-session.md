# 生产登录会话配置

2026-10-31 按用户授权 M-12、监督方顺序调整 M-13，仅修改生产配置并重启 `znytk-backend`。后端仍为 `2b6f3e0`，未重新发布前端或修改业务代码。

以下两项放在 `/srv/znytk-test/secrets/backend.properties`，由后端启动参数最后加载，后续 release 继续使用同一文件：

```properties
spring.session.timeout=7d
server.servlet.session.cookie.max-age=7d
```

会话空闲超时和浏览器 Cookie 有效期均设为七天；已有会话可能仍保留原来的三十分钟空闲超时。原 Cookie 配置保持 `Secure=true`、`HttpOnly=true`、`SameSite=Lax`、`Path=/tuku/api`。配置文件属主和权限仍为 `10001:10001`、`600`，不提交其内容。

变更前原文件已备份至 `/srv/znytk-test/backups/backend.properties.before-session7d-20261031`，备份仅 root 可读。回滚时在服务器内恢复这份原始配置，再只重启 `znytk-backend`；若此后有其他配置变更，应只移除上述两项，避免覆盖后来变更。不要输出备份内容，不恢复数据库或清空会话库。

重启后 `/health` 和 `/picture/capabilities` 均 HTTP 200、业务码 0；私有存储开启、外部搜图关闭。tc/cpa/codex/mb 响应分别为 200/200/404/200，与变更前一致。此时尚未取得浏览器 Cookie 属性证据，不把配置核验写成浏览器实测通过。

后续 M-15/M-16 排查：在同一容器内，用相同 jar 依赖、启动配置路径和环境运行独立的配置解析诊断，两个值均实际解析为 `7d`，来源为 `backend.properties`，未被导入的三十分钟默认值覆盖。该诊断在环境准备完成后退出，没有启动业务或重启服务。旧页面头像不代表会话有效；当时 Redis 已无会话。

M-17 用户刷新并重新登录、手动上传后，Redis 中实际新会话的 `maxInactiveInterval=604800` 秒，确认服务端七天超时已生效；采样时会话 hash TTL 为 604962 秒（含过期清理宽限）。只记录时长，没有输出会话 ID。浏览器 Cookie 的实际 Expires/Max-Age 和属性未独立核验；原生浏览器操作期间用户正使用其他标签页，未继续争用窗口。

监督意见 M-18 关闭浏览器属性的人工核验待办：原有 Cookie 属性配置未变，服务端七天时长已实测，不再占用用户窗口。最终登录保持情况由用户于 2026-11-01 确认。该安排不是已经完成跨日验证，也不将未读取的浏览器属性标为实测通过。
