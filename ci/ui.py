#!/usr/bin/env python3
"""Pequeno ajudante de UI para o teste no emulador: toca em elementos pelo texto/descrição."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args, check=False):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check)


def dump():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/ui.xml")
        out = adb("shell", "cat", "/sdcard/ui.xml").stdout
        if "<hierarchy" in out:
            return ET.fromstring(out[out.index("<hierarchy"):])
        time.sleep(1)
    return None


def find(text, exact=False):
    root = dump()
    if root is None:
        return None
    for node in root.iter("node"):
        for attr in ("text", "content-desc"):
            value = node.get(attr) or ""
            if (exact and value == text) or (not exact and text.lower() in value.lower()):
                b = re.findall(r"\d+", node.get("bounds", ""))
                if len(b) == 4:
                    x1, y1, x2, y2 = map(int, b)
                    return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(text, timeout=20, exact=False):
    end = time.time() + timeout
    while time.time() < end:
        pos = find(text, exact)
        if pos:
            adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
            time.sleep(1.5)
            print(f"tap '{text}' em {pos}")
            return True
        time.sleep(1)
    print(f"NÃO ENCONTRADO: '{text}'")
    return False


def wait(text, timeout=30):
    end = time.time() + timeout
    while time.time() < end:
        if find(text):
            print(f"visível: '{text}'")
            return True
        time.sleep(1)
    print(f"NÃO APARECEU: '{text}'")
    return False


if __name__ == "__main__":
    cmd, arg = sys.argv[1], sys.argv[2]
    timeout = int(sys.argv[3]) if len(sys.argv) > 3 else 20
    if cmd == "text":
        # Imprime o primeiro texto da tela que casa com a expressão regular.
        root = dump()
        ok = False
        if root is not None:
            for node in root.iter("node"):
                value = node.get("text") or ""
                m = re.search(arg, value)
                if m:
                    print(m.group(0))
                    ok = True
                    break
    elif cmd == "tap":
        ok = tap(arg, timeout)
    elif cmd == "tapx":
        ok = tap(arg, timeout, exact=True)
    else:
        ok = wait(arg, timeout)
    sys.exit(0 if ok else 1)
