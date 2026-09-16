package com.pjsk.toolbox.data.settings

import android.content.Context
import com.pjsk.toolbox.data.player.PlayMode
import com.pjsk.toolbox.util.decodeFavoriteSongs
import com.pjsk.toolbox.util.encodeFavoriteSongs
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
    }
}
