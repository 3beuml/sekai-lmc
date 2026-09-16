package com.pjsk.toolbox.data.player

import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pjsk.toolbox.PjskApp

/**
 * 播放服务：把 App 里**那一个** ExoPlayer 包成 `MediaSession`。
 *
 * 包装之后由系统负责三件事，我们一行通知代码都不用写：
 *  1. **通知栏**的播放卡片（`DefaultMediaNotificationProvider` 自己建渠道、自己画按钮）
 *  2. **锁屏**上的媒体控件
 *  3. **蓝牙耳机 / 车机**的上一首下一首、播放暂停
 *
 * ## 为什么服务里不新建播放器
 *
 * `ExoPlayer` 一个进程只该有一个实例：新建的话，"界面上暂停了、通知栏还在放"这种
 * 各说各话的问题立刻出现。所以这里直接用 [MusicPlayer.exoPlayer]（同进程共享）。
 *
 * ## 前台服务
 *
 * 播放中 [MediaSessionService] 会把服务提到前台（`foregroundServiceType="mediaPlayback"`），
 * 所以切到别的 App、锁屏都不会被杀 —— 这就是"后台播放"的来源。
 * 判断依据是 `playWhenReady`，因此 [MusicPlayer.playQueue] 必须先 `play()` 再起服务。
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // 通知栏小图标：默认是 media3 自带的图标，这里换成我们自己的音符，
        // 免得通知栏出现一个和 App 无关的图标。系统会把它染成单色。
        //
        // ⚠️ 这里**按名字取资源**而不是写 `R.drawable.xxx`：`tools/compile-check.ps1`
        // 是纯 kotlinc 编译（不跑 AGP），拿不到 AGP 生成的 R 类，写 R 会直接编译不过。
        // 取不到时（0）就退回 media3 的默认图标，不影响功能。
        val icon = resources.getIdentifier("ic_notification_music", "drawable", packageName)
        val provider = DefaultMediaNotificationProvider.Builder(this).build()
        // ⚠️ 注意 setSmallIcon 在 **provider** 上，不在 Builder 上（Builder 只有渠道/通知 id）
        if (icon != 0) provider.setSmallIcon(icon)
        setMediaNotificationProvider(provider)

        session = MediaSession.Builder(this, appContainer().musicPlayer.exoPlayer).build()
        // ⚠️ **必须 addSession**，不能只靠 onGetSession：后者只在"有控制器连进来"时才被调用，
        // 而我们的界面是直接操作 ExoPlayer 的、没有任何 MediaController —— 不 add 的话
        // 服务不知道有这个 session，通知栏永远不出现。
        // （addSession 本身是幂等的：同一个实例重复加只是跳过，不会抛"Session ID should be unique"。）
        session?.let { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * 从"最近任务"里划掉 App。
     *
     * 选择**停止播放**而不是继续：这是试听工具，不是音乐播放器，
     * 划掉之后还在响、又找不到入口去停，比断掉更烦人。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun appContainer() = (application as PjskApp).container
}
