package com.pjsk.toolbox.data.sync

/**
 * 内置数据快照的**行格式版本**。
 *
 * 为什么需要这个东西：内置快照的导入规则是「**表里已经有数据就跳过**」
 * （否则会覆盖用户在线同步到的更新数据，见 [BundledPack] 的说明）。
 * 于是出现过一个反复踩到的陷阱：**改了裁剪器（行格式），老用户升级 App 后
 * 新版数据根本进不去** —— 库里那张表已经有行，导入直接跳过，
 * 而界面需要的新字段永远是空的（上一轮的 `pk` 字段就是这样：
 * 不给它一条重导规则的话，装了 0.1.5 的手机升级到 0.1.6 也不会有 UP 标签）。
 *
 * 判据不能是「sha 变了就重导」：sha 变化可能来自用户在线同步到了**更新的**上游数据，
 * 那时重导反而是降级。所以这里用**独立的格式版本**：只有代码里的这个版本前进了，
 * 才把 [CHANGED] 里登记的表重导一次，导完记下版本，绝不再导第二次。
 *
 * 改裁剪器产出结构时：把 [VERSION] +1，并把受影响的表登记进 [CHANGED]。
 */
object DatapackSchema {

    /** 当前代码期望的行格式版本。改裁剪器产出结构时必须 +1。 */
    const val VERSION = 2

    /**
     * 每个版本**改动了哪些表的行格式**。
     *
     *  - 1：最初的行格式（没有这一步之前的全部历史版本都算 1）；
     *  - 2：`gachas.json` 加了 `pk`（官方 `gachaPickups` 的卡 id）——
     *    卡池详情要靠它标 UP、排重点卡，老行里没有这个字段。
     */
    val CHANGED: Map<Int, List<String>> = mapOf(
        2 to listOf(RowProjectors.GACHAS_FILE),
    )

    /**
     * 从 [installedVersion] 升到 [bundledVersion] 需要**重导**的表。
     *
     * 版本没前进就返回空集（这是绝大多数情况：装上之后一次都不会再重导）。
     */
    fun tablesToReimport(installedVersion: Int, bundledVersion: Int = VERSION): Set<String> {
        if (bundledVersion <= installedVersion) return emptySet()
        return ((installedVersion + 1)..bundledVersion)
            .flatMap { CHANGED[it].orEmpty() }
            .toSet()
    }
}
