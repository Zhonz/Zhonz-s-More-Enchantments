#!/usr/bin/env python3
"""简单测试 - 验证命令系统工作正常"""
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

    def send(self, cmd, wait=0.3):
        print(f"  >>> {cmd}")
        self._send_packet(2, cmd)
        time.sleep(wait)
        resp = self._recv_packet()
        if resp[1]:
            print(f"  <<< {resp[1][:150]}")
        return resp[1]

    def close(self):
        if self.sock:
            self.sock.close()

def main():
    rcon = RCONClient()
    try:
        rcon.connect()
    except Exception as e:
        print(f"RCON连接失败: {e}")
        sys.exit(1)

    print("\n=== 基础命令测试 ===")
    rcon.send("seed")
    rcon.send("list")
    rcon.send("time set day")

    print("\n=== 测试 zhonztest 命令 ===")
    result = rcon.send("help zhonztest")

    print("\n=== 生成测试假人 ===")
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.5)
    result = rcon.send("summon zombie 100 64 100 {Health:20f,CustomName:'\"测试假人\"',CustomNameVisible:1b}")
    time.sleep(0.5)

    print("\n=== 测试 damage 命令 ===")
    result = rcon.send("zhonztest damage @e[type=zombie,limit=1] 5")

    print("\n=== 测试 info 命令 ===")
    result = rcon.send("zhonztest info @e[type=zombie,limit=1]")

    print("\n=== 测试 equiparmor 命令 ===")
    result = rcon.send("zhonztest equiparmor @e[type=zombie,limit=1] chest zhonz_more_enchantments:deep_seas_grace 3")

    print("\n=== 测试 attack 命令（终结附魔）===")
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.5)
    rcon.send("summon zombie 100 64 100 {Health:20f}")
    time.sleep(0.5)
    result = rcon.send("zhonztest attack @e[type=zombie,limit=1] zhonz_more_enchantments:finale 1")
    time.sleep(0.5)
    rcon.send("zhonztest info @e[type=zombie,limit=1]")

    print("\n=== 测试深海的供养 ===")
    rcon.send("kill @e[type=zombie]")
    time.sleep(0.5)
    rcon.send("summon zombie 100 64 100 {Health:20f}")
    time.sleep(0.5)
    rcon.send("zhonztest equiparmor @e[type=zombie,limit=1] chest zhonz_more_enchantments:deep_seas_grace 3")
    time.sleep(0.5)
    print("  受伤前血量:")
    rcon.send("zhonztest info @e[type=zombie,limit=1]")
    result = rcon.send("zhonztest damage @e[type=zombie,limit=1] 10")
    time.sleep(0.5)
    print("  受伤后血量:")
    rcon.send("zhonztest info @e[type=zombie,limit=1]")

    print("\n=== 清理 ===")
    rcon.send("kill @e[type=zombie]")

    print("\n=== 检查服务器日志 ===")

    rcon.close()
    print("\n测试完成！")

if __name__ == '__main__':
    main()
