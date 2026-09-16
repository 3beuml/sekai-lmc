package com.pjsk.toolbox.data.remote

import com.pjsk.toolbox.data.story.StoryAssetKind

/**
 * 素材 CDN 路径构造。
 *
 * 验证方式：`storage.sekai.best` 是**允许公开列举**的 S3 兼容桶，
 * 所以可以直接列目录确认真实 key，而不是靠猜。例如：
 * ```
 * GET https://storage.sekai.best/sekai-jp-assets/?list-type=2&prefix=stamp/&max-keys=6
 * GET https://storage.sekai.best/sekai-jp-assets/?list-type=2&delimiter=/   ← 列出全部分类
 * ```
 * 下面每一项都标注了验证状态：✅ 已实测存在 / ⚠️ 未实测（调用方必须容忍 404）。
 *
 * 共同结论：**每个资源同时提供 `.png` 与 `.webp` 两种格式，webp 体积通常只有 png 的
 * 1/4 ~ 1/8**（例：`stamp0001.png` 76 KB vs `stamp0001.webp` 17 KB；
 * `bg_gacha326.png` 2.6 MB vs `.webp` 330 KB）。移动端一律用 `.webp`。
 *
 * 素材桶的顶层分类（✅ 实测完整列表）：
 * `actionset/ area/ area_image/ area_sd/ areaitem/ bonds_honor/ campaign/ character/
 *  character_archive/ character_icon/ comic/ common_icon/ crystal_shop/ custom_profile/
 *  effect_asset/ effects/ event/ event_story/ exchange/ font/ gacha/ home/ honor/
 *  honor_frame/ iconpenlightatlas/ live/ live2d/ live_pv/ livetalk/ liveuniticonatlas/
 *  loginbonus/ lottery_game/ mission/ model3d/ movie/ multi_room/ music/ music_collabo/
 *  mysekai/ mysekai_mission_pass1..3/ ondemand/ paid_virtual_live/ player_frame/
 *  rank_live/ rank_match/ scenario/ score_maker/ serial_code/ sound/ stamp/ stamp_balloon/
 *  stamp_mission/ story/ streaming_live/ thumbnail/ title_screen/ tutorial/ ui/`
 */
object AssetUrls {

    /** 官方素材 CDN 的 host。 */
    const val OFFICIAL_HOST = "storage.sekai.best"

    /**
     * 第三方镜像站的 host（社区自建，路径与官方完全一致）。
     *
     * ⚠️ 为什么用它：**卡面与音频在国内直连官方 CDN 基本不可用**。2026-09 在校园网实测
     * （同一个文件、同一时刻）：
     *
     * | 资源 | 官方 storage.sekai.best | 镜像 storage.exmeaning.com |
     * |---|---|---|
     * | 卡面大图 webp | 5–40 KB/s（首字节 1.2–7.5 秒） | **582 KB/s（首字节 220 毫秒）** |
     * | 曲绘 webp | 14–41 KB/s | **1753 KB/s（73 毫秒）** |
     * | 卡面图标 / 贴纸 / 抽卡语音 | 慢 | 21–60 毫秒 |
     *
     * 覆盖范围也实测过：**jp / cn / en / kr 四个区服桶都在**，卡面、图标、贴纸、
     * 抽卡语音、长版与短版音频都能取到。
     *
     * ⚠️ **但它没有剧情 `.asset`**（实测官方 206、镜像 404），所以剧情、背景等
     * 一律仍走官方；镜像只用在 [cardImage] / [cardPreview] / [cardIcon] / [cardImagePng]
     * 与 [musicAudio] / [musicJacketJp] 这几处。
     *
     * ⚠️ 它是别人的服务，随时可能挂 → [MirrorFallbackInterceptor] 负责在这种时候
     * 自动改回官方重试，所以不会出现「镜像挂了整个功能不可用」。
     */
    const val MIRROR_HOST = "storage.exmeaning.com"

    /** 镜像站根地址（不含尾部斜杠）。 */
    const val MIRROR_BASE = "https://$MIRROR_HOST"

    /**
     * 镜像站上的**区服素材根**。
     *
     * 镜像的桶名与官方相同（`sekai-<bucket>-assets`），所以只需要把 host 换掉；
     * 实测 `sekai-jp-assets` / `sekai-cn-assets` / `sekai-en-assets` / `sekai-kr-assets` 都存在。
     */
    private fun mirrorAssetBase(region: ServerRegion): String =
        "$MIRROR_BASE/sekai-${region.assetBucket}-assets"

    /**
     * 音频（镜像）：同样固定 jp 桶。
     *
     * 官方那边音频**只有日服桶有**（`sekai-cn-assets/music/...` 实测 404），
     * 镜像这边虽然忽略区服、五区同一份文件，但用同一个 jp 桶名即可，路径完全一致。
     */
    private const val AUDIO_BASE_MIRROR = "$MIRROR_BASE/sekai-jp-assets"

    // ─────────────────────────────────────────────────────────────
    // 音乐
    // ─────────────────────────────────────────────────────────────

    /** 曲绘。✅ 已实测：`music/jacket/jacket_s_001/jacket_s_001.webp`（jp / cn / en 三个桶都通）。 */
    fun musicJacket(region: ServerRegion, assetbundleName: String): String =
        "${mirrorAssetBase(region)}/music/jacket/$assetbundleName/$assetbundleName.webp"

    /**
     * 曲绘（固定日服桶）。
     *
     * 播放轨要用它填 MediaSession 的封面 —— 通知栏/锁屏那张图由**系统服务**按 URL 去拉，
     * 那时拿不到界面的区服设置，而曲绘在三个桶里是同一张图，所以固定 jp 最省事。
     */
    fun musicJacketJp(assetbundleName: String): String =
        "$AUDIO_BASE_MIRROR/music/jacket/$assetbundleName/$assetbundleName.webp"

    /**
     * 音频目录（✅ 实测 `music/` 下只有 `jacket/ long/ music_score/ short/` 四个子目录）。
     *
     * ⚠️ 音频**不要**用这个：见 [musicAudio] 的说明。
     */
    fun musicAudioDir(region: ServerRegion, length: MusicLength, assetbundleName: String): String =
        "${region.assetBase}/music/${length.dir}/$assetbundleName"

    enum class MusicLength(val dir: String) { LONG("long"), SHORT("short") }

    /**
     * 音源文件（试听/播放用）。✅ 已实测这两个路径都能拿到 `audio/mpeg`。
     *
     * ⚠️ **走镜像站，且固定 jp 桶**：
     *  - 官方那边音频**只有日服桶有**（`sekai-cn-assets/music/long/...` 实测 404），
     *    区域不同只影响图片与 master data；镜像路径与官方完全一致，所以直接用 jp 桶名。
     *  - 镜像的音频是 **320 kbps、单首约 5.05 MB**（官方 jp 桶是 128 kbps、约 2.02 MB），
     *    但实测首字节 150ms vs 官方 1.2s、整首取完约 3 秒 vs 100 秒以上
     *    —— 在这条线路上**体积大 2.5 倍也远比官方快**，所以选镜像。
     *  - 镜像万一挂了，[MirrorFallbackInterceptor] 会自动改回官方同路径重试。
     *
     * 两个长度：
     *  - [short] = false（默认）：**游戏版剪辑**，约 1:30~2:15，**开头有 `fillerSec` 秒空白**
     *    （播的时候要跳过，见 `data/music/Timeline.kt`）
     *  - [short] = true：32 秒试听
     */
    fun musicAudio(assetbundleName: String, short: Boolean = false): String =
        if (short) {
            "$AUDIO_BASE_MIRROR/music/short/$assetbundleName/${assetbundleName}_short.mp3"
        } else {
            "$AUDIO_BASE_MIRROR/music/long/$assetbundleName/$assetbundleName.mp3"
        }

    /** 谱面（✅ 目录存在：`music/music_score/`）。内部文件名未实测。 */
    fun musicScoreDir(region: ServerRegion): String = "${region.assetBase}/music/music_score"

    // ─────────────────────────────────────────────────────────────
    // 卡牌
    // ─────────────────────────────────────────────────────────────

    /**
     * 卡面（列表与详情共用，靠 [CardSize] 选目录）。
     *
     * ✅ 已实测确认（含文件名本身，不再是推断）：
     * - 目录 `character/member/<bundle>/`：`card_normal.webp` 2520×1440 / 90 KB；
     *   `card_after_training.webp` 同尺寸（4★ 约 350 KB）。
     * - 目录 `character/member_small/<bundle>/`：同两个文件名，940×530（约 27 KB / 90 KB）。
     * - 同目录还有 `.png` 版本（4★ 的 after_training.png 高达 4.3 MB）——移动端一律用 `.webp`。
     *
     * ⚠️ **`card_after_training.webp` 有 404 是正常的、且是预期行为**：
     * 游戏里只有 3★ / 4★ 有特训后卡面，1★ / 2★ / 生日卡（`rarity_birthday`）**根本没有**。
     * 实测规律：master data 的 `specialTrainingCosts` 非空 ⇔ 特训后卡面存在
     * （抽样 15 张完全吻合；`rarity_1` / `rarity_2` / `rarity_birthday` 全部 404）。
     * 所以**调用方必须先判断这张卡有没有特训后版本**，不要盲目请求再靠 404 兜底，
     * 否则「左右并排」的列表会出现大量半张空白。
     */
    fun cardImage(
        region: ServerRegion,
        assetbundleName: String,
        trained: Boolean,
        size: CardSize = CardSize.SMALL,
    ): String {
        val file = if (trained) "card_after_training" else "card_normal"
        val dir = if (size == CardSize.SMALL) "member_small" else "member"
        return "${mirrorAssetBase(region)}/character/$dir/$assetbundleName/$file.webp"
    }

    /**
     * 卡面的**列表 / 首页预览 URL**。
     *
     * ⚠️ 这里原来是走第三方缩放代理 `wsrv.nl`，把 940px 的原图缩到 800px
     * （58 KB → 35 KB），理由是官方 CDN 实测只有约 50 KB/s、58 KB 要 1.2 秒。
     * **换成镜像线路后不再需要这一跳**：镜像实测 582 KB/s，原图 58 KB 只需约 0.1 秒，
     * 直连还少一个第三方依赖、画质也更好。解码尺寸仍由 [PREVIEW_WIDTH] 约束。
     */
    fun cardPreview(
        region: ServerRegion,
        assetbundleName: String,
        trained: Boolean,
        size: CardSize = CardSize.SMALL,
    ): String = cardImage(region, assetbundleName, trained, size)

    /**
     * 卡面预览的**解码宽度**（Coil 按它采样，见 `ui/common/CardArt.kt`）。
     *
     * 取 800：首页最宽的格子约 796px、列表每半边约 585px，覆盖两处且不再放大；
     * 比 CDN 原图（940px）略小，单张解码内存从约 1.5 MB 降到约 1.2 MB。
     */
    const val PREVIEW_WIDTH = 800

    /** 卡面尺寸档位。列表用 [SMALL]（940×530），详情大图用 [LARGE]（2520×1440）。 */
    enum class CardSize { SMALL, LARGE }

    /**
     * 卡牌的**方形小图标**（`thumbnail/chara/<素材名>_<状态>.webp`）。
     *
     * 实测只有 **3.8 KB / 5.0 KB**（而卡面小图是 58 KB），一屏 8 张也才 40 KB，
     * 所以适合当「卡面还没下完时」的占位图 —— 用户立刻能看到是哪张卡，而不是一片空白。
     *
     * ⚠️ 它和卡面一样**有三态**：实测「出厂即特训后」的卡 `_normal` 图标返回 **404**，
     * 只有 `_after_training` 存在。调用方必须按同一套状态判断来选 `trained`。
     */
    fun cardIcon(region: ServerRegion, assetbundleName: String, trained: Boolean): String {
        val status = if (trained) "after_training" else "normal"
        return "${mirrorAssetBase(region)}/thumbnail/chara/${assetbundleName}_$status.webp"
    }

    /**
     * 卡面的 PNG 版本，给「保存到本地」用。
     *
     * 实测同一路径下 `.png` 与 `.webp` 并存，体积差很多
     * （4★ 的 `card_after_training.png` 有 4.3 MB，`.webp` 只有约 350 KB）。
     * 保存给用户就用 png：无损、任何看图软件都能打开；在线预览仍一律走 webp。
     * sekai.best 的下载也是把 URL 里的 `.webp` 换成 `.png`，做法一致。
     */
    fun cardImagePng(
        region: ServerRegion,
        assetbundleName: String,
        trained: Boolean,
        size: CardSize = CardSize.LARGE,
    ): String = cardImage(region, assetbundleName, trained, size).removeSuffix(".webp") + ".png"

    /**
     * 抽卡台词语音（扭蛋语音）。
     *
     * 路径与判据都对齐 sekai.best（`src/pages/card/CardDetail.tsx`）：
     * ```
     * sound/gacha/get_voice/${card.assetbundleName}/${card.assetbundleName}.mp3
     * ```
     * 实测（2026-09）对 `res001_no003`、`res017_no056` 等请求均返回
     * **200 / `audio/mpeg` / 16~22 KB**，是可直接流式播放的小文件。
     *
     * ⚠️ **「有台词」不等于「有语音」**：`cards.gachaPhrase` 为 `-` 时表示没有台词
     * （实测 1447 张里 347 张是 `-`、1100 张有台词），这种卡整个板块都不该出现。
     * 注意 `id=1462` 属于「有台词但没剧情」的卡，它的语音是存在的 —— 两个字段互不相关。
     */
    fun gachaVoice(region: ServerRegion, assetbundleName: String): String =
        "${region.assetBase}/sound/gacha/get_voice/$assetbundleName/$assetbundleName.mp3"

    /**
     * 活动 logo（卡片详情里「出自哪个活动」用）。
     *
     * 实测 `event/event_afterfire_2026/logo/logo.webp` 返回 **200**。
     * 同目录下的 `logo_rip.webp`、`screen/image/bg.webp`、`banner/banner.webp` **都是 404** ——
     * 所以活动图只有这一个可靠路径，别去猜别的。
     */
    fun eventLogo(region: ServerRegion, assetbundleName: String): String =
        "${region.assetBase}/event/$assetbundleName/logo/logo.webp"

    /**
     * 素材图标。
     *
     * 实测命名规则就是**按素材 id**：`thumbnail/material/material<id>.webp`
     * （如 `material2.webp` = 帅气碎片、`material14.webp` = 奇迹晶石）。
     * 桶里 `.png` / `.webp` 并存，界面一律用 webp。
     *
     * ⚠️ 另有 `thumbnail/common_material/<名字>.webp`（coin / honor_1..4 / ingamevoice），
     * 那是「通用素材」，**不按 id 命名**，目前用不到。
     */
    fun materialIcon(region: ServerRegion, resourceId: Int): String =
        "${region.assetBase}/thumbnail/material/material$resourceId.webp"

    // ── 剧情（正文 / 背景 / 语音）────────────────────────────
    //
    // ⚠️ **正文的 `.asset` 其实就是纯文本 JSON**（以 `{"m_GameObject"...` 开头），
    // 直接 `JSON.parse` 就能读，不需要 Unity 资源解析器。这是整个剧情模块能做的前提。
    //
    // 路径全部实测过（2026-09-14）：
    //  - 活动：`event_story/<活动bundle>/scenario/<id>.asset` → 200，119 KB
    //    ⚠️ 用**活动**的 bundle，不是每话的（`event_afterfire_2026`，不是 `..._01`）
    //  - 卡牌：`character/member/<卡bundle>/<id>.asset` → 200
    //  - 主线：`scenario/unitstory/<**章节**bundle>/<id>.asset` → 200，158 KB
    //    ⚠️ 章节 bundle 形如 `light-sound-story-chapter`
    //  - 自我介绍：`scenario/profile/<id>.asset`（无 bundle）
    //  - 特殊：`scenario/special/<bundle>/<id>.asset`
    //  - 语音：卡牌是 `sound/card_scenario/voice/...`，**另一个看似合理的
    //    `sound/scenario/voice/...` 对卡牌剧情是 404**（实测）
    //  - 背景：`scenario/background/<bg>/<bg>.webp` → 200，116 KB

    /** 剧情正文（`.asset`，实为 JSON 文本）。 */
    fun storyScenario(
        region: ServerRegion,
        type: StoryAssetKind,
        assetbundleName: String?,
        scenarioId: String,
    ): String = when (type) {
        StoryAssetKind.EVENT ->
            "${region.assetBase}/event_story/$assetbundleName/scenario/$scenarioId.asset"
        StoryAssetKind.CARD ->
            "${region.assetBase}/character/member/$assetbundleName/$scenarioId.asset"
        StoryAssetKind.UNIT ->
            "${region.assetBase}/scenario/unitstory/$assetbundleName/$scenarioId.asset"
        StoryAssetKind.SELF ->
            "${region.assetBase}/scenario/profile/$scenarioId.asset"
        StoryAssetKind.SPECIAL ->
            "${region.assetBase}/scenario/special/$assetbundleName/$scenarioId.asset"
        StoryAssetKind.AREA ->
            "${region.assetBase}/scenario/actionset/group$assetbundleName/$scenarioId.asset"
    }

    /**
     * **角色头像**（正脸小图）。实测路径是 `character/character_select/chr_tl_<角色id>.webp`。
     *
     * ⚠️ 注意是 `chr_tl_`，而且**没有子目录** —— 函数签名里那个 `assetbundleName` 参数
     * （[characterImage]）拼出来的形状是错的、会 404。头像只需要**角色 id**。
     */
    fun characterAvatar(region: ServerRegion, characterId: Int): String =
        "${region.assetBase}/character/character_select/chr_tl_$characterId.webp"

    /** 活动剧情的一话截图（16:9）。实测 `event_story/<b>/episode_image/<b>_NN.webp`。 */
    fun eventEpisodeImage(region: ServerRegion, assetbundleName: String, episodeNo: Int): String {
        val nn = episodeNo.toString().padStart(2, '0')
        return "${region.assetBase}/event_story/$assetbundleName/episode_image/${assetbundleName}_$nn.webp"
    }

    /** 活动剧情横幅（活动详情页顶部）。 */
    fun eventStoryBanner(region: ServerRegion, assetbundleName: String): String =
        "${region.assetBase}/event_story/$assetbundleName/screen_image/banner_event_story.webp"

    /** 剧情用背景图（`bgName` 形如 `bg_c000501`）。 */
    fun storyBackground(region: ServerRegion, bgName: String): String =
        "${region.assetBase}/scenario/background/$bgName/$bgName.webp"

    /**
     * 区域（`areas.assetbundleName`，如 `area1`）的**画面**。
     * 实测 `area/area1/background.webp`（94 KB）、`area2`(167 KB)、`area3`(143 KB) 均 200。
     */
    fun areaBackground(region: ServerRegion, assetbundleName: String): String =
        "${region.assetBase}/area/$assetbundleName/background.webp"

    /**
     * 组合 logo —— **内置**在 APK 里（`assets/icons/unit/<缩写>.webp`，6 张共 14.9 KB）。
     *
     * 为什么不走在线：官方桶里**没有**单独的组合 logo（`thumbnail/` 下没有 unit 目录，
     * `liveuniticonatlas/` 是图集不是单图）。这 6 张来自参考站仓库的
     * `web/public/data/icon/`，和属性图标是同一个来源（已有先例）。
     */
    fun unitLogo(unitKey: String?): String? {
        val abbr = when (unitKey) {
            "light_sound" -> "ln"
            "idol" -> "mmj"
            "street" -> "vbs"
            "theme_park" -> "wxs"
            "school_refusal" -> "n25"
            // ⚠️ 主线剧情表里的虚拟歌手 key 是 `piapro`，不是 `virtual_singer`
            "piapro", "virtual_singer" -> "vs"
            else -> return null
        }
        return "file:///android_asset/icons/unit/$abbr.webp"
    }

    /** 剧情语音。卡牌与其它剧情**不在同一个目录**（实测踩过）。 */
    fun storyVoice(
        region: ServerRegion,
        type: StoryAssetKind,
        scenarioId: String,
        voiceId: String,
    ): String {
        val dir = if (type == StoryAssetKind.CARD) "card_scenario" else "scenario"
        return "${region.assetBase}/sound/$dir/voice/$scenarioId/$voiceId.mp3"
    }

    /**
     * 卡牌立绘（去背景）。
     *
     * ✅ 已实测目录：`character/member_cutout/res001_no001/` 下只有 `normal.png` / `normal.webp`
     * —— **注意这里没有子目录**（我曾经误以为是 `<bundle>/normal/normal.webp`，已修正）。
     *
     * 特训后的立绘在同级分类 `character/member_cutout_trm/`（✅ 目录名实测存在），
     * 采用同样结构：⚠️ `member_cutout_trm/<bundle>/normal.webp` 的正确性未实测。
     */
    fun cardCutout(region: ServerRegion, assetbundleName: String, trained: Boolean): String {
        val category = if (trained) "member_cutout_trm" else "member_cutout"
        return "${region.assetBase}/character/$category/$assetbundleName/normal.webp"
    }

    // ─────────────────────────────────────────────────────────────
    // 角色
    // ─────────────────────────────────────────────────────────────

    /**
     * ⚠️ 未实测。
     *
     * `character/` 下实测存在的 17 个子目录：
     * `character_sd_l/ character_select/ character_select_small/ character_trim/ content_select/
     *  full_name_line/ label/ label_horizontal/ label_vertical/ member/ member_cutout/
     *  member_cutout_trm/ member_gacha/ member_small/ name_alphabet/ scenario_data/ small_name/`
     * —— **不存在 `character/character2d/`**（该路径是错的，已修正为 `character_select/`）。
     * 但 `character_select/` 内部的命名规则仍未实测，故本函数结果可能 404。
     */
    fun characterImage(region: ServerRegion, assetbundleName: String): String =
        "${region.assetBase}/character/character_select/$assetbundleName/$assetbundleName.webp"

    // ─────────────────────────────────────────────────────────────
    // 贴纸（表情包制作）
    // ─────────────────────────────────────────────────────────────

    /**
     * 贴纸。✅ 已实测 key 结构：
     * `stamp/stamp0001/stamp0001.webp`、`stamp/stamp0732/stamp0002.webp` 等，
     * 即目录名与文件名都等于 master data 的 `assetbundleName`。
     */
    fun stamp(region: ServerRegion, assetbundleName: String): String =
        "${region.assetBase}/stamp/$assetbundleName/$assetbundleName.webp"

    // ─────────────────────────────────────────────────────────────
    // 活动 / 扭蛋
    // ─────────────────────────────────────────────────────────────

    /**
     * 活动徽章（活动列表缩略图用）。
     * ✅ 已实测存在：`event/event_awakening_2021/icon/icon_eventbadge_1.webp`。
     * 目录名即 master data 的 `assetbundleName`（形如 `event_awakening_2021`）。
     */
    fun eventBadge(region: ServerRegion, assetbundleName: String, index: Int = 1): String =
        "${region.assetBase}/event/$assetbundleName/icon/icon_eventbadge_$index.webp"

    /** 活动点数图标。✅ 已实测存在：`event/<bundle>/icon/icon_eventpoint_1.webp`。 */
    fun eventPointIcon(region: ServerRegion, assetbundleName: String, index: Int = 1): String =
        "${region.assetBase}/event/$assetbundleName/icon/icon_eventpoint_$index.webp"

    /**
     * 卡池 Logo。✅ 已实测存在：`gacha/ab_gacha_326/logo/logo.webp`
     * （同目录 png 67 KB / webp 20 KB）。目录名即 `gachas.json` 的 `assetbundleName`。
     */
    fun gachaLogo(region: ServerRegion, assetbundleName: String): String =
        "${region.assetBase}/gacha/$assetbundleName/logo/logo.webp"

    /**
     * 卡池背景图。✅ 已实测存在：`gacha/ab_gacha_326/screen/texture/bg_gacha326.webp`。
     *
     * ⚠️ 文件名里的数字来自 `gachaId`（`bg_gacha326`）而非 assetbundleName 的后缀，
     * 因此需要调用方传入 `gachaId`。未逐个大池验证，可能 404。
     */
    fun gachaBackground(region: ServerRegion, assetbundleName: String, gachaId: Int): String =
        "${region.assetBase}/gacha/$assetbundleName/screen/texture/bg_gacha$gachaId.webp"

    // ─────────────────────────────────────────────────────────────
    // 称号 / 其他
    // ─────────────────────────────────────────────────────────────

    /**
     * 称号图标。✅ 目录结构实测：`honor/honor_0001/`、`honor/honor_0002/` …
     * ⚠️ 目录内部的文件名未实测，这里按其它分类的约定推断。
     */
    fun honor(region: ServerRegion, assetbundleName: String): String =
        "${region.assetBase}/honor/$assetbundleName/$assetbundleName.webp"

    /** 称号边框（✅ 目录 `honor_frame/` 实测存在）。 */
    fun honorFrame(region: ServerRegion, assetbundleName: String): String =
        "${region.assetBase}/honor_frame/$assetbundleName/$assetbundleName.webp"

    /**
     * Live2D 模型清单。✅ 该路径来自 sekai-viewer 源码常量
     * （`useLive2dModelList` → `${assetUrl.minio.live2d}/live2d/model_list.json`）。
     */
    const val LIVE2D_MODEL_LIST: String =
        "${ServerRegion.LIVE2D_BASE}/live2d/model_list.json"
}
