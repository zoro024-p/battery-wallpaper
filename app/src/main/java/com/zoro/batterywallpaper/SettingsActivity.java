package com.zoro.batterywallpaper;

import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private ActivityResultLauncher<String> imagePickerLauncher;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        saveBackgroundImage(uri);
                    }
                }
            );
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            Preference presetTop = findPreference("preset_top");
            if (presetTop != null) {
                presetTop.setOnPreferenceClickListener(p -> {
                    applyPositionPreset(15, 50, "Aligned to top (15%)");
                    return true;
                });
            }

            Preference presetCenter = findPreference("preset_center");
            if (presetCenter != null) {
                presetCenter.setOnPreferenceClickListener(p -> {
                    applyPositionPreset(45, 50, "Aligned to center (45%)");
                    return true;
                });
            }

            Preference presetBottom = findPreference("preset_bottom");
            if (presetBottom != null) {
                presetBottom.setOnPreferenceClickListener(p -> {
                    applyPositionPreset(85, 50, "Aligned to bottom (85%)");
                    return true;
                });
            }

            Preference textColorPref = findPreference("text_color");
            if (textColorPref != null) {
                textColorPref.setOnPreferenceClickListener(p -> {
                    showColorPickerDialog("text_color", "Choose Text Color", "#FFF8E7");
                    return true;
                });
            }

            Preference shadowColorPref = findPreference("shadow_color");
            if (shadowColorPref != null) {
                shadowColorPref.setOnPreferenceClickListener(p -> {
                    showColorPickerDialog("shadow_color", "Choose Shadow Color", "#000000");
                    return true;
                });
            }

            Preference bgColorPref = findPreference("bg_color");
            if (bgColorPref != null) {
                bgColorPref.setOnPreferenceClickListener(p -> {
                    showColorPickerDialog("bg_color", "Choose Background Color", "#1A1A1A");
                    return true;
                });
            }

            Preference pickImagePref = findPreference("pick_bg_image");
            if (pickImagePref != null) {
                pickImagePref.setOnPreferenceClickListener(p -> {
                    imagePickerLauncher.launch("image/*");
                    return true;
                });
            }

            Preference clearImagePref = findPreference("clear_bg_image");
            if (clearImagePref != null) {
                clearImagePref.setOnPreferenceClickListener(p -> {
                    clearBackgroundImage();
                    Toast.makeText(requireContext(), "Background image removed", Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            Preference applyButton = findPreference("apply_wallpaper");
            if (applyButton != null) {
                applyButton.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                    ComponentName component = new ComponentName(requireContext(), BatteryTextWallpaperService.class);
                    intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component);
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
                    }
                    return true;
                });
            }

            Preference resetAllPref = findPreference("reset_elderly_defaults");
            if (resetAllPref != null) {
                resetAllPref.setOnPreferenceClickListener(preference -> {
                    new AlertDialog.Builder(requireContext())
                        .setTitle("Reset to Senior Comfort Defaults?")
                        .setMessage("This will apply high-contrast colors (#FFF8E7 on #1A1A1A), large text (140), 100% opacity, and centered layout.")
                        .setPositiveButton("Reset", (dialog, which) -> applyFullReset())
                        .setNegativeButton("Cancel", null)
                        .show();
                    return true;
                });
            }

            updatePreferenceSummaries();
        }

        private void clearBackgroundImage() {
            File file = new File(requireContext().getFilesDir(), "custom_bg.jpg");
            if (file.exists()) {
                file.delete();
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            prefs.edit()
                 .putBoolean("use_custom_bg", false)
                 .putLong("bg_timestamp", System.currentTimeMillis())
                 .apply();
            updatePreferenceSummaries();
        }

        private void applyFullReset() {
            File file = new File(requireContext().getFilesDir(), "custom_bg.jpg");
            if (file.exists()) {
                file.delete();
            }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            prefs.edit()
                 .clear()
                 .putInt("text_size", 140)
                 .putBoolean("full_text_opacity", true)
                 .putInt("pos_vertical", 45)
                 .putInt("pos_horizontal", 50)
                 .putString("text_color", "#FFF8E7")
                 .putBoolean("enable_shadow", true)
                 .putString("shadow_color", "#000000")
                 .putInt("shadow_radius", 18)
                 .putInt("shadow_offset", 2)
                 .putString("bg_color", "#1A1A1A")
                 .putBoolean("use_custom_bg", false)
                 .putInt("bg_dim_opacity", 60)
                 .putInt("warning_threshold", 25)
                 .putBoolean("use_24_hour", false)
                 .putLong("bg_timestamp", System.currentTimeMillis())
                 .apply();

            SeekBarPreference textSizePref = findPreference("text_size");
            if (textSizePref != null) textSizePref.setValue(140);

            SwitchPreferenceCompat fullOpacityPref = findPreference("full_text_opacity");
            if (fullOpacityPref != null) fullOpacityPref.setChecked(true);

            SeekBarPreference vPosPref = findPreference("pos_vertical");
            if (vPosPref != null) vPosPref.setValue(45);

            SeekBarPreference hPosPref = findPreference("pos_horizontal");
            if (hPosPref != null) hPosPref.setValue(50);

            SwitchPreferenceCompat shadowPref = findPreference("enable_shadow");
            if (shadowPref != null) shadowPref.setChecked(true);

            SeekBarPreference shadowRadiusPref = findPreference("shadow_radius");
            if (shadowRadiusPref != null) shadowRadiusPref.setValue(18);

            SeekBarPreference shadowOffsetPref = findPreference("shadow_offset");
            if (shadowOffsetPref != null) shadowOffsetPref.setValue(2);

            SeekBarPreference bgDimPref = findPreference("bg_dim_opacity");
            if (bgDimPref != null) bgDimPref.setValue(60);

            SeekBarPreference warningPref = findPreference("warning_threshold");
            if (warningPref != null) warningPref.setValue(25);

            SwitchPreferenceCompat hourPref = findPreference("use_24_hour");
            if (hourPref != null) hourPref.setChecked(false);

            updatePreferenceSummaries();

            Toast.makeText(requireContext(), "Senior comfort defaults applied", Toast.LENGTH_SHORT).show();
        }

        private void updatePreferenceSummaries() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

            Preference textColorPref = findPreference("text_color");
            if (textColorPref != null) {
                textColorPref.setSummary("Current: " + prefs.getString("text_color", "#FFF8E7"));
            }

            Preference bgColorPref = findPreference("bg_color");
            if (bgColorPref != null) {
                bgColorPref.setSummary("Current: " + prefs.getString("bg_color", "#1A1A1A") + " (used when no image set)");
            }

            Preference shadowColorPref = findPreference("shadow_color");
            if (shadowColorPref != null) {
                shadowColorPref.setSummary("Current: " + prefs.getString("shadow_color", "#000000"));
            }

            Preference pickImagePref = findPreference("pick_bg_image");
            if (pickImagePref != null) {
                boolean hasCustomBg = prefs.getBoolean("use_custom_bg", false);
                pickImagePref.setSummary(hasCustomBg ? "Custom gallery photo active" : "No photo selected (using solid background)");
            }
        }

        private void applyPositionPreset(int verticalPercent, int horizontalPercent, String message) {
            SeekBarPreference vPref = findPreference("pos_vertical");
            SeekBarPreference hPref = findPreference("pos_horizontal");

            if (vPref != null) {
                vPref.setValue(verticalPercent);
            } else {
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit().putInt("pos_vertical", verticalPercent).apply();
            }

            if (hPref != null) {
                hPref.setValue(horizontalPercent);
            } else {
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit().putInt("pos_horizontal", horizontalPercent).apply();
            }

            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }

        private void saveBackgroundImage(Uri uri) {
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(new File(requireContext().getFilesDir(), "custom_bg.jpg"))) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                prefs.edit()
                     .putBoolean("use_custom_bg", true)
                     .putLong("bg_timestamp", System.currentTimeMillis())
                     .apply();
                updatePreferenceSummaries();
                Toast.makeText(requireContext(), "Background image set", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Failed to save image", Toast.LENGTH_SHORT).show();
            }
        }

        private void showColorPickerDialog(String prefKey, String title, String defaultHex) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            int currentColor;
            try {
                currentColor = Color.parseColor(prefs.getString(prefKey, defaultHex));
            } catch (Exception e) {
                currentColor = Color.parseColor(defaultHex);
            }

            int[] selectedColor = new int[]{currentColor};

            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(title);

            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad, pad, pad);

            View previewBox = new View(requireContext());
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (48 * getResources().getDisplayMetrics().density)
            );
            previewParams.bottomMargin = pad / 2;
            previewBox.setLayoutParams(previewParams);
            previewBox.setBackgroundColor(selectedColor[0]);
            container.addView(previewBox);

            TextView hexLabel = new TextView(requireContext());
            hexLabel.setText(String.format("#%06X", (0xFFFFFF & selectedColor[0])));
            hexLabel.setTextSize(16);
            hexLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            hexLabel.setPadding(0, 0, 0, pad / 2);
            container.addView(hexLabel);

            SeekBar redBar = addSlider(container, "Red", Color.red(selectedColor[0]));
            SeekBar greenBar = addSlider(container, "Green", Color.green(selectedColor[0]));
            SeekBar blueBar = addSlider(container, "Blue", Color.blue(selectedColor[0]));

            SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    selectedColor[0] = Color.rgb(redBar.getProgress(), greenBar.getProgress(), blueBar.getProgress());
                    previewBox.setBackgroundColor(selectedColor[0]);
                    hexLabel.setText(String.format("#%06X", (0xFFFFFF & selectedColor[0])));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            };

            redBar.setOnSeekBarChangeListener(listener);
            greenBar.setOnSeekBarChangeListener(listener);
            blueBar.setOnSeekBarChangeListener(listener);

            TextView presetHeader = new TextView(requireContext());
            presetHeader.setText("Accessibility Presets");
            presetHeader.setTextSize(13);
            presetHeader.setPadding(0, 16, 0, 8);
            container.addView(presetHeader);

            HorizontalScrollView scrollContainer = new HorizontalScrollView(requireContext());
            LinearLayout presetBar = new LinearLayout(requireContext());
            presetBar.setOrientation(LinearLayout.HORIZONTAL);

            addPresetButton(presetBar, "Warm Ivory", "#FFF8E7", selectedColor, redBar, greenBar, blueBar, previewBox, hexLabel);
            addPresetButton(presetBar, "Amber", "#FFC107", selectedColor, redBar, greenBar, blueBar, previewBox, hexLabel);
            addPresetButton(presetBar, "Charcoal", "#1A1A1A", selectedColor, redBar, greenBar, blueBar, previewBox, hexLabel);
            addPresetButton(presetBar, "Pure Black", "#000000", selectedColor, redBar, greenBar, blueBar, previewBox, hexLabel);

            scrollContainer.addView(presetBar);
            container.addView(scrollContainer);

            builder.setView(container);
            builder.setPositiveButton("Apply", (dialog, which) -> {
                String hexResult = String.format("#%06X", (0xFFFFFF & selectedColor[0]));
                prefs.edit().putString(prefKey, hexResult).apply();
                updatePreferenceSummaries();
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }

        private void addPresetButton(LinearLayout parent, String label, String hexColor, int[] selectedColor,
                                     SeekBar redBar, SeekBar greenBar, SeekBar blueBar, View previewBox, TextView hexLabel) {
            Button btn = new Button(requireContext());
            btn.setText(label);
            btn.setTextSize(12);
            btn.setOnClickListener(v -> {
                int c = Color.parseColor(hexColor);
                selectedColor[0] = c;
                redBar.setProgress(Color.red(c));
                greenBar.setProgress(Color.green(c));
                blueBar.setProgress(Color.blue(c));
                previewBox.setBackgroundColor(c);
                hexLabel.setText(hexColor);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.rightMargin = 8;
            btn.setLayoutParams(lp);
            parent.addView(btn);
        }

        private SeekBar addSlider(LinearLayout parent, String labelText, int initialValue) {
            TextView label = new TextView(requireContext());
            label.setText(labelText);
            parent.addView(label);

            SeekBar bar = new SeekBar(requireContext());
            bar.setMax(255);
            bar.setProgress(initialValue);
            bar.setPadding(0, 8, 0, 20);
            parent.addView(bar);
            return bar;
        }
    }
                }
            
