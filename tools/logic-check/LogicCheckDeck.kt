
import com.pjsk.toolbox.data.deck.DeckLiveType
import com.pjsk.toolbox.data.deck.DeckTarget
import com.pjsk.toolbox.data.deck.parseDeckRecommendResult
import com.pjsk.toolbox.data.deck.buildRecommendParams
import com.pjsk.toolbox.data.deck.restoreMusicMetasJson
import com.pjsk.toolbox.data.player.CoverCandidate
import com.pjsk.toolbox.data.player.CoverFilter
import com.pjsk.toolbox.data.player.CoverPick
import com.pjsk.toolbox.data.player.CoverVariant
import com.pjsk.toolbox.data.player.encodeIdSet
import com.pjsk.toolbox.data.player.expandCoverVariants
import com.pjsk.toolbox.data.player.matchesCoverFilter
import com.pjsk.toolbox.data.player.parseIdSet
import com.pjsk.toolbox.data.player.parseRarityKeys
import com.pjsk.toolbox.data.player.pickCover
import com.pjsk.toolbox.data.remote.DataModule
import com.pjsk.toolbox.data.remote.TableCatalog
import com.pjsk.toolbox.data.sync.RowProjectors
import com.pjsk.toolbox.ui.common.intOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 组卡引擎相关的自检节。
 *
 * **为什么单独一个文件**：`LogicCheck.kt` 的 `main()` 已经撞到 JVM 的
 * `MethodTooLargeException`（单方法 64KB 上限）。新加的断言一律写成这里的独立函数，
 * 由 main() 一行调用 —— 这样一个文件一个方法，永远不会再撞。
 */
internal fun checkDeckAutomationPart1() {
section("29. 组卡结果页：解析引擎返回（批次 D 第 1 步）")
// 引擎输出的形状以 `docs/research/_deck_probe/allium-README.en.md` 的样例为准：
//   completion / timed_out / diagnostics.pool_size / timing.{build_pool_ms,search_ms} / decks[]
// 而 moesekai 的 TS 绑定把这些性能字段放在 `performance` 下 —— 两种都要认（见 parsers 注释）。
val engineOk = parseDeckRecommendResult(
    """
    {
      "completion": "complete",
      "timed_out": false,
      "diagnostics": { "pool_size": 194 },
      "timing": { "build_pool_ms": 1.4, "search_ms": 0.4 },
      "decks": [
        {
          "rank": 1,
          "total_power": 361234,
          "target_value": 361234,
          "skill_score": 640.0,
          "live_score": 1234567,
          "event_point": 1234,
          "event_bonus_total": 25,
          "cards": [
            { "card_id": 111, "rarity": "rarity_4", "attr": "cool", "character_id": 3,
              "level": 60, "master_rank": 5, "power_total": 72247,
              "skill_score_up": 120.0, "skill_level": 4, "trained": true,
              "episode1_read": true, "episode2_read": false,
              "has_canvas_bonus": true, "canvas_power": 600, "event_bonus": 25 },
            { "card_id": 222, "power_total": 72247 },
            { "card_id": 333, "power_total": 72247 },
            { "card_id": 444, "power_total": 72247 },
            { "card_id": 555, "power_total": 72246 }
          ]
        }
      ]
    }
    """.trimIndent(),
)
checkEq("解析出 1 套结果", engineOk.decks.size, 1)
checkEq("★ completion 原样取回（这是「精确 Top-K」的唯一凭据）", engineOk.completion, "complete")
checkEq("completion=complete → isExact", engineOk.isExact, true)
checkEq("排名取引擎给的 rank", engineOk.decks.first().rank, 1)
checkEq("整套综合力", engineOk.decks.first().totalPower, 361234)
// 性能字段：引擎给的是 diagnostics + timing，不是 performance
checkEq("★ 候选池从 diagnostics.pool_size 读", engineOk.poolSize, 194)
checkEq("★ build_pool 从 timing.build_pool_ms 读（1.4 → 1，说明 timing 也被合并进来了）", engineOk.buildPoolMs, 1)
checkEq("★ 搜索耗时从 timing.search_ms 读（0.4 ms 截成 0：亚毫秒级，界面另有 JS 侧整数耗时）", engineOk.searchMs, 0)
val firstCard = engineOk.decks.first().cards.first()
checkEq("★ 逐卡 card_id 就是游戏卡 id（可直接 join 内置 cards.json）", firstCard.cardId, 111)
checkEq("逐卡等级", firstCard.level, 60)
checkEq("逐卡突破", firstCard.masterRank, 5)
checkEq("逐卡综合力", firstCard.powerTotal, 72247)
checkEq(
    "★ skill_score_up 是**百分数**，不做任何换算（120.0 = 120%，不是 1.2）",
    firstCard.skillScoreUp,
    120.0,
)
checkEq("逐卡技能等级", firstCard.skillLevel, 4)
checkEq("剧情已读标记", listOf(firstCard.episode1Read, firstCard.episode2Read), listOf(true, false))
checkEq("★ 逐卡特训状态（卡图标用特训前还是后，要听引擎的）", firstCard.trained, true)
checkEq("画布加成", listOf(firstCard.hasCanvasBonus, firstCard.canvasPower), listOf(true, 600))
checkEq("逐卡活动加成（本轮 target=power 用不到，但字段要能读到）", firstCard.eventBonus, 25.0)
checkEq("event_point 读成 Int", engineOk.decks.first().eventPoint, 1234)
// 引擎的加成按 0.1% 计（文档明确写过 4.8% / 5.1% 这种半档），所以是 Double 不是 Int ——
// 截成整数会把 230.5% 显示成 230%。
checkEq("★ event_bonus_total 保留半档（Double，不是截断成 Int）", engineOk.decks.first().eventBonusTotal, 25.0)
checkEq(
    "★ 半档加成不被截断",
    parseDeckRecommendResult("""{"decks":[{"event_bonus_total":230.5}]}""").decks.first().eventBonusTotal,
    230.5,
)

// 缺字段的条目：没有 card_id 的卡直接丢（接不到内置卡表就等于无法显示），
// 但**整套不能因此消失** —— 少一格比整页空白好。
val engineSparse = parseDeckRecommendResult(
    """{"decks":[{"rank":1,"cards":[{"card_id":9},{"rarity":"rarity_4"}]}]}""",
)
checkEq("★ 没有 card_id 的卡被丢掉，其余保留", engineSparse.decks.first().cards.map { it.cardId }, listOf(9))
checkEq("特训字段缺失时退回 false（不抛）", engineSparse.decks.first().cards.first().trained, false)
checkEq(
    "整套和逐卡都没有 power 时给 0（此时确实没有任何数字可显示）",
    engineSparse.decks.first().totalPower,
    0,
)
checkEq(
    "缺 completion 且没超时 → 视为精确（不误报「可能不是最优」）",
    engineSparse.isExact,
    true,
)

// 逐卡 power 存在、整套字段缺失时的兜底
val engineFallback = parseDeckRecommendResult(
    """{"decks":[{"cards":[{"card_id":1,"power_total":100},{"card_id":2,"power_total":250}]}]}""",
)
checkEq(
    "★ total_power 缺失 → 逐卡求和兜底",
    engineFallback.decks.first().totalPower,
    350,
)
checkEq("rank 缺失 → 按下标补 1", engineFallback.decks.first().rank, 1)

// 超时：引擎返回的是"合法但未证明最优"，界面必须能区分
val engineTimeout = parseDeckRecommendResult(
    """{"completion":"timed_out","timed_out":true,"performance":{"pool_size":10,"search_ms":2000},"decks":[]}""",
)
checkEq("★ timed_out → 不是精确解（不能对用户说「就这 5 套」）", engineTimeout.isExact, false)
checkEq("performance 这种写法也认", engineTimeout.searchMs, 2000)
check("只有 timed_out 标志、没有 completion 时不当精确", !parseDeckRecommendResult("""{"timed_out":true,"decks":[]}""").isExact)

// camelCase 别名（引擎入参两种都收；出参万一换成 camelCase 也不该整页归零）
val engineCamel = parseDeckRecommendResult(
    """{"completion":"complete","decks":[{"rank":2,"totalPower":1000,"cards":[
       {"cardId":7,"characterId":5,"masterRank":3,"powerTotal":200,"skillScoreUp":130.0,
        "skillLevel":2,"episode1Read":true,"episode2Read":true,"hasCanvasBonus":false,
        "canvasPower":0}]}]}""",
)
checkEq("★ camelCase 别名也认（totalPower / cardId / masterRank …）", engineCamel.decks.first().totalPower, 1000)
checkEq("camelCase 的逐卡字段", engineCamel.decks.first().cards.first().masterRank, 3)
checkEq("camelCase 的角色 id", engineCamel.decks.first().cards.first().characterId, 5)

// 解析失败必须是**显式失败**，不能悄悄返回空列表（界面上变成"没有推荐"）
check(
    "★ 非 JSON / 没有 decks 都要抛（静默返回空列表会被当成「无可用卡组」）",
    runCatching { parseDeckRecommendResult("not json") }.isFailure &&
        runCatching { parseDeckRecommendResult("""{"completion":"complete"}""") }.isFailure,
)
check(
    "decks 为空数组是**合法**结果（不抛）",
    runCatching { parseDeckRecommendResult("""{"decks":[]}""") }.getOrNull()?.decks?.isEmpty() == true,
)
}

internal fun checkDeckAutomationPart2() {
section("30. 三个目标 × 两种玩法：参数组装 + musicMetas 还原（批次 D 第 2/3 步）")
// 目标/玩法一多，最容易出的错是"参数拼上了但内容不对"——引擎不会报错，
// 只会按兜底表算出看着合理的数字。所以逐字段断言。

// 综合力：唯一完全离线的组合，绝不能带 event_id / music_id
val powerParams = json.parseToJsonElement(buildRecommendParams()).jsonObject
checkEq("power：target=power", powerParams.stringValue("target"), "power")
checkEq("power：live_type=solo", powerParams.stringValue("live_type"), "solo")
check(
    "★ power 不带 event_id / music_id（带了就会去查活动/曲目表）",
    !powerParams.containsKey("event_id") && !powerParams.containsKey("music_id"),
)

// 活动加成：必须带 event_id
val bonusParams = json.parseToJsonElement(
    buildRecommendParams(target = DeckTarget.BONUS, eventId = 220),
).jsonObject
checkEq("bonus：target=bonus", bonusParams.stringValue("target"), "bonus")
checkEq("★ bonus 带 event_id（加成率按活动算）", bonusParams.stringValue("event_id")?.toIntOrNull(), 220)
check("bonus 不带 music_id（不需要曲目）", !bonusParams.containsKey("music_id"))

// 分数：必须带 music_id + 难度；难度缺省时**不写** music_diff（引擎按 expert 兜底）
val scoreParams = json.parseToJsonElement(
    buildRecommendParams(
        target = DeckTarget.SCORE,
        musicId = 123,
        musicDiff = "master",
        eventId = 220,
    ),
).jsonObject
checkEq("score：target=score", scoreParams.stringValue("target"), "score")
checkEq("★ score 带 music_id", scoreParams.stringValue("music_id")?.toIntOrNull(), 123)
checkEq("★ score 带 music_diff", scoreParams.stringValue("music_diff"), "master")
checkEq("★ score 同时带 event_id 时才会算活动 PT", scoreParams.stringValue("event_id")?.toIntOrNull(), 220)
check(
    "没有 music_id 时**不写** music_diff（避免出现只有难度没有曲目的组合）",
    !json.parseToJsonElement(buildRecommendParams(target = DeckTarget.SCORE, musicDiff = "expert"))
        .jsonObject.containsKey("music_diff"),
)

// 多人：队友参数只在给了的时候出现
val multiParams = json.parseToJsonElement(
    buildRecommendParams(
        target = DeckTarget.SCORE,
        liveType = DeckLiveType.MULTI,
        musicId = 1,
        musicDiff = "expert",
        multiTeammatePower = 200000,
        multiTeammateScoreUp = 150,
    ),
).jsonObject
checkEq("multi：live_type=multi", multiParams.stringValue("live_type"), "multi")
checkEq("★ 队友综合力透传", multiParams.stringValue("multi_teammate_power")?.toIntOrNull(), 200000)
checkEq("★ 队友技能值透传", multiParams.stringValue("multi_teammate_score_up")?.toIntOrNull(), 150)
check(
    "队友参数不给就不写（按 0 算由引擎决定，不要伪造 0）",
    !json.parseToJsonElement(buildRecommendParams(target = DeckTarget.SCORE, musicId = 1))
        .jsonObject.containsKey("multi_teammate_power"),
)
// 稀有度配置必须每档都在：缺一档引擎就用默认（等于把用户没练的卡当满练）
checkEq(
    "★ 五档稀有度配置齐全",
    listOf("rarity1", "rarity2", "rarity3", "rarity4", "rarityBirthday")
        .count { scoreParams.containsKey("${it}Config") },
    5,
)

// ── 控分：target_bonus_list ──
val tierParams = json.parseToJsonElement(
    buildRecommendParams(target = DeckTarget.BONUS, eventId = 220, targetBonusList = listOf(260, 265)),
).jsonObject
checkEq(
    "★ 控分档位透传（target_bonus_list）",
    tierParams["target_bonus_list"]!!.jsonArray.map { it.jsonPrimitive.int },
    listOf(260, 265),
)
check(
    "没给档位时**不写** target_bonus_list（写了就变成精确命中，不再是最大化加成）",
    !bonusParams.containsKey("target_bonus_list"),
)
check(
    "空列表也不写（否则语义会从「最大化」变成「没有档位」）",
    !json.parseToJsonElement(buildRecommendParams(targetBonusList = emptyList())).jsonObject
        .containsKey("target_bonus_list"),
)
checkEq(
    "★ 档位最多 32 个（引擎硬限制，超了直接解析错误）",
    json.parseToJsonElement(
        buildRecommendParams(targetBonusList = (1..50).toList()),
    ).jsonObject["target_bonus_list"]!!.jsonArray.size,
    32,
)
checkEq(
    "档位被夹在 0..10000",
    json.parseToJsonElement(
        buildRecommendParams(targetBonusList = listOf(-5, 20_000)),
    ).jsonObject["target_bonus_list"]!!.jsonArray.map { it.jsonPrimitive.int },
    listOf(0, 10_000),
)
checkEq(
    "★ 解析器能读出引擎回报的档位（target_bonus / tier 两种写法）",
    listOf(
        parseDeckRecommendResult("""{"decks":[{"target_bonus":265}]}""").decks.first().targetBonus,
        parseDeckRecommendResult("""{"decks":[{"tier":260}]}""").decks.first().targetBonus,
        parseDeckRecommendResult("""{"decks":[{"rank":1}]}""").decks.first().targetBonus,
    ),
    listOf(265, 260, null),
)

// ── 播放器封面：条件筛选 + 变体展开 + 随机挑选 ──
// 这一层的错法全是"看着不对"（条件筛错 / ★1 莫名消失 / 连着两次同一张 / 空集合崩），
// 所以用固定构造的候选集和固定种子的 Random 断言**行为**，而不是断言某个随机结果。
val coverCandidates = listOf(
    CoverCandidate(id = 1, characterId = 10, rarityKey = "rarity_4", trainedAvailable = true),
    CoverCandidate(id = 2, characterId = 10, rarityKey = "rarity_3", trainedAvailable = true),
    CoverCandidate(id = 3, characterId = 10, rarityKey = "rarity_birthday", trainedAvailable = false),
    CoverCandidate(id = 4, characterId = 11, rarityKey = "rarity_4", trainedAvailable = true),
    CoverCandidate(id = 5, characterId = 11, rarityKey = "rarity_1", trainedAvailable = false),
)
val coverIdsOf = { filter: CoverFilter ->
    coverCandidates.filter { matchesCoverFilter(it, filter) }.map { it.id }
}
checkEq(
    "空条件 = 不限（全部命中）",
    coverIdsOf(CoverFilter()),
    listOf(1, 2, 3, 4, 5),
)
checkEq(
    "★ 按角色筛",
    coverIdsOf(CoverFilter(characterIds = setOf(10))),
    listOf(1, 2, 3),
)
checkEq(
    "★ 按星级筛（多选）",
    coverIdsOf(CoverFilter(rarityKeys = setOf("rarity_4", "rarity_1"))),
    listOf(1, 4, 5),
)
checkEq(
    "★ 角色 × 星级 两个维度是**与**关系",
    coverIdsOf(CoverFilter(characterIds = setOf(10), rarityKeys = setOf("rarity_4"))),
    listOf(1),
)

// 变体展开：★1/★2/生日卡没有觉醒后，选「只要觉醒后」必须把它们排除（不是显示 404）
checkEq(
    "★ 只要觉醒前 → 每张一条",
    expandCoverVariants(coverCandidates, CoverVariant.NORMAL).map { it.cardId },
    listOf(1, 2, 3, 4, 5),
)
checkEq(
    "★ 只要觉醒后 → 无觉醒后卡面的卡被排除",
    expandCoverVariants(coverCandidates, CoverVariant.TRAINED).map { it.cardId },
    listOf(1, 2, 4),
)
checkEq(
    "★ 都要 → 有觉醒后的卡各两条、没有的一条",
    expandCoverVariants(coverCandidates, CoverVariant.BOTH).size,
    8,
)
check(
    "★ 都要模式下 trained 标记正确（3 号生日卡只有未觉醒）",
    expandCoverVariants(coverCandidates, CoverVariant.BOTH)
        .filter { it.cardId == 3 }
        .map { it.trained } == listOf(false),
)

// 随机挑选：不连续重复同一张卡（一卡两版算同一张）
val coverPicks = expandCoverVariants(coverCandidates, CoverVariant.BOTH)
val coverRng = kotlin.random.Random(7)
var lastCoverId: Int? = null
var coverRepeats = 0
val pickedCovers = mutableListOf<CoverPick>()
repeat(60) {
    val next = pickCover(coverPicks, lastCoverId, coverRng)
    check("随机不能返回 null（集合非空）", next != null)
    if (next?.cardId == lastCoverId) coverRepeats++
    next?.let { pickedCovers += it }
    lastCoverId = next?.cardId
}
check("★ 连续 60 次不会与上一次同卡（同卡不同版本也算同卡）", coverRepeats == 0, "重复 $coverRepeats 次")
check("挑出来的卡必在候选集内", pickedCovers.all { it.cardId in listOf(1, 2, 3, 4, 5) })
check("随机确实覆盖多张", pickedCovers.map { it.cardId }.toSet().size >= 2)
checkEq("单条可选时永远是它", pickCover(listOf(CoverPick(9, false)), previousCardId = 9, random = coverRng)?.cardId, 9)
checkEq("空集合返回 null（调用方回退曲绘）", pickCover(emptyList(), null, coverRng), null)
checkEq(
    "★ 偏好编解码：角色 id 集合往返一致",
    parseIdSet(encodeIdSet(setOf(3, 1, 2))),
    setOf(1, 2, 3),
)
checkEq("脏 id 被丢掉", parseIdSet("1, x, 0, -2, 3"), setOf(1, 3))
checkEq(
    "★ 稀有度只保留已知 key（未知 key 会让命中数变成 0，比静默保留更难查）",
    parseRarityKeys("rarity_4, rarity_9, junk, rarity_birthday"),
    setOf("rarity_4", "rarity_birthday"),
)
checkEq("空串解析成空集合", listOf(parseIdSet(""), parseRarityKeys(null)), listOf(emptySet<Int>(), emptySet<String>()))
check("未收窄条件时 isNarrowed=false（等于全卡表随机，界面该提示）", !CoverFilter().isNarrowed)
check("选了任一维度就算收窄", CoverFilter(rarityKeys = setOf("rarity_4")).isNarrowed)

// ── musicMetas：上游 33 字段 → 引擎要的 12 个 ──
// ── musicMetas：上游 33 字段 → 引擎要的 12 个 ──
val metaRaw = """
    {"music_id":1,"difficulty":"expert","music_time":123.2,"event_rate":114,
     "base_score":1.005249294449671,"base_score_auto":0.7,
     "skill_score_solo":[0.1,0.2,0.3,0.4,0.5,0.6],
     "skill_score_auto":[0.1,0.2,0.3,0.4,0.5,0.6],
     "skill_score_multi":[0.1,0.2,0.3,0.4,0.5,0.6],
     "fever_score":0.228,"fever_end_time":100,"tap_count":76,
     "solo_score":1234567,"pt_per_hour_auto":999,"pspi_solo_score":1}
""".trimIndent()
val metaRow = RowProjectors.MUSIC_METAS.project(json.parseToJsonElement(metaRaw).jsonObject)
check("musicMetas 裁剪成功", metaRow != null)
checkEq("★ 造出可反解的主键 id = musicId×10 + 难度序号（expert=3）", metaRow?.intOrNull("id"), 13)
checkEq("music_id 保留", metaRow?.intOrNull("music_id"), 1)
checkEq("difficulty 保留", metaRow?.stringValue("difficulty"), "expert")
checkEq(
    "★ 引擎要的 12 个字段一个不少",
    listOf(
        "music_id", "difficulty", "music_time", "event_rate", "base_score", "base_score_auto",
        "skill_score_solo", "skill_score_auto", "skill_score_multi", "fever_score",
        "fever_end_time", "tap_count",
    ).filter { metaRow?.containsKey(it) != true },
    emptyList<String>(),
)
check(
    "★ 数值类型原样搬运（base_score 是小数、event_rate/tap_count 是整数）",
    metaRow?.stringValue("base_score") == "1.005249294449671" &&
        metaRow?.stringValue("event_rate") == "114" &&
        metaRow?.stringValue("tap_count") == "76",
)
check(
    "不需要的展示字段被裁掉（solo_score / pt_per_hour_* / pspi_*）",
    metaRow?.let { row ->
        listOf("solo_score", "pt_per_hour_auto", "pspi_solo_score").none { row.containsKey(it) }
    } == true,
)
checkEq(
    "★ 幂等：已经裁过的行不会被二次裁剪（否则 id 会越变越怪）",
    RowProjectors.MUSIC_METAS.project(metaRow!!),
    metaRow,
)
check(
    "未知难度被丢掉（引擎只认 easy/normal/hard/expert/master/append）",
    RowProjectors.MUSIC_METAS.project(
        json.parseToJsonElement("""{"music_id":1,"difficulty":"ultra"}""").jsonObject,
    ) == null,
)
check(
    "缺 music_id 的行被丢掉",
    RowProjectors.MUSIC_METAS.project(
        json.parseToJsonElement("""{"difficulty":"expert"}""").jsonObject,
    ) == null,
)
// 六个难度各自的主键必须互不相同（否则同歌不同难度会互相覆盖）
checkEq(
    "★ 六个难度的 id 互不相同",
    listOf("easy", "normal", "hard", "expert", "master", "append")
        .mapNotNull { diff ->
            RowProjectors.MUSIC_METAS.project(
                json.parseToJsonElement("""{"music_id":7,"difficulty":"$diff"}""").jsonObject,
            )?.intOrNull("id")
        }
        .distinct()
        .size,
    6,
)

// ── 还原成引擎的第二个入参：必须去掉本 App 的 id ──
val metasJson = restoreMusicMetasJson(listOf(metaRow.toString()))
val metasArray = json.parseToJsonElement(metasJson).jsonArray
checkEq("还原出 1 行", metasArray.size, 1)
val metasObj = metasArray.first().jsonObject
check(
    "★ 还原时要**丢掉本 App 的主键 id**（引擎的 music_metas 没有这个字段）",
    !metasObj.containsKey("id"),
)
checkEq("其余 12 个字段都还在", metasObj.keys.size, 12)
checkEq(
    "空输入给空数组（引擎会 JSON.parse 它，绝不能是空串）",
    restoreMusicMetasJson(emptyList()),
    "[]",
)
checkEq(
    "无效行（缺 music_id / difficulty）被丢掉",
    json.parseToJsonElement(
        restoreMusicMetasJson(
            listOf("""{"difficulty":"expert"}""", """{"music_id":3}""", metaRow.toString()),
        ),
    ).jsonArray.size,
    1,
)

// ── 表目录：musicMetas 只能随快照分发，在线同步必须看不到它 ──
val musicMetasSpec = TableCatalog.ALL.firstOrNull { it.fileName == "musicMetas.json" }
check("musicMetas 在表目录里（打包器要靠它）", musicMetasSpec != null)
checkEq("musicMetas 标了 snapshotOnly", musicMetasSpec?.snapshotOnly, true)
check(
    "★ 在线同步的表清单里**没有** musicMetas（否则每台设备同步都会 404）",
    TableCatalog.forModules(setOf(DataModule.MUSIC)).none { it.fileName == "musicMetas.json" },
)
check(
    "★ 体积统计也不含它（那是内置数据的体积，不是要下载的量）",
    TableCatalog.totalBytes(setOf(DataModule.MUSIC)) ==
        TableCatalog.ALL
            .filter { it.module == DataModule.MUSIC && !it.snapshotOnly }
            .sumOf { it.approxBytes },
)
check("musicMetas 有裁剪器（否则 5.4 MB 的原始表会整张进 APK）", RowProjectors.forFile("musicMetas.json") != null)
}

