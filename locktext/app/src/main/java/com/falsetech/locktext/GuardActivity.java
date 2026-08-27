package com.falsetech.locktext;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class GuardActivity extends Activity {
    private static final int REQ_DEVICE_CREDENTIAL = 301;

    private String key;
    private int level;
    private CancellationSignal cancellationSignal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        key = getIntent().getStringExtra("key");
        level = getIntent().getIntExtra("level", 1);
        String name = getIntent().getStringExtra("name");
        if (key == null) key = "";
        if (name == null || name.trim().isEmpty()) name = "Protected target";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(18));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("LockText");
        title.setTextSize(24);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView detail = new TextView(this);
        detail.setTextSize(16);
        detail.setPadding(0, dp(12), 0, dp(16));
        detail.setText(detail(name));
        root.addView(detail);

        Button allow = new Button(this);
        allow.setAllCaps(false);
        allow.setText(level == 1 ? "Confirm — allow one send"
                : level == 2 ? "Authenticate — allow one send"
                : "Hard locked");
        allow.setEnabled(level < 3);
        allow.setOnClickListener(v -> {
            if (level == 1) grantAndFinish();
            else if (level == 2) authenticate();
        });
        root.addView(allow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private String detail(String name) {
        if (level == 1) return name + " is Level 1 protected. Confirm intentionally before sending. The Send control is exposed for up to 5 seconds and the grant is consumed after the next Send tap.";
        if (level == 2) return name + " is Level 2 protected. Authenticate with biometrics or the device credential. The Send control is exposed for up to 5 seconds and the grant is consumed after the next Send tap.";
        return name + " is HARD LOCKED. Change this target to Level 1 or Level 2 inside LockText before sending.";
    }

    private void authenticate() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle("Unlock protected send")
                    .setSubtitle("LockText")
                    .setAllowedAuthenticators(
                            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
                                    | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();
            runPrompt(prompt);
            return;
        }

        if (android.os.Build.VERSION.SDK_INT == 29) {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle("Unlock protected send")
                    .setSubtitle("LockText")
                    .setDeviceCredentialAllowed(true)
                    .build();
            runPrompt(prompt);
            return;
        }

        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("Unlock protected send")
                .setSubtitle("LockText")
                .setNegativeButton("Use screen lock", getMainExecutor(), (dialog, which) -> launchDeviceCredential())
                .build();
        runPrompt(prompt);
    }

    private void runPrompt(BiometricPrompt prompt) {
        cancellationSignal = new CancellationSignal();
        prompt.authenticate(cancellationSignal, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                grantAndFinish();
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (android.os.Build.VERSION.SDK_INT == 28
                        && errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        && errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                        && errorCode != BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON) {
                    launchDeviceCredential();
                }
            }
        });
    }

    private void launchDeviceCredential() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            Toast.makeText(this, "No secure screen lock is configured.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = km.createConfirmDeviceCredentialIntent("Unlock protected send", "LockText");
        if (intent != null) startActivityForResult(intent, REQ_DEVICE_CREDENTIAL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DEVICE_CREDENTIAL && resultCode == RESULT_OK) grantAndFinish();
    }

    private void grantAndFinish() {
        if (key.isEmpty() || Store.byKey(this, key) == null) {
            Toast.makeText(this, "Protected target no longer exists.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Store.grant(this, key);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (cancellationSignal != null) cancellationSignal.cancel();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
