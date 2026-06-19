#!/usr/bin/env python3
"""ui-critique.py — send a HUD screenshot to the OpenAI vision API and get an
actionable design critique. Used to iterate on the voidscape custom HUD.

Usage:
  python3 scripts/ui-critique.py <image.png> [more.png ...] [--focus "..."]

Requires OPENAI_API_KEY (in ~/.zshenv). Prints the critique to stdout.
"""
import os, sys, base64, json, urllib.request

MODEL = os.environ.get("UI_CRITIQUE_MODEL", "gpt-4o")

SYSTEM = (
    "You are a senior game UI/UX designer reviewing a RuneScape Classic private server "
    "('Voidscape') custom in-game HUD. The intended aesthetic is ornate dark-fantasy: dark "
    "wrought-iron frames with purple gem accents and warm gold/cream text, matching concept art. "
    "It renders at 1024x768. Critique the screenshot(s) with SPECIFIC, ACTIONABLE problems only "
    "(no praise): alignment/centering, spacing/padding, readability/contrast, font choice/size, "
    "color, proportions, elements clipping or overlapping, empty/unbalanced space, anything that "
    "looks broken or unpolished. Reference exactly what you see. Order by severity (worst first), "
    "max ~8 bullet points, each one line."
)

def main():
    args = sys.argv[1:]
    focus = ""
    if "--focus" in args:
        i = args.index("--focus")
        focus = args[i + 1]
        args = args[:i] + args[i + 2:]
    if not args:
        print("usage: ui-critique.py <image.png> [...] [--focus \"...\"]"); sys.exit(2)
    key = os.environ.get("OPENAI_API_KEY")
    if not key:
        print("OPENAI_API_KEY not set"); sys.exit(1)
    content = [{"type": "text", "text": ("Focus: " + focus) if focus else "Critique this HUD."}]
    for p in args:
        with open(p, "rb") as f:
            b64 = base64.b64encode(f.read()).decode()
        content.append({"type": "image_url", "image_url": {"url": f"data:image/png;base64,{b64}", "detail": "high"}})
    body = json.dumps({
        "model": MODEL,
        "messages": [{"role": "system", "content": SYSTEM}, {"role": "user", "content": content}],
        "max_tokens": 700, "temperature": 0.2,
    }).encode()
    req = urllib.request.Request("https://api.openai.com/v1/chat/completions", data=body,
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
    try:
        d = json.load(urllib.request.urlopen(req, timeout=120))
        print(d["choices"][0]["message"]["content"])
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, e.read().decode()[:500]); sys.exit(1)

if __name__ == "__main__":
    main()
