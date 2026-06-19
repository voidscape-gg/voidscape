"""Sprite encode/decode — re-exports from the existing extract_ref.py / pack.py.

Keeps those modules as the single source of truth for the binary format.
"""
from __future__ import annotations
import sys

from .paths import TOOL_DIR

if str(TOOL_DIR) not in sys.path:
    sys.path.insert(0, str(TOOL_DIR))

from extract_ref import decode  # noqa: E402
from pack import encode  # noqa: E402

__all__ = ["decode", "encode"]
