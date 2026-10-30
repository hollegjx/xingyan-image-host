# 私有空间线上验收与配图

2026 年 10 月 30 日完成。验收与两张截图拍摄时，后端为 `7e8735b`，前端为 `a5eb976`。收尾时监督方于 20:17 上线前端第二批 `b340a56`；本次提交已先快进同步该提交，没有重新部署或覆盖界面改动。

## 访问验收

| 场景 | 实测结果 |
| --- | --- |
| 拥有者 | 两空间均可上传、保存图片信息；首张的原图、展示图、缩略图均实际加载成功 |
| 匿名 COS 直读 | 6 张图片 × 3 种变体，共 18 个对象均 HTTP 403 |
| 匿名应用读取 | 18 个请求均业务码 40100，data 为 null |
| 普通非成员 | 个人图 34、团队图 35 三种变体均业务码 40101 |
| 临时团队浏览者 | 图 35 三种变体均 HTTP 200，返回图片内容；Cache-Control 为 private, no-store，Vary 包含 Cookie |
| 团队撤权 | 移除本次测试成员后，原会话再次读取三种变体均立即返回 40101 |
| 隔离与清理 | 公共图库仍为 13 张且无私有记录；个人、团队各 3 张；公私文件清理队列均为 0 |

临时测试成员已撤回并退出会话，原创建者不变。测试账号 `2116137838037700609` 是本次验收新注册的普通账号，事前未单独征得确认，已按监督意见如实记录。用户随后明确选择保留作验收审计；该账号无图片、无团队权限，临时凭据与会话文件已删除，不再使用。不修改 AI 任务；核对前后整表摘要一致。匿名拒绝只阻止后续请求，无法收回已经下载的文件。

拥有者验收通过真实浏览器完成，普通账号与匿名验收经正常 HTTP 接口完成；没有从浏览器提取 Cookie，也没有伪造 Redis 会话或直接修改数据库授权。

## 配图来源

六张图复用 2026-10-28 公共图库配图时留存的 Wikimedia Commons CC0 许可记录；作者、来源和许可已写入各自的图片简介。本次不修改原公共图库中的图片。

| 空间 | 图片 ID / 名称 | 作者 | 来源与许可 |
| --- | --- | --- | --- |
| 日常灵感收藏 | 34 / 青山入雾 | Jay Mantri | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Mountain_Range_(Tumblr).jpg)，CC0 1.0 |
| 日常灵感收藏 | 38 / 海面微光 | Jay Mantri | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Sea_(Tumblr).jpg)，CC0 1.0 |
| 日常灵感收藏 | 39 / 花开正好 | Kurt Kaiser | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Flower_full_bloom.jpg)，CC0 1.0 |
| 星檐共创素材库 | 35 / 萨金特笔下的牧野 | John Singer Sargent / The Metropolitan Museum of Art | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Landscape_with_Goatherd_MET_DT2941.jpg)，CC0 1.0 |
| 星檐共创素材库 | 36 / 田园与牧人 | Robert Seldon Duncanson / The Metropolitan Museum of Art | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Landscape_with_Shepherd_MET_DT10421.jpg)，CC0 1.0 |
| 星檐共创素材库 | 37 / 海天之间 | Joselodos | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Sea_sky_and_clouds,_Ibiza,_Spain.jpg)，CC0 1.0 |

## 截图范围

`docs/images/personal-space.jpg` 和 `docs/images/team-space.jpg` 均在实际线上登录态拍摄，三张缩略图加载完成后保存，未修改 DOM 或拼接内容。README 只更新这两个同名文件与对应的 `<sub>` 说明，保留监督方其余结构和截图。

## 运维限制

只读 GetBucketLocation 已确认私有桶地域为 `ap-guangzhou`。当前公共客户端地域仍为 `ap-nanjing`，私有读写依赖独立显式加速开关；关闭私有加速会导致私有桶不可用。不要通过修改公共地域来修复，也不要放宽 ACL。详见 [COS 加速说明](cos-global-acceleration.md)。
