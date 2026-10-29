# COS 公共图片全球加速

## 作用范围

当后端服务器连接公共桶的地域入口超时，而全球加速入口可达时，可以将公共桶的 COS SDK 请求改走全球加速。浏览器能够展示已有图片，并不能证明后端服务器到 COS 的网络正常；两者是不同的访问链路。

- 保留真实 `cos.client.region`，不要把地域改成 `accelerate`。
- 只对 `cos.client.bucket` 指定的公共桶切换 SDK endpoint。
- 私有桶默认仍走地域入口，可按下文独立开关显式启用加速；其他桶和 GetService endpoint 不变。
- 数据库中原图、压缩图、缩略图的展示 URL 不改写，历史图片不需要迁移。
- 不修改桶 ACL、存储桶策略、访问凭据或私有存储开关。

## 启用

1. 在腾讯云为指定公共桶开通全球加速，确认配置为 `Enabled`；加速流量有额外费用。
2. 验证部署服务器能够对 `<bucket>.cos.accelerate.myqcloud.com` 完成 DNS、TCP 443 与 TLS 证书校验。
3. 使用示例配置时设置 `COS_ACCELERATE_ENABLED=true`。自定义部署配置需显式添加以下属性，不能假定该环境变量已经自动映射：

   ```yaml
   cos:
     client:
       accelerate-enabled: true
   ```

4. 重启后端并检查健康状态，再用管理员完成一次小图片上传、信息保存、公开列表展示和详情原图加载。
5. 验证自动生成的 WebP 缩略图可访问，旧图片仍正常。只有健康检查成功，不代表上传链路已经通过。

默认开关为 `false`。开启但未指定公共桶时，应用启动会快速失败。

## 日志安全

本项目使用的 COS SDK 5.6.227 在部分网络错误日志中可能携带请求签名。不要直接收集或公开原始请求头。当前部署对相关类关闭日志，同时保留应用层的业务错误记录：

```yaml
logging:
  level:
    com.qcloud.cos.http.DefaultCosHttpClient: 'OFF'
```

排障记录只保留错误类别、耗时和脱敏后的状态，不记录 SecretId、SecretKey、Authorization、Cookie 或完整签名 URL。

## 回退

将 `cos.client.accelerate-enabled` 改回 `false` 并重启，即可恢复公共桶 SDK 的地域入口。也可以通过既有发布脚本恢复上一版本 JAR 与配置；不需要回滚数据库或删除图片。

如果地域入口仍然不可达，关闭加速会再次导致上传失败。回退前应先验证网络，不能用增加重试次数代替连通性检查。应用开关和腾讯云桶加速状态相互独立：应用关闭不会自动暂停桶加速；需要停用云侧功能时另行确认并将状态设为 `Suspended`。

## 回归范围

`CosClientConfigTest` 覆盖默认地域入口、公共桶专用加速、其他桶隔离、真实地域与 HTTPS 保持、缺失公共桶配置时拒绝启动。真实部署还需按上述流程验收；单元测试不会调用真实 COS。

## 独立私有桶加速

`picture.private-storage.accelerate-enabled` 默认 `false`，不会因公共桶开启加速而隐式开启。只有该开关为 `true` 时，`picture.private-storage.bucket` 指定的桶才使用全球加速端点；公共桶继续遵循自己的开关，其他桶仍走地域端点。

```yaml
picture:
  private-storage:
    enabled: true
    bucket: ${PICTURE_PRIVATE_STORAGE_BUCKET:}
    accelerate-enabled: true
```

示例配置也支持 `PICTURE_PRIVATE_STORAGE_ACCELERATE_ENABLED`。开启前须在云端为独立私有桶启用全球加速，并从生产服务器验证新凭据的私有上传、读取、对象 ACL 设为 private、删除，以及公共桶既有对象读取和临时对象上传、删除。全部通过才替换共享凭据。缺少私有桶配置时开启加速会拒绝启动。

私有读取、上传、删除和派生对象 ACL 操作复用该 COSClient；数据库仍保存地域形式的内部引用，私有图片经应用鉴权代理返回，不向客户端重定向到 COS。加速开关不会放宽 ACL，也不能代替拥有者、未授权成员、撤权后的访问验收。

2026-10-30：7e8735b 已发布，生产私有存储和私有桶加速显式开启；加速端点完整权限预检通过，匿名 COS 测试对象读取返回 403。应用三个私有变体入口匿名访问返回业务码 40100。真实私有图片的身份矩阵与 README 截图尚待登录后验收，不能据此宣称阶段 6 全部完成。

新增回归同时覆盖公共桶加速开关两种状态下的私有桶显式加速、第三桶隔离，以及缺私有桶配置时拒绝启动；原默认不隐式加速测试保留。
