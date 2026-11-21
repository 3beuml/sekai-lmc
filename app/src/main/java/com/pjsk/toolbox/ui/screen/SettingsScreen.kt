package com.pjsk.toolbox.ui.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.data.settings.ThemeMode
import com.pjsk.toolbox.data.update.AppUpdate
import com.pjsk.toolbox.data.update.UpdateCenter
import com.pjsk.toolbox.ui.common.SectionTitle
import com.pjsk.toolbox.ui.common.openExternalLink

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
    onOpenMyPlayer: () -> Unit,
    onOpenBindAccount: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PjskApp).container
    val settings = container.appSettings
    val themeMode by settings.themeMode.collectAsState()
    val nameLanguage by settings.nameLanguage.collectAsState()
    val playerAccount by settings.playerAccount.collectAsState()

    // ── 检测更新（App 自身版本）──
    // 状态存放在 container.updateCenter：开屏已静默查过一次，这里只是读同一份。
    val updateRelease by container.updateCenter.release.collectAsState()
    val mutedVersion by container.updateCenter.mutedVersion.collectAsState()
    val currentVersion = remember { AppUpdate.localVersion(context) }
    val hasUpdate = AppUpdate.shouldShowDot(currentVersion, updateRelease?.version, mutedVersion)
    var showUpdateDialog by remember { mutableStateOf(false) }

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
            // ── 我的（2026-10-16 新增）──
            //
            // 放最上面：它是唯一"和用户自己有关"的入口，比外观/数据同步更需要被看到。
            // 未绑定时只有一行「绑定游戏账号」，不打扰不想用这个功能的人。
            SectionTitle("我的")
            val account = playerAccount
            if (account == null) {
                NavRow(
                    title = "绑定游戏账号",
                    description = "填你的游戏 UID，读你自己上传到 Haruki 工具箱的数据",
                    onClick = onOpenBindAccount,
                )
            } else {
                NavRow(
                    title = "我的数据",
                    description = "${
                        when (account.server) {
                            "jp" -> "日服"
                            "cn" -> "简中服"
                            "tw" -> "港澳台服"
                            "kr" -> "韩服"
                            "en" -> "国际服"
                            else -> account.server
                        }
                    } · 卡牌 / 成绩 / 资源",
                    onClick = onOpenMyPlayer,
                )
            }

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
            NavRow(
                title = "检测更新",
                description = if (hasUpdate) {
                    "发现新版本 v${updateRelease?.version} · 点击查看"
                } else {
                    "当前 v$currentVersion · 点这里手动检查"
                },
                onClick = { showUpdateDialog = true },
                badge = hasUpdate,
            )
            NavRow("关于本应用", "数据来源、许可与免责声明", onOpenAbout)

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showUpdateDialog) {
        UpdateCheckDialog(
            current = currentVersion,
            onDismiss = { showUpdateDialog = false },
            onMute = { version -> container.updateCenter.setUpdateMutedVersion(version) },
        )
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

/** 可点击的跳转行。[badge] = 右侧显示一个小红点（目前只有「检测更新」用）。 */
@Composable
private fun NavRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    badge: Boolean = false,
) {
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
        if (badge) {
            Badge()
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 「检测更新」弹窗：结果三个分支 —— 有新版 / 已是最新 / 网络不通。
 *
 * 做法与 ZZUPocket 一致：
 *  · **不在 App 内下载 APK 再装包**（要 `REQUEST_INSTALL_PACKAGES` 与安装确认流），
 *    只做到「跳浏览器」；
 *  · 「去下载」优先用 release assets 的 .apk 直链（GitHub CDN），
 *    没有直链就打开发布页；
 *  · 「不再提醒」只屏蔽当前这一版 —— 之后出了更新的版本，入口角标仍会亮。
 *
 * 连不上时**不显示「已是最新」**：GitHub 限流、断网和「确实没有新版」必须分开说，
 * 否则用户会以为已经是最新版而一直用旧包。
 */
@Composable
private fun UpdateCheckDialog(
    current: String,
    onDismiss: () -> Unit,
    onMute: (String) -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PjskApp).container
    val release by container.updateCenter.release.collectAsState()
    val phase by container.updateCenter.phase.collectAsState()

    // 弹窗判断不受「不再提醒」影响：进来了就把当前最新版说完，角标是否还亮由入口条自己管。
    val hasNew = AppUpdate.shouldShowDot(current, release?.version, muted = null)

    fun open(url: String) {
        if (!openExternalLink(context, url)) {
            Toast.makeText(context, "没有找到可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    hasNew -> "发现新版本"
                    phase == UpdateCenter.Phase.Checking -> "正在检查更新"
                    else -> "检测更新"
                },
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    hasNew && release != null -> {
                        val r = release!!
                        Text(
                            text = "当前 v$current → 最新 v${r.version}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = r.notes.takeIf { it.isNotBlank() }
                                ?: "（这次 release 没写更新说明）",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "下载需要能访问 GitHub。打不开时可在浏览器里换网络再试。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    phase == UpdateCenter.Phase.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text("正在连接 GitHub……", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    release != null -> {
                        Text(
                            text = "当前已是最新版本 v$current",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    else -> {
                        Text(
                            text = if (phase == UpdateCenter.Phase.Done) {
                                "连不上 GitHub"
                            } else {
                                "点「检查更新」检查一次"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (phase == UpdateCenter.Phase.Done) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "可能是网络不通或 GitHub 限流，稍后再试。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hasNew && release != null) {
                TextButton(onClick = { open(release!!.downloadUrl ?: release!!.pageUrl) }) {
                    Text("去下载")
                }
            } else {
                TextButton(
                    onClick = { container.updateCenter.checkExplicit() },
                    enabled = phase != UpdateCenter.Phase.Checking,
                ) {
                    Text(if (phase == UpdateCenter.Phase.Checking) "检查中…" else "检查更新")
                }
            }
        },
        dismissButton = {
            if (hasNew && release != null) {
                Row {
                    TextButton(
                        onClick = {
                            onMute(release!!.version)
                            onDismiss()
                        },
                    ) { Text("不再提醒此版本") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}
