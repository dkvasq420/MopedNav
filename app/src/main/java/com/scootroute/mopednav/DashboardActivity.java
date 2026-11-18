package com.scootroute.mopednav;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvUserName;
    private TextView tvAccountType;
    private TextView tvTotalRides;
    private TextView tvTotalKm;
    private TextView tvPinsAdded;
    private TextView tvMemberSince;
    private LinearLayout llRouteHistory;
    private CardView btnUpgrade;
    private CardView btnSettings;
    private CardView btnHelp;
    private CardView btnBack;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        bindViews();
        setupListeners();
        loadUserData();
        loadRouteHistory();
    }

    private void bindViews() {
        tvUserName     = findViewById(R.id.tvDashUserName);
        tvAccountType  = findViewById(R.id.tvDashAccountType);
        tvTotalRides   = findViewById(R.id.tvDashTotalRides);
        tvTotalKm      = findViewById(R.id.tvDashTotalKm);
        tvPinsAdded    = findViewById(R.id.tvDashPinsAdded);
        tvMemberSince  = findViewById(R.id.tvDashMemberSince);
        llRouteHistory = findViewById(R.id.llRouteHistory);
        btnUpgrade     = findViewById(R.id.btnDashUpgrade);
        btnSettings    = findViewById(R.id.btnDashSettings);
        btnHelp        = findViewById(R.id.btnDashHelp);
        btnBack        = findViewById(R.id.btnDashBack);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnSettings.setOnClickListener(v ->
            startActivity(new Intent(
                this, SettingsActivity.class)));

        btnHelp.setOnClickListener(v ->
            startActivity(new Intent(
                this, HelpActivity.class)));

        btnUpgrade.setOnClickListener(v ->
            startActivity(new Intent(
                this, AccountActivity.class)));
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.isAnonymous()) {
            tvUserName.setText("Guest Rider");
            tvAccountType.setText("GUEST");
            tvTotalRides.setText("0");
            tvTotalKm.setText("0.0 km");
            tvPinsAdded.setText("0");
            tvMemberSince.setText("--");
            return;
        }

        db.collection("users").document(user.getUid())
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String name =
                        doc.getString("name");
                    String type =
                        doc.getString("accountType");
                    Long rides =
                        doc.getLong("totalRides");
                    Double km =
                        doc.getDouble("totalKm");
                    Long pins =
                        doc.getLong("pinsAdded");

                    tvUserName.setText(
                        name != null ? name : "Rider");
                    tvAccountType.setText(
                        type != null
                            ? type.toUpperCase() : "FREE");

                    // Color badge by account type
                    int badgeColor;
                    switch (type != null ? type : "free") {
                        case "pro":
                            badgeColor = 0xFF00D4FF; break;
                        case "plus":
                            badgeColor = 0xFFFF9500; break;
                        default:
                            badgeColor = 0xFF888888;
                    }
                    tvAccountType.setBackgroundColor(
                        badgeColor);

                    tvTotalRides.setText(
                        String.valueOf(
                            rides != null ? rides : 0));
                    tvTotalKm.setText(
                        String.format("%.1f km",
                            km != null ? km : 0.0));
                    tvPinsAdded.setText(
                        String.valueOf(
                            pins != null ? pins : 0));

                    com.google.firebase.Timestamp ts =
                        doc.getTimestamp("createdAt");
                    if (ts != null) {
                        java.text.SimpleDateFormat sdf =
                            new java.text.SimpleDateFormat(
                                "MMM yyyy",
                                java.util.Locale.getDefault());
                        tvMemberSince.setText(
                            sdf.format(ts.toDate()));
                    }
                }
            });
    }

    private void loadRouteHistory() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.isAnonymous()) {
            addEmptyHistoryMessage();
            return;
        }

        db.collection("users")
            .document(user.getUid())
            .collection("routes")
            .orderBy("timestamp",
                com.google.firebase.firestore
                    .Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener(query -> {
                llRouteHistory.removeAllViews();
                if (query.isEmpty()) {
                    addEmptyHistoryMessage();
                    return;
                }
                for (QueryDocumentSnapshot doc
                        : query) {
                    String dest =
                        doc.getString("destination");
                    Double km =
                        doc.getDouble("distanceKm");
                    Integer mins =
                        doc.getLong("durationMinutes") != null
                        ? doc.getLong("durationMinutes")
                            .intValue()
                        : null;
                    addRouteHistoryItem(
                        dest != null ? dest : "Unknown",
                        km != null ? km : 0.0,
                        mins != null ? mins : 0);
                }
            })
            .addOnFailureListener(e ->
                addEmptyHistoryMessage());
    }

    private void addRouteHistoryItem(String dest,
                                      double km,
                                      int mins) {
        View item = getLayoutInflater().inflate(
            R.layout.item_route_history,
            llRouteHistory, false);
        TextView tvDest =
            item.findViewById(R.id.tvHistoryDest);
        TextView tvDetails =
            item.findViewById(R.id.tvHistoryDetails);
        tvDest.setText(dest);
        tvDetails.setText(
            String.format("%.1f km · %d min", km, mins));
        llRouteHistory.addView(item);
    }

    private void addEmptyHistoryMessage() {
        TextView tv = new TextView(this);
        tv.setText("No routes yet — start riding! 🛵");
        tv.setTextColor(0xFF888888);
        tv.setTextSize(14f);
        tv.setPadding(0, 16, 0, 16);
        tv.setGravity(android.view.Gravity.CENTER);
        llRouteHistory.addView(tv);
    }
}
