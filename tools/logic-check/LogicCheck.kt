import com.pjsk.toolbox.data.card.CardArtPlan
import com.pjsk.toolbox.data.card.CardAttr
import com.pjsk.toolbox.data.card.CardQuery
import com.pjsk.toolbox.data.card.CardSort
import com.pjsk.toolbox.data.card.CardUnit
import com.pjsk.toolbox.data.card.FALLBACK_RARITY_CAPS
import com.pjsk.toolbox.data.card.RarityCap
import com.pjsk.toolbox.data.card.belongsToUnit
import com.pjsk.toolbox.data.card.cardQueryFromSaveText
import com.pjsk.toolbox.data.card.toSaveText
import com.pjsk.toolbox.data.card.maxTotal
import com.pjsk.toolbox.data.card.normalMaxLevel
import com.pjsk.toolbox.data.card.parseCard
import com.pjsk.toolbox.data.card.PjskCharacter
import com.pjsk.toolbox.data.card.parseCharacter
import com.pjsk.toolbox.data.card.parseSkill
import com.pjsk.toolbox.data.card.filterCards
import com.pjsk.toolbox.data.card.trainedMaxLevel
import com.pjsk.toolbox.data.db.MasterRowEntity
import com.pjsk.toolbox.data.home.HomeMusic
import com.pjsk.toolbox.data.music.LyricsIndexEntry
import com.pjsk.toolbox.data.music.MusicCategory
import com.pjsk.toolbox.data.music.MusicDifficultyKind
import com.pjsk.toolbox.data.music.MusicSingerNames
import com.pjsk.toolbox.data.music.MusicUnitTag
import com.pjsk.toolbox.data.music.SongQuery
import com.pjsk.toolbox.data.music.SongSort
import com.pjsk.toolbox.data.music.TIME_UNSET_MS
import com.pjsk.toolbox.data.music.displayDurationMs
import com.pjsk.toolbox.data.music.displayPositionMs
import com.pjsk.toolbox.data.music.fillerMsOf
import com.pjsk.toolbox.data.music.formatClock
import com.pjsk.toolbox.data.music.fractionOf
import com.pjsk.toolbox.data.music.positionOfFraction
import com.pjsk.toolbox.data.music.playerPositionMs
import com.pjsk.toolbox.data.music.buildCharacterSongIds
import com.pjsk.toolbox.data.music.buildDefaultVocals
import com.pjsk.toolbox.data.music.buildMusicDetail
import com.pjsk.toolbox.data.music.buildSongCategoryCards
import com.pjsk.toolbox.data.music.filterSongs
import com.pjsk.toolbox.data.music.filterSongsBySingers
import com.pjsk.toolbox.data.music.sortSongs
import com.pjsk.toolbox.data.music.unitMembers
import com.pjsk.toolbox.data.music.lyricsStateLabel
import com.pjsk.toolbox.data.music.noLyricsReasonLabel
import com.pjsk.toolbox.data.music.parseLyricsDocument
import com.pjsk.toolbox.data.music.parseLyricsIndex
import com.pjsk.toolbox.data.music.parsePerformerId
import com.pjsk.toolbox.data.music.releaseConditionLabel
import com.pjsk.toolbox.data.music.renditionDisplayLabel
import com.pjsk.toolbox.data.music.songQueryFromSaveText
import com.pjsk.toolbox.data.music.toSaveText
import com.pjsk.toolbox.data.music.vocalTypeLabel
import com.pjsk.toolbox.data.home.HomeMusicVocal
import com.pjsk.toolbox.data.player.CustomTimerAction
import com.pjsk.toolbox.data.player.MAX_CUSTOM_MINUTES
import com.pjsk.toolbox.data.player.MIN_CUSTOM_MINUTES
import com.pjsk.toolbox.data.player.MusicTrack
import com.pjsk.toolbox.data.player.PlayMode
import com.pjsk.toolbox.data.player.PlaybackState
import com.pjsk.toolbox.data.player.RowPlayState
import com.pjsk.toolbox.data.player.SleepTimerAction
import com.pjsk.toolbox.data.player.SleepTimerOption
import com.pjsk.toolbox.data.player.SleepTimerState
import com.pjsk.toolbox.data.player.VersionPlayAction
import com.pjsk.toolbox.data.player.buildQueueFrom
import com.pjsk.toolbox.data.player.customTimerClickAction
import com.pjsk.toolbox.data.player.formatTimerClock
import com.pjsk.toolbox.data.player.isPresetSleepMinutes
import com.pjsk.toolbox.data.player.parseCustomSleepMinutes
import com.pjsk.toolbox.data.player.musicIdOfMediaId
import com.pjsk.toolbox.data.player.rowPlayState
import com.pjsk.toolbox.data.player.sleepTimerClickAction
import com.pjsk.toolbox.data.player.sleepTimerOptionOf
import com.pjsk.toolbox.data.player.sleepTimerRemainingMs
import com.pjsk.toolbox.data.player.versionPlayAction
import com.pjsk.toolbox.data.player.withVersionAt
import com.pjsk.toolbox.data.story.EventStoryUnit
import com.pjsk.toolbox.data.story.StoryQuery
import com.pjsk.toolbox.data.story.StoryRow
import com.pjsk.toolbox.data.story.StorySort
import com.pjsk.toolbox.data.story.buildStoryUnitOptions
import com.pjsk.toolbox.data.story.eventStoryUnitKeys
import com.pjsk.toolbox.data.story.filterStoryRows
import com.pjsk.toolbox.data.story.sortStoryRows
import com.pjsk.toolbox.data.story.storyQueryFromSaveText
import com.pjsk.toolbox.data.story.toSaveText
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.data.sync.RowProjectors
import com.pjsk.toolbox.data.sync.TableSchemas
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import com.pjsk.toolbox.util.decodeFavoriteSongs
import com.pjsk.toolbox.util.encodeFavoriteSongs
import com.pjsk.toolbox.util.moveMatchesFirst
import com.pjsk.toolbox.util.parseHexColor
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.DataModule
import com.pjsk.toolbox.data.remote.MasterRepository
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.remote.TableCatalog
import com.pjsk.toolbox.data.story.StoryAssetKind
import com.pjsk.toolbox.data.sync.DatapackImportOrder
import com.pjsk.toolbox.data.sync.SyncDecision
import com.pjsk.toolbox.data.sync.SyncManager
import java.io.File

/**
 * 离线逻辑自检。
 *
 * 为什么需要它：这个 App 的卡牌逻辑有三处「猜错就会静默出错」的地方 ——
 *  1) 裁剪器把 35 MB 的 `cardParameters` 压成 `p1/p2/p3` 时，等级顺序不能错；
 *  2) 解析器要同时吃「裁剪后的行」和「官方原始行」，两者结果必须一致；
 *  3) 特训前/特训后的取值靠 `cardRarities.json` 的等级上限，算错一个下标就全错。
 * 这三处都不会报错、只会显示错误数字，而且没有真机根本看不出来。
 * 所以这里用**真实抓下来的数据夹具**跑一遍，而不是手写假数据（假数据会顺着假设走）。
 *
 * 运行：tools/logic-check.ps1
 */
private val json = Json { isLenient = true; ignoreUnknownKeys = true }

private var failures = 0
private var checks = 0

private fun check(name: String, condition: Boolean, detail: String = "") {
    checks++
    if (condition) {
        println("  [PASS] $name")
    } else {
        failures++
        println("  [FAIL] $name${if (detail.isNotEmpty()) "  →  $detail" else ""}")
    }
}

private fun <T> checkEq(name: String, actual: T, expected: T) =
    check(name, actual == expected, "实际=$actual  期望=$expected")

private fun section(title: String) = println("\n── $title ──")

fun main(args: Array<String>) {
    val fixtures = File(args.firstOrNull() ?: "tools/logic-check/fixtures")
    require(fixtures.isDirectory) { "找不到夹具目录：${fixtures.absolutePath}（先跑 make-fixtures.mjs）" }

    val cardRaw = readObject(File(fixtures, "card1-raw.json"))
    val skillRaw = readObject(File(fixtures, "skill1-raw.json"))
    val characterRaw = readObject(File(fixtures, "character1-raw.json"))

    // ─────────────────────────────────────────────────────────
    section("1. 卡牌裁剪器（RowProjectors.CARDS）")
    // ─────────────────────────────────────────────────────────
    val projected = RowProjectors.CARDS.project(cardRaw)
    check("裁剪器不为 null", projected != null)
    val p = projected ?: error("裁剪器返回 null")

    val p1 = p.intList("p1")
    val p2 = p.intList("p2")
    val p3 = p.intList("p3")
    checkEq("表现力序列长度 = 20（1★ 卡的等级数）", p1.size, 20)
    checkEq("技巧序列长度 = 20", p2.size, 20)
    checkEq("体能序列长度 = 20", p3.size, 20)
    checkEq("maxLevel = 20", p.intValue("maxLevel"), 20)
    checkEq("hasTrained = false（1★ 卡没有特训后卡面）", p.boolValue("hasTrained"), false)
    checkEq("trainBonus = [0,0,0]（1★ 卡没有特训加成）", p.intList("trainBonus"), listOf(0, 0, 0))

    // 序列必须按等级升序：1 级最小、20 级最大
    check("表现力序列单调递增", p1.zipWithNext().all { (a, b) -> b > a }, "序列=$p1")
    checkEq("1 级表现力 = 1065（对照抓取到的原始数据）", p1.firstOrNull(), 1065)
    checkEq("20 级表现力 = 2663（对照抓取到的原始数据）", p1.lastOrNull(), 2663)

    // 官方原名必须保留，否则通用表浏览器与 AssetUrls 的通用助手会失效
    checkEq("保留官方字段 assetbundleName", p.stringValue("assetbundleName"), "res001_no001")
    checkEq("保留官方字段 cardRarityType", p.stringValue("cardRarityType"), "rarity_1")
    // prefix 是卡牌在**日服**里的显示名。丢了它的后果：简中服还没更新的新卡
    // 既没有中文名、也没有日文原名，界面上只能显示 "#1465"（真机上就是这么发现的）。
    checkEq("保留官方字段 prefix（卡牌显示名）", p.stringValue("prefix"), "クールだけど友達想い")

    // ── 幂等性（这一条是真机上踩出来的）──
    // 内置数据快照里存的就是**裁剪后**的行，App 导入时会再走一次同一个裁剪器。
    // 没有幂等保护时，第二次裁剪找不到 cardParameters，会把 p1/p2/p3 全写成空数组，
    // 表现是「综合力显示 0、觉醒图不显示」，且不报任何错。
    val reprojected = RowProjectors.CARDS.project(p)
    checkEq("裁剪器幂等：对已裁剪的行再裁一次结果不变", reprojected, p)
    checkEq("二次裁剪后 p1 不能变空", reprojected?.intList("p1")?.size, 20)
    checkEq("二次裁剪后 hasTrained 不能丢", reprojected?.boolValue("hasTrained"), false)

    val compactBytes = p.toString().length
    val rawBytes = cardRaw.toString().length
    println("  · 体积：原始 $rawBytes 字符 → 裁剪后 $compactBytes 字符" +
        "（压掉 ${"%.1f".format(100.0 * (1 - compactBytes.toDouble() / rawBytes))}%）")

    // ─────────────────────────────────────────────────────────
    section("2. 解析器要同时吃「裁剪行」和「官方原始行」")
    // ─────────────────────────────────────────────────────────
    val rowFromProjected = MasterRowEntity(
        tableName = "cards.json",
        id = 1,
        name = "クールだけど友達想い",
        nameZh = "虽然酷但是很重视朋友",
        sortValue = 1601391600000L,
        data = p.toString(),
    )
    val rowFromRaw = rowFromProjected.copy(data = cardRaw.toString())

    val cardFromProjected = parseCard(rowFromProjected)
    val cardFromRaw = parseCard(rowFromRaw)

    check("裁剪行能解析出卡牌", cardFromProjected != null)
    check("原始行能解析出卡牌（旧数据兼容）", cardFromRaw != null)
    checkEq("两种行解析出的卡牌完全相同", cardFromRaw, cardFromProjected)

    val card = cardFromProjected ?: error("解析失败")
    checkEq("中文名优先", card.displayName, "虽然酷但是很重视朋友")
    checkEq("稀有度星级", card.stars, 1)
    checkEq("属性解析", card.attr, CardAttr.COOL)
    checkEq("角色 id", card.characterId, 1)
    checkEq("特训后卡面标记", card.hasTrained, false)

    // ─────────────────────────────────────────────────────────
    section("3. 特训加成路径（把 1★ 样本改造成 3★ 来验证）")
    // ─────────────────────────────────────────────────────────
    val fakeTrained = buildJsonObject {
        cardRaw.forEach { (key, value) -> put(key, value) }
        put("cardRarityType", "rarity_3")
        put("specialTrainingCosts", buildJsonArray { add(buildJsonObject { put("id", 1) }) })
        put("specialTrainingPower1BonusFixed", 100)
        put("specialTrainingPower2BonusFixed", 200)
        put("specialTrainingPower3BonusFixed", 300)
    }
    val trainedCard = parseCard(
        rowFromProjected.copy(
            data = RowProjectors.CARDS.project(fakeTrained)?.toString() ?: error("裁剪失败"),
        ),
    ) ?: error("解析失败")

    checkEq("specialTrainingCosts 非空 → hasTrained = true", trainedCard.hasTrained, true)
    checkEq("特训加成解析", trainedCard.trainBonus, listOf(100, 200, 300))
    checkEq(
        "不特训时不吃加成",
        trainedCard.statsAt(20, applyTrainingBonus = false).vocal,
        trainedCard.vocal[19],
    )
    checkEq(
        "特训后加上加成",
        trainedCard.statsAt(20, applyTrainingBonus = true).vocal,
        trainedCard.vocal[19] + 100,
    )
    checkEq(
        "综合力 = 三项之和（数据里没有 param4，必须自己加）",
        trainedCard.statsAt(20).total,
        trainedCard.vocal[19] + trainedCard.dance[19] + trainedCard.visual[19],
    )

    // ── 三态里的第三态：「出厂即特训后」的卡（真机实测例：id=1462 联动限定卡）──
    // 它的 card_normal.webp 返回 404，只有 card_after_training.webp。
    // 若按「有没有特训消耗」两态判断，会去请求一个必然 404 的 URL，界面就是破图。
    val fakeTrainedOnly = buildJsonObject {
        cardRaw.forEach { (key, value) -> put(key, value) }
        put("cardRarityType", "rarity_4")
        put("specialTrainingCosts", buildJsonArray { })          // 没有特训消耗
        put("initialSpecialTrainingStatus", "done")               // 但已经是特训后状态
    }
    val trainedOnlyCard = parseCard(
        rowFromProjected.copy(
            data = RowProjectors.CARDS.project(fakeTrainedOnly)?.toString() ?: error("裁剪失败"),
        ),
    ) ?: error("解析失败")

    checkEq("出厂即特训后 → trainedOnly = true", trainedOnlyCard.trainedOnly, true)
    checkEq("出厂即特训后 → hasTrained = false（因为没有特训消耗）", trainedOnlyCard.hasTrained, false)
    checkEq("出厂即特训后 → 卡面方案 = 只用觉醒图", trainedOnlyCard.artPlan, CardArtPlan.TRAINED_ONLY)
    checkEq("普通可特训卡 → 卡面方案 = 左右两半", trainedCard.artPlan, CardArtPlan.BOTH)
    checkEq("不可特训的卡 → 卡面方案 = 只用未觉醒图", card.artPlan, CardArtPlan.NORMAL_ONLY)

    // ─────────────────────────────────────────────────────────
    section("4. 等级上限：内置表 vs 权威的 cardRarities.json")
    // ─────────────────────────────────────────────────────────
    val rarityRows = json.parseToJsonElement(File(fixtures, "cardRarities.json").readText()) as JsonArray
    val capsFromFile = rarityRows.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val key = obj.stringValue("cardRarityType") ?: return@mapNotNull null
        val maxLevel = obj.intValue("maxLevel") ?: return@mapNotNull null
        key to RarityCap(maxLevel, obj.intValue("trainingMaxLevel"))
    }.toMap()

    checkEq("cardRarities.json 有 5 个稀有度", capsFromFile.size, 5)
    checkEq("内置的兜底等级上限与官方表完全一致", FALLBACK_RARITY_CAPS, capsFromFile)
    checkEq("rarity_3 特训前上限 = 40", capsFromFile["rarity_3"]?.maxLevel, 40)
    checkEq("rarity_3 特训后上限 = 50", capsFromFile["rarity_3"]?.trainingMaxLevel, 50)
    checkEq("rarity_4 特训前上限 = 50", capsFromFile["rarity_4"]?.maxLevel, 50)
    checkEq("rarity_4 特训后上限 = 60", capsFromFile["rarity_4"]?.trainingMaxLevel, 60)
    checkEq("生日卡没有特训后上限", capsFromFile["rarity_birthday"]?.trainingMaxLevel, null)

    val caps = FALLBACK_RARITY_CAPS + capsFromFile
    checkEq("满级综合力（3★ 按特训后 50 级 + 加成算）", trainedCard.maxTotal(caps), trainedCard.statsAt(50, true).total)
    checkEq("特训前满级等级", trainedCard.normalMaxLevel(caps), 40)
    checkEq("特训后满级等级", trainedCard.trainedMaxLevel(caps), 50)
    checkEq("不可特训的卡没有特训后等级", card.trainedMaxLevel(caps), null)

    // ─────────────────────────────────────────────────────────
    section("5. 技能描述里的占位符替换")
    // ─────────────────────────────────────────────────────────
    val skill = parseSkill(
        MasterRowEntity("skills.json", 1, null, null, 1L, skillRaw.toString()),
    )
    check("技能能解析", skill != null)
    val s = skill ?: error("解析失败")
    checkEq("技能最大等级 = 4", s.maxLevel, 4)
    checkEq(
        "1 级：{{1;d}}→5  {{1;v}}→20",
        s.describe(1, preferChinese = false),
        "5秒間  スコアが20%UPする",
    )
    checkEq(
        "4 级：{{1;v}}→40",
        s.describe(4, preferChinese = false),
        "5秒間  スコアが40%UPする",
    )
    // 行为已变更：过去「算不出的占位符原样保留」，结果界面上出现 `{{54,103;r}}` 这种生文本。
    // 现在统一换成 ※ 并附一句说明 —— 把「不知道」讲清楚，而不是甩大括号。
    val unresolvedText = s.copy(description = "角色等级{{2;r}}时生效").describe(1, preferChinese = false)
    check(
        "算不出的占位符换成 ※，不残留 {{…}}",
        unresolvedText != null && !unresolvedText.contains("{{") && unresolvedText.contains("※"),
        "实际=$unresolvedText",
    )
    check(
        "并附上「条件数值」说明",
        unresolvedText?.contains("条件数值") == true,
        "实际=$unresolvedText",
    )

    // ─────────────────────────────────────────────────────────
    section("6. 角色：姓 + 名 的拼接（简中服就是中文）")
    // ─────────────────────────────────────────────────────────
    val character = parseCharacter(
        MasterRowEntity("gameCharacters.json", 1, "星乃 一歌", "星乃 一歌", 1L, characterRaw.toString()),
    )
    check("角色能解析", character != null)
    checkEq("组合 key", character?.unitKey, "light_sound")
    checkEq("组合显示名", character?.unitLabel, "Leo/need")
    checkEq(
        "官方 firstName(姓) + givenName(名) 拼出的名字",
        listOfNotNull(characterRaw.stringValue("firstName"), characterRaw.stringValue("givenName"))
            .joinToString(" "),
        "星乃 一歌",
    )

    // ─────────────────────────────────────────────────────────
    section("7. 筛选与排序")
    // ─────────────────────────────────────────────────────────
    // 每张样本给不同的 characterId —— 否则四个样本共用同一个角色，
    // 「按组合筛选」会全部命中，断言就测不出任何东西（第一版就是这么写错的）
    val samples = listOf(
        card.copy(id = 1, characterId = 1, attr = CardAttr.COOL, rarityKey = "rarity_1"),
        card.copy(id = 2, characterId = 2, attr = CardAttr.CUTE, rarityKey = "rarity_3", hasTrained = true),
        card.copy(id = 3, characterId = 3, attr = CardAttr.COOL, rarityKey = "rarity_4", hasTrained = true),
        card.copy(id = 4, characterId = 4, attr = CardAttr.PURE, rarityKey = "rarity_4", hasTrained = true),
    )
    val unitMap = mapOf(1 to "light_sound", 2 to "idol", 3 to "street", 4 to "theme_park")
    checkEq("不筛选时返回全部", filterCards(samples, CardQuery(), caps).size, 4)
    checkEq(
        "按属性筛选",
        filterCards(samples, CardQuery(attrs = setOf(CardAttr.COOL)), caps).map { it.id },
        listOf(1, 3),
    )
    checkEq(
        "按稀有度筛选",
        filterCards(samples, CardQuery(rarityKeys = setOf("rarity_4")), caps).map { it.id },
        listOf(3, 4),
    )
    checkEq(
        "按 id 搜索（直接输编号也能命中）",
        filterCards(samples, CardQuery(search = "3"), caps).map { it.id },
        listOf(3),
    )
    checkEq(
        "按中文名搜索",
        filterCards(samples, CardQuery(search = "重视朋友"), caps).size,
        4,
    )
    checkEq(
        "按稀有度排序（同级按发布时间倒序）",
        filterCards(samples, CardQuery(sort = CardSort.RARITY_DESC), caps)
            .map { it.rarityKey },
        listOf("rarity_4", "rarity_4", "rarity_3", "rarity_1"),
    )
    checkEq(
        "按组合筛选（characterId 都不在映射里 → 全部落选）",
        filterCards(samples, CardQuery(units = setOf(CardUnit.LEO_NEED)), caps).size,
        0,
    )
    checkEq(
        "按角色筛选",
        filterCards(samples, CardQuery(characterIds = setOf(3)), caps).map { it.id },
        listOf(3),
    )
    checkEq(
        "组合映射命中时能筛出来",
        filterCards(
            samples,
            CardQuery(units = setOf(CardUnit.LEO_NEED)),
            caps,
            characterUnits = unitMap,
        ).map { it.id },
        listOf(1),
    )
    checkEq(
        "组合筛选 + 属性筛选叠加（跨维取「且」）",
        filterCards(
            samples,
            CardQuery(units = setOf(CardUnit.VIVID_BAD_SQUAD), attrs = setOf(CardAttr.COOL)),
            caps,
            characterUnits = unitMap,
        ).map { it.id },
        listOf(3),
    )

    // ── 多选语义（2026-09-14 新增：四维从单选改成多选）──
    checkEq(
        "属性多选 = 同维取「或」",
        filterCards(samples, CardQuery(attrs = setOf(CardAttr.COOL, CardAttr.CUTE)), caps)
            .map { it.id }.sorted(),
        listOf(1, 2, 3),
    )
    checkEq(
        "角色多选 = 同维取「或」",
        filterCards(samples, CardQuery(characterIds = setOf(1, 2)), caps)
            .map { it.id }.sorted(),
        listOf(1, 2),
    )
    checkEq(
        "组合与角色是**两个独立维度**（同时选中要同时满足）",
        filterCards(
            samples,
            CardQuery(units = setOf(CardUnit.LEO_NEED), characterIds = setOf(9)),
            caps,
            characterUnits = unitMap,
        ).size,
        0,
    )
    checkEq(
        "空集合 = 该维不筛选",
        filterCards(samples, CardQuery(), caps).size,
        samples.size,
    )
    checkEq(
        "activeCount 数的是「启用了几维」而不是「选了几项」",
        CardQuery(
            attrs = setOf(CardAttr.COOL, CardAttr.CUTE),
            characterIds = setOf(1, 2, 3),
        ).activeCount,
        2,
    )

    // ── 虚拟歌手（2026-09-14 修复）──
    // 真机 bug：卡牌列表里「VIRTUAL SINGER」这一组是空的，角色筛选面板里也**找不到
    // 初音未来**。根因是 `gameCharacters.unit` 里虚拟歌手的取值是 `piapro`，
    // 而枚举 key 早先写成了字面量 `virtual_singer`。
    checkEq(
        "虚拟歌手的组合 key 是 piapro（不是 virtual_singer）",
        CardUnit.VIRTUAL_SINGER.key,
        "piapro",
    )
    checkEq(
        "写错的旧别名仍能解析（避免别处按字面量查到 null）",
        CardUnit.of("virtual_singer"),
        CardUnit.VIRTUAL_SINGER,
    )
    checkEq("piapro 解析成虚拟歌手", CardUnit.of("piapro"), CardUnit.VIRTUAL_SINGER)
    checkEq(
        "虚拟歌手角色的 unitLabel 不再是 null（角色选择里能归组）",
        parseCharacter(
            MasterRowEntity(
                "gameCharacters.json",
                21,
                "初音ミク",
                "初音未来",
                1L,
                buildJsonObject {
                    cardRaw.forEach { (key, value) -> put(key, value) }
                    put("id", 21)
                    put("unit", "piapro")
                }.toString(),
            ),
        )?.unitLabel,
        "VIRTUAL SINGER",
    )

    // 借调规则：虚拟歌手的卡按 supportUnit 归属到各团，**允许一张卡同时属于两个组合**
    val vsCard = card.copy(id = 100, characterId = 21, supportUnit = "light_sound")
    val pureVsCard = card.copy(id = 101, characterId = 21, supportUnit = null)
    val vsSamples = samples + listOf(vsCard, pureVsCard)
    val vsUnitMap = unitMap + (21 to "piapro")
    checkEq("虚拟歌手卡归属 Leo/need（借调）", vsCard.belongsToUnit(CardUnit.LEO_NEED, "piapro"), true)
    checkEq("虚拟歌手卡也仍属于 VIRTUAL SINGER", vsCard.belongsToUnit(CardUnit.VIRTUAL_SINGER, "piapro"), true)
    checkEq("supportUnit=none 的纯虚拟歌手卡不属于任何团", pureVsCard.belongsToUnit(CardUnit.LEO_NEED, "piapro"), false)
    checkEq("真人角色的卡不会被借调到别的团", vsCard.belongsToUnit(CardUnit.NIIGO, "light_sound"), false)
    checkEq(
        "按组合=VIRTUAL SINGER 筛选 → 两张虚拟歌手的卡都命中",
        filterCards(vsSamples, CardQuery(units = setOf(CardUnit.VIRTUAL_SINGER)), caps, vsUnitMap)
            .map { it.id }.sorted(),
        listOf(100, 101),
    )
    checkEq(
        "按组合=Leo/need 筛选 → 真人卡 + 借调过来的虚拟歌手卡",
        filterCards(vsSamples, CardQuery(units = setOf(CardUnit.LEO_NEED)), caps, vsUnitMap)
            .map { it.id }.sorted(),
        listOf(1, 100),
    )
    checkEq(
        "组合=Leo/need + 角色=初音未来 → 只剩借调的那张",
        filterCards(
            vsSamples,
            CardQuery(units = setOf(CardUnit.LEO_NEED), characterIds = setOf(21)),
            caps,
            vsUnitMap,
        ).map { it.id },
        listOf(100),
    )

    // ─────────────────────────────────────────────────────────
    section("7b. 筛选条件的跨界面保存：toSaveText / fromSaveText 必须往返一致")
    // ─────────────────────────────────────────────────────────
    // 为什么要有这一节：真机上被用户抓到的 bug —— 卡牌图鉴筛完之后点进一张卡，
    // 返回时**筛选条件全没了**。根因是筛选条件用了普通 `remember`
    // （导航到下一层会销毁 composition），修法是 `rememberSaveable` + 一对编解码函数。
    // 编解码一旦写错（字段顺序错位、枚举名存不下来），现象是「筛选静默失效」，
    // 不会报错 —— 正好是自检该管的事。
    checkEq(
        "空条件往返一致",
        cardQueryFromSaveText(CardQuery().toSaveText()),
        CardQuery(),
    )
    val fullQuery = CardQuery(
        search = "初音",
        rarityKeys = setOf("rarity_4", "rarity_birthday"),
        attrs = setOf(CardAttr.COOL, CardAttr.PURE),
        characterIds = setOf(21, 1, 26),
        units = setOf(CardUnit.VIRTUAL_SINGER, CardUnit.LEO_NEED),
        sort = CardSort.POWER_DESC,
    )
    checkEq("满条件往返一致", cardQueryFromSaveText(fullQuery.toSaveText()), fullQuery)
    checkEq(
        "往返后仍能筛出同样的结果（端到端，不只是字段相等）",
        filterCards(vsSamples, cardQueryFromSaveText(fullQuery.toSaveText()), caps, vsUnitMap)
            .map { it.id }.sorted(),
        filterCards(vsSamples, fullQuery, caps, vsUnitMap).map { it.id }.sorted(),
    )
    checkEq(
        "搜索词里混进字段分隔符也不会串位（自由文本放在最后一个字段）",
        cardQueryFromSaveText(
            fullQuery.copy(search = "a\u0001b|rarity_1").toSaveText(),
        ).search,
        "a\u0001b|rarity_1",
    )
    checkEq(
        "搜索词里混进逗号也不会串位（字段内分隔符只影响集合字段）",
        cardQueryFromSaveText(fullQuery.copy(search = "1,2,3").toSaveText()).search,
        "1,2,3",
    )
    checkEq(
        "存下来的文本里没有换行（能安全塞进 Bundle / 后续想落盘也行）",
        fullQuery.toSaveText().contains('\n'),
        false,
    )
    // 容错：以后枚举改名、删项、加减字段，旧文本必须只是「少一个筛选项」，不能崩
    // ⚠️ 存进文本的是枚举的 **name（NIIGO）**，不是它的 key（school_refusal）——
    // 第一版这里替换错了 key，于是断言失败，反倒是自检先替我抓到了测试本身的错。
    checkEq(
        "不认识的枚举名被丢掉，其余照常还原（不抛异常）",
        cardQueryFromSaveText(
            CardQuery(units = setOf(CardUnit.NIIGO), sort = CardSort.RARITY_DESC).toSaveText()
                .replace("NIIGO", "UNIT_THAT_NO_LONGER_EXISTS"),
        ).units,
        emptySet<CardUnit>(),
    )
    checkEq(
        "不认识的排序名退回默认（不抛异常）",
        cardQueryFromSaveText(
            CardQuery(sort = CardSort.RARITY_DESC).toSaveText().replace("RARITY_DESC", "NOPE"),
        ).sort,
        CardSort.RELEASE_DESC,
    )
    checkEq(
        "字段数对不上（读到旧版本的数据）→ 退回空条件，不崩",
        cardQueryFromSaveText("rarity_4\u0001COOL"),
        CardQuery(),
    )
    checkEq("完全空文本也不崩", cardQueryFromSaveText(""), CardQuery())
    checkEq(
        "不认识的 id / 稀有度 key 被丢掉",
        cardQueryFromSaveText(CardQuery(characterIds = setOf(21)).toSaveText().replace("21", "abc"))
            .characterIds,
        emptySet<Int>(),
    )

    // ─────────────────────────────────────────────────────────
    section("8. 「没有 id 字段」的表：配置的主键必须唯一")
    // 这一节防的是一类**真机上已经出现过**的 bug：这些表没有 `id` 字段，
    // 用默认主键会导致整张表导入 0 行（真机日志里就出现了
    // 「unitProfiles.json：导入 0 行」「characterProfiles.json：导入 0 行」）。
    // 而如果兜底主键选得不唯一，写入用 REPLACE 会静默覆盖掉重复行 —— 同样查不出来。
    // 实测过的坑：eventMusics 的 seq 全是 1（136 行压成 1 行）、
    // cardCostume3ds 的 cardId 只有 705 个唯一值（2345 行丢 1640 行）。
    val noIdTables = listOf(
        "unitProfiles.json",
        "characterProfiles.json",
        "eventMusics.json",
        "cardExchangeResources.json",
        "cardRarities.json",
        "cardCostume3ds.json",
    )
    for (file in noIdTables) {
        val rows = json.parseToJsonElement(File(fixtures, "noid-$file").readText()) as JsonArray
        val schema = TableSchemas[file]
        val ids = rows.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            schema.idKeys.firstNotNullOfOrNull { key -> obj.intValue(key)?.toLong() }
        }
        checkEq(
            "$file：${rows.size} 行全部能取到主键（idKeys=${schema.idKeys}）",
            ids.size,
            rows.size,
        )
        checkEq("$file：主键无重复（不会触发 REPLACE 静默覆盖）", ids.toSet().size, rows.size)
        check(
            "$file：遵循「id 优先 + 兜底字段」的写法",
            schema.idKeys.first() == "id" && schema.idKeys.size > 1,
            "idKeys=${schema.idKeys}",
        )
    }

    // ─────────────────────────────────────────────────────────
    section("9. 技能描述里不许残留 {{…}} 占位符")
    // ─────────────────────────────────────────────────────────
    // 真机上发现的问题：22 条技能里有 3 条含复合占位符（`{{54,103;r}}` 这种依赖
    // 角色等级 / 组合人数 / 其他成员技能的形式），旧实现「替换不了就保留原文」，
    // 于是界面上直接出现 `{{53;d}}`、`{{54,103;r}}` —— 看起来就是坏了。
    // 这里直接拿**生成的快照**跑一遍所有技能的所有等级，确保一个都不剩。
    val packSkillsFile = File("app/src/main/assets/datapack/skills.json")
    if (packSkillsFile.isFile) {
        val packSkills = json.parseToJsonElement(packSkillsFile.readText()) as JsonArray
        var checked = 0
        var leftovers = 0
        for (element in packSkills) {
            val row = element as? JsonObject ?: continue
            val info = parseSkill(
                MasterRowEntity("skills.json", row.intValue("id") ?: 0, null, null, 0L, row.toString()),
            ) ?: continue
            for (level in 1..info.maxLevel.coerceAtLeast(1)) {
                val text = info.describe(level) ?: continue
                checked++
                if (text.contains("{{")) {
                    leftovers++
                    if (leftovers <= 3) {
                        println("      残留: skill=${info.id} lv=$level → ${text.take(90)}")
                    }
                }
            }
        }
        checkEq("内置快照里全部技能的全部等级都不残留 {{", leftovers, 0)
        println("  · 共检查 $checked 条技能描述")
    } else {
        println("  · 跳过（还没生成内置快照：先跑 tools/datapack/build.ps1）")
    }

    // ─────────────────────────────────────────────────────────
    section("10. 歌曲详情的数据组装（musicId 过滤 / 顺序 / 演唱者解析）")
    // ─────────────────────────────────────────────────────────
    // 这一节防的是「静默出错」的一类：少一个演唱版本、演唱者张冠李戴、难度顺序乱掉。
    // 特别是 **musicId 陷阱**：`musicVocals` / `musicDifficulties` / `musicTags` /
    // `musicCategories` 都有一列**全局自增 id**，它会和**别的曲子的 musicId** 撞车
    // （实测 `musicVocals` 的 id=644 那一行属于 musicId 258）。
    // 用 id 过滤会拿到别的曲子的数据，而且看起来完全正常 —— 只有断言能拦住。
    fun row(table: String, id: Int, name: String?, nameZh: String? = null, body: JsonObject) =
        MasterRowEntity(table, id, name, nameZh, id.toLong(), body.toString())

    val MUSIC_ID = 644

    val musicRow = row(
        "musics.json", MUSIC_ID, "生きる", "生",
        buildJsonObject {
            put("title", "生きる")
            put("pronunciation", "いきる")
            put("lyricist", "水野あつ")
            put("composer", "水野あつ")
            // 实测有一批曲子的编曲字段是字面量「-」（最多的一位"制作人"就是它，74 首）
            put("arranger", "-")
            put("assetbundleName", "jacket_s_644")
            put("publishedAt", 1753682400000L)
            put("releaseConditionId", 5)
            put("creatorArtistId", 149)
            put("secForMusicScoreMaker", 101)
            put("fillerSec", 7.928599834442139)
        },
    )

    // 难度：**故意打乱顺序、并给反向的 id**，验证排序看的是难度枚举而不是 id/seq
    val difficultyRows = listOf(
        row("musicDifficulties.json", 3220, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("musicDifficulty", "master"); put("playLevel", 27); put("totalNoteCount", 704) }),
        row("musicDifficulties.json", 3216, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("musicDifficulty", "easy"); put("playLevel", 5); put("totalNoteCount", 181) }),
        row("musicDifficulties.json", 4118, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("musicDifficulty", "append"); put("playLevel", 32); put("totalNoteCount", 1131) }),
        row("musicDifficulties.json", 3218, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("musicDifficulty", "hard"); put("playLevel", 16); put("totalNoteCount", 379) }),
        row("musicDifficulties.json", 3217, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("musicDifficulty", "normal"); put("playLevel", 11); put("totalNoteCount", 165) }),
        row("musicDifficulties.json", 3219, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("musicDifficulty", "expert"); put("playLevel", 22); put("totalNoteCount", 536) }),
        // ⚠️ 陷阱行：行主键 == 644，但 musicId 是 129（真机上就是这么撞的）
        row("musicDifficulties.json", MUSIC_ID, null, null, buildJsonObject { put("musicId", 129); put("musicDifficulty", "expert"); put("playLevel", 99); put("totalNoteCount", 999) }),
        // 无关曲子的行
        row("musicDifficulties.json", 99, null, null, buildJsonObject { put("musicId", 1); put("musicDifficulty", "easy"); put("playLevel", 1); put("totalNoteCount", 1) }),
    )

    // MEIKO / KAITO 是 `firstName` 缺失的（不是空串），所以 name 列就是「MEIKO」
    val characterRow = row(
        "gameCharacters.json", 25, "MEIKO", "MEIKO",
        buildJsonObject { put("givenName", "MEIKO"); put("unit", "piapro") },
    )
    // 其余角色的 name 列 = firstName + 空格 + givenName（`TableSchemas` 的 nameBuilder），
    // nameZh 为空表示简中服叠加层还没给这个角色补中文名
    val characterRow2 = row(
        "gameCharacters.json", 17, "宵崎 奏", null,
        buildJsonObject { put("firstName", "宵崎"); put("givenName", "奏"); put("unit", "school_refusal") },
    )
    val outsideRow = row("outsideCharacters.json", 9, "可不", null, buildJsonObject { put("name", "可不") })
    val names = MusicSingerNames(
        gameCharacters = listOfNotNull(parseCharacter(characterRow), parseCharacter(characterRow2))
            .associateBy { it.id },
        outsideCharacters = mapOf(9 to "可不"),
    )
    checkEq("演唱者夹具：游戏内角色解析出 2 个", names.gameCharacters.size, 2)

    // 音源：**故意让 seq 大的排在前面**，验证排序看 seq 而不是列表顺序
    val vocalRows = listOf(
        row(
            "musicVocals.json", 1663, "セカイver.", "「世界」ver.",
            buildJsonObject {
                put("musicId", MUSIC_ID)
                put("musicVocalType", "sekai")
                put("seq", 201)
                put("caption", "セカイver.")
                put("assetbundleName", "se_0644_01")
                put("characters", buildJsonArray {
                    add(buildJsonObject { put("characterType", "game_character"); put("characterId", 25) })
                    add(buildJsonObject { put("characterType", "game_character"); put("characterId", 17) })
                })
            },
        ),
        row(
            "musicVocals.json", 1201, "バーチャル・シンガーver.", null,
            buildJsonObject {
                put("musicId", MUSIC_ID)
                put("musicVocalType", "original_song")
                put("seq", 101)
                put("caption", "バーチャル・シンガーver.")
                put("assetbundleName", "vs_0644_01")
                put("characters", buildJsonArray {
                    add(buildJsonObject { put("characterType", "outside_character"); put("characterId", 9) })
                })
            },
        ),
        // ⚠️ 陷阱行：行主键 == 644，但 musicId 是 258
        row(
            "musicVocals.json", MUSIC_ID, "別の曲", null,
            buildJsonObject {
                put("musicId", 258)
                put("musicVocalType", "sekai")
                put("seq", 1)
                put("caption", "別の曲のver.")
                put("assetbundleName", "se_0258_01")
                put("characters", buildJsonArray {
                    add(buildJsonObject { put("characterType", "game_character"); put("characterId", 25) })
                })
            },
        ),
    )

    val tagRows = listOf(
        row("musicTags.json", 1167, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("musicTag", "all") }),
        row("musicTags.json", 1593, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("musicTag", "school_refusal") }),
        row("musicTags.json", MUSIC_ID, null, null, buildJsonObject { put("musicId", 270); put("musicTag", "idol") }),
    )
    val categoryRows = listOf(
        row("musicCategories.json", 687, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("musicCategoryName", "image") }),
        row("musicCategories.json", MUSIC_ID, null, null, buildJsonObject { put("musicId", 601); put("musicCategoryName", "mv") }),
    )
    val artistRows = listOf(row("musicArtists.json", 149, "水野あつ", null, buildJsonObject { put("name", "水野あつ") }))
    val originalRows = listOf(
        row("musicOriginals.json", 555, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("videoLink", "https://youtu.be/example") }),
    )
    val eventMusicRows = listOf(
        row("eventMusics.json", 1, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("eventId", 999) }),
        row("eventMusics.json", 2, null, null, buildJsonObject { put("musicId", 645); put("eventId", 1000) }),
    )
    val eventRows = listOf(
        row(
            "events.json", 999, "JPイベント名", "中文活动名",
            buildJsonObject {
                put("name", "JPイベント名")
                put("assetbundleName", "ev999")
                put("startAt", 1700000000000L)
                put("closedAt", 1700100000000L)
            },
        ),
    )
    val limitedRows = listOf(
        row("limitedTimeMusics.json", 1, null, null, buildJsonObject { put("musicId", MUSIC_ID); put("startAt", 1695999600000L); put("endAt", 1697079600000L) }),
    )

    val detail = buildMusicDetail(
        musicRow = musicRow,
        difficultyRows = difficultyRows,
        vocalRows = vocalRows,
        tagRows = tagRows,
        categoryRows = categoryRows,
        artistRows = artistRows,
        originalRows = originalRows,
        eventMusicRows = eventMusicRows,
        eventRows = eventRows,
        limitedTimeRows = limitedRows,
        names = names,
    )
    check("歌曲详情能组装出来", detail != null)
    val d = detail ?: error("歌曲详情组装失败")

    checkEq("曲目 id", d.id, MUSIC_ID)
    checkEq("中文曲名进 nameZh", d.nameZh, "生")
    checkEq("曲师名字从 musicArtists 解析", d.creatorArtistName, "水野あつ")
    checkEq("编曲为「-」时清成 null（不是字面横杠）", d.arranger, null)
    checkEq("作曲保留", d.composer, "水野あつ")

    // 难度
    checkEq("难度取到 6 档", d.difficulties.size, 6)
    checkEq(
        "难度顺序 = 游戏内顺序（不看 id / seq / 输入顺序）",
        d.difficulties.map { it.kind.abbr },
        listOf("EAS", "NOR", "HAR", "EXP", "MAS", "APP"),
    )
    checkEq("难度等级跟着一起排对", d.difficulties.map { it.playLevel }, listOf(5, 11, 16, 22, 27, 32))
    checkEq("NOTE 数跟着一起排对", d.difficulties.map { it.noteCount }, listOf(181, 165, 379, 536, 704, 1131))
    check(
        "★ 行主键和 musicId 撞车时，不能把别的曲子的难度混进来",
        d.difficulties.none { it.playLevel == 99 },
        "出现了 playLevel=99 —— 说明按行主键过滤了",
    )

    // 演唱版本
    checkEq("演唱版本取到 2 个", d.vocals.size, 2)
    checkEq("版本按 seq 排（原曲版在前）", d.vocals.map { it.assetbundleName }, listOf("vs_0644_01", "se_0644_01"))
    check(
        "★ 行主键和 musicId 撞车时，不能把别的曲子的音源混进来",
        d.vocals.none { it.assetbundleName == "se_0258_01" },
        "出现了 se_0258_01 —— 说明按行主键过滤了",
    )
    checkEq("原曲版说明走中文名兜底（没有中文就用日文）", d.vocals[0].caption, "バーチャル・シンガーver.")
    checkEq("世界版说明用简中服的中文", d.vocals[1].caption, "「世界」ver.")
    checkEq("版本徽章标签", d.vocals.map { it.typeLabel }, listOf("原曲", "世界"))
    checkEq("原曲版的演唱者是社外角色「可不」", d.vocals[0].singers.map { it.displayName }, listOf("可不"))
    checkEq("社外角色被标成不可点", d.vocals[0].singers[0].isGameCharacter, false)
    checkEq("世界版有 2 位游戏内演唱者", d.vocals[1].singers.map { it.displayName }, listOf("MEIKO", "宵崎 奏"))
    checkEq("游戏内角色被标成可点", d.vocals[1].singers[0].isGameCharacter, true)

    // 组合标签 / 分类
    checkEq("组合标签：all 被滤掉、只留 school_refusal", d.unitTags, listOf("school_refusal"))
    checkEq("分类：只取本曲的 image", d.categories.map { it.key }, listOf("image"))
    checkEq("分类显示名", d.categories.map { it.label }, listOf("静态图片"))

    // 相关活动 / 原曲 / 解锁条件
    checkEq("相关活动只取本曲那一个", d.relatedEvents.map { it.eventId }, listOf(999))
    checkEq("活动名中文优先", d.relatedEvents[0].name, "中文活动名")
    checkEq("原曲链接", d.originalVideoLink, "https://youtu.be/example")
    checkEq("解锁条件 + 期间限定后缀", d.releaseCondition, "从音乐商店购买（期间限定）")
    checkEq("播放器要用的剪辑时长（本页不显示）", d.secForMusicScoreMaker, 101)
    checkEq("播放器要用的跳过空白秒数", d.fillerSec, 7.928599834442139)

    // 名称语言
    checkEq("中文优先：主标题", d.displayNameFor(NameLanguage.CHINESE_FIRST), "生")
    checkEq("中文优先：不显示日文副标题", d.secondaryNameFor(NameLanguage.CHINESE_FIRST), null)
    checkEq("日文优先：主标题", d.displayNameFor(NameLanguage.JAPANESE), "生きる")
    checkEq("日文优先：中文做副标题", d.secondaryNameFor(NameLanguage.JAPANESE), "生")
    checkEq("双语对照：主中文副日文", d.displayNameFor(NameLanguage.BILINGUAL), "生")

    // 边界
    checkEq("认不出的解锁条件原样报 id，不编说法", releaseConditionLabel(99, false), "未知（id=99）")
    checkEq("非期间限定没有后缀", releaseConditionLabel(5, false), "从音乐商店购买")
    checkEq("认不出的分类 key 返回 null（不崩）", MusicCategory.of("no_such_category"), null)
    checkEq("认不出的难度 key 返回 null（不崩）", MusicDifficultyKind.of("ultimate"), null)
    checkEq("伴奏类型的标签", vocalTypeLabel("instrumental"), "伴奏")

    // 中文名和日文名相同时不该重复显示（叠加层可能写进同一个字符串）
    val sameNameRow = row(
        "musics.json", 7, "Tell Your World", "Tell Your World",
        buildJsonObject { put("title", "Tell Your World"); put("assetbundleName", "jacket_s_007") },
    )
    checkEq(
        "中文名 == 日文名时 nameZh 置空（避免详情页写两遍）",
        buildMusicDetail(
            musicRow = sameNameRow,
            difficultyRows = emptyList(), vocalRows = emptyList(), tagRows = emptyList(),
            categoryRows = emptyList(), artistRows = emptyList(), originalRows = emptyList(),
            eventMusicRows = emptyList(), eventRows = emptyList(), limitedTimeRows = emptyList(),
            names = names,
        )?.nameZh,
        null,
    )

    // ─────────────────────────────────────────────────────────
    section("11. 歌词解析（v3 行内翻译 / v4 译本平行数组 / 演唱者 id）")
    // ─────────────────────────────────────────────────────────
    // 这一节防的同样是「静默出错」：**线上同时有 v3 和 v4 两套 schema**
    // （实测抽查 30 首：29 首 v3、1 首 v4）。只写 v3 的解析器遇到 v4 不会报错，
    // 只会一行翻译都不显示 —— 而「没翻译」看起来就像"这首歌还没有中文"，查不出来。

    // 演唱者 id：实测格式是中文的「歌唱者-17」，尾部数字就是 gameCharacterId
    checkEq("performerId「歌唱者-17」→ 17", parsePerformerId("歌唱者-17"), 17)
    checkEq("performerId「歌唱者-01」→ 1（前导零）", parsePerformerId("歌唱者-01"), 1)
    checkEq("performerId 为 null → null", parsePerformerId(null), null)
    checkEq("performerId 没有数字 → null", parsePerformerId("歌唱者"), null)

    // 版本标签：数据里是英文，界面要中文；performers 可能是空的（实测 585 的 7 个全空）
    checkEq(
        "有演唱者时：中文 kind + 演唱者",
        renditionDisplayLabel("sekai", "SEKAI Version", listOf("宵崎奏", "MEIKO")),
        "SEKAI 版 · 宵崎奏、MEIKO",
    )
    checkEq(
        "没有演唱者时：退回标签里破折号后面那段（否则 5 个版本会同名）",
        renditionDisplayLabel("alternate", "Alt. Group Covers (Full) — Leo/need", emptyList()),
        "另一版（Leo/need）",
    )
    checkEq(
        "认不出的 kind：原样用标签",
        renditionDisplayLabel("weird_kind", "Something Version", emptyList()),
        "Something Version",
    )
    checkEq("状态的中文说明", lyricsStateLabel("game_only"), "仅游戏版歌词")
    check("无歌词原因有专门说法", noLyricsReasonLabel("catalog_instrumental").contains("纯器乐"))

    // ── v3：翻译在行内 `zh-CN` ──
    val v3Json = """
        {"version":3,"musicId":644,"revision":3,"updatedAt":"2026-08-23T12:02:07Z","state":"complete",
         "renditions":[{"key":"sekai","kind":"sekai","label":"SEKAI Version","availableVersions":["full","game"],
          "performers":[{"performerId":"歌唱者-17","name":"宵崎奏"},{"performerId":"歌唱者-25","name":"MEIKO"}],
          "full":{"version":{"kind":"sekai","label":"SEKAI Version"},"lines":[
            {"id":"full-000001","order":0,"japanese":"ちょっとばかり ","zh-CN":"哪怕只有一点点",
             "segments":[{"text":"ちょっとばかり ","performerIds":["歌唱者-20"],"ruby":[{"text":"ちょっとばかり "}]}],"trailingPerformerIds":[]},
            {"id":"full-000002","order":1,"japanese":"生きてみようかな ","zh-CN":"试着努力活下去看看吧",
             "segments":[{"text":"生きてみようかな ","performerIds":["歌唱者-20"],
              "ruby":[{"text":"生","reading":"い"},{"text":"きてみようかな "}]}],"trailingPerformerIds":[]},
            {"id":"full-000003","order":2,"japanese":"学校に行くのはもうやめた","zh-CN":"已经彻底不再去学校了",
             "stanzaBreakBefore":true,
             "segments":[{"text":"学校に行くのはもうやめた","performerIds":["歌唱者-17"],
              "ruby":[{"text":"学校","reading":"がっこう"}]}],"trailingPerformerIds":[]}]},
          "game":{"version":{"kind":"sekai","label":"SEKAI Version"},"lines":[
            {"id":"game-000001","order":0,"japanese":"ちょっとばかり ","zh-CN":"哪怕只有一点点",
             "segments":[{"text":"ちょっとばかり ","performerIds":["歌唱者-20"],"ruby":[]}],"trailingPerformerIds":[]}]},
          "relation":{"kind":"exact_projection"},
          "provenance":[{"component":"renditions/sekai/full_text","provider":"sekaipedia","title":"Ikiru",
            "revisionId":329768,"revisionUrl":"https://www.sekaipedia.org/wiki/Ikiru?oldid=329768",
            "licenseName":"CC BY-SA 4.0","licenseUrl":"https://creativecommons.org/licenses/by-sa/4.0/"}],
          "translationCredits":{"translation":"雪莹ちゃん"}}]}
    """.trimIndent()

    val v3 = parseLyricsDocument(v3Json, fallbackMusicId = 0)
    check("v3 文档能解析", v3 != null)
    val d3 = v3 ?: error("v3 解析失败")
    checkEq("v3: musicId / revision / state", Triple(d3.musicId, d3.revision, d3.state), Triple(644, 3, "complete"))
    checkEq("v3: rendition 数", d3.renditions.size, 1)
    val r3 = d3.renditions[0]
    checkEq("v3: 完整版与游戏版都在", Pair(r3.hasFull, r3.hasGame), Pair(true, true))
    checkEq("v3: 完整版行数", r3.full?.lines?.size, 3)
    checkEq("v3: 游戏版行数", r3.game?.lines?.size, 1)
    checkEq("v3: 行内翻译取到了", r3.full?.lines?.get(0)?.translation, "哪怕只有一点点")
    checkEq("v3: 第二行翻译", r3.full?.lines?.get(1)?.translation, "试着努力活下去看看吧")
    checkEq("v3: 注音解析（生 → い）", r3.full?.lines?.get(1)?.segments?.get(0)?.ruby?.get(0)?.reading, "い")
    checkEq("v3: hasRuby 判定", r3.full?.lines?.map { it.hasRuby }, listOf(false, true, true))
    checkEq("v3: 段落标记", r3.full?.lines?.map { it.stanzaBreakBefore }, listOf(false, false, true))
    checkEq("v3: 演唱者 id 解析（歌唱者-20 → 20）", r3.full?.lines?.get(0)?.performerIds, listOf(20))
    checkEq("v3: 第三行换人（17）", r3.full?.lines?.get(2)?.performerIds, listOf(17))
    checkEq("v3: 版本中文标签", r3.displayLabel, "SEKAI 版 · 宵崎奏、MEIKO")

    // ── 演唱者名字必须**按角色 id** 查，不能按「这一行里第几个」取 ──
    // 这是真实 bug 的回归断言：原来是 `performerNames.getOrNull(index)`，
    // 而 index 是"行内的第几个"、performerNames 是"整个版本的名字列表"，两者毫无关系 ——
    // 只唱一个人的行下标恒为 0，于是整页都显示成名单里的第一个人，
    // 头像（按 id 拿，是对的）和名字对不上。
    checkEq("v3: 演唱者对象带 id", r3.performers.map { it.id }, listOf(17, 25))
    checkEq("v3: 名字按 id 查（17 → 宵崎奏）", r3.nameById[17], "宵崎奏")
    checkEq("v3: 名字按 id 查（25 → MEIKO）", r3.nameById[25], "MEIKO")
    check(
        "★ 行内 id 不在名单里时不冒充别人（第 1 行是「歌唱者-20」，名单只有 17/25）",
        r3.nameById[20] == null,
        "按位置取会得到名单第一个「宵崎奏」，实际=${r3.nameById[20]}",
    )
    checkEq(
        "★ 单人行的名字是该演唱者自己（第 3 行 歌唱者-17 → 宵崎奏，不是名单第一个的巧合）",
        r3.full?.lines?.get(2)?.performerIds?.mapNotNull { r3.nameById[it] },
        listOf("宵崎奏"),
    )
    checkEq("performerNames 仍按数据顺序（版本标签要用）", r3.performerNames, listOf("宵崎奏", "MEIKO"))

    // 数据里若出现"只有名字、没有 id"的条目：名字仍要能显示在标签里，只是查不到 id
    val noIdJson = """
        {"version":3,"musicId":999,"revision":1,"state":"complete","renditions":[
          {"key":"k","kind":"sekai","label":"SEKAI Version","performers":[{"name":"某人"}],
           "full":{"version":{"kind":"sekai"},"lines":[
             {"id":"l1","order":0,"japanese":"あ","segments":[{"text":"あ","performerIds":["歌唱者-17"]}]}]}}]}
    """.trimIndent()
    val noIdDoc = parseLyricsDocument(noIdJson, 0)
    checkEq("缺 id 的演唱者仍保留名字", noIdDoc?.renditions?.first()?.performerNames, listOf("某人"))
    checkEq("缺 id 时不进 nameById（查不到就不显示，不猜）", noIdDoc?.renditions?.first()?.nameById?.size, 0)
    checkEq("v3: 来源条数", r3.credits.size, 1)
    checkEq("v3: 来源显示名", r3.credits[0].providerLabel, "Sekaipedia")
    checkEq("v3: 许可名（必须展示）", r3.credits[0].licenseName, "CC BY-SA 4.0")
    checkEq("v3: 译者署名（必须展示）", r3.translators, listOf("雪莹ちゃん"))

    // ── v4：行内没有 zh-CN，翻译在译本的平行数组里，**按下标对应** ──
    val v4Json = """
        {"version":4,"musicId":1,"revision":5,"updatedAt":"2026-08-25T04:38:43Z","state":"complete",
         "defaultTranslationEditionKey":"main",
         "translationEditions":[
           {"key":"main","label":"默认译本","renditions":[{"renditionKey":"vocaloid",
             "translationCredits":{"translation":"雪莹ちゃん"},
             "full":{"translations":["为了不遗忘那无形的心意","抹去了早已定型的排版","捕捉偶然轻哼出的一句旋律"]}}]},
           {"key":"testedition","label":"测试译本","renditions":[{"renditionKey":"vocaloid",
             "full":{"translations":["【测试】甲","【测试】乙","【测试】丙"]}}]}],
         "renditions":[{"key":"vocaloid","kind":"vocaloid","label":"VIRTUAL SINGER Version",
          "availableVersions":["full"],"performers":[],
          "full":{"version":{"kind":"vocaloid","label":"VIRTUAL SINGER Version"},"lines":[
            {"id":"full-000001","order":0,"japanese":"形のない気持ち忘れないように",
             "segments":[{"text":"形のない気持ち忘れないように","performerIds":[],"ruby":[{"text":"形","reading":"かたち"}]}],"trailingPerformerIds":[]},
            {"id":"full-000002","order":1,"japanese":"定型に沿って","segments":[],"trailingPerformerIds":[]},
            {"id":"full-000003","order":2,"japanese":"偶然口ずさんだメロディー","segments":[],"trailingPerformerIds":[]}]},
          "relation":{"kind":"none"},"provenance":[],
          "translationCredits":{"translation":"雪莹ちゃん"}}]}
    """.trimIndent()

    val v4 = parseLyricsDocument(v4Json, fallbackMusicId = 0)
    check("v4 文档能解析", v4 != null)
    val d4 = v4 ?: error("v4 解析失败")
    val r4 = d4.renditions.firstOrNull()
    check("v4: rendition 解析出来了", r4 != null)
    checkEq("v4: 行内没有 zh-CN，翻译从译本的平行数组按下标取", r4?.full?.lines?.get(0)?.translation, "为了不遗忘那无形的心意")
    checkEq("v4: 第二行翻译", r4?.full?.lines?.get(1)?.translation, "抹去了早已定型的排版")
    checkEq("v4: 第三行翻译", r4?.full?.lines?.get(2)?.translation, "捕捉偶然轻哼出的一句旋律")
    check(
        "★ v4: 用的是 defaultTranslationEditionKey 指定的译本，不是第一个之外的测试译本",
        r4?.full?.lines?.none { it.translation?.startsWith("【测试】") == true } == true,
        "实际=${r4?.full?.lines?.map { it.translation }}",
    )
    checkEq("v4: 没有 game 版本", r4?.hasGame, false)
    checkEq("v4: 日文原文照常解析", r4?.full?.lines?.get(0)?.japanese, "形のない気持ち忘れないように")
    checkEq("v4: 注音照常解析", r4?.full?.lines?.get(0)?.segments?.get(0)?.ruby?.get(0)?.reading, "かたち")
    checkEq("v4: 没有演唱者时不崩", r4?.full?.lines?.get(0)?.performerIds, emptyList<Int>())
    checkEq("v4: 译者署名来自 rendition 自己的 translationCredits", r4?.translators, listOf("雪莹ちゃん"))

    // 平行数组比正文短时：缺的那几行翻译为 null，**不能崩、也不能错位**
    val shortTranslations = v4Json.replace(
        "\"为了不遗忘那无形的心意\",\"抹去了早已定型的排版\",\"捕捉偶然轻哼出的一句旋律\"",
        "\"只有一行翻译\"",
    )
    val shortDoc = parseLyricsDocument(shortTranslations, 1)
    checkEq(
        "平行数组短于正文时：第一行取到、后面的为 null（不错位、不崩）",
        shortDoc?.renditions?.firstOrNull()?.full?.lines?.map { it.translation },
        listOf("只有一行翻译", null, null),
    )

    // ── 索引 ──
    val indexJson = """
        {"version":3,"songs":[
          {"musicId":1,"revision":5,"updatedAt":"2026-08-25T04:38:43Z","state":"complete",
           "title":{"ja-JP":"Tell Your World","zh-CN":"Tell Your World"},"availableVersions":["full"]},
          {"musicId":644,"revision":3,"updatedAt":"2026-08-23T12:02:07Z","state":"complete",
           "title":{"ja-JP":"生きる","zh-CN":"生"},"availableVersions":["full","game"]},
          {"musicId":162,"revision":1,"updatedAt":"2026-08-08T13:24:16Z","state":"satisfied_no_lyrics",
           "title":{"ja-JP":"エンドマークに希望と涙を添えて"},"noLyricsReason":"catalog_instrumental"}]}
    """.trimIndent()
    val entries = parseLyricsIndex(indexJson)
    checkEq("索引解析出 3 首", entries.size, 3)
    val e644 = entries.firstOrNull { it.musicId == 644 }
    checkEq("索引: 中文曲名", e644?.titleZh, "生")
    checkEq("索引: 日文曲名", e644?.titleJa, "生きる")
    checkEq("索引: revision", e644?.revision, 3)
    checkEq("索引: availableVersions", e644?.availableVersions, listOf("full", "game"))
    checkEq("索引: 有文档的曲子", e644?.hasDocument, true)
    checkEq(
        "索引: 无歌词的曲子不该去碰文档",
        entries.firstOrNull { it.musicId == 162 }?.hasDocument,
        false,
    )

    // ── 非法输入一律返回 null，不抛异常 ──
    checkEq("404 的纯文本正文 → null（不崩）", parseLyricsDocument("404 page not found", 1), null)
    checkEq("空字符串 → null", parseLyricsDocument("", 1), null)
    checkEq("缺 renditions 字段 → null", parseLyricsDocument("""{"version":3,"musicId":5}""", 5), null)
    checkEq("索引不是 JSON → 空列表", parseLyricsIndex("<html>not json</html>"), emptyList<LyricsIndexEntry>())
    checkEq(
        "多出未知字段要能容忍（数据会加字段）",
        parseLyricsDocument(v3Json.replace("\"state\":\"complete\"", "\"state\":\"complete\",\"futureKey\":123"), 0)?.state,
        "complete",
    )
    // 两个版本都没有正文的 rendition 要丢掉，否则界面上会出现一个点不开的空版本
    checkEq(
        "只有 performers、没有任何正文的 rendition 被丢弃",
        parseLyricsDocument(
            """{"version":3,"musicId":5,"revision":1,"state":"complete","renditions":[
                 {"key":"x","kind":"sekai","label":"L","performers":[{"performerId":"歌唱者-1","name":"A"}]}]}""",
            5,
        )?.renditions?.size,
        0,
    )

    // ─────────────────────────────────────────────────────────
    section("12. 歌曲筛选（组合/分类/搜索/排序）与分类卡片统计")
    // ─────────────────────────────────────────────────────────
    // 歌曲页的筛选从"常驻 chip"改成"右侧面板"，条件是跨界面保存的
    // —— 所以这里的编解码和卡牌那套一样，错了就是"筛选静默失效"。

    // 组合标签：musicTags 的 key 和 unitProfiles 的 key **拼法不同**，
    // 映射错了表现是"配色和 logo 都对不上"，不会报错，所以逐个断言。
    checkEq("vocaloid → 组合 key 是 piapro（不是 virtual_singer）", MusicUnitTag.VIRTUAL_SINGER.unitProfileKey, "piapro")
    checkEq("light_music_club → light_sound", MusicUnitTag.LEO_NEED.unitProfileKey, "light_sound")
    checkEq("idol → idol", MusicUnitTag.MORE_MORE_JUMP.unitProfileKey, "idol")
    checkEq("street → street", MusicUnitTag.VIVID_BAD_SQUAD.unitProfileKey, "street")
    checkEq("theme_park → theme_park", MusicUnitTag.WONDERLANDS.unitProfileKey, "theme_park")
    checkEq("school_refusal → school_refusal", MusicUnitTag.NIIGO.unitProfileKey, "school_refusal")
    checkEq("other 没有官方组合色（返回 null 而不是编一个）", MusicUnitTag.OTHER.unitProfileKey, null)
    checkEq("other 没有 logo", MusicUnitTag.OTHER.logoAbbr, null)
    checkEq("认不出的标签 → null", MusicUnitTag.of("no_such_unit"), null)
    checkEq(
        "7 个标签的顺序 = 游戏内选曲界面的顺序（VS 最前、other 最后）",
        MusicUnitTag.ordered.map { it.key },
        listOf("vocaloid", "light_music_club", "idol", "street", "theme_park", "school_refusal", "other"),
    )

    // 编解码往返
    checkEq("歌曲条件：空条件往返一致", songQueryFromSaveText(SongQuery().toSaveText()), SongQuery())
    val fullSongQuery = SongQuery(
        search = "生きる",
        units = setOf("vocaloid", "school_refusal"),
        categories = setOf("image", "mv_2d"),
        sort = SongSort.OLDEST,
    )
    checkEq("歌曲条件：满条件往返一致", songQueryFromSaveText(fullSongQuery.toSaveText()), fullSongQuery)
    checkEq(
        "歌曲条件：搜索词里混进分隔符也不串位（自由文本放最后）",
        songQueryFromSaveText(fullSongQuery.copy(search = "a\u0001b|c").toSaveText()).search,
        "a\u0001b|c",
    )
    checkEq(
        "歌曲条件：认不出的排序名退回默认（不崩）",
        songQueryFromSaveText(SongQuery(sort = SongSort.OLDEST).toSaveText().replace("OLDEST", "NOPE")).sort,
        SongSort.NEWEST,
    )
    checkEq("歌曲条件：字段数对不上 → 退回空条件", songQueryFromSaveText("vocaloid"), SongQuery())
    checkEq("歌曲条件：完全空文本也不崩", songQueryFromSaveText(""), SongQuery())
    checkEq("歌曲条件：activeCount 数的是维度个数", fullSongQuery.activeCount, 2)
    checkEq("歌曲条件：重置保留排序、清掉条件", fullSongQuery.cleared(), SongQuery(sort = SongSort.OLDEST))

    // 真正的筛选行为
    fun song(id: Int, title: String, units: List<String>, cats: List<String>, at: Long, composer: String? = null) =
        HomeMusic(
            id = id, title = title, assetbundleName = "jacket_s_$id",
            publishedAt = at, unitTags = units, categories = cats, composer = composer,
        )
    val songs = listOf(
        song(1, "Tell Your World", listOf("vocaloid"), listOf("mv_2d"), 1000, "kz"),
        song(2, "ロキ", listOf("vocaloid", "light_music_club"), listOf("mv_2d", "mv"), 3000, "みきとP"),
        song(644, "生きる", listOf("school_refusal"), listOf("image"), 2000, "水野あつ"),
    )
    checkEq("不筛选时返回全部", filterSongs(songs, SongQuery()).size, 3)
    checkEq(
        "默认按最新上线倒序",
        filterSongs(songs, SongQuery()).map { it.id },
        listOf(2, 644, 1),
    )
    checkEq(
        "最早上线：时间相同时按 id 兜底，顺序稳定",
        filterSongs(songs, SongQuery(sort = SongSort.OLDEST)).map { it.id },
        listOf(1, 644, 2),
    )
    checkEq(
        "同维取「或」：vocaloid + school_refusal",
        filterSongs(songs, SongQuery(units = setOf("vocaloid", "school_refusal"))).map { it.id }.sorted(),
        listOf(1, 2, 644),
    )
    checkEq(
        "跨维取「且」：组合 + 演出形式同时满足",
        filterSongs(songs, SongQuery(units = setOf("vocaloid"), categories = setOf("mv"))).map { it.id },
        listOf(2),
    )
    checkEq("按曲名搜索", filterSongs(songs, SongQuery(search = "生きる")).map { it.id }, listOf(644))
    checkEq("按作曲搜索", filterSongs(songs, SongQuery(search = "みきと")).map { it.id }, listOf(2))
    checkEq("按编号精确搜索", filterSongs(songs, SongQuery(search = "644")).map { it.id }, listOf(644))
    checkEq("搜不到就是空", filterSongs(songs, SongQuery(search = "zzz")).size, 0)
    check(
        "搜索不该把「搜索词」当成模糊的 id 前缀（`64` 不能命中 644）",
        filterSongs(songs, SongQuery(search = "64")).isEmpty(),
        "实际=${filterSongs(songs, SongQuery(search = "64")).map { it.id }}",
    )

    // 分类卡片统计
    val cards = buildSongCategoryCards(songs)
    checkEq("分类卡片：只列出有曲子的分类", cards.map { it.tag.key }, listOf("vocaloid", "light_music_club", "school_refusal"))
    checkEq(
        "分类卡片：曲数正确（vocaloid 2 首）",
        cards.first { it.tag.key == "vocaloid" }.songCount,
        2,
    )
    checkEq(
        "分类卡片：卡面用该分类里**最新**那首歌的曲绘",
        cards.first { it.tag.key == "vocaloid" }.jacketAssetbundleName,
        "jacket_s_2",
    )
    checkEq(
        "分类卡片：只有一个标签的分类，卡面就是那首",
        cards.first { it.tag.key == "school_refusal" }.jacketAssetbundleName,
        "jacket_s_644",
    )
    check(
        "★ 各分类曲数之和会大于总曲数（一首歌挂多个标签，是数据事实不是 bug）",
        cards.sumOf { it.songCount } > songs.size,
        "实际=${cards.sumOf { it.songCount }} vs ${songs.size}",
    )
    checkEq("没有任何歌曲时不给空卡片", buildSongCategoryCards(emptyList()).size, 0)

    // 组合色解析：`unitProfiles.colorCode` 是 `#rrggbb`
    checkEq("颜色解析 #ee1166", parseHexColor("#ee1166"), androidx.compose.ui.graphics.Color(0xFFEE1166))
    checkEq("没有 # 也能解析", parseHexColor("4455dd"), androidx.compose.ui.graphics.Color(0xFF4455DD))
    checkEq("长度不对 → null（不崩）", parseHexColor("#abc"), null)
    checkEq("非十六进制 → null（不崩）", parseHexColor("#gggggg"), null)
    checkEq("null → null", parseHexColor(null), null)

    // ─────────────────────────────────────────────────────────
    section("13. 人物页的数据：每个角色唱过哪些歌")
    // ─────────────────────────────────────────────────────────
    // 用户拍板的口径是**甲 = 他唱过的歌**（不是"他所在团的歌"）。
    // 数据来自 `musicVocals.characters`，实测初音ミク 487 首、镜音铃 160、星乃一歌 90。
    val vocalSingerRows = listOf(
        row(
            "musicVocals.json", 1201, null, null,
            buildJsonObject {
                put("musicId", 644)
                put("musicVocalType", "original_song")
                put("assetbundleName", "vs_0644_01")
                put(
                    "characters",
                    buildJsonArray {
                        // 社外角色（可不）**不能**收进来：它们没有游戏内角色页
                        add(buildJsonObject { put("characterType", "outside_character"); put("characterId", 9) })
                    },
                )
            },
        ),
        row(
            "musicVocals.json", 1663, null, null,
            buildJsonObject {
                put("musicId", 644)
                put("musicVocalType", "sekai")
                put("assetbundleName", "se_0644_01")
                put(
                    "characters",
                    buildJsonArray {
                        add(buildJsonObject { put("characterType", "game_character"); put("characterId", 25) })
                        add(buildJsonObject { put("characterType", "game_character"); put("characterId", 17) })
                    },
                )
            },
        ),
        row(
            "musicVocals.json", 500, null, null,
            buildJsonObject {
                put("musicId", 100)
                put("assetbundleName", "se_0100_01")
                put(
                    "characters",
                    buildJsonArray {
                        add(buildJsonObject { put("characterType", "game_character"); put("characterId", 25) })
                    },
                )
            },
        ),
    )
    val singerMap = buildCharacterSongIds(vocalSingerRows)
    checkEq("两个角色有演唱记录", singerMap.keys.sorted(), listOf(17, 25))
    checkEq("MEIKO(25) 唱过 2 首", singerMap[25]?.sorted(), listOf(100, 644))
    checkEq("宵崎奏(17) 唱过 1 首", singerMap[17]?.sorted(), listOf(644))
    check(
        "★ 社外角色（可不）不该出现在人物页里 —— 它们没有游戏内角色页，点进去是死链",
        singerMap.keys.none { it == 9 },
        "实际 keys=${singerMap.keys}",
    )
    checkEq("空输入不崩", buildCharacterSongIds(emptyList()).size, 0)
    checkEq(
        "缺 characters 字段的行被跳过（不崩）",
        buildCharacterSongIds(listOf(row("musicVocals.json", 1, null, null, buildJsonObject { put("musicId", 1) }))).size,
        0,
    )

    // ─────────────────────────────────────────────────────────
    section("14. 分类详情页：成员筛选与时间排序")
    // ─────────────────────────────────────────────────────────
    // 用户要求：分类详情页能按**该团的人**筛；`其他` 不给这个按钮；
    // 每个详情页都有「按时间先后」按钮，默认新出的在前。

    val memberFixtures = listOf(
        parseCharacter(
            row("gameCharacters.json", 1, "星乃 一歌", null, buildJsonObject { put("unit", "light_sound") }),
        ),
        parseCharacter(
            row("gameCharacters.json", 2, "天馬 咲希", null, buildJsonObject { put("unit", "light_sound") }),
        ),
        parseCharacter(
            row("gameCharacters.json", 5, "花里 みのり", null, buildJsonObject { put("unit", "idol") }),
        ),
        // 虚拟歌手的 unit 是 piapro（不是 virtual_singer）—— 和卡牌那次踩的是同一个坑
        parseCharacter(
            row("gameCharacters.json", 21, "初音 ミク", null, buildJsonObject { put("unit", "piapro") }),
        ),
    ).filterNotNull()
    checkEq("夹具：4 个角色", memberFixtures.size, 4)

    checkEq(
        "Leo/need 的成员 = 该团的人（夹具里 2 个）",
        unitMembers(MusicUnitTag.LEO_NEED, memberFixtures).map { it.id }.sorted(),
        listOf(1, 2),
    )
    checkEq(
        "VIRTUAL SINGER 的成员来自 piapro",
        unitMembers(MusicUnitTag.VIRTUAL_SINGER, memberFixtures).map { it.id },
        listOf(21),
    )
    checkEq(
        "★ 「其他」返回空列表 → 界面据此**不显示**筛选按钮（不需要特判）",
        unitMembers(MusicUnitTag.OTHER, memberFixtures),
        emptyList<PjskCharacter>(),
    )

    // 按演唱者筛选：同维取「或」
    val singerSongs = listOf(
        song(1, "A", listOf("light_music_club"), listOf("image"), 1000),
        song(2, "B", listOf("light_music_club"), listOf("image"), 2000),
        song(3, "C", listOf("light_music_club"), listOf("image"), 3000),
    )
    val sungBy = mapOf(
        1 to setOf(1, 3),   // 一号角色唱过 1、3
        2 to setOf(2),      // 二号角色唱过 2
        21 to setOf(3),     // 初音唱过 3
    )
    checkEq("不选人 = 不筛", filterSongsBySingers(singerSongs, emptySet(), sungBy).size, 3)
    checkEq(
        "选 1 个人",
        filterSongsBySingers(singerSongs, setOf(1), sungBy).map { it.id },
        listOf(1, 3),
    )
    checkEq(
        "选 2 个人取「或」",
        filterSongsBySingers(singerSongs, setOf(1, 2), sungBy).map { it.id }.sorted(),
        listOf(1, 2, 3),
    )
    checkEq("选了没唱过的人 → 空", filterSongsBySingers(singerSongs, setOf(99), sungBy).size, 0)

    // 时间排序：默认新出的在前
    checkEq("默认新出的在前（NEWEST）", sortSongs(singerSongs, SongSort.NEWEST).map { it.id }, listOf(3, 2, 1))
    checkEq("按时间先后（OLDEST）", sortSongs(singerSongs, SongSort.OLDEST).map { it.id }, listOf(1, 2, 3))
    checkEq(
        "★ 排序不改变集合（只是换顺序）",
        sortSongs(singerSongs, SongSort.OLDEST).map { it.id }.sorted(),
        sortSongs(singerSongs, SongSort.NEWEST).map { it.id }.sorted(),
    )
    checkEq("空列表排序不崩", sortSongs(emptyList(), SongSort.NEWEST).size, 0)

    // ─────────────────────────────────────────────────────────
    section("15. 试听：跳过开头空白的时间轴换算")
    // ─────────────────────────────────────────────────────────
    // 游戏音源 `music/long/*.mp3` 的**开头有一段空白**（`musics.fillerSec`），
    // 播的时候要跳过；而且整条时间轴要平移 —— 只 seek 不换算的话，
    // 进度条一上来就在 0:08、总长显示 1:50，而这首歌实际只有 1:41 的内容。
    // 实测 717 首的 fillerSec 全部 > 0（4.50~11.07 秒，中位数 9 秒）。

    checkEq("fillerSec 换算成毫秒（644 的 7.928… 秒）", fillerMsOf(7.928599834442139), 7928L)
    checkEq("fillerSec 缺失 → 0（不跳过）", fillerMsOf(null), 0L)
    checkEq("fillerSec 为 0 → 0", fillerMsOf(0.0), 0L)
    checkEq("fillerSec 为负 → 0", fillerMsOf(-3.0), 0L)
    checkEq("fillerSec 为 NaN → 0（不崩）", fillerMsOf(Double.NaN), 0L)
    checkEq("fillerSec 大得离谱时封顶 60 秒（防脏数据把整首歌跳掉）", fillerMsOf(9999.0), 60_000L)

    // 用 644 的真实数字：音频 109.87 秒、filler 7.93 秒 → 显示 1:41（和参考站一致）
    val filler = fillerMsOf(7.928599834442139)
    checkEq("★ 644：显示总长 = 音频总长 - 开头空白", displayDurationMs(109_870L, filler), 101_942L)
    checkEq("★ 644：显示总长格式化后就是参考站那个 1:41", formatClock(displayDurationMs(109_870L, filler)), "1:41")
    // ⚠️ 这里是 1:49 而不是 1:50：`formatClock` 按播放器惯例**向下取整**到秒
    //（109.87 秒 → 109 秒 → 1:49）。早先探测时我按四舍五入报了 1:50，
    // 是那个数字不准，不是这里算错。
    checkEq("★ 644：音频文件本身是 1:49（显示的**不是**它，差的就是那 8 秒空白）", formatClock(109_870L), "1:49")
    checkEq("刚开始播（播放器停在 filler 处）→ 显示位置 0", displayPositionMs(filler, filler), 0L)
    checkEq("播放器位置还没到 filler → 显示 0 而不是负数", displayPositionMs(1000L, filler), 0L)
    checkEq("播放器位置 60 秒 → 显示 52 秒", displayPositionMs(60_000L, filler), 52_072L)
    checkEq("拖动到显示 30 秒 → seek 到播放器 37.9 秒", playerPositionMs(30_000L, filler), 37_928L)
    checkEq(
        "★ 往返一致：显示位置 → 播放器位置 → 显示位置",
        displayPositionMs(playerPositionMs(45_000L, filler), filler),
        45_000L,
    )
    checkEq("时长未知（-1）保持未知，而不是显示 0:00", displayDurationMs(-1L, filler), TIME_UNSET_MS)
    checkEq("时长未知时显示 --:--（不显示 0:00，那会让人以为这首是空的）", formatClock(TIME_UNSET_MS), "--:--")
    checkEq("时长比开头空白还短 → 显示 0，不出现负数", displayDurationMs(3000L, filler), 0L)
    checkEq("filler 为 0 时时间轴不动", displayPositionMs(12_345L, 0L), 12_345L)
    checkEq("时钟格式化：0 秒", formatClock(0L), "0:00")
    checkEq("时钟格式化：整数分钟", formatClock(120_000L), "2:00")
    checkEq("时钟格式化：秒补零", formatClock(65_000L), "1:05")

    // ─────────────────────────────────────────────────────────
    section("16. 筛选面板：选中的 chip 排到最前")
    // ─────────────────────────────────────────────────────────
    // 用户反馈：点了中间某个 chip 之后它还夹在一排未选中里，"放到中间有点奇怪"。
    // 规则：**选中项浮到最前，其余保持原顺序**（用稳定排序，整排不会每次点击都洗牌）。

    checkEq(
        "选中项浮到最前",
        listOf("a", "b", "c", "d").moveMatchesFirst { it == "c" },
        listOf("c", "a", "b", "d"),
    )
    checkEq(
        "★ 未选中的相对顺序不变（稳定排序，不会每次点击都整体洗牌）",
        listOf("a", "b", "c", "d", "e").moveMatchesFirst { it == "d" || it == "b" },
        listOf("b", "d", "a", "c", "e"),
    )
    checkEq(
        "什么都没选 → 原样",
        listOf("a", "b", "c").moveMatchesFirst { false },
        listOf("a", "b", "c"),
    )
    checkEq(
        "全选 → 原样",
        listOf("a", "b", "c").moveMatchesFirst { true },
        listOf("a", "b", "c"),
    )
    checkEq("空列表不崩", emptyList<String>().moveMatchesFirst { true }, emptyList<String>())
    checkEq(
        "顺序对调一次也稳定（反复点选不会漂）",
        listOf("a", "b", "c").moveMatchesFirst { it == "b" }.moveMatchesFirst { it == "a" },
        listOf("a", "b", "c"),
    )

    // ─────────────────────────────────────────────────────────
    section("17. 播放器进度条：分数 ↔ 毫秒")
    // ─────────────────────────────────────────────────────────
    // 波浪进度条拖动/点击都要经过这一对换算。这里用的是**显示时长**（已减掉开头空白），
    // 所以数字要和 §15 对得上。

    checkEq("拖到最右 → 显示位置 = 总长", positionOfFraction(1f, 101_942L), 101_942L)
    checkEq("拖到最左 → 0", positionOfFraction(0f, 101_942L), 0L)
    checkEq("拖到一半 → 一半", positionOfFraction(0.5f, 101_942L), 50_971L)
    checkEq("★ 分数越界（>1）被夹住，不会 seek 到歌外", positionOfFraction(1.8f, 101_942L), 101_942L)
    checkEq("★ 分数越界（<0）被夹住", positionOfFraction(-0.5f, 101_942L), 0L)
    checkEq("★ 时长未知（0）时不动，不出现负数 seek", positionOfFraction(0.5f, 0L), 0L)
    checkEq("时长未知（-1）时也不动", positionOfFraction(0.5f, TIME_UNSET_MS), 0L)

    checkEq("位置 0 → 分数 0", fractionOf(0L, 101_942L), 0f)
    checkEq("位置 = 总长 → 分数 1", fractionOf(101_942L, 101_942L), 1f)
    checkEq("位置超过总长 → 夹到 1（不画出界）", fractionOf(999_999L, 101_942L), 1f)
    checkEq("★ 时长未知时分数为 0（进度条画成 0，而不是满格或乱跳）", fractionOf(5_000L, 0L), 0f)
    checkEq("时长未知（-1）时分数为 0", fractionOf(5_000L, TIME_UNSET_MS), 0f)
    checkEq(
        "★ 往返一致：位置 → 分数 → 位置（浮点截断差 1ms 以内）",
        positionOfFraction(fractionOf(37_000L, 101_942L), 101_942L) in 36_998L..37_001L,
        true,
    )

    // ─────────────────────────────────────────────────────────
    section("18. 收藏（本地存储）与列表行的默认音源")
    // ─────────────────────────────────────────────────────────
    // 收藏存在偏好设置里（**不进数据库**，避免 Room 迁移），编解码抽成纯函数就是为了这里能断言。
    // 存坏了的表现是"收藏莫名其妙少了/多了"，真机上很难发现。

    val favorites = mapOf(644 to 1_700_000_000_000L, 1 to 0L, 585 to 1_600_000_000_000L)
    checkEq("收藏：往返一致", decodeFavoriteSongs(encodeFavoriteSongs(favorites)), favorites)
    checkEq("收藏：空集合往返", decodeFavoriteSongs(encodeFavoriteSongs(emptyMap())), emptyMap<Int, Long>())
    checkEq(
        "★ 时间戳损坏时不丢这条收藏（当作 0，排最后）",
        decodeFavoriteSongs(setOf("644", "1:abc")),
        mapOf(644 to 0L, 1 to 0L),
    )
    checkEq(
        "★ id 解析不出来才丢那一条",
        decodeFavoriteSongs(setOf("abc:123", "644:5")),
        mapOf(644 to 5L),
    )
    checkEq("空字符串不崩", decodeFavoriteSongs(setOf("")), emptyMap<Int, Long>())
    checkEq(
        "收藏页的排序：按收藏时间倒序（最近收藏的在最前）",
        decodeFavoriteSongs(encodeFavoriteSongs(favorites))
            .entries.sortedByDescending { it.value }.map { it.key },
        listOf(644, 585, 1),
    )

    // 列表行 ▶ 播的是"默认版本" = seq 最小那个
    fun vocalRow(rowId: Int, musicId: Int, seq: Int, bundle: String, caption: String) = row(
        "musicVocals.json", rowId, null, null,
        buildJsonObject {
            put("musicId", musicId)
            put("seq", seq)
            put("assetbundleName", bundle)
            put("caption", caption)
        },
    )
    val vocalFixtures = listOf(
        // 故意把 seq 大的排在前面，验证挑的是 seq 最小而不是列表顺序
        vocalRow(1663, 644, 201, "se_0644_01", "セカイver."),
        vocalRow(1201, 644, 101, "vs_0644_01", "バーチャル・シンガーver."),
        // ⚠️ 陷阱行：行主键和 musicId 撞车（真机上就这么撞）
        vocalRow(644, 258, 1, "se_0258_01", "别曲"),
        vocalRow(2, 1, 101, "0001_01", "バーチャル・シンガーver."),
    )
    val defaults = buildDefaultVocals(vocalFixtures)
    // 三条：1、644，以及**陷阱行自己的 musicId 258** —— 那一行确实属于 music 258，
    // 给它组出一条是**对的**；关键是别混进 644（下一条断言守这个）。
    checkEq("默认音源：按 musicId 分组", defaults.keys.sorted(), listOf(1, 258, 644))
    checkEq(
        "★ 默认音源取 seq 最小的那个（不是列表里第一个）",
        defaults[644]?.assetbundleName,
        "vs_0644_01",
    )
    checkEq("默认音源的版本说明", defaults[644]?.caption, "バーチャル・シンガーver.")
    checkEq(
        "★ 行主键和 musicId 撞车时不受影响",
        defaults[644]?.assetbundleName?.startsWith("se_0258"),
        false,
    )
    checkEq("空输入不崩", buildDefaultVocals(emptyList()).size, 0)
    checkEq(
        "缺 assetbundleName 的行被丢掉（列表上的 ▶ 会置灰）",
        buildDefaultVocals(
            listOf(row("musicVocals.json", 9, null, null, buildJsonObject { put("musicId", 9); put("seq", 1) })),
        ).size,
        0,
    )

    // ─────────────────────────────────────────────────────────
    section("19. 播放队列：从列表建队列、播放模式循环、列表行的播放键形态")
    // ─────────────────────────────────────────────────────────
    // 「列表」和「播放模式」都要队列才成立。这里守两个最容易错的地方：
    // **过滤掉没有音源的歌之后下标会错位**，以及**播放模式的循环**。

    fun listedSong(id: Int, hasVocal: Boolean) = HomeMusic(
        id = id,
        title = "S$id",
        assetbundleName = "jacket_s_$id",
        publishedAt = id.toLong(),
        unitTags = emptyList(),
        categories = emptyList(),
        composer = null,
        defaultVocal = if (hasVocal) HomeMusicVocal(id * 10, "vs_${id}_01", "ver.") else null,
        fillerSec = 1.0,
    )

    // 中间那首没有音源 → 会被过滤掉，导致下标错位（这就是要守的坑）
    val listWithGap = listOf(
        listedSong(1, true),
        listedSong(2, false),
        listedSong(3, true),
        listedSong(4, true),
    )
    val built = buildQueueFrom(listWithGap, 3)
    check("能建出队列", built != null)
    checkEq("★ 没有音源的歌不进队列", built?.first?.map { it.musicId }, listOf(1, 3, 4))
    checkEq(
        "★ 起点按 musicId 在**过滤后**的列表里找（不是用原始下标）",
        built?.second,
        1,
    )
    checkEq("队列里每一项都带着自己的开头空白秒数", built?.first?.get(1)?.fillerMs, 1000L)
    checkEq("点中间那首（没有音源）→ 不播", buildQueueFrom(listWithGap, 2), null)
    checkEq("点的歌不在列表里 → 不播", buildQueueFrom(listWithGap, 99), null)
    checkEq("空列表 → 不播", buildQueueFrom(emptyList(), 1), null)
    checkEq(
        "全都没有音源 → 不播（而不是建出一个空队列）",
        buildQueueFrom(listOf(listedSong(1, false)), 1),
        null,
    )
    checkEq("第一首：起点是 0", buildQueueFrom(listWithGap, 1)?.second, 0)
    checkEq("最后一首：起点是过滤后的最后一个", buildQueueFrom(listWithGap, 4)?.second, 2)

    // 播放模式循环：列表播放 → 单曲循环 → 随机播放 → 回到列表播放
    checkEq("模式循环：SEQUENTIAL → REPEAT_ONE", PlayMode.SEQUENTIAL.next(), PlayMode.REPEAT_ONE)
    checkEq("模式循环：REPEAT_ONE → SHUFFLE", PlayMode.REPEAT_ONE.next(), PlayMode.SHUFFLE)
    checkEq(
        "★ 模式循环：SHUFFLE → 回到 SEQUENTIAL（不会越界）",
        PlayMode.SHUFFLE.next(),
        PlayMode.SEQUENTIAL,
    )
    checkEq("三种模式的中文标签", PlayMode.entries.map { it.label }, listOf("列表播放", "单曲循环", "随机播放"))
    checkEq("连点三次回到原模式", PlayMode.SEQUENTIAL.next().next().next(), PlayMode.SEQUENTIAL)

    // ── 列表行的播放键形态（▶ / ⏸）───────────────────────────
    // 规则：正在播的那首显示 ⏸、点它是暂停；暂停后回到 ▶（含义是"继续"）；
    // 其它任何一行都是 ▶（含义是"试听 = 重新建队列"）。
    checkEq("没在播：任何一行都是 ▶", rowPlayState(null, 644, false), RowPlayState.IDLE)
    checkEq("★ 播的就是这一首：显示 ⏸", rowPlayState(644, 644, true), RowPlayState.PLAYING)
    checkEq("★ 同一首被暂停：回到 ▶（继续播放）", rowPlayState(644, 644, false), RowPlayState.PAUSED)
    checkEq("别的歌在播：这一行还是 ▶", rowPlayState(644, 645, true), RowPlayState.IDLE)
    // 播放单元是「曲」：版本不同不算别的歌 —— 从详情页切成世界ver. 回到列表，
    // 这首歌仍然该是 ⏸（否则会出现「两行同时显示 ⏸」这种自相矛盾的状态）
    checkEq("同一 musicId、不同版本仍然算同一首", rowPlayState(644, 644, true), RowPlayState.PLAYING)
    checkEq("暂停状态下别的歌也不受影响", rowPlayState(644, 162, false), RowPlayState.IDLE)
    checkEq("共 3 种形态", RowPlayState.entries.size, 3)

    // ── 切版本不许动队列、也不许换歌（用户拍板，两轮才定下来）──
    // 第一轮反馈：「换版本会把正在听的那份列表顶掉」→ 改成原地换版本。
    // 第二轮反馈：「点版本会跳成另一首歌」→ 连"跳到队列里那首歌的位置"这条分支也删了。
    // 规则只剩：**就是这首歌 → 原地换；不是 → 另起队列**。
    checkEq(
        "★ 点当前这一首的别的版本 → 原地换（队列不动）",
        versionPlayAction(queueMusicIdAtCurrent = 644, playerMusicId = 644, musicId = 644),
        VersionPlayAction.SWAP_CURRENT,
    )
    // ⚠️ 这条是用户第二轮反馈的那个 bug：在 B 的详情页点 B 的版本、而在播的是 A。
    // 旧规则会"跳到队列里 B 的位置"（= 播放光标跑别的歌去了），**必须**是另起队列。
    checkEq(
        "★ 在播 A、点 B 的版本 → 另起队列（绝不跳过去）",
        versionPlayAction(queueMusicIdAtCurrent = 1, playerMusicId = 1, musicId = 644),
        VersionPlayAction.NEW_QUEUE,
    )
    // ⚠️ 两边**都**要对上才敢原地换：只信我们队列的下标，一旦错位就会去换"别的歌"那一项
    checkEq(
        "★ 我们队列说 644、播放器说 1 → 不敢换（另起队列）",
        versionPlayAction(queueMusicIdAtCurrent = 644, playerMusicId = 1, musicId = 644),
        VersionPlayAction.NEW_QUEUE,
    )
    checkEq(
        "★ 我们队列说 1、播放器说 644 → 同样不敢换",
        versionPlayAction(queueMusicIdAtCurrent = 1, playerMusicId = 644, musicId = 644),
        VersionPlayAction.NEW_QUEUE,
    )
    checkEq(
        "没有当前项 → 另起队列",
        versionPlayAction(queueMusicIdAtCurrent = null, playerMusicId = null, musicId = 644),
        VersionPlayAction.NEW_QUEUE,
    )
    checkEq(
        "只读得到一边 → 也另起队列",
        versionPlayAction(queueMusicIdAtCurrent = 644, playerMusicId = null, musicId = 644),
        VersionPlayAction.NEW_QUEUE,
    )
    checkEq("只有两条路", VersionPlayAction.entries.size, 2)

    // mediaId ↔ musicId 的解析（播放器那边的真相）
    checkEq("mediaId 644-6441 → musicId 644", musicIdOfMediaId("644-6441"), 644)
    checkEq("mediaId 1-11 → musicId 1", musicIdOfMediaId("1-11"), 1)
    checkEq("mediaId 是空 → null（宁可另起队列，也不乱换）", musicIdOfMediaId(""), null)
    checkEq("mediaId 不是数字 → null", musicIdOfMediaId("abc-def"), null)
    checkEq("mediaId 为 null → null", musicIdOfMediaId(null), null)

    // ─────────────────────────────────────────────────────────
    section("20. 定时器（播放器右上角的闹钟）：选项 / 剩余时间 / 再点一次取消")
    // ─────────────────────────────────────────────────────────
    // 用户要求「**不要**关闭这一项」，所以取消只能靠"再点一次已选中的那一项"——
    // 这条规则最容易在改界面时被弄丢（丢了就变成"设了就撤不掉"），所以在这里守死。

    checkEq("默认不设定时器 = 一直播放", SleepTimerState().isActive, false)
    checkEq("默认选中的项是 null（= 一直播放）", sleepTimerOptionOf(SleepTimerState()), null)
    checkEq("6 个选项", SleepTimerOption.entries.size, 6)
    checkEq(
        "选项标签（最后一项不是「关闭」，是「播完这首」）",
        SleepTimerOption.entries.map { it.label },
        listOf("5 分钟后", "10 分钟后", "15 分钟后", "30 分钟后", "60 分钟后", "播完这首歌就停"),
    )
    checkEq("只有「播完这首」没有分钟数", SleepTimerOption.entries.count { it.minutes == null }, 1)
    checkEq(
        "设了 15 分钟 → 选中的就是 15 分钟",
        sleepTimerOptionOf(SleepTimerState(minutes = 15, endsAtMs = 0L)),
        SleepTimerOption.MIN_15,
    )
    checkEq(
        "两个字段都在时以「播完这首」为准",
        sleepTimerOptionOf(SleepTimerState(minutes = 5, atTrackEnd = true)),
        SleepTimerOption.TRACK_END,
    )
    checkEq("分钟数不在目录里（脏数据）→ null，不崩", sleepTimerOptionOf(SleepTimerState(minutes = 7)), null)
    checkEq("★「播完这首」也算定时器开着", SleepTimerState(atTrackEnd = true).isActive, true)
    checkEq("★ 设了时长也算开着", SleepTimerState(minutes = 30, endsAtMs = 0L).isActive, true)

    // 剩余时间：走墙钟，和播放位置无关
    checkEq(
        "没设固定时长 → null（不是 0）",
        sleepTimerRemainingMs(SleepTimerState(atTrackEnd = true), 1_000L),
        null,
    )
    checkEq(
        "设 5 分钟、过了 10 秒 → 还剩 290 秒",
        sleepTimerRemainingMs(SleepTimerState(minutes = 5, endsAtMs = 300_000L), 10_000L),
        290_000L,
    )
    checkEq(
        "正好到点 → 0",
        sleepTimerRemainingMs(SleepTimerState(minutes = 5, endsAtMs = 300_000L), 300_000L),
        0L,
    )
    checkEq(
        "★ 已经过点了 → 0（不给负数，否则界面会显示 -0:12）",
        sleepTimerRemainingMs(SleepTimerState(minutes = 5, endsAtMs = 300_000L), 999_999L),
        0L,
    )
    checkEq(
        "剩余时间能直接喂给 formatClock",
        formatClock(sleepTimerRemainingMs(SleepTimerState(minutes = 60, endsAtMs = 3_610_000L), 3_600_000L)!!),
        "0:10",
    )

    // 「再点一次 = 取消」
    checkEq(
        "点没选过的项 → 设定时长",
        sleepTimerClickAction(SleepTimerState(), SleepTimerOption.MIN_5),
        SleepTimerAction.SET_MINUTES,
    )
    checkEq(
        "点「播完这首」→ 设定播完这首",
        sleepTimerClickAction(SleepTimerState(), SleepTimerOption.TRACK_END),
        SleepTimerAction.SET_TRACK_END,
    )
    checkEq(
        "★ 再点一次已选中的时长 → 取消",
        sleepTimerClickAction(SleepTimerState(minutes = 5, endsAtMs = 0L), SleepTimerOption.MIN_5),
        SleepTimerAction.CLEAR,
    )
    checkEq(
        "★ 再点一次「播完这首」→ 取消",
        sleepTimerClickAction(SleepTimerState(atTrackEnd = true), SleepTimerOption.TRACK_END),
        SleepTimerAction.CLEAR,
    )
    checkEq(
        "选了 5 分钟时点 10 分钟 → 改成 10 分钟（不是取消）",
        sleepTimerClickAction(SleepTimerState(minutes = 5, endsAtMs = 0L), SleepTimerOption.MIN_10),
        SleepTimerAction.SET_MINUTES,
    )
    checkEq(
        "选的是「播完这首」时点 5 分钟 → 改成 5 分钟",
        sleepTimerClickAction(SleepTimerState(atTrackEnd = true), SleepTimerOption.MIN_5),
        SleepTimerAction.SET_MINUTES,
    )
    checkEq(
        "选了 5 分钟时点「播完这首」→ 改成播完这首",
        sleepTimerClickAction(SleepTimerState(minutes = 5, endsAtMs = 0L), SleepTimerOption.TRACK_END),
        SleepTimerAction.SET_TRACK_END,
    )
    checkEq("PlaybackState 默认也不带定时器", PlaybackState().sleepTimer.isActive, false)
    checkEq("取消后回到「一直播放」", PlaybackState(sleepTimer = SleepTimerState()).sleepTimer.isActive, false)

    // ── 自定义时长（用户要求：自己输入分钟数）──
    checkEq("自定义下限", MIN_CUSTOM_MINUTES, 1)
    checkEq("自定义上限 = 10 小时", MAX_CUSTOM_MINUTES, 600)

    checkEq("★ 输入 7 → 7 分钟", parseCustomSleepMinutes("7"), 7)
    checkEq("输入 600（上限）→ 通过", parseCustomSleepMinutes("600"), 600)
    checkEq("输入 601（超上限）→ 不合法", parseCustomSleepMinutes("601"), null)
    checkEq("输入 0 → 不合法", parseCustomSleepMinutes("0"), null)
    checkEq("输入 -5 → 不合法", parseCustomSleepMinutes("-5"), null)
    checkEq("空输入 → 不合法（确定键该是灰的）", parseCustomSleepMinutes(""), null)
    checkEq("只有空格 → 不合法", parseCustomSleepMinutes("   "), null)
    checkEq("中文/字母 → 不合法（不崩）", parseCustomSleepMinutes("七分钟"), null)
    checkEq("混了字母 → 不合法", parseCustomSleepMinutes("7分"), null)
    checkEq("前后空格容忍", parseCustomSleepMinutes("  15  "), 15)
    checkEq("带小数点 → 不合法（不四舍五入）", parseCustomSleepMinutes("7.5"), null)

    checkEq("5 是预设项", isPresetSleepMinutes(5), true)
    checkEq("★ 7 不是预设项（= 自定义）", isPresetSleepMinutes(7), false)
    checkEq("null 不算预设项", isPresetSleepMinutes(null), false)
    checkEq("600 不是预设项", isPresetSleepMinutes(600), false)
    // ⚠️ 自定义的分钟数**不该**被当成预设项点亮（否则弹层里会有两行同时选中）
    checkEq(
        "自定义 7 分钟时，预设项一个都没选中",
        SleepTimerOption.entries.count { it.minutes == 7 },
        0,
    )
    checkEq(
        "自定义 7 分钟时 sleepTimerOptionOf 仍返回 null",
        sleepTimerOptionOf(SleepTimerState(minutes = 7, endsAtMs = 0L)),
        null,
    )

    // 点「自定义」那一行：没设过 → 展开输入框；已经是自定义 → 取消
    checkEq(
        "没定时器时点自定义 → 展开输入框",
        customTimerClickAction(SleepTimerState()),
        CustomTimerAction.OPEN_INPUT,
    )
    checkEq(
        "选的是预设 15 分钟时点自定义 → 展开输入框",
        customTimerClickAction(SleepTimerState(minutes = 15, endsAtMs = 0L)),
        CustomTimerAction.OPEN_INPUT,
    )
    checkEq(
        "选的是「播完这首」时点自定义 → 展开输入框",
        customTimerClickAction(SleepTimerState(atTrackEnd = true)),
        CustomTimerAction.OPEN_INPUT,
    )
    checkEq(
        "★ 已经是自定义 7 分钟时再点 → 取消",
        customTimerClickAction(SleepTimerState(minutes = 7, endsAtMs = 0L)),
        CustomTimerAction.CLEAR,
    )
    // 自定义时长也是"到点暂停"的一种，走的还是同一个 endsAtMs 通道
    checkEq(
        "自定义 90 分钟：剩余时间照常算",
        sleepTimerRemainingMs(SleepTimerState(minutes = 90, endsAtMs = 5_400_000L), 0L),
        5_400_000L,
    )
    checkEq(
        "自定义 90 分钟：到点了就是 0（界面据此收尾）",
        sleepTimerRemainingMs(SleepTimerState(minutes = 90, endsAtMs = 5_400_000L), 5_400_000L),
        0L,
    )

    // 倒计时的写法：不足 1 小时 m:ss，1 小时以上 h:mm:ss
    checkEq("倒计时 7 分钟 → 7:00", formatTimerClock(420_000L), "7:00")
    checkEq("倒计时 1 分 30 秒 → 1:30", formatTimerClock(90_000L), "1:30")
    checkEq("★ 正好 1 小时 → 1:00:00（不是 60:00）", formatTimerClock(3_600_000L), "1:00:00")
    checkEq("★ 自定义 90 分钟 → 1:30:00", formatTimerClock(5_400_000L), "1:30:00")
    checkEq("★ 自定义上限 10 小时 → 10:00:00", formatTimerClock(36_000_000L), "10:00:00")
    checkEq("0 → 0:00", formatTimerClock(0L), "0:00")
    checkEq("负数也不崩（返回 0:00）", formatTimerClock(-5_000L), "0:00")
    checkEq("59 秒 → 0:59", formatTimerClock(59_000L), "0:59")
    checkEq("按秒向下取整（不四舍五入）", formatTimerClock(59_999L), "0:59")

    // ── 换版本只换那一项，别的一个都不许动 ──
    fun mt(musicId: Int, vocalId: Int) = MusicTrack(
        musicId = musicId,
        vocalId = vocalId,
        title = "S$musicId",
        versionLabel = "v$vocalId",
        audioUrl = "https://example.invalid/$vocalId.mp3",
        jacketAssetbundleName = "jacket_s_$musicId",
        fillerMs = 0L,
    )

    val fourSongs = listOf(mt(1, 11), mt(644, 6441), mt(585, 5851), mt(162, 1621))
    val swapped = fourSongs.withVersionAt(1, mt(644, 9999))
    checkEq("★ 换完长度不变", swapped.size, fourSongs.size)
    checkEq("★ 换完下标 1 就是新版本", swapped[1].vocalId, 9999)
    checkEq("★ 换完其它三项一个字没动", swapped.filterIndexed { i, _ -> i != 1 }, fourSongs.filterIndexed { i, _ -> i != 1 })
    checkEq("★ 换完顺序没变", swapped.map { it.musicId }, listOf(1, 644, 585, 162))
    checkEq("原列表没被改（是纯函数）", fourSongs[1].vocalId, 6441)
    checkEq("下标越界 → 原样返回（不崩）", fourSongs.withVersionAt(99, mt(644, 9999)), fourSongs)
    checkEq("负数下标 → 原样返回", fourSongs.withVersionAt(-1, mt(644, 9999)), fourSongs)

    // ─────────────────────────────────────────────────────────
    section("21. 活动剧情筛选：按相关的团 + 最新/最早")
    // ─────────────────────────────────────────────────────────
    // 数据实测（2026-09-16）：eventStoryUnits 450 行 / 214 个活动剧情全覆盖，
    // unit 只有 5 个团 + 5 行 none，**没有 piapro**（所以 VS 不会出现在筛选里）。

    fun eu(unit: String, main: Boolean = false) = EventStoryUnit(1, unit, main)
    fun srow(id: String, sortAt: Long, units: List<String> = emptyList()) = StoryRow(
        id = id,
        title = "E$id",
        subtitle = null,
        imageUrl = null,
        trailing = null,
        sortAt = sortAt,
        unitKeys = units,
    )

    // 归约：去重 / none → other / 按游戏内组合顺序
    checkEq("没有行 → 空（不崩）", eventStoryUnitKeys(emptyList()), emptyList())
    checkEq("单个团", eventStoryUnitKeys(listOf(eu("light_sound", main = true))), listOf("light_sound"))
    checkEq(
        "★ 同一个团多行要去重（450 行 ÷ 214 个活动）",
        eventStoryUnitKeys(listOf(eu("idol", main = true), eu("idol"))),
        listOf("idol"),
    )
    checkEq("★ none 归到「其他」", eventStoryUnitKeys(listOf(eu("none"))), listOf("other"))
    checkEq("空白 unit 也算「其他」", eventStoryUnitKeys(listOf(eu(" "))), listOf("other"))
    checkEq(
        "★ 多团按游戏内顺序排（不按数据里行的先后）",
        eventStoryUnitKeys(listOf(eu("school_refusal"), eu("light_sound"), eu("street"))),
        listOf("light_sound", "street", "school_refusal"),
    )
    checkEq(
        "★ 混合活动（5 个团）全部保留",
        eventStoryUnitKeys(
            listOf(
                eu("light_sound"),
                eu("idol"),
                eu("street"),
                eu("theme_park"),
                eu("school_refusal"),
            ),
        ),
        listOf("light_sound", "idol", "street", "theme_park", "school_refusal"),
    )
    checkEq("认不出的 unit 原样保留（不编说法）", eventStoryUnitKeys(listOf(eu("piapro"))), listOf("piapro"))
    // ⚠️ 这两条是给未来的人看的：**两张表的 key 体系不一样**，混了就出上面那两种症状。
    // 写实现时我自己就混过一次（自检抓出来的）。
    checkEq(
        "★ Leo/need：MusicUnitTag.key 是 light_music_club",
        MusicUnitTag.LEO_NEED.key,
        "light_music_club",
    )
    checkEq(
        "★ 而 eventStoryUnits.unit 用的是 unitProfileKey（light_sound）",
        MusicUnitTag.LEO_NEED.unitProfileKey,
        "light_sound",
    )
    checkEq(
        "★ VIRTUAL SINGER 的 unitProfileKey 是 piapro（不是 virtual_singer / vocaloid）",
        MusicUnitTag.VIRTUAL_SINGER.unitProfileKey,
        "piapro",
    )

    val eventList = listOf(
        srow("1", 100L, listOf("light_sound", "theme_park")),
        srow("2", 300L, listOf("school_refusal")),
        srow("3", 200L, listOf("light_sound")),
        srow("4", 400L, listOf("other")),
    )

    checkEq(
        "★ 不选团 = 全都要（默认最新在前）",
        filterStoryRows(eventList, StoryQuery()).map { it.id },
        listOf("4", "2", "3", "1"),
    )
    checkEq(
        "★ 选一个团",
        filterStoryRows(eventList, StoryQuery(units = setOf("light_sound"))).map { it.id },
        listOf("3", "1"),
    )
    checkEq(
        "★ 选两个团是「或」（混合活动两边都出现）",
        filterStoryRows(eventList, StoryQuery(units = setOf("light_sound", "school_refusal"))).map { it.id },
        listOf("2", "3", "1"),
    )
    checkEq(
        "★「其他」能筛出那 5 个不属于任何团的活动",
        filterStoryRows(eventList, StoryQuery(units = setOf("other"))).map { it.id },
        listOf("4"),
    )
    checkEq(
        "★ 最早在前",
        filterStoryRows(eventList, StoryQuery(sort = StorySort.OLDEST)).map { it.id },
        listOf("1", "3", "2", "4"),
    )
    checkEq(
        "筛出空来了（不是崩）",
        filterStoryRows(eventList, StoryQuery(units = setOf("street"))),
        emptyList<StoryRow>(),
    )
    // 稳定排序：时间相同的保持原有先后
    val sameTime = listOf(srow("a", 50L), srow("b", 50L), srow("c", 50L))
    checkEq("时间相同时顺序稳定", sortStoryRows(sameTime, StorySort.NEWEST).map { it.id }, listOf("a", "b", "c"))
    checkEq("时间相同时顺序稳定（最早在前也一样）", sortStoryRows(sameTime, StorySort.OLDEST).map { it.id }, listOf("a", "b", "c"))
    checkEq("两种排序方式", StorySort.entries.map { it.label }, listOf("最新在前", "最早在前"))

    // 筛选面板的选项：数据驱动 + 计数 + 顺序 + 团名
    val options = buildStoryUnitOptions(eventList)
    checkEq("★ 选项只有数据里真的有的团", options.map { it.key }, listOf("light_sound", "theme_park", "school_refusal", "other"))
    checkEq("★ 没有 piapro（VS）—— 数据里就没有它的活动剧情", options.any { it.key == "piapro" }, false)
    checkEq("计数是「有多少个活动相关」", options.map { it.count }, listOf(2, 1, 1, 1))
    checkEq(
        "团名走歌曲页那套（含「其他」）",
        options.map { it.label },
        listOf("Leo/need", "ワンダーランズ×ショウタイム", "25時、ナイトコードで。", "其他"),
    )
    checkEq("没有活动 → 没有选项（界面据此不显示筛选按钮）", buildStoryUnitOptions(emptyList()), emptyList())

    // 筛选状态跨界面的编解码（§11.13 的约定）
    val roundTrip = StoryQuery(units = setOf("light_sound", "other"), sort = StorySort.OLDEST)
    checkEq("★ 筛选状态往返一致", storyQueryFromSaveText(roundTrip.toSaveText()), roundTrip)
    checkEq("★ 默认状态往返一致", storyQueryFromSaveText(StoryQuery().toSaveText()), StoryQuery())
    checkEq("空串 → 默认值（不抛异常）", storyQueryFromSaveText(""), StoryQuery())
    checkEq("结构对不上 → 默认值", storyQueryFromSaveText("???@@@"), StoryQuery())
    checkEq("排序名认不出 → 退回最新在前", storyQueryFromSaveText("light_sound|NOPE").sort, StorySort.NEWEST)
    checkEq(
        "只存了团、没存排序 → 排序用默认值",
        storyQueryFromSaveText("light_sound|").sort,
        StorySort.NEWEST,
    )
    checkEq("重置只清筛选、保留排序", StoryQuery(units = setOf("idol"), sort = StorySort.OLDEST).cleared().sort, StorySort.OLDEST)
    checkEq("重置后不再是筛选态", StoryQuery(units = setOf("idol")).cleared().isFiltering, false)

    // ─────────────────────────────────────────────────────────
    // 首次启动体验：导入顺序 / 同步顺序 / 镜像线路（§11.37）
    //
    // 这几项的共性是「写错了不会报错，只会表现成体验变差」：
    // 导入顺序错了 → 打开 App 后卡牌页半天没数据；
    // 同步顺序错了 → 慢线路上先下 44 MB 的卡池表，卡牌反而排在后面；
    // 镜像地址写错或漏了兜底 → 卡面/音频整块不可用。
    // 所以都要有断言钉住。
    // ─────────────────────────────────────────────────────────
    section("内置数据导入顺序：卡牌与歌曲最先")

    val packDir = "app/src/main/assets/datapack"
    // 打包用的源数据目录（不随仓库分发，但开发机上一直有；缺了就跳过相关断言）
    val rawDirForCheck = "tools/datapack/raw"

    check("cards.json 属于 P0", DatapackImportOrder.group("cards.json") == 0)
    check("musics.json 属于 P0", DatapackImportOrder.group("musics.json") == 0)
    check("musicDifficulties.json 属于 P0", DatapackImportOrder.group("musicDifficulties.json") == 0)
    check("skills.json 属于 P0", DatapackImportOrder.group("skills.json") == 0)
    check("gameCharacters.json 属于 P0（卡牌与歌曲都要用）", DatapackImportOrder.group("gameCharacters.json") == 0)
    check("events.json 属于 P1", DatapackImportOrder.group("events.json") == 1)
    check("gachas.json 属于 P1", DatapackImportOrder.group("gachas.json") == 1)
    check("characterRanks.json 归 P2", DatapackImportOrder.group("characterRanks.json") == 2)
    check("没见过的表归 P2", DatapackImportOrder.group("brandNewTable.json") == 2)
    check("null 归 P2", DatapackImportOrder.group(null) == 2)

    check("卡牌排在歌曲前面（首页主入口更早可用）",
        DatapackImportOrder.orderWithinGroup("cards.json") < DatapackImportOrder.orderWithinGroup("musics.json"),
        "cards=${DatapackImportOrder.orderWithinGroup("cards.json")} musics=${DatapackImportOrder.orderWithinGroup("musics.json")}")

    val p0AndP1Overlap = DatapackImportOrder.P0_CARD_AND_MUSIC.intersect(DatapackImportOrder.P1_HOME.toSet())
    check("P0 与 P1 没有重叠", p0AndP1Overlap.isEmpty(), "重叠=$p0AndP1Overlap")
    check("P0 名单里没有重复项",
        DatapackImportOrder.P0_CARD_AND_MUSIC.size == DatapackImportOrder.P0_CARD_AND_MUSIC.toSet().size)
    check("P1 名单里没有重复项",
        DatapackImportOrder.P1_HOME.size == DatapackImportOrder.P1_HOME.toSet().size)

    // 拿一份真实的快照清单，验证排序结果：卡牌/歌曲在前、P2 在最后且按体积升序
    val manifestText = File(packDir, "manifest.json").takeIf { it.isFile }?.readText()
    if (manifestText == null) {
        check("读到内置快照清单（找不到就跳过排序验证）", false, "缺少 ${File(packDir, "manifest.json").absolutePath}")
    } else {
        val manifest = json.parseToJsonElement(manifestText).jsonObject
        val tables = manifest["tables"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
        val entries = tables.mapNotNull { row ->
            val file = row.stringValue("file") ?: return@mapNotNull null
            Triple(file, row.intValue("rows") ?: 0, (row.stringValue("bytes")?.toLongOrNull() ?: 0L))
        }
        check("清单里有 66 张表", entries.size == 66, "实际=${entries.size}")

        val sorted = entries.sortedWith(
            compareBy(
                { DatapackImportOrder.group(it.first) },
                { DatapackImportOrder.orderWithinGroup(it.first) },
                { it.third },
            ),
        ).map { it.first }

        check("排序后第一张表就是卡牌相关（cards.json 或角色基础表）",
            sorted.first() in setOf("gameCharacters.json", "cards.json"), "第一张=${sorted.first()}")
        val firstCardIdx = sorted.indexOf("cards.json")
        val firstEventIdx = sorted.indexOf("events.json")
        val firstGachaIdx = sorted.indexOf("gachas.json")
        check("cards.json 排在 events.json 之前", firstCardIdx in 0 until firstEventIdx, "cards=$firstCardIdx events=$firstEventIdx")
        check("cards.json 排在 gachas.json 之前", firstCardIdx in 0 until firstGachaIdx, "cards=$firstCardIdx gachas=$firstGachaIdx")
        check("前三张都是卡牌/歌曲相关的表",
            sorted.take(3).all { DatapackImportOrder.group(it) == 0 }, "前三=${sorted.take(3)}")

        // P2 段按体积升序
        val p2 = sorted.filter { DatapackImportOrder.group(it) == 2 }
        val p2Bytes = p2.map { name -> entries.first { it.first == name }.third }
        check("P2 段按体积升序（小表先完成，进度条动得快）",
            p2Bytes.zipWithNext().all { (a, b) -> a <= b }, "体积序列前 8 项=${p2Bytes.take(8)}")
        check("清单里 66 张表都排进去了", sorted.size == 66)
    }

    section("在线同步顺序：卡牌 / 歌曲在前，卡池垫底")

    check("卡牌模块优先级最高", DataModule.CARD.syncPriority < DataModule.MUSIC.syncPriority)
    check("歌曲排第二（高于角色/活动）",
        DataModule.MUSIC.syncPriority < DataModule.CHARACTER.syncPriority &&
            DataModule.MUSIC.syncPriority < DataModule.EVENT.syncPriority)
    check("卡池优先级最低（44 MB 且只在家首页看一眼）",
        DataModule.GACHA.syncPriority > DataModule.EVENT.syncPriority &&
            DataModule.GACHA.syncPriority > DataModule.STICKER.syncPriority)

    check("默认模块包含卡牌", DataModule.CARD in SyncManager.DEFAULT_MODULES)
    check("默认模块不包含卡池（体积太大，要用户自己勾）", DataModule.GACHA !in SyncManager.DEFAULT_MODULES)

    val defaultSpecs = TableCatalog.forModules(SyncManager.DEFAULT_MODULES)
        .sortedWith(compareBy({ it.module.syncPriority }, { it.approxBytes }))
    check("默认模块同步时第一张表属于卡牌", defaultSpecs.first().module == DataModule.CARD, "实际=${defaultSpecs.first().fileName}")
    check("默认模块里不会出现卡池的表", defaultSpecs.none { it.module == DataModule.GACHA })
    check("同模块内按体积升序（小表先落地）",
        defaultSpecs.filter { it.module == DataModule.MUSIC }
            .map { it.approxBytes }
            .zipWithNext().all { (a, b) -> a <= b })

    section("素材线路：卡面与音频走镜像，剧情仍走官方")

    val jp = ServerRegion.JP
    check("卡面 URL 用镜像 host",
        AssetUrls.cardImage(jp, "res001_no004", trained = false).startsWith("https://storage.exmeaning.com/"),
        AssetUrls.cardImage(jp, "res001_no004", trained = false))
    check("卡面大图也用镜像",
        AssetUrls.cardImage(jp, "res001_no004", trained = true, size = AssetUrls.CardSize.LARGE)
            .startsWith("https://storage.exmeaning.com/"))
    check("卡面原图（保存用 png）用镜像且是 .png",
        AssetUrls.cardImagePng(jp, "res001_no004", trained = false)
            .let { it.startsWith("https://storage.exmeaning.com/") && it.endsWith(".png") })
    check("卡面小图标用镜像",
        AssetUrls.cardIcon(jp, "res001_no004", trained = false).startsWith("https://storage.exmeaning.com/"))
    check("预览 URL 与直连一致（不再经过缩放代理）",
        AssetUrls.cardPreview(jp, "res001_no004", trained = false) ==
            AssetUrls.cardImage(jp, "res001_no004", trained = false))
    check("音频用镜像的 jp 桶",
        AssetUrls.musicAudio("0001_01").startsWith("https://storage.exmeaning.com/sekai-jp-assets/music/long/"),
        AssetUrls.musicAudio("0001_01"))
    check("短版音频同样走镜像",
        AssetUrls.musicAudio("0001_01", short = true).contains("/music/short/"))
    check("播放器封面（固定 jp）走镜像",
        AssetUrls.musicJacketJp("jacket_s_001").startsWith("https://storage.exmeaning.com/"))
    // ⚠️ 镜像实测**没有**剧情 .asset（404），所以剧情必须留在官方
    check("★ 剧情资源仍然走官方 host（镜像没有这些文件）",
        AssetUrls.storyScenario(jp, StoryAssetKind.EVENT, "event_afterfire_2026", "event_216_01")
            .startsWith("https://storage.sekai.best/"),
        AssetUrls.storyScenario(jp, StoryAssetKind.EVENT, "event_afterfire_2026", "event_216_01"))
    check("抽卡语音仍然走官方（未纳入镜像范围）",
        AssetUrls.gachaVoice(jp, "res001_no004").startsWith("https://storage.sekai.best/"))

    section("同步判定：按内容 sha，而不是版本号/ETag（§11.38）")

    checkEq("本地 sha 与远端一致 → 跳过（零请求）",
        SyncDecision.decide(localSha = "abc", remoteSha = "abc", force = false),
        SyncDecision.Action.SKIP_UNCHANGED)
    checkEq("内容变了 → 下载",
        SyncDecision.decide(localSha = "abc", remoteSha = "def", force = false),
        SyncDecision.Action.DOWNLOAD)
    checkEq("本地没有 sha 记录（旧版本升上来）→ 下载一次",
        SyncDecision.decide(localSha = null, remoteSha = "abc", force = false),
        SyncDecision.Action.DOWNLOAD)
    checkEq("远端已无此表 → 跳过且不动本地数据",
        SyncDecision.decide(localSha = "abc", remoteSha = null, force = false),
        SyncDecision.Action.SKIP_MISSING_REMOTE)
    checkEq("强制核对 → 即使 sha 一致也重下",
        SyncDecision.decide(localSha = "abc", remoteSha = "abc", force = true),
        SyncDecision.Action.DOWNLOAD)
    checkEq("远端已无此表 + 强制 → 依然跳过（不能把本地数据删掉）",
        SyncDecision.decide(localSha = "abc", remoteSha = null, force = true),
        SyncDecision.Action.SKIP_MISSING_REMOTE)

    // ── 内置快照的 sha 必须与本地源数据算出来的完全一致 ──
    // 这是整套机制的地基：manifest 里的 sha 一旦和真实内容对不上，
    // App 首次同步就会把每一张表都判成"变了"而全量重下，或者更糟——把对的判成"没变"。
    // 这里直接用 App 里那份 gitBlobShaOf 去算，等于同时验证「算法实现」与「快照记录」。
    run {
        val manifestFile = File(packDir, "manifest.json")
        if (!manifestFile.isFile) {
            check("读到内置快照清单", false, "缺少 ${manifestFile.absolutePath}")
        } else {
            val manifest = json.parseToJsonElement(manifestFile.readText()).jsonObject
            val tables = manifest["tables"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
            val shaRepo = MasterRepository(okhttp3.OkHttpClient(), File("."))
            var checked = 0
            val mismatched = mutableListOf<String>()
            val missingSha = mutableListOf<String>()
            for (row in tables) {
                val file = row.stringValue("file") ?: continue
                val sha = row.stringValue("sha")
                if (sha == null) { missingSha += file; continue }
                val raw = File(rawDirForCheck, file)
                if (!raw.isFile) continue
                checked++
                val actual = shaRepo.gitBlobShaOf(raw)
                if (actual != sha) mismatched += "$file(记录 ${sha.take(8)} / 实算 ${actual.take(8)})"
            }
            check("清单里带 sha 的表数 = 表总数", missingSha.isEmpty(), "缺 sha：${missingSha.take(5)}")
            check("抽查的源数据文件数 ≥ 50", checked >= 50, "实际 $checked")
            check(
                "★ 清单记录的 sha 与源数据算出来的完全一致（$checked 张表）",
                mismatched.isEmpty(),
                mismatched.take(3).joinToString("；"),
            )
        }
    }

    // ─────────────────────────────────────────────────────────
    println("\n" + "=".repeat(60))
    if (failures == 0) {
        println("逻辑自检 PASS —— $checks 项全部通过")
    } else {
        println("逻辑自检 FAIL —— $checks 项里有 $failures 项不通过")
    }
    println("=".repeat(60))
    if (failures > 0) kotlin.system.exitProcess(1)
}

// ── 夹具读取助手 ──────────────────────────────────────────────

private fun readObject(file: File): JsonObject {
    require(file.isFile) { "夹具不存在：${file.absolutePath}" }
    return json.parseToJsonElement(file.readText()) as JsonObject
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

private fun JsonObject.intValue(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()

private fun JsonObject.boolValue(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun JsonObject.intList(key: String): List<Int> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() }
        ?: emptyList()
