#!/usr/bin/env python3
"""RCON测试 - 验证命令执行"""
import socket
import struct
import time

class RCONClient:
    def __init__(self, host='127.0.0.1', port=25575, password='test123'):
        self.host = host
        self.port = port
        self.password = password
        self.sock = None
        self.request_id = 0

    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(10)
        self.sock.connect((self.host, self.port))
        self._send_packet(3, self.password)
        resp = self._recv_packet()
        print(f"[RCON] Login response type: {resp[0]}, id: {resp[1][:50] if resp[1] else 'empty'}")
        if resp[0] != 2:
            raise Exception("Login failed")
        print("[RCON] Connected and authenticated")

    def _send_packet(self, packet_type, data):
        self.request_id += 1
        payload = struct.pack('<iii', self.request_id, packet_type, 0) + data.encode('utf-8') + b'\x00\x00'
        self.sock.send(struct.pack('<i', len(payload)) + payload)

    def _recv_packet(self):
        # Read length (4 bytes, little-endian int)
        length_data = b''
        while len(length_data) < 4:
            length_data += self.sock.recv(4 - len(length_data))
        length = struct.unpack('<i', length_data)[0]
        if length <= 0 or length > 4110:
            return (0, "")
        # Read the rest
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
        # Remove trailing null bytes
        if payload.endswith(b'\x00\x00'):
            payload = payload[:-2]
        elif payload.endswith(b'\x00'):
            payload = payload[:-1]
        return (req_type, payload.decode('utf-8', errors='replace'))

    def send(self, cmd):
        print(f"  [SEND] {cmd}")
        self._send_packet(2, cmd)
        time.sleep(0.2)
        resp = self._recv_packet()
        print(f"  [RECV] type={resp[0]}, data={resp[1]!r}")
        return resp[1]

    def close(self):
        if self.sock:
            self.sock.close()

def main():
    rcon = RCONClient()
    rcon.connect()

    # Test 1: say command (should produce output in server log)
    print("\n=== Test 1: say ===")
    rcon.send('say Hello from RCON')

    # Test 2: seed (should return seed value)
    print("\n=== Test 2: seed ===")
    rcon.send('seed')

    # Test 3: list (should return player list)
    print("\n=== Test 3: list ===")
    rcon.send('list')

    # Test 4: time query
    print("\n=== Test 4: time ===")
    rcon.send('time query daytime')

    # Test 5: gamerule
    print("\n=== Test 5: gamerule ===")
    rcon.send('gamerule keepInventory')

    # Test 6: try giving item to server console (no player needed)
    print("\n=== Test 6: give to server ===")
    rcon.send('give @s diamond_sword')

    # Test 7: set time
    print("\n=== Test 7: set time ===")
    rcon.send('time set day')

    # Test 8: summon zombie
    print("\n=== Test 8: summon zombie ===")
    rcon.send('summon zombie 0 64 0')

    # Test 9: check entities
    print("\n=== Test 9: execute ===")
    rcon.send('execute as @e[type=zombie] run say I am a zombie')

    rcon.close()
    print("\n=== Done ===")

if __name__ == '__main__':
    main()
