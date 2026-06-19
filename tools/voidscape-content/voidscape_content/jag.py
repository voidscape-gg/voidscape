from __future__ import annotations

import bz2
from dataclasses import dataclass
from pathlib import Path


def read_u16be(data: bytes | bytearray | memoryview, offset: int) -> int:
    return (data[offset] << 8) | data[offset + 1]


def read_u24be(data: bytes | bytearray | memoryview, offset: int) -> int:
    return (data[offset] << 16) | (data[offset + 1] << 8) | data[offset + 2]


def read_u32be(data: bytes | bytearray | memoryview, offset: int) -> int:
    return (
        (data[offset] << 24)
        | (data[offset + 1] << 16)
        | (data[offset + 2] << 8)
        | data[offset + 3]
    )


def write_u16be(value: int) -> bytes:
    if not 0 <= value <= 0xFFFF:
        raise ValueError(f"uint16 out of range: {value}")
    return bytes(((value >> 8) & 0xFF, value & 0xFF))


def write_u24be(value: int) -> bytes:
    if not 0 <= value <= 0xFFFFFF:
        raise ValueError(f"uint24 out of range: {value}")
    return bytes(((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF))


def write_u32be(value: int) -> bytes:
    if not 0 <= value <= 0xFFFFFFFF:
        raise ValueError(f"uint32 out of range: {value}")
    return bytes(
        (
            (value >> 24) & 0xFF,
            (value >> 16) & 0xFF,
            (value >> 8) & 0xFF,
            value & 0xFF,
        )
    )


def jag_hash(name: str) -> int:
    value = 0
    for char in name.upper():
        value = value * 61 + ord(char) - 32
    return value & 0xFFFFFFFF


def _decompress_bzip_payload(payload: bytes, expected_len: int) -> bytes:
    # OpenRSC cache payloads omit the "BZhN" header. Existing archives are
    # normally BZh1, but trying all levels makes the reader tolerant.
    if payload.startswith(b"BZh"):
        return bz2.decompress(payload)
    last_error: Exception | None = None
    for level in range(1, 10):
        try:
            result = bz2.decompress(b"BZh" + bytes((ord("0") + level,)) + payload)
            if len(result) == expected_len:
                return result
        except OSError as exc:
            last_error = exc
    raise ValueError(f"could not decompress bzip payload: {last_error}")


@dataclass(frozen=True)
class JagEntry:
    hash: int
    unpacked_len: int
    packed_len: int
    packed_payload: bytes
    data: bytes

    @classmethod
    def uncompressed(cls, hash_value: int, data: bytes) -> "JagEntry":
        return cls(
            hash=hash_value,
            unpacked_len=len(data),
            packed_len=len(data),
            packed_payload=data,
            data=data,
        )


@dataclass
class JagArchive:
    entries: list[JagEntry]
    outer_was_compressed: bool = False

    @classmethod
    def read(cls, path: Path) -> "JagArchive":
        raw = path.read_bytes()
        if len(raw) < 6:
            raise ValueError(f"{path} is too small to be a JAG archive")
        unpacked_len = read_u24be(raw, 0)
        packed_len = read_u24be(raw, 3)
        if 6 + packed_len > len(raw):
            raise ValueError(f"{path} outer payload overruns file")
        packed_blob = raw[6 : 6 + packed_len]
        outer_was_compressed = packed_len != unpacked_len
        if outer_was_compressed:
            blob = _decompress_bzip_payload(packed_blob, unpacked_len)
        else:
            blob = packed_blob
        if len(blob) != unpacked_len:
            raise ValueError(f"{path} outer unpacked length mismatch")
        if len(blob) < 2:
            raise ValueError(f"{path} inner directory is missing")

        count = read_u16be(blob, 0)
        dir_len = 2 + count * 10
        if dir_len > len(blob):
            raise ValueError(f"{path} inner directory overruns archive")

        entries: list[JagEntry] = []
        data_offset = dir_len
        for i in range(count):
            entry_offset = 2 + i * 10
            hash_value = read_u32be(blob, entry_offset)
            entry_unpacked_len = read_u24be(blob, entry_offset + 4)
            entry_packed_len = read_u24be(blob, entry_offset + 7)
            if data_offset + entry_packed_len > len(blob):
                raise ValueError(f"{path} entry {i} payload overruns archive")
            payload = bytes(blob[data_offset : data_offset + entry_packed_len])
            data_offset += entry_packed_len
            if entry_packed_len == entry_unpacked_len:
                data = payload
            else:
                data = _decompress_bzip_payload(payload, entry_unpacked_len)
            entries.append(
                JagEntry(
                    hash=hash_value,
                    unpacked_len=entry_unpacked_len,
                    packed_len=entry_packed_len,
                    packed_payload=payload,
                    data=data,
                )
            )
        return cls(entries=entries, outer_was_compressed=outer_was_compressed)

    def get(self, name: str) -> bytes | None:
        hash_value = jag_hash(name)
        for entry in self.entries:
            if entry.hash == hash_value:
                return entry.data
        return None

    def put(self, name: str, data: bytes, *, replace: bool = True) -> None:
        hash_value = jag_hash(name)
        new_entry = JagEntry.uncompressed(hash_value, data)
        for index, entry in enumerate(self.entries):
            if entry.hash == hash_value:
                if not replace:
                    raise ValueError(f"archive entry already exists: {name}")
                self.entries[index] = new_entry
                return
        self.entries.append(new_entry)

    def to_bytes(self, *, compress_outer: bool | None = None) -> bytes:
        if len(self.entries) > 0xFFFF:
            raise ValueError(f"too many JAG entries: {len(self.entries)}")
        directory = bytearray(write_u16be(len(self.entries)))
        payload = bytearray()
        for entry in self.entries:
            directory += write_u32be(entry.hash)
            directory += write_u24be(entry.unpacked_len)
            directory += write_u24be(entry.packed_len)
            payload += entry.packed_payload
        inner = bytes(directory + payload)
        if len(inner) > 0xFFFFFF:
            raise ValueError(f"JAG archive too large for u24 length: {len(inner)}")

        if compress_outer is None:
            compress_outer = self.outer_was_compressed
        if compress_outer:
            packed = bz2.compress(inner, compresslevel=1)
            if packed.startswith(b"BZh"):
                packed = packed[4:]
            return write_u24be(len(inner)) + write_u24be(len(packed)) + packed
        return write_u24be(len(inner)) + write_u24be(len(inner)) + inner

    def write(self, path: Path, *, compress_outer: bool | None = None) -> None:
        path.write_bytes(self.to_bytes(compress_outer=compress_outer))
