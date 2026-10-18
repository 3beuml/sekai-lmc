import { writeFileSync, mkdirSync } from 'node:fs';
const OUT = 'tools/.cache/binding';
mkdirSync(OUT, { recursive: true });
const PROXIES = [(u) => u, (u) => 'https://gh-proxy.com/' + u, (u) => 'https://ghproxy.net/' + u, (u) => 'https://ghfast.top/' + u];
async function get(url, name) {
  for (const p of PROXIES) {
    try {
      const r = await fetch(p(url), { signal: AbortSignal.timeout(25000) });
      if (!r.ok) continue;
      const b = await r.text();
      if (b.includes('404: Not Found') || b.length < 20) continue;
      writeFileSync(`${OUT}/${name}`, b); console.log(`  OK ${b.length}B -> ${name}`);
      return b;
    } catch { }
  }
  console.log(`  FAIL ${name}`);
  return null;
}
console.log('=== sekai-viewer 卡牌 OCR 导入 ===');
for (const [p, n] of [
  ['/src/pages/user/import/ImportCard.tsx', 'sekai-import-card.tsx'],
  ['/src/pages/user/import/ImportCardList.tsx', 'sekai-import-cardlist.tsx'],
  ['/package.json', 'sekai-viewer-package.json'],
]) await get('https://raw.githubusercontent.com/Sekai-World/sekai-viewer/dev' + p, n);

console.log('\n=== sssekai（本地解密 Suite）===');
for (const [u, n] of [
  ['https://raw.githubusercontent.com/mos9527/sssekai/main/README.md', 'sssekai-readme.md'],
  ['https://raw.githubusercontent.com/mos9527/sssekai/master/README.md', 'sssekai-readme2.md'],
  ['https://raw.githubusercontent.com/mos9527/sssekai/main/LICENSE', 'sssekai-LICENSE.txt'],
  ['https://raw.githubusercontent.com/mos9527/sssekai/main/pyproject.toml', 'sssekai-pyproject.toml'],
]) await get(u, n);
