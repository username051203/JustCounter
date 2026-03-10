package com.username051203.justcounter.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.username051203.justcounter.R;
import com.username051203.justcounter.data.AppDatabase;
import com.username051203.justcounter.data.Counter;
import com.username051203.justcounter.data.HistoryEntry;
import com.username051203.justcounter.service.StealthOverlayService;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CounterActivity extends AppCompatActivity {
    private static final int REQ_PICK_IMAGE = 101;
    private static final int REQ_SETTINGS = 1;

    private ConstraintLayout rootLayout;
    private TextView tvSessionDate, tvMode, tvCurrentCount, tvToday, tvTodayCycles, tvLifetime, tvLifetimeCycles;
    private MaterialButton btnCountLarge, btnCountSmall;

    private AppDatabase db;
    private Counter counter;
    private Vibrator vibrator;
    private ToneGenerator toneGen;
    private boolean volumeMode = false;
    private String today;

    private android.content.BroadcastReceiver stealthUpdateReceiver;
    public static final String ACTION_STEALTH_UPDATE = "com.username051203.justcounter.STEALTH_UPDATE";

    // Battery optimizations
    private final android.os.Handler saveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService dbExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final long SAVE_DEBOUNCE_MS = 400;
    private final Runnable debouncedSave = () -> dbExecutor.execute(this::saveCounterBackground);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_counter);

        rootLayout = findViewById(R.id.rootLayout);
        tvSessionDate = findViewById(R.id.tvSessionDate);
        tvMode = findViewById(R.id.tvMode);
        tvCurrentCount = findViewById(R.id.tvCurrentCount);
        tvToday = findViewById(R.id.tvToday);
        tvTodayCycles = findViewById(R.id.tvTodayCycles);
        tvLifetime = findViewById(R.id.tvLifetime);
        tvLifetimeCycles = findViewById(R.id.tvLifetimeCycles);
        btnCountLarge = findViewById(R.id.btnCountLarge);
        btnCountSmall = findViewById(R.id.btnCountSmall);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        int counterId = getIntent().getIntExtra("counterId", -1);
        db = AppDatabase.getInstance(this);
        counter = db.counterDao().getCounterById(counterId);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try { toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100); } catch (Exception ignored) {}
        today = getTodayDate();

        if (counter == null) { finish(); return; }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(counter.name);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        checkDayChange();
        applyBackground();
        updateUI();

        btnCountLarge.setOnClickListener(v -> increment());
        btnCountSmall.setOnClickListener(v -> decrement());
        tvMode.setOnClickListener(v -> {
            volumeMode = !volumeMode;
            tvMode.setText(volumeMode ? "VOLUME MODE" : "ON-SCREEN MODE");
        });

        // Register receiver for stealth mode live updates
        stealthUpdateReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context ctx, Intent intent) {
                counter = db.counterDao().getCounterById(counter.id);
                if (counter != null) updateUI();
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter(ACTION_STEALTH_UPDATE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stealthUpdateReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stealthUpdateReceiver, filter);
        }
    }

    public void increment() {
        counter.dailyCount++;
        counter.lifetimeCount++;
        boolean cycleComplete = counter.cycleSize > 0 && (counter.dailyCount % counter.cycleSize == 0);
        if (cycleComplete) {
            // Cycle complete — skip regular haptic, do strong long one instead
            if (counter.soundAlert && toneGen != null)
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800);
            if (counter.hapticLong && vibrator != null)
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 100, 500}, -1));
        } else {
            if (counter.hapticRegular && vibrator != null)
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
        }
        saveCounter();
        updateUI();
    }

    private void decrement() {
        if (counter.dailyCount > 0) {
            counter.dailyCount--;
            counter.lifetimeCount = Math.max(0, counter.lifetimeCount - 1);
        }
        saveCounter();
        updateUI();
    }

    private void saveCounter() {
        // Update UI immediately, debounce actual DB write
        saveHandler.removeCallbacks(debouncedSave);
        saveHandler.postDelayed(debouncedSave, SAVE_DEBOUNCE_MS);
    }

    private void saveCounterBackground() {
        counter.sessionDate = today;
        db.counterDao().updateCounter(counter);
        HistoryEntry existing = db.counterDao().getHistoryForDate(counter.id, today);
        if (existing == null) {
            HistoryEntry entry = new HistoryEntry();
            entry.counterId = counter.id; entry.date = today;
            entry.count = counter.dailyCount; entry.cycleSize = counter.cycleSize;
            db.counterDao().insertHistory(entry);
        } else {
            db.counterDao().updateHistoryCount(counter.id, today, counter.dailyCount);
        }
    }

    private void updateUI() {
        // Big number shows position within current cycle (resets to 0 every cycleSize)
        int withinCycle = counter.cycleSize > 0 ? (int)(counter.dailyCount % counter.cycleSize) : (int)counter.dailyCount;
        tvCurrentCount.setText(String.valueOf(withinCycle));
        tvSessionDate.setText("Current session date: " + today);
        tvToday.setText(String.valueOf(counter.dailyCount));
        tvTodayCycles.setText(counter.cycleSize + " x " + counter.getDailyCycles());
        tvLifetime.setText(String.format(Locale.getDefault(), "%,d", counter.lifetimeCount));
        tvLifetimeCycles.setText(counter.cycleSize + " x " + counter.getLifetimeCycles());
    }

    private void applyBackground() {
        if (counter.backgroundUri == null || counter.backgroundUri.isEmpty()) {
            rootLayout.setBackgroundColor(getResources().getColor(R.color.background, null));
            return;
        }
        Object source = counter.backgroundUri.startsWith("http")
                ? counter.backgroundUri
                : Uri.parse(counter.backgroundUri);
        // Try as GIF first, fall back to static image
        com.bumptech.glide.Glide.with(this)
                .load(source)
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    @Override
                    public void onResourceReady(android.graphics.drawable.Drawable resource,
                            com.bumptech.glide.request.transition.Transition<? super android.graphics.drawable.Drawable> transition) {
                        if (resource instanceof com.bumptech.glide.load.resource.gif.GifDrawable) {
                            ((com.bumptech.glide.load.resource.gif.GifDrawable) resource).setLoopCount(
                                    com.bumptech.glide.load.resource.gif.GifDrawable.LOOP_FOREVER);
                            ((com.bumptech.glide.load.resource.gif.GifDrawable) resource).start();
                        }
                        rootLayout.setBackground(resource);
                    }
                    @Override public void onLoadCleared(android.graphics.drawable.Drawable placeholder) {}
                    @Override public void onLoadFailed(android.graphics.drawable.Drawable errorDrawable) {
                        Toast.makeText(CounterActivity.this, "Could not load background", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkDayChange() {
        if (counter.sessionDate != null && !counter.sessionDate.equals(today)) {
            HistoryEntry existing = db.counterDao().getHistoryForDate(counter.id, counter.sessionDate);
            if (existing == null && counter.dailyCount > 0) {
                HistoryEntry entry = new HistoryEntry();
                entry.counterId = counter.id; entry.date = counter.sessionDate;
                entry.count = counter.dailyCount; entry.cycleSize = counter.cycleSize;
                db.counterDao().insertHistory(entry);
            }
            counter.dailyCount = 0;
            counter.sessionDate = today;
            db.counterDao().updateCounter(counter);
        } else if (counter.sessionDate == null) {
            counter.sessionDate = today;
            db.counterDao().updateCounter(counter);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (volumeMode) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) { increment(); return true; }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { decrement(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) { getMenuInflater().inflate(R.menu.menu_counter, menu); return true; }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) { onBackPressed(); return true; }
        else if (id == R.id.action_reset_daily) { showResetDailyDialog(); }
        else if (id == R.id.action_reset_lifetime) { showResetLifetimeDialog(); }
        else if (id == R.id.action_log_manual) { showLogManualDialog(); }
        else if (id == R.id.action_view_history) { Intent i = new Intent(this, HistoryActivity.class); i.putExtra("counterId", counter.id); startActivity(i); }
        else if (id == R.id.action_set_background) { showBackgroundDialog(); }
        else if (id == R.id.action_stealth) { startStealthMode(); }
        else if (id == R.id.action_stealth_stop) { stopService(new Intent(this, StealthOverlayService.class)); Toast.makeText(this, "Stealth mode off", Toast.LENGTH_SHORT).show(); }
        else if (id == R.id.action_settings) { Intent i = new Intent(this, SettingsActivity.class); i.putExtra("counterId", counter.id); startActivityForResult(i, REQ_SETTINGS); }
        return super.onOptionsItemSelected(item);
    }

    private void startStealthMode() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Permission Required")
                    .setMessage("Stealth mode needs the 'Display over other apps' permission. Tap OK to open settings and enable it for Just Counter.")
                    .setPositiveButton("Open Settings", (d, w) -> {
                        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null).show();
        } else {
            Intent i = new Intent(this, StealthOverlayService.class);
            i.putExtra("counterId", counter.id);
            startService(i);
            Toast.makeText(this, "Stealth mode on — long press bubble to stop", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBackgroundDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("Set Background")
                .setItems(new String[]{"Pick from storage", "Enter image URL", "Remove background"}, (d, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.setType("image/*");
                        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivityForResult(intent, REQ_PICK_IMAGE);
                    } else if (which == 1) {
                        android.widget.EditText urlInput = new android.widget.EditText(this);
                        urlInput.setHint("https://example.com/image.jpg");
                        if (counter.backgroundUri != null && counter.backgroundUri.startsWith("http"))
                            urlInput.setText(counter.backgroundUri);
                        new MaterialAlertDialogBuilder(this).setTitle("Image URL").setView(urlInput)
                                .setPositiveButton("Apply", (dd, ww) -> {
                                    counter.backgroundUri = urlInput.getText().toString().trim();
                                    db.counterDao().updateCounter(counter);
                                    applyBackground();
                                }).setNegativeButton("Cancel", null).show();
                    } else {
                        counter.backgroundUri = null;
                        db.counterDao().updateCounter(counter);
                        rootLayout.setBackgroundColor(getResources().getColor(R.color.background, null));
                    }
                }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                counter.backgroundUri = uri.toString();
                db.counterDao().updateCounter(counter);
                applyBackground();
            }
        } else if (requestCode == REQ_SETTINGS && resultCode == RESULT_OK) {
            counter = db.counterDao().getCounterById(counter.id);
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(counter.name);
            applyBackground();
            updateUI();
        }
    }

    private void showResetDailyDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("Reset Daily Counter").setMessage("Reset today's count to 0?")
                .setPositiveButton("Reset", (d, w) -> { counter.dailyCount = 0; saveCounter(); updateUI(); })
                .setNegativeButton("Cancel", null).show();
    }

    private void showResetLifetimeDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("Reset Lifetime Counter").setMessage("This will reset ALL counts permanently.")
                .setPositiveButton("Reset", (d, w) -> { counter.dailyCount = 0; counter.lifetimeCount = 0; saveCounter(); updateUI(); })
                .setNegativeButton("Cancel", null).show();
    }

    private void showLogManualDialog() {
        // Build custom view
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        // Value input
        android.widget.EditText etValue = new android.widget.EditText(this);
        etValue.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etValue.setHint("Enter value to log");
        etValue.setTextColor(0xFFFFFFFF);
        etValue.setHintTextColor(0xFFAAAAAA);
        layout.addView(etValue);

        // Count / Cycles radio
        android.widget.TextView tvMode = new android.widget.TextView(this);
        tvMode.setText("Select whether entered value is count or cycles");
        tvMode.setTextColor(0xFFAAAAAA);
        tvMode.setPadding(0, pad, 0, 4);
        layout.addView(tvMode);

        android.widget.RadioGroup rg = new android.widget.RadioGroup(this);
        rg.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        android.widget.RadioButton rbCount = new android.widget.RadioButton(this);
        rbCount.setText("Count"); rbCount.setTextColor(0xFFFFFFFF); rbCount.setChecked(true);
        android.widget.RadioButton rbCycles = new android.widget.RadioButton(this);
        rbCycles.setText("Cycles"); rbCycles.setTextColor(0xFFFFFFFF);
        rg.addView(rbCount); rg.addView(rbCycles);
        layout.addView(rg);

        // Date label
        android.widget.TextView tvDate = new android.widget.TextView(this);
        tvDate.setText("Select date");
        tvDate.setTextColor(0xFFAAAAAA);
        tvDate.setPadding(0, pad, 0, 4);
        layout.addView(tvDate);

        // Scroll wheel date picker: Day | Month | Year
        android.widget.LinearLayout dateRow = new android.widget.LinearLayout(this);
        dateRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        dateRow.setGravity(android.view.Gravity.CENTER);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        int curDay = cal.get(java.util.Calendar.DAY_OF_MONTH);
        int curMonth = cal.get(java.util.Calendar.MONTH); // 0-based
        int curYear = cal.get(java.util.Calendar.YEAR);

        android.widget.NumberPicker npDay = new android.widget.NumberPicker(this);
        npDay.setMinValue(1); npDay.setMaxValue(31); npDay.setValue(curDay);
        npDay.setTextColor(0xFFFFFFFF);

        String[] months = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        android.widget.NumberPicker npMonth = new android.widget.NumberPicker(this);
        npMonth.setMinValue(0); npMonth.setMaxValue(11); npMonth.setValue(curMonth);
        npMonth.setDisplayedValues(months);
        npMonth.setTextColor(0xFFFFFFFF);

        android.widget.NumberPicker npYear = new android.widget.NumberPicker(this);
        npYear.setMinValue(2020); npYear.setMaxValue(curYear + 1); npYear.setValue(curYear);
        npYear.setTextColor(0xFFFFFFFF);

        android.widget.LinearLayout.LayoutParams npParams =
                new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        npParams.setMargins(4, 0, 4, 0);
        dateRow.addView(npDay, npParams);
        dateRow.addView(npMonth, npParams);
        dateRow.addView(npYear, npParams);
        layout.addView(dateRow);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Log data manually")
                .setView(layout)
                .setPositiveButton("LOG DATA ON SELECTED DATE", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String raw = etValue.getText().toString().trim();
                if (raw.isEmpty()) { etValue.setError("Enter a value"); return; }
                long val;
                try { val = Long.parseLong(raw); } catch (NumberFormatException e) { etValue.setError("Invalid number"); return; }

                boolean isCycles = rbCycles.isChecked();
                long countVal = isCycles ? val * counter.cycleSize : val;

                // Format selected date
                String selDate = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                        npYear.getValue(), npMonth.getValue() + 1, npDay.getValue());

                // Log to history for that date
                HistoryEntry existing = db.counterDao().getHistoryForDate(counter.id, selDate);
                if (existing != null) {
                    db.counterDao().updateHistoryCount(counter.id, selDate, existing.count + countVal);
                } else {
                    HistoryEntry entry = new HistoryEntry();
                    entry.counterId = counter.id; entry.date = selDate;
                    entry.count = countVal; entry.cycleSize = counter.cycleSize;
                    db.counterDao().insertHistory(entry);
                }

                // If logging for today, also update live counter
                if (selDate.equals(today)) {
                    counter.dailyCount += countVal;
                    counter.lifetimeCount += countVal;
                } else {
                    counter.lifetimeCount += countVal;
                }
                db.counterDao().updateCounter(counter);
                updateUI();
                dialog.dismiss();
                Toast.makeText(this, "Logged " + countVal + " to " + selDate, Toast.LENGTH_SHORT).show();
            });
        });
        dialog.show();
    }

    private String getTodayDate() { return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()); }

    @Override protected void onStop() {
        super.onStop();
        // Pause GIF animation when app goes to background
        android.graphics.drawable.Drawable bg = rootLayout.getBackground();
        if (bg instanceof com.bumptech.glide.load.resource.gif.GifDrawable)
            ((com.bumptech.glide.load.resource.gif.GifDrawable) bg).stop();
        // Flush any pending save immediately
        saveHandler.removeCallbacks(debouncedSave);
        dbExecutor.execute(this::saveCounterBackground);
    }

    @Override protected void onStart() {
        super.onStart();
        // Resume GIF when app comes back
        android.graphics.drawable.Drawable bg = rootLayout.getBackground();
        if (bg instanceof com.bumptech.glide.load.resource.gif.GifDrawable)
            ((com.bumptech.glide.load.resource.gif.GifDrawable) bg).start();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        saveHandler.removeCallbacks(debouncedSave);
        dbExecutor.shutdown();
        if (toneGen != null) toneGen.release();
        if (stealthUpdateReceiver != null) { try { unregisterReceiver(stealthUpdateReceiver); } catch (Exception ignored) {} }
    }
}
