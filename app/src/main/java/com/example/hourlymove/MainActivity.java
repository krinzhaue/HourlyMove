package com.example.hourlymove;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private Store store;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // tabs
    private ScrollView todayView;
    private ScrollView historyView;
    private TextView tabToday;
    private TextView tabHistory;
    private int currentTab = 0;

    // "Today" tab
    private TextView roundInfo;
    private TextView countdown;
    private EditText intervalInput;
    private EditText newExercise;
    private Button toggle;
    private LinearLayout list;
    private long shownSession = -1;

    // "History" tab
    private YearMonth shownMonth = YearMonth.now();
    private LocalDate selectedDate = LocalDate.now();
    private TextView monthTitle;
    private LinearLayout calendarBox;
    private LinearLayout dayDetails;

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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Tapping the notification brings you to the exercises.
        showTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if (currentTab == 1) {
            renderHistory();
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------ UI shell + tabs

    private void buildUi() {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);

        FrameLayout content = new FrameLayout(this);
        todayView = buildToday();
        historyView = buildHistory();
        content.addView(todayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(historyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFFEEEEEE);
        tabToday = makeTab("Today", 0);
        tabHistory = makeTab("History", 1);
        bar.addView(tabToday, new LinearLayout.LayoutParams(0, dp(52), 1f));
        bar.addView(tabHistory, new LinearLayout.LayoutParams(0, dp(52), 1f));
        frame.addView(bar, matchWrap());

        setContentView(frame);
        showTab(0);
    }

    private TextView makeTab(String label, final int index) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setOnClickListener(v -> showTab(index));
        return t;
    }

    private void showTab(int index) {
        currentTab = index;
        todayView.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        historyView.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        styleTab(tabToday, index == 0);
        styleTab(tabHistory, index == 1);
        if (index == 1) {
            renderHistory();
        }
    }

    private void styleTab(TextView t, boolean on) {
        t.setTextColor(on ? 0xFF2E7D32 : 0xFF666666);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    // ------------------------------------------------------------------ Today tab

    private ScrollView buildToday() {
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
                // Only restart a countdown that is actually running.
                if (store.enabled() && store.next() > 0) {
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
                store.setNext(0L);
                Scheduler.cancel(this);
                Notifier.cancel(this);
            } else {
                if (!applyInterval()) {
                    return;
                }
                store.setEnabled(true);
                store.clearDone();
                store.newSession();
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

        return scroll;
    }

    private void refresh() {
        shownSession = store.sessionId();
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

    private void setBig(String text, float sizeSp) {
        countdown.setText(text);
        countdown.setTextSize(sizeSp);
    }

    private void updateCountdown() {
        // A new round may have started in the background (the alarm fired): redraw the list.
        if (store.sessionId() != shownSession) {
            refresh();
        }

        List<String> exercises = store.exercises();
        Map<String, Integer> done = store.done();

        if (!store.enabled()) {
            roundInfo.setText("Reminders are off");
            setBig("--:--", 48);
            return;
        }

        int doneCount = 0;
        for (String e : exercises) {
            if (done.containsKey(e)) {
                doneCount++;
            }
        }

        long next = store.next();
        if (next <= 0) {
            // The alarm fired and no countdown is running: waiting for you to finish the round.
            if (exercises.isEmpty()) {
                roundInfo.setText("Add your exercises below.");
            } else {
                roundInfo.setText("Done " + doneCount + " of " + exercises.size()
                        + " – tap an exercise to log reps. The next countdown starts when you finish.");
            }
            setBig("Time to move!", 32);
            return;
        }

        boolean allDone = !exercises.isEmpty() && doneCount == exercises.size();
        roundInfo.setText(allDone ? "Round complete ✓  Next reminder in" : "Next reminder in");
        long left = next - System.currentTimeMillis();
        setBig(left <= 0 ? "Due now" : format(left), 48);
    }

    // ------------------------------------------------------------------ Today actions

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
                    } catch (Exception err) {
                        reps = 0;
                    }
                    if (reps < 1) {
                        Toast.makeText(this, "Enter the number of reps", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean wasAll = allDone();
                    store.markDone(name, reps);
                    afterChange(wasAll);
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private boolean allDone() {
        List<String> exercises = store.exercises();
        Map<String, Integer> done = store.done();
        if (exercises.isEmpty()) {
            return false;
        }
        for (String e : exercises) {
            if (!done.containsKey(e)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Called after reps are logged or an exercise is removed. When the round has just become
     * complete, the countdown to the next round starts (or, with reminders off, the round is
     * saved and a fresh one begins).
     */
    private void afterChange(boolean wasAllDone) {
        if (allDone() && !wasAllDone) {
            Notifier.cancel(this);
            if (store.enabled()) {
                restartCountdown();
                Toast.makeText(this, "Round complete! Next reminder in " + store.intervalMin() + " min",
                        Toast.LENGTH_LONG).show();
            } else {
                store.clearDone();
                store.newSession();
                Toast.makeText(this, "Round saved to History", Toast.LENGTH_LONG).show();
            }
        }
        refresh();
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
                .setMessage("Your history for it is kept.")
                .setPositiveButton("Remove", (d, w) -> {
                    boolean wasAll = allDone();
                    List<String> exercises = store.exercises();
                    exercises.remove(name);
                    store.setExercises(exercises);
                    store.removeDone(name);
                    afterChange(wasAll);
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

    // ------------------------------------------------------------------ History tab

    private ScrollView buildHistory() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(16), dp(12), dp(32));
        scroll.addView(root);

        // Month navigation
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);

        Button prev = new Button(this);
        prev.setText("‹");
        prev.setOnClickListener(v -> {
            shownMonth = shownMonth.minusMonths(1);
            renderHistory();
        });
        nav.addView(prev);

        monthTitle = new TextView(this);
        monthTitle.setTextSize(20);
        monthTitle.setTypeface(Typeface.DEFAULT_BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        nav.addView(monthTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button next = new Button(this);
        next.setText("›");
        next.setOnClickListener(v -> {
            shownMonth = shownMonth.plusMonths(1);
            renderHistory();
        });
        nav.addView(next);
        root.addView(nav, matchWrap());

        // Weekday labels (Sunday first)
        LinearLayout weekdays = new LinearLayout(this);
        weekdays.setOrientation(LinearLayout.HORIZONTAL);
        weekdays.setPadding(0, dp(8), 0, dp(4));
        String[] labels = {"S", "M", "T", "W", "T", "F", "S"};
        for (String label : labels) {
            TextView t = new TextView(this);
            t.setText(label);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(0xFF666666);
            weekdays.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(weekdays, matchWrap());

        calendarBox = new LinearLayout(this);
        calendarBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(calendarBox, matchWrap());

        dayDetails = new LinearLayout(this);
        dayDetails.setOrientation(LinearLayout.VERTICAL);
        dayDetails.setPadding(dp(8), dp(20), dp(8), 0);
        root.addView(dayDetails, matchWrap());

        return scroll;
    }

    private void renderHistory() {
        monthTitle.setText(shownMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault())
                + " " + shownMonth.getYear());

        List<Store.Entry> log = store.log();
        Set<LocalDate> daysWithData = new HashSet<>();
        for (Store.Entry e : log) {
            daysWithData.add(dateOf(e));
        }

        calendarBox.removeAllViews();
        LocalDate today = LocalDate.now();
        int offset = shownMonth.atDay(1).getDayOfWeek().getValue() % 7; // Sunday = 0
        int length = shownMonth.lengthOfMonth();
        int cell = 1 - offset; // day number shown in the first cell (<= 0 means blank)

        while (cell <= length) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 7; c++, cell++) {
                TextView tv = new TextView(this);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(16);
                if (cell >= 1 && cell <= length) {
                    final LocalDate date = shownMonth.atDay(cell);
                    tv.setText(String.valueOf(cell));
                    boolean has = daysWithData.contains(date);
                    boolean selected = date.equals(selectedDate);
                    if (selected) {
                        tv.setBackgroundColor(0xFF2E7D32);
                        tv.setTextColor(0xFFFFFFFF);
                        tv.setTypeface(Typeface.DEFAULT_BOLD);
                    } else if (has) {
                        tv.setBackgroundColor(0xFFC8E6C9);
                        tv.setTextColor(0xFF000000);
                    } else {
                        tv.setTextColor(0xFF444444);
                    }
                    if (date.equals(today) && !selected) {
                        tv.setTypeface(Typeface.DEFAULT_BOLD);
                        tv.setTextColor(0xFF1565C0);
                    }
                    tv.setOnClickListener(v -> {
                        selectedDate = date;
                        renderHistory();
                    });
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
                lp.setMargins(dp(1), dp(1), dp(1), dp(1));
                row.addView(tv, lp);
            }
            calendarBox.addView(row, matchWrap());
        }

        renderDay(log);
    }

    private void renderDay(List<Store.Entry> log) {
        dayDetails.removeAllViews();

        TextView title = new TextView(this);
        title.setText(selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(8));
        dayDetails.addView(title, matchWrap());

        // Group this day's entries by session, in the order they happened.
        Map<Long, List<Store.Entry>> sessions = new LinkedHashMap<>();
        for (Store.Entry e : log) {
            if (!dateOf(e).equals(selectedDate)) {
                continue;
            }
            List<Store.Entry> group = sessions.get(e.session);
            if (group == null) {
                group = new ArrayList<>();
                sessions.put(e.session, group);
            }
            group.add(e);
        }

        if (sessions.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("Nothing logged this day.");
            none.setTextSize(16);
            none.setTextColor(0xFF666666);
            dayDetails.addView(none, matchWrap());
            return;
        }

        int n = 1;
        for (List<Store.Entry> group : sessions.values()) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFFF1F3F4);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));

            TextView head = new TextView(this);
            head.setText("Session " + n + " · " + timeOf(group.get(0)));
            head.setTextSize(16);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setTextColor(0xFF2E7D32);
            card.addView(head, matchWrap());

            for (Store.Entry e : group) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(6), 0, 0);

                TextView name = new TextView(this);
                name.setText(e.name);
                name.setTextSize(16);
                row.addView(name, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView reps = new TextView(this);
                reps.setText(e.reps + (e.reps == 1 ? " rep" : " reps"));
                reps.setTextSize(16);
                reps.setTypeface(Typeface.DEFAULT_BOLD);
                row.addView(reps);

                card.addView(row, matchWrap());
            }

            LinearLayout.LayoutParams lp = matchWrap();
            lp.bottomMargin = dp(10);
            dayDetails.addView(card, lp);
            n++;
        }
    }

    private static LocalDate dateOf(Store.Entry e) {
        return Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static String timeOf(Store.Entry e) {
        return Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
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
