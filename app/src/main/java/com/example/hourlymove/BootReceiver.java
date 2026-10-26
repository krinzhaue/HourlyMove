package com.example.hourlymove;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the alarm after the phone restarts (alarms are cleared on reboot). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Store store = new Store(context);
        if (!store.enabled()) {
            return;
        }
        long next = Math.max(store.next(), System.currentTimeMillis() + 5_000L);
        store.setNext(next);
        Scheduler.schedule(context, next);
    }
}
