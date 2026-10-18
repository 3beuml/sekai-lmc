package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.AppContainer
import com.pjsk.toolbox.data.deck.DeckEngine
import com.pjsk.toolbox.data.deck.buildMasterDataPayload
import com.pjsk.toolbox.data.deck.buildPowerParams
import com.pjsk.toolbox.data.deck.buildUserJson
import com.pjsk.toolbox.data.deck.restoreAreaItemLevelsJson
import com.pjsk.toolbox.data.deck.restoreAreaItemsJson
import com.pjsk.toolbox.data.deck.restoreCardsJson
import com.pjsk.toolbox.data.deck.restoreHonorsJson
import com.pjsk.toolbox.data.deck.restoreMasterLessonsJson
import com.pjsk.toolbox.data.profile.PlayerSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 组卡推荐的**冒烟版**：先验「引擎能不能跑」，不做漂亮的推荐卡片。
 *
 * 引擎对 masterdata 的字段要求极严，而我们的快照是裁剪形状、中间隔着一层还原。
 * 第一次接几乎不可能一次就对，所以这一屏把「引擎原始报错 / 耗时 / 结果 JSON」
 * 直接摊在屏幕上 —— 它不追求好看，只追求**一眼看出卡在哪一步**。
 * 跑通之后这一屏会被真正的推荐 UI 取代。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckRecommendScreen(
    onBack: () -> Unit,
    onOpenBind: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PjskApp).container
    val settings = remember { container.appSettings }
    val playerRepository = remember { container.playerRepository }
    val account by settings.playerAccount.collectAsState()
    val scope = rememberCoroutineScope()
    val engine = remember { DeckEngine(context.applicationContext) }

    var status by remember { mutableStateOf("准备中…") }
    var detail by remember { mutableStateOf("") }
    var elapsed by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf<PlayerSnapshot?>(null) }
    var engineReady by remember { mutableStateOf(false) }
    var masterLoaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // 快照：只读本地缓存，不额外请求 Haruki
    LaunchedEffect(account) {
        val acc = account
        snapshot = if (acc == null) null else withContext(Dispatchers.IO) { playerRepository.loadCached(acc) }
        if (snapshot == null) {
            status = if (acc == null) {
                "未绑定账号 —— 组卡要知道「我有哪些卡」，先去绑定。"
            } else {
                "本地没有快照 —— 去「我的」点一次「重新读取」。"
            }
        }
    }

    // 引擎事件（ready / loaded / result / error / elapsed）
    LaunchedEffect(engine) {
        for ((kind, payload) in engine.events) {
            when (kind) {
                "ready" -> {
                    engineReady = true
                    status = "引擎就绪（wasm 已加载）。"
                }
                "loaded" -> {
                    masterLoaded = true
                    busy = false
                    status = "masterdata 已载入。可以跑推荐了。"
                }
                "result" -> {
                    busy = false
                    status = "推荐完成。"
                    detail = payload
                }
                "elapsed" -> elapsed = "引擎耗时 $payload ms"
                "error" -> {
                    busy = false
                    status = "❌ 引擎报错（原文见下）"
                    detail = payload
                }
            }
        }
    }

    DisposableEffect(engine) { onDispose { engine.destroy() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("组卡推荐（冒烟）", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (engineReady) "wasm ok" else "wasm loading…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(status, style = MaterialTheme.typography.bodyMedium)
            if (elapsed.isNotBlank()) Text(elapsed, style = MaterialTheme.typography.labelSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = engineReady && !busy,
                    onClick = {
                        if (snapshot == null) {
                            status = "没有快照，先绑定并读取。"
                            return@Button
                        }
                        busy = true
                        status = "正在还原 masterdata（约 10 MB JSON，第一次要几秒）…"
                        scope.launch {
                            val md = withContext(Dispatchers.IO) { buildMasterData(container) }
                            engine.loadMasterData(md)
                            status = "已提交给引擎（${md.length} 字符），等待 loaded 事件…"
                        }
                    },
                ) { Text(if (masterLoaded) "重新载入 masterdata" else "载入 masterdata") }

                OutlinedButton(
                    enabled = masterLoaded && !busy,
                    onClick = {
                        val snap = snapshot ?: return@OutlinedButton
                        busy = true
                        status = "正在跑推荐…"
                        engine.recommend(buildUserJson(snap), buildPowerParams(limit = 5))
                    },
                ) { Text("跑一次推荐") }
            }

            if (!engineReady) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(16.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("引擎加载中（要解 1.86 MB wasm）", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (detail.isNotBlank()) {
                Text("引擎输出", style = MaterialTheme.typography.labelLarge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(8.dp),
                ) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    )
                }
            }

            // 不可见的 WebView 宿主：让 JS 活着即可
            AndroidView(
                modifier = Modifier.height(1.dp).fillMaxWidth(),
                factory = { engine.attach() },
            )
        }
    }
}

/** 组装引擎要的 masterdata（裁剪形状 → 原始表形状）。缺的表交给引擎自己兜底。 */
private suspend fun buildMasterData(container: AppContainer): String {
    val dao = container.database.masterDao()
    suspend fun rows(table: String): List<String> = dao.observeAll(table).first().map { it.data }
    return withContext(Dispatchers.IO) {
        val tables = linkedMapOf(
            "cards" to restoreCardsJson(rows("cards.json")),
            "cardEpisodes" to rows("cardEpisodes.json").joinToString(",", "[", "]"),
            "skills" to rows("skills.json").joinToString(",", "[", "]"),
            "characterRanks" to rows("characterRanks.json").joinToString(",", "[", "]"),
            "areaItems" to restoreAreaItemsJson(rows("areaItems.json")),
            "areaItemLevels" to restoreAreaItemLevelsJson(rows("areaItemLevels.json")),
            "honors" to restoreHonorsJson(rows("honors.json")),
            "masterLessons" to restoreMasterLessonsJson(rows("masterLessons.json")),
            "cardRarities" to rows("cardRarities.json").joinToString(",", "[", "]"),
            "gameCharacters" to rows("gameCharacters.json").joinToString(",", "[", "]"),
            "gameCharacterUnits" to rows("gameCharacterUnits.json").joinToString(",", "[", "]"),
        )
        buildMasterDataPayload(tables)
    }
}
