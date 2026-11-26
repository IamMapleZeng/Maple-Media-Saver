# v1.0.0 验证记录

日期：2026-11-26（Asia/Shanghai）。

- `:app:testDebugUnitTest`：5 项测试通过；`:app:assembleRelease`：构建成功，包含 Release 静态检查。
- JUnit：5 项，失败 0，错误 0，跳过 0。覆盖操作结束恢复、嵌套、跨线程隔离、异常恢复和版本校验。
- 从连接手机提取的实际 X APK：适配涉及的类、方法签名、字段、订阅实现和两个会员判断调用点静态校验通过。
- APK：模块入口及实现类存在，Xposed API 实现类未被打包；模块未请求额外权限。
- `apksigner verify`：APK v3 签名验证通过，签名证书与此前安装版本一致。
- 包名：`io.github.iammaplezeng.xvideosaver`；显示名称：`XVideoSaver`；版本：`1.0.0` / `10000`。
- APK SHA-256：见 Release 附件 `SHA256SUMS.txt`。
- 测试签名证书 SHA-256：`63928b7f98bdf60e3be741f8807bd6ae4370e2451ccb88f2b6f7e8446f6f2c62`。

此前 0.1.0 已成功通过 adb 安装。尚未完成 LSPosed 启用后的真机下载播放验收。版本号 v1.0.0 不代表这些运行测试已经完成。

验收时应确认：READY 日志、两个入口的 tap/eligibility 日志、无会员弹窗、下载完成、视频和音频可播放。X 更新后需重新适配。
