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
import { createHash } from 'node:crypto';

const JP = 'https://sekai-world.github.io/sekai-master-db-diff/';
// 往回扫 20 条提交找版本号：机器人定时提交的信息里没有版本号（见文件末尾的说明）
const COMMIT_API_20 =
  'https://api.github.com/repos/Sekai-World/sekai-master-db-diff/commits?per_page=20';
// 仓库里所有文件的 blob sha —— 用来判断本地文件到底是不是最新的（见 fetchTreeShas）
const TREE_API =
  'https://api.github.com/repos/Sekai-World/sekai-master-db-diff/git/trees/main?recursive=1';

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

/**
 * 一次拿到仓库里所有文件的 **blob sha**，用它当唯一权威判据。
 *
 * 为什么要这么较真：反复出现过「本地文件存在、体积也差不多，但其实落后于仓库」的情况。
 * 真事：`cards.json` 在 09-13 15:00 的版本提交里加了 5 张新卡，
 * 而我们在 09-13 21:46 抓下来的却是**CDN 上的陈旧缓存**（少 5 张卡），
 * 结果这个旧版本被打进了快照 —— 用户看到的就是「卡牌数据不够新」。
 *
 * 体积对不上能发现大问题，但对不上「少 5 张卡」这种（124 KB / 34 MB ≈ 0.4%）完全无能为力，
 * 而 git blob sha 是内容哈希：差一个字节都对不上。
 */
async function fetchTreeShas() {
  try {
    const res = await fetch(TREE_API, {
      headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'sekai-lmc-datapack' },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    if (json.truncated) {
      console.log('  ⚠️ 仓库树响应被 GitHub 截断，本次退化为「只比体积」');
      return {};
    }
    const map = {};
    for (const entry of json.tree ?? []) {
      if (entry.type === 'blob') map[entry.path] = entry.sha;
    }
    console.log(`  已取到仓库 ${Object.keys(map).length} 个文件的 blob sha`);
    return map;
  } catch (e) {
    console.log(`  ⚠️ 取仓库树失败（${e.message}），本次退化为「只比体积」`);
    return {};
  }
}

/** 计算一段内容的 git blob sha（`sha1("blob <字节数>\0" + 内容)`）。 */
function gitBlobSha(buf) {
  return createHash('sha1').update(`blob ${buf.length}\0`, 'utf8').update(buf).digest('hex');
}

const treeShas = await fetchTreeShas();

/** 并发下载；失败重试一次（GitHub Pages 偶发 5xx）。 */
async function grab(spec) {
  const dest = new URL(spec.file, outDir);
  const expectedSha = treeShas[spec.file] ?? null;

  // 本地已有：只有当它的 blob sha 与仓库**完全一致**才跳过
  if (existsSync(dest)) {
    if (expectedSha && gitBlobSha(readFileSync(dest)) === expectedSha) {
      skipped++;
      return;
    }
    if (!expectedSha) {
      const near = Math.abs(statSync(dest).size - spec.bytes) < spec.bytes * 0.02 + 1024;
      if (near) {
        skipped++;
        return;
      }
    }
    // 否则：本地是旧版本（或被截断），下面重下
  }

  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      // ⚠️ 必须绕过 CDN 缓存：Pages 前面是 Fastly（max-age=600），
      // 裸 fetch 有可能拿到部署前的旧副本 —— 这正是上面那次「少 5 张卡」的来源。
      const url = `${JP}${spec.file}?cb=${Date.now()}`;
      const res = await fetch(url, { headers: { 'Cache-Control': 'no-cache', Pragma: 'no-cache' } });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const buf = Buffer.from(await res.arrayBuffer());

      if (expectedSha) {
        const actual = gitBlobSha(buf);
        if (actual !== expectedSha) {
          throw new Error(`内容与仓库不一致（sha 实得 ${actual.slice(0, 12)}，仓库 ${expectedSha.slice(0, 12)}）—— 多半还是 CDN 缓存`);
        }
      } else {
        // 退化路径：比字节数。**必须比字节**，不能比字符数
        // （这个数据里全是日文，一个日文 3 字节，早期版本因此漏写过 17 张表）。
        const drift = Math.abs(buf.length - spec.bytes) / spec.bytes;
        if (drift > 0.02) {
          throw new Error(`大小异常：实得 ${buf.length} 字节，目录记录 ${spec.bytes} 字节`);
        }
      }
      writeFileSync(dest, buf);
      done++;
      process.stdout.write(`\r已下载 ${done} / 已是最新 ${skipped} / 失败 ${failed} 张…`);
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
