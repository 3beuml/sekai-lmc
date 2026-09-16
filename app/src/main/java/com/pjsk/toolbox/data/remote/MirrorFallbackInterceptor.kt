package com.pjsk.toolbox.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 镜像站兜底：卡面与音频默认走第三方镜像（[MIRROR_HOST]），**镜像不可用时自动改回官方 CDN 重试一次**。
 *
 * 为什么需要这一层：镜像站是社区自建的服务（见 [AssetUrls] 里的说明），路径与内容都跟官方一致，
 * 但是**随时可能挂掉、限流或变更**。用户在国内慢线路上完全依赖它，可一旦它出问题，
 * 卡面与播放就会整块不可用 —— 所以必须有一条自动退路。
 *
 * 规则很简单：
 *  - 请求的是**官方 host** → 原样放行，不做任何改写；
 *  - 请求的是**镜像 host** → 先试镜像；只有在「抛异常（超时/连接失败）」或
 *    「响应不是 2xx（含 404 —— 镜像可能确实缺这个文件，实测剧情 `.asset` 就是 404）」
 *    的情况下才换成官方 host 重试一次。
 *
 * 因为官方 host 的请求不会再被改写，所以最多重试一次，不存在打转。
 */
class MirrorFallbackInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != AssetUrls.MIRROR_HOST) return chain.proceed(request)

        val attempt = runCatching { chain.proceed(request) }
        val response = attempt.getOrNull()
        if (response != null && response.isSuccessful) return response

        // 失败：先把上一个响应体关掉再重试，否则会泄漏连接
        response?.close()

        val official = request.url.newBuilder().host(AssetUrls.OFFICIAL_HOST).build()
        return chain.proceed(request.newBuilder().url(official).build())
    }
}
