/**
 * 抓 cards.json 的前若干 MB 并做字段统计，用于确定：
 *   - 每种 cardRarityType 的等级数（决定「满级」怎么取）
 *   - param1/param2/param3 在 1 级与满级时的数值
 *   - specialTrainingCosts 是否为空 ⇔ 有特训后卡面
 *   - initialSpecialTrainingStatus 的取值
 *
 * 背景：cards.json 有 35 MB 且 GitHub Pages 只有 ~200 KB/s，全量不现实；
 * 但前 2 MB 已包含数百张卡，足够做统计。截断处按大括号深度切掉半截对象。
 *
 * 用法：node tools/analyze-cards.mjs [bytes]
 */
import { mkdirSync, writeFileSync } from 'node:fs';

const wantBytes = Number(process.argv[2] ?? 2_000_000);
const url = 'https://sekai-world.github.io/sekai-master-db-diff/cards.json';

// 产物统一落在 tools/.cache/（已 gitignore），跑之前不必手动建目录
const outDir = new URL('../.cache/', import.meta.url);
mkdirSync(outDir, { recursive: true });
const outFile = new URL('cards-analysis.txt', outDir);

const t0 = Date.now();
const res = await fetch(url, { headers: { Range: `bytes=0-${wantBytes - 1}` } });
const raw = await res.text();
const elapsed = Date.now() - t0;

// ── 截断安全：找到最后一个「深度回到 1 且刚好闭合一个元素」的位置 ──
function lastCompleteElementEnd(s) {
  let depth = 0;
  let inStr = false;
  let esc = false;
  let lastEnd = -1;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
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
      if (depth === 1) lastEnd = i; // 顶层数组内一个元素刚好闭合
    }
  }
  return lastEnd;
}

const end = lastCompleteElementEnd(raw);
if (end < 0) {
  console.error('没找到完整元素，抓取量可能太小');
  process.exit(1);
}
const firstBracket = raw.indexOf('[');
const jsonText = raw.slice(firstBracket, end + 1) + ']';
const cards = JSON.parse(jsonText);

// ── 统计 ──
const byRarity = new Map();
const statuses = new Set();
const trainingCostLenByRarity = new Map();

for (const c of cards) {
  const r = c.cardRarityType;
  if (!byRarity.has(r)) {
    byRarity.set(r, {
      n: 0,
      levelsByParam: { param1: new Set(), param2: new Set(), param3: new Set() },
      lv1: { param1: new Set(), param2: new Set(), param3: new Set() },
      lvMax: { param1: new Set(), param2: new Set(), param3: new Set() },
      maxLevel: new Set(),
      costEmpty: 0,
      costNonEmpty: 0,
      bonusAllZero: 0,
      bonusNonZero: 0,
    });
  }
  const a = byRarity.get(r);
  a.n++;

  const groups = { param1: [], param2: [], param3: [] };
  for (const p of c.cardParameters ?? []) {
    if (groups[p.cardParameterType]) groups[p.cardParameterType].push(p);
  }
  for (const k of ['param1', 'param2', 'param3']) {
    const arr = groups[k].sort((x, y) => x.cardLevel - y.cardLevel);
    a.levelsByParam[k].add(arr.length);
    if (arr.length) {
      a.lv1[k].add(arr[0].power);
      a.lvMax[k].add(arr[arr.length - 1].power);
      a.maxLevel.add(arr[arr.length - 1].cardLevel);
    }
  }

  const costs = c.specialTrainingCosts ?? [];
  if (costs.length === 0) a.costEmpty++;
  else a.costNonEmpty++;
  if (!trainingCostLenByRarity.has(r)) trainingCostLenByRarity.set(r, new Set());
  trainingCostLenByRarity.get(r).add(costs.length);

  const bonusSum =
    (c.specialTrainingPower1BonusFixed ?? 0) +
    (c.specialTrainingPower2BonusFixed ?? 0) +
    (c.specialTrainingPower3BonusFixed ?? 0);
  if (bonusSum === 0) a.bonusAllZero++;
  else a.bonusNonZero++;

  statuses.add(String(c.initialSpecialTrainingStatus));
}

const lines = [];
lines.push(`url=${url}`);
lines.push(`status=${res.status} rawBytes=${raw.length} cardsParsed=${cards.length} elapsedMs=${elapsed}`);
lines.push(`initialSpecialTrainingStatus 取值=${[...statuses].join(', ')}`);
lines.push('');
lines.push('稀有度 | 张数 | 等级数(param1) | 1级power(p1/p2/p3) | 满级power(p1/p2/p3) | 满级等级 | specialTrainingCosts空/非空 | bonus全0/非0');
lines.push('---|---|---|---|---|---|---|---');

const fmtSet = (s) => [...s].sort((a, b) => a - b).join('/');
for (const [r, a] of [...byRarity.entries()].sort()) {
  lines.push(
    [
      r,
      a.n,
      fmtSet(a.levelsByParam.param1),
      `${fmtSet(a.lv1.param1)}/${fmtSet(a.lv1.param2)}/${fmtSet(a.lv1.param3)}`,
      `${fmtSet(a.lvMax.param1)}/${fmtSet(a.lvMax.param2)}/${fmtSet(a.lvMax.param3)}`,
      fmtSet(a.maxLevel),
      `${a.costEmpty} / ${a.costNonEmpty}`,
      `${a.bonusAllZero} / ${a.bonusNonZero}`,
    ].join(' | '),
  );
}

lines.push('');
lines.push('specialTrainingCosts 长度取值（按稀有度）：');
for (const [r, s] of [...trainingCostLenByRarity.entries()].sort()) {
  lines.push(`  ${r}: ${[...s].sort((a, b) => a - b).join(', ')}`);
}

// 抽一张 3★/4★ 看首尾数据，便于核对「特训前/特训后」
lines.push('');
lines.push('样例卡（各稀有度第一张）的 param1 全序列首尾：');
const seen = new Set();
for (const c of cards) {
  if (seen.has(c.cardRarityType)) continue;
  seen.add(c.cardRarityType);
  const arr = (c.cardParameters ?? [])
    .filter((p) => p.cardParameterType === 'param1')
    .sort((x, y) => x.cardLevel - y.cardLevel);
  lines.push(
    `  id=${c.id} ${c.cardRarityType} levels=${arr.length} ` +
      `levels范围=${arr.length ? arr[0].cardLevel + '..' + arr[arr.length - 1].cardLevel : '-'} ` +
      `首=${arr[0]?.power} 末=${arr[arr.length - 1]?.power} ` +
      `costs=${(c.specialTrainingCosts ?? []).length} status=${c.initialSpecialTrainingStatus}`,
  );
}

writeFileSync(outFile, lines.join('\n'), 'utf8');
console.log(lines.join('\n'));
console.log(`\n-> ${outFile.pathname}`);
