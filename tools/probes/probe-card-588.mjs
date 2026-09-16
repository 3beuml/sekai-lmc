/**
 * 抓出卡牌 588 的真实数据与它的全部卡面素材，用来确定「详情页要展示什么」。
 * 同时验证 sekai.best 详情页那四个图片标签页对应的素材在本项目能否拿到。
 *
 * 用法：node tools/probes/probe-card-588.mjs
 */
const JP = 'https://sekai-world.github.io/sekai-master-db-diff/';
const BUCKET = 'https://storage.sekai.best/sekai-jp-assets/';

/** 从（可能是半截的）JSON 数组文本里切出完整顶层对象。 */
function topLevelObjects(text) {
  const out = [];
  const first = text.indexOf('\n  {');
  if (first < 0) return out;
  const s = text.slice(first + 1);
  let depth = 0;
  let inStr = false;
  let esc = false;
  let start = -1;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (inStr) {
      if (esc) esc = false;
      else if (c === '\\') esc = true;
      else if (c === '"') inStr = false;
      continue;
    }
    if (c === '"') inStr = true;
    else if (c === '{' || c === '[') {
      if (depth === 0 && c === '{') start = i;
      depth++;
    } else if (c === '}' || c === ']') {
      depth--;
      if (depth === 0 && start >= 0) {
        try {
          out.push(JSON.parse(s.slice(start, i + 1)));
        } catch {
          /* 半截对象，跳过 */
        }
        start = -1;
      }
      if (depth < 0) break;
    }
  }
  return out;
}

const kb = (n) => `${(n / 1024).toFixed(1)} KB`;

const TARGET_ID = Number(process.argv[2] ?? 588);

// 按 id 估算字节位置：cards.json 共 35,064,338 字节、1447 张卡、按 id 升序。
// 估算值 ±(前 800 KB / 后 400 KB) 足够覆盖目标卡（实测 id 606–635 落在 13.8–14.6 MB）。
const TOTAL_BYTES = 35_064_338;
const MAX_ID = 1469;
const estimate = Math.floor((TARGET_ID / MAX_ID) * TOTAL_BYTES);
const from = Math.max(0, estimate - 800_000);
const to = estimate + 400_000;
console.log(`按 id=${TARGET_ID} 估算字节位置 ≈ ${estimate.toLocaleString()}，抓取 ${from.toLocaleString()}–${to.toLocaleString()}`);

const res = await fetch(JP + 'cards.json', { headers: { Range: `bytes=${from}-${to}` } });
const cards = topLevelObjects(await res.text());
console.log(`切出完整对象 ${cards.length} 个，id 范围 ${cards[0]?.id} … ${cards[cards.length - 1]?.id}`);

const card = cards.find((c) => c.id === TARGET_ID);
if (!card) {
  console.log(`\nid=${TARGET_ID} 不在这段区间里`);
  process.exit(0);
}

console.log(`\n════ 卡牌 ${TARGET_ID} 的全部字段 ════`);
for (const [key, value] of Object.entries(card)) {
  if (key === 'cardParameters') continue;
  const shown = Array.isArray(value)
    ? `数组(${value.length}) ${JSON.stringify(value).slice(0, 90)}`
    : typeof value === 'object' && value !== null
      ? `对象 ${JSON.stringify(value).slice(0, 90)}`
      : JSON.stringify(value);
  console.log(`  ${key.padEnd(34)} = ${String(shown).slice(0, 125)}`);
}

const byType = new Map();
for (const p of card.cardParameters ?? []) {
  const list = byType.get(p.cardParameterType) ?? [];
  list.push(p);
  byType.set(p.cardParameterType, list);
}
console.log('\n  cardParameters:');
for (const [type, list] of byType) {
  list.sort((a, b) => a.cardLevel - b.cardLevel);
  console.log(
    `    ${type}: ${list.length} 档 (${list[0].cardLevel}→${list[list.length - 1].cardLevel} 级, ` +
      `${list[0].power}→${list[list.length - 1].power})`,
  );
}

console.log('\n════ sekai.best 详情页那 4 个图片标签页 + 下载用的素材 ════');
const b = card.assetbundleName;
const targets = [
  ['通常卡图      member/card_normal.webp', `character/member/${b}/card_normal.webp`],
  ['通常卡图 PNG  member/card_normal.png', `character/member/${b}/card_normal.png`],
  ['特训后卡图    member/card_after_training.webp', `character/member/${b}/card_after_training.webp`],
  ['特训后卡图PNG member/card_after_training.png', `character/member/${b}/card_after_training.png`],
  ['通常角色图    member_cutout_trm/normal.webp', `character/member_cutout_trm/${b}/normal.webp`],
  ['特训后角色图  member_cutout_trm/after_training.webp', `character/member_cutout_trm/${b}/after_training.webp`],
  ['方形小图标    thumbnail/chara/..._normal.webp', `thumbnail/chara/${b}_normal.webp`],
];
for (const [label, key] of targets) {
  const head = await fetch(BUCKET + key, { method: 'HEAD' });
  console.log(
    `  ${label.padEnd(46)} HTTP ${head.status}  ${head.ok ? kb(Number(head.headers.get('content-length') ?? 0)) : ''}`,
  );
}
