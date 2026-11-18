package com.scootroute.mopednav;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class HazardReportActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 1001;

    private Spinner spinnerHazardType;
    private EditText etHazardComment;
    private ImageView ivHazardPhoto;
    private CardView btnAddPhoto;
    private Button btnSubmitReport;
    private CardView btnBack;
    private TextView tvLocation;

    private double reportLat = 0;
    private double reportLng = 0;
    private Uri selectedImageUri = null;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hazard_report);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Get location passed from MainActivity
        reportLat = getIntent().getDoubleExtra("lat", 0);
        reportLng = getIntent().getDoubleExtra("lng", 0);

        bindViews();
        setupSpinner();
        setupListeners();
        updateLocationDisplay();
    }

    private void bindViews() {
        spinnerHazardType =
            findViewById(R.id.spinnerHazardType);
        etHazardComment   =
            findViewById(R.id.etHazardComment);
        ivHazardPhoto     =
            findViewById(R.id.ivHazardPhoto);
        btnAddPhoto       =
            findViewById(R.id.btnAddPhoto);
        btnSubmitReport   =
            findViewById(R.id.btnSubmitReport);
        btnBack           =
            findViewById(R.id.btnHazardBack);
        tvLocation        =
            findViewById(R.id.tvHazardLocation);
    }

    private void setupSpinner() {
        ArrayAdapter<String> adapter =
            new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{
                    "Select hazard type...",
                    "🕳 Pothole",
                    "🚧 Road Construction",
                    "💧 Flooding / Standing Water",
                    "🪨 Debris on Road",
                    "⚠️ Dangerous Intersection",
                    "🚦 Broken Traffic Signal",
                    "🦔 Animal on Road",
                    "🛣 Poor Road Condition",
                    "🚫 Road Closed",
                    "💡 Poor Lighting",
                    "⛽ Gas Station",
                    "🔧 Mechanic / Repair Shop",
                    "📍 Point of Interest",
                    "Other"
                });
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        spinnerHazardType.setAdapter(adapter);
    }

    private void updateLocationDisplay() {
        if (reportLat != 0 && reportLng != 0) {
            tvLocation.setText(String.format(
                "📍 %.4f, %.4f", reportLat, reportLng));
        } else {
            tvLocation.setText("📍 Current location");
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnAddPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE);
        });

        btnSubmitReport.setOnClickListener(v ->
            submitReport());
    }

    @Override
    protected void onActivityResult(int requestCode,
            int resultCode, Intent data) {
        super.onActivityResult(
            requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE
                && resultCode == RESULT_OK
                && data != null) {
            selectedImageUri = data.getData();
            ivHazardPhoto.setImageURI(selectedImageUri);
            ivHazardPhoto.setVisibility(
                android.view.View.VISIBLE);
        }
    }

    private void submitReport() {
        if (spinnerHazardType
                .getSelectedItemPosition() == 0) {
            Toast.makeText(this,
                "Please select a hazard type",
                Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmitReport.setEnabled(false);
        btnSubmitReport.setText("Submitting...");

        String hazardType =
            spinnerHazardType.getSelectedItem().toString();
        String comment =
            etHazardComment.getText().toString().trim();

        FirebaseUser user = mAuth.getCurrentUser();
        String userId = user != null
            ? user.getUid() : "anonymous";

        Map<String, Object> report = new HashMap<>();
        report.put("hazardType", hazardType);
        report.put("comment", comment);
        report.put("lat", reportLat);
        report.put("lng", reportLng);
        report.put("userId", userId);
        report.put("timestamp",
            com.google.firebase.Timestamp.now());
        report.put("verified", false);
        report.put("upvotes", 0);

        db.collection("hazards")
            .add(report)
            .addOnSuccessListener(ref -> {
                // Also add to user's pins count
                if (user != null && !user.isAnonymous()) {
                    db.collection("users")
                        .document(user.getUid())
                        .update("pinsAdded",
                            com.google.firebase.firestore
                                .FieldValue.increment(1));
                }
                Toast.makeText(this,
                    "Report submitted! Thank you 🙏",
                    Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                btnSubmitReport.setEnabled(true);
                btnSubmitReport.setText("Submit Report");
                Toast.makeText(this,
                    "Failed to submit. Please try again.",
                    Toast.LENGTH_SHORT).show();
            });
    }
}
