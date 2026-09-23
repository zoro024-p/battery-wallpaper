-repackageclasses ''
-allowaccessmodification
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-keep class com.zoro.batterywallpaper.BatteryTextWallpaperService { *; }
-keep class com.zoro.batterywallpaper.SettingsActivity$SettingsFragment { *; }

