package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.profile.PlayerAccount
import com.pjsk.toolbox.data.profile.PlayerFetchResult
import com.pjsk.toolbox.data.profile.isValidGameId
import com.pjsk.toolbox.data.profile.normalizeGameId
import com.pjsk.toolbox.ui.common.SectionTitle
import com.pjsk.toolbox.ui.common.openExternalLink
import kotlinx.coroutines.launch

/**
 * 绑定游戏账号。
 *
 * 这里**不注册、不登录、不碰任何凭据** —— 只填一个游戏 UID，然后读一次公开只读接口。
 * 真正的"注册 Haruki 工具箱 + 上传数据"必须由用户自己在浏览器里完成，
 * 所以本页把两个外链放在最显眼的位置（没有那一步，这里填什么都查不到）。
 *
 * 目前只开放**日服**：另外两个区服（港服/国服）上传数据要抓包，
 * 而「继承码上传」只支持日服/国际服，所以选了也没法用（详见 PROJECT_MEMORY §14.6.3）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BindAccountScreen(
    onBack: () -> Unit,
    onBound: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PjskApp).container
    val repo = remember { container.playerRepository }
    val settings = remember { container.appSettings }
    val scope = rememberCoroutineScope()

    var uid by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("绑定游戏账号") },
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
            Text(
                text = "填你自己的游戏 UID，就能读到你自己上传到 Haruki 工具箱的数据。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "这个 App 不登录、不收密码、不存任何凭据；数据来源是公开只读接口，是快照不是实时。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── 前置条件：放在输入框之前，因为没做这两步填什么都没用 ──
            SectionTitle("先要做的两件事")
            Text(
                text = "① 在 Haruki 工具箱注册并绑定你的游戏账号（改游戏内签名来验证）。\n" +
                    "② 上传一次日服数据（日服用「继承码上传」，在网页里填引继 ID 与密码）。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { openExternalLink(context, TOOLBOX_URL) }) {
                    Text("去 Haruki 工具箱")
                }
                TextButton(onClick = { openExternalLink(context, GUIDE_URL) }) {
                    Text("上传教程")
                }
            }

            SectionTitle("游戏 UID")
            OutlinedTextField(
                value = uid,
                onValueChange = { uid = it; error = null },
                singleLine = true,
                label = { Text("UID") },
                placeholder = { Text("例如 123456789012345678") },
                supportingText = {
                    Text(
                        text = "游戏内：个人资料 → 基础个人资料 → 点击玩家 ID 复制。" +
                            "（不是引继码，引继码绝对不要填到这里，也不要发给任何人）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = "区服：日服（目前只支持日服）",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "港服 / 国服的数据要抓包上传，暂未开放。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            val id = normalizeGameId(uid)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !busy && isValidGameId(id),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val account = PlayerAccount(server = "jp", gameId = id)
                            when (val r = repo.fetch(account)) {
                                is PlayerFetchResult.Success -> {
                                    settings.setPlayerAccount(account)
                                    busy = false
                                    onBound()
                                }

                                is PlayerFetchResult.NotFound -> {
                                    busy = false
                                    error = NOT_FOUND_HINT
                                }

                                is PlayerFetchResult.Failed -> {
                                    busy = false
                                    error = r.message
                                }
                            }
                        }
                    },
                ) { Text(if (busy) "正在读取…" else "验证并绑定") }

                if (busy) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                }
            }

            if (uid.isNotBlank() && !isValidGameId(id)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "UID 应该是 1–20 位的纯数字（首位不是 0）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            error?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("关于隐私")
            Text(
                text = "绑定之后，你的这份数据对任何知道你 UID 的人都是可读的 —— 这是 Haruki 公开接口的设计，" +
                    "不是本 App 造成的。不想公开时，可以回到 Haruki 工具箱关掉「允许公开 API 访问」，" +
                    "但关掉之后这里也读不到（两者互斥）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val TOOLBOX_URL = "https://haruki.seiunx.com/"
private const val GUIDE_URL = "https://docs.shiromiku.moe/bot-help/toolbox_guide.html"

/**
 * 上游 404 的四种可能，必须一起列出来 ——
 * 那个接口对"不存在 / 没上传 / 没开公开 / 参数错"返回的是同一个 404，无法区分。
 */
private const val NOT_FOUND_HINT =
    "查不到你的数据。请逐条确认：\n" +
        "① UID 有没有填错（个人资料 → 基础个人资料里复制的那串）；\n" +
        "② 有没有在 Haruki 工具箱上传过**日服**数据；\n" +
        "③ 有没有打开「允许公开 API 访问」；\n" +
        "④ 数据是快照 —— 上传之后才有，不是实时的。"
