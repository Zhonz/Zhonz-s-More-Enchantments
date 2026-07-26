#!/usr/bin/env python3
"""RCON测试脚本 - 测试ZZ的更多附魔mod"""
import socket
import struct
import time
import json

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
        # Login
        self._send_packet(3, self.password)
        resp = self._recv_packet()
        if resp[0] != 2:
            raise Exception("Login failed")
        print("[RCON] Connected and authenticated")

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

    def send_command(self, cmd):
        self._send_packet(2, cmd)
        resp = self._recv_packet()
        return resp[1]

    def close(self):
        if self.sock:
            self.sock.close()

def test_enchantments():
    rcon = RCONClient()
    rcon.connect()

    results = []

    # 测试1: 给玩家一个带有终结附魔的剑
    print("\n=== 测试1: 终结附魔 (Finale) ===")
    resp = rcon.send_command('give @p diamond_sword[enchantments={"zhonz_more_enchantments:finale":1},damage=0]')
    print(f"Give finale sword: {resp}")
    time.sleep(0.5)

    # 生成一个僵尸来测试
    resp = rcon.send_command('execute at @p run summon zombie ~ ~ ~ {Health:20}')
    print(f"Summon zombie: {resp}")
    time.sleep(1)

    # 检查玩家状态
    resp = rcon.send_command('data get entity @p Health')
    print(f"Player health: {resp}")

    # 测试2: 给玩家一个带有收割附魔的剑
    print("\n=== 测试2: 收割附魔 (Harvest) ===")
    resp = rcon.send_command('give @p diamond_sword[enchantments={"zhonz_more_enchantments:harvest":3},damage=0]')
    print(f"Give harvest sword: {resp}")
    time.sleep(0.5)

    # 测试3: 给玩家一个带有制裁附魔的剑
    print("\n=== 测试3: 制裁附魔 (Sanction) ===")
    resp = rcon.send_command('give @p diamond_sword[enchantments={"zhonz_more_enchantments:sanction":3},damage=0]')
    print(f"Give sanction sword: {resp}")
    time.sleep(0.5)

    # 测试4: 给玩家一个带有血泣附魔的剑
    print("\n=== 测试4: 血泣附魔 (Blood Weep) ===")
    resp = rcon.send_command('give @p diamond_sword[enchantments={"zhonz_more_enchantments:blood_weep":3},damage=0]')
    print(f"Give blood_weep sword: {resp}")
    time.sleep(0.5)

    # 测试5: 给玩家一个带有重伤附魔的剑
    print("\n=== 测试5: 重伤附魔 (Grievous Wound) ===")
    resp = rcon.send_command('give @p diamond_sword[enchantments={"zhonz_more_enchantments:grievous_wound":1},damage=0]')
    print(f"Give grievous_wound sword: {resp}")
    time.sleep(0.5)

    # 测试6: 给玩家一个带有剥壳附魔的剑
    print("\n=== 测试6: 剥壳附魔 (Shell Strip) ===")
    resp = rcon.send_command('give @p diamond_sword[enchantments={"zhonz_more_enchantments:shell_strip":1},damage=0]')
    print(f"Give shell_strip sword: {resp}")
    time.sleep(0.5)

    # 测试7: 给玩家一个带有破军附魔的剑
    print("\n=== 测试7: 破军附魔 (Army Breaker) ===")
    resp = rcon.send_command('give @p diamond_sword[enchantments={"zhonz_more_enchantments:army_breaker":3},damage=0]')
    print(f"Give army_breaker sword: {resp}")
    time.sleep(0.5)

    # 测试8: 给玩家一个带有至高之术附魔的剑
    print("\n=== 测试8: 至高之术 (Supreme Art) ===")
    resp = rcon.send_command('give @p diamond_sword[enchantments={"zhonz_more_enchantments:supreme_art":2},damage=0]')
    print(f"Give supreme_art sword: {resp}")
    time.sleep(0.5)

    # 测试9: 深海的供养盔甲
    print("\n=== 测试9: 深海的供养 (Deep Sea's Grace) ===")
    resp = rcon.send_command('give @p diamond_chestplate[enchantments={"zhonz_more_enchantments:deep_seas_grace":3},damage=0]')
    print(f"Give deep_seas_grace chestplate: {resp}")
    time.sleep(0.5)

    # 测试10: 宝石伞护腿
    print("\n=== 测试10: 宝石伞 (Gem Umbrella) ===")
    resp = rcon.send_command('give @p diamond_leggings[enchantments={"zhonz_more_enchantments:gem_umbrella":1},damage=0]')
    print(f"Give gem_umbrella leggings: {resp}")
    time.sleep(0.5)

    # 测试11: 假面的愚者头盔
    print("\n=== 测试11: 假面的愚者 (Fools Mask) ===")
    resp = rcon.send_command('give @p diamond_helmet[enchantments={"zhonz_more_enchantments:fools_mask":1},damage=0]')
    print(f"Give fools_mask helmet: {resp}")
    time.sleep(0.5)

    # 测试12: 无垢之人护腿
    print("\n=== 测试12: 无垢之人 (The Pure) ===")
    resp = rcon.send_command('give @p diamond_leggings[enchantments={"zhonz_more_enchantments:the_pure":1},damage=0]')
    print(f"Give the_pure leggings: {resp}")
    time.sleep(0.5)

    # 测试13: 红莲业火胸甲
    print("\n=== 测试13: 红莲业火 (Crimson Hellfire) ===")
    resp = rcon.send_command('give @p diamond_chestplate[enchantments={"zhonz_more_enchantments:crimson_hellfire":1},damage=0]')
    print(f"Give crimson_hellfire chestplate: {resp}")
    time.sleep(0.5)

    # 测试14: 自地狱中归来靴子
    print("\n=== 测试14: 自地狱中归来 (Return from Hell) ===")
    resp = rcon.send_command('give @p diamond_boots[enchantments={"zhonz_more_enchantments:return_from_hell":1},damage=0]')
    print(f"Give return_from_hell boots: {resp}")
    time.sleep(0.5)

    # 测试15: 不完整的预知眼头盔
    print("\n=== 测试15: 不完整的预知眼 (Incomplete Foreknowledge Eye) ===")
    resp = rcon.send_command('give @p diamond_helmet[enchantments={"zhonz_more_enchantments:incomplete_foreknowledge_eye":1},damage=0]')
    print(f"Give incomplete_foreknowledge_eye helmet: {resp}")
    time.sleep(0.5)

    # 测试16: 食腐者头盔
    print("\n=== 测试16: 食腐者 (Scavenger) ===")
    resp = rcon.send_command('give @p diamond_helmet[enchantments={"zhonz_more_enchantments:scavenger":1},damage=0]')
    print(f"Give scavenger helmet: {resp}")
    time.sleep(0.5)

    # 测试17: 鱼丸胸甲
    print("\n=== 测试17: 鱼丸 (Fishball) ===")
    resp = rcon.send_command('give @p diamond_chestplate[enchantments={"zhonz_more_enchantments:fishball":1},damage=0]')
    print(f"Give fishball chestplate: {resp}")
    time.sleep(0.5)

    # 测试18: 神护盔甲
    print("\n=== 测试18: 神护 (Divine Protection) ===")
    resp = rcon.send_command('give @p diamond_chestplate[enchantments={"zhonz_more_enchantments:divine_protection":1},damage=0]')
    print(f"Give divine_protection chestplate: {resp}")
    time.sleep(0.5)

    # 测试19: 神咒
    print("\n=== 测试19: 神咒 (Divine Curse) ===")
    resp = rcon.send_command('give @p diamond_sword[enchantments={"zhonz_more_enchantments:divine_curse":1},damage=0]')
    print(f"Give divine_curse sword: {resp}")
    time.sleep(0.5)

    # 测试20: 翻飞之币
    print("\n=== 测试20: 翻飞之币 (Flipping Coin) ===")
    resp = rcon.send_command('give @p diamond_sword[enchantments={"zhonz_more_enchantments:flipping_coin":1},damage=0]')
    print(f"Give flipping_coin sword: {resp}")
    time.sleep(0.5)

    # 检查所有附魔是否已注册
    print("\n=== 检查附魔注册 ===")
    enchantments = [
        "finale", "scavenger", "harvest", "sanction", "deep_seas_grace",
        "gem_umbrella", "charger", "fishball", "liberator", "supreme_art",
        "my_sea_domain", "area_strike", "toughness", "self_doubt", "the_pure",
        "blood_weep", "grievous_wound", "shell_strip", "army_breaker", "crimson_hellfire",
        "emergency_rescue", "suppression", "prophets_call", "return_from_hell",
        "explosive_dawn", "fools_mask", "hang", "divine_curse", "fleeting_grace",
        "divine_protection", "flipping_coin", "must_open_path", "incomplete_foreknowledge_eye"
    ]

    # 查看最近的日志
    resp = rcon.send_command('list')
    print(f"Players online: {resp}")

    # 检查是否有错误日志
    print("\n=== 服务器日志检查 ===")
    resp = rcon.send_command('seed')
    print(f"Server seed: {resp}")

    rcon.close()
    print("\n=== 测试完成 ===")

if __name__ == '__main__':
    test_enchantments()
