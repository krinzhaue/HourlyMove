package com.example.hourlymove;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Builds and shows the exercise reminder notification. */
final class Notifier {
    static final String CHANNEL = "hourly_move";
    static final int ID = 1;

    private Notifier() {
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Exercise reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Reminders to do your exercises");
            nm.createNotificationChannel(ch);
        }
    }

    static void show(Context c, Store store) {
        ensureChannel(c);

        List<String> ex = store.exercises();
        String text;
        if (ex.isEmpty()) {
            text = "Open the app and add some exercises.";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ex.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(ex.get(i));
            }
            text = sb.toString();
        }

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Time to move!")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        c.getSystemService(NotificationManager.class).notify(ID, n);
    }

    static void cancel(Context c) {
        c.getSystemService(NotificationManager.class).cancel(ID);
    }
}
