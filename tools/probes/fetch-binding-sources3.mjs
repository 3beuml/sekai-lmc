import { writeFileSync, mkdirSync } from 'node:fs';
const OUT = 'tools/.cache/binding';
mkdirSync(OUT, { recursive: true });
const PROXIES = [(u) => u, (u) => 'https://gh-proxy.com/' + u, (u) => 'https://ghproxy.net/' + u];
async function get(url, name) {
  for (const p of PROXIES) {
    const u = p(url);
    try {
      const r = await fetch(u, { signal: AbortSignal.timeout(25000) });
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
const R = 'https://raw.githubusercontent.com/StarMoe-org/Moesekai/main';
for (const [p, n] of [
  ['/web/src/lib/deck-recommend/data-provider.ts', 'moesekai-deck-data-provider.ts'],
  ['/web/src/app/deck-recommend/client.tsx', 'moesekai-deck-recommend-client.tsx'],
  ['/web/src/app/oauth2/connect/client.tsx', 'moesekai-oauth-connect.tsx'],
  ['/web/src/app/oauth2/callback/code/client.tsx', 'moesekai-oauth-callback.tsx'],
]) await get(R + p, n);
