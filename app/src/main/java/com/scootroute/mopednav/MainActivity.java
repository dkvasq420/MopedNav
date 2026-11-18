package com.scootroute.mopednav;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements NavigationService.NavigationCallback {

    // ── UI ──
    private MapView mapView;
    private AutoCompleteTextView etDestination;
    private TextView tvOrigin;
    private CardView cardInstruction;
    private CardView btnRecenter;
    private CardView btnLayers;
    private CardView btnAddTag;
    private CardView btnGasFood;
    private CardView btnShareEta;
    private Button btnGetDirections;
    private Button btnStartNavigation;
    private Button btnStopNavigation;
    private TextView tvInstruction;
    private TextView tvDistance;
    private TextView tvTurnArrow;
    private TextView tvEta;
    private TextView tvDistanceToTurn;
    private LinearLayout llRouteSteps;
    private LinearLayout topOverlay;
    private View dragHandle;
    private ScrollView stepsScrollView;
    private BottomSheetBehavior bottomSheetBehavior;

    // ── Location ──
    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    // Default center: Honolulu, Oahu, Hawaii
    private double currentLat = 21.3069;
    private double currentLng = -157.8583;
    private float currentBearing = 0f;
    private float currentSpeedMs = 0f;
    private static final int LOCATION_PERMISSION_REQ = 1;

    // ── Map overlays ──
    private Marker userMarker;
    private Marker destMarker;
    private Polyline routeLine;

    // ── Navigation state ──
    private boolean isNavigating = false;
    private boolean navActive = false;  // true when user tapped Start
    private List<RouteStep> currentSteps = new ArrayList<>();
    private int currentStepIndex = 0;
    private List<GeoPoint> savedRoutePoints = null;
    private int savedDurationMinutes = 0;
    private double savedDestLat = 0;
    private double savedDestLng = 0;

    // ── Navigation Service ──
    private NavigationService navService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection =
        new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name,
                IBinder binder) {
            NavigationService.LocalBinder lb =
                (NavigationService.LocalBinder) binder;
            navService = lb.getService();
            serviceBound = true;
            // Start navigation immediately on connect
            navService.setRoutePoints(savedRoutePoints);
            navService.startNavigation(
                currentSteps, MainActivity.this);
        }
        @Override
        public void onServiceDisconnected(ComponentName n) {
            serviceBound = false;
            navService = null;
        }
    };

    // ── Ads ──
    private AdManager adManager;
    private LinearLayout bannerAdContainer;
    private View cardOrigin;
    private View layoutSearchInputs;
    private View routeBar;
    private View btnEditRoute;
    private TextView tvRouteSummary;
    private boolean editingRoute = false;

    // ── Bottom nav ──
    private LinearLayout navMap;
    private LinearLayout navDashboard;
    private LinearLayout navHazard;
    private LinearLayout navSettings;
    private LinearLayout navAccount;

    // ── Map layer state ──
    private boolean isSatelliteView = false;
    private boolean showLandmarks = true;

    // ── Long press context menu ──
    private LinearLayout layoutMapContextMenu;
    private TextView menuItemHazard;
    private TextView menuItemPin;
    private TextView menuItemRouteTo;
    private TextView menuItemDismiss;
    private double longPressLat = 0;
    private double longPressLng = 0;

    // ── Autocomplete ──
    private ArrayAdapter<String> suggestionAdapter;
    private List<double[]> suggestionCoords = new ArrayList<>();
    private Handler searchHandler =
        new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // ════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance()
            .setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);
        bindViews();
        initAds();
        setupBottomSheet();
        setupMap();
        setupAutocomplete();
        setupListeners();
        requestLocationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (adManager != null) adManager.resumeBanner();
        if (isNavigating && savedRoutePoints != null
                && !savedRoutePoints.isEmpty()) {
            restoreRoute();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (adManager != null) adManager.pauseBanner();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedClient != null && locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
        }
        if (serviceBound) {
            unbindService(serviceConnection);
        }
        if (adManager != null) adManager.destroyBanner();
    }

    // ════════════════════════════════════════
    // BIND VIEWS
    // ════════════════════════════════════════

    private void bindViews() {
        mapView             = findViewById(R.id.mapView);
        etDestination       = findViewById(R.id.etDestination);
        tvOrigin            = findViewById(R.id.tvOrigin);
        cardInstruction     = findViewById(R.id.cardInstruction);
        btnRecenter         = findViewById(R.id.btnRecenter);
        btnLayers           = findViewById(R.id.btnLayers);
        // btnAddTag removed from layout
        btnGasFood          = findViewById(R.id.btnGasFood);
        btnShareEta         = findViewById(R.id.btnShareEta);
        btnGetDirections    = findViewById(R.id.btnGetDirections);
        btnStartNavigation  = findViewById(
            R.id.btnStartNavigation);
        btnStopNavigation   = findViewById(
            R.id.btnStopNavigation);
        tvInstruction       = findViewById(R.id.tvInstruction);
        tvDistance          = findViewById(R.id.tvDistance);
        tvTurnArrow         = findViewById(R.id.tvTurnArrow);
        tvEta               = findViewById(R.id.tvEta);
        tvDistanceToTurn    = findViewById(
            R.id.tvDistanceToTurn);
        llRouteSteps        = findViewById(R.id.llRouteSteps);
        topOverlay          = findViewById(R.id.topOverlay);
        dragHandle          = findViewById(R.id.dragHandle);
        stepsScrollView     = findViewById(
            R.id.stepsScrollView);

        etDestination.setHintTextColor(
            Color.parseColor("#66FFFFFF"));
        etDestination.setTextColor(Color.WHITE);

        cardOrigin = findViewById(R.id.cardOrigin);
        layoutSearchInputs = findViewById(R.id.layoutSearchInputs);
        routeBar = findViewById(R.id.routeBar);
        btnEditRoute = findViewById(R.id.btnEditRoute);
        tvRouteSummary = findViewById(R.id.tvRouteSummary);

        // Ads
        bannerAdContainer =
            findViewById(R.id.bannerAdContainer);

        // Bottom nav
        navMap        = findViewById(R.id.navMap);
        navDashboard  = findViewById(R.id.navDashboard);
        navHazard     = findViewById(R.id.navHazard);
        navSettings   = findViewById(R.id.navSettings);
        navAccount    = findViewById(R.id.navAccount);

        // Long press context menu
        layoutMapContextMenu =
            findViewById(R.id.layoutMapContextMenu);
        menuItemHazard  = findViewById(R.id.menuItemHazard);
        menuItemPin     = findViewById(R.id.menuItemPin);
        menuItemRouteTo = findViewById(R.id.menuItemRouteTo);
        menuItemDismiss = findViewById(R.id.menuItemDismiss);

        // Initially hide nav buttons
        btnStartNavigation.setVisibility(View.GONE);
        btnStopNavigation.setVisibility(View.GONE);
    }

    // ════════════════════════════════════════
    // BOTTOM SHEET — drag handle only
    // ════════════════════════════════════════

    private void setupBottomSheet() {
        View container =
            findViewById(R.id.bottomSheetContainer);
        bottomSheetBehavior =
            BottomSheetBehavior.from(container);
        bottomSheetBehavior.setFitToContents(false);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.setPeekHeight(dpToPx(80));
        bottomSheetBehavior.setHalfExpandedRatio(0.42f);
        bottomSheetBehavior.setState(
            BottomSheetBehavior.STATE_COLLAPSED);
        bottomSheetBehavior.setDraggable(false);

        if (dragHandle != null) {
            dragHandle.setOnTouchListener(
                new View.OnTouchListener() {
                float startY = 0;
                int startState = 0;

                @Override
                public boolean onTouch(View v,
                        MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startY = event.getRawY();
                            startState =
                                bottomSheetBehavior.getState();
                            return true;
                        case MotionEvent.ACTION_UP:
                            float delta =
                                event.getRawY() - startY;
                            if (delta < -50) {
                                if (startState ==
                                    BottomSheetBehavior
                                        .STATE_COLLAPSED) {
                                    bottomSheetBehavior
                                        .setState(
                                        BottomSheetBehavior
                                        .STATE_HALF_EXPANDED);
                                } else {
                                    bottomSheetBehavior
                                        .setState(
                                        BottomSheetBehavior
                                        .STATE_EXPANDED);
                                }
                            } else if (delta > 50) {
                                if (startState ==
                                    BottomSheetBehavior
                                        .STATE_EXPANDED) {
                                    bottomSheetBehavior
                                        .setState(
                                        BottomSheetBehavior
                                        .STATE_HALF_EXPANDED);
                                } else {
                                    bottomSheetBehavior
                                        .setState(
                                        BottomSheetBehavior
                                        .STATE_COLLAPSED);
                                }
                            }
                            return true;
                    }
                    return false;
                }
            });
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp *
            getResources().getDisplayMetrics().density);
    }

    // ════════════════════════════════════════
    // MAP
    // ════════════════════════════════════════

    // ════════════════════════════════════════
    // ADS — load based on account type
    // ════════════════════════════════════════

    private void initAds() {
        android.content.SharedPreferences prefs =
            getSharedPreferences("ScootRoute", MODE_PRIVATE);
        String accountType =
            prefs.getString("accountType", "free");

        adManager = new AdManager(this);
        adManager.initialize(accountType);

        if (adManager.isAdsEnabled()) {
            // Load banner
            adManager.loadBanner(bannerAdContainer);

            // Show interstitial once per day
            adManager.showInterstitialIfDue(prefs);
        }
    }

    private void setupMap() {
        // Use reliable MAPNIK while we sort tile provider
        // MAPNIK is stable and always loads correctly
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        // Start centered on Honolulu, Oahu
        mapView.getController().setZoom(13.0);
        mapView.getController().setCenter(
            new GeoPoint(21.3069, -157.8583));
    }

    // ════════════════════════════════════════
    // NAVIGATION MAP MODE
    // Switches to low perspective, follows user
    // ════════════════════════════════════════

    private void enterNavigationMapMode() {
        // Zoom in closer for navigation
        mapView.getController().setZoom(18.0);
        mapView.getController().animateTo(
            new GeoPoint(currentLat, currentLng));
        // Note: OSMDroid doesn't support 3D tilt natively
        // but we can simulate with zoom + bearing follow
        mapView.setMapOrientation(-currentBearing);
    }

    private void exitNavigationMapMode() {
        mapView.setMapOrientation(0f);
        if (savedRoutePoints != null
                && !savedRoutePoints.isEmpty()) {
            mapView.zoomToBoundingBox(
                routeLine.getBounds(), true, 120);
        }
    }

    // ════════════════════════════════════════
    // START / STOP NAVIGATION
    // ════════════════════════════════════════

    private void startActiveNavigation() {
        if (currentSteps.isEmpty()) return;
        navActive = true;

        // Step 0 is "Head north on X"; the first real maneuver is 1
        currentStepIndex = currentSteps.size() > 1 ? 1 : 0;
        showStep(currentStepIndex);
        populateSteps(currentSteps);

        // Animate top search cards sliding up and out
        topOverlay.animate()
            .translationY(-topOverlay.getHeight() - 50)
            .alpha(0f)
            .setDuration(400)
            .withEndAction(() ->
                topOverlay.setVisibility(View.GONE))
            .start();

        // Make instruction banner larger and more prominent
        cardInstruction.setVisibility(View.VISIBLE);

        // Show stop button, hide start button
        btnStartNavigation.setVisibility(View.GONE);
        btnStopNavigation.setVisibility(View.VISIBLE);

        // Collapse sheet to just show instruction banner
        bottomSheetBehavior.setState(
            BottomSheetBehavior.STATE_COLLAPSED);

        // Hide banner during navigation — safety first
        if (bannerAdContainer != null)
            bannerAdContainer.setVisibility(
                android.view.View.GONE);

        // Enter navigation map mode
        enterNavigationMapMode();

        // Bind and start NavigationService
        Intent serviceIntent = new Intent(
            this, NavigationService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection,
            BIND_AUTO_CREATE);

        Toast.makeText(this,
            "Navigation started! 🛵",
            Toast.LENGTH_SHORT).show();
    }

    private void stopActiveNavigation() {
        navActive = false;

        // Stop service
        if (serviceBound && navService != null) {
            navService.stopNavigation();
            unbindService(serviceConnection);
            serviceBound = false;
            navService = null;
        }
        Intent serviceIntent = new Intent(
            this, NavigationService.class);
        stopService(serviceIntent);

        // Show top search cards again
        topOverlay.setVisibility(View.VISIBLE);
        topOverlay.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .start();

        // Hide stop button, show start button again
        btnStopNavigation.setVisibility(View.GONE);
        btnStartNavigation.setVisibility(View.VISIBLE);

        // Restore banner after navigation ends
        if (adManager != null
                && adManager.isAdsEnabled()
                && bannerAdContainer != null) {
            bannerAdContainer.setVisibility(
                android.view.View.VISIBLE);
        }

        // Exit nav map mode
        exitNavigationMapMode();

        // Reset step display
        currentStepIndex = 0;
        if (!currentSteps.isEmpty())
            showStep(0);
        populateSteps(currentSteps);

        Toast.makeText(this,
            "Navigation stopped",
            Toast.LENGTH_SHORT).show();
    }

    // ════════════════════════════════════════
    // NavigationService CALLBACKS
    // ════════════════════════════════════════

    @Override
    public void onStepAdvanced(int newIndex) {
        currentStepIndex = newIndex;
        runOnUiThread(() -> {
            showStep(currentStepIndex);
            populateSteps(currentSteps);
            // Keep map centered on user
            if (navActive) {
                mapView.getController().animateTo(
                    new GeoPoint(currentLat, currentLng));
                mapView.setMapOrientation(-currentBearing);
            }
        });
    }

    @Override
    public void onOffRoute() {
        // User went off route — recalculate
        runOnUiThread(() -> {
            if (savedDestLat != 0 && savedDestLng != 0) {
                Toast.makeText(this,
                    "Off route — recalculating...",
                    Toast.LENGTH_SHORT).show();
                fetchRoute(savedDestLat, savedDestLng);
            }
        });
    }

    @Override
    public void onNavigationFinished() {
        runOnUiThread(() -> {
            tvInstruction.setText(
                "You have arrived!");
            tvDistance.setText("");
            tvTurnArrow.setText("✓");

            // Auto stop after 3 seconds
            new Handler(Looper.getMainLooper())
                .postDelayed(() -> {
                    if (navActive) stopActiveNavigation();
                }, 3000);
        });
    }

    @Override
    public void onDistanceToNextStep(float distanceMeters) {
        runOnUiThread(() -> {
            if (tvDistanceToTurn != null) {
                tvDistanceToTurn.setText(
                    formatDistance(distanceMeters));
            }
            // Update distance in instruction banner
            tvDistance.setText(
                formatDistance(distanceMeters));
        });
    }

    // ════════════════════════════════════════
    // AUTOCOMPLETE
    // ════════════════════════════════════════

    private void setupAutocomplete() {
        suggestionAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_dropdown_item_1line,
            new ArrayList<>());
        etDestination.setAdapter(suggestionAdapter);
        etDestination.setThreshold(2);

        etDestination.addTextChangedListener(
            new TextWatcher() {
            @Override public void beforeTextChanged(
                CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(
                Editable s) {}
            @Override
            public void onTextChanged(CharSequence s,
                    int start, int before, int count) {
                if (searchRunnable != null)
                    searchHandler
                        .removeCallbacks(searchRunnable);
                String query = s.toString().trim();
                if (query.length() >= 2) {
                    searchRunnable =
                        () -> fetchSuggestions(query);
                    searchHandler.postDelayed(
                        searchRunnable, 500);
                }
            }
        });

        etDestination.setOnItemClickListener(
            (parent, view, position, id) -> {
                if (position < suggestionCoords.size()) {
                    double[] coords =
                        suggestionCoords.get(position);
                    String name =
                        suggestionAdapter.getItem(position);
                    etDestination.setText(name);
                    etDestination.dismissDropDown();
                    hideKeyboard();
                    setDirectionsButtonState(true);
                    fetchRoute(coords[0], coords[1]);
                }
            });
    }

    private void fetchSuggestions(String query) {
        new Thread(() -> {
            try {
                String encoded =
                    java.net.URLEncoder.encode(query, "UTF-8");
                String url =
                    "https://nominatim.openstreetmap.org"
                    + "/search?q=" + encoded
                    + "&format=json&limit=5";
                okhttp3.OkHttpClient client =
                    new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10,
                        java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10,
                        java.util.concurrent.TimeUnit.SECONDS)
                    .build();
                okhttp3.Request req =
                    new okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "ScootRoute/1.0")
                    .build();
                okhttp3.Response resp =
                    client.newCall(req).execute();
                if (resp.body() == null) return;
                String body = resp.body().string();
                org.json.JSONArray arr =
                    new org.json.JSONArray(body);
                List<String> names = new ArrayList<>();
                List<double[]> coords = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject place =
                        arr.getJSONObject(i);
                    String displayName =
                        place.getString("display_name");
                    String[] parts = displayName.split(",");
                    String shortName = parts.length > 2
                        ? parts[0].trim() + ", "
                            + parts[1].trim()
                        : displayName;
                    names.add(shortName);
                    coords.add(new double[]{
                        place.getDouble("lat"),
                        place.getDouble("lon")});
                }
                final List<String> fn = names;
                final List<double[]> fc = coords;
                runOnUiThread(() -> {
                    suggestionCoords = fc;
                    suggestionAdapter.clear();
                    suggestionAdapter.addAll(fn);
                    suggestionAdapter.notifyDataSetChanged();
                    if (!fn.isEmpty()
                            && etDestination.hasFocus()) {
                        etDestination.showDropDown();
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("Autocomplete",
                    e.getMessage());
            }
        }).start();
    }

    // ════════════════════════════════════════
    // LISTENERS
    // ════════════════════════════════════════

    private void setupListeners() {
        etDestination.setOnEditorActionListener(
            (v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null
                            && event.getKeyCode() ==
                                KeyEvent.KEYCODE_ENTER)) {
                    String query = etDestination
                        .getText().toString().trim();
                    if (!query.isEmpty())
                        startRouteSearch(query);
                    return true;
                }
                return false;
            });

        btnGetDirections.setOnClickListener(v -> {
            String query = etDestination
                .getText().toString().trim();
            if (query.isEmpty()) {
                Toast.makeText(this,
                    "Please enter a destination",
                    Toast.LENGTH_SHORT).show();
                return;
            }
            startRouteSearch(query);
        });

        btnStartNavigation.setOnClickListener(v ->
            startActiveNavigation());

        btnEditRoute.setOnClickListener(v -> showSearchInputs());

        btnStopNavigation.setOnClickListener(v ->
            stopActiveNavigation());

        btnRecenter.setOnClickListener(v -> {
            mapView.getController().animateTo(
                new GeoPoint(currentLat, currentLng));
            mapView.getController().setZoom(
                navActive ? 18.0 : 16.0);
        });

        // Bottom nav listeners
        navMap.setOnClickListener(v -> {
            layoutMapContextMenu.setVisibility(View.GONE);
            setNavSelected(navMap);
        });
        navDashboard.setOnClickListener(v -> {
            setNavSelected(navDashboard);
            startActivity(new Intent(
                this, DashboardActivity.class));
        });
        navHazard.setOnClickListener(v -> {
            // Center button opens Moped Rental page
            startActivity(new Intent(
                this, MopedRentalActivity.class));
        });
        navSettings.setOnClickListener(v -> {
            setNavSelected(navSettings);
            startActivity(new Intent(
                this, SettingsActivity.class));
        });
        navAccount.setOnClickListener(v -> {
            setNavSelected(navAccount);
            startActivity(new Intent(
                this, AccountActivity.class));
        });

        // Long press on map — show context menu
        mapView.setOnLongClickListener(v -> {
            GeoPoint center = new GeoPoint(
                mapView.getMapCenter().getLatitude(),
                mapView.getMapCenter().getLongitude());
            longPressLat = center.getLatitude();
            longPressLng = center.getLongitude();
            layoutMapContextMenu.setVisibility(View.VISIBLE);
            return true;
        });

        // Context menu actions
        menuItemHazard.setOnClickListener(v -> {
            layoutMapContextMenu.setVisibility(View.GONE);
            Intent intent = new Intent(
                this, HazardReportActivity.class);
            intent.putExtra("lat", longPressLat);
            intent.putExtra("lng", longPressLng);
            startActivity(intent);
        });
        menuItemPin.setOnClickListener(v -> {
            layoutMapContextMenu.setVisibility(View.GONE);
            // Pin opens rental or hazard - user chose pin
            Intent intent = new Intent(
                this, HazardReportActivity.class);
            intent.putExtra("lat", longPressLat);
            intent.putExtra("lng", longPressLng);
            startActivity(intent);
        });
        menuItemRouteTo.setOnClickListener(v -> {
            layoutMapContextMenu.setVisibility(View.GONE);
            fetchRoute(longPressLat, longPressLng);
            etDestination.setText(String.format(
                "%.4f, %.4f",
                longPressLat, longPressLng));
        });
        menuItemDismiss.setOnClickListener(v ->
            layoutMapContextMenu.setVisibility(View.GONE));

        if (btnAddTag != null)
            btnAddTag.setOnClickListener(v ->
                Toast.makeText(this, "Add tag coming soon!",
                    Toast.LENGTH_SHORT).show());
        btnShareEta.setOnClickListener(v -> shareEta());
        btnGasFood.setOnClickListener(v ->
            Toast.makeText(this, "Gas & Food coming soon!",
                Toast.LENGTH_SHORT).show());
        btnLayers.setOnClickListener(v ->
            showMapLayersDialog());
    }

    // ════════════════════════════════════════
    // ROUTING
    // ════════════════════════════════════════

    private void startRouteSearch(String query) {
        hideKeyboard();
        etDestination.dismissDropDown();
        setDirectionsButtonState(true);
        for (int i = 0; i < suggestionAdapter.getCount(); i++) {
            if (query.equals(suggestionAdapter.getItem(i))
                    && i < suggestionCoords.size()) {
                fetchRoute(suggestionCoords.get(i)[0],
                    suggestionCoords.get(i)[1]);
                return;
            }
        }
        geocodeAndRoute(query);
    }

    private void setDirectionsButtonState(
            boolean calculating) {
        runOnUiThread(() -> {
            if (calculating) {
                btnGetDirections.setText("Calculating...");
                btnGetDirections.setEnabled(false);
                btnGetDirections.setAlpha(0.7f);
            } else {
                btnGetDirections.setText("Get Directions");
                btnGetDirections.setEnabled(true);
                btnGetDirections.setAlpha(1.0f);
            }
        });
    }

    private void geocodeAndRoute(String query) {
        new Thread(() -> {
            try {
                String encoded =
                    java.net.URLEncoder.encode(query, "UTF-8");
                String url =
                    "https://nominatim.openstreetmap.org"
                    + "/search?q=" + encoded
                    + "&format=json&limit=1";
                okhttp3.OkHttpClient client =
                    new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10,
                        java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10,
                        java.util.concurrent.TimeUnit.SECONDS)
                    .build();
                okhttp3.Request req =
                    new okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "ScootRoute/1.0")
                    .build();
                okhttp3.Response resp =
                    client.newCall(req).execute();
                if (resp.body() == null) {
                    runOnUiThread(() -> {
                        setDirectionsButtonState(false);
                        Toast.makeText(this,
                            "No response from search",
                            Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                String body = resp.body().string();
                org.json.JSONArray arr =
                    new org.json.JSONArray(body);
                if (arr.length() > 0) {
                    org.json.JSONObject place =
                        arr.getJSONObject(0);
                    double lat = place.getDouble("lat");
                    double lng = place.getDouble("lon");
                    runOnUiThread(() -> fetchRoute(lat, lng));
                } else {
                    runOnUiThread(() -> {
                        setDirectionsButtonState(false);
                        Toast.makeText(this,
                            "Location not found",
                            Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setDirectionsButtonState(false);
                    Toast.makeText(this,
                        "Search error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void fetchRoute(double destLat, double destLng) {
        savedDestLat = destLat;
        savedDestLng = destLng;
        GeoPoint destPoint = new GeoPoint(destLat, destLng);
        if (destMarker == null) {
            destMarker = new Marker(mapView);
            destMarker.setAnchor(
                Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            destMarker.setIcon(
                createDestinationDrawable());
            mapView.getOverlays().add(destMarker);
        }
        destMarker.setPosition(destPoint);
        mapView.invalidate();
        new RoutingManager(this).getRoute(
            currentLat, currentLng, destLat, destLng);
    }

    public void onRouteReady(List<GeoPoint> points,
                             List<RouteStep> steps,
                             int durationMinutes) {
        savedRoutePoints = new ArrayList<>(points);
        currentSteps = steps;
        // Rerouting mid-ride starts at the first real maneuver
        currentStepIndex =
            (navActive && steps.size() > 1) ? 1 : 0;
        savedDurationMinutes = durationMinutes;
        isNavigating = true;
        drawRoute(points, steps, durationMinutes);
        setDirectionsButtonState(false);
        btnGetDirections.setText("Recalculate");

        // Riding already? hand the new route to the voice service
        if (navActive && serviceBound && navService != null) {
            navService.setRoutePoints(savedRoutePoints);
            navService.startNavigation(steps, this, true);
        }

        // Keep the top controls compact once a route exists
        editingRoute = false;
        runOnUiThread(() -> {
            showCompactRouteBar();
            if (!navActive)
                btnStartNavigation.setVisibility(View.VISIBLE);
        });
    }

    private void restoreRoute() {
        if (savedRoutePoints == null
                || savedRoutePoints.isEmpty()) return;
        drawRoute(savedRoutePoints,
            currentSteps, savedDurationMinutes);
        if (savedDestLat != 0 && savedDestLng != 0) {
            if (destMarker == null) {
                destMarker = new Marker(mapView);
                destMarker.setAnchor(
                    Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                mapView.getOverlays().add(destMarker);
            }
            destMarker.setPosition(
                new GeoPoint(savedDestLat, savedDestLng));
        }
        btnGetDirections.setText("Recalculate");
        if (!editingRoute) showCompactRouteBar();
        if (!navActive)
            btnStartNavigation.setVisibility(View.VISIBLE);
        mapView.invalidate();
    }

    // Once a route exists, the search boxes collapse into one slim
    // "Edit route" bar (plus Start) so the map stays visible.
    private void showCompactRouteBar() {
        if (layoutSearchInputs != null)
            layoutSearchInputs.setVisibility(View.GONE);
        if (cardOrigin != null)
            cardOrigin.setVisibility(View.GONE);
        if (routeBar != null)
            routeBar.setVisibility(View.VISIBLE);
        if (tvRouteSummary != null) {
            String dest = etDestination.getText().toString().trim();
            tvRouteSummary.setText(dest.isEmpty()
                ? "Edit route" : "Edit route  \u00B7  " + dest);
        }
    }

    // Tapping "Edit route" brings the destination box back
    private void showSearchInputs() {
        editingRoute = true;
        if (routeBar != null) routeBar.setVisibility(View.GONE);
        if (layoutSearchInputs != null)
            layoutSearchInputs.setVisibility(View.VISIBLE);
        etDestination.requestFocus();
        etDestination.selectAll();
        InputMethodManager imm =
            (InputMethodManager) getSystemService(
                INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.showSoftInput(etDestination,
                InputMethodManager.SHOW_IMPLICIT);
    }

    private void drawRoute(List<GeoPoint> points,
                           List<RouteStep> steps,
                           int durationMinutes) {
        if (routeLine != null)
            mapView.getOverlays().remove(routeLine);
        routeLine = new Polyline();
        routeLine.setPoints(points);
        routeLine.getOutlinePaint().setColor(
            Color.parseColor("#00D4FF"));
        routeLine.getOutlinePaint().setStrokeWidth(12f);
        routeLine.getOutlinePaint().setAlpha(220);
        mapView.getOverlays().add(0, routeLine);

        // Fit the whole route, leaving room for the top controls
        // and the collapsed sheet. Don't zoom out while riding.
        if (!navActive) {
            mapView.zoomToBoundingBox(
                routeLine.getBounds(), true, dpToPx(110));
        }

        cardInstruction.setVisibility(View.VISIBLE);
        tvEta.setText(durationMinutes + " min");
        populateSteps(steps);
        if (!steps.isEmpty()) showStep(currentStepIndex);
        mapView.invalidate();

        // The sheet only exists once there is a route. It starts
        // collapsed (banner + handle) so the map stays visible;
        // drag the handle up to see every step.
        findViewById(R.id.bottomSheetContainer)
            .setVisibility(View.VISIBLE);
        if (!navActive) {
            bottomSheetBehavior.setPeekHeight(dpToPx(120));
            bottomSheetBehavior.setState(
                BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    public void onRouteFailed() {
        setDirectionsButtonState(false);
    }

    // ════════════════════════════════════════
    // STEP DISPLAY
    // ════════════════════════════════════════

    private void populateSteps(List<RouteStep> steps) {
        llRouteSteps.removeAllViews();
        for (int i = 0; i < steps.size(); i++) {
            final int stepIndex = i;
            RouteStep step = steps.get(i);
            View stepView = getLayoutInflater().inflate(
                R.layout.item_route_step,
                llRouteSteps, false);
            TextView icon =
                stepView.findViewById(R.id.tvStepIcon);
            TextView instruction =
                stepView.findViewById(
                    R.id.tvStepInstruction);
            TextView dist =
                stepView.findViewById(R.id.tvStepDistance);
            TextView badge =
                stepView.findViewById(
                    R.id.tvStepDistanceBadge);

            icon.setText(arrowForType(step.type));

            instruction.setText(step.instruction);

            String distStr =
                formatDistance(step.distanceMeters);
            dist.setText(distStr);
            badge.setText(distStr);

            if (i == currentStepIndex) {
                instruction.setTextColor(
                    Color.parseColor("#00D4FF"));
                stepView.setBackgroundColor(
                    Color.parseColor("#1A00D4FF"));
            }
            if (i == steps.size() - 1) {
                instruction.setTextColor(
                    Color.parseColor("#00D4FF"));
                icon.setText("✓");
                icon.setTextColor(
                    Color.parseColor("#00C853"));
            }

            // ── TAP TO HIGHLIGHT SEGMENT ──
            stepView.setOnClickListener(v ->
                highlightRouteSegment(stepIndex));

            llRouteSteps.addView(stepView);
        }
        if (stepsScrollView != null)
            stepsScrollView.scrollTo(0, 0);
    }

    // ════════════════════════════════════════
    // HIGHLIGHT ROUTE SEGMENT ON TAP
    // ════════════════════════════════════════

    private Polyline highlightLine = null;

    private void highlightRouteSegment(int stepIndex) {
        if (savedRoutePoints == null
                || savedRoutePoints.isEmpty()
                || currentSteps == null
                || stepIndex >= currentSteps.size()) return;

        // Remove previous highlight
        if (highlightLine != null) {
            mapView.getOverlays().remove(highlightLine);
            highlightLine = null;
        }

        // Find the waypoint indices for this step
        // Each step covers from previous waypoint to this one
        // We use the RouteStep waypoint as segment end
        RouteStep step = currentSteps.get(stepIndex);

        // A step's road runs from its own maneuver point to the
        // next step's maneuver point
        int startIdx = findClosestPointIndex(
            step.waypointLat, step.waypointLng);
        int endIdx = savedRoutePoints.size() - 1;
        if (stepIndex + 1 < currentSteps.size()) {
            RouteStep nextStep = currentSteps.get(stepIndex + 1);
            endIdx = findClosestPointIndex(
                nextStep.waypointLat, nextStep.waypointLng);
        }

        // Extract segment points
        if (endIdx > startIdx) {
            List<GeoPoint> segmentPoints =
                savedRoutePoints.subList(startIdx, endIdx + 1);

            // Draw bright white highlight over segment
            highlightLine = new Polyline();
            highlightLine.setPoints(
                new ArrayList<>(segmentPoints));
            highlightLine.getOutlinePaint().setColor(
                Color.parseColor("#FFFFFF"));
            highlightLine.getOutlinePaint()
                .setStrokeWidth(16f);
            highlightLine.getOutlinePaint().setAlpha(200);

            // Add above route line
            mapView.getOverlays().add(highlightLine);

            // Zoom map to show just this segment
            if (segmentPoints.size() >= 2) {
                org.osmdroid.util.BoundingBox box =
                    org.osmdroid.util.BoundingBox
                        .fromGeoPoints(segmentPoints);
                mapView.zoomToBoundingBox(box, true, 150);
            } else {
                // Single point — just animate there
                mapView.getController().animateTo(
                    new GeoPoint(step.waypointLat,
                        step.waypointLng));
                mapView.getController().setZoom(17.0);
            }

            mapView.invalidate();

            // Auto clear highlight after 4 seconds
            new Handler(Looper.getMainLooper())
                .postDelayed(() -> {
                    if (highlightLine != null) {
                        mapView.getOverlays()
                            .remove(highlightLine);
                        highlightLine = null;
                        mapView.invalidate();
                    }
                }, 4000);
        }
    }

    private int findClosestPointIndex(double lat, double lng) {
        if (savedRoutePoints == null) return 0;
        int closest = 0;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < savedRoutePoints.size(); i++) {
            GeoPoint pt = savedRoutePoints.get(i);
            double dist = Math.sqrt(
                Math.pow(pt.getLatitude() - lat, 2)
                + Math.pow(pt.getLongitude() - lng, 2));
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        return closest;
    }

    private void showStep(int index) {
        if (currentSteps == null || index < 0
                || index >= currentSteps.size()) return;
        RouteStep step = currentSteps.get(index);
        tvInstruction.setText(step.instruction);
        // While riding, a maneuver is as far away as the step
        // before it is long. In preview, show the step's own length.
        double meters = (navActive && index > 0)
            ? currentSteps.get(index - 1).distanceMeters
            : step.distanceMeters;
        String d = formatDistance(meters);
        tvDistanceToTurn.setText(d);
        tvDistance.setText(d);
        tvTurnArrow.setText(arrowForType(step.type));
    }

    private void checkStepProgress() {
        // Only the navigation service advances steps, and only
        // after the rider taps Start Navigation.
        if (navActive && serviceBound && navService != null) {
            navService.updateLocation(
                currentLat, currentLng,
                currentSpeedMs, currentBearing);
        }
    }

    // ════════════════════════════════════════
    // LOCATION
    // ════════════════════════════════════════

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQ);
        }
    }

    private void startLocationUpdates() {
        fusedClient = LocationServices
            .getFusedLocationProviderClient(this);
        LocationRequest request = LocationRequest.create();
        request.setInterval(2000);
        request.setFastestInterval(1000);
        request.setPriority(
            LocationRequest.PRIORITY_HIGH_ACCURACY);
        request.setSmallestDisplacement(3f);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(
                    @NonNull LocationResult result) {
                if (result.getLastLocation() != null) {
                    android.location.Location loc =
                        result.getLastLocation();
                    currentLat = loc.getLatitude();
                    currentLng = loc.getLongitude();
                    if (loc.hasBearing()) {
                        currentBearing = loc.getBearing();
                    }
                    if (loc.hasSpeed()) {
                        currentSpeedMs = loc.getSpeed();
                    }
                    updateUserMarker();
                    tvOrigin.setText("Your location");
                    if (isNavigating) checkStepProgress();

                    // Keep map following user in nav mode
                    if (navActive) {
                        mapView.getController().animateTo(
                            new GeoPoint(currentLat,
                                currentLng));
                        mapView.setMapOrientation(
                            -currentBearing);
                    }
                }
            }
        };

        try {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                fusedClient.requestLocationUpdates(
                    request, locationCallback,
                    getMainLooper());
            }
        } catch (Exception e) {
            Toast.makeText(this,
                "Location error: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUserMarker() {
        GeoPoint userPoint =
            new GeoPoint(currentLat, currentLng);
        if (userMarker == null) {
            userMarker = new Marker(mapView);
            userMarker.setTitle("You");
            userMarker.setAnchor(
                Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            // Use a custom drawable instead of blue square
            userMarker.setIcon(
                createUserLocationDrawable());
            mapView.getOverlays().add(userMarker);
        }
        userMarker.setPosition(userPoint);
        // Rotate marker to match bearing
        if (navActive) {
            userMarker.setRotation(-currentBearing);
        }
        mapView.invalidate();
    }

    private android.graphics.drawable.Drawable
            createUserLocationDrawable() {
        // Create a cyan circle marker programmatically
        android.graphics.Bitmap bmp =
            android.graphics.Bitmap.createBitmap(
                48, 48,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas =
            new android.graphics.Canvas(bmp);
        android.graphics.Paint paint =
            new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);

        // Outer glow
        paint.setColor(0x4400D4FF);
        canvas.drawCircle(24, 24, 22, paint);

        // Inner filled circle
        paint.setColor(0xFF00D4FF);
        canvas.drawCircle(24, 24, 14, paint);

        // White center dot
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(24, 24, 6, paint);

        return new android.graphics.drawable
            .BitmapDrawable(getResources(), bmp);
    }

    private android.graphics.drawable.Drawable
            createDestinationDrawable() {
        android.graphics.Bitmap bmp =
            android.graphics.Bitmap.createBitmap(
                48, 64,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas =
            new android.graphics.Canvas(bmp);
        android.graphics.Paint paint =
            new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);

        // Pin circle
        paint.setColor(0xFFFF3B30);
        canvas.drawCircle(24, 24, 20, paint);

        // White inner circle
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(24, 24, 10, paint);

        // Pin tail
        paint.setColor(0xFFFF3B30);
        android.graphics.Path path =
            new android.graphics.Path();
        path.moveTo(16, 36);
        path.lineTo(32, 36);
        path.lineTo(24, 58);
        path.close();
        canvas.drawPath(path, paint);

        return new android.graphics.drawable
            .BitmapDrawable(getResources(), bmp);
    }

    // ════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════

    private String arrowForType(String type) {
        if (type == null) return "↑";
        switch (type.toLowerCase()) {
            case "turn-right":   return "→";
            case "turn-left":    return "←";
            case "slight-right": return "↗";
            case "slight-left":  return "↖";
            case "u-turn":       return "↩";
            case "keep-left":    return "↖";
            case "keep-right":   return "↗";
            case "roundabout":   return "↻";
            case "arrive":       return "✓";
            default:             return "↑";
        }
    }

    private String formatDistance(double meters) {
        double feet = meters * 3.28084;
        if (feet < 1000) {
            int rounded = (int)(Math.round(feet / 50.0) * 50);
            if (rounded <= 0) rounded = 50;
            return rounded + " ft";
        } else if (feet < 5280) {
            return (int)(Math.round(feet / 100.0) * 100) + " ft";
        } else {
            double miles = feet / 5280.0;
            return String.format("%.1f mi", miles);
        }
    }

    // ════════════════════════════════════════
    // MAP LAYERS DIALOG
    // Satellite view + landmark toggle
    // ════════════════════════════════════════

    private void showMapLayersDialog() {
        String[] options = {
            isSatelliteView
                ? "🗺  Switch to Street View"
                : "🛰  Switch to Satellite View",
            showLandmarks
                ? "🏢  Hide Landmarks & Businesses"
                : "🏢  Show Landmarks & Businesses",
            "↩  Cancel"
        };

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Map Layers")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        toggleSatelliteView();
                        break;
                    case 1:
                        toggleLandmarks();
                        break;
                    case 2:
                        dialog.dismiss();
                        break;
                }
            })
            .show();
    }

    private void toggleSatelliteView() {
        isSatelliteView = !isSatelliteView;

        if (isSatelliteView) {
            // Esri World Imagery satellite tiles.
            // OSMDroid's XYTileSource always builds base/zoom/x/y,
            // but Esri expects zoom/y/x, so we override the URL builder.
            org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
                    satellite =
                new org.osmdroid.tileprovider.tilesource
                        .OnlineTileSourceBase(
                    "EsriWorldImagery",
                    0, 18, 256, ".jpg",
                    new String[]{
                        "https://server.arcgisonline.com/ArcGIS/rest/"
                        + "services/World_Imagery/MapServer/tile/"
                    },
                    "Tiles © Esri") {
                    @Override
                    public String getTileURLString(long idx) {
                        return getBaseUrl()
                            + org.osmdroid.util.MapTileIndex.getZoom(idx)
                            + "/" + org.osmdroid.util.MapTileIndex.getY(idx)
                            + "/" + org.osmdroid.util.MapTileIndex.getX(idx);
                    }
                };
            mapView.setTileSource(satellite);
            Toast.makeText(this,
                "🛰 Satellite view",
                Toast.LENGTH_SHORT).show();
        } else {
            // Back to street map
            mapView.setTileSource(
                TileSourceFactory.MAPNIK);
            Toast.makeText(this,
                "🗺 Street view",
                Toast.LENGTH_SHORT).show();
        }
        mapView.invalidate();
    }

    private void toggleLandmarks() {
        showLandmarks = !showLandmarks;
        // OSMDroid doesn't have a direct landmark toggle
        // but we can switch between tile sources
        // that include or exclude POI labels
        if (!showLandmarks) {
            // Use a minimal tile source without POI clutter
            org.osmdroid.tileprovider.tilesource
                    .XYTileSource minimal =
                new org.osmdroid.tileprovider.tilesource
                    .XYTileSource(
                "Minimal",
                0, 19, 256, ".png",
                new String[]{
                    "https://a.basemaps.cartocdn.com"
                        + "/light_nolabels/",
                    "https://b.basemaps.cartocdn.com"
                        + "/light_nolabels/",
                    "https://c.basemaps.cartocdn.com"
                        + "/light_nolabels/"
                },
                "© CartoDB © OpenStreetMap");
            if (!isSatelliteView)
                mapView.setTileSource(minimal);
            Toast.makeText(this,
                "Landmarks hidden",
                Toast.LENGTH_SHORT).show();
        } else {
            if (!isSatelliteView)
                mapView.setTileSource(
                    TileSourceFactory.MAPNIK);
            Toast.makeText(this,
                "Landmarks visible",
                Toast.LENGTH_SHORT).show();
        }
        mapView.invalidate();
    }

    private void setNavSelected(LinearLayout selected) {
        int grey = 0xFF888888;
        int cyan = 0xFF00D4FF;
        LinearLayout[] navItems = {
            navMap, navDashboard,
            navSettings, navAccount};
        for (LinearLayout nav : navItems) {
            if (nav == null) continue;
            // Label is second child (index 1)
            android.view.View child = nav.getChildAt(1);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(grey);
            }
        }
        if (selected != null) {
            android.view.View child =
                selected.getChildAt(1);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(cyan);
            }
        }
    }

    private void shareEta() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT,
            "On my way via ScootRoute! 🛵");
        startActivity(
            Intent.createChooser(share, "Share ETA"));
    }

    private void hideKeyboard() {
        InputMethodManager imm =
            (InputMethodManager) getSystemService(
                INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(
                etDestination.getWindowToken(), 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQ
                && grantResults.length > 0
                && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }
}
