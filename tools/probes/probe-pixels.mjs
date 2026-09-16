// 截图取色探针：把「我看不到屏幕」变成「我能量屏幕」。
//
// 用法：
//   node tools/probes/probe-pixels.mjs <png> [x1,x2,...] [yFrom] [yTo] [step]
//   例：node tools/probes/probe-pixels.mjs shot.png 8,632 0 700 10
//
// 只做一件事：解码 PNG，按行打印取样点的 RGB，并在颜色发生变化时打标记。
// 存在的意义是量「某条色带从第几像素开始 / 到第几像素结束」这种纯几何问题，
// 不依赖视觉。截图请用：adb exec-out screencap -p > shot.png
import { readFileSync } from "node:fs";
import { inflateSync } from "node:zlib";

const [, , file, xsArg = "8", yFromArg = "0", yToArg = "0", stepArg = "10"] = process.argv;
if (!file) {
  console.error("usage: node tools/probes/probe-pixels.mjs <png> [x1,x2] [yFrom] [yTo] [step]");
  process.exit(2);
}

const buf = readFileSync(file);
if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error("不是 PNG");

// ── 解析 chunk，收集 IHDR 与 IDAT ──
let off = 8;
let width = 0, height = 0, bitDepth = 0, colorType = 0;
const idat = [];
while (off < buf.length) {
  const len = buf.readUInt32BE(off);
  const type = buf.toString("ascii", off + 4, off + 8);
  const data = buf.subarray(off + 8, off + 8 + len);
  if (type === "IHDR") {
    width = data.readUInt32BE(0);
    height = data.readUInt32BE(4);
    bitDepth = data[8];
    colorType = data[9];
    if (data[12] !== 0) throw new Error("不支持隔行扫描");
  } else if (type === "IDAT") idat.push(data);
  else if (type === "IEND") break;
  off += 12 + len;
}
if (bitDepth !== 8) throw new Error("只支持 8bit，实际 " + bitDepth);
const channels = { 0: 1, 2: 3, 4: 2, 6: 4 }[colorType];
if (!channels) throw new Error("不支持的 colorType " + colorType);

// ── 反滤波 ──
const raw = inflateSync(Buffer.concat(idat));
const stride = width * channels;
const px = Buffer.alloc(height * stride);
let prev = Buffer.alloc(stride);
for (let y = 0; y < height; y++) {
  const filter = raw[y * (stride + 1)];
  const line = raw.subarray(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
  const cur = px.subarray(y * stride, (y + 1) * stride);
  for (let i = 0; i < stride; i++) {
    const a = i >= channels ? cur[i - channels] : 0;
    const b = prev[i];
    const c = i >= channels ? prev[i - channels] : 0;
    let v = line[i];
    if (filter === 1) v += a;
    else if (filter === 2) v += b;
    else if (filter === 3) v += (a + b) >> 1;
    else if (filter === 4) {
      const p = a + b - c;
      const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
      v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
    }
    cur[i] = v & 0xff;
  }
  prev = cur;
}

const at = (x, y) => {
  const i = y * stride + x * channels;
  return [px[i], px[i + 1 % channels], channels >= 3 ? px[i + 2] : px[i]];
};
const hex = (c) => "#" + c.map((v) => v.toString(16).padStart(2, "0")).join("");

const xs = xsArg.split(",").map((s) => parseInt(s, 10)).filter((n) => n >= 0 && n < width);
const yFrom = parseInt(yFromArg, 10);
const yTo = yToArg === "0" ? Math.min(height - 1, yFrom + 600) : parseInt(yToArg, 10);
const step = Math.max(1, parseInt(stepArg, 10));

console.log(`${file}: ${width}x${height} colorType=${colorType}`);
console.log(`取样 x=${xs.join(",")}  y=${yFrom}..${yTo} step=${step}`);

let lastKey = null;
for (let y = yFrom; y <= yTo && y < height; y += step) {
  const cols = xs.map((x) => at(x, y));
  const key = cols.map(hex).join(" ");
  const changed = key !== lastKey;
  if (changed) console.log(`y=${String(y).padStart(5)}  ${key}   <-- 颜色变化`);
  lastKey = key;
}
