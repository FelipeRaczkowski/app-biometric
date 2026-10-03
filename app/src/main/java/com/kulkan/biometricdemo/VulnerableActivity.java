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

import java.security.KeyStore;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * VULNERABLE.
 *
 * The key is auth-bound, the Cipher is initialised in DECRYPT mode, and the
 * CryptoObject is handed to authenticate(). Everything is set up correctly.
 *
 * And none of it is used. onAuthenticationSucceeded ignores the result.
 */
public class VulnerableActivity extends AppCompatActivity {

    private static final String TAG = "KulkanDemo";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "vulnerable-Alias-Key";
    private static final String PREFS = "vulnerable_prefs";

    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

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
                Toast.makeText(getApplicationContext(),
                        "Authentication succeeded!", Toast.LENGTH_SHORT).show();
                showDialog("ACCESS GRANTED", "Authentication succeeded!");
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
                .setSubtitle("Vulnerable CryptoObject")
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

    /** Reads the IV stored at enrollment. */
    private void loadSavedToken() {
        iv = Base64.decode(
                getSharedPreferences(PREFS, MODE_PRIVATE).getString("IV_saved", ""),
                Base64.NO_WRAP);
    }
}
