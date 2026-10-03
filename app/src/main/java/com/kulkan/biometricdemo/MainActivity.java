package com.kulkan.biometricdemo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Launcher and one-time enrollment.
 *
 * The key is auth-bound for every purpose, encryption included, so the
 * ciphertext cannot be seeded silently. The first tap on each button creates
 * the key, asks for a biometric, encrypts the session token and stores it.
 *
 * None of this is part of the demo. It lives here so the two demo activities
 * contain nothing but the login flow.
 */
public class MainActivity extends AppCompatActivity {

    private static final String KEYSTORE = "AndroidKeyStore";

    private static final String ENFORCED_ALIAS = "enforced-Alias-Key";
    private static final String ENFORCED_PREFS = "enforced_prefs";

    private static final String VULNERABLE_ALIAS = "vulnerable-Alias-Key";
    private static final String VULNERABLE_PREFS = "vulnerable_prefs";

    private static final String SESSION_TOKEN = "FAKE-SESSION-TOKEN-FOR-DEMO";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnVulnerable).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(VULNERABLE_ALIAS, VULNERABLE_PREFS, VulnerableActivity.class);
            }
        });

        findViewById(R.id.btnEnforced).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(ENFORCED_ALIAS, ENFORCED_PREFS, EnforcedActivity.class);
            }
        });

        findViewById(R.id.txtReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reset();
            }
        });
    }

    private void open(String alias, String prefsName, Class<?> target) {
        if (getSharedPreferences(prefsName, MODE_PRIVATE).contains("token_saved")) {
            startActivity(new Intent(this, target));
        } else {
            enroll(alias, prefsName);
        }
    }

    private void enroll(final String alias, final String prefsName) {
        try {
            createKey(alias);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey(alias));

            BiometricPrompt prompt = new BiometricPrompt(this,
                    ContextCompat.getMainExecutor(this),
                    new BiometricPrompt.AuthenticationCallback() {

                @Override
                public void onAuthenticationSucceeded(
                        @NonNull BiometricPrompt.AuthenticationResult result) {
                    try {
                        Cipher c = result.getCryptoObject().getCipher();
                        byte[] ciphertext =
                                c.doFinal(SESSION_TOKEN.getBytes(StandardCharsets.UTF_8));

                        SharedPreferences prefs =
                                getSharedPreferences(prefsName, MODE_PRIVATE);
                        prefs.edit()
                                .putString("token_saved",
                                        Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                                .putString("IV_saved",
                                        Base64.encodeToString(c.getIV(), Base64.NO_WRAP))
                                .apply();

                        toast("Ready. Tap again to log in.");
                    } catch (Exception e) {
                        toast("Enrollment failed");
                    }
                }

                @Override
                public void onAuthenticationError(int errorCode,
                                                  @NonNull CharSequence errString) {
                    toast("Enrollment cancelled");
                }
            });

            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Enable biometric login")
                    .setNegativeButtonText("Cancel")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build();

            prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));

        } catch (Exception e) {
            toast("Setup error");
        }
    }

    private void createKey(String alias) throws Exception {
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(-1);
        }

        KeyGenerator generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(builder.build());
        generator.generateKey();
    }

    private SecretKey getKey(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        return (SecretKey) keyStore.getKey(alias, null);
    }

    private void reset() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            keyStore.deleteEntry(ENFORCED_ALIAS);
            keyStore.deleteEntry(VULNERABLE_ALIAS);
            getSharedPreferences(ENFORCED_PREFS, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences(VULNERABLE_PREFS, MODE_PRIVATE).edit().clear().apply();
            toast("Keys deleted");
        } catch (Exception e) {
            toast("Reset failed");
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
