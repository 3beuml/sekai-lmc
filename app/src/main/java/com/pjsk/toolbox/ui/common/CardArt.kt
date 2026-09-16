package com.pjsk.toolbox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.pjsk.toolbox.data.card.Card
import com.pjsk.toolbox.data.card.CardArtPlan
import com.pjsk.toolbox.data.card.CardAttr
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 卡面素材的图片加载体验。
 *
 * ## 为什么需要这一套
 *
 * 实测：`storage.sekai.best` 的卡面小图单张 **58 KB**，下载速度约 8–56 KB/s，
 * 也就是**一张图要 1–7 秒**。在这么慢的前提下，有两个体验问题必须一起解决：
 *
 * 1. **滑过去就取消**：用户在列表里滑动往往不到 1 秒就滑过去了，而 Compose 会销毁离开屏幕的条目
 *    → Coil 的请求被取消 → **磁盘里什么都没留下** → 滑回来又从头下。
 *    解决办法是 [PrefetchCardImages]：预取请求**不绑在组合上**，能真正下完并落盘。
 * 2. **加载期间一片空白**：看起来像坏了。
 *    解决办法是 [CardArtPlaceholder]：先用**属性色底 + 3 KB 方形小图标**铺住，
 *    真实卡面加载完自然盖上。小图只有 3–5 KB，一屏 8 张也才 40 KB，几乎瞬间。
 *
 * 注意：小图标走的是 `thumbnail/chara/<素材名>_<normal|after_training>.webp`，
 * 它**和卡面一样有三态** —— 实测「出厂即特训后」的卡没有 `_normal` 图标（404），
 * 所以占位图也必须跟着 [Card.artPlan] 选状态。
 */

/** 占位小图用的边长。 */
private val PLACEHOLDER_ICON_SIZE = 76.dp

/**
 * 属性图标。
 *
 * 5 张图**内置在 APK 里**（`assets/icons/attr/<attr>.webp`，共 16.6 KB，
 * 来源与许可见同目录的 `SOURCE.md`）。内置而不是走网络：总共才十几 KB，
 * 内置后离线可用、零延迟，也不依赖任何第三方。
 *
 * 为什么不用官方素材桶：实测 `sekai-jp-assets/common_icon/` 用 delimiter 列举是
 * **零个子目录**（唯一 Key 只有 `common_icon_atlas.spriteatlas`，Unity 图集，不能直接用），
 * 而 `thumbnail/common/` 目录**根本不存在** —— 官方桶里就没有可分发的单张属性图标。
 */
@Composable
fun AttrIcon(
    attr: CardAttr?,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
) {
    if (attr == null) return
    AsyncImage(
        model = "file:///android_asset/icons/attr/${attr.key}.webp",
        contentDescription = attr.label,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

/**
 * 卡面**统一的解码尺寸**（像素）。
 *
 * ⚠️ 这个常量是「切页面不再看到加载」的关键。
 *
 * Coil 的内存缓存键**包含目标解码尺寸**。如果首页格子（245dp ≈ 796px）和图鉴格子
 * （整屏宽，每半边约 585px）各自按自己的布局尺寸请求，同一张图在内存缓存里就是
 * **两条不同记录**，互相用不上 —— 表现就是「每次切换页面都要重新解码一遍」，
 * 即使文件早就在磁盘缓存里了（磁盘缓存按 URL 键，所以不会重新下载，但仍要解码 + 淡入，
 * 看起来就是明显的加载）。
 *
 * 取 800×451（卡面 940×530 的等比例缩小）：
 *  - 足够覆盖首页最宽的格子（796px），所以两个页面都能直接复用同一份解码结果；
 *  - 比原图小，单张内存占用从约 1.5 MB 降到约 1.2 MB。
 */
private val ART_DECODE_SIZE = Size(
    AssetUrls.PREVIEW_WIDTH,
    AssetUrls.PREVIEW_WIDTH * 530 / 940, // 卡面 940×530 的等比例
)

/**
 * 卡面占位层：**灰底** + 很淡的方形小图标。
 *
 * 为什么要灰底而不是像早期那样用属性色：属性色虽然好看，但在「一片还没加载出来」的时候
 * 满屏彩色反而更吵、更像出错。灰色是「安静地等着」的通用语言（骨架屏），
 * 用户不会焦虑；小图标淡化到 0.3 只是为了提示「这里马上会有一张哪张卡」，不抢眼。
 */
@Composable
fun CardArtPlaceholder(
    region: ServerRegion,
    card: Card,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = AssetUrls.cardIcon(
                region = region,
                assetbundleName = card.assetbundleName,
                // 三态：只特训后的卡没有 _normal 图标（实测 404）
                trained = card.artPlan != CardArtPlan.NORMAL_ONLY,
            ),
            contentDescription = null,
            modifier = Modifier.size(PLACEHOLDER_ICON_SIZE),
            contentScale = ContentScale.Fit,
            alpha = 0.3f,
        )
    }
}

/**
 * 「数据还在加载」还是「确实没有数据」。
 *
 * 为什么需要：数据（现在是本地数据库）通常几百毫秒内就到，但如果列表一为空就显示
 * 「请去下载 / 数据还没同步」，用户每次切换页面都会撞上这句话，非常焦虑。
 * 所以给一段宽限期（默认 **8 秒**——比首次启动导入内置数据的约 6 秒更长，
 * 这样全新安装的第一屏也不会闪出「去下载」）：期间显示灰色骨架，
 * 超时后仍为空才认定「确实没有数据」。
 *
 * @return true 表示**可以显示空状态提示**了（即已经等够了且确实为空）
 */
@Composable
fun rememberEmptyConfirmed(isEmpty: Boolean, graceMillis: Long = 8_000): Boolean {
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(isEmpty, graceMillis) {
        timedOut = false
        if (isEmpty) {
            delay(graceMillis)
            timedOut = true
        }
    }
    return isEmpty && timedOut
}

/**
 * 卡牌列表的骨架：一个卡面比例（1.68）的灰块 + 下面两条短灰条。
 *
 * 形状刻意与真实条目**完全一致**，这样数据到达时不会出现「空白 → 突然撑开」的跳变，
 * 切换页面时也就看不到"没有东西"。
 */
@Composable
fun CardTileSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.68f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/** 列表骨架：铺若干条**可滑动**的占位条目。 */
@Composable
fun CardListSkeleton(count: Int = 8, modifier: Modifier = Modifier) {
    // 用 LazyColumn 而不是普通的 Column —— 关键在于**骨架要能滑动**。
    // 静态灰块滑不动，手感像"卡死了"，反而更让人焦虑；
    // 能拖动、有回弹，就与真列表的手感一致，等待期间也能安心。
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(count) { CardTileSkeleton() }
    }
}

/**
 * 卡面本体。用 [ContentScale.Fit] 保证**完整显示不裁切**；
 * 左右半分时用 `CenterStart` / `CenterEnd` 决定从哪一侧取景。
 */
@Composable
fun CardArtImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    AsyncImage(
        // 明确指定统一尺寸，保证「首页 / 图鉴」命中同一条内存缓存记录
        model = ImageRequest.Builder(context)
            .data(url)
            .size(ART_DECODE_SIZE)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
    )
}

/**
 * 预取「视口前后各 [radius] 条」的图片。
 *
 * 关键点：预取用的是 `enqueue`，它**不随组合销毁而取消**，所以哪怕用户瞬间滑过去，
 * 这些请求也会在后台跑完并写进磁盘缓存；等用户滑回来时就是**本地读取**。
 * 这正是治「看一次就没了、还要重新下」的那一步。
 *
 * 重复调用同一批 URL 是廉价的（内存/磁盘缓存命中），所以这里只按「可见范围变化」触发。
 *
 * @param urlsAt 给定条目的下标，返回它需要的图片 URL（一个条目可能有左右两张）
 */
@Composable
fun PrefetchCardImages(
    listState: LazyListState,
    itemCount: Int,
    urlsAt: (Int) -> List<String>,
    // 预取半径取 3 而不是 10：实测一张卡面 58 KB（预览档 15.7 KB），
    // ±10 一次就是几十个请求、上兆字节，会在用户还没看清时就抢走可见图片的带宽 ——
    // 「第一次进来特别慢」很大程度上是这个造成的。±3 足够覆盖「快速滑一屏」。
    radius: Int = 3,
) {
    val context = LocalContext.current
    val loader = context.imageLoader
    LaunchedEffect(listState, itemCount) {
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo
            Triple(
                listState.isScrollInProgress,
                info.firstOrNull()?.index,
                info.lastOrNull()?.index,
            )
        }
            .distinctUntilChanged()
            .collect { (scrolling, first, last) ->
                if (first == null || last == null) return@collect
                // ⚠️ **滑动中不预取**。
                //
                // 这是「快速下滑时加载很慢」的主因：滑动时可见项本来就要下 8 张图，
                // 而预取还在同时下十几张，二十多个请求挤在同一条链路上，
                // 结果可见项也慢、预取也慢。
                // 滑动中把带宽全让给可见项（没轮到的就是灰框，符合预期）；
                // 手一停 isScrollInProgress 变回 false，预取立刻补上。
                if (scrolling) return@collect
                val from = (first - radius).coerceAtLeast(0)
                val to = (last + radius).coerceAtMost(itemCount - 1)
                if (from > to) return@collect
                // 近距窗口：连**内存缓存**一起写。这样从别的页面切回来时，
                // 最近看过的这几张直接从内存命中，完全无感。
                val nearFrom = (first - 2).coerceAtLeast(0)
                val nearTo = (last + 2).coerceAtMost(itemCount - 1)
                for (index in from..to) {
                    // 近距进内存、远距只进磁盘：
                    // 远距预取量最大可达几十张，若也进内存就会把屏幕上正在显示的挤掉
                    // （这正是「切页面时图片突然消失」那个 bug 的成因，别再犯）。
                    val inNearWindow = index in nearFrom..nearTo
                    for (url in urlsAt(index)) {
                        loader.enqueue(
                            ImageRequest.Builder(context)
                                .data(url)
                                .size(ART_DECODE_SIZE)
                                .memoryCachePolicy(
                                    if (inNearWindow) CachePolicy.ENABLED else CachePolicy.DISABLED,
                                )
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                        )
                    }
                }
            }
    }
}

/** 一张卡在列表里会显示的全部卡面 URL（左右半各一张，或只有一张）。 */
fun Card.listArtUrls(region: ServerRegion): List<String> = when (artPlan) {
    CardArtPlan.BOTH -> listOf(
        AssetUrls.cardPreview(region, assetbundleName, trained = false),
        AssetUrls.cardPreview(region, assetbundleName, trained = true),
    )
    CardArtPlan.TRAINED_ONLY -> listOf(
        AssetUrls.cardPreview(region, assetbundleName, trained = true),
    )
    CardArtPlan.NORMAL_ONLY -> listOf(
        AssetUrls.cardPreview(region, assetbundleName, trained = false),
    )
}
