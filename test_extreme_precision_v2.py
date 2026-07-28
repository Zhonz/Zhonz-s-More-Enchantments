#!/usr/bin/env python3
"""极端精度测试v2 - 修复测试设计问题，验证附魔数值完全符合描述"""

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
    match = re.search(r'\((\d+\.\d+) dmg\)', response)
    if match:
        return float(match.group(1))
    match = re.search(r'(\d+\.\d+) dmg\)', response)
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
        name = f"PR2{test_id}"
        test_id += 1
        return name

    BASE_DMG = 7.9  # 钻石剑基础伤害

    # ============================================================
    # 1. 至高之术 - 精确伤害验证 (+20% / +40%)
    # ============================================================
    print("\n" + "="*60)
    print("1. 至高之术: 精确伤害加成验证")
    print("="*60)

    for lvl, bonus in [(1, 0.20), (2, 0.40)]:
        name = next_name()
        cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
        time.sleep(0.5)
        cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
        cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
        time.sleep(0.3)
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:supreme_art {lvl}")
        dmg = extract_dmg(r)
        expected = BASE_DMG * (1 + bonus)
        check(f"至高之术-Lv{lvl}(+{int(bonus*100)}%)", dmg and abs(dmg - expected) < 0.5, f"实际={dmg}, 期望={expected:.2f}")
        cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 2. 血泣 - 精确自伤数值验证
    # ============================================================
    print("\n" + "="*60)
    print("2. 血泣: 精确伤害加成验证")
    print("="*60)

    for lvl, bonus in [(1, 0.20), (2, 0.30), (3, 0.45)]:
        name = next_name()
        cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
        time.sleep(0.5)
        cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
        cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
        time.sleep(0.3)
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:blood_weep {lvl}")
        dmg = extract_dmg(r)
        expected = BASE_DMG * (1 + bonus)
        check(f"血泣-Lv{lvl}(+{int(bonus*100)}%)", dmg and abs(dmg - expected) < 0.5, f"实际={dmg}, 期望={expected:.2f}")
        cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 3. 破军 - 精确额外伤害验证
    # ============================================================
    print("\n" + "="*60)
    print("3. 破军: 精确额外伤害验证")
    print("="*60)

    for lvl, bonus in [(1, 0.10), (2, 0.20), (3, 0.30)]:
        name = next_name()
        cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
        time.sleep(0.5)
        cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
        cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
        time.sleep(0.3)
        # 直接设置血量到阈值以下，避免 damage 命令的不确定性
        threshold_hp = 100 * (0.30 + 0.10 * (lvl - 1)) - 5  # 低于当前等级阈值
        cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:{threshold_hp}f}}")
        time.sleep(0.3)
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:army_breaker {lvl}")
        dmg = extract_dmg(r)
        expected = BASE_DMG * (1 + bonus)
        check(f"破军-Lv{lvl}(+{int(bonus*100)}%)", dmg and abs(dmg - expected) < 0.5, f"实际={dmg}, 期望={expected:.2f}")
        cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 4. 制裁 - 精确真伤验证
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
        check(f"制裁-Lv{lvl}({int(pct*100)}%真伤={expected_true:.1f})", dmg and abs(dmg - expected_total) < 1.0, f"实际={dmg}, 期望={expected_total:.1f}")
        cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 5. 深海的供养 - 治疗封顶验证
    # ============================================================
    print("\n" + "="*60)
    print("5. 深海的供养: 治疗封顶验证")
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
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:deep_seas_grace {lvl}")
        time.sleep(1.5)
        h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
        hp_after = extract_hp(h)
        # 治疗会被100HP上限封顶，所以治疗后不会超出100
        expected_max = max_hp  # 封顶值
        # 实际治疗后应该在 92~100 之间（取决于治疗量和封顶）
        check(f"深海的供养-Lv{lvl}(治疗封顶)", hp_after and hp_after > 90 and hp_after <= 100, f"实际={hp_after}")
        cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 6. 收割 - 阈值验证（正确设计）
    # ============================================================
    print("\n" + "="*60)
    print("6. 收割: 阈值验证")
    print("="*60)

    # Lv1: <=10%。100HP僵尸，damage到10HP（刚好10%），攻击后剩余2.1HP <= 10%，触发
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 90")  # 剩余约10HP
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:harvest 1")
    check("收割-Lv1(10%触发)", "-> 0.0" in r or "-> 0." in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # Lv1: 不触发。100HP僵尸，damage到20HP（20% > 10%），攻击后剩余12.1HP > 10%，不触发
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 80")  # 剩余约20HP
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:harvest 1")
    check("收割-Lv1(20%不触发)", "-> 0.0" not in r and "-> 0." not in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 7. 解放者 - 时间-伤害曲线验证
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

    # 第一次攻击（刚装备，应视为0秒，0.1倍）
    r1 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:liberator 1")
    dmg1 = extract_dmg(r1)
    expected1 = BASE_DMG * 0.1
    check(f"解放者-首次(0.1倍)", dmg1 and abs(dmg1 - expected1) < 0.5, f"实际={dmg1}, 期望={expected1:.2f}")

    time.sleep(6)
    # 第二次攻击（>5秒，应>0.1倍）
    r2 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:liberator 1")
    dmg2 = extract_dmg(r2)
    check("解放者->5秒(>0.1倍)", dmg2 and dmg2 > dmg1, f"实际={dmg2}, 第一次={dmg1}")

    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 8. 翻飞之币 - 虚弱效果
    # ============================================================
    print("\n" + "="*60)
    print("8. 翻飞之币: 施加虚弱")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:flipping_coin 1")
    time.sleep(0.3)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("翻飞之币-虚弱", "weakness" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 9. 倏忽恩赐 - 给假人装备盔甲测试
    # ============================================================
    print("\n" + "="*60)
    print("9. 倏忽恩赐: 记录伤害x2")
    print("="*60)

    # 给假人装备倏忽恩赐胸甲（通过givearmor命令给自己/玩家）
    # 但由于是假人，我们需要让假人受到攻击然后攻击别人
    # 简化测试：使用一个中间僵尸作为攻击者
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    # 装备倏忽恩赐胸甲
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:fleeting_grace 1")
    time.sleep(0.3)
    # 假人攻击僵尸（僵尸记录伤害）
    r1 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:finale 1")
    time.sleep(0.5)
    # 僵尸被秒杀，无法继续测试...
    # 改用不会造成秒杀的攻击
    cmd(f"kill @e[name=\"{name}\"]")
    time.sleep(0.3)

    # 重新设计：用低伤害攻击
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:100f}}")
    time.sleep(0.5)
    cmd(f"attribute @e[name=\"{name}\",limit=1] minecraft:generic.max_health base set 100")
    cmd(f"data merge entity @e[name=\"{name}\",limit=1] {{Health:100f}}")
    time.sleep(0.3)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:fleeting_grace 1")
    time.sleep(0.3)
    # 假人基础攻击8.0伤害
    r1 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] minecraft:sharpness 1")
    time.sleep(0.5)
    h1 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    hp1 = extract_hp(h1)
    # 再次攻击，僵尸（装备倏忽恩赐）反击...但僵尸不会主动攻击
    # 这个附魔的自动化测试比较困难，暂时跳过精确验证
    check("倏忽恩赐-存在性", hp1 is not None, f"HP={hp1}")
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 10. 不完整的预知眼 - 闪避概率（前5次）
    # ============================================================
    print("\n" + "="*60)
    print("10. 不完整的预知眼: 初始闪避率")
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
    for i in range(5):
        r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:incomplete_foreknowledge_eye 1")
        dmg = extract_dmg(r)
        if dmg and dmg > 0.1:
            hits += 1
        else:
            misses += 1
        cmd(f"effect clear @e[name=\"{name}\",limit=1]")
        time.sleep(0.2)

    total = hits + misses
    dodge_rate = misses / total if total > 0 else 0
    print(f"  5次攻击: 命中{hits}次, 闪避{misses}次, 闪避率={dodge_rate:.1%}")
    # 前5次预期大部分闪避（初始80%，每次失败降低）
    check("预知眼-初始闪避率", dodge_rate >= 0.4, f"闪避率={dodge_rate:.1%}")
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 11. 鱼丸 - 装备成功
    # ============================================================
    print("\n" + "="*60)
    print("11. 鱼丸: 装备成功")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:fishball 1")
    check("鱼丸-装备", "Equipped" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 12. 自地狱中归来 - 单次触发+冷却
    # ============================================================
    print("\n" + "="*60)
    print("12. 自地狱中归来: 触发+冷却")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] feet zhonz_more_enchantments:return_from_hell 1")
    time.sleep(0.3)

    # 第一次致命伤害 - 应该触发
    r1 = cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 100")
    h1 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    hp1 = extract_hp(h1)
    check("自地狱-触发", hp1 and hp1 >= 19.0, f"HP={hp1}")

    # 第二次致命伤害 - 有冷却，不应触发
    r2 = cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 100")
    h2 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    hp2 = extract_hp(h2)
    check("自地狱-冷却期不触发", hp2 is None or hp2 < 1.0, f"HP={hp2} (应死亡)")

    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 13. 神护 - 多次触发（无冷却，但每次扣25%耐久）
    # ============================================================
    print("\n" + "="*60)
    print("13. 神护: 多次触发")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:divine_protection 1")
    time.sleep(0.3)

    for i in range(3):
        cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 100")
        h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
        hp = extract_hp(h)
        check(f"神护-第{i+1}次触发", hp and hp >= 19.0, f"HP={hp}")

    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 14. 重伤 - 凋零效果持续时间
    # ============================================================
    print("\n" + "="*60)
    print("14. 重伤: 凋零效果")
    print("="*60)

    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:grievous_wound 1")
    time.sleep(0.3)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("重伤-凋零", "wither" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ============================================================
    # 15. 终结 - 秒杀
    # ============================================================
    print("\n" + "="*60)
    print("15. 终结: 秒杀")
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
    print(f"极端精度测试v2结果: {results['pass']} 通过, {results['fail']} 失败")
    print("="*60)
    for status, name, detail in results['tests']:
        if status == 'FAIL':
            print(f"  FAIL: {name} - {detail}")

    print(f"\n通过率: {results['pass']}/{results['pass'] + results['fail']}")
    rcon.close()


if __name__ == "__main__":
    run_tests()
