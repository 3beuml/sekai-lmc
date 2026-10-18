// 盘点 Moesekai 组卡/账号相关文件 + 抓 Haruki README
import { writeFileSync, mkdirSync } from 'node:fs';
const OUT = 'tools/.cache/binding';
mkdirSync(OUT, { recursive: true });

const PROXIES = [(u) => u, (u) => 'https://gh-proxy.com/' + u, (u) => 'https://ghproxy.net/' + u, (u) => 'https://ghfast.top/' + u];

async function get(url, name) {
  for (const p of PROXIES) {
    const u = p(url);
    try {
      const r = await fetch(u, { signal: AbortSignal.timeout(30000) });
      if (!r.ok) continue;
      const b = await r.text();
      if (b.includes('404: Not Found') || b.length < 20) continue;
      if (name) { writeFileSync(`${OUT}/${name}`, b); console.log(`  OK ${b.length}B -> ${name}`); }
      return b;
    } catch { /* next */ }
  }
  console.log(`  FAIL ${name || url}`);
  return null;
}

console.log('=== jsDelivr 文件树（Moesekai web/src/lib + hooks 中的 account/deck 相关）===');
const tree = await get('https://data.jsdelivr.com/v1/packages/gh/StarMoe-org/Moesekai@main?structure=flat', 'moesekai-tree.json');
if (tree) {
  try {
    const j = JSON.parse(tree);
    const hits = (j.files || []).map(f => f.name).filter(n => /account|oauth|deck|haruki|profile|user/i.test(n) && n.startsWith('/web/src'));
    console.log(hits.join('\n'));
  } catch (e) { console.log('parse fail', e.message, tree.slice(0, 200)); }
}

console.log('\n=== Haruki-Sekai-API README / config ===');
await get('https://raw.githubusercontent.com/Team-Haruki/Haruki-Sekai-API/main/README.md', 'haruki-readme.md');
await get('https://raw.githubusercontent.com/Team-Haruki/Haruki-Sekai-API/main/config.toml', 'haruki-config.toml');
await get('https://raw.githubusercontent.com/Team-Haruki/Haruki-Sekai-API/main/.env.example', 'haruki-env.example');
await get('https://raw.githubusercontent.com/Team-Haruki/Haruki-Sekai-API/main/src/api/apis.rs', 'haruki-apis.rs');
await get('https://raw.githubusercontent.com/Team-Haruki/Haruki-Sekai-API/main/src/client/account.rs', 'haruki-account.rs');

console.log('\n=== sssekai / twintail （本地解密路线）===');
for (const [repo, name] of [
  ['mos9527/sssekai/main/README.md', 'sssekai-readme.md'],
  ['mos9527/sssekai/main/LICENSE', 'sssekai-LICENSE.txt'],
  ['duosii/twintail/main/README.md', 'twintail-readme.md'],
  ['Xfl03/sssekai/main/README.md', 'x-sssekai.md'],
]) await get(`https://raw.githubusercontent.com/${repo}`, name);
