package com.example.hourlymove;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the alarm after the phone restarts (alarms are cleared on reboot). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Store store = new Store(context);
        // Nothing to re-arm if reminders are off or a round is already waiting for you.
        if (!store.enabled() || store.next() <= 0) {
            return;
        }
        long next = Math.max(store.next(), System.currentTimeMillis() + 5_000L);
        store.setNext(next);
        Scheduler.schedule(context, next);
    }
}
