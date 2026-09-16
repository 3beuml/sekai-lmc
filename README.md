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
| **Android APK** | 见 [Releases](https://github.com/3beuml/sekai-lmc/releases)（当前 `0.1.0`，约 23 MB，需要 **Android 8.0** 及以上） |
| **源代码** | 本仓库；每个 Release 页面也会自动附 `Source code (zip / tar.gz)` |

安装：侧载 APK，首次需要在系统里允许「安装未知来源应用」。

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
素材 CDN      https://storage.sekai.best/sekai-jp-assets/...                      （图片 / 音频）
版本探测      https://api.github.com/repos/Sekai-World/sekai-master-db-diff/commits
歌词          Sekaipedia（CC BY-SA 4.0，界面内标注来源）
```

以**日服**数据为主结构与进度，**简中服**只用来补中文名（按 id 关联，缺了就回退显示日文原名）。
构建时把数据裁剪后打进 APK（66 张表 / 约 12 MB），首次启动后台导入；之后只做增量更新
（版本没变则零下载，命中 304 的表不重复下）。这些都是社区维护的公开仓库与公开 CDN，不是官方接口。

## 从源码构建

需要 JDK 17 或 21、Android SDK（`platforms;android-36` + `build-tools;36.0.0`）。

```bash
./gradlew :app:assembleDebug     # 产物：app/build/outputs/apk/debug/app-debug.apk
```

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
