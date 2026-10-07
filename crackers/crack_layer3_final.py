#!/usr/bin/env python3
"""ClydeProtection V2 cracker for Layer 3."""
import struct
import re

with open('RoMidi_layer3_raw.txt', 'r') as f:
    data = f.read()

code = data[data.find(']]')+2:].strip()

def eval_arith(expr):
    expr = expr.strip().strip('()')
    try:
        return int(eval(expr))
    except:
        return None

def extract_table(name):
    m = re.search(rf'{re.escape(name)}=\{{([^}}]+)\}}', code)
    if not m:
        return []
    return [eval_arith(p) for p in m.group(1).split(',')]

# Extract tables
_0p = extract_table('_0d') + extract_table('_3u') + extract_table('_3w') + extract_table('_4p') + extract_table('_5a')
_4b = extract_table('_5x') + extract_table('_0w') + extract_table('_3r')
_4h = len(_0p)

# Key table: XOR with 47
_2b_key = [(_0p[i] ^ 47) for i in range(len(_0p))]
# Substitution table: XOR with 248
_0m = [(_4b[i] ^ 248) for i in range(256)]

print(f"[*] Key table: {_4h} entries, Sub table: {len(_0m)} entries")

def check_3e():
    result = 0
    for v in _2b_key:
        result ^= v
    return result == 205

def check_5k():
    return sum(_0m) % 65536 == 32640

def fix_5b():
    for i in range(256):
        _0m[i] ^= 0xAA
    for i in range(_4h):
        _2b_key[i] = (_2b_key[i] + i + 1) & 0xFF

def base85_decode(s):
    s = re.sub(r'\s', '', s)
    s = s.replace('z', '!!!!!')
    output = bytearray()
    for i in range(0, len(s), 5):
        chunk = s[i:i+5]
        if len(chunk) < 5:
            chunk = chunk + 'u' * (5 - len(chunk))
        d, e, f, g, h = [ord(c) for c in chunk]
        v = (d-33)*52200625 + (e-33)*614125 + (f-33)*7225 + (g-33)*85 + (h-33)
        v = v & 0xFFFFFFFF
        output.extend(struct.pack('>I', v))
    return bytes(output[:20555])

def adler32(data):
    a, b = 1, 0
    for i in range(len(data)):
        a = (a + data[i]) % 65521
        b = (b + a) % 65521
    return b * 65536 + a

def decrypt(data):
    n = len(data)
    output = []
    prev = 0
    for i in range(n):
        enc = data[i]
        sub = (enc ^ _2b_key[i % _4h]) ^ prev
        output.append(_0m[sub & 0xFF])
        prev = enc
    return bytes(output)

def transform_3a(data):
    output = bytearray()
    for i in range(len(data)):
        output.append(data[i] ^ ((i + 1 + 146) & 0xFF))
    return bytes(output)

# Extract base85 payload
m = re.search(r'\[=\[(.*?)\]=\]', code, re.DOTALL)
b85_payload = m.group(1)

# State machine:
# 9327: _1w = base85_decode(payload) -> 3370
# 3370: integrity check + _2k = decrypt(_1w) -> 7126
# 7126: if adler32(_2k) != 3899100478 then fix + re-decrypt; -> 7723
# 7723: if 2*_0g+_0c == 192 then loadstring(_2k) else loadstring(transform_3a(_2k)); -> 1338

print("[*] Step 1: Base85 decode")
_1w = base85_decode(b85_payload)
print(f"    {len(_1w)} bytes")

print("[*] Step 2: Integrity check + decrypt")
if not check_3e() or not check_5k():
    print("    Fixing tables...")
    fix_5b()

_2k = decrypt(_1w)
checksum = adler32(_2k)
print(f"    Adler-32: {checksum} (expected: 3899100478)")

attempts = 0
while checksum != 3899100478 and attempts < 30:
    fix_5b()
    _2k = decrypt(_1w)
    checksum = adler32(_2k)
    attempts += 1

if checksum == 3899100478:
    print(f"    Checksum matched after {attempts} fix passes!")
else:
    print(f"    FAILED after {attempts} attempts, checksum: {checksum}")

# Step 3: Check loadstring path
# _0g = band(105, 87) = 65
# _0c = bxor(105, 87) = 62
# Wait - those values are from layer 1. Let me extract from THIS code.
_0g_match = re.search(r'_0g=_0q\((\d+),(\d+)\)', code)
_0c_match = re.search(r'_0c=_2d\((\d+),(\d+)\)', code)
if _0g_match:
    _0g = int(_0g_match.group(1)) & int(_0g_match.group(2))
    print(f"    _0g = band({_0g_match.group(1)}, {_0g_match.group(2)}) = {_0g}")
if _0c_match:
    _0c = int(_0c_match.group(1)) ^ int(_0c_match.group(2))
    print(f"    _0c = bxor({_0c_match.group(1)}, {_0c_match.group(2)}) = {_0c}")

if 2*_0g + _0c == 192:
    print("[+] Direct loadstring path")
    payload = _2k
else:
    print("[+] Extra _3a transform needed")
    payload = transform_3a(_2k)

# Save
with open('RoMidi_layer3_decoded.lua', 'wb') as f:
    f.write(payload)
print(f"[+] Saved to RoMidi_layer3_decoded.lua ({len(payload)} bytes)")

try:
    preview = payload[:500].decode('utf-8', errors='replace')
except:
    preview = payload[:500].decode('latin-1')
print(f"\n[+] Preview:\n{preview}")
