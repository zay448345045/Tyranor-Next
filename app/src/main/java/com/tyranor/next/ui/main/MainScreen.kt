package com.tyranor.next.ui.main

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tyranor.next.R
import com.tyranor.next.settings.AppSettingsStore
import com.tyranor.next.theme.UnselectedGrey
import com.tyranor.next.ui.common.LiquidGlassNavItem
import com.tyranor.next.ui.common.LiquidGlassNavigationBar
import com.tyranor.next.ui.common.WithoutPressIndication
import com.tyranor.next.ui.pages.EngineScreen
import com.tyranor.next.ui.pages.GameScreen
import com.tyranor.next.ui.pages.HomeScreen
import com.tyranor.next.ui.pages.SettingsScreen
import kotlinx.coroutines.launch

// 底部导航栏 Tab 定义
private data class Tab(
  val label: String,
  val iconRes: Int,
)

private val tabItems = listOf(
  Tab("首页", R.drawable.ic_home),
  Tab("游戏", R.drawable.ic_game),
  Tab("引擎", R.drawable.ic_module),
  Tab("设置", R.drawable.ic_settings),
)

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var selectedIndex by rememberSaveable { mutableStateOf(0) }
  val pagerState = rememberPagerState(initialPage = selectedIndex, pageCount = { tabItems.size })
  val scope = rememberCoroutineScope()
  val unselectedColor = UnselectedGrey
  // 导航栏样式：应用设置 → 默认 / 圆角液态玻璃（内存态，设置页切换即时生效）
  LaunchedEffect(Unit) {
    AppSettingsStore.initNavStyle(context)
    AppSettingsStore.initGameSort(context)
  }
  val liquidGlass = AppSettingsStore.navStyleState.value == AppSettingsStore.NAV_STYLE_LIQUID_GLASS

  fun selectPage(index: Int) {
    if (index == selectedIndex) return
    selectedIndex = index
    scope.launch {
      pagerState.animateScrollToPage(index, animationSpec = tween(durationMillis = 240))
    }
  }

  // 外层只负责布局：内容区 + 底部导航栏（不用 Scaffold，避免与子页顶部栏的 inset 冲突）
  Box(modifier.fillMaxSize()) {
    // 内容层录制进 backdrop，供液态玻璃导航采样页面内容。
    // 关键：背景必须在 layerBackdrop 之后（内层）——layerBackdrop 只录制它之后的内容，
    // 放在外层（Surface/Column 背景）的内容不会被采样，玻璃会采到透明而漏出文字。
    val backdrop = rememberLayerBackdrop()
    val contentModifier = Modifier
      .fillMaxSize()
      .then(if (liquidGlass) Modifier.layerBackdrop(backdrop) else Modifier)
      .background(MaterialTheme.colorScheme.background)
    Column(contentModifier) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
        WithoutPressIndication {
          HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 仅预组合相邻页：目标页在点击前已就绪，同时避免四个完整页面参与每帧测量。
            beyondViewportPageCount = 1,
            userScrollEnabled = false,
          ) { page ->
            when (page) {
              0 -> HomeScreen(Modifier.fillMaxSize())
              1 -> GameScreen(Modifier.fillMaxSize())
              2 -> EngineScreen(Modifier.fillMaxSize())
              3 -> SettingsScreen(Modifier.fillMaxSize())
            }
          }
        }
      }
      if (!liquidGlass) {
        NavigationBar(
          containerColor = com.tyranor.next.theme.NavWhite,
          contentColor = androidx.compose.material3.LocalContentColor.current,
        ) {
          tabItems.forEachIndexed { index, tab ->
            val selected = selectedIndex == index
            val itemColor = if (selected) MaterialTheme.colorScheme.primary else unselectedColor
            NavigationBarItem(
              selected = selected,
              onClick = { selectPage(index) },
              icon = {
                Image(
                  painter = painterResource(tab.iconRes),
                  contentDescription = tab.label,
                  colorFilter = ColorFilter.tint(itemColor),
                  modifier = Modifier.size(28.dp),
                )
              },
              label = { Text(tab.label) },
              // 去掉选中高亮：仅图标与文字通过主题色区分选中态
              colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = Color.Transparent,
                unselectedIconColor = unselectedColor,
                unselectedTextColor = unselectedColor,
              ),
            )
          }
        }
      }
    }

    // 圆角液态玻璃导航：悬浮在内容之上
    if (liquidGlass) {
      LiquidGlassNavigationBar(
        backdrop = backdrop,
        selectedIndex = selectedIndex,
        primaryColor = MaterialTheme.colorScheme.primary,
        unselectedColor = unselectedColor,
        items = tabItems.map { LiquidGlassNavItem(it.label, it.iconRes) },
        onItemClick = { selectPage(it) },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}
