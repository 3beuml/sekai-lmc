package com.pjsk.toolbox.data.score

import com.pjsk.toolbox.data.profile.MusicResult
import com.pjsk.toolbox.data.profile.difficultyLabel
import com.pjsk.toolbox.data.profile.difficultyOrder

/**
 * Best30 的一条记录。
 *
 * [value] 是计入平均值的那个数：**AP → 定数本身，FC → 定数 − 1**。
 * 这不是我编的规则，是社区 B30（`auburnsummer/pjskb30`）的口径：
 * 一局 AP 等价于"打满了这个谱面的定数"，一局 FC 则是差一分。
 */
data class Best30Entry(
    val musicId: Int,
    val difficulty: String,
    val constant: Double,
    val allPerfect: Boolean,
    val value: Double,
) {
    val difficultyLabel: String get() = difficultyLabel(difficulty)
}

/** Best30 的结果。[poolSize] 是**合格谱面总数**（截断前），用来判断"够不够 30 条"。 */
data class Best30Result(
    val entries: List<Best30Entry>,
    val average: Double,
    val poolSize: Int,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /** 是否凑满了 30 条（没凑满时这个平均值与社区不可比，界面上要说出来）。 */
    val isComplete: Boolean get() = entries.size >= LIMIT

    companion object {
        const val LIMIT = 30
        val EMPTY = Best30Result(emptyList(), 0.0, 0)
    }
}

/**
 * 计算 Best30。
 *
 * ## 规则（用户已拍板，与 pjskb30 一致）
 *
 * 1. **候选池** = 所有"定数已知"的谱面（定数表只有 Expert / Master / Append）；
 * 2. 只有 **AP 或 FC** 的成绩计入 —— 高分（`highScore`）完全不看；
 * 3. `AP → 定数`、`FC → 定数 − 1`；
 * 4. 按这个值从高到低取前 30 条，**算术平均**。
 *
 * ## 几个刻意的处理
 *
 * - **同一谱面去重**：快照里同一 (musicId, 难度) 偶尔有重复行，这里取**较高**的那条
 *   （AP 一定高于同谱面的 FC），否则同一个谱面会把平均值撑高两次。
 * - **排序要稳定**：值相同时按 musicId、再按难度先后，避免每次进页面顺序跳动。
 * - **不补 0**：不足 30 条就按实际条数平均（并让界面显示"N / 30"）——
 *   用 0 填充会把平均值打到地板上，那是纯粹的错误信息。
 * - 定数表缺失（表没读出来）时返回 [Best30Result.EMPTY]，界面据此整块不显示。
 */
fun computeBest30(
    results: List<MusicResult>,
    constants: Map<Int, Map<String, Double>>,
    limit: Int = Best30Result.LIMIT,
): Best30Result {
    if (constants.isEmpty() || results.isEmpty()) return Best30Result.EMPTY

    // (musicId, 难度) → 最好的一条
    val best = LinkedHashMap<Pair<Int, String>, Best30Entry>()
    for (r in results) {
        val difficulty = r.difficulty.lowercase()
        val constant = constants[r.musicId]?.get(difficulty) ?: continue
        val value = when {
            r.allPerfect -> constant
            r.fullCombo -> constant - 1.0
            else -> continue
        }
        val key = r.musicId to difficulty
        val entry = Best30Entry(
            musicId = r.musicId,
            difficulty = difficulty,
            constant = constant,
            allPerfect = r.allPerfect,
            value = value,
        )
        val old = best[key]
        // 同谱面取较高值；相等时保留 AP 那条（展示用的徽章要准）
        if (old == null || entry.value > old.value || (entry.value == old.value && entry.allPerfect && !old.allPerfect)) {
            best[key] = entry
        }
    }

    if (best.isEmpty()) return Best30Result.EMPTY

    val sorted = best.values.sortedWith(
        compareByDescending<Best30Entry> { it.value }
            .thenBy { it.musicId }
            .thenBy { difficultyOrder(it.difficulty) },
    )
    val top = sorted.take(limit.coerceAtLeast(1))
    val average = top.sumOf { it.value } / top.size
    return Best30Result(entries = top, average = average, poolSize = sorted.size)
}

/**
 * 平均值取两位小数用于展示（`32.4666…` → `32.47`）。
 *
 * 单独抽出来是因为界面上只该出现这一种格式：有的地方写两位、有的地方写三位，
 * 用户会以为两处算的不是同一个东西。
 */
fun formatBest30(average: Double): String = String.format(java.util.Locale.US, "%.2f", average)
