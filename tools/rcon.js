// Minimal RCON client: node rcon.js <command>
const net = require('net');

const HOST = '127.0.0.1';
const PORT = 25575;
const PASSWORD = 'zhonz_test_rcon';

function packet(id, type, payload) {
  // body = id(4) + type(4) + payload + null(1) + pad(1); 前缀 = body 长度(4)
  const body = Buffer.alloc(payload.length + 10);
  body.writeInt32LE(id, 0);
  body.writeInt32LE(type, 4);
  body.write(payload, 8, 'utf8');
  const prefix = Buffer.alloc(4);
  prefix.writeInt32LE(body.length, 0);
  return Buffer.concat([prefix, body]);
}

const cmd = process.argv[2];
if (!cmd) { console.error('usage: node rcon.js <command>'); process.exit(1); }

const sock = net.connect(PORT, HOST, () => {
  sock.write(packet(1, 3, PASSWORD));
});

let buf = Buffer.alloc(0);
sock.on('data', (d) => {
  buf = Buffer.concat([buf, d]);
  while (buf.length >= 4) {
    const len = buf.readInt32LE(0);
    if (buf.length < len + 4) break;
    const pkt = buf.subarray(4, 4 + len);
    buf = buf.subarray(4 + len);
    const id = pkt.readInt32LE(0);
    const type = pkt.readInt32LE(4);
    if (id === 1 && type === 2) {
      // auth response
      sock.write(packet(2, 2, cmd));
    } else if (id === 2) {
      const text = pkt.subarray(8, len - 2).toString('utf8');
      console.log(text);
      sock.end();
      process.exit(0);
    }
  }
});
sock.on('error', (e) => { console.error('RCON ERROR:', e.message); process.exit(1); });
sock.setTimeout(15000, () => { console.error('RCON timeout'); process.exit(1); });
