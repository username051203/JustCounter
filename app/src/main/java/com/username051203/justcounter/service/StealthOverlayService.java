package com.username051203.justcounter.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import com.username051203.justcounter.R;
import com.username051203.justcounter.data.AppDatabase;
import com.username051203.justcounter.data.Counter;
import com.username051203.justcounter.data.HistoryEntry;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StealthOverlayService extends Service {
    private static final String CHANNEL_ID = "stealth_counter";

    // Single reusable background thread instead of new Thread() per tap
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private View overlayView;
    private WindowManager.LayoutParams params;
    private int counterId = -1;
    private AppDatabase db;
    private Vibrator vibrator;
    private ToneGenerator toneGen;

    // Touch state
    private float touchDownX, touchDownY;
    private int initParamX, initParamY;
    private long touchDownTime;
    private boolean wasDrag;

    // Pending save — batch rapid taps into one DB write
    private long pendingDailyCount = -1;
    private long pendingLifetimeCount = -1;
    private final Runnable saveRunnable = this::flushPendingSave;
    private static final long SAVE_DEBOUNCE_MS = 500;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        db = AppDatabase.getInstance(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try { toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100); } catch (Exception ignored) {}
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) counterId = intent.getIntExtra("counterId", -1);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Just Counter - Stealth Mode")
                .setContentText("Tap to count • Long-press to stop")
                .setSmallIcon(android.R.drawable.ic_menu_add)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(1, notification);
        if (overlayView == null) showOverlay();
        // NOT START_STICKY — don't restart after being killed, saves battery
        return START_NOT_STICKY;
    }

    private void showOverlay() {
        int sizePx = (int)(80 * getResources().getDisplayMetrics().density);
        params = new WindowManager.LayoutParams(
                sizePx, sizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 30;
        params.y = 400;

        overlayView = new View(this);
        overlayView.setBackgroundResource(R.drawable.stealth_circle);
        overlayView.setAlpha(0.55f);
        overlayView.setOnTouchListener(this::handleTouch);
        wm.addView(overlayView, params);
    }

    private boolean handleTouch(View v, MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = e.getRawX();
                touchDownY = e.getRawY();
                initParamX = params.x;
                initParamY = params.y;
                touchDownTime = System.currentTimeMillis();
                wasDrag = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float mdx = e.getRawX() - touchDownX;
                float mdy = e.getRawY() - touchDownY;
                if (!wasDrag && (Math.abs(mdx) > 12 || Math.abs(mdy) > 12)) wasDrag = true;
                if (wasDrag) {
                    params.x = (int)(initParamX + mdx);
                    params.y = (int)(initParamY + mdy);
                    wm.updateViewLayout(overlayView, params);
                }
                return true;
            case MotionEvent.ACTION_UP:
                long elapsed = System.currentTimeMillis() - touchDownTime;
                if (!wasDrag) {
                    if (elapsed >= 600) stopSelf();
                    else performIncrement();
                }
                return true;
        }
        return false;
    }

    private void performIncrement() {
        if (counterId == -1) return;
        executor.execute(() -> {
            Counter counter = db.counterDao().getCounterById(counterId);
            if (counter == null) return;

            counter.dailyCount++;
            counter.lifetimeCount++;

            boolean cycleComplete = counter.cycleSize > 0 && (counter.dailyCount % counter.cycleSize == 0);
            if (cycleComplete) {
                if (counter.soundAlert && toneGen != null)
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800);
                if (counter.hapticLong && vibrator != null)
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 100, 500}, -1));
            } else {
                if (counter.hapticRegular && vibrator != null)
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            }

            // Debounce DB writes — batch rapid taps, only write after 500ms of inactivity
            pendingDailyCount = counter.dailyCount;
            pendingLifetimeCount = counter.lifetimeCount;
            mainHandler.removeCallbacks(saveRunnable);
            mainHandler.postDelayed(saveRunnable, SAVE_DEBOUNCE_MS);

            // Notify activity immediately
            Intent broadcast = new Intent("com.username051203.justcounter.STEALTH_UPDATE");
            broadcast.setPackage(getPackageName());
            sendBroadcast(broadcast);

            // Pulse
            if (overlayView != null) {
                overlayView.post(() ->
                    overlayView.animate().scaleX(1.35f).scaleY(1.35f).setDuration(70)
                        .withEndAction(() -> overlayView.animate().scaleX(1f).scaleY(1f).setDuration(70).start())
                        .start());
            }
        });
    }

    private void flushPendingSave() {
        if (pendingDailyCount < 0 || counterId == -1) return;
        executor.execute(() -> {
            Counter counter = db.counterDao().getCounterById(counterId);
            if (counter == null) return;
            counter.dailyCount = pendingDailyCount;
            counter.lifetimeCount = pendingLifetimeCount;
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
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
            pendingDailyCount = -1;
        });
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Stealth Counter", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(saveRunnable);
        flushPendingSave(); // save any pending taps before dying
        executor.shutdown();
        if (overlayView != null) { wm.removeView(overlayView); overlayView = null; }
        if (toneGen != null) toneGen.release();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
