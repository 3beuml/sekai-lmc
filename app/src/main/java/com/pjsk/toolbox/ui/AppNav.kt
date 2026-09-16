package com.pjsk.toolbox.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.ui.player.PlayerScreen
import androidx.compose.foundation.layout.Column
import com.pjsk.toolbox.ui.player.MiniPlayerBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.story.StoryType
import com.pjsk.toolbox.ui.screen.AboutScreen
import com.pjsk.toolbox.ui.screen.CardDetailScreen
import com.pjsk.toolbox.ui.screen.CardEpisodeScreen
import com.pjsk.toolbox.ui.screen.CardListScreen
import com.pjsk.toolbox.ui.screen.CategorySongsScreen
import com.pjsk.toolbox.ui.screen.CharacterSongsScreen
import com.pjsk.toolbox.ui.screen.DetailScreen
import com.pjsk.toolbox.ui.screen.GachaScreen
import com.pjsk.toolbox.ui.screen.HomeScreen
import com.pjsk.toolbox.ui.screen.LyricsScreen
import com.pjsk.toolbox.ui.screen.SongsScreen
import com.pjsk.toolbox.ui.screen.MasterListScreen
import com.pjsk.toolbox.ui.screen.MusicDetailScreen
import com.pjsk.toolbox.ui.screen.SettingsScreen
import com.pjsk.toolbox.ui.screen.StoryReaderScreen
import com.pjsk.toolbox.ui.screen.StoryScreen
import com.pjsk.toolbox.ui.screen.SyncScreen
import com.pjsk.toolbox.ui.screen.TableIndexScreen

/**
 * 路由表。
 *
 * 关键设计：列表与详情**只有两个参数化路由**，覆盖全部模块。
 * 具体表名放进路径里（例如 `list/musics.json`、`detail/cards.json/42`），
 * 所以新增一个数据模块不需要新增路由，也不需要新增页面。
 */
object Routes {
    const val HOME = "home"
    const val SYNC = "sync"
    const val ABOUT = "about"
    const val TABLES = "tables"
    const val SETTINGS = "settings"

    const val ARG_TABLE = "table"
    const val ARG_ID = "id"
    const val ARG_CARD_ID = "cardId"
    const val ARG_MUSIC_ID = "musicId"
    const val ARG_CHARACTER_ID = "characterId"
    const val ARG_UNIT_TAG = "unitTag"
    const val ARG_PART = "part"
    const val ARG_STORY_TYPE = "storyType"
    const val ARG_STORY_ID = "storyId"
    const val ARG_STORY_BUNDLE = "storyBundle"
    const val ARG_STORY_TITLE = "storyTitle"
    const val ARG_SCENARIO_ID = "scenarioId"

    const val LIST_PATTERN = "list/{$ARG_TABLE}"
    const val DETAIL_PATTERN = "detail/{$ARG_TABLE}/{$ARG_ID}"

    /**
     * 卡牌有**自己的页面**，不再走通用表浏览器。
     *
     * 原因是卡牌的展示需求特殊：一个条目要在 16:9 图框里并排显示未特训与觉醒两张图、
     * 要按团体/角色/属性/稀有度筛选、详情页要算特训前后的数值。
     * 通用表浏览器（`list/{table}`）只认「缩略图 + 名字 + 副标题」，做不了这些。
     */
    const val CARDS = "cards"
    const val CARD_DETAIL_PATTERN = "card/{$ARG_CARD_ID}"

    /**
     * 卡牌图鉴**带一个可选的预设角色**。
     *
     * 为什么用查询参数而不是新开一条路由：从歌曲详情点演唱者，要落到
     * 「卡牌图鉴 + 该角色已筛选」这个**同一个页面**上，新开一条路由会让
     * 卡牌图鉴出现两套入口、两处状态。
     *
     * 默认值 -1 表示「不预设」——这样底部导航那个不带参的 `Routes.CARDS` 照样能用。
     */
    const val CARDS_PATTERN = "$CARDS?$ARG_CHARACTER_ID={$ARG_CHARACTER_ID}"

    /**
     * 歌曲详情（资料页）。阶段 1：纯展示，播放相关的一切都还没有。
     */
    const val MUSIC_DETAIL_PATTERN = "music/{$ARG_MUSIC_ID}"

    /** 歌词页。 */
    const val LYRICS_PATTERN = "music/{$ARG_MUSIC_ID}/lyrics"

    /**
     * 全屏播放器模式。
     *
     * 单独一条路由：进这一屏时**底部栏（歌曲状态栏 + 导航栏）整体隐藏**（用户要求），
     * 返回就回到进入前的页面。
     */
    const val PLAYER = "player"

    /**
     * 分类 / 人物点进去的**独立页面**（用户明确要求：不要切回「歌曲」Tab 去筛选）。
     */
    const val CATEGORY_SONGS_PATTERN = "songs/category/{$ARG_UNIT_TAG}"
    const val CHARACTER_SONGS_PATTERN = "songs/character/{$ARG_CHARACTER_ID}"

    /**
     * 卡牌剧情详情。
     *
     * 用 `part`（1=前篇 / 2=后篇）而不是 episode id 定位：一篇剧情永远属于一张卡 + 一个篇目，
     * 而 `part` 是从 `cardEpisodePartType` 归一化出来的稳定值，比 2768 个自增 id 更好读、也更好写。
     */
    const val CARD_EPISODE_PATTERN = "card/{$ARG_CARD_ID}/episode/{$ARG_PART}"

    /**
     * 剧情模块。三层结构与参考站一一对应：
     *  - `story`            → 第 1 层列表，默认**活动剧情**（活动列表本身不带筛选）
     *  - `story/{type}`     → 切到别的分类（主线 / 卡牌 / 区域 / 自我介绍 / 特殊）
     *  - `story/{type}/{id}`→ 第 2 层：某个活动 / 组合 / 卡的分话列表
     *  - `story/read/...`   → 第 3 层：阅读器
     *
     * 「筛选」按钮的作用就是在这 6 个分类之间切换（用户明确）。
     * 第 2 层的 `id` 与第 1 层的行 id 同源，所以两层能共用一个 `StoryScreen`。
     */
    const val GACHA = "gacha"
    const val SONGS = "songs"

    const val STORY = "story"
    const val STORY_TYPE_PATTERN = "story/{$ARG_STORY_TYPE}"
    const val STORY_ENTRIES_PATTERN = "story/{$ARG_STORY_TYPE}/{$ARG_STORY_ID}"
    const val STORY_READ_PATTERN =
        "story/read/{$ARG_STORY_TYPE}/{$ARG_STORY_BUNDLE}/{$ARG_SCENARIO_ID}?$ARG_STORY_TITLE={$ARG_STORY_TITLE}"

    fun list(tableName: String): String = "list/$tableName"

    fun detail(tableName: String, id: Int): String = "detail/$tableName/$id"

    fun cardDetail(cardId: Int): String = "card/$cardId"

    /** 卡牌图鉴，可选带上「预设筛选的角色」。 */
    fun cards(characterId: Int? = null): String =
        if (characterId == null) CARDS else "$CARDS?$ARG_CHARACTER_ID=$characterId"

    fun musicDetail(musicId: Int): String = "music/$musicId"

    fun lyrics(musicId: Int): String = "music/$musicId/lyrics"

    /** 某个游戏内分类下的歌（分类卡片点进来）。 */
    fun categorySongs(unitTag: String): String = "songs/category/$unitTag"

    /** 某个角色唱过的歌（人物列表点进来）。 */
    fun characterSongs(characterId: Int): String = "songs/character/$characterId"

    fun cardEpisode(cardId: Int, part: Int): String = "card/$cardId/episode/$part"

    fun story(type: StoryType): String = "story/${type.key}"

    fun storyEntries(type: StoryType, id: String): String = "story/${type.key}/$id"

    /**
     * 阅读器。[assetbundleName] 为 null 时（自我介绍）用 `-` 占位 ——
     * 路由参数不能为空字符串。
     */
    fun storyRead(
        type: StoryType,
        assetbundleName: String?,
        scenarioId: String,
        title: String?,
    ): String {
        val bundle = assetbundleName?.takeIf { it.isNotBlank() } ?: "-"
        val encodedTitle = java.net.URLEncoder.encode(title.orEmpty(), "UTF-8")
        return "story/read/${type.key}/$bundle/$scenarioId?$ARG_STORY_TITLE=$encodedTitle"
    }
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * 「不带预设角色」的哨兵值。
 *
 * 路由参数不能是 null，所以用一个不可能出现的角色 id 表示「不筛选」
 * （有效角色 id 是 1~26）。放在文件级而不是 `Routes` 里，是因为它只服务于导航层。
 */
private const val NO_CHARACTER_FILTER = -1

/**
 * 底部导航栏的高度（用户拍板的方案 A）。
 *
 * M3 默认 80dp（图标 + 文字）。去掉文字后**必须同时显式设高度**，否则
 * `defaultMinSize(minHeight = 80.dp)` 会让它保持 80dp、图标居中，白留一片空白。
 *
 * 56dp = 24dp 图标 + 上下各 16dp，仍高于无障碍推荐的 48dp 触控下限。
 */
private val NAV_BAR_HEIGHT = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav() {
    val navController = rememberNavController()
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val syncState by container.syncManager.state.collectAsState()
    val region = syncState.region

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 参数化路由的 route 是模式串（`list/{table}`），无法直接用于高亮判断，
    // 所以这里把「模式 + 实际参数」还原成一个可比较的 key。
    // `substringBefore("?")`：卡牌图鉴带了可选的预设角色查询参数
    // （`cards?characterId={characterId}`），不剥掉的话底部「卡牌」永远不高亮。
    val currentKey: String? = if (currentRoute == Routes.LIST_PATTERN) {
        val table = backStackEntry?.arguments?.getString(Routes.ARG_TABLE).orEmpty()
        "list/$table"
    } else {
        currentRoute?.substringBefore("?")
    }

    // 底部导航固定 5 项。数据同步 / 全部数据表 / 贴纸制作器 / 关于 都收进「更多」，
    // 否则底部条会被低频入口塞满。
    // 第 4 项由「活动」改成「剧情」：活动**列表**并进剧情页成了默认分类（用户要求）。
    val bottomItems = listOf(
        BottomItem(Routes.HOME, "首页", Icons.Default.Home),
        BottomItem(Routes.CARDS, "卡牌", Icons.Default.Style),
        BottomItem(Routes.SONGS, "歌曲", Icons.Default.MusicNote),
        BottomItem(Routes.STORY, "剧情", Icons.Default.MenuBook),
        BottomItem(Routes.SETTINGS, "更多", Icons.Default.MoreHoriz),
    )

    Scaffold(
        /**
         * ⚠️ 这里必须是 `WindowInsets(0)`，不能留默认值 —— 这是一个修过的**重复内边距 bug**。
         *
         * 默认值 `ScaffoldDefaults.contentWindowInsets`（= systemBars）会让外层 Scaffold
         * 为状态栏让出高度（实测本机 145px ≈ 44.6dp），而**每屏自己的 Scaffold + TopAppBar
         * 又会按状态栏内边距撑一次**，于是同一次内边距被算了两次。
         *
         * 真机实测（MEIZU 21 Note，1264×2780，密度 520 → 3.25px/dp）：
         *  - 状态栏底边 y=145；
         *  - AppBar 节点 y=145..498，高 353px = 108.6dp，而 M3 的 TopAppBar 只有 64dp；
         *  - 标题「sekai lmc」落在 y=346..441，**正好居中于 290..498**，
         *    而不是居中于 145..498 —— 这就是内部又垫了 145px 的铁证；
         *  - 内容要等到 y=498 才开始，也就是顶部白占 153dp。
         *
         * 改成 `WindowInsets(0)` 后，顶部内边距只由各屏的 TopAppBar 处理一次：
         * 内容起点从 498px 提到 353px，**每屏省下约 45dp**。
         *
         * 底部不需要担心：`innerPadding.bottom` 仍然等于 bottomBar（NavigationBar）的高度，
         * 与 `contentWindowInsets` 无关；而 NavigationBar 自己会消费系统导航栏内边距。
         */
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 全屏播放器模式：底部栏整体隐藏（用户要求）。
            // ⚠️ 这会让 innerPadding.bottom 从"状态栏 + 导航栏"变成 0，
            // 内容会重新布局 —— 而播放器这一屏本来就铺满全屏，所以是期望行为。
            if (currentKey != Routes.PLAYER) {
                // 歌曲状态栏在**底部导航之上**。没有在播的歌时它是 0 高度，
                // 所以"没听歌"的状态和加这个功能之前完全一样。
                Column {
                    MiniPlayerBar(
                        player = container.musicPlayer,
                        region = region,
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    )
                    NavigationBar(
                        // ⚠️ **必须显式给高度**：M3 的 NavigationBar 写死
                        // `defaultMinSize(minHeight = 80.dp)`，只把 label 传 null
                        // 只会让图标在 80dp 里居中，**一点空间都省不下来**。
                        // 这里压到 56dp（图标 24 + 上下各 16），比 80dp 省 24dp。
                        modifier = Modifier.height(NAV_BAR_HEIGHT),
                    ) {
                        bottomItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentKey == item.route,
                                onClick = {
                                    if (currentKey != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(Routes.HOME) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                // 只留图标：文字标签去掉，省下的高度给歌曲状态栏（用户拍的方案 A）。
                                // contentDescription 保留着，无障碍读屏仍然能念出这是哪一项。
                                label = null,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    region = region,
                    onOpenRoute = { route -> navController.navigate(route) },
                    onOpenCard = { id -> navController.navigate(Routes.cardDetail(id)) },
                    onOpenSongs = { navController.navigate(Routes.SONGS) },
                    onOpenMusic = { musicId -> navController.navigate(Routes.musicDetail(musicId)) },
                    onOpenGacha = { navController.navigate(Routes.GACHA) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenSync = { navController.navigate(Routes.SYNC) },
                    onOpenTables = { navController.navigate(Routes.TABLES) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }

            composable(Routes.SYNC) {
                SyncScreen()
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.TABLES) {
                TableIndexScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTable = { table -> navController.navigate(Routes.list(table)) },
                )
            }

            composable(
                route = Routes.CARDS_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_CHARACTER_ID) {
                        type = NavType.IntType
                        defaultValue = NO_CHARACTER_FILTER
                    },
                ),
            ) { entry ->
                val preset = entry.arguments?.getInt(Routes.ARG_CHARACTER_ID) ?: NO_CHARACTER_FILTER
                CardListScreen(
                    region = region,
                    onOpenDetail = { id -> navController.navigate(Routes.cardDetail(id)) },
                    onOpenSync = { navController.navigate(Routes.SYNC) },
                    initialCharacterId = preset.takeIf { it > 0 },
                )
            }

            composable(
                route = Routes.MUSIC_DETAIL_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_MUSIC_ID) { type = NavType.IntType }),
            ) { entry ->
                val musicId = entry.arguments?.getInt(Routes.ARG_MUSIC_ID) ?: 0
                MusicDetailScreen(
                    musicId = musicId,
                    region = region,
                    onBack = { navController.popBackStack() },
                    // 相关活动 → 复用剧情页的第 2 层（活动分话列表）
                    onOpenEvent = { eventId ->
                        navController.navigate(
                            Routes.storyEntries(StoryType.EVENT, eventId.toString()),
                        )
                    },
                    // 演唱者 → 卡牌图鉴，并预设按这个角色筛选
                    onOpenCharacter = { characterId ->
                        navController.navigate(Routes.cards(characterId))
                    },
                    onOpenLyrics = { navController.navigate(Routes.lyrics(musicId)) },
                )
            }

            composable(
                route = Routes.LYRICS_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_MUSIC_ID) { type = NavType.IntType }),
            ) { entry ->
                LyricsScreen(
                    musicId = entry.arguments?.getInt(Routes.ARG_MUSIC_ID) ?: 0,
                    region = region,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.CARD_DETAIL_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_CARD_ID) { type = NavType.IntType },
                ),
            ) { entry ->
                CardDetailScreen(
                    cardId = entry.arguments?.getInt(Routes.ARG_CARD_ID) ?: 0,
                    region = region,
                    onBack = { navController.popBackStack() },
                    onOpenEpisode = { bundle, scenarioId, title ->
                        navController.navigate(
                            Routes.storyRead(StoryType.CARD, bundle, scenarioId, title),
                        )
                    },
                    onOpenEvent = { eventId ->
                        navController.navigate(
                            Routes.storyEntries(StoryType.EVENT, eventId.toString()),
                        )
                    },
                )
            }

            // ── 剧情模块 ──
            composable(Routes.GACHA) {
                GachaScreen(
                    region = region,
                    onBack = { navController.popBackStack() },
                    onOpenCard = { id -> navController.navigate(Routes.cardDetail(id)) },
                )
            }

            composable(Routes.PLAYER) {
                PlayerScreen(
                    region = region,
                    onCollapse = { navController.popBackStack() },
                    onOpenDetail = { musicId -> navController.navigate(Routes.musicDetail(musicId)) },
                    onOpenLyrics = { musicId -> navController.navigate(Routes.lyrics(musicId)) },
                    // 播放模式存偏好：下次启动还是上次那个模式
                    onPlayModeChange = { container.appSettings.setPlayMode(it) },
                )
            }

            composable(Routes.SONGS) {
                SongsScreen(
                    region = region,
                    onOpenDetail = { musicId -> navController.navigate(Routes.musicDetail(musicId)) },
                    // 分类 / 人物点进去的是**独立页面**，不是切回歌曲 Tab 去筛选（用户要求）
                    onOpenCategory = { tag -> navController.navigate(Routes.categorySongs(tag)) },
                    onOpenCharacter = { characterId ->
                        navController.navigate(Routes.characterSongs(characterId))
                    },
                )
            }

            composable(
                route = Routes.CATEGORY_SONGS_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_UNIT_TAG) { type = NavType.StringType }),
            ) { entry ->
                CategorySongsScreen(
                    tagKey = entry.arguments?.getString(Routes.ARG_UNIT_TAG).orEmpty(),
                    region = region,
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { musicId -> navController.navigate(Routes.musicDetail(musicId)) },
                )
            }

            composable(
                route = Routes.CHARACTER_SONGS_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_CHARACTER_ID) { type = NavType.IntType }),
            ) { entry ->
                CharacterSongsScreen(
                    characterId = entry.arguments?.getInt(Routes.ARG_CHARACTER_ID) ?: 0,
                    region = region,
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { musicId -> navController.navigate(Routes.musicDetail(musicId)) },
                )
            }
            composable(Routes.STORY) {
                StoryScreen(
                    type = StoryType.DEFAULT,
                    parentId = null,
                    parentTitle = null,
                    region = region,
                    onOpenType = { navController.navigate(Routes.story(it)) },
                    onOpenRow = { row ->
                        // 自我介绍 / 特殊剧情的行自带 scenarioId → **直接进阅读器**，不再点第二次
                        if (row.directScenarioId != null) {
                            navController.navigate(
                                Routes.storyRead(
                                    type = StoryType.DEFAULT,
                                    assetbundleName = row.directBundle,
                                    scenarioId = row.directScenarioId,
                                    title = row.title,
                                ),
                            )
                        } else {
                            navController.navigate(Routes.storyEntries(StoryType.DEFAULT, row.id))
                        }
                    },
                    onOpenEntry = { },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.STORY_TYPE_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_STORY_TYPE) { type = NavType.StringType }),
            ) { entry ->
                val type = StoryType.of(entry.arguments?.getString(Routes.ARG_STORY_TYPE))
                StoryScreen(
                    type = type,
                    parentId = null,
                    parentTitle = null,
                    region = region,
                    onOpenType = { navController.navigate(Routes.story(it)) },
                    onOpenRow = { row ->
                        if (row.directScenarioId != null) {
                            navController.navigate(
                                Routes.storyRead(
                                    type = type,
                                    assetbundleName = row.directBundle,
                                    scenarioId = row.directScenarioId,
                                    title = row.title,
                                ),
                            )
                        } else {
                            navController.navigate(Routes.storyEntries(type, row.id))
                        }
                    },
                    onOpenEntry = { },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.STORY_ENTRIES_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_STORY_TYPE) { type = NavType.StringType },
                    navArgument(Routes.ARG_STORY_ID) { type = NavType.StringType },
                ),
            ) { entry ->
                val type = StoryType.of(entry.arguments?.getString(Routes.ARG_STORY_TYPE))
                val id = entry.arguments?.getString(Routes.ARG_STORY_ID).orEmpty()
                StoryScreen(
                    type = type,
                    parentId = id,
                    parentTitle = null,
                    region = region,
                    onOpenType = { },
                    onOpenRow = { },
                    onOpenEntry = { storyEntry ->
                        navController.navigate(
                            Routes.storyRead(
                                type = type,
                                assetbundleName = storyEntry.assetbundleName,
                                scenarioId = storyEntry.scenarioId,
                                title = storyEntry.title,
                            ),
                        )
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.STORY_READ_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_STORY_TYPE) { type = NavType.StringType },
                    navArgument(Routes.ARG_STORY_BUNDLE) { type = NavType.StringType },
                    navArgument(Routes.ARG_SCENARIO_ID) { type = NavType.StringType },
                    navArgument(Routes.ARG_STORY_TITLE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val bundleArg = entry.arguments?.getString(Routes.ARG_STORY_BUNDLE)
                StoryReaderScreen(
                    type = StoryType.of(entry.arguments?.getString(Routes.ARG_STORY_TYPE)),
                    assetbundleName = bundleArg?.takeIf { it != "-" },
                    scenarioId = entry.arguments?.getString(Routes.ARG_SCENARIO_ID).orEmpty(),
                    title = entry.arguments?.getString(Routes.ARG_STORY_TITLE)?.takeIf { it.isNotBlank() },
                    region = region,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.CARD_EPISODE_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_CARD_ID) { type = NavType.IntType },
                    navArgument(Routes.ARG_PART) { type = NavType.IntType },
                ),
            ) { entry ->
                CardEpisodeScreen(
                    cardId = entry.arguments?.getInt(Routes.ARG_CARD_ID) ?: 0,
                    part = entry.arguments?.getInt(Routes.ARG_PART) ?: 1,
                    region = region,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.LIST_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_TABLE) { type = NavType.StringType },
                ),
            ) { entry ->
                val table = entry.arguments?.getString(Routes.ARG_TABLE).orEmpty()
                MasterListScreen(
                    tableName = table,
                    region = region,
                    onBack = { navController.popBackStack() },
                    onOpenSync = { navController.navigate(Routes.SYNC) },
                    onOpenDetail = { id -> navController.navigate(Routes.detail(table, id)) },
                )
            }

            composable(
                route = Routes.DETAIL_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_TABLE) { type = NavType.StringType },
                    navArgument(Routes.ARG_ID) { type = NavType.IntType },
                ),
            ) { entry ->
                val table = entry.arguments?.getString(Routes.ARG_TABLE).orEmpty()
                val id = entry.arguments?.getInt(Routes.ARG_ID) ?: 0
                DetailScreen(
                    tableName = table,
                    rowId = id,
                    region = region,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
