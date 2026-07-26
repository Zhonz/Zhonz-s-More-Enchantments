#!/usr/bin/env python3
"""简化测试 - 只测试关键附魔验证日志"""
import socket, struct, time, sys

class RCONClient:
    def __init__(self, host='127.0.0.1', port=25575, password='test123'):
        self.host, self.port, self.password = host, port, password
        self.sock, self.request_id = None, 0
    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(15)
        self.sock.connect((self.host, self.port))
        self.request_id += 1
        self._send(self.request_id, 3, self.password)
        rid, rt, _ = self._recv()
        if rid != self.request_id or rt != 2: raise Exception("Login failed")
    def _send(self, rid, pt, data):
        p = struct.pack('<ii', rid, pt) + data.encode('utf-8') + b'\x00\x00'
        self.sock.send(struct.pack('<i', len(p)) + p)
    def _recv(self):
        try:
            ld = b''
            while len(ld) < 4: ld += self.sock.recv(4 - len(ld))
            ln = struct.unpack('<i', ld)[0]
            if ln <= 0 or ln > 4110: return (0, 0, "")
            d = b''
            while len(d) < ln: d += self.sock.recv(ln - len(d))
            rid, rt = struct.unpack('<ii', d[:8])
            p = d[8:]
            while p.endswith(b'\x00'): p = p[:-1]
            return (rid, rt, p.decode('utf-8', errors='replace'))
        except: return (0, 0, "")
    def send(self, cmd, wait=0.3):
        self.request_id += 1
        self._send(self.request_id, 2, cmd)
        time.sleep(wait)
        _, _, r = self._recv()
        return r.strip()
    def close(self):
        if self.sock: self.sock.close()

def main():
    rcon = RCONClient()
    rcon.connect()
    print("[RCON] 连接成功\n")

    # 测试1: 终结 - 100血目标
    print("="*50)
    print("测试: 终结 (Finale) - 1级")
    print("="*50)
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.2)
    rcon.send('summon zombie 0 64 0 {Health:100f,Attributes:[{Name:"minecraft:generic.max_health",Base:100f}]}')
    time.sleep(0.3)
    resp = rcon.send("data get entity @e[type=zombie,limit=1] Health")
    print(f"攻击前: {resp}")
    rcon.send("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:finale 1")
    time.sleep(0.5)
    resp = rcon.send("data get entity @e[type=zombie,limit=1] Health")
    print(f"攻击后: {resp}")
    rcon.send("kill @e[type=zombie]")

    # 测试2: 收割 - 10血目标，30%阈值=3
    print("\n" + "="*50)
    print("测试: 收割 (Harvest) - 3级")
    print("="*50)
    rcon.send("summon zombie 0 64 0 {Health:5f}")
    time.sleep(0.3)
    resp = rcon.send("data get entity @e[type=zombie,limit=1] Health")
    print(f"攻击前: {resp} (5血, 30%阈值=1.5)")
    rcon.send("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:harvest 3")
    time.sleep(0.5)
    resp = rcon.send("data get entity @e[type=zombie,limit=1] Health")
    print(f"攻击后: {resp}")
    rcon.send("kill @e[type=zombie]")

    # 测试3: 必须开辟的通路 - 100血目标
    print("\n" + "="*50)
    print("测试: 必须开辟的通路 (Must Open Path) - 1级")
    print("="*50)
    rcon.send('summon zombie 0 64 0 {Health:100f,Attributes:[{Name:"minecraft:generic.max_health",Base:100f}]}')
    time.sleep(0.3)
    resp = rcon.send("data get entity @e[type=zombie,limit=1] Health")
    print(f"攻击前: {resp}")
    rcon.send("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:must_open_path 1")
    time.sleep(0.5)
    resp = rcon.send("data get entity @e[type=zombie,limit=1] Health")
    print(f"攻击后: {resp}")
    rcon.send("kill @e[type=zombie]")

    # 测试4: 神护 - 致命伤害存活
    print("\n" + "="*50)
    print("测试: 神护 (Divine Protection) - 1级")
    print("="*50)
    rcon.send('summon zombie 0 64 0 {Health:5f}')
    time.sleep(0.3)
    rcon.send("zhonztest equiparmor @e[type=zombie,limit=1] chest zhonz_more_enchantments:divine_protection 1")
    time.sleep(0.3)
    resp = rcon.send("data get entity @e[type=zombie,limit=1] Health")
    print(f"致命伤害前: {resp}")
    rcon.send("zhonztest damage @e[type=zombie,limit=1] 100")
    time.sleep(0.5)
    resp = rcon.send("data get entity @e[type=zombie,limit=1] Health")
    print(f"致命伤害后: {resp}")
    rcon.send("kill @e[type=zombie]")

    print("\n\n请查看服务器日志确认详细输出:")
    print("grep -E 'Finale|Harvest|MustOpenPath|DamageEvent' /workspace/run/logs/debug.log")

    rcon.close()

if __name__ == '__main__':
    main()
