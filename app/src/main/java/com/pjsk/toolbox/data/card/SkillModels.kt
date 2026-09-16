package com.pjsk.toolbox.data.card

import com.pjsk.toolbox.data.db.MasterRowEntity
import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.jsonObject
import com.pjsk.toolbox.ui.common.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** 技能在某个等级下的效果数值。 */
data class SkillLevelValue(
    val level: Int,
    val durationSeconds: Double?,
    val value: Double?,
)

/**
 * 一个技能效果槽（`skillEffects` 里的一项），内部按等级给出数值。
 *
 * ⚠️ [id] 是关键：技能描述里的占位符 `{{16;v}}` 中的 **16 是「这个效果的 id」，
 * 不是它在数组里的下标**。实测 skill id=10 的描述是
 * `ライフが{{16;v}}回復し、{{13;d}}秒間  スコアが{{13;v}}%UPする`，
 * 而它的 `skillEffects` 是 `[id=13 加成分, id=16 回血分]` ——
 * 若按下标取（`effects[15]`）永远取不到，占位符就全部解析失败。
 * （sekai.best / pjsk.moe 的做法也是按 effect.id 查。）
 */
data class SkillEffect(val id: Int, val levels: List<SkillLevelValue>) {
    /** [level] 级时的数值；等级超出范围时退到最后一档。 */
    fun at(level: Int): SkillLevelValue? =
        levels.firstOrNull { it.level == level } ?: levels.lastOrNull()
}

/**
 * 技能。
 *
 * 实测 `skills.json` 的结构：
 * ```json
 * {
 *   "id": 1,
 *   "shortDescription": "スコア{{1;v}}%ＵＰ",
 *   "description": "{{1;d}}秒間  スコアが{{1;v}}%UPする",
 *   "skillEffects": [
 *     { "skillEffectDetails": [
 *         {"level":1,"activateEffectDuration":5.0,"activateEffectValue":20},
 *         {"level":2,"activateEffectDuration":5.0,"activateEffectValue":25} ] } ]
 * }
 * ```
 * 描述文本里的 `{{n;d}}` / `{{n;v}}` 是占位符：`n` 是第几个 `skillEffects`（从 1 起数），
 * `d` 取 `activateEffectDuration`（持续秒数）、`v` 取 `activateEffectValue`（数值）。
 */
data class SkillInfo(
    val id: Int,
    val shortDescription: String?,
    val description: String?,
    /**
     * 简中服的同一段描述模板。
     *
     * 来源：`skills.json` 在目录里标了 `cnOverlay = true`，叠加层按 id 把简中服该行的
     * 描述写进了 `nameZh` 列（简中表的 `description` 字段就是中文）。
     * 日文原文仍在 [description] 里，两者都保留，UI 按语言偏好挑。
     */
    val descriptionZh: String?,
    val effects: List<SkillEffect>,
) {
    /** 效果描述里出现的最大技能等级。 */
    val maxLevel: Int get() = effects.flatMap { it.levels }.maxOfOrNull { it.level } ?: 1

    val hasChinese: Boolean get() = !descriptionZh.isNullOrBlank()

    /**
     * 把占位符替换成 [level] 级时的实际数值。
     *
     * 两类占位符：
     *  - `{{n;d}}` / `{{n;v}}`：`n` 是 **effect 的 id**（`skillEffects[].id`，**不是数组下标**！
     *    实测 id 从 0 或 53 这类值起步，按下标查会全部错位），`d` 取持续秒数、`v` 取数值。
     *    这两类能算，直接替换成数字。
     *  - `{{0;c}}`：`c` = 角色名，前面的数字是**占位用的 0，不需要查表**，
     *    直接填当前卡牌的角色名即可（所以 `c` 分支必须排在 id 查找之前）。
     *  - `{{a,b;r|s|u|o}}` 这类：**取值不在 `skills.json` 里**
     *    （依赖角色等级 / 组合人数 / 其他成员技能的加成上限），本工具算不出来。
     *
     * ⚠️ 算不出来的占位符**绝不能原样显示**。之前的实现是「替换不了就保留原文」，
     * 结果界面上直接出现 `{{53;d}}`、`{{54,103;r}}` 这种生文本 —— 看起来就是坏了。
     * 现在统一替换成 `※`，并在末尾加一句说明，把「不知道」讲清楚而不是甩一堆大括号。
     *
     * @param preferChinese 优先用简中服模板（缺失时自动回退日文原文）。
     */
    fun describe(
        level: Int,
        preferChinese: Boolean = true,
        characterName: String? = null,
    ): String? {
        val primary = if (preferChinese) descriptionZh ?: description else description
        val template = primary ?: descriptionZh ?: shortDescription ?: return null

        var hasUnresolved = false
        // 第一遍：能算的算出来
        val partially = SIMPLE_PLACEHOLDER.replace(template) { match ->
            val effectId = match.groupValues[1].toIntOrNull()
            val kind = match.groupValues[2]
            // ⚠️ 按 effect 的 **id** 查，不是按下标查（见 SkillEffect 的说明）
            val levelValue = effectId?.let { id -> effects.firstOrNull { it.id == id }?.at(level) }
            when (kind) {
                // c = 角色名（与效果无关，直接替换）
                "c" -> characterName?.takeIf { it.isNotBlank() } ?: run {
                    hasUnresolved = true
                    UNKNOWN_MARK
                }
                "d" -> levelValue?.durationSeconds?.let(::formatSkillNumber) ?: run {
                    hasUnresolved = true
                    UNKNOWN_MARK
                }
                "v" -> levelValue?.value?.let(::formatSkillNumber) ?: run {
                    hasUnresolved = true
                    UNKNOWN_MARK
                }
                // e / m / 其它：取值依赖 `skillEnhance` 等本表没有的字段，算不了
                else -> run {
                    hasUnresolved = true
                    UNKNOWN_MARK
                }
            }
        }
        // 第二遍：把剩下的（复合形式、下标越界等）也收干净，绝不留 {{…}}
        val cleaned = ANY_PLACEHOLDER.replace(partially) {
            hasUnresolved = true
            UNKNOWN_MARK
        }
        if (!hasUnresolved) return cleaned
        return "$cleaned\n（$UNKNOWN_MARK 处为条件数值：随角色等级 / 组合人数 / 其他成员技能加成变化，本工具暂不计算）"
    }

    private companion object {
        /** `{{1;d}}` 这种能算的。 */
        val SIMPLE_PLACEHOLDER = Regex("""\{\{(\d+);(\w)\}\}""")

        /** 兜底：任何剩下的 `{{…}}` 都不许出现在界面上。 */
        val ANY_PLACEHOLDER = Regex("""\{\{[^}]*\}\}""")

        const val UNKNOWN_MARK = "※"
    }
}

/** 5.0 输出成 5，30.5 保持 30.5。 */
private fun formatSkillNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

fun parseSkill(row: MasterRowEntity): SkillInfo? {
    val obj = row.jsonObject() ?: return null
    val effects = (obj["skillEffects"] as? JsonArray).orEmpty().mapNotNull { element ->
        val effect = element as? JsonObject ?: return@mapNotNull null
        // effect 自己的 id 必须留着：描述里的占位符是按它来查的
        val effectId = effect.intOrNull("id") ?: return@mapNotNull null
        val levels = (effect["skillEffectDetails"] as? JsonArray).orEmpty().mapNotNull { detailElement ->
            val detail = detailElement as? JsonObject ?: return@mapNotNull null
            val level = detail.intOrNull("level") ?: return@mapNotNull null
            SkillLevelValue(
                level = level,
                durationSeconds = detail.str("activateEffectDuration")?.toDoubleOrNull(),
                value = detail.str("activateEffectValue")?.toDoubleOrNull(),
            )
        }
        SkillEffect(effectId, levels)
    }
    return SkillInfo(
        id = row.id,
        shortDescription = obj.str("shortDescription")?.takeIf { it.isNotBlank() },
        description = obj.str("description")?.takeIf { it.isNotBlank() },
        descriptionZh = row.nameZh?.takeIf { it.isNotBlank() },
        effects = effects,
    )
}

