#!/usr/bin/env python3
"""Request a saved, orderly shutdown of the isolated CI server through loopback RCON."""
import os
from pathlib import Path
import socket
import struct
import sys

if os.environ.get("GITHUB_ACTIONS") != "true":
    raise SystemExit("Automated game scenarios are restricted to GitHub Actions.")

properties = dict(
    line.split("=", 1)
    for line in Path(sys.argv[1]).read_text().splitlines()
    if "=" in line and not line.startswith("#")
)


def read_exact(connection: socket.socket, length: int) -> bytes:
    result = bytearray()
    while len(result) < length:
        chunk = connection.recv(length - len(result))
        if not chunk:
            raise ConnectionError("The RCON server closed the connection before replying.")
        result.extend(chunk)
    return bytes(result)


def receive(connection: socket.socket) -> tuple[int, int]:
    length = struct.unpack("<i", read_exact(connection, 4))[0]
    if not 10 <= length <= 4 * 1024 * 1024:
        raise ValueError("Invalid RCON response length.")
    packet = read_exact(connection, length)
    return struct.unpack("<ii", packet[:8])


def send(connection: socket.socket, request: int, kind: int, payload: str) -> None:
    packet = struct.pack("<ii", request, kind) + payload.encode() + b"\0\0"
    connection.sendall(struct.pack("<i", len(packet)) + packet)


with socket.create_connection(("127.0.0.1", int(properties["rcon.port"])), timeout=15) as connection:
    send(connection, 1, 3, properties["rcon.password"])
    for _ in range(4):
        request, kind = receive(connection)
        if request == -1:
            raise PermissionError("The CI server rejected RCON authentication.")
        if (request, kind) == (1, 2):
            break
    else:
        raise RuntimeError("The CI server did not acknowledge RCON authentication.")
    send(connection, 2, 2, "stop")
    request, kind = receive(connection)
    if (request, kind) != (2, 0):
        raise RuntimeError("The CI server did not acknowledge the stop command.")

print("FABRIC_SERVER_GRACEFUL_STOP_REQUESTED")
