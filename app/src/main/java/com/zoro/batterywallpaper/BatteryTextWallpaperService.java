package com.zoro.batterywallpaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BatteryTextWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() { return new BatteryEngine(); }

    private class BatteryEngine extends Engine {
        private static final int COLOR_CHARGING = 0xFF66BB6A;
        private static final int COLOR_LOW_BATTERY = 0xFFFF5722;

        private final Paint textPaint, outlinePaint, fillPaint, clockPaint, datePaint;
        private SimpleDateFormat timeFormat;
        private final SimpleDateFormat dateFormat;
        private final Date calendarDate = new Date();
        private final Calendar calendar = Calendar.getInstance();

        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final Object renderLock = new Object();
        private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();
        private Future<?> activeDecodeTask = null;

        private int batteryLevel = -1;
        private boolean isCharging = false;
        private boolean isVisible = false;
        private boolean isReceiverRegistered = false;

        private SharedPreferences prefs;
        private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
        private float baseBatteryTextSize, baseClockTextSize, baseDateTextSize;
        private int lowBatteryThreshold;
        private int customTextColor = 0xFFFFF8E7;
        private int parsedBgColor = 0xFF1A1A1A;

        private String cachedTimeText = "";
        private String cachedDateText = "";
        private String cachedBatteryText = "";
        private float cachedClockWidth = 0f;
        private float cachedDateWidth = 0f;
        private float cachedBatteryTextWidth = 0f;
        private long lastCachedMinute = -1L;
        private int cachedDayOfYear = -1;
        private float clockHeight, dateHeight, batteryBlockHeight;
        
        // Cached trigonometric values for orbital shift
        private float shiftCosFactor = 0f;
        private float shiftSinFactor = 0f;

        private volatile Bitmap bgBitmap = null;
        private boolean useCustomBg = false;
        private long lastBgTimestamp = 0L;
        private int bgDimOpacity = 60;

        private boolean fullTextOpacity = true;
        private int posVerticalPercent = 45;
        private int posHorizontalPercent = 50;

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

            Typeface primaryTypeface = Typeface.create("sans-serif-medium", Typeface.BOLD);
            int paintFlags = Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG;

            textPaint = new Paint(paintFlags);
            textPaint.setTypeface(primaryTypeface);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setLetterSpacing(0.02f);
            textPaint.setFontFeatureSettings("tnum");

            outlinePaint = new Paint(paintFlags);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(14f);

            fillPaint = new Paint(paintFlags);
            fillPaint.setStyle(Paint.Style.FILL);

            clockPaint = new Paint(paintFlags);
            clockPaint.setTypeface(primaryTypeface);
            clockPaint.setTextAlign(Paint.Align.CENTER);
            clockPaint.setLetterSpacing(0.04f);
            clockPaint.setFontFeatureSettings("tnum");

            datePaint = new Paint(paintFlags);
            datePaint.setTypeface(primaryTypeface);
            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setLetterSpacing(0.03f);

            dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
            prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            applyPreferences();

            prefListener = (sharedPreferences, key) -> {
                applyPreferences();
                draw();
            };
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

            try {
                parsedBgColor = Color.parseColor(prefs.getString("bg_color", "#1A1A1A"));
            } catch (Exception e) {
                parsedBgColor = 0xFF1A1A1A;
            }

            try {
                customTextColor = Color.parseColor(prefs.getString("text_color", "#FFF8E7"));
            } catch (Exception e) {
                customTextColor = 0xFFFFF8E7;
            }

            boolean enableShadow = prefs.getBoolean("enable_shadow", true);
            if (enableShadow) {
                int parsedShadowColor;
                try {
                    parsedShadowColor = Color.parseColor(prefs.getString("shadow_color", "#000000"));
                } catch (Exception e) {
                    parsedShadowColor = Color.BLACK;
                }
                int shadowArgb = Color.argb(230, Color.red(parsedShadowColor), Color.green(parsedShadowColor), Color.blue(parsedShadowColor));
                float shadowRadius = prefs.getInt("shadow_radius", 18);
                float shadowOffset = prefs.getInt("shadow_offset", 2);

                textPaint.setShadowLayer(shadowRadius, 0f, shadowOffset, shadowArgb);
                outlinePaint.setShadowLayer(shadowRadius, 0f, shadowOffset, shadowArgb);
                clockPaint.setShadowLayer(shadowRadius * 1.25f, 0f, shadowOffset, shadowArgb);
                datePaint.setShadowLayer(shadowRadius * 0.85f, 0f, shadowOffset, shadowArgb);
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

            if (prefs.getBoolean("use_24_hour", false)) {
                timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            } else {
                timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            }

            updateDateString(true);
            updateTimeString(true);
            updateBatteryString();

            useCustomBg = prefs.getBoolean("use_custom_bg", false);
            long bgTimestamp = prefs.getLong("bg_timestamp", 0L);
            if (useCustomBg) {
                if (bgBitmap == null || bgTimestamp != lastBgTimestamp) {
                    Rect frame = getSurfaceHolder().getSurfaceFrame();
                    if (frame.width() > 0 && frame.height() > 0) {
                        loadCustomBitmapAsync(frame.width(), frame.height());
                        lastBgTimestamp = bgTimestamp;
                    }
                }
            } else {
                recycleBitmap();
            }
        }

        private void updateTimeString(boolean force) {
            long currentMinute = System.currentTimeMillis() / 60000L;
            if (force || currentMinute != lastCachedMinute) {
                calendarDate.setTime(System.currentTimeMillis());
                cachedTimeText = timeFormat.format(calendarDate);
                clockPaint.setTextSize(baseClockTextSize);
                cachedClockWidth = clockPaint.measureText(cachedTimeText);
                
                // Cache trigonometric shifts once per minute
                shiftCosFactor = (float) Math.cos(currentMinute * 0.1);
                shiftSinFactor = (float) Math.sin(currentMinute * 0.1);
                
                lastCachedMinute = currentMinute;
            }
        }

        private void updateDateString(boolean force) {
            calendar.setTimeInMillis(System.currentTimeMillis());
            int currentDay = calendar.get(Calendar.DAY_OF_YEAR);
            if (force || currentDay != cachedDayOfYear) {
                calendarDate.setTime(calendar.getTimeInMillis());
                cachedDateText = dateFormat.format(calendarDate);
                datePaint.setTextSize(baseDateTextSize);
                cachedDateWidth = datePaint.measureText(cachedDateText);
                cachedDayOfYear = currentDay;
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
                    if (!old.isRecycled()) {
                        old.recycle();
                    }
                }
            }
        }

        private void loadCustomBitmapAsync(int targetWidth, int targetHeight) {
            if (activeDecodeTask != null && !activeDecodeTask.isDone()) {
                activeDecodeTask.cancel(true);
            }

            activeDecodeTask = diskExecutor.submit(() -> {
                File file = new File(getApplicationContext().getFilesDir(), "custom_bg.jpg");
                if (!file.exists()) {
                    recycleBitmap();
                    mainHandler.post(this::draw);
                    return;
                }

                BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
                boundsOptions.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), boundsOptions);

                int sampleSize = 1;
                while ((boundsOptions.outWidth / (sampleSize * 2)) >= targetWidth &&
                       (boundsOptions.outHeight / (sampleSize * 2)) >= targetHeight) {
                    sampleSize *= 2;
                }

                BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
                decodeOptions.inSampleSize = sampleSize;
                decodeOptions.inPreferredConfig = Bitmap.Config.RGB_565;

                Bitmap rawSampled = null;
                try {
                    rawSampled = BitmapFactory.decodeFile(file.getAbsolutePath(), decodeOptions);
                } catch (OutOfMemoryError ignored) {}

                if (rawSampled == null) return;
                if (Thread.currentThread().isInterrupted()) {
                    rawSampled.recycle();
                    return;
                }

                Bitmap bakedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565);
                Canvas bakeCanvas = new Canvas(bakedBitmap);

                float scale = Math.max((float) targetWidth / rawSampled.getWidth(), (float) targetHeight / rawSampled.getHeight());
                float scaledW = rawSampled.getWidth() * scale;
                float scaledH = rawSampled.getHeight() * scale;
                float left = (targetWidth - scaledW) * 0.5f;
                float top = (targetHeight - scaledH) * 0.5f;

                Rect destRect = new Rect((int) left, (int) top, (int) (left + scaledW), (int) (top + scaledH));
                bakeCanvas.drawBitmap(rawSampled, null, destRect, null);
                rawSampled.recycle();

                if (bgDimOpacity > 0) {
                    // Bitwise shift is faster than Color.argb()
                    int alphaColor = (Math.round(bgDimOpacity * 2.55f) << 24) | 0x00000000;
                    bakeCanvas.drawColor(alphaColor);
                }

                mainHandler.post(() -> {
                    synchronized (renderLock) {
                        Bitmap old = bgBitmap;
                        bgBitmap = bakedBitmap;
                        if (old != null && !old.isRecycled()) {
                            old.recycle();
                        }
                    }
                    draw();
                });
            });
        }

        private void processBatteryIntent(Intent intent) {
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean currentCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            if (level != -1 && scale > 0) {
                int newLevel = (level * 100) / scale;
                if (newLevel != batteryLevel || currentCharging != isCharging) {
                    batteryLevel = newLevel;
                    isCharging = currentCharging;
                    updateBatteryString();
                    draw();
                }
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.isVisible = visible;
            if (visible) {
                Intent initialBattery = getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (initialBattery != null) {
                    processBatteryIntent(initialBattery);
                }

                if (!isReceiverRegistered) {
                    IntentFilter filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                    filter.addAction(Intent.ACTION_TIME_TICK);
                    filter.addAction(Intent.ACTION_DATE_CHANGED);
                    filter.addAction(Intent.ACTION_TIME_CHANGED);
                    filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);

                    ContextCompat.registerReceiver(
                        getApplicationContext(),
                        asyncReceiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    );
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
                try {
                    getApplicationContext().unregisterReceiver(asyncReceiver);
                } catch (Exception ignored) {}
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    canvas = holder.lockHardwareCanvas();
                }
                if (canvas == null) {
                    canvas = holder.lockCanvas();
                }

                if (canvas != null) {
                    synchronized (renderLock) {
                        Bitmap currentBg = bgBitmap;
                        if (useCustomBg && currentBg != null && !currentBg.isRecycled()) {
                            canvas.drawBitmap(currentBg, 0f, 0f, null);
                        } else {
                            canvas.drawColor(parsedBgColor);
                        }
                    }

                    updateTimeString(false);
                    updateDateString(false);

                    float screenWidth = canvas.getWidth();
                    float screenHeight = canvas.getHeight();
                    
                    float rawIconWidth = baseBatteryTextSize * 1.25f;
                    float rawBatteryBlockWidth = rawIconWidth + (rawIconWidth * 0.08f) + (baseBatteryTextSize * 0.25f) + cachedBatteryTextWidth;

                    // Compute scaling layout once
                    float widestElement = Math.max(Math.max(cachedClockWidth, cachedDateWidth), rawBatteryBlockWidth);
                    float maxAllowedWidth = screenWidth * 0.92f;
                    float finalRatio = widestElement > maxAllowedWidth ? (maxAllowedWidth / widestElement) : 1f;

                    float scaledClockHeight = clockHeight * finalRatio;
                    float scaledDateHeight = dateHeight * finalRatio;
                    float scaledBatteryBlockHeight = batteryBlockHeight * finalRatio;
                    float gap1 = 24f * finalRatio;
                    float gap2 = 64f * finalRatio;
                    
                    float totalHeight = scaledClockHeight + gap1 + scaledDateHeight + gap2 + scaledBatteryBlockHeight;
                    float maxAllowedHeight = screenHeight * 0.88f;

                    if (totalHeight > maxAllowedHeight) {
                        float hRatio = maxAllowedHeight / totalHeight;
                        finalRatio *= hRatio;
                        gap1 *= hRatio;
                       
