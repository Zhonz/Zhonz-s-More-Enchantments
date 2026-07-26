#!/usr/bin/env python3
"""手动测试关键附魔 - 详细输出"""
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
        self._send_packet(3, self.password)
        resp = self._recv_packet()
        if resp[0] != 2:
            raise Exception(f"Login failed, got type {resp[0]}")
        print("[RCON] 连接成功")

    def _send_packet(self, packet_type, data):
        self.request_id += 1
        payload = struct.pack('<iii', self.request_id, packet_type, 0) + data.encode('utf-8') + b'\x00\x00'
        self.sock.send(struct.pack('<i', len(payload)) + payload)

    def _recv_packet(self):
        try:
            length_data = self.sock.recv(4)
            if len(length_data) < 4:
                return (0, "")
            length = struct.unpack('<i', length_data)[0]
            if length <= 0 or length > 4110:
                return (0, "")
            data = b''
            while len(data) < length:
                chunk = self.sock.recv(length - len(data))
                if not chunk:
                    break
                data += chunk
            if len(data) < 8:
                return (0, "")
            req_id, req_type = struct.unpack('<ii', data[:8])
            payload = data[8:]
            while payload.endswith(b'\x00'):
                payload = payload[:-1]
            return (req_type, payload.decode('utf-8', errors='replace'))
        except Exception as e:
            return (0, str(e))

    def send(self, cmd, wait=0.3):
        print(f"\n>>> {cmd}")
        self._send_packet(2, cmd)
        time.sleep(wait)
        resp = self._recv_packet()
        if resp[1]:
            print(f"<<< {resp[1]}")
        return resp[1]

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

    spawn_x, spawn_y, spawn_z = 100, 64, 100

    print("\n" + "="*60)
    print("清理环境")
    print("="*60)
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.5)

    # ============================================================
    # 测试1: 深海的供养
    # ============================================================
    print("\n" + "="*60)
    print("测试1: 深海的供养 (Deep Sea's Grace) - 3级")
    print("描述: 受伤后恢复生命上限20%的血量（治疗而非减伤）")
    print("="*60)

    # 生成僵尸
    rcon.send(f"summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:20f}}")
    time.sleep(0.3)

    # 查看初始血量
    rcon.send("data get entity @e[type=zombie,limit=1] Health")

    # 装备深海的供养胸甲
    rcon.send("zhonztest equiparmor @e[type=zombie,limit=1] chest zhonz_more_enchantments:deep_seas_grace 3")
    time.sleep(0.3)

    # 造成10点伤害
    rcon.send("zhonztest damage @e[type=zombie,limit=1] 10")
    time.sleep(0.5)  # 等待下一tick治疗

    # 查看伤害后的血量
    rcon.send("data get entity @e[type=zombie,limit=1] Health")

    # 清理
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.3)

    # ============================================================
    # 测试2: 终结附魔
    # ============================================================
    print("\n" + "="*60)
    print("测试2: 终结附魔 (Finale) - 1级")
    print("描述: 攻击伤害>=7时造成100000倍伤害")
    print("="*60)

    rcon.send(f"summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:100f}}")
    time.sleep(0.3)

    rcon.send("data get entity @e[type=zombie,limit=1] Health")

    rcon.send("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:finale 1")
    time.sleep(0.5)

    rcon.send("data get entity @e[type=zombie,limit=1] Health")

    rcon.send("kill @e[type=zombie]")
    time.sleep(0.3)

    # ============================================================
    # 测试3: 剥壳附魔
    # ============================================================
    print("\n" + "="*60)
    print("测试3: 剥壳附魔 (Shell Strip) - 1级")
    print("描述: 造成被护甲减免伤害40%的真实伤害，逐渐提高到75%")
    print("="*60)

    # 生成带护甲的僵尸
    rcon.send(f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:50f,Attributes:[{{Name:"minecraft:generic.armor",Base:10f}}]}}')
    time.sleep(0.3)

    rcon.send("data get entity @e[type=zombie,limit=1] Health")

    # 第一次攻击（40%真实伤害）
    rcon.send("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:shell_strip 1")
    time.sleep(0.3)
    rcon.send("data get entity @e[type=zombie,limit=1] Health")

    # 第二次攻击（45%真实伤害）
    rcon.send("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:shell_strip 1")
    time.sleep(0.3)
    rcon.send("data get entity @e[type=zombie,limit=1] Health")

    # 第三次攻击（50%真实伤害）
    rcon.send("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:shell_strip 1")
    time.sleep(0.3)
    rcon.send("data get entity @e[type=zombie,limit=1] Health")

    rcon.send("kill @e[type=zombie]")
    time.sleep(0.3)

    # ============================================================
    # 测试4: 鱼丸附魔
    # ============================================================
    print("\n" + "="*60)
    print("测试4: 鱼丸附魔 (Fishball) - 1级")
    print("描述: 同类生物的伤害转移到鱼丸装备者身上")
    print("="*60)

    # 生成两个僵尸
    rcon.send(f"summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:20f,CustomName:'\"鱼丸装备者\"'}}")
    rcon.send(f"summon zombie {spawn_x+2} {spawn_y} {spawn_z} {{Health:20f,CustomName:'\"普通僵尸\"'}}")
    time.sleep(0.5)

    # 给第一个僵尸装备鱼丸胸甲
    rcon.send('zhonztest equiparmor @e[name="鱼丸装备者",limit=1] chest zhonz_more_enchantments:fishball 1')
    time.sleep(0.3)

    # 查看两个僵尸的血量
    print("\n--- 伤害前 ---")
    rcon.send('data get entity @e[name="鱼丸装备者",limit=1] Health')
    rcon.send('data get entity @e[name="普通僵尸",limit=1] Health')

    # 攻击普通僵尸（非鱼丸装备者）
    rcon.send('zhonztest damage @e[name="普通僵尸",limit=1] 10')
    time.sleep(0.5)

    # 查看伤害后的血量
    print("\n--- 伤害后 ---")
    rcon.send('data get entity @e[name="鱼丸装备者",limit=1] Health')
    rcon.send('data get entity @e[name="普通僵尸",limit=1] Health')

    rcon.send("kill @e[type=zombie]")

    print("\n" + "="*60)
    print("测试完成！请查看服务器日志 /workspace/run/logs/debug.log")
    print("="*60)

    rcon.close()

if __name__ == '__main__':
    main()
