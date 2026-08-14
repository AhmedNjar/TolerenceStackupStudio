"""
Socket accept loop and message framing.

Wire format (both directions), matching the Kotlin EngineTransport:
    [4-byte big-endian length][UTF-8 JSON bytes]
"""
from __future__ import annotations

import json
import socket
import struct
import traceback

from tolerance_engine.protocol import dispatch

_LENGTH_PREFIX = struct.Struct(">I")


def _recv_exact(conn: socket.socket, n: int) -> bytes:
    buf = bytearray()
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("engine socket closed mid-message")
        buf.extend(chunk)
    return bytes(buf)


def read_message(conn: socket.socket) -> dict:
    (length,) = _LENGTH_PREFIX.unpack(_recv_exact(conn, 4))
    body = _recv_exact(conn, length)
    return json.loads(body.decode("utf-8"))


def write_message(conn: socket.socket, message: dict) -> None:
    body = json.dumps(message).encode("utf-8")
    conn.sendall(_LENGTH_PREFIX.pack(len(body)) + body)


def serve_forever(server_socket: socket.socket) -> None:
    while True:
        conn, _addr = server_socket.accept()
        try:
            _handle_connection(conn)
        finally:
            conn.close()


def _handle_connection(conn: socket.socket) -> None:
    while True:
        try:
            envelope = read_message(conn)
        except ConnectionError:
            return  # client (Kotlin app) disconnected — normal on app shutdown

        request_id = envelope.get("requestId")
        try:
            response = dispatch(envelope)
        except Exception as exc:  # noqa: BLE001 — must never crash the engine process
            response = {
                "requestId": request_id,
                "type": "ERROR",
                "success": False,
                "error": {"code": type(exc).__name__, "message": str(exc)},
                "payload": {"traceback": traceback.format_exc()},
            }
        write_message(conn, response)
