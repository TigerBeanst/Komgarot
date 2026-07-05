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
- AI 翻译
- 长按页面保存、分享、设置为书籍或系列封面
- 隐私阅读、应用锁、保持亮屏
- 管理员入口：书库、用户、服务器设置、维护、活动

## 截图

<p>
  <img src="assets/screenshot/Screenshot_01.jpg" alt="Komgarot 截图 1" width="180">
  <img src="assets/screenshot/Screenshot_02.jpg" alt="Komgarot 截图 2" width="180">
  <img src="assets/screenshot/Screenshot_03.jpg" alt="Komgarot 截图 3" width="180">
  <img src="assets/screenshot/Screenshot_04.jpg" alt="Komgarot 截图 4" width="180">
  <img src="assets/screenshot/Screenshot_05.jpg" alt="Komgarot 截图 5" width="180">
  <img src="assets/screenshot/Screenshot_06.jpg" alt="Komgarot 截图 6" width="180">
  <img src="assets/screenshot/Screenshot_07.jpg" alt="Komgarot 截图 7" width="180">
</p>

## AI 翻译
> 此功能需要用户自己准备 OpenAI 兼容的 AI 服务，且需使用支持视觉的模型
> 
> 生成速度和效果随模型而异，部分模型提供商可能因为对敏感内容较为严格，无法返回翻译结果，请自行选择
> 
> 为了速度和质量考虑，AI 翻译选择了每一句文本一次请求，请求中带有对话内容和完整图片，因此翻译一张图可能会产生较多消耗（个人平均一次请求2000+ Token）

### 翻译效果预览
<p>
  <img src="assets/screenshot/Screenshot_AI_01_origin.jpg" alt="Komgarot AI翻译 1 原图" width="180">
  <img src="assets/screenshot/Screenshot_AI_01_translated.jpg" alt="Komgarot AI翻译 1 结果" width="180">
  <img src="assets/screenshot/Screenshot_AI_02_origin.jpg" alt="Komgarot AI翻译 2 原图" width="180">
  <img src="assets/screenshot/Screenshot_AI_02_translated.jpg" alt="Komgarot AI翻译 2 结果" width="180">
</p>
<p>
  <img src="assets/screenshot/Screenshot_AI_03_origin.jpg" alt="Komgarot AI翻译 3 原图" width="180">
  <img src="assets/screenshot/Screenshot_AI_03_translated.jpg" alt="Komgarot AI翻译 3 结果" width="180">
  <img src="assets/screenshot/Screenshot_AI_04_origin.jpg" alt="Komgarot AI翻译 4 原图" width="180">
  <img src="assets/screenshot/Screenshot_AI_04_translated.jpg" alt="Komgarot AI翻译 4 结果" width="180">
</p>

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
