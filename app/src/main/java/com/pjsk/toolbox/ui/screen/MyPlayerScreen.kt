package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.profile.DifficultyStat
import com.pjsk.toolbox.data.profile.PlayerFetchResult
import com.pjsk.toolbox.data.profile.PlayerSnapshot
import com.pjsk.toolbox.data.profile.cardStats
import com.pjsk.toolbox.data.profile.difficultyLabel
import com.pjsk.toolbox.data.profile.materialStats
import com.pjsk.toolbox.data.profile.musicStats
import com.pjsk.toolbox.ui.common.SectionTitle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「我的」主页：玩家自己账号的概览。
 *
 * ## 它显示什么、不显示什么
 * 只显示**我们能从快照里确定读到的东西**：账号基本信息、持有卡计数、各难度 FC/AP 计数、材料计数。
 * 刻意**没有**"综合力""最优卡组"这类需要 master data 计算的指标 —— 那些要补表 + 接引擎，
 * 属于下一阶段（PROJECT_MEMORY §15.4 的 Tier B）。
 *
 * ## 为什么有「重新读取」而没有「刷新」
 * 数据是**玩家自己上传的快照**，App 无法让它变新 —— 所以不能承诺"刷新"。
 * 但用户重新上传之后必须有一个入口去拉新快照，否则只能解绑重绑。
 * 因此这里的措辞统一是「重新读取」（读的是 Haruki 上那份快照），并始终显示数据时间。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPlayerScreen(
    onBack: () -> Unit,
    onUnbound: () -> Unit,
    onOpenBind: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PjskApp).container
    val repo = remember { container.playerRepository }
    val settings = remember { container.appSettings }
    val scope = rememberCoroutineScope()

    val account by settings.playerAccount.collectAsState()

    // 先读本地快照（秒开），没有才去联网拉一次。
    var snapshot by remember(account) {
        mutableStateOf(account?.let { repo.loadCached(it) })
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 解绑：清偏好 + 删本地快照，然后返回上一页。
    // 快照文件必须一起删掉 —— 只清偏好会留下一份"没有账号却还躺在磁盘上"的玩家数据。
    val doUnbind: () -> Unit = {
        account?.let { repo.clear(it) }
        settings.setPlayerAccount(null)
        onUnbound()
    }

    LaunchedEffect(account) {
        val acc = account ?: return@LaunchedEffect
        if (snapshot != null) return@LaunchedEffect
        busy = true
        when (val r = repo.fetch(acc)) {
            is PlayerFetchResult.Success -> snapshot = r.snapshot
            is PlayerFetchResult.NotFound -> error = "读不到数据（可能还没上传，或没开「允许公开 API 访问」）。"
            is PlayerFetchResult.Failed -> error = r.message
        }
        busy = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            val acc = account
            if (acc == null) {
                Text("还没有绑定账号。", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenBind) { Text("去绑定") }
                return@Column
            }

            val snap = snapshot
            if (snap == null) {
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).padding(end = 8.dp))
                        Text("正在读取…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text("还没有可显示的数据。", style = MaterialTheme.typography.bodyMedium)
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            error = null
                            busy = true
                            scope.launch {
                                when (val r = repo.fetch(acc)) {
                                    is PlayerFetchResult.Success -> snapshot = r.snapshot
                                    is PlayerFetchResult.NotFound ->
                                        error = "读不到数据（可能还没上传，或没开「允许公开 API 访问」）。"
                                    is PlayerFetchResult.Failed -> error = r.message
                                }
                                busy = false
                            }
                        },
                    ) { Text("重新读取") }
                    TextButton(onClick = doUnbind) { Text("解除绑定") }
                }
                return@Column
            }

            PlayerAccountCard(snap = snap, server = acc.server)

            SectionTitle("卡牌")
            val cards = cardStats(snap.cards)
            StatRow("已持有", "${cards.owned} 张")
            StatRow("已特训", "${cards.trained} 张")
            StatRow("做过突破", "${cards.anyMasterRank} 张")
            StatRow("最高技能等级", "Lv.${cards.maxSkillLevel}")

            SectionTitle("歌曲成绩")
            val music = musicStats(snap.musicResults)
            if (music.isEmpty()) {
                Text(
                    text = "快照里没有歌曲成绩。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                music.forEach { diff -> DifficultyRow(diff) }
            }

            SectionTitle("资源")
            val mats = materialStats(snap.materials)
            StatRow("材料种类", "${mats.kinds} 种")
            StatRow("持有中", "${mats.nonZeroKinds} 种")
            StatRow("数量合计", "%,d".format(mats.totalQuantity))

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        error = null
                        busy = true
                        scope.launch {
                            when (val r = repo.fetch(acc)) {
                                is PlayerFetchResult.Success -> snapshot = r.snapshot
                                is PlayerFetchResult.NotFound ->
                                    error = "读不到数据（可能还没上传，或没开「允许公开 API 访问」）。"
                                is PlayerFetchResult.Failed -> error = r.message
                            }
                            busy = false
                        }
                    },
                ) { Text(if (busy) "读取中…" else "重新读取") }
                TextButton(onClick = doUnbind) { Text("解除绑定") }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "「重新读取」读的是你在 Haruki 上最新那份快照；想让数据变新，得先回 Haruki 重新上传一次。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlayerAccountCard(snap: PlayerSnapshot, server: String) {
    Column {
        Text(
            text = snap.name.ifBlank { "（未读到昵称）" },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${serverLabel(server)} · UID ${snap.account.gameId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        StatRow("等级", "Lv.${snap.rank}")
        StatRow("金币", "%,d".format(snap.coin))
        StatRow("总经验", "%,d".format(snap.totalExp))
        StatRow("数据时间", formatUploadTime(snap.uploadTime))
        Spacer(Modifier.height(6.dp))
        Text(
            text = "这是你自己上传到 Haruki 工具箱的快照，不是实时数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DifficultyRow(stat: DifficultyStat) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(difficultyLabel(stat.difficulty), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "FC ${stat.fullCombo} · AP ${stat.allPerfect} / 共 ${stat.played}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 上游给的是 Unix **秒**；格式化成设备本地时间。 */
private fun formatUploadTime(seconds: Long): String {
    if (seconds <= 0L) return "未知"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(seconds * 1000L))
}

/** 区服展示名。目前只有日服可绑定，其余先按 id 兜底。 */
private fun serverLabel(server: String): String = when (server) {
    "jp" -> "日服"
    "cn" -> "简中服"
    "tw" -> "港澳台服"
    "kr" -> "韩服"
    "en" -> "国际服"
    else -> server
}
