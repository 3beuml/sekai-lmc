// 用真实 SQLite 校验 MasterDao 里的每一条 @Query。
//
// 为什么需要它：Room 会在**编译期**校验这些 SQL（列名、语法），一旦写错就要等一次完整
// 构建才暴露。这个脚本用 Node 内置的 node:sqlite 建出同构表结构并逐条执行，
// 把你改 DAO 后的反馈时间从「一次构建」缩短到「一秒」，不需要 Gradle、不需要 Android SDK。
// 需要 Node 22.5+（内置 node:sqlite）。
// Room 在编译期会校验这些 SQL（列名、语法），本脚本把这一步提前到本地验证。
// 用法：node tools/check-dao-sql.mjs
import { DatabaseSync } from 'node:sqlite';

// ── 1. 按 Entities.kt 生成 Room 会创建的 schema ────────────────────────
// Kotlin String -> TEXT / nullable 时不加 NOT NULL；Int/Long -> INTEGER NOT NULL
const SCHEMA = `
CREATE TABLE IF NOT EXISTS master_rows (
  tableName TEXT NOT NULL,
  id INTEGER NOT NULL,
  name TEXT,
  nameZh TEXT,
  sortValue INTEGER NOT NULL,
  data TEXT NOT NULL,
  PRIMARY KEY (tableName, id)
);
CREATE INDEX IF NOT EXISTS index_master_rows_tableName ON master_rows (tableName);
CREATE INDEX IF NOT EXISTS index_master_rows_tableName_sortValue ON master_rows (tableName, sortValue);
CREATE INDEX IF NOT EXISTS index_master_rows_tableName_name ON master_rows (tableName, name);

CREATE TABLE IF NOT EXISTS sync_state (
  tableName TEXT NOT NULL,
  region TEXT NOT NULL,
  etag TEXT,
  lastModified TEXT,
  rowCount INTEGER NOT NULL,
  updatedAt INTEGER NOT NULL,
  module TEXT NOT NULL,
  PRIMARY KEY (tableName)
);

CREATE TABLE IF NOT EXISTS version_state (
  region TEXT NOT NULL,
  masterVersion TEXT,
  assetVersion TEXT,
  commitDate TEXT,
  checkedAt INTEGER NOT NULL,
  PRIMARY KEY (region)
);
`;

const db = new DatabaseSync(':memory:');
db.exec(SCHEMA);

// 插几行样例，便于验证 UPDATE/SELECT 的行为
db.prepare(
  `INSERT INTO master_rows (tableName, id, name, nameZh, sortValue, data)
   VALUES (?, ?, ?, ?, ?, ?)`
).run('musics.json', 3, 'テオ', null, 1200102, '{"id":3,"title":"テオ"}');
db.prepare(
  `INSERT INTO master_rows (tableName, id, name, nameZh, sortValue, data)
   VALUES (?, ?, ?, ?, ?, ?)`
).run('musics.json', 10, 'ハッピーシンセサイザ', '快乐合成器', 1300001, '{"id":10}');

db.prepare(
  `INSERT INTO sync_state (tableName, region, etag, lastModified, rowCount, updatedAt, module)
   VALUES (?, ?, ?, ?, ?, ?, ?)`
).run('musics.json', 'jp', 'W/"abc"', 'Wed, 01 Jan 2025 00:00:00 GMT', 2, 1735689600000, 'music');

db.prepare(
  `INSERT INTO version_state (region, masterVersion, assetVersion, commitDate, checkedAt)
   VALUES (?, ?, ?, ?, ?)`
).run('jp', '6.7.0.40', '6.7.0.40', '2026-08-17T02:31:53Z', 1735689600000);

// ── 2. MasterDao.kt 里的每一条 @Query（原样抄写，含 :name 参数）────────
const queries = [
  ['updateNameZh', `UPDATE master_rows SET nameZh = :nameZh WHERE tableName = :tableName AND id = :id`,
    { nameZh: '将手', tableName: 'musics.json', id: 3 }, 'run'],
  ['exists', `SELECT COUNT(*) AS c FROM master_rows WHERE tableName = :tableName AND id = :id`,
    { tableName: 'musics.json', id: 3 }, 'get'],
  ['count', `SELECT COUNT(*) AS c FROM master_rows WHERE tableName = :tableName`,
    { tableName: 'musics.json' }, 'get'],
  ['page', `SELECT * FROM master_rows WHERE tableName = :tableName ORDER BY sortValue ASC LIMIT :limit OFFSET :offset`,
    { tableName: 'musics.json', limit: 50, offset: 0 }, 'all'],
  ['search', `SELECT * FROM master_rows
     WHERE tableName = :tableName
       AND (name LIKE '%' || :query || '%' OR nameZh LIKE '%' || :query || '%')
     ORDER BY sortValue ASC LIMIT :limit OFFSET :offset`,
    { tableName: 'musics.json', query: '快乐', limit: 50, offset: 0 }, 'all'],
  ['byId', `SELECT * FROM master_rows WHERE tableName = :tableName AND id = :id LIMIT 1`,
    { tableName: 'musics.json', id: 3 }, 'get'],
  ['byIds(Room 会展开成多个 ?)', `SELECT * FROM master_rows WHERE tableName = :tableName AND id IN (:ids)`,
    { tableName: 'musics.json', ids: 3 }, 'all'],
  ['observeAll', `SELECT * FROM master_rows WHERE tableName = :tableName ORDER BY sortValue ASC`,
    { tableName: 'musics.json' }, 'all'],
  ['observeByIds', `SELECT * FROM master_rows WHERE tableName = :tableName AND id IN (:ids) ORDER BY sortValue ASC`,
    { tableName: 'musics.json', ids: 3 }, 'all'],
  ['deleteTable', `DELETE FROM master_rows WHERE tableName = :tableName`,
    { tableName: '__nope__' }, 'run'],
  ['allIdsBlocking', `SELECT id FROM master_rows WHERE tableName = :tableName`,
    { tableName: 'musics.json' }, 'all'],
  ['sync.byTable', `SELECT * FROM sync_state WHERE tableName = :tableName`,
    { tableName: 'musics.json' }, 'get'],
  ['sync.all', `SELECT * FROM sync_state`, {}, 'all'],
  ['sync.delete', `DELETE FROM sync_state WHERE tableName = :tableName`,
    { tableName: '__nope__' }, 'run'],
  ['sync.clear', `DELETE FROM sync_state`, {}, 'run'],
  ['sync.observeTotalRows', `SELECT COALESCE(SUM(rowCount), 0) AS total FROM sync_state`, {}, 'get'],
  ['ver.byRegion', `SELECT * FROM version_state WHERE region = :region`,
    { region: 'jp' }, 'get'],
  ['ver.observeAll', `SELECT * FROM version_state`, {}, 'all'],
];

// ── 3. 逐条执行 ───────────────────────────────────────────────────────
let ok = 0;
const failures = [];
for (const [name, sql, params, kind] of queries) {
  try {
    const stmt = db.prepare(sql);
    if (kind === 'all') stmt.all(params);
    else if (kind === 'get') stmt.get(params);
    else stmt.run(params);
    ok++;
    console.log(`  [OK]   ${name}`);
  } catch (e) {
    failures.push([name, e.message]);
    console.log(`  [FAIL] ${name}\n         ${e.message}`);
  }
}

// ── 4. 顺带验证业务语义 ───────────────────────────────────────────────
console.log('\n=== 语义验证 ===');
const afterUpdate = db
  .prepare(`SELECT name, nameZh FROM master_rows WHERE tableName = ? AND id = ?`)
  .get('musics.json', 3);
console.log(`  叠加层写入中文名: name=${afterUpdate.name} nameZh=${afterUpdate.nameZh}`);

const searched = db
  .prepare(`SELECT id, nameZh FROM master_rows
            WHERE tableName = ? AND (name LIKE '%' || ? || '%' OR nameZh LIKE '%' || ? || '%')
            ORDER BY sortValue ASC`)
  .all('musics.json', '快乐', '快乐');
console.log(`  按中文名搜索「快乐」命中 ${searched.length} 条 → ${JSON.stringify(searched)}`);

const total = db.prepare(`SELECT COALESCE(SUM(rowCount), 0) AS t FROM sync_state`).get();
console.log(`  同步总行数聚合: ${total.t}`);

console.log(`\n结果：${ok}/${queries.length} 条 SQL 通过`);
if (failures.length) {
  console.log('失败明细：');
  for (const [n, m] of failures) console.log(`  - ${n}: ${m}`);
  process.exitCode = 1;
} else {
  console.log('全部 DAO SQL 在真实 SQLite 上语法与列名均有效。');
}
