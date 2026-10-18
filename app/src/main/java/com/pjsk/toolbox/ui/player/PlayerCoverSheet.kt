package com.pjsk.toolbox.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pjsk.toolbox.data.card.Card
import com.pjsk.toolbox.data.card.CardArtPlan
import com.pjsk.toolbox.data.card.PjskCharacter
import com.pjsk.toolbox.data.card.displayNameFor
import com.pjsk.toolbox.data.player.MAX_COVER_CARDS
import com.pjsk.toolbox.data.player.PlayerCoverMode
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.ui.common.RemoteImage

/**
 * 「播放器封面」选择弹层。
 *
 * ## 为什么先只按角色选
 *
 * 用户的原话是"可以选一个角色的不同卡牌" —— 按角色取卡就覆盖了这个需求。
 * 完整的筛选面板（搜索/稀有度/属性/卡池类型）以后想接时，**塞进同一个 sheet 即可**，
 * 因为卡的取用已经走了 `cards.filter { it.characterId == … }`，换成分 `filterCards(cards, query)`
 * 是一行的事。
 *
 * ## 为什么存「卡 id 列表」而不是「筛选条件」
 *
 * 卡表每次更新都会让同一套条件筛出不同的卡；而用户的心智是"我选的就是这几张"。
 * 存 id 还顺带解决了"换区服后条件含义变化"的问题。
 *
 * ## 空集合的行为
 *
 * 一张都没选时播放器**照旧显示曲绘**（不是空白）。这一点在下面有一行明说，
 * 否则用户会以为"开了自定义却坏掉了"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerCoverSheet(
    mode: PlayerCoverMode,
    selectedIds: List<Int>,
    cards: List<Card>,
    characters: List<PjskCharacter>,
    nameLanguage: NameLanguage,
    region: ServerRegion,
    onModeChange: (PlayerCoverMode) -> Unit,
    onSelectionChange: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    // 默认停在这张已选卡所属的角色上；没选过就停第一个。
    // ⚠️ key 只用 `characters`：如果连 `selectedIds` 也当 key，勾一张卡就会把角色重置
    // （虽然重置到的还是同一角色，但"清空该角色"之后会突然跳走，看着像 bug）。
    var characterId by remember(characters) {
        mutableStateOf(
            selectedIds.firstNotNullOfOrNull { id -> cards.firstOrNull { it.id == id }?.characterId }
                ?: characters.firstOrNull()?.id
                ?: 1,
        )
    }
    val characterCards = remember(cards, characterId) {
        cards.filter { it.characterId == characterId }.sortedByDescending { it.releaseAt }
    }
    val selectedSet = remember(selectedIds) { selectedIds.toSet() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                "播放器封面",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))

            // ── 模式 ──
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerCoverMode.entries.forEach { option ->
                    FilterChip(
                        selected = option == mode,
                        onClick = { onModeChange(option) },
                        label = { Text(option.label) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = mode.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (mode == PlayerCoverMode.CARD_ART) {
                Spacer(Modifier.height(12.dp))

                // ── 角色 ──
                Text(
                    "角色",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(characters, key = { it.id }) { character ->
                        val chosen = character.id == characterId
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .then(
                                        if (chosen) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { characterId = character.id },
                            ) {
                                AsyncImage(
                                    model = AssetUrls.characterAvatar(region, character.id),
                                    contentDescription = character.displayNameFor(nameLanguage),
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.size(52.dp),
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = " ${character.displayNameFor(nameLanguage)} ".trim(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (chosen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "该角色 ${characterCards.size} 张 · 已选 ${selectedIds.size}/$MAX_COVER_CARDS",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            // 全选该角色（受总上限约束；超出的部分不静默丢，而是截断后由计数显示）
                            val merged = selectedIds + characterCards.map { it.id }
                            onSelectionChange(merged.distinct().take(MAX_COVER_CARDS))
                        },
                    ) { Text("全选") }
                    TextButton(
                        onClick = {
                            val ids = characterCards.map { it.id }.toSet()
                            onSelectionChange(selectedIds.filterNot { it in ids })
                        },
                    ) { Text("清空该角色") }
                }

                // ── 该角色的卡（3 列）──
                characterCards.chunked(3).forEach { rowCards ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowCards.forEach { card ->
                            CardPickTile(
                                card = card,
                                trained = card.artPlan != CardArtPlan.NORMAL_ONLY,
                                region = region,
                                selected = card.id in selectedSet,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val next = if (card.id in selectedSet) {
                                        selectedIds - card.id
                                    } else {
                                        (selectedIds + card.id).take(MAX_COVER_CARDS)
                                    }
                                    onSelectionChange(next)
                                },
                            )
                        }
                        repeat(3 - rowCards.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (selectedIds.isEmpty()) {
                        "一张都没选 → 播放器继续用**歌曲曲绘**。选中的卡会在**每次切歌时随机**出现一张。"
                    } else {
                        "已选 ${selectedIds.size} 张：**每次切歌随机**显示其中一张，播放器配色会跟着封面变。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/** 选卡格：16:9 卡面 + 选中描边。 */
@Composable
private fun CardPickTile(
    card: Card,
    trained: Boolean,
    region: ServerRegion,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            RemoteImage(
                url = AssetUrls.cardImage(region, card.assetbundleName, trained, AssetUrls.CardSize.SMALL),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (selected) "已选" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}
