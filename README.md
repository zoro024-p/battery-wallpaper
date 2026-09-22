# battery-wallpaper
dedicated to my mother
Battery Wallpaper
A lightweight, hardware-accelerated Android Live Wallpaper built specifically for elderly users, featuring a high-visibility clock, calendar date, and a large color-coded battery status indicator.
Features
 * High-Visibility Display: Features a prominent digital clock, date, and a custom battery level layout designed for readability at a glance.
 * Smart Color Coding:
   * 🟢 Green when the phone is charging.
   * 🔴 Red when the battery drops below your warning threshold.
   * ⚪ White during normal operation.
 * Customization Menu:
   * Adjustable text size slider (150px to 500px).
   * Background theme choices (Dark Gray, Pitch Black, Deep Navy, Forest Green).
   * Low battery warning threshold configuration (5% to 30%).
   * 12-hour or 24-hour time format toggle.
 * OLED Burn-In Protection: Subtle, minute-by-minute coordinate shifting to protect screen pixels over long durations.
Installation
 * Download the latest batter-wallpaper.apk from the repository's Releases or Actions artifacts section.
 * Install the APK onto the target Android device (requires Android 8.0 / API 26 or higher).
 * Open the Battery Wallpaper app icon from your launcher.
 * Customize your preferred settings, then tap Apply Wallpaper to set it as your active live background.
Technical Architecture
 * Engine: Built on native Android WallpaperService utilizing hardware-accelerated SurfaceHolder canvas rendering.
 * State Management: Persists all user preferences via SharedPreferences and dynamically updates the canvas via native change listeners.
 * CI/CD Pipeline: Fully automated compilation via GitHub Actions using Gradle 8.4 and OpenJDK 17.
 * 
