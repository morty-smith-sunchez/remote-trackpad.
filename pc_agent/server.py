from __future__ import annotations

import argparse
import asyncio
import json
import math
import os
import socket
from dataclasses import dataclass
from typing import Any

from aiohttp import web, WSMsgType
from pynput.keyboard import Controller as KeyboardController
from pynput.keyboard import Key
from pynput.mouse import Button, Controller

DISCOVERY_PORT = 8766
DISCOVERY_MAGIC = b"RTRACKPAD_DISCOVER"

HID_KEYS: dict[int, str] = {
    0x04: "a", 0x05: "b", 0x06: "c", 0x07: "d", 0x08: "e", 0x09: "f",
    0x0A: "g", 0x0B: "h", 0x0C: "i", 0x0D: "j", 0x0E: "k", 0x0F: "l",
    0x10: "m", 0x11: "n", 0x12: "o", 0x13: "p", 0x14: "q", 0x15: "r",
    0x16: "s", 0x17: "t", 0x18: "u", 0x19: "v", 0x1A: "w", 0x1B: "x",
    0x1C: "y", 0x1D: "z",
    0x1E: "1", 0x1F: "2", 0x20: "3", 0x21: "4", 0x22: "5", 0x23: "6",
    0x24: "7", 0x25: "8", 0x26: "9", 0x27: "0",
    0x28: "enter", 0x29: "esc", 0x2A: "backspace", 0x2B: "tab", 0x2C: "space",
    0x2D: "-", 0x2E: "=", 0x2F: "[", 0x30: "]", 0x31: "\\",
    0x4B: "page_up", 0x4E: "page_down",
    0x4F: "right", 0x50: "left", 0x51: "down", 0x52: "up",
}

ROOT_DIR = os.path.dirname(os.path.abspath(__file__))
WEB_DIR = os.path.join(os.path.dirname(ROOT_DIR), "mobile_web")

mouse = Controller()
keyboard = KeyboardController()


@dataclass
class Settings:
    sensitivity: float = 1.25
    scroll_sensitivity: float = 0.38


SETTINGS = Settings()
_move_remainder_x = 0.0
_move_remainder_y = 0.0


def clamp(v: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, v))


def safe_int(x: Any, default: int = 0) -> int:
    try:
        return int(x)
    except Exception:
        return default


def safe_float(x: Any, default: float = 0.0) -> float:
    try:
        f = float(x)
        return f if math.isfinite(f) else default
    except Exception:
        return default


def list_local_ipv4() -> list[str]:
    ips: set[str] = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127."):
                ips.add(ip)
    except OSError:
        pass
    # Route hint (works when internet is available)
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ips.add(s.getsockname()[0])
        s.close()
    except OSError:
        pass
    return sorted(ips)


async def api_info(request: web.Request) -> web.Response:
    port = int(request.app["ws_port"])
    return web.json_response(
        {
            "service": "remote_trackpad",
            "port": port,
            "ips": list_local_ipv4(),
        }
    )


async def index(request: web.Request) -> web.Response:
    return web.FileResponse(os.path.join(WEB_DIR, "index.html"))


async def static_files(request: web.Request) -> web.Response:
    name = request.match_info.get("name")
    path = os.path.join(WEB_DIR, name)
    if not os.path.isfile(path):
        raise web.HTTPNotFound()
    return web.FileResponse(path)


async def ws_handler(request: web.Request) -> web.WebSocketResponse:
    ws = web.WebSocketResponse(heartbeat=15)
    await ws.prepare(request)

    async for msg in ws:
        if msg.type != WSMsgType.TEXT:
            continue
        try:
            data = json.loads(msg.data)
        except Exception:
            continue
        if not isinstance(data, dict):
            continue

        t = data.get("type")
        if t == "move":
            global _move_remainder_x, _move_remainder_y
            dx = safe_float(data.get("dx"))
            dy = safe_float(data.get("dy"))
            _move_remainder_x += dx * SETTINGS.sensitivity
            _move_remainder_y += dy * SETTINGS.sensitivity
            ix = int(_move_remainder_x)
            iy = int(_move_remainder_y)
            if ix or iy:
                x, y = mouse.position
                mouse.position = (int(x) + ix, int(y) + iy)
                _move_remainder_x -= ix
                _move_remainder_y -= iy
        elif t == "scroll":
            dx = safe_float(data.get("dx"))
            dy = safe_float(data.get("dy"))
            if dx:
                amount_x = int(clamp(dx * SETTINGS.scroll_sensitivity, -200, 200))
                if amount_x:
                    mouse.scroll(amount_x, 0)
            if dy:
                amount_y = int(clamp(dy * SETTINGS.scroll_sensitivity, -200, 200))
                if amount_y:
                    mouse.scroll(0, -amount_y)
        elif t == "click":
            btn = str(data.get("button") or "left")
            mouse.click(_mouse_button(btn), 1)
        elif t == "down":
            btn = str(data.get("button") or "left")
            mouse.press(_mouse_button(btn))
        elif t == "up":
            btn = str(data.get("button") or "left")
            mouse.release(_mouse_button(btn))
        elif t == "key":
            _tap_hid_key(
                safe_int(data.get("modifiers"), 0),
                safe_int(data.get("key"), 0),
            )
        elif t == "hotkey":
            _tap_hid_key(
                safe_int(data.get("modifiers"), 0),
                safe_int(data.get("key"), 0),
            )
        elif t == "text":
            text = str(data.get("text") or "")
            if text:
                keyboard.type(text)
        elif t == "set":
            SETTINGS.sensitivity = clamp(safe_float(data.get("sensitivity"), SETTINGS.sensitivity), 0.2, 5.0)
            SETTINGS.scroll_sensitivity = clamp(
                safe_float(data.get("scroll_sensitivity"), SETTINGS.scroll_sensitivity), 0.2, 5.0
            )
        elif t == "ping":
            await ws.send_json({"type": "pong"})

    return ws


def _mouse_button(name: str) -> Button:
    if name == "right":
        return Button.right
    if name == "middle":
        return Button.middle
    return Button.left


def _resolve_key(token: str):
    special = {
        "enter": Key.enter,
        "esc": Key.esc,
        "backspace": Key.backspace,
        "tab": Key.tab,
        "space": Key.space,
        "page_up": Key.page_up,
        "page_down": Key.page_down,
        "left": Key.left,
        "right": Key.right,
        "up": Key.up,
        "down": Key.down,
    }
    if token in special:
        return special[token]
    if len(token) == 1:
        return token
    return None


def _tap_hid_key(modifiers: int, key_code: int) -> None:
    mods: list = []
    if modifiers & 0x01:
        mods.append(Key.ctrl)
    if modifiers & 0x02:
        mods.append(Key.shift)
    if modifiers & 0x04:
        mods.append(Key.alt)
    if modifiers & 0x08:
        mods.append(Key.cmd)

    token = HID_KEYS.get(key_code)
    if not token:
        return
    key = _resolve_key(token)
    if key is None:
        return

    for m in mods:
        keyboard.press(m)
    keyboard.press(key)
    keyboard.release(key)
    for m in reversed(mods):
        keyboard.release(m)


def make_app(ws_port: int) -> web.Application:
    app = web.Application()
    app["ws_port"] = ws_port
    app.router.add_get("/api/info", api_info)
    app.router.add_get("/", index)
    app.router.add_get("/ws", ws_handler)
    app.router.add_get("/{name}", static_files)
    return app


class DiscoveryProtocol(asyncio.DatagramProtocol):
    def __init__(self, ws_port: int) -> None:
        self.ws_port = ws_port
        self.transport: asyncio.DatagramTransport | None = None

    def connection_made(self, transport: asyncio.BaseTransport) -> None:
        self.transport = transport  # type: ignore[assignment]

    def datagram_received(self, data: bytes, addr: tuple[str, int]) -> None:
        if data != DISCOVERY_MAGIC or self.transport is None:
            return
        payload = json.dumps(
            {
                "service": "remote_trackpad",
                "port": self.ws_port,
                "ips": list_local_ipv4(),
            }
        ).encode("utf-8")
        self.transport.sendto(payload, addr)


async def run(host: str, port: int) -> None:
    app = make_app(port)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, host=host, port=port)
    await site.start()

    loop = asyncio.get_running_loop()
    await loop.create_datagram_endpoint(
        lambda: DiscoveryProtocol(port),
        local_addr=("0.0.0.0", DISCOVERY_PORT),
    )

    ips = list_local_ipv4()
    print("Remote Trackpad — сервер запущен", flush=True)
    print(f"  WebSocket: порт {port}", flush=True)
    print(f"  Поиск с телефона: UDP {DISCOVERY_PORT}", flush=True)
    if ips:
        print("  IP этого ПК (для справки):", flush=True)
        for ip in ips:
            print(f"    http://{ip}:{port}", flush=True)
    else:
        print(f"  http://<IP_ПК>:{port}", flush=True)
    print("На телефоне: режим USB / Wi-Fi → «Найти ПК»", flush=True)
    while True:
        await asyncio.sleep(3600)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="0.0.0.0")
    p.add_argument("--port", type=int, default=8765)
    args = p.parse_args()
    asyncio.run(run(args.host, args.port))


if __name__ == "__main__":
    main()

