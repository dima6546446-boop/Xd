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
For IsPaidSessionValid and IsClientVersionAllowed in the .so:
- Find the JNI function entry points
- Patch to: MOV W0, #1; RET (ARM64: 20 00 80 52 C0 03 5F D6)
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
    print("    - IsPaidSessionValid() - master gate")
    print("    - ApplySecurityState() - session activation")
    print("    - IsClientVersionAllowed() - version check")
    print("    - Anti-tamper: frida/xposed/magisk/root detection")
    print("    BYPASS: Patch JNI -> MOV W0, #1; RET")
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
    print("CRACK STATUS: COMPLETE")
    print("  3 bypass methods provided: Frida, Xposed, Smali+Binary patch")
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
