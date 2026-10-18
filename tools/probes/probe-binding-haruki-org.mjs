// Team-Haruki 组织仓库盘点 + suite-api 归属定位
const t = async (u, o = {}) => {
  try {
    const r = await fetch(u, { ...o, headers: { 'User-Agent': 'dsh-research', Accept: 'application/vnd.github+json', ...(o.headers || {}) }, signal: AbortSignal.timeout(25000) });
    const b = await r.text();
    return { status: r.status, body: b };
  } catch (e) { return { status: 0, body: 'ERR ' + e.message }; }
};

console.log('=== Team-Haruki repos ===');
let r = await t('https://api.github.com/orgs/Team-Haruki/repos?per_page=100');
if (r.status === 200) {
  const j = JSON.parse(r.body);
  for (const x of j) console.log(`  ${x.name}  license=${x.license?.spdx_id ?? 'null'}  stars=${x.stargazers_count}  pushed=${x.pushed_at}  ${x.description ?? ''}`);
} else console.log(r.status, r.body.slice(0, 300));

console.log('\n=== 搜 suite-api / toolbox 归属 ===');
for (const q of ['haruki+suite-api', 'haruki+toolbox-api', 'haruki.seiunx.com']) {
  const rr = await t(`https://api.github.com/search/code?q=${q}&per_page=5`);
  console.log(`  q=${q} -> ${rr.status}`);
}
for (const repo of ['Team-Haruki/Haruki-Sekai-API', 'Team-Haruki/Haruki-Toolbox', 'Team-Haruki/haruki-toolbox-api', 'Team-Haruki/Suite-API', 'Team-Haruki/haruki-suite-api']) {
  const rr = await t(`https://api.github.com/repos/${repo}`);
  console.log(`  ${repo} -> ${rr.status} ${rr.status === 200 ? JSON.parse(rr.body).license?.spdx_id : ''}`);
}

console.log('\n=== Haruki-Sekai-API README 里的鉴权/用户库描述关键行 ===');
