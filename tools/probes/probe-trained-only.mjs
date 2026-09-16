/**
 * 验证「只特训后」的卡（initialSpecialTrainingStatus = "done" 且无特训消耗）
 * 到底有没有未觉醒图 —— 这决定卡面显示逻辑必须是三态而不是两态。
 *
 * 另外把卡牌「类型」的映射关系解出来：
 * cards.json 里没有 cardSupplyType 字段，只有 cardSupplyId（数字），
 * 需要 join cardSupplies.json 才能变成「通常 / 期间限定 / 生日 …」这种可读文本。
 *
 * 用法：node tools/probes/probe-trained-only.mjs
 */
const JP = 'https://sekai-world.github.io/sekai-master-db-diff/';
const BUCKET = 'https://storage.sekai.best/sekai-jp-assets/';

async function head(url) {
  try {
    const r = await fetch(url, { method: 'HEAD' });
    return { status: r.status, len: Number(r.headers.get('content-length') ?? 0) };
  } catch {
    return { status: 'ERR', len: 0 };
  }
}
const kb = (b) => (b >= 1048576 ? `${(b / 1048576).toFixed(2)} MB` : `${(b / 1024).toFixed(1)} KB`);
const when = (ms) => (ms ? new Date(ms).toISOString().slice(0, 10) : '-');

// ── 1. 两类卡的卡面素材到底存在哪些 ──
console.log('═══ 卡面素材存在性对比 ═══');
const CASES = [
  { label: '1378（正常 4★，可特训）', bundle: 'res017_no053' },
  { label: '1462（只特训后的 4★）', bundle: 'res017_no056' },
];
const paths = [
  ['大图 未觉醒  webp', (b) => `character/member/${b}/card_normal.webp`],
  ['大图 觉醒    webp', (b) => `character/member/${b}/card_after_training.webp`],
  ['小图 未觉醒  webp', (b) => `character/member_small/${b}/card_normal.webp`],
  ['小图 觉醒    webp', (b) => `character/member_small/${b}/card_after_training.webp`],
  ['大图 未觉醒  png ', (b) => `character/member/${b}/card_normal.png`],
  ['大图 觉醒    png ', (b) => `character/member/${b}/card_after_training.png`],
];
for (const c of CASES) {
  console.log(`\n  ${c.label}  (${c.bundle})`);
  for (const [label, build] of paths) {
    const h = await head(BUCKET + build(c.bundle));
    console.log(`    ${label}  HTTP ${String(h.status).padEnd(4)} ${h.len ? kb(h.len) : ''}`);
  }
}

// ── 2. 卡牌类型映射：cardSupplyId → cardSupplyType ──
console.log('\n═══ cardSupplies.json（cardSupplyId → 类型名）═══');
const supRes = await fetch(JP + 'cardSupplies.json');
const supplies = await supRes.json();
console.log(`  共 ${supplies.length} 行`);
for (const s of supplies) {
  console.log(`    id=${String(s.id).padStart(2)}  cardSupplyType = ${JSON.stringify(s.cardSupplyType)}`);
}
console.log(`\n  → 1378 的 cardSupplyId=3 对应：${supplies.find((s) => s.id === 3)?.cardSupplyType}`);
console.log(`  → 1462 的 cardSupplyId=7 对应：${supplies.find((s) => s.id === 7)?.cardSupplyType}`);

// ── 3. 时间戳换算成人话 ──
console.log('\n═══ releaseAt 换算 ═══');
console.log(`  1378  releaseAt = ${when(1776481200000)}`);
console.log(`  1462  releaseAt = ${when(1788404400000)}`);
console.log(`  当前时间        = ${new Date().toISOString().slice(0, 10)}`);
