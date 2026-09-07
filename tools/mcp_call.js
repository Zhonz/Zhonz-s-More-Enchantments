// Generic MaaMCP tool caller: node mcp_call.js <toolName> <jsonArgs>
const http = require('http');
const URL = 'http://127.0.0.1:9527/mcp';
let sessionId = null;

function rpc(method, params, id) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({ jsonrpc: '2.0', id, method, params });
    const req = http.request(URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json, text/event-stream',
        ...(sessionId ? { 'Mcp-Session-Id': sessionId } : {}),
      },
    }, (res) => {
      if (res.headers['mcp-session-id']) sessionId = res.headers['mcp-session-id'];
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => {
        const trimmed = data.trim();
        if (trimmed.startsWith('event:')) {
          const lines = trimmed.split('\n').filter(l => l.startsWith('data:'));
          const json = lines.map(l => JSON.parse(l.slice(5).trim())).pop();
          resolve(json);
        } else {
          try { resolve(JSON.parse(trimmed)); } catch (e) { resolve(trimmed); }
        }
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

(async () => {
  let id = 0;
  await rpc('initialize', {
    protocolVersion: '2025-03-26', capabilities: {},
    clientInfo: { name: 'dsh-client', version: '1.0' },
  }, ++id);
  await rpc('notifications/initialized', {}, ++id);

  const toolName = process.argv[2];
  if (!toolName) {
    const tools = await rpc('tools/list', {}, ++id);
    for (const t of tools.result.tools) {
      console.log(`${t.name}  schema=${JSON.stringify(t.inputSchema ? t.inputSchema.properties : {})}`);
    }
    return;
  }
  let args = {};
  const rawArgs = process.argv[3];
  if (rawArgs) {
    if (rawArgs.startsWith('@')) {
      const fs = require('fs');
      args = JSON.parse(fs.readFileSync(rawArgs.slice(1), 'utf8'));
    } else {
      args = JSON.parse(rawArgs);
    }
  }
  const res = await rpc('tools/call', { name: toolName, arguments: args }, ++id);
  const content = res.result && res.result.content ? res.result.content.map(c => {
    if (c.type === 'text') return c.text;
    if (c.type === 'image') return `[IMAGE data:${(c.data || '').slice(0, 40)}...]`;
    return JSON.stringify(c);
  }).join('\n') : JSON.stringify(res);
  console.log(content);
})().catch(e => { console.error('ERR', e.message); process.exit(1); });
