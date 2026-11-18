package com.scootroute.mopednav;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        findViewById(R.id.btnHelpBack)
            .setOnClickListener(v -> finish());

        // Credits / About button
        android.view.View creditsBtn =
            findViewById(R.id.btnCredits);
        if (creditsBtn != null) {
            creditsBtn.setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog
                        .Builder(this)
                    .setTitle("About ScootRoute")
                    .setMessage(
                        "ScootRoute v1.1\n"
                        + "Moped Navigation for Oahu, Hawaii\n\n"
                        + "━━━━━━━━━━━━━━━━━━━━━\n\n"
                        + "Created by:\n"
                        + "David — Founder & Visionary\n"
                        + "Born from a real need in the Oahu "
                        + "moped riding community\n\n"
                        + "Built with:\n"
                        + "Claude (Anthropic) — AI Development Partner\n"
                        + "\"No matter what the challenge, "
                        + "Claude always has a knowledgeable "
                        + "and precise fix.\" — David\n\n"
                        + "━━━━━━━━━━━━━━━━━━━━━\n\n"
                        + "Routing: OpenRouteService / HeiGIT\n"
                        + "Maps: OpenStreetMap contributors\n"
                        + "Authentication: Firebase (Google)\n\n"
                        + "© 2025 ScootRoute\n"
                        + "All rights reserved\n\n"
                        + "Made with ❤️ in Honolulu, Hawaii 🌺")
                    .setPositiveButton("Mahalo!", null)
                    .show());
        }

        findViewById(R.id.btnContactSupport)
            .setOnClickListener(v -> {
                Intent email = new Intent(
                    Intent.ACTION_SENDTO);
                email.setData(Uri.parse(
                    "mailto:support@scootroute.app"
                    + "?subject=ScootRoute Support"));
                try {
                    startActivity(email);
                } catch (Exception e) {
                    android.widget.Toast.makeText(this,
                        "No email app found",
                        android.widget.Toast.LENGTH_SHORT)
                        .show();
                }
            });

        setupFaqItems();
    }

    private void setupFaqItems() {
        String[][] faqs = {
            {
                "How does ScootRoute avoid freeways?",
                "ScootRoute uses a special routing profile designed for cyclists and mopeds that physically cannot route onto freeways or motorways. All routes are validated before being shown to you."
            },
            {
                "Can I use the app without an account?",
                "Yes! You can use ScootRoute as a guest. However, creating a free account lets you save routes, earn stats, and add hazard pins for the community."
            },
            {
                "Why might my route include bike paths?",
                "Our routing engine uses a cycling profile to avoid freeways. Some routes may include shared paths. You can toggle 'Exclude bike-only paths' in your settings."
            },
            {
                "How do I report a hazard or point of interest?",
                "Tap the orange pin button on the map screen while navigating. You can select a hazard type, add a comment, and attach a photo."
            },
            {
                "How accurate is the estimated travel time?",
                "ScootRoute calculates time based on typical moped speeds of 35-40 mph on surface streets, including estimated stop time at intersections. Actual time may vary with traffic."
            },
            {
                "What is ScootRoute Pro?",
                "Pro users get advanced routing profiles, no ads, priority support, traffic data overlay, and custom vehicle profiles. Upgrade in the Account section."
            },
            {
                "How do I get the best routes?",
                "Make sure location permissions are enabled for precise GPS. Enter your destination clearly — try including 'Honolulu' or 'Oahu' if results seem off."
            },
            {
                "Does ScootRoute work offline?",
                "Currently ScootRoute requires an internet connection for routing and map tiles. Offline support is planned for a future update."
            },
            {
                "How do I change my vehicle type?",
                "Go to Settings and tap 'Reset Preferences' to redo the setup screen, or go to Dashboard and edit your profile."
            },
            {
                "My route looks wrong — what do I do?",
                "Try tapping Recalculate on the map screen. If the issue persists, please use the Contact Support button above to report it."
            }
        };

        LinearLayout faqContainer =
            findViewById(R.id.llFaqContainer);

        for (String[] faq : faqs) {
            View item = getLayoutInflater().inflate(
                R.layout.item_faq,
                faqContainer, false);

            TextView tvQ = item.findViewById(R.id.tvFaqQ);
            TextView tvA = item.findViewById(R.id.tvFaqA);
            CardView card =
                item.findViewById(R.id.cardFaqItem);

            tvQ.setText(faq[0]);
            tvA.setText(faq[1]);

            // Toggle answer on tap
            card.setOnClickListener(v -> {
                if (tvA.getVisibility() == View.VISIBLE) {
                    tvA.setVisibility(View.GONE);
                } else {
                    tvA.setVisibility(View.VISIBLE);
                }
            });

            faqContainer.addView(item);
        }
    }
}
