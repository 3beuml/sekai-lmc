package com.pjsk.toolbox.data.settings

import android.content.Context
import com.pjsk.toolbox.data.player.PlayMode
import com.pjsk.toolbox.data.profile.PlayerAccount
import com.pjsk.toolbox.util.decodeFavoriteSongs
import com.pjsk.toolbox.util.decodePreferredVocals
import com.pjsk.toolbox.util.encodeFavoriteSongs
import com.pjsk.toolbox.util.encodePreferredVocals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 外观模式。 */
enum class ThemeMode(val label: String, val description: String) {
    FOLLOW_SYSTEM("跟随系统", "系统开深色就深色"),
    DARK("始终深色", "一直用深色，夜里看卡面不刺眼"),
    LIGHT("始终浅色", "一直用浅色，白天看得更清楚"),
}

/**
 * 名称语言。
 *
 * 本 App 的数据主线是**日服**（版本最新），而中文名来自简中服的叠加层。
 * 简中进度落后于日服，所以新内容的 `nameZh` 为 null —— 这时候无论如何都会回退日文原名，
 * 这三个选项只是决定「有中文时怎么显示」。
 */
enum class NameLanguage(val label: String, val description: String) {
    CHINESE_FIRST("中文优先", "有中文就显示中文，没有则显示日文原名"),
    JAPANESE("日文原名", "始终显示日文原名，中文放在副标题"),
    BILINGUAL("双语对照", "中文为主、日文作为副标题一起给出"),
}

/**
 * 歌曲默认演唱版本的全局偏好（歌曲板块顶栏那个二态切换按钮）。
 *
 * - [ORIGINAL] = 「原曲版」：所有歌播各自默认版本（`musicVocals` 里 `seq` 最小的那个），
 *   即游戏内默认那个，和没加这个功能之前完全一致。
 * - [SEKAI] = 「世界版」：每首歌有 `sekai` 版就播 `seq` 最小的 `sekai` 版（实测就是
 *   全员合唱的"セカイver."），没有 `sekai` 版就回退默认（"没有就不切换"）。
 *
 * 只影响"没有单曲定制"的歌：收藏歌在播放器里单独记过的版本（[preferredVocals]）
 * 优先级高于这个全局态。详见 `HomeRepository` 组装 `defaultVocal` 的逻辑。
 *
 * 用名字存而不是序号：和 [ThemeMode] / [PlayMode] 同款，枚举中间插项不会让用户设置错位。
 */
enum class VocalMode(val label: String) {
    ORIGINAL("原曲版"),
    SEKAI("世界版"),
    ;
}

/**
 * 用户偏好。
 *
 * 用 `SharedPreferences` 而不是 DataStore：这里只有几个开关，
 * 引入 DataStore 会多一个依赖和一套异步初始化，收益不成比例。
 * 对外暴露 [StateFlow]，这样 Compose 侧能直接 `collectAsState()` 跟着变。
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        enumOrDefault(prefs.getString(KEY_THEME, null), ThemeMode.FOLLOW_SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _nameLanguage = MutableStateFlow(
        enumOrDefault(prefs.getString(KEY_NAME_LANGUAGE, null), NameLanguage.CHINESE_FIRST),
    )
    val nameLanguage: StateFlow<NameLanguage> = _nameLanguage.asStateFlow()

    /**
     * 播放模式（列表播放 / 单曲循环 / 随机播放）。
     *
     * 存偏好里，所以**下次启动还是上次那个模式** —— 播放模式属于长期偏好，
     * 每次开 App 都回到"列表播放"会让人反复重设。
     */
    private val _playMode = MutableStateFlow(
        enumOrDefault(prefs.getString(KEY_PLAY_MODE, null), PlayMode.SEQUENTIAL),
    )
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    fun setPlayMode(mode: PlayMode) {
        prefs.edit().putString(KEY_PLAY_MODE, mode.name).apply()
        _playMode.value = mode
    }

    /**
     * 歌曲默认演唱版本的全局态（顶栏二态切换）。
     *
     * 默认 [VocalMode.ORIGINAL]（原曲版）= 行为和加这个功能之前完全一致，
     * 老用户升上来不会察觉任何变化。
     */
    private val _vocalMode = MutableStateFlow(
        enumOrDefault(prefs.getString(KEY_VOCAL_MODE, null), VocalMode.ORIGINAL),
    )
    val vocalMode: StateFlow<VocalMode> = _vocalMode.asStateFlow()

    fun setVocalMode(mode: VocalMode) {
        prefs.edit().putString(KEY_VOCAL_MODE, mode.name).apply()
        _vocalMode.value = mode
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setNameLanguage(language: NameLanguage) {
        prefs.edit().putString(KEY_NAME_LANGUAGE, language.name).apply()
        _nameLanguage.value = language
    }

    // ─────────────────────────────────────────────────────────────
    // 收藏的歌曲
    //
    // 为什么放这里、**不进数据库**：收藏只是一串 musicId，放进 Room 就要动表结构
    // 并写迁移（`AppDatabase` 的 version 一升就得处理老库），风险远大于收益。
    // 这里存成「id:收藏时间」的字符串集合，顺便解决了收藏页的排序（按时间倒序）。
    //
    // key 是 musicId：官方几乎不会改已发布曲子的 id，所以这份数据能长期有效。
    // ─────────────────────────────────────────────────────────────

    private val _favoriteSongs = MutableStateFlow(readFavorites())
    val favoriteSongs: StateFlow<Map<Int, Long>> = _favoriteSongs.asStateFlow()

    /** 收藏 / 取消收藏。返回操作后**是否已收藏**，方便界面直接提示。 */
    fun toggleFavoriteSong(musicId: Int): Boolean {
        val current = _favoriteSongs.value
        val next = if (musicId in current) {
            current - musicId
        } else {
            current + (musicId to System.currentTimeMillis())
        }
        prefs.edit()
            .putStringSet(KEY_FAVORITE_SONGS, encodeFavoriteSongs(next))
            .apply()
        _favoriteSongs.value = next
        return musicId in next
    }

    private fun readFavorites(): Map<Int, Long> =
        decodeFavoriteSongs(prefs.getStringSet(KEY_FAVORITE_SONGS, emptySet()).orEmpty())

    // ─────────────────────────────────────────────────────────────
    // 收藏歌曲的版本偏好
    //
    // 只给**收藏歌曲**记一份"以后播这首就用这个版本"，在全屏播放器的「版本」弹层里
    // 切换版本时写入（[com.pjsk.toolbox.ui.player.PlayerScreen] 的 switchTo）。
    // 非收藏歌点版本只当场换、不写进来。
    //
    // 和收藏同款：SharedPreferences StringSet + 纯函数编解码（[encodePreferredVocals]），
    // 不进数据库。优先级高于 [vocalMode]（全局态），详见 `HomeRepository` 的组装逻辑。
    // ─────────────────────────────────────────────────────────────

    private val _preferredVocals = MutableStateFlow(readPreferredVocals())
    val preferredVocals: StateFlow<Map<Int, Int>> = _preferredVocals.asStateFlow()

    /**
     * 给某首**收藏歌曲**记一个版本。切版本时调；写完之后这首歌以后各处播都用这个版本。
     */
    fun setPreferredVocal(musicId: Int, vocalId: Int) {
        val next = _preferredVocals.value + (musicId to vocalId)
        prefs.edit().putStringSet(KEY_PREFERRED_VOCALS, encodePreferredVocals(next)).apply()
        _preferredVocals.value = next
    }

    /** 取消某首歌的版本记忆（回退到全局态/默认）。 */
    fun clearPreferredVocal(musicId: Int) {
        val next = _preferredVocals.value - musicId
        prefs.edit().putStringSet(KEY_PREFERRED_VOCALS, encodePreferredVocals(next)).apply()
        _preferredVocals.value = next
    }

    private fun readPreferredVocals(): Map<Int, Int> =
        decodePreferredVocals(prefs.getStringSet(KEY_PREFERRED_VOCALS, emptySet()).orEmpty())

    // ─────────────────────────────────────────────────────────────
    // 绑定的游戏账号（「我的」功能的入口状态）
    //
    // 只存两个字符串：区服 id + 游戏 UID。**没有任何凭据**——
    // 读取走的是 Haruki 工具箱的公开只读接口，靠的就是 (区服, UID) 这一对。
    // 所以解绑 = 删这两个键 + 删本地快照文件，不需要任何"注销"逻辑。
    // ─────────────────────────────────────────────────────────────

    private val _playerAccount = MutableStateFlow(readPlayerAccount())
    val playerAccount: StateFlow<PlayerAccount?> = _playerAccount.asStateFlow()

    /** 绑定 / 解绑（传 null）。 */
    fun setPlayerAccount(account: PlayerAccount?) {
        val editor = prefs.edit()
        if (account == null) {
            editor.remove(KEY_PLAYER_SERVER).remove(KEY_PLAYER_GAME_ID)
        } else {
            editor.putString(KEY_PLAYER_SERVER, account.server)
                .putString(KEY_PLAYER_GAME_ID, account.gameId)
        }
        editor.apply()
        _playerAccount.value = account
    }

    private fun readPlayerAccount(): PlayerAccount? {
        val server = prefs.getString(KEY_PLAYER_SERVER, null) ?: return null
        val gameId = prefs.getString(KEY_PLAYER_GAME_ID, null) ?: return null
        if (server.isBlank() || gameId.isBlank()) return null
        return PlayerAccount(server = server, gameId = gameId)
    }

    /**
     * 把存下来的字符串还原成枚举。
     *
     * 用名字存而不是序号：以后在枚举中间插入一项，序号就会整体错位，
     * 用户的外观设置会莫名其妙变成别的值。
     */
    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T =
        raw?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_NAME_LANGUAGE = "name_language"
        const val KEY_FAVORITE_SONGS = "favorite_songs"
        const val KEY_PLAY_MODE = "play_mode"
        const val KEY_VOCAL_MODE = "vocal_mode"
        const val KEY_PREFERRED_VOCALS = "preferred_vocals"
        const val KEY_PLAYER_SERVER = "player_server"
        const val KEY_PLAYER_GAME_ID = "player_game_id"
    }
}
