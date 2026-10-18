// 一次性探针：Haruki suite-api 路径形状 + pjsk.moe profile 页取数流程
// 用法: node tools/probes/probe-binding-a.mjs
const t = async (u, o = {}) => {
  const s = Date.now();
  try {
    const r = await fetch(u, { ...o, signal: AbortSignal.timeout(25000) });
    const b = await r.text();
    console.log(`[${r.status}] ${u} ${Date.now() - s}ms ${b.length}B`);
    if (b.length < 900) console.log('    ', b.replace(/\s+/g, ' ').slice(0, 850));
    return { status: r.status, body: b, headers: r.headers };
  } catch (e) {
    console.log(`[ERR] ${u} ${e.message} ${Date.now() - s}ms`);
    return { status: 0, body: '' };
  }
};

const H = 'https://suite-api.haruki.seiunx.com';

console.log('=== 路由存在性判定（区分「路由不存在」与「用户不存在」）===');
await t(`${H}/public/jp/totally-bogus-endpoint/123`);
await t(`${H}/public/jp/suite/123456789012345678`);
await t(`${H}/public/jp/suite/aaaaaaaaaaaaaaaa`);
await t(`${H}/totally-bogus`);
await t(`${H}/public/jp/suite`);
await t(`${H}/public/jp/suite/`);

console.log('\n=== key 参数形态 ===');
await t(`${H}/public/jp/suite/123456789012345678?key=userGamedata`);
await t(`${H}/public/jp/suite/123456789012345678?key=all`);
await t(`${H}/public/jp/suite/123456789012345678?key=`);
await t(`${H}/public/cn/suite/123456789012345678?key=userGamedata`);
await t(`${H}/public/en/suite/123456789012345678?key=userGamedata`);

console.log('\n=== pjsk.moe profile 页 ===');
let r = await t('https://pjsk.moe/zh-cn/profile/');
const html = r.body;
console.log('--- title ---');
console.log((html.match(/<title>[\s\S]*?<\/title>/) || [''])[0]);
console.log('--- 可见文本 ---');
const txt = html
  .replace(/<script[\s\S]*?<\/script>/g, '')
  .replace(/<style[\s\S]*?<\/style>/g, '')
  .replace(/<[^>]+>/g, ' ')
  .replace(/&nbsp;/g, ' ')
  .replace(/\s+/g, ' ');
console.log(txt.slice(0, 4000));
console.log('\n--- chunk refs ---');
const refs = Array.from(new Set(html.match(/_next\/static\/[^"'\\ ]+?\.js/g) || []));
console.log('chunks total:', refs.length);
console.log(refs.join('\n'));
console.log('\n--- 关键词命中 ---');
for (const k of ['oauth', 'OAuth', 'haruki', 'suite', 'gameId', 'profile', '自介绍', '绑定', 'verify']) {
  const n = (html.match(new RegExp(k, 'gi')) || []).length;
  if (n) console.log(`  ${k}: ${n}`);
}
