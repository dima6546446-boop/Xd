#!/usr/bin/env python3
"""ClydeProtection V2 deobfuscator for RoMidi script."""
import struct
import re

# --- Numeric table definitions (evaluated from Lua arithmetic) ---

_5x = [20+27, 127+46, 687-574, 7*32+0-31, 214-200, 358-115, 2*9+0, 9*32-88, 543-350]

_5q = [
    4*53-38, 9*6+4-42, 531-339, 8*41-82, 416-412, 365-200, 10*23+9-42,
    7*35+5, 534-446, 6*19-62, 347-232, 338-200, 25+5, 2*22-1, 5*19+3-36,
    2*81+0, 432-276, 402-200, 281-230, 157+12, 412+24-391, 5*58-75,
    201-101, 11*0+1, 3+7, 9*32-79, 351-285, 430-308, 740-523, 236-195,
    214-149, 242-148, 22+7, 568-345, 8*20+3, 167+182-220, 307-200,
    607-366, 91+97, 45+3, 325-219, 329-249, 336-117, 11*12+4-39,
    282-207, 4*15-25, 36+22, 92+370-235, 11+121, 35+9, 7*15+4-45,
    3*40-57, 266-170, 288-131, 240-121, 215-200, 8*13+4-41, 11*9+0,
    94+351-296, 5*7+1-14, 7*39-26, 21+121, 18+224, 7*8+3-40, 1+12,
    227-224, 696-462, 7*23+3-17, 372-137, 512+8-497, 266-58, 94-17,
    162-41, 4*50-15, 243+360-370, 610-434, 479-375, 6*12+1-41,
    399-255, 379-245, 676-489, 3*16+1, 3*71+0, 5*15+2-37, 83+343-299,
    2*36+1, 3*73+2, 11+50, 2+91, 3*61+2-17, 767-560, 215-178, 5+0,
    95+397-306
]

_4n = [
    96+361-319, 6*38+1, 3*45+2-46, 10*21+4-42, 12+27, 0+6, 12*15+8,
    204-200, 24+148, 11*10+8, 98-63, 342-137, 235-179
]

_0a = [
    6*29-8, 8*21+7, 5+52, 7*36+1, 7*27+5, 354-200, 4*50-16, 2*109+0,
    328+359-458, 394-214, 7*19+5-3, 10*8+1-31, 184+28, 255-200,
    324-200, 6*38+4-7, 6*19+4, 10*15+0-48, 8*27+2-18, 11*4+2-15,
    4*48-76, 5*23+2, 62+8, 4*35+0-37, 6*25-82, 765-513, 48+35,
    81+428-428, 779-589, 510+290-549, 433-228, 9*14+2, 547-505,
    9*36-121, 3+142, 579-525, 11*1+6-5, 70+6, 305-133, 6*23+3,
    8*13+4-22, 105+84, 574-465, 11*22+10-41, 4*18-25, 9*33-151,
    296-222, 84+30, 461-251, 9*23-64, 8*33-141, 372-141, 175+221-370,
    6*44-48, 489-450, 7+2, 8*40-122, 8*22+7, 149+228-181, 94+130,
    63+89, 7*14+4-1, 11*12+8, 242+120-202, 4*5+0, 233-177, 10*9+1-12,
    11*15+2, 12*8+2, 4*8+1, 213+30, 215+33, 613-381, 10*22+6,
    9*13+8, 9+15, 522-516, 675-430, 697-457, 440-269, 3*24+0,
    12*12+9, 454-261, 8*29+5, 361-270, 43+93, 558-322, 2+0, 3*23-41,
    88+25, 8*16-103, 12*14+10, 4*57+0, 8+19, 175+69, 192+59-140,
    52+17, 267+482-500, 289-253, 667-589, 9*37-142, 272-34, 683-429,
    142+39, 6*38+4-18
]

_0k = [262-198, 111+14, 12*15+0, 6*30+1]

_0g = [
    0+59, 357-151, 129-95, 376+312-540, 259+117-246, 64+18, 225+14,
    10*3+8, 4*45-22, 6+2, 426-271, 3*54+0-25, 504-327, 329-237,
    392-307, 6*50+1-46, 5*38-26, 210-102, 9*17+8, 722-540, 6*37-49,
    368-242, 12*21+9-39, 278-218, 10*8+4, 265-115, 70+1, 8*19-19,
    92+128, 4*38-32, 351-264, 6*57-112, 3*15+1, 16+94, 2*80-1,
    10*5+3, 173+225-286, 195-105, 5*39-56, 377-173, 483+19-485,
    77-66, 110-92, 7*3+0, 654-523, 289-289, 3*20+0-46, 284+330-519,
    356-251, 12*19+3-36, 616-417, 404-203, 7*12+5, 2+5, 422+149-420,
    619-449, 373-194
]

# --- Build combined tables (Lua 1-indexed, Python 0-indexed) ---

_5w = _0k + _5x + _4n  # combined key table
_3c_combined = _0a + _5q + _0g  # combined substitution source

_3d = len(_5w)

# Build _3g (substitution table, 256 entries)
# Lua: for _0i=1,256 do _3g[_0i]=_2t(_3c[_0i],(8*14+5-7)) end
# (8*14+5-7) = 110
_3g = [(_3c_combined[i] ^ 110) for i in range(256)]

# Build _1r (key table)
# Lua: for _0i=1,_3d do _1r[_0i]=_2t(_5w[_0i],(258-251)) end
# (258-251) = 7
_1r = [(_5w[i] ^ 7) for i in range(_3d)]

# --- Integrity checks ---

def _3t():
    """XOR all _1r values, check == 229"""
    result = 0
    for v in _1r:
        result ^= v
    return result == 229

def _5b():
    """Sum all _3g values, check sum % 65536 == 32640"""
    s = sum(_3g)
    return s % 65536 == 32640

def _5j():
    """Modify key tables"""
    global _3g, _1r
    for i in range(256):
        _3g[i] ^= 0xAA
    for i in range(_3d):
        _1r[i] = (_1r[i] + i + 1) & 0xFF  # Lua is 1-indexed, so +_0i where _0i starts at 1

# --- Adler-32 checksum ---

def _4s(data):
    a, b = 1, 0
    for byte in data:
        if isinstance(byte, int):
            a = (a + byte) % 65521
            b = (b + a) % 65521
        else:
            a = (a + ord(byte)) % 65521
            b = (b + a) % 65521
    return b * 65536 + a

# --- Base85 decoder (_3w) ---

def _3w(s):
    # Strip whitespace
    s = re.sub(r'\s', '', s)
    # Replace 'z' with '!!!!!' (chr(33) repeated 5 times)
    s = s.replace('z', '!!!!!')

    output = bytearray()
    i = 0
    while i < len(s):
        chunk = s[i:i+5]
        if len(chunk) < 5:
            # Pad with 'u' (ASCII 117, which is 84 = 'u'-33)
            chunk = chunk + 'u' * (5 - len(chunk))

        d, e, f, g, h = [ord(c) for c in chunk]
        v = (d-33)*52200625 + (e-33)*614125 + (f-33)*7225 + (g-33)*85 + (h-33)

        # Clamp to uint32
        v = v & 0xFFFFFFFF
        output.extend(struct.pack('>I', v))
        i += 5

    # Truncate to 20719 bytes
    return bytes(output[:20719])

# --- Decrypt function _2b ---

def _2b(data):
    n = len(data)
    output = []
    prev = 0
    for i in range(n):
        enc = data[i] if isinstance(data[i], int) else ord(data[i])
        # Lua: _2t(_2t(enc, _1r[((i-1)%_3d)+1]), prev)
        # In Python (0-indexed): _1r[i % _3d]
        sub = (enc ^ _1r[i % _3d]) ^ prev
        # Lua: _3k(_3g[sub+1]) — sub is 0-255, _3g is 1-indexed in Lua
        output.append(_3g[sub & 0xFF])
        prev = enc
    return bytes(output)

# --- Main deobfuscation ---

def main():
    # Read the base85 payload
    with open('base85_payload.txt', 'r') as f:
        b85_data = f.read()

    print(f"[*] Base85 payload: {len(b85_data)} chars")

    # Step 1: Decode base85
    _5c = _3w(b85_data)
    print(f"[*] Base85 decoded: {len(_5c)} bytes")

    # Step 2: Check integrity, fix if needed
    print(f"[*] _3t() check: {_3t()}")
    print(f"[*] _5b() check: {_5b()}")

    if not _3t() or not _5b():
        print("[*] Integrity check failed, running _5j() to fix tables...")
        _5j()
        print(f"[*] After fix - _3t(): {_3t()}, _5b(): {_5b()}")

    # Step 3: Decrypt
    _2x = _2b(_5c)
    print(f"[*] Decrypted: {len(_2x)} bytes")

    # Step 4: Check Adler-32
    checksum = _4s(_2x)
    print(f"[*] Adler-32: {checksum} (expected: 2847059320)")

    # If checksum fails, try _5j() again and re-decrypt
    attempts = 0
    while checksum != 2847059320 and attempts < 20:
        print(f"[*] Checksum mismatch, running _5j() pass {attempts+1}...")
        _5j()
        _2x = _2b(_5c)
        checksum = _4s(_2x)
        print(f"[*] Adler-32: {checksum}")
        attempts += 1

    if checksum == 2847059320:
        print(f"[+] Checksum matched after {attempts} _5j() passes!")

        # Step 5: Check 2*_0j+_0m==224
        _0j = 180 & 44  # bit32.band(180, 44) = 36
        _0m = 180 ^ 44  # bit32.bxor(180, 44) = 152

        if 2*_0j + _0m == 224:
            print("[+] Direct loadstring path (no extra _5r transform)")
            payload = _2x
        else:
            print("[+] Extra _5r transform needed")
            payload = _5r_func(_2x)

        # Save the deobfuscated payload
        with open('RoMidi_deobfuscated.lua', 'wb') as f:
            f.write(payload)
        print(f"[+] Deobfuscated payload saved to RoMidi_deobfuscated.lua ({len(payload)} bytes)")

        # Print first 500 chars
        try:
            preview = payload[:500].decode('utf-8', errors='replace')
        except:
            preview = payload[:500].decode('latin-1')
        print(f"\n[+] Preview:\n{preview}")
    else:
        print(f"[-] Failed to match checksum after {attempts} attempts")
        print(f"[-] Last checksum: {checksum}")

        # Save anyway for manual inspection
        with open('RoMidi_attempt.lua', 'wb') as f:
            f.write(_2x)
        print(f"[-] Saved attempt to RoMidi_attempt.lua for inspection")

def _5r_func(data):
    """XOR with rolling key starting at 40, step 179"""
    output = []
    key = 40
    for i in range(len(data)):
        b = data[i] if isinstance(data[i], int) else ord(data[i])
        output.append((b ^ (key & 0xFF)))
        key += 179
    return bytes(output)

if __name__ == '__main__':
    main()
