"""
Process entrypoint for the frozen tolerance-engine executable.

Responsible only for:
  1. binding a loopback TCP socket on an OS-assigned free port,
  2. emitting the single READY handshake line the Kotlin side waits for,
  3. handing off to server.serve_forever().

Nothing else may be printed to stdout before the handshake line, and after it
stdout/stderr are free-form (library warnings, debug prints, etc.) — the
protocol lives entirely on the socket, never on stdio. See docs/ARCHITECTURE.md.
"""
import json
import socket
import sys

from tolerance_engine.server import serve_forever

HOST = "127.0.0.1"
READY_MARKER = "TOLERANCE_ENGINE_READY"


def main() -> None:
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((HOST, 0))  # port 0 => OS picks a free port
    server_socket.listen(1)        # single-client desktop app; one connection at a time

    _, port = server_socket.getsockname()

    handshake = {"port": port, "pid": None}
    try:
        import os
        handshake["pid"] = os.getpid()
    except Exception:
        pass

    print(f"{READY_MARKER} {json.dumps(handshake)}", flush=True)

    try:
        serve_forever(server_socket)
    except KeyboardInterrupt:
        pass
    finally:
        server_socket.close()


if __name__ == "__main__":
    sys.exit(main() or 0)
