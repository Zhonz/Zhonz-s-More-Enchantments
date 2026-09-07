// MaaMCP over HTTP (streamable MCP) minimal client
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
        try {
          // streamable http may return SSE; strip event framing
          const trimmed = data.trim();
          if (trimmed.startsWith('event:')) {
            const lines = trimmed.split('\n').filter(l => l.startsWith('data:'));
            const json = lines.map(l => JSON.parse(l.slice(5).trim())).pop();
            resolve(json);
          } else {
            resolve(JSON.parse(trimmed));
          }
        } catch (e) { reject(new Error('parse: ' + e.message + ' raw=' + data.slice(0, 300))); }
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

(async () => {
  let id = 0;
  const init = await rpc('initialize', {
    protocolVersion: '2025-03-26',
    capabilities: {},
    clientInfo: { name: 'dsh-client', version: '1.0' },
  }, ++id);
  console.log('INIT:', JSON.stringify(init).slice(0, 300));
  await rpc('notifications/initialized', {}, ++id);
  const tools = await rpc('tools/list', {}, ++id);
  const names = tools.result && tools.result.tools ? tools.result.tools.map(t => t.name) : [];
  console.log('TOOLS:', names.join(', '));
  if (names.includes('find_window_list')) {
    const win = await rpc('tools/call', { name: 'find_window_list', arguments: {} }, ++id);
    const text = win.result && win.result.content ? win.result.content.map(c => c.text || '').join('') : JSON.stringify(win);
    console.log('WINDOWS:', text);
  }
})().catch(e => { console.error('ERR', e.message); process.exit(1); });
