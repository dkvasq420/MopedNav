package com.scootroute.mopednav;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * AdManager — handles all AdMob ads for ScootRoute
 *
 * Ad strategy:
 * - Free users: banner always visible + interstitial
 *   on first app open each day
 * - Plus/Pro users: NO ADS at all
 *
 * Ad Unit IDs:
 * App:          ca-app-pub-3254889504013066~8633172639
 * Banner:       ca-app-pub-3254889504013066/7320090965
 * Interstitial: ca-app-pub-3254889504013066/7462390332
 * Rewarded:     ca-app-pub-3254889504013066/6149308669
 */
public class AdManager {

    private static final String TAG = "AdManager";

    // ════════════════════════════════════
    // TEST IDs — shows real test ads during dev
    // Switch to LIVE IDs before Play Store upload
    // ════════════════════════════════════════
    public static final String APP_ID =
        "ca-app-pub-3254889504013066~8633172639";

    // Google's official test ad unit IDs
    // These always return a real visible test ad
    public static final String BANNER_ID =
        "ca-app-pub-3940256099942544/6300978111";
    public static final String INTERSTITIAL_ID =
        "ca-app-pub-3940256099942544/1033173712";
    public static final String REWARDED_ID =
        "ca-app-pub-3940256099942544/5224354917";

    // ── LIVE IDs (uncomment before publishing) ──
    // public static final String BANNER_ID =
    //     "ca-app-pub-3254889504013066/7320090965";
    // public static final String INTERSTITIAL_ID =
    //     "ca-app-pub-3254889504013066/7462390332";
    // public static final String REWARDED_ID =
    //     "ca-app-pub-3254889504013066/6149308669";

    private final Activity activity;
    private AdView bannerAdView;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private boolean adsEnabled = true;

    public AdManager(Activity activity) {
        this.activity = activity;
    }

    // ════════════════════════════════════════
    // INITIALIZE
    // Call this in MainActivity.onCreate()
    // ════════════════════════════════════════

    public void initialize(String accountType) {
        // Pro and Plus users never see ads
        if ("pro".equals(accountType)
                || "plus".equals(accountType)) {
            adsEnabled = false;
            Log.d(TAG, "Ads disabled for " + accountType);
            return;
        }

        adsEnabled = true;

        // Configure test device so ads always fill
        com.google.android.gms.ads.RequestConfiguration config =
            new com.google.android.gms.ads.RequestConfiguration
                .Builder()
                .setTestDeviceIds(java.util.Arrays.asList(
                    com.google.android.gms.ads.AdRequest
                        .DEVICE_ID_EMULATOR,
                    "DEVICE_TEST_ID" // replace with your device ID from logcat
                ))
                .build();
        MobileAds.setRequestConfiguration(config);

        MobileAds.initialize(activity, initStatus ->
            Log.d(TAG, "AdMob initialized"));

        // Preload interstitial and rewarded
        loadInterstitial();
        loadRewarded();
    }

    // ════════════════════════════════════════
    // BANNER AD
    // ════════════════════════════════════════

    public void loadBanner(LinearLayout container) {
        if (!adsEnabled || container == null) return;

        bannerAdView = new AdView(activity);
        bannerAdView.setAdSize(AdSize.BANNER);
        bannerAdView.setAdUnitId(BANNER_ID);

        bannerAdView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                container.setVisibility(View.VISIBLE);
                Log.d(TAG, "Banner loaded");
            }
            @Override
            public void onAdFailedToLoad(
                    LoadAdError error) {
                container.setVisibility(View.GONE);
                Log.d(TAG, "Banner failed: "
                    + error.getMessage());
            }
        });

        container.removeAllViews();
        container.addView(bannerAdView);
        bannerAdView.loadAd(new AdRequest.Builder().build());
    }

    public void destroyBanner() {
        if (bannerAdView != null) {
            bannerAdView.destroy();
            bannerAdView = null;
        }
    }

    public void pauseBanner() {
        if (bannerAdView != null) bannerAdView.pause();
    }

    public void resumeBanner() {
        if (bannerAdView != null) bannerAdView.resume();
    }

    // ════════════════════════════════════════
    // INTERSTITIAL AD
    // Shown on first app open each day
    // ════════════════════════════════════════

    public void loadInterstitial() {
        if (!adsEnabled) return;

        InterstitialAd.load(activity, INTERSTITIAL_ID,
            new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(
                        InterstitialAd ad) {
                    interstitialAd = ad;
                    Log.d(TAG, "Interstitial loaded");
                }
                @Override
                public void onAdFailedToLoad(
                        LoadAdError error) {
                    interstitialAd = null;
                    Log.d(TAG, "Interstitial failed: "
                        + error.getMessage());
                }
            });
    }

    // Show interstitial — only once per day
    public void showInterstitialIfDue(
            SharedPreferences prefs) {
        if (!adsEnabled || interstitialAd == null) return;

        long lastShown =
            prefs.getLong("lastInterstitialShown", 0);
        long now = System.currentTimeMillis();
        long oneDayMs = 24 * 60 * 60 * 1000L;

        if (now - lastShown < oneDayMs) {
            Log.d(TAG, "Interstitial already shown today");
            return;
        }

        interstitialAd.setFullScreenContentCallback(
            new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    interstitialAd = null;
                    loadInterstitial(); // Preload next
                }
            });

        prefs.edit()
            .putLong("lastInterstitialShown", now)
            .apply();
        interstitialAd.show(activity);
    }

    // Show interstitial immediately (no time check)
    public void showInterstitial() {
        if (!adsEnabled || interstitialAd == null) return;

        interstitialAd.setFullScreenContentCallback(
            new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    interstitialAd = null;
                    loadInterstitial();
                }
            });
        interstitialAd.show(activity);
    }

    // ════════════════════════════════════════
    // REWARDED AD
    // Show to unlock Pro features temporarily
    // ════════════════════════════════════════

    public interface RewardCallback {
        void onRewarded();
        void onRewardFailed();
    }

    public void loadRewarded() {
        if (!adsEnabled) return;

        RewardedAd.load(activity, REWARDED_ID,
            new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override
                public void onAdLoaded(RewardedAd ad) {
                    rewardedAd = ad;
                    Log.d(TAG, "Rewarded loaded");
                }
                @Override
                public void onAdFailedToLoad(
                        LoadAdError error) {
                    rewardedAd = null;
                    Log.d(TAG, "Rewarded failed: "
                        + error.getMessage());
                }
            });
    }

    public void showRewarded(RewardCallback callback) {
        if (!adsEnabled) {
            if (callback != null) callback.onRewarded();
            return;
        }
        if (rewardedAd == null) {
            if (callback != null) callback.onRewardFailed();
            return;
        }

        rewardedAd.setFullScreenContentCallback(
            new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    rewardedAd = null;
                    loadRewarded();
                }
            });

        rewardedAd.show(activity, rewardItem -> {
            Log.d(TAG, "User earned reward: "
                + rewardItem.getAmount()
                + " " + rewardItem.getType());
            if (callback != null) callback.onRewarded();
        });
    }

    public boolean isAdsEnabled() {
        return adsEnabled;
    }
}
