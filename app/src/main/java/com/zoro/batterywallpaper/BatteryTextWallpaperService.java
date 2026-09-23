package com.zoro.batterywallpaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.*;

public class BatteryTextWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() { return new BatteryEngine(); }

    private class BatteryEngine extends Engine {
        private static final int COLOR_CHARGING = 0xFF66BB6A;
        private static final int COLOR_LOW_BATTERY = 0xFFFF5722;

        private final Paint textPaint, outlinePaint, fillPaint, clockPaint, datePaint;
        private SimpleDateFormat timeFormat;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
        private final Date calendarDate = new Date();
        private final Calendar calendar = Calendar.getInstance();

        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final Object renderLock = new Object();
        private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();
        private Future<?> activeDecodeTask = null;

        private int batteryLevel = -1, lowBatteryThreshold = 25, bgDimOpacity = 60;
        private int customTextColor = 0xFFFFF8E7, parsedBgColor = 0xFF1A1A1A;
        private int posVerticalPercent = 45, posHorizontalPercent = 50, cachedDayOfYear = -1;
        private boolean isCharging = false, isVisible = false, isReceiverRegistered = false;
        private boolean fullTextOpacity = true, useCustomBg = false;

        private SharedPreferences prefs;
        private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
        private float baseBatteryTextSize, baseClockTextSize, baseDateTextSize;
        private float clockHeight, dateHeight, batteryBlockHeight;
        private float cachedClockWidth = 0f, cachedDateWidth = 0f, cachedBatteryTextWidth = 0f;
        private float shiftCosFactor = 0f, shiftSinFactor = 0f;
        private long lastCachedMinute = -1L, lastBgTimestamp = 0L;
        private String cachedTimeText = "", cachedDateText = "", cachedBatteryText = "";
        private volatile Bitmap bgBitmap = null;

        private final BroadcastReceiver asyncReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                    processBatteryIntent(intent);
                } else if (Intent.ACTION_DATE_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                    updateDateString(true);
                    updateTimeString(true);
                    draw();
                } else {
                    draw();
                }
            }
        };

        BatteryEngine() {
            setTouchEventsEnabled(false);
            Typeface font = Typeface.create("sans-serif-medium", Typeface.BOLD);
            int flags = Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG;

            textPaint = new Paint(flags);
            textPaint.setTypeface(font);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setLetterSpacing(0.02f);
            textPaint.setFontFeatureSettings("tnum");

            outlinePaint = new Paint(flags);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(14f);

            fillPaint = new Paint(flags);
            fillPaint.setStyle(Paint.Style.FILL);

            clockPaint = new Paint(flags);
            clockPaint.setTypeface(font);
            clockPaint.setTextAlign(Paint.Align.CENTER);
            clockPaint.setLetterSpacing(0.04f);
            clockPaint.setFontFeatureSettings("tnum");

            datePaint = new Paint(flags);
            datePaint.setTypeface(font);
            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setLetterSpacing(0.03f);

            prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            applyPreferences();

            prefListener = (sp, key) -> { applyPreferences(); draw(); };
            prefs.registerOnSharedPreferenceChangeListener(prefListener);
        }

        private void applyPreferences() {
            fullTextOpacity = prefs.getBoolean("full_text_opacity", true);
            baseBatteryTextSize = prefs.getInt("text_size", 140);
            baseClockTextSize = baseBatteryTextSize * 1.25f;
            baseDateTextSize = baseBatteryTextSize * 0.48f;
            lowBatteryThreshold = prefs.getInt("warning_threshold", 25);
            bgDimOpacity = prefs.getInt("bg_dim_opacity", 60);
            posVerticalPercent = prefs.getInt("pos_vertical", 45);
            posHorizontalPercent = prefs.getInt("pos_horizontal", 50);

            try { parsedBgColor = Color.parseColor(prefs.getString("bg_color", "#1A1A1A")); } catch (Exception e) { parsedBgColor = 0xFF1A1A1A; }
            try { customTextColor = Color.parseColor(prefs.getString("text_color", "#FFF8E7")); } catch (Exception e) { customTextColor = 0xFFFFF8E7; }

            if (prefs.getBoolean("enable_shadow", true)) {
                int sc;
                try { sc = Color.parseColor(prefs.getString("shadow_color", "#000000")); } catch (Exception e) { sc = Color.BLACK; }
                int argb = Color.argb(230, Color.red(sc), Color.green(sc), Color.blue(sc));
                float r = prefs.getInt("shadow_radius", 18), off = prefs.getInt("shadow_offset", 2);
                textPaint.setShadowLayer(r, 0f, off, argb);
                outlinePaint.setShadowLayer(r, 0f, off, argb);
                clockPaint.setShadowLayer(r * 1.25f, 0f, off, argb);
                datePaint.setShadowLayer(r * 0.85f, 0f, off, argb);
            } else {
                textPaint.clearShadowLayer();
                outlinePaint.clearShadowLayer();
                clockPaint.clearShadowLayer();
                datePaint.clearShadowLayer();
            }

            textPaint.setTextSize(baseBatteryTextSize);
            clockPaint.setTextSize(baseClockTextSize);
            datePaint.setTextSize(baseDateTextSize);
            clockHeight = clockPaint.descent() - clockPaint.ascent();
            dateHeight = datePaint.descent() - datePaint.ascent();
            batteryBlockHeight = baseBatteryTextSize * 0.65f;
            clockPaint.setColor(customTextColor);
            datePaint.setColor(customTextColor);
            datePaint.setAlpha(fullTextOpacity ? 255 : 215);

            timeFormat = new SimpleDateFormat(prefs.getBoolean("use_24_hour", false) ? "HH:mm" : "h:mm a", Locale.getDefault());
            updateDateString(true);
            updateTimeString(true);
            updateBatteryString();

            useCustomBg = prefs.getBoolean("use_custom_bg", false);
            long bgTs = prefs.getLong("bg_timestamp", 0L);
            if (useCustomBg) {
                if (bgBitmap == null || bgTs != lastBgTimestamp) {
                    Rect frame = getSurfaceHolder().getSurfaceFrame();
                    if (frame.width() > 0 && frame.height() > 0) {
                        loadCustomBitmapAsync(frame.width(), frame.height());
                        lastBgTimestamp = bgTs;
                    }
                }
            } else {
                recycleBitmap();
            }
        }

        private void updateTimeString(boolean force) {
            long minute = System.currentTimeMillis() / 60000L;
            if (force || minute != lastCachedMinute) {
                calendarDate.setTime(System.currentTimeMillis());
                cachedTimeText = timeFormat.format(calendarDate);
                clockPaint.setTextSize(baseClockTextSize);
                cachedClockWidth = clockPaint.measureText(cachedTimeText);
                shiftCosFactor = (float) Math.cos(minute * 0.1);
                shiftSinFactor = (float) Math.sin(minute * 0.1);
                lastCachedMinute = minute;
            }
        }

        private void updateDateString(boolean force) {
            calendar.setTimeInMillis(System.currentTimeMillis());
            int day = calendar.get(Calendar.DAY_OF_YEAR);
            if (force || day != cachedDayOfYear) {
                calendarDate.setTime(calendar.getTimeInMillis());
                cachedDateText = dateFormat.format(calendarDate);
                datePaint.setTextSize(baseDateTextSize);
                cachedDateWidth = datePaint.measureText(cachedDateText);
                cachedDayOfYear = day;
            }
        }

        private void updateBatteryString() {
            if (batteryLevel != -1) {
                cachedBatteryText = (isCharging ? "⚡\uFE0E " : "") + batteryLevel + "%";
                textPaint.setTextSize(baseBatteryTextSize);
                cachedBatteryTextWidth = textPaint.measureText(cachedBatteryText);
            }
        }

        private void recycleBitmap() {
            synchronized (renderLock) {
                if (bgBitmap != null) {
                    Bitmap old = bgBitmap;
                    bgBitmap = null;
                    if (!old.isRecycled()) old.recycle();
                }
            }
        }

        private void loadCustomBitmapAsync(int targetW, int targetH) {
            if (activeDecodeTask != null && !activeDecodeTask.isDone()) activeDecodeTask.cancel(true);
            activeDecodeTask = diskExecutor.submit(() -> {
                File file = new File(getApplicationContext().getFilesDir(), "custom_bg.jpg");
                if (!file.exists()) { recycleBitmap(); mainHandler.post(this::draw); return; }

                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

                int sampleSize = 1;
                while ((bounds.outWidth / (sampleSize * 2)) >= targetW && (bounds.outHeight / (sampleSize * 2)) >= targetH) sampleSize *= 2;

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sampleSize;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;

                Bitmap raw = null;
                try { raw = BitmapFactory.decodeFile(file.getAbsolutePath(), opts); } catch (OutOfMemoryError ignored) {}
                if (raw == null || Thread.currentThread().isInterrupted()) { if (raw != null) raw.recycle(); return; }

                Bitmap baked = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565);
                Canvas bakeCanvas = new Canvas(baked);
                float scale = Math.max((float) targetW / raw.getWidth(), (float) targetH / raw.getHeight());
                float sw = raw.getWidth() * scale, sh = raw.getHeight() * scale;
                bakeCanvas.drawBitmap(raw, null, new Rect((int) ((targetW - sw) * 0.5f), (int) ((targetH - sh) * 0.5f), (int) ((targetW + sw) * 0.5f), (int) ((targetH + sh) * 0.5f)), null);
                raw.recycle();

                if (bgDimOpacity > 0) bakeCanvas.drawColor(Color.argb((int) (bgDimOpacity * 2.55f), 0, 0, 0));
                mainHandler.post(() -> {
                    synchronized (renderLock) {
                        Bitmap old = bgBitmap;
                        bgBitmap = baked;
                        if (old != null && !old.isRecycled()) old.recycle();
                    }
                    draw();
                });
            });
        }

        private void processBatteryIntent(Intent intent) {
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level != -1 && scale > 0) {
                int pct = (level * 100) / scale;
                if (pct != batteryLevel || charging != isCharging) {
                    batteryLevel = pct;
                    isCharging = charging;
                    updateBatteryString();
                    draw();
                }
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.isVisible = visible;
            if (visible) {
                Intent init = getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (init != null) processBatteryIntent(init);
                if (!isReceiverRegistered) {
                    IntentFilter filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                    filter.addAction(Intent.ACTION_TIME_TICK);
                    filter.addAction(Intent.ACTION_DATE_CHANGED);
                    filter.addAction(Intent.ACTION_TIME_CHANGED);
                    filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
                    ContextCompat.registerReceiver(getApplicationContext(), asyncReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
                    isReceiverRegistered = true;
                }
                updateDateString(false);
                updateTimeString(true);
                draw();
            } else {
                unregisterReceiverSafely();
            }
        }

        private void unregisterReceiverSafely() {
            if (isReceiverRegistered) {
                try { getApplicationContext().unregisterReceiver(asyncReceiver); } catch (Exception ignored) {}
                isReceiverRegistered = false;
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            if (useCustomBg && (bgBitmap == null || bgBitmap.isRecycled() || bgBitmap.getWidth() != width || bgBitmap.getHeight() != height)) {
                loadCustomBitmapAsync(width, height);
            } else {
                draw();
            }
        }

        private void draw() {
            if (!isVisible || batteryLevel == -1) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) canvas = holder.lockHardwareCanvas();
                if (canvas == null) canvas = holder.lockCanvas();

                if (canvas != null) {
                    synchronized (renderLock) {
                        if (useCustomBg && bgBitmap != null && !bgBitmap.isRecycled()) canvas.drawBitmap(bgBitmap, 0f, 0f, null);
                        else canvas.drawColor(parsedBgColor);
                    }

                    updateTimeString(false);
                    updateDateString(false);

                    float sw = canvas.getWidth(), sh = canvas.getHeight();
                    float rawIconW = baseBatteryTextSize * 1.25f;
                    float rawBlockW = rawIconW + (rawIconW * 0.08f) + (baseBatteryTextSize * 0.25f) + cachedBatteryTextWidth;
                    float widest = Math.max(Math.max(cachedClockWidth, cachedDateWidth), rawBlockW);
                    float maxAllowedW = sw * 0.92f;
                    float ratio = widest > maxAllowedW ? (maxAllowedW / widest) : 1f;

                    float scaledH = (clockHeight + dateHeight + batteryBlockHeight) * ratio;
                    float g1 = 24f * ratio, g2 = 64f * ratio;
                    float totalH = scaledH + g1 + g2;
                    float maxAllowedH = sh * 0.88f;

                    if (totalH > maxAllowedH) {
                        float hr = maxAllowedH / totalH;
                        ratio *= hr;
                        g1 *= hr;
                        g2 *= hr;
                        totalH *= hr;
                    }

                    textPaint.setTextSize(baseBatteryTextSize * ratio);
                    clockPaint.setTextSize(baseClockTextSize * ratio);
                    datePaint.setTextSize(baseDateTextSize * ratio);

                    float drawX = (sw * (posHorizontalPercent / 100f)) + (shiftCosFactor * sw * 0.012f);
                    float drawY = (Math.max(0f, sh - totalH) * (posVerticalPercent / 100f)) + (shiftSinFactor * sh * 0.012f);

                    float clockY = drawY - clockPaint.ascent();
                    canvas.drawText(cachedTimeText, drawX, clockY, clockPaint);

                    float dateY = clockY + clockPaint.descent() + g1 - datePaint.ascent();
                    canvas.drawText(cachedDateText, drawX, dateY, datePaint);

                    float fSize = textPaint.getTextSize();
                    float iconW = fSize * 1.25f, iconH = fSize * 0.65f;
                    float nubW = iconW * 0.08f, nubH = iconH * 0.45f;
                    float pad = fSize * 0.25f;
                    float startX = drawX - ((iconW + nubW + pad + textPaint.measureText(cachedBatteryText)) * 0.5f);
                    float centerY = dateY + datePaint.descent() + g2 + (iconH * 0.5f);

                    float bL = startX, bT = centerY - (iconH * 0.5f), bR = bL + iconW, bB = centerY + (iconH * 0.5f);
                    float nL = bR, nT = centerY - (nubH * 0.5f), nR = nL + nubW, nB = centerY + (nubH * 0.5f);
                    float fL = bL + 11f, fT = bT + 11f, fR = bR - 11f, fB = bB - 11f;
                    float curFR = fL + ((fR - fL) * (batteryLevel / 100f));

                    int statusColor = isCharging ? COLOR_CHARGING : (batteryLevel <= lowBatteryThreshold ? COLOR_LOW_BATTERY : customTextColor);
                    outlinePaint.setColor(statusColor);
                    fillPaint.setColor(statusColor);
                    textPaint.setColor(statusColor);

                    canvas.drawRoundRect(bL, bT, bR, bB, 18f, 18f, outlinePaint);
                    canvas.drawRoundRect(nL, nT, nR, nB, 5f, 5f, fillPaint);

                    if (batteryLevel > 0 && curFR > fL) {
                        canvas.save();
                        canvas.clipRect(fL, fT, curFR, fB);
                        canvas.drawRoundRect(fL, fT, fR, fB, 12f, 12f, fillPaint);
                        canvas.restore();
                    }

                    canvas.drawText(cachedBatteryText, nR + pad, centerY - ((textPaint.descent() + textPaint.ascent()) * 0.5f), textPaint);
                }
            } catch (Exception ignored) {
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            unregisterReceiverSafely();
            if (activeDecodeTask != null) activeDecodeTask.cancel(true);
            recycleBitmap();
            diskExecutor.shutdown();
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
    }
                                                                                                                      }
                    
