/**
 * 审计 MasterCatalog 里全部表的「schema 假设」是否成立。
 *
 * 动机：导入器靠 `TableSchema` 里的字段名约定取主键和显示名。如果某张表实际上
 * 没有 `id` 字段，那张表会被**静默丢弃**（导入 0 行，UI 只显示空表），极难排查。
 * 这个脚本把每张表的第一个对象抓回来（只取前 600 字节，用 Range），
 * 报告它到底有哪些顶层字段，从而暴露所有对不上的假设。
 *
 * 用法：node tools/audit-tables.mjs [region]
 *   region: jp（默认）/ en / cn / kr / tc
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const region = process.argv[2] ?? 'jp';
const REPO = {
  jp: 'sekai-master-db-diff',
  en: 'sekai-master-db-en-diff',
  cn: 'sekai-master-db-cn-diff',
  kr: 'sekai-master-db-kr-diff',
  tc: 'sekai-master-db-tc-diff',
}[region];
if (!REPO) throw new Error(`未知 region: ${region}`);
const BASE = `https://sekai-world.github.io/${REPO}/`;

const catalogPath = new URL(
  '../app/src/main/java/com/pjsk/toolbox/data/remote/MasterCatalog.kt',
  import.meta.url,
);
const catalogSrc = readFileSync(catalogPath, 'utf8');

// TableSpec("xxx.json", DataModule.YYY, 123L, cnOverlay = true)
const specRe = /TableSpec\(\s*"([^"]+)"\s*,\s*DataModule\.(\w+)\s*,\s*([0-9_]+)L([^)]*)\)/g;
const specs = [];
let m;
while ((m = specRe.exec(catalogSrc)) !== null) {
  specs.push({
    file: m[1],
    module: m[2],
    bytes: Number(m[3].replace(/_/g, '')),
    cnOverlay: /cnOverlay\s*=\s*true/.test(m[4]),
  });
}

/** 从文本中取出第一个顶层对象的完整文本（截断安全）。 */
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

/** 只取顶层字段名（忽略嵌套对象的字段）。 */
function topLevelKeys(objText) {
  const keys = [];
  let depth = 0;
  let inStr = false;
  let esc = false;
  let buf = '';
  let lastKeyCandidate = null;
  for (let i = 0; i < objText.length; i++) {
    const c = objText[i];
    if (inStr) {
      if (esc) buf += c, (esc = false);
      else if (c === '\\') buf += c, (esc = true);
      else if (c === '"') {
        inStr = false;
        if (depth === 1) lastKeyCandidate = buf;
      } else buf += c;
      continue;
    }
    if (c === '"') {
      inStr = true;
      buf = '';
    } else if (c === '{' || c === '[') {
      depth++;
      if (depth === 2 && lastKeyCandidate !== null) {
        lastKeyCandidate = null;
      }
    } else if (c === '}' || c === ']') {
      depth--;
    } else if (c === ':' && depth === 1 && lastKeyCandidate !== null) {
      keys.push(lastKeyCandidate);
      lastKeyCandidate = null;
    }
  }
  return keys;
}

async function fetchHead(file, bytes = 600) {
  const res = await fetch(BASE + file, { headers: { Range: `bytes=0-${bytes - 1}` } });
  if (res.status !== 206 && res.status !== 200) {
    return { status: res.status, text: await res.text().catch(() => '') };
  }
  const text = await res.text();
  return { status: res.status, text };
}

const results = [];
const CONCURRENCY = 8;
for (let i = 0; i < specs.length; i += CONCURRENCY) {
  const slice = specs.slice(i, i + CONCURRENCY);
  const done = await Promise.all(
    slice.map(async (s) => {
      try {
        let text = '';
        let status = 0;
        let objText = null;
        let usedBytes = 0;
        // 首个对象可能比切片大得多（cards.json 一张卡就有 24 KB），所以逐级加大重试。
        for (const bytes of [600, 8000, 60000]) {
          usedBytes = bytes;
          const r = await fetchHead(s.file, bytes);
          status = r.status;
          text = r.text;
          if (/^\[\s*\]/.test(text.trim())) {
            return { ...s, status, keys: [], empty: true, note: '空表（远端就是 []）' };
          }
          objText = firstObjectText(text);
          if (objText !== null) break;
        }
        if (objText === null) {
          return {
            ...s,
            status,
            keys: [],
            note: `首个对象超过 ${usedBytes} 字节仍未闭合；开头=${text.slice(0, 60).replace(/\s+/g, ' ')}`,
          };
        }
        let keys;
        try {
          keys = Object.keys(JSON.parse(objText));
        } catch {
          keys = topLevelKeys(objText); // 截断导致 JSON 不完整时退回手工扫描
        }
        return { ...s, status, keys, note: '' };
      } catch (e) {
        return { ...s, status: 'ERR', keys: [], note: String(e).slice(0, 60) };
      }
    }),
  );
  results.push(...done);
  process.stdout.write(`\r已检查 ${results.length}/${specs.length} 张表…`);
}
process.stdout.write('\n');

const NAME_KEYS = ['name', 'title', 'prefix'];
const lines = [];
lines.push(`region=${region} repo=${REPO} 表数=${specs.length}`);
lines.push('');

const noId = results.filter((r) => r.status !== 'ERR' && r.keys.length && !r.keys.includes('id'));
const noName = results.filter(
  (r) => r.status !== 'ERR' && r.keys.length && !NAME_KEYS.some((k) => r.keys.includes(k)),
);
const emptyOrErr = results.filter((r) => r.status === 'ERR' || !r.keys.length);
const emptyTables = results.filter((r) => r.empty);

lines.push(`【A. 没有 id 字段的表（会被静默丢弃，共 ${noId.length} 张）】`);
for (const r of noId) {
  lines.push(`  ${r.file}  module=${r.module}  bytes=${r.bytes}  顶层字段=${r.keys.join(', ')}`);
}
lines.push('');
lines.push(`【B. 没有 name/title/prefix 的表（列表里没有显示名，共 ${noName.length} 张）】`);
for (const r of noName) {
  lines.push(`  ${r.file}  module=${r.module}  顶层字段=${r.keys.join(', ')}`);
}
lines.push('');
lines.push(`【C. 没读到顶层字段的表（共 ${emptyOrErr.length} 张）】`);
lines.push('  注意：如果 note 里写着「首个对象超过 N 字节仍未闭合」，那是脚本切片不够，不是表坏了。');
for (const r of emptyOrErr) {
  lines.push(`  ${r.file}  status=${r.status}  ${r.note}`);
}
lines.push('');
lines.push(`【E. 远端就是空数组的表（共 ${emptyTables.length} 张）】`);
for (const r of emptyTables) lines.push(`  ${r.file}  module=${r.module}  bytes=${r.bytes}`);
lines.push('');
lines.push('【D. 全部表一览】');
lines.push('  file | module | bytes | status | 顶层字段');
for (const r of results) {
  lines.push(`  ${r.file} | ${r.module} | ${r.bytes} | ${r.status} | ${r.keys.join(', ')}`);
}

// 产物统一落在 tools/.cache/（已 gitignore）
const outDir = new URL('../.cache/', import.meta.url);
mkdirSync(outDir, { recursive: true });
const outFile = new URL(`table-audit-${region}.txt`, outDir);
writeFileSync(outFile, lines.join('\n'), 'utf8');
console.log(lines.slice(0, lines.indexOf('') + 1).join('\n'));
console.log(`A=${noId.length} B=${noName.length} C=${emptyOrErr.length}`);
console.log(`-> ${outFile.pathname}`);
