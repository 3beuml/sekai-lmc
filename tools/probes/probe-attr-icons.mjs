/**
 * 找「属性图标」的小图片素材。
 *
 * 已知：官方主素材桶 storage.sekai.best/sekai-jp-assets 的 common_icon/ 里
 * **只有一个 Unity 精灵图集**（common_icon_atlas.spriteatlas），拿不到单张图标。
 * 但别的站点（sekai.best / pjsk.moe）界面上明明有属性图标，所以它们一定从某处取。
 *
 * 这个脚本探几条最可能的线索：
 *  1. sekai.best 自己的附加素材桶 sekai-best-assets
 *  2. exmeaning 的 CDN（简中素材）
 *  3. pjsk.moe（Moesekai）仓库里的 public 图标目录
 *
 * 用法：node tools/probes/probe-attr-icons.mjs
 */
const SEKAIBEST = 'https://storage.sekai.best/sekai-best-assets/';
const EXMEANING = 'https://storage.exmeaning.com/sekai-cn-assets/';

async function listS3(base, prefix, delimiter = '') {
  const url =
    `${base}?list-type=2&prefix=${encodeURIComponent(prefix)}&max-keys=30` +
    (delimiter ? `&delimiter=${encodeURIComponent(delimiter)}` : '');
  try {
    const res = await fetch(url);
    const xml = await res.text();
    const keys = [...xml.matchAll(/<Key>([^<]+)<\/Key>/g)].map((m) => m[1]);
    const prefixes = [...xml.matchAll(/<Prefix>([^<]+)<\/Prefix>/g)]
      .map((m) => m[1])
      .filter((p) => p !== prefix);
    return { status: res.status, keys, prefixes, raw: xml.slice(0, 160) };
  } catch (e) {
    return { status: 'ERR', keys: [], prefixes: [], raw: String(e).slice(0, 80) };
  }
}

function show(title, r, max = 12) {
  console.log(`\n── ${title} ──  HTTP ${r.status}`);
  if (r.prefixes.length) for (const p of r.prefixes.slice(0, max)) console.log(`   目录 ${p}`);
  for (const k of r.keys.slice(0, max)) console.log(`   ${k}`);
  if (!r.prefixes.length && !r.keys.length) console.log(`   (空) ${r.raw}`);
}

console.log('═══ 1. sekai.best 的附加素材桶（属性图标最可能在这里）═══');
show('根目录', await listS3(SEKAIBEST, '', '/'));
for (const p of ['common_icon/', 'attr/', 'attribute/', 'icon/']) {
  show(p, await listS3(SEKAIBEST, p, '/'));
}

console.log('\n═══ 2. exmeaning 的简中素材桶 ═══');
show('根目录', await listS3(EXMEANING, '', '/'));
show('common_icon/', await listS3(EXMEANING, 'common_icon/', '/'));

console.log('\n═══ 3. pjsk.moe（Moesekai）仓库里的图标 ═══');
for (const path of ['public/data/icon', 'public/data', 'public']) {
  const url = `https://api.github.com/repos/StarMoe-org/Moesekai/contents/${path}`;
  try {
    const res = await fetch(url, { headers: { 'User-Agent': 'sekai-lmc', Accept: 'application/vnd.github+json' } });
    const json = await res.json();
    console.log(`\n── ${path} ──  HTTP ${res.status}`);
    if (Array.isArray(json)) {
      for (const item of json.slice(0, 16)) console.log(`   ${item.type === 'dir' ? '目录' : '文件'} ${item.name}`);
    } else {
      console.log(`   ${JSON.stringify(json).slice(0, 120)}`);
    }
  } catch (e) {
    console.log(`\n── ${path} ── 失败 ${String(e).slice(0, 60)}`);
  }
}
