# 第三方说明

- Xposed API 82：编译接口 jar 来自官方 https://api.xposed.info/ ，上游 https://github.com/rovo89/XposedBridge ，Apache License 2.0。只作编译依赖，APK 不包含该接口实现。
- Android Gradle Plugin、JUnit 和 Gradle 为构建/测试依赖，按各自上游许可使用。
- 调研参考了 piko 的下载功能说明 https://github.com/crimera/piko ，本项目没有复制或打包其补丁代码。最终适配依据为用户手机提取的 X 实际 APK。
- X、Twitter、HyperOS、LSPosed 商标属于各自权利人，本模块是独立第三方项目。
