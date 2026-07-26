#!/usr/bin/env python3
"""RCON测试脚本 - 验证附魔注册和功能"""
import socket
import struct
import time

class RCONClient:
    def __init__(self, host='127.0.0.1', port=25575, password='test123'):
        self.host = host
        self.port = port
        self.password = password
        self.sock = None
        self.request_id = 0

    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(10)
        self.sock.connect((self.host, self.port))
        self._send_packet(3, self.password)
        resp = self._recv_packet()
        if resp[0] != 2:
            raise Exception("Login failed")
        print("[RCON] Connected")

    def _send_packet(self, packet_type, data):
        self.request_id += 1
        payload = struct.pack('<iii', self.request_id, packet_type, 0) + data.encode('utf-8') + b'\x00\x00'
        self.sock.send(struct.pack('<i', len(payload)) + payload)

    def _recv_packet(self):
        length_data = self.sock.recv(4)
        length = struct.unpack('<i', length_data)[0]
        data = b''
        while len(data) < length:
            data += self.sock.recv(length - len(data))
        req_id, req_type = struct.unpack('<ii', data[:8])
        payload = data[8:-2].decode('utf-8', errors='replace')
        return (req_type, payload)

    def send(self, cmd):
        self._send_packet(2, cmd)
        resp = self._recv_packet()
        return resp[1]

    def close(self):
        if self.sock:
            self.sock.close()

def main():
    rcon = RCONClient()
    rcon.connect()

    # 基本服务器状态
    print("=== 服务器状态 ===")
    print(f"List: {rcon.send('list')}")

    # 设置游戏模式为创造模式
    print(f"\nSet gamemode: {rcon.send('gamemode creative')}")

    # 给玩家一个金苹果用于测试
    print(f"\nGive golden apple: {rcon.send('give @s golden_apple')}")

    # 测试1: 终结附魔 - 给一把带有终结附魔的钻石剑
    print("\n=== 测试终结附魔 ===")
    cmd = 'give @s diamond_sword[enchantments={"zhonz_more_enchantments:finale":1},damage=0]'
    resp = rcon.send(cmd)
    print(f"Finale sword: {resp!r}")
    time.sleep(0.3)

    # 测试2: 深海的供养
    print("\n=== 测试深海的供养 ===")
    cmd = 'give @s diamond_chestplate[enchantments={"zhonz_more_enchantments:deep_seas_grace":3},damage=0]'
    resp = rcon.send(cmd)
    print(f"Deep Sea's Grace chestplate: {resp!r}")
    time.sleep(0.3)

    # 测试3: 宝石伞
    print("\n=== 测试宝石伞 ===")
    cmd = 'give @s diamond_leggings[enchantments={"zhonz_more_enchantments:gem_umbrella":1},damage=0]'
    resp = rcon.send(cmd)
    print(f"Gem Umbrella leggings: {resp!r}")
    time.sleep(0.3)

    # 测试4: 不完整的预知眼
    print("\n=== 测试不完整的预知眼 ===")
    cmd = 'give @s diamond_helmet[enchantments={"zhonz_more_enchantments:incomplete_foreknowledge_eye":1},damage=0]'
    resp = rcon.send(cmd)
    print(f"Foreknowledge Eye helmet: {resp!r}")
    time.sleep(0.3)

    # 测试5: 鱼丸
    print("\n=== 测试鱼丸 ===")
    cmd = 'give @s diamond_chestplate[enchantments={"zhonz_more_enchantments:fishball":1},damage=0]'
    resp = rcon.send(cmd)
    print(f"Fishball chestplate: {resp!r}")
    time.sleep(0.3)

    # 测试6: 挂附魔（应该成功但无法通过正常方式获取）
    print("\n=== 测试挂附魔 ===")
    cmd = 'give @s diamond_sword[enchantments={"zhonz_more_enchantments:hang":1},damage=0]'
    resp = rcon.send(cmd)
    print(f"Hang sword: {resp!r}")
    time.sleep(0.3)

    # 测试7: 神咒
    print("\n=== 测试神咒 ===")
    cmd = 'give @s diamond_sword[enchantments={"zhonz_more_enchantments:divine_curse":1},damage=0]'
    resp = rcon.send(cmd)
    print(f"Divine Curse sword: {resp!r}")
    time.sleep(0.3)

    # 测试8: 生成僵尸用于战斗测试
    print("\n=== 生成僵尸用于战斗测试 ===")
    resp = rcon.send('summon zombie ~ ~ ~ {Health:20f,Attributes:[{Name:"minecraft:generic.max_health",Base:20f}]}')
    print(f"Summon zombie: {resp!r}")
    time.sleep(0.3)

    # 检查玩家位置
    print(f"\nPlayer position: {rcon.send('execute as @s run tp @s ~ ~ ~')}")

    # 检查玩家手中的物品
    print(f"\nPlayer inventory item: {rcon.send('item replace block ~ ~ ~ chest 0 with diamond_sword[enchantments={\"zhonz_more_enchantments:finale\":1}]')}")

    # 列出所有已注册的附魔
    print("\n=== 检查附魔注册 ===")
    # 通过enchant命令测试附魔是否有效
    resp = rcon.send('enchant @s zhonz_more_enchantments:finale 1')
    print(f"Enchant finale: {resp!r}")

    # 最终状态
    print(f"\nFinal list: {rcon.send('list')}")
    print(f"Seed: {rcon.send('seed')}")

    rcon.close()
    print("\n=== 测试完成 ===")

if __name__ == '__main__':
    main()
