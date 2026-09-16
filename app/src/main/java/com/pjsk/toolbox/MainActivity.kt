package com.pjsk.toolbox

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.pjsk.toolbox.ui.AppNav
import com.pjsk.toolbox.ui.theme.SekaiLmcTheme
import com.pjsk.toolbox.ui.theme.WindowBackgroundDark
import com.pjsk.toolbox.ui.theme.WindowBackgroundLight
import com.pjsk.toolbox.ui.theme.resolveDark

class MainActivity : ComponentActivity() {

    /** 通知权限（Android 13+）只问一次，用户拒绝就不再弹。 */
    private var askedForNotifications = false

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PjskApp).container
        setContent {
            // 外观模式存在偏好里，改设置后整个界面立刻跟着切
            val themeMode by container.appSettings.themeMode.collectAsState()
            val dark = themeMode.resolveDark()

            // 让窗口底色跟当前主题一致。values-night 只跟随系统夜间模式，
            // 管不到 App 内手动切的主题，所以这里再同步一次，避免启动/切换时闪白。
            LaunchedEffect(dark) {
                window.setBackgroundDrawable(
                    ColorDrawable((if (dark) WindowBackgroundDark else WindowBackgroundLight).toArgb()),
                )
            }

            /*
             * 通知权限**等用户真的开始播了再问**（而不是一进 App 就弹）。
             *
             * 为什么：这个权限的唯一用途是"播放时显示通知栏控件"，一进 App 就问
             * 用户不知道要它干嘛，很容易顺手拒绝；而拒绝之后通知栏就不会出现，
             * 用户反而会以为是"后台播放坏了"。在按下第一个 ▶ 的瞬间问，意图才对得上。
             *
             * 拒绝也不影响播放，只是没有通知栏控件（锁屏/蓝牙控制仍由 MediaSession 提供）。
             */
            val playback by container.musicPlayer.state.collectAsState()
            LaunchedEffect(playback.track?.musicId) {
                if (playback.track == null || askedForNotifications) return@LaunchedEffect
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) return@LaunchedEffect
                askedForNotifications = true
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            SekaiLmcTheme(themeMode = themeMode) {
                AppNav()
            }
        }
    }
}
