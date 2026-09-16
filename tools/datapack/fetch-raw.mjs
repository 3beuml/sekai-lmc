/**
 * 把要打进 APK 的 master data 拉到本地，供 BuildDataPack 裁剪。
 *
 * 分工说明（重要）：
 *  - **本脚本只负责联网下载**。它跑在开发机上（到 GitHub Pages 约 1.6 MB/s），
 *    而手机上只有约 1.5 KB/s —— 这个 1000 倍的不对称正是「数据内置」的理由。
 *  - **裁剪不在这里做**，交给 Kotlin 侧的 BuildDataPack，复用 App 里那份经过测试的
 *    `RowProjectors`。这样做是为了避免同一套裁剪规则在 JS 和 Kotlin 里各写一遍然后逐渐跑偏。
 *
 * 用法：node tools/datapack/fetch-raw.mjs [模块名...]
 *   默认只抓要打包的模块（CARD CHARACTER MUSIC EVENT STICKER，约 46 MB）；
 *   COSTUME(53 MB) 与 GACHA(46 MB) 默认跳过，因为两张巨表占了全量 131/145 MB，
 *   而当前 UI 还用不到它们（要抓就显式写：node ... COSTUME GACHA）。
 */
import { mkdirSync, readFileSync, writeFileSync, existsSync, statSync } from 'node:fs';

const JP = 'https://sekai-world.github.io/sekai-master-db-diff/';
// 往回扫 20 条提交找版本号：机器人定时提交的信息里没有版本号（见文件末尾的说明）
const COMMIT_API_20 =
  'https://api.github.com/repos/Sekai-World/sekai-master-db-diff/commits?per_page=20';

const outDir = new URL('./raw/', import.meta.url);
mkdirSync(outDir, { recursive: true });

// 从 MasterCatalog.kt 读表清单，保证「打包哪些表」永远和 App 的认识一致
const catalog = readFileSync(
  new URL('../../app/src/main/java/com/pjsk/toolbox/data/remote/MasterCatalog.kt', import.meta.url),
  'utf8',
);
const specs = [...catalog.matchAll(
  /TableSpec\(\s*"([^"]+)"\s*,\s*DataModule\.(\w+)\s*,\s*([0-9_]+)L/g,
)].map((m) => ({ file: m[1], module: m[2], bytes: Number(m[3].replace(/_/g, '')) }));

const args = process.argv.slice(2);
const modules = args.length
  ? args.map((a) => a.toUpperCase())
  : ['CARD', 'CHARACTER', 'MUSIC', 'EVENT', 'STICKER'];

const targets = specs.filter((s) => modules.includes(s.module));
if (targets.length === 0) throw new Error(`没有匹配的模块：${modules.join(', ')}`);

console.log(`模块：${modules.join(', ')}，共 ${targets.length} 张表`);
console.log(`预计下载 ${(targets.reduce((a, s) => a + s.bytes, 0) / 1048576).toFixed(1)} MB\n`);

let done = 0;
let skipped = 0;
let failed = 0;
const started = Date.now();

/** 并发下载；失败重试一次（GitHub Pages 偶发 5xx）。 */
async function grab(spec) {
  const dest = new URL(spec.file, outDir);
  // 已存在且大小对得上就跳过（重跑脚本时不用重下 46 MB）
  if (existsSync(dest) && Math.abs(statSync(dest).size - spec.bytes) < spec.bytes * 0.02 + 1024) {
    skipped++;
    return;
  }
  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      const res = await fetch(JP + spec.file);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const text = await res.text();
      // 完整性校验：**必须比字节数**。
      // 踩过的坑：第一版拿 `text.length`（字符数）去比目录里的字节数，
      // 而这个数据里全是日文（一个日文 3 字节），于是 17 张含日文的表被误判成
      // 「大小异常」而**根本没写盘**（包括 musics.json、gachas.json）。
      const byteLength = Buffer.byteLength(text, 'utf8');
      const drift = Math.abs(byteLength - spec.bytes) / spec.bytes;
      if (drift > 0.02) {
        throw new Error(`大小异常：实得 ${byteLength} 字节，目录记录 ${spec.bytes} 字节`);
      }
      writeFileSync(dest, text, 'utf8');
      done++;
      process.stdout.write(`\r已下载 ${done} / 跳过 ${skipped} / 失败 ${failed} 张…`);
      return;
    } catch (e) {
      if (attempt === 2) {
        failed++;
        console.log(`\n  ✗ ${spec.file}: ${e.message}`);
      }
    }
  }
}

const CONCURRENCY = 6;
for (let i = 0; i < targets.length; i += CONCURRENCY) {
  await Promise.all(targets.slice(i, i + CONCURRENCY).map(grab));
}
process.stdout.write('\n');

// 记下快照对应的数据状态。
//
// 注意：**不能只读最新一条提交**。实测最近 10 条提交里有 9 条是
// `update user information`（机器人定时提交），只有少数几条才带
// `master version X asset version Y`。所以要往回扫若干条找版本号。
//
// 另外记下关键文件的 ETag 作为「数据变没变」的指纹：实测 ETag 形如
// `W/"6aa63c22-2170a12"`，后半段是**真实字节数的十六进制**
// （0x2170a12 = 35064338 = cards.json 的真实大小），所以 ETag 一变就说明文件变了。
try {
  const res = await fetch(COMMIT_API_20, {
    headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'sekai-lmc-datapack' },
  });
  const json = await res.json();
  let version = null;
  for (const c of json) {
    const m = /master version ([\d.]+) asset version ([\d.]+)/.exec(c?.commit?.message ?? '');
    if (m) {
      version = { masterVersion: m[1], assetVersion: m[2], at: c.commit.committer.date };
      break;
    }
  }
  const etags = {};
  for (const f of ['cards.json', 'events.json', 'musics.json', 'gachas.json', 'cardRarities.json']) {
    const r = await fetch(JP + f, { method: 'HEAD' });
    etags[f] = r.headers.get('etag');
  }
  const info = {
    latestCommit: {
      message: json?.[0]?.commit?.message ?? '',
      date: json?.[0]?.commit?.committer?.date ?? '',
    },
    recentVersionCommit: version,
    etags,
  };
  writeFileSync(new URL('_version.json', outDir), JSON.stringify(info, null, 2), 'utf8');
  console.log(
    `\n最近带版本号的提交：master ${version?.masterVersion ?? '?'} / asset ${version?.assetVersion ?? '?'}` +
      `（${version?.at ?? '?'}）`,
  );
  console.log(`最新提交信息：${JSON.stringify(info.latestCommit.message.split('\n')[0])}（${info.latestCommit.date}）`);
} catch (e) {
  console.log(`\n版本探测失败（不影响打包）：${e.message}`);
}

const totalBytes = targets.reduce((a, s) => a + s.bytes, 0);
console.log(
  `\n完成：下载 ${done} 张 / 复用 ${skipped} 张 / 失败 ${failed} 张，` +
    `耗时 ${((Date.now() - started) / 1000).toFixed(1)}s（源数据合计 ${(totalBytes / 1048576).toFixed(1)} MB）`,
);
console.log(`-> ${outDir.pathname}`);
if (failed > 0) process.exitCode = 1;
