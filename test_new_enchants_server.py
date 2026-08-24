#!/usr/bin/env python3
"""测试 血路拓成坦途 和 智能图腾 附魔"""
import socket
import struct
import time
import sys

class RCONClient:
    def __init__(self, host='127.0.0.1', port=25575, password='test123'):
        self.host = host; self.port = port; self.password = password
        self.sock = None; self.request_id = 0

    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(20)
        self.sock.connect((self.host, self.port))
        self._send_packet(3, self.password)
        resp = self._recv_packet()
        if resp[0] != 2: raise Exception(f"Login failed: {resp}")
        print("[RCON] Connected")

    def _send_packet(self, packet_type, data):
        self.request_id += 1
        payload = struct.pack('<iii', self.request_id, packet_type, 0) + data.encode('utf-8') + b'\x00\x00'
        self.sock.send(struct.pack('<i', len(payload)) + payload)

    def _recv_packet(self):
        try:
            ld = self.sock.recv(4)
            if len(ld) < 4: return (0, "")
            length = struct.unpack('<i', ld)[0]
            if length <= 0 or length > 4110: return (0, "")
            data = b''
            while len(data) < length:
                chunk = self.sock.recv(length - len(data))
                if not chunk: break
                data += chunk
            if len(data) < 8: return (0, "")
            req_id, req_type = struct.unpack('<ii', data[:8])
            payload = data[8:]
            while payload.endswith(b'\x00'): payload = payload[:-1]
            return (req_type, payload.decode('utf-8', errors='replace'))
        except Exception as e: return (0, str(e))

    def send(self, cmd, wait=0.8):
        print(f"\n>>> {cmd}")
        self._send_packet(2, cmd)
        time.sleep(wait)
        resp = self._recv_packet()
        out = resp[1]
        # strip section color codes
        import re
        clean = re.sub(r'§.', '', out)
        print(f"<<< {clean[:1500]}")
        return clean

    def close(self):
        if self.sock: self.sock.close()

PLAYER = "TestPlayer"
rcon = RCONClient()
try:
    rcon.connect()
except Exception as e:
    print(f"RCON连接失败: {e}")
    sys.exit(1)

print("\n" + "="*70)
print("PART 1: 测试\"血路拓成坦途\" (Blood Path)")
print("="*70)

# 准备：清理旧假人/僵尸
rcon.send("kill @e[type=zombie]")
time.sleep(0.3)
rcon.send("kill @e[type=player,name=!TestPlayer]")
time.sleep(0.5)

# 1) 确认附魔存在：enchant列表检查附魔id
rcon.send(f"execute as {PLAYER} run enchant @s zhonz_more_enchantments:blood_path")
time.sleep(0.5)

# 2) 生成假人并给予铁剑+附魔 + 手动设置击杀数
rcon.send(f"execute as {PLAYER} run give @s iron_sword")
time.sleep(0.3)
rcon.send(f"execute as {PLAYER} run enchant @s zhonz_more_enchantments:blood_path 1")
time.sleep(0.3)
rcon.send(f"execute as {PLAYER} at @s run bloodpathtest zombie 1000")
time.sleep(0.5)

# 3) 生成僵尸并伤害 (设置击杀数后每只僵尸伤害应+100%，即2倍)
rcon.send("summon zombie 100 64 100")
time.sleep(0.5)
zombie = "@e[type=zombie,limit=1]"
rcon.send(f"zhonztest info {zombie}")
rcon.send(f"zhonztest damage {zombie} 5 with_blood_path_zombie_kills=1000")

# 4) 测试击杀记录：手动打死一只，确认计数增加
rcon.send(f"zhonztest kill_as_player @e[type=zombie,limit=1] {PLAYER}")
time.sleep(0.5)
rcon.send(f"execute as {PLAYER} run bloodpathtest zombie 100000")
time.sleep(0.3)

# 5) 极端高击杀数测试 (100000 = +10000% 伤害 = 101倍伤害)
rcon.send("summon zombie 102 64 102")
time.sleep(0.5)
rcon.send(f"zhonztest damage @e[type=zombie,x=102,limit=1] 2 with_blood_path_zombie_kills=100000")

# 6) 不同生物类型隔离：对zombie有100000击杀但对pig 0击杀
rcon.send("summon pig 104 64 104")
time.sleep(0.5)
rcon.send(f"zhonztest damage @e[type=pig,limit=1] 5 blood_path_pig_check")

# 清理
rcon.send("kill @e[type=zombie]"); rcon.send("kill @e[type=pig]")

print("\n" + "="*70)
print("PART 2: 测试\"智能图腾\" (Smart Totem)")
print("="*70)

# 1) 给予钻石胸甲并附魔智能图腾 + 不死图腾（放到第4格=背包内，非主手非副手）
rcon.send(f"execute as {PLAYER} run give @s diamond_chestplate")
time.sleep(0.3)
rcon.send(f"execute as {PLAYER} run enchant @s zhonz_more_enchantments:smart_totem 1")
time.sleep(0.3)
# 直接给1个不死图腾到背包
rcon.send(f"execute as {PLAYER} run give @s totem_of_undying 1")
time.sleep(0.5)

# 2) 检查装备状态
rcon.send(f"execute as {PLAYER} run say Smart Totem test start. Check: chest enchanted? totem in inv?")

# 3) 将玩家血量设为0.1，随后造成巨大伤害应触发图腾
rcon.send(f"execute as {PLAYER} run attribute @s minecraft:generic.max_health base set 40")
time.sleep(0.2)
rcon.send(f"execute as {PLAYER} run effect give @s minecraft:instant_damage 1 10 true")
time.sleep(0.5)
# 如果成功触发，玩家应该活着且有再生/吸收效果
rcon.send(f"execute as {PLAYER} if @s[health=0] run say FAIL: SMART_TOTEM_DID_NOT_TRIGGER")
rcon.send(f"execute as {PLAYER} if @s[health=1] run say SUCCESS: totem set health to 1 (standard totem trigger)")
rcon.send(f"execute as {PLAYER} run effect list @s")
rcon.send(f"execute as {PLAYER} run clear @s totem_of_undying")
time.sleep(0.3)

# 4) 再次测试无胸甲附魔时：不应触发（totem不在主副手中）
rcon.send(f"execute as {PLAYER} run item replace entity @s armor.chest with air")
time.sleep(0.3)
rcon.send(f"execute as {PLAYER} run give @s totem_of_undying 1")
time.sleep(0.3)
rcon.send(f"execute as {PLAYER} run effect give @s minecraft:instant_damage 1 10 true")
time.sleep(0.5)
rcon.send(f"execute as {PLAYER} unless @s[health=0] run say INFO: still alive (expected if vanilla totem or respawn)")
rcon.send(f"execute as {PLAYER} run say End of Smart Totem tests")

rcon.close()
print("\n[DONE] All tests sent")
