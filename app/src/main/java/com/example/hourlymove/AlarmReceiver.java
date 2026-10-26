package com.example.hourlymove;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when a round is due: starts a fresh round, queues the next alarm, shows the notification. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Store store = new Store(context);
        if (!store.enabled()) {
            return;
        }

        // New round: everything is "not done" again.
        store.clearDone();

        // Keep reminders coming even if this round is ignored.
        long next = System.currentTimeMillis() + store.intervalMin() * 60_000L;
        store.setNext(next);
        Scheduler.schedule(context, next);

        Notifier.show(context, store);
    }
}
