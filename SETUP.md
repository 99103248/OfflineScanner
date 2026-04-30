# 开发环境搭建指南

跟着这份文档操作，30 分钟左右就能把这个项目跑起来。

> 这是给 **Windows** 用户的版本（macOS/Linux 同理，路径换成对应的）。

---

## 第一步：安装 JDK 17

**为什么需要**：Android Gradle Plugin 8.7 要求 JDK 17，你当前系统装的是 JDK 8（不够用）。

**推荐做法**：装 **Eclipse Temurin 17**（OpenJDK 的官方发行版，免费）。

1. 打开 https://adoptium.net/zh-CN/temurin/releases/?version=17
2. 下载 Windows x64 的 `.msi` 安装包
3. 双击安装，**勾选「Set JAVA_HOME variable」**和「Add to PATH」
4. 安装完成后开一个新的 PowerShell 窗口，验证：
   ```powershell
   java -version
   ```
   应该看到 `openjdk version "17.0.x"`

> **注意**：如果你以前装过其他 JDK，新装 17 之后需要确认 `JAVA_HOME` 指向 17。可以在系统属性 → 环境变量里检查 `JAVA_HOME` 的值是不是 `C:\Program Files\Eclipse Adoptium\jdk-17.x.x.x-hotspot`。

---

## 第二步：安装 Android Studio + Android SDK

**为什么需要**：Android Studio 自带 Android SDK 管理器，配置 SDK 是 Android 项目能编译的前提。

1. 打开 https://developer.android.com/studio
2. 下载 Windows 版 Android Studio（推荐 **Hedgehog 2023.1.1** 或更新）
3. 安装时一路下一步，**首次启动会引导你下载 Android SDK**：
   - 选择标准安装即可，会自动下载 ~3GB 的 SDK 到 `C:\Users\<你的用户名>\AppData\Local\Android\Sdk`
4. 启动后在欢迎页 → More Actions → SDK Manager 里再补充安装：
   - **Android 14 (API 34)** —— compileSdk
   - **Android 10 (API 29)** —— minSdk
   - **Android SDK Build-Tools 34.0.0**
   - **Android SDK Platform-Tools**
   - **Android SDK Command-line Tools (latest)**

---

## 第三步：打开项目

1. 启动 Android Studio
2. **Open** → 选择本项目所在目录 → 确定
3. Android Studio 会自动开始 Gradle 同步，**首次同步会下载约 200MB 的依赖**：
   - Gradle 8.10.2 本体（~150MB）
   - OpenCV Android AAR（~107MB，但很多设备已有缓存）
   - RapidOCR aar + ONNX Runtime（~50MB）
   - 其他依赖（Compose / Hilt / Room / CameraX / MLKit）
4. 同步过程显示在底部状态栏，耐心等候 5–15 分钟。

> 如果中间报错 **"failed to download xxx"**，多半是网络问题（特别在国内）。
> 解决方案见下面的「常见问题」。

---

## 第四步：连接设备 + 运行

### 方案 A：用 Android 真机（推荐，速度快）

1. 手机进入开发者选项（**关于手机** → 连续点 7 次「版本号」）
2. 打开「USB 调试」
3. 用数据线连接电脑，第一次会提示授权调试
4. Android Studio 顶部设备选择栏应该出现你的手机型号
5. 点绿色 ▶️ Run 按钮

### 方案 B：模拟器

1. Android Studio → Tools → Device Manager → Create Device
2. 选 Pixel 6 或更新机型
3. System Image 选 **API 34 (Android 14)**，下载需 ~1GB
4. Finish 后点 ▶️ 启动模拟器
5. 等模拟器开机后，再点项目的绿色 Run 按钮

> **重要**：模拟器跑 OpenCV/OCR 会比较慢，**强烈建议用真机**。

---

## 常见问题

### Q1：Gradle 同步卡在 "Downloading" 不动

**原因**：国内访问 Maven Central / Google Maven 慢。

**解决**：在 `gradle.properties` 末尾加上代理（如果你在用代理）：
```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```
或者改用国内镜像：编辑 `settings.gradle.kts`，把 `mavenCentral()` 上面加一行：
```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
```

### Q2：构建时报 `Could not resolve org.opencv:opencv:4.10.0`

**原因**：OpenCV AAR 比较大（107MB），下载超时。

**解决**：耐心重试 1–2 次，或者用上面的代理/镜像。

### Q3：APP 启动后崩溃，日志显示 "OpenCV 加载失败"

**原因**：很少见，通常是模拟器架构与 OpenCV native 不匹配。

**解决**：项目已配置 `abiFilters = ["arm64-v8a", "armeabi-v7a"]`，绝大多数手机都支持。如果你用的是 x86_64 模拟器，把这行去掉重编：
```kotlin
// app/build.gradle.kts
ndk {
    abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")  // 加上 x86_64
}
```

### Q4：APP 启动慢（首次 5 秒以上）

**原因**：RapidOCR 首次加载要解压 ~15MB 模型 + 初始化 ONNX Runtime。

**说明**：这是**正常现象**，只发生在第一次。你可以观察 logcat 里 "RapidOCR 初始化成功" 的提示。如果想优化，可以把 RapidOCR 的初始化推迟到首次使用 OCR 时（修改 `PaddleOcrEngine` 的 `init` 块为 lazy）。

### Q5：识别准确率不够高

**对中文文档**：确保用「中文」或「自动」模式，会走 RapidOCR
**对英文文档**：用「英文」模式走 MLKit
**对手写体**：开源模型效果都有限，建议拍照清晰、光线均匀
**表格识别**：当前是按行 + 制表符的简单切分；要做真正的表格识别需要接入 PP-Structure 模型（README 有指引）

### Q6：APK 太大想压缩

打 release 包：
```bash
./gradlew assembleRelease
```
会启用 R8 + 资源压缩，体积可降到 50MB 左右。

如果还想更小，可以：
- 限定单一 ABI：`abiFilters = ["arm64-v8a"]`（只支持 64 位手机，体积减半）
- 用 App Bundle (`bundleRelease`) 上传 Google Play / 国内应用商店，会按设备下发对应 ABI

---

## 项目结构速查

```
OfflineScanner/
├─ build.gradle.kts                项目级构建
├─ settings.gradle.kts             模块声明 + 仓库
├─ gradle/
│  ├─ libs.versions.toml           ★ 所有依赖版本集中管理
│  └─ wrapper/
│     ├─ gradle-wrapper.jar        Gradle 启动 jar
│     └─ gradle-wrapper.properties 指定 Gradle 版本 (8.10.2)
├─ gradlew / gradlew.bat           命令行 wrapper 脚本
├─ app/
│  ├─ build.gradle.kts             ★ APP 模块构建（依赖、签名、混淆）
│  ├─ proguard-rules.pro
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/scanner/offline/  ★ 业务代码
│     └─ res/                       资源
├─ README.md                       项目介绍
└─ SETUP.md                         本文档
```

★ 标记的是你修改最多的文件。

---

## 命令行用法（不用 Android Studio）

如果你已经有 Android SDK，可以纯命令行编译：

```powershell
# 在项目根目录
$env:ANDROID_HOME = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"

# 同步 + 编译 debug 包
.\gradlew assembleDebug

# 输出位置：app\build\outputs\apk\debug\app-debug.apk

# 安装到已连接的设备
.\gradlew installDebug
```

第一次执行会下载 Gradle，需要联网且耐心等几分钟。

---

## 我搭好了，下一步呢？

跑起来之后建议这样体验：

1. **首页 → 右下角扫描按钮** → 拍一张文档照片 → 拖动四角 → 选滤镜 → 保存
2. **文档详情** → 「OCR 文字识别」按钮 → 看中英文识别效果
3. **文档详情 → 右上角导出** → 试试 PDF / Word / Excel 几种格式
4. **工具箱** → 图片格式转换 → JPG / PNG / WebP 互转

如果某一步出问题，把 **logcat** 里相关的红色错误贴出来，我帮你排查。
