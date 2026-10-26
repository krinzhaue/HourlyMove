# Hourly Move

A tiny Android app that reminds you to exercise at an interval you choose.

- Add your own list of exercises.
- Set the interval in minutes (default 60) and press **Start reminders**.
- Each round a notification appears; tapping it opens the app.
- Tap an exercise, enter reps, and it turns green and shows "Done".
- When every exercise is done, the countdown to the next round restarts.
- Reminders are one-shot: after a notification fires, no new reminder is scheduled until
  you finish the round (or press Stop, then Start).
- The **History** tab shows a month calendar; tap a day to see each session with its
  exercises and reps.

## Get the .apk

**Option A – GitHub (no installs)**
1. Create a free GitHub repository and upload everything in this folder
   (keep the `.github` folder).
2. Open the **Actions** tab, choose **Build APK**, click **Run workflow**.
3. When it finishes (about 3-5 minutes), download `hourly-move-apk` from the run page.
   Unzip it to get `app-debug.apk`.

**Option B – Android Studio**
Open this folder, wait for Gradle sync, then **Build > Build APK(s)**.

**Option C – Command line** (JDK 17 and the Android SDK installed)

    ./gradlew assembleDebug

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Install on your phone
Copy the APK to the phone, open it, and allow "install unknown apps" when asked.
On first launch, allow notifications.
