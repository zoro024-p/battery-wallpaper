package com.zoro.batterywallpaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.BatteryManager;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import androidx.preference.PreferenceManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BatteryTextWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() { return new BatteryEngine(); }

    private class BatteryEngine extends Engine {
        private Paint textPaint, outlinePaint, fillPaint, clockPaint, datePaint, backgroundPaint;
        private SimpleDateFormat timeFormat, dateFormat;
        private int batteryLevel = -1;
        private boolean isCharging = false;
        private boolean isVisible = false;
        private SharedPreferences prefs;
        private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
        private float baseBatteryTextSize, baseClockTextSize, baseDateTextSize;
        private int lowBatteryThreshold;

        private final BroadcastReceiver asyncReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    processBatteryIntent(intent);
                } else {
                    draw();
                }
            }
        };

        BatteryEngine() {
            backgroundPaint = new Paint();
            
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setShadowLayer(10f, 0f, 0f, Color.BLACK);

            outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(12f);
            outlinePaint.setShadowLayer(10f, 0f, 0f, Color.BLACK);

            fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setStyle(Paint.Style.FILL);

            clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            clockPaint.setColor(Color.WHITE);
            clockPaint.setTextAlign(Paint.Align.CENTER);
            clockPaint.setShadowLayer(15f, 0f, 0f, Color.BLACK);

            datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            datePaint.setColor(Color.LTGRAY);
            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setShadowLayer(10f, 0f, 0f, Color.BLACK);

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
            baseBatteryTextSize = prefs.getInt("text_size", 350);
            baseClockTextSize = baseBatteryTextSize * 1.3f;
            baseDateTextSize = baseBatteryTextSize * 0.4f;
            lowBatteryThreshold = prefs.getInt("warning_threshold", 15);
            backgroundPaint.setColor(Color.parseColor(prefs.getString("bg_color", "#444444")));

            if (prefs.getBoolean("use_24_hour", false)) {
                timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            } else {
                timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            }
        }

        private void processBatteryIntent(Intent intent) {
            if (intent == null) return;
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean currentChargingState = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            
            if (level != -1 && scale != -1) {
                int newLevel = (int) ((level / (float) scale) * 100);
                if (newLevel != batteryLevel || currentChargingState != isCharging) {
                    batteryLevel = newLevel;
                    isCharging = currentChargingState;
                    draw(); 
                }
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.isVisible = visible;
            if (visible) {
                Intent initialBatteryState = getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                processBatteryIntent(initialBatteryState);

                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                filter.addAction(Intent.ACTION_TIME_TICK);
                filter.addAction(Intent.ACTION_TIME_CHANGED);
                filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
                getApplicationContext().registerReceiver(asyncReceiver, filter);
                
                draw();
            } else {
                try { getApplicationContext().unregisterReceiver(asyncReceiver); } catch (Exception ignored) {}
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            draw();
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
                    float screenWidth = canvas.getWidth();
                    float screenHeight = canvas.getHeight();

                    canvas.drawRect(0, 0, screenWidth, screenHeight, backgroundPaint);

                    Date now = new Date();
                    String timeText = timeFormat.format(now);
                    String dateText = dateFormat.format(now);
                    String batteryText = batteryLevel + "%";

                    textPaint.setTextSize(baseBatteryTextSize);
                    clockPaint.setTextSize(baseClockTextSize);
                    datePaint.setTextSize(baseDateTextSize);

                    float rawClockWidth = clockPaint.measureText(timeText);
                    float rawDateWidth = datePaint.measureText(dateText);
                    float rawBatteryTextWidth = textPaint.measureText(batteryText);
                    float rawIconWidth = baseBatteryTextSize * 1.2f;
                    float rawBatteryBlockWidth = rawIconWidth + (rawIconWidth * 0.08f) + (baseBatteryTextSize * 0.2f) + rawBatteryTextWidth;

                    float maxAllowedWidth = screenWidth * 0.90f;
                    float widestElement = Math.max(Math.max(rawClockWidth, rawDateWidth), rawBatteryBlockWidth);

                    if (widestElement > maxAllowedWidth) {
                        float scaleRatio = maxAllowedWidth / widestElement;
                        textPaint.setTextSize(baseBatteryTextSize * scaleRatio);
                        clockPaint.setTextSize(baseClockTextSize * scaleRatio);
                        datePaint.setTextSize(baseDateTextSize * scaleRatio);
                    }

                    float clockHeight = clockPaint.descent() - clockPaint.ascent();
                    float dateHeight = datePaint.descent() - datePaint.ascent();
                    float batteryBlockHeight = textPaint.getTextSize() * 0.6f;

                    float gap1 = 20f;
                    float gap2 = 80f;
                    float totalHeight = clockHeight + gap1 + dateHeight + gap2 + batteryBlockHeight;
                    float groupTopY = (screenHeight - totalHeight) / 2f;

                    long currentMinute = System.currentTimeMillis() / 60000L;
                    float maxShiftX = screenWidth * 0.03f;
                    float maxShiftY = screenHeight * 0.03f;
                    float shiftX = (float) (Math.cos(currentMinute * 0.1) * maxShiftX);
                    float shiftY = (float) (Math.sin(currentMinute * 0.1) * maxShiftY);

                    canvas.save();
                    canvas.translate(shiftX, shiftY);

                    float clockBaselineY = groupTopY - clockPaint.ascent();
                    canvas.drawText(timeText, screenWidth / 2f, clockBaselineY, clockPaint);

                    float dateBaselineY = clockBaselineY + clockPaint.descent() + gap1 - datePaint.ascent();
                    canvas.drawText(dateText, screenWidth / 2f, dateBaselineY, datePaint);

                    float finalSize = textPaint.getTextSize();
                    float iconWidth = finalSize * 1.2f;
                    float iconHeight = finalSize * 0.6f;
                    float nubWidth = iconWidth * 0.08f;
                    float nubHeight = iconHeight * 0.4f;
                    float padding = finalSize * 0.2f;
                    float textWidth = textPaint.measureText(batteryText);

                    float totalBatteryWidth = iconWidth + nubWidth + padding + textWidth;
                    float startX = (screenWidth - totalBatteryWidth) / 2f;
                    float centerY = dateBaselineY + datePaint.descent() + gap2 + (iconHeight / 2f);

                    float bodyLeft = startX;
                    float bodyTop = centerY - (iconHeight / 2f);
                    float bodyRight = bodyLeft + iconWidth;
                    float bodyBottom = centerY + (iconHeight / 2f);

                    float nubLeft = bodyRight;
                    float nubTop = centerY - (nubHeight / 2f);
                    float nubRight = nubLeft + nubWidth;
                    float nubBottom = centerY + (nubHeight / 2f);

                    float strokeOffset = outlinePaint.getStrokeWidth();
                    float fillLeft = bodyLeft + strokeOffset;
                    float fillTop = bodyTop + strokeOffset;
                    float maxFillRight = bodyRight - strokeOffset;
                    float fillBottom = bodyBottom - strokeOffset;
                    float currentFillRight = fillLeft + ((maxFillRight - fillLeft) * (batteryLevel / 100f));

                    int activeColor;
                    if (isCharging) {
                        activeColor = Color.parseColor("#4CAF50");
                    } else if (batteryLevel <= lowBatteryThreshold) {
                        activeColor = Color.parseColor("#FF4444");
                    } else {
                        activeColor = Color.WHITE;
                    }

                    outlinePaint.setColor(activeColor);
                    fillPaint.setColor(activeColor);
                    textPaint.setColor(activeColor);

                    canvas.drawRoundRect(bodyLeft, bodyTop, bodyRight, bodyBottom, 20f, 20f, outlinePaint);
                    canvas.drawRoundRect(nubLeft, nubTop, nubRight, nubBottom, 5f, 5f, fillPaint);
                    
                    if (batteryLevel > 0) {
                        canvas.drawRect(fillLeft, fillTop, currentFillRight, fillBottom, fillPaint);
                    }

                    float textXPos = nubRight + padding;
                    float textYPos = centerY - ((textPaint.descent() + textPaint.ascent()) / 2f);
                    canvas.drawText(batteryText, textXPos, textYPos, textPaint);

                    canvas.restore();
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (isVisible) {
                try { getApplicationContext().unregisterReceiver(asyncReceiver); } catch (Exception ignored) {}
            }
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
    }
}
