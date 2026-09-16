package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 「关于本应用」。
 *
 * 这一页同时承担**合规交代**：非官方声明、素材版权、数据来源与许可风险、
 * 第三方参考项目、外部链接与隐私。
 *
 * ⚠️ 写这里的文案时注意两件事（用户明确要求过）：
 *  1. **正文里不要出现 markdown 之类的符号**：以前写过 `**加粗**`，用户看到的就是两个星号；
 *     行首的 `·` 也不要（多行直接用换行排，不加符号）。
 *  2. **第三方的东西必须写清是第三方的**：参考项目、外链站点都要标明，
 *     不能让人以为这些是本应用（或作者）做的。首页「工具 / 参考项目」那几个卡片是简版，
 *     这里是完整版 —— 两处要保持一致，改了这边记得看那边。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于本应用") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("sekai lmc", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Body("一个非官方的 Project Sekai 公开数据下载与预览工具。数据在线获取，App 内不内置游戏素材。")

            // ⚠️ 「应用名称来源」放在最上面（用户要求）：先讲名字从哪来，再讲合规。
            Section("应用名称来源")
            Body(
                "sekai 与异世界情绪（ヰ世界情緒）的音乐同位体「星界（SEKAI）」同名；" +
                    "lmc 同 lemon、melon、cookie。",
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()

            Section("非官方声明")
            Body(
                "本 App 是粉丝作品，与 SEGA、Colorful Palette 均无关联，" +
                    "未获得其授权、赞助或认可。",
            )

            Section("素材版权")
            Body(
                "游戏素材（图片、音频、文本）版权均归 SEGA / Colorful Palette 所有。" +
                    "本 App 仅以在线方式引用这些素材用于资料浏览，不主张任何权利。",
            )
            Body(
                "应用图标由 @kwiozsn 绘制（https://x.com/kwiozsn），在此署名致谢。" +
                    "若原作者有异议，会立即更换。",
            )

            Section("数据来源")
            BodyLines(
                "Master data：Sekai-World/sekai-master-db-diff，日服",
                "中文名叠加：Sekai-World/sekai-master-db-cn-diff，简中服",
                "素材 CDN：storage.sekai.best",
                "版本探测：api.github.com",
            )
            Body(
                "这些是社区维护的公开数据仓库，不是官方接口。" +
                    "其中两个数据仓库本身没有 LICENSE 文件，严格来说数据并未被明确授予再分发许可，" +
                    "自用风险较低，但若要公开发布或再分发数据，请自行评估。",
            )

            Section("数据实时性")
            Body(
                "以日服数据为主结构与进度，中文名按 id 从简中服叠加。" +
                    "简中服尚未更新的新内容会显示日文原名，这是预期行为。" +
                    "部分素材路径尚未完全验证，加载失败时会显示占位图。",
            )

            Section("歌词与翻译")
            Body(
                "歌词按话回退：先取简中服，没有则取日服。" +
                    "词条来自社区（Sekaipedia），带独立授权，其中包含 CC BY-NC-SA 3.0（禁止商用）。" +
                    "若本 App 将来加入广告或内购，这类内容不可使用。",
            )

            Section("参考项目")
            Body(
                "下面这些是本 App 在数据格式、界面交互与功能设计上的参考来源，" +
                    "都是第三方作品，与本 App 没有隶属关系，本 App 也不包含它们的源代码。",
            )
            BodyLines(
                "Sekai-World/sekai-viewer：数据查看器，GPL-3.0。" +
                    "本 App 的数据格式与界面交互参考了它。",
                "StarMoe-org/Moesekai：资料站，AGPL-3.0。同上。",
                "TheOriginalAyaka/sekai-stickers（st.ayaka.one）：第三方在线的表情包制作站，MIT 许可。" +
                    "本 App 只在首页「工具」里提供外链跳转，没有使用它的代码与图片素材。",
                "Parallel-SEKAI/PJSK-Sticker：第三方表情包生成器，GPL-3.0。" +
                    "本 App 曾参考它的功能设计，后决定不做该功能，相关代码已删除，" +
                    "未使用它的代码与素材。",
                "Sonolus（sonolus.com）：第三方节奏游戏模拟器。" +
                    "本 App 只在首页「工具」里提供外链跳转。",
                "Sonolus 配套工具（tool.sonolus.reikohaku.fun）：第三方的服务器列表工具。" +
                    "本 App 只在首页「工具」里提供外链跳转。",
            )

            Section("开源许可")
            Body(
                "本 App 不包含、也不复制上述项目的任何源代码，所有实现均为独立编写。" +
                    "若将来加入组卡推荐、控分计算等算法功能，需要特别注意：" +
                    "Sekai Viewer 与 Moesekai 的算法实现受 GPL / AGPL 约束，不能直接搬用。",
            )

            Section("使用到的公开接口")
            BodyLines(
                "https://sekai-world.github.io/sekai-master-db-*-diff/*.json",
                "https://storage.sekai.best/sekai-jp-assets/",
                "https://api.github.com/repos/Sekai-World/sekai-master-db*-diff/commits",
            )

            Section("隐私")
            Body(
                "本 App 不收集、不上传任何个人信息，没有账号、统计与追踪。" +
                    "App 自身只请求上面列出的公开数据地址；" +
                    "首页与关于页里的第三方链接只有在你点击时才会唤起浏览器，App 不会主动访问它们。",
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 多行正文（一行一条）。
 *
 * ⚠️ **不加行首符号**（以前用 `·`，用户要求"不要有多余的符号"）：条目本身已经能读懂，
 * 靠换行分组就够了。
 */
@Composable
private fun BodyLines(vararg lines: String) {
    Text(
        text = lines.joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
