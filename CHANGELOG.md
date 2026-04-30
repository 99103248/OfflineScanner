# Changelog

All notable changes to OffScan will be documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This file is automatically maintained by [release-please](https://github.com/googleapis/release-please) — please do not edit it manually.

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
