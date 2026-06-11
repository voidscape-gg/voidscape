"""Logging TCP proxy: client -> 127.0.0.1:LISTEN -> 127.0.0.1:UPSTREAM.
Dumps each frame (2-byte big-endian length + opcode + payload) with direction,
so we can read the exact wire bytes of login + early packets. Temporary tool.

Usage: python capture-proxy.py [listen_port] [upstream_port] [out.jsonl]
"""
import socket, sys, threading, json, time

LISTEN = int(sys.argv[1]) if len(sys.argv) > 1 else 43600
UPSTREAM = int(sys.argv[2]) if len(sys.argv) > 2 else 43596
OUT = sys.argv[3] if len(sys.argv) > 3 else "tools/voidbot/capture.jsonl"

log_lock = threading.Lock()
def log(direction, data):
    with log_lock:
        with open(OUT, "a") as f:
            f.write(json.dumps({
                "t": round(time.time(), 3),
                "dir": direction,
                "len": len(data),
                "hex": data.hex(),
            }) + "\n")

def pump(src, dst, direction):
    try:
        while True:
            data = src.recv(8192)
            if not data:
                break
            log(direction, data)
            dst.sendall(data)
    except OSError:
        pass
    finally:
        try: dst.shutdown(socket.SHUT_WR)
        except OSError: pass

def handle(client):
    up = socket.create_connection(("127.0.0.1", UPSTREAM))
    threading.Thread(target=pump, args=(client, up, "C2S"), daemon=True).start()
    pump(up, client, "S2C")
    client.close(); up.close()

def main():
    open(OUT, "w").close()
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", LISTEN))
    srv.listen(5)
    print(f"proxy listening {LISTEN} -> {UPSTREAM}, logging to {OUT}", flush=True)
    while True:
        c, _ = srv.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()

if __name__ == "__main__":
    main()
