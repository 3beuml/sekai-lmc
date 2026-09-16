package com.pjsk.toolbox.util

import androidx.compose.ui.graphics.Color

/**
 * 把 `#rrggbb` / `rrggbb` 解析成 [Color]。
 *
 * 数据来源是 `unitProfiles.json` 的 `colorCode`（实测形如 `#4455dd`、`#ffffff`）。
 * **解析失败返回 null，绝不抛异常** —— 官方哪天换了写法（比如加 alpha 位），
 * 表现应该只是"这一处没上色"，而不是整个页面崩掉。
 *
 * 放在 `util` 而不是某个页面里，是因为自检（`tools/logic-check.ps1`）要用：
 * 它把 app 源码当成**另一个编译单元**，`internal` 是访问不到的。
 */
fun parseHexColor(hex: String?): Color? {
    val text = hex?.trim()?.removePrefix("#") ?: return null
    if (text.length != 6) return null
    val value = text.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)
}
