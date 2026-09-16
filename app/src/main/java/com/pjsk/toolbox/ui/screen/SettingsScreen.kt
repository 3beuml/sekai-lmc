package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.data.settings.ThemeMode
import com.pjsk.toolbox.ui.common.SectionTitle

/**
 * 「更多」页 = 设置 + 各类入口。
 *
 * 底部导航固定 5 项（首页 / 卡牌 / 歌曲 / 活动 / 更多），
 * 数据同步、全部数据表、贴纸制作器、关于这些低频入口都收在这里，
 * 避免底部条被塞满。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenSync: () -> Unit,
    onOpenTables: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val settings = (context.applicationContext as PjskApp).container.appSettings
    val themeMode by settings.themeMode.collectAsState()
    val nameLanguage by settings.nameLanguage.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("更多") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("外观")
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    ChoiceRow(
                        title = mode.label,
                        description = mode.description,
                        selected = themeMode == mode,
                        onClick = { settings.setThemeMode(mode) },
                    )
                }
            }

            SectionTitle("名称语言")
            Text(
                text = "数据主线用日服（版本最新），中文名来自简中服的叠加层。" +
                    "简中还没更新的内容会自动回退成日文原名。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Column(modifier = Modifier.selectableGroup()) {
                NameLanguage.entries.forEach { language ->
                    ChoiceRow(
                        title = language.label,
                        description = language.description,
                        selected = nameLanguage == language,
                        onClick = { settings.setNameLanguage(language) },
                    )
                }
            }

            SectionTitle("数据")
            NavRow("数据同步", "下载 / 更新 master data，查看同步状态", onOpenSync)
            HorizontalDivider()
            NavRow("全部数据表", "浏览全部 68 张原始数据表", onOpenTables)

            // ⚠️ 这里原来有一条「贴纸制作器」入口，2026-09-16 连同那个页面一起删掉了：
            // 表情包制作改成**外链第三方站点**（首页「工具」里那条），
            // 所以这一小节（"工具"）已经没有内容，标题也一起去掉。
            // 以后再往这里加入口时，记得把 SectionTitle("工具") 加回来。

            SectionTitle("其他")
            NavRow("关于本应用", "数据来源、许可与免责声明", onOpenAbout)

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 单选项行。 */
@Composable
private fun ChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 可点击的跳转行。 */
@Composable
private fun NavRow(title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
