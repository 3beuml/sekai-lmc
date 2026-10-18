import { writeFileSync, mkdirSync } from 'node:fs';
const OUT = 'tools/.cache/binding';
mkdirSync(OUT, { recursive: true });
const PROXIES = [(u) => u, (u) => 'https://gh-proxy.com/' + u, (u) => 'https://ghproxy.net/' + u, (u) => 'https://ghfast.top/' + u];
async function get(url, name) {
  for (const p of PROXIES) {
    try {
      const r = await fetch(p(url), { signal: AbortSignal.timeout(20000) });
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

console.log('=== sekai-viewer tree: find OCR card import ===');
const tree = await get('https://data.jsdelivr.com/v1/packages/gh/Sekai-World/sekai-viewer@dev?structure=flat', 'sekai-viewer-tree.json');
if (tree) {
  try {
    const j = JSON.parse(tree);
    const hits = j.files.map(f => f.name).filter(n => /import|ocr|tesseract/i.test(n));
    console.log(hits.join('\n') || '(no hits)');
  } catch (e) { console.log('parse fail', e.message); }
}

console.log('=== sssekai / twintail ===');
for (const [u, n] of [
  ['https://raw.githubusercontent.com/mos9527/sssekai/main/README.md', 'sssekai-readme.md'],
  ['https://raw.githubusercontent.com/mos9527/sssekai/master/README.md', 'sssekai-readme-master.md'],
  ['https://raw.githubusercontent.com/mos9527/sssekai/main/pyproject.toml', 'sssekai-pyproject.toml'],
  ['https://raw.githubusercontent.com/duosii/twintail/master/README.md', 'twintail-readme.md'],
]) await get(u, n);
