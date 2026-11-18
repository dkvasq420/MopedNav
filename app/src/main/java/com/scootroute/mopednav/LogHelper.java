package com.scootroute.mopednav;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * LogHelper — writes to internal storage
 * which NEVER needs permission on any Android version
 *
 * Read log with:
 * cat /data/data/com.scootroute.mopednav/files/scootroute.txt
 *
 * Or from terminal using the app's files dir:
 * run-as com.scootroute.mopednav cat files/scootroute.txt
 */
public class LogHelper {

    private static final String TAG = "ScootRoute";
    private static final String LOG_FILE = "scootroute.txt";
    private static Context appContext;
    private static File logFile;

    public static void init(Context context) {
        appContext = context.getApplicationContext();

        // Internal storage — NEVER needs permission
        File dir = appContext.getFilesDir();
        logFile = new File(dir, LOG_FILE);

        // Clear old log on fresh start
        if (logFile.exists()) logFile.delete();

        log("═══════════════════════════════");
        log("ScootRoute started");
        log("Log file: " + logFile.getAbsolutePath());
        log("═══════════════════════════════");
    }

    public static void log(String message) {
        String timestamp = new SimpleDateFormat(
            "HH:mm:ss.SSS", Locale.getDefault())
            .format(new Date());
        String line = "[" + timestamp + "] " + message;

        // Always print to Android logcat
        Log.d(TAG, message);

        // Write to internal file
        if (logFile != null) {
            try {
                FileWriter fw = new FileWriter(logFile, true);
                fw.write(line + "\n");
                fw.close();
            } catch (Exception e) {
                Log.e(TAG, "Log write failed: "
                    + e.getMessage());
            }
        }
    }

    public static String getLogPath() {
        return logFile != null
            ? logFile.getAbsolutePath() : "not initialized";
    }

    public static void printLogLocations() {
        Log.d(TAG, "Log file path: " + getLogPath());
        log("Log path confirmed: " + getLogPath());
    }
}
