# OffScan

> 一款 100% 离线运行的 Android 文档扫描应用，所有图像处理、文字识别和文档导出全部在手机本地完成，**不向任何服务器上传任何数据**。

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-green.svg)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-orange.svg)](https://developer.android.com/jetpack/compose)

> **新手须知**：第一次跑这个项目，请先看 [SETUP.md](./SETUP.md) 完成开发环境搭建。

---

## 功能列表

- 📷 拍照扫描 / 从相册选图
- ✂️ **文档边缘自动检测**（OpenCV Canny + 轮廓查找）+ 四角点拖拽调整
- 📐 **透视矫正**（OpenCV `warpPerspective`）
- 🎨 滤镜：原图 / 增强 / 灰度 / 黑白 / **去阴影**（OpenCV 自适应背景估计）
- 🔤 **OCR 文字识别**：
  - **中文**：RapidOCR（PaddleOCR PP-OCRv4 模型 + ONNX Runtime）
  - **英文**：Google ML Kit
  - **AUTO**：自动判断中英比例并选择更合适的引擎
- 📚 多页文档管理（Room 本地数据库）
- 📤 导出为 **PDF / Word(.docx) / Excel(.xlsx) / TXT**
- 🖼️ 图片格式互转（JPG / PNG / WebP）
- 📁 **自定义导出目录**：通过 SAF（Storage Access Framework）选择任意位置（Download、文件管理器自建文件夹等）
- 🔗 系统分享面板分享文件
- 🌗 Material Design 3 UI（自适应深色模式 + Material You 动态取色）

---

## 设计原则

| 原则 | 落地方式 |
|---|---|
| **完全离线** | `AndroidManifest.xml` 中显式 `tools:node="remove"` 掉 `INTERNET` 与 `ACCESS_NETWORK_STATE` 权限，APP 在运行时根本无法访问网络 |
| **零数据收集** | 不嵌入任何分析 SDK、不接 Crashlytics / Firebase / 友盟之类 |
| **隐私优先** | 所有图片、识别结果、数据库都在应用私有目录；导出文件由用户自己控制位置 |
| **可审计** | 整个 codebase 开源、所有依赖都在 `gradle/libs.versions.toml` 集中可见 |

---

## 技术栈

| 维度 | 选择 |
|---|---|
| 平台 | Android 10+ (minSdk 29, targetSdk 34) |
| 语言 / UI | Kotlin + Jetpack Compose + Material 3 |
| 架构 | MVVM + Clean Architecture（data / domain / ui 三层） |
| 依赖注入 | Hilt |
| 本地数据库 | Room |
| 拍照 | CameraX |
| 边缘检测 / 透视矫正 / 去阴影 | OpenCV 4.10（Maven Central 官方包） |
| 中文 OCR | RapidOCR PP-OCRv4（模型已打包，无需联网下载） |
| 英文 OCR | Google ML Kit Text Recognition |
| PDF | Android 原生 `PdfDocument` |
| Word / Excel | 自实现轻量 OOXML Writer（无需 Apache POI） |
| 自定义导出位置 | Android Storage Access Framework |

**APK 体积预估**：debug 包 ~130MB，release 包（开启 R8 压缩）~70MB。

---

## 目录结构

```
app/src/main/
├─ java/com/scanner/offline/
│  ├─ ScannerApplication.kt               Hilt 入口 + OpenCV 初始化
│  ├─ MainActivity.kt
│  │
│  ├─ ui/                                  表现层（Compose）
│  │  ├─ theme/                            Material 3 主题
│  │  ├─ navigation/                       NavHost / 路由
│  │  ├─ AppRoot.kt                        底部导航宿主
│  │  └─ screen/
│  │     ├─ home/         文档库
│  │     ├─ tools/        工具箱
│  │     ├─ me/           我的 / 设置（导出目录配置）
│  │     ├─ camera/       拍照
│  │     ├─ crop/         边缘 + 裁剪
│  │     ├─ filter/       滤镜
│  │     ├─ ocr/          OCR 识别
│  │     ├─ document/     文档详情
│  │     └─ export/       导出 / 格式转换
│  │
│  ├─ domain/                              业务层
│  │  ├─ model/           Document / Page / OcrResult / Language ...
│  │  ├─ repository/      仓储接口
│  │  └─ usecase/         单一目的用例（扫描保存 / OCR / 导出 ...）
│  │
│  ├─ data/                                数据层
│  │  ├─ db/              Room（DocumentEntity / PageEntity / DAO）
│  │  ├─ repository/      DocumentRepositoryImpl
│  │  └─ storage/         StorageManager / ExportPreferences / ExportSink
│  │
│  ├─ engine/                              核心引擎（可独立替换）
│  │  ├─ ocr/             OcrEngine 抽象 + ML Kit + RapidOCR + 路由工厂
│  │  ├─ image/           边缘检测 / 透视矫正 / 滤镜（OpenCV）
│  │  └─ export/          PDF / OOXML / 图片格式
│  │
│  ├─ di/                                  Hilt Modules
│  └─ util/                                Bitmap / 时间 / 分享
│
└─ res/                                    Android 资源
```

---

## 快速开始

详细环境搭建步骤请见 [SETUP.md](./SETUP.md)。简版三步：

1. 安装 **JDK 17** + **Android Studio Hedgehog (2023.1.1)** 或更新版
2. Android Studio 打开本项目，等待 Gradle 自动同步（首次约 5–15 分钟，依赖约 200MB）
3. 接 Android 10+ 真机或开模拟器，点 Run

---

## OCR 引擎说明

```
用户选 "自动" 时的路由策略（OcrEngineFactory）：

suspend fun recognize(bitmap, AUTO):
    1) 先用 ML Kit 拉丁文识别一次
    2) 如果结果中 ASCII 占比 < 50%（说明文档主体是中文），
       再用 RapidOCR 中文识别一次，取字数更多的结果
    3) 否则直接返回 ML Kit 结果

用户手动选 "中文" → RapidOCR
用户手动选 "英文" → ML Kit
```

**扩展新语言**（例如日语、韩语）：

1. 在 `domain/model/Models.kt` 的 `Language` 枚举中增加
2. 在 `OcrEngineFactory.engineFor()` 中返回对应实现
3. 添加新的 `OcrEngine` 实现类（或在已有引擎里加 Recognizer）

UI 完全不需要改动。

---

## 自定义导出目录

进入 APP 底部导航的「我的」→ 点「选择导出目录」，会弹出系统目录选择器（SAF），可以选择手机上任意位置（包括 `Download/`、`Documents/`、SD 卡、其它已挂载的存储）。

选完后，所有 PDF / Word / Excel / 图片导出都会写到该目录。点「恢复默认」即可回到应用私有目录。

实现要点：
- 通过 `ACTION_OPEN_DOCUMENT_TREE` 拿 tree URI
- 调用 `takePersistableUriPermission` 持久化授权（重启 / 重装后失效需要重新授权）
- `ExportSink` 抽象屏蔽 SAF URI 与本地 File 的差异
- 不需要 `MANAGE_EXTERNAL_STORAGE` 等敏感权限

---

## 离线优势 vs 联网方案

| 维度 | 离线（OffScan） | 联网方案 |
|---|---|---|
| 隐私 | 数据从不离开手机 | 上传服务器 |
| 网络 | 无依赖 | 必须联网 |
| 成本 | 一次性 APK 体积 | 持续 API 调用 |
| 速度 | 取决于手机算力 | 取决于网络往返 |
| 功能 | 模型受限于本地体积 | 可调用大模型 |

---

## 开源协议

本项目以 **[Apache License 2.0](./LICENSE)** 协议发布。

- 你可以自由地：使用 / 复制 / 修改 / 合并 / 发布 / 分发 / 再许可 / 商用
- 你必须：保留版权与许可声明（见 `LICENSE` / `NOTICE`），并在分发修改版时声明已做出修改

详细的第三方依赖许可清单见 [THIRD_PARTY_LICENSES.md](./THIRD_PARTY_LICENSES.md)。

---

## 商标声明

> OffScan 是一个独立的开源项目，**与任何商业文档扫描产品（包括但不限于 CamScanner / 扫描全能王）均无任何关联、隶属或合作关系**。
> 项目名 "OffScan" 系作者自创，刻意避免使用任何已注册商标的字样组合。

---

## 贡献

欢迎贡献代码 / 修 Bug / 提 issue / 给 star。请先看 [CONTRIBUTING.md](./CONTRIBUTING.md) 了解贡献流程。

参与项目即表示你认可项目的 [行为准则](./CODE_OF_CONDUCT.md)。

---

## 相关链接

- 上游 OCR 模型：[PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) · [RapidOCR4j-Android](https://github.com/hzkitty/RapidOCR4j-Android)
- 计算机视觉：[OpenCV](https://opencv.org/)
- Google 在端侧机器学习：[ML Kit](https://developers.google.com/ml-kit)
