package com.example.hourlymove;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private Store store;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView roundInfo;
    private TextView countdown;
    private EditText intervalInput;
    private EditText newExercise;
    private Button toggle;
    private LinearLayout list;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        Notifier.ensureChannel(this);
        buildUi();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(root);

        roundInfo = new TextView(this);
        roundInfo.setTextSize(16);
        roundInfo.setGravity(Gravity.CENTER);
        root.addView(roundInfo, matchWrap());

        countdown = new TextView(this);
        countdown.setTextSize(48);
        countdown.setTypeface(Typeface.DEFAULT_BOLD);
        countdown.setGravity(Gravity.CENTER);
        countdown.setPadding(0, dp(8), 0, dp(16));
        root.addView(countdown, matchWrap());

        // Interval row
        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setOrientation(LinearLayout.HORIZONTAL);
        intervalRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView every = new TextView(this);
        every.setText("Remind me every");
        every.setTextSize(16);
        intervalRow.addView(every);

        intervalInput = new EditText(this);
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        intervalInput.setGravity(Gravity.CENTER);
        intervalInput.setText(String.valueOf(store.intervalMin()));
        intervalRow.addView(intervalInput,
                new LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView min = new TextView(this);
        min.setText("min");
        min.setTextSize(16);
        intervalRow.addView(min);

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> {
            if (applyInterval()) {
                if (store.enabled()) {
                    restartCountdown();
                }
                Toast.makeText(this, "Saved: every " + store.intervalMin() + " min",
                        Toast.LENGTH_SHORT).show();
                updateCountdown();
            }
        });
        intervalRow.addView(save);
        root.addView(intervalRow, matchWrap());

        // Start / stop
        toggle = new Button(this);
        toggle.setOnClickListener(v -> {
            if (store.enabled()) {
                store.setEnabled(false);
                Scheduler.cancel(this);
                Notifier.cancel(this);
            } else {
                if (!applyInterval()) {
                    return;
                }
                store.setEnabled(true);
                restartCountdown();
                Toast.makeText(this, "First reminder in " + store.intervalMin() + " min",
                        Toast.LENGTH_SHORT).show();
            }
            refresh();
            updateCountdown();
        });
        root.addView(toggle, matchWrap());

        // Exercise list
        TextView header = new TextView(this);
        header.setText("Your exercises");
        header.setTextSize(18);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, dp(24), 0, dp(8));
        root.addView(header, matchWrap());

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, matchWrap());

        // Add row
        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addRow.setPadding(0, dp(12), 0, 0);

        newExercise = new EditText(this);
        newExercise.setHint("New exercise (e.g. Push-ups)");
        newExercise.setSingleLine(true);
        newExercise.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        addRow.addView(newExercise, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button add = new Button(this);
        add.setText("Add");
        add.setOnClickListener(v -> addExercise());
        addRow.addView(add);
        root.addView(addRow, matchWrap());

        setContentView(scroll);
    }

    private void refresh() {
        toggle.setText(store.enabled() ? "Stop reminders" : "Start reminders");

        list.removeAllViews();
        List<String> exercises = store.exercises();
        Map<String, Integer> done = store.done();

        if (exercises.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No exercises yet. Add one below.");
            empty.setTextSize(16);
            list.addView(empty, matchWrap());
        }

        for (final String name : exercises) {
            Integer reps = done.get(name);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(reps != null ? 0xFFE3F4E5 : 0xFFF1F3F4);
            row.setPadding(dp(12), dp(4), dp(4), dp(4));
            row.setClickable(true);
            row.setOnClickListener(v -> askReps(name));

            TextView nameView = new TextView(this);
            nameView.setText(name);
            nameView.setTextSize(18);
            nameView.setPadding(0, dp(14), dp(8), dp(14));
            row.addView(nameView, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView status = new TextView(this);
            if (reps != null) {
                status.setText("✓ Done · " + reps + " reps");
                status.setTextColor(0xFF2E7D32);
                status.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                status.setText("Tap to log");
                status.setTextColor(0xFF666666);
            }
            status.setTextSize(14);
            row.addView(status);

            TextView remove = new TextView(this);
            remove.setText("✕");
            remove.setTextSize(18);
            remove.setPadding(dp(14), dp(14), dp(14), dp(14));
            remove.setOnClickListener(v -> confirmRemove(name));
            row.addView(remove);

            LinearLayout.LayoutParams lp = matchWrap();
            lp.bottomMargin = dp(8);
            list.addView(row, lp);
        }
    }

    private void updateCountdown() {
        List<String> exercises = store.exercises();
        Map<String, Integer> done = store.done();

        if (!store.enabled()) {
            roundInfo.setText("Reminders are off");
            countdown.setText("--:--");
            return;
        }

        int doneCount = 0;
        for (String e : exercises) {
            if (done.containsKey(e)) {
                doneCount++;
            }
        }

        if (exercises.isEmpty()) {
            roundInfo.setText("Add an exercise below. Next reminder in");
        } else if (doneCount == exercises.size()) {
            roundInfo.setText("Round complete ✓  Next round in");
        } else {
            roundInfo.setText("Done " + doneCount + " of " + exercises.size()
                    + " – tap an exercise to log reps. Next reminder in");
        }

        long left = store.next() - System.currentTimeMillis();
        countdown.setText(left <= 0 ? "Due now" : format(left));
    }

    // ------------------------------------------------------------ actions

    private void askReps(final String name) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Reps");
        Integer prev = store.done().get(name);
        if (prev != null) {
            input.setText(String.valueOf(prev));
            input.setSelection(input.getText().length());
        }

        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage("How many reps did you do?")
                .setView(box)
                .setPositiveButton("Done", (d, w) -> {
                    int reps;
                    try {
                        reps = Integer.parseInt(input.getText().toString().trim());
                    } catch (Exception ex) {
                        reps = 0;
                    }
                    if (reps < 1) {
                        Toast.makeText(this, "Enter the number of reps", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    store.markDone(name, reps);
                    onLogged();
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    /** Called after reps are saved. When every exercise is done, the countdown to the next round restarts. */
    private void onLogged() {
        refresh();
        List<String> exercises = store.exercises();
        Map<String, Integer> done = store.done();

        boolean all = !exercises.isEmpty();
        for (String e : exercises) {
            if (!done.containsKey(e)) {
                all = false;
                break;
            }
        }

        if (all) {
            Notifier.cancel(this);
            if (store.enabled()) {
                restartCountdown();
                Toast.makeText(this, "Round complete! Next round in " + store.intervalMin() + " min",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Round complete!", Toast.LENGTH_LONG).show();
            }
        }
        updateCountdown();
    }

    private void addExercise() {
        String name = newExercise.getText().toString().trim();
        if (name.isEmpty()) {
            return;
        }
        List<String> exercises = store.exercises();
        for (String e : exercises) {
            if (e.equalsIgnoreCase(name)) {
                Toast.makeText(this, "Already in your list", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        exercises.add(name);
        store.setExercises(exercises);
        newExercise.setText("");
        refresh();
        updateCountdown();
    }

    private void confirmRemove(final String name) {
        new AlertDialog.Builder(this)
                .setTitle("Remove \"" + name + "\"?")
                .setPositiveButton("Remove", (d, w) -> {
                    List<String> exercises = store.exercises();
                    exercises.remove(name);
                    store.setExercises(exercises);
                    store.removeDone(name);
                    refresh();
                    updateCountdown();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Reads and saves the interval field. Returns false (with a message) if it is invalid. */
    private boolean applyInterval() {
        int minutes;
        try {
            minutes = Integer.parseInt(intervalInput.getText().toString().trim());
        } catch (Exception e) {
            minutes = 0;
        }
        if (minutes < 1) {
            Toast.makeText(this, "Enter the interval in minutes (1 or more)", Toast.LENGTH_SHORT).show();
            return false;
        }
        store.setIntervalMin(minutes);
        return true;
    }

    private void restartCountdown() {
        long next = System.currentTimeMillis() + store.intervalMin() * 60_000L;
        store.setNext(next);
        Scheduler.schedule(this, next);
    }

    // ------------------------------------------------------------ helpers

    private static String format(long millis) {
        long total = (millis + 999) / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
