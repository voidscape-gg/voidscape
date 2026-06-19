"""Direct OpenAI API call wrapper for /v1/images/edits with gpt-image-1.5.

Locked v1 config (see DIVERGENCE.md / project memory):
  model=gpt-image-1.5, input_fidelity=high, quality=high,
  background=transparent, size=1024x1024, output_format=png.

gpt-image-2 is rejected here because it does not support input_fidelity or
transparent background as of 2026-04. Don't change without re-validating.
"""
from __future__ import annotations
import base64
import os
import sys
import time
from pathlib import Path

import requests

API_URL = "https://api.openai.com/v1/images/edits"


def call_edits_high_fidelity(
    reference: Path,
    prompt: str,
    n: int = 1,
    size: str = "1024x1024",
    quality: str = "high",
    background: str = "transparent",
    input_fidelity: str = "high",
    model: str = "gpt-image-1.5",
    timeout: int = 420,
) -> list[bytes]:
    """Call /v1/images/edits and return raw PNG bytes per variant."""
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY not set")

    headers = {"Authorization": f"Bearer {api_key}"}
    with open(reference, "rb") as f:
        files = [("image", (reference.name, f.read(), "image/png"))]
    data = {
        "model": model,
        "prompt": prompt,
        "n": str(n),
        "size": size,
        "quality": quality,
        "input_fidelity": input_fidelity,
        "output_format": "png",
    }
    if background and not model.startswith("gpt-image-2"):
        data["background"] = background

    backoff = 2.0
    for attempt in range(4):
        r = requests.post(API_URL, headers=headers, data=data, files=files, timeout=timeout)
        if r.status_code == 429 or r.status_code >= 500:
            print(f"  HTTP {r.status_code}; retrying in {backoff:.0f}s", file=sys.stderr)
            time.sleep(backoff)
            backoff *= 2
            continue
        r.raise_for_status()
        body = r.json()
        return [base64.b64decode(item["b64_json"]) for item in body["data"]]
    raise RuntimeError("exhausted retries")


