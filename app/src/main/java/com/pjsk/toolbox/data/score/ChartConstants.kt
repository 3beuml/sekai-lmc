package com.pjsk.toolbox.data.score

/**
 * 谱面定数（"定数" = 该谱面的理论难度值，社区用它算 Best30）。
 *
 * ## 为什么要内置一份人造表
 *
 * 官方 master data 里**没有**定数：`musicDifficulties.json` 只有 `playLevel`
 * 这样的整数等级（Lv.32），而定数是带小数的社区标定值（32.7）。缺了它就算不出 Best30。
 *
 * 数据由 `tools/pjskb30/build-asset.mjs` 从 `moe.exmeaning.com/data/pjskb30/merged_chart.csv`
 * 生成（上游项目 github.com/auburnsummer/pjskb30，**CC0-1.0**），产物是
 * `app/src/main/assets/pjskb30/chart-constants.csv`：
 *
 * ```
 * musicId,difficulty,constant
 * 1,expert,22.9
 * ```
 *
 * 只收录 Expert / Master / Append —— 上游也只收录这三档，Easy / Normal / Hard 没有定数，
 * 因此它们永远不会进 Best30 的候选池（这与社区通行的 B30 口径一致）。
 *
 * ⚠️ 这是本项目**唯一**一份非官方数据源。所有断言与口径都写在 `tools/logic-check` §26。
 */

/**
 * 解析定数表文本 → `musicId → 难度 → 定数`。
 *
 * 容错但不猜：
 *  - `#` 开头的注释行、表头行跳过；
 *  - 列数不对、id 不是正整数、定数不是正数 —— 跳过那一行；
 *  - 同一 (id, 难度) 出现多次时**取最大值**（生成脚本已经去重，这里再兜一层：
 *    上游怎么变，运行时都不会出现"同一谱面两个定数"）；
 *  - 难度统一转小写。
 */
fun parseChartConstants(text: String): Map<Int, Map<String, Double>> {
    val out = HashMap<Int, MutableMap<String, Double>>(1_800)
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        val cells = line.split(',')
        if (cells.size < 3) continue
        val id = cells[0].trim().toIntOrNull() ?: continue
        if (id <= 0) continue
        val difficulty = cells[1].trim().lowercase()
        if (difficulty.isEmpty()) continue
        val constant = cells[2].trim().toDoubleOrNull() ?: continue
        if (constant <= 0.0) continue
        val perSong = out.getOrPut(id) { HashMap(3) }
        val previous = perSong[difficulty]
        if (previous == null || constant > previous) perSong[difficulty] = constant
    }
    return out
}

/**
 * 读内置定数表（首次调用读盘并解析，之后返回缓存）。
 *
 * 失败时返回空表而不是抛异常：整个 Best30 板块会因此不显示，
 * 但卡牌/歌曲/我的等其余功能必须照常可用 —— 一个可选的展示功能不该拖垮 App。
 * 读盘约 27 KB，很快；仍然放在调用方的 IO 上下文里（见调用点）。
 */
object ChartConstants {

    @Volatile
    private var cache: Map<Int, Map<String, Double>>? = null

    fun load(openAsset: () -> java.io.InputStream): Map<Int, Map<String, Double>> {
        cache?.let { return it }
        val parsed = runCatching {
            openAsset().use { stream -> parseChartConstants(stream.bufferedReader().readText()) }
        }.getOrDefault(emptyMap())
        // 解析失败（空表）不写缓存：下次进页面还能再试一次，而不是把"空"永久钉住
        if (parsed.isNotEmpty()) cache = parsed
        return parsed
    }
}
