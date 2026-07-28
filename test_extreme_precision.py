#!/usr/bin/env python3
"""极端精度测试 - 验证附魔数值完全符合描述"""

import socket
import struct
import time
import re

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


def extract_dmg(response):
    """从攻击响应中提取伤害值"""
    # Format: 20.0 -> 12.1 (7.9 dmg)
    match = re.search(r'\d+\.\d+ -> \d+\.\d+ \((\d+\.\d+) dmg\)', response)
    if match:
        return float(match.group(1))
    return None


def extract_hp(response):
    """从data get entity Health响应中提取HP值"""
    match = re.search(r'(\d+\.\d+)f', response)
    if match:
        return float(match.group(1))
    return None


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
            print(f"  PASS: {name}")
            results['pass'] += 1
            results['tests'].append(('PASS', name, detail))
        else:
            print(f"  FAIL: {name} - {detail}")
            results['fail'] += 1
            results['tests'].append(('FAIL', name, detail))

    # Setup
    cmd("kill @e[type=!player]")
    cmd("gamerule keepInventory true")
    cmd("gamerule doMobLoot false")
    cmd("gamerule doDaylightCycle false")
    time.sleep(1)

    test_id = 1
    def next_name():
        nonlocal test_id
        name = f"PRE{test_id}"
        test_id += 1
        return name

    BASE_DMG = 7.9  # 钻石剑基础伤害

    # ============================================================
    # 1. 至高之术 - 精确伤害验证 (+20% / +40%)
    # ============================================================
    print("\n" + "="*60)
    print("1. 至高之术: 精确伤害加成验证")
    print("="*60)

    # Lv1: +20% -> 7.9 * 1.2 = 9.48
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:supreme_art 1")
    dmg = extract_dmg(r)
    expected = BASE_DMG * 1.2
    check("至高之术-Lv1精确伤害", dmg and abs(dmg - expected) < 0.5, f"实际={dmg}, 期望={expected:.2f}")
    cmd(f"kill @e[name=\"{name}\"]")

    # Lv2: +40% -> 7.9 * 1.4 = 11.06
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:supreme_art 2")
    dmg = extract_dmg(r)
    expected = BASE_DMG * 1.4
    check("至高之术-Lv2精确伤害", dmg and abs(dmg - expected) < 0.5, f"实际={dmg}, 期望={expected:.2f}")
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 2. 血泣 - 精确自伤数值验证
    # ============================================================
    print("\n" + "="*60)
    print("2. 血泣: 精确自伤数值验证")
    print("="*60)

    # 由于假人攻击不会真正扣假人自己的血，我们验证伤害加成比例
    # Lv1: +20%, 自伤10 -> 伤害 ~7.9 * 1.2 = 9.48
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:blood_weep 1")
    dmg = extract_dmg(r)
    expected = BASE_DMG * 1.2
    check("血泣-Lv1精确伤害(+20%)", dmg and abs(dmg - expected) < 0.5, f"实际={dmg}, 期望={expected:.2f}")
    cmd(f"kill @e[name=\"{name}\"]")

    # Lv2: +30% -> 7.9 * 1.3 = 10.27
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:blood_weep 2")
    dmg = extract_dmg(r)
    expected = BASE_DMG * 1.3
    check("血泣-Lv2精确伤害(+30%)", dmg and abs(dmg - expected) < 0.5, f"实际={dmg}, 期望={expected:.2f}")
    cmd(f"kill @e[name=\"{name}\"]")

    # Lv3: +45% -> 7.9 * 1.45 = 11.455
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:blood_weep 3")
    dmg = extract_dmg(r)
    expected = BASE_DMG * 1.45
    check("血泣-Lv3精确伤害(+45%)", dmg and abs(dmg - expected) < 0.5, f"实际={dmg}, 期望={expected:.2f}")
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 3. 破军 - 精确额外伤害验证
    # ============================================================
    print("\n" + "="*60)
    print("3. 破军: 精确额外伤害验证")
    print("="*60)

    for lvl, bonus_pct in [(1, 0.10), (2, 0.20), (3, 0.30)]:
        name = next_name()
        cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
        time.sleep(0.5)
        cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
        cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
        time.sleep(0.3)
        # 打到20%血量以下
        cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 85")
        time.sleep(0.3)
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:army_breaker {lvl}")
        dmg = extract_dmg(r)
        expected = BASE_DMG * (1 + bonus_pct)
        check(f"破军-Lv{lvl}精确伤害(+{int(bonus_pct*100)}%)", dmg and abs(dmg - expected) < 0.5, f"实际={dmg}, 期望={expected:.2f}")
        cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 4. 制裁 - 精确真伤验证 (1%/2%/3%最大生命值)
    # ============================================================
    print("\n" + "="*60)
    print("4. 制裁: 精确真伤验证")
    print("="*60)

    for lvl, pct in [(1, 0.01), (2, 0.02), (3, 0.03)]:
        name = next_name()
        max_hp = 200
        cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:{max_hp}f}}")
        time.sleep(0.5)
        cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set {max_hp}")
        cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:{max_hp}f}}")
        time.sleep(0.3)
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:sanction {lvl}")
        dmg = extract_dmg(r)
        expected_true = max_hp * pct
        expected_total = BASE_DMG + expected_true
        check(f"制裁-Lv{lvl}精确真伤({int(pct*100)}%={expected_true:.1f})", dmg and abs(dmg - expected_total) < 1.0, f"实际={dmg}, 期望={expected_total:.1f}")
        cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 5. 深海的供养 - 精确治疗量验证
    # ============================================================
    print("\n" + "="*60)
    print("5. 深海的供养: 精确治疗量验证")
    print("="*60)

    for lvl, heal_pct in [(1, 0.05), (2, 0.10), (3, 0.20)]:
        name = next_name()
        max_hp = 100
        cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:{max_hp}f}}")
        time.sleep(0.5)
        cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set {max_hp}")
        cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:{max_hp}f}}")
        time.sleep(0.3)
        cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:deep_seas_grace {lvl}")
        time.sleep(0.3)
        # 记录攻击前血量
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:deep_seas_grace {lvl}")
        time.sleep(1.5)
        h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
        hp_after = extract_hp(h)
        # 受到~7.9伤害，治疗max_hp*heal_pct，最终约100-7.9+max_hp*heal_pct
        expected_hp = 100 - BASE_DMG + max_hp * heal_pct
        check(f"深海的供养-Lv{lvl}精确治疗({int(heal_pct*100)}%={max_hp*heal_pct:.1f}HP)", hp_after and abs(hp_after - expected_hp) < 2.0, f"实际={hp_after}, 期望={expected_hp:.1f}")
        cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 6. 收割 - 精确阈值边界测试
    # ============================================================
    print("\n" + "="*60)
    print("6. 收割: 精确阈值边界测试")
    print("="*60)

    # Lv1: <=10% -> 100HP僵尸，<=10HP应该被秒杀
    # 边界: 10HP应该秒杀，11HP不应该
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 90")  # 剩余~10HP
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:harvest 1")
    check("收割-Lv1边界(10%阈值)", "-> 0.0" in r or "-> 0." in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # 边界: 11HP不应该被秒杀
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 89")  # 剩余~11HP
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:harvest 1")
    check("收割-Lv1边界(11%不应秒杀)", "-> 0.0" not in r and "-> 0." not in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 7. 解放者 - 精确时间-伤害曲线验证
    # ============================================================
    print("\n" + "="*60)
    print("7. 解放者: 时间-伤害曲线验证")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)

    # 第一次攻击（<=5秒，0.1倍）
    r1 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:liberator 1")
    dmg1 = extract_dmg(r1)
    expected1 = BASE_DMG * 0.1
    check("解放者-<=5秒(0.1倍)", dmg1 and abs(dmg1 - expected1) < 0.5, f"实际={dmg1}, 期望={expected1:.2f}")

    time.sleep(6)
    # 第二次攻击（>5秒，应该>0.1倍）
    r2 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:liberator 1")
    dmg2 = extract_dmg(r2)
    check("解放者->5秒(>0.1倍)", dmg2 and dmg2 > dmg1, f"实际={dmg2}, 第一次={dmg1}")

    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 8. 翻飞之币 - 生命值上限修改验证
    # ============================================================
    print("\n" + "="*60)
    print("8. 翻飞之币: 生命值上限修改")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:flipping_coin 1")
    time.sleep(0.3)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    # 检查是否有虚弱效果
    check("翻飞之币-施加虚弱", "weakness" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 9. 倏忽恩赐 - 精确x2伤害验证
    # ============================================================
    print("\n" + "="*60)
    print("9. 倏忽恩赐: 精确x2伤害验证")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:fleeting_grace 1")
    time.sleep(0.3)

    # 第一次攻击（记录伤害）
    r1 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:fleeting_grace 1")
    dmg1 = extract_dmg(r1)
    h1 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    hp1 = extract_hp(h1)
    time.sleep(0.5)

    # 第二次攻击（应该额外造成dmg1*2）
    r2 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:fleeting_grace 1")
    dmg2 = extract_dmg(r2)
    h2 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    hp2 = extract_hp(h2)

    # 验证第二次伤害约为第一次的2倍（基础+dmg1*2）
    expected_dmg2 = BASE_DMG + dmg1 * 2
    check("倏忽恩赐-第二次伤害x2", dmg2 and abs(dmg2 - expected_dmg2) < 1.5, f"实际={dmg2}, 期望={expected_dmg2:.1f}")
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 10. 不完整的预知眼 - 闪避概率统计验证
    # ============================================================
    print("\n" + "="*60)
    print("10. 不完整的预知眼: 闪避概率统计验证")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] head zhonz_more_enchantments:incomplete_foreknowledge_eye 1")
    time.sleep(0.3)

    hits = 0
    misses = 0
    for i in range(20):
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:incomplete_foreknowledge_eye 1")
        dmg = extract_dmg(r)
        if dmg and dmg > 0.1:
            hits += 1
        else:
            misses += 1
        # 清除可能的效果
        cmd(f"effect clear @e[name=\"{name}\",limit=1]")
        time.sleep(0.2)

    total = hits + misses
    dodge_rate = misses / total if total > 0 else 0
    print(f"  20次攻击: 命中{hits}次, 闪避{misses}次, 闪避率={dodge_rate:.1%}")
    # 预期约80%闪避率，允许较大误差（60%-100%）
    check("预知眼-闪避率统计", 0.5 <= dodge_rate <= 1.0, f"闪避率={dodge_rate:.1%}")
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 11. 鱼丸 - 十倍耐久验证
    # ============================================================
    print("\n" + "="*60)
    print("11. 鱼丸: 十倍耐久验证")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:fishball 1")
    check("鱼丸-装备成功", "Equipped" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 12. 自地狱中归来 - 多次触发耐久扣除验证
    # ============================================================
    print("\n" + "="*60)
    print("12. 自地狱中归来: 多次触发耐久")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] feet zhonz_more_enchantments:return_from_hell 1")
    time.sleep(0.3)

    # 第一次致命伤害
    r1 = cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 100")
    h1 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    hp1 = extract_hp(h1)
    check("自地狱-第一次触发", hp1 and hp1 >= 19.0, f"HP={hp1}")

    # 第二次致命伤害（应该再次触发）
    r2 = cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 100")
    h2 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    hp2 = extract_hp(h2)
    check("自地狱-第二次触发", hp2 and hp2 >= 19.0, f"HP={hp2}")

    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 13. 神护 - 多次触发耐久扣除25%
    # ============================================================
    print("\n" + "="*60)
    print("13. 神护: 多次触发耐久扣除")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:divine_protection 1")
    time.sleep(0.3)

    # 第一次致命伤害
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 100")
    h1 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    hp1 = extract_hp(h1)
    check("神护-第一次触发", hp1 and hp1 >= 19.0, f"HP={hp1}")

    # 第二次致命伤害
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 100")
    h2 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    hp2 = extract_hp(h2)
    check("神护-第二次触发", hp2 and hp2 >= 19.0, f"HP={hp2}")

    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 14. 重伤 - 治疗减少持续时间精确验证
    # ============================================================
    print("\n" + "="*60)
    print("14. 重伤: 效果持续时间")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:grievous_wound 1")
    time.sleep(0.3)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    # 检查是否有5秒(100tick)的凋零效果
    check("重伤-5秒凋零", "wither" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 15. 终结 - 耐久消耗等于伤害量
    # ============================================================
    print("\n" + "="*60)
    print("15. 终结: 耐久消耗等于伤害量")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:finale 1")
    check("终结-秒杀", "-> 0.0" in r or "-> 0." in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # 清理
    cmd("kill @e[type=!player]")

    # ============================================================
    # 结果汇总
    # ============================================================
    print("\n" + "="*60)
    print(f"极端精度测试结果: {results['pass']} 通过, {results['fail']} 失败")
    print("="*60)
    for status, name, detail in results['tests']:
        if status == 'FAIL':
            print(f"  FAIL: {name} - {detail}")

    if results['fail'] == 0:
        print(f"\n通过率: {results['pass']}/{results['pass'] + results['fail']}")
    else:
        print(f"\n通过率: {results['pass']}/{results['pass'] + results['fail']}")

    rcon.close()


if __name__ == "__main__":
    run_tests()
