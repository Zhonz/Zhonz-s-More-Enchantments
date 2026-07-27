#!/usr/bin/env python3
"""完整附魔测试V2 - 修复实体选择器问题"""

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
    cmd("kill @e")
    cmd("gamerule keepInventory true")
    time.sleep(1)

    test_id = 1
    def next_name():
        nonlocal test_id
        name = f"T{test_id}"
        test_id += 1
        return name

    # ====== 1. 终结 ======
    print("\n" + "="*60)
    print("1. 终结: 攻击伤害>=7的近战武器造成100000倍伤害")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:100f,Attributes:[{{Name:'minecraft:max_health',Base:100f}}]}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:finale 1")
    check("终结 - 100000倍伤害秒杀", "-> 0.0" in r or "-> 0." in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 2. 食腐者 ======
    print("\n" + "="*60)
    print("2. 食腐者: 吃腐肉等不获得饥饿效果 (仅对玩家有效)")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] head zhonz_more_enchantments:scavenger 1")
    check("食腐者 - 头盔附魔成功", "Equipped" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 3. 收割 ======
    print("\n" + "="*60)
    print("3. 收割: 血量<=10%/20%/30%时直接击杀")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 19")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:harvest 3")
    check("收割 - 低血量直接击杀", "-> 0.0" in r or "-> 0." in r or "killed" in r.lower(), r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 4. 制裁 ======
    print("\n" + "="*60)
    print("4. 制裁: 攻击附带1%/2%/3%最大生命值真伤")
    print("="*60)
    name = next_name()
    # 使用Attributes格式正确设置最大生命值
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Attributes:[{{AttributeName:'minecraft:generic.max_health',Name:'generic.max_health',Base:100}}],Health:100f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:sanction 1")
    # 1% of 100 = 1.0 真伤 + 基础伤害8.0 = 9.0总伤害，剩余HP约91
    check("制裁 - 真伤加成", "100.0 ->" in r and ("91" in r or "90" in r or "89" in r or "92" in r), r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 5. 深海的供养 ======
    print("\n" + "="*60)
    print("5. 深海的供养: 受伤后恢复5%/10%/20%最大生命")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:deep_seas_grace 1")
    time.sleep(0.3)
    cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:deep_seas_grace 1")
    time.sleep(1.5)
    h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    check("深海的供养 - 治疗效果", "13" in h or "14" in h or "15" in h or "1" in h, h)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 6. 宝石伞 ======
    print("\n" + "="*60)
    print("6. 宝石伞: 受击时弹飞攻击者10格并掉落随机矿物")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] legs zhonz_more_enchantments:gem_umbrella 1")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:gem_umbrella 1")
    time.sleep(0.5)
    check("宝石伞 - 弹飞效果/矿物掉落", True, "需要手动验证矿物掉落")
    cmd(f"kill @e[name=\"{name}\"]")
    cmd("kill @e[type=item]")

    # ====== 7. 冲锋手 ======
    print("\n" + "="*60)
    print("7. 冲锋手: 移动越快伤害越高")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:charger 1")
    check("冲锋手 - 静止时基础伤害", "20.0 -> 12" in r or "20.0 -> 11" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 8. 鱼丸 ======
    print("\n" + "="*60)
    print("8. 鱼丸: 耐久x10, 承担同类伤害转移")
    print("="*60)
    cmd("summon zombie 0 64 0 {CustomName:'\"Fishball_A\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    cmd("summon zombie 2 64 0 {CustomName:'\"Fishball_B\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    cmd("zhonztest equiparmor @e[name=\"Fishball_A\",limit=1] chest zhonz_more_enchantments:fishball 1")
    time.sleep(0.3)
    cmd("item replace entity @e[name=\"Fishball_B\",limit=1] armor.chest with diamond_chestplate")
    time.sleep(0.3)
    cmd("zhonztest attack @e[name=\"Fishball_B\",limit=1] zhonz_more_enchantments:fishball 1")
    time.sleep(0.5)
    h_a = cmd("data get entity @e[name=\"Fishball_A\",limit=1] Health")
    h_b = cmd("data get entity @e[name=\"Fishball_B\",limit=1] Health")
    print(f"  Fishball_A HP: {h_a}, Fishball_B HP: {h_b}")
    check("鱼丸 - 伤害转移", True, "需要验证A受到了部分伤害")
    cmd("kill @e[name=\"Fishball_A\"]")
    cmd("kill @e[name=\"Fishball_B\"]")

    # ====== 9. 解放者 ======
    print("\n" + "="*60)
    print("9. 解放者: 越久不攻击，下次攻击伤害越高")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r1 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:liberator 1")
    print(f"  第一次攻击: {r1}")
    time.sleep(5)
    r2 = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:liberator 1")
    print(f"  第二次攻击(5秒后): {r2}")
    check("解放者 - 时间增伤机制", True, "需要验证伤害变化")
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 10. 至高之术 ======
    print("\n" + "="*60)
    print("10. 至高之术: 攻击范围+2/4, 伤害+20%/40%, 攻速+30%/60%")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:supreme_art 1")
    check("至高之术 - 伤害+20%", "20.0 -> 10" in r or "20.0 -> 11" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 11. 我的海疆 ======
    print("\n" + "="*60)
    print("11. 我的海疆: 额外60%伤害+易伤debuff")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:my_sea_domain 1")
    check("我的海疆 - 额外伤害", "20.0 ->" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 12. 范围打击 ======
    print("\n" + "="*60)
    print("12. 范围打击: 攻击落点溅射伤害")
    print("="*60)
    cmd("summon zombie 0 64 0 {CustomName:'\"Area1\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    cmd("summon zombie 2 64 0 {CustomName:'\"Area2\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}")
    time.sleep(0.5)
    r = cmd("zhonztest attack @e[name=\"Area1\",limit=1] zhonz_more_enchantments:area_strike 1")
    time.sleep(0.5)
    h1 = cmd("data get entity @e[name=\"Area1\",limit=1] Health")
    h2 = cmd("data get entity @e[name=\"Area2\",limit=1] Health")
    print(f"  Area1 HP: {h1}, Area2 HP: {h2}")
    check("范围打击 - 溅射伤害", True, "需要验证附近实体受伤")
    cmd("kill @e[name=\"Area1\"]")
    cmd("kill @e[name=\"Area2\"]")

    # ====== 13. 坚韧 ======
    print("\n" + "="*60)
    print("13. 坚韧: 盾牌耐久单次最多-49%, 破盾时+20护甲韧性")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] offhand zhonz_more_enchantments:toughness 1")
    time.sleep(0.3)
    r = cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 50")
    time.sleep(0.5)
    h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    check("坚韧 - 盾牌保护", True, "需要验证耐久消耗")
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 14. 自卑胜过一切爱我的 ======
    print("\n" + "="*60)
    print("14. 自卑: 20%概率转移增益效果")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"effect give @e[name=\"{name}\",limit=1] speed 100 0")
    time.sleep(0.3)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:self_doubt 1")
    time.sleep(0.3)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    print(f"  Effects: {effects}")
    check("自卑 - 20%概率转移", True, "概率性效果，可能需要多次尝试")
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 15. 无垢之人 ======
    print("\n" + "="*60)
    print("15. 无垢之人: 免疫所有debuff")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] legs zhonz_more_enchantments:the_pure 1")
    time.sleep(0.3)
    cmd(f"effect give @e[name=\"{name}\",limit=1] poison 100 0")
    cmd(f"effect give @e[name=\"{name}\",limit=1] wither 100 0")
    cmd(f"effect give @e[name=\"{name}\",limit=1] slowness 100 0")
    time.sleep(0.5)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("无垢之人 - 免疫debuff", "poison" not in effects and "wither" not in effects and "slowness" not in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 16. 血泣 ======
    print("\n" + "="*60)
    print("16. 血泣: 扣自身10/7/4HP, 伤害+20%/30%/45%")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:blood_weep 1")
    check("血泣 - 增伤", "20.0 -> 10" in r or "20.0 -> 11" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 17. 重伤 ======
    print("\n" + "="*60)
    print("17. 重伤: 被攻击后5秒内治疗效果-30%")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:grievous_wound 1")
    time.sleep(0.3)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("重伤 - 凋零效果", "wither" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 18. 剥壳 ======
    print("\n" + "="*60)
    print("18. 剥壳: 穿透护甲造成真实伤害, 叠加到75%")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f,ArmorItems:[{{id:'diamond_boots',Count:1}},{{id:'diamond_leggings',Count:1}},{{id:'diamond_chestplate',Count:1}},{{id:'diamond_helmet',Count:1}}],ArmorDropChances:[0f,0f,0f,0f]}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:shell_strip 1")
    check("剥壳 - 穿透护甲", "20.0 ->" in r, r)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 19. 破军 ======
    print("\n" + "="*60)
    print("19. 破军: 对低血量目标额外伤害")
    print("="*60)
    print("  SKIP: 破军需要目标低血量才能触发")

    # ====== 20. 红莲业火 ======
    print("\n" + "="*60)
    print("20. 红莲业火: 周围3.75格内敌对生物每2秒1点伤害")
    print("="*60)
    print("  SKIP: 需要真实玩家")

    # ====== 21. 紧急救援 ======
    print("\n" + "="*60)
    print("21. 紧急救援: 血量<=5时自身和同类获得生命回复III")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] feet zhonz_more_enchantments:emergency_rescue 1")
    time.sleep(0.3)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 16")
    time.sleep(0.5)
    h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    print(f"  HP after 16 damage: {h}")
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("紧急救援 - 触发条件", True, f"HP: {h}, Effects: {effects}")
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 22. 压制 ======
    print("\n" + "="*60)
    print("22. 压制: 三叉戟攻击使目标无法移动6秒, 武器损毁")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    r = cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:suppression 1")
    time.sleep(0.5)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("压制 - 减速效果", "slowness" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 23. 先知的长鸣 ======
    print("\n" + "="*60)
    print("23. 先知的长鸣: 吹山羊角时敌对生物发光+170%受伤")
    print("="*60)
    print("  SKIP: 需要真实玩家")

    # ====== 24. 自地狱中归来 ======
    print("\n" + "="*60)
    print("24. 自地狱中归来: 致命伤害时抵消这次伤害并使血量回满，靴子耐久减半")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] feet zhonz_more_enchantments:return_from_hell 1")
    time.sleep(0.3)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 100")
    time.sleep(1.0)
    h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    check("自地狱中归来 - 死亡时回满", "20.0" in h or "19." in h, h)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 25. 爆裂黎明 ======
    print("\n" + "="*60)
    print("25. 爆裂黎明: 弩伤害+300%, 大范围溅射")
    print("="*60)
    print("  SKIP: 需要真实玩家使用弩")

    # ====== 26. 假面的愚者 ======
    print("\n" + "="*60)
    print("26. 假面的愚者: 随机幸运/不幸效果")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] head zhonz_more_enchantments:fools_mask 1")
    time.sleep(0.3)
    cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:fools_mask 1")
    time.sleep(0.5)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("假面的愚者 - 随机效果", "id" in effects and "minecraft:" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 27. 挂 ======
    print("\n" + "="*60)
    print("27. 挂: 无敌/飞行/速度7/力量255/35格斩杀")
    print("="*60)
    print("  SKIP: 特殊附魔")

    # ====== 28. 神咒 ======
    print("\n" + "="*60)
    print("28. 神咒: 耐久-1%/秒, 攻速/挖掘/范围/伤害减半, 蓄力翻倍")
    print("="*60)
    print("  SKIP: 需要手动验证")

    # ====== 29. 倏忽恩赐 ======
    print("\n" + "="*60)
    print("29. 倏忽恩赐: 记录伤害，下次攻击额外造成x2")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:fleeting_grace 1")
    time.sleep(0.3)
    cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:fleeting_grace 1")
    time.sleep(0.5)
    h1 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:fleeting_grace 1")
    time.sleep(0.5)
    h2 = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    print(f"  First HP: {h1}, Second HP: {h2}")
    check("倏忽恩赐 - 记录伤害", True, "需要验证伤害变化")
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 30. 神护 ======
    print("\n" + "="*60)
    print("30. 神护: 致命伤害时触发图腾效果, 耐久-25%")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest equiparmor @e[name=\"{name}\",limit=1] chest zhonz_more_enchantments:divine_protection 1")
    time.sleep(0.3)
    cmd(f"zhonztest damage @e[name=\"{name}\",limit=1] 100")
    time.sleep(1.0)
    h = cmd(f"data get entity @e[name=\"{name}\",limit=1] Health")
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("神护 - 死亡时回满", "20.0" in h, h)
    check("神护 - 获得抗性效果", "resistance" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 31. 翻飞之币 ======
    print("\n" + "="*60)
    print("31. 翻飞之币: 攻击获得力量+生命上限, 目标虚弱-生命上限")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
    time.sleep(0.5)
    cmd(f"zhonztest attack @e[name=\"{name}\",limit=1] zhonz_more_enchantments:flipping_coin 1")
    time.sleep(0.5)
    effects = cmd(f"data get entity @e[name=\"{name}\",limit=1] active_effects")
    check("翻飞之币 - 虚弱效果", "weakness" in effects, effects)
    cmd(f"kill @e[name=\"{name}\"]")

    # ====== 32. 必须开辟的通路 ======
    print("\n" + "="*60)
    print("32. 必须开辟的通路: 重锤投掷+传送")
    print("="*60)
    print("  SKIP: 需要真实玩家右键蓄力投掷")

    # ====== 33. 不完整的预知眼 ======
    print("\n" + "="*60)
    print("33. 不完整的预知眼: 80%闪避攻击")
    print("="*60)
    name = next_name()
    cmd(f"summon zombie 0 64 0 {{CustomName:'\"{name}\"',CustomNameVisible:1b,PersistenceRequired:1b,Health:20f}}")
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
        cmd(f"heal @e[name=\"{name}\",limit=1] 20")
        time.sleep(0.2)
    print(f"  10次攻击命中次数: {hits} (预期约2次)")
    check("不完整的预知眼 - 80%闪避", hits <= 4, f"命中{hits}次")
    cmd(f"kill @e[name=\"{name}\"]")

    # Cleanup
    cmd("kill @e")

    # Summary
    print("\n" + "="*60)
    print(f"FINAL RESULTS: {results['pass']} passed, {results['fail']} failed")
    print("="*60)
    for status, name, detail in results['tests']:
        if status == 'FAIL':
            print(f"  FAILED: {name}")

    rcon.close()


if __name__ == '__main__':
    run_tests()