# sekai lmc

一个非官方的 Project Sekai 公开数据下载与预览工具（Android / Kotlin + Jetpack Compose）。

把公开的游戏数据做成一个装进手机就能用、**离线也能翻**的客户端：数据随安装包内置，
图片与音频在线获取。

> ⚠️ **注意**
>
> 本项目是个人兴趣作品，代码在 AI 辅助下编写，难免有错误或不够好的写法，请自行评估后再使用。

## 下载

| | |
| --- | --- |
| **Android APK** | 见 [Releases](https://github.com/3beuml/sekai-lmc/releases)（当前 `0.1.3`，约 16 MB，需要 **Android 8.0** 及以上），下载 `app-release.apk` 侧载 |
| **源代码** | 本仓库；每个 Release 页面也会自动附 `Source code (zip / tar.gz)` |

安装：侧载 APK，首次需要在系统里允许「安装未知来源应用」。

安装包用固定密钥签名，证书 SHA-256 指纹：

```
AA:49:A7:B9:BC:D9:CE:75:18:56:B3:DC:EC:7C:0C:2C:DD:F3:F5:05:9F:22:B7:9F:10:DD:DB:C8:77:9A:1D:92
```

想核对下载到的文件，可以 `apksigner verify --print-certs app-release.apk`，或者直接看
系统「应用信息 → 应用详情」里的签名摘要。指纹一致就说明是本仓库发布的包。

## 功能

底部五个板块：

- **首页**：最新卡牌、当前卡池、最新歌曲、最新活动、近期角色生日，以及工具与参考项目入口
- **卡牌**：三态卡面图鉴，按属性 / 稀有度 / 组合 / 角色筛选与排序；详情页有**能力值完整公式**
  （等级与突破滑杆实时计算）、技能、卡牌剧情、卡面原图保存、抽卡语音试听与下载
- **歌曲**：717 首，四个 Tab（歌曲 / 分类 / 人物 / 收藏）；搜索与筛选；歌词带日文注音与简中对照；
  试听与播放会跳过每首歌开头那段空白，支持队列、三种播放模式、通知栏与锁屏控制、定时器
- **剧情**：主线剧情与活动剧情（214 个活动，可按「和哪个团相关」筛选）；阅读器逐句显示，
  带出场角色头像与注音
- **更多**：外观与名称语言、数据同步（按模块下载并显示体积）、全部数据表浏览、关于与合规说明

## 数据来源

```
master data   https://sekai-world.github.io/sekai-master-db-diff/<file>.json      （日服）
              https://sekai-world.github.io/sekai-master-db-cn-diff/<file>.json   （简中名叠加层）
素材 CDN      https://storage.exmeaning.com/sekai-jp-assets/...                   （图片 / 音频，第三方镜像）
              官方为 https://storage.sekai.best/...（镜像不可用时自动回退到这里）
版本探测      https://api.github.com/repos/Sekai-World/sekai-master-db-diff/commits
歌词          Sekaipedia（CC BY-SA 4.0，界面内标注来源）
```

以**日服**数据为主结构与进度，**简中服**只用来补中文名（按 id 关联，缺了就回退显示日文原名）。
构建时把数据裁剪后打进 APK（66 张表 / 约 12 MB），**首次启动在后台离线导入，不联网下载任何东西**；
之后只做增量更新。这些都是社区维护的公开仓库与公开 CDN，不是官方接口。

**更新怎么判定（这一块的设计值得说明）**：不用版本号、也不用 ETag —— 两者都会骗人。
版本号只在游戏大版本时跳（粒度太粗），而 ETag / `Last-Modified` 来自 GitHub Pages，
前面有 Fastly 缓存：我们实测撞过一次「版本提交之后 6.5 小时抓取，拿到的仍是部署前的旧副本」，
而那份旧副本的体积只差 0.4%，靠条件请求或比大小都发现不了。

现在改成**按内容 sha 判定**：

1. 每次同步先取一次仓库文件列表（git trees API，**1 个请求、不过 CDN 缓存**）→ 每张表的权威 sha；
2. 与本地记的 sha 比：一致的整张表跳过，**一个请求都不发**；
3. 不一致的才下载（URL 带 `?cb=<时间戳>` 绕过 CDN 缓存），**写盘前算一遍 sha 校验**，
   对不上就换一次请求重试；仍对不上就**不导入、保留本地旧数据**并报错；
4. **只有一次都没失败**才记录「已同步到 XX 版本」——否则下次启动会继续补，不会卡在旧数据上。

内置快照的 `manifest.json` 里也带着每张表的 sha，所以**全新安装后的首次同步就能判定为「无更新」**，
同样是一个字节都不用下。这套机制在 `tools/logic-check` 里有断言钉着（含"快照记录的 sha
必须与源数据算出来的完全一致"这条端到端校验）。

### ⚠️ 关于素材镜像

卡面与音频默认走**第三方社区镜像** `storage.exmeaning.com`，因为直连官方 CDN 在国内基本不可用
（2026-09 实测同一文件：官方 5–40 KB/s、首字节 1.2–7.5 秒；镜像 582–1753 KB/s、首字节 21–220 毫秒）。
镜像与官方**路径完全一致**，实测 jp / cn / en / kr 四个区服桶都在。

- 镜像**没有剧情 `.asset`**（实测 404），所以剧情、贴纸、抽卡语音、背景等仍走官方；
- 镜像是别人的服务，**随时可能挂掉** → App 里有一层兜底：镜像请求失败（超时 / 5xx / 404）时
  自动改回官方 CDN 重试一次，所以不会出现「镜像挂了整个功能不可用」；
- 如果不希望使用第三方镜像，可以在 `data/remote/AssetUrls.kt` 里把 `mirrorAssetBase()` 换成
  `region.assetBase`、把 `AUDIO_BASE_MIRROR` 换成官方地址后自行构建。

## 从源码构建

需要 JDK 17 或 21、Android SDK（`platforms;android-36` + `build-tools;36.0.0`）。

```bash
./gradlew :app:assembleDebug     # 产物：app/build/outputs/apk/debug/app-debug.apk
```

如果需要 release 包：`./gradlew :app:assembleRelease`。仓库里**不含任何密钥** ——
正式签名读根目录的 `keystore.properties`（已 gitignore，写明 keystore 路径与密码），
没有这个文件时会自动退回调试签名，所以别人 clone 下来也能正常构建。
注意**调试签名与 Release 里的正式签名互不兼容**：装了正式签名的包以后，再装自己构建的调试包
会报签名不一致，得先卸载（数据也会一起清掉）。

仓库里**已经包含打包好的数据快照**，所以不下载任何原始数据也能构建出可用安装包。
游戏大更新后要重建快照（原始数据不进仓库）：先 `tools/datapack/fetch-raw.mjs` 拉原始数据，
再 `fetch-cn-overlay.mjs` 拉简中叠加层，最后 `build.ps1` 裁剪打包 —— 三个脚本头部都有用法说明。

改完代码建议跑一遍离线自检：`tools/compile-check.ps1`（纯 kotlinc 编译，约 40 秒）与
`tools/logic-check.ps1`（446 项逻辑断言，用真实数据做夹具）。

## 许可

代码采用 [MIT](LICENSE)。

⚠️ 边界要说清：**MIT 只覆盖本仓库中的代码。**

- 游戏素材（图片、音频、文本）版权归 **SEGA / Colorful Palette**，App 只在线引用，
  不随本仓库分发。
- 内置的数据快照来自社区仓库 `Sekai-World/sekai-master-db-diff`，那些仓库**本身没有
  LICENSE 文件**，严格来说数据未被明确授予再分发许可——自用风险低，再分发请自行评估。
- 歌词来自 Sekaipedia，带独立授权（含 **CC BY-NC-SA** 条目），不可用于商业用途。

## 参考与致谢

| 项目 | 许可 | 本项目怎么用它 |
| --- | --- | --- |
| [Sekai-World/sekai-viewer](https://github.com/Sekai-World/sekai-viewer)（sekai.best） | GPL-3.0 | 数据格式与界面交互的参考 |
| [StarMoe-org/Moesekai](https://github.com/StarMoe-org/Moesekai)（pjsk.moe） | AGPL-3.0 | 同上 |
| [Sekai-World/sekai-master-db-diff](https://github.com/Sekai-World/sekai-master-db-diff) | 无 LICENSE | 数据来源 |
| `storage.exmeaning.com`（exmeaning 社区镜像） | — | 卡面与音频的加速线路（第三方服务，详见上文「关于素材镜像」） |
| [TheOriginalAyaka/sekai-stickers](https://github.com/TheOriginalAyaka/sekai-stickers)（st.ayaka.one） | MIT | 只在首页提供外链跳转，未使用其代码与素材 |
| [Parallel-SEKAI/PJSK-Sticker](https://github.com/Parallel-SEKAI/PJSK-Sticker) | GPL-3.0 | 曾参考其功能设计，后决定不做该功能，相关代码已删除 |
| [Sonolus](https://sonolus.com/) | — | 只在首页提供外链 |

以上项目均为第三方作品，与本项目无隶属关系，**本仓库不包含它们的源代码**。

应用图标由 **[@kwiozsn](https://x.com/kwiozsn)** 绘制（如原作者有异议会立即更换）。
其余开源组件：Jetpack Compose、Room、Media3 / ExoPlayer、Coil、OkHttp、kotlinx.serialization。

## 免责声明

本 App 是**非官方粉丝作品**，与 SEGA、Colorful Palette 均无关联，未获得其授权、赞助或认可。
游戏素材版权归 SEGA / Colorful Palette 所有，本 App 仅在线引用用于资料浏览，不主张任何权利。

本 App **不收集、不上传任何个人信息**：没有账号、没有统计、没有追踪。
