package com.pjsk.toolbox.data.sync

import kotlinx.serialization.json.JsonObject

/**
 * 每张表的「字段抽取规格」。
 *
 * 本 App 刻意**不为 40 张表各写一个 data class**：master data 的字段会随游戏版本增删，
 * 硬编码 schema 会让 App 因为官方加了一个字段就崩掉（需求里明确要求「能够根据这些更新」）。
 * 因此每张表统一落成 `MasterRowEntity(id, name, nameZh, sortValue, data)`，
 * 其中 `data` 是原始 JSON 文本，需要具体字段时再用 `JsonObject` 按 key 现取。
 * 这里只声明「哪个 key 是 id」「哪些 key 能当显示名」「中文名去哪找」。
 */
data class TableSchema(
    val fileName: String,
    /**
     * 主键字段名的**候选列表**，按顺序取第一个存在的。
     *
     * 为什么做成列表：master data 里有几种「字典表」**根本没有 `id` 字段**，
     * 例如实测 `cardRarities.json` 的行是
     * `{cardRarityType, seq, maxLevel, trainingMaxLevel, maxSkillLevel}`。
     * 若硬用 `id` 当主键，整张表会被**静默丢弃**（`id` 取不到 → 每行都 return，
     * 导入结果是 0 行，UI 上只显示「空表」，排查起来非常费劲）。这类表改用 `seq`。
     */
    val idKeys: List<String> = listOf("id"),
    /** 显示名候选字段，按顺序取第一个非空字符串。 */
    val nameKeys: List<String> = listOf("name", "title", "prefix"),
    /** 排序键候选字段（数值），取第一个存在的。 */
    val sortKeyNames: List<String> = listOf("seq", "publishedAt", "releaseAt", "id"),
    /**
     * 简中叠加层里中文名的取值路径。
     *
     * 实测依据：日服 `musics.json` **没有** `infos` 字段（只有日文原名 `title`），
     * 而简中版把中文名放在 `infos[0].title`（`title="テオ"` / `infos[0].title="将手"`）。
     * 所以中文名优先走这条路径；若不存在则回退到 [nameKeys]。
     */
    val overlayNamePath: List<String> = listOf("infos", "0", "title"),
    /**
     * 显示名构造器。给了它就用它，不再看 [nameKeys]。
     *
     * 为什么需要：有些表的「名字」是**两个字段拼出来的**。实测 `gameCharacters.json` 的
     * `firstName` 是**姓**、`givenName` 是**名**（日服 1 号角色 = `firstName:"星乃", givenName:"一歌"`），
     * 而简中服的同名表里这两个字段直接是简体中文
     * （`天马 咲希` / `望月 穗波` / `日野森 志步`，对应日服的 `天馬 咲希` / `望月 穂波` / `日野森 志歩`）。
     * 所以「中文名优先」这套机制对角色表必须先把姓和名拼起来才成立。
     */
    val nameBuilder: ((JsonObject) -> String?)? = null,
) {
    /**
     * 该表的字段裁剪器，`null` 表示原样存原始 JSON。
     *
     * 做成计算属性而不是构造参数，是为了让 [TableSchemas.OVERRIDES] 里那几十条声明
     * 不用重复写一遍，也不会漏写 —— 新增表的裁剪规则只需改 [RowProjectors.forFile] 一处。
     */
    val projector: RowProjector? get() = RowProjectors.forFile(fileName)
}

/**
 * 从一行**原始** JSON 里取出显示名。
 *
 * 为什么抽成公共函数：`MasterImporter`（在线同步）和 `BuildDataPack`（构建内置快照）
 * 都要做同一件事。如果两边各写一遍，内置快照里的名字和后来同步进来的名字会不一致，
 * 而且这种不一致很难发现（两边都不报错，只是名字长得不一样）。
 */
fun TableSchema.extractName(obj: JsonObject): String? =
    nameBuilder?.invoke(obj) ?: obj.firstString(nameKeys)

/**
 * 从**简中服**的一行里取出中文名。
 *
 * 优先走 [TableSchema.overlayNamePath]（实测：日服 `musics.json` 没有 `infos` 字段，
 * 中文名在简中版的 `infos[0].title`），取不到再退回显示名规则。
 */
fun TableSchema.extractOverlayName(obj: JsonObject): String? =
    obj.stringAtPath(overlayNamePath) ?: extractName(obj)

object TableSchemas {

    private val OVERRIDES: Map<String, TableSchema> = listOf(
        // 角色
        TableSchema(
            "gameCharacters.json",
            nameBuilder = { obj ->
                listOfNotNull(obj.stringOrNull("firstName"), obj.stringOrNull("givenName"))
                    .joinToString(" ")
                    .ifBlank { null }
            },
        ),
        TableSchema("gameCharacterUnits.json", nameKeys = listOf("name", "title")),
        // ⚠️ 没有 id 字段。实测字段：characterId, characterVoice, birthday, height, school,
        // schoolYear, hobby, specialSkill, favoriteFood, hatedFood, weak, introduction, scenarioId。
        // 主键退回 characterId —— 实测 26 行 26 个唯一值，安全。
        TableSchema(
            "characterProfiles.json",
            idKeys = listOf("id", "characterId"),
            nameKeys = listOf("name", "title"),
        ),
        // ⚠️ 没有 id 字段。实测字段：unit, unitName, unitProfileName, seq, profileSentence, colorCode。
        // 主键退回 seq —— 实测 6 行 6 个唯一值。
        TableSchema(
            "unitProfiles.json",
            idKeys = listOf("id", "seq"),
            nameKeys = listOf("unitName", "name", "title"),
        ),
        // 音乐
        TableSchema("musics.json"),
        TableSchema("musicDifficulties.json", nameKeys = listOf("musicDifficulty", "name")),
        TableSchema("musicVocals.json", nameKeys = listOf("caption", "name", "title")),
        TableSchema("musicTags.json", nameKeys = listOf("name", "title")),
        TableSchema("musicArtists.json", nameKeys = listOf("name", "title")),
        TableSchema("musicCategories.json", nameKeys = listOf("musicCategoryName", "name")),
        // 活动
        TableSchema("events.json", nameKeys = listOf("name", "title")),
        TableSchema("eventStories.json", nameKeys = listOf("title", "name")),
        TableSchema("eventCards.json", nameKeys = listOf("name", "title")),
        // ⚠️ 没有 id 字段，而且**不能拿 seq 当主键**：实测 136 行的 seq 全是 1
        // （只有 1 个唯一值），用它会把整张表压成 1 行。
        // 改用 eventId —— 实测 136 行 136 个唯一值（每个活动恰好一首活动曲）。
        TableSchema(
            "eventMusics.json",
            idKeys = listOf("id", "eventId"),
            nameKeys = listOf("name", "title"),
        ),
        TableSchema("eventItems.json", nameKeys = listOf("name", "title")),
        TableSchema("worldBlooms.json", nameKeys = listOf("name", "title")),
        // 贴纸
        TableSchema("stamps.json", nameKeys = listOf("name", "title")),
        // 卡牌
        // 排序用 releaseAt（默认「最新发布」），不能用默认候选里的 seq —— seq 是官方图鉴序号，
        // 同一批上线的卡 seq 相同，排序会不稳定。
        TableSchema(
            "cards.json",
            nameKeys = listOf("prefix", "name", "title"),
            sortKeyNames = listOf("releaseAt", "id"),
        ),
        // cardRarities.json 没有 id：主键退回 seq（100/200/300/400/500，实测 5 行 5 个唯一值），
        // 而「哪个稀有度」这个语义信息正好就是它的 cardRarityType，所以拿它当显示名。
        TableSchema(
            "cardRarities.json",
            idKeys = listOf("id", "seq"),
            nameKeys = listOf("cardRarityType", "name"),
            sortKeyNames = listOf("seq"),
        ),
        // ⚠️ 没有 id，且**不能拿 seq 当主键**：实测 4 行的 seq 全是 1，只剩 1 行。
        // 用 resourceBoxId —— 实测 4 行 4 个唯一值。
        TableSchema(
            "cardExchangeResources.json",
            idKeys = listOf("id", "resourceBoxId"),
            nameKeys = listOf("cardRarityType", "name"),
        ),
        // ⚠️ 没有 id，且**绝对不能拿 cardId 当主键**：实测 2345 行里 cardId 只有 705 个唯一值，
        // 用它会静默丢掉 1640 行（一张卡可以对应多套 3D 服装）。
        // 用 costume3dId —— 实测 2345 行 2345 个唯一值。
        TableSchema(
            "cardCostume3ds.json",
            idKeys = listOf("id", "costume3dId"),
        ),
        TableSchema("skills.json", nameKeys = listOf("description", "name")),
        // 素材表。`id` 就是剧情/突破消耗里的 `resourceId`，名字在 `name`
        // （简中服叠加层给的是「帅气碎片」「奇迹宝石」这类中文名）。
        TableSchema("materials.json", nameKeys = listOf("name", "title")),
        // 剧情表。两张都是「一行含 episodes[]」的嵌套结构，顶层拿不到剧集名，
        // 所以显示名只能用组合 key / 章节标题。
        // ⚠️ 主键**必须能取到数字**：这张表没有 `id`，早先我把回退写成 `unit`（字符串），
        // 结果 `importTable` 取不到主键就**整表抛异常跳过** —— 而且不报错，
        // 界面上只表现为「unitStories.json 未下载」，主线剧情整块空白，查了好几轮才找到。
        // `seq` 实测是 3,2,1,6,4,5（6 个唯一值）✅
        TableSchema("unitStories.json", idKeys = listOf("id", "seq"), nameKeys = listOf("unit")),
        TableSchema("specialStories.json", nameKeys = listOf("title", "name")),
        // 扭蛋
        TableSchema("gachas.json", nameKeys = listOf("name", "title")),
        TableSchema("gachaTabs.json", nameKeys = listOf("name", "title")),
        // ⚠️ 这里原来还有 `areas.json` / `actionSets.json`（区域对话）和 4 张服装表：
        //  - 区域对话那批功能已于 2026-09-14 撤掉，表也从目录里移除了；
        //  - 服装模块已于 2026-09-16 移除（应用里没有任何界面显示服装，见 DataModule 的说明）。
        // 留着 schema 只会让「全部数据表」里出现永远下不下来的空表。
    ).associateBy { it.fileName }

    private val DEFAULT = TableSchema(fileName = "")

    operator fun get(fileName: String): TableSchema =
        OVERRIDES[fileName] ?: DEFAULT.copy(fileName = fileName)
}
