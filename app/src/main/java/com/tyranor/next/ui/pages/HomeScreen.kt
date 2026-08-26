package com.tyranor.next.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tyranor.next.R
import com.tyranor.next.scanner.EngineLauncher
import com.tyranor.next.scanner.ScanGame
import com.tyranor.next.theme.NavWhite
import com.tyranor.next.ui.common.TimeFormats
import com.tyranor.next.ui.common.glassNavBottomInset
import com.tyranor.next.ui.main.MainLibraryUiState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    libraryState: MainLibraryUiState,
    onGameUpdated: (ScanGame) -> Unit,
    onGameDeleted: (ScanGame) -> Unit,
    onRecentRemoved: (ScanGame) -> Unit,
    onQuickLaunchToggle: (ScanGame) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val quickLaunch = libraryState.quickLaunch
    val recentGames = libraryState.recentGames
    var selectedGame by remember { mutableStateOf<ScanGame?>(null) }
    var launchError by remember { mutableStateOf<String?>(null) }
    var patchLaunchTarget by remember { mutableStateOf<ScanGame?>(null) }

    LaunchedEffect(libraryState.games) {
        selectedGame = selectedGame?.let { selected ->
            libraryState.games.firstOrNull { it.uri == selected.uri }
        }
    }

    fun replaceGame(updated: ScanGame) {
        selectedGame = selectedGame?.let { if (it.uri == updated.uri) updated else it }
        onGameUpdated(updated)
    }

    fun deleteGame(target: ScanGame) {
        selectedGame = null
        onGameDeleted(target)
    }

    /** 仅删除该条最近游玩记录，不影响游戏库。 */
    fun removeRecentRecord(target: ScanGame) {
        onRecentRemoved(target)
    }

    // 点按直接启动游戏；Artemis 按既有策略弹出补丁确认（与游戏页长按启动一致）。
    fun launchGame(game: ScanGame) {
        if (EngineLauncher.needsArtemisPatchConfirm(context, game)) {
            patchLaunchTarget = game
        } else {
            scope.launch { launchError = EngineLauncher.launch(context, game) }
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("首页", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        // ===== 顶部栏底下固定三个快捷启动游戏（一行三个） =====
        // 平板上整行三列会把每张卡撑得过大：限制快捷启动区最大宽度并水平居中，
        // 手机宽度不变，平板仅占居中一段（约 3 列 × 166dp）
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(3) { i ->
                    val game = quickLaunch.getOrNull(i)
                    // 按游戏 uri 做 key：删除卡片后槽位状态跟随游戏移动而不是按位置复用，
                    // 避免被顶上来那张卡复用被删卡的封面 MutableState（上一个封面串到另一张卡）。
                    key(game?.uri ?: "quick_launch_empty_$i") {
                        QuickLaunchSlot(
                            game = game,
                            onClick = { if (game != null) selectedGame = game },
                            onLongClick = { if (game != null) launchGame(game) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ===== 快捷启动下方：最近打开列表（最多 10 条，圆角长矩形） =====
        if (!libraryState.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (recentGames.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无最近打开的游戏",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + glassNavBottomInset()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = recentGames,
                    key = { it.uri },
                    contentType = { "recent_game" },
                ) { game ->
                    RecentGameRow(
                        game = game,
                        onClick = { selectedGame = game },
                        onLongClick = { launchGame(game) },
                        onSwipeDelete = { removeRecentRecord(game) },
                    )
                }
            }
        }
    }

    // ===== 与游戏页统一：点按打开操作抽屉，长按直接启动 =====
    selectedGame?.let { game ->
        key(game.uri) {
            GameActionsSheet(
                game = game,
                onDismiss = { selectedGame = null },
                onGameUpdated = { replaceGame(it) },
                onDeleteGame = { deleteGame(game) },
                quickLaunched = quickLaunch.any { it.uri == game.uri },
                onQuickLaunchToggle = { onQuickLaunchToggle(game) },
                onEngineSettings = {
                    startActivityWithPageTransition(context, PerGameSettingsActivity.createIntent(context, game))
                    selectedGame = null
                },
            )
        }
    }

    // ===== Artemis 首次启动补丁确认（与游戏页一致） =====
    patchLaunchTarget?.let { game ->
        AppAlertDialog(
            onDismissRequest = { patchLaunchTarget = null },
            title = {
                Text(
                    "应用自动补丁",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    "「${game.title}」的启动文件打包在 .pfs 归档内，首次启动需要解出少量基础文件" +
                        "（system.ini、窗口配置与视频）并适配 Android 平台。是否应用补丁？",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        patchLaunchTarget = null
                        scope.launch {
                            launchError = EngineLauncher.launch(context, game, EngineLauncher.ArtemisPatchChoice.ALWAYS)
                        }
                    },
                ) { Text("总是") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            patchLaunchTarget = null
                            scope.launch {
                                launchError = EngineLauncher.launch(context, game, EngineLauncher.ArtemisPatchChoice.NEVER)
                            }
                        },
                    ) { Text("不再") }
                    TextButton(
                        onClick = {
                            patchLaunchTarget = null
                            scope.launch {
                                launchError = EngineLauncher.launch(context, game, EngineLauncher.ArtemisPatchChoice.ONCE)
                            }
                        },
                    ) { Text("本次") }
                }
            },
        )
    }

    launchError?.let { message ->
        AppAlertDialog(
            onDismissRequest = { launchError = null },
            title = { Text("启动失败", style = MaterialTheme.typography.titleMedium) },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { launchError = null }) { Text("确定") }
            },
        )
    }
}

/** 首页快捷启动槽位：已设置复用游戏页卡片样式（封面跟随游戏页），交互与游戏页统一——点按开菜单、长按直启；空槽显示白色封面 + 加号。 */
@Composable
private fun QuickLaunchSlot(
    game: ScanGame?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    if (game != null) {
        GameCard(game = game, onClick = onClick, modifier = modifier, onLongClick = onLongClick)
    } else {
        Column(modifier) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NavWhite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "空槽位",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                "快捷启动",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** 最近打开列表项：圆角长矩形，左侧统一图标 + 游戏名，右侧打开时间；交互与游戏页统一——点按开菜单、长按直启；向左滑动约 1/6 露出独立删除按钮，点击直接移除该条记录。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentGameRow(
    game: ScanGame,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeDelete: () -> Unit,
) {
    BoxWithConstraints {
        val revealPx = with(LocalDensity.current) { (maxWidth / 6f).toPx() }
        val scope = rememberCoroutineScope()
        var offset by remember { mutableFloatStateOf(0f) }
        val settleJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
        fun settleOffset(target: Float) {
            settleJob[0]?.cancel()
            settleJob[0] = scope.launch {
                androidx.compose.animation.core.Animatable(offset).animateTo(target) {
                    offset = value
                }
            }
        }
        val formattedOpenTime = remember(game.openTime) { TimeFormats.formatDateTime(game.openTime) }
        // 统一裁切圆角：红色删除层与白色内容层圆角一致，内容左移越界部分被裁掉
        Box(Modifier.clip(RoundedCornerShape(8.dp))) {
            // 删除层：主题色背景 + 白色删除图标，仅滑出约 1/6 时露出右侧「删除」区域
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onSwipeDelete),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(LocalDensity.current) { revealPx.toDp() })
                        .align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = Color.White,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .offset { IntOffset(offset.roundToInt(), 0) }
                    .fillMaxWidth()
                    .background(NavWhite)
                    .combinedClickable(
                        onClick = {
                            if (offset != 0f) {
                                settleOffset(0f)
                            } else {
                                onClick()
                            }
                        },
                        onLongClick = onLongClick,
                    )
                    .pointerInput(revealPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                settleOffset(if (offset < -revealPx / 2f) -revealPx else 0f)
                            },
                            onDragCancel = {
                                settleOffset(if (offset < -revealPx / 2f) -revealPx else 0f)
                            },
                        ) { _, dragAmount ->
                            settleJob[0]?.cancel()
                            offset = (offset + dragAmount).coerceIn(-revealPx, 0f)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 23.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_recent),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    game.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                )
                Text(
                    formattedOpenTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
