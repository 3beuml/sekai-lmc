package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.remote.DataModule
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.remote.TableCatalog
import com.pjsk.toolbox.data.remote.TableSpec
import com.pjsk.toolbox.data.sync.SyncManager
import kotlinx.coroutines.launch

/**
 * 数据同步页：本 App 的「下载端」。
 *
 * 设计要点：
 *  - 明确展示**已选模块的总体积**，因为服装(56MB)/扭蛋(47MB)/卡牌(35MB) 三张表加起来超过 130 MB；
 *  - 默认只勾选体积可控的四个模块，大模块必须用户主动勾选；
 *  - 默认开启「仅 WiFi 下载」；
 *  - 中文名叠加层分三档，并解释清楚「日服为主 + 简中补名」的机制与回退行为。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen() {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val syncManager = remember { container.syncManager }
    val state by syncManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    var overlayScopeName by rememberSaveable {
        mutableStateOf(SyncManager.OverlayScope.SMALL_ONLY.name)
    }
    val overlayScope = runCatching {
        SyncManager.OverlayScope.valueOf(overlayScopeName)
    }.getOrDefault(SyncManager.OverlayScope.SMALL_ONLY)

    LaunchedEffect(Unit) {
        syncManager.refreshFromDatabase()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("数据同步") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ── 区服 ────────────────────────────────────────────────
            Text("区服", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ServerRegion.entries.forEach { region ->
                    FilterChip(
                        selected = state.region == region,
                        onClick = { syncManager.setRegion(region) },
                        label = { Text(region.displayName) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "默认日服（更新最快）。切换区服会清空已下载的本地数据，" +
                    "因为同一张表不会按区服分开存放。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // ── 模块 ────────────────────────────────────────────────
            Text("数据模块", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            DataModule.entries.forEach { module ->
                val selected = module in state.selectedModules
                val bytes = TableCatalog.totalBytes(setOf(module))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = selected,
                        onClick = { syncManager.toggleModule(module) },
                        label = {
                            Text("${module.displayName}　${TableCatalog.formatBytes(bytes)}")
                        },
                    )
                }
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "已选模块预计需要下载：${TableCatalog.formatBytes(state.selectedBytes)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (state.selectedBytes > 20L * 1024 * 1024) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "体积较大，强烈建议在 Wi-Fi 下进行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 中文名叠加层 ────────────────────────────────────────
            Text("中文名叠加层", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SyncManager.OverlayScope.entries.forEach { option ->
                    val label = when (option) {
                        SyncManager.OverlayScope.NONE -> "不下载中文名"
                        SyncManager.OverlayScope.SMALL_ONLY -> "只给小表补中文名"
                        SyncManager.OverlayScope.ALL -> "所有表都补（耗流量）"
                    }
                    FilterChip(
                        selected = overlayScope == option,
                        onClick = { overlayScopeName = option.name },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "机制：以日服数据为主（进度最快），再用简中区服的同 id 数据补上中文名。" +
                    "简中尚未更新的新内容会自动显示日文原名。" +
                    "开启「所有表」会额外下载简中的 cards.json(34MB) 与 gachas.json(46MB)。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // ── 网络开关 ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("仅 WiFi 下载", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "避免在移动数据下消耗上百 MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
            }

            Spacer(Modifier.height(16.dp))

            // ── 操作 ────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !state.running,
                    onClick = { scope.launch { syncManager.checkVersion() } },
                ) { Text("检查更新") }

                Button(
                    enabled = !state.running && state.selectedModules.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            syncManager.runSync(wifiOnly = wifiOnly, overlayScope = overlayScope)
                        }
                    },
                ) { Text(if (state.running) "同步中…" else "开始同步") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("本机版本：${state.localVersion ?: "尚未同步"}")
                    state.remoteVersion?.masterVersion?.let { append("　远端版本：$it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // ── 逐表状态 ────────────────────────────────────────────
            Text("各表状态", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))

            val specs: List<TableSpec> = remember(state.selectedModules) {
                TableCatalog.forModules(state.selectedModules)
            }
            specs.forEach { spec ->
                val tableState = state.tables[spec.fileName]
                TableStatusRow(spec = spec, state = tableState)
            }

            Spacer(Modifier.height(16.dp))

            // ── 删除 ────────────────────────────────────────────────
            Text("删除本地数据", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DataModule.entries.forEach { module ->
                    OutlinedButton(
                        enabled = !state.running,
                        onClick = { scope.launch { syncManager.removeModule(module) } },
                    ) { Text(module.displayName) }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 日志 ────────────────────────────────────────────────
            Text("日志", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (state.log.isEmpty()) {
                        Text(
                            text = "还没有操作记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.log.takeLast(50).forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TableStatusRow(spec: TableSpec, state: SyncManager.TableState?) {
    // 注意：不能写成 `when (state?.phase) { ... state.rows ... }` ——
    // 对安全调用结果做 when 匹配并不会把 state 智能转换为非空，
    // 那样写会编译失败。这里先取出 phase，再用 state?.x ?: 默认值 访问。
    val phase = state?.phase
    val rows = state?.rows ?: 0

    val phaseText = when (phase) {
        null, SyncManager.Phase.PENDING -> "待同步"
        SyncManager.Phase.DOWNLOADING -> "下载中"
        SyncManager.Phase.IMPORTING -> "导入中 $rows 行"
        SyncManager.Phase.OVERLAY -> "补中文名 $rows 行"
        SyncManager.Phase.DONE -> "已完成 $rows 行"
        SyncManager.Phase.NOT_MODIFIED -> "未变化（已跳过）"
        SyncManager.Phase.FAILED -> "失败：${state?.message ?: "未知原因"}"
        SyncManager.Phase.SKIPPED -> "已跳过"
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = spec.fileName,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = TableCatalog.formatBytes(spec.approxBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = phaseText,
                style = MaterialTheme.typography.bodySmall,
                color = if (phase == SyncManager.Phase.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (phase == SyncManager.Phase.DOWNLOADING) {
            val progress = state?.progress ?: 0f
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}
