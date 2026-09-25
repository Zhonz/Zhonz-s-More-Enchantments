// 三版本资源一致性门禁: 主工程(1.21.1) vs platforms/1.20.1-{forge,neoforge}。
// 目的: 每次改完主工程资源后, 一条命令确认三版本没有漂移(历史上漂移过两次:
//       平台缺 lang / 平台 damage_type 的 scaling+effects 与主工程不一致)。
// 比较对象(白名单, 不碰各平台自有的 pack.mcmeta / mods.toml 等):
//   - assets/zhonz_more_enchantments/lang/*.json  : 键集合 + 每个键的文案
//   - data/zhonz_more_enchantments/damage_type/*.json
//   - data/minecraft/tags/damage_type/*.json
// 行尾(CRLF/LF)与 BOM 不参与比较, JSON 按语义比对。
// 用法: node tools/check-platform-parity.mjs   (退出码 0=一致, 1=有漂移)
import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.cwd();
const REF = 'src/main/resources';
const PLATFORMS = [
  'platforms/1.20.1-forge/src/main/resources',
  'platforms/1.20.1-neoforge/src/main/resources',
];

const readJson = (p) => {
  let t = fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, '');
  return JSON.parse(t);
};
const exists = (p) => fs.existsSync(p);
const rel = (p) => path.relative(ROOT, p).split(path.sep).join('/');

/** 收集白名单里应当存在的文件(以主工程为准) */
function collectRef() {
  const out = [];
  const langDir = path.join(ROOT, REF, 'assets/zhonz_more_enchantments/lang');
  for (const f of fs.readdirSync(langDir).sort()) {
    if (f.endsWith('.json')) out.push(path.join('assets/zhonz_more_enchantments/lang', f));
  }
  for (const dir of ['data/zhonz_more_enchantments/damage_type', 'data/minecraft/tags/damage_type']) {
    const abs = path.join(ROOT, REF, dir);
    for (const f of fs.readdirSync(abs).sort()) {
      if (f.endsWith('.json')) out.push(path.join(dir, f));
    }
  }
  return out;
}

const problems = [];
const files = collectRef();

for (const platform of PLATFORMS) {
  for (const file of files) {
    const refAbs = path.join(ROOT, REF, file);
    const platAbs = path.join(ROOT, platform, file);
    if (!exists(platAbs)) {
      problems.push(`缺失: ${rel(platAbs)}  (主工程有 ${rel(refAbs)})`);
      continue;
    }
    const a = readJson(refAbs);
    const b = readJson(platAbs);
    if (/[\\/]lang[\\/][^\\/]+\.json$/.test(file)) {
      const ka = Object.keys(a).sort();
      const kb = Object.keys(b).sort();
      const missing = ka.filter((k) => !(k in b));
      const extra = kb.filter((k) => !(k in a));
      const diff = ka.filter((k) => k in b && a[k] !== b[k]);
      if (missing.length) problems.push(`键缺失: ${rel(platAbs)} 缺 ${missing.length} 个: ${missing.slice(0, 8).join(', ')}${missing.length > 8 ? ' …' : ''}`);
      if (extra.length) problems.push(`多余键: ${rel(platAbs)} 多 ${extra.length} 个: ${extra.slice(0, 8).join(', ')}${extra.length > 8 ? ' …' : ''}`);
      if (diff.length) problems.push(`文案不一致: ${rel(platAbs)} 有 ${diff.length} 个键与主工程不同: ${diff.slice(0, 5).join(', ')}${diff.length > 5 ? ' …' : ''}`);
    } else if (JSON.stringify(a) !== JSON.stringify(b)) {
      problems.push(`内容不一致: ${rel(platAbs)}\n    主工程: ${JSON.stringify(a)}\n    平台版: ${JSON.stringify(b)}`);
    }
  }
}

// 反向: 平台有、主工程没有的同目录文件(避免"只在平台里加了个文件")
for (const platform of PLATFORMS) {
  for (const dir of ['data/zhonz_more_enchantments/damage_type', 'data/minecraft/tags/damage_type']) {
    const abs = path.join(ROOT, platform, dir);
    if (!exists(abs)) continue;
    for (const f of fs.readdirSync(abs).sort()) {
      if (!f.endsWith('.json')) continue;
      const refAbs = path.join(ROOT, REF, dir, f);
      if (!exists(refAbs)) problems.push(`孤立文件: ${rel(path.join(abs, f))} 在主工程 ${REF}/${dir} 中不存在`);
    }
  }
}

const langCount = files.filter((f) => /[\\/]lang[\\/][^\\/]+\.json$/.test(f)).length;
console.log(`[parity] 主工程基准文件 ${files.length} 个(含 lang ${langCount} 个 JSON),对比 ${PLATFORMS.length} 个平台 x ${files.length} 项`);
if (problems.length === 0) {
  console.log('[parity] RESULT=PASS 三版本资源一致(JSON 语义级;行尾/BOM 差异忽略)');
  process.exit(0);
}
console.log(`[parity] RESULT=FAIL 发现 ${problems.length} 处漂移:`);
for (const p of problems) console.log('  - ' + p);
process.exit(1);
