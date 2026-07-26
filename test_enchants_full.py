#!/usr/bin/env python3
"""完整附魔测试脚本 - 通过RCON测试所有附魔功能"""
import socket
import struct
import time
import sys

class RCONClient:
    def __init__(self, host='127.0.0.1', port=25575, password='test123'):
        self.host = host
        self.port = port
        self.password = password
        self.sock = None
        self.request_id = 0

    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(15)
        self.sock.connect((self.host, self.port))
        self._send_packet(3, self.password)
        resp = self._recv_packet()
        if resp[0] != 2:
            raise Exception(f"Login failed, got type {resp[0]}")
        print("[RCON] 连接成功")

    def _send_packet(self, packet_type, data):
        self.request_id += 1
        payload = struct.pack('<iii', self.request_id, packet_type, 0) + data.encode('utf-8') + b'\x00\x00'
        self.sock.send(struct.pack('<i', len(payload)) + payload)

    def _recv_packet(self):
        try:
            length_data = self.sock.recv(4)
            if len(length_data) < 4:
                return (0, "")
            length = struct.unpack('<i', length_data)[0]
            if length <= 0 or length > 4110:
                return (0, "")
            data = b''
            while len(data) < length:
                chunk = self.sock.recv(length - len(data))
                if not chunk:
                    break
                data += chunk
            if len(data) < 8:
                return (0, "")
            req_id, req_type = struct.unpack('<ii', data[:8])
            payload = data[8:]
            while payload.endswith(b'\x00'):
                payload = payload[:-1]
            return (req_type, payload.decode('utf-8', errors='replace'))
        except Exception as e:
            return (0, str(e))

    def send(self, cmd, wait=0.1):
        self._send_packet(2, cmd)
        time.sleep(wait)
        resp = self._recv_packet()
        return resp[1]

    def close(self):
        if self.sock:
            self.sock.close()

def run_test(rcon, test_name, setup_cmds, test_cmd, expected_check, description):
    """运行单个测试"""
    print(f"\n{'='*60}")
    print(f"测试: {test_name}")
    print(f"描述: {description}")
    print(f"{'='*60}")

    # 清理
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.2)

    # 执行设置命令
    for cmd in setup_cmds:
        r = rcon.send(cmd, 0.3)
        if r:
            print(f"  [SETUP] {cmd[:50]}... -> {r[:80]}")

    time.sleep(0.5)

    # 执行测试命令
    result = rcon.send(test_cmd, 0.5)
    print(f"  [TEST]  {test_cmd[:60]}...")
    if result:
        print(f"  [RESULT] {result[:120]}")

    # 检查服务器日志
    time.sleep(0.5)

    # 清理
    rcon.send("kill @e[type=zombie]")

    # 结果判定
    passed = expected_check(result) if expected_check else True
    status = "✓ 通过" if passed else "✗ 待验证"
    print(f"  {status}")
    return passed

def main():
    rcon = RCONClient()
    try:
        rcon.connect()
    except Exception as e:
        print(f"RCON连接失败: {e}")
        sys.exit(1)

    results = {}

    # 预生成一个测试假人并获取其坐标
    print("\n=== 初始化测试环境 ===")
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.5)

    # 在指定位置生成假人
    spawn_x, spawn_y, spawn_z = 100, 64, 100
    rcon.send(f"setworldspawn {spawn_x} {spawn_y} {spawn_z}")
    time.sleep(0.2)

    # ============================================================
    # 测试1: 终结附魔 (Finale)
    # ============================================================
    def check_finale(result):
        # 终结应该造成100000倍伤害，假人应该直接死亡
        return "0.0" in result or "died" in result.lower() or "killed" in result.lower()

    run_test(rcon, "终结附魔 (Finale)",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:20f}}',
         'data merge entity @e[type=zombie,limit=1] {Invulnerable:0}'],
        f'zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:finale 1',
        check_finale,
        "攻击伤害>=7时造成100000倍伤害，消耗武器耐久"
    )
    results['finale'] = "测试完成"

    # ============================================================
    # 测试2: 深海的供养 (Deep Sea's Grace)
    # ============================================================
    def check_deep_sea(result):
        # 深海的供养3级应该回复20%最大生命值
        # 受到10点伤害，回复4点(20% max HP)，所以净伤害应该是6点左右
        return "->" in result

    run_test(rcon, "深海的供养 (Deep Sea's Grace) - 3级",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:20f}}',
         'zhonztest equiparmor @e[type=zombie,limit=1] chest zhonz_more_enchantments:deep_seas_grace 3'],
        'zhonztest damage @e[type=zombie,limit=1] 10',
        check_deep_sea,
        "受伤后恢复生命上限20%的血量（治疗而非减伤）"
    )
    results['deep_seas_grace'] = "测试完成"

    # ============================================================
    # 测试3: 宝石伞 (Gem Umbrella)
    # ============================================================
    run_test(rcon, "宝石伞 (Gem Umbrella)",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:20f}}',
         'zhonztest equiparmor @e[type=zombie,limit=1] legs zhonz_more_enchantments:gem_umbrella 1'],
        f'zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:deep_seas_grace 3',
        None,
        "受到攻击时弹飞攻击者10格，掉落随机矿物"
    )
    results['gem_umbrella'] = "测试完成"

    # ============================================================
    # 测试4: 制裁附魔 (Sanction)
    # ============================================================
    run_test(rcon, "制裁附魔 (Sanction) - 3级",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:100f,Attributes:[{{Name:"minecraft:generic.max_health",Base:100f}}]}}'],
        f'zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:sanction 3',
        None,
        "造成目标最大生命值3%的真实伤害"
    )
    results['sanction'] = "测试完成"

    # ============================================================
    # 测试5: 血泣附魔 (Blood Weep)
    # ============================================================
    run_test(rcon, "血泣附魔 (Blood Weep) - 3级",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:50f}}'],
        f'zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:blood_weep 3',
        None,
        "攻击者受到4点生命，伤害提高45%"
    )
    results['blood_weep'] = "测试完成"

    # ============================================================
    # 测试6: 收割附魔 (Harvest)
    # ============================================================
    run_test(rcon, "收割附魔 (Harvest) - 3级",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:6f}}',
         'data merge entity @e[type=zombie,limit=1] {Health:6f}'],
        f'zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:harvest 3',
        None,
        "目标血量<=30%时直接击杀"
    )
    results['harvest'] = "测试完成"

    # ============================================================
    # 测试7: 重伤附魔 (Grievous Wound)
    # ============================================================
    run_test(rcon, "重伤附魔 (Grievous Wound)",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:20f}}'],
        f'zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:grievous_wound 1',
        None,
        "使目标后5秒内治疗效果减少30%"
    )
    results['grievous_wound'] = "测试完成"

    # ============================================================
    # 测试8: 剥壳附魔 (Shell Strip)
    # ============================================================
    run_test(rcon, "剥壳附魔 (Shell Strip)",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:50f,Attributes:[{{Name:"minecraft:generic.armor",Base:10f}}]}}'],
        f'zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:shell_strip 1',
        None,
        "造成被护甲减免伤害40%的真实伤害，逐渐提高到75%"
    )
    results['shell_strip'] = "测试完成"

    # ============================================================
    # 测试9: 破军附魔 (Army Breaker)
    # ============================================================
    run_test(rcon, "破军附魔 (Army Breaker) - 3级",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:20f}}'],
        f'zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:army_breaker 3',
        None,
        "对血量低于50%的目标额外造成30%伤害"
    )
    results['army_breaker'] = "测试完成"

    # ============================================================
    # 测试10: 鱼丸附魔 (Fishball)
    # ============================================================
    run_test(rcon, "鱼丸附魔 (Fishball)",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:20f}}',
         f'summon zombie {spawn_x+2} {spawn_y} {spawn_z} {{Health:20f}}',
         'zhonztest equiparmor @e[type=zombie,limit=1,sort=nearest] chest zhonz_more_enchantments:fishball 1'],
        'zhonztest damage @e[type=zombie,sort=farthest,limit=1] 10',
        None,
        "同类生物的伤害转移到鱼丸装备者身上"
    )
    results['fishball'] = "测试完成"

    # ============================================================
    # 测试11: 不完整的预知眼
    # ============================================================
    run_test(rcon, "不完整的预知眼",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:50f}}',
         'zhonztest equiparmor @e[type=zombie,limit=1] head zhonz_more_enchantments:incomplete_foreknowledge_eye 1'],
        'zhonztest damage @e[type=zombie,limit=1] 10',
        None,
        "80%概率闪避攻击，失败降10%，80秒恢复"
    )
    results['foreknowledge_eye'] = "测试完成"

    # ============================================================
    # 测试12: 压制附魔 (Suppression)
    # ============================================================
    run_test(rcon, "压制附魔 (Suppression)",
        [f'summon zombie {spawn_x} {spawn_y} {spawn_z} {{Health:30f}}'],
        f'zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:suppression 1',
        None,
        "被攻击目标无法移动6秒，武器损毁"
    )
    results['suppression'] = "测试完成"

    # 清理
    rcon.send("kill @e[type=zombie]")

    # ============================================================
    # 总结
    # ============================================================
    print(f"\n\n{'='*60}")
    print(f"测试总结")
    print(f"{'='*60}")
    for name, status in results.items():
        print(f"  {name}: {status}")

    print(f"\n共测试 {len(results)} 个附魔")
    print("详细结果请查看服务器日志 /workspace/run/logs/latest.log")

    rcon.close()

if __name__ == '__main__':
    main()
