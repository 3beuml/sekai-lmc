const t = async (u, o = {}) => {
  try {
    const r = await fetch(u, { ...o, redirect: 'manual', signal: AbortSignal.timeout(20000) });
    const b = await r.text();
    console.log(`[${r.status}] ${u} ${b.length}B`);
    if (b.length < 400) console.log('    ', b.replace(/\s+/g, ' '));
    return b;
  } catch (e) { console.log(`[ERR] ${u} ${e.message}`); return ''; }
};
console.log('=== toolbox-api-direct OAuth2 (Ory Hydra) 端点形状 ===');
await t('https://toolbox-api-direct.haruki.seiunx.com/api/oauth2/authorize');
await t('https://toolbox-api-direct.haruki.seiunx.com/api/oauth2/user/profile');
await t('https://toolbox-api-direct.haruki.seiunx.com/api/oauth2/user/bindings');
await t('https://toolbox-api-direct.haruki.seiunx.com/api/oauth2/.well-known/openid-configuration');
await t('https://toolbox-api-direct.haruki.seiunx.com/.well-known/openid-configuration');
await t('https://haruki.seiunx.com/.well-known/openid-configuration');
console.log('=== suite-api 根/公开路径再确认 ===');
await t('https://suite-api.haruki.seiunx.com/');
await t('https://suite-api.haruki.seiunx.com/public/jp/suite/123456789012345678');
