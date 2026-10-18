/**
 * 下载 **`musicMetas.json`**（每首歌 × 每个难度的分数/PT 系数）。
 *
 * ## 为什么它单独一个脚本
 *
 * 其余所有表都来自 `sekai-world.github.io/sekai-master-db-diff/`（官方 master data 的镜像，
 * `fetch-raw.mjs` 负责，按 blob sha 判定是否需要重下）。这张表**不在那个仓库里** ——
 * 它是社区（pjsk.moe 那条线）从游戏资源反解出来的派生物，上游是
 * `moe.exmeaning.com`。组卡引擎算「分数 / 活动 PT」非它不可，
 * 而 master data 里只有谱面等级、没有这些系数。
 *
 * ## 为什么原样存 5.6 MB 而不是在这里裁剪
 *
 * 裁剪规则**只允许存在于一处**：`RowProjectors.MUSIC_METAS`。
 * 这个脚本只负责联网，裁剪交给 `BuildDataPack`（它复用 App 里那份经过自检的裁剪器）。
 * 同一套规则在 JS 与 Kotlin 里各写一遍然后逐渐跑偏，是本项目已经踩过的坑。
 *
 * ## 不做 sha 比对
 *
 * 上游没有稳定的「文件 sha" 接口（那是 GitHub 仓库才有的东西）。改用**内容哈希**：
 * 下载后算 sha256，与上次记录的相同就保留旧文件（同样能避免无意义的改动）。
 *
 * 用法：node tools/datapack/fetch-music-metas.mjs [--force]
 */
import { mkdirSync, readFileSync, writeFileSync, existsSync, statSync } from 'node:fs';
import { createHash } from 'node:crypto';

const URLS = [
  'https://moe.exmeaning.com/data/music_meta/music_metas.json',
  // 备选（研究文档实测本机不可达，但在别的网络里可能可用）
  'https://storage.sekai.best/sekai-best-assets/music_metas.json',
];

const outDir = new URL('./raw/', import.meta.url);
const outFile = new URL('./raw/musicMetas.json', import.meta.url);
const shaFile = new URL('./raw/musicMetas.sha256', import.meta.url);

const force = process.argv.includes('--force');

mkdirSync(outDir, { recursive: true });

const sha256 = (buf) => createHash('sha256').update(buf).digest('hex');

let source = null;
let body = null;

for (const url of URLS) {
  try {
    process.stdout.write(`拉取 ${url} … `);
    const started = Date.now();
    const res = await fetch(url, { headers: { 'User-Agent': 'sekai-lmc-datapack' } });
    if (!res.ok) {
      console.log(`HTTP ${res.status}`);
      continue;
    }
    body = Buffer.from(await res.arrayBuffer());
    const rows = JSON.parse(body.toString('utf8'));
    if (!Array.isArray(rows) || rows.length === 0) {
      console.log(`返回了 ${rows && rows.length} 行，不可用`);
      continue;
    }
    // 只认「确实是 music_metas」的内容：必须有引擎要的关键字段
    const sample = rows[0];
    for (const key of ['music_id', 'difficulty', 'event_rate', 'base_score', 'fever_score']) {
      if (!(key in sample)) throw new Error(`返回内容缺少字段 ${key}`);
    }
    source = url;
    console.log(`OK，${rows.length} 行 / ${(body.length / 1048576).toFixed(2)} MB / ${Date.now() - started} ms`);
    break;
  } catch (e) {
    console.log(`失败：${e.message}`);
  }
}

if (!body) {
  console.error('\n[失败] 所有源都拿不到 musicMetas.json。若本机网络受限，可以手动放一份到 tools/datapack/raw/musicMetas.json 后重跑 build.ps1 --skip-fetch。');
  process.exit(1);
}

const digest = sha256(body);
const previous = existsSync(shaFile) ? readFileSync(shaFile, 'utf8').trim() : '';

if (!force && existsSync(outFile) && previous === digest) {
  const mb = (statSync(outFile).size / 1048576).toFixed(2);
  console.log(`内容未变（sha ${digest.slice(0, 12)}），保留现有 raw/musicMetas.json（${mb} MB）`);
  process.exit(0);
}

writeFileSync(outFile, body);
writeFileSync(shaFile, digest + '\n');
console.log(
  `已写入 raw/musicMetas.json（${(body.length / 1048576).toFixed(2)} MB，sha ${digest.slice(0, 12)}，源 ${source}）`,
);
