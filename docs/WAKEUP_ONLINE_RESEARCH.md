# WakeUp 分享口令兼容研究

研究日期：2026-09-17。以下为当时的观察，不代表服务端持续保证。

## 主要来源

- [WakeUpDecoder](https://github.com/airline233/WakeUpDecoder)：协议算法与公开客户端配置，Apache-2.0。
- [WakeUpDecoder Worker 分支](https://github.com/airline233/WakeUpDecoder/tree/cloudflare-worker)：公共兼容身份路径。
- [CourseFlow](https://github.com/GreyWolf1101/CourseFlow)：另一个公开客户端的兼容策略参考。

## 结论

普通独立设备身份可完成握手，但取课表接口可能返回 410004（设备拒绝）。
公开实现使用的全零兼容身份与 6.4.0 配置曾成功取得并解密原生课表数据。
Schedule 将其实现为默认关闭、用户主动开启的实验选项，直接访问官方 HTTPS 接口。
没有静默身份轮换，也不把口令发送到第三方 Worker。

原生课表可能附带超过实际课程需要的无效尾部作息。解析器仅保留连续有效前缀，
并校验所有课程引用的节次均存在；实际被课程引用的无效作息会明确拒绝。

仓库不包含私人课表、分享口令、网络响应或官方 APK。
