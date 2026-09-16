package com.pjsk.toolbox.data.sync

/**
 * 内置快照的**导入顺序**：P0 卡牌＋歌曲 → P1 首页其余区块 → P2 其余浏览用表。
 *
 * 为什么要有这个顺序：用户最常用的就是「卡牌」和「歌曲」，而且这两块的内容变化也最频繁。
 * 66 张表全导完要几秒到十几秒，如果按目录顺序铺，用户打开 App 看到的是「正在准备数据」，
 * 卡牌页还是空的 —— 而按这个顺序，**卡牌与歌曲会在最前面就绪**。
 *
 * 单独抽成对象是为了能在 `tools/logic-check/LogicCheck.kt` 里直接断言。
 * 顺序这种东西写错了**不会报任何错**，只会表现成「打开 App 后卡牌页半天没数据」，
 * 属于最难归因的那类问题，所以值得测。
 * （logic-check 是**另一个编译单元**，看不到 `internal`，所以这里必须是 public。）
 *
 * ⚠️ 名单按**文件名**写，必须与 `tools/datapack/build.ps1` 产出的文件名一致；
 * 快照里出现但这里没列到的表一律算 P2。
 */
object DatapackImportOrder {

    /**
     * P0：**卡牌 + 歌曲**（外加两块都要用的角色基础表）。
     *
     * 先铺两边共用的角色表（都很小、几百行），再铺卡牌，最后铺歌曲 ——
     * 卡牌是首页与「卡牌」板块的主入口，让它更早可用。
     */
    val P0_CARD_AND_MUSIC: List<String> = listOf(
        // 卡牌与歌曲都要用的角色基础
        "gameCharacters.json",
        "gameCharacterUnits.json",
        "characterProfiles.json",
        "unitProfiles.json",
        "character2ds.json",
        "outsideCharacters.json",
        // 卡牌
        "cards.json",
        "cardRarities.json",
        "cardSupplies.json",
        "cardSupplyGroups.json",
        "cardExtras.json",
        "cardExchangeResources.json",
        "cardSkillCosts.json",
        "skills.json",
        "cardEpisodes.json",
        "materials.json",
        // 歌曲
        "musics.json",
        "musicDifficulties.json",
        "musicVocals.json",
        "musicTags.json",
        "musicCategories.json",
        "musicArtists.json",
        "musicOriginals.json",
        "musicVideoCharacters.json",
        "musicDanceMembers.json",
        "musicAssetVariants.json",
        "musicAchievements.json",
        "limitedTimeMusics.json",
        "musicCollaborations.json",
        "musicSoundTracks.json",
        "musicSoundTrackCategories.json",
    )

    /** P1：首页其余区块（最新活动、当前卡池、世界链接章节）。 */
    val P1_HOME: List<String> = listOf(
        "events.json",
        "eventCards.json",
        "eventMusics.json",
        "eventStories.json",
        "eventStoryUnits.json",
        "gachas.json",
        "gachaTabs.json",
        "worldBlooms.json",
    )

    private val p0Index: Map<String, Int> = P0_CARD_AND_MUSIC.withIndex().associate { it.value to it.index }
    private val p1Index: Map<String, Int> = P1_HOME.withIndex().associate { it.value to it.index }

    /** 优先级组：0 = P0，1 = P1，2 = P2（也用于清单里出现的新表）。 */
    fun group(fileName: String?): Int = when {
        fileName == null -> 2
        p0Index.containsKey(fileName) -> 0
        p1Index.containsKey(fileName) -> 1
        else -> 2
    }

    /**
     * 组内序号。
     *
     * P2 的表统一返回 [Int.MAX_VALUE]，让调用方接在**体积升序**后面排 ——
     * 小表先完成，进度条动得快，用户不会以为卡住了。
     */
    fun orderWithinGroup(fileName: String?): Int {
        if (fileName == null) return Int.MAX_VALUE
        return p0Index[fileName] ?: p1Index[fileName] ?: Int.MAX_VALUE
    }
}
