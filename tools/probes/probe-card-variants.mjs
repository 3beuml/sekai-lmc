/**
 * 实测卡面素材的「质量档位」：每个变体的尺寸与体积，用来决定「显示用哪档、下载用哪档」。
 *
 * 已知存在这些变体（都是 `character/` 下的目录）：
 *   member/<bundle>/card_normal.webp        卡面（大）
 *   member_small/<bundle>/card_normal.webp  卡面（小）
 *   member_gacha/<bundle>/normal.webp       卡池展示用
 *   member_cutout/<bundle>/normal.webp      去背景立绘
 *   member_cutout_trm/<bundle>/normal.webp  去背景立绘（特训后）
 *   thumbnail/chara/<bundle>_normal.webp    方形小图标
 * 且每个都有 .png 版本（体积大得多，用于「下载原图」）。
 *
 * 尺寸靠读文件头的二进制字段解析（不依赖任何图片库）：
 *   WebP: RIFF....WEBP 之后可能是 VP8 (有损) / VP8L (无损) / VP8X (扩展)
 *   PNG : IHDR 里第 16-23 字节是宽高（大端 32 位）
 *
 * 用法：node tools/probes/probe-card-variants.mjs
 */
const BUCKET = 'https://storage.sekai.best/sekai-jp-assets/';

function webpSize(buf) {
  if (buf.length < 30) return null;
  const fourcc = buf.toString('ascii', 12, 16);
  if (fourcc === 'VP8 ') {
    // 有损：帧头 3 字节是 0x9d 0x01 0x2a，之后 2 字节宽、2 字节高（小端，取低 14 位）
    const w = buf.readUInt16LE(26) & 0x3fff;
    const h = buf.readUInt16LE(28) & 0x3fff;
    return { w, h, kind: 'VP8 有损' };
  }
  if (fourcc === 'VP8L') {
    const b = buf.readUInt32LE(21);
    return { w: (b & 0x3fff) + 1, h: ((b >> 14) & 0x3fff) + 1, kind: 'VP8L 无损' };
  }
  if (fourcc === 'VP8X') {
    const w = (buf[24] | (buf[25] << 8) | (buf[26] << 16)) + 1;
    const h = (buf[27] | (buf[28] << 8) | (buf[29] << 16)) + 1;
    return { w, h, kind: 'VP8X 扩展' };
  }
  return { w: null, h: null, kind: fourcc };
}

function pngSize(buf) {
  if (buf.length < 24 || buf.toString('ascii', 12, 16) !== 'IHDR') return { w: null, h: null };
  return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20), kind: 'PNG' };
}

async function inspect(key) {
  const url = BUCKET + key;
  // 先 HEAD 拿体积
  let len = 0;
  let status = 0;
  try {
    const head = await fetch(url, { method: 'HEAD' });
    status = head.status;
    len = Number(head.headers.get('content-length') ?? 0);
    if (!head.ok) return { key, status, len: 0, dims: null };
  } catch (e) {
    return { key, status: 'ERR', len: 0, dims: null };
  }
  // 再取前 64 字节解析尺寸
  let dims = null;
  try {
    const r = await fetch(url, { headers: { Range: 'bytes=0-63' } });
    const buf = Buffer.from(await r.arrayBuffer());
    dims = key.endsWith('.png') ? pngSize(buf) : webpSize(buf);
  } catch {
    /* 尺寸解析失败不影响体积结论 */
  }
  return { key, status, len, dims };
}

const kb = (b) => (b >= 1048576 ? `${(b / 1048576).toFixed(2)} MB` : `${(b / 1024).toFixed(1)} KB`);

/** 对某张卡列出所有变体。 */
async function cardVariants(bundle, label) {
  console.log(`\n═══ ${label}（${bundle}）═══`);
  const targets = [
    ['卡面大图  member', `character/member/${bundle}/card_normal.webp`],
    ['卡面大图PNG member', `character/member/${bundle}/card_normal.png`],
    ['觉醒大图  member', `character/member/${bundle}/card_after_training.webp`],
    ['觉醒大图PNG member', `character/member/${bundle}/card_after_training.png`],
    ['卡面小图  member_small', `character/member_small/${bundle}/card_normal.webp`],
    ['觉醒小图  member_small', `character/member_small/${bundle}/card_after_training.webp`],
    ['卡池展示  member_gacha', `character/member_gacha/${bundle}/normal.webp`],
    ['去背立绘  member_cutout', `character/member_cutout/${bundle}/normal.webp`],
    ['去背立绘PNG member_cutout', `character/member_cutout/${bundle}/normal.png`],
    ['去背觉醒  member_cutout_trm', `character/member_cutout_trm/${bundle}/normal.webp`],
    ['方形图标  thumbnail/chara', `thumbnail/chara/${bundle}_normal.webp`],
  ];
  for (const [label2, key] of targets) {
    const r = await inspect(key);
    const d = r.dims && r.dims.w ? `${r.dims.w}×${r.dims.h}` : '-';
    const pad = (s, n) => String(s).padEnd(n);
    console.log(
      `  ${pad(label2, 30)} ${pad(r.status, 4)} ${pad(r.len ? kb(r.len) : '-', 10)} ${pad(d, 12)} ${r.dims?.kind ?? ''}`,
    );
  }
}

// 1★ 卡（无觉醒图）与 4★ 卡（有觉醒图）各测一张
await cardVariants('res001_no001', '1★ 卡');
await cardVariants('res001_no004', '4★ 卡');
