package com.pjsk.toolbox.data.card

/**
 * 卡池类型（这张卡当初是怎么来的）。
 *
 * 口径来自官方 `cardSupplies.json` —— 整张表只有 7 行，`cards.json` 里每张卡的
 * `cardSupplyId` 指向其中一行。所以「限定 / 常驻」这类标签**不需要任何猜测**，
 * 也不用按卡名或稀有度去推。
 *
 * 实测 1452 张卡的分布：常驻 925 / 生日 130 / 期间限定 218 / 组合活动限定 52 /
 * 联动限定 82 / CF 27 / BF 18（合计 1452，无遗漏）。
 *
 * 两处刻意的取舍：
 *  - **中文标签写死在代码里**：`cardSupplies.json` 里只有 `cardSupplyType` 这类
 *    机器名（`term_limited` / `collaboration_limited`…），简中服叠加层里没有这张表，
 *    拿不到官方中文名。所以这里按玩家通用叫法写死一份（CF / BF 就是
 *    カラフルフェス / ブルームフェス 的通用缩写，游戏自己的素材名也是这个叫法）。
 *  - **[BIRTHDAY] 不算「限定」**：生日卡确实是限定获取，但它是按角色生日循环开放的
 *    另一套池子，混进「限定」里会让筛选结果变得没有意义。列表页的角标也不给它
 *    （稀有度筛选里本来就有「生日」这一项了）。
 */
enum class CardSupplyType(val id: Int, val label: String, val limited: Boolean) {
    NORMAL(1, "常驻", false),
    BIRTHDAY(2, "生日", false),
    TERM_LIMITED(3, "期间限定", true),
    COLORFUL_FESTIVAL_LIMITED(4, "CF 限定", true),
    BLOOM_FESTIVAL_LIMITED(5, "BF 限定", true),
    UNIT_EVENT_LIMITED(6, "组合活动限定", true),
    COLLABORATION_LIMITED(7, "联动限定", true),
    ;

    companion object {
        /** `cards.cardSupplyId` → 类型。未知 id（官方以后新增一类）返回 null。 */
        fun of(supplyId: Int?): CardSupplyType? =
            entries.firstOrNull { it.id == supplyId }
    }
}

/** 这张卡的类型。数据里没有 `cardSupplyId` 时返回 null（不假装是常驻）。 */
val Card.supplyType: CardSupplyType?
    get() = CardSupplyType.of(supplyId)

/**
 * 列表页卡面角标上写的字：**只写「限定」两个字**，具体是哪一类留给卡牌详情。
 *
 * 原因：一屏能看到三四个格子，每格都挂一串「缤纷祭限定」会把卡面压得很难看；
 * 而「是不是限定」才是筛选和浏览时真正要一眼分出来的信息。
 * 常驻卡与生日卡返回 null —— 不标，省得满屏都是角标。
 */
val Card.listTagLabel: String?
    get() = if (supplyType?.limited == true) "限定" else null
