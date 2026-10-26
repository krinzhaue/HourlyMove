package com.example.hourlymove;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Tiny wrapper around SharedPreferences: exercise list, interval, timer and today's progress. */
class Store {
    private final SharedPreferences p;

    Store(Context c) {
        p = c.getApplicationContext().getSharedPreferences("hourly_move", Context.MODE_PRIVATE);
    }

    // ---- exercise list ----

    List<String> exercises() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("exercises", "[]"));
            for (int i = 0; i < a.length(); i++) {
                out.add(a.getString(i));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    void setExercises(List<String> list) {
        p.edit().putString("exercises", new JSONArray(list).toString()).apply();
    }

    // ---- progress in the current round (exercise name -> reps) ----

    Map<String, Integer> done() {
        Map<String, Integer> m = new HashMap<>();
        try {
            JSONObject o = new JSONObject(p.getString("done", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                m.put(k, o.getInt(k));
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private void saveDone(Map<String, Integer> m) {
        p.edit().putString("done", new JSONObject(m).toString()).apply();
    }

    void markDone(String name, int reps) {
        Map<String, Integer> m = done();
        m.put(name, reps);
        saveDone(m);
    }

    void removeDone(String name) {
        Map<String, Integer> m = done();
        m.remove(name);
        saveDone(m);
    }

    void clearDone() {
        saveDone(new HashMap<String, Integer>());
    }

    // ---- settings / timer ----

    int intervalMin() {
        return p.getInt("interval_min", 60);
    }

    void setIntervalMin(int minutes) {
        p.edit().putInt("interval_min", minutes).apply();
    }

    boolean enabled() {
        return p.getBoolean("enabled", false);
    }

    void setEnabled(boolean on) {
        p.edit().putBoolean("enabled", on).apply();
    }

    /** Epoch millis of the next reminder. */
    long next() {
        return p.getLong("next", 0L);
    }

    void setNext(long millis) {
        p.edit().putLong("next", millis).apply();
    }
}
