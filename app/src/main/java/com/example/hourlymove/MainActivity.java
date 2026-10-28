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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
    private static final int SURFACE = 0xFF1B1E15;
    private static final int SURFACE_DONE = 0xFF222E10;
    private static final int ICON_BG = 0xFF2A3015;
    private static final int LIME = 0xFFA9CC3C;
    private static final int LIME_DARK = 0xFF3D4D15;
    private static final int ON_LIME = 0xFF11140A;
    private static final int TEXT = 0xFFEDEDED;
    private static final int TEXT_DIM = 0xFF9E9E9E;
    private static final int TRACK = 0xFF2B2D28;
    private static final int FIELD = 0xFF2C2C2C;

    private Store store;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** A dialog action that returns true when it succeeded and the dialog may close. */
    private interface Check {
        boolean run();
    }

    // tabs
    private ScrollView todayView;
    private ScrollView historyView;
    private NavItem tabToday;
    private NavItem tabHistory;
    private int currentTab = 0;

    // swipe between tabs
    private float swipeDownX;
    private float swipeDownY;
    private boolean swipeTracking;
    private boolean swiped;
    private boolean dragging; // true while an exercise is being dragged by its handle

    // "Today" tab
    private RingView ring;
    private TextView countdown;
    private TextView subtitle;
    private TextView roundInfo;
    private EditText intervalInput;
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

    // ------------------------------------------------------------------ swipe between tabs

    /**
     * A mostly-horizontal swipe anywhere on the screen switches tab: swipe left for History,
     * swipe right to go back to Today. Vertical scrolling and the drag handle are not affected.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                swipeDownX = ev.getRawX();
                swipeDownY = ev.getRawY();
                swipeTracking = true;
                swiped = false;
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (swiped) {
                    return true; // the rest of this gesture belongs to the swipe
                }
                if (swipeTracking && !dragging && ev.getPointerCount() == 1) {
                    float dx = ev.getRawX() - swipeDownX;
                    float dy = ev.getRawY() - swipeDownY;
                    if (Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy) * 2) {
                        // Cancel whatever was under the finger (a card press, a scroll...).
                        MotionEvent cancel = MotionEvent.obtain(ev);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                        swiped = true;
                        if (dx < 0 && currentTab == 0) {
                            swipeTo(1, true);
                        } else if (dx > 0 && currentTab == 1) {
                            swipeTo(0, false);
                        }
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                swipeTracking = false;
                if (swiped) {
                    swiped = false;
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /** Switches tab with a short slide-and-fade; the new page comes in from the right or the left. */
    private void swipeTo(int index, boolean fromRight) {
        if (index == currentTab) {
            return;
        }
        showTab(index);
        View page = index == 0 ? todayView : historyView;
        page.setAlpha(0f);
        page.setTranslationX(fromRight ? dp(48) : -dp(48));
        page.animate().alpha(1f).translationX(0f).setDuration(180).start();
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

    /** A container that turns a clear sideways swipe into a tab change. */
    private static class SwipeFrame extends FrameLayout {
        interface Listener {
            /** @param toLeft true when the finger moved left (go to the next tab). */
            void onSwipe(boolean toLeft);
        }

        private final Listener listener;
        private final int slop;
        private final int distance;
        private float downX;
        private float downY;
        private boolean swiping;

        SwipeFrame(Context c, Listener l) {
            super(c);
            listener = l;
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            distance = (int) (72 * c.getResources().getDisplayMetrics().density);
        }

        /** Mostly sideways movement, so normal up/down scrolling is left alone. */
        private boolean isSideways(MotionEvent e) {
            float dx = e.getX() - downX;
            float dy = e.getY() - downY;
            return Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 2;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    swiping = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isSideways(e)) {
                        swiping = true;
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    swiping = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (isSideways(e)) {
                        swiping = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP: {
                    float dx = e.getX() - downX;
                    boolean go = swiping && Math.abs(dx) > distance;
                    swiping = false;
                    if (go) {
                        listener.onSwipe(dx < 0);
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    swiping = false;
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }
    }

    /** Switches tab after a swipe: Today is on the left, History on the right. */
    private void swipeTo(int index, boolean toLeft) {
        showTab(index);
        View v = index == 0 ? todayView : historyView;
        v.animate().cancel();
        v.setTranslationX((toLeft ? 1 : -1) * dp(48));
        v.setAlpha(0f);
        v.animate().translationX(0f).alpha(1f).setDuration(180).start();
    }

    private void buildUi() {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setBackgroundColor(BG);

        SwipeFrame content = new SwipeFrame(this, toLeft -> {
            int target = toLeft ? 1 : 0;
            if (target != currentTab) {
                swipeTo(target, toLeft);
            }
        });
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
        header.setPadding(0, dp(28), 0, dp(4));
        root.addView(header, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("Tap to log · long-press to edit · drag ≡ to reorder");
        hint.setTextSize(13);
        hint.setTextColor(TEXT_DIM);
        hint.setPadding(0, 0, 0, dp(10));
        root.addView(hint, matchWrap());

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, matchWrap());

        // Add exercise
        PillButton add = new PillButton(this, "Add exercise", R.drawable.ic_add, false);
        add.setOnClickListener(v -> showExerciseDialog(null));
        LinearLayout.LayoutParams alp = matchWrap();
        alp.topMargin = dp(8);
        root.addView(add, alp);

        return scroll;
    }

    private void refresh() {
        shownSession = store.sessionId();
        toggle.setLabel(store.enabled() ? "Stop reminders" : "Start reminders");

        list.removeAllViews();
        List<Store.Exercise> exercises = store.exercises();
        Map<String, Integer> done = store.done();

        if (exercises.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No exercises yet. Tap Add exercise.");
            empty.setTextSize(16);
            empty.setTextColor(TEXT_DIM);
            empty.setPadding(0, 0, 0, dp(10));
            list.addView(empty, matchWrap());
        }

        for (int i = 0; i < exercises.size(); i++) {
            Store.Exercise ex = exercises.get(i);
            LinearLayout.LayoutParams lp = matchWrap();
            lp.bottomMargin = dp(10);
            list.addView(buildExerciseRow(ex, done.get(ex.name), i), lp);
        }
    }

    /** One exercise card: round icon, name with its reps, drag handle and trash. */
    private View buildExerciseRow(final Store.Exercise ex, Integer reps, final int index) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rippleBg(density(), reps != null ? SURFACE_DONE : SURFACE, 28, 0x22FFFFFF));
        row.setPadding(dp(12), dp(12), dp(6), dp(12));
        row.setClickable(true);
        row.setOnClickListener(v -> askReps(ex));
        row.setOnLongClickListener(v -> {
            showExerciseDialog(ex);
            return true;
        });

        // Icon in a circle (filled lime once done)
        LinearLayout circle = new LinearLayout(this);
        circle.setGravity(Gravity.CENTER);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(reps != null ? LIME : ICON_BG);
        circle.setBackground(oval);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconFor(ex.name));
        icon.setColorFilter(reps != null ? ON_LIME : LIME);
        circle.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        row.addView(circle, new LinearLayout.LayoutParams(dp(58), dp(58)));

        // Name + subtitle
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView nameView = new TextView(this);
        nameView.setText(ex.name);
        nameView.setTextSize(20);
        nameView.setTextColor(TEXT);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(nameView, matchWrap());

        TextView sub = new TextView(this);
        sub.setTextSize(15);
        if (reps != null) {
            sub.setText("✓ Done · " + reps + " reps");
            sub.setTextColor(LIME);
            sub.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            sub.setText(ex.reps + (ex.reps == 1 ? " rep" : " reps"));
            sub.setTextColor(TEXT_DIM);
        }
        LinearLayout.LayoutParams sublp = matchWrap();
        sublp.topMargin = dp(2);
        col.addView(sub, sublp);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.setMarginStart(dp(16));
        clp.setMarginEnd(dp(8));
        row.addView(col, clp);

        // Drag handle
        ImageView handle = iconButton(R.drawable.ic_drag_handle);
        attachDrag(handle, row, index);
        row.addView(handle, new LinearLayout.LayoutParams(dp(44), dp(44)));

        // Trash
        ImageView trash = iconButton(R.drawable.ic_delete);
        trash.setOnClickListener(v -> confirmRemove(ex.name));
        row.addView(trash, new LinearLayout.LayoutParams(dp(44), dp(44)));

        return row;
    }

    private static int iconFor(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("push")) {
            return R.drawable.ic_pushup;
        }
        if (n.contains("squat")) {
            return R.drawable.ic_squat;
        }
        return R.drawable.ic_dumbbell;
    }

    private ImageView iconButton(int res) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setColorFilter(0xFFB5B5B5);
        v.setPadding(dp(10), dp(10), dp(10), dp(10));
        v.setClickable(true);
        return v;
    }

    // ---- drag to reorder (press and hold the handle, then move up or down) ----

    private void attachDrag(final View handle, final View row, final int index) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float startY;
            private int target;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragging = true;
                        startY = e.getRawY();
                        target = index;
                        row.setTranslationZ(dp(8));
                        row.setAlpha(0.9f);
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dy = e.getRawY() - startY;
                        row.setTranslationY(dy);
                        target = dropTarget(index, dy);
                        shiftRows(index, target);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        dragging = false;
                        boolean drop = e.getActionMasked() == MotionEvent.ACTION_UP && target != index;
                        resetRows();
                        if (drop) {
                            List<Store.Exercise> all = store.exercises();
                            if (index < all.size()) {
                                Store.Exercise moved = all.remove(index);
                                all.add(Math.min(target, all.size()), moved);
                                store.setExercises(all);
                            }
                        }
                        handler.post(() -> refresh());
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
    }

    /** The position the dragged card would take if dropped now. */
    private int dropTarget(int index, float dy) {
        View dragged = list.getChildAt(index);
        if (dragged == null) {
            return index;
        }
        float center = dragged.getTop() + dragged.getHeight() / 2f + dy;
        int target = 0;
        for (int j = 0; j < list.getChildCount(); j++) {
            if (j == index) {
                continue;
            }
            View c = list.getChildAt(j);
            if (c.getTop() + c.getHeight() / 2f < center) {
                target++;
            }
        }
        return target;
    }

    /** Slides the other cards out of the way so you can see where the dragged one will land. */
    private void shiftRows(int index, int target) {
        View dragged = list.getChildAt(index);
        if (dragged == null) {
            return;
        }
        float step = dragged.getHeight() + dp(10);
        for (int j = 0; j < list.getChildCount(); j++) {
            if (j == index) {
                continue;
            }
            float t = 0f;
            if (index < j && j <= target) {
                t = -step;
            } else if (target <= j && j < index) {
                t = step;
            }
            list.getChildAt(j).setTranslationY(t);
        }
    }

    private void resetRows() {
        for (int j = 0; j < list.getChildCount(); j++) {
            View c = list.getChildAt(j);
            c.setTranslationY(0f);
            c.setTranslationZ(0f);
            c.setAlpha(1f);
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

        List<Store.Exercise> exercises = store.exercises();
        Map<String, Integer> done = store.done();

        if (!store.enabled()) {
            ring.setFraction(0f);
            setBig("--:--", 44);
            subtitle.setText("reminders off");
            roundInfo.setText("Press Start reminders to begin.");
            return;
        }

        int doneCount = 0;
        for (Store.Exercise e : exercises) {
            if (done.containsKey(e.name)) {
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

    private void askReps(final Store.Exercise ex) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        styleField(input, FIELD);
        Integer prev = store.done().get(ex.name);
        input.setText(String.valueOf(prev != null ? prev : ex.reps));

        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(ex.name)
                .setMessage("How many reps did you do?")
                .setView(box)
                .setPositiveButton("Done", null)
                .setNegativeButton("Cancel", null)
                .create();
        showValidated(dialog, true, () -> {
            int reps = parseInt(input);
            if (reps < 1) {
                toast("Enter the number of reps");
                return false;
            }
            boolean wasAll = allDone();
            store.markDone(ex.name, reps);
            afterChange(wasAll);
            return true;
        });
    }

    /** Add (existing == null) or edit an exercise: name and reps. */
    private void showExerciseDialog(final Store.Exercise existing) {
        final EditText name = new EditText(this);
        name.setHint("Exercise name (e.g. Push-ups)");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        styleField(name, FIELD);

        final EditText reps = new EditText(this);
        reps.setInputType(InputType.TYPE_CLASS_NUMBER);
        reps.setSingleLine(true);
        reps.setSelectAllOnFocus(true);
        styleField(reps, FIELD);

        if (existing != null) {
            name.setText(existing.name);
            name.setSelection(existing.name.length());
            reps.setText(String.valueOf(existing.reps));
        } else {
            reps.setText("12");
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(name, matchWrap());

        LinearLayout.LayoutParams nlp = matchWrap();
        nlp.topMargin = dp(14);
        box.addView(labeled("Reps", reps), nlp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add exercise" : "Edit exercise")
                .setView(box)
                .setPositiveButton(existing == null ? "Add" : "Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        showValidated(dialog, true, () -> {
            String n = name.getText().toString().trim();
            int r = parseInt(reps);
            if (n.isEmpty()) {
                toast("Enter a name");
                return false;
            }
            if (r < 1) {
                toast("Reps must be 1 or more");
                return false;
            }
            List<Store.Exercise> all = store.exercises();
            for (Store.Exercise other : all) {
                boolean itself = existing != null && other.name.equals(existing.name);
                if (!itself && other.name.equalsIgnoreCase(n)) {
                    toast("Already in your list");
                    return false;
                }
            }
            if (existing == null) {
                all.add(new Store.Exercise(n, r));
            } else {
                for (int i = 0; i < all.size(); i++) {
                    if (all.get(i).name.equals(existing.name)) {
                        all.set(i, new Store.Exercise(n, r));
                    }
                }
                store.renameExercise(existing.name, n);
            }
            store.setExercises(all);
            refresh();
            updateCountdown();
            return true;
        });
    }

    private boolean allDone() {
        List<Store.Exercise> exercises = store.exercises();
        Map<String, Integer> done = store.done();
        if (exercises.isEmpty()) {
            return false;
        }
        for (Store.Exercise e : exercises) {
            if (!done.containsKey(e.name)) {
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

    private void confirmRemove(final String name) {
        new AlertDialog.Builder(this)
                .setTitle("Remove \"" + name + "\"?")
                .setMessage("Your history for it is kept.")
                .setPositiveButton("Remove", (d, w) -> {
                    boolean wasAll = allDone();
                    List<Store.Exercise> exercises = store.exercises();
                    exercises.removeIf(e -> e.name.equals(name));
                    store.setExercises(exercises);
                    store.removeDone(name);
                    afterChange(wasAll);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Reads and saves the interval field. Returns false (with a message) if it is invalid. */
    private boolean applyInterval() {
        int minutes = parseInt(intervalInput);
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

        // Group this day's entries by session (in the order they happened) and add up the totals.
        Map<Long, List<Store.Entry>> sessions = new LinkedHashMap<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        int entryCount = 0;
        int totalReps = 0;
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
            entryCount++;
            totalReps += e.reps;
            Integer sum = totals.get(e.name);
            totals.put(e.name, (sum == null ? 0 : sum) + e.reps);
        }

        if (sessions.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("Nothing logged this day.");
            none.setTextSize(16);
            none.setTextColor(TEXT_DIM);
            dayDetails.addView(none, matchWrap());
            return;
        }

        LinearLayout.LayoutParams sumlp = matchWrap();
        sumlp.bottomMargin = dp(20);
        dayDetails.addView(buildSummary(sessions.size(), entryCount, totalReps, totals), sumlp);

        TextView sessionsTitle = new TextView(this);
        sessionsTitle.setText("Sessions");
        sessionsTitle.setTextSize(16);
        sessionsTitle.setTextColor(TEXT);
        sessionsTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dayDetails.addView(sessionsTitle, matchWrap());

        TextView editHint = new TextView(this);
        editHint.setText("Long-press a session to edit it");
        editHint.setTextSize(13);
        editHint.setTextColor(TEXT_DIM);
        editHint.setPadding(0, dp(2), 0, dp(10));
        dayDetails.addView(editHint, matchWrap());

        int n = 1;
        for (final List<Store.Entry> group : sessions.values()) {
            final int number = n;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(rippleBg(density(), SURFACE, 20, 0x22FFFFFF));
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setClickable(true);
            card.setOnLongClickListener(v -> {
                editSession(group, number);
                return true;
            });

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

    /** "Sessions / Exercises done / Total reps" tiles, plus total reps for each exercise. */
    private View buildSummary(int sessionCount, int entryCount, int totalReps,
                              Map<String, Integer> totals) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(SURFACE, 24));
        card.setPadding(dp(8), dp(16), dp(8), dp(12));

        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.addView(statTile(String.valueOf(sessionCount), "Sessions"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tiles.addView(statTile(String.valueOf(entryCount), "Exercises done"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tiles.addView(statTile(String.valueOf(totalReps), "Total reps"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(tiles, matchWrap());

        View line = new View(this);
        line.setBackgroundColor(0xFF2E3326);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        llp.setMargins(dp(8), dp(14), dp(8), dp(6));
        card.addView(line, llp);

        for (Map.Entry<String, Integer> t : totals.entrySet()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(8), dp(6), dp(8), dp(6));

            TextView name = new TextView(this);
            name.setText(t.getKey());
            name.setTextSize(15);
            name.setTextColor(TEXT);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView reps = new TextView(this);
            reps.setText(t.getValue() + (t.getValue() == 1 ? " rep" : " reps"));
            reps.setTextSize(15);
            reps.setTextColor(TEXT_DIM);
            row.addView(reps);

            card.addView(row, matchWrap());
        }
        return card;
    }

    private View statTile(String value, String label) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(28);
        v.setTextColor(LIME);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        tile.addView(v, matchWrap());

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(TEXT_DIM);
        l.setGravity(Gravity.CENTER);
        tile.addView(l, matchWrap());
        return tile;
    }

    // ---- editing a session (long-press) ----

    private void editSession(final List<Store.Entry> group, int number) {
        final List<EditText> inputs = new ArrayList<>();
        final boolean[] removed = new boolean[group.size()];

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(4), dp(24), 0);
        sv.addView(box);

        for (int i = 0; i < group.size(); i++) {
            final int idx = i;
            Store.Entry e = group.get(i);

            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView nm = new TextView(this);
            nm.setText(e.name);
            nm.setTextSize(16);
            nm.setTextColor(TEXT);
            row.addView(nm, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            EditText reps = new EditText(this);
            reps.setInputType(InputType.TYPE_CLASS_NUMBER);
            reps.setSingleLine(true);
            reps.setGravity(Gravity.CENTER);
            reps.setSelectAllOnFocus(true);
            reps.setText(String.valueOf(e.reps));
            styleField(reps, FIELD);
            row.addView(reps, new LinearLayout.LayoutParams(dp(84),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            inputs.add(reps);

            ImageView del = iconButton(R.drawable.ic_delete);
            del.setOnClickListener(v -> {
                removed[idx] = true;
                row.setVisibility(View.GONE);
            });
            row.addView(del, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(6);
            box.addView(row, lp);
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit session " + number + " · " + timeOf(group.get(0)))
                .setView(sv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete", null)
                .create();
        showValidated(dialog, false, () -> {
            int[] values = new int[group.size()];
            for (int i = 0; i < group.size(); i++) {
                if (removed[i]) {
                    continue;
                }
                values[i] = parseInt(inputs.get(i));
                if (values[i] < 1) {
                    toast("Reps must be 1 or more");
                    return false;
                }
            }
            for (int i = 0; i < group.size(); i++) {
                Store.Entry entry = group.get(i);
                if (removed[i]) {
                    store.deleteEntry(entry.session, entry.name);
                } else if (values[i] != entry.reps) {
                    store.setEntryReps(entry.session, entry.name, values[i]);
                }
            }
            renderHistory();
            refresh();
            updateCountdown();
            return true;
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(0xFFE57373);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> confirmDeleteSession(group, dialog));
    }

    private void confirmDeleteSession(final List<Store.Entry> group, final AlertDialog parent) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this session?")
                .setMessage("It will be removed from your history.")
                .setPositiveButton("Delete", (d, w) -> {
                    for (Store.Entry e : group) {
                        store.deleteEntry(e.session, e.name);
                    }
                    parent.dismiss();
                    renderHistory();
                    refresh();
                    updateCountdown();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static LocalDate dateOf(Store.Entry e) {
        return Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static String timeOf(Store.Entry e) {
        return Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
    }

    // ------------------------------------------------------------ helpers

    /**
     * Shows a dialog whose Save/Done button only closes it when the check succeeds, so a bad
     * value keeps the dialog open instead of throwing your input away.
     */
    private void showValidated(final AlertDialog dialog, boolean keyboard, final Check check) {
        if (keyboard) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (check.run()) {
                dialog.dismiss();
            }
        });
    }

    private LinearLayout labeled(String label, EditText field) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(TEXT_DIM);
        t.setPadding(dp(4), 0, 0, dp(4));
        box.addView(t, matchWrap());
        box.addView(field, matchWrap());
        return box;
    }

    private static int parseInt(EditText e) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

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
