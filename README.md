# Tyranor Next

基于 **Tyranor 模拟器逆向重写**的多引擎视觉小说（Galgame）聚合启动器，面向 Android 平台。内置 Kirikiri / ONScripter / Tyrano / Artemis 四套引擎运行时，提供游戏库管理、封面获取、存档镜像、引擎参数调节等一体化体验。

主打轻便、简单、快捷，不引入其他冗余功能的简约设计思路

## 参与贡献

欢迎参与项目开发与维护！

在提交 Issue 或 Pull Request 前，请先阅读 [贡献指南](./CONTRIBUTING.md)。

## 功能特性

### 游戏库

- **目录扫描**：通过系统文件选择器（SAF）添加游戏目录，自动识别并索引游戏，识别逻辑支持 Kirikiri(kr/krkr2)、ONS(ons)、Tyrano(ty)、Artemis(ar)
- **卡片网格**：三列封面卡片布局，无封面时显示引擎标识占位
- **搜索**：顶部栏搜索按钮，按游戏名实时模糊过滤（忽略大小写）
- **最近游戏**：首页展示最近游玩记录，点击卡片弹出操作抽屉栏

### 封面管理

- **VNDB 自动获取**：一键为缺失封面的游戏批量拉取 VNDB 官方封面（带限速与失败容忍）
- **手动搜索绑定**：弹窗内按游戏名搜索 VNDB 候选，确认后绑定并同步中文标题
- **自定义封面**：从系统相册选择图片作为任意游戏的封面（保留 png/webp/jpg 原格式，自动清理旧封面）

### 游戏操作（卡片抽屉栏）

- 启动游戏（自动匹配引擎运行库）
- 搜索 / 修改封面
- 存档管理（应用内镜像备份，不动游戏原文件）
- 引擎设置（含单游戏独立覆盖项）
- KRKR 游戏在线补丁（远程 patch 索引下载安装）
- 删除游戏（仅清理应用内记录与缓存，绝不触碰游戏文件）

### 引擎设置

- **Kirikiri**：引擎版本（自动/1.3.9/1.3.4/1.2.6）、内核（吉里吉里2 / krkrsdl3）、渲染器（软件/OpenGL）、纹理压缩、最大纹理尺寸、内存用量、FPS 限制、默认字体与强制字体、独立存档目录、精确渲染
- **ONScripter**：文本编码（GBK/Shift-JIS/UTF-8）、全屏拉伸、忽略刘海、禁用视频、画面锐化
- **Artemis**：引擎版本（V1/V2/V3）、画面反转、自动补丁策略
- **Tyrano**：外部网络资源加载、独立存档目录

## 技术架构

### 模块划分

| 模块 | 职责 |
| --- | --- |
| `app` | 启动器 UI：游戏扫描/管理、封面、存档、设置入口（Kotlin + Jetpack Compose） |
| `engine` | 引擎运行时核心：SDL2/SDL3、Kirikiri TVP、krkrsdl3、ONScripter、Artemis、Tyrano 执行环境 |

### 技术栈

- **语言**：Kotlin（引擎层含 Java 桥接代码）
- **UI**：Jetpack Compose + Material 3 + [Miuix](https://github.com/compose-miuix-ui/miuix)
- **导航**：底部导航 `NavigationBar`；主 Tab 内容页使用水平移动切换，详情/设置等独立 Activity 进入使用向上翻页、退出使用向下翻页
- **构建**：Gradle 9.5.1 / AGP 9.2.1 / Kotlin 2.x + Compose Compiler，`compileSdk 37`、`minSdk 26`、`targetSdk 36`
- **持久化**：SharedPreferences（扫描结果、引擎全局设置、单游戏设置覆盖、最近记录）
- **文件访问**：Storage Access Framework（SAF）管理外部游戏目录，`documentFile` 库辅助

### 引擎集成设计

- 引擎原生插件（`kirikiroid2` / `ons` / `artemis` 的 `.so`）以 assets 形式随 APK 打包（`nativeplugins/`），首次启动由 `NativePluginManager` 自动解压安装到应用私有目录
- `app` 模块通过 `EngineLauncher` 将扫描结果映射到对应引擎 Activity 启动（SAF URI → 真实路径转换）
- Tyrano 运行环境内置本地 HTTP 服务器、Asar 归档解析与 JS 钩子脚本（`__tyrano__.js` 等），无需外部依赖即可运行网页式脚本游戏
- 原生库仅提供 `arm64-v8a` 架构

### 关键流程

1. **扫描**：添加 SAF 目录 → `EngineScanner` 递归识别游戏文件 → 按引擎类型分类入库
2. **启动**：点击游戏 → `EngineLauncher` 按引擎选择启动器 → 加载对应 `.so` 与运行时
3. **封面**：`VndbCoverService` 查询 VNDB API → 下载封面到应用缓存目录 → `coverUri` 持久化
4. **存档**：`GameSaveManager` 将引擎存档目录镜像到应用内，删除游戏时同步清理镜像与应用数据

## 构建

```bash
# 编译 Debug APK
./gradlew assembleDebug --no-daemon
```

产物位于 `app/build/outputs/apk/debug/`。需要 Android SDK（本地平台 android-37）环境。

## 目录结构

```
app/    启动器（UI、游戏扫描、封面、存档、设置）
engine/ 引擎运行时核心（SDL、Kirikiri、krkrsdl3、ONScripter、Artemis、Tyrano）
docs/   设计文档
```

## 许可证

本项目基于 **GNU General Public License v2.0** 发布，详见 [LICENSE](LICENSE)（GPL-2.0-only）。

- `engine/` 引擎运行时基于 Tyranor 模拟器逆向重写，上游涉及 Kirikiroid2 / ONScripter 等 GPL-2.0 项目，因此整个项目以 GPL-2.0 授权分发
- 基于本项目发布的衍生作品须遵循 GPL-2.0 条款，并随发行物提供完整源码
- Miuix 等第三方依赖按各自许可证引入

## 致谢

- **Tyranor 模拟器**：本项目引擎运行时与核心架构的逆向重写基础
- **RinneMobile**：游戏扫描识别/SAF路径映射逻辑/独立存档映射/krkrsdl3 等多个功能的参考实现
- [Miuix](https://github.com/compose-miuix-ui/miuix)：设置界面组件库
- 各引擎运行时均基于其开源许可引入
