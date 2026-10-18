package com.pjsk.toolbox.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 筛选面板的**外壳**：标题 + 右上「重置」+ 可滚动的内容 + 底部结果按钮。
 *
 * 为什么抽出来：「歌曲」页和「我的歌曲」页的筛选面板除了中间那几组条件以外完全一样
 * （同样的浮层、同样的 560dp 上限、同样的重置与结果按钮）。两边各写一份的结果是
 * 「一个面板改了间距/上限，另一个没改」，用户会觉得两个筛选器手感不同。
 *
 * ⚠️ 这里只管**壳**，不管里面有哪些组 —— 各页的组合/分类/状态条件差别很大，
 * 硬塞进一个模型反而会把两种页面的需求互相污染。内容由调用方用
 * [FilterGroupTitle] + chip 自己拼。
 *
 * [footerLabel] 由调用方算好（例如「显示 320 / 372 首」）—— 面板不知道分母是什么。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    footerLabel: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("筛选", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onReset) { Text("重置") }
            }
            content()
            Spacer(Modifier.height(14.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(footerLabel)
            }
        }
    }
}

/** 一个筛选组的标题。 */
@Composable
fun FilterGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

/** 组标题下面的一句小字，用来说明这一组的计数口径（口径不说清，数字就会被当成 bug）。 */
@Composable
fun FilterGroupHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
