#!/bin/bash
# Build client, cleanly relaunch workbench (fresh login = skin-on), open bank at a viewport, screenshot + lint.
# Usage: bankiter.sh [viewport_index]  (default 5 = Classic)
cd /Users/s/Desktop/voidscape-github-latest || exit 2
VP="${1:-5}"
BUILD=$(cd Client_Base && ant compile 2>&1 | tail -3)
echo "$BUILD" | grep -q "BUILD SUCCESSFUL" || { echo "BUILD FAILED"; echo "$BUILD"; exit 1; }
pkill -TERM -f "Open_RSC_Client" 2>/dev/null; sleep 4
pkill -9 -f "voidscape.workbench\|compile-and-run" 2>/dev/null || true
lsof -ti tcp:18787 2>/dev/null | xargs kill -9 2>/dev/null || true
sleep 12
VOIDSCAPE_WORKBENCH_PORT=18787 scripts/run-workbench-client.sh --login wbtest:voidtest123 >tmp/qa/verify/wb-iter.log 2>&1 &
for i in $(seq 1 150); do curl -s -m2 http://127.0.0.1:18787/health >/dev/null 2>&1 && break; sleep 1; done
curl -s -m3 http://127.0.0.1:18787/health >/dev/null 2>&1 || { echo "workbench DOWN"; tail -5 tmp/qa/verify/wb-iter.log; exit 1; }
grep -q resumed tmp/qa/verify/wb-iter.log && echo "!! resumed (skin may be off)" || echo "fresh login"
sleep 7
W=http://127.0.0.1:18787
curl -s -m5 -X POST "$W/dev/ready" -H 'content-type: application/json' -d '{}' >/dev/null; sleep 1
curl -s -m5 -X POST "$W/dev/viewport" -H 'content-type: application/json' -d "{\"index\":$VP}" >/dev/null; sleep 1
for c in "::item 10 501" "::noteditem 401 60" "::item 70 1" "::item 87 1" "::item 120 5" "::item 88 1" "::item 90 12000"; do
  curl -s -m5 -X POST "$W/input/command" -H 'content-type: application/json' -d "{\"command\":\"$c\"}" >/dev/null; sleep 1.0; done
curl -s -m5 -X POST "$W/input/command" -H 'content-type: application/json' -d '{"command":"::quickbank"}' >/dev/null; sleep 2
curl -s -m5 "$W/state" | python3 -c 'import sys,json;b=json.load(sys.stdin)["interfaces"]["bank"]["layout"];print("branch={} slotW={} slotH={} panelX={} panelY={} panelW={} panelH={} bottom={}".format("WEB" if b["slotWidth"]==49 else "other",b["slotWidth"],b["slotHeight"],b["panelX"],b["panelY"],b["panelWidth"],b["panelHeight"],b["panelY"]+b["panelHeight"]))'
python3 scripts/ui-geometry-lint.py; echo "lint-exit=$?"
curl -s -m5 "$W/screenshot" | python3 -c 'import sys,json;print("SHOT:",json.load(sys.stdin)["pngPath"])'
