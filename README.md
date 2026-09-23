# battery-wallpaper
dedicated to my mother

build usimg ai help and the readme is entirely ai made

Battery Text Wallpaper
A lightweight, high-contrast Android Live Wallpaper engineered for elderly vision accessibility, low cognitive load, and minimal battery impact. Built with a custom zero-allocation rendering engine that displays time, date, and live battery status directly on your home and lock screens.
Features
Optical & Senior Accessibility
 * Cataract-Safe Contrast (16.4:1): Warm Ivory (#FFF8E7) glyphs over Deep Charcoal (#1A1A1A) surfaces eliminate high-energy blue scatter (halations) without the eye fatigue of pitch-black OLED displays. Exceeds WCAG AAA requirements.
 * Large Typographic Scale: Base text size defaults to 140px (exceeding 4.5 mm on 1080p panels), providing clear legibility without reading glasses.
 * Jitter-Free Tabular Numerals (tnum): Enforces fixed-width tabular OpenType figures across clock and percentage displays, preventing numerals from jumping horizontally on minute ticks.
 * High-Saliency Battery Warning: Transitions to safety vermillion (#FF5722) when battery levels drop to \le 25\%, preserving visibility through cataract-yellowed crystalline lenses.
 * Typographic Charging Indicator: Uses Unicode standard text mode (⚡\uFE0E) in charging green (#66BB6A) to prevent unpredictable system emoji bitmap substitutions.
 * One-Tap Accessibility Reset: Instantly restores all 16 preference keys to senior-optimized defaults and updates the UI in memory with zero screen flash.
Engine & Performance
 * Zero-GC Render Loop: Eliminates runtime object allocations (Rect, RectF, SimpleDateFormat, Calendar) inside draw(). String measurements and date formatting only execute when the minute or calendar day changes.
 * Hardware-Optimized Blitting: Custom background photos are downsampled, pre-dimmed, and baked into a 1:1 surface-sized RGB_565 bitmap on a background worker thread, cutting bitmap heap footprint by 50% and eliminating runtime GPU overdraw scrims.
 * Thread-Safe Canvas Swapping: Background decode workers and system broadcasts synchronize state swaps via internal render locks before posting updates to the main thread.
 * Sub-Perceptual Burn-in Mitigation: Sinusoidal orbital shifting (\pm1.2\%) prevents OLED burn-in below the threshold of human alignment perception.
 * Modern Android Lifecycle: Fully compliant with Android 14 (API 34) broadcast registration rules using ContextCompat.RECEIVER_NOT_EXPORTED.
Technical Specifications
| Parameter | Default Value | Target Standard |
|---|---|---|
| Package | com.zoro.batterywallpaper | Modern Android Gradle namespace |
| Min / Target SDK | Android 8.0 (API 26) / Android 14 (API 34) | Wide compatibility |
| Font Family | sans-serif-medium (Bold, tnum) | Material typographic baseline |
| Color Scheme | #FFF8E7 text on #1A1A1A background | WCAG AAA Contrast (16.4:1) |
| Warning Threshold | 25\% (#FF5722 Vermillion) | High-wavelength transmission |
| Position Defaults | Vertical: 45\%, Horizontal: 50\% | Ergonomic focal axis |
| Bitmap Space | 16-bit Bitmap.Config.RGB_565 | 2 bytes/pixel memory footprint |
Build Variants
The Gradle configuration produces both architecture-specific splits and a universal APK:
 * app-universal-release.apk — Installs on any device architecture (best for simple sideloading).
 * app-arm64-v8a-release.apk — Optimized for modern 64-bit Android smartphones.
 * app-armeabi-v7a-release.apk — Optimized for legacy 32-bit hardware.
 * app-x86_64-release.apk & app-x86-release.apk — Optimized for Android emulators and ChromeOS.
Local Development & Compilation
Prerequisites
 * Android Studio Iguana (or newer) / Android SDK Platform 34
 * JDK 17 (Temurin recommended)
 * Gradle 8.x
Build Commands
Clone the repository and compile via command line:
# Clone the repository
git clone https://github.com/<your-username>/<your-repo>.git
cd <your-repo>

# Clean and compile release artifacts
./gradlew clean assembleRelease

Compiled APKs are output to:
app/build/outputs/apk/release/

CI/CD Pipeline
Automated builds run on Ubuntu runners via GitHub Actions (.github/workflows/android-build.yml). Every push to main or tag push (v*):
 * Sets up JDK 17 and Gradle build caches.
 * Compiles release APKs with R8 code and resource shrinking.
 * Automatically signs all ABI splits and universal builds using Android's standard debug key for easy sideloading.
 * Uploads build artifacts to workflow summaries and attaches them to GitHub Releases.
License
Distributed under the Apache 2.0 License. See LICENSE for details.
Remember: if you commit this README.md file using the GitHub web interface without [skip ci] in the commit message, GitHub Actions will detect the push to main and start compiling your new release APKs immediately.

