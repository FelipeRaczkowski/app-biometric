/*
 * early-dofinal.js - calls doFinal() before the Biometric TA has issued
 * the HardwareAuthToken. The Secure World refuses.
 *
 *   frida -U -N com.kulkan.biometricdemo -l frida/early-dofinal.js
 */
Java.perform(function () {

var Prompt = Java.use('androidx.biometric.BiometricPrompt');

Prompt.authenticate.overload(
    'androidx.biometric.BiometricPrompt$PromptInfo',
    'androidx.biometric.BiometricPrompt$CryptoObject'
).implementation = function (info, cryptoObject) {
    try {
        cryptoObject.getCipher().doFinal(Java.array('byte', [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]));
        console.log("[!] doFinal() funciono sin autorizacion");
    } catch (e) {
        console.log("[+] doFinal() rechazado: " + e);
    }
    return this.authenticate(info, cryptoObject);
}})
