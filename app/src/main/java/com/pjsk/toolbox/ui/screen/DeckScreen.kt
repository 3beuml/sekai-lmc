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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.pjsk.toolbox.data.profile.OwnedCard
import com.pjsk.toolbox.data.profile.PlayerDeck
import com.pjsk.toolbox.data.profile.PlayerSnapshot
import com.pjsk.toolbox.data.profile.computeDeckPower
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.ui.common.PlayerDataGate
import com.pjsk.toolbox.ui.common.RemoteImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「编成」页：游戏里存的那几套编成，逐套摊开看。
 *
 * ## 为什么需要这一屏
 *
 * 「我的」页的综合力模块只能给出一个总数（「5 张卡」+ 四个分项），
 * 看不到到底是哪 5 张 —— 用户的原话是「看不到卡片，只能看到是五张卡」。
 * 这一屏把每套编成的 5 张卡连卡面带等级列出来，并且**每套各算一份综合力**，
 * 这样"当前这套为什么是这个数"就一目了然。
 *
 * ## 复用的东西
 *
 * 综合力用 [computeDeckPower]（与「我的」页同一个纯函数、同一份数据），
 * 不另算一套 —— 否则两处的数字迟早会不一致，而这种不一致最难发现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(
    region: ServerRegion,
    onBack: () -> Unit,
    onOpenRecommend: () -> Unit = {},
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

    val account by settings.playerAccount.collectAsState()
    val cards by cardRepository.cards.collectAsState()
    val characters by cardRepository.characters.collectAsState(initial = emptyList())
    val episodesByCard by remember { cardRepository.cardEpisodes }.collectAsState(initial = emptyMap())
    val areaItemLevels by remember { masterRef.areaItemLevels }.collectAsState(initial = emptyMap())
    val honorBonuses by remember { masterRef.honorBonuses }.collectAsState(initial = emptyMap())
    val characterRankRates by remember { masterRef.characterRankRates }.collectAsState(initial = emptyMap())
    val nameLanguage by settings.nameLanguage.collectAsState()

    val snapshotState by produceState<DeckLoad>(DeckLoad.Loading, account) {
        val acc = account
        value = if (acc == null) {
            DeckLoad.Unbound
        } else {
            // 只读本地缓存，不额外请求 Haruki（进这一屏只是换一种看法）
            val snap = withContext(Dispatchers.IO) { playerRepository.loadCached(acc) }
            if (snap == null) DeckLoad.NoCache else DeckLoad.Ready(snap)
        }
    }

    when (val state = snapshotState) {
        DeckLoad.Unbound -> PlayerDataGate(
            title = "编成",
            reason = "还没有绑定账号。",
            actionLabel = "去绑定",
            onBack = onBack,
            onAction = onOpenBind,
        )

        DeckLoad.NoCache -> PlayerDataGate(
            title = "编成",
            reason = "本地还没有这份账号的数据，去「我的」读取一次就有了。",
            actionLabel = "去「我的」",
            onBack = onBack,
            onAction = onOpenMyPlayer,
        )

        DeckLoad.Loading -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("编成") },
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

        is DeckLoad.Ready -> DeckContent(
            region = region,
            snapshot = state.snapshot,
            cards = remember(cards) { cards.associateBy { it.id } },
            characters = remember(characters) { characters.associateBy { it.id } },
            episodesByCard = episodesByCard,
            areaItemLevels = areaItemLevels,
            honorBonuses = honorBonuses,
            characterRankRates = characterRankRates,
            nameLanguage = nameLanguage,
            onBack = onBack,
            onOpenCard = onOpenCard,
        )
    }
}

/** 快照读取的三种结局。 */
private sealed interface DeckLoad {
    data object Loading : DeckLoad
    data object Unbound : DeckLoad
    data object NoCache : DeckLoad
    data class Ready(val snapshot: PlayerSnapshot) : DeckLoad
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckContent(
    region: ServerRegion,
    snapshot: PlayerSnapshot,
    cards: Map<Int, Card>,
    characters: Map<Int, PjskCharacter>,
    episodesByCard: Map<Int, List<com.pjsk.toolbox.data.card.CardEpisode>>,
    areaItemLevels: Map<Long, com.pjsk.toolbox.data.profile.AreaItemLevelRow>,
    honorBonuses: Map<Int, Map<Int, Int>>,
    characterRankRates: Map<Long, List<Double>>,
    nameLanguage: NameLanguage,
    onBack: () -> Unit,
    onOpenCard: (Int) -> Unit,
) {
    val decks = snapshot.decks
    val ownedById = remember(snapshot.cards) { snapshot.cards.associateBy { it.cardId } }
    val unitOf = { card: Card ->
        val base = characters[card.characterId]?.unitKey
        val support = card.supportUnit?.takeIf { it.isNotBlank() && it != "none" }
        buildList {
            support?.let { add(it) }
            base?.let { add(it) }
        }.distinct()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("编成", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${decks.size} 套 · 共 ${snapshot.cards.size} 张持有卡",
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
        if (decks.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("快照里没有编成数据", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "去「我的」点一次「重新读取」，或确认游戏里存过编成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(decks, key = { it.deckId }) { deck ->
                val ownedCards = deck.cardIds.mapNotNull { ownedById[it] }
                // 每套各算一份综合力（同一个纯函数、同一份数据）
                val power = remember(deck, snapshot, cards, episodesByCard, areaItemLevels, honorBonuses, characterRankRates) {
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
                    )
                }
                DeckCard(
                    deck = deck,
                    ownedCards = ownedCards,
                    cards = cards,
                    characters = characters,
                    nameLanguage = nameLanguage,
                    region = region,
                    power = power?.total,
                    onOpenCard = onOpenCard,
                )
            }
        }
    }
}

@Composable
private fun DeckCard(
    deck: PlayerDeck,
    ownedCards: List<OwnedCard>,
    cards: Map<Int, Card>,
    characters: Map<Int, PjskCharacter>,
    nameLanguage: NameLanguage,
    region: ServerRegion,
    power: Int?,
    onOpenCard: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (deck.isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = deck.name.ifBlank { "编成 ${deck.deckId}" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (deck.isCurrent) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "当前",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = power?.let { "综合力 %,d".format(it) } ?: "综合力算不出来",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                deck.cardIds.forEach { cardId ->
                    val owned = ownedCards.firstOrNull { it.cardId == cardId }
                    val card = cards[cardId]
                    DeckCardTile(
                        owned = owned,
                        card = card,
                        character = card?.let { characters[it.characterId] },
                        nameLanguage = nameLanguage,
                        region = region,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenCard(cardId) },
                    )
                }
                // 不足 5 张时占位，避免已填的格子被拉伸
                repeat((5 - deck.cardIds.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 编成里的一个卡格：卡图标 + （有持有数据时）等级与突破。
 *
 * 卡图标用 `cardIcon`（正方形小图，几百字节），不是卡面大图 —— 5 张并排时
 * 大图既装不下也浪费流量。
 */
@Composable
private fun DeckCardTile(
    owned: OwnedCard?,
    card: Card?,
    character: PjskCharacter?,
    nameLanguage: NameLanguage,
    region: ServerRegion,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (card != null) {
                AsyncImage(
                    model = AssetUrls.cardIcon(region, card.assetbundleName, owned?.trained ?: card.trainedOnly),
                    contentDescription = card.displayNameFor(nameLanguage),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = owned?.let { "Lv.${it.level}" } ?: "—",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            // 突破等级；0 就不写（省一行空间）
            text = owned?.takeIf { it.masterRank > 0 }?.let { "MR${it.masterRank}" } ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = character?.displayNameFor(nameLanguage) ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}



