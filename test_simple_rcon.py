#!/usr/bin/env python3
"""简单RCON测试 - 验证命令执行"""
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

    def send(self, cmd, wait=0.5):
        print(f"\n>>> {cmd}")
        self._send_packet(2, cmd)
        time.sleep(wait)
        resp = self._recv_packet()
        print(f"<<< [type={resp[0]}] {repr(resp[1])}")
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

    # 测试1: 简单的help命令
    print("\n" + "="*60)
    print("测试1: help命令")
    print("="*60)
    rcon.send("help")

    # 测试2: 列出玩家
    print("\n" + "="*60)
    print("测试2: list命令")
    print("="*60)
    rcon.send("list")

    # 测试3: 生成僵尸
    print("\n" + "="*60)
    print("测试3: 生成僵尸")
    print("="*60)
    rcon.send("summon zombie 100 64 100")
    time.sleep(0.5)

    # 测试4: 检查实体
    print("\n" + "="*60)
    print("测试4: 检查实体数量")
    print("="*60)
    rcon.send("execute if entity @e[type=zombie] run say Zombie exists!")

    # 测试5: 测试自定义命令
    print("\n" + "="*60)
    print("测试5: 测试zhonztest info命令")
    print("="*60)
    rcon.send("zhonztest info @e[type=zombie,limit=1]")

    # 测试6: 直接伤害测试
    print("\n" + "="*60)
    print("测试6: 伤害测试")
    print("="*60)
    rcon.send("zhonztest damage @e[type=zombie,limit=1] 5")

    # 清理
    rcon.send("kill @e[type=zombie]")

    rcon.close()

if __name__ == '__main__':
    main()
