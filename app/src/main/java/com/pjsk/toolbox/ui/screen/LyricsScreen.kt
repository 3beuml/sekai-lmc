package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.music.LyricsDocument
import com.pjsk.toolbox.data.music.LyricsIndexEntry
import com.pjsk.toolbox.data.music.LyricsLine
import com.pjsk.toolbox.data.music.LyricsRendition
import com.pjsk.toolbox.data.music.LyricsResult
import com.pjsk.toolbox.data.music.LyricsVersion
import com.pjsk.toolbox.data.music.lyricsStateLabel
import com.pjsk.toolbox.data.music.noLyricsReasonLabel
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.RemoteImage
import com.pjsk.toolbox.ui.common.RowDivider
import com.pjsk.toolbox.ui.common.SectionTitle
import com.pjsk.toolbox.ui.common.openExternalLink

/**
 * 歌词页。
 *
 * ## 这一页**做不到**什么（数据源的限制，不是偷懒）
 *
 * **没有时间戳**。数据源的校验器是严格键白名单，多一个 `startTime` 整份文档就判非法，
 * 全文件也扫不到任何时间类字段。所以：
 *  - 没有卡拉OK逐句高亮，也不跟随播放滚动；
 *  - **不做假的「按行数均分估算」**——那只会得到一条会飘的高亮线，比没有更糟。
 *
 * 参考站（pjsk.moe）自己也没做，只是按行排版。
 *
 * ## 两套 schema
 *
 * v3（行内 `zh-CN`）和 v4（译本里的平行数组）都支持，解析在 `LyricsModels.kt`。
 *
 * ## 演唱者怎么标
 *
 * 数据里能拿到**每一段**是谁唱的（`segments[].performerIds`）。这里在
 * **演唱者发生变化的那一行**左侧标一组头像 + 名字（连续同一个人不重复标）——
 * 和参考页的做法一致。
 *
 * ⚠️ 参考页还会**按角色主题色给每段文字上色**，这一版**没做**：
 * 角色主题色不在我们内置的任何一张表里（`unitProfiles` 只有组合色），
 * 要做得自己硬编码 26 个色值 —— 那属于"编数据"，先不做，等你决定。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    musicId: Int,
    region: ServerRegion,
    onBack: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val nameLanguage by container.appSettings.nameLanguage.collectAsState()

    var result by remember(musicId) { mutableStateOf<LyricsResult?>(null) }
    var loading by remember(musicId) { mutableStateOf(true) }
    var renditionIndex by remember(musicId) { mutableStateOf(0) }
    var showGameVersion by remember(musicId) { mutableStateOf(false) }

    LaunchedEffect(musicId) {
        loading = true
        result = container.lyricsRepository.load(musicId)
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歌词") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val current = result
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                current is LyricsResult.Failed -> LyricsMessage(
                    title = "歌词加载失败",
                    description = current.message,
                )

                current is LyricsResult.NoEntry || current is LyricsResult.NoLyrics -> {
                    val reason = (current as? LyricsResult.NoLyrics)?.reason
                    LyricsMessage(
                        title = "这首曲子还没有歌词",
                        description = noLyricsReasonLabel(reason),
                    )
                }

                current is LyricsResult.Incomplete -> LyricsBody(
                    document = current.document,
                    entry = null,
                    region = region,
                    nameLanguage = nameLanguage,
                    renditionIndex = renditionIndex,
                    showGameVersion = showGameVersion,
                    onRenditionChange = { renditionIndex = it },
                    onVersionChange = { showGameVersion = it },
                    notice = "这份歌词还在录入中，可能不完整。",
                )

                current is LyricsResult.Ok -> LyricsBody(
                    document = current.document,
                    entry = current.entry,
                    region = region,
                    nameLanguage = nameLanguage,
                    renditionIndex = renditionIndex,
                    showGameVersion = showGameVersion,
                    onRenditionChange = { renditionIndex = it },
                    onVersionChange = { showGameVersion = it },
                    notice = null,
                )
            }
        }
    }
}

@Composable
private fun LyricsMessage(title: String, description: String) {
    EmptyState(title = title, description = description)
}

// ─────────────────────────────────────────────────────────────
// 正文
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsBody(
    document: LyricsDocument,
    entry: LyricsIndexEntry?,
    region: ServerRegion,
    nameLanguage: NameLanguage,
    renditionIndex: Int,
    showGameVersion: Boolean,
    onRenditionChange: (Int) -> Unit,
    onVersionChange: (Boolean) -> Unit,
    notice: String?,
) {
    val rendition = document.renditions.getOrNull(renditionIndex) ?: document.renditions.firstOrNull()
    if (rendition == null) {
        LyricsMessage("这份歌词没有可显示的内容", "歌词文档里没有任何一行。")
        return
    }

    // 选了 game 但这份 rendition 没有游戏版 → 退回完整版，避免出现空白页
    val useGame = showGameVersion && rendition.hasGame
    val version: LyricsVersion? = if (useGame) rendition.game else rendition.full ?: rendition.game
    if (version == null) {
        LyricsMessage("这份歌词没有可显示的内容", "歌词文档里没有任何一行。")
        return
    }

    // 「这一句是谁唱的」：按 **角色 id** 查名字。
    // ⚠️ 不要再按"这一行里第几个演唱者"的位置去取名 —— 两个列表的顺序毫无关系，
    // 只唱一个人的行下标恒为 0，会整页都显示成名单里的第一个人（已修，见 §bug）。
    val performerNameById = remember(rendition) { rendition.nameById }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    ) {
        // 标题 + 版本徽章
        item {
            val titleJa = entry?.titleJa
            val titleZh = entry?.titleZh
            Column {
                Text(
                    text = when (nameLanguage) {
                        NameLanguage.JAPANESE -> titleJa ?: titleZh.orEmpty()
                        else -> titleZh ?: titleJa.orEmpty()
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                val secondary = when (nameLanguage) {
                    NameLanguage.CHINESE_FIRST -> null
                    NameLanguage.JAPANESE -> titleZh?.takeIf { it != titleJa }
                    NameLanguage.BILINGUAL -> titleJa?.takeIf { it != titleZh }
                }
                if (!secondary.isNullOrBlank()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append("歌词资料库 v${document.revision}")
                        append(" · ")
                        append(lyricsStateLabel(document.state))
                        if (rendition.translators.isNotEmpty()) {
                            append(" · 翻译：")
                            append(rendition.translators.joinToString("、"))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        notice?.let { text ->
            item {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(10.dp),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        // 版本切换（多个 rendition 才显示）
        if (document.renditions.size > 1) {
            item {
                SectionTitle("演唱版本")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    document.renditions.forEachIndexed { index, item ->
                        FilterChip(
                            selected = index == renditionIndex,
                            onClick = { onRenditionChange(index) },
                            label = { Text(item.displayLabel, maxLines = 1) },
                        )
                    }
                }
            }
        } else {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = rendition.displayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 完整版 / 游戏版切换（两个都有才显示）
        if (rendition.hasFull && rendition.hasGame) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !useGame,
                        onClick = { onVersionChange(false) },
                        label = { Text("完整版（${rendition.full?.lines?.size ?: 0} 行）") },
                    )
                    FilterChip(
                        selected = useGame,
                        onClick = { onVersionChange(true) },
                        label = { Text("游戏版（${rendition.game?.lines?.size ?: 0} 行）") },
                    )
                }
            }
        }

        // 歌词正文
        item {
            SectionTitle("歌词正文")
            Text(
                text = "左：日文原文（含假名注音）　右：中文翻译",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(version.lines, key = { it.id.ifBlank { it.order.toString() } }) { line ->
            // 上一行的演唱者，用来判断这一行要不要重新标一次演唱者
            val prev = version.lines.getOrNull(line.order - 1)
            LyricLineItem(
                line = line,
                region = region,
                performerNameById = performerNameById,
                showSingers = prev?.performerIds != line.performerIds,
            )
        }

        // 来源与许可（**许可要求必须展示**）
        item {
            SectionTitle("歌词来源与许可")
            rendition.credits.forEach { credit ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        text = credit.providerLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = credit.title + (credit.licenseName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        credit.revisionUrl?.let { url ->
                            CreditLink(label = "查看原文与版本", url = url)
                        }
                        credit.licenseUrl?.let { url ->
                            CreditLink(label = credit.licenseName ?: "许可协议", url = url)
                        }
                    }
                }
                RowDivider()
            }
            if (rendition.translators.isNotEmpty()) {
                Text(
                    text = "中文翻译：${rendition.translators.joinToString("、")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CreditLink(label: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.clickable { openExternalLink(context, url) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 一行歌词
// ─────────────────────────────────────────────────────────────

@Composable
private fun LyricLineItem(
    line: LyricsLine,
    region: ServerRegion,
    /** 角色 id → 演唱者名字。**必须按 id 查**（见调用处的说明）。 */
    performerNameById: Map<Int, String>,
    showSingers: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 段落分隔：数据里没有空行约定，只靠 stanzaBreakBefore 这个标记
        if (line.stanzaBreakBefore) {
            Spacer(Modifier.height(14.dp))
            RowDivider()
        }
        Spacer(Modifier.height(10.dp))

        if (showSingers && line.performerIds.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                line.performerIds.forEach { id ->
                    RemoteImage(
                        url = AssetUrls.characterAvatar(region, id),
                        contentDescription = null,
                        // 半身像是 376×840 竖构图，圆形头像必须顶部对齐，否则是胸口不是脸
                        modifier = Modifier.size(20.dp).clip(CircleShape),
                        alignment = Alignment.TopCenter,
                    )
                    // ⚠️ 按 **id** 取名字：头像也是按 id 拿的，两者必须同一个来源，
                    // 否则就会出现"图上是一个人、名字写着另一个人"。
                    val name = performerNameById[id]
                    if (!name.isNullOrBlank()) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // 日文原文（带注音）
        Text(
            text = line.annotatedJapanese(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // 中文翻译
        if (!line.translation.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = line.translation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 把一行拼成带注音的富文本。
 *
 * Compose 里没有 HTML 的 `<ruby>`，所以注音用**小字跟在原字后面的括号**表达：
 * `生き(い)てみようかな`。这比只显示原字更能帮到不熟悉汉字的读者，
 * 而且不需要自定义 Layout（真正的「字上注音」要自己写 `Layout` 去量行高）。
 */
private fun LyricsLine.annotatedJapanese(): AnnotatedString = buildAnnotatedString {
    segments.forEach { segment ->
        if (segment.ruby.isEmpty()) {
            append(segment.text)
            return@forEach
        }
        segment.ruby.forEach { ruby ->
            withStyle(SpanStyle()) { append(ruby.text) }
            ruby.reading?.let { reading ->
                withStyle(SpanStyle(fontSize = 10.sp, color = ANNOTATION_COLOR_HINT)) {
                    append("(")
                    append(reading)
                    append(")")
                }
            }
        }
    }
}

/** 注音颜色。用固定灰度而不是主题色：注音是辅助信息，不该抢正文的注意力。 */
private val ANNOTATION_COLOR_HINT = androidx.compose.ui.graphics.Color(0xFF9AA0A6)
