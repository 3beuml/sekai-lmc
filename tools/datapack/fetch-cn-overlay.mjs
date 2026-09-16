/**
 * 下载**简中服**的表，用来生成中文名叠加层。
 *
 * 为什么要单独下：本 App 的数据主线是日服（版本最新），中文名来自简中服的同一个表
 * （按 id 对齐）。内置快照要「装完就是中文」，就得把简中服的中文名一起预置进去。
 *
 * 只下**真正会被打包**的那些表（判断规则与 BuildDataPack.kt 一致）：
 * 没有裁剪器、且日服原始体积超过 4 MB 的表会被打包器跳过，那它的中文名也就没必要下。
 *
 * 输出：tools/datapack/cn/<表名>
 * 用法：node tools/datapack/fetch-cn-overlay.mjs [模块名...]
 */
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';

const CN = 'https://sekai-world.github.io/sekai-master-db-cn-diff/';
// 简中服仓库里所有文件的 blob sha —— 判断本地 cn/ 里的文件是不是最新的
const CN_TREE_API =
  'https://api.github.com/repos/Sekai-World/sekai-master-db-cn-diff/git/trees/main?recursive=1';
const MAX_RAW_WITHOUT_PROJECTOR = 4 * 1024 * 1024;

/** 与 RowProjectors.forFile 保持一致：目前只有 cards.json 有裁剪器。 */
const PROJECTED = new Set(['cards.json']);

const outDir = new URL('./cn/', import.meta.url);
mkdirSync(outDir, { recursive: true });

const catalog = readFileSync(
  new URL('../../app/src/main/java/com/pjsk/toolbox/data/remote/MasterCatalog.kt', import.meta.url),
  'utf8',
);
const specs = [...catalog.matchAll(
  /TableSpec\(\s*"([^"]+)"\s*,\s*DataModule\.(\w+)\s*,\s*([0-9_]+)L([^)]*)\)/g,
)].map((m) => ({
  file: m[1],
  module: m[2],
  bytes: Number(m[3].replace(/_/g, '')),
  cnOverlay: /cnOverlay\s*=\s*true/.test(m[4]),
}));

const args = process.argv.slice(2);
const modules = args.length
  ? args.map((a) => a.toUpperCase())
  : ['CARD', 'CHARACTER', 'MUSIC', 'EVENT', 'STICKER', 'GACHA'];

const targets = specs.filter((s) => {
  if (!s.cnOverlay) return false;
  if (!modules.includes(s.module)) return false;
  // 打包器会跳过的表，中文名也就不用下
  if (!PROJECTED.has(s.file) && s.bytes > MAX_RAW_WITHOUT_PROJECTOR) return false;
  return true;
});

console.log(`需要中文名的表：${targets.length} 张`);
const skippedBig = specs.filter(
  (s) => s.cnOverlay && modules.includes(s.module) && !PROJECTED.has(s.file) && s.bytes > MAX_RAW_WITHOUT_PROJECTOR,
);
if (skippedBig.length) {
  console.log(`（跳过 ${skippedBig.map((s) => s.file).join(', ')}：打包器不会收它们）`);
}
console.log('');

let done = 0, reused = 0, failed = 0;
const started = Date.now();

/**
 * 一次拿到简中服仓库的 blob sha，用它判断本地 cn/ 里的文件是否需要重下。
 *
 * ⚠️ 这里原来是「文件存在就复用」，和日服那边踩的是**同一个坑**：
 * GitHub Pages 前面有 CDN 缓存，某次抓取很可能拿到部署前的旧副本，
 * 而这个「文件存在就复用」的策略会让旧副本一直留着 —— 日服那边就是这么
 * 把 19 张表（含卡牌与歌曲）的旧数据打进了快照。所以两边都改成按内容 sha 判断。
 */
async function fetchTreeShas() {
  try {
    const res = await fetch(CN_TREE_API, {
      headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'sekai-lmc-datapack' },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    if (json.truncated) {
      console.log('  ⚠️ 简中服仓库树被截断，本次退化为「文件存在即复用」');
      return {};
    }
    const map = {};
    for (const e of json.tree ?? []) if (e.type === 'blob') map[e.path] = e.sha;
    console.log(`  已取到简中服仓库 ${Object.keys(map).length} 个文件的 blob sha`);
    return map;
  } catch (e) {
    console.log(`  ⚠️ 取简中服仓库树失败（${e.message}），本次退化为「文件存在即复用」`);
    return {};
  }
}

const cnShas = await fetchTreeShas();

/** 计算一段内容的 git blob sha（`sha1("blob <字节数>\0" + 内容)`）。 */
function gitBlobSha(buf) {
  return createHash('sha1').update(`blob ${buf.length}\0`, 'utf8').update(buf).digest('hex');
}

async function grab(spec) {
  const dest = new URL(spec.file, outDir);
  const expectedSha = cnShas[spec.file] ?? null;

  if (existsSync(dest)) {
    if (expectedSha && gitBlobSha(readFileSync(dest)) === expectedSha) {
      reused++;
      return;
    }
    if (!expectedSha) {
      reused++;
      return;
    }
    // 内容对不上 → 下面重下
  }
  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      // 绕过 CDN 缓存（同 fetch-raw.mjs 的说明）
      const res = await fetch(`${CN}${spec.file}?cb=${Date.now()}`, {
        headers: { 'Cache-Control': 'no-cache', Pragma: 'no-cache' },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const buf = Buffer.from(await res.arrayBuffer());
      // 简中服体积与日服不同，所以**不能**拿日服的目录大小校验；这里优先比内容 sha。
      if (expectedSha) {
        const actual = gitBlobSha(buf);
        if (actual !== expectedSha) {
          throw new Error(`内容与简中服仓库不一致（sha 实得 ${actual.slice(0, 12)}，仓库 ${expectedSha.slice(0, 12)}）`);
        }
      } else {
        const trimmed = buf.toString('utf8').trim();
        if (!trimmed.startsWith('[') || !trimmed.endsWith(']')) {
          throw new Error(`不像完整的 JSON 数组（开头 ${JSON.stringify(trimmed.slice(0, 20))}）`);
        }
      }
      writeFileSync(dest, buf);
      done++;
      process.stdout.write(`\r已下载 ${done} / 复用 ${reused} / 失败 ${failed} 张…`);
      return;
    } catch (e) {
      if (attempt === 2) {
        failed++;
        console.log(`\n  ✗ ${spec.file}: ${e.message}`);
      }
    }
  }
}

const CONCURRENCY = 5;
for (let i = 0; i < targets.length; i += CONCURRENCY) {
  await Promise.all(targets.slice(i, i + CONCURRENCY).map(grab));
}
process.stdout.write('\n');

const total = targets.reduce((a, s) => a + s.bytes, 0);
console.log(
  `\n完成：下载 ${done} 张 / 复用 ${reused} 张 / 失败 ${failed} 张，` +
    `耗时 ${((Date.now() - started) / 1000).toFixed(1)}s（日服对应体积合计 ${(total / 1048576).toFixed(1)} MB）`,
);
if (failed > 0) process.exitCode = 1;
