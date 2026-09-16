/**
 * 下载 5 个属性图标，内置进 APK（`app/src/main/assets/icons/attr/`）。
 *
 * 为什么内置：总共才 16.6 KB，内置之后运行时零依赖、离线可用、也不用担心中间层挂掉。
 *
 * 素材来源（实测确认，见下）：官方素材桶**拿不到**单张属性图标 ——
 * `sekai-jp-assets/common_icon/` 用 delimiter 列举是**零个子目录**，
 * 唯一的 Key 只有 `common_icon/common_icon_atlas.spriteatlas`（Unity 图集，不能直接用）；
 * `thumbnail/common/` 这个目录**根本不存在**。所以只能从开源仓库的本地静态文件取。
 *
 * ⚠️ 文件名大小写**不统一**：只有 cute 是小写，其余首字母大写。
 *    这是实测踩出来的坑，别顺手全小写化。
 *
 * ⚠️ 许可提醒：这两张图是**游戏衍生美术**。仓库（Moesekai AGPL-3.0 /
 *    sekai-viewer GPL-3.0）只是转发者，图的版权本来就不属于它们。
 *    本项目把它们当作「游戏素材」使用（与卡面、曲绘一致），并在 README 里注明来源。
 *
 * 用法：node tools/datapack/fetch-attr-icons.mjs
 */
import { mkdirSync, writeFileSync } from 'node:fs';

const BASE = 'https://raw.githubusercontent.com/StarMoe-org/Moesekai/main/web/public/data/icon/';
/**
 * 依次尝试的镜像。
 *
 * 为什么要多源：raw.githubusercontent 在连续请求后会**瞬时限流**
 * （实测：第一轮 1/5 成功，紧接着整批 fetch failed，连刚成功的那张也失败）。
 * jsDelivr 是本轮一直被验证可用的镜像，放第二顺位兜底。
 */
const BASES = [
  BASE,
  'https://cdn.jsdelivr.net/gh/StarMoe-org/Moesekai@main/web/public/data/icon/',
];
/** 属性 key → 仓库里的实际文件名（大小写按实测，不要改） */
const FILES = {
  cool: 'Cool.webp',
  cute: 'cute.webp',
  happy: 'Happy.webp',
  mysterious: 'Mysterious.webp',
  pure: 'Pure.webp',
};

const outDir = new URL('../../app/src/main/assets/icons/attr/', import.meta.url);
mkdirSync(outDir, { recursive: true });

const lines = [];
let ok = 0;
let failed = 0;

for (const [attr, fileName] of Object.entries(FILES)) {
  let done = false;
  let lastError = '';
  for (const base of BASES) {
    if (done) break;
    const url = base + fileName;
    // 每个镜像重试两次：限流往往是短暂的
    for (let attempt = 1; attempt <= 2 && !done; attempt++) {
      try {
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const bytes = Buffer.from(await res.arrayBuffer());
        // WebP 魔数校验：RIFF....WEBP
        const isWebp =
          bytes.length > 12 &&
          bytes.toString('ascii', 0, 4) === 'RIFF' &&
          bytes.toString('ascii', 8, 12) === 'WEBP';
        if (!isWebp) throw new Error('不是 WebP 文件（魔数不对）');
        // 落盘时统一改成小写属性名，运行时代码就不用记仓库里的大小写
        writeFileSync(new URL(`${attr}.webp`, outDir), bytes);
        ok++;
        done = true;
        const via = base === BASE ? 'raw' : 'jsDelivr';
        lines.push(
          `  ${attr.padEnd(11)} ← ${fileName.padEnd(16)} ${String(bytes.length).padStart(6)} B  ✅ (${via})`,
        );
      } catch (e) {
        lastError = String(e).slice(0, 60);
      }
    }
  }
  if (!done) {
    failed++;
    lines.push(`  ${attr.padEnd(11)} ← ${fileName.padEnd(16)} 失败: ${lastError}`);
  }
}

// 把来源与许可写进旁边的说明文件，避免以后没人知道这些图哪来的
writeFileSync(
  new URL('SOURCE.md', outDir),
  [
    '# 属性图标的来源',
    '',
    '这 5 个 WebP 是**游戏衍生美术**，取自开源仓库的本地静态文件：',
    '',
    '```',
    BASE + '<File>.webp',
    '```',
    '',
    '仓库：`StarMoe-org/Moesekai`（AGPL-3.0）路径 `web/public/data/icon/`。',
    '备用来源：`Sekai-World/sekai-viewer`（GPL-3.0）的 `src/assets/icon_attribute_<attr>.png`。',
    '',
    '⚠️ 为什么不用官方素材桶：`sekai-jp-assets/common_icon/` 用 delimiter 列举是零个子目录，',
    '唯一的 Key 只有 `common_icon_atlas.spriteatlas`（Unity 图集）；`thumbnail/common/` 目录不存在。',
    '即**官方桶里没有可分发的单张属性图标**（已实测证伪）。',
    '',
    '仓库里的原始文件名（大小写不统一，只有 cute 是小写）：',
    Object.entries(FILES).map(([a, f]) => `- ${a} ← ${f}`).join('\n'),
    '',
    '本目录下统一存成小写属性名（`cool.webp` …），运行时代码只认小写。',
    '',
  ].join('\n'),
  'utf8',
);

console.log(lines.join('\n'));
console.log(`\n完成：成功 ${ok} 个 / 失败 ${failed} 个`);
console.log(`-> ${outDir.pathname}`);
if (failed > 0) process.exitCode = 1;
