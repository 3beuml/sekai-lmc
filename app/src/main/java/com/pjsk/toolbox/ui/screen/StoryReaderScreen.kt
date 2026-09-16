package com.pjsk.toolbox.ui.screen

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.story.StoryScript
import com.pjsk.toolbox.data.story.StorySource
import com.pjsk.toolbox.data.story.StoryType
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.util.AssetDownloader
import kotlinx.coroutines.launch

/**
 * 剧情阅读器（第 3 层）。
 *
 * 形态对齐 `pjsk.moe/zh-cn/story/unit/2/leo_01_00/`：
 * 返回上级 + 标题 + 编号 + **来源徽标（国服/日服）** + 正文。
 *
 * 用户明确「**不用加完整的游戏演出**」，所以这里只做：
 *  - 开场背景图当**氛围底图**（模糊铺底，不做逐句换背景）
 *  - 逐句正文（说话人 + 台词）
 *  - 点有配音的句子**试听语音**
 *
 * **不做**：立绘站位、表情、转场、特效、SE —— 那些要完整实现 `Snippets` + `LayoutData`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryReaderScreen(
    type: StoryType,
    assetbundleName: String?,
    scenarioId: String,
    /** 从上一步带过来的标题，用于顶栏。 */
    title: String?,
    region: ServerRegion,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PjskApp).container
    val repository = container.storyRepository
    val scope = rememberCoroutineScope()

    var loading by remember(scenarioId) { mutableStateOf(true) }
    var script by remember(scenarioId) { mutableStateOf<StoryScript?>(null) }
    var error by remember(scenarioId) { mutableStateOf<String?>(null) }
    var playingIndex by remember(scenarioId) { mutableIntStateOf(-1) }
    var voiceError by remember(scenarioId) { mutableStateOf<String?>(null) }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(scenarioId) {
        onDispose {
            val p = player
            player = null
            p?.runCatching { release() }
        }
    }

    LaunchedEffect(type, assetbundleName, scenarioId) {
        loading = true
        error = null
        repository.loadScript(type, assetbundleName, scenarioId)
            .onSuccess { script = it }
            .onFailure { error = it.message ?: "正文加载失败" }
        loading = false
    }

    fun playVoice(index: Int, voiceId: String) {
        voiceError = null
        playingIndex = index
        scope.launch {
            val url = AssetUrls.storyVoice(region, type.kind, scenarioId, voiceId)
            AssetDownloader.cacheToCacheDir(context, container.httpClient, url, "story_voice")
                .onSuccess { file ->
                    runCatching {
                        val mp = player ?: MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build(),
                            )
                            setOnCompletionListener { playingIndex = -1 }
                            setOnErrorListener { _, _, _ -> playingIndex = -1; true }
                            player = this
                        }
                        if (mp.isPlaying) mp.stop()
                        mp.reset()
                        mp.setDataSource(file.absolutePath)
                        mp.prepare()
                        mp.start()
                    }.onFailure {
                        voiceError = it.message ?: "播放失败"
                        playingIndex = -1
                    }
                }
                .onFailure {
                    voiceError = "语音获取失败：${it.message ?: "未知原因"}"
                    playingIndex = -1
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title ?: scenarioId, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上级")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> EmptyState(
                    title = "正文加载失败",
                    description = (error ?: "") +
                        "\n\n正文是在线取的（一话 60~158 KB），需要联网；" +
                        "如果简中服还没上这一话，会自动回退到日服。",
                )

                else -> {
                    val current = script
                    if (current == null || current.lines.isEmpty()) {
                        EmptyState(title = "这一话没有正文", description = "资源里没有解析出任何台词。")
                    } else {
                        ReaderBody(
                            script = current,
                            region = region,
                            scenarioId = scenarioId,
                            playingIndex = playingIndex,
                            voiceError = voiceError,
                            onPlayVoice = ::playVoice,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderBody(
    script: StoryScript,
    region: ServerRegion,
    scenarioId: String,
    playingIndex: Int,
    voiceError: String?,
    onPlayVoice: (index: Int, voiceId: String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 氛围底图：把开场背景图放大 + 模糊铺在正文后面（参考站也是这么做的）。
        // 刻意**不做逐句换背景** —— 那要解析 Snippets 里的 ChangeBackground 指令。
        script.firstBackground?.let { bg ->
            AsyncImage(
                model = AssetUrls.storyBackground(region, bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(24.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.25f,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            ),
                        ),
                    ),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                ReaderHeader(script = script, scenarioId = scenarioId)
            }
            itemsIndexed(
                items = script.lines,
                key = { index, _ -> index },
            ) { index, line ->
                val hasVoice = line.voiceId != null
                val playing = playingIndex == index
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (hasVoice) {
                                Modifier.clickable { onPlayVoice(index, line.voiceId!!) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 说话人头像：Character2dId → character2ds → characterId → chr_tl_<id>.webp
                        //
                        // ⚠️ 这张图是 **376×840 的竖长半身立绘**（角色选择界面用的）。
                        // 默认居中裁切成圆形会**正好裁到胸口**，根本看不出是谁 ——
                        // 所以必须 `alignment = TopCenter`，把裁切框对到头部。
                        line.characterId?.let { charId ->
                            AsyncImage(
                                model = AssetUrls.characterAvatar(region, charId),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        line.speaker?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (hasVoice && line.speaker != null) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = if (playing) Icons.Default.PlayArrow
                                else Icons.Default.VolumeUp,
                                contentDescription = if (playing) "播放中" else "播放语音",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = line.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        // 有头像时正文与头像对齐，读起来更整齐
                        modifier = if (line.characterId != null) Modifier.padding(start = 36.dp) else Modifier,
                    )
                }
            }
            voiceError?.let { message ->
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** 正文顶部：编号 + 来源徽标 + 背景/语音说明。 */
@Composable
private fun ReaderHeader(script: StoryScript, scenarioId: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = scenarioId,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            // 来源徽标：简中服进度落后，所以同一活动里也可能有的有中文、有的是日文
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (script.source == StorySource.CN) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = script.source.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (script.source == StorySource.CN) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "共 ${script.lines.size} 句",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (script.source == StorySource.CN) {
                "正文来自简中服官方资源。点带 🔊 的句子可以试听语音。"
            } else {
                "简中服还没上这一话，显示的是日服原文。点带 🔊 的句子可以试听语音。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
