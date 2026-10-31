# 生产登录会话配置

2026-10-31 按用户授权 M-12、监督方顺序调整 M-13，仅修改生产配置并重启 `znytk-backend`。后端仍为 `2b6f3e0`，未重新发布前端或修改业务代码。

以下两项放在 `/srv/znytk-test/secrets/backend.properties`，由后端启动参数最后加载，后续 release 继续使用同一文件：

```properties
spring.session.timeout=7d
server.servlet.session.cookie.max-age=7d
```

会话空闲超时和浏览器 Cookie 有效期均设为七天；已有会话可能仍保留原来的三十分钟空闲超时。原 Cookie 配置保持 `Secure=true`、`HttpOnly=true`、`SameSite=Lax`、`Path=/tuku/api`。配置文件属主和权限仍为 `10001:10001`、`600`，不提交其内容。

变更前原文件已备份至 `/srv/znytk-test/backups/backend.properties.before-session7d-20261031`，备份仅 root 可读。回滚时在服务器内恢复这份原始配置，再只重启 `znytk-backend`；若此后有其他配置变更，应只移除上述两项，避免覆盖后来变更。不要输出备份内容，不恢复数据库或清空会话库。

重启后 `/health` 和 `/picture/capabilities` 均 HTTP 200、业务码 0；私有存储开启、外部搜图关闭。tc/cpa/codex/mb 响应分别为 200/200/404/200，与变更前一致。浏览器重新登录后的七天 Cookie 过期时间及属性核验仍待用户登录，不把配置核验写成浏览器实测通过。
