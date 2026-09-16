package com.pjsk.toolbox.ui

import com.pjsk.toolbox.data.db.MasterRowEntity
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.ui.common.assetbundleName
import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.jsonObject
import com.pjsk.toolbox.ui.common.str
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 每张表在 UI 上的「表现方式」。
 *
 * 这样设计的好处：列表页与详情页只需要**一份通用实现**，
 * 音乐/卡牌/角色/服装/活动/扭蛋/贴纸全部复用同一套代码
 * （由 `list/{table}` 与 `detail/{table}/{id}` 两个路由覆盖）。
 * 新增一个模块只需要在这里加一行，而不是复制一整个页面。
 */
data class TablePresentation(
    val title: String,
    /** 未下载时给用户的提示语。 */
    val emptyHint: String,
    /** 缩略图 URL；返回 null 表示该表没有可用的缩略图（UI 会显示占位图标）。 */
    val imageUrl: (ServerRegion, MasterRowEntity) -> String?,
    /** 列表行的副标题。 */
    val subtitle: (MasterRowEntity) -> String?,
)

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

/** 把 master data 里的 epoch 毫秒渲染成日期；取不到就返回 null。 */
private fun formatEpoch(raw: String?): String? {
    val millis = raw?.toLongOrNull() ?: return null
    if (millis <= 0L) return null
    return runCatching { dateFormatter.format(Instant.ofEpochMilli(millis)) }.getOrNull()
}

private fun MasterRowEntity.field(key: String): String? = jsonObject()?.str(key)

object Presentations {

    private val MUSIC = TablePresentation(
        title = "音乐",
        emptyHint = "请先到「数据同步」下载音乐模块（约 2.4 MB）。",
        imageUrl = { region, row ->
            row.assetbundleName()?.let { AssetUrls.musicJacket(region, it) }
        },
        subtitle = { row ->
            listOfNotNull(row.field("composer"), row.field("lyricist"))
                .distinct()
                .joinToString(" / ")
                .ifBlank { null }
        },
    )

    private val CARD = TablePresentation(
        title = "卡牌",
        emptyHint = "请先到「数据同步」勾选卡牌模块（约 37 MB）后同步。",
        imageUrl = { region, row ->
            // 列表缩略图用 member_small（940×530，约 27 KB），详情大图才用 member（2520×1440）
            row.assetbundleName()?.let { AssetUrls.cardImage(region, it, trained = false) }
        },
        subtitle = { row ->
            val rarity = row.field("cardRarityType")?.removePrefix("rarity_")?.let { "★$it" }
            val attr = row.field("attr")?.removePrefix("attr_")
            listOfNotNull(rarity, attr, row.field("releaseAt")?.let { formatEpoch(it) })
                .joinToString(" · ")
                .ifBlank { null }
        },
    )

    private val CHARACTER = TablePresentation(
        title = "角色",
        emptyHint = "请先到「数据同步」下载角色模块（约 1.3 MB）。",
        imageUrl = { region, row ->
            // ⚠️ character_select 的内部命名未实测，加载失败会自动走占位图
            row.assetbundleName()?.let { AssetUrls.characterImage(region, it) }
        },
        subtitle = { row ->
            row.jsonObject()?.intOrNull("gameCharacterUnitId")?.let { "组合 #$it" }
        },
    )

    // ⚠️ 服装（COSTUME）这一组已删（2026-09-16）：那 5 张表 53.2 MB，
    // 应用里没有任何界面显示服装，只会在同步界面误导用户去下载。
    // 如果以后做服装图鉴，把 TablePresentation + MasterCatalog 的条目一起加回来。

    private val EVENT = TablePresentation(
        title = "活动",
        emptyHint = "请先到「数据同步」下载活动模块（约 4.3 MB）。",
        imageUrl = { region, row ->
            row.assetbundleName()?.let { AssetUrls.eventBadge(region, it) }
        },
        subtitle = { row ->
            listOfNotNull(
                formatEpoch(row.field("startAt"))?.let { "开始 $it" },
                row.field("eventType"),
            ).joinToString(" · ").ifBlank { null }
        },
    )

    private val GACHA = TablePresentation(
        title = "扭蛋卡池",
        emptyHint = "请先到「数据同步」勾选扭蛋模块（约 47 MB）后同步。",
        imageUrl = { region, row ->
            row.assetbundleName()?.let { AssetUrls.gachaLogo(region, it) }
        },
        subtitle = { row ->
            listOfNotNull(
                formatEpoch(row.field("startAt"))?.let { "开始 $it" },
                row.field("gachaType"),
            ).joinToString(" · ").ifBlank { null }
        },
    )

    private val STICKER = TablePresentation(
        title = "贴纸",
        emptyHint = "请先到「数据同步」下载贴纸模块（约 427 KB）。",
        imageUrl = { region, row ->
            row.assetbundleName()?.let { AssetUrls.stamp(region, it) }
        },
        subtitle = { row -> row.field("characterId")?.let { "角色 #$it" } },
    )

    private val FALLBACK = TablePresentation(
        title = "数据表",
        emptyHint = "该表尚未下载，请到「数据同步」同步。",
        imageUrl = { _, _ -> null },
        subtitle = { row -> "#${row.id}" },
    )

    private val MAP: Map<String, TablePresentation> = mapOf(
        "musics.json" to MUSIC,
        "musicDifficulties.json" to MUSIC.copy(title = "歌曲难度"),
        "musicVocals.json" to MUSIC.copy(title = "音源版本"),
        "musicTags.json" to MUSIC.copy(title = "音乐标签", imageUrl = { _, _ -> null }),
        "musicArtists.json" to MUSIC.copy(title = "曲师", imageUrl = { _, _ -> null }),
        "cards.json" to CARD,
        "cardEpisodes.json" to CARD.copy(title = "卡牌剧情", imageUrl = { _, _ -> null }),
        "skills.json" to CARD.copy(title = "技能", imageUrl = { _, _ -> null }),
        "gameCharacters.json" to CHARACTER,
        "gameCharacterUnits.json" to CHARACTER.copy(title = "组合"),
        "characterProfiles.json" to CHARACTER.copy(title = "角色资料"),
        "unitProfiles.json" to CHARACTER.copy(title = "组合资料", imageUrl = { _, _ -> null }),
        "events.json" to EVENT,
        "eventMusics.json" to EVENT.copy(title = "活动曲", imageUrl = { _, _ -> null }),
        "eventCards.json" to EVENT.copy(title = "活动卡", imageUrl = { _, _ -> null }),
        "eventStories.json" to EVENT.copy(title = "活动剧情", imageUrl = { _, _ -> null }),
        "eventItems.json" to EVENT.copy(title = "活动道具", imageUrl = { _, _ -> null }),
        "worldBlooms.json" to EVENT.copy(title = "世界链接", imageUrl = { _, _ -> null }),
        "stamps.json" to STICKER,
        "gachas.json" to GACHA,
        "gachaTabs.json" to GACHA.copy(title = "卡池分类", imageUrl = { _, _ -> null }),
        "gachaCeilItems.json" to GACHA.copy(title = "天井交换所", imageUrl = { _, _ -> null }),

        // ── 以下为补充收录的表（服务于「活动加成 / 原声带 / 天井」等功能）──
        "musicSoundTracks.json" to MUSIC.copy(title = "游戏原声带"),
        "musicSoundTrackCategories.json" to MUSIC.copy(title = "原声带分类", imageUrl = { _, _ -> null }),
        "musicOriginals.json" to MUSIC.copy(title = "原曲信息"),
        "limitedTimeMusics.json" to MUSIC.copy(title = "期间限定曲"),
        "musicVideoCharacters.json" to MUSIC.copy(title = "MV 出演角色", imageUrl = { _, _ -> null }),
        "musicAssetVariants.json" to MUSIC.copy(title = "音源变体", imageUrl = { _, _ -> null }),
        "eventExchangeSummaries.json" to EVENT.copy(title = "活动交换所"),
        "eventMissions.json" to EVENT.copy(title = "活动任务"),
        "eventCardBonusLimits.json" to EVENT.copy(title = "加成上限", imageUrl = { _, _ -> null }),
        "eventShuffleUnitBonuses.json" to EVENT.copy(title = "混合组合加成", imageUrl = { _, _ -> null }),
        "eventSkillScoreUpLimits.json" to EVENT.copy(title = "技能加分上限", imageUrl = { _, _ -> null }),
        "eventTotalPowerLimits.json" to EVENT.copy(title = "综合力上限", imageUrl = { _, _ -> null }),
        "eventBreakTimes.json" to EVENT.copy(title = "活动休息时段", imageUrl = { _, _ -> null }),
        "eventHonorBonuses.json" to EVENT.copy(title = "活动称号加成", imageUrl = { _, _ -> null }),
        "eventStoryUnits.json" to EVENT.copy(title = "活动剧情组合", imageUrl = { _, _ -> null }),
        "eventRarityBonusRates.json" to EVENT.copy(title = "星级加成率", imageUrl = { _, _ -> null }),
        "gachaCeilExchangeSummaries.json" to GACHA.copy(title = "天井交换汇总"),
        "gachaBonusItemReceivableRewards.json" to GACHA.copy(title = "赠品奖励", imageUrl = { _, _ -> null }),
        "gachaBonusPoints.json" to GACHA.copy(title = "卡池奖励点数", imageUrl = { _, _ -> null }),
        "gachaBonuses.json" to GACHA.copy(title = "卡池奖励", imageUrl = { _, _ -> null }),
        "gachaTickets.json" to GACHA.copy(title = "抽卡券", imageUrl = { _, _ -> null }),
        "gachaExtras.json" to GACHA.copy(title = "卡池附加", imageUrl = { _, _ -> null }),
        "gachaFreebieGroups.json" to GACHA.copy(title = "免费十连组", imageUrl = { _, _ -> null }),
        "cardExtras.json" to CARD.copy(title = "卡牌附加信息", imageUrl = { _, _ -> null }),
        "cardSupplyGroups.json" to CARD.copy(title = "卡牌素材组", imageUrl = { _, _ -> null }),
        "cardSupplies.json" to CARD.copy(title = "卡牌素材", imageUrl = { _, _ -> null }),
        "cardSkillCosts.json" to CARD.copy(title = "技能消耗", imageUrl = { _, _ -> null }),
        "cardExchangeResources.json" to CARD.copy(title = "卡牌交换资源", imageUrl = { _, _ -> null }),
        // ⚠️ 这张**留着**：它属于 CARD 模块（在快照里、151 KB），只是"卡牌 → 3D 服装"的映射，
        // 和上面被移除的服装模块不是一回事 —— 表浏览器还能翻它。
        "cardCostume3ds.json" to CARD.copy(title = "卡牌服装映射", imageUrl = { _, _ -> null }),
        "cardRarities.json" to CARD.copy(title = "星级定义", imageUrl = { _, _ -> null }),
        "cardCostume3ds.json" to CARD.copy(title = "卡牌服装", imageUrl = { _, _ -> null }),
        "outsideCharacters.json" to CHARACTER.copy(title = "外部角色"),
        "challengeLiveCharacters.json" to CHARACTER.copy(title = "挑战 Live 角色"),
        "subGameCharacters.json" to CHARACTER.copy(title = "小游戏角色"),
        "mobCharacters.json" to CHARACTER.copy(title = "路人角色"),
        "character2ds.json" to CHARACTER.copy(title = "角色 2D 立绘"),
        "characterRanks.json" to CHARACTER.copy(title = "角色等级", imageUrl = { _, _ -> null }),
    )

    operator fun get(tableName: String): TablePresentation =
        MAP[tableName] ?: FALLBACK.copy(title = tableName.removeSuffix(".json"))
}
