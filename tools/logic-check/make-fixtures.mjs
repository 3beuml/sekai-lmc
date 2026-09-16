/**
 * 生成逻辑测试用的真实数据夹具。
 *
 * 为什么不手写假数据：手写的 JSON 会「正好符合我的假设」，测试就成了自证。
 * 这里从远端抓**真实**的 cards.json / skills.json 开头一段，取第一个对象存成夹具，
 * 再用 Kotlin 侧的裁剪器/解析器去跑它。
 *
 * 用法：node tools/logic-check/make-fixtures.mjs
 */
import { mkdirSync, writeFileSync } from 'node:fs';

const outDir = new URL('./fixtures/', import.meta.url);
mkdirSync(outDir, { recursive: true });

/** 取出文本里第一个完整闭合的顶层对象（切片被截断时返回 null）。 */
function firstObjectText(s) {
  const start = s.indexOf('{');
  if (start < 0) return null;
  let depth = 0;
  let inStr = false;
  let esc = false;
  for (let i = start; i < s.length; i++) {
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
      if (depth === 0) return s.slice(start, i + 1);
    }
  }
  return null;
}

async function grab(fileName, base, bytes, outName) {
  const res = await fetch(base + fileName, { headers: { Range: `bytes=0-${bytes - 1}` } });
  const text = await res.text();
  const objText = firstObjectText(text);
  if (!objText) throw new Error(`${fileName}: 在 ${bytes} 字节内没找到完整对象`);
  // 统一压成一行，避免夹具文件里出现无关的空白差异
  const parsed = JSON.parse(objText);
  writeFileSync(new URL(outName, outDir), JSON.stringify(parsed, null, 2), 'utf8');
  console.log(`${fileName} -> ${outName} (${Object.keys(parsed).length} 个字段)`);
  return parsed;
}

// 日服主库
const JP = 'https://sekai-world.github.io/sekai-master-db-diff/';

// cards.json 的第一个对象是 1★ 的 res001_no001（没有特训后卡面），
// 正好用来验证 hasTrained=false 的分支；30 KB 足够包含完整对象。
await grab('cards.json', JP, 30_000, 'card1-raw.json');

// skills.json 很小，直接全抓，取第一个技能（描述里带 {{1;d}} / {{1;v}} 占位符）
await grab('skills.json', JP, 3_000, 'skill1-raw.json');

// cardRarities.json 全表（5 行），用来验证等级上限的读取
const raritiesRes = await fetch(JP + 'cardRarities.json');
const rarities = await raritiesRes.json();
writeFileSync(new URL('cardRarities.json', outDir), JSON.stringify(rarities, null, 2), 'utf8');
console.log(`cardRarities.json -> cardRarities.json (${rarities.length} 行)`);

// gameCharacters.json 很小，取第一个角色（验证 firstName/givenName 的拼接）
await grab('gameCharacters.json', JP, 1_500, 'character1-raw.json');

// ── 没有 id 字段的表：主键选择必须用真实数据验证 ──
// 选错主键的后果是「静默丢行」（写入用 REPLACE，主键重复就覆盖），不报错、不提示。
// 这几张表的完整内容都不大（最大的 cardCostume3ds 约 200 KB），整份存下来做回归测试。
const NO_ID_TABLES = [
  'unitProfiles.json',
  'characterProfiles.json',
  'eventMusics.json',
  'cardExchangeResources.json',
  'cardRarities.json',
  'cardCostume3ds.json',
];
for (const name of NO_ID_TABLES) {
  const res = await fetch(JP + name);
  const rows = await res.json();
  // 不缩进，避免夹具文件白白变大
  writeFileSync(new URL(`noid-${name}`, outDir), JSON.stringify(rows), 'utf8');
  console.log(`${name} -> noid-${name} (${rows.length} 行)`);
}

console.log('\n夹具已写入 tools/logic-check/fixtures/');
