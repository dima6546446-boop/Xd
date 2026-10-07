#!/usr/bin/env python3
"""W constants cracker v4 — byte-level parsing, no latin-1 text tricks."""
import re

with open('RoMidi_deobfuscated.lua', 'rb') as f:
    raw = f.read()

# Extract inverse S-box
text = raw.decode('latin-1')
inv_match = re.search(r'_0inv=\{([^}]+)\}', text)
inv_sbox = [int(x.strip()) for x in inv_match.group(1).split(',')]

# Find W={...} boundaries with string-aware brace matching
idx = raw.find(b'W={')
pos = idx + 3  # right after the opening {
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
        if b == 0x5C:  # backslash
            escape_next = True
        elif b == 0x22:  # closing quote
            in_string = False
    else:
        if b == 0x22:  # opening quote
            in_string = True
        elif b == 0x7B:  # {
            depth += 1
        elif b == 0x7D:  # }
            depth -= 1
            if depth == 0:
                break
    i += 1

w_content = raw[pos:i]  # bytes between outer { and }
print(f"[*] W content: {len(w_content)} bytes")

# Parse W entries from raw bytes
# Entries are separated by commas, can be:
# 1. "..." — Lua string with \DDD escapes
# 2. {n,n,n,...} — byte array

def parse_lua_string_bytes(raw_bytes):
    """Parse bytes of a Lua string literal (content between quotes) into byte values."""
    result = []
    i = 0
    while i < len(raw_bytes):
        b = raw_bytes[i]
        if b == 0x5C and i + 1 < len(raw_bytes):  # backslash
            nb = raw_bytes[i + 1]
            if 0x30 <= nb <= 0x39:  # digit
                # \DDD numeric escape (1-3 digits)
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
            elif nb == 0x5C: result.append(92); i += 2  # \\
            elif nb == 0x22: result.append(34); i += 2  # \"
            elif nb == 0x27: result.append(39); i += 2  # \'
            else: result.append(nb); i += 2
        else:
            result.append(b)
            i += 1
    return result

entries = []
i = 0
while i < len(w_content):
    b = w_content[i]
    if b in (0x20, 0x2C, 0x0A, 0x0D, 0x09):  # whitespace/comma
        i += 1
        continue
    elif b == 0x22:  # quote — string entry
        # Find matching close quote, respecting escapes
        j = i + 1
        escape = False
        while j < len(w_content):
            if escape:
                escape = False
                j += 1
                continue
            if w_content[j] == 0x5C:
                escape = True
                j += 1
                continue
            if w_content[j] == 0x22:
                break
            j += 1
        # w_content[i+1:j] is the string content bytes
        str_bytes = w_content[i+1:j]
        parsed = parse_lua_string_bytes(str_bytes)
        entries.append(parsed)
        i = j + 1
    elif b == 0x7B:  # { — byte array
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
for idx2, e in enumerate(entries):
    print(f"  W[{idx2+1}]: {len(e)} bytes = {e[:20]}{'...' if len(e)>20 else ''}")

# === Decryption pipeline ===

def decrypt_K(W):
    for i in range(len(W)):
        s = i & 0xFF
        for j in range(len(W[i])):
            W[i][j] ^= ((j * 17 + 215 + s) % 251)

def decrypt_L(W):
    for i in range(len(W)):
        s = i & 0xFF
        for j in range(len(W[i])):
            h = j // 2
            k = (6 + s + h * 242) & 0xFF if j % 2 == 0 else (142 + s + h * 78) & 0xFF
            W[i][j] ^= k

def decrypt_M(W):
    for i in range(len(W)):
        if len(W[i]) < 2: continue
        s = i & 0xFF
        prev = W[i][0]
        for j in range(1, len(W[i])):
            k = (prev * 123 + 25 + s) & 0xFF
            W[i][j] ^= k
            prev = W[i][j]

def decrypt_N(W):
    for i in range(len(W)):
        s = i & 0xFF
        a = 27 + s
        for j in range(len(W[i])):
            W[i][j] = (W[i][j] - (a & 0xFF) + 256) & 0xFF
            a += 149

def decrypt_O(W):
    for i in range(len(W)):
        s = i & 0xFF
        for j in range(len(W[i])):
            W[i][j] = inv_sbox[W[i][j]] ^ ((s + j) & 0xFF)

print("\n[*] Decrypting...")
decrypt_K(entries)
decrypt_L(entries)
decrypt_M(entries)
decrypt_N(entries)
decrypt_O(entries)
print("  All layers done.")

print(f"\n{'='*60}")
print("[+] DECRYPTED W CONSTANTS:")
print(f"{'='*60}")

for idx2, arr in enumerate(entries):
    s = ''.join(chr(b) if 32 <= b < 127 else f'\\x{b:02x}' for b in arr)
    print(f"  W[{idx2+1}] ({len(arr):3d} bytes) = \"{s}\"")
    try:
        utf = bytes(arr).decode('utf-8')
        if utf != s:
            print(f"         UTF-8: \"{utf}\"")
    except:
        pass

with open('W_decoded.txt', 'w') as f:
    for idx2, arr in enumerate(entries):
        try:
            s = bytes(arr).decode('utf-8')
        except:
            s = ''.join(chr(b) if 32 <= b < 127 else f'\\x{b:02x}' for b in arr)
        f.write(f"W[{idx2+1}] = \"{s}\"\n")
print(f"\n[+] Saved to W_decoded.txt")
