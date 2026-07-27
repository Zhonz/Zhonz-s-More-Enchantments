#!/usr/bin/env python3
"""RCON client for testing Minecraft enchantment mod - no player dependency."""

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

    # Spawn dummy at 0,64,0
    print("\n=== Setup: Spawn test dummy ===")
    cmd("summon zombie 0 64 0 {CustomName:'\"测试假人\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)

    # Verify dummy exists
    r = cmd("data get entity @e[type=zombie,limit=1,sort=nearest] Health")
    time.sleep(0.3)

    # Test 1: Deep Sea's Grace (深海的供养) - damage then heal
    print("\n=== Test 1: Deep Sea's Grace (深海的供养) ===")
    # Equip deep_seas_grace on zombie chest
    cmd("item replace entity @e[type=zombie,limit=1] armor.chest with diamond_chestplate[enchantments={zhonz_more_enchantments:deep_seas_grace:1}]")
    time.sleep(0.3)
    # Check health before
    cmd("data get entity @e[type=zombie,limit=1] Health")
    # Damage
    cmd("damage @e[type=zombie,limit=1] 10 from magic")
    time.sleep(0.5)
    # Check health after - should be healed by 20% of max
    r = cmd("data get entity @e[type=zombie,limit=1] Health")
    time.sleep(0.3)

    # Test 2: Clean up and prepare for Shell Strip (剥壳)
    print("\n=== Test 2: Shell Strip (剥壳) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"测试假人2\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # Use zhonztest attack with shell_strip enchantment
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:shell_strip 1")
    time.sleep(0.3)
    cmd("data get entity @e[type=zombie,limit=1] Health")
    time.sleep(0.3)

    # Test 3: Grievous Wound (重伤)
    print("\n=== Test 3: Grievous Wound (重伤) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"测试假人3\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:grievous_wound 1")
    time.sleep(0.3)
    # Now try healing - should be reduced by 30%
    cmd("effect give @e[type=zombie,limit=1] instant_health 1 0 true")
    time.sleep(0.5)
    r = cmd("data get entity @e[type=zombie,limit=1] Health")

    # Test 4: Finale (终结) - damage below 20% should execute
    print("\n=== Test 4: Finale (终结) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"测试假人4\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:finale 1")
    time.sleep(0.3)
    r = cmd("data get entity @e[type=zombie,limit=1] Health")

    # Test 5: Fool's Mask (假面的愚者)
    print("\n=== Test 5: Fool's Mask (假面的愚者) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"测试假人5\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("item replace entity @e[type=zombie,limit=1] armor.head with diamond_helmet[enchantments={zhonz_more_enchantments:fools_mask:1}]")
    time.sleep(0.3)
    cmd("damage @e[type=zombie,limit=1] 5 from magic")
    time.sleep(0.5)
    r = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Check if effects were applied
    r = cmd("data get entity @e[type=zombie,limit=1] active_effects")

    # Test 6: Divine Protection (神护) - at low health should heal to full
    print("\n=== Test 6: Divine Protection (神护) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"测试假人6\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("item replace entity @e[type=zombie,limit=1] armor.chest with diamond_chestplate[enchantments={zhonz_more_enchantments:divine_protection:1}]")
    time.sleep(0.3)
    # Deal large damage to trigger divine protection
    cmd("damage @e[type=zombie,limit=1] 18 from magic")
    time.sleep(0.5)
    r = cmd("data get entity @e[type=zombie,limit=1] Health")

    # Test 7: Fishball (鱼丸) - damage redirect
    print("\n=== Test 7: Fishball (鱼丸) ===")
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"鱼丸A\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    cmd("summon zombie 0 64 1 {CustomName:'\"鱼丸B\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # Equip fishball on zombie A
    cmd("item replace entity @e[name=\"鱼丸A\"] armor.chest with diamond_chestplate[enchantments={zhonz_more_enchantments:fishball:1}]")
    time.sleep(0.3)
    # Damage zombie B (without fishball) - 30% should redirect to A
    cmd("damage @e[name=\"鱼丸B\"] 10 from magic")
    time.sleep(0.5)
    r = cmd("data get entity @e[name=\"鱼丸A\"] Health")
    r = cmd("data get entity @e[name=\"鱼丸B\"] Health")

    # Cleanup
    print("\n=== Cleanup ===")
    cmd("kill @e[type=zombie]")

    rcon.close()
    print("\nAll tests completed!")


if __name__ == '__main__':
    run_tests()
