# krkrsdl3 引擎壳配置能力清单与 TyranorNext 完善方案

更新时间：2026-08-23

目标：梳理 TyranorNext 启动壳当前能够影响 krkrsdl3 内核的配置入口、已验证生效范围、现有 UI 中暂未被 krkrsdl3 消费的设置项，并给出后续完善计划。

## 结论

- 当前稳定推荐默认值：`software` 渲染。实机已验证软件渲染可以正常显示。
- `opengl` 渲染入口已打通，但当前实机表现为画面输出不完整，应暂时标为实验项或隐藏到高级选项。
- krkrsdl3 当前主要通过 `gameargs` extra 传递 native argv；Activity 级行为通过 Intent extra 控制。
- TyranorNext 已有多项 KR 设置来自 Kirikiroid2/Rinne 体系，但 krkrsdl3 内核尚未消费这些 JSON/extra，需要 UI 灰显、提示，或补内核解析。
- `--save-dir <path>` 目前已经由 TyranorNext 传入，但当前 krkrsdl3 内核没有解析该参数，实际存档目录仍是项目目录下的 `savedata/`。这是优先级最高的配置补齐项。

## 配置通道

### 1. Native argv：`gameargs`

方法：

```kotlin
intent.putStringArrayListExtra("gameargs", args)
```

当前 TyranorNext 组装位置：

- `app/src/main/java/com/tyranor/next/core/game/launch/EngineLauncher.kt`
- `buildKrkrsdl3Args(...)`

当前 krkrsdl3 读取位置：

- `engine/src/main/java/org/tvp/krkrsdl3/KRKRActivity.java`
- `getArguments()`

内核解析位置：

- `cpp/core/utils/TVPSettings.cpp`
- `TVPParseArguments(...)`
- `TVPGetCommandLine(...)`
- `TVPSelectRenderer()`

协议要点：

- `args[0]` 必须是游戏入口路径，通常是游戏目录、归档文件，或 `startup.tjs` / `start.ks` 等启动文件。
- 后续参数建议统一使用 `-key=value` 单参数形式。
- 当前 `TVPGetCommandLine("-key")` 能读取 `-key=value` 或 `-key`，但 `--key` 后面再跟一个非 `-` 开头的独立 value，容易被当前解析流程忽略。因此新配置优先不要用 `--save-dir`, `<path>` 两段式。

### 2. Activity extra

方法：

```kotlin
intent.putExtra("orientation", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
intent.putExtra("focus", "true")
intent.putExtra("darkMode", darkMode)
```

当前消费位置：

- `engine/src/main/java/com/core/krkrsdl3/Krkrsdl3Activity.kt`
- `engine/src/main/java/org/tvp/krkrsdl3/KRKRCall.java`

作用范围：

- 只影响 Android 壳层窗口、方向、沉浸式、系统栏、输入弹窗主题等 Java 层行为。
- 不会自动传入 TJS 或 native 内核，除非额外桥接。

### 3. SharedPreferences / `krkr_engine_prefs`

当前 TyranorNext 已有：

- `EngineSettingsStore.buildKrEnginePrefsJson(...)`
- KR2 启动链会通过 `krkr_engine_prefs` 注入渲染、内存、纹理压缩等偏好。

当前 krkrsdl3 状态：

- krkrsdl3 启动链目前没有读取 `krkr_engine_prefs`。
- 这些设置对 Kirikiroid2 可能有效，但对 krkrsdl3 不应默认宣称生效。

## 当前已可用配置

| 配置 | 方法 | 作用 | 当前状态 | TyranorNext 建议 |
| --- | --- | --- | --- | --- |
| 游戏入口 | `gameargs[0] = launchEntry` | 决定 `TVPNativeProjectData`、项目目录、启动脚本或归档 | 已生效 | 保持；扫描器需继续优先传最明确的启动入口 |
| 渲染器 | `-render=software` / `-render=opengl` | 选择软件渲染或 OpenGL 后端 | 已生效 | 默认 `software`；`opengl` 标为实验项 |
| 归档分隔符 | `-arcdelim=<char>` | 改变 xp3/目录内路径分隔符 | 内核支持，壳未暴露 | 高级调试项，不建议普通 UI 暴露 |
| 图形缓存上限 | `-gclim=<MB>` / `-gclim=auto` | 限制图形缓存内存 | 内核支持，壳未暴露 | 可作为后续“内存占用”实现底层之一 |
| 自动书签 | `-autosave=yes` | 开启自动书签 | 内核支持，壳未暴露 | 暂不放入核心配置页 |
| 图形分割操作 | `-gsplit=no/int/simple/bidi` | 控制图形 split operation | 内核支持，壳未暴露 | 高级兼容项，需单独实测后再开放 |
| Alpha 保持 | `-holdalpha=yes/no` | 控制默认 alpha 行为 | 内核支持，壳未暴露 | 高级兼容项 |
| Timer 精度 | `-timerprec=high/higher/normal` | 控制计时精度 | 内核支持，壳未暴露 | 可作为性能/耗电高级项 |
| 绘制线程数 | `-drawthread=auto/<n>` | 控制 `TVPDrawThreadNum` | 内核支持，壳未接入 | 可映射 TyranorNext 的软件绘制线程设置 |
| 低优先级 | `-lowpri` | 影响进程/线程优先级策略 | 内核支持，壳未暴露 | 暂不开放 |
| 强制日志 | `-forcelog` | 强制日志输出 | 内核支持，壳未暴露 | 调试构建可加开关 |
| 日志错误 | `-logerror` | 错误日志行为 | 内核支持，壳未暴露 | 调试构建可加开关 |
| 启动期方向 | `orientation` extra | Android Activity 方向、加载期方向锁 | 已生效 | 保持默认 sensor landscape，允许未来每游戏覆盖 |
| 强制 focus | `focus=true` extra | 避免启动期 focus 丢失导致沉浸状态异常 | 已生效 | 保持 |
| 主题色/深色模式 | `darkMode` / `themeColor*` extras | 输入框、窗口底色、系统栏颜色 | 已生效 | 保持与启动器主题同步 |

## 当前 UI 已有但 krkrsdl3 未完整生效的配置

| TyranorNext 设置 | 当前传递方式 | krkrsdl3 当前状态 | 问题 | 建议 |
| --- | --- | --- | --- | --- |
| 独立存档目录 | `--save-dir`, `<path>` | 未消费 | 内核仍固定 `TVPDataPath = TVPProjectDir + "savedata/"` | 优先补内核；改用 `-savedir=<path>` |
| 默认字体 | `default_font` extra | 未消费 | krkrsdl3 Java/native 未接该 extra | 先在 UI 标注 KR2 专用；后续补字体管理桥接 |
| 强制默认字体 | `force_default_font` extra | 未消费 | 同上 | 与默认字体一起补 |
| 软件绘制线程 | `krkr_engine_prefs` JSON | 未消费 | krkrsdl3 不读 JSON；但 native 支持 `-drawthread` | Launcher 直接映射为 `-drawthread=<n>` |
| 软件纹理压缩 | `krkr_engine_prefs` JSON | 未消费 | 当前未发现 krkrsdl3 对应命令行入口 | UI 对 krkrsdl3 灰显，待内核确认实现 |
| OpenGL 纹理压缩 | `krkr_engine_prefs` JSON | 未消费 | 当前 OpenGL 画面尚不完整 | 先不开放 |
| 内存占用 | `krkr_engine_prefs` JSON | 未消费 | 内核目前硬编码 `unlimited`；但支持 `-gclim` | 短期映射到 `-gclim`；长期补 `-memusage` |
| OpenGL 最大纹理 | `krkr_engine_prefs` JSON | 未消费 | 未接到渲染后端 | OpenGL 修复后再做 |
| OpenGL 精确渲染 | `krkr_engine_prefs` JSON | 未消费 | `TVPSettings.ogl_accurate_render` 默认 false，未解析参数 | OpenGL 修复后补 `-ogl-accurate-render=0/1` |
| FPS 限制 | `krkr_engine_prefs` JSON | 未消费 | 未发现当前内核 throttle 接口 | 保持 UI 灰显或 KR2 专用 |

## TyranorNext 配置页建议

### 全局 KR 配置

保留：

- 引擎内核选择：Kirikiroid2 / krkrsdl3。
- krkrsdl3 渲染器：`software`、`opengl`。
- 独立存档目录：保留开关，但在内核补齐前显示“待内核支持”或仅在 debug 版开放。

调整：

- 当内核选择为 krkrsdl3 时，默认渲染器强制落到 `software`。
- `opengl` 选项旁增加实验提示：当前已能启动，但部分机型/游戏画面不完整。
- 对 krkrsdl3 不生效的 KR2 项增加 capability 判断，不要让用户误以为已经生效。

建议暂时隐藏或灰显：

- 软件纹理压缩。
- OpenGL 纹理压缩。
- OpenGL 最大纹理。
- OpenGL 精确渲染。
- FPS 限制。
- 默认字体/强制字体。

可优先接入：

- 软件绘制线程：直接转成 `-drawthread=<n>`。
- 图形缓存上限：新增 krkrsdl3 专用“图形缓存上限”，底层转成 `-gclim=<MB>`。

### 单游戏配置

优先保留这些覆盖：

- 渲染器覆盖：默认跟随全局，允许单游戏强制 `software`。
- 独立存档目录覆盖：等内核补齐 `-savedir` 后正式启用。
- 高级命令行覆盖：可给 debug/高级用户追加 krkrsdl3 参数，便于快速验证 `-gclim`、`-timerprec`、`-drawthread` 等。

不建议单游戏先开放：

- OpenGL 纹理和精确渲染相关项。当前 OpenGL 基础显示仍有问题，先修主路径。
- 字体强制项。不同游戏字体加载链差异大，需要补桥接后再做用户可见设置。

## 内核补齐计划

### P0：存档目录真正生效

目标：

- TyranorNext 开启独立存档目录后，krkrsdl3 的 `TVPDataPath`、`TVPNativeDataPath`、日志目录都指向启动器分配的目录。

实现建议：

1. 修改 TyranorNext `buildKrkrsdl3Args(...)`：

```kotlin
args.add("-savedir=${saveDir.absolutePath}")
```

2. 修改 krkrsdl3 `TVPParseArguments(...)`：

- 在设置 `TVPDataPath` 前解析 argv 中的 `-savedir=<path>`。
- 路径转成本地可访问路径，并补尾部 `/`。
- 如果没有传 `-savedir`，保持旧逻辑：`TVPProjectDir + "savedata/"`。

3. 保证日志路径同步：

- `TVPSetLogLocation(TVPNativeDataPath)` 继续使用最终 save dir。

验收：

- ADB 启动游戏后，项目目录不再新增/写入 `savedata/`。
- Android app 私有目录下出现对应游戏存档和日志。
- 冷启动、二次启动、退出重进都能读取旧存档。

### P1：渲染器配置稳定化

目标：

- 默认软件渲染稳定。
- OpenGL 可选但不会误导用户。

实现建议：

1. TyranorNext 默认值保持 `software`。
2. UI 对 `opengl` 增加实验标识。
3. 在 krkrsdl3 日志中保留 `Selected Render: ...`，便于用户反馈时确认实际后端。
4. 单游戏层允许覆盖为 `software`，方便绕过全局误选。

验收：

- 软件渲染启动目标游戏画面完整。
- OpenGL 路径至少不会崩溃；画面不完整问题单独建 debug 文档追踪。

### P1：软件绘制线程接入

目标：

- TyranorNext 的“软件绘制线程”设置对 krkrsdl3 生效。

实现建议：

1. 读取全局/单游戏 `software_draw_thread`。
2. 值为空时不传参数。
3. 值为 `0..8` 时传：

```kotlin
args.add("-drawthread=$threadCount")
```

验收：

- native 日志或调试输出能确认 `TVPDrawThreadNum` 被设置。
- 对 0、1、4、8 做启动回归，确认不崩溃。

### P2：内存配置映射

目标：

- 让 TyranorNext 的内存占用设置至少能影响 krkrsdl3 图形缓存。

短期方案：

- 将 `low / medium / high / unlimited` 映射到 `-gclim`：

| UI 值 | 建议参数 |
| --- | --- |
| `low` | `-gclim=64` |
| `medium` | `-gclim=128` |
| `high` | `-gclim=256` |
| `unlimited` | `-gclim=auto` |

长期方案：

- 在 krkrsdl3 内核增加 `-memusage=low/medium/high/unlimited`。
- 替换 `TVPBeforeSystemInit()` 中当前硬编码的 `std::string str = "unlimited"`。

验收：

- 启动日志能看到图形缓存上限变化。
- 大图/长时间运行下不会出现明显回退或异常。

### P2：字体配置桥接

目标：

- `default_font` 和 `force_default_font` 对 krkrsdl3 生效。

实现建议：

1. 优先走命令行：

```text
-default-font=/path/to/font.ttf
-force-default-font=yes
```

2. 在 native 字体管理层读取并替换默认字体候选。
3. 保留 Activity extra 仅作为兼容旧 KR2/Kirikiroid2 路径，不作为 krkrsdl3 主协议。

验收：

- 无内置字体游戏可以使用用户指定字体正常显示。
- `force_default_font=false` 时不破坏游戏自带字体。

### P3：OpenGL 配置与画面完整性

目标：

- 先修复 OpenGL 画面不完整，再开放 OpenGL 高级项。

待办：

- 定位 Surface/viewport/scissor/render target 尺寸是否与 Android 窗口、SDL drawable size 不一致。
- 检查横屏、挖孔、沉浸式状态下 `glViewport` 是否使用实际 drawable 尺寸。
- 修复后再接入：
  - `-ogl-accurate-render=0/1`
  - `-ogl-max-texsize=<n>`
  - OpenGL 纹理压缩策略

验收：

- 目标游戏 title、菜单、对话框画面完整。
- 横屏、反向横屏、息屏恢复后画面尺寸不裁切。

### P3：FPS 限制

目标：

- 用户可按游戏设置 60/45/30/15 FPS。

实现建议：

- 先定位 krkrsdl3 当前主循环节拍位置。
- 增加 `-fps-limit=<n>`。
- 优先通过帧调度/延迟控制，不在脚本层做 hack。

验收：

- 日志确认 FPS limit。
- 画面速度、音频同步、输入响应无明显异常。

## 推荐实施顺序

1. P0：补 `-savedir=<path>`，修正 TyranorNext 两段式 `--save-dir` 参数。
2. P1：保持软件渲染默认，UI 标注 OpenGL 实验状态。
3. P1：把软件绘制线程接到 `-drawthread=<n>`。
4. P2：把内存占用短期映射到 `-gclim`。
5. P2：补字体配置桥接。
6. P3：独立追踪 OpenGL 画面不完整，再开放 OpenGL 高级项。
7. P3：补 FPS 限制。

## 回归测试清单

每个变更至少覆盖：

- 冷启动游戏。
- 退出后再次启动。
- 软件渲染启动。
- OpenGL 启动，确认是否仍存在画面裁切/不完整。
- 独立存档开关开启/关闭。
- 新存档写入、旧存档读取。
- 横屏、反向横屏、息屏恢复。
- 输入框弹出、取消、确认。
- ADB 日志检查：
  - `KRKRActivity: launch args=...`
  - `Selected Render: ...`
  - Savedata path / log path 是否符合预期。

## 当前代码参考

- TyranorNext 启动参数组装：`app/src/main/java/com/tyranor/next/core/game/launch/EngineLauncher.kt`
- TyranorNext KR 设置存储：`app/src/main/java/com/tyranor/next/core/settings/EngineSettingsStore.kt`
- TyranorNext krkrsdl3 Activity 壳：`engine/src/main/java/com/core/krkrsdl3/Krkrsdl3Activity.kt`
- TyranorNext krkrsdl3 argv 读取：`engine/src/main/java/org/tvp/krkrsdl3/KRKRActivity.java`
- krkrsdl3 argv/渲染器解析：`cpp/core/utils/TVPSettings.cpp`
- krkrsdl3 系统初始化参数：`cpp/core/main/TVPSystem.cpp`
