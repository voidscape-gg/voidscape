from __future__ import annotations

from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
import json
from pathlib import Path
from urllib.parse import urlparse

from PIL import Image

from .evidence_contract import require_current_evidence_contract
from .geometry import runtime_mirror
from .paths import REPO_ROOT


LAYERS = {
    "base": Path("compiled/base"),
    "mullet": Path("compiled/layers/future_mullet"),
    "mustache": Path("compiled/layers/future_mustache"),
    "composed": Path("compiled/composed"),
    "player": Path("compiled/player"),
}

HTML = r'''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Voidscape Draft Look Review</title><style>
body{margin:0;background:#0d1117;color:#e6edf3;font:14px system-ui}header,nav{display:flex;gap:14px;align-items:center;padding:12px 18px;border-bottom:1px solid #30363d}h1{font-size:18px;margin:0 auto 0 0}main{height:calc(100vh - 112px);display:grid;place-items:center;overflow:auto}.stage{background:repeating-conic-gradient(#27303b 0 25%,#18202a 0 50%) 50%/16px 16px;border:1px solid #58a6ff}canvas{display:block;image-rendering:pixelated}button,input,select{background:#21262d;color:#e6edf3;border:1px solid #484f58;border-radius:5px;padding:6px}label{white-space:nowrap}.warn{color:#f0b65b}
</style></head><body><header><h1>Draft Look Review</h1><span class="warn">Read-only · non-shipping · deterministic draft</span></header>
<nav><label><input type="checkbox" data-layer="base" checked>Base</label><label><input type="checkbox" data-layer="mullet" checked>Mullet</label><label><input type="checkbox" data-layer="mustache" checked>Mustache</label><label><input type="checkbox" id="composed">Composed</label><label><input type="checkbox" id="player">Full player</label><input id="frame" type="range" min="0" value="0"><button id="play">Play</button><label>Zoom <select id="zoom"><option>1</option><option selected>4</option><option>8</option><option>12</option></select>×</label><span id="label"></span></nav>
<main><div class="stage"><canvas id="canvas"></canvas></div></main><script>
const C=document.querySelector('#canvas'),X=C.getContext('2d');X.imageSmoothingEnabled=false;let S;let timer=null;
const load=u=>new Promise((ok,no)=>{const i=new Image;i.onload=()=>ok(i);i.onerror=no;i.src=u+'?t='+Date.now()});
async function draw(){const n=+frame.value,state=S.runtimeStates[n],player=document.querySelector('#player').checked,composed=document.querySelector('#composed').checked;const names=player?['player']:composed?['composed']:[...document.querySelectorAll('[data-layer]:checked')].map(x=>x.dataset.layer);const imgs=await Promise.all(names.map(x=>load(`/api/frame/${x}/${n}.png`)));const first=imgs[0]||await load(`/api/frame/base/${n}.png`);C.width=first.width;C.height=first.height;X.clearRect(0,0,C.width,C.height);imgs.forEach(i=>X.drawImage(i,0,0));const z=+zoom.value;C.style.width=C.width*z+'px';C.style.height=C.height*z+'px';label.textContent=`${n+1}/30 ${state.direction} phase ${state.phase} · stored ${state.storedOffset} · mirror ${state.mirrorX}`}
fetch('/api/state').then(r=>r.json()).then(s=>{S=s;frame.max=s.runtimeStates.length-1;draw()});document.querySelectorAll('input,select').forEach(x=>x.onchange=draw);play.onclick=()=>{if(timer){clearInterval(timer);timer=null;play.textContent='Play'}else{timer=setInterval(()=>{frame.value=(+frame.value+1)%S.runtimeStates.length;draw()},350);play.textContent='Stop'}};
</script></body></html>'''


class DraftReview:
    def __init__(self, root: Path, *, repo_root: Path = REPO_ROOT):
        self.root = root.resolve()
        self.root.relative_to((repo_root / "tmp").resolve())
        self.report = json.loads((self.root / "report.json").read_text())
        require_current_evidence_contract(self.report)
        if self.report.get("shipping") is not False:
            raise ValueError("review root is not a non-shipping draft Look")
        if len(self.report.get("runtimeStates", [])) != 30:
            raise ValueError("draft Look review requires exactly 30 runtime states")

    def frame(self, layer: str, state_index: int) -> Image.Image:
        if layer not in LAYERS or not 0 <= state_index < 30:
            raise ValueError("unknown review frame")
        state = self.report["runtimeStates"][state_index]
        path = (self.root / LAYERS[layer] / f"frame_{state['storedOffset']:02d}.png").resolve()
        path.relative_to(self.root)
        image = Image.open(path).convert("RGBA")
        return runtime_mirror(image) if state["mirrorX"] else image


def serve_draft_review(review: DraftReview, host: str = "127.0.0.1", port: int = 18790) -> None:
    if host not in {"127.0.0.1", "localhost"}:
        raise ValueError("draft review must bind to loopback")

    class Handler(BaseHTTPRequestHandler):
        def send(self, status: int, body: bytes, kind: str) -> None:
            self.send_response(status); self.send_header("Content-Type", kind); self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self'")
            self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

        def do_GET(self) -> None:
            path = urlparse(self.path).path
            if path == "/": return self.send(200, HTML.encode(), "text/html; charset=utf-8")
            if path == "/api/state":
                payload = {"look": review.report["look"], "layers": list(LAYERS),
                           "runtimeStates": review.report["runtimeStates"], "readOnly": True, "shipping": False}
                return self.send(200, json.dumps(payload).encode(), "application/json")
            parts = path.strip("/").split("/")
            if len(parts) == 4 and parts[:2] == ["api", "frame"] and parts[3].endswith(".png"):
                try:
                    image = review.frame(parts[2], int(parts[3][:-4])); buffer = BytesIO(); image.save(buffer, "PNG")
                    return self.send(200, buffer.getvalue(), "image/png")
                except (ValueError, OSError): return self.send(404, b"missing", "text/plain")
            self.send(404, b"missing", "text/plain")

        def do_POST(self) -> None: self.send(HTTPStatus.METHOD_NOT_ALLOWED, b"read only", "text/plain")
        def do_PUT(self) -> None: self.send(HTTPStatus.METHOD_NOT_ALLOWED, b"read only", "text/plain")
        def log_message(self, format: str, *args) -> None: return

    server = ThreadingHTTPServer((host, port), Handler)
    print(f"Draft Look review: http://{server.server_address[0]}:{server.server_address[1]}", flush=True)
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close()
