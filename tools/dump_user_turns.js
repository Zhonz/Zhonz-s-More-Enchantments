// Extract only human user turns (with timestamps) from a DSH session.jsonl
// usage: node tools/dump_user_turns.js <session.jsonl>
const fs = require('fs');
const file = process.argv[2];
const lines = fs.readFileSync(file, 'utf8').split('\n');
let turn = 0;
const seen = new Set();
function isNoise(t) {
  return /^Current runtime context\.|^<system-reminder>|^This snapshot supersedes/.test(t.trim());
}
const out = [];
for (const line of lines) {
  if (!line.trim()) continue;
  let o; try { o = JSON.parse(line); } catch { continue; }
  const d = o.data || {};
  if (o.type === 'turn/start') turn = d.turn;
  if (o.type === 'turn/end') {
    out.push(`  (turn ${d.turn} end @${new Date(o.time).toISOString()} reason=${d.reason && d.reason.kind})\n`);
  }
  if (o.type === 'user/message' || o.type === 'agent/inbox/spliced') {
    const msgs = o.type === 'user/message' ? [d] : (d.inserted || []);
    for (const m of msgs) {
      if (m.role !== 'user') continue;
      const src = m.source || {};
      if (src.kind && src.kind !== 'user') continue;
      let txt = (m.content || []).map(c => c.text || `[${c.type}]`).join('\n');
      if (isNoise(txt)) continue;
      txt = txt.replace(/\r/g, '').trim();
      if (!txt) continue;
      const key = turn + '|' + txt.slice(0, 120);
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(`\n[TURN ${turn}] @${new Date(o.time).toISOString()}\n${txt}\n`);
    }
  }
}
const text = out.join('\n');
const oi = process.argv.indexOf('--out');
if (oi > -1) { fs.writeFileSync(process.argv[oi + 1], text, 'utf8'); console.log(`wrote ${process.argv[oi + 1]} (${text.length} chars)`); }
else console.log(text);
