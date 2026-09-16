/**
 * 实测首页要用到的素材路径与字段格式。
 *
 * 首页要展示：当前卡池 / 最新卡面 / 最新歌曲 / 最新活动 / 角色生日 / 友链。
 * 其中「卡池横幅、活动横幅、角色头像」的文件名我还没验证过，
 * 「生日字段的格式」也没验证过 —— 这个脚本把它们一次性钉死。
 *
 * 手段：storage.sekai.best 是**允许匿名列举**的 S3 兼容桶，
 * 所以可以直接问「这个目录下到底有哪些文件」，而不是靠猜文件名再去看 404。
 *
 * 用法：node tools/probes/probe-home-assets.mjs
 */
const BUCKET = 'https://storage.sekai.best/sekai-jp-assets/';
const JP = 'https://sekai-world.github.io/sekai-master-db-diff/';

async function list(prefix, max = 14) {
  const url = `${BUCKET}?list-type=2&prefix=${encodeURIComponent(prefix)}&max-keys=${max}`;
  const res = await fetch(url);
  const xml = await res.text();
  const keys = [...xml.matchAll(/<Key>([^<]+)<\/Key>/g)].map((m) => m[1]);
  const truncated = /<IsTruncated>true<\/IsTruncated>/.test(xml);
  return { status: res.status, keys, truncated };
}

function show(title, r) {
  console.log(`\n── ${title} ──  (HTTP ${r.status}${r.truncated ? ', 还有更多' : ''})`);
  if (r.keys.length === 0) console.log('  (空)');
  for (const k of r.keys) console.log(`  ${k}`);
}

// 1) 角色头像 —— 注意必须带 `character/` 前缀！
//    （第一版忘了前缀，查的是桶根目录下的 character_select/，自然是空的）
for (const prefix of [
  'character/character_select_small/',
  'character/character_select/',
  'character/member_gacha/',
  'character/character_sd_l/',
]) {
  show(prefix, await list(prefix, 6));
}

// 2) 活动横幅 —— 已知 event/<bundle>/icon/icon_eventbadge_1.webp 存在；
//    上一步看到还有 logo/logo.webp 与 screen/banner.png，这里确认 screen 下有没有 webp
show('event/event_awakening_2021/screen/', await list('event/event_awakening_2021/screen/'));
show('event/event_awakening_2021/logo/', await list('event/event_awakening_2021/logo/'));

// 3) 卡池横幅 —— 已知 logo/logo.webp 存在
show('gacha/ab_gacha_326/screen/texture/', await list('gacha/ab_gacha_326/screen/texture/'));

// 4) 生日字段格式
console.log('\n── characterProfiles.json 第一行（看 birthday 字段的格式）──');
const cpRes = await fetch(JP + 'characterProfiles.json');
const cp = await cpRes.json();
console.log('  共', cp.length, '行');
console.log('  第一行:', JSON.stringify(cp[0], null, 2).split('\n').slice(0, 14).join('\n  '));

// 5) 活动表里判断「正在进行」要用的字段
console.log('\n── events.json 第一个活动（看时间字段）──');
const evRes = await fetch(JP + 'events.json', { headers: { Range: 'bytes=0-16000' } });
const evText = await evRes.text();
const start = evText.indexOf('{');
let depth = 0, inStr = false, esc = false, end = -1;
for (let i = start; i < evText.length; i++) {
  const c = evText[i];
  if (inStr) {
    if (esc) esc = false;
    else if (c === '\\') esc = true;
    else if (c === '"') inStr = false;
    continue;
  }
  if (c === '"') inStr = true;
  else if (c === '{' || c === '[') depth++;
  else if (c === '}' || c === ']') { depth--; if (depth === 0) { end = i; break; } }
}
if (end > 0) {
  const obj = JSON.parse(evText.slice(start, end + 1));
  console.log('  字段:', Object.keys(obj).join(', '));
  console.log('  startAt =', obj.startAt, '  endAt =', obj.endAt);
  console.log('  assetbundleName =', obj.assetbundleName, '  name =', obj.name);
} else {
  console.log('  切片太小没读全，字段看不到');
}

// 6) 卡池表的时间字段（gachas.json 44 MB，只取开头）
console.log('\n── gachas.json 第一个卡池（看时间字段）──');
// gachas.json 有 44 MB，单个卡池对象可能就有几十 KB（卡池内每张卡的概率明细都内联在里面），
// 所以切片要开到 200 KB 才能包含一个完整对象。
const gRes = await fetch(JP + 'gachas.json', { headers: { Range: 'bytes=0-200000' } });
const gText = await gRes.text();
const gs = gText.indexOf('{');
depth = 0; inStr = false; esc = false; let ge = -1;
for (let i = gs; i < gText.length; i++) {
  const c = gText[i];
  if (inStr) {
    if (esc) esc = false;
    else if (c === '\\') esc = true;
    else if (c === '"') inStr = false;
    continue;
  }
  if (c === '"') inStr = true;
  else if (c === '{' || c === '[') depth++;
  else if (c === '}' || c === ']') { depth--; if (depth === 0) { ge = i; break; } }
}
if (ge > 0) {
  const obj = JSON.parse(gText.slice(gs, ge + 1));
  console.log('  字段:', Object.keys(obj).join(', '));
  for (const k of Object.keys(obj)) {
    const v = obj[k];
    const t = Array.isArray(v) ? `数组(${v.length}项)` : typeof v === 'object' ? '对象' : JSON.stringify(v);
    console.log(`    ${k} = ${String(t).slice(0, 90)}`);
  }
} else {
  console.log('  切片太小没读全');
}
