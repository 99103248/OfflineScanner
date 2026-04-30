# 贡献指南

感谢你对 OffScan 的关注！本文档说明如何向项目贡献代码、报告 bug 或提交功能建议。

参与本项目即表示你认可项目的 [行为准则](./CODE_OF_CONDUCT.md)。

---

## 报告 Bug

发 issue 之前，请先：

1. 在 [Issues](../../issues) 中搜索是否已有相同问题
2. 用最新版（`main` 分支或最新 release）复现一遍

提 issue 时请尽量提供：

- **复现步骤**（越具体越好）
- **预期行为** vs **实际行为**
- **设备信息**：Android 版本、手机型号、APK 来源（自构建 / 下载 release）
- **logcat 日志**（如有崩溃）：

  ```bash
  adb logcat -s ScannerApp:* RapidOCR:* OpenCV:* PaddleOcrEngine:* EdgeDetector:*
  ```

- **截图** 或 **录屏**（UI 类问题）

---

## 提交功能建议

在 [Issues](../../issues) 里开一个新的，标签选 `feature`，描述：

- **要解决的问题**（不是直接给方案）
- **现状**：现有功能怎么不够用
- **期望**：希望的最终用户体验

---

## 提交代码（Pull Request）

### 1. 开发流程

```bash
# 1. Fork 本仓库到你自己的账号
# 2. clone 你 fork 的仓库
git clone https://github.com/<your-username>/OfflineScanner.git
cd OfflineScanner

# 3. 创建特性分支
git checkout -b feature/short-description

# 4. 改代码、提交
git commit -m "feat: 简短描述要做什么"

# 5. push 并发起 PR
git push origin feature/short-description
```

### 2. 提交信息规范（Conventional Commits）

| 前缀 | 用途 | 示例 |
|---|---|---|
| `feat:` | 新功能 | `feat: 支持自定义导出目录` |
| `fix:` | 修 bug | `fix: 在某些手机上 OCR 首次启动崩溃` |
| `refactor:` | 重构（不改变外部行为） | `refactor: 抽出 ExportSink 抽象` |
| `docs:` | 仅改文档 | `docs: 修正 README 安装说明` |
| `test:` | 仅改测试 | `test: 增加 ExportPreferencesTest` |
| `build:` | 构建系统 / CI | `build: 升级 Hilt 到 2.52` |
| `chore:` | 其它杂项 | `chore: 清理 .gitignore` |

### 3. 代码规范

- **Kotlin**：遵循 [Kotlin 官方风格](https://kotlinlang.org/docs/coding-conventions.html)，已在 `gradle.properties` 中设置 `kotlin.code.style=official`
- **Android Lint**：PR 必须通过 `./gradlew lintDebug`
- **测试**：新增公共逻辑要有对应的单元测试（`src/test/`）或集成测试（`src/androidTest/`）；修 bug 推荐先写复现用的失败测试
- **架构**：保持 `data` / `domain` / `ui` 三层分离，新依赖统一加到 `gradle/libs.versions.toml`

### 4. 提交前 checklist

```bash
# 编译通过
./gradlew assembleDebug

# JVM 单元测试通过
./gradlew testDebugUnitTest

# Lint 通过
./gradlew lintDebug

# 真机 / 模拟器集成测试（可选，但有真机请跑）
./gradlew connectedDebugAndroidTest
```

### 5. PR 描述模板

发 PR 时请说明：

```
## 解决的问题
（链接到 issue 或用一两句描述）

## 改动内容
（高层次说明改了什么；不需要逐行）

## 验证方式
（你怎么测的？跑了什么命令？真机还是模拟器？）

## 截图 / 录屏（UI 类）
```

---

## 添加新依赖

新增任何第三方库前请先确认：

1. **许可证兼容**：必须是 Apache 2.0 / MIT / BSD / EPL（与本项目相容）
2. **加到 `gradle/libs.versions.toml`** 集中管理，禁止在 `app/build.gradle.kts` 里硬编码版本号
3. **更新 `THIRD_PARTY_LICENSES.md`** 增加该依赖的条目
4. **更新 `NOTICE`**（如果许可证要求保留版权声明）

如果新依赖会显著增加 APK 体积（> 5MB）请在 PR 中明确说明并讨论替代方案。

---

## 添加新 OCR 语言 / 引擎

参考 README 里「OCR 引擎说明」一节：

1. 在 `domain/model/Models.kt` 的 `Language` 枚举中加新值
2. 实现 `engine/ocr/OcrEngine` 接口（或扩展现有实现）
3. 在 `engine/ocr/OcrEngineFactory.engineFor()` 中加路由
4. 加单元测试（路由层）+ 集成测试（真实识别效果）

---

## 商标 / 命名

> 本项目刻意避开了任何已注册商标的字样组合。
> 提 PR 时请勿在 README、APP 名、应用图标中加入与已知商业产品（如 CamScanner / 扫描全能王 / Adobe Scan 等）相似的元素。

---

## 项目维护者

- 在合并 PR 前会运行完整 CI（lint + 单元测试）
- PR 通常 3-7 天内得到反馈；如长期无回复，欢迎在 issue 里 @ 维护者催一下

### 如何发版（仅维护者）

项目使用 [release-please](https://github.com/googleapis/release-please) 自动管理 CHANGELOG 和发布。**你几乎不需要手动操作 tag**：

1. 平时 merge PR 到 `main`（用规范的 conventional commit 前缀：`feat:` / `fix:` / `docs:` 等）
2. release-please bot 会自动持续维护一个 PR，标题类似 `chore(main): release 1.1.0`
3. 该 PR 包含：自动生成的 `CHANGELOG.md` 更新 + `version.txt` 版本号 bump
4. **你 merge 这个 PR** —— 会自动：
   - 打新 tag（`v1.1.0`）
   - 触发 [release.yml](./.github/workflows/release.yml)，构建签名 release APK 并上传到 GitHub Release

如果某个 commit **不应该出现在 CHANGELOG**（例如调整 README typo），用 `chore:` 前缀。

---

感谢你的贡献！🎉
