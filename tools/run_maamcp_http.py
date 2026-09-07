import sys
from maa_mcp.main import mcp

print("Starting MaaMCP over HTTP on 127.0.0.1:9527", flush=True)
try:
    mcp.run(transport="http", host="127.0.0.1", port=9527)
except TypeError:
    # fastmcp 2.x style
    mcp.run(host="127.0.0.1", port=9527, transport="http")
