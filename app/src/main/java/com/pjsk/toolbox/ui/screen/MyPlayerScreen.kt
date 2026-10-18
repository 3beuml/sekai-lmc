package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.card.Card
import com.pjsk.toolbox.data.card.PjskCharacter
import com.pjsk.toolbox.data.card.displayNameFor
import com.pjsk.toolbox.data.profile.DifficultyStat
import com.pjsk.toolbox.data.profile.OwnedBond
import com.pjsk.toolbox.data.profile.OwnedCharacter
import com.pjsk.toolbox.data.profile.PlayerFetchResult
import com.pjsk.toolbox.data.profile.PlayerSnapshot
import com.pjsk.toolbox.data.profile.RarityBucket
import com.pjsk.toolbox.data.profile.cardStats
import com.pjsk.toolbox.data.profile.difficultyLabel
import com.pjsk.toolbox.data.profile.materialStats
import com.pjsk.toolbox.data.profile.musicStats
import com.pjsk.toolbox.data.profile.rarityDistribution
import com.pjsk.toolbox.data.profile.rarityLabel
import com.pjsk.toolbox.data.profile.sortBonds
import com.pjsk.toolbox.data.profile.sortCharacters
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.NameLanguage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「我的」个人主页。
 *
 * ## 为什么长成这样
 * 对标 pjsk.moe 的 `/profile`（它由 7 个模块拼成：Hero 卡 / 歌曲通关 / 角色等级 / 挑战 Live /
 * 羁绊 / 加成明细 / 头像）。本页按同样思路做成**卡片流**，但只做**数据支撑得住**的几块：
 *
 * | 模块 | 数据来源 |
 * | --- | --- |
 * | Hero 卡 | `userGamedata` + `userProfile.word`；头像 = 当前编成队长的卡（`profileImageType=leader`） |
 * | 卡牌 | `userCards` + 内置 `cards.json`（稀有度分布要 join） |
 * | 歌曲 | `userMusicResults` |
 * | 角色等级 | `userCharacters` + 内置 `gameCharacters`（名字） |
 * | 羁绊 | `userBonds`（⚠️ 内置表没有羁绊组名表，只能显示组 id） |
 * | 资源 | `userMaterials` |
 *
 * **没做的**：综合力构成（要补 `areaItemLevels`/`areaItems`/`honors` 三张表，属 Tier B）、
 * 挑战 Live 图（快照里只有 1 个角色的 3 个 stage，做图没意义）。
 *
 * ## 配色
 * 用**官方单位色**（`unitProfiles.colorCode`，App 早就解析好了）。
 * ⚠️ **角色专属色没有做**：内置表里没有这份数据（`gameCharacters` 无 color 字段），
 * 凭记忆硬编码 26 个颜色会违背"数据口径不许猜"的约定，所以这一版统一用单位色。
 * 想要每角色一色，需要先找到一份可引用的官方色表。
 *
 * ## 图表的做法
 * **不引入任何图表库**：横向条形就是 `Box` + `weight`；完成度环用 `Canvas.drawArc`（二十行）。
 * 这个项目的依赖只有 6 个，为几条条形图加一个图表库不划算。
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
    val cardRepository = remember { container.cardRepository }
    val musicRepository = remember { container.musicRepository }
    val scope = rememberCoroutineScope()

    val account by settings.playerAccount.collectAsState()
    val nameLanguage by settings.nameLanguage.collectAsState()
    val allCards by cardRepository.cards.collectAsState()
    val allCharacters by remember { cardRepository.characters }.collectAsState(initial = emptyList())
    val unitColors by remember { musicRepository.unitColors }.collectAsState(initial = emptyMap())

    var snapshot by remember(account) { mutableStateOf(account?.let { repo.loadCached(it) }) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 解绑：清偏好 + 删本地快照，然后返回上一页。
    val doUnbind: () -> Unit = {
        account?.let { repo.clear(it) }
        settings.setPlayerAccount(null)
        onUnbound()
    }

    val reload: () -> Unit = {
        val acc = account
        if (acc != null && !busy) {
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
        }
    }

    LaunchedEffect(account) {
        val acc = account ?: return@LaunchedEffect
        if (snapshot != null) return@LaunchedEffect
        busy = true
        when (val r = repo.fetch(acc)) {
            is PlayerFetchResult.Success -> snapshot = r.snapshot
            is PlayerFetchResult.NotFound ->
                error = "读不到数据（可能还没上传，或没开「允许公开 API 访问」）。"
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val acc = account
            if (acc == null) {
                Text("还没有绑定账号。", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpenBind) { Text("去绑定") }
                return@Column
            }

            val snap = snapshot
            if (snap == null) {
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("正在读取…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text("还没有可显示的数据。", style = MaterialTheme.typography.bodyMedium)
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = reload) { Text("重新读取") }
                    TextButton(onClick = doUnbind) { Text("解除绑定") }
                }
                return@Column
            }

            val region = ServerRegion.fromId(acc.server)
            val cardsById = remember(allCards) { allCards.associateBy { it.id } }
            val charsById = remember(allCharacters) { allCharacters.associateBy { it.id } }
            val leaderCard = cardsById[snap.deckLeaderCardId]
            val accent = remember(leaderCard, unitColors, charsById) {
                unitColorOf(leaderCard, charsById, unitColors) ?: Color(0xFF6750A4)
            }

            HeroCard(snap = snap, server = acc.server, region = region, leaderCard = leaderCard, accent = accent)

            CardModule("卡牌") {
                val stats = cardStats(snap.cards)
                TripleStat(
                    a = "已持有" to "${stats.owned}",
                    b = "已特训" to "${stats.trained}",
                    c = "做过突破" to "${stats.anyMasterRank}",
                )
                Spacer(Modifier.height(10.dp))
                val dist = rarityDistribution(snap.cards) { id -> cardsById[id]?.rarityKey }
                if (dist.isEmpty()) {
                    HintText("快照里没有卡牌数据。")
                } else {
                    val max = dist.maxOf { it.count }.coerceAtLeast(1)
                    dist.forEach { bucket -> RarityBarRow(bucket, max) }
                }
            }

            CardModule("歌曲成绩") {
                val music = musicStats(snap.musicResults)
                if (music.isEmpty()) {
                    HintText("快照里没有歌曲成绩。")
                } else {
                    val totalPlayed = music.sumOf { it.played }
                    val totalAp = music.sumOf { it.allPerfect }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(
                            fraction = if (totalPlayed == 0) 0f else totalAp.toFloat() / totalPlayed,
                            centerTop = "$totalAp",
                            centerBottom = "AP",
                            color = accent,
                        )
                        Spacer(Modifier.width(16.dp))
                        HintText("AP 占全部已打曲目的比例（按难度分别见下面）")
                    }
                    Spacer(Modifier.height(10.dp))
                    val maxPlayed = music.maxOf { it.played }.coerceAtLeast(1)
                    music.forEach { diff -> MusicBarRow(diff, maxPlayed, accent) }
                }
            }

            CardModule("角色等级") {
                val chars = sortCharacters(snap.characters)
                if (chars.isEmpty()) {
                    HintText("快照里没有角色等级数据。")
                } else {
                    chars.chunked(6).forEach { rowChars ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            rowChars.forEach { owned ->
                                CharacterCell(
                                    owned = owned,
                                    character = charsById[owned.characterId],
                                    nameLanguage = nameLanguage,
                                    region = region,
                                    accent = unitColorOf(charsById[owned.characterId], unitColors) ?: accent,
                                )
                            }
                            repeat(6 - rowChars.size) { Spacer(Modifier.width(48.dp)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            CardModule("羁绊") {
                val bonds = sortBonds(snap.bonds)
                if (bonds.isEmpty()) {
                    HintText("快照里没有羁绊数据。")
                } else {
                    HintText("共 ${bonds.size} 组 · 内置数据里没有羁绊组的名称表，只能显示组 id")
                    Spacer(Modifier.height(6.dp))
                    val shown = bonds.take(8)
                    val maxRank = shown.maxOf { it.rank }.coerceAtLeast(1)
                    shown.forEach { bond -> BondBarRow(bond, maxRank) }
                    if (bonds.size > shown.size) {
                        HintText("其余 ${bonds.size - shown.size} 组按等级排在后面")
                    }
                }
            }

            CardModule("资源") {
                val mats = materialStats(snap.materials)
                TripleStat(
                    a = "材料种类" to "${mats.kinds}",
                    b = "持有中" to "${mats.nonZeroKinds}",
                    c = "数量合计" to "%,d".format(mats.totalQuantity),
                )
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = reload) {
                    Text(if (busy) "读取中…" else "重新读取")
                }
                TextButton(onClick = doUnbind) { Text("解除绑定") }
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            HintText("「重新读取」读的是你在 Haruki 上最新那份快照；想让数据变新，得先回 Haruki 重新上传一次。")
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 模块
// ─────────────────────────────────────────────────────────────

/** 一个模块卡片：标题 + 内容。 */
@Composable
private fun CardModule(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun HeroCard(
    snap: PlayerSnapshot,
    server: String,
    region: ServerRegion,
    leaderCard: Card?,
    accent: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.14f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 头像 = 当前编成队长的卡图标（profileImageType 实测是 "leader"）
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (leaderCard != null) {
                        AsyncImage(
                            model = AssetUrls.cardIcon(
                                region,
                                leaderCard.assetbundleName,
                                !leaderCard.trainedOnly,
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("?", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = snap.name.ifBlank { "（未读到昵称）" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = serverLabel(server) + " · Lv." + snap.rank + " · 金币 " + "%,d".format(snap.coin),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (snap.signature.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = snap.signature,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "数据时间 " + formatUploadTime(snap.uploadTime) +
                    " · 这是你自己上传到 Haruki 工具箱的快照，不是实时数据",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 小部件
// ─────────────────────────────────────────────────────────────

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 三列等宽数字块。 */
@Composable
private fun TripleStat(a: Pair<String, String>, b: Pair<String, String>, c: Pair<String, String>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(a, b, c).forEach { pair ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    pair.second,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    pair.first,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 一条横向条形：左边标签、中间进度条、右边数值。
 *
 * 用 `Box` + `fillMaxWidth(fraction)` 画，**不需要 Canvas**（这是不引图表库的底气）。
 */
@Composable
private fun BarRow(
    label: String,
    fraction: Float,
    color: Color,
    valueText: String,
    labelWidth: Int = 56,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(labelWidth.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(84.dp),
        )
    }
}

@Composable
private fun RarityBarRow(bucket: RarityBucket, max: Int) {
    BarRow(
        label = rarityLabel(bucket.rarityKey),
        fraction = bucket.count.toFloat() / max,
        color = MaterialTheme.colorScheme.primary,
        valueText = "${bucket.count} 张",
    )
}

@Composable
private fun MusicBarRow(stat: DifficultyStat, max: Int, accent: Color) {
    BarRow(
        label = difficultyLabel(stat.difficulty),
        fraction = stat.played.toFloat() / max,
        color = accent,
        valueText = "FC ${stat.fullCombo} · AP ${stat.allPerfect}",
    )
}

@Composable
private fun BondBarRow(bond: OwnedBond, maxRank: Int) {
    BarRow(
        label = "${bond.bondsGroupId}",
        fraction = bond.rank.toFloat() / maxRank,
        color = MaterialTheme.colorScheme.tertiary,
        valueText = "Lv.${bond.rank}",
    )
}

/** 一个角色格：头像 + 等级，底色用该角色所属单位的官方色。 */
@Composable
private fun CharacterCell(
    owned: OwnedCharacter,
    character: PjskCharacter?,
    nameLanguage: NameLanguage,
    region: ServerRegion,
    accent: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = AssetUrls.characterAvatar(region, owned.characterId),
                contentDescription = character?.displayNameFor(nameLanguage),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${owned.characterRank}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 完成度环。
 *
 * `Canvas.drawArc` 二十行就够 —— 又一次说明这里不需要图表库。
 */
@Composable
private fun ProgressRing(
    fraction: Float,
    centerTop: String,
    centerBottom: String,
    color: Color,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val stroke = 9.dp.toPx()
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                centerBottom,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 工具
// ─────────────────────────────────────────────────────────────

/** 角色所属单位的官方色（`unitProfiles.colorCode`）。查不到返回 null。 */
private fun unitColorOf(character: PjskCharacter?, unitColors: Map<String, String>): Color? =
    character?.unitKey?.let { key -> parseHexColor(unitColors[key]) }

/** 队长卡的卡 → 角色 → 单位 → 颜色。 */
private fun unitColorOf(
    leaderCard: Card?,
    charsById: Map<Int, PjskCharacter>,
    unitColors: Map<String, String>,
): Color? = unitColorOf(leaderCard?.characterId?.let { charsById[it] }, unitColors)

private fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching {
        val clean = hex.removePrefix("#")
        when (clean.length) {
            6 -> Color(("FF" + clean).toLong(16))
            8 -> Color(clean.toLong(16))
            else -> null
        }
    }.getOrNull()
}

/** 上游给的是 Unix **秒**；格式化成设备本地时间。 */
private fun formatUploadTime(seconds: Long): String {
    if (seconds <= 0L) return "未知"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(seconds * 1000L))
}

private fun serverLabel(server: String): String = when (server) {
    "jp" -> "日服"
    "cn" -> "简中服"
    "tw" -> "港澳台服"
    "kr" -> "韩服"
    "en" -> "国际服"
    else -> server
}
