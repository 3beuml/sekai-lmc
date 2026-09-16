package com.pjsk.toolbox.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 用系统浏览器打开一个外部链接。
 *
 * 抽出来的原因：首页的友情链接、歌曲详情页的「原曲」要做同一件事。
 * 之前这段 `Intent(ACTION_VIEW)` 是内联写在首页里的，加第二个入口就会出现两份 ——
 * 而这类代码一旦分叉，通常是「其中一处加了容错、另一处没加」。
 *
 * **不抛异常**：设备上没有浏览器时会抛 `ActivityNotFoundException`，
 * 这里统一吞掉并返回 false（调用方只关心「有没有唤起」）。
 *
 * @return 是否成功唤起（false 不代表数据有问题，只代表这台机器打不开）
 */
fun openExternalLink(context: Context, url: String?): Boolean {
    val target = url?.trim().orEmpty()
    if (target.isEmpty()) return false
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}
