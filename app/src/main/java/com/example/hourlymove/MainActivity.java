package com.example.hourlymove;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

    // ---- palette ----
    private static final int BG = 0xFF000000;
    private static final int SURFACE = 0xFF1A1A1A;
    private static final int SURFACE_DONE = 0xFF222E10;
    private static final int LIME = 0xFFA9CC3C;
    private static final int LIME_DARK = 0xFF3D4D15;
    private static final int ON_LIME = 0xFF11140A;
    private static final int TEXT = 0xFFEDEDED;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    private static final int TRACK = 0xFF2B2D28;

    private Store store;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // tabs
    private ScrollView todayView;
    private ScrollView historyView;
    private NavItem tabToday;
    private NavItem tabHistory;
    private int currentTab = 0;

    // "Today" tab
    private RingView ring;
    private TextView countdown;
    private TextView subtitle;
    private TextView roundInfo;
    private EditText intervalInput;
    private EditText newExercise;
    private PillButton toggle;
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

    // ------------------------------------------------------------------ custom views

    /** The circular countdown: a dark track with a lime arc showing the time left. */
    private static class RingView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float fraction = 0f;

        RingView(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(6 * d);
            track.setColor(TRACK);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(10 * d);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setColor(LIME);
        }

        void setFraction(float f) {
            fraction = Math.max(0f, Math.min(1f, f));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float pad = arc.getStrokeWidth();
            oval.set(pad, pad, getWidth() - pad, getHeight() - pad);
            canvas.drawOval(oval, track);
            if (fraction > 0f) {
                canvas.drawArc(oval, -90f, 360f * fraction, false, arc);
            }
        }
    }

    /** Full-width lime pill button with optional leading icon. */
    private static class PillButton extends LinearLayout {
        private final TextView label;

        PillButton(Context c, String text, int iconRes, boolean compact) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setClickable(true);
            setFocusable(true);
            setBackground(rippleBg(d, LIME, 100, 0x44000000));
            int pad = (int) ((text.isEmpty() ? 12 : (compact ? 20 : 24)) * d);
            setPadding(pad, 0, pad, 0);
            setMinimumHeight((int) ((compact ? 44 : 52) * d));

            if (iconRes != 0) {
                ImageView icon = new ImageView(c);
                icon.setImageResource(iconRes);
                icon.setColorFilter(ON_LIME);
                LinearLayout.LayoutParams ilp =
                        new LinearLayout.LayoutParams((int) (20 * d), (int) (20 * d));
                if (!text.isEmpty()) {
                    ilp.setMarginEnd((int) (8 * d));
                }
                addView(icon, ilp);
            }

            label = new TextView(c);
            label.setText(text);
            label.setTextColor(ON_LIME);
            label.setTextSize(16);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            addView(label);
        }

        void setLabel(String t) {
            label.setText(t);
        }
    }

    /** Bottom navigation item: icon in a pill highlight when selected, label underneath. */
    private static class NavItem extends LinearLayout {
        private final float d;
        private final LinearLayout pill;
        private final ImageView icon;
        private final TextView label;

        NavItem(Context c, String text, int iconRes) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setClickable(true);
            setPadding(0, (int) (12 * d), 0, (int) (16 * d));

            pill = new LinearLayout(c);
            pill.setGravity(Gravity.CENTER);
            icon = new ImageView(c);
            icon.setImageResource(iconRes);
            pill.addView(icon, new LinearLayout.LayoutParams((int) (24 * d), (int) (24 * d)));
            addView(pill, new LinearLayout.LayoutParams((int) (64 * d), (int) (32 * d)));

            label = new TextView(c);
            label.setText(text);
            label.setTextSize(13);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (4 * d);
            addView(label, lp);

            setActive(false);
        }

        void setActive(boolean on) {
            if (on) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(LIME_DARK);
                g.setCornerRadius(100 * d);
                pill.setBackground(g);
            } else {
                pill.setBackground(null);
            }
            icon.setColorFilter(on ? LIME : 0xFFB3B3B3);
            label.setTextColor(on ? LIME : 0xFFB3B3B3);
            label.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    // ------------------------------------------------------------------ UI shell + tabs

    private void buildUi() {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setBackgroundColor(BG);

        FrameLayout content = new FrameLayout(this);
        todayView = buildToday();
        historyView = buildHistory();
        content.addView(todayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(historyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View divider = new View(this);
        divider.setBackgroundColor(0xFF1F1F1F);
        frame.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(BG);
        tabToday = new NavItem(this, "Today", R.drawable.ic_home);
        tabToday.setOnClickListener(v -> showTab(0));
        tabHistory = new NavItem(this, "History", R.drawable.ic_history);
        tabHistory.setOnClickListener(v -> showTab(1));
        bar.addView(tabToday, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(tabHistory, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        frame.addView(bar, matchWrap());

        setContentView(frame);
        showTab(0);
    }

    private void showTab(int index) {
        currentTab = index;
        todayView.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        historyView.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabToday.setActive(index == 0);
        tabHistory.setActive(index == 1);
        if (index == 1) {
            renderHistory();
        }
    }

    // ------------------------------------------------------------------ Today tab

    private ScrollView buildToday() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(32));
        scroll.addView(root);

        // Ring clock
        FrameLayout ringBox = new FrameLayout(this);
        ring = new RingView(this);
        ringBox.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(30), 0, dp(30), 0);

        countdown = new TextView(this);
        countdown.setTextColor(TEXT);
        countdown.setTypeface(Typeface.DEFAULT_BOLD);
        countdown.setGravity(Gravity.CENTER);
        center.addView(countdown, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        subtitle = new TextView(this);
        subtitle.setTextSize(14);
        subtitle.setTextColor(TEXT_DIM);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(4);
        center.addView(subtitle, slp);

        ringBox.addView(center, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(240), dp(240));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(ringBox, rlp);

        roundInfo = new TextView(this);
        roundInfo.setTextSize(14);
        roundInfo.setTextColor(TEXT_DIM);
        roundInfo.setGravity(Gravity.CENTER);
        roundInfo.setPadding(dp(8), dp(14), dp(8), dp(20));
        root.addView(roundInfo, matchWrap());

        // Interval row
        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setOrientation(LinearLayout.HORIZONTAL);
        intervalRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView every = new TextView(this);
        every.setText("Remind every");
        every.setTextSize(16);
        every.setTextColor(0xFFCCCCCC);
        intervalRow.addView(every);

        intervalInput = new EditText(this);
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        intervalInput.setGravity(Gravity.CENTER);
        intervalInput.setSingleLine(true);
        intervalInput.setText(String.valueOf(store.intervalMin()));
        styleField(intervalInput, SURFACE);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                dp(72), ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(dp(10), 0, dp(10), 0);
        intervalRow.addView(intervalInput, ilp);

        TextView min = new TextView(this);
        min.setText("min");
        min.setTextSize(16);
        min.setTextColor(0xFFCCCCCC);
        intervalRow.addView(min);

        View spacer = new View(this);
        intervalRow.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));

        PillButton save = new PillButton(this, "Save", 0, true);
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
        intervalRow.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(intervalRow, matchWrap());

        // Start / stop
        toggle = new PillButton(this, "Start reminders", 0, false);
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
        LinearLayout.LayoutParams tlp = matchWrap();
        tlp.topMargin = dp(16);
        root.addView(toggle, tlp);

        // Exercise list
        TextView header = new TextView(this);
        header.setText("Your exercises");
        header.setTextSize(18);
        header.setTextColor(TEXT);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, dp(28), 0, dp(10));
        root.addView(header, matchWrap());

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, matchWrap());

        // Add exercise
        newExercise = new EditText(this);
        newExercise.setHint("New exercise (e.g. Push-ups)");
        newExercise.setSingleLine(true);
        newExercise.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        styleField(newExercise, SURFACE);
        LinearLayout.LayoutParams nlp = matchWrap();
        nlp.topMargin = dp(8);
        root.addView(newExercise, nlp);

        PillButton add = new PillButton(this, "Add exercise", R.drawable.ic_add, false);
        add.setOnClickListener(v -> addExercise());
        LinearLayout.LayoutParams alp = matchWrap();
        alp.topMargin = dp(12);
        root.addView(add, alp);

        return scroll;
    }

    private void refresh() {
        shownSession = store.sessionId();
        toggle.setLabel(store.enabled() ? "Stop reminders" : "Start reminders");

        list.removeAllViews();
        List<String> exercises = store.exercises();
        Map<String, Integer> done = store.done();

        if (exercises.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No exercises yet. Add one below.");
            empty.setTextSize(16);
            empty.setTextColor(TEXT_DIM);
            list.addView(empty, matchWrap());
        }

        for (final String name : exercises) {
            Integer reps = done.get(name);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(rippleBg(density(), reps != null ? SURFACE_DONE : SURFACE, 20, 0x22FFFFFF));
            row.setPadding(dp(16), dp(2), dp(4), dp(2));
            row.setClickable(true);
            row.setOnClickListener(v -> askReps(name));

            TextView nameView = new TextView(this);
            nameView.setText(name);
            nameView.setTextSize(18);
            nameView.setTextColor(TEXT);
            nameView.setPadding(0, dp(14), dp(8), dp(14));
            row.addView(nameView, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView status = new TextView(this);
            if (reps != null) {
                status.setText("✓ Done · " + reps + " reps");
                status.setTextColor(LIME);
                status.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                status.setText("Tap to log");
                status.setTextColor(TEXT_DIM);
            }
            status.setTextSize(14);
            row.addView(status);

            TextView remove = new TextView(this);
            remove.setText("✕");
            remove.setTextSize(18);
            remove.setTextColor(TEXT_DIM);
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
            ring.setFraction(0f);
            setBig("--:--", 44);
            subtitle.setText("reminders off");
            roundInfo.setText("Press Start reminders to begin.");
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
            ring.setFraction(1f);
            setBig("Time to\nmove!", 30);
            subtitle.setText("finish your round");
            if (exercises.isEmpty()) {
                roundInfo.setText("Add your exercises below.");
            } else {
                roundInfo.setText("Done " + doneCount + " of " + exercises.size()
                        + " – tap an exercise to log reps. The next countdown starts when you finish.");
            }
            return;
        }

        boolean allDone = !exercises.isEmpty() && doneCount == exercises.size();
        long left = next - System.currentTimeMillis();
        long total = Math.max(1L, store.intervalMin() * 60_000L);
        ring.setFraction(left <= 0 ? 0f : (float) left / total);

        if (left <= 0) {
            setBig("Due now", 32);
        } else {
            String t = format(left);
            setBig(t, t.length() > 5 ? 36 : 44);
        }
        subtitle.setText("until next round");
        roundInfo.setText(allDone ? "Round complete ✓" : "");
    }

    // ------------------------------------------------------------------ Today actions

    private void askReps(final String name) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Reps");
        input.setSingleLine(true);
        styleField(input, 0xFF2C2C2C);
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
        root.setPadding(dp(12), dp(20), dp(12), dp(32));
        scroll.addView(root);

        // Month navigation
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);

        PillButton prev = new PillButton(this, "", R.drawable.ic_chevron_left, true);
        prev.setOnClickListener(v -> {
            shownMonth = shownMonth.minusMonths(1);
            renderHistory();
        });
        nav.addView(prev, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        monthTitle = new TextView(this);
        monthTitle.setTextSize(20);
        monthTitle.setTextColor(TEXT);
        monthTitle.setTypeface(Typeface.DEFAULT_BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        nav.addView(monthTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        PillButton next = new PillButton(this, "", R.drawable.ic_chevron_right, true);
        next.setOnClickListener(v -> {
            shownMonth = shownMonth.plusMonths(1);
            renderHistory();
        });
        nav.addView(next, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(nav, matchWrap());

        // Weekday labels (Sunday first)
        LinearLayout weekdays = new LinearLayout(this);
        weekdays.setOrientation(LinearLayout.HORIZONTAL);
        weekdays.setPadding(0, dp(16), 0, dp(6));
        String[] labels = {"S", "M", "T", "W", "T", "F", "S"};
        for (String label : labels) {
            TextView t = new TextView(this);
            t.setText(label);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(TEXT_DIM);
            weekdays.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(weekdays, matchWrap());

        calendarBox = new LinearLayout(this);
        calendarBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(calendarBox, matchWrap());

        dayDetails = new LinearLayout(this);
        dayDetails.setOrientation(LinearLayout.VERTICAL);
        dayDetails.setPadding(dp(8), dp(24), dp(8), 0);
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
                    boolean isToday = date.equals(today);
                    if (selected) {
                        tv.setBackground(rounded(LIME, 100));
                        tv.setTextColor(ON_LIME);
                        tv.setTypeface(Typeface.DEFAULT_BOLD);
                    } else if (isToday) {
                        GradientDrawable g = rounded(has ? LIME_DARK : 0x00000000, 100);
                        g.setStroke(dp(2), LIME);
                        tv.setBackground(g);
                        tv.setTextColor(LIME);
                        tv.setTypeface(Typeface.DEFAULT_BOLD);
                    } else if (has) {
                        tv.setBackground(rounded(LIME_DARK, 100));
                        tv.setTextColor(TEXT);
                    } else {
                        tv.setTextColor(0xFFBDBDBD);
                    }
                    tv.setOnClickListener(v -> {
                        selectedDate = date;
                        renderHistory();
                    });
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
                lp.setMargins(dp(2), dp(2), dp(2), dp(2));
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
        title.setTextColor(TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(10));
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
            none.setTextColor(TEXT_DIM);
            dayDetails.addView(none, matchWrap());
            return;
        }

        int n = 1;
        for (List<Store.Entry> group : sessions.values()) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(rounded(SURFACE, 20));
            card.setPadding(dp(16), dp(14), dp(16), dp(14));

            TextView head = new TextView(this);
            head.setText("Session " + n + " · " + timeOf(group.get(0)));
            head.setTextSize(16);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setTextColor(LIME);
            card.addView(head, matchWrap());

            for (Store.Entry e : group) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(8), 0, 0);

                TextView name = new TextView(this);
                name.setText(e.name);
                name.setTextSize(16);
                name.setTextColor(TEXT);
                row.addView(name, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView reps = new TextView(this);
                reps.setText(e.reps + (e.reps == 1 ? " rep" : " reps"));
                reps.setTextSize(16);
                reps.setTextColor(TEXT);
                reps.setTypeface(Typeface.DEFAULT_BOLD);
                row.addView(reps);

                card.addView(row, matchWrap());
            }

            LinearLayout.LayoutParams lp = matchWrap();
            lp.bottomMargin = dp(12);
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

    private static Drawable rippleBg(float density, int fill, float radiusDp, int rippleColor) {
        GradientDrawable content = new GradientDrawable();
        content.setColor(fill);
        content.setCornerRadius(radiusDp * density);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(radiusDp * density);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private void styleField(EditText e, int background) {
        e.setBackground(rounded(background, 16));
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFF777777);
        e.setTextSize(16);
        e.setPadding(dp(16), dp(12), dp(16), dp(12));
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private int dp(int v) {
        return (int) (v * density() + 0.5f);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
