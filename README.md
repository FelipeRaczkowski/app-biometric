# BiometricPrompt Demo

A deliberately vulnerable Android app showing how `BiometricPrompt` can be
bypassed when the authentication decision lives inside the app process, and why
the same bypass fails when it does not.

## What it does

Two buttons. Both run **exactly the same setup**: an auth-bound AES/GCM key in
the AndroidKeyStore, a `Cipher` initialised in `DECRYPT` mode, and a
`CryptoObject` handed to `authenticate()`.

The only difference is the body of `onAuthenticationSucceeded`.

| Button | Key alias | What the success callback does |
|---|---|---|
| **Vulnerable CryptoObject** | `vulnerable-Alias-Key` | Ignores `result`, shows ACCESS GRANTED |
| **CryptoObject Enforced** | `enforced-Alias-Key` | Takes the Cipher from `result` and calls `doFinal()` |

## Demo flow

1. Launch the app and tap a button. The first tap enrolls: it creates the key,
   asks for a biometric, encrypts a placeholder token and stores it.
2. Tap again. The LOCKED screen appears with the system biometric prompt.
3. Attach Frida and run `frida/bypass.js`.
4. Present a finger that does **not** match. The system dialog says "not
   recognised" and the app pops ACCESS GRANTED anyway.
5. Point the same hook at `EnforcedActivity$1` and you get LOGIN REFUSED
   instead, because `doFinal()` has no valid HardwareAuthToken behind it.

The result dialog stays on screen until you tap OK. Nothing calls `finish()`,
so the biometric prompt keeps asking for a fingerprint behind it — which is
the point worth showing.

**Reset** deletes both keys and both stored tokens so the demo can be re-run.

## Source layout

```
MainActivity.java        two buttons, plus the one-time enrollment
VulnerableActivity.java  the full login flow, callback ignores the result
EnforcedActivity.java    the full login flow, callback uses the result
```

Each demo activity is self-contained: key lookup, cipher setup and the result
handling all live in the same file. Diffing the two is the whole point.

Lambdas are avoided throughout, since D8 desugars them into
`$$ExternalSyntheticLambda` classes that clutter decompiled output.

## Stored values

```
/data/data/com.kulkan.biometricdemo/shared_prefs/enforced_prefs.xml
/data/data/com.kulkan.biometricdemo/shared_prefs/vulnerable_prefs.xml

    token_saved   the encrypted session token, Base64
    IV_saved      the GCM initialization vector, Base64
```

Worth showing on a rooted device: both sit there in plain sight, and are
useless without the key.

## Building

No Gradle wrapper, so use the included CI or supply your own Gradle 8.7+.

**GitHub Actions** — push the repo. The workflow uploads `biometric-demo-apk`
as an artifact. Download, unzip, then `adb install -r app-debug.apk`.

**Locally** — JDK 17 and an Android SDK with `platforms;android-34` and
`build-tools;34.0.0`, then `gradle assembleDebug`. Android Studio also works
with no extra setup.

## Requirements

- Android 10 (API 29) or later
- A Class 3 (Strong) biometric enrolled on the device
- Root plus a running `frida-server` for the bypass

## Notes

- `minifyEnabled` is off in both build types so decompiled output stays readable.
- `setAllowedAuthenticators(BIOMETRIC_STRONG)` decompiles to
  `setAllowedAuthenticators(15)`; the constant is inlined at compile time.
- The AuthenticationCallback is the first anonymous class in each demo
  activity, so it compiles to `$1`. The dialog listener becomes `$2`. Confirm
  in jadx against the APK you actually built before relying on it.
- No obfuscation, no root detection, no anti-tamper. Intentional: the point is
  the trust boundary, not the hardening around it.
