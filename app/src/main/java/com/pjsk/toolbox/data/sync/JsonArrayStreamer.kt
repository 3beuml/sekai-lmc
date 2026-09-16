package com.pjsk.toolbox.data.sync

import java.io.File
import java.io.Reader

/**
 * 流式遍历「JSON 顶层数组」的每个元素。
 *
 * 为什么需要它：`cards.json`(35MB) / `gachas.json`(46MB) / `costume3ds.json`(54MB)
 * 都不能用 `Json.parseToJsonElement` 一次性读入 —— 在手机上会直接 OOM。
 * 这里只用「括号深度 + 字符串状态」两个状态机变量切出每个元素的原始字符串，
 * 内存占用恒定（只与单个元素大小有关），元素解析交给上层逐个处理。
 */
object JsonArrayStreamer {

    /** 逐个回调顶层数组元素（返回元素的 JSON 文本）。调用方抛异常会中断整个遍历。 */
    fun forEachElement(file: File, onElement: (String) -> Unit) {
        require(file.isFile) { "不存在的数据文件: ${file.absolutePath}" }
        file.bufferedReader(Charsets.UTF_8).use { reader -> forEachElement(reader, onElement) }
    }

    /**
     * 从任意 [Reader] 读。
     *
     * 存在的理由：**内置数据快照放在 APK 的 assets 里**，只能拿到 InputStream，
     * 没有真实文件路径。没有这个重载的话，内置快照就得先落到临时文件再解析，
     * 白白多一次十几 MB 的磁盘写入。
     */
    fun forEachElement(reader: Reader, onElement: (String) -> Unit) {
        val buf = CharArray(64 * 1024)
        val sb = StringBuilder(8192)
        var started = false
        var depth = 0
        var inString = false
        var escaped = false

        while (true) {
            val n = reader.read(buf)
            if (n <= 0) break
            for (i in 0 until n) {
                val ch = buf[i]

                if (!started) {
                    if (ch == '[') started = true
                    continue
                }

                if (inString) {
                    sb.append(ch)
                    when {
                        escaped -> escaped = false
                        ch == '\\' -> escaped = true
                        ch == '"' -> inString = false
                    }
                    continue
                }

                if (depth == 0) {
                    when (ch) {
                        '"' -> { inString = true; sb.append(ch) }
                        '{', '[' -> { depth = 1; sb.append(ch) }
                        ']' -> return // 顶层数组结束
                        else -> Unit // 逗号与空白，忽略
                    }
                } else {
                    when (ch) {
                        '"' -> { inString = true; sb.append(ch) }
                        '{', '[' -> { depth++; sb.append(ch) }
                        '}', ']' -> {
                            depth--
                            sb.append(ch)
                            if (depth == 0) {
                                onElement(sb.toString())
                                sb.setLength(0)
                            }
                        }
                        else -> sb.append(ch)
                    }
                }
            }
        }
    }
}
