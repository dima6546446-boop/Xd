#!/usr/bin/env python3
"""Decrypt W constants from Layer 3 with correct parameters."""
import re

with open('RoMidi_layer3_decoded.lua', 'rb') as f:
    raw = f.read()

text = raw.decode('latin-1')

# Resolve bit32 expressions
prev = None
while prev != text:
    prev = text
    text = re.sub(r'bit32\.bxor\(bit32\.bxor\((\d+),(\d+)\),(\d+)\)', lambda m: str((int(m.group(1)) ^ int(m.group(2))) ^ int(m.group(3))), text)
    text = re.sub(r'bit32\.bxor\((\d+),(\d+)\)', lambda m: str(int(m.group(1)) ^ int(m.group(2))), text)
    text = re.sub(r'bit32\.band\((\d+),(\d+)\)', lambda m: str(int(m.group(1)) & int(m.group(2))), text)

# Extract inverse S-box from _U function
inv_match = re.search(r'_0inv=\{([^}]+)\}', text)
inv_sbox = [int(x.strip()) for x in inv_match.group(1).split(',')]
assert len(inv_sbox) == 256

# Build _Y lookup table: _Y[k] = (k*127+101)%251, k=0..511
_Y = [(k * 127 + 101) % 251 for k in range(512)]

# Find W table in raw bytes
w_idx = raw.find(b'W={')
pos = w_idx + 3
depth = 1
in_string = False
escape_next = False
i = pos
while i < len(raw):
    b = raw[i]
    if in_string:
        if escape_next:
            escape_next = False
            i += 1
            continue
        if b == 0x5C: escape_next = True
        elif b == 0x22: in_string = False
    else:
        if b == 0x22: in_string = True
        elif b == 0x7B: depth += 1
        elif b == 0x7D:
            depth -= 1
            if depth == 0: break
    i += 1

w_content = raw[pos:i]

# Parse W entries
def parse_lua_string_bytes(raw_bytes):
    result = []
    i = 0
    while i < len(raw_bytes):
        b = raw_bytes[i]
        if b == 0x5C and i + 1 < len(raw_bytes):
            nb = raw_bytes[i + 1]
            if 0x30 <= nb <= 0x39:
                num_str = chr(nb)
                j = i + 2
                while j < len(raw_bytes) and 0x30 <= raw_bytes[j] <= 0x39 and len(num_str) < 3:
                    num_str += chr(raw_bytes[j])
                    j += 1
                result.append(int(num_str))
                i = j
            elif nb == ord('n'): result.append(10); i += 2
            elif nb == ord('r'): result.append(13); i += 2
            elif nb == ord('t'): result.append(9); i += 2
            elif nb == ord('a'): result.append(7); i += 2
            elif nb == ord('b'): result.append(8); i += 2
            elif nb == ord('f'): result.append(12); i += 2
            elif nb == ord('v'): result.append(11); i += 2
            elif nb == 0x5C: result.append(92); i += 2
            elif nb == 0x22: result.append(34); i += 2
            elif nb == 0x27: result.append(39); i += 2
            else: result.append(nb); i += 2
        else:
            result.append(b)
            i += 1
    return result

entries = []
i = 0
while i < len(w_content):
    b = w_content[i]
    if b in (0x20, 0x2C, 0x0A, 0x0D, 0x09):
        i += 1; continue
    elif b == 0x22:
        j = i + 1
        esc = False
        while j < len(w_content):
            if esc: esc = False; j += 1; continue
            if w_content[j] == 0x5C: esc = True; j += 1; continue
            if w_content[j] == 0x22: break
            j += 1
        entries.append(parse_lua_string_bytes(w_content[i+1:j]))
        i = j + 1
    elif b == 0x7B:
        j = i + 1
        d = 1
        while j < len(w_content) and d > 0:
            if w_content[j] == 0x7B: d += 1
            elif w_content[j] == 0x7D: d -= 1
            j += 1
        arr_text = w_content[i+1:j-1].decode('latin-1')
        nums = [int(x.strip()) for x in arr_text.split(',') if x.strip()]
        entries.append(nums)
        i = j
    else:
        i += 1

print(f"[*] Parsed {len(entries)} W entries:")
for idx, e in enumerate(entries):
    print(f"    W[{idx+1}]: {len(e)} bytes")

# === Decryption pipeline: _P → _W(_Q → _R → _S → _T → _U → _V) ===
# _P converts strings to byte arrays (already done by parser)

# _Q: XOR with (_Y[(j-1)%512] + s) % 251
def decrypt_Q(W):
    for i in range(len(W)):
        s = i & 0xFF
        for j in range(len(W[i])):
            W[i][j] ^= (_Y[j % 512] + s) % 251

# _R: XOR with alternating keys
# Even (j=1,3,5... in Lua = j=0,2,4... in Python): band(30+s+floor((j-1)/2)*142, 0xFF)
# Odd (j=2,4,6... in Lua = j=1,3,5... in Python): band(58+s+floor((j-1)/2)*22, 0xFF)
def decrypt_R(W):
    for i in range(len(W)):
        s = i & 0xFF
        n = len(W[i])
        for j in range(n):
            lua_j = j + 1  # convert to 1-indexed
            h = (lua_j - 1) // 2
            if lua_j % 2 == 1:  # odd Lua index (even Python index)
                k = (30 + s + h * 142) & 0xFF
            else:  # even Lua index (odd Python index)
                k = (58 + s + h * 22) & 0xFF
            W[i][j] ^= k

# _S: Chain XOR: prev*123+124+s
def decrypt_S(W):
    for i in range(len(W)):
        if len(W[i]) < 2: continue
        s = i & 0xFF
        for j in range(1, len(W[i])):
            k = (W[i][j-1] * 123 + 124 + s) & 0xFF
            W[i][j] ^= k

# _T: Subtract band(91+s+(k-1)*235, 0xFF)
def decrypt_T(W):
    for i in range(len(W)):
        s = i & 0xFF
        for j in range(len(W[i])):
            z = (91 + s + j * 235) & 0xFF
            W[i][j] = (W[i][j] - z + 256) & 0xFF

# _U: Inverse S-box + XOR with (s+j) & 0xFF
def decrypt_U(W):
    for i in range(len(W)):
        s = i & 0xFF
        for j in range(len(W[i])):
            W[i][j] = inv_sbox[W[i][j]] ^ ((s + j) & 0xFF)

print("\n[*] Decrypting W constants...")
decrypt_Q(entries); print("    _Q done")
decrypt_R(entries); print("    _R done")
decrypt_S(entries); print("    _S done")
decrypt_T(entries); print("    _T done")
decrypt_U(entries); print("    _U done")

print(f"\n{'='*60}")
print("[+] DECRYPTED W CONSTANTS:")
print(f"{'='*60}")

for idx, arr in enumerate(entries):
    s = ''.join(chr(b) if 32 <= b < 127 else f'\\x{b:02x}' for b in arr)
    print(f"    W[{idx+1}] ({len(arr):3d} bytes) = \"{s}\"")
    try:
        utf = bytes(arr).decode('utf-8')
        if all(32 <= ord(c) < 127 or c in '\n\r\t' for c in utf):
            if utf != s:
                print(f"             UTF-8: \"{utf}\"")
    except:
        pass

with open('W_layer3_decoded.txt', 'w') as f:
    for idx, arr in enumerate(entries):
        try:
            s = bytes(arr).decode('utf-8')
        except:
            s = ''.join(chr(b) if 32 <= b < 127 else f'\\x{b:02x}' for b in arr)
        f.write(f"W[{idx+1}] = \"{s}\"\n")
print(f"\n[+] Saved to W_layer3_decoded.txt")
