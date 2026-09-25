/**
 * 复验「卡牌 / 卡池标签」的口径与排序依据（2026-09-19 那一轮的实测数字）。
 *
 * 这篇脚本是**复验用**的：那些结论现在钉在 `tools/logic-check` 的纯函数断言
 * 与 `docs/PROJECT_NOTES.md` §11.40 里，但断言用的是构造的小样本；
 * **想拿真实数据再核一遍**时跑这个脚本。
 *
 * 它回答 5 个问题：
 *  ① 卡池类型（常驻 / 生日 / 期间限定 / 联动限定…）有多少类、每类多少张卡？
 *  ② 官方 UP（`gachaPickups`）覆盖多少池？它和池内卡是什么关系？
 *  ③ 「复刻」怎么判？为什么不能按 banner 素材名判？
 *  ④ 池内卡的原始顺序是什么样（这就是「每个池看起来都一样」的根因）？
 *  ⑤ 「本池首发」与「重点卡（UP + 首发）」的规模 —— 为什么值得把其余卡折叠起来？
 *
 * 数据源：**内置快照** `app/src/main/assets/datapack/`（`gachas.json` 里已含
 * `pk` = pickups 的卡 id），所以不需要那 45 MB 的 `tools/datapack/raw/gachas.json`。
 *
 * 用法（在仓库根目录跑）：`node tools/probes/probe-card-gacha-tags.mjs`
 */
import { readFileSync } from "node:fs";

const DP = "app/src/main/assets/datapack/";
const read = (f) => JSON.parse(readFileSync(DP + f, "utf8"));

const cards = read("cards.json");
const supplies = read("cardSupplies.json");
const gachas = read("gachas.json");

const pct = (a, b) => `${((a * 100) / b).toFixed(1)}%`;
const median = (arr) => {
  const s = [...arr].sort((x, y) => x - y);
  return s[Math.floor(s.length / 2)];
};

// ── ① 卡池类型 ──────────────────────────────────────────────────
console.log("① 卡池类型（cardSupplies，官方口径；3~7 算限定，生日不算）");
console.log(`   共 ${supplies.length} 类：${supplies.map((s) => `${s.id}=${s.cardSupplyType}`).join("  ")}`);
const bySupply = {};
for (const c of cards) bySupply[c.cardSupplyId] = (bySupply[c.cardSupplyId] ?? 0) + 1;
for (const s of supplies) {
  console.log(`   #${s.id} ${s.cardSupplyType.padEnd(26)} ${String(bySupply[s.id] ?? 0).padStart(4)} 张`);
}
console.log(`   有类型的卡合计 ${Object.values(bySupply).reduce((a, b) => a + b, 0)} / ${cards.length}`);

// ── ② UP 卡（pickups）───────────────────────────────────────────
console.log("\n② 官方 UP（gachas.pk，来自 gachaPickups 的 cardId）");
const withPk = gachas.filter((g) => Array.isArray(g.pk) && g.pk.length > 0);
console.log(`   有 pickups 的池：${withPk.length} / ${gachas.length}`);
console.log(`   pickups 条数合计：${gachas.reduce((a, g) => a + (g.pk?.length ?? 0), 0)}`);
const notInPool = [];
for (const g of gachas) {
  const pool = new Set(g.gc);
  for (const id of g.pk ?? []) if (!pool.has(id)) notInPool.push(`池 #${g.id} 的卡 ${id}`);
}
console.log(`   pickups 不在池内的脏数据：${notInPool.length} 条（${notInPool.join("；") || "无"}）`);
const notSmaller = gachas.filter((g) => (g.pk?.length ?? 0) > 0 && (g.pk?.length ?? 0) >= g.gc.length);
console.log(`   pickups 不小于整个池的池子：${notSmaller.length}（应为 0，否则 UP 就没有区分度）`);

// ── ③ 复刻怎么判 ────────────────────────────────────────────────
console.log("\n③ 「复刻」的判据");
const rerun = gachas.filter((g) => g.name.includes("復刻"));
const bracket = gachas.filter((g) => g.name.includes("[復刻]"));
const bare = gachas.filter((g) => g.name.includes("復刻") && !g.name.includes("[復刻]"));
console.log(`   名字含「復刻」的池：${rerun.length} / ${gachas.length}`);
console.log(`   其中写成 [復刻] 的：${bracket.length}；其它写法：${bare.length}（应为 0）`);
const byBundle = new Map();
for (const g of gachas) {
  if (!g.bundle) continue;
  if (!byBundle.has(g.bundle)) byBundle.set(g.bundle, []);
  byBundle.get(g.bundle).push(g);
}
const shared = [...byBundle.values()].filter((l) => l.length > 1);
console.log(`   素材名被多个池复用的：${shared.length} 组（实测都是每日票券池/路径池，不是复刻）`);
console.log(
  `   复刻池里素材名只属于自己（也就是按素材名判不出复刻）的：` +
    `${rerun.filter((g) => (byBundle.get(g.bundle) ?? []).length === 1).length} / ${rerun.length}`,
);

// ── ④ 池内原始顺序 ──────────────────────────────────────────────
console.log("\n④ 池内卡的原始顺序（gachas.gc）");
const asc = gachas.filter((g) => g.gc.join() === [...g.gc].sort((a, b) => a - b).join());
console.log(`   顺序就是卡 id 升序的池：${asc.length} / ${gachas.length}  ← 「每个池看起来都一样」的根因`);
console.log(`   池内卡数 中位 ${median(gachas.map((g) => g.gc.length))}，最多 ${Math.max(...gachas.map((g) => g.gc.length))}`);

// ── ⑤ 首发卡与重点卡 ────────────────────────────────────────────
console.log("\n⑤ 本池首发 + 重点卡规模（决定「其余折叠」值不值得）");
const seen = new Set();
const debutByGacha = new Map(); // gachaId -> Set(本池首次出现的卡)
for (const g of [...gachas].sort((a, b) => a.startAt - b.startAt)) {
  const fresh = g.gc.filter((id) => !seen.has(id) && seen.add(id));
  if (fresh.length) debutByGacha.set(g.id, new Set(fresh));
}
const debutTotal = [...debutByGacha.values()].reduce((a, s) => a + s.size, 0);
console.log(`   有首发卡的池：${debutByGacha.size} / ${gachas.length}，首发卡合计 ${debutTotal} 张`);
console.log(
  `   复刻池里没有首发卡的：${rerun.filter((g) => !debutByGacha.has(g.id)).length} / ${rerun.length}` +
    "  ← 定义站得住（复刻池当然不会有首发）",
);
const focusCounts = gachas.map((g) => {
  const union = new Set([...(g.pk ?? []), ...(debutByGacha.get(g.id) ?? [])]);
  return { total: g.gc.length, focus: union.size };
});
console.log(`   重点卡（UP + 首发）中位 ${median(focusCounts.map((f) => f.focus))} 张`);
console.log(`   重点卡 ≤10 张的池：${focusCounts.filter((f) => f.focus <= 10).length} / ${gachas.length}`);
console.log(`   最多的一个池：${Math.max(...focusCounts.map((f) => f.focus))} 张（开服纪念池，全是首发，本来就该全显示）`);
const repeat = focusCounts.reduce((a, f) => a + (f.total - f.focus), 0);
const all = focusCounts.reduce((a, f) => a + f.total, 0);
console.log(`   池内总卡数里属于「往期池已有」的比例：${pct(repeat, all)}  ← 这就是折叠的依据`);
console.log(`   例：池 #4 → 池内 ${focusCounts[3]?.total} 张，重点 ${focusCounts[3]?.focus} 张`);
