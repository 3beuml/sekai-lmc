// 生成「Best30」要用的谱面定数表（内置进 APK 的一个很小的人造文件）。
//
// 用法：node tools/pjskb30/build-asset.mjs
//
// ## 数据来源与许可
// 上游：https://moe.exmeaning.com/data/pjskb30/merged_chart.csv
//   —— 由 auburnsummer/pjskb30 汇总，许可 **CC0-1.0**（公有领域贡献）。
//   CC0 允许任意使用与再分发，所以可以把它做成本项目的内置资源。
// 官方 master data 里**没有**谱面定数（`musicDifficulties` 只有 playLevel 这样的整数等级），
// 所以定数只能来自社区汇总，这是本项目里唯一一份非官方数据源。
//
// ## 这次做了什么变换
//  1. 只保留 **Expert / Master / Append** 三档：上游也只收录这三档；
//     Easy / Normal / Hard 没有定数，因此永远不进 Best30 的池子。
//  2. 丢掉没有 Song ID 的行（上游对 Hard 之类明确标了 "hard not supported"）。
//  3. 丢掉定数解析不出来的行（实测 1 行：id=793 的 Master）。
//  4. **同一 (Song ID, 难度) 有多行时取最大值**。上游把「25+」这类记法在部分行写成
//     `25`、部分行写成 `25.9`（实测 185 组冲突，差值 0.1~1.0）。取大的那一个，
//     因为 `+` 的本义就是"高于该整数"，取小的会系统性低估。
//  5. 输出 `musicId,difficulty,constant` 三列，按 id 升序、难度按 expert/master/append。
//
// 生成结果约 25 KB / 1721 行，随 APK 分发，运行时从 assets 读取。

import { writeFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const SOURCE = "https://moe.exmeaning.com/data/pjskb30/merged_chart.csv";
const OUT = resolve("app/src/main/assets/pjskb30/chart-constants.csv");
const DIFFICULTIES = new Set(["expert", "master", "append"]);

/** RFC4180 风格的 CSV 解析：上游有 `"Help me, ERINNNNNN!!"` 这种带逗号的引号字段。 */
function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = "";
  let inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        field += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ",") {
      row.push(field);
      field = "";
    } else if (ch === "\n") {
      row.push(field);
      field = "";
      rows.push(row);
      row = [];
    } else if (ch !== "\r") {
      field += ch;
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

const response = await fetch(SOURCE);
if (!response.ok) throw new Error(`下载失败：HTTP ${response.status}`);
const text = Buffer.from(await response.arrayBuffer()).toString("utf8");

const rows = parseCsv(text);
const header = rows.shift();
if (!header || header.length < 8 || header[2].trim() !== "Constant" || header[6].trim() !== "Song ID") {
  throw new Error(`表头与预期不符，上游可能改结构了：${JSON.stringify(header)}`);
}

/** key = `${id}|${difficulty}` → 定数 */
const constants = new Map();
let kept = 0;
let droppedNoId = 0;
let droppedBadConstant = 0;
let droppedBadDifficulty = 0;

for (const cells of rows) {
  const id = (cells[6] ?? "").trim();
  const difficulty = (cells[5] ?? "").trim().toLowerCase();
  const constant = Number.parseFloat((cells[2] ?? "").trim());
  if (id === "") {
    droppedNoId++;
    continue;
  }
  if (!/^\d+$/.test(id) || !DIFFICULTIES.has(difficulty)) {
    droppedBadDifficulty++;
    continue;
  }
  if (!Number.isFinite(constant) || constant <= 0) {
    droppedBadConstant++;
    continue;
  }
  const key = `${id}|${difficulty}`;
  const previous = constants.get(key);
  // 取较大值：见文件头第 4 条
  if (previous === undefined || constant > previous) constants.set(key, constant);
  kept++;
}

if (constants.size < 1500) {
  throw new Error(`只解析出 ${constants.size} 个定数，明显不对（预期 1700+），拒绝写入`);
}

const order = ["expert", "master", "append"];
const entries = [...constants.entries()]
  .map(([key, constant]) => {
    const [id, difficulty] = key.split("|");
    return { id: Number.parseInt(id, 10), difficulty, constant };
  })
  .sort((a, b) => a.id - b.id || order.indexOf(a.difficulty) - order.indexOf(b.difficulty));

const lines = [
  "# pjskb30 谱面定数表（内置资源，运行时由 ChartConstants 读取）",
  "# 来源：https://moe.exmeaning.com/data/pjskb30/merged_chart.csv",
  "# 上游项目：github.com/auburnsummer/pjskb30 · 许可：CC0-1.0（公有领域）",
  "# 官方 master data 不含定数；这是本项目唯一的非官方数据源",
  "# 格式：musicId,difficulty,constant（同一谱面多行时上文取最大值）",
  "musicId,difficulty,constant",
];
for (const entry of entries) {
  lines.push(`${entry.id},${entry.difficulty},${entry.constant}`);
}
const output = lines.join("\n") + "\n";

await mkdir(dirname(OUT), { recursive: true });
await writeFile(OUT, output, "utf8");

console.log(`上游行数 ${rows.length}`);
console.log(`  丢：没有 Song ID     ${droppedNoId}`);
console.log(`  丢：难度/ID 不合法   ${droppedBadDifficulty}`);
console.log(`  丢：定数非数字       ${droppedBadConstant}`);
console.log(`有效原始行 ${kept} → 去重后 ${entries.length} 个谱面`);
console.log(`写入 ${OUT}（${Buffer.byteLength(output)} 字节）`);
