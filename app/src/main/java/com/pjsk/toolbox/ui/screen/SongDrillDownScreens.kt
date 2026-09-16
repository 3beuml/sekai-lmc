package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.card.PjskCharacter
import com.pjsk.toolbox.data.card.displayNameFor
import com.pjsk.toolbox.data.music.MusicUnitTag
import com.pjsk.toolbox.data.music.SongSort
import com.pjsk.toolbox.data.music.filterSongsBySingers
import com.pjsk.toolbox.data.music.sortSongs
import com.pjsk.toolbox.data.music.unitMembers
import com.pjsk.toolbox.data.player.playFromList
import com.pjsk.toolbox.data.player.toTrack
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.ui.common.IntSetSaver
import com.pjsk.toolbox.ui.common.RemoteImage
import com.pjsk.toolbox.ui.common.SongListColumn
import com.pjsk.toolbox.util.moveMatchesFirst

/**
 * 某个分类（＝游戏内选曲界面的分类）下的全部歌曲。
 *
 * **这是分类卡片点进来的独立页面**（用户要求：不要切回「歌曲」Tab 去筛选）。
 *
 * 页面自带两个能力（用户要求）：
 *  - **按成员筛选**：`Leo/need` 这类团分类，可以按这个团的人筛。
 *    ⚠️ **`其他` 没有筛按钮** —— 它本来就不是一个团（`unitProfileKey` 是 null），
 *    `unitMembers()` 返回空列表，界面靠"空列表"决定不显示按钮，不需要特判。
 *  - **按时间先后排序**（默认「新出的在前」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySongsScreen(
    tagKey: String,
    region: ServerRegion,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val musics by container.homeRepository.musics.collectAsState(initial = emptyList())
    val unitColors by container.musicRepository.unitColors.collectAsState(initial = emptyMap())
    val characters by container.cardRepository.characters.collectAsState(initial = emptyList())
    val characterSongIds by container.musicRepository.characterSongIds.collectAsState(initial = emptyMap())
    val nameLanguage by container.appSettings.nameLanguage.collectAsState()
    val favorites by container.appSettings.favoriteSongs.collectAsState()
    val player = container.musicPlayer
    val playback by player.state.collectAsState()

    val tag = MusicUnitTag.of(tagKey)

    // 排序与成员筛选都用 rememberSaveable：进歌曲详情再返回时不能丢（见 ui/common/Savers.kt）
    var sort by rememberSaveable { mutableStateOf(SongSort.NEWEST) }
    var memberIds by rememberSaveable(stateSaver = IntSetSaver) { mutableStateOf(emptySet<Int>()) }
    var showFilter by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // 该分类的成员。「其他」拿不到团 → 空列表 → 不显示筛选按钮
    val members = remember(tag, characters) {
        tag?.let { unitMembers(it, characters) }.orEmpty()
    }

    val all = remember(musics, tagKey) {
        musics.filter { tagKey in it.unitTags }
    }
    val visible = remember(all, memberIds, sort, characterSongIds) {
        sortSongs(filterSongsBySingers(all, memberIds, characterSongIds), sort)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = tag?.label ?: tagKey,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = if (memberIds.isEmpty()) "${all.size} 首"
                            else "筛选出 ${visible.size} / ${all.size} 首",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 「按时间先后」按钮：按钮上直接写着当前顺序，一眼看得出现在怎么排的
                    Box {
                        TextButton(onClick = { sortMenuOpen = true }) {
                            Text(sort.label, maxLines = 1)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            SongSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        sort = option
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    // 只有这个分类真的有"团成员"时才出现筛按钮
                    if (members.isNotEmpty()) {
                        IconButton(onClick = { showFilter = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "筛选",
                                tint = if (memberIds.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (musics.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                SongListColumn(
                    musics = visible,
                    region = region,
                    unitColors = unitColors,
                    favoriteIds = favorites.keys,
                    emptyTitle = if (memberIds.isEmpty()) "这个分类下还没有歌" else "这几个人在这个分类里没有歌",
                    emptyDescription = if (memberIds.isEmpty()) "换个分类看看。" else "换个成员，或者清空筛选。",
                    onToggleFavorite = { container.appSettings.toggleFavoriteSong(it) },
                    // 点的是正在播的那首就是暂停/继续，否则整份列表入队从那首开始
                    onPlay = { music -> player.playFromList(visible, music.id) },
                    onOpenDetail = onOpenDetail,
                    playingMusicId = playback.track?.musicId,
                    isPlayRequested = playback.isPlayRequested,
                )
            }
        }
    }

    if (showFilter && members.isNotEmpty()) {
        MemberFilterSheet(
            members = members,
            selected = memberIds,
            songIdsByCharacter = characterSongIds,
            categorySongCount = all.size,
            matchedCount = visible.size,
            nameLanguage = nameLanguage,
            region = region,
            onDismiss = { showFilter = false },
        ) { selection -> memberIds = selection }
    }
}

/**
 * 成员筛选弹层。
 *
 * 每个 chip 上的数字是**这个人在这一个分类里唱了几首** —— 用总曲数会误导
 * （初音ミク 487 首是全站的总数，在这个分类里可能只有几首）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MemberFilterSheet(
    members: List<PjskCharacter>,
    selected: Set<Int>,
    songIdsByCharacter: Map<Int, Set<Int>>,
    categorySongCount: Int,
    matchedCount: Int,
    nameLanguage: NameLanguage,
    region: ServerRegion,
    onDismiss: () -> Unit,
    onChange: (Set<Int>) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("按成员筛选", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onChange(emptySet()) }) { Text("重置") }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 选中的成员排到前面
                members.moveMatchesFirst { it.id in selected }.forEach { member ->
                    val isSelected = member.id in selected
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onChange(
                                if (isSelected) selected - member.id else selected + member.id,
                            )
                        },
                        label = { Text(member.displayNameFor(nameLanguage)) },
                        leadingIcon = {
                            RemoteImage(
                                url = AssetUrls.characterAvatar(region, member.id),
                                contentDescription = null,
                                // 半身像 376×840 竖构图，圆头像必须顶部对齐，否则是胸口不是脸
                                modifier = Modifier.size(22.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (selected.isEmpty()) "显示全部 $categorySongCount 首"
                    else "显示 $matchedCount / $categorySongCount 首",
                )
            }
        }
    }
}

/**
 * 某个角色**唱过**的全部歌曲。
 *
 * 口径是用户拍板的**甲**：他参与演唱的歌（`musicVocals.characters`），
 * 不是"他所在团的歌"。实测初音ミク 487 首、镜音铃 160、星乃一歌 90。
 *
 * 只有「按时间先后」排序按钮，没有按人筛选（这个页面本身就是一个人）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSongsScreen(
    characterId: Int,
    region: ServerRegion,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val musics by container.homeRepository.musics.collectAsState(initial = emptyList())
    val unitColors by container.musicRepository.unitColors.collectAsState(initial = emptyMap())
    val characters by container.cardRepository.characters.collectAsState(initial = emptyList())
    val characterSongIds by container.musicRepository.characterSongIds.collectAsState(initial = emptyMap())
    val nameLanguage by container.appSettings.nameLanguage.collectAsState()
    val favorites by container.appSettings.favoriteSongs.collectAsState()
    val player = container.musicPlayer
    val playback by player.state.collectAsState()

    var sort by rememberSaveable { mutableStateOf(SongSort.NEWEST) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val name = remember(characters, characterId, nameLanguage) {
        characters.firstOrNull { it.id == characterId }?.displayNameFor(nameLanguage)
    }
    val list = remember(musics, characterSongIds, characterId, sort) {
        val ids = characterSongIds[characterId].orEmpty()
        sortSongs(musics.filter { it.id in ids }, sort)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = name ?: "角色 #$characterId",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "演唱的歌曲 ${list.size} 首",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { sortMenuOpen = true }) {
                            Text(sort.label, maxLines = 1)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            SongSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        sort = option
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (musics.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                SongListColumn(
                    musics = list,
                    region = region,
                    unitColors = unitColors,
                    favoriteIds = favorites.keys,
                    emptyTitle = "这个人还没有演唱记录",
                    emptyDescription = "数据来自 musicVocals，同步一次就会有。",
                    onToggleFavorite = { container.appSettings.toggleFavoriteSong(it) },
                    // 点的是正在播的那首就是暂停/继续，否则整份列表入队从那首开始
                    onPlay = { music -> player.playFromList(list, music.id) },
                    onOpenDetail = onOpenDetail,
                    playingMusicId = playback.track?.musicId,
                    isPlayRequested = playback.isPlayRequested,
                )
            }
        }
    }
}
