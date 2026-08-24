# Tyranor Next — AGENT 规范

本文档是 AI Agent 在本项目内开发时必须遵循的统一规范。新增或修改代码前请先阅读，与既有实现保持一致。

## 技术栈

- Android Jetpack Compose + Material 3
- Kotlin
- 底部导航结构：`app/src/main/java/com/example/tyranornext/ui/main/MainScreen.kt`
- 各页面位于 `app/src/main/java/com/example/tyranornext/ui/pages/`

---

## 页面顶部栏统一规范

所有页面（首页 / 游戏 / 书库 / 设置）的顶部栏必须统一，规则如下。当前已由统一入口
`com.tyranor.next.ui.pages.PlaceholderPage` 实现，新页面应复用或遵循同等效果。

### 1. 结构

- 顶部栏使用 **Column + Centre**，**不使用** Material3 的 `TopAppBar` / `Scaffold`。
- 页面整体由外层 `Column` 组装，顺序固定为：顶部栏 → 正文内容。
- **禁止在顶部栏放置任何返回按钮/图标**。返回统一依赖系统返回键/手势（`Activity` 默认 `finish()`），不要通过 `onBack` 参数下发返回回调。

### 2. 高度

- 标题区高度固定为 **64dp**。
- 顶部栏整体无需在 64dp 之外再叠加额外高度。

### 3. 标题

- 标题**居左**，水平内边距 `horizontal = 16.dp`，纵向居中。
- 标题字号使用 `MaterialTheme.typography.titleLarge`。
- 标题**必须加粗**：`fontWeight = FontWeight.Bold`。

### 4. 背景色

- 顶部栏**使用页面背景色 `colorScheme.background`（不透明）**（`Modifier.background(colorScheme.background)`），标题与图标统一使用 `colorScheme.onBackground`。
- 禁止使用主题色 `primary` 作为顶部栏背景。

### 5. 状态栏

- 状态栏必须是**透明沉浸式**（`window.statusBarColor = Color.TRANSPARENT`），顶部栏的
  页面背景色向上延伸覆盖状态栏区域。
- 状态栏/导航栏图标使用**深色**（`SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)`），因为页面背景为浅色。
- 顶部栏需使用 `Modifier.statusBarsPadding()`，使标题内容避开状态栏但背景色连续延伸。
- 不要自行给状态栏设置非透明背景色。

### 6. 位置顺序

```
Column(fillMaxSize)                                // 页面根
├── Column(fillMaxWidth, background(background))    // 页面背景色容器（不透明）
│   ├── Column(statusBarsPadding)                    // 避开状态栏（背景延伸至状态栏）
│   └── Column(height 64dp, padding horizontal 16dp)  // 标题区
└── 正文内容
```

> 设置类页面若使用 `MiuixScaffold`，顶部栏在 `topBar` 槽中按同样规则实现：
> `Column(background(background)) { Column(statusBarsPadding) { Row(height 64dp, padding horizontal 16dp) { ... } } }`，
> 并设 `contentWindowInsets = WindowInsets(0.dp)` 避免系统 inset 再次叠加间距。

### 7. 顶部栏图标按钮

- 顶部栏右侧图标按钮**必须使用公共组件** `com.tyranor.next.ui.common.TopBarIcon`（排版规范对齐游戏页顶部栏），**禁止**自行用 `IconButton`/`Icon` 拼装。
- 组件排版：`Image` 渲染 + `size(34.dp)` 触控区 + `clip(RoundedCornerShape(6.dp))` 圆角 + `clickable` + `padding(4.dp)` 内边距，并 `ColorFilter.tint` 主题色；组件左侧自带 `padding(start = 2.dp)`，保证相邻图标之间留白统一。
- `tint` 由调用方传入当前主题色：Material 页面传 `MaterialTheme.colorScheme.primary`，Miuix 页面传 `MiuixTheme.colorScheme.primary`。
- 顶部栏图标一律使用 drawable 资源（`R.drawable.ic_*`）；需要新图标时在 `res/drawable` 新建资源（PNG 或 vector XML），禁止直接用 `Icons.*` 矢量图标充当顶部栏按钮。

---

## 跨页面一致性要求

- 所有页面共用 `PlaceholderPage`（或与之一致的结构），禁止各页面各自实现不同样式的顶部栏。
- 新增页面时：页面主体内容放入 `Column` 的正文区域即可，顶部栏保持相同。
- 顶部栏下方禁止放置页面说明/描述文案；正文区域应直接展示该页面的实际内容或列表。
- 页面切换动画必须保持统一：主 Screen 四个 Tab 间切换使用水平移动动画；其他独立 Activity 页面进入使用向上翻页动画，退出/返回使用向下翻页动画。
- 组件统一圆角数值为 **8dp**；列表项卡片、功能项卡片、弹窗等圆角组件都应使用 `RoundedCornerShape(8.dp)`。
- 所有弹窗背景必须为白色，且圆角必须使用统一圆角数值 **8dp**。

## 页面内容文字尺寸规范

页面内容（顶部栏除外）只允许使用**两种**文字尺寸，与引擎设置页保持一致：

1. `MaterialTheme.typography.titleMedium` —— 卡片头、对话框标题、列表项主标题（可加粗）。
2. `MaterialTheme.typography.bodyMedium` —— 正文、行值、辅助描述、按钮文字、空态/错误提示。

- **禁止**使用 `bodySmall` / `bodyLarge` / `labelMedium` / `labelLarge` / `headlineMedium` / `headlineSmall` 等其它排版尺寸。
- Miuix preference 组件标题默认用 `headline1`(17sp)，已在 `MiuixSettingsTheme` 中全局覆盖为 16sp（`defaultTextStyles(headline1 = TextStyle(fontSize = 16.sp))`），使其严格落入两档；不要自行在单行上改字号。
- 顶部栏标题不受此限制，仍用 `MaterialTheme.typography.titleLarge` Bold。

---

## 搜索/输入框统一规范

全 App 的搜索过滤框与弹窗内单行文本输入框，必须统一使用公共组件
`com.tyranor.next.ui.common.AppSearchField`（`app/src/main/java/com/tyranor/next/ui/common/AppSearchField.kt`）。
**禁止**在页面内直接拼装 Miuix `SearchBar`/`InputField`，也**禁止**使用 Material 的
`TextField`/`OutlinedTextField` 充当搜索框或弹窗输入框。

### 1. 组件形态（Miuix 风格，非展开内嵌式）

- 组件内部固定为 `MiuixSettingsTheme + SearchBar + InputField` 模板，`expanded` 恒为
  `false`（内嵌式），不使用 Miuix 的展开式全屏搜索页形态。
- 前导图标固定 **26dp**，内边距固定 `SearchBarDefaults.LeadingIconStartPadding /
  LeadingIconEndPadding`，`tint` 统一取 `MiuixTheme.colorScheme.primary`——以上均由组件
  内部处理，调用方不要传色值/尺寸，保证全局样式单点可改。

### 2. 参数约定

- `query` / `onQueryChange`：必传，输入状态由调用方持有。
- `onSearch`：键盘 IME 动作回调。本地即时过滤场景可不传（默认空实现）；
  需显式触发时传入（如网络请求、回车即保存）。
- `leadingIcon`：drawable 资源（`R.drawable.ic_*`）。搜索语义用默认 `ic_game_search`；
  其他语义必须传对应图标（如名称修改用 `ic_sheet_rename`），禁止搜索图标滥用。
- `iconContentDescription`：无障碍描述，跟随图标语义。
- `modifier`：仅用于布局微调（如外边距），宽度由组件内部 `fillMaxWidth` 保证。

### 3. 现有调用点（新增场景照此对齐）

| 场景 | 位置 | 图标 | onSearch |
|---|---|---|---|
| 游戏库过滤 | `GameScreen.kt`（顶栏下方） | `ic_game_search` | 即时过滤，不传 |
| VNDB 封面搜索 | `GameScreen.kt`（VndbSearchDialog） | `ic_game_search` | `search()` 网络请求 |
| krkr 在线补丁过滤 | `KrkrOnlinePatchActivity.kt`（列表首项） | `ic_game_search` | 即时过滤，不传 |
| 名称修改 | `GameScreen.kt`（RenameGameDialog） | `ic_sheet_rename` | 内容有效即保存 |

---

## 主题色调统一使用规范

应用主题色（primary）由用户通过 **应用设置 → 色调轮盘** 修改，必须全局统一生效。规范如下：

### 1. 主题色的唯一定义与入口

- 全局主题色唯一状态：`com.tyranor.next.theme.AppThemeColors.primary`（`mutableStateOf`，变化即触发全 App 重组）。
- 持久化：`com.tyranor.next.settings.AppSettingsStore`（独立 prefs 文件 `app_settings`，key `theme_color`，默认 `#307DEF`）。
- 修改主题色的唯一入口：持久化后调用 `AppThemeColors.refresh(context)`；任何页面不得自行修改 `primary`。
- `TyranorNextTheme`（Material 页面）与 `MiuixSettingsTheme`（Miuix 页面）的 `primaryColor` 参数**必须为 `Color? = null`**，并在**函数体内**以 `val primary = primaryColor ?: AppThemeColors.primary` 读取全局主题色；两个主题函数都必须标注 `@NonSkippableComposable`（配合函数体内读取，保证轮盘切换时主题必然重组、全局同步变色，不依赖调用点对默认参数表达式的订阅）。内部均会先 `ensureLoaded` 从存储加载，禁止传入写死颜色。

### 2. 页面如何获取主题色

- **Material 页面**（首页/游戏/引擎等 Tab、存档管理、在线补丁等 Activity）：统一使用 `MaterialTheme.colorScheme.primary`（选中态图标/文字、按钮、开关、输入框聚焦指示等）。
- **Miuix 页面**（设置页/引擎设置/单游戏设置/应用设置等）：统一使用 `MiuixTheme.colorScheme.primary`（Preference 图标、Slider、Switch、下拉选中等）。
- 图标 tint、高亮文字、选中态等一切"强调色"位置只能从上述 colorScheme 获取，**禁止**在页面里硬编码 `Color(0x...)`、`#307DEF`、`Blue40` 或任何品牌蓝。

### 3. 中性色与语义色（固定，不属于主题色）

以下颜色固定不变，从 `theme/Color.kt` 常量引用，**禁止**在页面中直接写 `Color(0x...)`：

- `PageGrey` 页面背景色、`NavWhite` 卡片/导航栏组件色；二者由应用设置「色调切换」控制是否互换。`TextColor` 正文深灰、`UnselectedGrey` 导航栏未选中灰。
- 语义色：`colorScheme.error`（错误/删除）、引擎封面色（`EngineType.coverColor()`）、封面占位白字等。
- 新增任何颜色先检查 `Color.kt` 是否已有现成常量；中性色必须统一收口到 `Color.kt`，不在页面内散落硬编码。

### 3.5 组件容器/背景色的色调跟随

组件（卡片、列表项、弹窗内容项、设置项容器等）的背景色**不得依赖**
`colorScheme.surface*` / `surfaceContainer` 等 scheme 颜色来自动跟随色调切换——这些 scheme
颜色可能被主题函数固定写死（如 `MiuixSettingsTheme` 深浅色分支都设为 `NavWhite`），且弹窗等
`Dialog` 组合可能不在目标 Miuix/Material 主题作用域内，导致背景色不随「外观模式 / 色调切换」变化。

统一做法：需要随「色调切换」变色的组件容器，**直接引用 `theme/Color.kt` 的计算常量**（`get()`
读取 `AppThemeColors.isDark` / `toneSwitchEnabled` 的 snapshot state，变化即触发重组）：

- 卡片/导航栏/组件容器 → `NavWhite`
- 页面背景 → `PageGrey`
- 文字 → `TextColor`

通过 `MiuixTheme`/`MaterialTheme` 的 `colorScheme` 取容器背景属于**反例**（例：弹窗内项目用
`MiuixTheme.colorScheme.surfaceContainer` 不会随色调切换变色，应改用 `NavWhite`）。
仅当某 scheme 颜色确为实时计算且随色调切换变化时才允许引用。

### 4. 层级要求

- 任何页面根组件必须包在 `TyranorNextTheme {}` 或 `MiuixSettingsTheme {}` 内，且主题必须最外层（Activity `setContent` 中包裹）。
- `MiuixSettingsTheme` 只提供 Miuix 主题，页面内部若用到 `MaterialTheme.*`（如弹窗、typography），外层仍须有 `TyranorNextTheme`（所有现有 Activity 均已满足，新增页面须遵循）。
- XML / drawable 资源中**禁止**出现主题色（启动图标等除外）。

---

## 构建

- 构建命令：`./gradlew assembleDebug --no-daemon`
- 使用 Android CLI（`--sdk=/tmp/androidsdk`）安装到实机。
