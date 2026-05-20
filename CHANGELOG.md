# Changelog

All notable changes to OffScan will be documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This file is automatically maintained by [release-please](https://github.com/googleapis/release-please) — please do not edit it manually.

## [1.2.0](https://github.com/99103248/OfflineScanner/compare/v1.1.0...v1.2.0) (2026-05-20)


### Features

* document-to-image conversion and PDF layout options (v1.2.0) ([25aea56](https://github.com/99103248/OfflineScanner/commit/25aea560eb034d882350d75ee89f36ad8ae8fc52))

## [1.2.0](https://github.com/99103248/OfflineScanner/compare/v1.1.0...v1.2.0) (2026-05-20)

### Features

* **文档转图片**：支持 PDF、Word（.docx）、TXT 及图片，导出为 JPG / PNG / WebP（完全离线）
* **多页 PDF 排版**：可选择「多张图片」（每页一张）或「一张长图」（纵向拼接）
* 工具箱「格式转换」界面升级：统一文件选择器，多文件结果列表与分享

### Tests

* 新增 `FileToImageIntegrationTest`（PDF / Word / TXT / 多页与长图）
* 新增 `OoxmlReaderTest`（docx 文本提取）
* 模拟器 API 34 完整仪器回归：19/19 通过

## [1.1.0](https://github.com/99103248/OfflineScanner/compare/v1.0.1...v1.1.0) (2026-04-30)


### ⚠ BREAKING CHANGES

* bump baseline to compileSdk 35 / Kotlin 2.1 / Compose 2025.05

### Features

* bump baseline to compileSdk 35 / Kotlin 2.1 / Compose 2025.05 ([f1e5082](https://github.com/99103248/OfflineScanner/commit/f1e50829e8bbbc56300fe783942dadb6da6a789c))


### Miscellaneous Chores

* target v1.1.0 for the SDK upgrade release ([3cab6cb](https://github.com/99103248/OfflineScanner/commit/3cab6cb75fdaf48a44f55545ce198cc7c1464c2a))

## [1.0.1](https://github.com/99103248/OfflineScanner/releases/tag/v1.0.1) (2026-04-30)

### Features

* First publicly signed release of OffScan
* On-device OCR (PaddleOCR PP-OCRv4 for Chinese, Google ML Kit for Latin)
* Edge detection + perspective correction (OpenCV)
* Image filters (enhance, BW, shadow removal)
* Export to PDF / Word / Excel / TXT
* Image format conversion (JPG / PNG / WebP)
* Custom export directory via Storage Access Framework

### Continuous Integration

* GitHub Actions CI: compile + lint + JVM unit tests on every push/PR
* Nightly Android instrumentation tests on a real API 34 emulator
* Automatic signed release APK on `v*` tag pushes
