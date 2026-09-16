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
import { mkdirSync, readFileSync, writeFileSync, existsSync, statSync } from 'node:fs';

const CN = 'https://sekai-world.github.io/sekai-master-db-cn-diff/';
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

async function grab(spec) {
  const dest = new URL(spec.file, outDir);
  if (existsSync(dest) && statSync(dest).size > 0) {
    reused++;
    return;
  }
  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      const res = await fetch(CN + spec.file);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const text = await res.text();
      // 简中服体积与日服不同，所以**不能**拿日服的目录大小校验。
      // 只做一个廉价的形态校验：确实是 JSON 数组即可。
      const trimmed = text.trim();
      if (!trimmed.startsWith('[') || !trimmed.endsWith(']')) {
        throw new Error(`不像完整的 JSON 数组（开头 ${JSON.stringify(trimmed.slice(0, 20))}）`);
      }
      writeFileSync(dest, text, 'utf8');
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
