/**
 * 检查「没有 id 字段」的那几张表该拿哪个字段当主键。
 *
 * 为什么必须实测：这些表会被导入 `master_rows`，主键是 `(tableName, id)`，
 * 写入用 `OnConflictStrategy.REPLACE`。所以**一旦候选字段有重复值，
 * 重复的那几行会被静默覆盖掉** —— 不报错、不提示，只是数据少了几行。
 * 选主键这种事只能靠真实数据验证，不能靠字段名猜。
 *
 * 用法：node tools/check-keys.mjs
 */
const JP = 'https://sekai-world.github.io/sekai-master-db-diff/';

/** 表名 → 候选主键字段（按优先级）。字符串字段也会一起统计唯一性，但只有数值字段能当主键。 */
const TARGETS = {
  'unitProfiles.json': ['seq', 'unit'],
  'characterProfiles.json': ['characterId', 'scenarioId'],
  'eventMusics.json': ['seq', 'musicId', 'eventId'],
  'cardExchangeResources.json': ['seq', 'resourceBoxId'],
  'cardRarities.json': ['seq', 'cardRarityType'],
  'cardCostume3ds.json': ['costume3dId', 'cardId'],
};

const lines = [];
const probe = async (file, keys) => {
  const res = await fetch(JP + file);
  if (!res.ok) {
    lines.push(`${file}  抓取失败 status=${res.status}`);
    return;
  }
  const rows = await res.json();
  lines.push(`${file}  共 ${rows.length} 行`);

  for (const key of keys) {
    const present = rows.filter((r) => r[key] !== undefined);
    if (present.length === 0) {
      lines.push(`    ${key.padEnd(16)} 不存在这个字段`);
      continue;
    }
    const values = rows.map((r) => r[key]);
    const missing = rows.length - present.length;
    const unique = new Set(values).size;
    const dup = values.length - unique;
    const numeric = values.every((v) => typeof v === 'number');
    const kind = numeric ? '数值' : '非数值(不能当主键)';
    const verdict = dup === 0 && missing === 0 ? '✅ 唯一，可当主键' : `❌ 重复 ${dup} 个 / 缺失 ${missing} 个`;
    lines.push(`    ${key.padEnd(16)} 唯一值 ${String(unique).padStart(6)}  ${kind.padEnd(22)} ${verdict}`);
    if (dup > 0 && dup <= 5) {
      const counts = new Map();
      for (const v of values) counts.set(v, (counts.get(v) ?? 0) + 1);
      const dups = [...counts.entries()].filter(([, c]) => c > 1);
      lines.push(`      重复样例：${dups.slice(0, 5).map(([v, c]) => `${v}×${c}`).join(', ')}`);
    }
  }
  lines.push('');
};

for (const [file, keys] of Object.entries(TARGETS)) {
  await probe(file, keys);
}

console.log(lines.join('\n'));
