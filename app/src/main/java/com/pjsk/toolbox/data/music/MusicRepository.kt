package com.pjsk.toolbox.data.music

import com.pjsk.toolbox.data.card.parseCharacter
import com.pjsk.toolbox.data.db.MasterDao
import com.pjsk.toolbox.ui.common.jsonObject
import com.pjsk.toolbox.ui.common.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 歌曲详情的数据源。
 *
 * ## 为什么是 `suspend fun` 而不是 `StateFlow`
 *
 * 卡牌那边用 `StateFlow` 是因为「列表页要一直看整张表」。歌曲详情页是**按需打开**
 * 的单首页面，每次只关心一首歌，所以直接用一次性的挂载查询更简单，
 * 也避免为一个页面养一整张表的常驻缓存。
 *
 * ## 性能
 *
 * 详情页要读 8 张表，其中 4 张（难度 3716 行 / 音源 1786 / 标签 1683 / 分类 862）
 * 需要「全读进来再按 musicId 过滤」——因为 Room 查不了 JSON 字段。
 * 合计约 8700 行的解析，本机实测在几百毫秒量级，配合页面的加载态可以接受。
 * 这也是**刻意不做索引优化**的原因：为一次点击引入投影器/新表不划算。
 *
 * ## ⚠️ 全部按 `musicId` 过滤，绝不用行主键
 *
 * 见 [MusicModels] 顶部的说明：那几张表的全局 `id` 会和别的曲子的 musicId 撞车。
 */
class MusicRepository(private val dao: MasterDao) {

    /**
     * 官方组合色（`unitProfiles.json` 的 `colorCode`）。
     *
     * **从库里读、不硬编码成常量**：这些色值是游戏数据的一部分，写死在代码里
     * 一旦官方改了就会和游戏不一致，而且没人会想起来去改。
     *
     * key 是 `unitProfiles` 的 `unit` 字段值（`light_sound` / `piapro`…），
     * 和 `musicTags` 的取值**拼法不同**，映射由 [MusicUnitTag.unitProfileKey] 负责。
     */
    val unitColors: Flow<Map<String, String>> = dao.observeAll(TABLE_UNIT_PROFILES)
        .map { rows ->
            rows.mapNotNull { row ->
                val obj = row.jsonObject() ?: return@mapNotNull null
                val unit = obj.str("unit")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val color = obj.str("colorCode")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                unit to color
            }.toMap()
        }
        .flowOn(Dispatchers.Default)

    /**
     * 「每个角色唱过哪些歌」（characterId → musicId 集合）。
     *
     * 「人物」页用它列出某个人唱过的曲子。口径是用户拍板的**甲**：
     * 他参与演唱的歌，而不是他所在团的歌。
     */
    val characterSongIds: Flow<Map<Int, Set<Int>>> = dao.observeAll(TABLE_MUSIC_VOCALS)
        .map { rows -> buildCharacterSongIds(rows) }
        .flowOn(Dispatchers.Default)

    /**
     * 载入一首歌的完整展示数据；库里没有这一首就返回 null。
     *
     * `gameCharacters` / `outsideCharacters` 两张表用来把音源行里的
     * `characters[].characterId` 变成人名，所以一起读。
     */
    suspend fun detail(musicId: Int): MusicDetail? = withContext(Dispatchers.IO) {
        val musicRow = dao.byId(TABLE_MUSICS, musicId) ?: return@withContext null

        val names = MusicSingerNames(
            gameCharacters = dao.observeAll(TABLE_GAME_CHARACTERS).first()
                .mapNotNull { row -> parseCharacter(row)?.let { it.id to it } }
                .toMap(),
            outsideCharacters = dao.observeAll(TABLE_OUTSIDE_CHARACTERS).first()
                .associate { it.id to (it.name ?: "#${it.id}") },
        )

        buildMusicDetail(
            musicRow = musicRow,
            difficultyRows = dao.observeAll(TABLE_MUSIC_DIFFICULTIES).first(),
            vocalRows = dao.observeAll(TABLE_MUSIC_VOCALS).first(),
            tagRows = dao.observeAll(TABLE_MUSIC_TAGS).first(),
            categoryRows = dao.observeAll(TABLE_MUSIC_CATEGORIES).first(),
            artistRows = dao.observeAll(TABLE_MUSIC_ARTISTS).first(),
            originalRows = dao.observeAll(TABLE_MUSIC_ORIGINALS).first(),
            eventMusicRows = dao.observeAll(TABLE_EVENT_MUSICS).first(),
            eventRows = dao.observeAll(TABLE_EVENTS).first(),
            limitedTimeRows = dao.observeAll(TABLE_LIMITED_TIME_MUSICS).first(),
            names = names,
        )
    }

    private companion object {
        const val TABLE_MUSICS = "musics.json"
        const val TABLE_MUSIC_DIFFICULTIES = "musicDifficulties.json"
        const val TABLE_MUSIC_VOCALS = "musicVocals.json"
        const val TABLE_MUSIC_TAGS = "musicTags.json"
        const val TABLE_MUSIC_CATEGORIES = "musicCategories.json"
        const val TABLE_MUSIC_ARTISTS = "musicArtists.json"
        const val TABLE_MUSIC_ORIGINALS = "musicOriginals.json"
        const val TABLE_EVENT_MUSICS = "eventMusics.json"
        const val TABLE_EVENTS = "events.json"
        const val TABLE_LIMITED_TIME_MUSICS = "limitedTimeMusics.json"
        const val TABLE_GAME_CHARACTERS = "gameCharacters.json"
        const val TABLE_OUTSIDE_CHARACTERS = "outsideCharacters.json"
        const val TABLE_UNIT_PROFILES = "unitProfiles.json"
    }
}
