package com.scootroute.mopednav;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private LinearLayout layoutLogin;
    private LinearLayout layoutRegister;
    private LinearLayout layoutForgotPassword;
    private EditText etLoginEmail, etLoginPassword;
    private EditText etRegEmail, etRegPassword, etRegName;
    private EditText etForgotEmail;
    private Button btnLogin, btnRegister;
    private Button btnSwitchToRegister, btnSwitchToLogin;
    private Button btnSendReset, btnBackToLogin;
    private TextView btnForgotPassword;
    private Button btnGuest;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // If already logged in check what screen to show
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            navigateFromAuth(currentUser);
            return;
        }

        setContentView(R.layout.activity_auth);
        bindViews();
        setupListeners();
        showLoginMode();
    }

    // Decide where to send the user based on their state
    private void navigateFromAuth(FirebaseUser user) {
        SharedPreferences prefs = getSharedPreferences(
            "ScootRoute", MODE_PRIVATE);

        boolean waiverAccepted =
            prefs.getBoolean("waiverAccepted", false);
        boolean onboardingComplete =
            prefs.getBoolean("onboardingComplete", false);

        // Guests skip waiver, go straight to onboarding
        // or main if already onboarded
        if (user.isAnonymous()) {
            if (!onboardingComplete) {
                goTo(OnboardingActivity.class);
            } else {
                goTo(MainActivity.class);
            }
            return;
        }

        // Registered users must accept waiver first
        if (!waiverAccepted) {
            goTo(WaiverActivity.class);
        } else if (!onboardingComplete) {
            goTo(OnboardingActivity.class);
        } else {
            goTo(MainActivity.class);
        }
    }

    private void goTo(Class<?> dest) {
        // Show interstitial ad before main map for free users
        if (dest == MainActivity.class) {
            showInterstitialThenGo();
        } else {
            Intent intent = new Intent(this, dest);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private com.google.android.gms.ads.interstitial
            .InterstitialAd splashInterstitial;

    private void showInterstitialThenGo() {
        android.content.SharedPreferences prefs =
            getSharedPreferences("ScootRoute", MODE_PRIVATE);
        String accountType =
            prefs.getString("accountType", "free");

        // Pro/Plus skip the ad entirely
        if ("pro".equals(accountType)
                || "plus".equals(accountType)) {
            launchMain();
            return;
        }

        // Check if shown today already
        long lastShown =
            prefs.getLong("lastSplashAd", 0);
        long now = System.currentTimeMillis();
        if (now - lastShown < 24 * 60 * 60 * 1000L) {
            launchMain();
            return;
        }

        // Load and show interstitial
        com.google.android.gms.ads.MobileAds.initialize(
            this, status -> {});

        com.google.android.gms.ads.interstitial
            .InterstitialAd.load(
            this,
            AdManager.INTERSTITIAL_ID,
            new com.google.android.gms.ads.AdRequest
                .Builder().build(),
            new com.google.android.gms.ads.interstitial
                    .InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(
                        com.google.android.gms.ads
                        .interstitial.InterstitialAd ad) {
                    splashInterstitial = ad;
                    splashInterstitial
                        .setFullScreenContentCallback(
                        new com.google.android.gms.ads
                                .FullScreenContentCallback() {
                            @Override
                            public void
                                    onAdDismissedFullScreenContent() {
                                prefs.edit()
                                    .putLong("lastSplashAd",
                                        System.currentTimeMillis())
                                    .apply();
                                launchMain();
                            }
                            @Override
                            public void
                                    onAdFailedToShowFullScreenContent(
                                    com.google.android.gms.ads
                                        .AdError error) {
                                launchMain();
                            }
                        });
                    splashInterstitial.show(AuthActivity.this);
                }
                @Override
                public void onAdFailedToLoad(
                        com.google.android.gms.ads
                        .LoadAdError error) {
                    // Ad failed — just go to main
                    launchMain();
                }
            });
    }

    private void launchMain() {
        Intent intent = new Intent(
            this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void bindViews() {
        layoutLogin          = findViewById(R.id.layoutLogin);
        layoutRegister       = findViewById(R.id.layoutRegister);
        layoutForgotPassword = findViewById(
            R.id.layoutForgotPassword);
        etLoginEmail         = findViewById(R.id.etLoginEmail);
        etLoginPassword      = findViewById(R.id.etLoginPassword);
        etRegEmail           = findViewById(R.id.etRegEmail);
        etRegPassword        = findViewById(R.id.etRegPassword);
        etRegName            = findViewById(R.id.etRegName);
        etForgotEmail        = findViewById(R.id.etForgotEmail);
        btnLogin             = findViewById(R.id.btnLogin);
        btnRegister          = findViewById(R.id.btnRegister);
        btnGuest             = findViewById(R.id.btnGuest);
        btnSendReset         = findViewById(R.id.btnSendReset);
        btnSwitchToRegister  = findViewById(
            R.id.btnSwitchToRegister);
        btnSwitchToLogin     = findViewById(
            R.id.btnSwitchToLogin);
        btnForgotPassword    = findViewById(
            R.id.btnForgotPassword);
        btnBackToLogin       = findViewById(R.id.btnBackToLogin);
        tvError              = findViewById(R.id.tvError);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> {
            String email =
                etLoginEmail.getText().toString().trim();
            String password =
                etLoginPassword.getText().toString().trim();
            if (email.isEmpty() || password.isEmpty()) {
                showError("Please enter email and password");
                return;
            }
            setLoading(true, "login");
            mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    setLoading(false, "login");
                    if (task.isSuccessful()) {
                        navigateFromAuth(
                            mAuth.getCurrentUser());
                    } else {
                        showError("Login failed: "
                            + (task.getException() != null
                                ? task.getException()
                                    .getMessage()
                                : "Unknown error"));
                    }
                });
        });

        btnRegister.setOnClickListener(v -> {
            String name =
                etRegName.getText().toString().trim();
            String email =
                etRegEmail.getText().toString().trim();
            String password =
                etRegPassword.getText().toString().trim();
            if (name.isEmpty() || email.isEmpty()
                    || password.isEmpty()) {
                showError("Please fill in all fields");
                return;
            }
            if (password.length() < 6) {
                showError(
                    "Password must be at least 6 characters");
                return;
            }
            setLoading(true, "register");
            mAuth.createUserWithEmailAndPassword(
                    email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user =
                            mAuth.getCurrentUser();
                        if (user != null) {
                            createUserProfile(
                                user.getUid(), name, email);
                        }
                    } else {
                        setLoading(false, "register");
                        showError("Registration failed: "
                            + (task.getException() != null
                                ? task.getException()
                                    .getMessage()
                                : "Unknown error"));
                    }
                });
        });

        btnGuest.setOnClickListener(v -> {
            setLoading(true, "login");
            mAuth.signInAnonymously()
                .addOnCompleteListener(task -> {
                    setLoading(false, "login");
                    FirebaseUser user =
                        mAuth.getCurrentUser();
                    if (user != null) {
                        navigateFromAuth(user);
                    } else {
                        goTo(MainActivity.class);
                    }
                });
        });

        btnForgotPassword.setOnClickListener(v -> {
            String email =
                etLoginEmail.getText().toString().trim();
            if (!email.isEmpty()) {
                etForgotEmail.setText(email);
            }
            showForgotPasswordMode();
        });

        btnSendReset.setOnClickListener(v -> {
            String email =
                etForgotEmail.getText().toString().trim();
            if (email.isEmpty()) {
                showError("Please enter your email address");
                return;
            }
            setLoading(true, "reset");
            mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    setLoading(false, "reset");
                    if (task.isSuccessful()) {
                        tvError.setTextColor(
                            android.graphics.Color
                                .parseColor("#00D4FF"));
                        showError(
                            "Reset email sent! Check your inbox.");
                        new android.os.Handler(
                            android.os.Looper.getMainLooper())
                            .postDelayed(() -> {
                                showLoginMode();
                                tvError.setTextColor(
                                    android.graphics.Color
                                        .parseColor("#FF4444"));
                            }, 2500);
                    } else {
                        showError("Could not send reset email");
                    }
                });
        });

        btnSwitchToRegister.setOnClickListener(v ->
            showRegisterMode());
        btnSwitchToLogin.setOnClickListener(v ->
            showLoginMode());
        btnBackToLogin.setOnClickListener(v ->
            showLoginMode());
    }

    private void createUserProfile(String uid,
                                    String name,
                                    String email) {
        Map<String, Object> user = new HashMap<>();
        user.put("name", name);
        user.put("email", email);
        user.put("accountType", "free");
        user.put("totalRides", 0);
        user.put("totalKm", 0.0);
        user.put("pinsAdded", 0);
        user.put("createdAt",
            com.google.firebase.Timestamp.now());

        db.collection("users").document(uid)
            .set(user)
            .addOnSuccessListener(unused -> {
                setLoading(false, "register");
                // New user always goes to waiver first
                goTo(WaiverActivity.class);
            })
            .addOnFailureListener(e -> {
                setLoading(false, "register");
                goTo(WaiverActivity.class);
            });
    }

    private void showLoginMode() {
        layoutLogin.setVisibility(View.VISIBLE);
        layoutRegister.setVisibility(View.GONE);
        layoutForgotPassword.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private void showRegisterMode() {
        layoutLogin.setVisibility(View.GONE);
        layoutRegister.setVisibility(View.VISIBLE);
        layoutForgotPassword.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private void showForgotPasswordMode() {
        layoutLogin.setVisibility(View.GONE);
        layoutRegister.setVisibility(View.GONE);
        layoutForgotPassword.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading, String mode) {
        switch (mode) {
            case "login":
                btnLogin.setEnabled(!loading);
                btnGuest.setEnabled(!loading);
                btnForgotPassword.setEnabled(!loading);
                btnLogin.setText(
                    loading ? "Please wait..." : "Sign In");
                break;
            case "register":
                btnRegister.setEnabled(!loading);
                btnRegister.setText(loading
                    ? "Please wait..." : "Create Account");
                break;
            case "reset":
                btnSendReset.setEnabled(!loading);
                btnSendReset.setText(loading
                    ? "Sending..." : "Send Reset Email");
                break;
        }
    }

    public void onGuestClick(android.view.View v) {
        goTo(MainActivity.class);
    }
}
