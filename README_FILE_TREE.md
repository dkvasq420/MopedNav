# ScootRoute — project tree (v1.2 / versionCode 3) — FIXED BUILD

Copy each file into the same path inside /storage/internal_new/project/mopednavigator/

```
mopednavigator/
├── build.gradle                      (root — AGP 4.2.2)
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties   (Gradle 6.9)
└── app/
    ├── build.gradle                  (SDK 35, signing, AdMob, Firebase)
    ├── google-services.json          ← YOURS, already there. Not included.
    ├── scootroute.keystore           ← lives one level up, NOT in this zip
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/scootroute/mopednav/
        │   ├── AuthActivity.java          WaiverActivity.java
        │   ├── OnboardingActivity.java    MainActivity.java
        │   ├── RoutingManager.java        NavigationService.java
        │   ├── RouteStep.java             LogHelper.java
        │   ├── DashboardActivity.java     SettingsActivity.java
        │   ├── HazardReportActivity.java  HelpActivity.java
        │   ├── AccountActivity.java       MopedRentalActivity.java
        │   └── AdManager.java
        └── res/
            ├── layout/   activity_main / auth / waiver / onboarding / dashboard /
            │             settings / hazard_report / help / account / moped_rental .xml
            │             item_route_step / route_history / faq / rental_shop / rental_partner .xml
            ├── drawable/ bg_glass_card, bg_input_field, bg_nav_bar,
            │             btn_cyan_rounded, btn_outline_cyan .xml
            ├── values/   colors.xml, styles.xml
            └── mipmap-mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi/
                          ic_launcher.png, ic_launcher_round.png
```

## Before you build — 3 things only you can fill in
1. RoutingManager.java line ~36: put your real OpenRouteService key in ORS_API_KEY.
   (If your current file already has the key, copy it across before overwriting.)
2. app/build.gradle: replace both YOUR_KEYSTORE_PASSWORD values.
3. AdManager.java: uses Google's TEST ad IDs. Before the final production build,
   comment out the test IDs and uncomment the LIVE block right below them.

## Delete these if they exist (they cause "Duplicate resources" errors)
- res/values/res_values_styles.xml

## What changed in this version
- FIXED the build error: RouteStep.java had a leftover "com.yourname" package line.
- Compact main screen: one row for Get Directions + Start, origin card hides
  after a route, smaller instruction banner, bottom sheet stays collapsed so
  the map is visible (drag the handle up for the step list). Sheet is hidden
  until a route exists. Bottom-nav labels shrink to fit.
- Moped mode avoids bike paths by default (uses the "exclude bike-only paths"
  setting; Bicycle vehicle type allows them). If one is unavoidable you get a
  warning instead of a silent bike-path route.
- Unnamed steps now say what they are ("onto the bike path"). Per-step
  Nominatim lookups were removed.
- Turn-type fix: left turns now show the correct arrow and are spoken as
  "turn left" (they were shown as straight arrows / "continue straight").
- Voice/maneuver timing fixed, and "off route" now measures distance to the
  route line (it used to trigger on any long straight road).
- Debug toasts/dialogs only appear in debug builds, not in the release AAB.

## Quick test after installing
1. Route to a destination: map stays visible, banner is small, sheet collapsed.
2. Drag the handle up: step list appears. Tap a step: that road highlights.
3. Check a left turn shows a left arrow.
4. Moped mode: route should not use bike paths (or shows the warning toast).
5. Take a short real ride in Start Navigation: listen for turn announcements.
