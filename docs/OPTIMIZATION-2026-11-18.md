# 2026-11-18 全面优化、问题修复与功能升级记录

本次迭代在不增加任何网络权限、保持“100% 离线、零数据上传”设计原则的前提下，修复了数据一致性与交互流程中的潜在隐患，并对图像增强算法、版面坐标感知导出、相机实时预览与内存管理进行了全面升级。

## 一、高优问题与数据一致性修复

1. **修复删页重排后再追加页面覆盖已有图片文件的问题**：
   - `StorageManager` 生成页面图片文件（`newOriginalFile` / `newProcessedFile` / `newThumbnailFile`）时引入唯一 token（`${pageIndex}_${token}_proc.jpg`），与可变排序字段 `index` 解耦，避免删除前序页面并重排 `index` 后新追加页面覆写原有页面图片。
2. **修复页面图像编辑/旋转后缩略图与详情页缓存不刷新的问题**：
   - `UpdatePageImageUseCase` 每次保存写入新文件路径并自动清理旧版本处理图与缩略图，使 Compose 状态对比与 Coil 图片缓存立即感知变更。
   - `DocumentDao` 与 `DocumentRepository` 新增 `observeDocument(id)` 响应式数据流，`DocumentDetailViewModel` 实时订阅文档及页面变更；任何页面的新增、删除、重排、OCR 更新、图像修改均同步刷新父文档 `updatedAt` 时间戳。
3. **修复单页 OCR 界面无条件重跑识别覆盖用户校对结果的问题**：
   - `OcrViewModel.load()` 优先检查绑定页面是否已存在 `ocrText`，若有则直接加载展示已有文本与文本框坐标，仅在用户主动切换语言或点击顶部栏「重新识别」按钮时才重新执行推理。
   - `OcrScreen` 底部栏新增「导出 Word」操作，并对语言选择栏与底部操作栏增加横向滚动适配。
4. **修复工具箱（`ToolsScreen`）入口路由未区分及编辑文档页多余导出的问题**：
   - 工具箱中「图片转文字」选图后直达 `OcrScreen`；「图片转 PDF / Word / Excel」支持多选图片自动建档并直达对应格式的自动导出流程。
   - 从文档详情页进入 `ImageEditScreen` 保存时仅更新文档内页面，不再向外部导出目录重复生成冗余文件。

## 二、图像算法与核心功能升级

1. **版面坐标感知的可搜索 PDF 与 Excel 表格还原**：
   - Room 数据库平滑升级至 v3（含 `MIGRATION_2_3`），新增 `OcrBlockCodec` 持久化保存 OCR 识别出的 `TextBlock` 归一化坐标。
   - **可搜索 PDF（`DocumentExporter`）**：按每个 `TextBlock` 的真实归一化矩形原位绘制隐形文字层与字号缩放，实现 PDF 阅读器内精准原位框选与搜索。
   - **表格还原（`TableParser`）**：新增 `fromOcr()` / `fromBlocks()`，利用二维文本框的纵坐标重叠度聚类成行、横坐标排序分列，大幅提升扫描表格转 Excel 的行列对齐准确度。
2. **彩色去阴影滤镜、自适应黑白与多通道边缘检测**：
   - **彩色去阴影（`ImageFilter.REMOVE_SHADOW`）**：改为在 HSV 色彩空间对亮度通道（V）下采样 4 倍执行形态学闭运算 + 高斯模糊估计背景并归一化，在消除纸面阴影的同时完整保留红章、签名、彩笔批注等色彩信息，且处理速度提升数倍。
   - **自适应黑白（`ImageFilter.BLACK_WHITE`）**：采用 OpenCV 高斯局部自适应阈值二值化并与原灰度平滑融合，文字边缘更清晰自然。
   - **多通道边缘检测（`EdgeDetector`）**：支持灰度双阈值 + HSV 饱和度通道三路候选检测，将最小面积阈值下调至 8%，显著提升浅色桌面拍白纸、身份证件、小票与名片的检出率。
3. **相机实时文档边框预览、常亮手电筒补光、连拍胶片条与实时扫码**：
   - **相机增强（`CameraScreen`）**：接入 CameraX `ImageAnalysis` 实时绘制检测到的蓝色文档四边形轮廓；闪光灯按钮支持「关 / 自动 / 闪光 / 常亮手电筒（Torch）」四档循环切换；连拍模式底部新增已拍缩略图胶片条，支持单张移除。
   - **实时扫码（`BarcodeScreen`）**：除相册选图识别外，新增「相机实时扫码」取景模式，检出条码/二维码后自动停止预览并展示复制卡片。

## 三、性能、内存与工程化优化

1. **临时缓存自动清理**：
   - 各 UseCase 与工具页（连拍、追加页面、批量处理、长图拼接、PDF 工具、扫码）消费完 `cacheDir/tmp` 下的临时文件后立即清理，并在进入首页时后台清理超过 24 小时的过期残留缓存。
2. **PDF 工具流式处理与系统打印异步化**：
   - `PdfToolkit` 合并、拆页、抽页改为逐页流式渲染并立即回收 `Bitmap`（峰值内存仅占单页），渲染分辨率提升至最高 2200px 并预填白底。
   - `PrintUtils.onWrite()` 移至独立后台线程执行并响应 `CancellationSignal`，避免多页文档打印阻塞主线程。
3. **线程安全与混淆优化**：
   - `Time` 工具类改用线程安全的 `java.time.format.DateTimeFormatter`。
   - 收紧 `proguard-rules.pro` 中过宽的 UI 层 keep 规则，提升 Release 包 R8 压缩率。

## 四、测试与构建验证

- **JVM 单元测试（`testDebugUnitTest`）**：31 项全部通过（含新增二维坐标表格还原测试、`OcrBlockCodec` 序列化往返测试）。
- **Android 仪器测试编译（`compileDebugAndroidTestKotlin`）**：通过（含新增删页后追加页面不覆盖文件测试）。
- **Android Lint 检查（`lintDebug` / `lintVitalRelease`）**：0 Error 通过。
- **构建产物**：`assembleDebug` 与 `assembleRelease`（含 R8 混淆与资源压缩）全部通过。
