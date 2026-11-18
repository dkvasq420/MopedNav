package com.scootroute.mopednav;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Routing for ScootRoute.
 *
 * Flow:
 *  1. cycling-regular (cannot use motorways/trunks). When the rider has
 *     "exclude bike paths" on (the default for moped mode) we also ask for
 *     alternative routes + way-type data and pick the route with the
 *     fewest meters of path/cycleway/footway/stairs.
 *  2. If a bike path is still unavoidable, try cycling-road, which
 *     prefers real roads, and keep whichever candidate is best.
 *  3. Only if cycling fails completely: driving-car (validated against
 *     the freeway name blacklist), then OSRM (also validated).
 *
 * driving-car is never used just to dodge bike paths, because it can
 * route onto freeways whose steps have no names.
 */
public class RoutingManager {

    private static final String TAG = "ScootRoute";

    // PUT YOUR OpenRouteService API KEY HERE
    private static final String ORS_API_KEY = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6IjcyMzRmNGJkOWY4YTQ2M2FhMmQ3YzNjOWY2MTVkNjZkIiwiaCI6Im11cm11cjY0In0=";

    // Toasts/dialogs with raw API details only appear in debug builds.
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String ORS_BASE =
        "https://api.heigit.org/openrouteservice/v2/directions/";
    private static final String CYCLING_URL      = ORS_BASE + "cycling-regular";
    private static final String CYCLING_ROAD_URL = ORS_BASE + "cycling-road";
    private static final String DRIVING_URL      = ORS_BASE + "driving-car";

    // A bike path shorter than this (meters) is treated as a crossing,
    // not worth rejecting a route over.
    private static final double PATH_TOLERANCE_M = 30.0;

    // Final validation blacklist catches anything that slips through
    private static final List<String> FREEWAY_BLACKLIST =
        Arrays.asList(
            "freeway", "lunalilo", "liliuokalani",
            "liluokalani", "queen lili", "interstate"
        );

    private final MainActivity activity;
    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build();

    // A RoutingManager instance serves exactly one route request,
    // so this list is per-request.
    private final List<Candidate> collected = new ArrayList<>();

    private static class Candidate {
        List<GeoPoint> points;
        List<RouteStep> steps;
        int durationSecs;
        double pathMeters;   // meters on path/cycleway/footway/stairs
    }

    public RoutingManager(MainActivity activity) {
        this.activity = activity;
    }

    // ════════════════════════════════════════
    // DEBUG HELPERS
    // ════════════════════════════════════════

    private void debugToast(final String message) {
        Log.d(TAG, message);
        if (!DEBUG) return;
        activity.runOnUiThread(() ->
            Toast.makeText(activity, message,
                Toast.LENGTH_LONG).show());
    }

    private void debugDialog(final String title,
                             final String message) {
        Log.d(TAG, title + ": " + message);
        if (!DEBUG) return;
        activity.runOnUiThread(() -> {
            ScrollView sv = new ScrollView(activity);
            TextView tv = new TextView(activity);
            tv.setText(message);
            tv.setTextIsSelectable(true);
            tv.setPadding(32, 16, 32, 16);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(13);
            sv.addView(tv);
            new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("OK", null)
                .setNegativeButton("Copy", (d, w) -> {
                    ClipboardManager cm =
                        (ClipboardManager) activity
                        .getSystemService(
                            Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(
                            ClipData.newPlainText(
                                "Error", message));
                        Toast.makeText(activity,
                            "Copied!",
                            Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
        });
    }

    // ════════════════════════════════════════
    // PREFERENCES
    // Moped mode excludes bike paths by default.
    // Bicycle riders (or anyone who unticked the option) may use them.
    // ════════════════════════════════════════

    private boolean shouldExcludePaths() {
        SharedPreferences p = activity.getSharedPreferences(
            "ScootRoute", Context.MODE_PRIVATE);
        String vehicle = p.getString("vehicleType", "Moped");
        if (vehicle != null
                && vehicle.toLowerCase(Locale.US)
                    .contains("bicycle")) {
            return false;
        }
        return p.getBoolean("excludeBikePaths", true);
    }

    // ════════════════════════════════════════
    // TEXT HELPERS
    // ════════════════════════════════════════

    private String normalizeText(String text) {
        if (text == null) return "";
        String result = text.toLowerCase()
            .replace("\u02bb", "").replace("\u2018", "")
            .replace("\u2019", "").replace("\u02bc", "")
            .replace("\u0060", "").replace("'", "")
            .replace("\u2032", "");
        try {
            result = Normalizer.normalize(
                result, Normalizer.Form.NFD)
                .replaceAll("[^\\x00-\\x7F]", "");
        } catch (Exception ignored) {}
        return result;
    }

    private String routeContainsFreeway(List<RouteStep> steps) {
        for (RouteStep step : steps) {
            String normalized = " "
                + normalizeText(step.instruction) + " ";
            for (String keyword : FREEWAY_BLACKLIST) {
                if (normalized.contains(
                        normalizeText(keyword))) {
                    return "Step: \"" + step.instruction
                        + "\" matched: \"" + keyword + "\"";
                }
            }
        }
        return null;
    }

    // ════════════════════════════════════════
    // ENTRY POINT
    // ════════════════════════════════════════

    public void getRoute(double fromLat, double fromLng,
                         double toLat, double toLng) {
        debugToast("Routing...");
        collected.clear();
        requestCycling(CYCLING_URL, "cycling",
            fromLat, fromLng, toLat, toLng, 0);
    }

    private JSONObject baseBody(double fromLat, double fromLng,
                                double toLat, double toLng)
            throws Exception {
        JSONObject body = new JSONObject();

        JSONArray coords = new JSONArray();
        JSONArray start = new JSONArray();
        start.put(fromLng); start.put(fromLat);
        JSONArray end = new JSONArray();
        end.put(toLng); end.put(toLat);
        coords.put(start); coords.put(end);
        body.put("coordinates", coords);

        JSONArray radiuses = new JSONArray();
        radiuses.put(2000); radiuses.put(2000);
        body.put("radiuses", radiuses);

        body.put("instructions", true);
        body.put("language", "en");
        return body;
    }

    private Request buildRequest(String url, JSONObject body) {
        RequestBody reqBody = RequestBody.create(
            body.toString(),
            MediaType.parse("application/json"));
        return new Request.Builder()
            .url(url)
            .post(reqBody)
            .addHeader("Authorization", "Bearer " + ORS_API_KEY)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept",
                "application/json, application/geo+json")
            .build();
    }

    // ════════════════════════════════════════
    // CYCLING REQUESTS (cycling-regular, then cycling-road)
    // attempt 0 = full request (way types + alternatives)
    // attempt 1 = plain retry in case ORS rejected the extras
    // ════════════════════════════════════════

    private void requestCycling(final String url, final String tag,
                                final double fromLat,
                                final double fromLng,
                                final double toLat,
                                final double toLng,
                                final int attempt) {
        try {
            JSONObject body =
                baseBody(fromLat, fromLng, toLat, toLng);

            JSONObject options = new JSONObject();
            JSONArray avoid = new JSONArray();
            avoid.put("ferries");
            options.put("avoid_features", avoid);
            body.put("options", options);
            body.put("preference", "recommended");

            if (attempt == 0) {
                // Way types let us measure bike-path usage
                JSONArray extra = new JSONArray();
                extra.put("waytype");
                body.put("extra_info", extra);

                // Alternatives give us routes that avoid paths
                if (shouldExcludePaths()) {
                    JSONObject alt = new JSONObject();
                    alt.put("target_count", 3);
                    alt.put("weight_factor", 1.6);
                    alt.put("share_factor", 0.6);
                    body.put("alternative_routes", alt);
                }
            }

            debugToast(tag + " attempt " + attempt + "...");

            client.newCall(buildRequest(url, body))
                .enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    debugToast(tag + " FAIL: " + e.getMessage());
                    cyclingFailed(tag, fromLat, fromLng,
                        toLat, toLng, attempt);
                }

                @Override
                public void onResponse(Call call,
                        Response response) throws IOException {
                    String rb = response.body() != null
                        ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        handleSuccess(rb, fromLat, fromLng,
                            toLat, toLng, tag);
                    } else {
                        debugDialog(tag + " Error "
                            + response.code(), rb);
                        cyclingFailed(tag, fromLat, fromLng,
                            toLat, toLng, attempt);
                    }
                }
            });

        } catch (Exception e) {
            debugDialog("Build Error",
                String.valueOf(e.getMessage()));
            cyclingFailed(tag, fromLat, fromLng,
                toLat, toLng, 99);
        }
    }

    private void cyclingFailed(String tag,
                               double fromLat, double fromLng,
                               double toLat, double toLng,
                               int attempt) {
        if (tag.equals("cycling")) {
            if (attempt < 1) {
                requestCycling(CYCLING_URL, "cycling",
                    fromLat, fromLng, toLat, toLng, attempt + 1);
            } else {
                tryDrivingRoute(fromLat, fromLng, toLat, toLng);
            }
        } else {
            // cycling-road is only an optional improvement
            if (!collected.isEmpty()) {
                deliverBest(tag);
            } else {
                tryDrivingRoute(fromLat, fromLng, toLat, toLng);
            }
        }
    }

    // ════════════════════════════════════════
    // DRIVING PROFILE — fallback only if cycling is down
    // ════════════════════════════════════════

    private void tryDrivingRoute(final double fromLat,
                                 final double fromLng,
                                 final double toLat,
                                 final double toLng) {
        try {
            JSONObject body =
                baseBody(fromLat, fromLng, toLat, toLng);

            JSONObject options = new JSONObject();
            JSONArray avoid = new JSONArray();
            avoid.put("highways");
            avoid.put("tollways");
            options.put("avoid_features", avoid);
            body.put("options", options);
            body.put("preference", "shortest");

            debugToast("Driving fallback...");

            client.newCall(buildRequest(DRIVING_URL, body))
                .enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    debugToast("Driving FAIL: " + e.getMessage());
                    tryOsrmRoute(fromLat, fromLng, toLat, toLng);
                }

                @Override
                public void onResponse(Call call,
                        Response response) throws IOException {
                    String rb = response.body() != null
                        ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        handleSuccess(rb, fromLat, fromLng,
                            toLat, toLng, "driving");
                    } else {
                        debugDialog("Driving Error "
                            + response.code(), rb);
                        tryOsrmRoute(fromLat, fromLng,
                            toLat, toLng);
                    }
                }
            });

        } catch (Exception e) {
            tryOsrmRoute(fromLat, fromLng, toLat, toLng);
        }
    }

    // ════════════════════════════════════════
    // HANDLE SUCCESS — validate, compare, deliver
    // ════════════════════════════════════════

    private void handleSuccess(String json,
                               double fromLat, double fromLng,
                               double toLat, double toLng,
                               String profile) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray routes = root.getJSONArray("routes");

            String rejected = null;
            for (int i = 0; i < routes.length(); i++) {
                Candidate c =
                    buildCandidate(routes.getJSONObject(i));
                String hit = routeContainsFreeway(c.steps);
                if (hit != null) {
                    rejected = hit;
                    continue;
                }
                collected.add(c);
            }

            if (collected.isEmpty()) {
                debugToast("REJECTED! " + rejected);
                if (profile.equals("driving")) {
                    tryOsrmRoute(fromLat, fromLng, toLat, toLng);
                } else {
                    tryDrivingRoute(fromLat, fromLng,
                        toLat, toLng);
                }
                return;
            }

            // Still a real bike path in every candidate?
            // Give the road-bike profile one chance.
            if (profile.equals("cycling")
                    && shouldExcludePaths()
                    && bestPathMeters() > PATH_TOLERANCE_M) {
                debugToast("Bike path found, trying road profile");
                requestCycling(CYCLING_ROAD_URL, "cycling-road",
                    fromLat, fromLng, toLat, toLng, 0);
                return;
            }

            deliverBest(profile);

        } catch (Exception e) {
            debugDialog("Parse Error",
                e.getMessage() + "\n\n"
                + json.substring(0,
                    Math.min(400, json.length())));
            if (!collected.isEmpty()) {
                deliverBest(profile);
            } else if (profile.equals("driving")) {
                tryOsrmRoute(fromLat, fromLng, toLat, toLng);
            } else {
                tryDrivingRoute(fromLat, fromLng, toLat, toLng);
            }
        }
    }

    private double bestPathMeters() {
        double best = Double.MAX_VALUE;
        for (Candidate c : collected) {
            if (c.pathMeters < best) best = c.pathMeters;
        }
        return best;
    }

    private Candidate pickBest(boolean excludePaths) {
        Candidate best = collected.get(0);
        if (excludePaths) {
            for (Candidate c : collected) {
                // strictly less path wins; ties keep the earlier
                // (faster / recommended) route
                if (c.pathMeters < best.pathMeters - 1.0) {
                    best = c;
                }
            }
        }
        return best;
    }

    private void deliverBest(String profile) {
        final boolean exclude = shouldExcludePaths();
        final Candidate best = pickBest(exclude);
        final int mopedMins = calculateMopedTime(
            best.steps, best.durationSecs, profile);

        debugToast("APPROVED! " + best.steps.size() + " steps, "
            + mopedMins + " min, path "
            + Math.round(best.pathMeters) + " m (" + profile + ")");

        final boolean warn =
            exclude && best.pathMeters > PATH_TOLERANCE_M;
        final int pathFeet =
            (int) Math.round(best.pathMeters * 3.28084);

        activity.runOnUiThread(() -> {
            if (warn) {
                Toast.makeText(activity,
                    "Heads up: about " + pathFeet
                    + " ft of this route is a bike path. "
                    + "Check signs before riding it.",
                    Toast.LENGTH_LONG).show();
            }
            activity.onRouteReady(
                best.points, best.steps, mopedMins);
        });
    }

    // ════════════════════════════════════════
    // MOPED TIME CALCULATOR
    // ════════════════════════════════════════

    private int calculateMopedTime(List<RouteStep> steps,
                                   int orsSeconds,
                                   String profile) {
        double totalMeters = 0;
        for (RouteStep step : steps) {
            totalMeters += step.distanceMeters;
        }

        // Moped average ~40 kph with lights/stops
        double mopedSpeedMs = 11.0;
        int mopedSeconds = (int) (totalMeters / mopedSpeedMs);

        // ~25 seconds per km for intersections
        int stopTime = (int) ((totalMeters / 1000.0) * 25);
        int minutes = Math.max(1, (mopedSeconds + stopTime) / 60);

        Log.d(TAG, "Moped ETA: "
            + String.format(Locale.US, "%.1f", totalMeters / 1000.0)
            + "km = " + minutes + "min"
            + " (ORS " + profile + " said "
            + (orsSeconds / 60) + "min)");
        return minutes;
    }

    // ════════════════════════════════════════
    // PARSING
    // ════════════════════════════════════════

    private Candidate buildCandidate(JSONObject route)
            throws Exception {
        Candidate c = new Candidate();
        c.durationSecs = (int) route.getJSONObject("summary")
            .getDouble("duration");
        c.points = parseGeometry(route);
        int[] wayTypes = parseWayTypes(route, c.points.size());
        c.steps = parseSteps(route, c.points, wayTypes);
        c.pathMeters = pathMeters(c.points, wayTypes);
        return c;
    }

    private List<GeoPoint> parseGeometry(
            JSONObject route) throws Exception {
        List<GeoPoint> points = new ArrayList<>();
        Object geometryObj = route.get("geometry");

        if (geometryObj instanceof JSONObject) {
            JSONObject geometry = (JSONObject) geometryObj;
            if (geometry.has("coordinates")) {
                JSONArray coords =
                    geometry.getJSONArray("coordinates");
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray c = coords.getJSONArray(i);
                    points.add(new GeoPoint(
                        c.getDouble(1), c.getDouble(0)));
                }
            }
        } else if (geometryObj instanceof String) {
            points = decodePolyline((String) geometryObj);
        }

        Log.d(TAG, "Geometry: " + points.size() + " pts");
        return points;
    }

    // ORS way type per geometry segment:
    // 3 street, 4 path, 5 track, 6 cycleway, 7 footway, 8 steps
    private int[] parseWayTypes(JSONObject route, int pointCount) {
        int[] wt = new int[Math.max(pointCount, 1)];
        try {
            JSONObject extras = route.optJSONObject("extras");
            if (extras == null) return wt;
            JSONObject w = extras.optJSONObject("waytype");
            if (w == null) return wt;
            JSONArray vals = w.optJSONArray("values");
            if (vals == null) return wt;
            for (int i = 0; i < vals.length(); i++) {
                JSONArray v = vals.getJSONArray(i);
                int s = v.getInt(0);
                int e = v.getInt(1);
                int t = v.getInt(2);
                for (int k = Math.max(0, s);
                        k < e && k < wt.length; k++) {
                    wt[k] = t;
                }
            }
        } catch (Exception ignored) {}
        return wt;
    }

    private boolean isPathType(int t) {
        return t == 4 || t == 6 || t == 7 || t == 8;
    }

    private double pathMeters(List<GeoPoint> points, int[] wt) {
        double total = 0;
        for (int k = 0; k + 1 < points.size()
                && k < wt.length; k++) {
            if (isPathType(wt[k])) {
                total += meters(points.get(k), points.get(k + 1));
            }
        }
        return total;
    }

    private static double meters(GeoPoint a, GeoPoint b) {
        double r = 6371000.0;
        double dLat = Math.toRadians(
            b.getLatitude() - a.getLatitude());
        double dLng = Math.toRadians(
            b.getLongitude() - a.getLongitude());
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(a.getLatitude()))
            * Math.cos(Math.toRadians(b.getLatitude()))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(s)));
    }

    // Describes an unnamed way using its OSM way type
    private String pathLabel(int[] wt, int idx) {
        if (wt == null || idx < 0 || idx >= wt.length) return null;
        switch (wt[idx]) {
            case 6:  return "bike path";
            case 4:  return "path";
            case 7:  return "footpath";
            case 8:  return "stairs";
            default: return null;
        }
    }

    private List<RouteStep> parseSteps(JSONObject route,
            List<GeoPoint> points, int[] wayTypes)
            throws Exception {
        List<RouteStep> steps = new ArrayList<>();
        JSONArray segments = route.getJSONArray("segments");
        for (int s = 0; s < segments.length(); s++) {
            JSONArray stepArr =
                segments.getJSONObject(s).getJSONArray("steps");
            for (int i = 0; i < stepArr.length(); i++) {
                JSONObject step = stepArr.getJSONObject(i);

                String instruction = step.getString("instruction");
                double dist = step.getDouble("distance");
                int typeCode = step.getInt("type");
                JSONArray wp = step.getJSONArray("way_points");
                int startIdx = wp.getInt(0);

                // ── Make sure the instruction names the road ──
                boolean mentionsRoad =
                    instruction.contains(" onto ")
                    || instruction.contains(" on ");
                if (!mentionsRoad && typeCode != 10) {
                    String connector =
                        instruction.startsWith("Head")
                            ? " on " : " onto ";
                    String name = step.optString("name", "");
                    if (!name.isEmpty() && !name.equals("-")) {
                        instruction += connector + name;
                    } else {
                        // Unnamed way: describe what it is
                        String label = pathLabel(
                            wayTypes, startIdx);
                        if (label != null) {
                            instruction += connector
                                + "the " + label;
                        }
                    }
                }

                // A maneuver happens where its step STARTS
                GeoPoint at = points.isEmpty()
                    ? new GeoPoint(0.0, 0.0)
                    : points.get(Math.max(0, Math.min(
                        startIdx, points.size() - 1)));

                steps.add(new RouteStep(instruction,
                    orsTypeToString(typeCode), dist,
                    at.getLatitude(), at.getLongitude()));
            }
        }
        return steps;
    }

    // ORS step type codes:
    // 0 left, 1 right, 2 sharp left, 3 sharp right, 4 slight left,
    // 5 slight right, 6 straight, 7 enter roundabout,
    // 8 exit roundabout, 9 u-turn, 10 goal, 11 depart,
    // 12 keep left, 13 keep right
    private String orsTypeToString(int type) {
        switch (type) {
            case 0:  return "turn-left";
            case 1:  return "turn-right";
            case 2:  return "turn-left";
            case 3:  return "turn-right";
            case 4:  return "slight-left";
            case 5:  return "slight-right";
            case 6:  return "straight";
            case 7:
            case 8:  return "roundabout";
            case 9:  return "u-turn";
            case 10: return "arrive";
            case 11: return "straight";
            case 12: return "keep-left";
            case 13: return "keep-right";
            default: return "straight";
        }
    }

    // ════════════════════════════════════════
    // OSRM FALLBACK — absolute last resort
    // The public OSRM demo server only knows car routes, so the
    // result is validated against the freeway blacklist.
    // ════════════════════════════════════════

    private void tryOsrmRoute(final double fromLat,
                              final double fromLng,
                              final double toLat,
                              final double toLng) {
        debugToast("OSRM fallback...");
        try {
            String url =
                "https://router.project-osrm.org/route/v1/bike/"
                + fromLng + "," + fromLat + ";"
                + toLng + "," + toLat
                + "?overview=full&geometries=geojson&steps=true";

            Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "ScootRoute/1.0")
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    activity.runOnUiThread(() -> {
                        activity.onRouteFailed();
                        Toast.makeText(activity,
                            "Routing unavailable",
                            Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onResponse(Call call,
                        Response response) throws IOException {
                    if (response.body() == null) {
                        activity.runOnUiThread(() ->
                            activity.onRouteFailed());
                        return;
                    }
                    parseOsrmRoute(response.body().string(),
                        toLat, toLng);
                }
            });
        } catch (Exception e) {
            activity.runOnUiThread(() -> {
                activity.onRouteFailed();
                Toast.makeText(activity,
                    "Routing failed",
                    Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void parseOsrmRoute(String json,
                                double destLat,
                                double destLng) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray routes = root.getJSONArray("routes");
            JSONObject route = routes.getJSONObject(0);
            int durSecs = (int) route.getDouble("duration");

            JSONObject geometry = route.getJSONObject("geometry");
            JSONArray coords = geometry.getJSONArray("coordinates");
            List<GeoPoint> points = new ArrayList<>();
            for (int i = 0; i < coords.length(); i++) {
                JSONArray c = coords.getJSONArray(i);
                points.add(new GeoPoint(
                    c.getDouble(1), c.getDouble(0)));
            }

            List<RouteStep> steps = new ArrayList<>();
            JSONArray legs = route.getJSONArray("legs");
            for (int l = 0; l < legs.length(); l++) {
                JSONArray sa = legs.getJSONObject(l)
                    .getJSONArray("steps");
                for (int i = 0; i < sa.length(); i++) {
                    JSONObject step = sa.getJSONObject(i);
                    double dist = step.getDouble("distance");
                    String inst = "Continue";
                    String type = "straight";
                    double wpLat = destLat;
                    double wpLng = destLng;
                    if (step.has("maneuver")) {
                        JSONObject man =
                            step.getJSONObject("maneuver");
                        String mType = man.optString("type", "");
                        String mMod  = man.optString("modifier", "");
                        inst = formatOsrmInstruction(mType, mMod,
                            step.optString("name", ""));
                        type = osrmType(mType, mMod);
                        JSONArray loc =
                            man.optJSONArray("location");
                        if (loc != null && loc.length() >= 2) {
                            wpLng = loc.getDouble(0);
                            wpLat = loc.getDouble(1);
                        }
                    }
                    steps.add(new RouteStep(inst, type, dist,
                        wpLat, wpLng));
                }
            }

            // This server ignores "bike" and returns car routes,
            // so never show one that uses a freeway.
            if (routeContainsFreeway(steps) != null) {
                activity.runOnUiThread(() -> {
                    activity.onRouteFailed();
                    Toast.makeText(activity,
                        "Couldn't find a freeway-free route. "
                        + "Please try again in a moment.",
                        Toast.LENGTH_LONG).show();
                });
                return;
            }

            int mopedMins = calculateMopedTime(
                steps, durSecs, "osrm");
            final List<GeoPoint> fp = points;
            final List<RouteStep> fs = steps;
            final int fd = mopedMins;
            activity.runOnUiThread(() ->
                activity.onRouteReady(fp, fs, fd));

        } catch (Exception e) {
            activity.runOnUiThread(() -> {
                activity.onRouteFailed();
                Toast.makeText(activity,
                    "Could not parse route",
                    Toast.LENGTH_SHORT).show();
            });
        }
    }

    private String osrmType(String type, String modifier) {
        if ("arrive".equals(type)) return "arrive";
        if ("depart".equals(type)) return "straight";
        if (type.startsWith("roundabout")
                || type.equals("rotary")) return "roundabout";
        if ("uturn".equals(modifier)) return "u-turn";
        if ("sharp left".equals(modifier)
                || "left".equals(modifier)) return "turn-left";
        if ("sharp right".equals(modifier)
                || "right".equals(modifier)) return "turn-right";
        if ("slight left".equals(modifier)) return "slight-left";
        if ("slight right".equals(modifier)) return "slight-right";
        return "straight";
    }

    private List<GeoPoint> decodePolyline(String encoded) {
        List<GeoPoint> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return points;
        try {
            int index = 0, len = encoded.length();
            int lat = 0, lng = 0;
            while (index < len) {
                int b, shift = 0, result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                lat += ((result & 1) != 0
                    ? ~(result >> 1) : (result >> 1));
                shift = 0; result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                lng += ((result & 1) != 0
                    ? ~(result >> 1) : (result >> 1));
                points.add(new GeoPoint(lat / 1e5, lng / 1e5));
            }
        } catch (Exception ignored) {}
        return points;
    }

    private String formatOsrmInstruction(String type,
                                         String modifier,
                                         String name) {
        String road = name.isEmpty() ? "" : " onto " + name;
        switch (type) {
            case "turn":
            case "end of road":
                return "Turn " + modifier + road;
            case "fork":
                return "Keep " + modifier + road;
            case "new name":   return "Continue" + road;
            case "depart":
                return "Head " + modifier
                    + (name.isEmpty() ? "" : " on " + name);
            case "arrive":     return "Arrive at destination";
            case "merge":      return "Merge " + modifier + road;
            case "roundabout": return "Enter roundabout" + road;
            default:           return "Continue" + road;
        }
    }
}
