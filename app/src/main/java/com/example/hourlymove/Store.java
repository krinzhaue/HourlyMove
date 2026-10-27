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

    /** An exercise in your list, with its target number of reps. */
    static class Exercise {
        final String name;
        final int reps;

        Exercise(String name, int reps) {
            this.name = name;
            this.reps = reps;
        }
    }

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

    /** Reads the list. Exercises saved by older versions (plain names) get 12 reps. Old set counts are ignored. */
    List<Exercise> exercises() {
        List<Exercise> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("exercises", "[]"));
            for (int i = 0; i < a.length(); i++) {
                Object o = a.get(i);
                if (o instanceof JSONObject) {
                    JSONObject j = (JSONObject) o;
                    out.add(new Exercise(j.getString("n"), j.optInt("r", 12)));
                } else {
                    out.add(new Exercise(String.valueOf(o), 12));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    void setExercises(List<Exercise> list) {
        JSONArray a = new JSONArray();
        try {
            for (Exercise e : list) {
                JSONObject o = new JSONObject();
                o.put("n", e.name);
                o.put("r", e.reps);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        p.edit().putString("exercises", a.toString()).apply();
    }

    /** Keeps the current round (done state and its history entries) attached to a renamed exercise. */
    void renameExercise(String oldName, String newName) {
        if (oldName.equals(newName)) {
            return;
        }
        Map<String, Integer> m = done();
        if (m.containsKey(oldName)) {
            Integer reps = m.remove(oldName);
            m.put(newName, reps);
            saveDone(m);
        }
        try {
            JSONArray a = new JSONArray(p.getString("log", "[]"));
            long sid = sessionId();
            boolean changed = false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.getLong("s") == sid && o.getString("n").equals(oldName)) {
                    o.put("n", newName);
                    changed = true;
                }
            }
            if (changed) {
                p.edit().putString("log", a.toString()).apply();
            }
        } catch (Exception ignored) {
        }
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

    /** Changes the reps of a logged exercise. If it belongs to the current round, that round follows. */
    void setEntryReps(long session, String name, int reps) {
        try {
            JSONArray a = new JSONArray(p.getString("log", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.getLong("s") == session && o.getString("n").equals(name)) {
                    o.put("r", reps);
                }
            }
            p.edit().putString("log", a.toString()).apply();
        } catch (Exception ignored) {
        }
        if (session == sessionId()) {
            Map<String, Integer> m = done();
            if (m.containsKey(name)) {
                m.put(name, reps);
                saveDone(m);
            }
        }
    }

    /** Removes a logged exercise from the history. In the current round it becomes "not done" again. */
    void deleteEntry(long session, String name) {
        try {
            JSONArray a = new JSONArray(p.getString("log", "[]"));
            JSONArray kept = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!(o.getLong("s") == session && o.getString("n").equals(name))) {
                    kept.put(o);
                }
            }
            p.edit().putString("log", kept.toString()).apply();
        } catch (Exception ignored) {
        }
        if (session == sessionId()) {
            removeDone(name);
        }
    }

    /**
     * Reps from the most recent time this exercise was logged in any session,
     * or {@code fallback} if it has never been logged.
     */
    int lastReps(String name, int fallback) {
        long bestTime = -1L;
        int reps = fallback;
        for (Entry e : log()) {
            if (e.name.equals(name) && e.time > bestTime) {
                bestTime = e.time;
                reps = e.reps;
            }
        }
        return reps;
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
