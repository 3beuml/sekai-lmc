package com.pjsk.toolbox.data.sync

/**
 * 「这张表要不要下载」的判定。
 *
 * 单独抽出来是为了能在 `tools/logic-check/LogicCheck.kt` 里直接断言 —— 这段判断一旦写反，
 * 后果是**静默的**：要么每次同步都把上百 MB 重下一遍，要么内容变了却永远不更新。
 *
 * 判据是**内容 sha**（本地记的 sha vs 仓库文件列表里的 sha），不是版本号、也不是 ETag：
 *  - 版本号只在游戏大版本时跳，粒度太粗；
 *  - ETag / Last-Modified 来自 GitHub Pages，前面有 Fastly 缓存，我们实测撞过"部署后 6.5 小时
 *    仍拿到旧副本"的情况，而旧副本的体积只差 0.4%，靠大小/条件请求都发现不了。
 *  - sha 是内容哈希，差一个字节就对不上；而且它来自 git trees API（仓库直读、不过缓存）。
 */
object SyncDecision {

    enum class Action {
        /** 本地 sha 与远端一致 → 整张表跳过，**一个请求都不发**。 */
        SKIP_UNCHANGED,

        /** 远端文件列表里没有这张表（被删或改名）→ 不动本地已有数据。 */
        SKIP_MISSING_REMOTE,

        /** 需要下载：首次同步、内容变了、或用户要求强制核对。 */
        DOWNLOAD,
    }

    /**
     * @param localSha 本地记录的"当前内容对应的上游 sha"；旧版本升上来的库可能为 null。
     * @param remoteSha 仓库文件列表里这张表的 sha；为 null 表示远端已经没有这张表。
     * @param force 用户点了「强制核对」时为 true：即使 sha 一致也重新下一遍。
     */
    fun decide(localSha: String?, remoteSha: String?, force: Boolean): Action = when {
        remoteSha == null -> Action.SKIP_MISSING_REMOTE
        force -> Action.DOWNLOAD
        localSha != null && localSha == remoteSha -> Action.SKIP_UNCHANGED
        else -> Action.DOWNLOAD
    }
}
