# 2026-11-23 全面梳理、同类功能移植与质量加固记录

本次迭代在严格维持「100% 离线运行、零网络权限、零数据上传」设计原则的基础上，系统梳理了现有代码库中的潜在缺陷与内存隐患，调研主流文档扫描应用（扫描全能王、白描、夸克扫描王、Adobe Scan、Genius Scan、OSS Document Scanner）的高频离线能力，并按优先级完成了核心修复、实用功能移植与全链路自动化测试验证。

---

## 一、P0 缺陷修复与健壮性加固

1. **修复纯文本导出 PDF 长文截断问题（`DocumentExporter.textsToPdf`）**：
   - 修复单页内容超出 A4 高度时静默丢弃剩余行的问题，改为自动新建后续 A4 页面并连续编号。
2. **修复图像处理链中的 Bitmap 内存泄漏**：
   - 修复 `ImageTransform.perspectiveCrop()`、`CropViewModel.confirm()` 与 `ImageEditViewModel.save()` 中间产物未及时 `recycle()` 的隐患，降低高分辨率连续编辑的 OOM 风险。
3. **修复设置项「图片导出质量」与「默认滤镜」未全局生效的问题**：
   - `ExportDocumentUseCase`、`ConvertImageFormatUseCase`、`ConvertFileToImagesUseCase` 与 `ExportViewModel` 完整透传 `SettingsRepository.exportQuality`（默认 92，向后兼容）。
   - `SaveBurstPagesUseCase` 与 `DocumentDetailViewModel.appendImages()` 改为遵循用户在设置页配置的 `defaultFilter`，且仅在真实检出文档四边形（`detectQuadOrNull`）时执行透视裁切，避免误裁已裁好的图片。
4. **修复 OOXML 导出非法控制字符与多页分页符**：
   - `OoxmlWriter.escapeXml()` 自动过滤 XML 1.0 非法控制字符，防止 OCR 偶发异常字符导致 Word / Excel / WPS 无法打开导出文件。
   - 多页 Word（`.docx`）页间改用标准分页符 `<w:br w:type="page"/>`，同时 `OoxmlReader` 兼容新版分页符与历史空段落分页。
5. **修复中文 GBK/GB18030 纯文本转图片乱码（`TextCharsetDetector`）**：
   - 支持 UTF-8 / UTF-16LE / UTF-16BE BOM 识别与严格 UTF-8 校验，非 UTF-8 字节流自动回退 GB18030 解码。
6. **修复证件扫描竖拍裁剪失真与无边框误裁（`IdCardComposer`）**：
   - 改用 `edgeDetector.detectQuadOrNull()`，未检测到边框时完整保留原图；当证件图片为竖拍（高 > 宽 × 1.15）时自动顺时针扶正 90° 后排入 A4 横向卡槽。
7. **补齐关键交互防误操作与流程死胡同**：
   - `TrashScreen`：彻底删除与清空回收站均增加二次确认弹窗，防止误触导致不可逆数据丢失。
   - `FormatConvertScreen`：已选文件后提供「更换文件」按钮。
   - `BurstProcessScreen`：处理结束或出错时提供顶部返回按钮。
   - `StorageManager.clearAllCache()`：修复先删文件后判断 `isFile` 导致返回值恒为 0 的缺陷（仪器测试检出并已修复）。

---

## 二、P1 市面同类应用实用功能移植与体验升级

1. **裁剪页 2.5× 精准角点放大镜 + 快捷工具栏（`CropScreen` / `CropViewModel`）**：
   - 拖拽任意角点时，在画面顶部对侧实时绘制带十字准星的 2.5 倍圆形放大镜，解决手指遮挡文档边角痛点。
   - 底部新增「自动识别」「铺满」「逆时针 90°」「顺时针 90°」快捷按钮。
2. **相机自动稳定拍摄（`CameraScreen` / `QuadStabilityTracker`）**：
   - 新增「自动拍」开关，基于连续帧四边形角点最大位移与面积阈值判断画面稳定性，显示稳定进度条并自动触发快门（含触发冷却保护，避免重复连拍）。
3. **证件扫描防盗用斜向平铺水印与复印滤镜（`IdCardScreen` / `IdCardComposer`）**：
   - 新增「复印效果」切换（原图 / 增强 / 黑白 / 去阴影），一键生成干净黑白复印件。
   - 新增全屏斜向平铺防盗水印与常用快捷预设（如「仅供办理业务使用，他用无效」）。
4. **试卷/文档「去蓝红笔迹」滤镜（`FilterMode.REMOVE_HANDWRITING` / `ImageFilter`）**：
   - 基于 OpenCV HSV 色相与饱和度通道掩码（含纯像素降级实现），去除蓝色圆珠笔与红色批改笔迹，保留黑色印刷体与表格线。
5. **文档详情页全屏双指缩放查看器 + 单页无损重套滤镜（`DocumentDetailScreen` / `ApplyPageFilterUseCase`）**：
   - 点击任意页面卡片进入全屏大图查看器，支持双指缩放（1x–4x）、双击放大还原、拖拽平移、上下页切换及快捷触发滤镜/编辑/OCR。
   - 页面菜单新增「重新应用滤镜」，基于保存的 `originalPath` 无损重新生成处理图与缩略图。
6. **编辑器升级：亮度/对比度微调 + 多色电子签名 + 全屏平铺水印（`ImageEditScreen` / `ImageEditViewModel` / `ImageTransform`）**：
   - 新增「调色」模式（亮度 -80~+80、对比度 50%~200%）。
   - 升级「签名/批注」模式，支持黑签 / 蓝签 / 红批、细/中/粗三档相对线宽、撤销与清空，以及「全屏平铺」防盗水印（`WatermarkPosition.TILED`）。
7. **扫码工具结构化解析与补光手电筒（`BarcodeScreen` / `BarcodeContentParser`）**：
   - 实时扫码新增顶部手电筒补光开关。
   - 纯离线本地解析 Wi-Fi（支持转义分号并一键复制密码）、URL、邮箱、电话与 vCard 名片字段。
8. **PDF 工具升级 + 文档库快捷筛选 + 本地存储清理**：
   - `PdfPageRangeParser` 独立抽离，支持 `1-3, 5, 7-`、`-3`、反向区间及中文标点；`PdfToolScreen` 展示已选 PDF 页数并支持移除与上下排序。
   - `HomeScreen` 新增「全部 / 收藏」快捷筛选与文档总数显示，网格视图同步补齐三点快捷菜单。
   - `MeScreen` 新增已扫描文档/临时缓存空间统计与一键「清理缓存」。

---

## 三、自动化测试与验证结果

| 测试类型 | 范围与结果 |
|---|---|
| JVM 单元测试（`testDebugUnitTest`） | **9 个测试套件，48 项全部通过（0 失败，0 跳过）**（新增 17 项，覆盖页码范围解析、编码检测、自动拍稳定追踪、条码内容解析、OOXML 非法控制符与分页符兼容） |
| Android 仪器测试（Android 14 / API 34 `test34` 模拟器） | **10 个测试类，47 项全部通过（0 失败）**（新增 6 项，覆盖长文本 PDF 自动分页、竖拍证件扶正与平铺水印、去蓝红笔迹滤镜、调色/多色签名/平铺水印、单页无损重套滤镜、缓存体积与清理计数） |
| Android Lint（`lintDebug` / `lintVitalRelease`） | **0 Error 通过** |
| Release APK 构建（`assembleRelease`，arm64-v8a + R8 混淆与资源压缩） | **构建通过，离线权限校验无 `INTERNET` / `ACCESS_NETWORK_STATE`** |

