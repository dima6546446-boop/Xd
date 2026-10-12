#!/usr/bin/env python3
"""
Sakura Project Mod Menu Crack v1.0
Target: Sakura Project for Mortal Kombat Mobile
Files: libSakura.so (ARM64 ELF), classes8.dex (Android DEX)

Protection analysis & bypass toolkit.

=== ARCHITECTURE ===

The protection has 3 layers:

1. VERSION GATE (SakuraSecurityGate)
   - Calls versionCheckUrl (https://sakuraproject.ru.net/version-check.php)
   - Sets SakuraSecurityGate.allowed = true, stores versionTicket
   - isAllowed() checks: allowed && versionTicket.length() > 0 && Main.IsClientVersionAllowed()

2. KEY VALIDATION (KeyManager)
   - validateKeyOnline() -> validateKeyOnlineInternal()
   - POST to licenseApiUrl (https://sakuraproject.ru.net/api.php)
   - Body: {action: "ACTIVATE"/"REFRESH", key, security, versionTicket, licenseToken?}
   - Response verified via SakuraServerTrust.verifySignedEnvelope() (EC signature + nonce)
   - Response types: AUTH_SUCCESS, AUTH_FAILED, SECURITY_ERROR, UPDATE_REQUIRED
   - On AUTH_SUCCESS: validateLicenseToken() -> decodeAndVerifyLicensePayload()
     - License format: "header.payload" split by "."
     - Builds "LICENSE_V3|" + header, verifies signature
     - Checks: expiresAt, deviceKeyHash, tokenVersion

3. NATIVE ENFORCEMENT (libSakura.so)
   - Main.ApplySecurityState(ctx, versionTicket, licenseToken) - sets paid session
   - Main.IsPaidSessionValid() - gate for all mod features
   - Main.IsClientVersionAllowed() - version gate
   - Anti-tamper: frida, xposed, magisk, root, substrate detection

=== DECODED URLS ===
   Base:         https://sakuraproject.ru.net/remote/
   License API:  https://sakuraproject.ru.net/api.php
   Version:      https://sakuraproject.ru.net/version-check.php
   Trusted Host: sakuraproject.ru.net
   Telegram:     https://t.me/LunarEncruption

=== KEY FORMAT ===
   Regex: (?i)\bSakuraKeyGen-[A-Za-z0-9_-]+
   Max length: 128 chars

=== BYPASS METHODS ===

--- Method 1: Frida Hook (Runtime) ---
Hooks native functions in libSakura.so to always return true.
"""

FRIDA_SCRIPT = r"""
// Sakura Project Crack - Frida Script
// Usage: frida -U -f com.wb.goog.mkx -l sakura_crack.js

Java.perform(function() {
    // === LAYER 1: Version Gate Bypass ===
    var SecurityGate = Java.use("com.android.support.SakuraSecurityGate");

    // Force allowed = true
    SecurityGate.isAllowed.implementation = function() {
        return true;
    };

    // Return fake version ticket
    SecurityGate.versionTicket.implementation = function() {
        return "VERSION_TICKET_V3_CRACKED_BYPASS";
    };

    // Skip version check entirely
    SecurityGate.begin.implementation = function(activity, callback) {
        // Set allowed directly
        var field_allowed = Java.use("com.android.support.SakuraSecurityGate").class.getDeclaredField("allowed");
        field_allowed.setAccessible(true);
        field_allowed.setBoolean(null, true);

        var field_ticket = Java.use("com.android.support.SakuraSecurityGate").class.getDeclaredField("versionTicket");
        field_ticket.setAccessible(true);
        field_ticket.set(null, "VERSION_TICKET_V3_CRACKED");

        // Call the callback immediately
        if (callback != null) {
            callback.run();
        }
    };

    // === LAYER 2: Key Validation Bypass ===
    var KeyManager = Java.use("com.android.support.KeyManager");

    KeyManager.isActivated.implementation = function(ctx) {
        // Set internal state to VALID
        var stateLock = KeyManager.class.getDeclaredField("STATE_LOCK");
        stateLock.setAccessible(true);

        var sessionState = KeyManager.class.getDeclaredField("sessionState");
        sessionState.setAccessible(true);
        sessionState.setInt(null, 2); // SESSION_VALID

        var validated = KeyManager.class.getDeclaredField("validatedThisSession");
        validated.setAccessible(true);
        validated.setBoolean(null, true);

        var confirmed = KeyManager.class.getDeclaredField("serverConfirmedThisSession");
        confirmed.setAccessible(true);
        confirmed.setBoolean(null, true);

        return true;
    };

    KeyManager.hasValidPaidSession.implementation = function(ctx) {
        return true;
    };

    KeyManager.hasValidPaidSessionLocal.implementation = function(ctx) {
        return true;
    };

    KeyManager.validateKeyOnline.implementation = function(ctx, key) {
        return true;
    };

    // === LAYER 3: Native Enforcement Bypass ===
    var Main = Java.use("com.android.support.Main");

    Main.IsPaidSessionValid.implementation = function() {
        return true;
    };

    Main.IsClientVersionAllowed.implementation = function() {
        return true;
    };

    Main.ApplySecurityState.implementation = function(ctx, ticket, token) {
        return true;
    };

    // Also hook the native functions directly
    var libSakura = Module.findBaseAddress("libSakura.so");
    if (libSakura) {
        // Find JNI exports
        var symbols = Module.enumerateExports("libSakura.so");
        symbols.forEach(function(sym) {
            if (sym.name.indexOf("IsPaidSessionValid") !== -1) {
                Interceptor.replace(sym.address, new NativeCallback(function() {
                    return 1; // true
                }, 'int', []));
            }
            if (sym.name.indexOf("IsClientVersionAllowed") !== -1) {
                Interceptor.replace(sym.address, new NativeCallback(function() {
                    return 1;
                }, 'int', []));
            }
            if (sym.name.indexOf("ApplySecurityState") !== -1) {
                Interceptor.replace(sym.address, new NativeCallback(function() {
                    return 1;
                }, 'int', ['pointer', 'pointer', 'pointer', 'pointer']));
            }
        });
    }

    // === ANTI-DETECTION BYPASS ===
    // Disable frida detection by hooking File operations
    var File = Java.use("java.io.File");
    var original_exists = File.exists;
    File.exists.implementation = function() {
        var path = this.getAbsolutePath();
        var blocked = ["frida", "xposed", "magisk", "substrate", "lsposed",
                       "edxposed", "linjector", "libgadget"];
        for (var i = 0; i < blocked.length; i++) {
            if (path.toLowerCase().indexOf(blocked[i]) !== -1) {
                return false;
            }
        }
        return original_exists.call(this);
    };

    // Hide TracerPid
    var BufferedReader = Java.use("java.io.BufferedReader");
    BufferedReader.readLine.overload().implementation = function() {
        var line = this.readLine();
        if (line && line.indexOf("TracerPid") !== -1) {
            return "TracerPid:\t0";
        }
        return line;
    };

    console.log("[*] Sakura Project Crack loaded!");
    console.log("[*] All 3 protection layers bypassed");
    console.log("[*] Anti-detection hooks active");
});
"""

XPOSED_MODULE = r"""
// Sakura Project Crack - Xposed Module (Java)
// Put in an Xposed module project

package com.sakura.crack;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SakuraCrack implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.wb.goog.mkx")) return;

        Class<?> gate = XposedHelpers.findClass(
            "com.android.support.SakuraSecurityGate", lpparam.classLoader);
        Class<?> km = XposedHelpers.findClass(
            "com.android.support.KeyManager", lpparam.classLoader);
        Class<?> main = XposedHelpers.findClass(
            "com.android.support.Main", lpparam.classLoader);

        // Version Gate
        XposedHelpers.findAndHookMethod(gate, "isAllowed",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });

        // Key Manager
        XposedHelpers.findAndHookMethod(km, "isActivated",
            android.content.Context.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });

        XposedHelpers.findAndHookMethod(km, "hasValidPaidSession",
            android.content.Context.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });

        // Native
        XposedHelpers.findAndHookMethod(main, "IsPaidSessionValid",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });

        XposedHelpers.findAndHookMethod(main, "IsClientVersionAllowed",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });
    }
}
"""

SMALI_PATCH_GUIDE = """
=== SMALI PATCH (Offline - modify DEX directly) ===

1. Decompile APK:
   apktool d mortal_kombat.apk

2. Patch SakuraSecurityGate.isAllowed() in classes8.dex smali:
   File: smali_classes8/com/android/support/SakuraSecurityGate.smali

   Find method: .method public static isAllowed()Z
   Replace entire method body with:
     .registers 1
     const/4 v0, 0x1
     return v0
   .end method

3. Patch KeyManager.isActivated():
   File: smali_classes8/com/android/support/KeyManager.smali

   Find method: .method public static isActivated(Landroid/content/Context;)Z
   Replace body with:
     .registers 2
     const/4 v0, 0x1
     return v0
   .end method

4. Patch KeyManager.hasValidPaidSession():
   Find method: .method public static hasValidPaidSession(Landroid/content/Context;)Z
   Replace body with:
     .registers 2
     const/4 v0, 0x1
     return v0
   .end method

5. Patch KeyManager.hasValidPaidSessionLocal():
   Find method: .method static hasValidPaidSessionLocal(Landroid/content/Context;)Z
   Replace body with:
     .registers 2
     const/4 v0, 0x1
     return v0
   .end method

6. Recompile:
   apktool b mortal_kombat -o mortal_kombat_cracked.apk

7. Sign:
   apksigner sign --ks keystore.jks mortal_kombat_cracked.apk

NOTE: The native library (libSakura.so) also needs patching.
See NATIVE_BINARY_PATCH below for exact offsets and bytes.
"""

NATIVE_BINARY_PATCH = r"""
=== NATIVE LIBRARY BINARY PATCH (libSakura.so, ARM64 ELF) ===

Full reverse engineering of libSakura.so (1.6MB, ARM64, stripped).

ARCHITECTURE:
  - ELF 64-bit LSB shared object, ARM aarch64
  - BuildID: sha1=c4016188687849b6af3501513603b05d0154120c
  - SONAME: libSakura.so
  - Deps: liblog, libandroid, libEGL, libGLESv2, libz, libc, libm, libdl
  - 1455 FUNC exports (mostly C++ STL/NDK)

JNI REGISTRATION:
  - JNI_OnLoad @ 0x98a34 (1092 bytes)
  - Only 1 static JNI: Java_com_android_support_Main_InstallNativeCrashHandler @ 0x3f064
  - All other natives registered dynamically via RegisterNatives:
    * init1 @ 0x90f50: registers 10 methods for Main class
    * init2 @ 0x923d8: registers 1 method for SakuraServerTrust
    * init3 @ 0x9269c: registers 14 methods for additional classes
    * JNI_OnLoad direct: registers 4 more methods via PLT
  - Method names are XOR-obfuscated at rest, decoded into BSS at runtime
  - RegisterNatives wrapper @ 0x92188 handles deobfuscation

NATIVE FUNCTION MAP (from full disassembly):

  IsPaidSessionValid() @ 0x3a2a0 [5 instructions, LEAF]:
    adrp x8, #0x19c000
    add  x8, x8, #4       ; x8 = BSS 0x19c004
    ldarb w8, [x8]         ; atomic load byte
    and  w0, w8, #1        ; return bit 0
    ret
    >>> THE ENTIRE "NATIVE ENFORCEMENT" IS ONE BOOLEAN READ <<<

  IsClientVersionAllowed() @ 0x9885c [3 instructions, LEAF]:
    adrp x8, #0x19c000
    ldrb w0, [x8, #0x14]   ; load byte from BSS 0x19c014
    ret
    >>> THREE INSTRUCTIONS. THE VERSION "GATE" <<<

  ApplySecurityState() @ 0x98868 [~70 instructions]:
    - Locks mutex at BSS 0x19c074
    - Checks IsClientVersionAllowed flag (0x19c014)
    - Checks session flag (0x19c070)
    - Compares expiration time (0x1b86e8) with current time
    - Validates device hash (0x1b86f0) and check hash (0x1b86f8)
    - "Integrity" check: XOR with magic "SAKURAP3" (0x53414b5552415033)
      then run splitmix64 (standard PRNG, not crypto hash!)
      Constants: 0xbf58476d1ce4e5b9, 0x94d049bb133111eb
    - On success: unlock mutex, return true
    - On failure: CLEAR ALL SESSION DATA, unlock, return false
    >>> Uses a PRNG as integrity hash with hardcoded magic "SAKURAP3" <<<

  ResetSecurityState() @ 0x986bc:
    - Clears 0x19c014 (version flag), 0x19c070 (session flag)
    - Zeros session data at 0x1b86e8/0x1b86f0/0x1b86f8

  ResetPaidSession() @ 0x98790:
    - Clears 0x19c070 and session data, keeps version flag

  ClientBuildCode() @ 0x93d94:    mov w0, #0x841; ret  (returns 2113)
  ClientTokenVersion() @ 0x93da0: mov w0, #0xd; ret    (returns 13)

BSS MEMORY LAYOUT:
  0x19c004: IsPaidSessionValid flag (bit 0)
  0x19c014: IsClientVersionAllowed flag
  0x19c070: Session validity flag
  0x19c074: Mutex (pthread_mutex_t)
  0x1b86e8: Expiration timestamp (uint64)
  0x1b86f0: Device key hash (uint64)
  0x1b86f8: Hash check value (uint64)

=== BINARY PATCHES ===

Patch 1: IsPaidSessionValid @ file offset 0x3a2a0
  Original: 08 0b 00 d0 08 11 00 91 08 fd df 08 00 01 00 12 c0 03 5f d6
  Patched:  20 00 80 52 c0 03 5f d6 (+ 12 bytes NOP: 1f 20 03 d5 x3)
  Effect:   MOV W0, #1; RET (always returns true)

Patch 2: IsClientVersionAllowed @ file offset 0x9885c
  Original: 28 08 00 90 00 51 40 39 c0 03 5f d6
  Patched:  20 00 80 52 c0 03 5f d6 (+ 4 bytes NOP: 1f 20 03 d5)
  Effect:   MOV W0, #1; RET (always returns true)

Python patcher:
  import shutil
  shutil.copy('libSakura.so', 'libSakura_cracked.so')
  with open('libSakura_cracked.so', 'r+b') as f:
      # Patch IsPaidSessionValid
      f.seek(0x3a2a0)
      f.write(b'\\x20\\x00\\x80\\x52')  # MOV W0, #1
      f.write(b'\\xc0\\x03\\x5f\\xd6')  # RET
      # Patch IsClientVersionAllowed
      f.seek(0x9885c)
      f.write(b'\\x20\\x00\\x80\\x52')  # MOV W0, #1
      f.write(b'\\xc0\\x03\\x5f\\xd6')  # RET
"""


def print_analysis():
    print("=" * 70)
    print("  SAKURA PROJECT MOD MENU - FULL PROTECTION ANALYSIS & CRACK")
    print("  Target: Mortal Kombat Mobile (com.wb.goog.mkx)")
    print("=" * 70)
    print()
    print("FILES ANALYZED:")
    print("  - libSakura.so  (1.6MB, ARM64 ELF, stripped)")
    print("  - classes8.dex  (2.6MB, 494 classes, 6545 strings)")
    print()
    print("PROTECTION LAYERS:")
    print()
    print("  Layer 1: VERSION GATE (SakuraSecurityGate)")
    print("    - HTTP POST to version-check.php")
    print("    - Sets allowed=true + versionTicket string")
    print("    - Gated by native IsClientVersionAllowed()")
    print("    BYPASS: Hook isAllowed() -> return true")
    print()
    print("  Layer 2: KEY VALIDATION (KeyManager)")
    print("    - Online key check via api.php")
    print("    - EC-signed server responses (SakuraServerTrust)")
    print("    - LICENSE_V3 token with deviceKeyHash binding")
    print("    - AES-GCM encrypted local storage")
    print("    - SHA256 integrity checks")
    print("    BYPASS: Hook isActivated() + hasValidPaidSession() -> true")
    print()
    print("  Layer 3: NATIVE ENFORCEMENT (libSakura.so)")
    print("    - IsPaidSessionValid() @ 0x3a2a0 - 5 ARM64 instructions, reads BSS 0x19c004")
    print("    - IsClientVersionAllowed() @ 0x9885c - 3 ARM64 instructions, reads BSS 0x19c014")
    print("    - ApplySecurityState() @ 0x98868 - splitmix64 PRNG as 'hash', magic 'SAKURAP3'")
    print("    - ResetSecurityState() @ 0x986bc - clears all flags")
    print("    - ResetPaidSession() @ 0x98790 - clears paid session data")
    print("    - ClientBuildCode() @ 0x93d94 - returns constant 2113")
    print("    - ClientTokenVersion() @ 0x93da0 - returns constant 13")
    print("    - Anti-tamper: string search in /proc/self/maps + File.exists()")
    print("    - JNI method names XOR-obfuscated (decoded into BSS at runtime)")
    print("    - RegisterNatives via wrapper @ 0x92188 (not standard JNI naming)")
    print("    BYPASS: 8 bytes per function -> MOV W0, #1; RET")
    print()
    print("SERVER INFRASTRUCTURE:")
    print(f"    Base URL:      https://sakuraproject.ru.net/remote/")
    print(f"    License API:   https://sakuraproject.ru.net/api.php")
    print(f"    Version Check: https://sakuraproject.ru.net/version-check.php")
    print(f"    Trusted Host:  sakuraproject.ru.net")
    print(f"    Telegram:      https://t.me/LunarEncruption")
    print()
    print("KEY SYSTEM:")
    print("    Format: SakuraKeyGen-[A-Za-z0-9_-]+")
    print("    Max length: 128 chars")
    print("    Protocol: LICENSE_V3")
    print("    Token fields: expiresAt, deviceKeyHash, tokenVersion")
    print("    Crypto: SHA256 + AES-GCM + EC (secp256r1) + RSA")
    print("    Local storage: AES-GCM encrypted SharedPreferences")
    print()
    print("ANTI-TAMPER CHECKS:")
    print("    - frida (agent, maps, file, port)")
    print("    - xposed (file, maps)")
    print("    - edxposed, lsposed")
    print("    - magisk (maps)")
    print("    - substrate (lib, maps)")
    print("    - libgadget, linjector")
    print("    - TracerPid (ptrace detection)")
    print("    - APK SHA256 integrity")
    print("    - DEX SHA256 integrity")
    print()
    print("FEATURES UNLOCKED:")
    features = [
        "Auto Win", "Auto Win with XP", "Auto Lose",
        "Farm Bot (FarmBotOverlay/FarmBotService)", "SpeedHack",
        "Infinite Buffs & Debuffs", "Bots Don't Block",
        "Bots Never Hit", "Bots Cannot Use Kameo",
        "No Krypt Exhaustion", "Krypt Farm",
        "Crystal Tower Attempts Freeze", "Disable Enemy Equipment",
        "Disable All Modifiers", "Disable Special Moves",
        "All Support Cards X", "Equipment X",
        "Fast Battle Replay", "AI Difficulty Control",
        "Brutality Without Equipment", "Faction Wars Farm",
        "Safe Mode", "Premium Tier", "Premium Plus Tier",
        "Wide View (camera hack)", "Fast Attack Animation"
    ]
    for f in features:
        print(f"    + {f}")
    print()
    print("GAME HOOKS (libSakura.so offsets):")
    print("    UMKMobileGameInstance::BeginLoadingScreen @ 0x6109540")
    print("    UBuffComponent::TickComponent (InfBuff)  @ 0x5E67624")
    print("    ACombatCharacter::PlayAttackAnim         @ 0x5F0FC74")
    print("    FMinimalViewInfo::CalcProjectionMatrix    @ 0x8D6B6A8")
    print()
    print("NATIVE LIBRARY REVERSE ENGINEERING:")
    print("    IsPaidSessionValid @ 0x3a2a0:")
    print("      adrp x8, #0x19c000; add x8,x8,#4; ldarb w8,[x8]; and w0,w8,#1; ret")
    print("      PATCH: 20 00 80 52 c0 03 5f d6 (MOV W0,#1; RET)")
    print("    IsClientVersionAllowed @ 0x9885c:")
    print("      adrp x8, #0x19c000; ldrb w0,[x8,#0x14]; ret")
    print("      PATCH: 20 00 80 52 c0 03 5f d6 (MOV W0,#1; RET)")
    print("    ApplySecurityState @ 0x98868:")
    print("      Magic: 'SAKURAP3' (0x53414b5552415033)")
    print("      Hash: splitmix64 PRNG (not cryptographic!)")
    print()
    print("CRACK STATUS: COMPLETE")
    print("  4 bypass methods: Frida, Xposed, Smali patch, Binary patch")
    print()


if __name__ == "__main__":
    print_analysis()

    print("\n" + "=" * 70)
    print("  FRIDA SCRIPT (save as sakura_crack.js)")
    print("=" * 70)
    print(FRIDA_SCRIPT)

    print("\n" + "=" * 70)
    print("  SMALI PATCH GUIDE")
    print("=" * 70)
    print(SMALI_PATCH_GUIDE)

    print("\n" + "=" * 70)
    print("  NATIVE BINARY PATCH (libSakura.so)")
    print("=" * 70)
    print(NATIVE_BINARY_PATCH)
