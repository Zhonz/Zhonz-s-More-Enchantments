#!/usr/bin/env python3
"""RCON client for testing Minecraft enchantment mod."""

import socket
import struct
import sys
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
        # Login
        self._send(3, self.password)
        _, response = self._recv()
        return True

    def _send(self, pid, data):
        if isinstance(data, str):
            data = data.encode('utf-8')
        # Packet: length(4) + request_id(4) + type(4) + payload + null(1) + null(1)
        payload = struct.pack('<ii', pid, pid) + data + b'\x00\x00'
        length = len(payload)
        packet = struct.pack('<i', length) + payload
        self.sock.sendall(packet)

    def _recv(self):
        # Read length
        length_data = self._recv_exact(4)
        length = struct.unpack('<i', length_data)[0]
        # Read body
        body = self._recv_exact(length)
        if len(body) < 8:
            return -1, ''
        request_id = struct.unpack('<i', body[:4])[0]
        # type = struct.unpack('<i', body[4:8])[0]
        payload = body[8:]
        # Find null terminator
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

    # Basic tests
    cmd("list")
    cmd("gamerule keepInventory true")

    # Spawn a test dummy
    cmd("execute at @p run zhonztest spawndummy")
    time.sleep(0.5)

    # Test deep_seas_grace (深海的供养)
    print("\n=== Testing Deep Sea's Grace (深海的供养) ===")
    cmd("execute at @p run zhonztest equiparmor @e[type=zombie,limit=1,sort=nearest] chest zhonz_more_enchantments:deep_seas_grace 1")
    time.sleep(0.3)
    # Damage the zombie
    cmd("execute at @p run zhonztest damage @e[type=zombie,limit=1,sort=nearest] 10")
    time.sleep(0.5)
    # Check health - should have been healed
    cmd("execute at @p run zhonztest info @e[type=zombie,limit=1,sort=nearest]")

    # Test shell_strip (剥壳)
    print("\n=== Testing Shell Strip (剥壳) ===")
    # Clear old dummy and spawn new one
    cmd("execute at @p run zhonztest cleardummies")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest spawndummy")
    time.sleep(0.3)
    # Attack with shell_strip
    cmd("execute at @p run zhonztest attack @e[type=zombie,limit=1,sort=nearest] zhonz_more_enchantments:shell_strip 1")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest info @e[type=zombie,limit=1,sort=nearest]")

    # Test fishball (鱼丸)
    print("\n=== Testing Fishball (鱼丸) ===")
    cmd("execute at @p run zhonztest cleardummies")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest spawndummy")
    time.sleep(0.3)
    # Equip fishball on the dummy
    cmd("execute at @p run zhonztest equiparmor @e[type=zombie,limit=1,sort=nearest] chest zhonz_more_enchantments:fishball 1")
    time.sleep(0.3)
    # Damage the dummy - should trigger fishball
    cmd("execute at @p run zhonztest damage @e[type=zombie,limit=1,sort=nearest] 5")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest info @e[type=zombie,limit=1,sort=nearest]")

    # Test grievous_wound (重伤)
    print("\n=== Testing Grievous Wound (重伤) ===")
    cmd("execute at @p run zhonztest cleardummies")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest spawndummy")
    time.sleep(0.3)
    # Attack with grievous_wound
    cmd("execute at @p run zhonztest attack @e[type=zombie,limit=1,sort=nearest] zhonz_more_enchantments:grievous_wound 1")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest info @e[type=zombie,limit=1,sort=nearest]")

    # Test finale (终结)
    print("\n=== Testing Finale (终结) ===")
    cmd("execute at @p run zhonztest cleardummies")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest spawndummy")
    time.sleep(0.3)
    # Attack with finale
    cmd("execute at @p run zhonztest attack @e[type=zombie,limit=1,sort=nearest] zhonz_more_enchantments:finale 1")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest info @e[type=zombie,limit=1,sort=nearest]")

    # Test fools_mask (假面的愚者)
    print("\n=== Testing Fool's Mask (假面的愚者) ===")
    cmd("execute at @p run zhonztest cleardummies")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest spawndummy")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest equiparmor @e[type=zombie,limit=1,sort=nearest] head zhonz_more_enchantments:fools_mask 1")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest damage @e[type=zombie,limit=1,sort=nearest] 5")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest info @e[type=zombie,limit=1,sort=nearest]")

    # Test divine_protection (神护)
    print("\n=== Testing Divine Protection (神护) ===")
    cmd("execute at @p run zhonztest cleardummies")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest spawndummy")
    time.sleep(0.3)
    cmd("execute at @p run zhonztest equiparmor @e[type=zombie,limit=1,sort=nearest] chest zhonz_more_enchantments:divine_protection 1")
    time.sleep(0.3)
    # Damage to low health, should trigger divine protection
    cmd("execute at @p run zhonztest damage @e[type=zombie,limit=1,sort=nearest] 15")
    time.sleep(0.5)
    cmd("execute at @p run zhonztest info @e[type=zombie,limit=1,sort=nearest]")

    # Clean up
    print("\n=== Cleanup ===")
    cmd("execute at @p run zhonztest cleardummies")

    rcon.close()
    print("\nAll tests completed!")


if __name__ == '__main__':
    run_tests()
