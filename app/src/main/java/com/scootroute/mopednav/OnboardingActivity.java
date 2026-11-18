package com.scootroute.mopednav;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class OnboardingActivity extends AppCompatActivity {

    private TextView tvGreeting;
    private Spinner spinnerVehicle;
    private Spinner spinnerDisplay;
    private Spinner spinnerRoutingProfile;
    private View layoutRoutingProfile;

    // Routing preferences checkboxes
    private CheckBox cbTrafficData;
    private CheckBox cbExcludeBikePaths;
    private CheckBox cbExcludeStairways;
    private CheckBox cbIgnoreOneWay;
    private CheckBox cbAvoidTolls;
    private CheckBox cbAvoidUnpaved;
    private CheckBox cbAvoidSteepHills;

    private Button btnSaveSettings;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String accountType = "free";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        bindViews();
        loadUserData();
        setupSpinners();
        setupListeners();
    }

    private void bindViews() {
        tvGreeting          = findViewById(R.id.tvOnboardGreeting);
        spinnerVehicle      = findViewById(R.id.spinnerVehicle);
        spinnerDisplay      = findViewById(R.id.spinnerDisplay);
        spinnerRoutingProfile = findViewById(
            R.id.spinnerRoutingProfile);
        layoutRoutingProfile = findViewById(
            R.id.layoutRoutingProfile);
        cbTrafficData       = findViewById(R.id.cbTrafficData);
        cbExcludeBikePaths  = findViewById(R.id.cbExcludeBikePaths);
        cbExcludeStairways  = findViewById(R.id.cbExcludeStairways);
        cbIgnoreOneWay      = findViewById(R.id.cbIgnoreOneWay);
        cbAvoidTolls        = findViewById(R.id.cbAvoidTolls);
        cbAvoidUnpaved      = findViewById(R.id.cbAvoidUnpaved);
        cbAvoidSteepHills   = findViewById(R.id.cbAvoidSteepHills);
        btnSaveSettings     = findViewById(R.id.btnSaveSettings);

        // Set defaults
        cbExcludeBikePaths.setChecked(true);
        cbExcludeStairways.setChecked(true);
        cbAvoidTolls.setChecked(true);
        cbTrafficData.setChecked(true);
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            tvGreeting.setText("Welcome to ScootRoute!");
            layoutRoutingProfile.setVisibility(View.GONE);
            return;
        }

        if (user.isAnonymous()) {
            tvGreeting.setText("Welcome, Guest!\nPlease configure your preferences:");
            layoutRoutingProfile.setVisibility(View.GONE);
            return;
        }

        // Load user profile from Firestore
        db.collection("users").document(user.getUid())
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String name = doc.getString("name");
                    accountType = doc.getString("accountType");
                    if (accountType == null)
                        accountType = "free";

                    String firstName = name != null
                        ? name.split(" ")[0] : "Rider";
                    tvGreeting.setText("Hello " + firstName
                        + "!\nPlease provide the following:");

                    // Show routing profile for pro users only
                    if ("pro".equals(accountType)) {
                        layoutRoutingProfile
                            .setVisibility(View.VISIBLE);
                    } else {
                        layoutRoutingProfile
                            .setVisibility(View.GONE);
                    }
                }
            })
            .addOnFailureListener(e -> {
                tvGreeting.setText(
                    "Welcome to ScootRoute!\nPlease configure your preferences:");
            });
    }

    private void setupSpinners() {
        // Vehicle type spinner
        ArrayAdapter<String> vehicleAdapter =
            new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{
                    "Select vehicle type...",
                    "Moped (under 50cc)",
                    "Moped (50cc - 150cc)",
                    "Scooter (150cc+)",
                    "Electric Scooter",
                    "Electric Moped",
                    "Motorcycle",
                    "Bicycle"
                });
        vehicleAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        spinnerVehicle.setAdapter(vehicleAdapter);

        // Display style spinner
        ArrayAdapter<String> displayAdapter =
            new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{
                    "Dark Mode (Default)",
                    "Light Mode",
                    "Auto (Follow System)"
                });
        displayAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        spinnerDisplay.setAdapter(displayAdapter);

        // Routing profile (Pro only)
        ArrayAdapter<String> routingAdapter =
            new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{
                    "Recommended (Default)",
                    "Fastest Route",
                    "Shortest Route",
                    "Safest Route",
                    "Scenic Route"
                });
        routingAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        spinnerRoutingProfile.setAdapter(routingAdapter);
    }

    private void setupListeners() {
        btnSaveSettings.setOnClickListener(v -> saveSettings());
    }

    private void saveSettings() {
        btnSaveSettings.setEnabled(false);
        btnSaveSettings.setText("Saving...");

        // Build settings map
        String vehicleType =
            spinnerVehicle.getSelectedItemPosition() > 0
            ? spinnerVehicle.getSelectedItem().toString()
            : "Moped";

        String displayMode =
            spinnerDisplay.getSelectedItem().toString();

        String routingProfile =
            spinnerRoutingProfile.getSelectedItem().toString();

        boolean trafficData    = cbTrafficData.isChecked();
        boolean excludeBike    = cbExcludeBikePaths.isChecked();
        boolean excludeStairs  = cbExcludeStairways.isChecked();
        boolean ignoreOneWay   = cbIgnoreOneWay.isChecked();
        boolean avoidTolls     = cbAvoidTolls.isChecked();
        boolean avoidUnpaved   = cbAvoidUnpaved.isChecked();
        boolean avoidSteepHills= cbAvoidSteepHills.isChecked();

        // Save to SharedPreferences for quick local access
        SharedPreferences prefs = getSharedPreferences(
            "ScootRoute", MODE_PRIVATE);
        prefs.edit()
            .putString("vehicleType", vehicleType)
            .putString("displayMode", displayMode)
            .putString("routingProfile", routingProfile)
            .putBoolean("trafficData", trafficData)
            .putBoolean("excludeBikePaths", excludeBike)
            .putBoolean("excludeStairways", excludeStairs)
            .putBoolean("ignoreOneWay", ignoreOneWay)
            .putBoolean("avoidTolls", avoidTolls)
            .putBoolean("avoidUnpaved", avoidUnpaved)
            .putBoolean("avoidSteepHills", avoidSteepHills)
            .putBoolean("onboardingComplete", true)
            .apply();

        // Save to Firestore for logged in users
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            Map<String, Object> settings = new HashMap<>();
            settings.put("vehicleType", vehicleType);
            settings.put("displayMode", displayMode);
            settings.put("routingProfile", routingProfile);
            settings.put("trafficData", trafficData);
            settings.put("excludeBikePaths", excludeBike);
            settings.put("excludeStairways", excludeStairs);
            settings.put("ignoreOneWay", ignoreOneWay);
            settings.put("avoidTolls", avoidTolls);
            settings.put("avoidUnpaved", avoidUnpaved);
            settings.put("avoidSteepHills", avoidSteepHills);

            db.collection("users").document(user.getUid())
                .update(settings)
                .addOnSuccessListener(u -> goToMain())
                .addOnFailureListener(e -> goToMain());
        } else {
            goToMain();
        }
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
