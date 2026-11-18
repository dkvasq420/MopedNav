package com.scootroute.mopednav;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WaiverActivity extends AppCompatActivity {

    private EditText etSignature;
    private CheckBox cbAccept;
    private Button btnAccept;
    private TextView tvError;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiver);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        etSignature = findViewById(R.id.etSignature);
        cbAccept    = findViewById(R.id.cbAccept);
        btnAccept   = findViewById(R.id.btnAccept);
        tvError     = findViewById(R.id.tvWaiverError);

        btnAccept.setEnabled(false);

        // Enable button only when both conditions met
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(
                CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(
                    CharSequence s, int st, int b, int c) {
                checkReady();
            }
        };
        etSignature.addTextChangedListener(watcher);
        cbAccept.setOnCheckedChangeListener(
            (btn, checked) -> checkReady());

        btnAccept.setOnClickListener(v -> submitWaiver());
    }

    private void checkReady() {
        boolean sigOk = etSignature.getText()
            .toString().trim().length() >= 2;
        boolean cbOk = cbAccept.isChecked();
        btnAccept.setEnabled(sigOk && cbOk);
        btnAccept.setAlpha(sigOk && cbOk ? 1.0f : 0.5f);
    }

    private void submitWaiver() {
        String signature =
            etSignature.getText().toString().trim();

        if (signature.length() < 2) {
            tvError.setText(
                "Please enter your full name as signature");
            tvError.setVisibility(
                android.view.View.VISIBLE);
            return;
        }

        btnAccept.setEnabled(false);
        btnAccept.setText("Saving...");

        // Save waiver acceptance to Firestore
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()).format(new Date());

            Map<String, Object> waiver = new HashMap<>();
            waiver.put("waiverAccepted", true);
            waiver.put("waiverSignature", signature);
            waiver.put("waiverTimestamp", timestamp);
            waiver.put("waiverVersion", "1.0");

            db.collection("users")
                .document(user.getUid())
                .update(waiver)
                .addOnSuccessListener(unused ->
                    proceedToOnboarding())
                .addOnFailureListener(e ->
                    proceedToOnboarding());
        } else {
            proceedToOnboarding();
        }

        // Also save locally so we don't show again
        SharedPreferences prefs = getSharedPreferences(
            "ScootRoute", MODE_PRIVATE);
        prefs.edit()
            .putBoolean("waiverAccepted", true)
            .putString("waiverSignature", signature)
            .apply();
    }

    private void proceedToOnboarding() {
        Intent intent = new Intent(
            this, OnboardingActivity.class);
        startActivity(intent);
        finish();
    }
}
