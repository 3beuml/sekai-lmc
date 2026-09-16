/**
 * 可行性探测（一轮做完四件事）：
 *  1. 属性图标的官方素材路径（用户要求优化属性图标）
 *  2. 技能效果显示问题的成因（对比日服 / 简中服 skills.json 的占位符写法）
 *  3. 卡牌 1223 是什么（用户要求参考 pjsk.moe/cards/1223）
 *
 * 用法：node tools/probes/probe-feasibility.mjs
 */
import { readFileSync, existsSync } from 'node:fs';

const JP = 'https://sekai-world.github.io/sekai-master-db-diff/';
const BUCKET = 'https://storage.sekai.best/sekai-jp-assets/';

// ── 1. 属性图标 ──────────────────────────────────────────────
console.log('═══ 1. 属性图标素材（common_icon/ 与 thumbnail/common/）═══');
async function list(prefix, max = 24) {
  const url = `${BUCKET}?list-type=2&prefix=${encodeURIComponent(prefix)}&max-keys=${max}`;
  const xml = await (await fetch(url)).text();
  return {
    keys: [...xml.matchAll(/<Key>([^<]+)<\/Key>/g)].map((m) => m[1]),
    prefixes: [...xml.matchAll(/<Prefix>([^<]+)<\/Prefix>/g)]
      .map((m) => m[1])
      .filter((p) => p !== prefix),
  };
}
for (const prefix of ['common_icon/', 'thumbnail/common/', 'thumbnail/common/attr/']) {
  const r = await list(prefix, 16);
  console.log(`  ${prefix}`);
  if (r.prefixes.length) for (const p of r.prefixes) console.log(`    目录 ${p}`);
  for (const k of r.keys.slice(0, 10)) console.log(`    ${k}`);
  if (!r.prefixes.length && !r.keys.length) console.log('    (空)');
}
// 直接验证候选路径
for (const key of [
  'common_icon/attr_icon/cool.webp',
  'thumbnail/common/attr/cool.webp',
  'common_icon/attribute/cool.webp',
]) {
  const h = await fetch(BUCKET + key, { method: 'HEAD' });
  console.log(`  候选 ${key.padEnd(40)} HTTP ${h.status}`);
}

// ── 2. 技能效果：日服 vs 简中服的占位符 ──────────────────────
console.log('\n═══ 2. 技能效果显示问题：对比日服 / 简中服 skills.json ═══');
function firstOf(file) {
  if (!existsSync(file)) return null;
  const text = readFileSync(file, 'utf8');
  const start = text.indexOf('{');
  let depth = 0, inStr = false, esc = false;
  for (let i = start; i < text.length; i++) {
    const c = text[i];
    if (inStr) {
      if (esc) esc = false;
      else if (c === '\\') esc = true;
      else if (c === '"') inStr = false;
      continue;
    }
    if (c === '"') inStr = true;
    else if (c === '{' || c === '[') depth++;
    else if (c === '}' || c === ']') {
      depth--;
      if (depth === 0) {
        try {
          return JSON.parse(text.slice(start, i + 1));
        } catch {
          return null;
        }
      }
    }
  }
  return null;
}
const jpSkill = firstOf('tools/datapack/raw/skills.json');
const cnSkill = firstOf('tools/datapack/cn/skills.json');
console.log('  日服第一条：');
console.log(`    description      = ${JSON.stringify(jpSkill?.description)}`);
console.log(`    shortDescription = ${JSON.stringify(jpSkill?.shortDescription)}`);
console.log('  简中服第一条：');
console.log(`    字段 = ${cnSkill ? Object.keys(cnSkill).join(', ') : '(读不到)'}`);
console.log(`    description      = ${JSON.stringify(cnSkill?.description)}`);
console.log(`    shortDescription = ${JSON.stringify(cnSkill?.shortDescription)}`);
const ph = (s) => [...String(s ?? '').matchAll(/\{\{[^}]*\}\}/g)].map((m) => m[0]);
console.log(`  日服占位符: ${JSON.stringify(ph(jpSkill?.description))}`);
console.log(`  简中占位符: ${JSON.stringify(ph(cnSkill?.description))}`);
const lv = jpSkill?.skillEffects?.[0]?.skillEffectDetails ?? [];
console.log(`  日服第一条的等级档数 = ${lv.length}，等级取值 = ${lv.map((d) => d.level).join(',')}`);

// ── 3. 卡牌 1223 ────────────────────────────────────────────
console.log('\n═══ 3. 卡牌 1223 是什么 ═══');
const TOTAL = 35_064_338, MAX_ID = 1469;
const est = Math.floor((1223 / MAX_ID) * TOTAL);
const res = await fetch(JP + 'cards.json', {
  headers: { Range: `bytes=${Math.max(0, est - 700_000)}-${est + 300_000}` },
});
const text = await res.text();
const cards = [];
{
  const first = text.indexOf('\n  {');
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
    else if (c === '{' || c === '[') {
      if (depth === 0 && c === '{') start = i;
      depth++;
    } else if (c === '}' || c === ']') {
      depth--;
      if (depth === 0 && start >= 0) {
        try { cards.push(JSON.parse(s.slice(start, i + 1))); } catch {}
        start = -1;
      }
      if (depth < 0) break;
    }
  }
}
const c1223 = cards.find((c) => c.id === 1223);
if (!c1223) {
  console.log(`  没抓到（这段是 ${cards[0]?.id}–${cards[cards.length - 1]?.id}）`);
} else {
  console.log(`  ${c1223.cardRarityType} / attr=${c1223.attr} / 角色 ${c1223.characterId} / ${c1223.prefix}`);
  console.log(`  素材名 ${c1223.assetbundleName}  技能id=${c1223.skillId}  supplyId=${c1223.cardSupplyId}`);
  console.log(`  特训消耗=${(c1223.specialTrainingCosts ?? []).length} 条  突破级数=${(c1223.masterLessonAchieveResources ?? []).length}`);
  console.log(`  有 flavorText=${JSON.stringify(c1223.flavorText)}  有 gachaPhrase=${JSON.stringify(c1223.gachaPhrase)}`);
}
