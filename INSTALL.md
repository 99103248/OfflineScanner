# 安装与运行

> 想要直接安装编译好的 APK，或者从源码自己构建后安装到手机，都看这一篇。

---

## 系统要求

- **Android 10 (API 29) 或更高**
- **64 位 ARM CPU**（绝大多数 2018 年后的 Android 手机都是）
- 至少 **200MB 可用空间**（APP 解压后约 250MB）

> 项目默认 `abiFilters = ["arm64-v8a"]`，仅打包 64 位 ARM 以减小 APK 体积。如果需要 x86_64 模拟器或老 32 位机型，请按下文「构建变体」一节调整。

---

## 方式一：下载预编译 APK（最简单）

> 如果项目维护者发布了 Release，到 [GitHub Releases](https://github.com/) 页面下载最新的 `app-release.apk` 即可。

下载到电脑后选择下面任一方式装到手机：

### A. 直接拷贝安装

1. 用数据线连接手机和电脑（手机选择「文件传输」模式）
2. 把 APK 拷到手机的「下载」目录
3. 在手机上打开**文件管理器** → 找到 APK → 点击安装
4. 第一次会提示「未知来源」，请到**设置 → 应用与权限 → 特别权限 → 安装未知应用**里允许文件管理器安装应用
5. 安装完成

### B. 使用 ADB 安装

需要先在手机开启 USB 调试（**关于手机** → 连续点 7 次「版本号」→ 回开发者选项打开「USB 调试」）。

```bash
# 验证手机已连接
adb devices

# 安装 APK
adb install -r path/to/app-release.apk
```

---

## 方式二：从源码构建并安装

### 1. 准备环境

跟着 [SETUP.md](./SETUP.md) 安装 JDK 17 + Android SDK + Android Studio。

### 2. 命令行构建

在项目根目录执行：

```bash
# Linux / macOS
./gradlew assembleDebug

# Windows (PowerShell)
.\gradlew.bat assembleDebug
```

输出位置：

```
app/build/outputs/apk/debug/app-debug.apk
```

如果想要更小体积、含 R8 优化的 release 包：

```bash
./gradlew assembleRelease
```

> **注意**：`assembleRelease` 默认产出 unsigned APK，不能直接安装。你需要：
> - 自己生成签名证书（`keytool`），或
> - 在 Android Studio 中通过 **Build → Generate Signed Bundle / APK** 引导签名

### 3. 安装到设备

```bash
./gradlew installDebug
```

或手动用 adb：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 首次启动须知

1. **首次启动会请求几个权限**，全部允许：
   - 相机（拍照扫描需要）
   - 读取相册（从相册选图需要）
2. **首次启动会有 2-3 秒的初始化时间**：
   - OpenCV native library 加载
   - RapidOCR 模型解压（约 15MB）+ ONNX Runtime 初始化
3. 看到 Material 3 风格的「我的文档」首页就表示成功了

---

## 功能自测清单

按这个顺序试一遍，能确认全部功能正常：

- [ ] **首页空状态**：第一次打开应该显示「还没有扫描过任何文档」
- [ ] **拍照扫描**：点扫描 → 拍照 → 自动检测四角 → 拖动调整 → 选「增强」滤镜 → 保存
- [ ] **从相册选图**：在拍照页左下角点相册图标 → 选张图
- [ ] **OCR 中文**：扫描一段中文文本 → 详情页 → 点「图片转文字」→ 选「中文」→ 看 RapidOCR 识别结果
- [ ] **OCR 英文**：换张英文文档试试 → 选「英文」→ 看 ML Kit 识别结果
- [ ] **OCR 自动**：选「自动」，会先 ML Kit 跑一遍，再决定要不要叫 RapidOCR
- [ ] **导出 PDF / Word / Excel**：详情页右上角分享图标 → 选对应格式
- [ ] **自定义导出目录**：底部 Tab 切到「我的」→「选择导出目录」→ 在系统选择器里选一个文件夹 → 重新导出，文件应落到该位置
- [ ] **图片格式转换**：底部 Tab 切到「工具」→「图片格式转换」→ 选 JPG → PNG
- [ ] **去阴影滤镜**：拍一张光线不均的纸张 → 选「去阴影」滤镜 → 看效果

---

## 卸载

**设置 → 应用 → OffScan → 卸载**，或长按桌面图标拖到「卸载」。

> 卸载会删除 APP 和应用私有目录里所有的扫描文档。
> 如果你**自定义过导出目录**（在 SAF 选择器里选过位置），那个目录里的导出文件**不会**被卸载操作删除——它们由系统文件管理器管理，需要你自己清理。

---

## 构建变体

`app/build.gradle.kts` 的 `defaultConfig.ndk.abiFilters` 决定打哪些 CPU 架构：

```kotlin
ndk {
    // 默认仅 64 位 ARM（覆盖 95%+ 现代 Android 手机）
    abiFilters += listOf("arm64-v8a")

    // 如果想支持 x86_64 模拟器，加：
    // abiFilters += "x86_64"

    // 如果想支持 32 位老机型，加：
    // abiFilters += "armeabi-v7a"
}
```

加架构会显著增大 APK 体积（OpenCV native 库每多一个 ABI 多约 30MB）。

如果想用 Android App Bundle（.aab）按设备分发，跑：

```bash
./gradlew bundleRelease
```

输出：`app/build/outputs/bundle/release/app-release.aab`，上传到 Google Play 时会自动只下发用户设备需要的 ABI。

---

## 常见问题

### Q1：APK 体积偏大（70MB+）

主要是 OCR 模型 + native 库。压缩思路：
- 启用 `assembleRelease`（R8 + 资源压缩）
- 限定单一 ABI（已默认 `arm64-v8a`）
- 不需要英文 OCR 的话，可以从 `app/build.gradle.kts` 移除 `mlkit-text-recognition` 让所有识别都走 RapidOCR
- 用 App Bundle 上架商店

### Q2：RapidOCR 首次识别慢（3-5 秒）

模型加载到内存一次后，后续识别都是 1-2 秒。这是正常现象，不是 bug。

### Q3：APP 启动后崩溃，提示 OpenCV 加载失败

**原因**：极少数情况下，模拟器架构与 OpenCV native 不匹配。

**解决**：默认配置只支持 `arm64-v8a`。如果你用 x86_64 模拟器，参考上文「构建变体」加上 `x86_64` 后重新构建。

### Q4：某些手机相机预览有黑屏

CameraX 在小米 / 华为 / vivo 某些定制 ROM 上偶有兼容性问题，杀进程重开就好。后续可考虑 fallback 到 Camera2 原生 API。

---

## 调试日志

如果遇到问题想看日志：

```bash
adb logcat -s ScannerApp:* RapidOCR:* OpenCV:* PaddleOcrEngine:* EdgeDetector:*
```

提 issue 时附上相关日志会让排查快很多。
