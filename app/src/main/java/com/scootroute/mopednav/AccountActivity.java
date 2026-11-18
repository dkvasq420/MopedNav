package com.scootroute.mopednav;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AccountActivity extends AppCompatActivity {

    private TextView tvCurrentPlan;
    private CardView btnUpgradePlus;
    private CardView btnUpgradePro;
    private CardView btnRestorePurchase;
    private CardView btnBack;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        bindViews();
        loadCurrentPlan();
        setupListeners();
    }

    private void bindViews() {
        tvCurrentPlan      =
            findViewById(R.id.tvCurrentPlan);
        btnUpgradePlus     =
            findViewById(R.id.btnUpgradePlus);
        btnUpgradePro      =
            findViewById(R.id.btnUpgradePro);
        btnRestorePurchase =
            findViewById(R.id.btnRestorePurchase);
        btnBack            =
            findViewById(R.id.btnAccountBack);
    }

    private void loadCurrentPlan() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.isAnonymous()) {
            tvCurrentPlan.setText("Current Plan: Guest");
            return;
        }

        db.collection("users").document(user.getUid())
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String type =
                        doc.getString("accountType");
                    if (type == null) type = "free";
                    String display =
                        type.substring(0, 1).toUpperCase()
                        + type.substring(1);
                    tvCurrentPlan.setText(
                        "Current Plan: " + display);
                }
            });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnUpgradePlus.setOnClickListener(v ->
            showUpgradeDialog("Plus", "$2.99/month",
                new String[]{
                    "No ads",
                    "Save unlimited routes",
                    "Traffic overlay",
                    "Priority routing",
                    "Route history (90 days)"
                }));

        btnUpgradePro.setOnClickListener(v ->
            showUpgradeDialog("Pro", "$5.99/month",
                new String[]{
                    "Everything in Plus",
                    "Custom routing profiles",
                    "Vehicle garage (unlimited)",
                    "Advanced hazard analytics",
                    "Offline map caching (coming soon)",
                    "Priority support",
                    "Route history (unlimited)"
                }));

        btnRestorePurchase.setOnClickListener(v ->
            Toast.makeText(this,
                "Checking for existing purchases...",
                Toast.LENGTH_SHORT).show());

        // Watch rewarded ad — triggered from Pro upgrade button
        // when user taps upgrade but wants to try first
    }

    private void showUpgradeDialog(String tier,
                                    String price,
                                    String[] features) {
        StringBuilder featureList = new StringBuilder();
        for (String f : features) {
            featureList.append("✓ ").append(f).append("\n");
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Upgrade to ScootRoute " + tier)
            .setMessage(price + " per month\n\n"
                + featureList.toString()
                + "\nIn-app purchases coming soon!")
            .setPositiveButton("Notify Me When Available",
                (d, w) -> Toast.makeText(this,
                    "We'll let you know when "
                    + tier + " is available!",
                    Toast.LENGTH_SHORT).show())
            .setNegativeButton("Maybe Later", null)
            .show();
    }
}
