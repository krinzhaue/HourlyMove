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

/** Tiny wrapper around SharedPreferences: exercise list, interval, timer, progress and history. */
class Store {

    /** One logged exercise in the history. */
    static class Entry {
        final long time;     // epoch millis when first logged
        final long session;  // which round/session it belongs to
        final String name;
        final int reps;

        Entry(long time, long session, String name, int reps) {
            this.time = time;
            this.session = session;
            this.name = name;
            this.reps = reps;
        }
    }

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

    /** Marks an exercise done in the current round and records it in the history. */
    void markDone(String name, int reps) {
        Map<String, Integer> m = done();
        m.put(name, reps);
        saveDone(m);
        logReps(name, reps);
    }

    void removeDone(String name) {
        Map<String, Integer> m = done();
        m.remove(name);
        saveDone(m);
    }

    void clearDone() {
        saveDone(new HashMap<String, Integer>());
    }

    // ---- sessions + history ----

    long sessionId() {
        return p.getLong("session", 0L);
    }

    /** Starts a new session: later logs are grouped separately from earlier ones. */
    void newSession() {
        p.edit().putLong("session", sessionId() + 1).apply();
    }

    List<Entry> log() {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("log", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Entry(o.getLong("t"), o.getLong("s"), o.getString("n"), o.getInt("r")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Adds an entry to the history, or updates the reps if this exercise is already logged in this session. */
    private void logReps(String name, int reps) {
        long sid = sessionId();
        try {
            JSONArray a = new JSONArray(p.getString("log", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.getLong("s") == sid && o.getString("n").equals(name)) {
                    o.put("r", reps);
                    p.edit().putString("log", a.toString()).apply();
                    return;
                }
            }
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("s", sid);
            o.put("n", name);
            o.put("r", reps);
            a.put(o);
            p.edit().putString("log", a.toString()).apply();
        } catch (Exception ignored) {
        }
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

    /**
     * Epoch millis of the next reminder, or 0 when no countdown is running
     * (i.e. a round is waiting for you to finish it).
     */
    long next() {
        return p.getLong("next", 0L);
    }

    void setNext(long millis) {
        p.edit().putLong("next", millis).apply();
    }
}
