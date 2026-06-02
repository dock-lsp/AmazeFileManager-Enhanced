# Amaze File Manager - 增强版功能说明

## 📦 版本信息
- **版本号**: 3.12.0-Enhanced
- **版本代码**: 125
- **默认语言**: 中文（简体中文）

---

## 🎬 媒体功能

### 1. 内置音视频播放器
**文件位置**: `MediaPlayerActivity.java`, `VideoPlayerFragment.java`, `AudioPlayerFragment.java`

**功能特性**:
- ✅ 支持格式: MP4, MKV, AVI (视频); MP3, AAC, FLAC (音频)
- ✅ 手势控制: 滑动调节进度、音量、亮度
- ✅ 后台播放: 音频支持后台播放和通知栏控制
- ✅ 播放控制: 播放/暂停、上一首/下一首、循环、随机播放
- ✅ 屏幕锁定: 防止误触
- ✅ 全屏切换: 沉浸式观影体验

**使用方式**:
- 点击视频/音频文件自动使用内置播放器打开
- 支持从通知栏控制音频播放

---

### 2. 图片编辑器
**文件位置**: `ImageEditorActivity.java`

**功能特性**:
- ✅ 裁剪: 自由比例、1:1、3:4、16:9 固定比例
- ✅ 旋转: 90°、180°、270° 旋转，水平/垂直翻转
- ✅ 滤镜: 黑白、复古、模糊、锐化、反色
- ✅ 标注: 画笔涂鸦、添加文字、马赛克、橡皮擦
- ✅ 调节: 亮度、对比度实时调节
- ✅ 撤销/重做: 完整的操作历史管理
- ✅ 保存: 支持覆盖原文件或另存为新文件

**支持格式**: JPG, PNG, WEBP, GIF, BMP

**使用方式**:
- 长按图片文件 → 选择"编辑图片"

---

### 3. PDF工具
**文件位置**: `PdfViewerActivity.java`, `PdfEditorActivity.java`, `PdfAnnotationActivity.java`

**功能特性**:
- ✅ PDF阅读: 缩放(0.5x-3.0x)、滚动浏览、页面导航
- ✅ PDF标注: 高亮、下划线、删除线、批注、手绘
- ✅ PDF合并: 将多个PDF合并为一个
- ✅ PDF拆分: 按页码范围拆分PDF
- ✅ PDF转图片: 将PDF页面转为PNG/JPG
- ✅ 文本提取: 提取PDF文本内容

**使用方式**:
- 点击PDF文件打开阅读器
- 在阅读器菜单中选择编辑或标注功能

---

## 💻 开发者功能

### 4. 代码编辑器增强
**文件位置**: `TextEditorActivity.java`, `SyntaxHighlighter.java`, `GitIntegration.java`, `CodeFoldingManager.java`

**功能特性**:
- ✅ 语法高亮: 支持 Java, Kotlin, Python, JavaScript, HTML, CSS, XML, JSON, C/C++, Markdown
- ✅ 代码折叠: 按代码块折叠/展开（函数、类、if/for等）
- ✅ Git集成: 
  - 显示文件Git状态（修改、新增、删除）
  - 查看文件修改历史
  - 显示行级别的修改标记
  - 简单的Diff查看器
- ✅ 行号显示: 左侧显示行号
- ✅ 自动缩进: 基于当前行缩进级别
- ✅ 括号匹配: 点击括号高亮匹配对
- ✅ 搜索替换: 支持正则表达式、大小写敏感选项

**使用方式**:
- 点击代码文件自动使用增强编辑器打开

---

### 5. 终端模拟器
**文件位置**: `TerminalActivity.java`, `TerminalFragment.java`, `TerminalSession.java`

**功能特性**:
- ✅ 多标签页: 支持同时打开多个终端会话
- ✅ 颜色主题: 暗黑、亮色、纯黑三种主题
- ✅ 字体调整: 支持 8-24sp 范围调整
- ✅ Root权限: 支持普通用户和root模式切换
- ✅ 快捷键支持:
  - Ctrl+C - 中断当前命令
  - Ctrl+D - 发送 EOF
  - Ctrl+L - 清屏
  - Ctrl+V - 粘贴
  - Up/Down - 命令历史
  - Tab - 缩进
- ✅ 命令历史: 保存最近 100 条命令
- ✅ ANSI颜色: 正确显示终端颜色代码
- ✅ 会话保存: 保存字体大小和主题设置

**使用方式**:
- 侧边栏 → 工具 → 终端
- 或在文件管理器菜单中选择"打开终端"

---

### 6. 日志查看器
**文件位置**: `LogViewerActivity.java`, `LogAdapter.java`, `LogEntry.java`, `LogFilter.java`

**功能特性**:
- ✅ 实时日志: 使用 logcat 捕获系统日志
- ✅ 级别筛选: Verbose, Debug, Info, Warn, Error
- ✅ 关键词搜索: 实时搜索并高亮匹配内容
- ✅ 颜色区分: 不同级别使用不同颜色显示
- ✅ 自动滚动: 当用户滚动到底部时自动跟随最新日志
- ✅ 暂停/继续: 可随时暂停和恢复日志捕获
- ✅ 导出日志: 支持导出日志到文件并分享
- ✅ 打开日志文件: 支持打开外部日志文件
- ✅ 缓冲区设置: 可设置日志缓冲区大小（1000-50000条）
- ✅ 标签过滤: 支持按标签过滤日志

**使用方式**:
- 侧边栏 → 工具 → 日志查看器

---

## 🔧 其他改进

### 默认语言
- 应用启动时自动设置为简体中文
- 修改文件: `AppConfig.java`

### GitHub Actions 自动构建
- **工作流文件**: `.github/workflows/release-build.yml`
- **触发方式**:
  1. 推送标签 `v*` 自动触发
  2. 手动触发（workflow_dispatch）
- **构建输出**:
  - Fdroid 版本 APK
  - Play 版本 APK
  - 自动签名
  - 自动发布到 GitHub Releases

---

## 📁 新增文件清单

### Java/Kotlin 源文件 (23个)
```
app/src/main/java/com/amaze/filemanager/
├── asynchronous/services/mediaplayer/AudioPlayerService.java
├── logviewer/LogAdapter.java
├── logviewer/LogEntry.java
├── logviewer/LogFilter.java
├── ui/activities/LogViewerActivity.java
├── ui/activities/imageeditor/ImageEditorActivity.java
├── ui/activities/mediaplayer/MediaPlayerActivity.java
├── ui/activities/pdf/AnnotationView.java
├── ui/activities/pdf/PdfAnnotationActivity.java
├── ui/activities/pdf/PdfEditorActivity.java
├── ui/activities/pdf/PdfOperationAdapter.java
├── ui/activities/pdf/PdfProcessor.java
├── ui/activities/pdf/PdfViewerActivity.java
├── ui/activities/terminal/TerminalActivity.java
├── ui/activities/texteditor/CodeFoldingManager.java
├── ui/activities/texteditor/GitIntegration.java
├── ui/activities/texteditor/SyntaxHighlighter.java
├── ui/fragments/mediaplayer/AudioPlayerFragment.java
├── ui/fragments/mediaplayer/VideoPlayerFragment.java
├── ui/fragments/terminal/TerminalFragment.java
└── ui/fragments/terminal/TerminalSession.java
```

### 布局文件 (17个)
```
app/src/main/res/layout/
├── activity_image_editor.xml
├── activity_log_viewer.xml
├── activity_media_player.xml
├── activity_pdf_annotation.xml
├── activity_pdf_editor.xml
├── activity_pdf_viewer.xml
├── activity_terminal.xml
├── dialog_extracted_text.xml
├── dialog_pdf_compress.xml
├── dialog_pdf_merge.xml
├── dialog_pdf_split.xml
├── dialog_pdf_to_images.xml
├── fragment_audio_player.xml
├── fragment_terminal.xml
├── fragment_video_player.xml
├── item_log_entry.xml
├── item_pdf_operation.xml
└── text_editor_replace_bar.xml
```

### 菜单文件 (6个)
```
app/src/main/res/menu/
├── image_editor_menu.xml
├── log_viewer_menu.xml
├── pdf_annotation_menu.xml
├── pdf_editor_menu.xml
├── pdf_viewer_menu.xml
└── terminal_menu.xml
```

---

## 🚀 如何构建

### 本地构建
```bash
# 克隆仓库
git clone https://github.com/TeamAmaze/AmazeFileManager.git
cd AmazeFileManager

# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease
```

### 自动构建
1. 推送标签到仓库:
   ```bash
   git tag -a v3.12.0-enhanced -m "Release v3.12.0"
   git push origin v3.12.0-enhanced
   ```
2. GitHub Actions 会自动构建并发布

---

## ⚠️ 注意事项

1. **PDF功能**: 使用 Android 原生 PdfRenderer，需要 API 21+ (Android 5.0+)
2. **终端功能**: 需要 libsu 库支持，已集成在项目中
3. **日志查看器**: 需要 READ_LOGS 权限，部分设备可能需要 root
4. **代码编辑器**: 语法高亮使用正则表达式，大文件可能影响性能

---

## 📄 许可证

本项目基于 GPL-3.0 许可证开源。

原始项目: [Amaze File Manager](https://github.com/TeamAmaze/AmazeFileManager)
