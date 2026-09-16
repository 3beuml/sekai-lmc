/**
 * 抓取远端 master data JSON 的**开头一小段**，用来确认字段结构。
 *
 * 为什么需要它：`cards.json` 有 35 MB，本机没有能一次拉完的通道
 *   - curl.exe / Invoke-WebRequest 被 TLS 阻断（schannel SEC_E_CREDENTIALS）
 *   - jsDelivr 拒绝 > 20 MB 的文件
 *   - GitHub Pages 只有 ~13 KB/s，拉全量要 45 分钟
 * 但 Node 的原生 fetch 可以联网并支持 Range，所以只取前 8 KB 就够看清一行数据的字段。
 *
 * 用法：node tools/probes/probe-json-head.mjs <fileName> [bytes] [基址]
 */
import { mkdirSync, writeFileSync } from 'node:fs';

const fileName = process.argv[2] ?? 'cards.json';
const wantBytes = Number(process.argv[3] ?? 8000);
const base = process.argv[4] ?? 'https://sekai-world.github.io/sekai-master-db-diff/';
const url = base + fileName;

// 产物统一落在 tools/.cache/（已 gitignore）
const outDir = new URL('../.cache/', import.meta.url);
mkdirSync(outDir, { recursive: true });
const stem = fileName.replace(/\.json$/, '');
const outFile = new URL(`probe-${stem}-head.txt`, outDir);
const metaFile = new URL(`probe-${stem}-meta.txt`, outDir);

const started = Date.now();
const res = await fetch(url, { headers: { Range: `bytes=0-${wantBytes - 1}` } });
const status = res.status;
const len = res.headers.get('content-length');
const range = res.headers.get('content-range');

let text;
if (status === 206) {
  text = await res.text();
} else {
  // 服务端不支持 Range：只读流的前几块，然后主动断开，避免把 35 MB 全拉下来
  const reader = res.body.getReader();
  const chunks = [];
  let total = 0;
  while (total < wantBytes) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    total += value.length;
  }
  await reader.cancel().catch(() => {});
  text = Buffer.concat(chunks).toString('utf8');
}

writeFileSync(outFile, text.slice(0, wantBytes), 'utf8');
writeFileSync(
  metaFile,
  [
    `url=${url}`,
    `status=${status}`,
    `content-length=${len}`,
    `content-range=${range}`,
    `gotBytes=${Buffer.byteLength(text)}`,
    `elapsedMs=${Date.now() - started}`,
  ].join('\n'),
  'utf8',
);
console.log(`status=${status} gotBytes=${Buffer.byteLength(text)} elapsedMs=${Date.now() - started}`);
console.log(`-> ${outFile.pathname}`);
