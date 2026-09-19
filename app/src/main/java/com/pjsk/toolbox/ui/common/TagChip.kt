package com.pjsk.toolbox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 小标签（角标）。
 *
 * 用途是「一眼看出这张卡 / 这个池子特殊在哪」：卡面左上角的 `限定`、卡池详情里的 `UP`、
 * `首发`，以及卡池列表行上的 `复刻` / `生日池`。三档配色按**信息的重要程度**分，
 * 不是按语义分（语义太细会变成一堆几乎相同的颜色，反而看不出差别）：
 *
 *  - [TagTone.HOT]：这个池子/这张卡的**卖点**（UP），用 tertiary，最跳；
 *  - [TagTone.ACCENT]：限定 —— 用户真正在意的分类，用 primary；
 *  - [TagTone.MUTED]：其余补充说明（首发、复刻、生日池…），用 secondaryContainer，
 *    能被看见但不抢戏。
 *
 * 角标压在卡面上，所以**必须是不透明底色**：用半透明的话浅色卡面上会糊成一团。
 */
enum class TagTone { HOT, ACCENT, MUTED }

@Composable
fun TagChip(
    text: String,
    tone: TagTone,
    modifier: Modifier = Modifier,
) {
    val (container, content) = when (tone) {
        TagTone.HOT -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
        TagTone.ACCENT -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        TagTone.MUTED -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(container)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = content,
        )
    }
}
