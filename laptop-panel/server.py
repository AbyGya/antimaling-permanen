#!/usr/bin/env python3
"""Panel Kontrol AntiMaling — server lokal + buka browser otomatis. Stdlib only."""
import http.server
import functools
import webbrowser
import os
import threading

PORT = 8765
HERE = os.path.dirname(os.path.abspath(__file__))

def main():
    h = functools.partial(http.server.SimpleHTTPRequestHandler, directory=HERE)
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), h)
    url = f"http://127.0.0.1:{PORT}/"
    print(f"[*] Panel AntiMaling: {url}")
    print("[*] Ctrl+C untuk berhenti.")
    threading.Timer(1.0, lambda: webbrowser.open(url)).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\n[*] Berhenti.")

if __name__ == "__main__":
    main()
