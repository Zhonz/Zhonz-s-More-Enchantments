#!/usr/bin/env python3
"""RCON client for testing Minecraft enchantment mod - fixed command syntax."""

import socket
import struct
import time

class RCONClient:
    def __init__(self, host='localhost', port=25575, password='test123'):
        self.host = host
        self.port = port
        self.password = password
        self.sock = None

    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(10)
        self.sock.connect((self.host, self.port))
        self._send(3, self.password)
        self._recv()
        return True

    def _send(self, pid, data):
        if isinstance(data, str):
            data = data.encode('utf-8')
        payload = struct.pack('<ii', pid, pid) + data + b'\x00\x00'
        length = len(payload)
        packet = struct.pack('<i', length) + payload
        self.sock.sendall(packet)

    def _recv(self):
        length_data = self._recv_exact(4)
        length = struct.unpack('<i', length_data)[0]
        body = self._recv_exact(length)
        if len(body) < 8:
            return -1, ''
        request_id = struct.unpack('<i', body[:4])[0]
        payload = body[8:]
        null_idx = payload.find(b'\x00')
        if null_idx >= 0:
            payload = payload[:null_idx]
        return request_id, payload.decode('utf-8', errors='replace')

    def _recv_exact(self, n):
        data = b''
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                raise ConnectionError('Connection closed')
            data += chunk
        return data

    def command(self, cmd):
        self._send(2, cmd)
        _, response = self._recv()
        return response

    def close(self):
        if self.sock:
            self.sock.close()
            self.sock = None


def run_tests():
    rcon = RCONClient()
    try:
        rcon.connect()
        print("Connected to RCON")
    except Exception as e:
        print(f"Failed to connect: {e}")
        return

    def cmd(c):
        print(f"> {c}")
        r = rcon.command(c)
        print(f"< {r}")
        return r

    # Setup
    cmd("gamerule keepInventory true")

    # ====== Test 1: Deep Sea's Grace (深海的供养) ======
    # 使用zhonztest命令来装备和测试，因为item replace附魔语法在1.21.1不兼容
    print("\n=== Test 1: Deep Sea's Grace (深海的供养) ===")
    cmd("summon zombie 0 64 0 {CustomName:'\"深海的供养\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # Equip deep_seas_grace on chest using zhonztest
    cmd("zhonztest equiparmor @e[type=zombie,limit=1] chest zhonz_more_enchantments:deep_seas_grace 1")
    time.sleep(0.3)
    # Check initial health
    cmd("data get entity @e[type=zombie,limit=1] Health")
    # Damage the zombie
    cmd("zhonztest damage @e[type=zombie,limit=1] 10")
    time.sleep(1.0)  # Wait for tick-scheduled heal
    # Check health after - should be healed by 20% of max health (4.0)
    r = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Expected: 20 - 10 + 4 = 14.0 (if deep_seas_grace heals 20% of max)

    # ====== Test 2: Shell Strip (剥壳) ======
    print("\n=== Test 2: Shell Strip (剥壳) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"剥壳\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # First attack - should do extra damage based on percentage reduction
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:shell_strip 1")
    time.sleep(0.3)
    r1 = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Second attack - damage should be higher due to stacking
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:shell_strip 1")
    time.sleep(0.3)
    r2 = cmd("data get entity @e[type=zombie,limit=1] Health")

    # ====== Test 3: Grievous Wound (重伤) ======
    print("\n=== Test 3: Grievous Wound (重伤) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"重伤\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # Attack with grievous_wound - should reduce healing
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:grievous_wound 1")
    time.sleep(0.3)
    health_before = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Try healing with instant health - should be reduced by 30%
    cmd("effect give @e[type=zombie,limit=1] instant_health 1 0 true")
    time.sleep(0.5)
    health_after = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Without grievous wound: instant_health level 0 heals 4.0
    # With grievous wound: heals 4.0 * 0.7 = 2.8

    # ====== Test 4: Finale (终结) ======
    print("\n=== Test 4: Finale (终结) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    # Spawn a zombie with more health
    cmd("summon zombie 0 64 0 {CustomName:'\"终结\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f,Attributes:[{Name:'minecraft:max_health',Base:100f}]}")
    time.sleep(0.5)
    # First attack with normal damage
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:finale 1")
    time.sleep(0.3)
    health1 = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Damage again to get below 20% health
    cmd("zhonztest damage @e[type=zombie,limit=1] 70")
    time.sleep(0.3)
    health2 = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Now attack with finale - should execute (instant kill when below 20%)
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:finale 1")
    time.sleep(0.5)
    r = cmd("data get entity @e[type=zombie,limit=1] Health")

    # ====== Test 5: Fool's Mask (假面的愚者) ======
    print("\n=== Test 5: Fool's Mask (假面的愚者) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"假面\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # Equip fool's mask on head
    cmd("zhonztest equiparmor @e[type=zombie,limit=1] head zhonz_more_enchantments:fools_mask 1")
    time.sleep(0.3)
    # Damage to trigger the effect
    cmd("zhonztest damage @e[type=zombie,limit=1] 5")
    time.sleep(0.5)
    # Check health and effects
    cmd("data get entity @e[type=zombie,limit=1] Health")
    cmd("data get entity @e[type=zombie,limit=1] active_effects")

    # ====== Test 6: Divine Protection (神护) ======
    print("\n=== Test 6: Divine Protection (神护) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"神护\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # Equip divine protection on chest
    cmd("zhonztest equiparmor @e[type=zombie,limit=1] chest zhonz_more_enchantments:divine_protection 1")
    time.sleep(0.3)
    # Damage to low health - should trigger divine protection (heal to full)
    cmd("zhonztest damage @e[type=zombie,limit=1] 18")
    time.sleep(1.0)
    r = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Should be back to 20.0 if divine protection triggers

    # ====== Test 7: Fishball (鱼丸) ======
    print("\n=== Test 7: Fishball (鱼丸) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    # Spawn two zombies near each other
    cmd("summon zombie 0 64 0 {CustomName:'\"鱼丸装备者\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    cmd("summon zombie 1 64 0 {CustomName:'\"鱼丸无装备\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # Equip fishball on zombie A
    cmd("zhonztest equiparmor @e[name=\"鱼丸装备者\"] chest zhonz_more_enchantments:fishball 1")
    time.sleep(0.3)
    # Damage zombie B (without fishball) - 30% should redirect to A
    cmd("zhonztest damage @e[name=\"鱼丸无装备\"] 10")
    time.sleep(0.5)
    r_a = cmd("data get entity @e[name=\"鱼丸装备者\"] Health")
    r_b = cmd("data get entity @e[name=\"鱼丸无装备\"] Health")

    # ====== Test 8: Area Strike (范围打击) ======
    print("\n=== Test 8: Area Strike (范围打击) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    # Spawn multiple zombies
    cmd("summon zombie 0 64 0 {CustomName:'\"范围打击目标\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    cmd("summon zombie 1 64 0 {CustomName:'\"范围打击目标2\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:area_strike 1")
    time.sleep(0.3)
    cmd("data get entity @e[name=\"范围打击目标\"] Health")
    cmd("data get entity @e[name=\"范围打击目标2\"] Health")

    # ====== Test 9: Blood Weep (血泣) ======
    print("\n=== Test 9: Blood Weep (血泣) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"血泣\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:blood_weep 1")
    time.sleep(0.3)
    # Check if zombie has wither effect
    cmd("data get entity @e[type=zombie,limit=1] active_effects")
    cmd("data get entity @e[type=zombie,limit=1] Health")

    # ====== Test 10: Crimson Hellfire (猩红地狱火) ======
    print("\n=== Test 10: Crimson Hellfire (猩红地狱火) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"猩红地狱火\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:crimson_hellfire 1")
    time.sleep(0.5)
    # Check if zombie is on fire
    cmd("data get entity @e[type=zombie,limit=1] Fire")
    cmd("data get entity @e[type=zombie,limit=1] Health")

    # Cleanup
    print("\n=== Cleanup ===")
    cmd("kill @e[type=zombie]")

    rcon.close()
    print("\nAll tests completed!")


if __name__ == '__main__':
    run_tests()
