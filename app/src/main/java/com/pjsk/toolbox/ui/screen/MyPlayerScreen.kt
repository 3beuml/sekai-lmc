package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.pjsk.toolbox.data.profile.BondPair
import com.pjsk.toolbox.data.profile.DifficultyStat
import com.pjsk.toolbox.data.profile.OwnedBond
import com.pjsk.toolbox.data.profile.OwnedCharacter
import com.pjsk.toolbox.data.profile.PlayerFetchResult
import com.pjsk.toolbox.data.profile.PlayerSnapshot
import com.pjsk.toolbox.data.profile.RarityBucket
import com.pjsk.toolbox.data.profile.availableDifficulties
import com.pjsk.toolbox.data.profile.cardStats
import com.pjsk.toolbox.data.profile.computeDeckPower
import com.pjsk.toolbox.data.profile.difficultyLabel
import com.pjsk.toolbox.data.profile.materialStats
import com.pjsk.toolbox.data.profile.musicStats
import com.pjsk.toolbox.data.profile.rarityDistribution
import com.pjsk.toolbox.data.profile.rarityLabel
import com.pjsk.toolbox.data.profile.sortBonds
import com.pjsk.toolbox.data.profile.sortCharacters
import com.pjsk.toolbox.data.profile.songsWithDifficulty
import com.pjsk.toolbox.data.settings.ScoreMetric
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.data.score.Best30Entry
import com.pjsk.toolbox.data.score.Best30Result
import com.pjsk.toolbox.data.score.ChartConstants
import com.pjsk.toolbox.data.score.computeBest30
import com.pjsk.toolbox.data.score.formatBest30
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** 「卡牌」模块点进去 → 卡牌图鉴的「我的卡牌」模式（已拥有 / 未拥有）。 */
    onOpenMyCards: () -> Unit,
    /** 「歌曲成绩」模块点进去 → 按难度看已打 / FC / AP，并筛出没 FC / 没 AP 的曲目。 */
    onOpenMyMusic: () -> Unit,
    /** Best30 模块点进去 → 独立页面（带曲绘的 30 条完整列表）。 */
    onOpenBest30: () -> Unit,
    /** 「编成」页：逐套摊开看 5 张卡 + 各自的综合力。 */
    onOpenMyDecks: () -> Unit,
    /** 组卡推荐（批次 D 冒烟）。 */
    onOpenDeckRecommend: () -> Unit,
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

    // 把快照里的原始 id 翻译成人读的东西：羁绊组名、角色专属色（都来自 master data）
    val masterRef = remember { container.masterRefRepository }
    val characterColors by remember { masterRef.characterColors }.collectAsState(initial = emptyMap())
    val bondPairs by remember { masterRef.bondPairs }.collectAsState(initial = emptyMap())

    var snapshot by remember(account) { mutableStateOf(account?.let { repo.loadCached(it) }) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // 羁绊面板的当前层级。放在 Scaffold 外面，因为面板本身要画在整页之上。
    var sheet by remember(account) { mutableStateOf<BondSheetState>(BondSheetState.None) }

    // 这几个提到 Scaffold 外面：羁绊面板画在 Scaffold 之后，拿不到 Column 内部的局部变量。
    val region = account?.let { ServerRegion.fromId(it.server) } ?: ServerRegion.DEFAULT
    val charsById = remember(allCharacters) { allCharacters.associateBy { it.id } }

    // 歌曲成绩的分母：「每个难度有多少首曲目」。和「我的歌曲」用同一个函数算，
    // 两处的百分比必须对得上，否则用户会觉得其中一处在骗人。
    val scoreMetric by settings.scoreMetric.collectAsState()
    val difficultyLevels by remember { musicRepository.difficultyLevels }.collectAsState(initial = emptyMap())
    val allMusics by remember { container.homeRepository.musics }.collectAsState(initial = emptyList())
    val songsTotal = remember(allMusics, difficultyLevels) {
        availableDifficulties(difficultyLevels).associateWith { d ->
            songsWithDifficulty(allMusics, difficultyLevels, d).size
        }
    }
    val allMusicsById = remember(allMusics) { allMusics.associateBy { it.id } }

    // ── 综合力用的 master data ──
    // 四张表都在 masterRefRepository 里：区域道具加成率 / 称号加成 / 角色等级加成率；
    // 卡牌剧情来自 cardRepository（算基础值时要加已读剧情的加成）。
    val areaItemLevels by remember { masterRef.areaItemLevels }.collectAsState(initial = emptyMap())
    val honorBonuses by remember { masterRef.honorBonuses }.collectAsState(initial = emptyMap())
    val characterRankRates by remember { masterRef.characterRankRates }.collectAsState(initial = emptyMap())
    val episodesByCard by remember { cardRepository.cardEpisodes }.collectAsState(initial = emptyMap())

    // Best30 的定数表是内置资源（27 KB CSV）。读盘 + 解析放 IO；读不出来就是空表，
    // 那一块整个不显示，不影响页面其余部分。
    val chartConstants by produceState<Map<Int, Map<String, Double>>>(emptyMap()) {
        value = withContext(Dispatchers.IO) {
            ChartConstants.load { context.assets.open(CHART_CONSTANTS_ASSET) }
        }
    }

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

            val cardsById = remember(allCards) { allCards.associateBy { it.id } }
            val leaderCard = cardsById[snap.deckLeaderCardId]
            val accent = remember(leaderCard, unitColors, charsById) {
                unitColorOf(leaderCard, charsById, unitColors) ?: Color(0xFF6750A4)
            }
            val bondsAll = remember(snap.bonds) { sortBonds(snap.bonds) }
            // 综合力：当前编成的持有卡 → 纯函数计算（算不出来就是 null，界面整块不显示）
            val power = remember(
                snap,
                cardsById,
                allCharacters,
                areaItemLevels,
                characterRankRates,
                honorBonuses,
                episodesByCard,
            ) {
                val deckOwned = snap.deckCardIds.mapNotNull { id ->
                    snap.cards.firstOrNull { it.cardId == id }
                }
                val unitOf = { card: com.pjsk.toolbox.data.card.Card ->
                    val base = allCharacters.firstOrNull { it.id == card.characterId }?.unitKey
                    val support = card.supportUnit?.takeIf { it.isNotBlank() && it != "none" }
                    buildList {
                        support?.let { add(it) }
                        base?.let { add(it) }
                    }.distinct()
                }
                computeDeckPower(
                    deckCards = deckOwned,
                    cards = cardsById,
                    episodesByCard = episodesByCard,
                    userCharacterRanks = snap.characters.associate { it.characterId to it.characterRank },
                    characterRankRates = characterRankRates,
                    userAreaItems = snap.areaItems,
                    areaItemLevels = areaItemLevels,
                    honorBonuses = honorBonuses,
                    userHonors = snap.honors,
                    cardUnits = unitOf,
                )
            }
            val best30 = remember(snap.musicResults, chartConstants) {
                computeBest30(snap.musicResults, chartConstants)
            }

            HeroCard(snap = snap, server = acc.server, region = region, leaderCard = leaderCard, accent = accent)

            CardModule("卡牌", actionLabel = "我的卡牌", onAction = onOpenMyCards) {
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

            CardModule(
                title = "歌曲成绩",
                actionLabel = "我的歌曲",
                onAction = onOpenMyMusic,
            ) {
                val music = musicStats(snap.musicResults)
                if (music.isEmpty()) {
                    HintText("快照里没有歌曲成绩。")
                } else {
                    // 选中指标从偏好里来（默认 AP），切了之后「我的歌曲」也跟着进对应那一档
                    val metricCount: (DifficultyStat) -> Int = { stat ->
                        if (scoreMetric == ScoreMetric.AP) stat.allPerfect else stat.fullCombo
                    }
                    val totalPlayed = music.sumOf { it.played }
                    val totalMetric = music.sumOf(metricCount)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(
                            fraction = if (totalPlayed == 0) 0f else totalMetric.toFloat() / totalPlayed,
                            centerTop = "$totalMetric",
                            centerBottom = scoreMetric.label,
                            color = accent,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // AP ↔ FC 二态切换：点标签就切（用户要求），默认 AP
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ScoreMetric.entries.forEach { option ->
                                    FilterChip(
                                        selected = scoreMetric == option,
                                        onClick = { settings.setScoreMetric(option) },
                                        label = { Text(option.label) },
                                    )
                                }
                            }
                            HintText(
                                "${scoreMetric.label} 占全部已打曲目的比例。" +
                                    "FC 含 AP，所以切到 FC 数字只会更多；按难度见下面。",
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // 各难度的条：分子是选中指标，分母是**内置谱面里真有这一难度**的曲目数
                    // —— 和「我的歌曲」用同一个口径，不然两处的百分比对不上。
                    music.forEach { diff ->
                        val total = songsTotal[diff.difficulty] ?: 0
                        BarRow(
                            label = difficultyLabel(diff.difficulty),
                            fraction = if (total <= 0) 0f else metricCount(diff).toFloat() / total,
                            color = accent,
                            valueText = "${metricCount(diff)} / $total",
                            labelWidth = 52,
                            valueWidth = 92,
                        )
                    }
                }
            }

            // ── Best30 ──
            // 只有「定数表读出来了 + 至少有一条 AP/FC」时才显示；否则整块不出现
            if (!best30.isEmpty) {
                CardModule(
                    title = "Best30",
                    actionLabel = "查看全部 ${best30.entries.size} 首",
                    onAction = onOpenBest30,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatBest30(best30.average),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "平均定数 · 已计入 ${best30.entries.size} / ${Best30Result.LIMIT}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            HintText(
                                if (best30.isComplete) {
                                    "AP 计定数、FC 计定数 − 1；只看 AP/FC，不看高分。"
                                } else {
                                    "合格谱面只有 ${best30.poolSize} 个（不足 30），这个平均值与社区的 B30 不可比。"
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // 预览前几条也带曲绘：30 条里大部分歌光看名字认不出是哪首
                    best30.entries.take(BEST30_PREVIEW).forEachIndexed { index, entry ->
                        val song = allMusicsById[entry.musicId]
                        Best30ListRow(
                            rank = index + 1,
                            entry = entry,
                            title = song?.title,
                            jacketUrl = song?.let { AssetUrls.musicJacket(region, it.assetbundleName) },
                            onClick = onOpenBest30,
                        )
                    }
                }
            }

            // ── 综合力 ──
            // 只有在「编成有卡 + 至少能算出基础值」时才显示；算不出来就整块不出现（不显示 0）
            power?.let { p ->
                CardModule(
                    title = "综合力",
                    // 用户反馈只看得到「5 张卡」看不到是哪几张 —— 用这个入口去「编成」页
                    actionLabel = "查看编成",
                    onAction = onOpenMyDecks,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "%,d".format(p.total),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${p.cardCount} 张卡",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    PowerRow("卡牌基础", p.base, p.total, MaterialTheme.colorScheme.primary)
                    PowerRow("区域道具", p.areaItemBonus, p.total, MaterialTheme.colorScheme.secondary)
                    PowerRow("角色等级", p.characterBonus, p.total, MaterialTheme.colorScheme.tertiary)
                    PowerRow("称号", p.honorBonus, p.total, MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(4.dp))
                    HintText(
                        "按游戏公式本地估算：不含自定义世界（烤森）的家具/大门加成。" +
                            "诊断：区域道具 ${p.areaItemCount} 件、称号 ${p.honorCount} 个。",
                    )
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
                                    // 优先用这个角色自己的官方主题色（gameCharacterUnits.colorCode），
                                    // 取不到才退回队长卡所在单位的色 —— 这样 26 格是 26 种颜色，
                                    // 而不是"同团 4 人同色"。
                                    accent = parseHexColor(characterColors[owned.characterId]) ?: accent,
                                )
                            }
                            repeat(6 - rowChars.size) { Spacer(Modifier.width(48.dp)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            CardModule(
                title = "羁绊",
                // 只列前 8 组，其余用一个入口 —— 31 组一次性铺开会把下面的按钮推出屏外。
                actionLabel = if (bondsAll.size > BOND_PREVIEW) "全部 ${bondsAll.size} 组" else null,
                onAction = { sheet = BondSheetState.All },
            ) {
                val bonds = bondsAll
                if (bonds.isEmpty()) {
                    HintText("快照里没有羁绊数据。")
                } else {
                    val shown = bonds.take(BOND_PREVIEW)
                    val maxRank = shown.maxOf { it.rank }.coerceAtLeast(1)
                    shown.forEach { bond ->
                        BondRowItem(
                            bond = bond,
                            pair = bondPairs[bond.bondsGroupId],
                            charsById = charsById,
                            characterColors = characterColors,
                            nameLanguage = nameLanguage,
                            maxRank = maxRank,
                            // 组名翻不出来也不必禁点：详情里仍然能看到组 id 和等级
                            onClick = { sheet = BondSheetState.Detail(bond.bondsGroupId) },
                        )
                    }
                    if (bonds.size > shown.size) {
                        HintText("其余 ${bonds.size - shown.size} 组按等级排在后面，点上面的「全部」看完整列表")
                    }
                }
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

    // 羁绊面板。全页级的浮层，所以放在 Scaffold 之后而不是模块内部 ——
    // 模块里有 verticalScroll，塞在里面的浮层会被父级的滚动裁切。
    if (sheet != BondSheetState.None) {
        BondSheet(
            state = sheet,
            bonds = remember(snapshot) { sortBonds(snapshot?.bonds.orEmpty()) },
            bondPairs = bondPairs,
            charsById = charsById,
            characterColors = characterColors,
            nameLanguage = nameLanguage,
            region = region,
            onNavigate = { sheet = it },
            onDismiss = { sheet = BondSheetState.None },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 模块
// ─────────────────────────────────────────────────────────────

/**
 * 一个模块卡片：标题 +（可选的）右上角入口 + 内容。
 *
 * [actionLabel] 非空时右上角出现一个文字按钮 —— 比"整张卡片都能点"更明确：
 * 模块里还有图表，整块可点会让用户不知道点了会发生什么。
 */
@Composable
private fun CardModule(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (actionLabel != null && onAction != null) {
                    // 负内边距靠 TextButton 自己的最小尺寸压住，视觉上贴着卡片右上角
                    TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(if (actionLabel != null) 4.dp else 10.dp))
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
    /** 右侧数值列的宽度。「12 / 372」这种要宽一些，`Lv.8` 只要 84dp。 */
    valueWidth: Int = 84,
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
            modifier = Modifier.width(valueWidth.dp),
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

/**
 * 综合力的一行：标签 · 占比条 · 点数。
 *
 * 条的比例用「占总数」而不是「占最大项」—— 四项加起来正好 100%，
 * 一眼就能看出哪一项贡献最大，也不用额外解释刻度。
 */
@Composable
private fun PowerRow(label: String, value: Int, total: Int, color: Color) {
    BarRow(
        label = label,
        fraction = if (total <= 0) 0f else value.toFloat() / total,
        color = color,
        valueText = "%,d".format(value),
        labelWidth = 64,
        valueWidth = 84,
    )
}

// ─────────────────────────────────────────────────────────────
// Best30
// ─────────────────────────────────────────────────────────────

/**
 * 模块里先列几条 —— 其余进独立页面（`Best30Screen`）。
 *
 * 为什么不再用底部面板：30 条带曲绘的记录塞进 480dp 的浮层只能看到 7 条，
 * 翻起来很难受；而且曲绘是认歌的唯一可靠线索，值得给一整屏。
 */
private const val BEST30_PREVIEW = 5

/** 内置定数表在 assets 里的路径（生成脚本见 tools/pjskb30/build-asset.mjs）。 */
private const val CHART_CONSTANTS_ASSET = "pjskb30/chart-constants.csv"

// ─────────────────────────────────────────────────────────────
// 羁绊：一行 + 面板 + 详情
// ─────────────────────────────────────────────────────────────

/** 「我的」页里先列几组羁绊（其余进「全部」面板）。 */
private const val BOND_PREVIEW = 8

/**
 * 羁绊面板的层级。
 *
 * 一个 `ModalBottomSheet` 内部换内容，而不是叠两个 sheet：
 * 嵌套 sheet 在 Compose 里会盖住父 sheet 的拖拽手柄，返回也说不清回到哪一层。
 */
private sealed interface BondSheetState {
    data object None : BondSheetState

    /** 全部羁绊（按等级降序）。 */
    data object All : BondSheetState

    /** 某一组的详情。 */
    data class Detail(val groupId: Int) : BondSheetState
}

/**
 * 一行羁绊：上面「两个色点 + 组名 + 等级」，下面一条进度。
 *
 * ## 为什么名字单独占一整行（这是上一版的问题）
 * 旧版把名字塞进 `BarRow` 的 `labelWidth = 120dp` 里，而一行里还有进度条和「Lv.N」，
 * 于是「星乃一歌 & 望月穗波」这种 10 个字的组名必然被截成「星乃一歌 & 望月…」——
 * 恰恰是**确认"这是谁和谁"最需要的那半个字**被截掉了。
 * 现在进度条挪到第二行，名字拿到除「Lv.N」之外的整行宽度，正常长度的组名不会再截。
 *
 * 名字前面两个**角色主题色圆点**是第二重保障：即使名字仍然被截，颜色也能立刻对上人
 * （颜色来自 `gameCharacterUnits.colorCode`，不是编的）。
 */
@Composable
private fun BondRowItem(
    bond: OwnedBond,
    pair: BondPair?,
    charsById: Map<Int, PjskCharacter>,
    characterColors: Map<Int, String>,
    nameLanguage: NameLanguage,
    maxRank: Int,
    onClick: () -> Unit,
) {
    val label = bondLabel(pair, charsById, nameLanguage) ?: "#${bond.bondsGroupId}"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pair != null) {
                BondDot(characterColors[pair.characterIdSmall])
                Spacer(Modifier.width(3.dp))
                BondDot(characterColors[pair.characterIdLarge])
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Lv.${bond.rank}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.height(3.dp))
        Bar(fraction = bond.rank.toFloat() / maxRank.coerceAtLeast(1))
    }
}

/** 角色主题色小圆点。取不到颜色时留一个中性灰点（不隐藏，保持两边的对齐）。 */
@Composable
private fun BondDot(hex: String?) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(parseHexColor(hex) ?: MaterialTheme.colorScheme.outline),
    )
}

/** 纯进度条（羁绊行第二行用；`BarRow` 那条是「标签 + 条 + 数值」的三段式）。 */
@Composable
private fun Bar(fraction: Float, color: Color = MaterialTheme.colorScheme.tertiary) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}

/**
 * 羁绊面板：[BondSheetState.All] 列全部，[BondSheetState.Detail] 看一组。
 *
 * 详情里能给出的都是**已有数据**：两个角色（头像 + 名字 + 角色等级）、羁绊等级、
 * 当前等级内的经验。**不显示"满级"或"升级还差多少"** —— 那需要各级门槛经验
 * （`levels.json`），现在没有内置，硬算就是编数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BondSheet(
    state: BondSheetState,
    bonds: List<OwnedBond>,
    bondPairs: Map<Int, BondPair>,
    charsById: Map<Int, PjskCharacter>,
    characterColors: Map<Int, String>,
    nameLanguage: NameLanguage,
    region: ServerRegion,
    onNavigate: (BondSheetState) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val maxRank = bonds.maxOfOrNull { it.rank }?.coerceAtLeast(1) ?: 1
        when (state) {
            BondSheetState.None -> Unit

            BondSheetState.All -> {
                SheetHeader(
                    title = "全部羁绊",
                    subtitle = "${bonds.size} 组 · 从高到低",
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    items(bonds, key = { it.bondsGroupId }) { bond ->
                        BondRowItem(
                            bond = bond,
                            pair = bondPairs[bond.bondsGroupId],
                            charsById = charsById,
                            characterColors = characterColors,
                            nameLanguage = nameLanguage,
                            maxRank = maxRank,
                            onClick = { onNavigate(BondSheetState.Detail(bond.bondsGroupId)) },
                        )
                    }
                }
            }

            is BondSheetState.Detail -> {
                val bond = bonds.firstOrNull { it.bondsGroupId == state.groupId }
                val pair = bondPairs[state.groupId]
                SheetHeader(
                    title = bondLabel(pair, charsById, nameLanguage) ?: "羁绊 #${state.groupId}",
                    subtitle = "羁绊等级 Lv.${bond?.rank ?: 0}",
                    onBack = { onNavigate(BondSheetState.All) },
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    if (pair == null) {
                        HintText("内置数据里没找到这一组的角色对，可能是数据表还没同步完整。")
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            BondCharacterCard(
                                characterId = pair.characterIdSmall,
                                charsById = charsById,
                                characterColors = characterColors,
                                nameLanguage = nameLanguage,
                                region = region,
                                modifier = Modifier.weight(1f),
                            )
                            BondCharacterCard(
                                characterId = pair.characterIdLarge,
                                charsById = charsById,
                                characterColors = characterColors,
                                nameLanguage = nameLanguage,
                                region = region,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    if (bond != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Lv.${bond.rank}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "当前等级经验 ${bond.exp}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Bar(fraction = bond.rank.toFloat() / maxRank.coerceAtLeast(1))
                        Spacer(Modifier.height(6.dp))
                        HintText(
                            "进度条按「这一组相对你最高那一组」的比例画；" +
                                "升级还差多少经验需要各级门槛表，暂未内置。",
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

/** 面板顶部一行：返回（可选）+ 标题 + 副标题。 */
@Composable
private fun SheetHeader(title: String, subtitle: String?, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回全部羁绊")
            }
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 详情里的一个角色：头像 + 名字 +（有数据时）角色等级。 */
@Composable
private fun BondCharacterCard(
    characterId: Int,
    charsById: Map<Int, PjskCharacter>,
    characterColors: Map<Int, String>,
    nameLanguage: NameLanguage,
    region: ServerRegion,
    modifier: Modifier = Modifier,
) {
    val color = parseHexColor(characterColors[characterId]) ?: MaterialTheme.colorScheme.primary
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = AssetUrls.characterAvatar(region, characterId),
                contentDescription = charsById[characterId]?.displayNameFor(nameLanguage),
                // 和「角色等级」那 26 格一样：这张图是 376×840 的竖版立绘，顶部对齐才看得到头
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = charsById[characterId]?.displayNameFor(nameLanguage) ?: "#$characterId",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = charsById[characterId]?.unitLabel.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 羁绊组名：「一歌 & 穗波」。拿不到就返回 null（调用方退回显示组 id）。
 *
 * 名字跟随 App 的「名称语言」设置，和卡牌页、剧情页一致。
 */
private fun bondLabel(
    pair: BondPair?,
    charsById: Map<Int, PjskCharacter>,
    nameLanguage: NameLanguage,
): String? {
    if (pair == null) return null
    val a = charsById[pair.characterIdSmall]?.displayNameFor(nameLanguage) ?: return null
    val b = charsById[pair.characterIdLarge]?.displayNameFor(nameLanguage) ?: return null
    return "$a & $b"
}

/** 一个角色格：**圆角方形** + 头像 + 等级，底色用该角色自己的官方主题色。 */
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
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = AssetUrls.characterAvatar(region, owned.characterId),
                contentDescription = character?.displayNameFor(nameLanguage),
                contentScale = ContentScale.Crop,
                // ⚠️ 这张图实测是 376×840 的**竖版立绘**，居中裁只会截到躯干。
                // 顶部对齐后 44dp 的方框里看到的是头部，这才是"头像"。
                alignment = Alignment.TopCenter,
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




