package com.pjsk.toolbox.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 「需要玩家数据，但这些数据现在拿不到」的门。
 *
 * 给「我的卡牌」「我的歌曲」这类**派生自绑定账号**的页面用：没绑定、或本地还没有快照。
 *
 * 为什么不直接渲染空列表：那会显示「共 0 张 / 0 首」，用户第一反应是自己的数据丢了。
 * 这里明确写出缺什么、下一步点哪里。带一个可选的返回箭头 —— 这些页是从「我的」
 * 里点进来的，系统返回键也存在，但有形箭头更明确。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDataGate(
    title: String,
    reason: String,
    actionLabel: String,
    onBack: (() -> Unit)? = null,
    onAction: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = "暂时看不到这些数据",
                description = reason,
                actionLabel = actionLabel,
                onAction = onAction,
            )
        }
    }
}
