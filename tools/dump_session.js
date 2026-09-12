// Dump a DSH session.jsonl into a readable per-turn transcript.
// usage: node tools/dump_session.js <session.jsonl> [--full|--brief] [--out <file>]
const fs = require('fs');

const file = process.argv[2];
const outIdx = process.argv.indexOf('--out');
const outFile = outIdx > -1 ? process.argv[outIdx + 1] : null;
const brief = process.argv.includes('--brief');

const lines = fs.readFileSync(file, 'utf8').split('\n');
const out = [];
let curTurn = 0;
let curStep = 0;

function clip(s, n) {
  s = String(s).replace(/\r/g, '');
  if (s.length <= n) return s;
  return s.slice(0, n) + `\n…[+${s.length - n} chars]`;
}

for (const line of lines) {
  if (!line.trim()) continue;
  let o;
  try { o = JSON.parse(line); } catch { continue; }
  const d = o.data || {};
  switch (o.type) {
    case 'session':
      out.push(`# SESSION ${o.id}\ncwd=${o.cwd}\ncreatedAt=${new Date(o.createdAt).toISOString()}`);
      break;
    case 'session/title':
      out.push(`\n## TITLE: ${d.title}  (source=${d.source && d.source.kind})`);
      break;
    case 'turn/start':
      curTurn = d.turn;
      out.push(`\n${'='.repeat(70)}\n### TURN ${d.turn}  @${new Date(o.time).toISOString()}\n${'='.repeat(70)}`);
      break;
    case 'turn/end':
      out.push(`--- turn ${d.turn} end: ${JSON.stringify(d.reason)}`);
      break;
    case 'step/start':
      curStep = d.step;
      break;
    case 'user/message': {
      const txt = (d.content || []).map(c => c.text || `[${c.type}]`).join('\n');
      out.push(`\n>>> USER (turn ${curTurn}):\n${clip(txt, brief ? 1500 : 8000)}`);
      break;
    }
    case 'agent/inbox/spliced': {
      const ins = d.inserted || [];
      for (const m of ins) {
        const txt = (m.content || []).map(c => c.text || `[${c.type}]`).join('\n');
        if (m.role === 'user') out.push(`\n>>> USER(inbox):\n${clip(txt, brief ? 1500 : 8000)}`);
      }
      break;
    }
    case 'assistant/message': {
      const msg = d.message || {};
      const parts = [];
      for (const c of msg.content || []) {
        if (c.type === 'text') parts.push(c.text);
        else if (c.type === 'tool-call') parts.push(`[tool-call ${c.name}] ${clip(c.arguments, brief ? 200 : 1500)}`);
      }
      const body = parts.join('\n');
      if (!body.trim()) break;
      out.push(`\n<<< ASSISTANT (turn ${d.turn} step ${d.step}):\n${clip(body, brief ? 2500 : 20000)}`);
      break;
    }
    case 'tool/call':
      out.push(`\n[tool ${d.name}] ${clip(d.arguments, brief ? 200 : 800)}`);
      break;
    case 'tool/result': {
      const msg = d.message || {};
      let txt = '';
      for (const c of msg.content || []) {
        if (c.type === 'tool-result') {
          for (const cc of c.content || []) txt += (cc.text || `[${cc.type}]`) + '\n';
        }
      }
      out.push(`\n[tool-result] ${clip(txt, brief ? 700 : 1200)}`);
      break;
    }
    case 'todo/write':
      out.push(`\n[todos] ${(d.todos || []).map(t => `(${t.status}) ${t.content}`).join(' | ')}`);
      break;
    case 'command/run':
      out.push(`\n[cmd] ${d.name} ${d.args || ''}`);
      break;
    case 'compaction/start':
      out.push(`\n[[compaction start turn=${d.turn}]]`);
      break;
    case 'compaction/end':
      out.push(`[[compaction end turn=${d.turn} error=${d.error || 'none'}]]`);
      break;
    case 'model/selection':
      out.push(`\n[model] ${d.provider}/${d.model} effort=${d.reasoningEffort}`);
      break;
    default:
      break;
  }
}

const text = out.join('\n');
if (outFile) {
  fs.writeFileSync(outFile, text, 'utf8');
  console.log(`wrote ${outFile} (${text.length} chars, ${out.length} records)`);
} else {
  console.log(text);
}
