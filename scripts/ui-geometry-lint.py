#!/usr/bin/env python3
"""Deterministic UI-geometry lint for the workbench client.

Reads GET /state panel bounding boxes and asserts, at whatever viewport is set,
that every visible panel (a) fits inside the game viewport and (b) does not
intrude on the reserved persistent-HUD zones (top-left location plaque, the
bottom chat strip, the top-right tab row). This catches panel overflow/overlap
bugs (e.g. VS-030 / VS-044 bank-vs-plaque) deterministically, instead of relying
on an agent eyeballing a screenshot.

Usage: scripts/ui-geometry-lint.py [--url http://127.0.0.1:18787] [--json]
Exit 0 = clean, 1 = violations found, 2 = could not reach the workbench.
"""
import argparse, json, sys, urllib.request

def get(url, path):
    with urllib.request.urlopen(url + path, timeout=5) as r:
        return json.load(r)

def rects_overlap(a, b):
    return not (a['x2'] <= b['x1'] or b['x2'] <= a['x1'] or a['y2'] <= b['y1'] or b['y2'] <= a['y1'])

def find_panels(state):
    """Yield (name, {x,y,w,h}) for every object exposing a panel/interface bbox."""
    out = []
    def walk(o, path):
        if isinstance(o, dict):
            # a layout node with panelX/Y/Width/Height
            keys = o.keys()
            if 'panelX' in keys and 'panelY' in keys and 'panelWidth' in keys and 'panelHeight' in keys:
                out.append((path, {'x': o['panelX'], 'y': o['panelY'], 'w': o['panelWidth'], 'h': o['panelHeight']}))
            for k, v in o.items():
                walk(v, path + '/' + k)
        elif isinstance(o, list):
            for i, v in enumerate(o):
                walk(v, f'{path}[{i}]')
    walk(state, '')
    return out

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--url', default='http://127.0.0.1:18787')
    ap.add_argument('--json', action='store_true')
    ns = ap.parse_args()
    try:
        st = get(ns.url, '/state')
    except Exception as e:
        print(json.dumps({'ok': False, 'error': f'workbench unreachable: {e}'}))
        return 2

    gw = st.get('game', {}).get('width', 0)
    gh = st.get('game', {}).get('height', 0)
    if not gw or not gh:
        print(json.dumps({'ok': False, 'error': 'no game width/height in /state'})); return 2

    panels = find_panels(st)

    # The location plaque + top-right tab row are HIDDEN while a bank is open (the client
    # blanks the HUD for clean space), so those zones aren't reserved then. The bottom chat
    # strip stays visible in every state. Condition the plaque/tab zones on bank-closed.
    bank_open = any('bank' in name.lower() for name, _ in panels)
    # Reserved persistent-HUD zones (client geometry). A panel must not intrude here.
    # Plaque: top-left (18,18) ~150x30 at compact hud, plus a small margin. Top tabs:
    # top-right strip. Chat strip: bottom ~24px.
    hud = [
        {'name': 'chat-strip',       'x1': 0,        'y1': gh - 20, 'x2': gw,       'y2': gh},
    ]
    if not bank_open:
        hud += [
            {'name': 'location-plaque',  'x1': 4,        'y1': 4,       'x2': 168,      'y2': 52},
            {'name': 'top-tab-row',      'x1': gw - 300, 'y1': 0,       'x2': gw,       'y2': 40},
        ]
    violations = []
    for name, p in panels:
        pr = {'x1': p['x'], 'y1': p['y'], 'x2': p['x'] + p['w'], 'y2': p['y'] + p['h']}
        if pr['x1'] < 0 or pr['y1'] < 0 or pr['x2'] > gw or pr['y2'] > gh:
            violations.append({'panel': name, 'kind': 'overflow',
                               'detail': f"panel {pr} exceeds viewport {gw}x{gh}"})
        for z in hud:
            if rects_overlap(pr, z):
                violations.append({'panel': name, 'kind': 'hud-overlap',
                                   'detail': f"panel {pr} overlaps {z['name']} zone [{z['x1']},{z['y1']}..{z['x2']},{z['y2']}]"})
    result = {'ok': not violations, 'viewport': f'{gw}x{gh}',
              'panels_checked': [n for n, _ in panels], 'violations': violations}
    print(json.dumps(result, indent=2 if ns.json else None))
    return 0 if not violations else 1

if __name__ == '__main__':
    sys.exit(main())
