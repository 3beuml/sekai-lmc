import { writeFileSync, mkdirSync } from 'node:fs';
const OUT = 'tools/.cache/binding';
mkdirSync(OUT, { recursive: true });
const PROXIES = [(u) => u, (u) => 'https://gh-proxy.com/' + u, (u) => 'https://ghproxy.net/' + u];
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
const B = 'https://raw.githubusercontent.com/Sekai-World/sekai-viewer/dev/src/pages/user/sekai_profile/';
await get(B + 'SekaiUserImportMember.tsx', 'sekai-import-member.tsx');
await get(B + 'SekaiID.tsx', 'sekai-id.tsx');

console.log('=== pypi sssekai raw ===');
try {
  const r = await fetch('https://pypi.org/pypi/sssekai/json', { signal: AbortSignal.timeout(20000) });
  console.log('status', r.status);
  const t = await r.text();
  console.log(t.slice(0, 600));
} catch (e) { console.log('ERR', e.message); }
