package com.pjsk.toolbox.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.pjsk.toolbox.data.player.COVER_RARITY_KEYS
import com.pjsk.toolbox.data.player.CoverAspect
import com.pjsk.toolbox.data.player.CoverCandidate
import com.pjsk.toolbox.data.player.CoverFilter
import com.pjsk.toolbox.data.player.CoverVariant
import com.pjsk.toolbox.data.player.PlayerCoverMode
import com.pjsk.toolbox.data.player.coverRarityLabel
import com.pjsk.toolbox.data.player.expandCoverVariants
import com.pjsk.toolbox.data.player.matchesCoverFilter
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.ui.common.RemoteImage

/**
 * 「播放器封面」设置弹层（歌曲列表顶栏与播放器**共用这一个**）。
 *
 * ## 为什么是"条件式"而不是"逐张勾选"
 *
 * 用户的心智是"我要一歌的 ★4 卡面"这种条件，而不是"这 37 张卡的清单"：
 * 新卡上线后条件天然把它们包含进来。所以这里**不做逐张排除**，只给三个维度：
 * 角色（多选）/ 星级（多选）/ 觉醒版本。想看具体是什么，下面有命中的预览网格。
 *
 * ## 三处必须让用户看见的东西
 *
 * 1. **命中张数** —— 条件越窄越可控，但用户看不见后果就只是瞎猜；
 * 2. **觉醒后排除的张数** —— ★1/★2/生日卡没有觉醒后卡面，"只要觉醒后"会把它们剔掉。
 *    不说出来用户会以为"我勾了 ★1 怎么没有"；
 * 3. **命中 0 张会回退曲绘** —— 否则他以为功能坏了。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerCoverSheet(
    mode: PlayerCoverMode,
    aspect: CoverAspect,
    filter: CoverFilter,
    cards: List<Card>,
    characters: List<PjskCharacter>,
    nameLanguage: NameLanguage,
    region: ServerRegion,
    onModeChange: (PlayerCoverMode) -> Unit,
    onAspectChange: (CoverAspect) -> Unit,
    onFilterChange: (CoverFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    // 条件 → 候选（与播放器里同一套判定函数，不各写一遍）
    val candidates = remember(cards, filter) {
        cards.mapNotNull { card ->
            CoverCandidate(
                id = card.id,
                characterId = card.characterId,
                rarityKey = card.rarityKey,
                trainedAvailable = card.artPlan != CardArtPlan.NORMAL_ONLY,
            ).takeIf { matchesCoverFilter(it, filter) }
        }
    }
    val picks = remember(candidates, filter.variant) {
        expandCoverVariants(candidates, filter.variant)
    }
    // "按变体展开后会被排除"的张数：候选里有、但展开后一张都没有的卡
    val excludedByVariant = remember(candidates, picks) {
        val keptIds = picks.map { it.cardId }.toSet()
        candidates.count { it.id !in keptIds }
    }
    val hitCards = remember(picks, cards) {
        val byId = cards.associateBy { it.id }
        picks.mapNotNull { byId[it.cardId] }.distinctBy { it.id }
    }
    val trainedOf = remember(picks) { picks.associate { it.cardId to it.trained } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            Text(
                "播放器封面",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))

            // ── 来源 ──
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

            // ── 比例（只有卡面模式才有意义）──
            if (mode == PlayerCoverMode.CARD_ART) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("比例", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
                    CoverAspect.entries.forEach { option ->
                        FilterChip(
                            selected = option == aspect,
                            onClick = { onAspectChange(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
                Text(
                    text = CoverAspect.entries.first { it == aspect }.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                // ── 角色（多选，不选 = 不限）──
                Spacer(Modifier.height(12.dp))
                ConditionTitle("角色", hint = if (filter.characterIds.isEmpty()) "不选 = 不限" else "已选 ${filter.characterIds.size} 位")
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(characters, key = { it.id }) { character ->
                        val chosen = character.id in filter.characterIds
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .then(
                                        if (chosen) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable {
                                        onFilterChange(
                                            filter.copy(
                                                characterIds = if (chosen) {
                                                    filter.characterIds - character.id
                                                } else {
                                                    filter.characterIds + character.id
                                                },
                                            ),
                                        )
                                    },
                            ) {
                                AsyncImage(
                                    model = AssetUrls.characterAvatar(region, character.id),
                                    contentDescription = character.displayNameFor(nameLanguage),
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.size(50.dp),
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = character.displayNameFor(nameLanguage),
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

                // ── 星级（多选）──
                Spacer(Modifier.height(12.dp))
                ConditionTitle("星级", hint = if (filter.rarityKeys.isEmpty()) "不选 = 不限" else "已选 ${filter.rarityKeys.size} 档")
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    COVER_RARITY_KEYS.forEach { key ->
                        val chosen = key in filter.rarityKeys
                        FilterChip(
                            selected = chosen,
                            onClick = {
                                onFilterChange(
                                    filter.copy(
                                        rarityKeys = if (chosen) {
                                            filter.rarityKeys - key
                                        } else {
                                            filter.rarityKeys + key
                                        },
                                    ),
                                )
                            },
                            label = { Text(coverRarityLabel(key)) },
                        )
                    }
                }

                // ── 觉醒版本 ──
                Spacer(Modifier.height(12.dp))
                ConditionTitle("卡面版本", hint = null)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CoverVariant.entries.forEach { option ->
                        FilterChip(
                            selected = option == filter.variant,
                            onClick = { onFilterChange(filter.copy(variant = option)) },
                            label = { Text(option.label) },
                        )
                    }
                }
                Text(
                    text = filter.variant.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                // ── 命中结果：必须让用户看见后果 ──
                Spacer(Modifier.height(12.dp))
                Text(
                    text = buildString {
                        append("命中 ${hitCards.size} 张")
                        if (excludedByVariant > 0) {
                            append("（另有 $excludedByVariant 张因为**没有觉醒后卡面**被排除）")
                        }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hitCards.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                if (hitCards.isEmpty()) {
                    Text(
                        "没有命中任何卡 → 播放器会继续用**歌曲曲绘**。把条件放宽一点试试。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    // 预览前 9 张（只读）：让用户确认"这条条件筛出来的长什么样"
                    hitCards.take(9).chunked(3).forEach { rowCards ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowCards.forEach { card ->
                                RemoteImage(
                                    url = AssetUrls.cardImage(
                                        region,
                                        card.assetbundleName,
                                        trainedOf[card.id] ?: false,
                                        AssetUrls.CardSize.SMALL,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            }
                            repeat(3 - rowCards.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (hitCards.size > 9) {
                        Text(
                            "…以及另外 ${hitCards.size - 9} 张",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "**每次切歌随机**显示一张，播放器配色会跟着封面变；切版本不换封面。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ConditionTitle(text: String, hint: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
        if (hint != null) {
            Spacer(Modifier.size(6.dp))
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
