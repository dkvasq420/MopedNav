package com.scootroute.mopednav;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.osmdroid.util.GeoPoint;

import java.util.List;
import java.util.Locale;

public class NavigationService extends Service {

    private static final String TAG = "NavService";
    private static final String CHANNEL_ID = "ScootRouteNav";
    private static final int NOTIF_ID = 1001;

    // ════════════════════════════════════════
    // ANNOUNCEMENT DISTANCES (in meters)
    // Adjusted based on current speed
    // ════════════════════════════════════════
    private static final float ANNOUNCE_PREPARE = 400f; // "In X, get ready"
    private static final float ANNOUNCE_FAR     = 200f; // "In X, turn left"
    private static final float ANNOUNCE_NEAR    = 80f;  // "Turn left ahead"
    private static final float ANNOUNCE_NOW     = 25f;  // "Turn left NOW"
    private static final float STEP_ADVANCE     = 18f;  // advance step
    private static final float ARRIVE_DIST     = 30f;  // destination reached

    // Off-route detection
    private static final float OFF_ROUTE_DIST   = 80f;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private List<RouteStep> steps;
    private int currentStepIndex = 0;
    private float currentSpeedMs = 0f;

    // Track which announcements fired per step
    private boolean announcedPrepare = false;
    private boolean announcedFar     = false;
    private boolean announcedNear    = false;
    private boolean announcedNow     = false;

    // Off route tracking
    private int offRouteCount = 0;
    private long lastRerouteMs = 0;
    private List<GeoPoint> routePoints;

    // Closest we have been to the current maneuver (meters),
    // so a fast ride-by still advances the step
    private float minDistThisStep = Float.MAX_VALUE;
    private static final int OFF_ROUTE_THRESHOLD = 3;

    private NavigationCallback callback;

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        NavigationService getService() {
            return NavigationService.this;
        }
    }

    public interface NavigationCallback {
        void onStepAdvanced(int newIndex);
        void onNavigationFinished();
        void onDistanceToNextStep(float distanceMeters);
        void onOffRoute();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initTts();
    }

    @Override
    public int onStartCommand(Intent intent,
                               int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification(
            "ScootRoute Active",
            "Navigation running"));
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ════════════════════════════════════════
    // TTS SETUP
    // ════════════════════════════════════════

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                if (result != TextToSpeech.LANG_MISSING_DATA
                        && result !=
                            TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsReady = true;
                    tts.setSpeechRate(0.92f);
                    tts.setPitch(1.0f);
                    Log.d(TAG, "TTS ready");
                }
            }
        });
    }

    // Interrupt current speech and say this
    private void speak(String text) {
        if (!ttsReady || tts == null) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH,
            null, "nav_" + System.currentTimeMillis());
        Log.d(TAG, "TTS: " + text);
    }

    // Queue after current speech
    private void speakQueued(String text) {
        if (!ttsReady || tts == null) return;
        tts.speak(text, TextToSpeech.QUEUE_ADD,
            null, "nav_q_" + System.currentTimeMillis());
    }

    // ════════════════════════════════════════
    // NAVIGATION CONTROL
    // ════════════════════════════════════════

    public void startNavigation(List<RouteStep> routeSteps,
                                 NavigationCallback cb) {
        startNavigation(routeSteps, cb, false);
    }

    // rerouted = true when replacing the route mid-ride
    public void startNavigation(List<RouteStep> routeSteps,
                                 NavigationCallback cb,
                                 boolean rerouted) {
        this.steps = routeSteps;
        this.callback = cb;
        // Step 0 is "Head north on X" (we are already there).
        // The first real maneuver is step 1.
        this.currentStepIndex =
            (steps != null && steps.size() > 1) ? 1 : 0;
        offRouteCount = 0;
        resetAnnouncements();

        if (steps != null && !steps.isEmpty()) {
            RouteStep depart = steps.get(0);
            speak((rerouted ? "New route. " : "Navigation started. ")
                + depart.instruction);
            if (steps.size() > 1) {
                RouteStep first = steps.get(1);
                int toFirst = (int) Math.round(depart.distanceMeters);
                speakQueued(buildAnnouncement(first, toFirst));
                markAnnounced(toFirst);
            }
            updateNotification(
                steps.get(currentStepIndex).instruction, "");
        }
    }

    // Route geometry, used to detect riding off the route
    public void setRoutePoints(List<GeoPoint> points) {
        this.routePoints = points;
        offRouteCount = 0;
    }

    // Don't repeat an announcement we already covered in the
    // "Then, in X, ..." message
    private void markAnnounced(float distMeters) {
        float sf = getSpeedFactor();
        if (distMeters <= ANNOUNCE_PREPARE * sf) announcedPrepare = true;
        if (distMeters <= ANNOUNCE_FAR * sf)     announcedFar = true;
        if (distMeters <= ANNOUNCE_NEAR * sf)    announcedNear = true;
    }

    public void stopNavigation() {
        speak("Navigation ended. Ride safe!");
        if (tts != null) tts.stop();
        stopForeground(true);
        stopSelf();
    }

    // ════════════════════════════════════════
    // LOCATION UPDATE
    // Called every second from MainActivity
    // ════════════════════════════════════════

    public void updateLocation(double lat, double lng,
                                float speedMs,
                                float bearing) {
        if (steps == null
                || currentStepIndex >= steps.size()) return;

        this.currentSpeedMs = speedMs;

        RouteStep currentStep = steps.get(currentStepIndex);

        // Distance to current waypoint
        float[] result = new float[1];
        android.location.Location.distanceBetween(
            lat, lng,
            currentStep.waypointLat,
            currentStep.waypointLng,
            result);
        float dist = result[0];

        // Notify UI
        if (callback != null)
            callback.onDistanceToNextStep(dist);

        // Check destination arrival
        boolean isLast =
            currentStepIndex == steps.size() - 1;
        if (isLast && dist < ARRIVE_DIST) {
            speak("You have arrived at your destination!"
                + " Great ride!");
            if (callback != null)
                callback.onNavigationFinished();
            return;
        }

        // Off-route detection
        checkOffRoute(lat, lng);

        // Speed-adjusted announcement distances
        float speedFactor = getSpeedFactor();

        // Make announcements
        makeAnnouncements(currentStep, dist, speedFactor);

        // Advance when we reach the maneuver, or have clearly
        // passed it (fast ride-bys can skip the small radius)
        if (dist < minDistThisStep) minDistThisStep = dist;
        boolean reached = dist < STEP_ADVANCE;
        boolean passed  = minDistThisStep < 40f
            && dist > minDistThisStep + 15f;
        if ((reached || passed) && !isLast) {
            advanceStep();
        }
    }

    private float getSpeedFactor() {
        // At 40kph (11 m/s) factor = 1.0 (normal)
        // At 20kph (5.5 m/s) factor = 0.7 (announce closer)
        // At 60kph (16 m/s) factor = 1.4 (announce earlier)
        if (currentSpeedMs <= 0) return 1.0f;
        return Math.max(0.5f,
            Math.min(2.0f, currentSpeedMs / 11.0f));
    }

    private void makeAnnouncements(RouteStep step,
                                    float dist,
                                    float speedFactor) {
        float prepareDist = ANNOUNCE_PREPARE * speedFactor;
        float farDist     = ANNOUNCE_FAR     * speedFactor;
        float nearDist    = ANNOUNCE_NEAR    * speedFactor;

        // Skip generic steps (just "Continue")
        boolean isActionStep = isActionStep(step);

        // Prepare announcement (~400m)
        if (!announcedPrepare
                && dist <= prepareDist
                && dist > farDist
                && isActionStep) {
            announcedPrepare = true;
            speak(buildAnnouncement(step,
                Math.round(dist)));
            updateNotification(step.instruction,
                formatDistanceFt(dist));
        }
        // Main announcement (~200m)
        else if (!announcedFar
                && dist <= farDist
                && dist > nearDist) {
            announcedFar = true;
            speak(buildAnnouncement(step,
                Math.round(dist)));
            updateNotification(step.instruction,
                formatDistanceFt(dist));
        }
        // Near announcement (~80m)
        else if (!announcedNear
                && dist <= nearDist
                && dist > ANNOUNCE_NOW) {
            announcedNear = true;
            // Short sharp reminder
            String nearText = buildNearAnnouncement(step);
            speak(nearText);
        }
        // NOW announcement (~25m)
        else if (!announcedNow
                && dist <= ANNOUNCE_NOW) {
            announcedNow = true;
            speak(buildNowAnnouncement(step));
        }
    }

    // ════════════════════════════════════════
    // ANNOUNCEMENT BUILDERS
    // Creates natural sounding directions
    // ════════════════════════════════════════

    private String buildAnnouncement(RouteStep step,
                                      int distMeters) {
        String action = getActionPhrase(step);
        String street = getStreetName(step.instruction);

        if (distMeters <= 0) {
            // First step — just say the action
            if (!street.isEmpty())
                return action + " onto " + street;
            return action;
        }

        String distText = formatDistanceFt(distMeters);

        // Build natural phrase
        if (!street.isEmpty()) {
            // "In 500 feet, turn left onto King Street"
            return "In " + distText + ", "
                + action + " onto " + street;
        } else {
            // "In 500 feet, turn left"
            return "In " + distText + ", " + action;
        }
    }

    private String buildNearAnnouncement(RouteStep step) {
        String action = getActionPhrase(step);
        String street = getStreetName(step.instruction);

        if (!street.isEmpty()) {
            // "Turn left onto King Street, ahead"
            return action + " onto " + street + ", ahead";
        }
        return action + " ahead";
    }

    private String buildNowAnnouncement(RouteStep step) {
        String action = getActionPhrase(step);
        String street = getStreetName(step.instruction);

        if (!street.isEmpty()) {
            return action + " onto " + street + ", now";
        }
        return action + ", now";
    }

    // Extract the action from the step type / instruction
    private String getActionPhrase(RouteStep step) {
        if (step == null) return "Continue";
        String raw = step.instruction == null ? "" : step.instruction;
        String instr = raw.toLowerCase();
        String type  = step.type != null
            ? step.type.toLowerCase() : "";

        switch (type) {
            case "turn-left":    return "Turn left";
            case "turn-right":   return "Turn right";
            case "slight-left":  return "Bear left";
            case "slight-right": return "Bear right";
            case "keep-left":    return "Keep left";
            case "keep-right":   return "Keep right";
            case "u-turn":       return "Make a U-turn";
            case "arrive":       return "Arrive at your destination";
            case "roundabout": {
                int i = raw.indexOf(" onto ");
                return i > 0 ? raw.substring(0, i) : raw;
            }
            default: break;
        }

        // Fall back to the instruction text
        if (instr.contains("turn left"))   return "Turn left";
        if (instr.contains("turn right"))  return "Turn right";
        if (instr.contains("keep left"))   return "Keep left";
        if (instr.contains("keep right"))  return "Keep right";
        if (instr.contains("u-turn"))      return "Make a U-turn";
        if (instr.contains("merge"))       return "Merge";
        if (instr.contains("roundabout"))  return "Enter the roundabout";
        if (instr.contains("arrive"))      return "Arrive at your destination";
        return "Continue";
    }

    // Extract street name from instruction
    // Handles "Turn left onto King Street" → "King Street"
    private String getStreetName(String instruction) {
        if (instruction == null) return "";

        // "onto X" pattern
        int idx = instruction.indexOf(" onto ");
        if (idx >= 0) {
            return instruction.substring(idx + 6).trim();
        }

        // "on X" pattern
        idx = instruction.indexOf(" on ");
        if (idx >= 0 && idx > 5) {
            String candidate =
                instruction.substring(idx + 4).trim();
            // Sanity check — must look like a street name
            if (candidate.length() > 2
                    && !candidate.startsWith("the")) {
                return candidate;
            }
        }

        return "";
    }

    // Is this step an actionable turn (not just continue)?
    private boolean isActionStep(RouteStep step) {
        if (step == null) return false;
        String type = step.type != null
            ? step.type.toLowerCase() : "";
        return !type.equals("straight")
            && !type.isEmpty()
            && !step.instruction.toLowerCase()
                .startsWith("continue");
    }

    // ════════════════════════════════════════
    // OFF ROUTE DETECTION
    // ════════════════════════════════════════

    private void checkOffRoute(double lat, double lng) {
        if (routePoints == null || routePoints.size() < 2) return;
        long now = System.currentTimeMillis();
        if (now - lastRerouteMs < 20000) return;

        double d = distanceToRouteMeters(lat, lng);
        if (d > OFF_ROUTE_DIST) {
            offRouteCount++;
            if (offRouteCount >= OFF_ROUTE_THRESHOLD) {
                offRouteCount = 0;
                lastRerouteMs = now;
                speak("Recalculating route.");
                if (callback != null) callback.onOffRoute();
            }
        } else {
            offRouteCount = 0;
        }
    }

    // Shortest distance from the rider to the route line (meters)
    private double distanceToRouteMeters(double lat, double lng) {
        double cosLat = Math.cos(Math.toRadians(lat));
        double best = Double.MAX_VALUE;
        GeoPoint first = routePoints.get(0);
        double ax = (first.getLongitude() - lng) * 111320.0 * cosLat;
        double ay = (first.getLatitude() - lat) * 110540.0;
        for (int i = 1; i < routePoints.size(); i++) {
            GeoPoint p = routePoints.get(i);
            double bx = (p.getLongitude() - lng) * 111320.0 * cosLat;
            double by = (p.getLatitude() - lat) * 110540.0;
            double dx = bx - ax, dy = by - ay;
            double len2 = dx * dx + dy * dy;
            double t = len2 == 0 ? 0
                : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
            double cx = ax + t * dx, cy = ay + t * dy;
            double dist = Math.sqrt(cx * cx + cy * cy);
            if (dist < best) best = dist;
            ax = bx; ay = by;
        }
        return best;
    }

    // ════════════════════════════════════════
    // STEP ADVANCEMENT
    // ════════════════════════════════════════

    private void advanceStep() {
        currentStepIndex++;
        resetAnnouncements();

        if (currentStepIndex >= steps.size()) return;

        RouteStep next = steps.get(currentStepIndex);
        // The next maneuver is as far away as the step we just
        // finished is long
        double toNext = steps.get(currentStepIndex - 1).distanceMeters;
        boolean isLast = currentStepIndex == steps.size() - 1;

        if (isLast) {
            speakQueued("Continue " + formatDistanceFt(toNext)
                + " to your destination");
        } else {
            String then = buildAnnouncement(
                next, (int) Math.round(toNext));
            if (then.length() > 0) {
                then = then.substring(0, 1).toLowerCase()
                    + then.substring(1);
            }
            speakQueued("Then, " + then);
        }
        markAnnounced((float) toNext);

        updateNotification(next.instruction,
            formatDistanceFt(toNext));

        if (callback != null)
            callback.onStepAdvanced(currentStepIndex);
    }

    private void resetAnnouncements() {
        announcedPrepare = false;
        announcedFar     = false;
        announcedNear    = false;
        announcedNow     = false;
        minDistThisStep  = Float.MAX_VALUE;
    }

    // ════════════════════════════════════════
    // DISTANCE FORMATTING
    // Uses feet for short distances (US standard)
    // Miles for long distances
    // ════════════════════════════════════════

    private String formatDistanceFt(double meters) {
        double feet = meters * 3.28084;
        if (feet < 500) {
            // Round to nearest 50 feet
            int rounded = (int)(Math.round(feet / 50.0) * 50);
            if (rounded <= 0) rounded = 50;
            return rounded + " feet";
        } else if (feet < 5280) {
            // Show in feet rounded to 100
            int rounded = (int)(Math.round(feet / 100.0) * 100);
            return rounded + " feet";
        } else {
            // Miles
            double miles = feet / 5280.0;
            if (miles < 10) {
                return String.format("%.1f miles", miles);
            }
            return (int)miles + " miles";
        }
    }

    // ════════════════════════════════════════
    // NOTIFICATION
    // ════════════════════════════════════════

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                new NotificationChannel(
                    CHANNEL_ID,
                    "ScootRoute Navigation",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            NotificationManager nm =
                getSystemService(NotificationManager.class);
            if (nm != null)
                nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title,
                                            String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT
                | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(
                android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }

    private void updateNotification(String instruction,
                                     String dist) {
        NotificationManager nm =
            (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE);
        if (nm != null) {
            String text = dist.isEmpty()
                ? instruction
                : "In " + dist + " — " + instruction;
            nm.notify(NOTIF_ID,
                buildNotification(instruction, text));
        }
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
