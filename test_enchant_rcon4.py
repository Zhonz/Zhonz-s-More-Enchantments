#!/usr/bin/env python3
"""RCON test script - comprehensive enchantment testing."""

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

    results = {}

    # ====== Test 1: Deep Sea's Grace (深海的供养) ======
    print("\n" + "="*60)
    print("Test 1: Deep Sea's Grace (深海的供养) - 受伤后治疗")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"D1\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("zhonztest equiparmor @e[type=zombie,limit=1] chest zhonz_more_enchantments:deep_seas_grace 1")
    time.sleep(0.3)
    # Damage with playerAttack source to trigger LivingDamageEvent.Pre
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:deep_seas_grace 1")
    time.sleep(1.5)  # Wait for tick-scheduled heal
    r = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Expected: Take ~8 damage from diamond sword, then heal 5% of 20 = 1.0 on next tick
    # 20 - ~8 + 1.0 = ~13.0
    results['deep_seas_grace'] = r

    # ====== Test 2: Shell Strip (剥壳) ======
    print("\n" + "="*60)
    print("Test 2: Shell Strip (剥壳) - 护甲百分比递减")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"D2\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f,ArmorItems:[{id:'diamond_boots',Count:1},{id:'diamond_leggings',Count:1},{id:'diamond_chestplate',Count:1},{id:'diamond_helmet',Count:1}]}")
    time.sleep(0.5)
    # First attack
    r1 = cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:shell_strip 1")
    time.sleep(0.3)
    h1 = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Second attack - should do more damage due to stacking
    r2 = cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:shell_strip 1")
    time.sleep(0.3)
    h2 = cmd("data get entity @e[type=zombie,limit=1] Health")
    results['shell_strip'] = f"Attack1: {r1} Health1: {h1} | Attack2: {r2} Health2: {h2}"

    # ====== Test 3: Grievous Wound (重伤) ======
    print("\n" + "="*60)
    print("Test 3: Grievous Wound (重伤) - 治疗减少30%")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"D3\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # Attack with grievous wound
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:grievous_wound 1")
    time.sleep(0.3)
    h_before = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Try healing - should be reduced by 30%
    cmd("effect give @e[type=zombie,limit=1] instant_health 1 0 true")
    time.sleep(0.5)
    h_after = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Check for wither effect (grievous wound applies wither II)
    effects = cmd("data get entity @e[type=zombie,limit=1] active_effects")
    results['grievous_wound'] = f"Before heal: {h_before} | After heal: {h_after} | Effects: {effects}"

    # ====== Test 4: Finale (终结) ======
    print("\n" + "="*60)
    print("Test 4: Finale (终结) - 高攻击力武器造成巨额伤害")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"D4\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f,Attributes:[{Name:'minecraft:max_health',Base:100f}]}")
    time.sleep(0.5)
    r = cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:finale 1")
    # Finale with attack_damage >= 7 should deal 100000x damage = instant kill
    results['finale'] = r

    # ====== Test 5: Fool's Mask (假面的愚者) ======
    print("\n" + "="*60)
    print("Test 5: Fool's Mask (假面的愚者) - 受伤时随机效果")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"D5\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("zhonztest equiparmor @e[type=zombie,limit=1] head zhonz_more_enchantments:fools_mask 1")
    time.sleep(0.3)
    # Use playerAttack so that LivingDamageEvent.Pre fires with an attacker
    cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:fools_mask 1")
    time.sleep(0.5)
    health = cmd("data get entity @e[type=zombie,limit=1] Health")
    effects = cmd("data get entity @e[type=zombie,limit=1] active_effects")
    results['fools_mask'] = f"Health: {health} | Effects: {effects}"

    # ====== Test 6: Divine Protection (神护) ======
    print("\n" + "="*60)
    print("Test 6: Divine Protection (神护) - 死亡时回满")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"D6\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("zhonztest equiparmor @e[type=zombie,limit=1] chest zhonz_more_enchantments:divine_protection 1")
    time.sleep(0.3)
    # Kill the zombie - divine protection should prevent death and heal to full
    cmd("zhonztest damage @e[type=zombie,limit=1] 30")
    time.sleep(1.0)
    health = cmd("data get entity @e[type=zombie,limit=1] Health")
    results['divine_protection'] = health

    # ====== Test 7: Blood Weep (血泣) ======
    print("\n" + "="*60)
    print("Test 7: Blood Weep (血泣) - 扣血增伤")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"D7\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # FakePlayer starts at 20HP, blood_weep Lv1 costs 10HP for +20% damage
    r = cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:blood_weep 1")
    results['blood_weep'] = r

    # ====== Test 8: Area Strike (范围打击) ======
    print("\n" + "="*60)
    print("Test 8: Area Strike (范围打击) - 溅射伤害")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"D8A\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    cmd("summon zombie 1 64 0 {CustomName:'\"D8B\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # Attack first zombie with area_strike
    r = cmd("zhonztest attack @e[type=zombie,limit=1,sort=nearest] zhonz_more_enchantments:area_strike 1")
    time.sleep(0.3)
    # Check both zombies' health
    h1 = cmd("data get entity @e[type=zombie,limit=1,sort=nearest] Health")
    h2 = cmd("data get entity @e[type=zombie,limit=2] Health")
    results['area_strike'] = f"Attack: {r} | Nearest: {h1} | All: {h2}"

    # ====== Test 9: Sanction (制裁) ======
    print("\n" + "="*60)
    print("Test 9: Sanction (制裁) - 最大生命值百分比真实伤害")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"D9\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f,Attributes:[{Name:'minecraft:max_health',Base:100f}]}")
    time.sleep(0.5)
    r = cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:sanction 1")
    time.sleep(0.3)
    h = cmd("data get entity @e[type=zombie,limit=1] Health")
    # Sanction Lv1 deals 1% max health as true damage = 1.0 extra
    results['sanction'] = f"Attack: {r} | Health: {h}"

    # ====== Test 10: Crimson Hellfire (猩红地狱火) - per-tick aura ======
    print("\n" + "="*60)
    print("Test 10: Crimson Hellfire (猩红地狱火) - 持续光环伤害")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    # This is a per-tick enchantment that damages nearby mobs every 40 ticks
    # It requires a PLAYER wearing it, not a zombie. We can't test this without a real player.
    print("SKIP: Crimson Hellfire is a per-tick player aura, cannot test without real player")
    results['crimson_hellfire'] = "SKIPPED - requires real player for tick events"

    # ====== Test 11: Supreme Art (至高之术) ======
    print("\n" + "="*60)
    print("Test 11: Supreme Art (至高之术) - 伤害加成+范围增加")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"D11\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    r = cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:supreme_art 1")
    time.sleep(0.3)
    h = cmd("data get entity @e[type=zombie,limit=1] Health")
    results['supreme_art'] = f"Attack: {r} | Health: {h}"

    # ====== Test 12: Charger (冲锋) ======
    print("\n" + "="*60)
    print("Test 12: Charger (冲锋) - 移动速度增伤")
    print("="*60)
    cmd("kill @e[type=zombie]")
    time.sleep(0.3)
    cmd("summon zombie 0 64 0 {CustomName:'\"D12\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    # FakePlayer isn't moving, so charger shouldn't add much damage
    r = cmd("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:charger 1")
    results['charger'] = r

    # Cleanup
    print("\n=== Cleanup ===")
    cmd("kill @e[type=zombie]")

    # Summary
    print("\n" + "="*60)
    print("TEST RESULTS SUMMARY")
    print("="*60)
    for name, result in results.items():
        print(f"\n{name}: {result}")

    rcon.close()
    print("\nAll tests completed!")


if __name__ == '__main__':
    run_tests()
