package com.pjsk.toolbox.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.profile.PlayerSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 当前绑定账号的本地快照；**没绑定 / 没有缓存 / 还在读** 一律返回 `null`。
 *
 * ## 为什么返回 null 而不是一个 sealed state
 *
 * 调用它的页面（卡牌详情、歌曲详情）对这三种情况的处理**完全一样**：
 * 什么也不显示。用户明确要求过 —— 没登录就不要出现与账号相关的内容，
 * 连"去绑定"的引导也不要在图鉴/详情页里冒出来（那是「我的」页的事）。
 * 所以这里只需要"有没有数据"这一个信号，多一层状态分支反而是噪音。
 *
 * ## 为什么在 IO 线程读
 *
 * 快照原文有几百 KB，`loadCached` 会解析它。放主线程会在打开详情页时卡一下，
 * 而这一屏的内容本来就要等内置数据解析完才画得出来，所以多等几十毫秒无感。
 *
 * ## 读的是哪个账号
 *
 * [PjskApp.container] 里的 `appSettings.playerAccount`。它变了（绑定 / 解绑 / 换号）
 * 会重新读一次 —— 所以解绑之后详情页里的"我的…"块会自动消失，不需要谁去通知它。
 */
@Composable
fun rememberPlayerSnapshot(): PlayerSnapshot? {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val account by container.appSettings.playerAccount.collectAsState()
    val snapshot by produceState<PlayerSnapshot?>(initialValue = null, account) {
        value = account?.let { acc ->
            withContext(Dispatchers.IO) { container.playerRepository.loadCached(acc) }
        }
    }
    return snapshot
}
