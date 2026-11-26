# v1.3.0 包名迁移

用户指定应用 ID 改为 `io.github.iammaplezeng.xtgytmediasaver`。同步迁移自维护 Kotlin package/import、源文件目录、namespace、Manifest 组件、ProGuard 和 xposed_init 入口。LSPosed 新入口为 `io.github.iammaplezeng.xtgytmediasaver.XTGMediaSaverModule`；宿主方法目标、Hook 顺序、作用域列表、配置键和媒体路径不变。

Android 无法用不同包名覆盖同一个应用。为保留已有设置，先用原签名覆盖安装旧 ID 的迁移桥 APK，再安装新 ID：

```powershell
.\gradlew.bat -PlegacyMigrationBridge=true :app:assembleRelease
# 用原密钥签名，adb install -r 旧包迁移桥；不要卸载旧包
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease :app:lintDebug
# 用同一密钥签名，adb install 新包，再启动新包 MainActivity
```

迁移桥构建仍用旧 applicationId，但 `xposedmodule=false`，不再注册为旧 LSPosed 模块。其只读 Provider 仅在桥构建启用，核对调用方必须是新包且 UID 签名相同；新包也核对来源签名。它只返回七个功能开关、系统保存的应用语言（Android 13+）及最近下载记录，不暴露通用文件访问或修改接口。正常新包中此 Provider 禁用。

新包首次启动，缺失的设置从桥补齐，不覆盖已存在的新包设置；完成后标记不再重复迁移。YouTube 去广告缺省值为开启，显式保存的关闭值保留。旧包私有数据、已保存媒体及 Git 历史不清除。下载文件不移动，仍可通过相册/文件管理器访问。Cookie 不自动迁移，如有需要由用户重新导入。迁移不授予新应用对旧包创建的全部 MediaStore 记录的所有权。

安装后，需在 LSPosed 重新启用新包，并勾选 X、Telegram、YouTube 作用域，随后强行停止并重开这些应用。旧应用可以保留用于迁移回退；未经用户要求不自动卸载或清除数据。
