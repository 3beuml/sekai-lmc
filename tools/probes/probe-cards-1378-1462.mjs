/**
 * 把 sekai.best/card/1378 与 /card/1462 对应的**真实卡牌数据**抓出来，
 * 用来确定「卡牌详情页」到底要展示哪些字段、这两张卡各属于什么类型。
 *
 * 手段：cards.json 是按 id 升序的（已实测），所以从文件尾部取一段就能拿到这两张卡。
 * 尾部的解析用已验证过的「从 \n  { 开始扫顶层对象」的办法。
 *
 * 用法：node tools/probes/probe-cards-1378-1462.mjs
 */
const JP = 'https://sekai-world.github.io/sekai-master-db-diff/';
const WANT = [1378, 1462];

function topLevelObjects(text) {
  const out = [];
  const first = text.indexOf('\n  {');
  if (first < 0) return out;
  const s = text.slice(first + 1);
  let depth = 0, inStr = false, esc = false, start = -1;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (inStr) {
      if (esc) esc = false;
      else if (c === '\\') esc = true;
      else if (c === '"') inStr = false;
      continue;
    }
    if (c === '"') inStr = true;
    else if (c === '{' || c === '[') { if (depth === 0 && c === '{') start = i; depth++; }
    else if (c === '}' || c === ']') {
      depth--;
      if (depth === 0 && start >= 0) {
        try { out.push(JSON.parse(s.slice(start, i + 1))); } catch { /* 半截跳过 */ }
        start = -1;
      }
      if (depth < 0) break;
    }
  }
  return out;
}

// 取足够大的尾部：卡平均约 24 KB，最大 id 1469，要覆盖到 1378 需要往回约 91 张 ≈ 2.2 MB
const res = await fetch(JP + 'cards.json', { headers: { Range: 'bytes=-3145728' } });
const text = await res.text();
console.log(`尾部抓取 HTTP ${res.status}  ${text.length} 字符  ${res.headers.get('content-range')}`);

const cards = topLevelObjects(text);
console.log(`切出完整对象 ${cards.length} 个，id 范围 ${cards[0]?.id} … ${cards[cards.length - 1]?.id}\n`);

/** 汇总 cardParameters 的等级分布（用来确认等级上限与 param 类型）。 */
function paramSummary(card) {
  const byType = new Map();
  for (const p of card.cardParameters ?? []) {
    const t = p.cardParameterType;
    const arr = byType.get(t) ?? [];
    arr.push(p);
    byType.set(t, arr);
  }
  const out = [];
  for (const [t, arr] of byType) {
    arr.sort((a, b) => a.cardLevel - b.cardLevel);
    out.push(`${t}: ${arr.length} 档 (${arr[0].cardLevel}→${arr[arr.length - 1].cardLevel}级, ${arr[0].power}→${arr[arr.length - 1].power})`);
  }
  return out;
}

for (const id of WANT) {
  const card = cards.find((c) => c.id === id);
  console.log('═'.repeat(70));
  if (!card) {
    console.log(`id=${id} 不在这段尾部里（可能需要取更长的尾部）`);
    continue;
  }
  console.log(`卡牌 id=${id}`);
  const skip = new Set(['cardParameters']);
  for (const [k, v] of Object.entries(card)) {
    if (skip.has(k)) continue;
    const val = Array.isArray(v)
      ? `数组(${v.length}) ${JSON.stringify(v).slice(0, 110)}`
      : typeof v === 'object' && v !== null
        ? `对象 ${JSON.stringify(v).slice(0, 110)}`
        : JSON.stringify(v);
    console.log(`  ${k.padEnd(36)} = ${String(val).slice(0, 150)}`);
  }
  console.log(`  cardParameters 等级分布：`);
  for (const line of paramSummary(card)) console.log(`      ${line}`);
  console.log('');
}

console.log('═'.repeat(70));
console.log('两张卡的稀有度/类型对比：');
for (const id of WANT) {
  const c = cards.find((x) => x.id === id);
  if (!c) continue;
  console.log(
    `  id=${id}  ${c.cardRarityType}  attr=${c.attr}  角色=${c.characterId}  ` +
      `特训消耗=${(c.specialTrainingCosts ?? []).length}条  ` +
      `supplyType=${c.cardSupplyType ?? '(无此字段)'}  gachaPhrase=${JSON.stringify(c.gachaPhrase)}`,
  );
  console.log(`      prefix = ${c.prefix}`);
  console.log(`      技能名 = ${c.cardSkillName} (skillId=${c.skillId})  特训后技能=${c.specialTrainingSkillId ?? '(无)'}`);
}
