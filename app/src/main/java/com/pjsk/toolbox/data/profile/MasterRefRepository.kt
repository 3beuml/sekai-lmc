package com.pjsk.toolbox.data.profile

import com.pjsk.toolbox.data.db.MasterDao
import com.pjsk.toolbox.data.db.MasterRowEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * master data 里「给玩家数据当参照物」的那几张表。
 *
 * 和 [PlayerRepository] 分工不同：那个管**联网 + 玩家快照**，这个只读**内置/同步进来的 master data**，
 * 用来把快照里的原始 id 翻译成人能读的东西：
 *
 * | 用途 | 表 | 为什么需要 |
 * | --- | --- | --- |
 * | 羁绊组名 | `bonds.json` + `gameCharacterUnits.json` | `userBonds` 只给 `bondsGroupId`（如 10103） |
 * | 角色专属色 | `gameCharacterUnits.json` 的 `colorCode` | 一歌 `#33aaee`、咲希 `#ffdd44`… |
 *
 * ⚠️ 两个已知的坑都写在 [PlayerModels] 里：`bonds.characterId1/2` 是 style id 不是角色 id；
 * 同一角色在 `gameCharacterUnits` 里可能有多行（虚拟歌手的多个 style）。
 */
class MasterRefRepository(private val dao: MasterDao) {

    /** `groupId` → 羁绊的两个基础角色。表没下全时是空 map（界面自行兜底）。 */
    val bondPairs: Flow<Map<Int, BondPair>> =
        combineBonds(
            rowsOf(TABLE_BONDS),
            rowsOf(TABLE_CHARACTER_UNITS),
        )

    /** 角色 id → 官方主题色（`#rrggbb`）。 */
    val characterColors: Flow<Map<Int, String>> =
        rowsOf(TABLE_CHARACTER_UNITS).map { rows -> parseCharacterColors(rows) }

    private fun rowsOf(table: String): Flow<List<String>> =
        dao.observeAll(table).map { rows: List<MasterRowEntity> -> rows.map { row -> row.data } }

    private fun combineBonds(
        bonds: Flow<List<String>>,
        units: Flow<List<String>>,
    ): Flow<Map<Int, BondPair>> = kotlinx.coroutines.flow.combine(bonds, units) { bondRows, unitRows ->
        resolveBondPairs(
            rows = parseBondRows(bondRows),
            styleToBase = parseStyleToBase(unitRows),
        )
    }

    private companion object {
        const val TABLE_BONDS = "bonds.json"
        const val TABLE_CHARACTER_UNITS = "gameCharacterUnits.json"
    }
}
