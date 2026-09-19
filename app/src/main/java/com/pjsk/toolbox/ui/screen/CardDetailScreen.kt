package com.pjsk.toolbox.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.card.Card
import com.pjsk.toolbox.data.card.CardArtPlan
import com.pjsk.toolbox.data.card.CardEpisode
import com.pjsk.toolbox.data.card.CardEvent
import com.pjsk.toolbox.data.card.CardStats
import com.pjsk.toolbox.data.card.EpisodeCost
import com.pjsk.toolbox.data.card.FALLBACK_RARITY_CAPS
import com.pjsk.toolbox.data.card.MASTER_RANK_MAX
import com.pjsk.toolbox.data.card.RarityCap
import com.pjsk.toolbox.data.card.SkillInfo
import com.pjsk.toolbox.data.card.displayNameFor
import com.pjsk.toolbox.data.card.downloadFileName
import com.pjsk.toolbox.data.card.fullStats
import com.pjsk.toolbox.data.card.masterRankBonusOf
import com.pjsk.toolbox.data.card.secondaryNameFor
import com.pjsk.toolbox.data.card.sumEpisodeBonus
import com.pjsk.toolbox.data.card.supplyType
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.ui.common.AttrIcon
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.KeyValueRow
import com.pjsk.toolbox.ui.common.RowDivider
import com.pjsk.toolbox.ui.common.SectionTitle
import com.pjsk.toolbox.ui.common.TagChip
import com.pjsk.toolbox.ui.common.TagTone
import com.pjsk.toolbox.util.AssetDownloader
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RELEASE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

/** 抽卡语音相关日志的 tag，方便 `adb logcat -s PjskVoice` 单独看。 */
private const val LOG_TAG = "PjskVoice"

/**
 * 卡牌详情。
 *
 * 结构参考 sekai.best 的 `/card/:id`：卡面区（**标签页切换，一次显示一张**，
 * 而不是并排两张）→ 基本信息 → 技能 → 能力值。
 *
 * 与 sekai.best 的两处**有意不同**：
 *  1) 能力值用「等级滑杆 + 突破滑杆」而不是两列对比 —— 一开始本项目写的是
 *     「特训前满级 / 特训后满级」两列，后来实测 `cardParameters` 的数值**逐级齐全**，
 *     滑到任意等级都是真数据，于是改成和参考站点一致的滑杆。
 *     等级上限取自 `cardRarities.json`（40→50 / 50→60），不是写死的。
 *  2) 提供「保存卡面到本地」按钮。sekai.best 把下载藏在点开大图后的查看器里，
 *     手机上没有 hover、也不容易发现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    cardId: Int,
    region: ServerRegion,
    onBack: () -> Unit,
    /**
     * 点某一篇剧情 → 进剧情模块的阅读器。
     * 带上 `assetbundleName`（卡牌剧情正文在**卡**的 bundle 下）与 `scenarioId`。
     */
    onOpenEpisode: (assetbundleName: String?, scenarioId: String, title: String?) -> Unit =
        { _, _, _ -> },
    /** 点「出自活动」→ 进剧情模块里这个活动的各话列表。 */
    onOpenEvent: (eventId: Int) -> Unit = {},
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PjskApp).container
    val repository = container.cardRepository

    // cards 现在是 StateFlow（仓库里用 stateIn 缓存了解析结果），所以不需要 initial
    val cards by repository.cards.collectAsState()
    val characters by repository.characters.collectAsState(initial = emptyList())
    val caps by repository.rarityCaps.collectAsState(initial = FALLBACK_RARITY_CAPS)
    val skills by repository.skills.collectAsState(initial = emptyMap())
    val episodesByCard by repository.cardEpisodes.collectAsState(initial = emptyMap())
    val nameLanguage by container.appSettings.nameLanguage.collectAsState()

    val card = remember(cards, cardId) { cards.firstOrNull { it.id == cardId } }
    val character = remember(characters, card) {
        card?.let { c -> characters.firstOrNull { it.id == c.characterId } }
    }
    val skill = remember(skills, card) { card?.skillId?.let { skills[it] } }
    val episodes = remember(episodesByCard, cardId) { episodesByCard[cardId].orEmpty() }

    // 素材名（剧情开放消耗要显示「帅气碎片 ×2000」而不是「素材 #2 ×2000」）
    val materials by repository.materials.collectAsState(initial = emptyMap())
    // 卡片详情的剧情板块按用户要求用**日文**道具名，所以这里另取一份
    val materialsJapanese by repository.materialsJapanese.collectAsState(initial = emptyMap())
    // 这张卡出自哪个活动（不是每张卡都有：实测 #1462 / #1 查不到）
    val cardEventIds by repository.cardEventIds.collectAsState(initial = emptyMap())
    val allEvents by repository.events.collectAsState(initial = emptyMap())
    val cardEvent = remember(cardEventIds, allEvents, cardId) {
        cardEventIds[cardId]?.let { allEvents[it] }
    }

    // 下面这几个都是**用户在这一屏上选的**，一律 rememberSaveable：
    // 从卡牌详情跳到「卡牌剧情」再返回时 composition 会重建，`remember` 会把它们全清掉。
    // 纯临时的东西（下载进度、语音探测结果）保持 `remember`，见 ui/common/Savers.kt。
    // 0 = 未特训，1 = 觉醒（特训后）
    var faceTab by rememberSaveable(cardId) { mutableIntStateOf(0) }
    var skillLevel by rememberSaveable(cardId) { mutableIntStateOf(1) }
    var statLevel by rememberSaveable(cardId) { mutableIntStateOf(1) }
    // 突破等级 0..5，0 = 未突破
    var masterRank by rememberSaveable(cardId) { mutableIntStateOf(0) }
    // 剧情是否「已读」。前篇/后篇各一个开关，因为读过之后会给能力值加成。
    var ep1Read by rememberSaveable(cardId) { mutableStateOf(false) }
    var ep2Read by rememberSaveable(cardId) { mutableStateOf(false) }

    /**
     * 抽卡语音到底存不存在。`null` = 还没探测出来。
     *
     * ⚠️ 判据不能只用 `gachaPhrase`：实测**有台词但没语音**的卡是存在的
     * （`#1468` / `res010_no056` 探测回来 404）。所以进页面时探一次，
     * 只有**明确 404** 才隐藏板块；探测因网络失败时保持 `null`（板块照常显示），
     * 让播放/下载去报真实错误 —— 不能因为一次网络抖动就把内容藏起来。
     */
    var voiceExists by remember(cardId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(cardId, card?.gachaPhrase) {
        voiceExists = null
        val target = card ?: return@LaunchedEffect
        if (target.gachaPhrase == null) return@LaunchedEffect
        AssetDownloader.exists(
            container.httpClient,
            AssetUrls.gachaVoice(region, target.assetbundleName),
        )
            .onSuccess { voiceExists = it }
            // 探测因网络失败时**当作有**：宁可显示一个可能点不动的按钮，
            // 也不要在离线时把内容整个藏掉（点击时会报出真实错误）。
            .onFailure { voiceExists = true }
    }

    // 已标记「已读」的剧情给出的能力加成（三项）。直接进能力值公式。
    val readEpisodeBonus = remember(episodes, ep1Read, ep2Read) {
        sumEpisodeBonus(
            episodes.filter { episode ->
                if (episode.part == 2) ep2Read else ep1Read
            },
        )
    }

    // ── 下载状态 ──
    // 下载全部走「App 内 OkHttp + MediaStore」，所以必须有一个协程作用域；
    // 进度用 (已下载, 总字节) 表示，总字节未知时为 -1。
    // 不加 remember(cardId)：下载中切卡不应该把进度丢掉。
    val scope = rememberCoroutineScope()
    var faceDownloading by remember { mutableStateOf(false) }
    var faceProgress by remember { mutableStateOf(0L to -1L) }
    var voiceDownloading by remember { mutableStateOf(false) }
    var voiceProgress by remember { mutableStateOf(0L to -1L) }

    fun downloadCurrentFace() {
        val current = card ?: return
        // ⚠️ **必须用 artPlan 三态判断正反面，不能只看 faceTab。**
        //
        // 这是「图片有时候下载不了」的根因：旧实现写的是 `val trained = faceTab == 1`，
        // 而「出厂即特训后」的卡（如 #1462）**根本不显示切换标签**、faceTab 恒为 0，
        // 于是它去下载一个必然 404 的 `card_normal.png`（实测 404，只有 after_training 存在）。
        // 更糟的是按钮文字用的是正确的 showingTrained —— 界面上写着「保存特训后卡面」，
        // 实际却在下载「通常」，两处自相矛盾。
        val trained = when (current.artPlan) {
            CardArtPlan.TRAINED_ONLY -> true
            CardArtPlan.NORMAL_ONLY -> false
            CardArtPlan.BOTH -> faceTab == 1
        }
        val url = AssetUrls.cardImagePng(region, current.assetbundleName, trained)
        // 用户拍板的命名：`<id>_<卡名>_<状态>.png`（如 `1465_星野一歌_特训后.png`）
        val fileName = current.downloadFileName(trained, nameLanguage)
        faceDownloading = true
        scope.launch {
            val result = AssetDownloader.download(
                context = context,
                client = container.httpClient,
                url = url,
                fileName = fileName,
                kind = AssetDownloader.Kind.IMAGE,
                onProgress = { done, total -> faceProgress = done to total },
            )
            faceDownloading = false
            faceProgress = 0L to -1L
            result
                .onSuccess { saved ->
                    Toast.makeText(
                        context,
                        "已保存到相册：${saved.displayPath}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        "保存失败：${it.message ?: "未知原因"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    /** 下载抽卡台词语音（与卡面同一套逻辑，只是落到音乐库）。 */
    fun downloadGachaVoice() {
        val current = card ?: return
        voiceDownloading = true
        scope.launch {
            val result = AssetDownloader.download(
                context = context,
                client = container.httpClient,
                url = AssetUrls.gachaVoice(region, current.assetbundleName),
                fileName = "${current.assetbundleName}_gacha_voice.mp3",
                kind = AssetDownloader.Kind.AUDIO,
                onProgress = { done, total -> voiceProgress = done to total },
            )
            voiceDownloading = false
            voiceProgress = 0L to -1L
            result
                .onSuccess { saved ->
                    Toast.makeText(
                        context,
                        "语音已保存到音乐库：${saved.displayPath}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        "语音下载失败：${it.message ?: "未知原因"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    // Android 10 以下才需要存储权限；高版本由系统 DownloadManager 代写，不弹框
    var awaitingPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && awaitingPermission) {
            downloadCurrentFace()
        } else if (!granted) {
            Toast.makeText(context, "没有存储权限，无法保存到下载目录", Toast.LENGTH_LONG).show()
        }
        awaitingPermission = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("卡牌详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val current = card
        if (current == null) {
            // ⚠️ 必须区分「数据还在加载」和「确实没有这张卡」。
            //
            // 踩过的坑：`cards` 的初始值是空列表，而 1447 张卡要解析约 1.4 MB JSON
            // （几百毫秒）。这段空白期如果直接显示「找不到这张卡」，
            // 用户点开任意一张卡都会先看到「找不到」—— 真机实测就撞上了这个。
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (cards.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    EmptyState(
                        title = "找不到这张卡",
                        description = "卡牌数据里没有 #$cardId。可能数据还没同步完整，去「更多 → 数据同步」检查一下。",
                    )
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ── 卡面区 ──
            // 只有「通常 + 特训后」两张都有的卡才需要标签；
            // 只有一张的（1★/2★/生日卡、以及出厂即特训后的联动限定卡）直接显示，不给无意义的切换。
            if (current.artPlan == CardArtPlan.BOTH) {
                TabRow(selectedTabIndex = faceTab) {
                    Tab(
                        selected = faceTab == 0,
                        onClick = { faceTab = 0 },
                        text = { Text("通常") },
                    )
                    Tab(
                        selected = faceTab == 1,
                        onClick = { faceTab = 1 },
                        text = { Text("特训后") },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // 当前该显示哪一张：三态决定，不能只看 faceTab
            val showingTrained = when (current.artPlan) {
                CardArtPlan.TRAINED_ONLY -> true
                CardArtPlan.NORMAL_ONLY -> false
                CardArtPlan.BOTH -> faceTab == 1
            }

            // 卡面区：**两层**
            //
            // 底层用列表已经在缓存里的预览档（800px）→ 打开详情**瞬间**就有卡面，
            // 不再是「慢半拍」；上层是 member 原图（2338×1440，约 512 KB），
            // 下载完自然盖住底层。借鉴 Netflix / Spotify 的 LQIP 做法：
            // 先用低清占位、高清到了再替换，观感是「变清晰」而不是「在加载」。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.68f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = AssetUrls.cardPreview(
                        region = region,
                        assetbundleName = current.assetbundleName,
                        trained = showingTrained,
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                AsyncImage(
                    model = AssetUrls.cardImage(
                        region = region,
                        assetbundleName = current.assetbundleName,
                        trained = showingTrained,
                        // 详情页用大图（2338×1440），不是列表用的 940×530
                        size = AssetUrls.CardSize.LARGE,
                    ),
                    contentDescription = current.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val needsPermission = AssetDownloader.needsStoragePermission &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        awaitingPermission = true
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        downloadCurrentFace()
                    }
                },
                enabled = !faceDownloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                // 原图 PNG 有 3.6~5.7 MB，下载要几秒，所以按钮上必须能看到进度，
                // 否则用户会以为点了没反应（之前走 DownloadManager 时甚至连失败都看不出来）。
                val (done, total) = faceProgress
                Text(
                    when {
                        !faceDownloading -> "保存${if (showingTrained) "特训后" else "通常"}卡面（原图）"
                        total > 0 -> "下载中 ${done * 100 / total}%（${formatBytes(total)}）"
                        else -> "下载中 ${formatBytes(done)}"
                    },
                )
            }

            // ── 名称与星级 ──
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(current.stars) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(18.dp),
                    )
                }
                current.attr?.let { attr ->
                    Spacer(Modifier.width(10.dp))
                    AttrIcon(attr = attr, size = 20.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(attr.label, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = current.displayNameFor(nameLanguage),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            // 副标题按语言偏好决定：例如「双语对照」下这里显示日文原名。
            // 简中服还没更新的卡 nameZh 为空，主标题本身就是日文原名，此时副标题自然为空。
            current.secondaryNameFor(nameLanguage)?.let { secondary ->
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── 基本信息 ──
            SectionTitle("基本信息")
            InfoCard {
                KeyValueRow("编号", "#${current.id}")
                KeyValueRow("稀有度", if (current.stars > 0) "★".repeat(current.stars) else current.rarityKey)
                KeyValueRow("属性") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AttrIcon(attr = current.attr, size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = current.attr?.label ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                KeyValueRow("角色", character?.displayNameFor(nameLanguage) ?: "（需同步「角色」模块）")
                character?.unitLabel?.let { KeyValueRow("组合", it) }
                // 卡池类型（口径来自官方 cardSupplies，不是猜的）：
                // 列表上的角标只写「限定」两个字，这里把完整类型写清楚 ——
                // 「期间限定 / 联动限定 / CF 限定」的差别只有在这一屏才看得全。
                KeyValueRow("卡池") {
                    val supply = current.supplyType
                    if (supply == null) {
                        Text("—", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (supply.limited) {
                                TagChip(text = "限定", tone = TagTone.ACCENT)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(supply.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                KeyValueRow("技能名", current.skillName ?: "—")
                KeyValueRow(
                    "开放时间",
                    if (current.releaseAt > 0) {
                        runCatching {
                            RELEASE_FORMATTER.format(Instant.ofEpochMilli(current.releaseAt))
                        }.getOrDefault("—")
                    } else {
                        "—"
                    },
                )
                KeyValueRow("素材名", current.assetbundleName)
            }

            // ── 技能 ──
            SectionTitle("技能")
            InfoCard {
                Text(
                    text = current.skillName ?: "（这张卡没有技能名）",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                SkillBody(
                    skill = skill,
                    level = skillLevel,
                    characterName = character?.displayNameFor(nameLanguage).orEmpty(),
                    onLevelChange = { skillLevel = it },
                )
            }

            // ── 能力值 ──
            SectionTitle("能力值")
            InfoCard {
                StatSection(
                    card = current,
                    cap = caps[current.rarityKey] ?: FALLBACK_RARITY_CAPS[current.rarityKey],
                    level = statLevel,
                    onLevelChange = { statLevel = it },
                    masterRank = masterRank,
                    onMasterRankChange = { masterRank = it },
                    episodeBonus = readEpisodeBonus,
                )
            }

            // ── 卡牌剧情 ──
            //
            // 用户要求：**没有卡牌剧情的卡不做这个功能** —— 两者都没有就整块不显示，
            // 而不是留一个写着「这张卡没有卡牌剧情。」的空板块。
            // （「所属活动」也算内容：有活动没剧情的卡仍然值得显示活动配图。）
            if (episodes.isNotEmpty() || cardEvent != null) {
                SectionTitle("卡牌剧情")
                InfoCard {
                    // 活动配图：这张卡出自哪个活动（点了进这个活动的剧情）
                    cardEvent?.let { event ->
                        EventHeader(
                            event = event,
                            region = region,
                            onClick = { onOpenEvent(event.id) },
                        )
                    }
                    if (episodes.isNotEmpty()) {
                        EpisodeSection(
                            episodes = episodes,
                            ep1Read = ep1Read,
                            ep2Read = ep2Read,
                            materials = materialsJapanese,
                            region = region,
                            onToggleRead = { part, read ->
                                if (part == 2) ep2Read = read else ep1Read = read
                            },
                            onOpenEpisode = { episode ->
                                onOpenEpisode(
                                    current.assetbundleName,
                                    episode.scenarioId.orEmpty(),
                                    episode.title,
                                )
                            },
                        )
                    }
                }
            }

            // ── 抽卡台词（扭蛋语音）──
            //
            // 显示条件 = **确认语音文件存在**（`voiceExists == true`）。
            //
            // 实测 1447 张里 347 张的 `gachaPhrase` 是 `-`（`parseCard` 已归一化成 null），
            // 另外还有「有台词但语音 404」的卡（如 `#1468`）—— 这两类都**整个板块不显示**，
            // 而不是给一个点了没反应的按钮。
            // 探测期间（null）不显示，避免对没有语音的卡先闪一下再消失。
            if (current.gachaPhrase != null && voiceExists == true) {
                SectionTitle("抽卡台词")
                InfoCard {
                    GachaVoiceSection(
                        phrase = current.gachaPhrase,
                        url = AssetUrls.gachaVoice(region, current.assetbundleName),
                        client = container.httpClient,
                        downloading = voiceDownloading,
                        progress = voiceProgress,
                        onDownload = { downloadGachaVoice() },
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * 小节容器。
 *
 * ⚠️ 这里**刻意不画框**。原来是 `Card(containerColor = surfaceVariant)`，
 * 而主题里页面背景是 `#F5FAF9`、`surfaceVariant` 是 `#DAE5E3` —— 明显更深的一块实心灰绿。
 * 详情页上「基本信息 / 技能 / 能力值 / 卡牌剧情 / 抽卡台词」五个小节各套一个，
 * 整页就变成「一摞灰色方块」，用户反馈「暗色的框住那些东西感觉怪怪的」。
 *
 * 现在内容直接落在页面背景上：分段靠 [SectionTitle] 的主色竖条，
 * 行间靠 `KeyValueRow` 自带的细分隔线，不再有任何实心色块。
 */
@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) { content() }
}

/**
 * 技能效果。
 *
 * 描述文本里的 `{{1;v}}` 之类占位符由 [SkillInfo.describe] 按等级替换成真实数值，
 * 所以这里给一个等级选择条 —— 同一个技能不同等级数值不同，不选等级只能瞎猜。
 */
@Composable
private fun SkillBody(
    skill: SkillInfo?,
    level: Int,
    /** 技能描述里 `{{c;...}}` 占位符要填的角色名——不传就只能显示 ※。 */
    characterName: String,
    onLevelChange: (Int) -> Unit,
) {
    if (skill == null) {
        Text(
            text = "同步「卡牌」模块后可以显示技能效果数值。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val maxLevel = skill.maxLevel.coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "技能等级",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        for (lv in 1..maxLevel) {
            FilterChip(
                selected = lv == level,
                onClick = { onLevelChange(lv) },
                label = { Text("$lv") },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    Text(
        text = skill.describe(level, characterName = characterName) ?: "（没有效果描述文本）",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (skill.hasChinese) {
        // 中文模板来自简中服；日文原文一起给出，方便对照。
        //
        // ⚠️ 这里**必须也走 describe()**，不能直接打印 skill.description ——
        // 那是**带占位符的原文模板**（`{{1;d}}秒間 スコアが{{1;v}}%UPする`），
        // 直接显示就会看到一堆大括号。之前的需求里「日文技能描述有问题」就是这个。
        skill.describe(level, preferChinese = false, characterName = characterName)?.let { ja ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = ja,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 抽卡台词语音：试听 + 下载。
 *
 * 只在 `card.gachaPhrase != null` 时被调用 —— 没有台词的卡（实测 347 张）整个板块不出现，
 * 而不是显示一个点了没反应的按钮。
 *
 * 播放用平台自带的 [MediaPlayer] **直接流式**拉 mp3（实测 16~22 KB，秒开），
 * 不为此引入 ExoPlayer：为几十 KB 的音频加一个几 MB 的依赖不划算。
 *
 * ⚠️ 必须在离开页面时 `release()`：否则音频会在后台继续播，而且 MediaPlayer 是
 * native 资源，不释放就是泄漏。
 */
@Composable
private fun GachaVoiceSection(
    phrase: String,
    url: String,
    client: OkHttpClient,
    downloading: Boolean,
    progress: Pair<Long, Long>,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 播放器放在 state 里只是为了能在重建时拿到同一个实例；它本身不参与渲染。
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // url 变了（切卡）或页面销毁时释放
    DisposableEffect(url) {
        onDispose {
            val p = player
            player = null
            playing = false
            p?.runCatching { release() }
        }
    }

    Text(
        text = phrase,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(10.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = {
                Log.i(LOG_TAG, "试听被点击 playing=$playing loading=$loading")
                if (playing) {
                    player?.runCatching { if (isPlaying) stop() }
                    playing = false
                    return@FilledTonalButton
                }
                if (loading) return@FilledTonalButton
                error = null
                loading = true
                scope.launch {
                    // ① 先下到本地（IO 线程）—— 不把网络放进播放器状态机
                    val cached = AssetDownloader.cacheToCacheDir(context, client, url, "gacha_voice")
                    loading = false
                    cached
                        .onSuccess { file ->
                            Log.i(LOG_TAG, "语音已就绪 ${file.absolutePath} (${file.length()} B)")
                            runCatching {
                                val mp = player ?: MediaPlayer().apply {
                                    setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                            .build(),
                                    )
                                    setOnCompletionListener { playing = false }
                                    setOnErrorListener { _, what, extra ->
                                        Log.w(LOG_TAG, "播放出错 what=$what extra=$extra")
                                        error = "播放失败（错误码 $what/$extra）"
                                        playing = false
                                        true
                                    }
                                    player = this
                                }
                                if (mp.isPlaying) mp.stop()
                                mp.reset()
                                // ② 本地文件：prepare() 是毫秒级，不会卡住主线程
                                mp.setDataSource(file.absolutePath)
                                mp.prepare()
                                mp.start()
                                playing = true
                            }.onFailure {
                                Log.w(LOG_TAG, "播放失败", it)
                                error = it.message ?: "无法播放"
                                playing = false
                            }
                        }
                        .onFailure {
                            Log.w(LOG_TAG, "语音获取失败", it)
                            error = "语音获取失败：${it.message ?: "未知原因"}"
                        }
                }
            },
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    loading -> "加载中…"
                    playing -> "停止"
                    else -> "试听"
                },
            )
        }

        OutlinedButton(onClick = onDownload, enabled = !downloading) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (downloading) {
                    val (done, total) = progress
                    if (total > 0) "下载 ${done * 100 / total}%" else "下载中 ${formatBytes(done)}"
                } else {
                    "下载语音"
                },
            )
        }
    }

    error?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(6.dp))
    Text(
        text = "语音来自 storage.sekai.best（约 20 KB，与素材同源）。下载后存到音乐库 " +
            "Music/SekaiLMC/，系统音乐播放器里就能看到。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 把字节数写得像给人看的。 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/**
 * 卡牌剧情区块：前篇 / 后篇两行。
 *
 * 两件事在这里发生：
 *  1) **「已读」开关** —— 读完一篇会给三项能力加成（`powerNBonusFixed`），
 *     实测分 5 档：前篇 +100/+150/+200/+240/+250，后篇 +200/+300/+500/+550/+600，
 *     而且**三项永远相等**（详见 [CardEpisode.powerBonus] 的表）。
 *     真机验证过联动：`#1468` 勾上前篇后 表现力 3,060 → 3,260、综合力 9,180 → 9,780（正好 +600）。
 *  2) **点进剧情详情页**（本项目在参考站点基础上多做的部分 —— sekai.best 只到 chip 为止）。
 *
 * 注意 `episodes` 为空是**正常情况**：实测 1447 张卡里 63 张没有剧情（如 `id=1462`），
 * 所以这里必须给出说明文字，而不是一片空白。
 */
@Composable
private fun EpisodeSection(
    episodes: List<CardEpisode>,
    ep1Read: Boolean,
    ep2Read: Boolean,
    materials: Map<Int, String>,
    region: ServerRegion,
    onToggleRead: (part: Int, read: Boolean) -> Unit,
    onOpenEpisode: (CardEpisode) -> Unit,
) {
    if (episodes.isEmpty()) return
    episodes.forEachIndexed { index, episode ->
        if (index > 0) RowDivider()
        val read = if (episode.part == 2) ep2Read else ep1Read
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenEpisode(episode) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.partLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = episode.title ?: "（没有标题）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 需要的素材图标（只给图标，名字在剧情详情页里）
                    MaterialIconStrip(costs = episode.costs, region = region)
                    if (episode.costs.isNotEmpty()) Spacer(Modifier.width(8.dp))
                    Text(
                        text = "读完 +${"%,d".format(episode.totalPowerBonus)} 综合力",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilterChip(
                selected = read,
                onClick = { onToggleRead(episode.part, !read) },
                label = { Text(if (read) "已读" else "未读") },
            )
        }
    }
    // 素材名字给一次说明，避免用户只看到图标不知道是什么
    if (episodes.any { it.costs.isNotEmpty() } && materials.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "素材：" + episodes.flatMap { it.costs }
                .distinctBy { it.resourceId }
                .joinToString("、") { "${materials[it.resourceId] ?: "素材 #${it.resourceId}"} ×${"%,d".format(it.quantity)}" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 一排素材图标。
 *
 * 图标按**素材 id** 命名（`thumbnail/material/material<id>.webp`），所以只要有 resourceId
 * 就能直接取图，不需要额外的映射表。名字才需要 `materials.json`。
 */
@Composable
private fun MaterialIconStrip(
    costs: List<EpisodeCost>,
    region: ServerRegion,
    iconSize: Dp = 22.dp,
) {
    if (costs.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        costs.take(MAX_STRIP_ICONS).forEach { cost ->
            AsyncImage(
                model = AssetUrls.materialIcon(region, cost.resourceId),
                contentDescription = materialsLabel(cost),
                modifier = Modifier.size(iconSize),
            )
        }
        if (costs.size > MAX_STRIP_ICONS) {
            Text(
                text = "+${costs.size - MAX_STRIP_ICONS}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 活动条：活动 logo + 「出自活动 · <名字>」。
 *
 * 数据来自 `eventCards.json`（卡 → 活动）与 `events.json`（活动名 + 素材名），
 * 图是 `event/<bundle>/logo/logo.webp`（实测唯一可靠的路径，同目录下
 * `logo_rip` / `banner` / `screen/image/bg` 全是 404）。
 */
@Composable
private fun EventHeader(event: CardEvent, region: ServerRegion, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = AssetUrls.eventLogo(region, event.assetbundleName),
            contentDescription = null,
            modifier = Modifier.height(34.dp).widthIn(max = 150.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "出自活动",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = event.name ?: "#${event.id}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 素材的读屏标签（图标没有文字，给无障碍用）。 */
private fun materialsLabel(cost: EpisodeCost): String = "素材 #${cost.resourceId}"

/** 图标条最多显示几个，多出来的折叠成「+N」。 */
private const val MAX_STRIP_ICONS = 5

/**
 * 能力值（等级滑杆 + 突破滑杆，对齐 sekai.best / pjsk.moe 的做法）。
 *
 * 两个参考站点在详情页用的都是「拖等级滑杆 + 实时显示数值」，而不是「特训前/特训后两列」。
 * 之所以能这么做，是因为实测 `cardParameters` 的数值序列**逐级齐全**
 * （1★20 档 / 2★30 / 3★50 / 4★60），不是插值出来的，滑到任意等级都是真数据。
 *
 * 特训分界：滑杆上限是**最终上限**（3★ 50 / 4★ 60），
 * 在「特训前上限」（3★ 40 / 4★ 50）处标出来；**超过这条线才把特训加成算进去**，
 * 这正是游戏里的实际规则。
 */
@Composable
private fun StatSection(
    card: Card,
    cap: RarityCap?,
    level: Int,
    onLevelChange: (Int) -> Unit,
    masterRank: Int,
    onMasterRankChange: (Int) -> Unit,
    episodeBonus: List<Int>,
) {
    val normalMax = cap?.maxLevel ?: card.maxLevel
    val trainedMax = cap?.trainingMaxLevel
    val maxLevel = (trainedMax ?: normalMax).coerceAtLeast(1)
    val clamped = level.coerceIn(1, maxLevel)
    val withBonus = trainedMax != null && clamped > normalMax
    val rankBonusPerLevel = masterRankBonusOf(card.rarityKey)
    val stats = card.fullStats(
        level = clamped,
        applyTrainingBonus = withBonus,
        masterRank = masterRank,
        episodeBonus = episodeBonus,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "等级 Lv.$clamped",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        if (withBonus) {
            Text(
                text = "已含特训加成",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    Slider(
        value = clamped.toFloat(),
        onValueChange = { onLevelChange(it.toInt()) },
        valueRange = 1f..maxLevel.toFloat(),
        steps = (maxLevel - 2).coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth(),
    )

    // 在滑杆下方标出关键刻度，用户才知道特训前后分界在哪
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (trainedMax != null) "特训前 $normalMax" else "满级 $normalMax",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trainedMax != null) {
            Text(
                text = "特训后 $trainedMax",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    HorizontalDivider()

    // ── 突破（Master Rank）──
    //
    // 加成按「稀有度 × 突破等级」算，见 MASTER_RANK_BONUS 的说明。
    // 未知稀有度（bonus == 0）时整块不显示，避免给出一个恒为 0 的假控件。
    if (rankBonusPerLevel > 0) {
        val rankClamped = masterRank.coerceIn(0, MASTER_RANK_MAX)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "突破 Lv.$rankClamped",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "+${"%,d".format(rankClamped * rankBonusPerLevel)}/项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = rankClamped.toFloat(),
            onValueChange = { onMasterRankChange(it.toInt()) },
            valueRange = 0f..MASTER_RANK_MAX.toFloat(),
            steps = MASTER_RANK_MAX - 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("未突破", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("满突破 $MASTER_RANK_MAX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
    }

    // ── 加成构成 ──
    //
    // 三项加成都是「每项加同样多」，所以这里按三项合计显示，一行讲清楚差额从哪来。
    run {
        val trainTotal = if (withBonus) card.trainBonus.sum() else 0
        val episodeTotal = episodeBonus.sum()
        val rankTotal = masterRank.coerceIn(0, MASTER_RANK_MAX) * rankBonusPerLevel * 3
        if (trainTotal + episodeTotal + rankTotal > 0) {
            val parts = buildList {
                if (trainTotal > 0) add("特训 +${"%,d".format(trainTotal)}")
                if (episodeTotal > 0) add("剧情 +${"%,d".format(episodeTotal)}")
                if (rankTotal > 0) add("突破 +${"%,d".format(rankTotal)}")
            }
            Text(
                text = "已计入加成：${parts.joinToString(" · ")}（三项合计）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
        }
    }
    STAT_ROWS.forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (row.label == "综合力") FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = "%,d".format(row.pick(stats)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (row.label == "综合力") FontWeight.Bold else FontWeight.Normal,
                color = if (row.label == "综合力") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        HorizontalDivider()
    }
}

private data class StatRowDef(val label: String, val pick: (CardStats) -> Int)

private val STAT_ROWS = listOf(
    StatRowDef("表现力") { it.vocal },
    StatRowDef("技巧") { it.dance },
    StatRowDef("体能") { it.visual },
    StatRowDef("综合力") { it.total },
)

/**
 * 数值表。
 *
 * 「特训前 / 特训后」不是两个数据集，而是**同一个等级序列的两个不同等级上限**：
 * 实测 `cardParameters` 的数值序列是 1 级到最终上限连续排列的
 * （3★ 有 50 档、4★ 有 60 档），而 `cardRarities.json` 给出
 * 「特训前上限」与「特训后上限」（3★ 40→50、4★ 50→60）。
 * 所以特训前 = 序列在 40/50 档的值，特训后 = 序列在 50/60 档的值 + 特训固定加成。
 */
@Composable
private fun StatHeaderCell(title: String, subtitle: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
