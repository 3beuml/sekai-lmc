package com.pjsk.toolbox.ui.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.AppContainer
import com.pjsk.toolbox.data.card.Card
import com.pjsk.toolbox.data.card.PjskCharacter
import com.pjsk.toolbox.data.card.displayNameFor
import com.pjsk.toolbox.data.db.AppDatabase
import com.pjsk.toolbox.data.db.MasterRowEntity
import com.pjsk.toolbox.data.deck.DeckEngine
import com.pjsk.toolbox.data.deck.DeckLiveType
import com.pjsk.toolbox.data.deck.DeckRecommendResult
import com.pjsk.toolbox.data.deck.DeckTarget
import com.pjsk.toolbox.data.deck.RecommendedCard
import com.pjsk.toolbox.data.deck.RecommendedDeck
import com.pjsk.toolbox.data.deck.buildMasterDataPayload
import com.pjsk.toolbox.data.deck.buildRecommendParams
import com.pjsk.toolbox.data.deck.buildUserJson
import com.pjsk.toolbox.data.deck.parseDeckRecommendResult
import com.pjsk.toolbox.data.deck.restoreAreaItemLevelsJson
import com.pjsk.toolbox.data.deck.restoreAreaItemsJson
import com.pjsk.toolbox.data.deck.restoreCardsJson
import com.pjsk.toolbox.data.deck.restoreHonorsJson
import com.pjsk.toolbox.data.deck.restoreMasterLessonsJson
import com.pjsk.toolbox.data.deck.restoreMusicMetasJson
import com.pjsk.toolbox.data.profile.AreaItemLevelRow
import com.pjsk.toolbox.data.profile.OwnedCard
import com.pjsk.toolbox.data.profile.PlayerSnapshot
import com.pjsk.toolbox.data.profile.computeDeckPower
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.ui.common.PlayerDataGate
import com.pjsk.toolbox.ui.common.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「组卡推荐」结果页。
 *
 * ## 这一屏在做什么
 *
 * 把账号快照（我有哪些卡、什么等级、读了哪些剧情、区域道具/称号/角色等级）
 * 还原成组卡引擎（allium-deck WASM）要的 masterdata，跑一次**纯综合力最优**的搜索，
 * 再把 5 套结果摊开：排名 / 综合力 / 与当前编成差多少 / 5 张卡。
 *
 * ## 三个必须摆在界面上的事实
 *
 * 1. **基于快照，不是实时** —— 引擎用的是本地上次同步下来的账号数据。
 *    用户换了卡组、升了级，这里不会跟着变，所以时间要写在最上面。
 * 2. **引擎报 `complete` 才是精确解** —— 超时返回的是"合法但未证明最优"的当前最优，
 *    不能让人以为"全世界就这 5 套"。判定来自 [DeckRecommendResult.isExact]。
 * 3. **与当前编成的差值混用了两个来源** —— 推荐卡组的综合力是引擎算的，
 *    当前编成的综合力用 [computeDeckPower]（和「我的」页、编成页同一个纯函数）。
 *    两者都是同一套官方公式的移植，但**不是同一个实现**，末尾几位可能差几点。
 *    这里选 [computeDeckPower] 当基准，是为了让"当前编成"这个数与用户在别处看到的一致。
 *
 * ## 模式骨架：「综合力 × 单人」以外的格子是故意置灰的
 *
 * 活动加成需要活动 id，分数/多人需要曲目系数（`music_metas`，还没进快照）。
 * 现在把它们做成**看得见但点不动**，比藏起来好：用户能知道"这东西在计划里"，
 * 而不是以为我们只打算做一个模式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckRecommendScreen(
    region: ServerRegion,
    onBack: () -> Unit,
    onOpenBind: () -> Unit,
    onOpenMyPlayer: () -> Unit,
    onOpenCard: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PjskApp).container
    val settings = remember { container.appSettings }
    val playerRepository = remember { container.playerRepository }
    val masterRef = remember { container.masterRefRepository }
    val cardRepository = remember { container.cardRepository }
    val engine = remember { DeckEngine(context.applicationContext) }

    val account by settings.playerAccount.collectAsState()
    val cards by cardRepository.cards.collectAsState()
    val characters by cardRepository.characters.collectAsState(initial = emptyList())
    val episodesByCard by remember { cardRepository.cardEpisodes }.collectAsState(initial = emptyMap())
    val areaItemLevels by remember { masterRef.areaItemLevels }.collectAsState(initial = emptyMap())
    val honorBonuses by remember { masterRef.honorBonuses }.collectAsState(initial = emptyMap())
    val characterRankRates by remember { masterRef.characterRankRates }.collectAsState(initial = emptyMap())
    val nameLanguage by settings.nameLanguage.collectAsState()

    // ── 账号快照（只读本地缓存，不额外请求 Haruki） ──
    var load by remember { mutableStateOf<RecLoad>(RecLoad.Loading) }
    var snapshot by remember { mutableStateOf<PlayerSnapshot?>(null) }
    LaunchedEffect(account) {
        val acc = account
        if (acc == null) {
            load = RecLoad.Unbound
            snapshot = null
            return@LaunchedEffect
        }
        val snap = withContext(Dispatchers.IO) { playerRepository.loadCached(acc) }
        snapshot = snap
        load = if (snap == null) RecLoad.NoCache else RecLoad.Ready(snap)
    }

    // ── 引擎状态机 ──
    var stage by remember { mutableStateOf(RecStage.Booting) }
    var engineReady by remember { mutableStateOf(false) }
    var masterLoaded by remember { mutableStateOf(false) }
    var masterLoading by remember { mutableStateOf(false) }
    /** 让「重新载入 masterdata」能重跑装载协程（LaunchedEffect 只认 key 变化）。 */
    var masterToken by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<DeckRecommendResult?>(null) }
    // 原始输出用 rememberSaveable 存：旋转屏幕 / 系统回收后 Activity 会整块重建，
    // 而引擎（WebView + wasm）**带不过去**，只能重新装载 masterdata（几秒到十几秒）。
    // 把上次的输出存进 savedInstanceState，重建后能立刻把结果画出来，后台再重算一次。
    var rawOutput by rememberSaveable { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var searchMs by remember { mutableStateOf(0) }
    var diagOpen by remember { mutableStateOf(false) }
    var expandedDecks by remember { mutableStateOf(setOf<Int>()) }
    /** 装载链跑了几次（诊断用：正常应该正好 1，重建后 +1）。 */
    var chainRuns by rememberSaveable { mutableStateOf(0) }

    // ── 模式与输入（三个目标 × 两种玩法）──
    //
    // 选择项全部用 rememberSaveable 存：转屏/重建之后，**必须**让界面上的选择
    // 与"已经显示着的那份结果"对得上。用 remember 的话，重建后选择回到默认的
    // 「综合力」，而屏幕下方还挂着上次「分数」的结果 —— 那是最容易误导人的状态。
    var targetKey by rememberSaveable { mutableStateOf(DeckTarget.POWER.key) }
    var liveKey by rememberSaveable { mutableStateOf(DeckLiveType.SOLO.key) }
    var eventId by rememberSaveable { mutableStateOf<Int?>(null) }
    var eventLabel by rememberSaveable { mutableStateOf("") }
    var musicId by rememberSaveable { mutableStateOf<Int?>(null) }
    var musicLabel by rememberSaveable { mutableStateOf("") }
    var musicDiff by rememberSaveable { mutableStateOf("expert") }
    var teammatePower by rememberSaveable { mutableStateOf("") }
    var teammateScoreUp by rememberSaveable { mutableStateOf("") }
    /** 「重新推荐」/「重新载入」用来强制重跑搜索（LaunchedEffect 只认 key 变化）。 */
    var searchToken by remember { mutableStateOf(0) }

    val target = DeckTarget.entries.firstOrNull { it.key == targetKey } ?: DeckTarget.POWER
    val liveType = DeckLiveType.entries.firstOrNull { it.key == liveKey } ?: DeckLiveType.SOLO
    // 输入齐不齐决定「能不能搜」：缺活动的加成、缺曲目的分数都不该去问引擎 ——
    // 那会让引擎按兜底表算出一个看起来正常、实际不对的数字。
    val inputsReady = when (target) {
        DeckTarget.POWER -> true
        DeckTarget.BONUS -> eventId != null
        DeckTarget.SCORE -> musicId != null
    }

    fun runRecommend(snap: PlayerSnapshot?) {
        if (snap == null) {
            stage = RecStage.Error
            errorText = "没有账号快照，先绑定并读取一次。"
            return
        }
        if (!inputsReady) {
            // 缺输入就不要问引擎：引擎会走兜底表，给出"看起来正常"的数字
            stage = RecStage.Idle
            return
        }
        stage = RecStage.Searching
        errorText = ""
        // limit 就是「返回几套」：5 套是用户能一眼扫完的量（再多要滚很久才看得出差别）
        engine.recommend(
            buildUserJson(snap),
            buildRecommendParams(
                target = target,
                liveType = liveType,
                limit = RESULT_LIMIT,
                eventId = eventId,
                musicId = musicId,
                musicDiff = musicDiff,
                multiTeammatePower = teammatePower.trim().toIntOrNull(),
                multiTeammateScoreUp = teammateScoreUp.trim().toIntOrNull(),
            ),
        )
    }

    LaunchedEffect(engine) {
        for ((kind, payload) in engine.events) {
            when (kind) {
                "ready" -> {
                    engineReady = true
                    if (stage == RecStage.Booting) stage = RecStage.Ready
                }
                "loaded" -> {
                    masterLoaded = true
                    masterLoading = false
                    // 装完不在这里搜 —— 搜索统一由下面那个 LaunchedEffect 触发，
                    // 否则「装完搜一次」+「模式变化再搜一次」会连成两次搜索。
                }
                "result" -> {
                    rawOutput = payload
                    dumpToLog("RESULT", payload)
                    val parsed = runCatching { parseDeckRecommendResult(payload) }
                    parsed.fold(
                        onSuccess = {
                            result = it
                            stage = RecStage.Done
                        },
                        onFailure = {
                            result = null
                            stage = RecStage.Error
                            errorText = "引擎返回解析失败：${it.message}（原文见下方诊断）"
                        },
                    )
                }
                "elapsed" -> payload.toIntOrNull()?.let { searchMs = it }
                "error" -> {
                    masterLoading = false
                    stage = RecStage.Error
                    errorText = payload
                    dumpToLog("ERROR", payload)
                }
            }
        }
    }

    // 唯一的触发点：masterdata 装好了、输入也齐了，就（重新）搜一次。
    // 换目标 / 换玩法 / 换活动 / 换曲目 / 点「重新推荐」都会走到这里。
    LaunchedEffect(
        masterLoaded, searchToken,
        targetKey, liveKey, eventId, musicId, musicDiff, teammatePower, teammateScoreUp,
    ) {
        if (!masterLoaded) return@LaunchedEffect
        if (!inputsReady) {
            // 输入不齐时把上一次的结果清掉：留着旧模式的结果会让人以为"这就是当前模式的答案"
            result = null
            errorText = ""
            stage = RecStage.Idle
            return@LaunchedEffect
        }
        runRecommend(snapshot)
    }

    // 条件齐了就自动装 masterdata（约 10 MB 的组装 + 引擎解析，几秒）
    LaunchedEffect(load, engineReady, masterToken) {
        val ready = load as? RecLoad.Ready ?: return@LaunchedEffect
        if (!engineReady || masterLoaded || masterLoading) return@LaunchedEffect
        masterLoading = true
        // 已经有结果（从 savedInstanceState 恢复的）就不要把界面切回进度条
        if (result == null) stage = RecStage.BuildingMaster
        chainRuns += 1
        android.util.Log.i("DeckEngineDump", "CHAIN master-load run#$chainRuns")
        val owned = ready.snapshot.cards.map { it.cardId }.toSet()
        val files = withContext(Dispatchers.IO) { writeMasterData(container, owned) }
        engine.loadMasterDataFile(files.master, files.musicMetas)
    }

    // 重建后先把上次的结果画出来（引擎还在后台重装，装完会覆盖成新结果）
    LaunchedEffect(Unit) {
        if (result == null && rawOutput.isNotBlank()) {
            runCatching { parseDeckRecommendResult(rawOutput) }.getOrNull()?.let {
                result = it
                stage = RecStage.Done
            }
        }
    }

    DisposableEffect(engine) { onDispose { engine.destroy() } }

    when (val state = load) {
        RecLoad.Unbound -> PlayerDataGate(
            title = "组卡推荐",
            reason = "还没有绑定账号 —— 组卡要知道「我有哪些卡」，先去绑定。",
            actionLabel = "去绑定",
            onBack = onBack,
            onAction = onOpenBind,
        )

        RecLoad.NoCache -> PlayerDataGate(
            title = "组卡推荐",
            reason = "本地还没有这份账号的数据，去「我的」读取一次就有了。",
            actionLabel = "去「我的」",
            onBack = onBack,
            onAction = onOpenMyPlayer,
        )

        RecLoad.Loading -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("组卡推荐") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }

        is RecLoad.Ready -> RecommendContent(
            region = region,
            snapshot = state.snapshot,
            cards = remember(cards) { cards.associateBy { it.id } },
            characters = remember(characters) { characters.associateBy { it.id } },
            episodesByCard = episodesByCard,
            areaItemLevels = areaItemLevels,
            honorBonuses = honorBonuses,
            characterRankRates = characterRankRates,
            nameLanguage = nameLanguage,
            stage = stage,
            result = result,
            rawOutput = rawOutput,
            errorText = errorText,
            searchMs = searchMs,
            diagOpen = diagOpen,
            expandedDecks = expandedDecks,
            chainRuns = chainRuns,
            // masterdata 没装好之前按「重新推荐」只会拿到引擎的「未载入」错误，
            // 而那个错误会盖住已经显示的结果 —— 所以按钮跟着装载状态走，不跟结果走。
            canRerun = masterLoaded && !masterLoading && inputsReady,
            target = target,
            liveType = liveType,
            eventId = eventId,
            eventLabel = eventLabel,
            musicId = musicId,
            musicLabel = musicLabel,
            musicDiff = musicDiff,
            teammatePower = teammatePower,
            teammateScoreUp = teammateScoreUp,
            inputsReady = inputsReady,
            onPickTarget = { targetKey = it.key },
            onPickLiveType = { liveKey = it.key },
            onPickEvent = { id, label ->
                eventId = id
                eventLabel = label
            },
            onPickMusic = { id, label ->
                musicId = id
                musicLabel = label
            },
            onPickMusicDiff = { musicDiff = it },
            onTeammatePower = { teammatePower = it },
            onTeammateScoreUp = { teammateScoreUp = it },
            database = container.database,
            onBack = onBack,
            onRetryAll = {
                // 整条链重来：丢掉结果 → 重新装 masterdata（装完会自动再搜一次）
                result = null
                errorText = ""
                masterLoaded = false
                masterLoading = false
                masterToken += 1
            },
            onRerunSearch = { searchToken += 1 },
            onToggleDiag = { diagOpen = !diagOpen },
            onToggleDeck = { index ->
                expandedDecks = if (index in expandedDecks) expandedDecks - index else expandedDecks + index
            },
            onOpenCard = onOpenCard,
            engineHost = { engine.attach() },
        )
    }
}

/** 快照读取的三种结局（比 `null` 多一个信号：到底是没绑定，还是绑了但没缓存）。 */
private sealed interface RecLoad {
    data object Loading : RecLoad
    data object Unbound : RecLoad
    data object NoCache : RecLoad
    data class Ready(val snapshot: PlayerSnapshot) : RecLoad
}

/** 引擎这条链走到哪一步了（界面上就靠它决定显示哪句提示、要不要转圈）。 */
private enum class RecStage { Booting, Ready, BuildingMaster, Searching, Done, Error, Idle }

/** 一次要几套结果（引擎上限 30；5 套是"一眼扫完"的量）。 */
private const val RESULT_LIMIT = 5

/** 引擎认的六个难度名，顺序与游戏里一致。 */
private val MUSIC_DIFFICULTIES = listOf("easy", "normal", "hard", "expert", "master", "append")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendContent(
    region: ServerRegion,
    snapshot: PlayerSnapshot,
    cards: Map<Int, Card>,
    characters: Map<Int, PjskCharacter>,
    episodesByCard: Map<Int, List<com.pjsk.toolbox.data.card.CardEpisode>>,
    areaItemLevels: Map<Long, AreaItemLevelRow>,
    honorBonuses: Map<Int, Map<Int, Int>>,
    characterRankRates: Map<Long, List<Double>>,
    nameLanguage: NameLanguage,
    stage: RecStage,
    result: DeckRecommendResult?,
    rawOutput: String,
    errorText: String,
    searchMs: Int,
    diagOpen: Boolean,
    expandedDecks: Set<Int>,
    chainRuns: Int,
    canRerun: Boolean,
    target: DeckTarget,
    liveType: DeckLiveType,
    eventId: Int?,
    eventLabel: String,
    musicId: Int?,
    musicLabel: String,
    musicDiff: String,
    teammatePower: String,
    teammateScoreUp: String,
    inputsReady: Boolean,
    onPickTarget: (DeckTarget) -> Unit,
    onPickLiveType: (DeckLiveType) -> Unit,
    onPickEvent: (Int, String) -> Unit,
    onPickMusic: (Int, String) -> Unit,
    onPickMusicDiff: (String) -> Unit,
    onTeammatePower: (String) -> Unit,
    onTeammateScoreUp: (String) -> Unit,
    database: AppDatabase,
    onBack: () -> Unit,
    onRetryAll: () -> Unit,
    onRerunSearch: () -> Unit,
    onToggleDiag: () -> Unit,
    onToggleDeck: (Int) -> Unit,
    onOpenCard: (Int) -> Unit,
    engineHost: () -> android.webkit.WebView,
) {
    val ownedById = remember(snapshot.cards) { snapshot.cards.associateBy { it.cardId } }
    var showEventPicker by remember { mutableStateOf(false) }
    var showMusicPicker by remember { mutableStateOf(false) }
    val unitOf = remember(characters) {
        { card: Card ->
            val base = characters[card.characterId]?.unitKey
            val support = card.supportUnit?.takeIf { it.isNotBlank() && it != "none" }
            buildList {
                support?.let { add(it) }
                base?.let { add(it) }
            }.distinct()
        }
    }

    // 当前编成的综合力：**与「我的」页、编成页同一个纯函数**，这样"较当前 +N"
    // 的基准就是用户已经看过的那个数，不会出现"两处对不上"。
    val currentDeckPower = remember(snapshot, cards, episodesByCard, areaItemLevels, honorBonuses, characterRankRates) {
        val current = snapshot.decks.firstOrNull { it.isCurrent } ?: snapshot.decks.firstOrNull()
        val ownedCards = current?.cardIds?.mapNotNull { ownedById[it] }.orEmpty()
        if (ownedCards.isEmpty()) {
            null
        } else {
            computeDeckPower(
                deckCards = ownedCards,
                cards = cards,
                episodesByCard = episodesByCard,
                userCharacterRanks = snapshot.characters.associate { it.characterId to it.characterRank },
                characterRankRates = characterRankRates,
                userAreaItems = snapshot.areaItems,
                areaItemLevels = areaItemLevels,
                honorBonuses = honorBonuses,
                userHonors = snapshot.honors,
                cardUnits = unitOf,
            )?.total
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("组卡推荐", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${target.label} · ${liveType.label} · 基于快照，不是实时",
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
                actions = {
                    TextButton(onClick = onRerunSearch, enabled = canRerun) {
                        Text("重新推荐")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            // 不可见的 WebView 宿主：引擎的 JS/wasm 就活在这 1dp 里。
            //
            // 为什么**不**把它塞进 LazyColumn 的 item：列表项滚出屏幕会被回收，
            // 一旦被移除出视图树，wasm 实例与 `window.deckBoot` 的状态就跟着悬空 ——
            // 表现为"滚到下面再点重新推荐就没反应了"。放在列表**外面**（Box 里、
            // 列表之下）它就永远在树上，且 1dp 不会挡住任何点击。
            AndroidView(
                modifier = Modifier.size(1.dp),
                factory = { engineHost() },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            item(key = "note") {
                SnapshotNote(snapshot)
            }

            item(key = "modes") {
                ModeCard(
                    target = target,
                    liveType = liveType,
                    eventLabel = eventLabel,
                    musicLabel = musicLabel,
                    musicDiff = musicDiff,
                    teammatePower = teammatePower,
                    teammateScoreUp = teammateScoreUp,
                    onPickTarget = onPickTarget,
                    onPickLiveType = onPickLiveType,
                    onOpenEventPicker = { showEventPicker = true },
                    onOpenMusicPicker = { showMusicPicker = true },
                    onPickMusicDiff = onPickMusicDiff,
                    onTeammatePower = onTeammatePower,
                    onTeammateScoreUp = onTeammateScoreUp,
                )
            }

            item(key = "stage") {
                StageRow(stage, result, searchMs, errorText, inputsReady, target, onRetryAll)
            }

            result?.decks?.forEachIndexed { index, deck ->
                // key 用**下标**而不是 rank：rank 由引擎给，理论上不该重复，
                // 但一旦重复 LazyColumn 会直接崩（duplicate key），而崩的是整页。
                item(key = "deck-$index") {
                    RecommendDeckCard(
                        deck = deck,
                        target = target,
                        isExact = result.isExact,
                        timedOut = result.timedOut,
                        currentPower = currentDeckPower,
                        cards = cards,
                        characters = characters,
                        ownedById = ownedById,
                        nameLanguage = nameLanguage,
                        region = region,
                        expanded = index in expandedDecks,
                        poolSize = result.poolSize,
                        searchMs = if (result.searchMs > 0) result.searchMs else searchMs,
                        onToggleExpand = { onToggleDeck(index) },
                        onOpenCard = onOpenCard,
                    )
                }
            }

            if (result != null && result.decks.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "引擎没有返回任何卡组 —— 通常是持有卡数量不足（要 5 张同一稀有度池里的卡），" +
                            "或者快照里的卡与内置表对不上。看下面的诊断。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "diag") {
                Diagnostics(
                    open = diagOpen,
                    rawOutput = rawOutput,
                    stage = stage,
                    result = result,
                    searchMs = searchMs,
                    chainRuns = chainRuns,
                    onToggle = onToggleDiag,
                    onReloadMaster = onRetryAll,
                )
            }
            }
        }
    }

    if (showEventPicker) {
        EventPickerSheet(
            database = database,
            selectedId = eventId,
            onDismiss = { showEventPicker = false },
            onPick = { id, label ->
                showEventPicker = false
                onPickEvent(id, label)
            },
        )
    }
    if (showMusicPicker) {
        MusicPickerSheet(
            database = database,
            selectedId = musicId,
            onDismiss = { showMusicPicker = false },
            onPick = { id, label ->
                showMusicPicker = false
                onPickMusic(id, label)
            },
        )
    }
}

/**
 * 活动选择（底部弹层）。
 *
 * 数据来自内置的 `events.json`（220+ 行）。**倒序**排（最新的在最上面）：
 * 组卡几乎总是为了"现在这期活动"，让用户滚到底找新活动的设计没有意义。
 *
 * 不给搜索框：活动名是中/日文，键盘输入体验差；220 行滚一下比打字快。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventPickerSheet(
    database: AppDatabase,
    selectedId: Int?,
    onDismiss: () -> Unit,
    onPick: (Int, String) -> Unit,
) {
    val rows by produceState(initialValue = emptyList<MasterRowEntity>()) {
        value = withContext(Dispatchers.IO) {
            database.masterDao().observeAll("events.json").first()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            "选择活动",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            "加成率按活动的加成角色 / 属性算；列表按新到旧。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            items(rows.sortedByDescending { it.id }, key = { it.id }) { row ->
                val label = row.displayName()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(row.id, label) }
                        .background(
                            if (row.id == selectedId) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#${row.id}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 曲目选择（底部弹层，带搜索）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicPickerSheet(
    database: AppDatabase,
    selectedId: Int?,
    onDismiss: () -> Unit,
    onPick: (Int, String) -> Unit,
) {
    val rows by produceState(initialValue = emptyList<MasterRowEntity>()) {
        value = withContext(Dispatchers.IO) {
            database.masterDao().observeAll("musics.json").first()
        }
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(rows, query) {
        val sorted = rows.sortedByDescending { it.id }
        if (query.isBlank()) {
            sorted
        } else {
            val q = query.trim()
            sorted.filter { it.displayName().contains(q, ignoreCase = true) || it.id.toString() == q }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("选择曲目", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索歌名或输入曲目 id") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "得分取决于这首歌的谱面系数；难度在上一屏选。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            items(filtered, key = { it.id }) { row ->
                val label = row.displayName()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(row.id, label) }
                        .background(
                            if (row.id == selectedId) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#${row.id}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 顶部那句「基于快照，不是实时」—— 连同快照的时间一起说清。 */
@Composable
private fun SnapshotNote(snapshot: PlayerSnapshot) {    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "基于快照，不是实时",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "数据时间 ${formatSnapshotTime(snapshot.uploadTime)} · 共 ${snapshot.cards.size} 张持有卡。" +
                    "换卡组/升级后要重新读取才会变。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 模式与输入。
 *
 * 三个目标各自的**必需输入**不同，所以这一块会随选择变形：
 *  - 综合力：什么都不用选（完全离线）。
 *  - 活动加成：必须选一个活动 —— 加成率是按活动的加成角色/属性算的。
 *  - 分数：必须选曲目与难度；再给活动 id 才会顺带算活动 PT。
 *
 * 为什么不把「缺输入」做成可以点下去再说：引擎在没有上下文时会用兜底表，
 * 给出一个数值合理但语义不对的结果，而界面上看不出区别。
 */
@Composable
private fun ModeCard(
    target: DeckTarget,
    liveType: DeckLiveType,
    eventLabel: String,
    musicLabel: String,
    musicDiff: String,
    teammatePower: String,
    teammateScoreUp: String,
    onPickTarget: (DeckTarget) -> Unit,
    onPickLiveType: (DeckLiveType) -> Unit,
    onOpenEventPicker: () -> Unit,
    onOpenMusicPicker: () -> Unit,
    onPickMusicDiff: (String) -> Unit,
    onTeammatePower: (String) -> Unit,
    onTeammateScoreUp: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目标", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
                DeckTarget.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == target,
                        onClick = { onPickTarget(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("玩法", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
                DeckLiveType.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == liveType,
                        onClick = { onPickLiveType(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            when (target) {
                DeckTarget.POWER -> Text(
                    "纯综合力最优：不需要活动或曲目，完全离线。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                DeckTarget.BONUS, DeckTarget.SCORE -> {
                    // 活动：bonus 必需；score 可选（选了才算活动 PT）
                    PickerRow(
                        label = "活动",
                        value = eventLabel.ifBlank { if (target == DeckTarget.BONUS) "必选" else "不选（只算得分）" },
                        emphasised = eventLabel.isBlank() && target == DeckTarget.BONUS,
                        onClick = onOpenEventPicker,
                    )
                }
            }

            if (target == DeckTarget.SCORE) {
                PickerRow(
                    label = "曲目",
                    value = musicLabel.ifBlank { "必选" },
                    emphasised = musicLabel.isBlank(),
                    onClick = onOpenMusicPicker,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("难度", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
                    MUSIC_DIFFICULTIES.forEach { diff ->
                        FilterChip(
                            selected = diff == musicDiff,
                            onClick = { onPickMusicDiff(diff) },
                            label = { Text(diff) },
                        )
                    }
                }
            }

            if (target == DeckTarget.SCORE && liveType == DeckLiveType.MULTI) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("队友", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
                    SmallNumberField(
                        value = teammatePower,
                        onValueChange = onTeammatePower,
                        label = "综合力",
                        modifier = Modifier.weight(1f),
                    )
                    SmallNumberField(
                        value = teammateScoreUp,
                        onValueChange = onTeammateScoreUp,
                        label = "技能%",
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "多人得分含 4 名队友的贡献。留空就是按 0 算 —— 绝对值偏低，但同一份缺口" +
                        "对所有候选卡组一样，所以排名仍然可比。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 一行「标签 + 当前值 + 可点选」。 */
@Composable
private fun PickerRow(
    label: String,
    value: String,
    emphasised: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (emphasised) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            "选择",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 小号数字输入（多人模式的队友参数）。只收数字，空串表示"不传"。 */
@Composable
private fun SmallNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        // 只留数字：引擎只认整数，带逗号/小数会被静默截断成 0
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(9)) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** 进度 / 结果摘要 / 待选输入 / 报错。 */
@Composable
private fun StageRow(
    stage: RecStage,
    result: DeckRecommendResult?,
    searchMs: Int,
    errorText: String,
    inputsReady: Boolean,
    target: DeckTarget,
    onRetryAll: () -> Unit,
) {
    val busy = stage == RecStage.Booting || stage == RecStage.BuildingMaster || stage == RecStage.Searching
    if (busy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = when (stage) {
                    RecStage.Booting -> "引擎加载中（要解 1.86 MB wasm）…"
                    RecStage.BuildingMaster -> "正在把快照还原成引擎要的 masterdata（第一次约几秒）…"
                    else -> "正在搜索最优卡组…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // 输入没齐：明说还差什么，而不是给一个空白页让人猜
    if (!inputsReady) {
        Text(
            text = when (target) {
                DeckTarget.BONUS -> "先在上面选一个活动 —— 加成率是按活动的加成角色/属性算的。"
                DeckTarget.SCORE -> "先在上面选一首曲目 —— 得分要用这首歌的谱面系数。"
                DeckTarget.POWER -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    when (stage) {
        RecStage.Error -> {
            Column {
                Text(
                    "没能算出结果",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                Text(errorText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onRetryAll) { Text("重试") }
            }
        }

        RecStage.Done -> {
            val decks = result?.decks.orEmpty()
            Text(
                text = buildString {
                    append("找到 ${decks.size} 套")
                    if (result != null) {
                        append(" · 候选池 ${result.poolSize} 张")
                        val ms = if (result.searchMs > 0) result.searchMs else searchMs
                        if (ms > 0) append(" · 搜索 ${ms} ms")
                    }
                    if (result?.isExact == true) {
                        append(" · 精确 Top-K")
                    } else if (result != null) {
                        append(" · 超时，结果是当前最优但不保证最优")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> Unit
    }
}

/** 一套推荐卡组：排名 / 综合力 / 与当前编成的差 / 5 张卡 / 展开看加成明细。 */
@Composable
private fun RecommendDeckCard(
    deck: RecommendedDeck,
    target: DeckTarget,
    isExact: Boolean,
    timedOut: Boolean,
    currentPower: Int?,
    cards: Map<Int, Card>,
    characters: Map<Int, PjskCharacter>,
    ownedById: Map<Int, OwnedCard>,
    nameLanguage: NameLanguage,
    region: ServerRegion,
    expanded: Boolean,
    poolSize: Int,
    searchMs: Int,
    onToggleExpand: () -> Unit,
    onOpenCard: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // 第 1 名给一点强调：它才是"要换的那套"
            containerColor = if (deck.rank == 1) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankBadge(deck.rank)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // 主指标跟着目标变：综合力模式看综合力，加成模式看加成率，
                    // 分数模式看得分。综合力始终作为第二行露出来 —— 它是所有模式的共同底座。
                    Text(
                        text = primaryMetric(deck, target),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (target == DeckTarget.POWER) {
                        DiffFromCurrent(deck.totalPower, currentPower)
                    } else {
                        Text(
                            text = secondaryMetrics(deck, target),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onToggleExpand) {
                    Text(if (expanded) "收起" else "加成明细")
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                deck.cards.forEach { item ->
                    val card = cards[item.cardId]
                    RecommendedCardTile(
                        item = item,
                        card = card,
                        character = card?.let { characters[it.characterId] },
                        // 引擎自己的 trained 才是它算这套卡组时用的状态；
                        // 只有它没给（老输出）时才退回本地持有记录。
                        trained = item.trained || (ownedById[item.cardId]?.trained ?: card?.trainedOnly ?: false),
                        nameLanguage = nameLanguage,
                        region = region,
                        onClick = { onOpenCard(item.cardId) },
                    )
                }
                // 不足 5 张时占位，避免已填的格子被拉开（引擎正常总是给 5 张）
                repeat((5 - deck.cards.size).coerceAtLeast(0)) { Spacer(Modifier.width(52.dp)) }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                DeckBreakdown(
                    deck = deck,
                    cards = cards,
                    characters = characters,
                    nameLanguage = nameLanguage,
                    isExact = isExact,
                    timedOut = timedOut,
                    poolSize = poolSize,
                    searchMs = searchMs,
                )
            }
        }
    }
}

/**
 * 一套卡组的**主指标**文案（跟着目标变）。
 *
 * 为什么按目标换：综合力模式盯着综合力，加成模式盯加成率，分数模式盯着单局得分。
 * 三个数永远都在引擎输出里，但同一屏并列三个大数字只会让人不知道该看哪个。
 *
 * 引擎没给对应字段时显示「—」而不是 0：`target=power` 就不算 `event_bonus_total`，
 * 显示 0% 会被当成"这套没有加成"。
 */
private fun primaryMetric(deck: RecommendedDeck, target: DeckTarget): String = when (target) {
    DeckTarget.POWER -> "综合力 %,d".format(deck.totalPower)
    DeckTarget.BONUS -> "加成 ${formatBonus(deck.eventBonusTotal)}"
    DeckTarget.SCORE -> if (deck.liveScore >= 1.0) {
        "得分 %,d".format(deck.liveScore.toLong())
    } else {
        "得分 —"
    }
}

/** 主指标之外的次要指标（同一行，小字）。 */
private fun secondaryMetrics(deck: RecommendedDeck, target: DeckTarget): String = buildList {
    add("综合力 %,d".format(deck.totalPower))
    if (target != DeckTarget.BONUS && deck.eventBonusTotal != null) {
        add("加成 " + formatBonus(deck.eventBonusTotal))
    }
    if (target != DeckTarget.SCORE && deck.liveScore >= 1.0) {
        add("得分 %,d".format(deck.liveScore.toLong()))
    }
    deck.eventPoint?.let { add("PT %,d".format(it)) }
}.joinToString(" · ")

/**
 * 加成率文案。
 *
 * 引擎的加成按 **0.1%** 计（文档里明确写过 4.8% / 5.1% 这种半档是真实存在的），
 * 所以整档才去掉小数，半档要显示一位 —— 截断成整数会让"230.5%"显示成"230%"。
 * 引擎没给时显示「—」，不显示 0%（0 会被读成"这套完全没有加成"）。
 */
private fun formatBonus(value: Double?): String {
    if (value == null) return "—"
    return if (value % 1.0 == 0.0) "%d%%".format(value.toInt()) else "%.1f%%".format(value)
}

@Composable
private fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (rank == 1) 0.20f else 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 「与当前编成差多少」。
 *
 * [currentPower] 为 null 时（快照里没有编成 / 内置表缺卡）**明说算不出来**，
 * 不要退回显示「+0」—— 那看起来像"推荐的和现在一样"。
 */
@Composable
private fun DiffFromCurrent(deckPower: Int, currentPower: Int?) {
    if (currentPower == null) {
        Text(
            "当前编成算不出综合力，无法比较",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val diff = deckPower - currentPower
    val text = when {
        diff > 0 -> "较当前编成 +%,d".format(diff)
        diff < 0 -> "较当前编成 %,d".format(diff)
        else -> "与当前编成持平"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = when {
            diff > 0 -> MaterialTheme.colorScheme.primary
            diff < 0 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/** 一格卡：52dp 卡图标 + 角色名，点进卡详情。 */
@Composable
private fun RecommendedCardTile(
    item: RecommendedCard,
    card: Card?,
    character: PjskCharacter?,
    trained: Boolean,
    nameLanguage: NameLanguage,
    region: ServerRegion,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(52.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (card != null) {
                AsyncImage(
                    model = AssetUrls.cardIcon(region, card.assetbundleName, trained),
                    contentDescription = card.displayNameFor(nameLanguage),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = character?.displayNameFor(nameLanguage) ?: "#${item.cardId}",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 加成明细：引擎对这套卡组**实际采用的逐卡数据**。
 *
 * 为什么值得摊开：推荐结果里"为什么是这几张"用户是看不出来的 ——
 * 引擎算的是（基础三维 + 突破 × 加成率 + 特训 + 剧情）× 各分项，
 * 要验证它没算错，唯一的办法是把它用的等级/突破/技能摆出来跟游戏里对一遍。
 */
@Composable
private fun DeckBreakdown(
    deck: RecommendedDeck,
    cards: Map<Int, Card>,
    characters: Map<Int, PjskCharacter>,
    nameLanguage: NameLanguage,
    isExact: Boolean,
    timedOut: Boolean,
    poolSize: Int,
    searchMs: Int,
) {
    Column {
        Text(
            "引擎逐卡数据（等级 / 突破 / 该卡在引擎里的综合力）",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        deck.cards.forEach { item ->
            val card = cards[item.cardId]
            val extras = buildList {
                if (item.skillLevel > 0) {
                    add("技能 Lv.${item.skillLevel} +${formatPercent(item.skillScoreUp)}")
                }
                val episodes = buildString {
                    append("剧情 ")
                    append(if (item.episode1Read) "①" else "①✕")
                    append(if (item.episode2Read) "②" else "②✕")
                }
                add(episodes)
                if (item.hasCanvasBonus) add("画布 +%,d".format(item.canvasPower))
                item.eventBonus?.let { add("活动加成 " + formatBonus(it)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card?.displayNameFor(nameLanguage)
                        ?: "${characters[item.characterId]?.displayNameFor(nameLanguage) ?: "#${item.cardId}"} 的卡",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Lv.${item.level}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "MR${item.masterRank}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "%,d".format(item.powerTotal),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = extras.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = buildString {
                // 刻意不显示 `skill_score` / `target_value` 两个字段：
                // 引擎文档没写清它们的单位（倍率还是百分数、是否含活动加成），
                // 猜一个单位显示出来，用户没法判断对不对。原始值在下面的「诊断」里。
                if (poolSize > 0) append("候选池 $poolSize 张")
                if (searchMs > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("搜索 $searchMs ms")
                }
                if (isExact) {
                    if (isNotEmpty()) append(" · ")
                    append("引擎判定 complete（精确 Top-K）")
                } else if (timedOut) {
                    if (isNotEmpty()) append(" · ")
                    append("引擎超时（合法但未证明最优）")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "「当前编成」的综合力用「我的」页同一个公式算，与本页推荐结果不是同一个实现。" +
                "实测同一账号下两者差约 0.5%（≈750 点），所以「较当前编成」出现几百点的小额负数，" +
                "多半是这个口径差，不代表推荐结果更差。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 诊断区：引擎**原始输出** + 重新装载的入口。
 *
 * 保留它的理由：引擎报错时唯一可信的东西是原文（我们自己再包装一层只会丢信息），
 * 而这个页面会长期是"数据形状"问题的第一现场。
 */
@Composable
private fun Diagnostics(
    open: Boolean,
    rawOutput: String,
    stage: RecStage,
    result: DeckRecommendResult?,
    searchMs: Int,
    chainRuns: Int,
    onToggle: () -> Unit,
    onReloadMaster: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "诊断（引擎原始输出）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (rawOutput.isBlank()) "空" else "${rawOutput.length} 字符",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append("阶段=").append(
                        when (stage) {
                            RecStage.Booting -> "引擎加载中"
                            RecStage.Ready -> "引擎就绪"
                            RecStage.BuildingMaster -> "还原 masterdata"
                            RecStage.Searching -> "搜索中"
                            RecStage.Done -> "完成"
                            RecStage.Error -> "失败"
                            RecStage.Idle -> "等输入（缺活动或曲目）"
                        },
                    )
                    result?.let {
                        append(" · completion=").append(it.completion.ifBlank { "（未给）" })
                        append(" · timed_out=").append(it.timedOut)
                        append(" · pool=").append(it.poolSize)
                    }
                    if (searchMs > 0) append(" · 耗时 $searchMs ms")
                    append(" · 装载链跑了 $chainRuns 次")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (rawOutput.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(8.dp),
                ) {
                    Text(
                        text = rawOutput,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    )
                }
            } else {
                Text(
                    "还没有输出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReloadMaster) { Text("重新载入 masterdata") }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "同样的内容也写进了 logcat（tag `DeckEngineDump`），装机排查用：\n" +
                    "adb logcat -d -s DeckEngineDump:I",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 数据侧：把快照还原成引擎要的 masterdata
// ─────────────────────────────────────────────────────────────

/** 组卡引擎要的两个落盘文件（WebView 通过 `/masterdata.json` 与 `/musicMetas.json` 取）。 */
private data class EngineFiles(val master: java.io.File, val musicMetas: java.io.File?)

/**
 * 组装 masterdata + musicMetas 并写到缓存文件。
 *
 * ⚠️ 两个文件都**必须**写：`musicMetas` 是引擎算「分数 / 活动 PT」的第二个入参，
 * 缺了它引擎会退回兜底表，得分看着合理但不对（而且不会报错）。
 */
private suspend fun writeMasterData(container: AppContainer, ownedCardIds: Set<Int>): EngineFiles {
    return withContext(Dispatchers.IO) {
        val dir = java.io.File(container.database.openHelper.writableDatabase.path).parentFile
            .let { java.io.File(it, "deck") }
            .apply { mkdirs() }
        val master = java.io.File(dir, "masterdata.json")
        master.writeText(buildMasterData(container, ownedCardIds))

        val musicMetas = java.io.File(dir, "musicMetas.json")
        val metasJson = buildMusicMetasJson(container)
        // 空数组（库里还没有这张表 = 老用户没导入新快照）时**不注册**这个文件：
        // 引擎会收到 "[]" 并按兜底表算，比给一个空文件更可控。
        val musicFile = if (metasJson.length > 2) {
            musicMetas.writeText(metasJson)
            musicMetas
        } else {
            musicMetas.delete()
            null
        }
        EngineFiles(master, musicFile)
    }
}

/** 从库里读 `musicMetas` 全部行，还原成引擎第二个入参的 JSON 数组文本。 */
private suspend fun buildMusicMetasJson(container: AppContainer): String {
    val dao = container.database.masterDao()
    return withContext(Dispatchers.IO) {
        restoreMusicMetasJson(dao.observeAll("musicMetas.json").first().map { it.data })
    }
}

private suspend fun buildMasterData(container: AppContainer, ownedCardIds: Set<Int> = emptySet()): String {
    val dao = container.database.masterDao()
    suspend fun rows(table: String): List<String> = dao.observeAll(table).first().map { it.data }
    return withContext(Dispatchers.IO) {
        // 引擎按名取表：**缺一张就直接报错**（不是静默忽略）。所以这里对「我们没有的表」
        // 显式给空数组 `[]`，让"缺少表"这类错误消失，剩下的才是真正要修的数据形状问题。
        val restored = mapOf(
            "cards" to restoreCardsJson(rows("cards.json"), keepIds = ownedCardIds.ifEmpty { null }),
            "areaItems" to restoreAreaItemsJson(rows("areaItems.json")),
            "areaItemLevels" to restoreAreaItemLevelsJson(rows("areaItemLevels.json")),
            "honors" to restoreHonorsJson(rows("honors.json")),
            "masterLessons" to restoreMasterLessonsJson(rows("masterLessons.json")),
        )
        // 不需要裁剪的表直接给紧凑化后的原始行
        val rawNames = listOf(
            "cardEpisodes", "skills", "characterRanks", "cardRarities",
            "gameCharacters", "gameCharacterUnits",
            "events", "eventCards", "eventRarityBonusRates", "eventDeckBonuses",
            "eventHonorBonuses", "eventTotalPowerLimits", "eventSkillScoreUpLimits",
            "eventCardBonusLimits", "eventShuffleUnitBonuses", "worldBlooms",
        )
        // 我们快照里没有、但引擎会按名索要的表（给空数组，别让它报"缺少表"）
        val emptyNames = listOf(
            "areas", "shopItems", "ingameNotes", "ingameCombos",
            "cardMysekaiCanvasBonuses", "mysekaiGates", "mysekaiGateLevels",
            "worldBloomSupportDeckBonuses", "worldBloomSupportDeckBonusesWL1",
            "worldBloomSupportDeckBonusesWL2", "worldBloomSupportDeckBonusesWL3",
            "worldBloomDifferentAttributeBonuses", "worldBloomSupportDeckUnitEventLimitedBonuses",
            "eventMysekaiFixtureGameCharacterPerformanceBonusLimits",
        )
        val tables = linkedMapOf<String, String>()
        restored.forEach { (name, text) -> tables[name] = text }
        for (name in rawNames) {
            val tableRows = rows("$name.json")
            tables[name] = if (tableRows.isEmpty()) "[]" else tableRows.joinToString(",", "[", "]")
        }
        for (name in emptyNames) tables[name] = "[]"
        buildMasterDataPayload(tables)
    }
}

// ─────────────────────────────────────────────────────────────
// 小工具
// ─────────────────────────────────────────────────────────────

/** 倍率显示：引擎给的是**百分数**（`120.0` 就是 120%），所以只加符号，不再乘 100。 */
private fun formatPercent(value: Double): String = "%.0f%%".format(value)

/** 上游给的是 Unix **秒**；格式化成设备本地时间。 */
private fun formatSnapshotTime(seconds: Long): String {
    if (seconds <= 0L) return "未知"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(seconds * 1000L))
}

/**
 * 把引擎结果/报错**分片写进 logcat**。
 *
 * 为什么走 logcat 而不是写文件：release 版 App 的 `filesDir` 在 `/data/data/...`，
 * adb 没有 root/run-as 读不到；写 `/sdcard/Download` 又受分区存储限制。
 * logcat 是唯一不需要任何权限、`adb logcat -d` 就能取的通道。
 * 每片控制在 3 KB（logcat 单条有长度上限），带 `n/N` 序号便于拼回。
 */
private fun dumpToLog(tag: String, text: String) {
    val chunkSize = 3000
    val total = (text.length + chunkSize - 1) / chunkSize
    for (i in 0 until total) {
        val part = text.substring(i * chunkSize, minOf(text.length, (i + 1) * chunkSize))
        android.util.Log.i("DeckEngineDump", "$tag ${i + 1}/$total $part")
    }
    android.util.Log.i("DeckEngineDump", "$tag END len=${text.length}")
}
