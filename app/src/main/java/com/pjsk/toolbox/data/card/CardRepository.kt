package com.pjsk.toolbox.data.card

import com.pjsk.toolbox.data.db.MasterDao
import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.jsonObject
import com.pjsk.toolbox.ui.common.str
import com.pjsk.toolbox.util.decodeQueryFields
import com.pjsk.toolbox.util.decodeQueryItems
import com.pjsk.toolbox.util.encodeQueryFields
import com.pjsk.toolbox.util.encodeQueryItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 卡牌/角色的读取入口。
 *
 * 关键设计：**不做 SQL 侧筛选，而是一次性把卡牌读进内存再筛**。
 *
 * 理由：卡牌一共只有 1447 张，`RowProjectors.CARDS` 把它们从 35 MB 压到约 1 MB，
 * 载入 + 解析在十几毫秒量级。相比之下走 SQL 筛选要把 `rarity` / `attr` / `characterId`
 * 这些字段用 `json_extract()` 从 JSON 列里掏出来，既无法建索引、又要依赖设备 SQLite
 * 的 JSON1 支持（各版本行为不一致）。而「综合力」这种要跨字段求和的排序，
 * 在 SQL 里几乎写不出来。放在内存里，筛选和排序都是瞬时的。
 */
class CardRepository(
    private val dao: MasterDao,
    scope: CoroutineScope,
) {

    /**
     * 全部卡牌，按发布日期倒序。
     *
     * 末尾的 [flowOn] 是必须的：1447 条 JSON 的解析 + 排序如果跑在收集者所在的线程
     * （Compose 里就是主线程），每次数据变化都会卡一下界面。
     *
     * 外层 [stateIn] 同样关键：**它让解析结果在页面之间共享并缓存**。
     * 没有它的话，每次从首页切到图鉴（或切回来）都会重新收集 Flow → **把 1447 条
     * 重新解析一遍**（几百毫秒）→ 这段时间列表是空的，用户就会看到骨架闪一下。
     * `WhileSubscribed(5000)` 表示「最后一个订阅者离开后保留 5 秒」，
     * 所以页面来回切是瞬时的；而真正没有任何界面在看它超过 5 秒后，缓存会被释放，
     * 不至于长期占内存。
     */
    val cards: StateFlow<List<Card>> = dao.observeAll(TABLE_CARDS)
        .map { rows ->
            rows.mapNotNull(::parseCard).sortedByDescending { it.releaseAt }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 全部角色。 */
    val characters: Flow<List<PjskCharacter>> = dao.observeAll(TABLE_CHARACTERS)
        .map { rows -> rows.mapNotNull(::parseCharacter) }
        .flowOn(Dispatchers.Default)

    /**
     * 卡牌剧情，按 `cardId` 分组、组内按前篇/后篇排序。
     *
     * 为什么整表读进来：2768 行、投影后 1.6 MB，比卡牌表还小一个量级，
     * 而且详情页要按卡一次性取两篇，分组后是 O(1) 命中。
     */
    val cardEpisodes: Flow<Map<Int, List<CardEpisode>>> = dao.observeAll(TABLE_CARD_EPISODES)
        .map { rows ->
            rows.mapNotNull(::parseCardEpisode)
                .groupBy { it.cardId }
                .mapValues { (_, list) -> list.sortedBy { it.part } }
        }
        .flowOn(Dispatchers.Default)

    /**
     * 素材 id → 名称（简中名优先）。
     *
     * 剧情开放消耗、突破消耗在 master data 里**只给 `resourceId`**，
     * 名字要查这张表（`materials.json`，280 行，随快照内置）。
     * 简中服只覆盖了前 200 条，缺的会回退成日文名 —— 所以这里不做「查不到就不显示」，
     * 而是**尽量给一个名字**，实在没有时才由调用方兜底成 `素材 #id`。
     */
    val materials: Flow<Map<Int, String>> = dao.observeAll(TABLE_MATERIALS)
        .map { rows ->
            rows.mapNotNull { row ->
                val name = row.nameZh?.takeIf { it.isNotBlank() }
                    ?: row.name?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                row.id to name
            }.toMap()
        }
        .flowOn(Dispatchers.Default)

    /**
     * 素材 id → **日文**名（`materials.json` 里的 `name`，不走简中叠加层）。
     *
     * 为什么单独留一份：卡片详情的「卡牌剧情」板块按用户要求显示**日文**道具名
     * （クールピース / クールジェム / ミラクルジェム），而剧情详情页显示中文。
     * 两张表都从同一批行来，只是一张取 `name`、一张取 `nameZh ?: name`。
     */
    val materialsJapanese: Flow<Map<Int, String>> = dao.observeAll(TABLE_MATERIALS)
        .map { rows ->
            rows.mapNotNull { row ->
                val name = row.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                row.id to name
            }.toMap()
        }
        .flowOn(Dispatchers.Default)

    /**
     * 卡牌 id → 所属活动 id（`eventCards.json`）。
     *
     * 只想用它做一次跳转，所以解析成最简单的 Map，省得把整张 eventCards 的行结构暴露出去。
     */
    val cardEventIds: Flow<Map<Int, Int>> = dao.observeAll(TABLE_EVENT_CARDS)
        .map { rows ->
            rows.mapNotNull { row ->
                val obj = row.jsonObject() ?: return@mapNotNull null
                val cardId = obj.intOrNull("cardId") ?: return@mapNotNull null
                val eventId = obj.intOrNull("eventId") ?: return@mapNotNull null
                cardId to eventId
            }.toMap()
        }
        .flowOn(Dispatchers.Default)

    /** 活动 id → 活动（名字 + logo 素材名）。 */
    val events: Flow<Map<Int, CardEvent>> = dao.observeAll(TABLE_EVENTS)
        .map { rows -> rows.mapNotNull(::parseCardEvent).associateBy { it.id } }
        .flowOn(Dispatchers.Default)

    /**
     * 技能表。`card.skillId` → 技能效果模板（描述里带 `{{1;v}}` 这类占位符，
     * 由 [SkillInfo.describe] 按技能等级替换成实际数值）。
     */
    val skills: Flow<Map<Int, SkillInfo>> = dao.observeAll(TABLE_SKILLS)
        .map { rows -> rows.mapNotNull(::parseSkill).associateBy { it.id } }
        .flowOn(Dispatchers.Default)

    /**
     * 稀有度等级上限。优先用同步下来的 `cardRarities.json`，没有该表时回退到内置表。
     *
     * 之所以要读数据库而不是直接把上限写死在代码里：上限属于「官方可能调整的数据」，
     * 而这个 App 的前提就是「数据能跟着官方更新走」。内置表只是首次同步前的兜底。
     */
    val rarityCaps: Flow<Map<String, RarityCap>> = dao.observeAll(TABLE_RARITIES)
        .map { rows ->
            val fromDb = rows.mapNotNull { row ->
                val obj = row.jsonObject() ?: return@mapNotNull null
                val key = obj.str("cardRarityType") ?: row.name ?: return@mapNotNull null
                val maxLevel = obj.intOrNull("maxLevel") ?: return@mapNotNull null
                key to RarityCap(maxLevel, obj.intOrNull("trainingMaxLevel"))
            }.toMap()
            // 远端的值覆盖内置值
            FALLBACK_RARITY_CAPS + fromDb
        }
        .flowOn(Dispatchers.Default)

    private companion object {
        const val TABLE_CARDS = "cards.json"
        const val TABLE_CHARACTERS = "gameCharacters.json"
        const val TABLE_RARITIES = "cardRarities.json"
        const val TABLE_SKILLS = "skills.json"
        const val TABLE_CARD_EPISODES = "cardEpisodes.json"
        const val TABLE_MATERIALS = "materials.json"
        const val TABLE_EVENT_CARDS = "eventCards.json"
        const val TABLE_EVENTS = "events.json"
    }
}

/** 排序方式。 */
enum class CardSort(val label: String) {
    RELEASE_DESC("最新发布"),
    RARITY_DESC("稀有度"),
    POWER_DESC("综合力"),
}

/**
 * 列表页的筛选条件。
 *
 * **五个维度都是多选**（对齐 sekai.best / pjsk.moe 的筛选逻辑）：
 *  - **同一维度内取「或」**：选了 HAPPY + CUTE，两种属性的卡都留下；
 *  - **跨维度取「且」**：属性选了 HAPPY、组合选了 Vivid BAD SQUAD，就要同时满足。
 *
 * 空集合一律表示「这一维不筛选」。
 * 注意「组合」和「角色」是**互相独立的两维**（旧版是互斥的，选了角色就清空组合，
 * 那样没法表达「某组合里的某几个角色」）。
 */
data class CardQuery(
    val search: String = "",
    val rarityKeys: Set<String> = emptySet(),
    val attrs: Set<CardAttr> = emptySet(),
    val characterIds: Set<Int> = emptySet(),
    val units: Set<CardUnit> = emptySet(),
    /** 卡池类型（常驻 / 生日 / 期间限定 / 联动限定…），口径见 [CardSupplyType]。 */
    val supplyTypes: Set<CardSupplyType> = emptySet(),
    val sort: CardSort = CardSort.RELEASE_DESC,
) {
    /** 已启用的筛选**维度个数**（不是选中项个数），用于顶部「筛选」按钮上的角标。 */
    val activeCount: Int
        get() = (if (rarityKeys.isNotEmpty()) 1 else 0) +
            (if (attrs.isNotEmpty()) 1 else 0) +
            (if (characterIds.isNotEmpty()) 1 else 0) +
            (if (units.isNotEmpty()) 1 else 0) +
            (if (supplyTypes.isNotEmpty()) 1 else 0)

    val isFiltering: Boolean
        get() = search.isNotBlank() || activeCount > 0
}

/**
 * [CardQuery] 的字段个数。
 *
 * 改动字段时**必须同步改这个数**：它是编解码的结构契约，
 * 旧数据对不上就会被 [cardQueryFromSaveText] 整条丢掉并退回默认值（而不是崩）。
 */
private const val CARD_QUERY_FIELDS = 7

/**
 * 把筛选条件存成一行文本 / 从一行文本还原。
 *
 * 用途是 `rememberSaveable`：跳到卡牌详情再回来时，**筛选条件不能丢**。
 * 只靠 `remember` 会丢 —— 这一步的来龙去脉见 docs/PROJECT_NOTES.md §11.13。
 *
 * 两个刻意的设计：
 *  - `search`（自由文本）放在**最后一个字段**：`decodeQueryFields` 带 `limit`，
 *    搜索词里就算混进分隔符也不会把前面几个字段撞错位；
 *  - 枚举只存 `name()`，**反查不到就当没选**（不抛异常）。这样以后枚举改名/删项，
 *    旧文本也只是少一个筛选项，不会让界面崩掉。
 */
fun CardQuery.toSaveText(): String = encodeQueryFields(
    encodeQueryItems(rarityKeys),
    encodeQueryItems(attrs.map { it.name }),
    encodeQueryItems(characterIds.map { it.toString() }),
    encodeQueryItems(units.map { it.name }),
    encodeQueryItems(supplyTypes.map { it.name }),
    sort.name,
    search,
)

/** [toSaveText] 的逆运算。任何结构对不上的输入都退回空条件，绝不抛异常。 */
fun cardQueryFromSaveText(raw: String): CardQuery {
    val f = decodeQueryFields(raw, CARD_QUERY_FIELDS) ?: return CardQuery()
    return CardQuery(
        rarityKeys = decodeQueryItems(f[0]).toSet(),
        attrs = decodeQueryItems(f[1]).mapNotNullTo(LinkedHashSet()) { name ->
            CardAttr.entries.firstOrNull { it.name == name }
        },
        characterIds = decodeQueryItems(f[2]).mapNotNullTo(LinkedHashSet()) { it.toIntOrNull() },
        units = decodeQueryItems(f[3]).mapNotNullTo(LinkedHashSet()) { name ->
            CardUnit.entries.firstOrNull { it.name == name }
        },
        supplyTypes = decodeQueryItems(f[4]).mapNotNullTo(LinkedHashSet()) { name ->
            CardSupplyType.entries.firstOrNull { it.name == name }
        },
        sort = CardSort.entries.firstOrNull { it.name == f[5] } ?: CardSort.RELEASE_DESC,
        search = f[6],
    )
}

/**
 * 这张卡算不算某个组合的卡。
 *
 * 只看角色的 `unit` 是不够的 —— 虚拟歌手的卡会「借调」到各个团：
 * 实测 `cards.supportUnit` 在虚拟歌手的 350 张卡里取到了
 * `light_sound` 51 / `idol` 51 / `street` 57 / `theme_park` 53 / `school_refusal` 43，
 * 另外 95 张是 `none`（纯虚拟歌手卡）。这些带 `supportUnit` 的卡在游戏里就是那个团的卡
 * （活动加成、编队都按那个团算），所以：
 *
 *  - 选「Leo/need」时，要连「初音ミク（Leo/need）」这类卡一起筛出来；
 *  - 选「VIRTUAL SINGER」时，**全部** 350 张虚拟歌手角色的卡都算。
 *
 * 代价是**一张卡可以同时属于两个组合**（VIRTUAL SINGER + 借调的那个团）。
 * 这是刻意的取舍：否则「每个团里的虚拟歌手」在组合筛选下永远查不到。
 *
 * @param characterUnitKey 卡主的 `gameCharacters.unit`（`piapro` / `light_sound` / …）
 */
fun Card.belongsToUnit(unit: CardUnit, characterUnitKey: String?): Boolean {
    val characterUnit = CardUnit.of(characterUnitKey) ?: return false
    if (unit == CardUnit.VIRTUAL_SINGER) return characterUnit == CardUnit.VIRTUAL_SINGER
    if (characterUnit == unit) return true
    // 虚拟歌手按 supportUnit 借调到该团
    return characterUnit == CardUnit.VIRTUAL_SINGER && CardUnit.of(supportUnit) == unit
}

/**
 * 满级综合力。
 *
 * 有特训的卡（3★/4★）按**特训后**满级算，并把特训加成算进去 —— 这才是玩家口中
 * 「这张卡的综合力」。没有特训的卡（1★/2★/生日卡）按自身满级算。
 */
fun Card.maxTotal(caps: Map<String, RarityCap>): Int {
    val cap = caps[rarityKey]
    val level = cap?.trainingMaxLevel ?: cap?.maxLevel ?: maxLevel
    return statsAt(level, applyTrainingBonus = cap?.trainingMaxLevel != null).total
}

/** 该卡「特训前满级」的等级。 */
fun Card.normalMaxLevel(caps: Map<String, RarityCap>): Int =
    caps[rarityKey]?.maxLevel ?: maxLevel

/** 该卡「特训后满级」的等级；没有特训后卡面时返回 null。 */
fun Card.trainedMaxLevel(caps: Map<String, RarityCap>): Int? =
    caps[rarityKey]?.trainingMaxLevel

/**
 * 筛选 + 排序。纯函数，方便单独验证。
 *
 * 搜索同时匹配中文名、日文原名与卡牌 id，这样「输入 1378 直接跳到那张卡」也能用。
 */
fun filterCards(
    source: List<Card>,
    query: CardQuery,
    caps: Map<String, RarityCap>,
    characterUnits: Map<Int, String?> = emptyMap(),
): List<Card> {
    val keyword = query.search.trim()
    val filtered = source.filter { card ->
        // 同维「或」、跨维「且」——每一维只在非空时才起作用
        if (query.rarityKeys.isNotEmpty() && card.rarityKey !in query.rarityKeys) return@filter false
        if (query.attrs.isNotEmpty()) {
            val attr = card.attr ?: return@filter false
            if (attr !in query.attrs) return@filter false
        }
        if (query.characterIds.isNotEmpty() && card.characterId !in query.characterIds) {
            return@filter false
        }
        if (query.units.isNotEmpty()) {
            val unitKey = characterUnits[card.characterId]
            // 注意：这里用 belongsToUnit 而不是 `it.key == unitKey` —— 后者的写法
            // 会把「借调」到各团的虚拟歌手卡漏掉（见 belongsToUnit 的说明）
            if (query.units.none { card.belongsToUnit(it, unitKey) }) return@filter false
        }
        if (query.supplyTypes.isNotEmpty()) {
            // 查不到 cardSupplyId 的卡一律不留下：宁可它只出现在「不筛选」的结果里，
            // 也不要把它当成常驻塞进「常驻」这一项里（那是编造数据）。
            val supply = card.supplyType ?: return@filter false
            if (supply !in query.supplyTypes) return@filter false
        }
        if (keyword.isNotEmpty()) {
            val hit = card.nameZh?.contains(keyword, ignoreCase = true) == true ||
                card.nameJa?.contains(keyword, ignoreCase = true) == true ||
                card.id.toString() == keyword
            if (!hit) return@filter false
        }
        true
    }

    return when (query.sort) {
        CardSort.RELEASE_DESC -> filtered.sortedByDescending { it.releaseAt }
        CardSort.RARITY_DESC -> filtered.sortedWith(
            // 稀有度相同时按发布时间倒序，保证顺序稳定（否则同一星级内部顺序会随机跳动）
            compareByDescending<Card> { it.stars }.thenByDescending { it.releaseAt },
        )
        CardSort.POWER_DESC -> filtered.sortedWith(
            compareByDescending<Card> { it.maxTotal(caps) }.thenByDescending { it.releaseAt },
        )
    }
}
