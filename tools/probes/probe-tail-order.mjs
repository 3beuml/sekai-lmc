/**
 * 验证「增量更新」方案的核心前提：巨表是否按 id 升序追加？
 *
 * 如果成立，「最新内容」全在文件末尾，可以用 HTTP Range 只取尾部几百 KB，
 * 不必每次重下 35 MB —— 这是「打开就能很快看到最新」的技术基础。
 *
 * 踩过的两个坑（都写在这里，免得下次再犯）：
 *  1. 用正则 `"id"\s*:\s*(\d+)` 抓 id 会把**嵌套对象**的 id 也算进去
 *     （cards.json 的 `cardParameters[].id` 高达 1469 万，events.json 的
 *     `eventRankingRewardRanges[].id` 是四位数），结论完全失真。
 *  2. 自己写状态机切「顶层对象」时，如果片段是从文件**中间**切的，
 *     起始括号深度是未知的；而文件以 `[` 开头又会让深度整体偏一级。
 *     解决办法：从片段里第一个 `\n  {` 开始扫 —— 顶层对象是两空格缩进，
 *     更深的嵌套对象缩进更多，所以这个标记唯一。
 *
 * 用法：node tools/probes/probe-tail-order.mjs
 */
const JP = 'https://sekai-world.github.io/sekai-master-db-diff/';

/** 从（可能是半截的）JSON 数组文本里切出所有完整的顶层对象。 */
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
      if (depth < 0) break; // 顶层数组结束
    }
  }
  return out;
}

async function range(file, rangeHeader) {
  const res = await fetch(JP + file, { headers: { Range: rangeHeader } });
  const text = await res.text();
  const cr = res.headers.get('content-range') ?? '';
  return { status: res.status, text, total: Number(cr.split('/')[1] ?? 0) };
}

function brief(nums) {
  if (nums.length === 0) return '(无)';
  if (nums.length <= 6) return nums.join(', ');
  return `${nums.slice(0, 3).join(', ')} … ${nums.slice(-3).join(', ')}`;
}

const FILES = [
  { file: 'cards.json', extra: ['assetbundleName'] },
  { file: 'musics.json', extra: ['title'] },
  { file: 'events.json', extra: ['name', 'startAt', 'closedAt'] },
  { file: 'gachas.json', extra: ['name', 'assetbundleName', 'startAt', 'endAt'] },
];

const summary = [];
for (const f of FILES) {
  console.log(`\n═══ ${f.file} ═══`);
  const head = await range(f.file, 'bytes=0-4000');
  const total = head.total || head.text.length;
  const midAt = Math.floor(total / 2);
  const mid = await range(f.file, `bytes=${midAt}-${midAt + 8000}`);

  // 尾部：逐步扩大，直到切出至少 3 个完整对象
  let tail = { objs: [], raw: 0 };
  for (const bytes of [16384, 65536, 262144, 1048576]) {
    const r = await range(f.file, `bytes=-${bytes}`);
    const objs = topLevelObjects(r.text);
    tail = { objs, raw: bytes, got: r.text.length };
    if (objs.length >= 3) break;
  }

  const headObjs = topLevelObjects(head.text);
  const midObjs = topLevelObjects(mid.text);
  console.log(`  文件总大小 ${total.toLocaleString()} 字节`);
  console.log(`  开头片段 id: ${brief(headObjs.map((o) => o.id))}   (完整对象 ${headObjs.length} 个)`);
  console.log(`  中间片段 id: ${brief(midObjs.map((o) => o.id))}   (完整对象 ${midObjs.length} 个)`);
  console.log(
    `  末尾片段 id: ${brief(tail.objs.map((o) => o.id))}   ` +
      `(完整对象 ${tail.objs.length} 个，取尾部 ${tail.raw / 1024} KB)`,
  );

  const last = tail.objs[tail.objs.length - 1];
  if (last) {
    const bits = f.extra
      .map((k) => {
        const v = last[k];
        return `${k}=${typeof v === 'string' ? `"${v.slice(0, 34)}"` : v}`;
      })
      .join('  ');
    console.log(`  末尾对象: id=${last.id}  ${bits}`);
  }
  summary.push({
    file: f.file,
    headMax: Math.max(...headObjs.map((o) => o.id ?? 0), 0),
    midMax: Math.max(...midObjs.map((o) => o.id ?? 0), 0),
    tailIds: tail.objs.map((o) => o.id).filter((v) => typeof v === 'number'),
  });
}

console.log('\n═══ 判定：是否升序追加（尾部取法是否可用）═══');
for (const s of summary) {
  const tailMax = s.tailIds.length ? Math.max(...s.tailIds) : -1;
  const tailMin = s.tailIds.length ? Math.min(...s.tailIds) : -1;
  const ordered = s.tailIds.length >= 3 && tailMin > s.midMax && tailMax > s.headMax;
  console.log(
    `  ${s.file.padEnd(14)} 开头max=${String(s.headMax).padStart(5)}  中间max=${String(s.midMax).padStart(5)}` +
      `  末尾 min=${String(tailMin).padStart(5)} max=${String(tailMax).padStart(5)}` +
      `   ${ordered ? '✅ 升序追加，尾部取法可用' : '❌ 顺序不符'}`,
  );
}
