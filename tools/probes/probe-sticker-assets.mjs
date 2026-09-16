/**
 * 实测「表情包制作器」的底图素材：三条件各有多少张、多大、能不能内置。
 *
 * 背景：pjsk.moe 的表情包制作器靠一套「表情包框」底图（每角色一组），
 * 但那套图**不在它自己的仓库里**（.gitignore 排除了），托管在第三方 moe.exmeaning.com。
 * 所以「素材从哪来」是这个功能能不能做的前提。这里把三条候选路一次性量出来：
 *   1. moe.exmeaning.com/sticker-maker/（与 pjsk.moe 完全一致的观感）
 *   2. TheOriginalAyaka/sekai-stickers（MIT、已归档，许可最干净）
 *   3. storage.sekai.best 的 stamp_balloon/（官方 CDN，最"正版"）
 *
 * 用法：node tools/probes/probe-sticker-assets.mjs
 */
const SM_BASE = 'https://moe.exmeaning.com/sticker-maker';
const UPSTREAM_TREE =
  'https://api.github.com/repos/TheOriginalAyaka/sekai-stickers/git/trees/HEAD?recursive=1';
const BUCKET = 'https://storage.sekai.best/sekai-jp-assets/';

const fmtMB = (b) => `${(b / 1048576).toFixed(2)} MB`;
const fmtKB = (b) => `${(b / 1024).toFixed(1)} KB`;

async function head(url) {
  try {
    const res = await fetch(url, { method: 'HEAD' });
    return {
      status: res.status,
      length: Number(res.headers.get('content-length') ?? 0),
      type: res.headers.get('content-type') ?? '',
    };
  } catch (e) {
    return { status: 'ERR', length: 0, type: String(e).slice(0, 60) };
  }
}

// ─────────────────────────────────────────────────────────────
console.log('═══ 路子一：moe.exmeaning.com/sticker-maker ═══');
// ─────────────────────────────────────────────────────────────
let characters = null;
try {
  const res = await fetch(`${SM_BASE}/characters.json?t=${Date.now()}`);
  console.log(`  characters.json  HTTP ${res.status}`);
  if (res.ok) {
    characters = await res.json();
    const byChar = new Map();
    for (const item of characters) {
      const key = item.name?.split(' ')[0] ?? item.folder ?? '?';
      byChar.set(key, (byChar.get(key) ?? 0) + 1);
    }
    console.log(`  条目总数 = ${characters.length}`);
    console.log(`  角色数 = ${byChar.size}`);
    console.log(`  每角色条数：${[...byChar.entries()].map(([k, v]) => `${k}:${v}`).join(', ')}`);
    console.log(`  第一条样例字段：${JSON.stringify(Object.keys(characters[0]))}`);
    console.log(`  第一条样例：${JSON.stringify(characters[0]).slice(0, 260)}`);
  }
} catch (e) {
  console.log(`  抓取失败：${String(e).slice(0, 120)}`);
}

// 抽样量单张框图的体积
const samples = [
  'ichika/ichika1', 'ichika/ichika20', 'saki/saki1', 'miku/miku1',
  'kanade/kanade1', 'emu/emu1', 'kaito/kaito1', 'mafuyu/mafuyu5',
];
console.log('\n  抽样单张框图（webp / png）：');
let sum = 0, n = 0;
for (const s of samples) {
  const w = await head(`${SM_BASE}/img/${s}.webp`);
  if (w.status === 200 && w.length > 0) { sum += w.length; n++; }
  const p = await head(`${SM_BASE}/img/${s}.png`);
  console.log(
    `    ${s.padEnd(20)} webp: ${w.status} ${w.length ? fmtKB(w.length) : '-'}` +
      `   png: ${p.status} ${p.length ? fmtKB(p.length) : '-'}`,
  );
}
if (n > 0 && characters) {
  const avg = sum / n;
  console.log(`\n  平均单张 webp ≈ ${fmtKB(avg)}（基于 ${n} 个样本）`);
  console.log(`  → ${characters.length} 张估算总量 ≈ ${fmtMB(avg * characters.length)}`);
}
// 字体
const font = await head(`${SM_BASE}/fonts/MaokenAssortedSans-Lite.ttf`);
console.log(`  字体 MaokenAssortedSans-Lite.ttf: ${font.status} ${font.length ? fmtMB(font.length) : '-'}`);

// ─────────────────────────────────────────────────────────────
console.log('\n═══ 路子二：TheOriginalAyaka/sekai-stickers（MIT）═══');
// ─────────────────────────────────────────────────────────────
try {
  const res = await fetch(UPSTREAM_TREE, { headers: { 'User-Agent': 'sekai-lmc' } });
  console.log(`  git tree API HTTP ${res.status}`);
  if (res.ok) {
    const tree = await res.json();
    const imgs = tree.tree.filter((t) => /public\/img\/.*\.(png|webp|jpg)$/i.test(t.path));
    const total = imgs.reduce((a, t) => a + (t.size ?? 0), 0);
    const dirs = new Set(imgs.map((t) => t.path.split('/')[2]));
    console.log(`  图片数 = ${imgs.length}，角色目录数 = ${dirs.size}`);
    console.log(`  仓库内合计 = ${fmtMB(total)}（平均 ${fmtKB(total / Math.max(imgs.length, 1))}）`);
    console.log(`  样例路径：${imgs.slice(0, 4).map((t) => t.path).join(', ')}`);
    const fonts = tree.tree.filter((t) => /\.(ttf|otf|woff2?)$/i.test(t.path));
    console.log(`  字体文件：${fonts.map((f) => `${f.path}(${fmtKB(f.size ?? 0)})`).join(', ') || '无'}`);
  }
} catch (e) {
  console.log(`  失败：${String(e).slice(0, 120)}`);
}

// ─────────────────────────────────────────────────────────────
console.log('\n═══ 路子三：官方 CDN 的 stamp_balloon/ ═══');
// ─────────────────────────────────────────────────────────────
async function list(prefix, max = 20, delimiter = '') {
  const url =
    `${BUCKET}?list-type=2&prefix=${encodeURIComponent(prefix)}&max-keys=${max}` +
    (delimiter ? `&delimiter=${encodeURIComponent(delimiter)}` : '');
  const res = await fetch(url);
  const xml = await res.text();
  const keys = [...xml.matchAll(/<Key>([^<]+)<\/Key>/g)].map((m) => m[1]);
  const prefixes = [...xml.matchAll(/<Prefix>([^<]+)<\/Prefix>/g)].map((m) => m[1]);
  const count = /<KeyCount>(\d+)<\/KeyCount>/.exec(xml)?.[1];
  return { status: res.status, keys, prefixes, count };
}
// 先看 stamp_balloon 下一层有哪些目录（它是每张贴纸一个目录，还是每角色一组？）
const sb = await list('stamp_balloon/', 20, '/');
// 注意：S3 的响应里会把「请求用的 prefix」也回显成一个 <Prefix>，
// 所以不能拿 prefixes.length 判断有没有子目录 —— 要排掉它自己。
const realPrefixes = sb.prefixes.filter((p) => p !== 'stamp_balloon/');
console.log(`  HTTP ${sb.status}  子目录数=${realPrefixes.length}`);
for (const p of realPrefixes) console.log(`    ${p}`);

// 无论有没有子目录，都直接平铺列出前若干个 key，看真实文件长什么样
const flat = await list('stamp_balloon/', 24);
console.log(`  stamp_balloon/ 平铺前 ${flat.keys.length} 个 key：`);
for (const k of flat.keys) console.log(`    ${k}`);
const sampleBalloon = flat.keys.find((k) => /\.(webp|png)$/i.test(k));
if (sampleBalloon) {
  const h = await head(BUCKET + sampleBalloon);
  console.log(`  抽样 ${sampleBalloon}: HTTP ${h.status} ${h.length ? fmtKB(h.length) : '-'} ${h.type}`);
}
