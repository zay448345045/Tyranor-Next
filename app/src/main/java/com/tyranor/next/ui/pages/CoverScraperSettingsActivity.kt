package com.tyranor.next.ui.pages

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tyranor.next.R
import com.tyranor.next.scanner.CoverScrapeTaskManager
import com.tyranor.next.settings.AppSettingsStore
import com.tyranor.next.settings.HikarinagiAuthService
import com.tyranor.next.settings.HikarinagiAuthStore
import com.tyranor.next.theme.MiuixSettingsTheme
import com.tyranor.next.theme.NavWhite
import com.tyranor.next.theme.PageGrey
import com.tyranor.next.theme.TextColor
import com.tyranor.next.theme.TyranorNextTheme
import com.tyranor.next.ui.common.WithoutPressIndication
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class CoverScraperSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val darkMode = AppSettingsStore.isDarkEffective(this)
        enableEdgeToEdge(
            statusBarStyle = if (darkMode) androidx.activity.SystemBarStyle.dark(Color.TRANSPARENT) else androidx.activity.SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (darkMode) androidx.activity.SystemBarStyle.dark(Color.TRANSPARENT) else androidx.activity.SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        setContent {
            TyranorNextTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    WithoutPressIndication {
                        CoverScraperSettingsScreen()
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.page_slide_in_from_top, R.anim.page_slide_out_to_bottom)
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, CoverScraperSettingsActivity::class.java)
    }
}

@Composable
internal fun CoverScraperSettingsScreen() {
    val ctx = LocalContext.current
    val activity = ctx as? ComponentActivity
    val authVersion = HikarinagiAuthStore.statusVersion.value
    val settingsVersion = AppSettingsStore.coverScraperSettingsVersion.value
    val scrapeTaskState = CoverScrapeTaskManager.state.value
    var sources by remember(settingsVersion) { mutableStateOf(AppSettingsStore.getCoverScraperSourceOrder(ctx)) }
    var onlyMissing by remember(settingsVersion) { mutableStateOf(AppSettingsStore.isCoverScraperOnlyMissing(ctx)) }
    val scraping = scrapeTaskState.running
    val authStatus = remember(authVersion) { HikarinagiAuthStore.getStatus(ctx) }

    LaunchedEffect(scrapeTaskState.eventId) {
        if (scrapeTaskState.eventId == 0L) return@LaunchedEffect
        scrapeTaskState.result?.let { result ->
            Toast.makeText(
                ctx,
                "批量刮削完成：更新 ${result.updatedCount}，跳过 ${result.skippedCount}，失败 ${result.failedCount}",
                Toast.LENGTH_SHORT,
            ).show()
        }
        scrapeTaskState.error?.let { message ->
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
        CoverScrapeTaskManager.clearFinished(scrapeTaskState.eventId)
    }

    fun startBatchScrape() {
        if (!CoverScrapeTaskManager.start(ctx)) {
            Toast.makeText(ctx, "批量刮削正在进行", Toast.LENGTH_SHORT).show()
        }
    }

    MiuixSettingsTheme {
        MiuixScaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = PageGrey,
            contentWindowInsets = WindowInsets(0.dp),
            topBar = { CoverScraperTopBar() },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 8.dp) {
                        Column(Modifier.background(NavWhite).padding(vertical = 4.dp)) {
                            sources.forEachIndexed { index, source ->
                                CoverSourceRow(
                                    index = index,
                                    count = sources.size,
                                    source = source,
                                    enabled = AppSettingsStore.isCoverScraperSourceEnabled(ctx, source),
                                    authStatus = authStatus,
                                    onEnabledChange = { enabled ->
                                        AppSettingsStore.setCoverScraperSourceEnabled(ctx, source, enabled)
                                    },
                                    onMoveUp = {
                                        AppSettingsStore.moveCoverScraperSource(ctx, source, -1)
                                        sources = AppSettingsStore.getCoverScraperSourceOrder(ctx)
                                    },
                                    onMoveDown = {
                                        AppSettingsStore.moveCoverScraperSource(ctx, source, 1)
                                        sources = AppSettingsStore.getCoverScraperSourceOrder(ctx)
                                    },
                                    onAuthClick = {
                                        if (source == AppSettingsStore.COVER_SOURCE_HIKARINAGI) {
                                            if (authStatus.authorized && !authStatus.needsReauth) {
                                                HikarinagiAuthStore.clear(ctx)
                                            } else {
                                                val error = activity?.let { HikarinagiAuthService.startAuthorization(it) }
                                                if (error != null) Toast.makeText(ctx, error, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 8.dp) {
                        Column(Modifier.background(NavWhite).padding(vertical = 4.dp)) {
                            SwitchPreference(
                                title = "仅处理缺少封面的游戏",
                                summary = "避免覆盖用户手动设置的封面",
                                checked = onlyMissing,
                                onCheckedChange = { checked ->
                                    onlyMissing = checked
                                    AppSettingsStore.setCoverScraperOnlyMissing(ctx, checked)
                                },
                            )
                        }
                    }
                }

                item {
                    MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 8.dp) {
                        Column(
                            modifier = Modifier.background(NavWhite).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "写入内容",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextColor,
                            )
                            Text(
                                "当前只保存封面图片和来源标识，不写入标题、标签或简介。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                enabled = !scraping,
                                onClick = { startBatchScrape() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    if (scraping) "正在批量刮削封面" else "开始批量刮削封面",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverSourceRow(
    index: Int,
    count: Int,
    source: String,
    enabled: Boolean,
    authStatus: com.tyranor.next.settings.HikarinagiAuthStatus,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAuthClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavWhite)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PageGrey),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                coverSourceTitle(source),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sourceSummary(source, authStatus),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = index > 0, onClick = onMoveUp) {
                    Text("上移", style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(enabled = index < count - 1, onClick = onMoveDown) {
                    Text("下移", style = MaterialTheme.typography.bodyMedium)
                }
                if (source == AppSettingsStore.COVER_SOURCE_HIKARINAGI) {
                    TextButton(onClick = onAuthClick) {
                        Text(
                            if (authStatus.authorized && !authStatus.needsReauth) "退出" else "登录",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun CoverScraperTopBar() {
    Column(modifier = Modifier.fillMaxWidth().background(PageGrey)) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "封面刮削",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun sourceSummary(source: String, authStatus: com.tyranor.next.settings.HikarinagiAuthStatus): String = when (source) {
    AppSettingsStore.COVER_SOURCE_HIKARINAGI -> when {
        authStatus.needsReauth -> "授权已失效，重新登录后参与封面搜索"
        authStatus.authorized -> "已授权，优先搜索中文条目封面"
        else -> "可选登录；未登录时自动跳过此来源"
    }
    AppSettingsStore.COVER_SOURCE_BANGUMI -> "无需登录；用于补充番组条目封面"
    AppSettingsStore.COVER_SOURCE_STEAM -> "无需登录；作为商店封面补充来源"
    AppSettingsStore.COVER_SOURCE_VNDB -> "无需登录；稳定兜底来源"
    else -> ""
}
