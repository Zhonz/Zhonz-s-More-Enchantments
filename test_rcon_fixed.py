#!/usr/bin/env python3
"""修复后的RCON测试 - 正确处理Minecraft RCON协议"""
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
        
        # 登录
        self.request_id += 1
        req_id = self.request_id
        self._send_packet(req_id, 3, self.password)
        resp_id, resp_type, resp_data = self._recv_packet()
        
        print(f"登录响应: id={resp_id}, type={resp_type}, data={repr(resp_data)}")
        
        if resp_id != req_id or resp_type != 2:
            raise Exception(f"Login failed: id={resp_id}, type={resp_type}, data={resp_data}")
        print("[RCON] 连接成功")

    def _send_packet(self, req_id, packet_type, data):
        # 包结构: 长度(4) + 请求ID(4) + 类型(4) + 数据 + 2个空字节
        payload = struct.pack('<ii', req_id, packet_type) + data.encode('utf-8') + b'\x00\x00'
        self.sock.send(struct.pack('<i', len(payload)) + payload)
        print(f"  发送: id={req_id}, type={packet_type}, data={repr(data)}")

    def _recv_packet(self):
        try:
            # 读取长度
            length_data = self._recv_exact(4)
            if len(length_data) < 4:
                return (0, 0, "")
            length = struct.unpack('<i', length_data)[0]
            print(f"  接收长度: {length}")
            
            if length <= 0 or length > 4110:
                return (0, 0, "")
            
            # 读取包内容
            data = self._recv_exact(length)
            if len(data) < length:
                print(f"  数据不完整: {len(data)}/{length}")
                return (0, 0, "")
            
            req_id, req_type = struct.unpack('<ii', data[:8])
            payload = data[8:]
            # 移除末尾的空字节
            while payload.endswith(b'\x00'):
                payload = payload[:-1]
            
            resp_data = payload.decode('utf-8', errors='replace')
            print(f"  接收: id={req_id}, type={req_type}, data={repr(resp_data)}")
            return (req_id, req_type, resp_data)
        except Exception as e:
            print(f"  接收错误: {e}")
            return (0, 0, str(e))

    def _recv_exact(self, n):
        data = b''
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                break
            data += chunk
        return data

    def send(self, cmd, wait=0.5):
        print(f"\n>>> {cmd}")
        self.request_id += 1
        req_id = self.request_id
        self._send_packet(req_id, 2, cmd)
        time.sleep(wait)
        resp_id, resp_type, resp_data = self._recv_packet()
        if resp_data:
            print(f"<<< {resp_data}")
        return resp_data

    def close(self):
        if self.sock:
            self.sock.close()

def main():
    rcon = RCONClient()
    try:
        rcon.connect()
    except Exception as e:
        print(f"RCON连接失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

    # 测试简单命令
    print("\n" + "="*60)
    print("测试: say命令")
    print("="*60)
    rcon.send("say Hello from RCON test!")
    
    # 测试list命令
    print("\n" + "="*60)
    print("测试: list命令")
    print("="*60)
    rcon.send("list")
    
    # 测试生成僵尸
    print("\n" + "="*60)
    print("测试: 生成僵尸")
    print("="*60)
    result = rcon.send("summon zombie 100 64 100 {CustomName:'\"TestZombie\"'}")
    time.sleep(0.5)
    
    # 测试自定义命令
    print("\n" + "="*60)
    print("测试: zhonztest info")
    print("="*60)
    rcon.send("zhonztest info @e[type=zombie,limit=1]")
    
    # 清理
    rcon.send("kill @e[type=zombie]")

    rcon.close()

if __name__ == '__main__':
    main()
