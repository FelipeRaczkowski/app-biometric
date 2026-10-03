package com.kulkan.biometricdemo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * ENFORCED.
 *
 * Identical setup to VulnerableActivity. The only difference is inside
 * onAuthenticationSucceeded: the Cipher is taken out of the result and
 * doFinal() is called.
 *
 * Forcing this callback gets you an exception, not a session.
 */
public class EnforcedActivity extends AppCompatActivity {

    private static final String TAG = "KulkanDemo";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "enforced-Alias-Key";
    private static final String PREFS = "enforced_prefs";

    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private byte[] encryptedToken;
    private byte[] iv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        loadSavedToken();

        executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                try {
                    Cipher cipher = result.getCryptoObject().getCipher();
                    byte[] plaintext = cipher.doFinal(encryptedToken);
                    String token = new String(plaintext, StandardCharsets.UTF_8);

                    Log.i(TAG, "Decrypted token: " + token);
                    showDialog("ACCESS GRANTED", token);

                } catch (Exception e) {
                    Log.e(TAG, "doFinal failed, no valid auth token", e);
                    showDialog("LOGIN REFUSED", e.getClass().getSimpleName());
                }
            }

            @Override
            public void onAuthenticationFailed() {
                Log.w(TAG, "Biometric not recognised");
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                Log.w(TAG, "Error " + errorCode + ": " + errString);
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Log in")
                .setSubtitle("CryptoObject enforced")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        authenticate();
    }

    /** Builds the Cipher and starts the biometric flow. */
    private void authenticate() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));

            biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));

        } catch (Exception e) {
            Log.e(TAG, "Setup failed", e);
            Toast.makeText(this, "Setup error", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    /** Reads the ciphertext and the IV stored at enrollment. */
    private void loadSavedToken() {
        encryptedToken = Base64.decode(
                getSharedPreferences(PREFS, MODE_PRIVATE).getString("token_saved", ""),
                Base64.NO_WRAP);
        iv = Base64.decode(
                getSharedPreferences(PREFS, MODE_PRIVATE).getString("IV_saved", ""),
                Base64.NO_WRAP);
    }
}
