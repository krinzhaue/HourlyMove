package com.example.hourlymove;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fires when a round is due: starts a fresh round and shows the notification.
 * It does NOT schedule another alarm; the next countdown only starts once you finish the round.
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Store store = new Store(context);
        if (!store.enabled()) {
            return;
        }

        // New round: everything is "not done" again, and it is a new history session.
        store.clearDone();
        store.newSession();

        // 0 = no countdown running; waiting for you to finish this round.
        store.setNext(0L);

        Notifier.show(context, store);
    }
}
