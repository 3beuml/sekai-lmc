package com.pjsk.toolbox.data.remote

/**
 * 区服定义。
 *
 * 全部实测可用的 GitHub Pages 基址（2026-09 验证 HTTP 200）：
 *   https://sekai-world.github.io/sekai-master-db-diff/musics.json        (JP)
 *   https://sekai-world.github.io/sekai-master-db-en-diff/...             (EN)
 *   https://sekai-world.github.io/sekai-master-db-cn-diff/...             (CN)
 *   https://sekai-world.github.io/sekai-master-db-kr-diff/...             (KR)
 *   https://sekai-world.github.io/sekai-master-db-tc-diff/...             (TW)
 *
 * 注意：繁中的仓库名是 `-tc-diff`，但素材桶是 `sekai-tc-assets`。
 */
enum class ServerRegion(
    val id: String,
    val displayName: String,
    /** GitHub 仓库名后缀：sekai-master-db<repoSuffix> */
    val repoSuffix: String,
    /** 素材桶名：sekai-<assetBucket>-assets */
    val assetBucket: String,
) {
    JP("jp", "日服", "-diff", "jp"),
    EN("en", "国际服", "-en-diff", "en"),
    CN("cn", "简中", "-cn-diff", "cn"),
    KR("kr", "韩服", "-kr-diff", "kr"),
    TW("tw", "繁中", "-tc-diff", "tc"),
    ;

    /** master data 单表完整 URL。 */
    fun tableUrl(fileName: String): String = "$GITHUB_PAGES_BASE/sekai-master-db$repoSuffix/$fileName"

    /** 素材 CDN 根（不含尾部斜杠）。 */
    val assetBase: String get() = "$ASSET_CDN_BASE/sekai-$assetBucket-assets"

    companion object {
        const val GITHUB_PAGES_BASE = "https://sekai-world.github.io"
        const val ASSET_CDN_BASE = "https://storage.sekai.best"

        /** 额外素材桶（与区服无关）。 */
        const val LIVE2D_BASE = "$ASSET_CDN_BASE/sekai-live2d-assets"
        const val MUSIC_CHART_BASE = "$ASSET_CDN_BASE/sekai-music-charts"
        const val BEST_ASSETS_BASE = "$ASSET_CDN_BASE/sekai-best-assets"

        fun fromId(id: String): ServerRegion =
            // 显式写全限定名，避免依赖 companion 内对枚举 `entries` 的隐式解析
            ServerRegion.entries.firstOrNull { it.id == id } ?: JP

        val DEFAULT: ServerRegion = JP
    }
}

/**
 * 数据模块。用来把「体积巨大」的表和「体积很小」的表分开，让用户可以选择性下载。
 *
 * ⚠️ **2026-09-16 删掉了 `COSTUME`（服装）模块**：
 * 它 5 张表 raw 合计 **53.2 MB**（`costume3ds.json` 一张就 55.6 MB），
 * 但**应用里没有任何界面显示服装** —— 没有任何 Repository 读它，
 * 界面上只会出现"可以下载 53 MB"却看不到东西，属于误导。
 *
 * ⚠️ 注意：**快照里本来也没有这几张表**（`tools/datapack/raw/` 里从来没下载过），
 * 所以删它**不影响 APK 体积**，删掉的是"同步界面上的一个假选择"。
 * 以后真要做服装图鉴（2D/3D 预览）时，再把模块和那几条 TableSpec 加回来即可。
 */
enum class DataModule(
    val id: String,
    val displayName: String,
    val description: String,
    /**
     * 同步顺序（越小越先），也是界面上模块列表的顺序。
     *
     * 依据是「用户多常用 × 内容多常变」：
     *  - **卡牌、音乐**是最常用的两块，而且几乎每次活动更新都会变 → 最前面；
     *  - 角色、活动是列表与详情的支撑数据 → 中间；
     *  - **卡池垫底**：`gachas.json` 有 44.7 MB，而它只在首页看一眼。
     *    放在最后，慢线路上不会因为它把卡牌/歌曲挤到后面。
     */
    val syncPriority: Int,
) {
    CARD("card", "卡牌", "卡牌、星级、技能、卡牌剧情（体积较大）", 0),
    MUSIC("music", "音乐", "歌曲、难度、音源、曲师、标签", 1),
    CHARACTER("character", "角色", "角色、组合、角色资料", 2),
    EVENT("event", "活动", "活动列表、活动卡、加成、活动剧情", 3),
    STICKER("sticker", "贴纸", "表情包制作所需的贴纸素材定义", 4),
    GACHA("gacha", "扭蛋卡池", "卡池、抽卡券、天井（体积很大）", 5),
    ;
}

/**
 * 单张表的规格。
 *
 * [approxBytes] 来自实测的 GitHub tree 响应（398 个文件 / 合计 347.8 MB）。
 * 取 0 表示体积尚未实测，UI 上显示为「体积未知」。
 */
data class TableSpec(
    val fileName: String,
    val module: DataModule,
    val approxBytes: Long,
    /** 是否为「中文名叠加层」所需的 CN 表（见 [com.pjsk.toolbox.data.sync.MasterImporter.applyOverlay]）。 */
    val cnOverlay: Boolean = false,
)

/**
 * 表目录。
 *
 * 收录原则：**每一张表都经过实测确认存在**，不靠猜。
 *
 * 判定方法（两种，响应都很小）：
 *  - 存在性：`GET https://api.github.com/repos/Sekai-World/sekai-master-db-diff/commits?path=<file>&per_page=1`
 *    返回 `[]` 即不存在；
 *  - 体积：解析 `GET .../git/trees/main?recursive=1` 的 `size` 字段
 *    （注意该响应会被截断，所以「搜不到」不能作为「不存在」的证据）。
 *
 * 实测「不存在」的名字，切勿加入：`characters.json`、`costumes.json`、
 * `gachaCards.json`、`gachaPickups.json`、`gachaDetails.json`、`live2ds.json`、
 * `eventRarityBonusParameters.json`。
 *
 * ## 维护方式
 *
 * `ALL` 块由 `tools/regen-catalog.ps1` 自动重写（按模块分组、按体积升序、写入精确字节数），
 * 所以**不要把说明性注释写在 `ALL` 块内部**——重新生成时不会保留。要长期保留的说明写在这里。
 *
 * ## 两条容易踩的数据事实
 *
 *  - `worldBlooms.json`（世界链接章节）**没有 `name` 字段**，实际字段是
 *    `id / eventId / gameCharacterId / worldBloomChapterType / chapterNo /
 *    chapterStartAt / aggregateAt / chapterEndAt / isSupplemental`，
 *    因此它不参与中文名叠加（已实测其大小为 21,586 字节）。
 *  - 显示名字段随表而异：`cards.json` 是 `prefix`，`musics.json` 是 `title`；
 *    简中中文名统一在 `infos[0].title`（见 [com.pjsk.toolbox.data.sync.TableSchemas]）。
 */
object TableCatalog {

    private const val KB = 1024L
    private const val MB = 1024L * 1024L

    val ALL: List<TableSpec> = listOf(
        // ── 角色 ────────────────────────────────────────────────
        TableSpec("outsideCharacters.json", DataModule.CHARACTER, 2_935L, cnOverlay = true),
        TableSpec("unitProfiles.json", DataModule.CHARACTER, 3_005L, cnOverlay = true),
        TableSpec("challengeLiveCharacters.json", DataModule.CHARACTER, 3_052L, cnOverlay = true),
        TableSpec("subGameCharacters.json", DataModule.CHARACTER, 6_497L, cnOverlay = true),
        TableSpec("gameCharacters.json", DataModule.CHARACTER, 11_767L, cnOverlay = true),
        TableSpec("gameCharacterUnits.json", DataModule.CHARACTER, 11_838L),
        TableSpec("mobCharacters.json", DataModule.CHARACTER, 14_789L),
        TableSpec("characterProfiles.json", DataModule.CHARACTER, 19_130L, cnOverlay = true),
        TableSpec("character2ds.json", DataModule.CHARACTER, 112_555L),
        TableSpec("characterRanks.json", DataModule.CHARACTER, 1_174_467L),

        // ── 音乐 ────────────────────────────────────────────────
        TableSpec("limitedTimeMusics.json", DataModule.MUSIC, 785L, cnOverlay = true),
        TableSpec("musicCollaborations.json", DataModule.MUSIC, 1_001L),
        TableSpec("musicSoundTrackCategories.json", DataModule.MUSIC, 1_477L, cnOverlay = true),
        TableSpec("musicAchievements.json", DataModule.MUSIC, 4_360L, cnOverlay = true),
        TableSpec("musicAssetVariants.json", DataModule.MUSIC, 6_693L),
        TableSpec("musicDanceMembers.json", DataModule.MUSIC, 12_849L),
        TableSpec("musicArtists.json", DataModule.MUSIC, 32_461L, cnOverlay = true),
        TableSpec("musicOriginals.json", DataModule.MUSIC, 51_935L, cnOverlay = true),
        TableSpec("musicSoundTracks.json", DataModule.MUSIC, 53_837L, cnOverlay = true),
        // ⚠️ 简中服仓库里**没有** musicCategories.json（实测 404，仓库树里也没有），
        // 所以这里不能标 cnOverlay —— 标了只会让每次同步都白跑一次 404。
        // 效果：分类名用日文原名（上游简中服本来就没给这一项）。
        TableSpec("musicCategories.json", DataModule.MUSIC, 66_535L),
        TableSpec("musicVideoCharacters.json", DataModule.MUSIC, 125_974L, cnOverlay = true),
        TableSpec("musicTags.json", DataModule.MUSIC, 140_613L, cnOverlay = true),
        TableSpec("musicDifficulties.json", DataModule.MUSIC, 460_717L),
        TableSpec("musics.json", DataModule.MUSIC, 497_098L, cnOverlay = true),
        TableSpec("musicVocals.json", DataModule.MUSIC, 1_155_918L, cnOverlay = true),

        // ── 活动 ────────────────────────────────────────────────
        TableSpec("eventShuffleUnitBonuses.json", DataModule.EVENT, 2L),
        TableSpec("eventCardBonusLimits.json", DataModule.EVENT, 70L),
        TableSpec("eventSkillScoreUpLimits.json", DataModule.EVENT, 72L),
        TableSpec("eventTotalPowerLimits.json", DataModule.EVENT, 362L),
        TableSpec("eventBreakTimes.json", DataModule.EVENT, 606L),
        TableSpec("eventRarityBonusRates.json", DataModule.EVENT, 3_176L),
        TableSpec("eventMusics.json", DataModule.EVENT, 12_299L),
        TableSpec("eventHonorBonuses.json", DataModule.EVENT, 18_248L),
        TableSpec("worldBlooms.json", DataModule.EVENT, 21_586L),
        TableSpec("eventStoryUnits.json", DataModule.EVENT, 56_383L),
        TableSpec("eventItems.json", DataModule.EVENT, 59_813L, cnOverlay = true),
        TableSpec("eventCards.json", DataModule.EVENT, 160_376L),
        TableSpec("eventDeckBonuses.json", DataModule.EVENT, 374_389L),
        TableSpec("eventMissions.json", DataModule.EVENT, 459_632L, cnOverlay = true),
        // 剧情列表用的两张表。**必须保留嵌套**：两者都是「一行里含 episodes[]」，
        // 按 id 建行会把整整一集当成一行（RowProjectors 里没有它们 → 原样保留 ✅）。
        TableSpec("specialStories.json", DataModule.EVENT, 47_881L, cnOverlay = true),
        TableSpec("unitStories.json", DataModule.EVENT, 56_089L, cnOverlay = true),
        // 「区域对话」要的两张表：areas 给区域名，actionSets 给每个区域下的对话。
        // actionSets 实测 **956 KB**（GitHub Pages 的 HEAD 会按 gzip 报成 52 KB，别信 HEAD）。
        TableSpec("eventStories.json", DataModule.EVENT, 790_091L, cnOverlay = true),
        TableSpec("eventExchangeSummaries.json", DataModule.EVENT, 1_775_686L, cnOverlay = true),
        TableSpec("events.json", DataModule.EVENT, 2_824_340L, cnOverlay = true),

        // ── 贴纸（表情包制作） ──────────────────────────────────
        TableSpec("stamps.json", DataModule.STICKER, 437_066L, cnOverlay = true),

        // ── 卡牌（体积大） ──────────────────────────────────────
        TableSpec("cardExchangeResources.json", DataModule.CARD, 329L),
        TableSpec("cardRarities.json", DataModule.CARD, 575L),
        TableSpec("cardSupplyGroups.json", DataModule.CARD, 623L),
        TableSpec("cardSupplies.json", DataModule.CARD, 716L),
        TableSpec("cardExtras.json", DataModule.CARD, 916L),
        TableSpec("cardSkillCosts.json", DataModule.CARD, 5_456L),
        // 素材表：剧情开放 / 突破消耗在 master data 里只给 `resourceId`，名字只能从这里查。
        // 只有 280 行 / 85 KB，但卡片详情的「开放消耗」和剧情详情页都要靠它出中文名。
        TableSpec("materials.json", DataModule.CARD, 85_840L, cnOverlay = true),
        TableSpec("skills.json", DataModule.CARD, 115_916L, cnOverlay = true),
        TableSpec("cardCostume3ds.json", DataModule.CARD, 207_259L),
        TableSpec("cardEpisodes.json", DataModule.CARD, 1_644_684L, cnOverlay = true),
        TableSpec("cards.json", DataModule.CARD, 35_064_338L, cnOverlay = true),

        // ── 扭蛋卡池（体积很大） ────────────────────────────────
        TableSpec("gachaBonuses.json", DataModule.GACHA, 117L),
        TableSpec("gachaBonusPoints.json", DataModule.GACHA, 218L),
        TableSpec("gachaTickets.json", DataModule.GACHA, 5_713L),
        TableSpec("gachaExtras.json", DataModule.GACHA, 7_222L),
        TableSpec("gachaFreebieGroups.json", DataModule.GACHA, 11_928L),
        TableSpec("gachaTabs.json", DataModule.GACHA, 20_579L, cnOverlay = true),
        TableSpec("gachaBonusItemReceivableRewards.json", DataModule.GACHA, 23_135L),
        TableSpec("gachaCeilItems.json", DataModule.GACHA, 103_505L),
        TableSpec("gachaCeilExchangeSummaries.json", DataModule.GACHA, 1_573_276L, cnOverlay = true),
        TableSpec("gachas.json", DataModule.GACHA, 46_839_552L, cnOverlay = true),

        // ── 服装：**已移除**（2026-09-16）──
        // 原来这里 5 张表（costume3ds 55.6 MB + costume2ds 110 KB + avatarCostumes +
        // characterCostumes + costume2dGroups），raw 合计 53.2 MB，
        // 但应用里没有任何界面显示服装 → 只会在同步界面误导用户去下 53 MB。
        // 详情见 DataModule 的说明；以后做服装图鉴时把这几条加回来。
    )

    val byFileName: Map<String, TableSpec> = ALL.associateBy { it.fileName }

    fun forModules(modules: Set<DataModule>): List<TableSpec> =
        ALL.filter { it.module in modules }

    fun totalBytes(modules: Set<DataModule>): Long =
        forModules(modules).sumOf { it.approxBytes }

    /** 语义化的体积文案。 */
    fun formatBytes(bytes: Long): String = when {
        bytes <= 0L -> "体积未知"
        bytes < KB -> "$bytes B"
        bytes < MB -> "%.0f KB".format(bytes / KB.toDouble())
        else -> "%.1f MB".format(bytes / MB.toDouble())
    }
}
