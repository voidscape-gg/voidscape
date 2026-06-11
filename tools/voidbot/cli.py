"""voidbot CLI — thin client that talks to a running voidbotd over its control socket.

Every invocation prints one JSON object to stdout and sets an exit code:
  0  ok / condition matched
  1  ok:false (action rejected, wait timed out, etc.)
  2  usage / connection error

Examples:
  voidbot goto 120 648
  voidbot state inventory
  voidbot wait inventory-contains --id 526 --timeout 10
  voidbot wait npc-dead --id 67 --timeout 30
  voidbot admin "::item 10 1000"
  voidbot events --since 0
"""
import argparse
import json
import socket
import sys

CTRL_PORT = 18900


def send(req, port):
    # Retry the connect briefly: the daemon may still be binding / mid-login.
    import time
    last = None
    for _ in range(25):
        try:
            s = socket.create_connection(("127.0.0.1", port), timeout=120)
            break
        except OSError as e:
            last = e; time.sleep(0.2)
    else:
        raise last
    f = s.makefile("rwb")
    f.write((json.dumps(req) + "\n").encode()); f.flush()
    line = f.readline()
    s.close()
    if not line:
        return {"ok": False, "error": "no response from daemon"}
    return json.loads(line)


def kv(rest):
    """Parse `--key value`/`--flag` pairs and bare positionals into args dict."""
    args = {}; pos = []
    i = 0
    while i < len(rest):
        t = rest[i]
        if t.startswith("--"):
            key = t[2:].replace("-", "_")   # --server-index -> server_index
            if i + 1 < len(rest) and not rest[i+1].startswith("--"):
                args[key] = rest[i+1]; i += 2
            else:
                args[key] = True; i += 1
        else:
            pos.append(t); i += 1
    return args, pos


def main():
    ap = argparse.ArgumentParser(add_help=True)
    ap.add_argument("--ctrl-port", type=int, default=CTRL_PORT)
    ap.add_argument("cmd")
    ap.add_argument("rest", nargs=argparse.REMAINDER)
    ns = ap.parse_args()
    cmd = ns.cmd
    args, pos = kv(ns.rest)

    # positional sugar per command
    if cmd in ("goto", "walk", "walk-step") and len(pos) >= 2:
        args["x"], args["y"] = pos[0], pos[1]
    elif cmd == "take-item" and len(pos) >= 3:
        args["x"], args["y"], args["id"] = pos[0], pos[1], pos[2]
    elif cmd in ("npc-talk", "npc-command", "attack-npc") and pos and "id" not in args and "server_index" not in args:
        args["server_index"] = pos[0]
    elif cmd == "menu-reply" and pos:
        args["option"] = pos[0]
    elif cmd in ("item-command", "drop", "equip", "unequip") and pos:
        args["slot"] = pos[0]
    elif cmd == "admin" and pos:
        args["command"] = " ".join(pos)
    elif cmd == "say" and pos:
        args["text"] = " ".join(pos)
    elif cmd == "state" and pos:
        args["section"] = pos[0]
    elif cmd == "wait" and pos:
        args["condition"] = pos[0]

    try:
        resp = send({"cmd": cmd, "args": args}, ns.ctrl_port)
    except (ConnectionError, OSError) as e:
        print(json.dumps({"ok": False, "error": "cannot reach voidbotd: %s" % e}))
        sys.exit(2)

    print(json.dumps(resp, indent=None))
    sys.exit(0 if resp.get("ok") else 1)


if __name__ == "__main__":
    main()
