package com.scootroute.mopednav;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;

public class SettingsActivity extends AppCompatActivity {

    private Switch switchVoiceNav;
    private Switch switchTraffic;
    private Switch switchNightMode;
    private Switch switchKeepScreenOn;
    private Switch switchSpeedWarnings;
    private TextView tvAccountEmail;
    private TextView tvAccountType;
    private CardView btnSignOut;
    private CardView btnDeleteAccount;
    private CardView btnBack;
    private CardView btnUpgrade;
    private Button btnSaveSettings;
    private Button btnResetOnboarding;

    private SharedPreferences prefs;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences("ScootRoute",
            MODE_PRIVATE);

        bindViews();
        loadSettings();
        setupListeners();
    }

    private void bindViews() {
        switchVoiceNav      =
            findViewById(R.id.switchVoiceNav);
        switchTraffic       =
            findViewById(R.id.switchTrafficData);
        switchNightMode     =
            findViewById(R.id.switchNightMode);
        switchKeepScreenOn  =
            findViewById(R.id.switchKeepScreenOn);
        switchSpeedWarnings =
            findViewById(R.id.switchSpeedWarnings);
        tvAccountEmail      =
            findViewById(R.id.tvSettingsEmail);
        tvAccountType       =
            findViewById(R.id.tvSettingsAccountType);
        btnSignOut          =
            findViewById(R.id.btnSettingsSignOut);
        btnDeleteAccount    =
            findViewById(R.id.btnDeleteAccount);
        btnBack             =
            findViewById(R.id.btnSettingsBack);
        btnUpgrade          =
            findViewById(R.id.btnSettingsUpgrade);
        btnSaveSettings     =
            findViewById(R.id.btnSaveSettings);
        btnResetOnboarding  =
            findViewById(R.id.btnResetOnboarding);
    }

    private void loadSettings() {
        switchVoiceNav.setChecked(
            prefs.getBoolean("voiceNav", true));
        switchTraffic.setChecked(
            prefs.getBoolean("trafficData", true));
        switchNightMode.setChecked(
            prefs.getBoolean("nightMode", true));
        switchKeepScreenOn.setChecked(
            prefs.getBoolean("keepScreenOn", true));
        switchSpeedWarnings.setChecked(
            prefs.getBoolean("speedWarnings", true));

        com.google.firebase.auth.FirebaseUser user =
            mAuth.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            tvAccountEmail.setText(user.getEmail());
            String type = prefs.getString(
                "accountType", "free");
            tvAccountType.setText(
                type.substring(0, 1).toUpperCase()
                + type.substring(1) + " Account");
        } else {
            tvAccountEmail.setText("Guest Mode");
            tvAccountType.setText("Guest Account");
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnSaveSettings.setOnClickListener(v -> {
            prefs.edit()
                .putBoolean("voiceNav",
                    switchVoiceNav.isChecked())
                .putBoolean("trafficData",
                    switchTraffic.isChecked())
                .putBoolean("nightMode",
                    switchNightMode.isChecked())
                .putBoolean("keepScreenOn",
                    switchKeepScreenOn.isChecked())
                .putBoolean("speedWarnings",
                    switchSpeedWarnings.isChecked())
                .apply();
            Toast.makeText(this,
                "Settings saved!",
                Toast.LENGTH_SHORT).show();
            finish();
        });

        btnUpgrade.setOnClickListener(v ->
            startActivity(new Intent(
                this, AccountActivity.class)));

        btnResetOnboarding.setOnClickListener(v -> {
            prefs.edit()
                .putBoolean("onboardingComplete", false)
                .putBoolean("waiverAccepted", false)
                .apply();
            Toast.makeText(this,
                "Onboarding reset — takes effect next launch",
                Toast.LENGTH_SHORT).show();
        });

        btnSignOut.setOnClickListener(v -> {
            mAuth.signOut();
            prefs.edit()
                .putBoolean("waiverAccepted", false)
                .putBoolean("onboardingComplete", false)
                .apply();
            Intent intent = new Intent(
                this, AuthActivity.class);
            intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        btnDeleteAccount.setOnClickListener(v ->
            new androidx.appcompat.app.AlertDialog.Builder(
                    this)
                .setTitle("Delete Account")
                .setMessage(
                    "Are you sure? This cannot be undone. "
                    + "All your data will be permanently "
                    + "deleted.")
                .setPositiveButton("Delete",
                    (d, w) -> deleteAccount())
                .setNegativeButton("Cancel", null)
                .show());
    }

    private void deleteAccount() {
        com.google.firebase.auth.FirebaseUser user =
            mAuth.getCurrentUser();
        if (user == null) return;

        // Delete Firestore data first
        com.google.firebase.firestore.FirebaseFirestore
            .getInstance()
            .collection("users")
            .document(user.getUid())
            .delete()
            .addOnCompleteListener(t -> {
                // Then delete auth account
                user.delete().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        prefs.edit().clear().apply();
                        Intent intent = new Intent(
                            this, AuthActivity.class);
                        intent.setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    } else {
                        Toast.makeText(this,
                            "Could not delete account. "
                            + "Please re-login and try again.",
                            Toast.LENGTH_LONG).show();
                    }
                });
            });
    }
}
