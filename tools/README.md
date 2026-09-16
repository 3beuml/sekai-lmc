# tools/

开发期用的脚本，**不参与 APK 构建**，只是让「数据契约」和「界面逻辑」这两块看不见的地方
变得可验证。需要 Node.js（`check-dao-sql.mjs` 要 22.5+）与 JDK 17+ / Android SDK，部分脚本需要联网。

```
tools/
├─ compile-check.ps1     仅用 kotlinc 编一遍 app 源文件（约 40 秒），不跑 Gradle
├─ logic-check.ps1       用真实数据夹具跑纯逻辑断言（筛选 / 排序 / 状态编解码 / 播放队列）
├─ verify.ps1            联网校验数据契约：表是否还在、体积是否异常、素材路径是否失效
├─ regen-catalog.ps1     按远端真实体积重写 MasterCatalog.kt 的表清单
├─ check-keys.mjs        检查「没有 id 字段」的表该拿哪个字段当主键（重复值会静默覆盖）
├─ check-dao-sql.mjs     用 node:sqlite 逐条跑 MasterDao 的 @Query，免一次完整构建
├─ analyze-cards.mjs     抓 cards.json 前几 MB 做字段统计
├─ audit-tables.mjs      逐表审计 schema 假设（主键 / 显示名字段是否真的存在）
├─ datapack/             内置数据快照的生成流水线（见下）
├─ logic-check/          逻辑自检的 Kotlin 源码与数据夹具
├─ probes/               一次性网络探测脚本（查清某个资源路径/字段到底长什么样）
└─ .cache/               脚本产物（已 gitignore，跑之前不必手动建）
```

## datapack/ —— 内置数据快照

App 首次启动要能离线导入，靠的是 `app/src/main/assets/datapack/` 里的裁剪快照（66 张表 / 3.4 万行 / 约 12 MB）。
重新生成（游戏大版本更新后）：

```powershell
node tools/datapack/fetch-raw.mjs character music event sticker card gacha
node tools/datapack/fetch-cn-overlay.mjs character music event sticker card gacha
powershell -File tools/datapack/build.ps1
```

`raw/`（日服源数据，约 93 MB）与 `cn/`（简中服，用于中文名叠加）都在 gitignore 里，
随时可以重新下载；**打包好的快照本身要提交**，这样新克隆的人不下载源数据也能直接构建。

## probes/ —— 一次性探测

这些脚本是为回答某个具体问题写的（「这张图的 CDN 路径到底带不带版本戳」「表情包底图从哪来」），
结论已经写进代码注释与本地开发笔记，脚本留下来是为了以后能**重新跑一遍验证**，而不是给人当工具用。
产物同样落在 `tools/.cache/`。
