package com.pjsk.toolbox.data.home

import com.pjsk.toolbox.data.story.StoryStatus

/**
 * 卡池身上的标签。
 *
 * **一个池可以同时是「限定 + 复刻 + 进行中」**，所以这里用集合而不是互斥的单值：
 * 列表上的小签可以挂好几枚，筛选时是「包含该标签」而不是单选。
 */
enum class GachaTag(val label: String) {
    ALL("全部"),
    LIMITED("限定"),
    PERMANENT("常驻"),
    RERUN("复刻"),
    BIRTHDAY("生日池"),
    ANNIVERSARY("纪念"),
    ONCE_ONLY("一回限定"),
    RUNNING("进行中"),
    ENDED("已结束"),
    ;

    /**
     * 值不值得在列表行上挂成小签。
     *
     * 「进行中 / 已结束」是**日期算出来的状态**，行上已经有一枚状态徽章了，
     * 再挂一枚小签是重复；「全部」是筛选用的伪标签。
     */
    val showsOnRow: Boolean
        get() = this != ALL && this != RUNNING && this != ENDED
}

/**
 * 名字里带这三个字的就是复刻池。
 *
 * 为什么只看名字：**没有任何官方字段标记复刻**。试过按 `assetbundleName`（素材名）
 * 找「同一个 banner 被多个池子复用」——实测 1004 个池里只有 5 组素材名被复用，
 * 而且全是每日票券池和路径池，不是复刻池，这条路走不通。
 * 而名字这条口径实测是干净的：`[復刻]` 出现 260 次，260 个池全都长这样，
 * 没有别的写法（不是 `復刻` 裸词、也不是 `【復刻】`）。
 */
fun isRerunGachaName(name: String): Boolean = name.contains("[復刻]")

/**
 * 算出一个卡池的标签。纯函数，方便单独验证。
 *
 * @param isLimitedCard 这张卡是不是限定卡（查 `cardSupplies`，见 [com.pjsk.toolbox.data.card.CardSupplyType]）
 * @param characterNames 全部角色的日文名（生日池的判据是名字开头方括号里就是角色名）
 */
fun gachaTagsOf(
    gacha: HomeGacha,
    isLimitedCard: (Int) -> Boolean,
    characterNames: Set<String>,
    now: Long,
): Set<GachaTag> {
    val tags = mutableSetOf<GachaTag>()
    if (gacha.cardIds.any(isLimitedCard)) tags += GachaTag.LIMITED else tags += GachaTag.PERMANENT
    val name = gacha.name
    if (isRerunGachaName(name)) tags += GachaTag.RERUN
    if (name.contains("[1回限定]")) tags += GachaTag.ONCE_ONLY
    // 「纪念」在数据里没有字段，只有名字：周年纪念池与完结章池（这两类都不是复刻）
    if (name.contains("周年記念") || name.contains("フィナーレチャプター")) tags += GachaTag.ANNIVERSARY
    // 生日池：名字方括号里就是角色名（实测 `[桐谷遥]…` 这种，每个角色 6 个）
    Regex("^[\\[【]([^\\]】]+)[\\]】]").find(name)?.groupValues?.get(1)?.let { first ->
        if (first in characterNames) tags += GachaTag.BIRTHDAY
    }
    if (now in gacha.startAt until gacha.endAt) tags += GachaTag.RUNNING else tags += GachaTag.ENDED
    return tags
}

/** 行上要挂的小签，按枚举顺序（限定 → 复刻 → 生日池 → 纪念 → 一回限定）。 */
fun rowTagsOf(tags: Set<GachaTag>): List<GachaTag> =
    GachaTag.entries.filter { it.showsOnRow && it in tags }

/**
 * 每个池子的**首发卡**：这张卡第一次能在卡池里抽到，就是在这个池子里。
 *
 * 为什么要算这个：池内卡数中位 150 张，而其中位只有 **3 张**是这个池子独有的，
 * 其余 97% 都是往期池早就出现过的老卡 —— 所以池子详情的重点应该由
 * 「UP + 首发」组成，而不是老老实实铺 150 张。
 *
 * 算法：把全部池子按开始时间**升序**走一遍，第一次见到的卡记在这个池子名下。
 * 验证过这个定义是站得住的：260 个复刻池的首发卡是 **0 张**（复刻池里当然没有首发），
 * 361 个池子各有首发卡、合计 1363 张。
 */
fun debutCardIdsByGacha(gachas: List<HomeGacha>): Map<Int, Set<Int>> {
    val seen = HashSet<Int>()
    val result = HashMap<Int, Set<Int>>()
    gachas.sortedBy { it.startAt }.forEach { gacha ->
        val debut = gacha.cardIds.filter { seen.add(it) }
        if (debut.isNotEmpty()) result[gacha.id] = debut.toSet()
    }
    return result
}

/**
 * 池内**重点卡**：UP + 本池首发。
 *
 * 顺序：UP 在前（**保持官方 `gachaPickups` 里的顺序** —— 那是有意义的，不是 id 升序），
 * 然后是其余首发卡，按发售时间倒序（新的在前）。
 */
fun gachaFocusCardIds(
    pool: List<Int>,
    upCardIds: List<Int>,
    debutCardIds: Set<Int>,
    releaseAtOf: (Int) -> Long,
): List<Int> {
    val inPool = pool.toHashSet()
    val up = upCardIds.filter { it in inPool }.distinct()
    val upSet = up.toHashSet()
    val debut = pool.filter { it in debutCardIds && it !in upSet }
        .sortedByDescending(releaseAtOf)
    return up + debut
}

/**
 * 池内**其余卡**（重点卡之外的部分），按发售时间倒序。
 *
 * 这里刻意不再用数据自带的顺序：实测 **1002 / 1004 个池的顺序就是卡 id 升序**，
 * 于是每个池子打开都是 `#2 #3 #4 #6 #7 #10…`，跟别的池子长得一模一样 ——
 * 用户看到的就是「差异化不大」。按发售时间倒序至少让「哪些是新的」看得见。
 */
fun gachaRestCardIds(
    pool: List<Int>,
    focusCardIds: List<Int>,
    releaseAtOf: (Int) -> Long,
): List<Int> {
    val focus = focusCardIds.toHashSet()
    return pool.filter { it !in focus }.sortedByDescending(releaseAtOf)
}

/** 卡池的进行状态（按日期算，和活动剧情同一套规则）。 */
fun statusOfGacha(gacha: HomeGacha, now: Long = System.currentTimeMillis()): StoryStatus = when {
    now < gacha.startAt -> StoryStatus.UPCOMING
    now >= gacha.endAt -> StoryStatus.ENDED
    else -> StoryStatus.RUNNING
}
