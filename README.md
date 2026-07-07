<p align="center">
  <img src="app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.webp" alt="Komgarot 图标" width="144" height="144">
</p>

<p align="center">
  简体中文 | <a href="README.en-US.md">English</a>
</p>

# Komgarot

Komgarot 是一个面向 Komga 服务器的 Android 漫画阅读客户端，使用 Kotlin 与 Jetpack Compose 构建。

> 欢迎在 issues 里提需求，因为我自己用 Komga 的时候很多功能也不会用到，最开始就不会考虑做

## 功能

- 登录 Komga 服务器
- 浏览书库、系列、收藏、阅读列表
- 系列搜索、作者搜索、排序、筛选
- 书籍详情、元数据查看与编辑
- 分页阅读、滚动阅读、阅读方向、页面适配、进度跳转
- AI 翻译：本地 PaddleOCR 检测、视觉模型翻译、缓存、重试、耗时和失败原因查看
- WebDAV ZIP 备份与恢复
- 长按页面保存、分享、设置为书籍或系列封面
- 隐私阅读、应用锁、保持亮屏
- 管理员入口：书库、用户、服务器设置、维护、活动

## 截图

<p>
  <img src="assets/screenshot/Screenshot_01.jpg" alt="Komgarot 截图 1" width="150">
  <img src="assets/screenshot/Screenshot_02.jpg" alt="Komgarot 截图 2" width="150">
  <img src="assets/screenshot/Screenshot_03.jpg" alt="Komgarot 截图 3" width="150">
  <img src="assets/screenshot/Screenshot_04.jpg" alt="Komgarot 截图 4" width="150">
  <img src="assets/screenshot/Screenshot_05.jpg" alt="Komgarot 截图 5" width="150">
  <img src="assets/screenshot/Screenshot_06.jpg" alt="Komgarot 截图 6" width="150">
  <img src="assets/screenshot/Screenshot_07.jpg" alt="Komgarot 截图 7" width="150">
</p>

## AI 翻译
> 此功能需要用户自己准备 OpenAI 兼容的 AI 服务和支持视觉的模型。**注意，此功能会消耗大量 Token。**
>
> 使用前需要在应用内下载本地 PaddleOCR 检测模型。应用会先在本地识别页面文字区域并生成遮罩，再把当前文字区域截图和页面上下文图发送给视觉模型翻译。
>
> AI 翻译支持串行和并发请求，默认串行；并发模式可配置请求数量。长按翻译按钮可以查看本页耗时统计和失败原因，重试本页会重新执行本页的 OCR、遮罩和翻译流程。
>
> 可按原文类型优化检测和提示词，包括自动、日漫竖排和韩文横排 Webtoon。翻译结果会按书本缓存，已缓存和任务列表支持清空与失效数据净化。

### 翻译效果预览
<p>
  <img src="assets/screenshot/Screenshot_AI_01_origin.jpg" alt="Komgarot AI翻译 1 原图" width="150">
  <img src="assets/screenshot/Screenshot_AI_01_translated.jpg" alt="Komgarot AI翻译 1 结果" width="150">
  <img src="assets/screenshot/Screenshot_AI_02_origin.jpg" alt="Komgarot AI翻译 2 原图" width="150">
  <img src="assets/screenshot/Screenshot_AI_02_translated.jpg" alt="Komgarot AI翻译 2 结果" width="150">
  <img src="assets/screenshot/Screenshot_AI_03_origin.jpg" alt="Komgarot AI翻译 3 原图" width="150">
  <img src="assets/screenshot/Screenshot_AI_03_translated.jpg" alt="Komgarot AI翻译 3 结果" width="150">
</p>

## WebDAV 备份与恢复

Komgarot 会在填写的 WebDAV URL 路径下创建 `Komgarot` 目录，并保存形如 `Komgarot_backup_20260706_170802.zip` 的备份包。URL 末尾缺少 `/` 时，应用会自动补全。

备份包包含：

- `app-settings.json`：App 设置、AI 服务配置、S3 图片 URL 配置和 API 密钥配置
- `ai-translate/`：按书本 ID 拆分保存的 AI 翻译 JSON 数据

恢复备份时会显示最新 5 个备份供选择。Komga 账号/服务器配置和 WebDAV 服务器/用户名/密码仅保存在本机。

## 环境

- Android 11+
- 可访问的 Komga 服务器
- JDK 11+
- Android Studio 或 Gradle 命令行环境

## 构建

Debug APK：

```powershell
.\gradlew.bat :app:assembleDebug
```

单元测试：

```powershell
.\gradlew.bat testDebugUnitTest
```

Lint：

```powershell
.\gradlew.bat :app:lintDebug
```

Release APK：

```powershell
.\gradlew.bat :app:assembleRelease
```

Debug 产物目录：

```text
app/build/outputs/apk/debug/
```


## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Retrofit + Gson
- OkHttp
- Coil
- DataStore Preferences
- AndroidX Security Crypto
- AndroidX Biometric

## 友链
[LINUX DO](https://linux.do)
