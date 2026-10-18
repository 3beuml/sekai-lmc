// 抓 Moesekai 与前序仓库的关键源文件（走 gh-proxy 通道），落盘到 tools/.cache/binding/
import { writeFileSync, mkdirSync } from 'node:fs';
const OUT = 'tools/.cache/binding';
mkdirSync(OUT, { recursive: true });

const PROXIES = [
  (u) => u,
  (u) => 'https://gh-proxy.com/' + u,
  (u) => 'https://ghproxy.net/' + u,
  (u) => 'https://ghfast.top/' + u,
];

async function get(url, name) {
  for (const p of PROXIES) {
    const u = p(url);
    try {
      const r = await fetch(u, { signal: AbortSignal.timeout(30000) });
      if (!r.ok) { console.log(`  x ${r.status} ${u}`); continue; }
      const b = await r.text();
      if (b.includes('404: Not Found') || b.length < 20) { console.log(`  x bad-body ${u}`); continue; }
      writeFileSync(`${OUT}/${name}`, b);
      console.log(`  OK ${b.length}B -> ${name}  (via ${new URL(u).host})`);
      return b;
    } catch (e) { console.log(`  x ${e.message} ${u}`); }
  }
  console.log(`  FAIL ${name}`);
  return null;
}

const RAW = 'https://raw.githubusercontent.com';
const files = [
  // Moesekai
  ['StarMoe-org/Moesekai/main/web/src/lib/oauth.ts', 'moesekai-oauth.ts'],
  ['StarMoe-org/Moesekai/main/web/src/lib/haruki-public-api.ts', 'moesekai-haruki-public-api.ts'],
  ['StarMoe-org/Moesekai/main/web/src/lib/account.ts', 'moesekai-account.ts'],
  ['StarMoe-org/Moesekai/main/web/src/lib/account-servers.ts', 'moesekai-account-servers.ts'],
  ['StarMoe-org/Moesekai/main/web/src/app/profile/page.tsx', 'moesekai-profile-page.tsx'],
  ['StarMoe-org/Moesekai/main/web/src/app/profile/client.tsx', 'moesekai-profile-client.tsx'],
  ['StarMoe-org/Moesekai/main/web/src/app/[locale]/profile/page.tsx', 'moesekai-locale-profile-page.tsx'],
  ['StarMoe-org/Moesekai/main/web/src/lib/haruki-oauth.ts', 'moesekai-haruki-oauth.ts'],
  // Haruki
  ['Team-Haruki/Haruki-Sekai-API/main/README.md', 'haruki-readme.md'],
  ['Team-Haruki/Haruki-Sekai-API/main/src/api/routes.rs', 'haruki-routes.rs'],
  ['Team-Haruki/Haruki-Sekai-API/main/src/api/middleware.rs', 'haruki-middleware.rs'],
  ['Team-Haruki/Haruki-Sekai-API/main/src/api/apis.rs', 'haruki-apis.rs'],
  ['Team-Haruki/Haruki-Sekai-API/main/src/client/account.rs', 'haruki-account.rs'],
  ['Team-Haruki/Haruki-Sekai-API/main/config.toml', 'haruki-config.toml'],
  ['Team-Haruki/Haruki-Sekai-API/main/.env.example', 'haruki-env.example'],
];
for (const [path, name] of files) {
  await get(`${RAW}/${path}`, name);
}
