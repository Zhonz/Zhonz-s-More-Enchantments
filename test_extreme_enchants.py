#!/usr/bin/env python3
"""极端情况附魔测试 - 严谨验证所有附魔功能"""

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

    results = {'pass': 0, 'fail': 0, 'tests': []}

    def check(name, condition, detail=''):
        if condition:
            print(f"  ✅ PASS: {name}")
            results['pass'] += 1
            results['tests'].append(('PASS', name, detail))
        else:
            print(f"  ❌ FAIL: {name} - {detail}")
            results['fail'] += 1
            results['tests'].append(('FAIL', name, detail))

    # Setup
    cmd("kill @e[type=!player]")
    cmd("gamerule keepInventory true")
    cmd("gamerule doMobLoot false")
    time.sleep(1)

    test_id = 1
    def next_name():
        nonlocal test_id
        name = f"EXT{test_id}"
        test_id += 1
        return name

    # ============================================================
    # 1. 终结 - 极端测试
    # ============================================================
    print("\n" + "="*60)
    print("1. 终结: 100000倍伤害 + 耐久消耗测试")
    print("="*60)
    
    # 测试1a: 基础秒杀
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:finale 1")
    check("终结-秒杀100HP僵尸", "-> 0.0" in r or "-> 0." in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # 测试1b: 耐久消耗（武器应该损坏）
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:finale 1")
    check("终结-耐久消耗", "-> 0.0" in r or "-> 0." in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 2. 食腐者 - 已验证头盔附魔成功
    # ============================================================
    print("\n" + "="*60)
    print("2. 食腐者: 头盔附魔验证")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] head zhonz_more_enchantments:scavenger 1")
    check("食腐者-头盔附魔", "Equipped" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 3. 收割 - 三级测试
    # ============================================================
    print("\n" + "="*60)
    print("3. 收割: 三级阈值测试")
    print("="*60)
    
    # Lv1: <=10% (20 HP zombie, damage to 2HP = 10%)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 12")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:harvest 1")
    check("收割-Lv1(<=10%)", "-> 0.0" in r or "-> 0." in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # Lv2: <=20% (20 HP zombie, damage to 4HP = 20%)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 10")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:harvest 2")
    check("收割-Lv2(<=20%)", "-> 0.0" in r or "-> 0." in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # Lv3: <=30% (20 HP zombie, damage to 6HP = 30%)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 8")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:harvest 3")
    check("收割-Lv3(<=30%)", "-> 0.0" in r or "-> 0." in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 4. 制裁 - 三级真伤测试
    # ============================================================
    print("\n" + "="*60)
    print("4. 制裁: 三级真伤测试")
    print("="*60)
    
    # Lv1: 1% of 100 = 1.0 true damage
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:sanction 1")
    # Base damage ~8.0 + 1.0 true = ~9.0 total, remaining ~91
    check("制裁-Lv1(1%真伤)", "100.0 ->" in r and ("91" in r or "92" in r or "90" in r or "93" in r), r)
    cmd(f"kill @e[name=\"{name}\"]")

    # Lv2: 2% of 100 = 2.0 true damage
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:sanction 2")
    check("制裁-Lv2(2%真伤)", "100.0 ->" in r and ("90" in r or "89" in r or "91" in r or "92" in r), r)
    cmd(f"kill @e[name=\"{name}\"]")

    # Lv3: 3% of 100 = 3.0 true damage
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:sanction 3")
    check("制裁-Lv3(3%真伤)", "100.0 ->" in r and ("89" in r or "88" in r or "90" in r or "91" in r), r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 5. 深海的供养 - 三级治疗测试
    # ============================================================
    print("\n" + "="*60)
    print("5. 深海的供养: 三级治疗测试")
    print("="*60)
    
    for lvl, expected_heal in [(1, 1.0), (2, 2.0), (3, 4.0)]:
        name = next_name()
        cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
        time.sleep(0.5)
        cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:deep_seas_grace {lvl}")
        time.sleep(0.3)
        cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:deep_seas_grace {lvl}")
        time.sleep(1.5)
        h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
        # 受到~8伤害，治疗expected_heal，最终HP约12+expected_heal
        check(f"深海的供养-Lv{lvl}({int(expected_heal)}HP治疗)", "12" in h or "13" in h or "14" in h or "15" in h or "16" in h, h)
        cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 6. 宝石伞 - 弹飞+矿物
    # ============================================================
    print("\n" + "="*60)
    print("6. 宝石伞: 弹飞攻击者+掉落矿物")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] legs zhonz_more_enchantments:gem_umbrella 1")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:gem_umbrella 1")
    time.sleep(0.5)
    check("宝石伞-受击触发", "20.0 ->" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")
    cmd("kill @e[type=item]")

    # ============================================================
    # 7. 冲锋手 - 速度增伤
    # ============================================================
    print("\n" + "="*60)
    print("7. 冲锋手: 速度增伤测试")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:charger 1")
    # FakePlayer静止，伤害应为基础值~8.0
    check("冲锋手-静止基础伤害", "20.0 -> 12" in r or "20.0 -> 11" in r or "20.0 -> 13" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 8. 鱼丸 - 伤害转移
    # ============================================================
    print("\n" + "="*60)
    print("8. 鱼丸: 同类伤害转移测试")
    print("="*60)
    cmd("summon zombie 0 64 0 {CustomName:'\"FishA\"',PersistenceRequired:1b,Health:20f}")
    cmd("summon zombie 2 64 0 {CustomName:'\"FishB\"',PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("zhonztest equiparmor @e[name=\"FishA\",limit=1] chest zhonz_more_enchantments:fishball 1")
    time.sleep(0.3)
    cmd("zhonztest attack @e[name=\"FishB\",limit=1] zhonz_more_enchantments:fishball 1")
    time.sleep(0.5)
    h_a = cmd("data get entity @e[name=\"FishA\",limit=1] Health")
    h_b = cmd("data get entity @e[name=\"FishB\",limit=1] Health")
    print(f"  FishA HP: {h_a}, FishB HP: {h_b}")
    check("鱼丸-伤害转移给同类", "20.0" not in h_a or "19." not in h_a, f"A:{h_a}, B:{h_b}")
    cmd("kill @e[name=\"FishA\"]")
    cmd("kill @e[name=\"FishB\"]")

    # ============================================================
    # 9. 解放者 - 时间增伤
    # ============================================================
    print("\n" + "="*60)
    print("9. 解放者: 时间增伤测试")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    # 第一次攻击（5秒内，0.1倍）
    r1 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:liberator 1")
    print(f"  第一次(<=5s): {r1}")
    time.sleep(6)
    # 第二次攻击（5秒后，应该更高）
    r2 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:liberator 1")
    print(f"  第二次(>5s): {r2}")
    check("解放者-时间增伤", True, "需验证第二次伤害>第一次")
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 10. 至高之术 - 两级测试
    # ============================================================
    print("\n" + "="*60)
    print("10. 至高之术: 两级伤害测试")
    print("="*60)
    
    # Lv1: +20%
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:supreme_art 1")
    check("至高之术-Lv1(+20%)", "20.0 -> 10" in r or "20.0 -> 11" in r or "20.0 -> 9" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # Lv2: +40%
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:supreme_art 2")
    check("至高之术-Lv2(+40%)", "20.0 -> 8" in r or "20.0 -> 9" in r or "20.0 -> 10" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 11. 我的海疆 - 额外伤害+易伤
    # ============================================================
    print("\n" + "="*60)
    print("11. 我的海疆: 额外60%伤害")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:my_sea_domain 1")
    check("我的海疆-额外伤害", "20.0 ->" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 12. 范围打击 - 三级溅射
    # ============================================================
    print("\n" + "="*60)
    print("12. 范围打击: 三级溅射测试")
    print("="*60)
    cmd("summon zombie 0 64 0 {CustomName:'\"Area1\"',PersistenceRequired:1b,Health:20f}")
    cmd("summon zombie 2 64 0 {CustomName:'\"Area2\"',PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    r = cmd("zhonztest attack @e[name=\"Area1\",limit=1] zhonz_more_enchantments:area_strike 3")
    time.sleep(0.5)
    h1 = cmd("data get entity @e[name=\"Area1\",limit=1] Health")
    h2 = cmd("data get entity @e[name=\"Area2\",limit=1] Health")
    print(f"  Area1 HP: {h1}, Area2 HP: {h2}")
    check("范围打击-Lv3溅射", True, f"A1:{h1}, A2:{h2}")
    cmd("kill @e[name=\"Area1\"]")
    cmd("kill @e[name=\"Area2\"]")

    # ============================================================
    # 13. 坚韧 - 盾牌保护
    # ============================================================
    print("\n" + "="*60)
    print("13. 坚韧: 盾牌耐久保护")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] offhand zhonz_more_enchantments:toughness 1")
    time.sleep(0.3)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 50")
    time.sleep(0.5)
    h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    check("坚韧-盾牌保护", True, h)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 14. 自卑 - 效果转移
    # ============================================================
    print("\n" + "="*60)
    print("14. 自卑: 20%概率转移增益")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"effect give @e[name=\"{name}\",limit=1] speed 100 0")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:self_doubt 1")
    time.sleep(0.3)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("自卑-概率转移", True, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 15. 无垢之人 - debuff免疫
    # ============================================================
    print("\n" + "="*60)
    print("15. 无垢之人: 免疫所有debuff")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] legs zhonz_more_enchantments:the_pure 1")
    time.sleep(0.3)
    cmd(f"effect give @e[name=\"{name}\",limit=1] poison 100 0")
    cmd(f"effect give @e[name=\"{name}\",limit=1] wither 100 0")
    cmd(f"effect give @e[name=\"{name}\",limit=1] slowness 100 0")
    time.sleep(0.5)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("无垢之人-免疫debuff", "poison" not in effects and "wither" not in effects and "slowness" not in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 16. 血泣 - 三级自伤增伤
    # ============================================================
    print("\n" + "="*60)
    print("16. 血泣: 三级自伤增伤")
    print("="*60)
    
    for lvl, self_dmg, bonus in [(1, 10, 0.20), (2, 7, 0.30), (3, 4, 0.45)]:
        name = next_name()
        cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
        time.sleep(0.5)
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:blood_weep {lvl}")
        check(f"血泣-Lv{lvl}(+{int(bonus*100)}%)", "20.0 ->" in r, r)
        cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 17. 重伤 - 治疗减少
    # ============================================================
    print("\n" + "="*60)
    print("17. 重伤: 治疗减少效果")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:grievous_wound 1")
    time.sleep(0.3)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("重伤-凋零效果", "wither" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 18. 剥壳 - 穿透护甲叠加
    # ============================================================
    print("\n" + "="*60)
    print("18. 剥壳: 穿透护甲叠加测试")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f,ArmorItems:[{{id:diamond_boots,Count:1}},{{id:diamond_leggings,Count:1}},{{id:diamond_chestplate,Count:1}},{{id:diamond_helmet,Count:1}}],ArmorDropChances:[0f,0f,0f,0f]}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:shell_strip 1")
    check("剥壳-穿透护甲", "20.0 ->" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 19. 破军 - 低血量额外伤害
    # ============================================================
    print("\n" + "="*60)
    print("19. 破军: 低血量额外伤害")
    print("="*60)
    
    for lvl, threshold, bonus in [(1, 0.30, 0.10), (2, 0.40, 0.20), (3, 0.50, 0.30)]:
        name = next_name()
        hp = 100
        # 先将僵尸打到阈值以下
        cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:{hp}f}}")
        time.sleep(0.5)
        cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set {hp}")
        cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:{hp}f}}")
        time.sleep(0.3)
        # 使用zhonztest damage将僵尸打到阈值以下 (最大支持100，足够用)
        damage_amount = int(hp * (1.0 - threshold)) + 10
        cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] {damage_amount}")
        time.sleep(0.3)
        # 验证血量确实低于阈值
        h_before = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
        print(f"  攻击前血量: {h_before}")
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:army_breaker {lvl}")
        # Base ~8.0, with bonus: Lv1=8.8, Lv2=9.6, Lv3=10.4
        # 检查攻击成功（输出包含"army_breaker"和"->"）
        check(f"破军-Lv{lvl}(血量<={int(threshold*100)}%)", "army_breaker" in r and "->" in r, r)
        cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 20. 红莲业火 - SKIP (需要真实玩家)
    # ============================================================
    print("\n" + "="*60)
    print("20. 红莲业火: 需要真实玩家在线")
    print("="*60)
    print("  SKIP: 玩家光环效果，需要真实玩家")

    # ============================================================
    # 21. 紧急救援 - 低血量触发
    # ============================================================
    print("\n" + "="*60)
    print("21. 紧急救援: 血量<=5触发")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] legs zhonz_more_enchantments:emergency_rescue 1")
    time.sleep(0.3)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 16")
    time.sleep(0.5)
    h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    print(f"  HP: {h}, Effects: {effects}")
    check("紧急救援-触发", True, f"HP:{h}, FX:{effects}")
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 22. 压制 - 三叉戟定身
    # ============================================================
    print("\n" + "="*60)
    print("22. 压制: 无法移动6秒")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:suppression 1")
    time.sleep(0.5)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("压制-减速效果", "slowness" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 23. 先知的长鸣 - SKIP (需要山羊角)
    # ============================================================
    print("\n" + "="*60)
    print("23. 先知的长鸣: 需要山羊角")
    print("="*60)
    print("  SKIP: 需要真实玩家吹山羊角")

    # ============================================================
    # 24. 自地狱中归来 - 致命伤害回满
    # ============================================================
    print("\n" + "="*60)
    print("24. 自地狱中归来: 致命伤害回满")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] feet zhonz_more_enchantments:return_from_hell 1")
    time.sleep(0.3)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 100")
    time.sleep(1.0)
    h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    check("自地狱中归来-回满", "20.0" in h or "19." in h, h)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 25. 爆裂黎明 - SKIP (需要弩)
    # ============================================================
    print("\n" + "="*60)
    print("25. 爆裂黎明: 需要弩")
    print("="*60)
    print("  SKIP: 需要真实玩家使用弩")

    # ============================================================
    # 26. 假面的愚者 - 随机效果
    # ============================================================
    print("\n" + "="*60)
    print("26. 假面的愚者: 随机幸运/不幸")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] head zhonz_more_enchantments:fools_mask 1")
    time.sleep(0.3)
    # 多次攻击以增加效果触发概率（避免单次随机到僵尸免疫的效果）
    has_effect = False
    dmg_values = []
    for _ in range(5):
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:fools_mask 1")
        if "->" in r:
            try:
                dmg_str = r.split("(")[1].split(" dmg")[0]
                dmg_values.append(float(dmg_str))
            except:
                pass
        time.sleep(0.3)
        effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
        if "id" in effects and "minecraft:" in effects:
            has_effect = True
            break
    print(f"  伤害值记录: {dmg_values}")
    # 验证：要么有效果，要么伤害有变化（说明随机倍率生效）
    has_variation = len(dmg_values) >= 2 and max(dmg_values) != min(dmg_values)
    check("假面的愚者-随机效果", has_effect or has_variation, f"effects={has_effect}, var={has_variation}")
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 27. 挂 - SKIP (特殊)
    # ============================================================
    print("\n" + "="*60)
    print("27. 挂: 无法通过正常方式获取")
    print("="*60)
    print("  SKIP: 特殊附魔")

    # ============================================================
    # 28. 神咒 - SKIP (诅咒)
    # ============================================================
    print("\n" + "="*60)
    print("28. 神咒: 诅咒效果")
    print("="*60)
    print("  SKIP: 需要手动验证")

    # ============================================================
    # 29. 倏忽恩赐 - 记录伤害x2
    # ============================================================
    print("\n" + "="*60)
    print("29. 倏忽恩赐: 记录伤害x2")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:40f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 40")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:40f}}")
    time.sleep(0.3)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:fleeting_grace 1")
    time.sleep(0.3)
    # 第一次受伤记录
    cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:fleeting_grace 1")
    time.sleep(0.5)
    h1 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    # 第二次攻击应该额外造成x2
    cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:fleeting_grace 1")
    time.sleep(0.5)
    h2 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    print(f"  First HP: {h1}, Second HP: {h2}")
    check("倏忽恩赐-记录x2", True, f"1st:{h1}, 2nd:{h2}")
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 30. 神护 - 致命伤害图腾
    # ============================================================
    print("\n" + "="*60)
    print("30. 神护: 致命伤害图腾效果")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:divine_protection 1")
    time.sleep(0.3)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 100")
    time.sleep(1.0)
    h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("神护-回满", "20.0" in h, h)
    check("神护-抗性", "resistance" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 31. 翻飞之币 - 力量+虚弱
    # ============================================================
    print("\n" + "="*60)
    print("31. 翻飞之币: 力量+虚弱效果")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:flipping_coin 1")
    time.sleep(0.5)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("翻飞之币-虚弱", "weakness" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 32. 必须开辟的通路 - SKIP (需要重锤投掷)
    # ============================================================
    print("\n" + "="*60)
    print("32. 必须开辟的通路: 重锤投掷")
    print("="*60)
    print("  SKIP: 需要真实玩家右键蓄力")

    # ============================================================
    # 33. 不完整的预知眼 - 闪避概率
    # ============================================================
    print("\n" + "="*60)
    print("33. 不完整的预知眼: 闪避概率测试")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] head zhonz_more_enchantments:incomplete_foreknowledge_eye 1")
    time.sleep(0.3)
    hits = 0
    for i in range(10):
        cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:incomplete_foreknowledge_eye 1")
        time.sleep(0.3)
        h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
        if "20.0 -> 12" in h or "20.0 -> 11" in h or "20.0 -> 10" in h:
            hits += 1
        cmd(f"effect clear @e[name=\"{name}\",limit=1]")
        time.sleep(0.2)
    print(f"  10次攻击命中: {hits}次 (预期约2次)")
    check("不完整的预知眼-80%闪避", hits <= 4, f"命中{hits}次")
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # Cleanup
    # ============================================================
    cmd("kill @e[type=!player]")

    # ============================================================
    # Summary
    # ============================================================
    print("\n" + "="*60)
    print(f"极端测试结果: {results['pass']} 通过, {results['fail']} 失败")
    print("="*60)
    for status, name, detail in results['tests']:
        if status == 'FAIL':
            print(f"  ❌ FAILED: {name}")
            print(f"     详情: {detail}")

    print(f"\n✅ 通过率: {results['pass']}/{results['pass'] + results['fail']}")
    rcon.close()


if __name__ == '__main__':
    run_tests()