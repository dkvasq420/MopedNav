package com.scootroute.mopednav;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class MopedRentalActivity extends AppCompatActivity {

    private LinearLayout llRentalList;
    private TextView tvRentalCount;
    private CardView btnBack;
    private FirebaseFirestore db;

    // Hardcoded Oahu rental shops to start
    // Later these can come from Firestore as partners sign up
    private static final String[][] RENTAL_SHOPS = {
        {
            "Big Kahuna Rentals",
            "Waikiki",
            "Mopeds from $35/day · Scooters from $55/day",
            "808-555-0101",
            "https://maps.google.com/?q=Waikiki+Honolulu",
            "⭐ 4.8",
            "Open 8am–6pm daily"
        },
        {
            "Aloha Scooters",
            "Kailua",
            "Mopeds from $40/day · Electric scooters available",
            "808-555-0102",
            "https://maps.google.com/?q=Kailua+Hawaii",
            "⭐ 4.6",
            "Open 9am–5pm daily"
        },
        {
            "Island Moped Co.",
            "Honolulu Downtown",
            "Mopeds from $30/day · Helmets included",
            "808-555-0103",
            "https://maps.google.com/?q=Downtown+Honolulu",
            "⭐ 4.7",
            "Open 8am–7pm daily"
        },
        {
            "North Shore Rides",
            "Haleiwa",
            "Mopeds from $45/day · Beach cruiser scooters",
            "808-555-0104",
            "https://maps.google.com/?q=Haleiwa+Hawaii",
            "⭐ 4.9",
            "Open 8am–5pm daily"
        },
        {
            "Kapolei Scooter Shop",
            "Kapolei / Ewa Beach",
            "Mopeds from $35/day · West side coverage",
            "808-555-0105",
            "https://maps.google.com/?q=Kapolei+Hawaii",
            "⭐ 4.5",
            "Open 9am–6pm daily"
        },
        {
            "Pearl City Mopeds",
            "Pearl City",
            "Mopeds from $32/day · Military discount available",
            "808-555-0106",
            "https://maps.google.com/?q=Pearl+City+Hawaii",
            "⭐ 4.6",
            "Open 8am–6pm daily"
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moped_rental);

        db = FirebaseFirestore.getInstance();

        llRentalList = findViewById(R.id.llRentalList);
        tvRentalCount = findViewById(R.id.tvRentalCount);
        btnBack = findViewById(R.id.btnRentalBack);

        btnBack.setOnClickListener(v -> finish());

        tvRentalCount.setText(
            RENTAL_SHOPS.length + " shops near you");

        populateRentalShops();
    }

    private void populateRentalShops() {
        llRentalList.removeAllViews();

        for (String[] shop : RENTAL_SHOPS) {
            View card = getLayoutInflater().inflate(
                R.layout.item_rental_shop,
                llRentalList, false);

            TextView tvName =
                card.findViewById(R.id.tvShopName);
            TextView tvArea =
                card.findViewById(R.id.tvShopArea);
            TextView tvPricing =
                card.findViewById(R.id.tvShopPricing);
            TextView tvHours =
                card.findViewById(R.id.tvShopHours);
            TextView tvRating =
                card.findViewById(R.id.tvShopRating);
            CardView btnCall =
                card.findViewById(R.id.btnShopCall);
            CardView btnMap =
                card.findViewById(R.id.btnShopMap);
            CardView btnBook =
                card.findViewById(R.id.btnShopBook);

            tvName.setText(shop[0]);
            tvArea.setText("📍 " + shop[1]);
            tvPricing.setText(shop[2]);
            tvHours.setText(shop[6]);
            tvRating.setText(shop[5]);

            final String phone = shop[3];
            final String mapUrl = shop[4];
            final String name = shop[0];

            btnCall.setOnClickListener(v -> {
                Intent call = new Intent(
                    Intent.ACTION_DIAL,
                    Uri.parse("tel:" + phone));
                startActivity(call);
            });

            btnMap.setOnClickListener(v -> {
                Intent map = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(mapUrl));
                startActivity(map);
            });

            btnBook.setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog
                        .Builder(this)
                    .setTitle("Book with " + name)
                    .setMessage(
                        "Online booking coming soon!\n\n"
                        + "For now, call us at:\n"
                        + phone
                        + "\n\nMention ScootRoute for "
                        + "10% off your first rental! 🛵")
                    .setPositiveButton("Call Now",
                        (d, w) -> {
                            Intent call = new Intent(
                                Intent.ACTION_DIAL,
                                Uri.parse("tel:" + phone));
                            startActivity(call);
                        })
                    .setNegativeButton("Maybe Later", null)
                    .show());

            llRentalList.addView(card);
        }

        // Partner signup prompt at bottom
        View partnerCard = getLayoutInflater().inflate(
            R.layout.item_rental_partner,
            llRentalList, false);
        CardView btnPartner =
            partnerCard.findViewById(R.id.btnBecomePartner);
        btnPartner.setOnClickListener(v ->
            new androidx.appcompat.app.AlertDialog
                    .Builder(this)
                .setTitle("Partner with ScootRoute")
                .setMessage(
                    "Own a moped rental shop on Oahu?\n\n"
                    + "List your shop on ScootRoute and "
                    + "reach thousands of riders!\n\n"
                    + "• Free to list\n"
                    + "• Pay only on bookings\n"
                    + "• Track rentals in real time\n\n"
                    + "Contact us to get started!")
                .setPositiveButton("Contact Us",
                    (d, w) -> {
                        Intent email = new Intent(
                            Intent.ACTION_SENDTO);
                        email.setData(Uri.parse(
                            "mailto:partners@scootroute.app"
                            + "?subject=Partner Application"));
                        try {
                            startActivity(email);
                        } catch (Exception ignored) {}
                    })
                .setNegativeButton("Maybe Later", null)
                .show());
        llRentalList.addView(partnerCard);
    }
}
