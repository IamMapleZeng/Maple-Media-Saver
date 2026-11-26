# X、TG 媒体下载器 (`XTGMediaSaver`)

<p>
  <img src="docs/icon.png" width="112" alt="XTG Media Saver icon" />
</p>

[English](README.md) | **简体中文**

保存 X 和 Telegram 中的图片与视频。（Save photos and videos from X and Telegram.）

当前版本：**v1.1.0** — [下载 APK](https://github.com/IamMapleZeng/XTGMediaSaver/releases/tag/v1.1.0)

## 适配版本

本模块基于真机 APK 精确静态分析实现（宿主更新导致版本不匹配时，对应平台自动停用 Hook 以保证稳定）：

| 平台 | 包名 | 适配版本 | Version Code |
| --- | --- | --- | --- |
| **X (Twitter)** | `com.twitter.android` | `12.46.1-prod.01` | `312461001` |
| **Telegram** | `org.telegram.messenger` | `13.3.1` | `73461` |

## 功能特性

### X (Twitter)
- **视频 / GIF 下载**：保留 X 原有的长按菜单与原生下载器，在信息流长按菜单及视频页下载时自动通过本地会员检查。
- **图片帖子一键下载全部图片**：长按图片帖子时，在原生 `Save Photo` / 保存图片选项正下方新增“下载全部图片”（英文系统下显示为 `Save All Photo`，语言跟随系统），点击后自动后台请求原图（`name=orig`）并保存至相册 `Pictures/XTGMediaSaver`。
- **图文混合帖子双选项**：同时包含图片和视频的帖子，长按菜单会同时提供“下载视频”与“下载全部图片”两个选项；纯图片帖子则只提供图片保存选项，不出现“下载视频”。

### Telegram
- **受限群组 / 频道保存媒体**：解除禁止保存内容（`noforwards`）的群组与频道限制，在消息菜单与全屏查看器（`PhotoViewer`）中恢复 Telegram 原生的保存图片与保存视频选项。

### 应用界面
- **Material 3 样式**：支持浅色 / 深色主题、Edge-to-Edge 沉浸式布局与 Android 12+ 动态取色。
- **多语言名称**：根据手机系统语言自动显示应用名（中文 `X、TG 媒体下载器`，英文 `XTG Media Saver`）。
- **双平台独立配置**：顶部实时显示 LSPosed 模块激活状态，X 与 Telegram 各自拥有独立配置卡片，可按需分别开关各项功能。

## 安装与使用

1. 前往 [GitHub Releases](https://github.com/IamMapleZeng/XTGMediaSaver/releases/tag/v1.1.0) 下载并安装 `XTGMediaSaver-v1.1.0.apk`。
2. 在 **LSPosed** 中启用模块，作用域勾选 **X**（`com.twitter.android`）和 **Telegram**（`org.telegram.messenger`）。
3. 强行停止并重新打开 X 或 Telegram。
4. 开始使用：
   - **X**：长按视频点击“下载视频”（或在视频页菜单下载）；长按图片帖子点击 `Save Photo` 下方的“下载全部图片 / Save All Photo”即可批量保存全部原图。
   - **Telegram**：在禁止保存的群组/频道中点击图片或视频消息菜单，或在全屏预览界面点击右上角菜单即可直接保存图片或视频。

## 隐私与权限

- 仅在作用域对应的宿主进程内运行。
- 不包含任何网络统计上传、账号读取、后台常驻服务，也不请求额外存储权限（Android 10+ 使用 `MediaStore` 保存图片）。

## 构建

需要 **JDK 17**、**Android SDK 35**、**Gradle 8.10+**。在 `local.properties` 中配置 `sdk.dir` 后运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

构建产物位于 `app/build/outputs/apk/release/app-release-unsigned.apk`，使用 Android SDK 的 `apksigner` 签名后即可安装。

`app/libs/xposed-api-82.jar` 仅作 `compileOnly` 使用，不打包入 APK（详见 [THIRD_PARTY.md](THIRD_PARTY.md)）。
