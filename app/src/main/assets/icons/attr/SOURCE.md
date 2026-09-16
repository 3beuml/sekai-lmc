# 属性图标的来源

这 5 个 WebP 是**游戏衍生美术**，取自开源仓库的本地静态文件：

```
https://raw.githubusercontent.com/StarMoe-org/Moesekai/main/web/public/data/icon/<File>.webp
```

仓库：`StarMoe-org/Moesekai`（AGPL-3.0）路径 `web/public/data/icon/`。
备用来源：`Sekai-World/sekai-viewer`（GPL-3.0）的 `src/assets/icon_attribute_<attr>.png`。

⚠️ 为什么不用官方素材桶：`sekai-jp-assets/common_icon/` 用 delimiter 列举是零个子目录，
唯一的 Key 只有 `common_icon_atlas.spriteatlas`（Unity 图集）；`thumbnail/common/` 目录不存在。
即**官方桶里没有可分发的单张属性图标**（已实测证伪）。

仓库里的原始文件名（大小写不统一，只有 cute 是小写）：
- cool ← Cool.webp
- cute ← cute.webp
- happy ← Happy.webp
- mysterious ← Mysterious.webp
- pure ← Pure.webp

本目录下统一存成小写属性名（`cool.webp` …），运行时代码只认小写。
