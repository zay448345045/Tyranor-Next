package com.tyranor.next.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 占位页面：固定 64dp 高度顶部栏(标题居左)，无背景色(与页面背景一致，沉浸式)。
 * 正文居中显示标题和一句用于区分的话。
 */
@Composable
internal fun PlaceholderPage(title: String, description: String, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxSize()) {
    // 顶部栏：标题区固定 64dp，无独立背景色
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
      }
    }
    // 正文区域
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(
        description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 16.dp),
      )
    }
  }
}