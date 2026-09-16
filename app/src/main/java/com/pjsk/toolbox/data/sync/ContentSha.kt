package com.pjsk.toolbox.data.sync

import java.io.File
import java.security.MessageDigest

/**
 * 计算文件的 **git blob sha**：`sha1("blob <字节数>\0" + 内容)`。
 *
 * 与仓库里 `git hash-object` 的结果同算法，所以能直接和 GitHub 的 trees API 给的 sha 比对 —— 这是
 * 「本地这份内容到底是不是上游那份」的唯一权威判据（体积、ETag、时间戳都靠不住，见 [SyncDecision]）。
 *
 * 两处用它：
 *  - App 下载完校验（`MasterRepository.downloadTable`）—— 对不上就绝不覆盖本地数据；
 *  - 打包时校验（`tools/datapack/BuildDataPack.kt`）—— 源数据与记录的 sha 对不上就**拒绝打包**，
 *    否则快照会"谎报"自己是最新的，App 之后就再也不会去更新那几张表。
 *
 * ⚠️ 长度用的是**字节数**：这个数据集里全是日文，按字符数算会全错。
 */
object ContentSha {

    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${file.length()}\u0000".toByteArray(Charsets.UTF_8))
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
