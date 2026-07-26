#!/usr/bin/env python3
"""详细测试附魔功能"""
import socket
import struct
import time
import sys

class RCONClient:
    def __init__(self, host='127.0.0.1', port=25575, password='test123'):
        self.host = host
        self.port = port
        self.password = password
        self.sock = None
        self.request_id = 0

    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(15)
        self.sock.connect((self.host, self.port))
        self.request_id += 1
        self._send_packet(self.request_id, 3, self.password)
        resp_id, resp_type, resp_data = self._recv_packet()
        if resp_id != self.request_id or resp_type != 2:
            raise Exception(f"Login failed")
        print("[RCON] 连接成功")

    def _send_packet(self, req_id, packet_type, data):
        payload = struct.pack('<ii', req_id, packet_type) + data.encode('utf-8') + b'\x00\x00'
        self.sock.send(struct.pack('<i', len(payload)) + payload)

    def _recv_packet(self):
        try:
            length_data = self._recv_exact(4)
            if len(length_data) < 4:
                return (0, 0, "")
            length = struct.unpack('<i', length_data)[0]
            if length <= 0 or length > 4110:
                return (0, 0, "")
            data = self._recv_exact(length)
            if len(data) < length:
                return (0, 0, "")
            req_id, req_type = struct.unpack('<ii', data[:8])
            payload = data[8:]
            while payload.endswith(b'\x00'):
                payload = payload[:-1]
            return (req_id, req_type, payload.decode('utf-8', errors='replace'))
        except Exception as e:
            return (0, 0, str(e))

    def _recv_exact(self, n):
        data = b''
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                break
            data += chunk
        return data

    def send(self, cmd, wait=0.3):
        print(f"\n>>> {cmd}")
        self.request_id += 1
        self._send_packet(self.request_id, 2, cmd)
        time.sleep(wait)
        resp_id, resp_type, resp_data = self._recv_packet()
        if resp_data:
            print(f"<<< {resp_data.strip()}")
        return resp_data.strip()

    def close(self):
        if self.sock:
            self.sock.close()

def main():
    rcon = RCONClient()
    try:
        rcon.connect()
    except Exception as e:
        print(f"RCON连接失败: {e}")
        sys.exit(1)

    # 先设置世界出生点
    rcon.send("setworldspawn 0 64 0")
    
    # 传送到出生点
    rcon.send("execute in minecraft:overworld run summon zombie 0 64 0 {CustomName:'\"TestDummy\"'}")
    time.sleep(0.5)
    
    # 查找实体
    rcon.send("execute if entity @e[type=zombie] run say Found zombie!")
    rcon.send("execute store result score @s dummy run data get entity @e[type=zombie,limit=1] Health")
    
    # 测试装备附魔
    print("\n" + "="*60)
    print("测试1: 深海的供养")
    print("="*60)
    
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.3)
    
    # 生成僵尸在出生点
    rcon.send("summon zombie 0 64 0 {Health:20f}")
    time.sleep(0.3)
    
    rcon.send("execute if entity @e[type=zombie] run say Zombie found")
    
    # 查看初始血量
    result = rcon.send("data get entity @e[type=zombie,limit=1] Health")
    
    # 装备深海的供养
    rcon.send("zhonztest equiparmor @e[type=zombie,limit=1] chest zhonz_more_enchantments:deep_seas_grace 3")
    time.sleep(0.3)
    
    # 查看装备信息
    rcon.send("zhonztest info @e[type=zombie,limit=1]")
    
    # 造成伤害
    rcon.send("zhonztest damage @e[type=zombie,limit=1] 10")
    time.sleep(1.0)  # 等待治疗
    
    # 查看伤害后的血量
    rcon.send("data get entity @e[type=zombie,limit=1] Health")
    
    # ============================================================
    # 测试2: 终结附魔
    # ============================================================
    print("\n" + "="*60)
    print("测试2: 终结附魔")
    print("="*60)
    
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.3)
    
    rcon.send("summon zombie 0 64 0 {Health:100f}")
    time.sleep(0.3)
    
    rcon.send("data get entity @e[type=zombie,limit=1] Health")
    
    rcon.send("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:finale 1")
    time.sleep(0.5)
    
    rcon.send("data get entity @e[type=zombie,limit=1] Health")
    
    # ============================================================
    # 测试3: 剥壳附魔
    # ============================================================
    print("\n" + "="*60)
    print("测试3: 剥壳附魔")
    print("="*60)
    
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.3)
    
    rcon.send('summon zombie 0 64 0 {Health:50f,Attributes:[{Name:"minecraft:generic.armor",Base:10f}]}')
    time.sleep(0.3)
    
    rcon.send("data get entity @e[type=zombie,limit=1] Health")
    
    # 连续攻击3次
    for i in range(3):
        print(f"\n--- 第{i+1}次攻击 ---")
        rcon.send("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:shell_strip 1")
        time.sleep(0.3)
        rcon.send("data get entity @e[type=zombie,limit=1] Health")
    
    # ============================================================
    # 测试4: 鱼丸附魔
    # ============================================================
    print("\n" + "="*60)
    print("测试4: 鱼丸附魔")
    print("="*60)
    
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.3)
    
    # 生成两个僵尸
    rcon.send("summon zombie 0 64 0 {Health:20f,CustomName:'\"鱼丸装备者\"'}")
    rcon.send("summon zombie 2 64 0 {Health:20f,CustomName:'\"普通僵尸\"'}")
    time.sleep(0.5)
    
    # 给第一个僵尸装备鱼丸
    rcon.send('zhonztest equiparmor @e[name="鱼丸装备者",limit=1] chest zhonz_more_enchantments:fishball 1')
    time.sleep(0.3)
    
    print("\n--- 伤害前 ---")
    rcon.send('data get entity @e[name="鱼丸装备者",limit=1] Health')
    rcon.send('data get entity @e[name="普通僵尸",limit=1] Health')
    
    # 伤害普通僵尸
    rcon.send('zhonztest damage @e[name="普通僵尸",limit=1] 10')
    time.sleep(0.5)
    
    print("\n--- 伤害后 ---")
    rcon.send('data get entity @e[name="鱼丸装备者",limit=1] Health')
    rcon.send('data get entity @e[name="普通僵尸",limit=1] Health')
    
    # 清理
    rcon.send("kill @e[type=zombie]")
    
    print("\n" + "="*60)
    print("测试完成！")
    print("="*60)

    rcon.close()

if __name__ == '__main__':
    main()
